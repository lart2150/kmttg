package com.tivo.kmttg.main;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Date;
import java.util.Hashtable;
import java.util.Stack;

import javax.swing.Timer;

import com.tivo.kmttg.gui.gui;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;
import com.tivo.kmttg.task.*;

public class jobMonitor {
   public static Stack<jobData> JOBS = new Stack<jobData>();
   //private static Stack<jobData> JOB_HISTORY = new Stack<jobData>();
   private static int JOB_COUNT = 0;
   private static int JOB = 0;
   private static int FAMILY_ID = 0;
   public static Boolean NoNewJobs = false;
   private static String jobDataFile = "jobData.dat";
   
   // These used for Auto Transfers->Loop in GUI mode
   private static Hashtable<String,Long> launch = new Hashtable<String,Long>();
   private static Long launchTime;
   
   // Master job monitor that runs on timer. Key functions:
   //   * Check status of active job and take action if running job is completed
   //   * Launch a job in queue if appropriate
   //     - Restrict to 1 download per Tivo at a time
   //     - Restrict to 1 VideoRedo job at a time
   //     - Restrict CPU intensive jobs to defined 'MaxJobs' (default of 1)
   //     - Order dependancy of jobs operating on same program   
   static void monitor(gui gui, Timer timer) {
      Stack<jobData> running = new Stack<jobData>();
      Stack<jobData> queued  = new Stack<jobData>();
      jobData job;
      if (config.GUI && gui == null && timer != null) {
         timer.stop();
         return;
      }
      
      // Handle auto transfers GUI run if "Loop in GUI" menu item enabled
      if (config.GUI && config.GUI_LOOP == 1) {
         handleLoopInGUI();
      }
      
      // Check for new tivos
      if (config.UseOldBeacon == 0 && config.jmdns != null)
         config.jmdns.process();
      if (config.UseOldBeacon == 1 && config.tivo_beacon != null)
         tivoBeaconUpdate();
      
      if ( JOBS != null && ! JOBS.isEmpty() ) {
         for (int i=0; i<JOBS.size(); ++i) {
            job = JOBS.get(i);
                        
            // Add running jobs to running list so we can check them
            if ( job.status.equals("running") ) {
               running.add(job);
            }
            
            // Add non playlist queued jobs to queued list so we can decide when to launch
            if ( job.status.equals("queued") && ! job.type.equals("playlist") && ! job.type.equals("javaplaylist")) {
               queued.add(job);
            }
            
            // queued playlist jobs launch right away, other jobs go to queue
            if ( (job.type.equals("playlist") || job.type.equals("javaplaylist")) && job.status.matches("queued") ) {
               if (job.type.equals("playlist")) {
                  job.process_npl = new NowPlaying(job);
                  if (job.process_npl != null) {
                     job.process_npl.job = job; // This is used by npl check
                     if (job.process_npl.start()) {
                        updateJobStatus(job, "running");
                        job.time = new Date().getTime();
                        job.job_name = "playlist" + job.tivoName;
                        running.add(job);
                     } else {
                        // Failed to launch
                        removeFromJobList(job);
                     }
                  } else {
                     // Failed to launch
                     removeFromJobList(job);
                  }
               }
               if (job.type.equals("javaplaylist")) {
                  job.process_javanpl = new javaNowPlaying(job);
                  if (job.process_javanpl != null) {
                     job.process_javanpl.job = job; // This is used by npl check
                     if (job.process_javanpl.start()) {
                        updateJobStatus(job, "running");
                        job.time = new Date().getTime();
                        job.job_name = "javaplaylist" + job.tivoName;
                        running.add(job);
                     } else {
                        // Failed to launch
                        removeFromJobList(job);
                     }
                  } else {
                     // Failed to launch
                     removeFromJobList(job);
                  }
               }
            }
         }
      }
      
      if ( ! running.isEmpty() ) {
         // There are jobs running so check up on them
         for (int i=0; i<running.size(); i++) {
            job = running.get(i);
            job.check();            
         }
      }
      
      // Build list of queued and running job familyIds
      Stack<Float> famList = new Stack<Float>();
      Float d;
      for (int i=0; i<queued.size(); i++) {
         d = (Float)(queued.get(i).familyId);
         famList.add(d);
      }
      for (int i=0; i<running.size(); i++) {
         d = (Float)(running.get(i).familyId);
         famList.add(d);
      }
      
      // Hash to track running downloads per Tivo
      // Also count number of cpu intensive jobs currently running
      // Also count number of COM VideoRedo jobs currently running
      // Also count number of GUI VideoRedo jobs currently running
      int cpuActiveJobs = 0;
      int VideoRedoCOMJobs = 0;
      int VideoRedoGUIJobs = 0;
      Hashtable<String,Integer> tivoDownload = new Hashtable<String,Integer>();
      for (int i=0; i<running.size(); i++) {
         job = running.get(i);
         if ( oneJobAtATime(job.type) ) {
            if ( ! tivoDownload.containsKey(job.tivoName) ) {
               tivoDownload.put(job.tivoName, 0);
            }
            tivoDownload.put(job.tivoName, tivoDownload.get(job.tivoName)+1);
         } else if ( isVideoRedoGUIJob(job) ) {
            // NOTE: VRD GUI job not considered CPU active
            VideoRedoGUIJobs++;
         } else {
            cpuActiveJobs++;
            if ( isVideoRedoCOMJob(job) ) {
               VideoRedoCOMJobs++;
            }
         }
      }
      
      // See if we can launch any queued jobs
      for (int i=0; i<queued.size(); i++) {
         if (NoNewJobs) continue; //Skip all jobs when this flag is set (to facilitate a graceful shutdown)
         
         job = queued.get(i);
         debug.print("job=" + job);
         
         // If there are prior job types in the family queued don't schedule yet
         if ( priorInFamilyExist(job.familyId, famList) ) {
            continue;
         }
         
         // Only 1 download at a time per Tivo allowed
         if (tivoDownload.size() > 0) {
            if ( oneJobAtATime(job.type) ) {
               if ( tivoDownload.containsKey(job.tivoName) ) {
                  if (tivoDownload.get(job.tivoName) > 0) {
                     continue;
                  }
               }
            }
         }
         
         // Don't run more than 'MaxJobs' active jobs at a time
         // NOTE: VRD GUI job not considered CPU active
         if ( ! job.type.equals("download") &&
              ! job.type.equals("javadownload") &&
              ! job.type.equals("metadata") &&
              ! job.type.equals("javametadata") &&
              ! isVideoRedoGUIJob(job)) {
            if (cpuActiveJobs >= config.MaxJobs) continue;
         }
         
         // Don't launch more than one VideoRedo COM job at a time
         if ( isVideoRedoCOMJob(job) ) {
            if (VideoRedoCOMJobs > 0) continue;
         }
         
         // Don't launch more than one VideoRedo GUI job at a time
         if ( isVideoRedoGUIJob(job) ) {
            if (VideoRedoGUIJobs > 0) continue;
         }
         
         // If there is a launch time setup for this job then don't launch until
         // after job.launch_time
         if (job.launch_time != null) {
            long now = new Date().getTime();
            if (now < job.launch_time) continue;
         }

         // OK to launch job
         cpuActiveJobs = jobData.launch(job, cpuActiveJobs);
         
         // Update tivoDownload hash if appropriate
         // (to prevent multiple queued downloads for same tivo to launch at once)
         if ( oneJobAtATime(job.type) ) {
            if (job.status.equals("running")) {
               if ( ! tivoDownload.containsKey(job.tivoName) ) {
                  tivoDownload.put(job.tivoName, 0);
               }
               tivoDownload.put(job.tivoName, tivoDownload.get(job.tivoName)+1);
            }
         }
         
         // Update VideoRedoCOMJobs number
         if ( isVideoRedoCOMJob(job) )
            VideoRedoCOMJobs++;
         
         // Update VideoRedoGUIJobs number
         if ( isVideoRedoGUIJob(job) )
            VideoRedoGUIJobs++;
      }
   }
   
   public static void getNPL(String name) {
      if (config.java_downloads == 0)
         NowPlaying.submitJob(name);
      else
         javaNowPlaying.submitJob(name);
   }
   
   // If true this job can only be run one at a time per TiVo
   private static Boolean oneJobAtATime(String type) {
      return type.equals("download") ||
             type.equals("javadownload") ||
             type.equals("metadata") ||
             type.equals("javametadata") ||
             type.equals("metadataTivo") ||
             type.equals("push");
   }

   // Determine if there are jobs in same family to run before this one
   private static Boolean priorInFamilyExist(float familyId, Stack<Float> famList) {
      debug.print("familyId=" + familyId + " famList=" + famList);
      // Filter out other job families
      int major = (int)familyId;
      Stack<Float> family = new Stack<Float>();
      Float id;
      for(int i=0; i<famList.size(); ++i) {
         id = famList.get(i);
         if ( id != null && id.intValue() == major ) {
            family.add(famList.get(i));
         }
      }
      
      // Any number < familyId => other jobs to run before this one
      Boolean prior = false;
      for(int i=0; i<family.size(); ++i) {
         if ( family.get(i) < familyId ) {
            prior = true;
         }
      }
     
      return prior;
   }

   // Create directory of f if it doesn't already exist
   public static Boolean createSubFolders(String f, jobData job) {
      debug.print("f=" + f);
      String baseDir = string.dirname(f);
      if ( ! file.isDir(baseDir) ) {
         if ( ! new File(baseDir).mkdirs() ) {
            log.error("Failed to create path: " + baseDir);
            return false;
         }
      }

      // Check for sufficient disk space in this dir if check enabled
      if ( config.CheckDiskSpace == 1 ) {
         return checkDiskSpace(baseDir, job);
      }
      
      return true;
   }
   
   public static Boolean checkDiskSpace(String dir, jobData job) {
      debug.print("dir=" + dir);
      // Min available expected (in bytes)
      long min = (long) (config.LowSpaceSize * Math.pow(2, 30));
      
      // Get space available (in bytes)
      long space = file.freeSpace(dir);
      // If 0 then try again just in case...
      if (space == 0)
         space = file.freeSpace(dir);
      // If still 0 then give up and return true
      if (space == 0) {
         log.warn("NOTE: Free space check failed...");
         return true;
      }
      
      // Get estimated space needed for already running jobs
      long runningSpace = getJobsEstimatedDiskSpace();
      
      // Get estimated space for this job candidate
      long candidateSpace = 0;
      if (job.type.equals("download") || job.type.equals("javadownload")) {
         candidateSpace = job.tivoFileSize;
      }
      
      // jobSpace = runningSpace (space for running jobs) + candidateSpace (jobs to be launched)
      long jobSpace = runningSpace + candidateSpace;
      
      if (space-jobSpace <= min) {
         String message = String.format(
            "%s: Space available = %.2f GB - estimated needed = %.2f GB is less than min = %.2f GB",
            dir, space/Math.pow(2,30), jobSpace/Math.pow(2,30), min/Math.pow(2, 30)
         );
         log.error(message);
         return false;
      }
      return true;
   }

   // Return an estimate of disk space required for running jobs
   static long getJobsEstimatedDiskSpace() {
      long total = 0;
      jobData job;
      if ( JOBS != null && ! JOBS.isEmpty() ) {
         for (int i=0; i<JOBS.size(); ++i) {
            job = JOBS.get(i);                        
            // Only considering download jobs for now
            if ( (job.type.equals("download") || job.type.equals("javadownload")) && job.status.equals("running")) {
               // Estimated size - what is already downloaded
               total += job.tivoFileSize - file.size(job.tivoFile);
            }
         }
      }
      return total;
   }
   
   static void addToJobList(jobData job) {
      debug.print("job=" + job);
      // Prevent duplicate jobs from being queued to the job list
      // NOTE: There can be identical shows on different Tivos, so must check output file
      // since source will be different in that case
      for (int i=0; i<JOBS.size(); ++i) {
         if (job.type.equals(JOBS.get(i).type)) {
            if (job.getOutputFile().equals(JOBS.get(i).getOutputFile())) return;
         }
      }      
      
      // Insert job in proper location if same source as existing jobs
      if ( ! assignFamilyId(job) ) return;
      
      // Create unique job name
      job.job_name = "job" + JOB_COUNT;
      JOB_COUNT++;
      
      // Update GUI Job Monitor
      if (config.GUI) {
         String output = "";
         if (! job.type.equals("playlist") && ! job.type.equals("javaplaylist") && ! job.type.equals("custom")) {
            if (config.jobMonitorFullPaths == 1)
               output = job.getOutputFile();
            else
               output = string.basename(job.getOutputFile());
         }
         
         // For encode jobs add profile name before output file name
         if (job.type.equals("encode") || job.type.equals("vrdencode")) {
            output = "(" + job.encodeName + ") " + output;
         }
         
         // For push jobs add push Tivo name
         if (job.type.equals("push")) {
            output = "(" + job.pyTivo_tivo + ") " + output;
         }
         
         config.gui.jobTab_AddJobMonitorRow(job, job.tivoName, output);
      }
      
      // Add job to master job list
      JOBS.add(job);
      updateNPLjobStatus();
   }
   
   public static void removeFromJobList(jobData job) {
      debug.print("job=" + job);
      Stack<jobData> new_jobs = new Stack<jobData>();
      for (int i=0; i<JOBS.size(); ++i) {
         if ( job.job_name != null && JOBS.get(i).job_name != null ) {
            // NOTE: job.job_name surrounded by \Q..\E to escape any special regex chars
            if ( ! JOBS.get(i).job_name.matches("\\Q"+job.job_name+"\\E") ) {
               new_jobs.add(JOBS.get(i));
            }
         }
      }
      JOBS = new_jobs;
      
      // Remove entry from job monitor
      if (config.GUI) {
         config.gui.jobTab_RemoveJobMonitorRow(job); 
         updateNPLjobStatus();
      }
   }
   
   // Change job status
   public static void updateJobStatus(jobData job, String status) {
      job.status = status;
      if (config.GUI) {
         config.gui.jobTab_UpdateJobMonitorRowStatus(job,status);
         updateNPLjobStatus();
      }
   }
   
   public static void submitNewJob(jobData job) {
      debug.print("job=" + job);
      // Add new job request to job list in queued state
      // The job monitor will decide when to actually launch it
      job.monitor  = -1;
      job.status   = "queued";      
      JOB++;            
      addToJobList(job);
   }
   
   // Set job monitor familyId
   // familyId structure is majorId.minorId (majorId is integer, minorId is float)
   // All jobs with same source are considered same family and have same majorId
   // minorId is set based on location in allTaskNames array
   // Jobs in family are designed to execute in lowest to highest minorId order
   public static boolean assignFamilyId(jobData job) {
      debug.print("job=" + job);
      
      // Assume this is a new family at first => new majorId
      int majorId = FAMILY_ID;
      
      // minorId assignment related to taskName location in allTaskNames
      String taskNames[] = jobData.allTaskNames();
      int task_offset = 0;
      if (job.type != null) {
         while (! job.type.equals(taskNames[task_offset])) {
            task_offset++;
         }
      }
      float minorId = (float)0.01*task_offset;

      // Determine if this job has same source as running/queued jobs
      // in which case use existing family majorId
      Boolean sameSource = false;
      String sourceFile = job.source;
      for (int i=0; i<JOBS.size(); ++i) {
         if (sourceFile != null && JOBS.get(i).source != null) {
            if(JOBS.get(i).source.equals(sourceFile)) {
               if (JOBS.get(i).type.equals(job.type)) {
                  // Identical job => do not run this job
                  // NOTE: Types below are allowed to have multiple at a time
                  if (! job.type.equals("push") && ! job.type.equals("metadata") && ! job.type.equals("javametadata") && ! job.type.equals("metadataTivo"))
                     return false;
               }
               sameSource = true;
               // Use existing family majorId
               float major_float = JOBS.get(i).familyId;
               majorId = (int)major_float;
               break;
            }
         }
      }

      job.familyId = majorId + minorId;
      
      if ( ! sameSource ) {
         FAMILY_ID++;
      }
      return true;
   }
   
   public static Boolean isFirstJobInMonitor(jobData job) {
      if ( config.GUI && config.gui.jobTab_GetRowData(0) == job )
         return true;
      return false;
   }
   
   // status = completed, failed, killed or canceled
   public static void addToJobHistory(jobData job, String status) {
      if (config.GUI) {
         // Save final status of job
         job.status = status;
         // Save length of time job lasted
         job.duration = (new Date().getTime()) - job.time;
         
         // Add to job history stack
         //JOB_HISTORY.add(job);
      }
   }
   
   // If any jobs remain for given tivoName then return true
   public static Boolean jobsRemain(String tivoName) {
      if ( JOBS != null && ! JOBS.isEmpty() ) {
         for (int i=0; i<JOBS.size(); ++i) {
            if (JOBS.get(i).tivoName.equals(tivoName))
               return true;
         }
      }
      return false;
   }

   // Return elapsed time of a job in h:mm:ss format
   public static String getElapsedTime(long start) {
      long elapsed = (new Date().getTime() - start)/1000;
      int hours = (int)(elapsed/3600);
      int mins  = (int)((elapsed/60) - (hours*60));
      int secs  = (int)(elapsed % 60);
      return String.format("%d:%02d:%02d",hours,mins,secs);
   }

   // Return elapsed time of a job in hh:mm:ss format
   public static String getRate(long size, long start) {
      long elapsed = (new Date().getTime() - start)/1000;
      Double rate;
      if (elapsed ==0) {
         rate = 0.0;
      } else {
         rate = (size*8)/(1e6*elapsed);
      }
      
      return String.format("%.2f Mbps", rate);
   }
     
   // Master job launch function (in both GUI & auto modes)
   // Builds file names & launches jobs according to specs
   @SuppressWarnings("unchecked")
   public static void LaunchJobs(Hashtable<String,Object> specs) {
      debug.print("specs=" + specs);
      
      String mode = (String)specs.get("mode");
      
      if (mode.equals("Download")) {
         Hashtable<String,String> entry = (Hashtable<String,String>)specs.get("entry");

         // Copy protected or recording entry should not be processed
         if (entry.containsKey("CopyProtected")) {
            log.error("This show is copy protected - cannot process");
            return;
         }
         if (entry.containsKey("InProgress")) {
            log.error("This show is still recording - cannot process");
            return;
         }
         
         String name = tivoFileName.buildTivoFileName(entry);
         if (name == null) {
            // Invalid file name => abort processing
            return;
         } else {
            // Valid file name => proceed
            specs.put("startFile", name);            
         }
      }

      // Determine which actions are enabled
      String startFile     = (String)specs.get("startFile");
      Boolean metadata     = (Boolean)specs.get("metadata");
      Boolean metadataTivo = (Boolean)specs.get("metadataTivo");
      Boolean decrypt      = (Boolean)specs.get("decrypt");
      Boolean qsfix        = (Boolean)specs.get("qsfix");
      Boolean twpdelete    = (Boolean)specs.get("twpdelete");
      Boolean comskip      = (Boolean)specs.get("comskip");
      Boolean comcut       = (Boolean)specs.get("comcut");
      Boolean captions     = (Boolean)specs.get("captions");
      Boolean encode       = (Boolean)specs.get("encode");
      Boolean push         = (Boolean)specs.get("push");
      Boolean custom       = (Boolean)specs.get("custom");
      Boolean useProgramId_unique = false;
      if (specs.containsKey("useProgramId_unique")) {
         useProgramId_unique = (Boolean)specs.get("useProgramId_unique");
      }
      
      Boolean streamfix    = false;
      
      if (metadataTivo) {
         // In FILES mode can only get metadata from .tivo files
         if ( ! startFile.toLowerCase().endsWith(".tivo") ) metadataTivo = false;
      }
      
      if (decrypt && mode.equals("FILES")) {
         // In FILES mode can only run decrypt on .tivo files
         if ( ! startFile.toLowerCase().endsWith(".tivo") ) decrypt = false;
      }
      
      // Init names
      String source       = null;
      String tivoName     = null;
      String encodeName   = null;
      String tivoFile     = null;
      String metaFile     = null;
      String mpegFile     = null;
      String mpegFile_fix = null;
      String edlFile      = null;
      String mpegFile_cut = null;
      String videoFile    = null;
      String srtFile      = null;
      String encodeFile   = null;
 
      // Init tivoName
      if (specs.containsKey("tivoName")) {
         tivoName = (String)specs.get("tivoName");
      } else {
         log.error("LaunchJobs error: tivo name not specified!");
         return;
      }

      // Init encodeName
      if (specs.containsKey("encodeName")) {
         encodeName = (String)specs.get("encodeName");
      } else {
         encodeName = encodeConfig.getEncodeName();
      }

      String outputDir = config.outputDir;
      if ( ! file.isDir(outputDir) ) {
         outputDir = config.programDir;
      }
      
      String mpegDir = config.mpegDir;
      if ( ! file.isDir(mpegDir) ) {
         mpegDir = outputDir;
      }
      
      String mpegCutDir = config.mpegCutDir;
      if ( ! file.isDir(mpegCutDir) ) {
         mpegCutDir = outputDir;
      }
      
      String encodeDir = config.encodeDir;
      if ( ! file.isDir(encodeDir) ) {
         encodeDir = outputDir;
      }
      
      // Define file names according to specs
      String s = File.separator;
      if ( mode.equals("FILES") ) {
         // FILES mode means different starting points than download mode
         source = startFile;
         if ( startFile.toLowerCase().endsWith(".tivo") ) {
            tivoFile = startFile;
            metaFile = string.replaceSuffix(tivoFile, ".mpg.txt");
            mpegFile = string.replaceSuffix(tivoFile, ".mpg");
            mpegFile = mpegDir + s + string.basename(mpegFile);
            metaFile = mpegDir + s + string.basename(metaFile);
         } else {
            mpegFile = startFile;
         }
         mpegFile_fix = mpegFile + ".qsfix";
         
         edlFile = string.replaceSuffix(mpegFile, ".edl");
         
         mpegFile_cut = string.replaceSuffix(mpegFile, "_cut.mpg");
         mpegFile_cut = mpegCutDir + s + string.basename(mpegFile_cut);
         
         if (comcut) metaFile = mpegFile_cut + ".txt";
         
         String encodeExt = encodeConfig.getExtension(encodeName);
         encodeFile = string.replaceSuffix(mpegFile, "." + encodeExt);
         encodeFile = encodeDir + s + string.basename(encodeFile);
         
         if (encode) metaFile = encodeFile + ".txt";
         
         // For captions decide on source and srtFile
         videoFile = startFile;
         if (decrypt) videoFile = mpegFile;
         if (comcut)  videoFile = mpegFile_cut;
         srtFile = string.replaceSuffix(videoFile, ".srt");
         if (encode)  srtFile = string.replaceSuffix(encodeFile, ".srt");
         
      } else {
         // Download mode
         tivoFile = outputDir + s + startFile;
         mpegFile = string.replaceSuffix(startFile, ".mpg");
         mpegFile = mpegDir + s + mpegFile;
         metaFile = mpegFile + ".txt";
         
         mpegFile_fix = mpegFile + ".qsfix";
         
         edlFile = string.replaceSuffix(mpegFile, ".edl");
         
         mpegFile_cut = string.replaceSuffix(startFile, "_cut.mpg");
         mpegFile_cut = mpegCutDir + s + mpegFile_cut;
         
         if (comcut) metaFile = mpegFile_cut + ".txt";

         String encodeExt = encodeConfig.getExtension(encodeName);
         encodeFile = string.replaceSuffix(startFile, "." + encodeExt);
         encodeFile = encodeDir + s + encodeFile;
         
         if (encode) metaFile = encodeFile + ".txt";
         
         // For captions decide on source and srtFile
         videoFile = tivoFile;
         if (decrypt) videoFile = mpegFile;
         if (comcut)  videoFile = mpegFile_cut;
         srtFile = string.replaceSuffix(videoFile, ".srt");
         if (encode)  srtFile = string.replaceSuffix(encodeFile, ".srt");
      }
      
      // Decide if streamfix should be enabled
      // windows AND qsfix AND ! vrd AND encode => enable
      /* This intentionally disabled for now
      if (config.OS.equals("windows") && qsfix && ! file.isDir(config.VRD)) {
         qsfix = false;
         streamfix = true;
      }
      */
      
      // Check task dependencies and enable prior tasks if necessary
      
      // encode requires mpegFile or mpegFile_cut which may require at minimum decrypt
      if (encode && config.VrdEncode == 0) {
         if ( ! decrypt ) {
            if ( ! file.isFile(mpegFile) && ! file.isFile(mpegFile_cut) ) {
               decrypt = true;
            }
         }
      }
      
      // comcut requires an edlFile or vprjFile which may require comskip
      if (comcut) {
         if ( ! comskip ) {
            if (file.isDir(config.VRD)) {
               if ( config.VrdReview_noCuts == 0 && ! file.isFile(string.replaceSuffix(mpegFile, ".VPrj")) ) {
                  comskip = true;
               }
            } else {
               if ( ! file.isFile(edlFile) ) {
                  comskip = true;
               }
            }
         }
      }
      
      // comskip requires an mpeg file which may require decrypt
      if (comskip) {
         if ( ! decrypt && ! file.isFile(mpegFile)) {
            decrypt = true;
         }
      }
      
      // qsfix requires an mpeg file which may require decrypt
      if (qsfix) {
         if ( ! decrypt && ! file.isFile(mpegFile)) {
            decrypt = true;
         }
      }
      
      // ccextractor requires an mpeg file which may require decrypt
      if (captions && ! file.isFile(config.t2extract) && file.isFile(config.ccextractor)) {
         if ( ! decrypt && ! file.isFile(mpegFile)) {
            decrypt = true;
         }
      }
                           
      // Launch jobs depending on selections
      Hashtable<String,String> entry = (Hashtable<String,String>)specs.get("entry");
      if ( mode.equals("Download") ) {
         source = entry.get("url_TiVoVideoDetails");
         if (metadata) {
            Stack<String> meta_files = videoFilesToProcess(
               mode, decrypt, comcut, encode, config.metadata_files,
               startFile, videoFile, tivoFile, mpegFile, mpegFile_cut, encodeFile, ".txt"
            );
            if (meta_files.size() > 0) {
               for (int i=0; i<meta_files.size(); ++i) {
                  jobData job = new jobData();
                  job.source             = source;
                  job.tivoName           = tivoName;
                  if (config.java_downloads == 0) {
                     job.type            = "metadata";
                     job.name            = "curl";
                  } else {
                     job.type            = "javametadata";
                     job.name            = "java";                     
                  }
                  job.metaFile           = meta_files.get(i);
                  job.url                = entry.get("url_TiVoVideoDetails");
                  if (entry.containsKey("EpisodeNumber"))
                     job.episodeNumber = entry.get("EpisodeNumber");
                  if (entry.containsKey("channelNum"))
                     job.displayMajorNumber = entry.get("channelNum");
                  if (entry.containsKey("channel"))
                     job.callsign = entry.get("channel");
                  if (entry.containsKey("SeriesId"))
                     job.seriesId = entry.get("SeriesId");
                  submitNewJob(job);
               }
            } else {
               log.error("metadata files setting=" + config.metadata_files + " but file(s) not available for this task set");
            }
         }
         
         if (autotune.isConfigured(tivoName)) {
            jobData job = new jobData();
            job.source   = source;
            job.tivoName = tivoName;
            job.type     = "autotune";
            job.name     = "telnet";
            submitNewJob(job);
         }
         
         jobData job = new jobData();
         job.source       = source;
         job.tivoName     = tivoName;
         if (config.java_downloads == 1) {
            job.type      = "javadownload";
            job.name      = "java";
         } else {
            job.type      = "download";
            job.name      = "curl";
         }
         job.tivoFile     = tivoFile;
         job.url          = entry.get("url");
         job.tivoFileSize = Long.parseLong(entry.get("size"));
         if (entry.containsKey("ProgramId")) {
            job.ProgramId = entry.get("ProgramId");
         }
         if (useProgramId_unique && entry.containsKey("ProgramId_unique")) {
            job.ProgramId_unique = entry.get("ProgramId_unique");
         }
         if (entry.containsKey("title")) {
            job.title = entry.get("title");
         }
         submitNewJob(job);
      }
      
      if (metadataTivo) {
         Stack<String> meta_files = videoFilesToProcess(
            mode, decrypt, comcut, encode, config.metadata_files,
            startFile, videoFile, tivoFile, mpegFile, mpegFile_cut, encodeFile, ".txt"
         );
         if (meta_files.size() > 0) {
            for (int i=0; i<meta_files.size(); ++i) {
               jobData job = new jobData();
               job.source             = source;
               job.tivoName           = tivoName;
               job.type               = "metadataTivo";
               job.name               = "tivodecode";
               job.tivoFile           = tivoFile;
               job.metaFile           = meta_files.get(i);
               submitNewJob(job);
            }
         } else {
            log.error("metadata files setting=" + config.metadata_files + " but file(s) not available for this task set");
         }
      }
      
      if (decrypt && config.VrdDecrypt == 0) {
         jobData job = new jobData();
         job.source       = source;
         job.tivoName     = tivoName;
         job.type         = "decrypt";
         job.name         = config.tivodecode;
         job.tivoFile     = tivoFile;
         job.mpegFile     = mpegFile;
         if (twpdelete && entry != null && entry.containsKey("url")) {
            job.twpdelete = true;
            job.url       = entry.get("url");
         }
         submitNewJob(job);
      }
      
      if (qsfix || (decrypt && config.VrdDecrypt == 1)) {
         jobData job = new jobData();
         job.source       = source;
         job.tivoName     = tivoName;
         job.type         = "qsfix";
         job.name         = config.VRD;
         job.mpegFile     = mpegFile;
         job.mpegFile_fix = mpegFile_fix;
         if (config.VrdDecrypt == 1) {
            job.tivoFile  = tivoFile;
            if (twpdelete && entry != null && entry.containsKey("url")) {
               job.twpdelete = true;
               job.url       = entry.get("url");
            }
         }
         submitNewJob(job);
      }
      
      if (streamfix) {
         jobData job = new jobData();
         job.source       = source;
         job.tivoName     = tivoName;
         job.type         = "streamfix";
         job.name         = config.mencoder;
         job.mpegFile     = mpegFile;
         job.mpegFile_fix = mpegFile_fix;
         submitNewJob(job);
      }
      
      if (comskip) {
         if (file.isDir(config.VRD) && config.UseAdscan == 1) {
            jobData job = new jobData();
            job.source       = source;
            job.tivoName     = tivoName;
            job.type         = "adscan";
            job.name         = config.VRD;
            job.mpegFile     = mpegFile;
            job.vprjFile     = string.replaceSuffix(mpegFile, ".VPrj");
            submitNewJob(job);
         } else {
            jobData job = new jobData();
            job.source       = source;
            job.tivoName     = tivoName;
            job.type         = "comskip";
            job.name         = config.comskip;
            if (streamfix)
               job.mpegFile  = mpegFile_fix;
            else
               job.mpegFile  = mpegFile;
            job.edlFile      = edlFile;
            if (file.isDir(config.VRD))
               job.vprjFile = string.replaceSuffix(mpegFile, ".VPrj");
            if (specs.containsKey("comskipIni"))
               job.comskipIni = (String) specs.get("comskipIni");
            submitNewJob(job);            
         }
      }
      
      // Schedule VideoRedo GUI manual cuts review if requested (GUI mode only)
      if (file.isDir(config.VRD) && config.GUI) {
         if ( (comskip && config.VrdReview == 1) || (comcut && config.VrdReview_noCuts == 1) ) {
            jobData job = new jobData();
            job.source       = source;
            job.tivoName     = tivoName;
            job.type         = "vrdreview";
            job.name         = config.VRD;
            job.mpegFile     = mpegFile;
            job.vprjFile     = string.replaceSuffix(mpegFile, ".VPrj");
            submitNewJob(job);
            // VRD will be used to save output with cuts, so cancel comcut
            if (config.VrdReview_noCuts == 1)
               comcut = false;
         }
      }
      
      if (comcut) {
         if ( file.isFile(config.VRD + File.separator + "vp.vbs") ) {
            jobData job = new jobData();
            job.source       = source;
            job.tivoName     = tivoName;
            job.type         = "adcut";
            job.name         = config.VRD;
            job.mpegFile     = mpegFile;
            job.mpegFile_cut = mpegFile_cut;
            job.edlFile      = edlFile;
            submitNewJob(job);
         } else {
            jobData job = new jobData();
            job.source       = source;
            job.tivoName     = tivoName;
            job.type         = "comcut";
            job.name         = config.mencoder;
            if (streamfix)
               job.mpegFile  = mpegFile_fix;
            else
               job.mpegFile  = mpegFile;
            job.mpegFile_cut = mpegFile_cut;
            job.edlFile      = edlFile;
            submitNewJob(job);            
         }
      }
      
      if (captions) {
         jobData job = new jobData();
         job.source       = source;
         job.tivoName     = tivoName;
         job.type         = "captions";
         if (file.isFile(config.t2extract))
        	 job.name     = config.t2extract;
         else
        	 job.name     = config.ccextractor;
         if (streamfix && videoFile.equals(mpegFile))
            job.videoFile = mpegFile_fix;
         else {
            if (file.isFile(config.t2extract))
               job.videoFile = videoFile;
            else
               job.videoFile = mpegFile;
         }
         job.srtFile      = srtFile;
         submitNewJob(job);
      }
      
      if (encode) {
         jobData job = new jobData();
         job.source       = source;
         job.tivoName     = tivoName;
         job.type         = "encode";
         job.name         = encodeName;
         job.encodeName   = encodeName;
         if (streamfix)
            job.mpegFile  = mpegFile_fix;
         else
            job.mpegFile  = mpegFile;
         job.mpegFile_cut = mpegFile_cut;
         job.encodeFile   = encodeFile;
         job.srtFile      = srtFile;
         if (config.VrdEncode == 1 && encodeConfig.getCommandName(encodeName) == null) {
            // VRD encode selected => vrdencode job
            job.type      = "vrdencode";
            job.tivoFile  = tivoFile;
         }
         submitNewJob(job);
      }
            
      if (push) {
         Stack<String> push_files = videoFilesToProcess(
            mode, decrypt, comcut, encode, config.pyTivo_files,
            startFile, videoFile, tivoFile, mpegFile, mpegFile_cut, encodeFile, ""
         );
         if (push_files.size() > 0) {
            for (int i=0; i<push_files.size(); ++i) {
               jobData job = new jobData();
               job.source       = source;
               job.tivoName     = tivoName;
               job.type         = "push";
               job.name         = "pyTivo_push";
               job.tivoName     = tivoName;
               job.pyTivo_tivo  = config.pyTivo_tivo;
               job.videoFile    = push_files.get(i);
               submitNewJob(job);
            }
         } else {
            log.error("push files setting=" + config.pyTivo_files + " but file(s) not available for this task set");
         }
      }
      
      if (custom) {
         jobData job = new jobData();
         job.source       = source;
         job.tivoName     = tivoName;
         job.type         = "custom";
         job.name         = config.customCommand;
         job.tivoName     = tivoName;
         job.tivoFile     = tivoFile;
         job.metaFile     = metaFile;
         job.mpegFile     = mpegFile;
         job.mpegFile_cut = mpegFile_cut;
         job.srtFile      = srtFile;
         job.encodeFile   = encodeFile;
         submitNewJob(job);
      }
      
   }
   
   // This makes decisions based on file filter setting for metadata and/or push tasks which
   // video files specifically should be processed
   private static Stack<String> videoFilesToProcess(
      String mode, Boolean decrypt, Boolean comcut, Boolean encode, String filter,
      String startFile, String videoFile, String tivoFile, String mpegFile, String mpegFile_cut, String encodeFile,
      String suffix
      ) {
      // Don't want null values for file names
      if (startFile    == null)    startFile = "startFile";
      if (videoFile    == null)    videoFile = "videoFile";
      if (tivoFile     == null)     tivoFile = "tivoFile";
      if (mpegFile     == null)     mpegFile = "mpegFile";
      if (mpegFile_cut == null) mpegFile_cut = "mpegFile_cut";
      if (encodeFile   == null)   encodeFile = "encodeFile";
      
      Stack<String> files = new Stack<String>();
      if ( ! filter.equals("all") ) {
         // files setting != "all" => single push job
         if (filter.equals("last")) {
            if (encode || (mode.equals("FILES") && encodeFile.equals(startFile)))
               files.add(encodeFile + suffix);
            else if (decrypt || comcut || (mode.equals("FILES") && videoFile.equals(startFile)))
               files.add(videoFile + suffix);
            else if (mode.equals("Download") || (mode.equals("FILES") && tivoFile.equals(startFile)))
               files.add(tivoFile + suffix);
         }
         else if (filter.equals("tivoFile")) {
            if (mode.equals("Download") || (mode.equals("FILES") && tivoFile.equals(startFile)))
               files.add(tivoFile + suffix);
         }
         else if (filter.equals("mpegFile")) {
            if (decrypt || (mode.equals("FILES") && mpegFile.equals(startFile)))
               files.add(mpegFile + suffix);
         }
         else if (filter.equals("mpegFile_cut")) {
            if (comcut || (mode.equals("FILES") && mpegFile_cut.equals(startFile)))
               files.add(mpegFile_cut + suffix);
         }
         else if (filter.equals("encodeFile")) {
            if (encode || (mode.equals("FILES") && encodeFile.equals(startFile)))
               files.add(encodeFile + suffix);
         }
      } else {
         // files setting = "all" => potentially multiple push jobs
         if (mode.equals("Download") || (mode.equals("FILES") && tivoFile.equals(startFile)))
            files.add(tivoFile + suffix);
         if (decrypt || (mode.equals("FILES") && mpegFile.equals(startFile)))
            files.add(mpegFile + suffix);
         if (comcut || (mode.equals("FILES") && mpegFile_cut.equals(startFile)))
            files.add(mpegFile_cut + suffix);
         if (encode || (mode.equals("FILES") && encodeFile.equals(startFile)))
            files.add(encodeFile + suffix);
      }
      return files;
   }

   // Cancel and/or kill given job
   public static void kill(jobData job) {
      if (job.type != null) {
         removeFamilyJobs(job);
         // Kill job if running
         if (job.status.equals("running")) {
            /*if (job.type.equals("push")) {
               job.kill();
            } else {
               log.warn("Killing '" + job.type + "' job: " + job.getProcess().toString());
               job.getProcess().kill();
            }*/
            job.kill();
         }
         // Clear title & progress bar
         if ( config.GUI && isFirstJobInMonitor(job) ) {
            config.gui.setTitle(config.kmttg);
            config.gui.progressBar_setValue(0);
         }         
         removeFromJobList(job);
      } else {
         log.error("Could not kill job - Missing 'type' key: " + job);
      }
   }

   // Remove all jobs in the family whose familId > given job familyId
   private static void removeFamilyJobs(jobData job) {
      float major_float = job.familyId;
      int majorId = (int)major_float;
      float mf;
      int m;
      for (int i=0; i<JOBS.size(); ++i) {
         mf = JOBS.get(i).familyId;
         m = (int)mf;
         if (m == majorId && mf > major_float) {
            log.warn("Removing job: " + JOBS.get(i).toString());
            removeFromJobList(JOBS.get(i));
         }
      }
   }
   
   // Kill all running jobs - called on program exit
   public static void killRunning() {
      for (int i=0; i<JOBS.size(); ++i) {
         kill(JOBS.get(i));
      }
   }

   // Listen on tivo_beacon for any newly detected tivos
   private static void tivoBeaconUpdate() {
      Hashtable<String,String> b = config.tivo_beacon.listen();
      if (b != null) {
         // Check against current Tivos list
         Stack<String> tivoNames = config.getTivoNames();
         Boolean add = true;
         for (int i=0; i<tivoNames.size(); ++i) {
            if ( tivoNames.get(i).matches(b.get("machine")) ) {
               add = false;
            }
         }
         if (add) {
            config.addTivo(b);
         } else {
            // Update existing IP if necessary (for case if DHCP updates IP of existing Tivo)
            String name = b.get("machine");
            String ip = b.get("ip");
            if (! ip.equals(config.TIVOS.get(name))) {
               log.warn("Updating IP for TiVo: " + name);
               config.TIVOS.put(name, ip);
               config.save(config.configIni);
            }
         }
      }
   }

   // Return true if this job is a VideoRedo COM job that needs to be restricted to 1 at a time
   private static Boolean isVideoRedoCOMJob(jobData job) {
      Boolean restricted = false;
      if ( job.type.equals("qsfix") || job.type.equals("adscan") ||
           job.type.equals("adcut") || job.type.equals("vrdencode") ) {
         restricted = true;
      }
      
      // If VrdAllowMultiple is set then don't restrict to 1 at a time
      if (restricted && config.VrdAllowMultiple == 1)
         restricted = false;
      
      return restricted;
   }

   // Return true if this job is a VideoRedo GUI job that needs to be restricted to 1 at a time
   private static Boolean isVideoRedoGUIJob(jobData job) {
      Boolean restricted = false;
      if ( job.type.equals("vrdreview") ) {
         restricted = true;
      }
      
      // If VrdAllowMultiple is set then don't restrict to 1 at a time
      if (restricted && config.VrdAllowMultiple == 1)
         restricted = false;
      
      return restricted;
   }
   
   // Shut down OS (Windows only)
   public void shutdown() {
      if (config.OS.equals("windows")) {
         try {
            Runtime.getRuntime().exec("shutdown.exe -f -s");
         }
         catch(Exception e) {
            log.error(e.toString());
         }
      } else {
         log.warn("shutdown not supported on this OS");
      }
   }
   
   private static int getNumQueuedJobs() {
      int num = 0;
      for (int i=0; i<JOBS.size(); ++i) {
         if ( JOBS.get(i).status.equals("queued") ) {
            num++;
         }
      }
      return num;
   }
   
   public static void saveQueuedJobs() {
      if ( JOBS.isEmpty() ) {
         log.error("There are currently no queued jobs to save.");
      } else {
         try {
            FileOutputStream fos = new FileOutputStream(config.programDir + File.separator + jobDataFile);
            ObjectOutputStream oos = new ObjectOutputStream(fos);

            int n = getNumQueuedJobs();
            if (n == 0) {
               log.error("There are currently no queued jobs to save.");
               return;
            }
            oos.writeInt(n);
            for (int i=0; i<JOBS.size(); ++i) {
               if ( JOBS.get(i).status.equals("queued") )
                  oos.writeObject(JOBS.get(i));
            }
            oos.close();
            fos.close();
            log.warn("Saved " + n + " queued jobs to file: " + jobDataFile);
         } catch (Exception ex) {
            log.error("Failed to save queued jobs to file\n" + ex.toString());
         }
      }
   }

   public static void loadQueuedJobs() {
      if ( JOBS.isEmpty() ) {
         try {
            String jobFile = config.programDir + File.separator + jobDataFile;
            FileInputStream fis = new FileInputStream(jobFile);
            ObjectInputStream ois = new ObjectInputStream(fis);
            int n = ois.readInt();
            for (int i=0; i<n; ++i) {
               submitNewJob((jobData) ois.readObject()); 
            }
            ois.close();
            fis.close();
            log.warn("Loaded " + n + " queued jobs from file: " + jobDataFile);
            // Should we delete file after load?
            //file.delete(jobFile);
         } catch (Exception ex) {
            log.error("Failed to load queued jobs from file\n" + ex.toString());
         }
      } else {
         log.error("You can only load queued jobs from file when there are no active or queued jobs.");
      }
   }
    
   // Identify NPL table items associated with queued/running jobs
   public static void updateNPLjobStatus() {
      if (config.GUI && JOBS != null) {
         // Build hash of source ID -> job status
         Hashtable<String,String> map = new Hashtable<String,String>();
         for (int i=0; i<JOBS.size(); ++i) {
            if (JOBS.get(i).source != null) {
               if (map.containsKey(JOBS.get(i).source)) {
                  // Already have an entry with this source
                  if (JOBS.get(i).status.equals("running")) {
                     // Running status should override queued status when there
                     // are multiple jobs with same source id
                     map.put(JOBS.get(i).source,JOBS.get(i).status);
                  }
               } else {
                  map.put(JOBS.get(i).source,JOBS.get(i).status);
               }
            }
         }
         
         // Update NPL lists according to above hash
         config.gui.updateNPLjobStatus(map);
      }
   }
   
   private static void handleLoopInGUI() {
      // Clear out launch hash if menu item was just toggled
      if (config.GUI_AUTO == -1) {
         launch.clear();
         config.GUI_AUTO = 0;
      }
      
      // Launch jobs for Tivos or update launch times appropriately
      Long now = new Date().getTime();
      for (int i=0; i < config.getTivoNames().size(); i++) {
         String tivoName = config.getTivoNames().get(i);
         if (launch.get(tivoName) == null) {
            launch.put(tivoName, now - 1);
         }
         launchTime = launch.get(tivoName);
         
         // Launch new jobs for this Tivo if ready
         if (launchTime != -1 && now > launchTime) {
            log.print("\n>> Running auto transfers for TiVo: " + tivoName);
            // Parse auto.ini each time before launch in case it gets updated
            if ( ! autoConfig.parseAuto(config.autoIni) ) {
               log.error("Auto Transfers config has errors or is not setup");
               return;
            }
            if ( auto.getTitleEntries().isEmpty() && auto.getKeywordsEntries().isEmpty() ) {
               log.error("No keywords defined in " + config.autoIni + "... aborting");
               return;
            }
            // Launch jobs for this tivo
            config.GUI_AUTO++;
            getNPL(tivoName);
            launch.put(tivoName, (long)-1);
         }
         
         if ( ! jobsRemain(tivoName) && launchTime == -1 ) {
            // Setup to launch new jobs after user configured sleep time
            launch.put(tivoName, now + autoConfig.CHECK_TIVOS_INTERVAL*60*1000);
            log.print("\n'" + tivoName + "' AUTO TRANSFERS PROCESSING SLEEPING " + autoConfig.CHECK_TIVOS_INTERVAL + " mins ...");
         }
      }
   }
}
