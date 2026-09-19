package com.tivo.kmttg.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;

// End to end cover for the remux task: the job lifecycle, the .part staging file and the
// rename on success. The mux package has its own unit tests and the corpus exercises the
// parsing, but nothing else runs launchJob/check to completion, and the staging rename is
// exactly the kind of thing that works in isolation and not in the task.
//
// The fixture is a three second 320x240 MPEG-2 plus AC-3 transport stream, 226 KB, so this
// test needs neither a .TiVo file nor the 32 GB corpus.
public class RemuxTaskTest {

   @TempDir
   Path work;

   private Path input;
   private Path output;

   private boolean savedGui;
   private int savedOverwrite, savedDiskSpace, savedRemoveMpeg;
   private boolean savedPersist;

   @AfterEach
   void restoreConfig() {
      // Same reason as BuiltinMuxProfileTest: these are static and shared across the JVM.
      config.GUIMODE = savedGui;
      config.OverwriteFiles = savedOverwrite;
      config.CheckDiskSpace = savedDiskSpace;
      config.RemoveMpegFile = savedRemoveMpeg;
      config.persistQueue = savedPersist;
   }

   @BeforeEach
   void setUp() throws Exception {
      savedGui = config.GUIMODE;
      savedOverwrite = config.OverwriteFiles;
      savedDiskSpace = config.CheckDiskSpace;
      savedRemoveMpeg = config.RemoveMpegFile;
      savedPersist = config.persistQueue;
      input = work.resolve("sample.ts");
      try (InputStream in = getClass().getResourceAsStream("/fixtures/mux_sample.ts")) {
         Files.copy(in, input, StandardCopyOption.REPLACE_EXISTING);
      }
      output = work.resolve("out.mkv");

      config.GUIMODE = false;
      config.persistQueue = false;
      config.OverwriteFiles = 1;
      config.CheckDiskSpace = 0;
      // Off by default here; removesTheSourceWhenConfigured covers the other way.
      config.RemoveMpegFile = 0;
   }

   private jobData newJob() {
      jobData job = new jobData();
      job.type         = "remux";
      job.name         = "mkv_copy";
      job.encodeName   = "mkv_copy";
      job.job_name     = "remux-test";
      job.mpegFile     = input.toString();
      job.mpegFile_cut = work.resolve("no_such_cut.ts").toString();
      job.encodeFile   = output.toString();
      return job;
   }

   // Drives the task the way jobMonitor does, rather than reaching inside it.
   private void runToCompletion(remux task) throws Exception {
      assertTrue(task.launchJob(), "launchJob should schedule the job");
      for (int i = 0; i < 600 && task.check(); i++) {
         Thread.sleep(50);
      }
   }

   @Test
   void remuxesToMkvAndRenamesTheStagingFile() throws Exception {
      remux task = new remux(newJob());
      runToCompletion(task);

      assertTrue(Files.exists(output), "the .mkv should exist");
      assertTrue(Files.size(output) > 100_000, "output looks too small to hold the fixture");
      // Written straight to its final name - no staging file to clean up.
      assertFalse(Files.exists(work.resolve("out.mkv.part")));
   }

   @Test
   void outputIsAMatroskaFile() throws Exception {
      remux task = new remux(newJob());
      runToCompletion(task);

      byte[] head = new byte[4];
      try (InputStream in = Files.newInputStream(output)) {
         assertEquals(4, in.read(head));
      }
      // EBML magic. Getting this wrong produces a file players reject outright.
      assertEquals(0x1A, head[0] & 0xFF);
      assertEquals(0x45, head[1] & 0xFF);
      assertEquals(0xDF, head[2] & 0xFF);
      assertEquals(0xA3, head[3] & 0xFF);
   }

   @Test
   void missingInputFailsWithoutLeavingAFile() throws Exception {
      jobData job = newJob();
      job.mpegFile = work.resolve("absent.ts").toString();

      remux task = new remux(job);
      assertFalse(task.launchJob(), "a missing source must not schedule");
      assertFalse(Files.exists(output), "a refused job must leave nothing behind");
   }

   @Test
   void removesTheSourceWhenConfigured() throws Exception {
      // encode.java deletes the intermediate after a successful encode. Without the same
      // here every MKV job left a full size .ts on disk forever.
      config.RemoveMpegFile = 1;
      remux task = new remux(newJob());
      runToCompletion(task);

      assertTrue(Files.exists(output));
      assertFalse(Files.exists(input), "the decrypted source should have been removed");
   }

   @Test
   void keepsTheSourceWhenAnotherEncodeStillNeedsIt() throws Exception {
      config.RemoveMpegFile = 1;
      jobData job = newJob();
      job.hasMoreEncodingJobs = true;

      remux task = new remux(job);
      runToCompletion(task);

      assertTrue(Files.exists(output));
      assertTrue(Files.exists(input),
         "a second encode job works off the same source, so it must survive");
   }

   @Test
   void theCutFileWinsWhenItExists() throws Exception {
      // encode.java prefers mpegFile_cut over mpegFile, and remux has to match that or a job
      // run after commercial removal would silently remux the uncut source.
      Path cut = work.resolve("sample_cut.ts");
      Files.copy(input, cut, StandardCopyOption.REPLACE_EXISTING);

      jobData job = newJob();
      job.mpegFile_cut = cut.toString();

      remux task = new remux(job);
      runToCompletion(task);

      assertEquals(cut.toString(), job.inputFile, "the cut file should have been chosen");
      assertTrue(Files.exists(output));
   }
}
