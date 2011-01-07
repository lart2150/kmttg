package com.tivo.kmttg.main;

import java.io.Serializable;

import com.tivo.kmttg.task.*;
import com.tivo.kmttg.util.backgroundProcess;

public class jobData implements Serializable {
   private static final long serialVersionUID = 1L;
   // Common to all jobs
   public String  source = null;
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
   public javaNowPlaying process_javanpl = null;
   public metadata     process_metadata = null;
   public javametadata process_javametadata = null;
   public metadataTivo process_metadataTivo = null;
   public autotune     process_autotune = null;
   public download     process_download = null;
   public download_decrypt process_download_decrypt = null;
   public javadownload process_javadownload = null;
   public jdownload_decrypt process_jdownload_decrypt = null;
   public decrypt      process_decrypt = null;
   public qsfix        process_qsfix = null;
   public comskip      process_comskip = null;
   public adscan       process_adscan = null;
   public vrdreview    process_vrdreview = null;
   public comcut       process_comcut = null;
   public adcut        process_adcut = null;
   public captions     process_captions = null;
   public encode       process_encode = null;
   public vrdencode    process_vrdencode = null;
   public atomic       process_atomic = null;
   public push         process_push = null;
   public custom       process_custom = null;
   public streamfix    process_streamfix = null;
   
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
   
   // Delayed launch variables
   public Long launch_time = null;
   public int launch_tries = 1;
   
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
   String ProgramId_unique = null;
   String title = null;
   public Long time1=null, time2=null, size1=null, size2=null;
   public String rate = "n/a";
   
   // pyTivo push related
   public String pyTivo_tivo = null;
   
   // TWP delete related
   public Boolean twpdelete = false;
   
   // comskip related
   public String comskipIni = null;
   
   // autotune testing related
   public int autotune_channel_interval = -1;
   public int autotune_button_interval = -1;
   public String autotune_chan1 = null;
   public String autotune_chan2 = null;
   
   // NOTE: This used for job insertion purposes
   // ** JOB ORDER IS VERY IMPORTANT **
   public static String[] allTaskNames() {
      return new String[] {
         "playlist",
         "javaplaylist",
         "metadata",
         "javametadata",
         "metadataTivo",
         "autotune",
         "download",
         "download_decrypt",
         "javadownload",
         "jdownload_decrypt",
         "decrypt",
         "qsfix",
         "streamfix",
         "comskip",
         "adscan",
         "vrdreview",
         "comcut",
         "adcut",
         "captions",
         "encode",
         "vrdencode",
         "atomic",
         "push",
         "custom"
      };
   }
      
   public String toString() {
      return "{source=" + source + " tivoName=" + tivoName + " type=" + type + " status=" + status + " familyId=" + familyId + "}";
   }
   
   public Boolean check() {
      if (type.matches("playlist")) {
         return process_npl.check();
      }      
      else if (type.matches("javaplaylist")) {
         return process_javanpl.check();
      }      
      else if (type.matches("metadata")) {
         return process_metadata.check();
      }      
      else if (type.matches("javametadata")) {
         return process_javametadata.check();
      }      
      else if (type.matches("metadataTivo")) {
         return process_metadataTivo.check();
      }      
      else if (type.matches("autotune")) {
         return process_autotune.check();
      }      
      else if (type.matches("download")) {
         return process_download.check();
      }      
      else if (type.matches("download_decrypt")) {
         return process_download_decrypt.check();
      }      
      else if (type.matches("javadownload")) {
         return process_javadownload.check();
      }      
      else if (type.matches("jdownload_decrypt")) {
         return process_jdownload_decrypt.check();
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
      else if (type.matches("vrdreview")) {
         return process_vrdreview.check();
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
      else if (type.matches("vrdencode")) {
         return process_vrdencode.check();
      }   
      else if (type.matches("atomic")) {
         return process_atomic.check();
      }
      else if (type.matches("custom")) {
         return process_custom.check();
      }
      else if (type.matches("push")) {
         return process_push.check();
      }
      else if (type.matches("streamfix")) {
         return process_streamfix.check();
      }         
      return false;
   }
      
   public backgroundProcess getProcess() {
      if (type.equals("playlist")) {
         return process_npl.getProcess();
      }
      else if (type.equals("javaplaylist")) {
         return process_javanpl.getProcess();
      }
      else if (type.equals("metadata")) {
         return process_metadata.getProcess();
      }
      else if (type.equals("javametadata")) {
         return process_javametadata.getProcess();
      }
      else if (type.equals("metadataTivo")) {
         return process_metadataTivo.getProcess();
      }
      else if (type.equals("autotune")) {
         return process_autotune.getProcess();
      }
      else if (type.equals("download")) {
         return process_download.getProcess();
      }
      else if (type.equals("download_decrypt")) {
         return process_download_decrypt.getProcess();
      }
      else if (type.equals("javadownload")) {
         return process_javadownload.getProcess();
      }
      else if (type.equals("jdownload_decrypt")) {
         return process_jdownload_decrypt.getProcess();
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
      else if (type.equals("vrdreview")) {
         return process_vrdreview.getProcess();
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
      else if (type.equals("vrdencode")) {
         return process_vrdencode.getProcess();
      }
      else if (type.equals("atomic")) {
         return process_atomic.getProcess();
      }
      else if (type.equals("push")) {
         return process_push.getProcess();
      }
      else if (type.equals("custom")) {
         return process_custom.getProcess();
      }
      else if (type.equals("streamfix")) {
         return process_streamfix.getProcess();
      }
      return null;
   }
   
   public String getInputFile() {
      String file = "";
      if (type.equals("playlist")) {
         file = tivoName;
      }
      else if (type.equals("javaplaylist")) {
         file = tivoName;
      }
      else if (type.equals("metadata")) {
         file = tivoName;
      }
      else if (type.equals("javametadata")) {
         file = tivoName;
      }
      else if (type.equals("metadataTivo")) {
         file = tivoFile;
      }
      else if (type.equals("autotune")) {
         file = tivoName;
      }
      else if (type.equals("download")) {
         file = url;
      }
      else if (type.equals("download_decrypt")) {
         file = url;
      }
      else if (type.equals("javadownload")) {
         file = url;
      }
      else if (type.equals("jdownload_decrypt")) {
         file = url;
      }
      else if (type.equals("decrypt")) {
         file = tivoFile;
      }
      else if (type.equals("qsfix")) {
         file = mpegFile;
      }
      else if (type.equals("captions")) {
         file = videoFile;
      }
      else if (type.equals("comskip")) {
         file = mpegFile;
      }
      else if (type.equals("adscan")) {
         file = mpegFile;
      }
      else if (type.equals("vrdreview")) {
         file = vprjFile;
      }
      else if (type.equals("comcut")) {
         file = mpegFile;
      }
      else if (type.equals("adcut")) {
         file = vprjFile;
      }
      else if (type.equals("encode")) {
         file = inputFile;
      }
      else if (type.equals("vrdencode")) {
         file = inputFile;
      }
      else if (type.equals("atomic")) {
         file = encodeFile;
      }
      else if (type.equals("push")) {
         file = videoFile;
      }
      else if (type.equals("custom")) {
         file = tivoFile;
      }
      else if (type.equals("streamfix")) {
         file = mpegFile;
      }
      return file;
   }
   
   public String getOutputFile() {
      String file = "";
      if (type.equals("playlist")) {
         file = tivoName;
      }
      else if (type.equals("javaplaylist")) {
         file = tivoName;
      }
      else if (type.equals("metadata")) {
         file = metaFile;
      }
      else if (type.equals("javametadata")) {
         file = metaFile;
      }
      else if (type.equals("metadataTivo")) {
         file = metaFile;
      }
      else if (type.equals("autotune")) {
         file = tivoName;
      }
      else if (type.equals("download")) {
         file = tivoFile;
      }
      else if (type.equals("download_decrypt")) {
         file = mpegFile;
      }
      else if (type.equals("javadownload")) {
         file = tivoFile;
      }
      else if (type.equals("jdownload_decrypt")) {
         file = mpegFile;
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
         if (vprjFile != null)
            file = vprjFile;
      }
      else if (type.equals("adscan")) {
         file = vprjFile;
      }
      else if (type.equals("vrdreview")) {
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
      else if (type.equals("vrdencode")) {
         file = encodeFile;
      }
      else if (type.equals("atomic")) {
         file = encodeFile;
      }
      else if (type.equals("push")) {
         file = videoFile;
      }
      else if (type.equals("custom")) {
    	  // NOTE: Must assign an output file of some sort to prevent job duplication
    	  // across different Tivos
         file = mpegFile;
      }
      else if (type.equals("streamfix")) {
         file = mpegFile_fix;
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
      
      else if (job.type.equals("javametadata")) {  
         javametadata proc = new javametadata(job);
         active = 0; // Not CPU active
         success = proc.launchJob();
      }
      
      else if (job.type.equals("metadataTivo")) {  
         metadataTivo proc = new metadataTivo(job);
         active = 0; // Not CPU active
         success = proc.launchJob();
      }
      
      else if (job.type.equals("autotune")) {  
         autotune proc = new autotune(job);
         success = proc.launchJob();
      }
      
      else if (job.type.equals("download")) {  
         download proc = new download(job);
         active = 0; // Not CPU active
         success = proc.launchJob();
      }
      
      else if (job.type.equals("download_decrypt")) {  
         download_decrypt proc = new download_decrypt(job);
         active = 0; // Not CPU active
         success = proc.launchJob();
      }
      
      else if (job.type.equals("javadownload")) {  
         javadownload proc = new javadownload(job);
         active = 0; // Not CPU active
         success = proc.launchJob();
      }
      
      else if (job.type.equals("jdownload_decrypt")) {  
         jdownload_decrypt proc = new jdownload_decrypt(job);
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
      
      else if (job.type.equals("vrdreview")) {  
         vrdreview proc = new vrdreview(job);
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
      
      else if (job.type.equals("vrdencode")) {  
         vrdencode proc = new vrdencode(job);
         success = proc.launchJob();
      }
      
      else if (job.type.equals("atomic")) {  
         atomic proc = new atomic(job);
         success = proc.launchJob();
      }
      
      else if (job.type.equals("push")) {  
         push proc = new push(job);
         success = proc.launchJob();
      }
      
      else if (job.type.equals("custom")) {  
         custom proc = new custom(job);
         success = proc.launchJob();
      }
      
      else if (job.type.equals("streamfix")) {  
         streamfix proc = new streamfix(job);
         success = proc.launchJob();
      }
      
      if (success) {
         cpuActiveJobs += active;
      } else {
         jobMonitor.removeFromJobList(job);
      }
      
      return cpuActiveJobs;
   }

   
   public void kill() {
      if (type.equals("playlist")) {
         process_npl.kill();
      }
      else if (type.equals("javaplaylist")) {
         process_javanpl.kill();
      }
      else if (type.equals("metadata")) {
         process_metadata.kill();
      }
      else if (type.equals("javametadata")) {
         process_javametadata.kill();
      }
      else if (type.equals("metadataTivo")) {
         process_metadataTivo.kill();
      }
      else if (type.equals("autotune")) {
         process_autotune.kill();
      }
      else if (type.equals("download")) {
         process_download.kill();
      }
      else if (type.equals("download_decrypt")) {
         process_download_decrypt.kill();
      }
      else if (type.equals("javadownload")) {
         process_javadownload.kill();
      }
      else if (type.equals("jdownload_decrypt")) {
         process_jdownload_decrypt.kill();
      }
      else if (type.equals("decrypt")) {
         process_decrypt.kill();
      }
      else if (type.equals("qsfix")) {
         process_qsfix.kill();
      }
      else if (type.equals("comskip")) {
         process_comskip.kill();
      }
      else if (type.equals("adscan")) {
         process_adscan.kill();
      }
      else if (type.equals("vrdreview")) {
         process_vrdreview.kill();
      }
      else if (type.equals("comcut")) {
         process_comcut.kill();
      }
      else if (type.equals("adcut")) {
         process_adcut.kill();
      }
      else if (type.equals("captions")) {
         process_captions.kill();
      }
      else if (type.equals("encode")) {
         process_encode.kill();
      }
      else if (type.equals("vrdencode")) {
         process_vrdencode.kill();
      }
      else if (type.equals("atomic")) {
         process_atomic.kill();
      }
      else if (type.equals("push")) {
         process_push.kill();
      }
      else if (type.equals("custom")) {
         process_custom.kill();
      }
      else if (type.equals("streamfix")) {
         process_streamfix.kill();
      }
   }

}
