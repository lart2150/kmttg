package com.tivo.kmttg.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Guards the rule that decides whether a fused download writes the decrypted transport stream
// at all. Getting it wrong in one direction wastes a full sized .ts on every recording; in the
// other it takes away a file a later job is about to read, which fails long after the download
// and looks like an unrelated bug.
public class StreamToMuxTest {

   private int savedRemoveMpeg;

   @AfterEach
   void restoreConfig() {
      // config is process-wide static and gradle runs the source set in one JVM.
      config.RemoveMpegFile = savedRemoveMpeg;
   }

   @BeforeEach
   void setUp() {
      savedRemoveMpeg = config.RemoveMpegFile;
      config.RemoveMpegFile = 0;
   }

   // The happy case: MKV asked for, nothing else in the task set.
   private Boolean nothingElse() {
      return jobMonitor.canStreamToMux(false, false, false, false, false, false, false);
   }

   @Test
   void streamsWhenTheMuxerIsTheOnlyConsumer() {
      assertTrue(nothingElse());
   }

   @Test
   void keepsTheStreamWhenTheUserAskedForIt() {
      // Decrypt checked by hand means the .ts is an output, not an intermediate. This is the
      // whole reason the decision reads the checkbox rather than the flag LaunchJobs derives,
      // which is true for everyone by the time the jobs are built.
      assertFalse(jobMonitor.canStreamToMux(true, false, false, false, false, false, false));
   }

   @Test
   void keepsTheStreamForEveryLaterReaderOfTheMpeg() {
      // comskip, ffcut/adcut, qsfix, ccextractor and the custom command all open mpegFile, and
      // a second encoding profile encodes from it.
      assertFalse(jobMonitor.canStreamToMux(false, true, false, false, false, false, false), "comskip");
      assertFalse(jobMonitor.canStreamToMux(false, false, true, false, false, false, false), "comcut");
      assertFalse(jobMonitor.canStreamToMux(false, false, false, true, false, false, false), "qsfix");
      assertFalse(jobMonitor.canStreamToMux(false, false, false, false, true, false, false), "captions");
      assertFalse(jobMonitor.canStreamToMux(false, false, false, false, false, true, false), "custom");
      assertFalse(jobMonitor.canStreamToMux(false, false, false, false, false, false, true), "2nd profile");
   }

   @Test
   void removeMpegFileMeansTheStreamWasNeverWanted() {
      // The combination almost every existing user has: Decrypt checked (it is the default, and
      // the MKV task used to lock it on) plus "Remove .mpg file". Treating the checkbox as a
      // request there would write a full sized .ts and immediately delete it, and - because
      // only a remux job knows how to do that deletion - would also split the work back into
      // two jobs. Both of which is exactly what it did.
      config.RemoveMpegFile = 1;
      assertFalse(jobMonitor.mpegIsAnOutput(true), "RemoveMpegFile=1 outranks the checkbox");
      assertTrue(jobMonitor.canStreamToMux(jobMonitor.mpegIsAnOutput(true),
            false, false, false, false, false, false));

      config.RemoveMpegFile = 0;
      assertTrue(jobMonitor.mpegIsAnOutput(true), "otherwise the checkbox is a request");
      assertFalse(jobMonitor.canStreamToMux(jobMonitor.mpegIsAnOutput(true),
            false, false, false, false, false, false));
   }

   @Test
   void removeMpegFileDoesNotOverrideALaterReader() {
      // comskip still needs the file on disk; something has to write it and then delete it.
      config.RemoveMpegFile = 1;
      assertFalse(jobMonitor.canStreamToMux(jobMonitor.mpegIsAnOutput(true),
            true, false, false, false, false, false));
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
