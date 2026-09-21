package com.tivo.kmttg.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Stack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tivo.kmttg.task.comskip;

// The pending job queue is written to jobData.dat on every change and read back at startup, so
// the file in a user's install dir was written by whatever kmttg they ran last - often an older
// one. Java leaves a field the stream does not carry at its JVM default and never runs the
// field initialiser, which for jobData's boxed flags means null where the code expects false.
// These tests restore a queue the way a user upgrading would, against a fixture serialized from
// an older field set rather than from today's class, which would carry every field and prove
// nothing.
public class JobQueuePersistenceTest {

   @TempDir Path programDir;

   private String savedProgramDir;
   private String savedOs;
   private Stack<jobData> savedJobs;
   // Saving and loading both log. Pin logging to stdout so no auto.log handler is opened.
   private boolean savedStartingUp;

   @BeforeEach
   void redirectProgramDir() {
      savedStartingUp = kmttg._startingUp;
      kmttg._startingUp = true;
      savedProgramDir = config.programDir;
      savedOs = config.OS;
      savedJobs = jobMonitor.JOBS;
      jobMonitor.JOBS = new Stack<jobData>();
      config.programDir = programDir.toString();
      // saveAllJobs shells out to chmod on anything that is not windows
      config.OS = "windows";
   }

   @AfterEach
   void restore() {
      kmttg._startingUp = savedStartingUp;
      jobMonitor.JOBS = savedJobs;
      config.programDir = savedProgramDir;
      config.OS = savedOs;
   }

   private static jobData queued(String type, String source, String output) {
      jobData job = new jobData();
      job.type = type;
      job.name = type;
      job.tivoName = "Bolt";
      job.source = source;
      job.startFile = "Ghosts - Gate-gate.TiVo";
      job.mpegFile = output;
      job.edlFile = output + ".edl";
      job.encodeFile = output + ".mkv";
      job.tivoFile = output + ".TiVo";
      jobMonitor.submitNewJob(job);
      return job;
   }

   private static jobData find(String type) {
      for (jobData job : jobMonitor.JOBS)
         if (job.type.equals(type)) return job;
      return null;
   }

   private void installLegacyQueue() throws Exception {
      try (InputStream is = getClass().getResourceAsStream("/fixtures/queue_pre_autoskip.dat")) {
         assertNotNull(is, "missing fixture: /fixtures/queue_pre_autoskip.dat");
         Files.write(programDir.resolve("jobData.dat"), is.readAllBytes());
      }
   }

   @Test
   void anOlderBuildsQueueFileStillLoads() throws Exception {
      installLegacyQueue();
      jobMonitor.loadQueuedJobs();

      assertEquals(2, jobMonitor.JOBS.size(), "the saved jobs did not come back");
      assertNotNull(find("tivolibre"));
      assertNotNull(find("comskip"));
      // Same source, so the two have to land back in one family in task order
      assertEquals((int)(float)find("tivolibre").familyId, (int)(float)find("comskip").familyId);
      assertTrue(find("tivolibre").familyId < find("comskip").familyId,
         "decrypt must still be scheduled before the ad detect that reads its output");
      assertEquals("C:\\video\\Ghosts - Gate-gate.mpg", find("comskip").mpegFile);
   }

   @Test
   void flagsTheOlderBuildNeverSavedComeBackFalseNotNull() throws Exception {
      // The whole point. autoskip and exportSkip postdate the fixture's field set, so the
      // stream has nothing for them and they arrive null. Every reader unboxes them.
      installLegacyQueue();
      jobMonitor.loadQueuedJobs();

      jobData job = find("comskip");
      assertFalse(job.exportSkip, "exportSkip came back null");
      assertFalse(job.autoskip, "autoskip came back null");
      assertFalse(job.twpdelete);
      assertFalse(job.rpcdelete);
      assertFalse(job.getURLs);
      assertEquals(Integer.valueOf(0), job.TSDownload);
      assertEquals("qsfix", job.qsfix_mode, "a String default is lost the same way");
   }

   // A primitive the older build never wrote comes back as 0, not as the value this class
   // starts it at - and 0 is a meaningful setting for all three of these rather than an
   // obviously unset one. remote_search_max at 0 makes Remote.searchKeywords return nothing
   // (match_count > 0 on the first hit), and an autotune interval of 0 reads as an explicit
   // zero second interval where -1 means "not set".
   @Test
   void settingsTheOlderBuildNeverSavedKeepTheirOwnDefaults() throws Exception {
      installLegacyQueue();
      jobMonitor.loadQueuedJobs();

      jobData job = find("comskip");
      assertEquals(200, job.remote_search_max, "remote_search_max came back zeroed");
      assertEquals(-1, job.autotune_channel_interval);
      assertEquals(-1, job.autotune_button_interval);
      assertEquals(1, job.launch_tries);
   }

   @Test
   void aRestoredJobLaunchesInsteadOfStallingTheQueue() throws Exception {
      // comskip.launchJob() reads job.exportSkip before it touches a file or a process. A null
      // there threw out of jobData.launch and out of jobMonitor.monitor, so the pass ended
      // early every second and nothing queued behind this job ever started again.
      installLegacyQueue();
      jobMonitor.loadQueuedJobs();

      jobData job = find("comskip");
      // config.comskip is not an executable here, so the task declines the job rather than
      // spawning anything - reaching that decision at all is what is being pinned.
      assertFalse(new comskip(job).launchJob(),
         "comskip should decline when the comskip executable is not configured");
   }

   @Test
   void queuedJobsSurviveSaveAndReload() {
      queued("tivolibre", "http://tivo/detail?id=1", "C:\\video\\A");
      queued("comskip", "http://tivo/detail?id=1", "C:\\video\\A");
      jobMonitor.saveQueuedJobs();

      jobMonitor.JOBS = new Stack<jobData>();
      jobMonitor.loadQueuedJobs();

      assertEquals(2, jobMonitor.JOBS.size());
      assertEquals("C:\\video\\A", find("comskip").mpegFile);
      assertEquals("queued", find("comskip").status);
   }

   @Test
   void saveQueuedJobsLeavesOutTheJobsThatCannotBeResumed() {
      // A remote or slingbox job is a live connection to the TiVo, not a file task, so it has
      // nothing to resume into - and a running job is mid stream.
      queued("remote", "Bolt", "Bolt");
      queued("slingbox", "sling", "C:\\video\\S.mpg");
      jobData running = queued("tivolibre", "http://tivo/detail?id=2", "C:\\video\\B");
      running.status = "running";
      queued("comskip", "http://tivo/detail?id=2", "C:\\video\\B");
      jobMonitor.saveQueuedJobs();

      jobMonitor.JOBS = new Stack<jobData>();
      jobMonitor.loadQueuedJobs();

      assertEquals(1, jobMonitor.JOBS.size(), "only the queued file job should come back");
      assertEquals("comskip", jobMonitor.JOBS.get(0).type);
   }

   @Test
   void saveAllJobsBringsARunningJobBackAsQueued() {
      // What the persistQueue setting writes on every queue change: an unclean exit has to be
      // able to restart the download that was in flight, not just the ones behind it.
      jobData running = queued("tivolibre", "http://tivo/detail?id=3", "C:\\video\\C");
      running.status = "running";
      running.process = new comskip(running);
      queued("playlist", null, "Bolt");
      jobMonitor.saveAllJobs();

      jobMonitor.JOBS = new Stack<jobData>();
      jobMonitor.loadAllJobs(0);

      assertEquals(1, jobMonitor.JOBS.size(), "the playlist job should not have been saved");
      assertEquals("tivolibre", jobMonitor.JOBS.get(0).type);
      assertEquals("queued", jobMonitor.JOBS.get(0).status, "it has to start over");
      assertEquals(null, jobMonitor.JOBS.get(0).process,
         "the live task must not be carried into the file");
   }

   @Test
   void loadAllJobsAppendsToAQueueThatIsNotEmpty() {
      // loadQueuedJobs refuses in that case; the persistQueue path has to add to whatever the
      // startup NPL jobs already put in the list.
      queued("comskip", "http://tivo/detail?id=4", "C:\\video\\D");
      jobMonitor.saveAllJobs();

      jobMonitor.JOBS = new Stack<jobData>();
      queued("tivolibre", "http://tivo/detail?id=5", "C:\\video\\E");
      jobMonitor.loadQueuedJobs();
      assertEquals(1, jobMonitor.JOBS.size(), "loadQueuedJobs should have refused");

      jobMonitor.loadAllJobs(0);
      assertEquals(2, jobMonitor.JOBS.size());
   }
}
