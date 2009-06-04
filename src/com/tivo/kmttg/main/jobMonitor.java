package com.tivo.kmttg.main;

import java.io.File;
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
   public static int JOB_COUNT = 0;
   public static int JOB = 0;
   public static int FAMILY_ID = 0;
   
   // Master job monitor that runs on timer. Key functions:
   //   * Check status of active job and take action if running job is completed
   //   * Launch a job in queue if appropriate
   //     - Restrict to 1 download per Tivo at a time
   //     - Restrict CPU intensive jobs to defined 'MaxJobs' (default of 2)
   //     - Order dependancy of jobs operating on same program   
   static void monitor(gui gui, Timer timer) {
      Stack<jobData> running = new Stack<jobData>();
      Stack<jobData> queued  = new Stack<jobData>();
      jobData job;
      if (config.GUI && gui == null && timer != null) {
         timer.stop();
         return;
      }
      
      tivoBeaconUpdate();
      
      if ( JOBS != null && ! JOBS.isEmpty() ) {
         for (int i=0; i<JOBS.size(); ++i) {
            job = JOBS.get(i);
                        
            // Add running jobs to running list so we can check them
            if ( job.status.equals("running") ) {
               running.add(job);
            }
            
            // Add non playlist queued jobs to queued list so we can decide when to launch
            if ( job.status.equals("queued") && ! job.type.equals("playlist") ) {
               queued.add(job);
            }
            
            // queued playlist jobs launch right away, other jobs go to queue
            if ( job.type.equals("playlist") && job.status.matches("queued") ) {
               job.process_npl = new NowPlaying(job);
               if (job.process_npl != null) {
                  job.process_npl.job = job; // This is used by npl check
                  if (job.process_npl.start()) {
                     job.status = "running";
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
      int cpuActiveJobs = 0;
      Hashtable<String,Integer> tivoDownload = new Hashtable<String,Integer>();
      for (int i=0; i<running.size(); i++) {
         job = running.get(i);
         if ( job.type.equals("download") || job.type.equals("metadata") ) {
            if ( ! tivoDownload.containsKey(job.tivoName) ) {
               tivoDownload.put(job.tivoName, 0);
            }
            tivoDownload.put(job.tivoName, tivoDownload.get(job.tivoName)+1);
         } else {
            cpuActiveJobs++;
         }
      }
      
      // See if we can launch any queued jobs
      for (int i=0; i<queued.size(); i++) {
         job = queued.get(i);
         debug.print("job=" + job);
         
         // If there are prior job types in the family queued don't schedule yet
         if ( priorInFamilyExist(job.familyId, famList) ) {
            continue;
         }
         
         // Only 1 download at a time per Tivo allowed
         if (tivoDownload.size() > 0) {
            if ( job.type.equals("download") || job.type.equals("metadata") ) {
               if ( tivoDownload.containsKey(job.tivoName) ) {
                  if (tivoDownload.get(job.tivoName) > 0) {
                     continue;
                  }
               }
            }
         }
         
         // Don't run more than 'MaxJobs' active jobs at a time
         if (cpuActiveJobs >= config.MaxJobs) continue;

         // OK to launch job
         cpuActiveJobs = jobData.launch(job, cpuActiveJobs);
         
         // Update tivoDownload hash if appropriate
         // (to prevent multiple queued downloads for same tivo to launch at once)
         if ( job.type.equals("download") || job.type.equals("metadata") ) {
            if (job.status.equals("running")) {
               if ( ! tivoDownload.containsKey(job.tivoName) ) {
                  tivoDownload.put(job.tivoName, 0);
               }
               tivoDownload.put(job.tivoName, tivoDownload.get(job.tivoName)+1);
            }
         }
      }
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
   public static Boolean createSubFolders(String f) {
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
         return checkDiskSpace(baseDir);
      }
      
      return true;
   }
   
   public static Boolean checkDiskSpace(String dir) {
      debug.print("dir=" + dir);
      // Min available expected (in bytes)
      long min = (long) (config.LowSpaceSize * Math.pow(2, 30));
      
      // Get space available (in bytes)
      long space = file.freeSpace(dir);
      
      if (space <= min) {
         String message = String.format(
            "%s: Space available = %.2f GB is less than min = %.2f GB",
            dir, space/Math.pow(2,30), min/Math.pow(2, 30)
         );
         log.error(message);
         return false;
      }
      return true;
   }
   
   static void addToJobList(jobData job) {
      debug.print("job=" + job);
      // Prevent duplicate jobs from being queued to the job list
      for (int i=0; i<JOBS.size(); ++i) {
         if (job.type.equals(JOBS.get(i).type)) {
            if (job.getOutputFile().equals(JOBS.get(i).getOutputFile())) return;
         }
      }      
      
      // Create unique job name
      job.job_name = "job" + JOB_COUNT;
      JOB_COUNT++;
      
      // Update GUI Job Monitor
      if (config.GUI) {
         String s = "Tivo=" + job.tivoName;
         if (! job.type.equals("playlist") && ! job.type.equals("custom"))
            s += "---Output=" + job.getOutputFile();         
         if (config.GUI) config.gui.jobTab_AddJobMonitorRow(job, s);
      }
      
      // Add job to master job list
      JOBS.add(job);
   }
   
   public static void removeFromJobList(jobData job) {
      debug.print("job=" + job);
      Stack<jobData> new_jobs = new Stack<jobData>();
      for (int i=0; i<JOBS.size(); ++i) {
         if ( job.job_name != null && JOBS.get(i).job_name != null ) {
            if ( ! JOBS.get(i).job_name.matches(job.job_name) ) {
               new_jobs.add(JOBS.get(i));
            }
         }
      }
      JOBS = new_jobs;
      
      // Remove entry from job monitor
      if (config.GUI) config.gui.jobTab_RemoveJobMonitorRow(job);      
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
   
   public static Boolean isFirstJobInMonitor(jobData job) {
      if ( config.GUI && config.gui.jobTab_GetRowData(0) == job )
         return true;
      return false;
   }
  

   // Return elapsed time of a job in hh:mm:ss format
   public static String getElapsedTime(long start) {
      long elapsed = (new Date().getTime() - start)/1000;
      int hours = (int)(elapsed/3600);
      int mins  = (int)((elapsed/60) - (hours*60));
      int secs  = (int)(elapsed % 60);
      return String.format("%02d:%02d:%02d",hours,mins,secs);
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
         
         specs.put("startFile", tivoFileName.buildTivoFileName(entry));
      }

      // Determine which actions are enabled
      String startFile     = (String)specs.get("startFile");
      Boolean metadata     = (Boolean)specs.get("metadata");
      Boolean metadataTivo = (Boolean)specs.get("metadataTivo");
      Boolean decrypt      = (Boolean)specs.get("decrypt");
      Boolean qsfix        = (Boolean)specs.get("qsfix");
      Boolean comskip      = (Boolean)specs.get("comskip");
      Boolean comcut       = (Boolean)specs.get("comcut");
      Boolean captions     = (Boolean)specs.get("captions");
      Boolean encode       = (Boolean)specs.get("encode");
      Boolean custom       = (Boolean)specs.get("custom");
      
      if (metadataTivo) {
         // In FILES mode can only get metadata from .tivo files
         if ( ! startFile.toLowerCase().endsWith(".tivo") ) metadataTivo = false;
      }
      
      if (decrypt && mode.equals("FILES")) {
         // In FILES mode can only run decrypt on .tivo files
         if ( ! startFile.toLowerCase().endsWith(".tivo") ) decrypt = false;
      }
      
      // Init names
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
         tivoName = config.tivoName;
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
      
      // Check task dependencies and enable prior tasks if necessary
      
      // encode requires mpegFile or mpegFile_cut which may require at minimum decrypt
      if (encode) {
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
               if ( ! file.isFile(string.replaceSuffix(edlFile, ".VPrj")) ) {
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
                           
      // Launch jobs depending on selections
      float familyId = FAMILY_ID++;
      Hashtable<String,String> entry = (Hashtable<String,String>)specs.get("entry");
      if ( mode.equals("Download") ) {         
         if (metadata) {
            jobData job = new jobData();
            job.tivoName           = tivoName;
            job.type               = "metadata";
            job.name               = "curl";
            job.familyId           = familyId;
            job.metaFile           = metaFile;
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
         
         familyId += 0.1;
         jobData job = new jobData();
         job.tivoName     = tivoName;
         job.type         = "download";
         job.name         = "curl";
         job.familyId     = familyId;
         job.tivoFile     = tivoFile;
         job.url          = entry.get("url");
         job.tivoFileSize = Long.parseLong(entry.get("size"));
         if (entry.containsKey("ProgramId")) {
            job.ProgramId = entry.get("ProgramId");
         }
         if (entry.containsKey("title")) {
            job.title = entry.get("title");
         }
         submitNewJob(job);
      }
      
      if (metadataTivo) {
         jobData job = new jobData();
         job.tivoName           = tivoName;
         job.type               = "metadataTivo";
         job.name               = "tivodecode";
         job.familyId           = familyId;
         job.tivoFile           = tivoFile;
         job.metaFile           = metaFile;
         submitNewJob(job);
      }
      
      if (decrypt) {
         familyId += 0.1;
         jobData job = new jobData();
         job.tivoName     = tivoName;
         job.type         = "decrypt";
         job.name         = config.tivodecode;
         job.familyId     = familyId;
         job.tivoFile     = tivoFile;
         job.mpegFile     = mpegFile;
         submitNewJob(job);
      }
      
      if (qsfix) {
         familyId += 0.1;
         jobData job = new jobData();
         job.tivoName     = tivoName;
         job.type         = "qsfix";
         job.name         = config.VRD;
         job.familyId     = familyId;
         job.mpegFile     = mpegFile;
         job.mpegFile_fix = mpegFile_fix;
         submitNewJob(job);
      }
      
      if (comskip) {
         if (file.isDir(config.VRD) && config.UseAdscan == 1) {
            familyId += 0.1;
            jobData job = new jobData();
            job.tivoName     = tivoName;
            job.type         = "adscan";
            job.name         = config.VRD;
            job.familyId     = familyId;
            job.mpegFile     = mpegFile;
            job.vprjFile     = string.replaceSuffix(edlFile, ".VPrj");
            submitNewJob(job);
         } else {
            familyId += 0.1;
            jobData job = new jobData();
            job.tivoName     = tivoName;
            job.type         = "comskip";
            job.name         = config.comskip;
            job.familyId     = familyId;
            job.mpegFile     = mpegFile;
            job.edlFile      = edlFile;
            if (file.isDir(config.VRD))
               job.vprjFile = string.replaceSuffix(edlFile, ".VPrj");
            submitNewJob(job);            
         }
      }
      
      if (comcut) {
         if ( file.isFile(config.VRD + File.separator + "vp.vbs") ) {
            familyId += 0.1;
            jobData job = new jobData();
            job.tivoName     = tivoName;
            job.type         = "adcut";
            job.name         = config.VRD;
            job.familyId     = familyId;
            job.mpegFile     = mpegFile;
            job.mpegFile_cut = mpegFile_cut;
            job.edlFile      = edlFile;
            submitNewJob(job);
         } else {
            familyId += 0.1;
            jobData job = new jobData();
            job.tivoName     = tivoName;
            job.type         = "comcut";
            job.name         = config.mencoder;
            job.familyId     = familyId;
            job.mpegFile     = mpegFile;
            job.mpegFile_cut = mpegFile_cut;
            job.edlFile      = edlFile;
            submitNewJob(job);            
         }
      }
      
      if (captions) {
         familyId += 0.1;
         jobData job = new jobData();
         job.tivoName     = tivoName;
         job.type         = "captions";
         job.name         = config.t2extract;
         job.familyId     = familyId;
         job.videoFile    = videoFile;
         job.srtFile      = srtFile;
         submitNewJob(job);
      }
      
      if (encode) {
         familyId += 0.1;
         jobData job = new jobData();
         job.tivoName     = tivoName;
         job.type         = "encode";
         job.name         = encodeName;
         job.familyId     = familyId;
         job.encodeName   = encodeName;
         job.mpegFile     = mpegFile;
         job.mpegFile_cut = mpegFile_cut;
         job.encodeFile   = encodeFile;
         submitNewJob(job);
      }
      
      if (custom) {
         familyId += 0.1;
         jobData job = new jobData();
         job.tivoName     = tivoName;
         job.type         = "custom";
         job.name         = config.customCommand;
         job.familyId     = familyId;
         job.tivoName     = tivoName;
         job.metaFile     = metaFile;
         job.mpegFile     = mpegFile;
         job.mpegFile_cut = mpegFile_cut;
         job.srtFile      = srtFile;
         job.encodeFile   = encodeFile;
         submitNewJob(job);
      }
      
   }

   // Cancel and/or kill given job
   public static void kill(jobData job) {
      if (job.type != null) {
         // Kill job if running
         if (job.status.equals("running")) {
            log.warn("Killing '" + job.type + "' job: " + job.getProcess().toString());
            job.getProcess().kill();
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
   
   // Kill all running jobs - called on program exit
   public static void killRunning() {
      jobData job;
      for (int i=0; i<JOBS.size(); ++i) {
         job = JOBS.get(i);
         if ( job.status.equals("running") ) job.getProcess().kill();
      }
   }

   // Listen on tivo_beacon for any newly detected tivos
   private static void tivoBeaconUpdate() {
      if (config.CheckBeacon == 1 && config.tivo_beacon != null) {
         Hashtable<String,String> b = config.tivo_beacon.listen();
         if (b != null) {
            Stack<String> tivoNames = config.getTivoNames();
            Boolean add = true;
            for (int i=0; i<tivoNames.size(); ++i) {
               if ( tivoNames.get(i).matches(b.get("machine")) ) {
                  add = false;
               }
            }
            if (add) {
               config.addTivo(b);
            }
         }
      }
   }
   
}
