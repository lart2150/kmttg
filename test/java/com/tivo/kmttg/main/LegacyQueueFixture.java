package com.tivo.kmttg.main;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;
import java.util.Stack;

/**
 * Writes test/resources/fixtures/queue_pre_autoskip.dat - a jobData.dat as an older kmttg
 * saved it, for JobQueuePersistenceTest. NOT part of the test suite; run it only to
 * regenerate the fixture:
 *
 *   gradlew compileTestJava
 *   java -cp "build/classes/java/test;build/classes/java/main" ^
 *        com.tivo.kmttg.main.LegacyQueueFixture ^
 *        test/resources/fixtures/queue_pre_autoskip.dat
 *
 * Re-saving the fixture through today's jobData would prove nothing: the stream would carry
 * every field the class has, which is exactly the case that works. What breaks is a stream
 * whose class descriptor is missing fields, so the shape below is jobData as it stood before
 * the AutoSkip block was added, serialized under jobData's name.
 */
public class LegacyQueueFixture {

   // Field names and types have to match jobData's exactly or the read fails outright with
   // InvalidClassException instead of the silent null this is here to demonstrate.
   public static class LegacyJob implements Serializable {
      private static final long serialVersionUID = 1L;
      public String  startFile = null;
      public String  source = null;
      public Integer monitor = null;
      public Long    time = null;
      public String  status = null;
      public String  tivoName = null;
      public String  type = null;
      public String  name = null;
      public Float   familyId = null;
      public String  job_name = null;
      public String  ip = null;
      public String  inputFile = null;
      public String  tivoFile = null;
      public String  mpegFile = null;
      public String  mpegFile_fix = null;
      public String  mpegFile_cut = null;
      public String  srtFile = null;
      public String  edlFile = null;
      public String  vprjFile = null;
      public String  videoFile = null;
      public String  encodeName = null;
      public String  encodeFile = null;
      public String  customCommand = "";
      public Long    duration = null;
      public Long    launch_time = null;
      public int     launch_tries = 1;
      public String  url = null;
      public String  metaTmpFile = null;
      public String  metaFile = null;
      public String  episodeNumber = null;
      public String  displayMajorNumber = null;
      public String  callsign = null;
      public String  seriesId = null;
      public boolean hasMoreEncodingJobs = false;
      public Long    tivoFileSize = null;
      public String  ProgramId = null;
      String         ProgramId_unique = null;
      public String  title = null;
      public Long    time1 = null, time2 = null, size1 = null, size2 = null;
      public String  rate = "n/a";
      public Boolean getURLs = false;
      public Boolean partiallyViewed = false;
      public Integer download_duration = 0;
      public Integer TSDownload = 0;
      public Boolean twpdelete = false;
      public Boolean rpcdelete = false;
      public Hashtable<String,String> entry = null;
      public String  comskipIni = null;
      public int     autotune_channel_interval = -1;
      public int     autotune_button_interval = -1;
      public String  autotune_chan1 = null;
      public String  autotune_chan2 = null;
      public Boolean remote_todo = false;
      public Boolean remote_upcoming = false;
      public Stack<String> demuxFiles = null;
      public String  qsfix_mode = "qsfix";
   }

   // A decrypt and a comskip for one recording, the pair "Ad Detect" leaves behind when kmttg
   // is closed mid task set. saveQueuedJobs writes a count and then the jobs.
   public static byte[] queue() throws IOException {
      LegacyJob decrypt = new LegacyJob();
      decrypt.type = "tivolibre";
      decrypt.name = "tivolibre";
      decrypt.status = "queued";
      decrypt.tivoName = "Bolt";
      decrypt.source = "http://tivo/TiVoVideoDetails?id=8675309";
      decrypt.job_name = "job2";
      decrypt.familyId = 0.11f;
      decrypt.monitor = -1;
      decrypt.startFile = "Ghosts - Gate-gate.TiVo";
      decrypt.tivoFile = "C:\\video\\Ghosts - Gate-gate.TiVo";
      decrypt.mpegFile = "C:\\video\\Ghosts - Gate-gate.mpg";
      decrypt.mpegFile_cut = "C:\\video\\Ghosts - Gate-gate_cut.mpg";

      LegacyJob comskip = new LegacyJob();
      comskip.type = "comskip";
      comskip.name = "comskip";
      comskip.status = "queued";
      comskip.tivoName = "Bolt";
      comskip.source = decrypt.source;
      comskip.job_name = "job3";
      comskip.familyId = 0.15f;
      comskip.monitor = -1;
      comskip.startFile = decrypt.startFile;
      comskip.mpegFile = decrypt.mpegFile;
      comskip.edlFile = "C:\\video\\Ghosts - Gate-gate.edl";

      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(bytes);
      oos.writeInt(2);
      oos.writeObject(decrypt);
      oos.writeObject(comskip);
      oos.close();
      return rename(bytes.toByteArray(), LegacyJob.class.getName(), jobData.class.getName());
   }

   public static void main(String[] argv) throws IOException {
      FileOutputStream fos = new FileOutputStream(argv[0]);
      fos.write(queue());
      fos.close();
      System.out.println("wrote " + argv[0]);
   }

   // Swap the serialized class name for jobData's, fixing the 2 byte UTF length in front of it
   private static byte[] rename(byte[] raw, String from, String to) throws IOException {
      byte[] f = from.getBytes(StandardCharsets.UTF_8);
      byte[] t = to.getBytes(StandardCharsets.UTF_8);
      ByteArrayOutputStream o = new ByteArrayOutputStream();
      int at = 0;
      while (true) {
         int found = indexOf(raw, f, at);
         if (found < 0) break;
         if (found < 2 || ((raw[found-2] & 0xff) << 8 | (raw[found-1] & 0xff)) != f.length)
            throw new IOException("class name not preceded by its own length");
         o.write(raw, at, found - at - 2);
         o.write(t.length >> 8);
         o.write(t.length & 0xff);
         o.write(t, 0, t.length);
         at = found + f.length;
      }
      if (at == 0) throw new IOException("class name not found in stream");
      o.write(raw, at, raw.length - at);
      return o.toByteArray();
   }

   private static int indexOf(byte[] hay, byte[] needle, int from) {
      outer:
      for (int i = from; i + needle.length <= hay.length; ++i) {
         for (int j = 0; j < needle.length; ++j)
            if (hay[i+j] != needle[j]) continue outer;
         return i;
      }
      return -1;
   }
}
