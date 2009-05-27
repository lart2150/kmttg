package com.tivo.kmttg.main;

import com.tivo.kmttg.task.*;
import com.tivo.kmttg.util.backgroundProcess;

public class jobData {
   // Common to all jobs
   public Integer monitor = null;
   public Long    time = null;
   public String  status = null;
   public String  tivoName = null;
   public String  type = null;
   public String  name = null;
   public Float   familyId = null;
   public String  job_name = null;
   
   // process
   public NowPlaying   process_npl = null;
   public metadata     process_metadata = null;
   public metadataTivo process_metadataTivo = null;
   public download     process_download = null;
   public decrypt      process_decrypt = null;
   public qsfix        process_qsfix = null;
   public comskip      process_comskip = null;
   public adscan       process_adscan = null;
   public comcut       process_comcut = null;
   public adcut        process_adcut = null;
   public captions     process_captions = null;
   public encode       process_encode = null;
   public atomic       process_atomic = null;
   public custom       process_custom = null;
   
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
   
   // job status update related
   public Long duration = null;
   
   // metadata related
   public String url = null;
   public String metaTmpFile = null;
   public String metaFile = null;
   public String episodeNumber = null;
   public String displayMajorNumber = null;
   public String callsign = null;
   public String seriesId = null;
   
   // download related
   public Long tivoFileSize = null;
   String ProgramId = null;
   String title = null;
   
   public String toString() {
      return "{tivoName=" + tivoName + " type=" + type + " status=" + status + " familyId=" + familyId + "}";
   }
   
   public Boolean check() {
      if (type.matches("playlist")) {
         return process_npl.check();
      }      
      else if (type.matches("metadata")) {
         return process_metadata.check();
      }      
      else if (type.matches("metadataTivo")) {
         return process_metadataTivo.check();
      }      
      else if (type.matches("download")) {
         return process_download.check();
      }      
      else if (type.matches("decrypt")) {
         return process_decrypt.check();
      }         
      else if (type.matches("qsfix")) {
         return process_qsfix.check();
      }         
      else if (type.matches("comskip")) {
         return process_comskip.check();
      }         
      else if (type.matches("adscan")) {
         return process_adscan.check();
      }         
      else if (type.matches("comcut")) {
         return process_comcut.check();
      }         
      else if (type.matches("adcut")) {
         return process_adcut.check();
      }         
      else if (type.matches("captions")) {
         return process_captions.check();
      }        
      else if (type.matches("encode")) {
         return process_encode.check();
      }   
      else if (type.matches("atomic")) {
         return process_atomic.check();
      }
      else if (type.matches("custom")) {
         return process_custom.check();
      }
      return false;
   }
   
   public backgroundProcess getProcess() {
      if (type.equals("playlist")) {
         return process_npl.getProcess();
      }
      else if (type.equals("metadata")) {
         return process_metadata.getProcess();
      }
      else if (type.equals("metadataTivo")) {
         return process_metadataTivo.getProcess();
      }
      else if (type.equals("download")) {
         return process_download.getProcess();
      }
      else if (type.equals("decrypt")) {
         return process_decrypt.getProcess();
      }
      else if (type.equals("qsfix")) {
         return process_qsfix.getProcess();
      }
      else if (type.equals("comskip")) {
         return process_comskip.getProcess();
      }
      else if (type.equals("adscan")) {
         return process_adscan.getProcess();
      }
      else if (type.equals("comcut")) {
         return process_comcut.getProcess();
      }
      else if (type.equals("adcut")) {
         return process_adcut.getProcess();
      }
      else if (type.equals("captions")) {
         return process_captions.getProcess();
      }
      else if (type.equals("encode")) {
         return process_encode.getProcess();
      }
      else if (type.equals("atomic")) {
         return process_atomic.getProcess();
      }
      else if (type.equals("custom")) {
         return process_custom.getProcess();
      }
      return null;
   }
   
   public String getOutputFile() {
      String file = "";
      if (type.equals("playlist")) {
         file = tivoName;
      }
      else if (type.equals("metadata")) {
         file = metaFile;
      }
      else if (type.equals("metadataTivo")) {
         file = metaFile;
      }
      else if (type.equals("download")) {
         file = tivoFile;
      }
      else if (type.equals("decrypt")) {
         file = mpegFile;
      }
      else if (type.equals("qsfix")) {
         file = mpegFile_fix;
      }
      else if (type.equals("captions")) {
         file = srtFile;
      }
      else if (type.equals("comskip")) {
         file = edlFile;
      }
      else if (type.equals("adscan")) {
         file = vprjFile;
      }
      else if (type.equals("comcut")) {
         file = mpegFile_cut;
      }
      else if (type.equals("adcut")) {
         file = mpegFile_cut;
      }
      else if (type.equals("encode")) {
         file = encodeFile;
      }
      else if (type.equals("atomic")) {
         file = encodeFile;
      }
      else if (type.equals("custom")) {
         file = "" + familyId;
      }
      return file;
   }
   
   public static int launch(jobData job, int cpuActiveJobs) {
      Boolean success = false;
      int active = 1;
      if (job.type.equals("metadata")) {  
         metadata proc = new metadata(job);
         active = 0; // Not CPU active
         success = proc.launchJob();
      }
      
      else if (job.type.equals("metadataTivo")) {  
         metadataTivo proc = new metadataTivo(job);
         active = 0; // Not CPU active
         success = proc.launchJob();
      }
      
      else if (job.type.equals("download")) {  
         download proc = new download(job);
         active = 0; // Not CPU active
         success = proc.launchJob();
      }
      
      else if (job.type.equals("decrypt")) {  
         decrypt proc = new decrypt(job);
         success = proc.launchJob();
      }
      
      else if (job.type.equals("qsfix")) {  
         qsfix proc = new qsfix(job);
         success = proc.launchJob();
      }
      
      else if (job.type.equals("comskip")) {  
         comskip proc = new comskip(job);
         success = proc.launchJob();
      }
      
      else if (job.type.equals("adscan")) {  
         adscan proc = new adscan(job);
         success = proc.launchJob();
      }
      
      else if (job.type.equals("comcut")) {  
         comcut proc = new comcut(job);
         success = proc.launchJob();
      }
      
      else if (job.type.equals("adcut")) {  
         adcut proc = new adcut(job);
         success = proc.launchJob();
      }
      
      else if (job.type.equals("captions")) {  
         captions proc = new captions(job);
         success = proc.launchJob();
      }
                  
      else if (job.type.equals("encode")) {  
         encode proc = new encode(job);
         success = proc.launchJob();
      }
      
      else if (job.type.equals("atomic")) {  
         atomic proc = new atomic(job);
         success = proc.launchJob();
      }
      
      else if (job.type.equals("custom")) {  
         custom proc = new custom(job);
         success = proc.launchJob();
      }
      
      if (success) {
         cpuActiveJobs += active;
      } else {
         jobMonitor.removeFromJobList(job);
      }
      
      return cpuActiveJobs;
   }
}
