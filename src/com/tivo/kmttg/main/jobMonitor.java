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
package com.tivo.kmttg.main;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.application.Platform;

import com.tivo.kmttg.gui.gui;
import com.tivo.kmttg.util.NplItemXML;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;
import com.tivo.kmttg.rpc.SkipManager;
import com.tivo.kmttg.task.*;

public class jobMonitor {
   public static Stack<jobData> JOBS = new Stack<jobData>();
   //private static Stack<jobData> JOB_HISTORY = new Stack<jobData>();
   private static int JOB_COUNT = 0;
   private static int FAMILY_ID = 0;
   public static Boolean NoNewJobs = false;
   private static String jobDataFile = "jobData.dat";
   private static boolean _isLoadingQueue = false;
   
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
   public static void monitor(gui gui) {
      Stack<jobData> running = new Stack<jobData>();
      Stack<jobData> queued  = new Stack<jobData>();
      jobData job;
      if (config.GUIMODE && gui == null && kmttg.timer != null) {
         kmttg.timer.cancel();
         return;
      }
      
      // Handle auto transfers GUI run if "Loop in GUI" menu item enabled
      if (config.GUIMODE && config.GUI_LOOP == 1) {
         if (file.isFile(config.autoIni) && file.size(config.autoIni) > 0)
            handleLoopInGUI();
         else
            config.gui.autoLoopInGUICB(false);
      }
      
      // Check for new tivos
      if (config.UseOldBeacon == 0 && config.jmdns != null)
         config.jmdns.process();
      if (config.UseOldBeacon == 1 && config.tivo_beacon != null)
         config.tivo_beacon.tivoBeaconUpdate();
      
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
                  job.process = new NowPlaying(job);
                  if (job.process != null) {
                     if (job.process.start()) {
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
                  job.process = new javaNowPlaying(job);
                  if (job.process != null) {
                     if (job.process.start()) {
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
      // Also count total number of download jobs currently running
      int cpuActiveJobs = 0;
      //int VideoRedoCOMJobs = 0;
      //int VideoRedoGUIJobs = 0;
      int VideoRedoJobs = 0;
      int totalDownloads = 0;
      int atomicJobs = 0;
      Hashtable<String,Integer> tivoDownload = new Hashtable<String,Integer>();
      for (int i=0; i<running.size(); i++) {
         job = running.get(i);
         if (job.type.equals("atomic"))
            atomicJobs++;
         if (isDownloadJob(job))
            totalDownloads++;
         if (isVideoRedoJob(job))
        	 VideoRedoJobs++;
         if ( oneJobAtATime(job.type) ) {
            if ( ! tivoDownload.containsKey(job.tivoName) ) {
               tivoDownload.put(job.tivoName, 0);
            }
            tivoDownload.put(job.tivoName, tivoDownload.get(job.tivoName)+1);
         //} else if ( isVideoRedoGUIJob(job) ) {
         //   // NOTE: VRD GUI job not considered CPU active
         //   VideoRedoGUIJobs++;
         } else {
            cpuActiveJobs++;
            //if ( isVideoRedoCOMJob(job) ) {
            //   VideoRedoCOMJobs++;
            //}
         }
      }
      
      // See if we can launch any queued jobs
      for (int i=0; i<queued.size(); i++) {
         if (NoNewJobs) continue; //Skip all jobs when this flag is set (to facilitate a graceful shutdown)
         
         job = queued.get(i);
         debug.print("job=" + job);
         
         // Atomic jobs in FILES mode should only run 1 at a time
         if (job.type.equals("atomic") && atomicJobs > 0 && job.tivoName.equals("FILES"))
            continue;
         
         // If there are prior job types in the family queued don't schedule yet (except atomic)
         if ( priorInFamilyExist(job.familyId, famList) ) {
            if ( ! job.type.equals("atomic") )
               continue;
         }
         
         // Only 1 download at a time per Tivo allowed
         if (tivoDownload.size() > 0) {            
            if ( oneJobAtATime(job.type) ) {
               if ( tivoDownload.containsKey(job.tivoName) ) {
                  if (tivoDownload.get(job.tivoName) > 0) {
                     // Cannot launch this download yet
                     job.launch_time = null;
                     continue;
                  }
               }
            }
         }
         
         // If single_download option is set only allow 1 download at a time
         if (isDownloadJob(job) && config.single_download == 1 && totalDownloads >= 1)
            continue;
      
         // Don't run more than 'MaxJobs' active jobs at a time
         if ( isActiveJob(job) && cpuActiveJobs >= config.MaxJobs) continue;
         
         // Don't launch more than one VideoRedo COM job at a time
         //if ( isVideoRedoCOMJob(job) ) {
         //   if (VideoRedoCOMJobs > 0) continue;
         //}
         
         // Don't launch more than one VideoRedo GUI job at a time
         //if ( isVideoRedoGUIJob(job) ) {
         //   if (VideoRedoGUIJobs > 0) continue;
         //}

         if ( config.VrdOneAtATime == 1 && isVideoRedoJob(job) ) {
        	 if (VideoRedoJobs > 0) continue;
         }
         // Apply a start delay to download jobs (to avoid TiVo server overload)
         if (config.download_delay > 0 && isDownloadJob(job) && job.launch_time == null) {
            long now = new Date().getTime();
            job.launch_time = now + config.download_delay*1000;
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
         
         // Update totalDownloads if a download job was just launched
         if (isDownloadJob(job))
            totalDownloads++;
         
         // Update VideoRedoCOMJobs number
         //if ( isVideoRedoCOMJob(job) )
         //   VideoRedoCOMJobs++;
         
         // Update VideoRedoGUIJobs number
         //if ( isVideoRedoGUIJob(job) )
         //   VideoRedoGUIJobs++;
         
         if ( isVideoRedoJob(job) )
        	 VideoRedoJobs++;
         
         if (job.type.equals("atomic"))
            atomicJobs++;
      }
   }
   
   public static void getNPL(String name) {
      jobData job = new jobData();
      job.tivoName           = name;
      job.type               = "playlist";
      job.name               = "playlist";
      submitNewJob(job);
      if (config.rpcnpl == 1 && config.rpcEnabled(name)) {
         // RPC NPL
         if (config.GUIMODE && config.gui.getTab(name) != null && config.gui.getTab(name).partiallyViewed()) {
               job.partiallyViewed = true;
         }
         job.type = "playlist";
         new NowPlaying(job).launchJob();
      }
      else {
         // Java NPL
         job.type = "javaplaylist";
         new javaNowPlaying(job).launchJob();
      }
   }   
   
   // If true this job can only be run one at a time per TiVo
   private static Boolean oneJobAtATime(String type) {
      return type.equals("javadownload") ||
             type.equals("jdownload_decrypt") ||
             type.equals("tdownload_decrypt") ||
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
      if (isDownloadJob(job) && job.tivoFileSize != null) {
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
            if ( isDownloadJob(job) && job.status.equals("running")) {
               // Estimated size - what is already downloaded
               if (job.tivoFileSize != null && job.tivoFile != null)
                  total += job.tivoFileSize - file.size(job.tivoFile);
            }
         }
      }
      return total;
   }
   
   static void addToJobList(final jobData job) {
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
      if (config.GUIMODE) {
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
         //if (job.type.equals("push")) {
         //   output = "(" + job.pyTivo_tivo + ") " + output;
         //}
         
         class backgroundRun implements Runnable {
            String output;
            public backgroundRun(String output) {
               this.output = output;
            }
            @Override public void run() {
               config.gui.jobTab_AddJobMonitorRow(job, job.tivoName, output);
            }
         }
         backgroundRun b = new backgroundRun(output);
         new Thread(b).start();
      }
      
      // Add job to master job list
      JOBS.add(job);
      updateNPLjobStatus();
      
      // Save job queue backup upon change in case of an unclean exit
      // Do not save queue if loading jobs from the queue file
      // Do not save queue during the initial addition of the NPL jobs
      if (config.persistQueue && !_isLoadingQueue && !kmttg._startingUp)
    		jobMonitor.saveAllJobs();
   }
   
   public static void removeFromJobList(final jobData job) {
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
      if (config.GUIMODE) {
         Platform.runLater(new Runnable() {
            @Override public void run() {
               config.gui.jobTab_RemoveJobMonitorRow(job);
            }
         });
         updateNPLjobStatus();
      }
      
      // Save job queue backup upon change in case of an unclean exit
      // Do not save job queue if shutting down and cleaning up queue
      // Do not save job queue if starting up and initial NPL jobs finish early
      if (config.persistQueue && !kmttg._shuttingDown && !kmttg._startingUp)
    		jobMonitor.saveAllJobs();
   }
   
   // Change job status
   public static void updateJobStatus(final jobData job, final String status) {
      job.status = status;
      if (config.GUIMODE) {
         Platform.runLater(new Runnable() {
            @Override public void run() {
               config.gui.jobTab_UpdateJobMonitorRowStatus(job,status);
            }
         });
         updateNPLjobStatus();
      }
   }
   
   public static void submitNewJob(jobData job) {
      debug.print("job=" + job);
      // Add new job request to job list in queued state
      // The job monitor will decide when to actually launch it
      job.monitor  = -1;
      job.status   = "queued";      
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
                  // Identical job => do not run this job (with a few exceptions)
                  String[] exceptions = {"push","metadata","javametadata","metadataTivo","atomic"};
                  Boolean OK = false;
                  for (String ex : exceptions)
                     if (job.type.equals(ex))
                        OK = true;
                  if (!OK)
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
      if ( config.GUIMODE && config.gui.jobTab_GetRowData(0) == job )
         return true;
      return false;
   }
   
   // status = completed, failed, killed or canceled
   public static void addToJobHistory(jobData job, String status) {
      if (config.GUIMODE) {
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
   
   // Search through all jobData class variable names for given name and set
   // it to given value. Do this for all pending jobs only.
   // This is for update file names that may have to change for pending jobs
   // like jobData.mpegFile for example.
   public static void updatePendingJobFieldValue(jobData job, String name, String value) {
      if ( JOBS != null && ! JOBS.isEmpty() ) {
         Field[] fields = jobData.class.getFields();
         for (int i=0; i<JOBS.size(); ++i) {
            if (JOBS.get(i).status.equals("queued")) {
               if (isInSameFamily(job, JOBS.get(i))) {
                  for (int j=0; j<fields.length; ++j) {
                     if (fields[j].getName().equals(name)) {
                        try {
                           if (fields[j].get(JOBS.get(i)) != null)
                              fields[j].set(JOBS.get(i), value);
                        } catch (Exception e) {
                           log.error("updatePendingJobFieldValue - " + e.getMessage());
                        }
                     }
                  }
               }
            }
         }
      }
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
   
   private static void skipImport(String mode, String tivoName, jobData job, Hashtable<String,String> entry) {
      if (mode.equals("Download") && SkipManager.skipEnabled() && config.autoskip_import == 1) {
         if (config.rpcEnabled(tivoName)) {
            job.autoskip  = true;
            job.contentId = entry.get("contentId");
            job.offerId   = entry.get("offerId");
            job.title     = entry.get("title");
            job.duration  = Long.parseLong(entry.get("duration"));
         }
      }
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
         
         String name = null;
         if ( specs.containsKey("name") )
            name = (String)specs.get("name");
         else
            name = tivoFileName.buildTivoFileName(entry);
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
      Boolean rpcdelete    = (Boolean)specs.get("rpcdelete");
      Boolean comskip      = (Boolean)specs.get("comskip");
      Boolean comcut       = (Boolean)specs.get("comcut");
      Boolean captions     = (Boolean)specs.get("captions");
      Boolean encode       = (Boolean)specs.get("encode");
      //Boolean push         = (Boolean)specs.get("push");
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
      String encodeName2   = null;
      String encodeName2_suffix = null;
      String tivoFile     = null;
      String metaFile     = null;
      String mpegFile     = null;
      String mpegFile_fix = null;
      String edlFile      = null;
      String mpegFile_cut = null;
      String videoFile    = null;
      String srtFile      = null;
      String encodeFile   = null;
      String encodeFile2  = null;
 
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
      if (encode && ! encodeConfig.isValidEncodeName(encodeName) ) {
         log.error("Cancelling encode task due to invalid encoding profile specified: " + encodeName);
         encode = false;
      }
      
      // Init encodeName2
      if (specs.containsKey("encodeName2")) {
         encodeName2 = (String)specs.get("encodeName2");
         encodeName2_suffix = (String)specs.get("encodeName2_suffix");
      } else {
         encodeName2 = null;	// null will = no second encoding later on
      }
      if (encode && encodeName2 != null && ! encodeConfig.isValidEncodeName(encodeName2) ) {
         log.error("Cancelling second encode task due to invalid encoding profile specified: " + encodeName2);
      }

      String outputDir = config.outputDir;
      if ( ! file.isDir(outputDir) ) {
         outputDir = config.programDir;
      }
      
      String mpegDir = config.mpegDir;
      if ( ! file.isDir(mpegDir) ) {
         mpegDir = outputDir;
      }
      
      String qsfixDir = config.qsfixDir;
      if ( ! file.isDir(qsfixDir) ) {
         qsfixDir = outputDir;
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
         // NOTE: OK to use basename here since no file naming with folders to have to honor
         source = startFile;
         if ( startFile.toLowerCase().endsWith(".tivo") ) {
            tivoFile = startFile;
            mpegFile = string.replaceSuffix(string.basename(startFile), ".mpg");
            mpegFile = mpegDir + s + mpegFile;
            metaFile = mpegFile + ".txt";            
         } else {
            mpegFile = startFile;
         }
         mpegFile_fix = string.replaceSuffix(string.basename(startFile), ".mpg.qsfix");
         mpegFile_fix = qsfixDir + s + mpegFile_fix;
         
         edlFile = string.replaceSuffix(mpegFile, ".edl");

         if (mpegCutDir.equals(mpegDir)) {
            Pattern p = Pattern.compile("^(.+)(\\..+$)");
            Matcher m = p.matcher(mpegFile);
            if (m.matches()) {
               mpegFile_cut = m.group(1) + "_cut" + m.group(2);
            } else {
               mpegFile_cut = string.replaceSuffix(mpegFile, "_cut.mpg");
            }
            if (mpegFile.toLowerCase().endsWith(".vprj"))
               mpegFile_cut = string.replaceSuffix(mpegFile, "_cut.mpg"); 
         } else {
            // If mpegCutDir different than mpegDir then no need for _cut
            mpegFile_cut = mpegFile;
         }
         mpegFile_cut = mpegCutDir + s + string.basename(mpegFile_cut);
         
         if (comcut) metaFile = mpegFile_cut + ".txt";
         
         String encodeExt = encodeConfig.getExtension(encodeName);
         encodeFile = string.replaceSuffix(startFile, "." + encodeExt);
         encodeFile = encodeDir + s + string.basename(encodeFile);
         
         // Add indicated extension to differentiate this file from first
         if (encodeName2 != null) {
        	    String encodeExt2 = encodeConfig.getExtension(encodeName2);
             encodeFile2 = string.replaceSuffix(startFile, "_" + encodeName2_suffix + "." + encodeExt2);
             encodeFile2 = encodeDir + s + string.basename(encodeFile2);
         }
         
         if (encode) metaFile = encodeFile + ".txt";
         
         // For captions decide on source and srtFile
         videoFile = startFile;
         if (decrypt) videoFile = mpegFile;
         if (comcut)  videoFile = mpegFile_cut;
         srtFile = string.replaceSuffix(videoFile, ".srt");
         if (encode)  srtFile = string.replaceSuffix(encodeFile, ".srt");
         
      } else {
         // Download mode
         // NOTE: Be careful using basename function here - need to honor file naming with folders
         tivoFile = outputDir + s + startFile;
         mpegFile = string.replaceSuffix(startFile, ".mpg");
         mpegFile = mpegDir + s + mpegFile;
         metaFile = mpegFile + ".txt";
         
         // mpegFile_fix doesn't have to honor sub-folders
         mpegFile_fix = string.replaceSuffix(string.basename(startFile), ".mpg.qsfix");
         mpegFile_fix = qsfixDir + s + mpegFile_fix;
         
         edlFile = string.replaceSuffix(mpegFile, ".edl");
         
         if (mpegCutDir.equals(mpegDir)) {
            mpegFile_cut = string.replaceSuffix(startFile, "_cut.mpg");
         } else {
            // If mpegCutDir different than mpegDir then no need for _cut in name
            mpegFile_cut = string.replaceSuffix(startFile, ".mpg");
         }
         mpegFile_cut = mpegCutDir + s + mpegFile_cut;
         
         if (comcut) metaFile = mpegFile_cut + ".txt";

         String encodeExt = encodeConfig.getExtension(encodeName);
         encodeFile = string.replaceSuffix(startFile, "." + encodeExt);
         encodeFile = encodeDir + s + encodeFile;
         
         // Add indicated extension to differentiate this file from first
         if (encodeName2 != null) {
        	    String encodeExt2 = encodeConfig.getExtension(encodeName2);
        	    encodeFile2 = string.replaceSuffix(startFile, "_" + encodeName2_suffix + "." + encodeExt2);
             encodeFile2 = encodeDir + s + encodeFile2;
         }
         
         if (encode) metaFile = encodeFile + ".txt";
         
         // For captions decide on source and srtFile
         videoFile = tivoFile;
         if (decrypt) videoFile = mpegFile;
         if (comcut)  videoFile = mpegFile_cut;
         srtFile = string.replaceSuffix(videoFile, ".srt");
         if (encode)  srtFile = string.replaceSuffix(encodeFile, ".srt");
      }
      
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
            if (config.VRD == 1) {
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
            // If VRD being used then decrypt not required
            if (config.VRD == 1 && config.VrdDecrypt == 1)
               decrypt = false;
         }
      }
      
      // qsfix requires an mpeg file which may require decrypt
      if (qsfix) {
         if ( ! decrypt && ! file.isFile(mpegFile)) {
            decrypt = true;
            // If VRD being used then decrypt not required
            if (config.VRD == 1 && config.VrdDecrypt == 1)
               decrypt = false;
         }
      }
      
      // ccextractor requires an mpeg file which may require decrypt
      if (captions && ! file.isFile(config.t2extract) && file.isFile(config.ccextractor)) {
         if ( ! decrypt && ! file.isFile(mpegFile)) {
            decrypt = true;
            // If VRD being used then decrypt not required
            if (config.VRD == 1 && config.VrdDecrypt == 1)
               decrypt = false;
         }
      }
                           
      // Launch jobs depending on selections
      Hashtable<String,String> entry = (Hashtable<String,String>)specs.get("entry");      
      
      // If config.autoskip_cutonly enabled, then may have to cancel Ad Detect & Ad Skip
      if (comskip && config.autoskip_cutonly == 1 && entry != null) {
         if (! SkipManager.hasEntry(entry.get("contentId"))) {
            comskip = false;
            comcut = false;
         }
      }

      Boolean exportSkip = false;
      if (comskip && entry != null &&
            entry.containsKey("contentId") && SkipManager.hasEntry(entry.get("contentId")))
         exportSkip = true;
      if ( mode.equals("Download") ) {
         source = entry.get("url_TiVoVideoDetails");
         if (metadata) {
            Stack<String> meta_files = videoFilesToProcess(
               mode, decrypt, comcut, encode, config.metadata_files,
               startFile, videoFile, tivoFile, mpegFile, mpegFile_cut, encodeFile, encodeFile2, ".txt"
            );
            if (meta_files.size() > 0) {
               for (int i=0; i<meta_files.size(); ++i) {
                  jobData job = new jobData();
                  job.startFile          = startFile;
                  job.source             = source;
                  job.tivoName           = tivoName;
                  job.type               = "javametadata";
                  job.name               = "java";                     
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
                  if (entry.containsKey("ProgramId"))
                     job.ProgramId = entry.get("ProgramId");
                  // Only submit job if metaFile doesn't exist already
                  if (! file.isFile(job.metaFile))
                     submitNewJob(job);
               }
            } else {
               log.error("metadata files setting=" + config.metadata_files + " but file(s) not available for this task set");
            }
         }

         // Autotune
         if (autotune.isConfigured(tivoName)) {
            jobData job = new jobData();
            job.startFile = startFile;
            job.source    = source;
            job.tivoName  = tivoName;
            job.type      = "autotune";
            job.name      = "telnet";
            submitNewJob(job);
         }

         // Download
         jobData job = new jobData();
         job.startFile    = startFile;
         job.source       = source;
         job.tivoName     = tivoName;
         if (entry != null && entry.containsKey("duration"))
            job.download_duration = (int) (Long.parseLong(entry.get("duration"))/1000);
         if (config.resumeDownloads) {
            if (entry.containsKey("url")) {
               String ByteOffset = NplItemXML.ByteOffset(tivoName, entry.get("url"));
               if (ByteOffset != null) {
                  if (entry.containsKey("title"))
                     log.warn(">> '" + entry.get("title") + "' ByteOffset=" + ByteOffset);
                  entry.put("ByteOffset", ByteOffset);
                  job.offset = ByteOffset;
               }
            }
         }
         if (config.combine_download_decrypt == 1 && decrypt && config.VrdDecrypt == 0) {
            // Combined java download & decrypt
            decrypt = false;
            job.type = "jdownload_decrypt";
            if (config.tivolibreDecrypt == 1)
               job.type = "tdownload_decrypt";
            job.name = "java";
            job.mpegFile = mpegFile;
            job.mpegFile_cut = mpegFile_cut;
            if (twpdelete && entry != null && entry.containsKey("url")) {
               job.twpdelete = true;
               job.url       = entry.get("url");
            }
            if (rpcdelete && entry != null) {
               job.rpcdelete  = true;
               job.entry = entry;
            }
         } else {
            // Standalone java download
            job.type      = "javadownload";
            job.name      = "java";
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
         if (! specs.containsKey("nodownload"))
            submitNewJob(job);
      }
      
      if (metadataTivo) {
         Stack<String> meta_files = videoFilesToProcess(
            mode, decrypt, comcut, encode, config.metadata_files,
            startFile, videoFile, tivoFile, mpegFile, mpegFile_cut, encodeFile, encodeFile2, ".txt"
         );
         if (meta_files.size() > 0) {
            for (int i=0; i<meta_files.size(); ++i) {
               jobData job = new jobData();
               job.startFile          = startFile;
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
         job.startFile    = startFile;
         job.source       = source;
         job.tivoName     = tivoName;
         if (config.tivolibreDecrypt == 1) {
            job.type      = "tivolibre";
            job.name      = "tivolibre";            
         } else if (config.DsdDecrypt == 1) {
            job.type      = "dsd";
            job.name      = config.dsd;            
         } else {
            job.type      = "decrypt";
            job.name      = config.tivodecode;
         }
         job.tivoFile     = tivoFile;
         job.mpegFile     = mpegFile;
         job.mpegFile_cut = mpegFile_cut;
         if (twpdelete && entry != null && entry.containsKey("url")) {
            job.twpdelete = true;
            job.url       = entry.get("url");
         }
         if (rpcdelete && entry != null) {
            job.rpcdelete  = true;
            job.entry = (entry);
         }
         submitNewJob(job);
      }
      
      if (qsfix && config.VRD == 0) {
         streamfix = true;
         qsfix = false;
      }
      
      if (qsfix || (decrypt && config.VrdDecrypt == 1)) {
         // VRD qsfix
         jobData job = new jobData();
         job.startFile    = startFile;
         job.source       = source;
         job.tivoName     = tivoName;
         job.type         = "qsfix";
         job.name         = "VRD";
         job.mpegFile     = mpegFile;
         job.mpegFile_cut = mpegFile_cut;
         job.mpegFile_fix = mpegFile_fix;
         if (config.VrdDecrypt == 1) {
            job.tivoFile  = tivoFile;
            if (twpdelete && entry != null && entry.containsKey("url")) {
               job.twpdelete = true;
               job.url       = entry.get("url");
            }
            if (rpcdelete && entry != null) {
               job.rpcdelete  = true;
               job.entry = entry;
            }
         }
         if (! qsfix)
            job.qsfix_mode = "decrypt";
         submitNewJob(job);
      }
      
      if (streamfix) {
         jobData job = new jobData();
         job.startFile    = startFile;
         job.source       = source;
         job.tivoName     = tivoName;
         job.type         = "fffix";
         job.name         = "fffix";
         job.mpegFile     = mpegFile;
         job.mpegFile_fix = mpegFile_fix;
         job.mpegFile_cut = mpegFile_cut;
         submitNewJob(job);
      }
      
      if (comskip) {
         if (config.VRD == 1 && config.UseAdscan == 1 && ! specs.containsKey("SkipPoint")) {
            jobData job = new jobData();
            job.tivoFile     = tivoFile;
            job.startFile    = startFile;
            job.source       = source;
            job.tivoName     = tivoName;
            job.type         = "adscan";
            job.name         = "VRD";
            job.mpegFile     = mpegFile;
            job.vprjFile     = string.replaceSuffix(mpegFile, ".VPrj");
            job.exportSkip   = exportSkip;
            if (job.exportSkip)
               job.entry     = entry;
            // Setup job for skip table import if relevant
            skipImport(mode, tivoName, job, entry);
            submitNewJob(job);
         } else {
            jobData job = new jobData();
            job.startFile    = startFile;
            job.source       = source;
            if (specs.containsKey("source"))
               job.source    = (String)specs.get("source");
            job.tivoName     = tivoName;
            job.type         = "comskip";
            job.name         = config.comskip;
            job.mpegFile     = mpegFile;
            job.edlFile      = edlFile;
            if (config.VRD == 1)
               job.vprjFile = string.replaceSuffix(mpegFile, ".VPrj");
            if (specs.containsKey("comskipIni"))
               job.comskipIni = (String) specs.get("comskipIni");
            job.exportSkip    = exportSkip;
            if (job.exportSkip)
               job.entry      = entry;
            // Setup job for skip table import if relevant
            skipImport(mode, tivoName, job, entry);
            submitNewJob(job);            
         }
      }
      
      // Schedule VideoRedo GUI manual cuts review if requested (GUI mode only)
      if (config.VRD == 1 && config.GUIMODE && ! specs.containsKey("SkipPoint")) {
         if ( (comskip && config.VrdReview == 1) || (comcut && config.VrdReview_noCuts == 1) ) {
            jobData job = new jobData();
            job.startFile    = startFile;
            job.source       = source;
            job.tivoName     = tivoName;
            job.type         = "vrdreview";
            job.name         = "VRD";
            job.tivoFile     = tivoFile; // This used as backup in case mpegFile not available
            job.mpegFile     = mpegFile;
            job.vprjFile     = string.replaceSuffix(mpegFile, ".VPrj");
            job.entry        = entry;
            // Setup job for skip table import if relevant
            skipImport(mode, tivoName, job, entry);
            submitNewJob(job);
            // VRD will be used to save output with cuts, so cancel comcut
            if (config.VrdReview_noCuts == 1)
               comcut = false;
         }
      }
      
      // Schedule comskip commercial cut point review if requested (GUI mode only)
      if (comskip && config.UseAdscan == 0 && config.VrdReview == 0 &&
            config.comskip_review == 1 && config.GUIMODE && ! specs.containsKey("SkipPoint")) {
         jobData job = new jobData();
         job.startFile    = startFile;
         job.source       = source;
         job.tivoName     = tivoName;
         job.type         = "comskip_review";
         job.name         = "comskip_review";
         job.mpegFile     = mpegFile;
         if (config.VRD == 1)
            job.vprjFile  = string.replaceSuffix(mpegFile, ".VPrj");
         job.edlFile      = edlFile;
         // Setup job for skip table import if relevant
         skipImport(mode, tivoName, job, entry);
         submitNewJob(job);         
      }
      
      if (comcut) {
         if ( config.VRD == 1 ) {
            // Use VRD
            jobData job = new jobData();
            job.startFile = startFile;
            if (config.VrdCombineCutEncode == 1) {
               if (config.VrdEncode == 1 && encodeConfig.getCommandName(encodeName) == null) {
                  // Combine Ad Cut & Encode option set => vrdencode task
                  job.source       = source;
                  job.tivoName     = tivoName;
                  job.type         = "vrdencode";
                  job.name         = encodeName;
                  job.encodeName   = encodeName;
                  job.mpegFile     = mpegFile;
                  job.mpegFile_cut = mpegFile_cut;
                  job.encodeFile   = encodeFile;
                  job.srtFile      = srtFile;
                  job.edlFile      = edlFile;
                  job.tivoFile     = tivoFile;
                  job.vprjFile     = string.replaceSuffix(job.mpegFile, ".VPrj");
                  submitNewJob(job);
               } else {
                  log.error("VRD combine Ad Cut & Encode option set, but VRD encoding profile not selected");
               }
            } else {
               // Ad Cut job using VRD
               job.source       = source;
               job.tivoName     = tivoName;
               job.type         = "adcut";
               job.name         = "VRD";
               job.tivoFile     = tivoFile;
               job.mpegFile     = mpegFile;
               job.mpegFile_cut = mpegFile_cut;
               job.edlFile      = edlFile;
               submitNewJob(job);
            }
         } else {
            // Use ffcut
            jobData job = new jobData();
            job.startFile = startFile;
            job.source       = source;
            job.tivoName     = tivoName;
            job.type         = "ffcut";
            job.name         = "ffcut";
            job.mpegFile     = mpegFile;
            job.mpegFile_cut = mpegFile_cut;
            job.edlFile      = edlFile;
            job.vprjFile     = string.replaceSuffix(job.mpegFile, ".VPrj");
            submitNewJob(job);            
         }
      }
      
      if (captions) {
         jobData job = new jobData();
         job.startFile    = startFile;
         job.source       = source;
         job.tivoName     = tivoName;
         job.type         = "captions";
         if (file.isFile(config.t2extract))
            job.name      = config.t2extract;
         else
            job.name      = config.ccextractor;
         if (file.isFile(config.t2extract))
            job.videoFile = videoFile;
         else
            job.videoFile = mpegFile;
         job.srtFile      = srtFile;
         submitNewJob(job);
      }
      
      if (encode) {
         jobData job = new jobData();
         job.startFile    = startFile;
         job.source       = source;
         job.tivoName     = tivoName;
         job.type         = "encode";
         job.name         = encodeName;
         job.encodeName   = encodeName;
         job.mpegFile     = mpegFile;
         job.mpegFile_cut = mpegFile_cut;
         job.encodeFile   = encodeFile;
         job.srtFile      = srtFile;
         if (config.VrdEncode == 1 && encodeConfig.getCommandName(encodeName) == null) {
            // VRD encode selected => vrdencode job
            job.type      = "vrdencode";
            job.tivoFile  = tivoFile;
            job.vprjFile  = string.replaceSuffix(job.mpegFile, ".VPrj");
         }
         // Indicate we need to keep source file longer
         if (encodeName2 != null)
            job.hasMoreEncodingJobs = true;
         submitNewJob(job);
         
         if (encodeName2 != null) {
            job = new jobData();
            job.startFile    = startFile;
            job.source       = source;
            job.tivoName     = tivoName;
            job.type         = "encode";
            job.name         = encodeName2;
            job.encodeName   = encodeName2;
            job.mpegFile     = mpegFile;
            job.mpegFile_cut = mpegFile_cut;
            job.encodeFile   = encodeFile2;
            job.srtFile      = srtFile;
            if (config.VrdEncode == 1 && encodeConfig.getCommandName(encodeName2) == null) {
               // VRD encode selected => vrdencode job
               job.type      = "vrdencode";
               job.tivoFile  = tivoFile;
               job.vprjFile  = string.replaceSuffix(job.mpegFile, ".VPrj");
            }
            submitNewJob(job);
         }
      }
            
      /*if (push) {
         // NOTE: encodeFile2 arg intentionally set to null to avoid push of 2nd encoding
         Stack<String> push_files = videoFilesToProcess(
            mode, decrypt, comcut, encode, config.pyTivo_files,
            startFile, videoFile, tivoFile, mpegFile, mpegFile_cut, encodeFile, null, ""
         );
         if (push_files.size() > 0) {
            for (int i=0; i<push_files.size(); ++i) {
               jobData job = new jobData();
               job.startFile    = startFile;
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
      }*/
      
      if (custom) {
         jobData job = new jobData();
         job.startFile    = startFile;
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
         if (entry != null && entry.containsKey("url"))
            job.url       = entry.get("url");
         submitNewJob(job);
      }
      
   }
   
   // This makes decisions based on file filter setting for metadata and/or push tasks which
   // video files specifically should be processed
   private static Stack<String> videoFilesToProcess(
      String mode, Boolean decrypt, Boolean comcut, Boolean encode, String filter,
      String startFile, String videoFile, String tivoFile, String mpegFile, String mpegFile_cut,
      String encodeFile, String encodeFile2, String suffix
      ) {
      Boolean encode2 = false;
      if (encodeFile2 != null)
         encode2 = true;
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
            if (encode || (mode.equals("FILES") && encodeFile.equals(startFile))) {
               files.add(encodeFile + suffix);
               if (encode2)
                  files.add(encodeFile2 + suffix);
            }
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
            if (encode2)
               files.add(encodeFile2 + suffix);
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
         if (encode2)
            files.add(encodeFile2 + suffix);
      }
      return files;
   }

   // Cancel and/or kill given job
   public static void kill(jobData job) {
      if (job.type != null) {
         removeFamilyJobs(job);
         // Kill job if running
         if (job.status.equals("running")) {
            job.kill();
         }
         // Clear title & progress bar
         if ( config.GUIMODE && isFirstJobInMonitor(job) ) {
            Platform.runLater(new Runnable() {
               @Override public void run() {
                  config.gui.setTitle(config.kmttg);
                  config.gui.progressBar_setValue(0);
               }
            });
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
      Stack<jobData> jobs_copy = JOBS;
      for (jobData j : jobs_copy) {
         mf = j.familyId;
         m = (int)mf;
         if (m == majorId && mf > major_float) {
            log.warn("Removing job: " + j.toString());
            removeFromJobList(j);
         }
      }
   }
   
   private static Boolean isInSameFamily(jobData job1, jobData job2) {
      Boolean same = false;
      float mf1 = job1.familyId;
      int m1 = (int)mf1;
      float mf2 = job2.familyId;
      int m2 = (int)mf2;
      if (m1 == m2)
         same = true;
      return same;
   }
   
   // Kill all running jobs - called on program exit
   public static void killRunning() {
      for (int i=0; i<JOBS.size(); ++i) {
         kill(JOBS.get(i));
      }
   }
   
   public static Boolean isActiveJob(jobData job) {
      Boolean active = true;
      if (isDownloadJob(job))
         active = false;
      if (job.type.equals("vrdreview"))
         active = false;
      String[] a = {"atomic", "autotune", "javametadata", "metadata", "push", "remote", "slingbox"};
      for (int i=0; i<a.length; ++i) {
         if (job.type.equals(a[i]))
            active = false;
      }
      return active;
   }
   
   // Given an existing job, return associated job in same family with given type
   public static jobData getJobInFamily(jobData job, String jobType) {
      float mf1 = job.familyId;
      int jobMajor = (int)mf1;
      for (int i=0; i<JOBS.size(); ++i) {
         float mf = JOBS.get(i).familyId;
         int major = (int)mf;
         if (major == jobMajor) {
            if ((JOBS.get(i).type).equals(jobType))
               return JOBS.get(i);
         }
      }
      return null;
   }
   
   private static Boolean isDownloadJob(jobData job) {
      return (job.type.equals("javadownload") ||
              job.type.equals("jdownload_decrypt") ||
              job.type.equals("tdownload_decrypt") ||
              job.type.equals("metadata"));
   }

   // Return true if this job is a VideoRedo COM job that needs to be restricted to 1 at a time
   /*private static Boolean isVideoRedoCOMJob(jobData job) {
      Boolean restricted = false;
      if ( job.type.equals("qsfix") || job.type.equals("adscan") ||
           job.type.equals("adcut") || job.type.equals("vrdencode") ) {
         restricted = true;
      }
      
      // If VrdAllowMultiple is set then don't restrict to 1 at a time
      if (restricted && config.VrdAllowMultiple == 1)
         restricted = false;
      
      return restricted;
   }*/

   // Return true if this job is a VideoRedo GUI job that needs to be restricted to 1 at a time
   /*private static Boolean isVideoRedoGUIJob(jobData job) {
      Boolean restricted = false;
      if ( job.type.equals("vrdreview") ) {
         restricted = true;
      }
      
      // If VrdAllowMultiple is set then don't restrict to 1 at a time
      if (restricted && config.VrdAllowMultiple == 1)
         restricted = false;
      
      return restricted;
   }*/
   
   private static Boolean isVideoRedoJob(jobData job) {
	   Boolean vrd = false;
	   String[] names = {"qsfix", "adscan", "adcut", "vrdencode", "vrdreview"};
	   for (String name : names) {
		   if (job.type.equals(name))
			   vrd = true;
	   }
	   return vrd;
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
   
   public static void saveQueuedJobs() {
      if ( JOBS.isEmpty() ) {
         log.error("There are currently no queued jobs to save.");
      } else {
         try {
            FileOutputStream fos = new FileOutputStream(config.programDir + File.separator + jobDataFile);
            ObjectOutputStream oos = new ObjectOutputStream(fos);

            // Count queued jobs we actually want to save
            int n = 0;
            for (int i=0; i<JOBS.size(); ++i) {
               jobData job = JOBS.get(i);
               if ( job.status.equals("queued") && ! job.type.equals("remote") && ! job.type.equals("slingbox") )
                  n++;
            }
            if (n == 0) {
               log.error("There are currently no queued jobs to save (some jobs can't be saved).");
               oos.close();
               return;
            }
            oos.writeInt(n);
            for (int i=0; i<JOBS.size(); ++i) {
               jobData job = JOBS.get(i);
               if ( job.status.equals("queued") && ! job.type.equals("remote") && ! job.type.equals("slingbox"))
                  oos.writeObject(job);
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
   
	/**
	 * Saves all jobs, including those that are in progress to the data file.
	 * Any in progress jobs will be saved as "queued" like the rest. When the
	 * data file is loaded it will appear to kmttg as though the job has not yet
	 * been processed. Excludes playlist jobs. Mainly targeting file processing
	 * jobs.
	 */
	public static void saveAllJobs() {
		if (JOBS.isEmpty()) {
			log.print("There are currently no queued jobs to save.");
		}

		// We do not want to leave an old queue file laying around to prevent
		// loading old jobs upon next start, so we will simply write a blank
		// file with just a '0' in it. Upon load, it will load nothing.
		try {
			FileOutputStream fos = new FileOutputStream(config.programDir
					+ File.separator + jobDataFile);
			ObjectOutputStream oos = new ObjectOutputStream(fos);

			int queueSize = JOBS.size();
			oos.writeInt(queueSize);

			int savedJobs = 0;
			for (int i = 0; i < queueSize; ++i) {
				jobData job = JOBS.get(i).clone();	// background process removed from job during the clone

				// Exclude non file related jobs
				if (job.type.matches("playlist")) {
					continue;
				} else if (job.type.matches("javaplaylist")) {
					continue;
				} else if (job.type.matches("remote")) {
					continue;
				} else if (job.type.matches("slingbox")) {
               continue;
            }

				// When restoring the jobs, all jobs get added as queued, so no need to do this.
				//updateJobStatus(job, "queued"); // ensure it is "queued" status
												// so kmttg knows to start it
												// over
				oos.writeObject(job);
				savedJobs++;
			}
			oos.flush();
			fos.flush();
			oos.close();
			fos.close();

			// If the daemon/service is running under a different user name than
			// the GUI, make sure the data file is readable by everyone so the
			// file is interchangable with the service and the GUI. ie One can
			// save it in the GUI and then load it in the service.
			// Edit: Only really an issue in *nix, exec chmod for < j1.6
			//new File(config.programDir + File.separator + jobDataFile).setReadable(true, false);
			if (!config.OS.equals("windows")) {
				try {
					Runtime.getRuntime().exec(
							"chmod ugo+r \"" + config.programDir
									+ File.separator + jobDataFile + "\"");
				} catch (Exception e) {
					// quiet...
				}
			}

			if (savedJobs > 0)
				log.print("Saved " + savedJobs + " queued jobs to file: "
						+ jobDataFile);
		} catch (Exception ex) {
			log.error("Failed to save queued jobs to file");
			ex.printStackTrace(System.err);
		}
	}

	/**
	 * Loads all jobs from data file, corresponds to {@link #saveAllJobs()}.
	 * This function does the same as {@link #loadQueuedJobs()}, but appends all
	 * jobs even if the current queue is not empty.
	 * @param delayInSec The number of seconds to wait before loading saved queue
	 */
	public static void loadAllJobs(int delayInSec) {
		String jobFile = config.programDir + File.separator + jobDataFile;
		if (!new File(jobFile).exists())
			return;

		// If it does exist, but don't have permission to read, let the user
		// know
		if (!new File(jobFile).canRead()) {
			log.error("Can't load job queue data file, do not have permission to read.");
			return;
		}
		
		try {
			Thread.sleep(delayInSec*1000);
		} catch (InterruptedException e) {
			log.error("Loading previous job queue got interrupted while waiting for app to load");
		}

		_isLoadingQueue = true;
		
		try {
			FileInputStream fis = new FileInputStream(jobFile);
			ObjectInputStream ois = new ObjectInputStream(fis);
			int n = ois.readInt();
			for (int i = 0; i < n; ++i) {
				try {
					submitNewJob((jobData) ois.readObject());
				} catch (EOFException eof) {
					// reached end of file unexpectedly, haven't decided if we
					// should notify user or not
				}
			}
			ois.close();
			fis.close();
			log.warn("Loaded " + n + " queued jobs from file: " + jobDataFile);
			// Should we delete file after load?
			// file.delete(jobFile);
		} catch (Exception ex) {
			log.error("Failed to load queued jobs from file");
			ex.printStackTrace(System.err);
		}

		_isLoadingQueue = false;
	}
    
   // Identify NPL table items associated with queued/running jobs
   public static void updateNPLjobStatus() {
      if (config.GUIMODE && JOBS != null) {
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
         class backgroundRun implements Runnable {
            Hashtable<String,String> map;
            public backgroundRun(Hashtable<String,String> map) {
               this.map = map;
            }
            @Override public void run() {
               config.gui.updateNPLjobStatus(map);
            }
         }
         Platform.runLater(new backgroundRun(map));
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
      for (String tivoName : auto.getTiVos()) {
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
         
         if ( ! waitForJobs(tivoName) && launchTime == -1 ) {
            // Setup to launch new jobs after user configured sleep time
            launch.put(tivoName, now + autoConfig.CHECK_TIVOS_INTERVAL*60*1000);
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            String time = sdf.format(new Date());
            log.print("\n'" + tivoName + "' AUTO TRANSFERS PROCESSING COMPLETED @ " + time);
            log.print("'" + tivoName + "' AUTO TRANSFERS PROCESSING SLEEPING " + autoConfig.CHECK_TIVOS_INTERVAL + " mins ...");
         }
      }
   }
   
   // Return true if should wait for running/queued jobs to complete, false otherwise
   public static Boolean waitForJobs(String tivoName) {
      if (autoConfig.noJobWait == 0)
         return jobsRemain(tivoName);
      else
         return false;
   }
}
