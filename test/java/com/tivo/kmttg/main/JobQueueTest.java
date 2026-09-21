package com.tivo.kmttg.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Stack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// The bookkeeping jobMonitor does around the queue: what makes two jobs the same job, what
// order the jobs for one recording run in, and what has to come out of the queue when one of
// them is cancelled. None of it needs a TiVo or a spawned process, and all of it decides
// whether a user's task set produces the file they asked for or half of it.
public class JobQueueTest {

   @TempDir Path dir;

   private Stack<jobData> savedJobs;
   private int savedMaxJobs, savedLowSpace, savedNoJobWait, savedCheckSpace;
   // Cancelling a job logs. Pin logging to stdout so no auto.log handler is opened.
   private boolean savedStartingUp;

   @BeforeEach
   void emptyTheQueue() {
      savedStartingUp = kmttg._startingUp;
      kmttg._startingUp = true;
      savedJobs = jobMonitor.JOBS;
      savedMaxJobs = config.MaxJobs;
      savedLowSpace = config.LowSpaceSize;
      savedCheckSpace = config.CheckDiskSpace;
      savedNoJobWait = autoConfig.noJobWait;
      jobMonitor.JOBS = new Stack<jobData>();
   }

   @AfterEach
   void restore() {
      kmttg._startingUp = savedStartingUp;
      jobMonitor.JOBS = savedJobs;
      config.MaxJobs = savedMaxJobs;
      config.LowSpaceSize = savedLowSpace;
      config.CheckDiskSpace = savedCheckSpace;
      autoConfig.noJobWait = savedNoJobWait;
   }

   // A job as LaunchJobs would build it: everything for one recording shares a source, and the
   // per type output file is what the duplicate check compares.
   private static jobData submit(String type, String source, String output) {
      jobData job = new jobData();
      job.type = type;
      job.name = type;
      job.tivoName = "Bolt";
      job.source = source;
      job.tivoFile = output + ".TiVo";
      job.mpegFile = output + ".mpg";
      job.mpegFile_cut = output + "_cut.mpg";
      job.mpegFile_fix = output + ".mpg.qsfix";
      job.edlFile = output + ".edl";
      job.srtFile = output + ".srt";
      job.metaFile = output + ".mpg.txt";
      job.encodeFile = output + ".mkv";
      jobMonitor.submitNewJob(job);
      return job;
   }

   private static jobData find(String type) {
      for (jobData job : jobMonitor.JOBS)
         if (job.type.equals(type)) return job;
      return null;
   }

   private static int major(jobData job) {
      return (int)(float)job.familyId;
   }

   private static Stack<String> types() {
      Stack<String> o = new Stack<String>();
      for (jobData job : jobMonitor.JOBS)
         o.add(job.type);
      return o;
   }

   // ---- family ids --------------------------------------------------------

   @Test
   void oneRecordingsJobsShareAFamilyAndRunInTaskOrder() {
      String source = "http://tivo/TiVoVideoDetails?id=1";
      submit("tdownload_decrypt", source, "C:\\video\\A");
      submit("comskip", source, "C:\\video\\A");
      submit("ffcut", source, "C:\\video\\A");
      submit("encode", source, "C:\\video\\A");

      int family = major(find("comskip"));
      for (jobData job : jobMonitor.JOBS)
         assertEquals(family, major(job), job.type + " landed in another family");
      // The minor id is the position in jobData.allTaskNames(), and the monitor holds a job
      // back while anything lower in its family is still queued - so this ordering is the
      // whole dependency mechanism.
      assertTrue(find("tdownload_decrypt").familyId < find("comskip").familyId);
      assertTrue(find("comskip").familyId < find("ffcut").familyId);
      assertTrue(find("ffcut").familyId < find("encode").familyId);
   }

   @Test
   void twoRecordingsDoNotHoldEachOtherUp() {
      submit("tdownload_decrypt", "http://tivo/detail?id=1", "C:\\video\\A");
      submit("tdownload_decrypt", "http://tivo/detail?id=2", "C:\\video\\B");

      assertEquals(2, jobMonitor.JOBS.size());
      assertFalse(major(jobMonitor.JOBS.get(0)) == major(jobMonitor.JOBS.get(1)),
         "two different recordings were put in one family, so one would wait for the other");
   }

   @Test
   void theSameTaskOnTheSameRecordingIsNotQueuedTwice() {
      // Selecting a show twice in the NPL, or an auto run coming round again before the first
      // finished, must not start a second decrypt over the same output file.
      String source = "http://tivo/detail?id=1";
      submit("tivolibre", source, "C:\\video\\A");
      submit("tivolibre", source, "C:\\video\\A");

      assertEquals(1, jobMonitor.JOBS.size());
   }

   @Test
   void theSameOutputFileFromAnotherTivoIsNotQueuedTwice() {
      // The same episode recorded on two TiVos has different sources but one output file, and
      // two jobs writing it would race.
      submit("tivolibre", "http://bolt/detail?id=1", "C:\\video\\A");
      submit("tivolibre", "http://roamio/detail?id=9", "C:\\video\\A");

      assertEquals(1, jobMonitor.JOBS.size());
   }

   @Test
   void metadataAndPushJobsAreAllowedMoreThanOncePerRecording() {
      // metadata_files=all writes a sidecar next to each video file the task set produces, so
      // several of these for one recording is the normal case, not a duplicate.
      String source = "http://tivo/detail?id=1";
      jobData first = submit("javametadata", source, "C:\\video\\A");
      first.metaFile = "C:\\video\\A.mpg.txt";
      jobData second = new jobData();
      second.type = "javametadata";
      second.name = "java";
      second.tivoName = "Bolt";
      second.source = source;
      second.metaFile = "C:\\video\\A.mkv.txt";
      jobMonitor.submitNewJob(second);

      assertEquals(2, jobMonitor.JOBS.size(), "the second sidecar was dropped as a duplicate");
      assertEquals(major(first), major(second), "both sidecars belong to the one recording");
   }

   @Test
   void aJobTypeMissingFromTheTaskListDoesNotThrow() {
      // allTaskNames drives the minor id by position. A type that is not in it used to walk
      // off the end of the array; the job loses its ordering but the queue has to survive.
      jobData job = new jobData();
      job.type = "notATask";
      job.name = "notATask";
      job.tivoName = "Bolt";
      job.source = "http://tivo/detail?id=1";
      job.mpegFile = "C:\\video\\A.mpg";
      assertTrue(jobMonitor.assignFamilyId(job));
      assertNotNull(job.familyId);
   }

   // ---- ordering gate -----------------------------------------------------

   // priorInFamilyExist is the rule that keeps a task set in order. It is private, so reach it
   // the way ConfigIniRoundTripTest reaches parseIni - there is no public seam, and driving it
   // through monitor() would launch real jobs.
   private static boolean priorInFamily(float familyId, float... others) throws Exception {
      Method m = jobMonitor.class.getDeclaredMethod(
         "priorInFamilyExist", float.class, Stack.class);
      m.setAccessible(true);
      Stack<Float> famList = new Stack<Float>();
      for (float f : others) famList.add(Float.valueOf(f));
      return ((Boolean)m.invoke(null, Float.valueOf(familyId), famList)).booleanValue();
   }

   @Test
   void aJobWaitsForTheEarlierTasksOnItsOwnRecording() throws Exception {
      // 3.11 is the decrypt, 3.15 the ad detect: the ad detect reads what the decrypt writes.
      assertTrue(priorInFamily(3.15f, 3.11f, 3.15f));
      assertFalse(priorInFamily(3.11f, 3.11f, 3.15f));
   }

   @Test
   void aJobIsNotHeldUpByAnotherRecordingsTasks() throws Exception {
      assertFalse(priorInFamily(3.15f, 4.11f, 5.11f));
   }

   @Test
   void twoJobsAtTheSamePositionDoNotBlockEachOther() throws Exception {
      // metadata_files=all queues several javametadata jobs with identical family ids; a
      // "<=" here instead of "<" would deadlock them against each other.
      assertFalse(priorInFamily(3.02f, 3.02f, 3.02f));
   }

   // ---- cancelling --------------------------------------------------------

   @Test
   void cancellingAJobTakesTheTasksThatDependOnItWithIt() {
      String source = "http://tivo/detail?id=1";
      submit("tdownload_decrypt", source, "C:\\video\\A");
      submit("comskip", source, "C:\\video\\A");
      submit("ffcut", source, "C:\\video\\A");
      submit("encode", source, "C:\\video\\A");
      submit("tivolibre", "http://tivo/detail?id=2", "C:\\video\\B");

      jobMonitor.kill(find("comskip"));

      // The download already ran or is running, so it stays; everything downstream of the ad
      // detect would have operated on a cut file that is never going to exist.
      Stack<String> left = new Stack<String>();
      left.add("tdownload_decrypt");
      left.add("tivolibre");
      assertEquals(left, types(), "cancel took out the wrong jobs");
   }

   @Test
   void cancellingTheLastJobLeavesTheRestOfItsFamilyAlone() {
      String source = "http://tivo/detail?id=1";
      submit("tdownload_decrypt", source, "C:\\video\\A");
      submit("comskip", source, "C:\\video\\A");

      jobMonitor.kill(find("comskip"));

      assertEquals(1, jobMonitor.JOBS.size());
      assertEquals("tdownload_decrypt", jobMonitor.JOBS.get(0).type);
   }

   @Test
   void getJobInFamilyFindsTheSiblingAndNotAnotherRecordingsCopy() {
      String source = "http://tivo/detail?id=1";
      jobData download = submit("tdownload_decrypt", source, "C:\\video\\A");
      jobData encode = submit("encode", source, "C:\\video\\A");
      submit("encode", "http://tivo/detail?id=2", "C:\\video\\B");

      assertSame(encode, jobMonitor.getJobInFamily(download, "encode"));
      assertNull(jobMonitor.getJobInFamily(download, "captions"));
   }

   // ---- pending job updates ----------------------------------------------

   @Test
   void renamingAnOutputFileOnlyTouchesTheRecordingItBelongsTo() {
      // A download that has to change its file name - a collision, or the TiVo reporting a
      // different title - has to carry the new name into the tasks queued behind it.
      String source = "http://tivo/detail?id=1";
      jobData download = submit("tdownload_decrypt", source, "C:\\video\\A");
      jobData comskipJob = submit("comskip", source, "C:\\video\\A");
      jobData other = submit("comskip", "http://tivo/detail?id=2", "C:\\video\\B");

      jobMonitor.updatePendingJobFieldValue(download, "mpegFile", "C:\\video\\A (2).mpg");

      assertEquals("C:\\video\\A (2).mpg", comskipJob.mpegFile);
      assertEquals("C:\\video\\B.mpg", other.mpegFile, "another recording was renamed too");
   }

   @Test
   void renamingSkipsAJobAlreadyRunningAndAFieldThatWasNeverSet() {
      String source = "http://tivo/detail?id=1";
      jobData download = submit("tdownload_decrypt", source, "C:\\video\\A");
      jobData running = submit("comskip", source, "C:\\video\\A");
      running.status = "running";
      running.vprjFile = null;

      jobMonitor.updatePendingJobFieldValue(download, "mpegFile", "C:\\video\\A (2).mpg");
      jobMonitor.updatePendingJobFieldValue(download, "vprjFile", "C:\\video\\A (2).VPrj");

      assertEquals("C:\\video\\A.mpg", running.mpegFile, "a running job was renamed under it");
      assertNull(running.vprjFile, "a field the job does not use was filled in");
   }

   // ---- what holds the MaxJobs slot ---------------------------------------

   @Test
   void onlyTheCpuBoundTasksCountAgainstMaxJobs() {
      // MaxJobs limits work that pins a core. A download is network bound, and the tivo.com
      // round trips are neither, so counting them would idle the machine.
      for (String type : new String[] {"comskip", "encode", "remux", "ffcut", "captions"})
         assertTrue(jobMonitor.isActiveJob(typed(type)), type + " should count against MaxJobs");
      for (String type : new String[] {"javadownload", "jdownload_decrypt", "tdownload_decrypt",
                                       "metadata", "javametadata", "remote", "skipfetch",
                                       "autotune", "atomic", "vrdreview", "slingbox"})
         assertFalse(jobMonitor.isActiveJob(typed(type)), type + " should not count");
   }

   private static jobData typed(String type) {
      jobData job = new jobData();
      job.type = type;
      return job;
   }

   // oneJobAtATime is the per TiVo serialisation rule - the TiVo itself only serves one of
   // these at a time, so it is not a kmttg preference.
   private static boolean oneAtATime(String type) throws Exception {
      Method m = jobMonitor.class.getDeclaredMethod("oneJobAtATime", String.class);
      m.setAccessible(true);
      return ((Boolean)m.invoke(null, type)).booleanValue();
   }

   @Test
   void everyTaskThatPullsFromTheTivoIsOnePerTivo() throws Exception {
      for (String type : new String[] {"javadownload", "jdownload_decrypt", "tdownload_decrypt",
                                       "metadata", "javametadata", "metadataTivo", "push"})
         assertTrue(oneAtATime(type), type + " reads from the TiVo and must be serialised");
      for (String type : new String[] {"comskip", "encode", "remux", "captions"})
         assertFalse(oneAtATime(type), type + " does not touch the TiVo");
   }

   // ---- disk space --------------------------------------------------------

   @Test
   void spaceForRunningDownloadsIsWhatIsLeftToFetch() throws Exception {
      Path part = dir.resolve("A.TiVo");
      Files.write(part, new byte[1024]);
      jobData running = submit("tdownload_decrypt", "http://tivo/detail?id=1", "C:\\video\\A");
      running.status = "running";
      running.tivoFile = part.toString();
      running.tivoFileSize = 4096L;

      // Only what has not arrived yet is still to be found on disk
      assertEquals(4096 - 1024, jobMonitor.getJobsEstimatedDiskSpace());

      // A queued one has not started, so it is not holding any space yet
      jobData queued = submit("tdownload_decrypt", "http://tivo/detail?id=2", "C:\\video\\B");
      queued.tivoFile = part.toString();
      queued.tivoFileSize = 4096L;
      assertEquals(4096 - 1024, jobMonitor.getJobsEstimatedDiskSpace());
   }

   @Test
   void aDownloadIsRefusedWhenItWouldEatTheFreeSpaceReserve() {
      jobData candidate = new jobData();
      candidate.type = "javadownload";
      candidate.tivoFileSize = 4096L;

      config.LowSpaceSize = 0;
      assertTrue(jobMonitor.checkDiskSpace(dir.toString(), candidate));

      // A reserve larger than the disk: whatever is free, this job cannot fit under it
      config.LowSpaceSize = 1024 * 1024;
      assertFalse(jobMonitor.checkDiskSpace(dir.toString(), candidate));
   }

   @Test
   void aFreeSpaceCheckThatCannotAnswerLetsTheJobThrough() {
      // freeSpace returns 0 for a path that is not a directory. Blocking every job because the
      // check failed would be worse than running out of space.
      jobData candidate = new jobData();
      candidate.type = "javadownload";
      candidate.tivoFileSize = 4096L;
      config.LowSpaceSize = 1024 * 1024;

      assertTrue(jobMonitor.checkDiskSpace(dir.resolve("no such dir").toString(), candidate));
   }

   // ---- auto transfers waiting on the queue -------------------------------

   @Test
   void autoTransfersWaitForTheTivosOwnJobsUnlessToldNotTo() {
      submit("tdownload_decrypt", "http://tivo/detail?id=1", "C:\\video\\A");

      autoConfig.noJobWait = 0;
      assertTrue(jobMonitor.jobsRemain("Bolt"));
      assertFalse(jobMonitor.jobsRemain("Roamio"));
      assertTrue(jobMonitor.waitForJobs("Bolt"));

      // noJobWait means the next NPL fetch goes out while the last round is still downloading
      autoConfig.noJobWait = 1;
      assertFalse(jobMonitor.waitForJobs("Bolt"));
   }

   @Test
   void elapsedTimeAndRateAreReportedForTheJobMonitorColumns() {
      long start = System.currentTimeMillis() - 7325 * 1000L;
      assertEquals("2:02:05", jobMonitor.getElapsedTime(start));
      assertEquals("0.00 Mbps", jobMonitor.getRate(0, System.currentTimeMillis()));
   }

   @Test
   void createSubFoldersMakesThePathTheJobIsAboutToWriteTo() {
      Path target = dir.resolve("Ghosts").resolve("Season 3").resolve("Gate-gate.mpg");
      config.CheckDiskSpace = 0;

      assertTrue(jobMonitor.createSubFolders(target.toString(), null));
      assertTrue(Files.isDirectory(target.getParent()));
      // Already there is not a failure - the second episode of the season lands here too
      assertTrue(jobMonitor.createSubFolders(target.toString(), null));
   }

   @Test
   void jobMonitorRowsReportTheFileEachTaskWrites() {
      // getOutputFile is both the job monitor's OUTPUT column and the duplicate check above,
      // so a task naming the wrong file silently stops being deduplicated.
      String[][] expected = {
         {"javadownload", "C:\\video\\A.TiVo"}, {"tivolibre", "C:\\video\\A.mpg"},
         {"comskip", "C:\\video\\A.edl"}, {"ffcut", "C:\\video\\A_cut.mpg"},
         {"captions", "C:\\video\\A.srt"}, {"encode", "C:\\video\\A.mkv"},
         {"remux", "C:\\video\\A.mkv"}, {"javametadata", "C:\\video\\A.mpg.txt"},
         {"qsfix", "C:\\video\\A.mpg.qsfix"},
      };
      for (String[] row : expected) {
         jobData job = new jobData();
         job.type = row[0];
         job.tivoName = "Bolt";
         job.tivoFile = "C:\\video\\A.TiVo";
         job.mpegFile = "C:\\video\\A.mpg";
         job.mpegFile_cut = "C:\\video\\A_cut.mpg";
         job.mpegFile_fix = "C:\\video\\A.mpg.qsfix";
         job.edlFile = "C:\\video\\A.edl";
         job.srtFile = "C:\\video\\A.srt";
         job.metaFile = "C:\\video\\A.mpg.txt";
         job.encodeFile = "C:\\video\\A.mkv";
         assertEquals(row[1], job.getOutputFile(), row[0]);
      }
   }
}
