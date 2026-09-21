package com.tivo.kmttg.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Hashtable;
import java.util.Stack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// LaunchJobs turns a set of ticked checkboxes into the jobs that produce a file. It is the one
// place that knows a task needs an input some earlier task has to write first, and getting that
// wrong shows up as a job failing hours later on a file that was never created. The tests work
// in FILES mode where they can, because it reaches the same naming and dependency code without
// a TiVo, and in Download mode for the parts only that path has.
public class LaunchJobsTest {

   @TempDir Path dir;

   // config is process wide, gradle runs the whole source set in one JVM, and LaunchJobs reads
   // a lot of it. Named rather than snapshotting the class so what the tests depend on is
   // visible here.
   private static final String[] TOUCHED = {
      "GUIMODE", "outputDir", "mpegDir", "qsfixDir", "mpegCutDir", "encodeDir", "programDir",
      "encProfDir", "encodeName", "ffmpeg", "comskip", "t2extract", "ccextractor",
      "VRD", "VrdDecrypt", "VrdEncode", "VrdReview", "VrdReview_noCuts", "VrdCombineCutEncode",
      "UseAdscan", "comskip_review", "DsdDecrypt", "tivolibreDecrypt", "combine_download_decrypt",
      "OverwriteFiles", "RemoveMpegFile", "metadata_files", "autoskip_enabled",
      "autoskip_cutonly", "resumeDownloads", "autotune", "persistQueue",
   };

   private Object[] saved;
   private Stack<jobData> savedJobs;
   // Cancelled tasks log. Pin logging to stdout so no auto.log handler is opened.
   private boolean savedStartingUp;

   @BeforeEach
   void setUp() throws Exception {
      savedStartingUp = kmttg._startingUp;
      kmttg._startingUp = true;
      saved = new Object[TOUCHED.length];
      for (int i = 0; i < TOUCHED.length; i++)
         saved[i] = field(TOUCHED[i]).get(null);
      savedJobs = jobMonitor.JOBS;
      jobMonitor.JOBS = new Stack<jobData>();

      config.GUIMODE = false;
      config.persistQueue = false;
      config.programDir = dir.toString();
      config.outputDir = videoDir("video");
      config.mpegDir = config.outputDir;
      config.qsfixDir = config.outputDir;
      config.mpegCutDir = config.outputDir;
      config.encodeDir = config.outputDir;
      config.VRD = 0;
      config.VrdDecrypt = 0;
      config.VrdEncode = 0;
      config.VrdReview = 0;
      config.VrdReview_noCuts = 0;
      config.VrdCombineCutEncode = 0;
      config.UseAdscan = 0;
      config.comskip_review = 0;
      config.DsdDecrypt = 0;
      config.tivolibreDecrypt = 1;
      config.combine_download_decrypt = 0;
      config.OverwriteFiles = 0;
      config.RemoveMpegFile = 0;
      config.metadata_files = "last";
      config.resumeDownloads = false;
      config.autotune = null;
      // Keeps SkipManager off AutoSkip.ini, which lives in the real install dir
      config.autoskip_enabled = 0;
      config.autoskip_cutonly = 0;
      config.comskip = dir.resolve("comskip.exe").toString();
      config.t2extract = dir.resolve("tivo2go.exe").toString();
      config.ccextractor = dir.resolve("ccextractor.exe").toString();
      config.ffmpeg = dir.resolve("ffmpeg.exe").toString();

      writeProfile("mkv_copy", "KMTTG_MUX INPUT OUTPUT", "mkv");
      writeProfile("ff_mp4", "FFMPEG -y -i INPUT -c copy OUTPUT", "mp4");
      config.encProfDir = dir.resolve("encode").toString();
      encodeConfig.parseEncodingProfiles();
      config.encodeName = "ff_mp4";
   }

   @AfterEach
   void restore() throws Exception {
      kmttg._startingUp = savedStartingUp;
      jobMonitor.JOBS = savedJobs;
      for (int i = 0; i < TOUCHED.length; i++)
         field(TOUCHED[i]).set(null, saved[i]);
      encodeConfig.parseEncodingProfiles();
   }

   private static Field field(String name) throws Exception {
      Field f = config.class.getDeclaredField(name);
      f.setAccessible(true);
      return f;
   }

   private String videoDir(String name) throws Exception {
      return Files.createDirectories(dir.resolve(name)).toString();
   }

   private void writeProfile(String name, String command, String extension) throws Exception {
      Path enc = Files.createDirectories(dir.resolve("encode"));
      String body = "<description>\n" + name + "\n<command>\n" + command
                  + "\n<extension>\n" + extension + "\n";
      Files.write(enc.resolve(name + ".enc"), body.getBytes(StandardCharsets.UTF_8));
   }

   private String touch(String name) throws Exception {
      Path p = Path.of(config.mpegDir, name);
      Files.write(p, "not really video".getBytes(StandardCharsets.UTF_8));
      return p.toString();
   }

   private String inVideoDir(String name) {
      return Path.of(config.mpegDir, name).toString();
   }

   // A FILES mode task set with everything off; the tests turn on what they are about.
   private Hashtable<String,Object> files(String startFile) {
      Hashtable<String,Object> specs = new Hashtable<String,Object>();
      specs.put("mode", "FILES");
      specs.put("tivoName", "FILES");
      specs.put("startFile", startFile);
      specs.put("encodeName", "ff_mp4");
      for (String task : new String[] {"metadata", "metadataTivo", "decrypt", "qsfix",
            "twpdelete", "rpcdelete", "comskip", "comcut", "captions", "encode", "custom"})
         specs.put(task, Boolean.FALSE);
      return specs;
   }

   // A Download mode task set for one recording off the Now Playing list.
   private Hashtable<String,Object> download(String name) {
      Hashtable<String,Object> specs = files(name);
      specs.put("mode", "Download");
      specs.put("tivoName", "Bolt");
      specs.put("name", name);
      specs.put("TSDownload", Integer.valueOf(1));
      Hashtable<String,String> entry = new Hashtable<String,String>();
      entry.put("url", "http://bolt/download/Ghosts.TiVo?id=8675309");
      entry.put("url_TiVoVideoDetails", "http://bolt/TiVoVideoDetails?id=8675309");
      entry.put("size", "4294967296");
      entry.put("title", "Ghosts - Gate-gate");
      entry.put("titleOnly", "Ghosts");
      entry.put("duration", "1800000");
      entry.put("ProgramId", "EP012345670001");
      specs.put("entry", entry);
      return specs;
   }

   private static Stack<String> types() {
      Stack<String> o = new Stack<String>();
      for (jobData job : jobMonitor.JOBS)
         o.add(job.type);
      return o;
   }

   private static jobData find(String type) {
      for (jobData job : jobMonitor.JOBS)
         if (job.type.equals(type)) return job;
      return null;
   }

   private static void assertQueued(String... expected) {
      Stack<String> want = new Stack<String>();
      for (String t : expected) want.add(t);
      assertEquals(want, types());
   }

   // ---- FILES mode dependencies -------------------------------------------

   @Test
   void aFullTaskSetOnATivoFileQueuesEveryStepInOrder() throws Exception {
      Hashtable<String,Object> specs = files(touch("Ghosts.TiVo"));
      specs.put("decrypt", Boolean.TRUE);
      specs.put("comskip", Boolean.TRUE);
      specs.put("comcut", Boolean.TRUE);
      specs.put("encode", Boolean.TRUE);
      jobMonitor.LaunchJobs(specs);

      assertQueued("tivolibre", "comskip", "ffcut", "encode");
      assertEquals(inVideoDir("Ghosts.mpg"), find("tivolibre").mpegFile);
      assertEquals(inVideoDir("Ghosts.edl"), find("comskip").edlFile);
      assertEquals(inVideoDir("Ghosts_cut.mpg"), find("ffcut").mpegFile_cut);
      assertEquals(inVideoDir("Ghosts.mp4"), find("encode").encodeFile);
   }

   @Test
   void encodingATivoFileTurnsOnTheDecryptItNeeds() throws Exception {
      // The user ticks only Encode. Nothing can encode a .TiVo, and no .mpg exists yet, so
      // the decrypt has to be added or the encode job starts on a file that is not there.
      Hashtable<String,Object> specs = files(touch("Ghosts.TiVo"));
      specs.put("encode", Boolean.TRUE);
      jobMonitor.LaunchJobs(specs);

      assertQueued("tivolibre", "encode");
   }

   @Test
   void encodingLeavesTheDecryptOffWhenTheMpegIsAlreadyThere() throws Exception {
      // Re-running the encode on a show that was decrypted earlier must not decrypt it again -
      // that is a full length file rewritten for nothing.
      touch("Ghosts.mpg");
      Hashtable<String,Object> specs = files(touch("Ghosts.TiVo"));
      specs.put("encode", Boolean.TRUE);
      jobMonitor.LaunchJobs(specs);

      assertQueued("encode");
   }

   @Test
   void adCuttingTurnsOnTheAdDetectThatProducesItsCutPoints() throws Exception {
      Hashtable<String,Object> specs = files(touch("Ghosts.mpg"));
      specs.put("comcut", Boolean.TRUE);
      jobMonitor.LaunchJobs(specs);

      assertQueued("comskip", "ffcut");
   }

   @Test
   void adCuttingReusesAnEdlFileThatAlreadyExists() throws Exception {
      // An edl the user edited by hand, or one comskip wrote on an earlier run. Re-detecting
      // would throw those cut points away.
      touch("Ghosts.edl");
      Hashtable<String,Object> specs = files(touch("Ghosts.mpg"));
      specs.put("comcut", Boolean.TRUE);
      jobMonitor.LaunchJobs(specs);

      assertQueued("ffcut");
   }

   @Test
   void adDetectOnATivoFileTurnsOnTheDecryptItNeeds() throws Exception {
      Hashtable<String,Object> specs = files(touch("Ghosts.TiVo"));
      specs.put("comskip", Boolean.TRUE);
      jobMonitor.LaunchJobs(specs);

      assertQueued("tivolibre", "comskip");
   }

   @Test
   void decryptAndTivoMetadataAreDroppedForAFileThatIsNotATivoFile() throws Exception {
      // FILES mode happily takes an .mpg. Neither task has anything to read out of one.
      Hashtable<String,Object> specs = files(touch("Ghosts.mpg"));
      specs.put("decrypt", Boolean.TRUE);
      specs.put("metadataTivo", Boolean.TRUE);
      specs.put("captions", Boolean.TRUE);
      jobMonitor.LaunchJobs(specs);

      assertQueued("captions");
   }

   @Test
   void theCutFileKeepsItsSuffixOnlyWhenItWouldLandOnTheUncutOne() throws Exception {
      Hashtable<String,Object> specs = files(touch("Ghosts.mpg"));
      specs.put("comcut", Boolean.TRUE);
      specs.put("comskip", Boolean.TRUE);
      jobMonitor.LaunchJobs(specs);
      assertEquals(inVideoDir("Ghosts_cut.mpg"), find("ffcut").mpegFile_cut);

      // With a separate cut dir configured there is nothing to collide with, and keeping the
      // name means the file a media library already knows does not change.
      jobMonitor.JOBS = new Stack<jobData>();
      config.mpegCutDir = videoDir("cut");
      specs = files(touch("Ghosts.mpg"));
      specs.put("comcut", Boolean.TRUE);
      specs.put("comskip", Boolean.TRUE);
      jobMonitor.LaunchJobs(specs);
      assertEquals(Path.of(config.mpegCutDir, "Ghosts.mpg").toString(),
         find("ffcut").mpegFile_cut);
   }

   @Test
   void captionsFollowTheLastVideoFileTheTaskSetProduces() throws Exception {
      // The .srt has to sit next to the file that will be played, or the player never finds it.
      Hashtable<String,Object> specs = files(touch("Ghosts.TiVo"));
      specs.put("decrypt", Boolean.TRUE);
      specs.put("captions", Boolean.TRUE);
      jobMonitor.LaunchJobs(specs);
      assertEquals(inVideoDir("Ghosts.srt"), find("captions").srtFile);

      jobMonitor.JOBS = new Stack<jobData>();
      specs = files(touch("Ghosts.TiVo"));
      specs.put("decrypt", Boolean.TRUE);
      specs.put("encode", Boolean.TRUE);
      specs.put("captions", Boolean.TRUE);
      jobMonitor.LaunchJobs(specs);
      assertEquals(inVideoDir("Ghosts.srt"), find("captions").srtFile);
      assertEquals(inVideoDir("Ghosts.mp4"), find("encode").encodeFile);
   }

   // ---- encoding profiles -------------------------------------------------

   @Test
   void anEncodingProfileThatDoesNotExistCancelsTheEncode() throws Exception {
      Hashtable<String,Object> specs = files(touch("Ghosts.mpg"));
      specs.put("encode", Boolean.TRUE);
      specs.put("encodeName", "no such profile");
      jobMonitor.LaunchJobs(specs);

      assertQueued();
   }

   @Test
   void aSecondProfileThatDoesNotExistIsDroppedAndTheFirstStillRuns() throws Exception {
      // "Cancelling" used to leave the name in place, and the second job was built from it
      // anyway - which then threw looking the missing name up in the profile table.
      Hashtable<String,Object> specs = files(touch("Ghosts.mpg"));
      specs.put("encode", Boolean.TRUE);
      specs.put("encodeName2", "no such profile");
      specs.put("encodeName2_suffix", "phone");
      jobMonitor.LaunchJobs(specs);

      assertQueued("encode");
      assertEquals(inVideoDir("Ghosts.mp4"), find("encode").encodeFile);
      assertFalse(find("encode").hasMoreEncodingJobs,
         "the source file would be held back for a job that is not coming");
   }

   @Test
   void twoEncodingProfilesEachGetTheirOwnOutputFile() throws Exception {
      Hashtable<String,Object> specs = files(touch("Ghosts.mpg"));
      specs.put("encode", Boolean.TRUE);
      specs.put("encodeName2", "mkv_copy");
      specs.put("encodeName2_suffix", "phone");
      jobMonitor.LaunchJobs(specs);

      assertEquals(2, jobMonitor.JOBS.size());
      assertEquals(inVideoDir("Ghosts.mp4"), jobMonitor.JOBS.get(0).encodeFile);
      assertEquals(inVideoDir("Ghosts_phone.mkv"), jobMonitor.JOBS.get(1).encodeFile);
      assertTrue(jobMonitor.JOBS.get(0).hasMoreEncodingJobs,
         "the first encode would delete the source out from under the second");
   }

   @Test
   void theBuiltinMuxProfileBecomesARemuxJobNotAnEncode() throws Exception {
      Hashtable<String,Object> specs = files(touch("Ghosts.mpg"));
      specs.put("encode", Boolean.TRUE);
      specs.put("encodeName", "mkv_copy");
      jobMonitor.LaunchJobs(specs);

      assertQueued("remux");
      assertEquals(inVideoDir("Ghosts.mkv"), find("remux").encodeFile);
   }

   // ---- Download mode -----------------------------------------------------

   @Test
   void aPlainDownloadAndDecryptAreTwoJobs() {
      Hashtable<String,Object> specs = download("Ghosts - Gate-gate.TiVo");
      specs.put("decrypt", Boolean.TRUE);
      jobMonitor.LaunchJobs(specs);

      assertQueued("javadownload", "tivolibre");
      assertEquals(Long.valueOf(4294967296L), find("javadownload").tivoFileSize);
      assertEquals(Integer.valueOf(1800), find("javadownload").download_duration);
   }

   @Test
   void combineDownloadAndDecryptCollapsesThemIntoOneJob() {
      // Two jobs means the whole recording is written to disk and read back; one means the
      // decode happens as it arrives.
      config.combine_download_decrypt = 1;
      Hashtable<String,Object> specs = download("Ghosts - Gate-gate.TiVo");
      specs.put("decrypt", Boolean.TRUE);
      specs.put("rpcdelete", Boolean.TRUE);
      jobMonitor.LaunchJobs(specs);

      assertQueued("tdownload_decrypt");
      assertTrue(find("tdownload_decrypt").rpcdelete,
         "delete-after-download has to move onto the job that now finishes the decrypt");
      assertNotNull(find("tdownload_decrypt").entry);
   }

   @Test
   void theShowsDetailsReachTheJobThatWritesTheTags() {
      // The remuxer writes these into the MKV, and the download job now carries the remux.
      Hashtable<String,Object> specs = download("Ghosts - Gate-gate.TiVo");
      Hashtable<String,String> entry = entryOf(specs);
      entry.put("EpisodeNumber", "301");
      entry.put("season", "03");
      entry.put("episode", "01");
      entry.put("channel", "CBS");
      entry.put("offerId", "tivo:of.42");
      jobMonitor.LaunchJobs(specs);

      jobData job = find("javadownload");
      assertEquals("Ghosts - Gate-gate", job.title);
      assertEquals("03", job.season);
      assertEquals("01", job.episode);
      assertEquals("CBS", job.callsign);
      assertEquals("tivo:of.42", job.offerId);
      assertEquals(1800000L, job.recordingDurationMs);
   }

   @Test
   void aRecordingStillInProgressIsNotQueuedAtAll() {
      Hashtable<String,Object> specs = download("Ghosts - Gate-gate.TiVo");
      entryOf(specs).put("InProgress", "Yes");
      jobMonitor.LaunchJobs(specs);

      assertQueued();
   }

   @Test
   void aCopyProtectedRecordingIsNotQueuedAtAll() {
      Hashtable<String,Object> specs = download("Ghosts - Gate-gate.TiVo");
      entryOf(specs).put("CopyProtected", "Yes");
      jobMonitor.LaunchJobs(specs);

      assertQueued();
   }

   @Test
   void theBuiltinMuxNeedsATransportStreamDownload() {
      // The muxer reads elementary streams out of the TS decoder; a program stream download
      // would hand it nothing and it would write an empty file. The GUI greys the task out,
      // but auto mode and the web server build their specs without it.
      Hashtable<String,Object> specs = download("Ghosts - Gate-gate.TiVo");
      specs.put("TSDownload", Integer.valueOf(0));
      specs.put("encode", Boolean.TRUE);
      specs.put("encodeName", "mkv_copy");
      jobMonitor.LaunchJobs(specs);

      assertQueued("javadownload");
   }

   @SuppressWarnings("unchecked")
   private static Hashtable<String,String> entryOf(Hashtable<String,Object> specs) {
      return (Hashtable<String,String>)specs.get("entry");
   }

   // ---- metadata sidecars -------------------------------------------------

   // videoFilesToProcess turns the metadata_files setting into the list of .txt files to write.
   // Private, and the only way to reach the "all" and filter branches without building a task
   // set per case, so reflect on it the way ConfigIniRoundTripTest reaches parseIni.
   @SuppressWarnings("unchecked")
   private static Stack<String> sidecars(String mode, boolean decrypt, boolean comcut,
         boolean encode, String filter, String startFile, String videoFile, String tivoFile,
         String mpegFile, String mpegFile_cut, String encodeFile, String encodeFile2)
         throws Exception {
      Method m = jobMonitor.class.getDeclaredMethod("videoFilesToProcess",
         String.class, Boolean.class, Boolean.class, Boolean.class, String.class,
         String.class, String.class, String.class, String.class, String.class,
         String.class, String.class, String.class);
      m.setAccessible(true);
      return (Stack<String>)m.invoke(null, mode, decrypt, comcut, encode, filter,
         startFile, videoFile, tivoFile, mpegFile, mpegFile_cut, encodeFile, encodeFile2, ".txt");
   }

   @Test
   void metadataFilesLastFollowsTheEndOfTheTaskSet() throws Exception {
      assertEquals("E.mp4.txt", sidecars("Download", true, true, true, "last",
         "S", "V", "T", "M", "C", "E.mp4", null).get(0));
      assertEquals("C.txt", sidecars("Download", true, true, false, "last",
         "S", "C", "T", "M", "C", "E.mp4", null).get(0));
      assertEquals("T.txt", sidecars("Download", false, false, false, "last",
         "S", "V", "T", "M", "C", "E.mp4", null).get(0));
   }

   @Test
   void metadataFilesAllWritesOneSidecarPerFileAndNoDuplicates() throws Exception {
      Stack<String> all = sidecars("Download", true, true, true, "all",
         "S", "V", "T", "M", "C", "E.mp4", null);
      assertEquals(4, all.size());
      assertTrue(all.contains("T.txt") && all.contains("M.txt")
             && all.contains("C.txt") && all.contains("E.mp4.txt"));

      // A streaming download passes the encode output as the mpeg because no .ts is written,
      // so the two names coincide and the sidecar must not be queued twice - the second job
      // would be dropped as a duplicate anyway, but only by luck.
      Stack<String> streamed = sidecars("Download", true, false, true, "all",
         "S", "V", "T", "E.mkv", "C", "E.mkv", null);
      assertEquals(2, streamed.size(), "duplicate sidecar: " + streamed);
   }

   @Test
   void metadataForATaskSetThatProducesNothingItAsksForIsSkipped() throws Exception {
      // mpegFile is only an output if something decrypts. Asking for a sidecar next to it when
      // nothing will write it has to come back empty, not name a file that never appears.
      assertTrue(sidecars("Download", false, false, false, "mpegFile",
         "S", "V", "T", "M", "C", "E.mp4", null).isEmpty());
   }

   @Test
   void metadataSidecarsAreQueuedNextToEachOutputFile() {
      config.metadata_files = "all";
      Hashtable<String,Object> specs = download("Ghosts - Gate-gate.TiVo");
      specs.put("metadata", Boolean.TRUE);
      specs.put("decrypt", Boolean.TRUE);
      jobMonitor.LaunchJobs(specs);

      Stack<String> meta = new Stack<String>();
      for (jobData job : jobMonitor.JOBS)
         if (job.type.equals("javametadata")) meta.add(job.metaFile);
      assertEquals(2, meta.size(), "expected a sidecar for the .TiVo and the decrypted file");
      assertTrue(meta.get(0).endsWith(".TiVo.txt"), meta.get(0));
      // A TS download decrypts to .ts, so the second sidecar has to follow that name and not
      // the .mpg a program stream download would have produced
      assertTrue(meta.get(1).endsWith(".ts.txt"), meta.get(1));
   }

   @Test
   void aSidecarThatIsAlreadyThereIsNotWrittenAgain() throws Exception {
      // Overwrite Files off means the user's edited metadata stays.
      config.metadata_files = "tivoFile";
      Files.write(Path.of(config.outputDir, "Ghosts - Gate-gate.TiVo.txt"),
         "edited by hand".getBytes(StandardCharsets.UTF_8));
      Hashtable<String,Object> specs = download("Ghosts - Gate-gate.TiVo");
      specs.put("metadata", Boolean.TRUE);
      jobMonitor.LaunchJobs(specs);

      assertNull(find("javametadata"));
   }
}
