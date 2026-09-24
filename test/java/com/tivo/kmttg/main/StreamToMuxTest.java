package com.tivo.kmttg.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

// Guards the rule that decides whether a fused download writes the decrypted transport stream
// at all. Getting it wrong in one direction wastes a full sized .ts on every recording; in the
// other it takes away a file a later job is about to read, which fails long after the download
// and looks like an unrelated bug.
public class StreamToMuxTest {

   @Test
   void streamsWhenTheMuxerIsTheOnlyConsumer() {
      // The Decrypt checkbox is not an input: it is checked by default, so reading it as a
      // request for the .ts wrote a second full size copy next to almost every mkv.
      assertTrue(jobMonitor.canStreamToMux(false, false, false, false, false, false));
   }

   @Test
   void keepsTheStreamForEveryLaterReaderOfTheMpeg() {
      // comskip, ffcut/adcut, qsfix, ccextractor and the custom command all open mpegFile, and
      // a second encoding profile encodes from it.
      assertFalse(jobMonitor.canStreamToMux(true, false, false, false, false, false), "comskip");
      assertFalse(jobMonitor.canStreamToMux(false, true, false, false, false, false), "comcut");
      assertFalse(jobMonitor.canStreamToMux(false, false, true, false, false, false), "qsfix");
      assertFalse(jobMonitor.canStreamToMux(false, false, false, true, false, false), "captions");
      assertFalse(jobMonitor.canStreamToMux(false, false, false, false, true, false), "custom");
      assertFalse(jobMonitor.canStreamToMux(false, false, false, false, false, true), "2nd profile");
   }

   @Test
   void monitorRowNamesTheFileTheJobActuallyWrites() {
      // getOutputFile drives the job monitor's OUTPUT column. A streaming job that reported
      // mpegFile would name a path the user can never find.
      jobData job = new jobData();
      job.type = "tdownload_decrypt";
      job.mpegFile = "C:/tmp/Show.ts";
      job.muxFile = "C:/tmp/Show.mkv";

      assertEquals("C:/tmp/Show.ts", job.getOutputFile(), "a job writing both is named by the .ts");
      job.muxOnly = true;
      assertEquals("C:/tmp/Show.mkv", job.getOutputFile());
   }
}
