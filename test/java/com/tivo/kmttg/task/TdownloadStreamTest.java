package com.tivo.kmttg.task;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;

// A streaming download writes no .ts, so every check the task makes about "the file" has to be
// made against the MKV. The overwrite guard is the one that matters most: judged by mpegFile it
// never fires, and OverwriteFiles=0 silently stops protecting an MKV that is already there.
public class TdownloadStreamTest {

   @TempDir
   Path work;

   private boolean savedGui;
   private int savedOverwrite, savedDiskSpace;

   @AfterEach
   void restoreConfig() {
      // Same reason as the other task tests: these are static and shared across the JVM.
      config.GUIMODE = savedGui;
      config.OverwriteFiles = savedOverwrite;
      config.CheckDiskSpace = savedDiskSpace;
   }

   @BeforeEach
   void setUp() {
      savedGui = config.GUIMODE;
      savedOverwrite = config.OverwriteFiles;
      savedDiskSpace = config.CheckDiskSpace;
      config.GUIMODE = false;
      config.CheckDiskSpace = 0;
      config.OverwriteFiles = 0;
   }

   private jobData newJob() {
      jobData job = new jobData();
      job.type       = "tdownload_decrypt";
      job.name       = "java";
      job.job_name   = "stream-test";
      job.mpegFile   = work.resolve("Show.ts").toString();
      job.muxFile    = work.resolve("Show.mkv").toString();
      job.encodeName = "mkv_copy";
      return job;
   }

   @Test
   void anExistingMkvStopsAStreamingDownload() throws Exception {
      Files.write(work.resolve("Show.mkv"), new byte[] { 1, 2, 3 });
      jobData job = newJob();
      job.muxOnly = true;

      // No .ts exists and none ever will, so a guard reading mpegFile would schedule the
      // download and overwrite the MKV that OverwriteFiles=0 exists to protect.
      assertFalse(new tdownload_decrypt(job).launchJob());
   }

   @Test
   void anExistingMpegStillStopsAnOrdinaryDownload() throws Exception {
      Files.write(work.resolve("Show.ts"), new byte[] { 1, 2, 3 });

      assertFalse(new tdownload_decrypt(newJob()).launchJob());
   }
}
