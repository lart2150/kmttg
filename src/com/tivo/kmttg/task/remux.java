/*
 * Copyright 2008-Present Kevin Moye <moyekj@yahoo.com>.
 *
 * This file is part of kmttg package.
 *
 * kmttg is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this project.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tivo.kmttg.task;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.mux.MetadataTags;
import com.tivo.kmttg.mux.MuxSink;
import com.tivo.kmttg.mux.mkv.MkvMuxer;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;

import com.tivo.kmttg.rpc.artwork;
import com.tivo.kmttg.rpc.ClipSegments;

import net.straylightlabs.tivolibre.TivoDecoder;
import net.straylightlabs.tivolibre.TransportStreamReader;

// Built in remux: writes an MKV carrying the source streams unchanged, no re-encoding and no
// external binary. Takes either an already decrypted transport stream or, when no decrypt job
// produced one, the .TiVo itself - decrypting and muxing in a single read with nothing
// intermediate on disk. This is the in-process counterpart of an "encode" job, so it follows
// tivolibre's thread pattern rather than encode's, which polls a spawned process.
public class remux extends baseTask implements Serializable {
   private static final long serialVersionUID = 1L;
   private killableThread thread = null;
   // Written by the worker thread, read by the job monitor polling check(). Without volatile
   // there is no ordering between them: the monitor could see thread_running go false and
   // still read a stale success, throw away a finished mkv and kill the whole job family.
   private volatile Boolean thread_running = false;
   private volatile Boolean success = false;
   private volatile String failure = null;
   private backgroundProcess process;
   public jobData job;

   // Closing the input stream is what actually stops a read in progress.
   public class killableThread extends Thread {
      private BufferedInputStream inputStream = null;

      public void interrupt() {
         super.interrupt();
         try {
            if (inputStream != null) inputStream.close();
         } catch (IOException e) {}
      }

      public void run() {
         MuxSink sink = null;
         try {
            inputStream = new BufferedInputStream(new FileInputStream(job.inputFile), 1 << 20);
            sink = new MuxSink(new File(job.encodeFile));
            sink.setSupplement(supplement(job));
            attachChapters(sink, job);
            attachCoverArt(sink, job);
            if (job.inputFile.toLowerCase().endsWith(".tivo")) {
               // Fused: decrypt and mux in one read, no intermediate .ts written at all.
               success = new TivoDecoder.Builder()
                  .input(inputStream)
                  .frameSink(sink)
                  .mak(config.MAK)
                  .build()
                  .decode();
            } else {
               success = new TransportStreamReader.Builder()
                  .input(inputStream)
                  .frameSink(sink)
                  .build()
                  .read();
            }
            if (success && sink.isFailed()) {
               success = false;
               failure = sink.getFailure();
            }
            reportNotes(sink);
         }
         catch (Exception e) {
            success = false;
            failure = e.getMessage();
         }
         finally {
            // Both of these leak on the cancel path otherwise: interrupt() closes the input
            // to stop the read, decode() throws, and neither the stream nor the muxer's
            // RandomAccessFile was released - which on Windows also blocks deleting .part.
            try {
               if (inputStream != null) inputStream.close();
            } catch (IOException e) {}
            if (sink != null) sink.abort();
            // kill() removes the job from the list, so check() never runs on a cancel.
            // A partial mkv left at the real name would be skipped by OverwriteFiles=0
            // forever, so it goes here.
            if (! success) file.delete(job.encodeFile);
            thread_running = false;
         }
      }
   }

   // What the sink has to say beyond pass or fail. Shared with the download path, which runs
   // the same muxer inside the decode rather than as a job of its own.
   public static void reportNotes(MuxSink sink) {
      // Dropped payloads are lost pictures, so say so rather than only counting them.
      if (sink.getDiscardedNoPts() > 0)
         log.warn("remux: dropped " + sink.getDiscardedNoPts() + " payload(s) with no PTS");
      for (String note : sink.getNotes()) {
         log.warn("remux: " + note);
      }
   }

   // callsign and the episode/season numbers are never in the .TiVo - they reach kmttg over
   // RPC and ride on the job, so they have to be handed to the muxer separately.
   public static MetadataTags.Supplement supplement(jobData job) {
      MetadataTags.Supplement s = new MetadataTags.Supplement();
      s.callsign = job.callsign;
      s.title    = job.title;
      s.seriesId = job.seriesId;
      s.setSeasonEpisode(job.season, job.episode);
      s.setEpisodeNumber(job.episodeNumber);
      return s;
   }

   // SkipMode segments as Matroska chapters, so a player can jump the ad breaks the way the
   // TiVo does. Best effort like the cover art - no skip data is not a reason to fail, and no
   // rpcEnabled check here because neither source needs the local box.
   public static void attachChapters(MuxSink sink, jobData job) {
      if (job.contentId == null) return;
      try {
         ClipSegments.Result result = ClipSegments.get(
            job.tivoName, job.contentId, job.recordingId, job.clipMetadataId, job.offerId);
         if (result == null || result.segments.isEmpty()) return;
         // Same check the AutoSkip write makes. Without it, segments anchored outside the
         // recording all clamp away in the muxer and the "keep at least one" fallback leaves
         // a single meaningless chapter spanning the whole file.
         // Records the verdict when it does not fit, so the fetch job stops asking for this
         // recording on every NPL refresh. Only tivo.com data is worth remembering: AutoSkip
         // data that fails is a local problem, not something re-fetching would fix.
         if (result.source == ClipSegments.SOURCE_SKIPMODE) {
            if (! ClipSegments.remember(job.contentId, job.clipMetadataId, job.title,
                  result.segments, job.recordingDurationMs)) return;
         } else {
            String reason = ClipSegments.rejectReason(result.segments, job.recordingDurationMs);
            if (reason != null) {
               ClipSegments.warnRejected(job.title, reason);
               return;
            }
         }
         sink.setChapters(buildChapters(result.segments));
         log.print("Embedding " + result.segments.size() + " SkipMode segments as chapters");

         // Only SkipMode data is worth keeping: AutoSkip data is where it would be written.
         if (result.source == ClipSegments.SOURCE_SKIPMODE
               && config.autoskip_save_skipmode == 1) {
            ClipSegments.saveToAutoSkip(job.contentId, job.offerId, job.title, job.tivoName,
               result.segments, job.recordingDurationMs);
         }
      } catch (Exception e) {
         log.warn("Could not fetch SkipMode data: " + e.getMessage());
      }
   }

   // Alternating show and ad chapters rather than show only: a gap between chapters is
   // invisible in a chapter menu, and marking the breaks is what makes them skippable. The ad
   // chapters are the gaps between segments, so n segments give 2n-1 chapters - there is no
   // break after the last segment, and anything before the first is inside it.
   public static List<MkvMuxer.Chapter> buildChapters(List<ClipSegments.Segment> segments) {
      List<MkvMuxer.Chapter> chapters = new ArrayList<MkvMuxer.Chapter>();
      for (int i=0; i<segments.size(); ++i) {
         ClipSegments.Segment s = segments.get(i);
         chapters.add(new MkvMuxer.Chapter(s.startMs, s.endMs, "Segment " + (i+1)));
         if (i+1 < segments.size()) {
            chapters.add(new MkvMuxer.Chapter(
               s.endMs, segments.get(i+1).startMs, "Commercials " + (i+1)));
         }
      }
      return chapters;
   }

   // Poster art for the MKV, the same image Show Information displays. Fetched here rather
   // than in the mux package, which must not depend on gui or rpc to stay testable without a
   // display or a TiVo. Best effort throughout: no art is not a reason to fail a remux.
   public static void attachCoverArt(MuxSink sink, jobData job) {
      if (job.contentId == null && job.collectionId == null) return;
      try {
         // Inside the try as well: this reads shared config and needs a tivoName, neither of
         // which is guaranteed on every path that reaches a remux job.
         if (! config.rpcEnabled(job.tivoName)) return;
         // Largest available: the dialog wants a 180px thumbnail, a media library wants the
         // real poster.
         String url = artwork.findUrl(job.tivoName, job.contentId, job.collectionId,
            artwork.LARGEST);
         if (url == null) return;
         byte[] data = artwork.fetch(url);
         if (data == null) return;
         sink.setCoverArt(artwork.coverName(url), artwork.mimeType(url), data);
         log.print("Embedding cover art (" + data.length + " bytes) from " + url);
      } catch (Exception e) {
         log.warn("Could not fetch cover art: " + e.getMessage());
      }
   }

   public remux(jobData job) {
      debug.print("job=" + job);
      this.job = job;
   }

   public backgroundProcess getProcess() {
      return process;
   }

   public Boolean launchJob() {
      debug.print("");
      Boolean schedule = true;

      if ( file.isFile(job.encodeFile) ) {
         if (config.OverwriteFiles == 0) {
            log.warn("SKIPPING REMUX, FILE ALREADY EXISTS: " + job.encodeFile);
            schedule = false;
         } else {
            log.warn("OVERWRITING EXISTING FILE: " + job.encodeFile);
         }
      }

      // Same source selection as an encode job: the cut file when there is one. Falling back
      // to the .TiVo lets a job run fused, decrypting and muxing in one read with no
      // intermediate on disk - only reachable when no decrypt job produced an mpeg first.
      String mpeg = file.isFile(job.mpegFile_cut) ? job.mpegFile_cut : job.mpegFile;
      if (! file.isFile(mpeg) && job.tivoFile != null && file.isFile(job.tivoFile)) {
         if (config.MAK == null || config.MAK.length() == 0) {
            log.error("Cannot remux a .TiVo file without a MAK configured");
            schedule = false;
         } else {
            mpeg = job.tivoFile;
         }
      }
      if (schedule && ! file.isFile(mpeg)) {
         log.error("source file not given or doesn't exist: " + mpeg);
         schedule = false;
      }
      job.inputFile = mpeg;

      if (schedule && ! jobMonitor.createSubFolders(job.encodeFile, job)) {
         schedule = false;
      }

      if (schedule) {
         if ( start() ) {
            job.process = this;
            jobMonitor.updateJobStatus(job, "running");
            job.time = new Date().getTime();
         }
         return true;
      }
      return false;
   }

   public Boolean start() {
      debug.print("");
      // Written straight to its final name, as encode.java does. The decode verdict only
      // arrives at EOF, so the worker deletes a failed output on the way out. A hard crash
      // can still leave a partial behind; that is a redownload, not something worth a staging
      // file and a rename that File.renameTo cannot do over an existing file on Windows.
      file.delete(job.encodeFile);

      log.print(">> REMUXING " + job.inputFile + " TO " + job.encodeFile + " ...");
      thread_running = true;
      thread = new killableThread();
      thread.start();
      return true;
   }

   public void kill() {
      debug.print("");
      log.warn("Killing '" + job.type + "' job: " + job.encodeFile);
      // Null when the job was killed before start() ran, which a family kill can do.
      if (thread != null) thread.interrupt();
      thread_running = false;
   }

   public Boolean check() {
      if (thread_running) {
         if (config.GUIMODE) {
            String t = jobMonitor.getElapsedTime(job.time);
            int pct = -1;
            if (job.inputFileSize == null && file.isFile(job.inputFile)) {
               job.inputFileSize = file.size(job.inputFile);
            }
            String size = "";
            if (file.isFile(job.encodeFile)) {
               size = String.format("%.2f MB", (float)file.size(job.encodeFile)/Math.pow(2,20));
               if (job.inputFileSize != null && job.inputFileSize > 0) {
                  // Output tracks input closely for a remux, so its size is a fair proxy.
                  pct = (int)(file.size(job.encodeFile)*100/job.inputFileSize);
                  if (pct > 100) pct = 100;
               }
            }
            if ( jobMonitor.isFirstJobInMonitor(job) ) {
               config.gui.jobTab_UpdateJobMonitorRowStatus(job, t + "---" + size);
               if (pct > -1) {
                  config.gui.setTitle(String.format("remux: %d%% %s", pct, config.kmttg));
                  config.gui.progressBar_setValue(pct);
               }
            } else {
               config.gui.jobTab_UpdateJobMonitorRowStatus(job,
                  (pct > -1 ? String.format("%d%%", pct) : t) + "---" + size);
            }
         }
         return true;
      }

      if (config.GUIMODE && jobMonitor.isFirstJobInMonitor(job)) {
         config.gui.setTitle(config.kmttg);
         config.gui.progressBar_setValue(0);
      }
      jobMonitor.removeFromJobList(job);

      // An empty output is a failure, not a success with a small file.
      if ( success && file.isFile(job.encodeFile) && ! file.isEmpty(job.encodeFile) ) {
         log.warn("remux job completed: " + jobMonitor.getElapsedTime(job.time));
         log.print("---DONE--- job=" + job.type + " output=" + job.encodeFile);
         copyMetadataSidecar();
         removeSourceIfConfigured();
      } else {
         if (failure != null) log.error("remux failed: " + failure);
         else log.error("remux failed on: " + job.inputFile);
         file.delete(job.encodeFile);
         // Kill the family, or later jobs run against an encodeFile that does not exist.
         // familyId is assigned by submitNewJob, so a job driven directly - a test, or any
         // caller that bypasses the monitor - has no family and must not be killed.
         if (job.familyId != null) jobMonitor.kill(job);
      }
      return false;
   }

   private void copyMetadataSidecar() {
      String input_meta = job.inputFile + ".txt";
      String output_meta = job.encodeFile + ".txt";
      if ( ! file.isFile(output_meta) && file.isFile(input_meta) ) {
         if (file.copy(input_meta, output_meta))
            log.warn("Copied metadata file " + input_meta + " to " + output_meta);
         else
            log.error("Failed to copy metadata file: " + input_meta);
      }
   }

   // Mirrors encode.java: a second encoding job working off the same source means the source
   // has to stay. Without this every MKV job left its full size intermediate .ts on disk.
   private void removeSourceIfConfigured() {
      if (job.hasMoreEncodingJobs || config.RemoveMpegFile != 1) return;
      // On the fused path inputFile IS the .TiVo. RemoveMpegFile means "remove the decrypted
      // mpeg"; deleting the download is governed by RemoveTivoFile and belongs to the decrypt
      // task, so removing it here would destroy the only copy of the recording.
      if (job.tivoFile != null && job.tivoFile.equals(job.inputFile)) return;
      if ( file.delete(job.inputFile) ) {
         log.print("(Deleted file: " + job.inputFile + ")");
      } else {
         log.error("Failed to delete file: " + job.inputFile);
      }
      if ( file.delete(job.inputFile + ".txt") ) {
         log.print("(Deleted file: " + job.inputFile + ".txt)");
      }
      if ( ! job.inputFile.equals(job.mpegFile) && file.delete(job.mpegFile) ) {
         log.print("(Deleted file: " + job.mpegFile + ")");
      }
      return;
   }
}
