package com.tivo.kmttg.main;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Stack;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.gui.cancelledTable;
import com.tivo.kmttg.gui.deletedTable;
import com.tivo.kmttg.gui.guideTable;
import com.tivo.kmttg.gui.premiereTable;
import com.tivo.kmttg.gui.searchTable;
import com.tivo.kmttg.gui.spTable;
import com.tivo.kmttg.gui.todoTable;
import com.tivo.kmttg.task.*;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.log;

public class jobData implements Serializable, Cloneable {
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
   
   // process (Variable name is required to be process_name)
   public NowPlaying   process_npl = null;
   public javaNowPlaying process_javanpl = null;
   public metadata     process_metadata = null;
   public javametadata process_javametadata = null;
   public metadataTivo process_metadataTivo = null;
   public autotune     process_autotune = null;
   public remote       process_remote = null;
   public download     process_download = null;
   public download_decrypt process_download_decrypt = null;
   public javadownload process_javadownload = null;
   public jdownload_decrypt process_jdownload_decrypt = null;
   public decrypt      process_decrypt = null;
   public qsfix        process_qsfix = null;
   public projectx     process_projectx = null;
   public comskip      process_comskip = null;
   public comskip_review process_comskip_review = null;
   public adscan       process_adscan = null;
   public vrdreview    process_vrdreview = null;
   public comcut       process_comcut = null;
   public projectxcut  process_projectxcut = null;
   public adcut        process_adcut = null;
   public captions     process_captions = null;
   public encode       process_encode = null;
   public vrdencode    process_vrdencode = null;
   public atomic       process_atomic = null;
   public push         process_push = null;
   public custom       process_custom = null;
   public streamfix    process_streamfix = null;
   public slingbox     process_slingbox = null;
   
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
   
   // Encode related
   public boolean hasMoreEncodingJobs = false;	// Are there additional encoding jobs after this that require the source file be kept longer
   
   // download related
   public Long tivoFileSize = null;
   public String ProgramId = null;
   String ProgramId_unique = null;
   String title = null;
   public String offset = null;
   public Long time1=null, time2=null, size1=null, size2=null;
   public String rate = "n/a";
   
   // pyTivo push related
   public String pyTivo_tivo = null;
   
   // TWP delete related
   public Boolean twpdelete = false;
   
   // iPad delete related
   public Boolean ipaddelete = false;
   public Hashtable<String,String> entry = null;
   
   // comskip related
   public String comskipIni = null;
   
   // autotune testing related
   public int autotune_channel_interval = -1;
   public int autotune_button_interval = -1;
   public String autotune_chan1 = null;
   public String autotune_chan2 = null;
   
   // remote jobs & GUI related
   public Boolean remote_todo = false;
   public Boolean remote_upcoming = false;
   public todoTable todo = null;
   
   // demux/remux related
   public Stack<String> demuxFiles = null;
   public String xclFile = null;
   
   public Boolean remote_sp = false;
   public spTable sp = null;
   
   public Boolean remote_spreorder = false;
   public JSONArray remote_orderIds = null;
   
   public Boolean remote_cancel = false;
   public Boolean remote_conflicts = false;
   public cancelledTable cancelled = null;
   
   public Boolean remote_deleted = false;
   public deletedTable deleted = null;
   
   public Boolean remote_search = false;
   public String remote_search_keyword = null;
   public searchTable search = null;
   public int remote_search_max = 200;
   
   public Boolean remote_guideChannels = false;
   public guideTable gTable = null;
   
   public Boolean remote_rnpl = false;
   public JSONArray rnpl = null;
   public Stack<Hashtable<String,String>> auto_entries = null;
   
   public Boolean remote_premiere = false;
   public Boolean remote_channels = false;
   public premiereTable premiere = null;
   
   // Slingbox related
   public String slingbox_perl = null;
   public String slingbox_file = null;
   public String slingbox_dur = null;
   public String slingbox_chan = null;
   
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
         "remote",
         "download",
         "download_decrypt",
         "javadownload",
         "jdownload_decrypt",
         "decrypt",
         "qsfix",
         "projectx",
         "streamfix",
         "comskip",
         "adscan",
         "comskip_review",
         "vrdreview",
         "comcut",
         "projectxcut",
         "adcut",
         "captions",
         "encode",
         "vrdencode",
         "atomic",
         "push",
         "custom",
         "slingbox"
      };
   }
      
   public String toString() {
      return "{source=" + source + " tivoName=" + tivoName + " type=" + type + " status=" + status + " familyId=" + familyId + "}";
   }
   
   // NOTE: Relies on job.type to be a task name under com.tivo.kmttg.task
   public static int launch(jobData job, int cpuActiveJobs) {
      Boolean success = false;
      try {
         Class<?> c = Class.forName(job.typeToTaskClassName());
         Constructor<?> constructor = c.getConstructor(jobData.class);
         Object proc = constructor.newInstance(new Object[] {job});
         Method method = c.getMethod("launchJob");
         success = (Boolean) method.invoke(proc);
      } catch (Exception e) {
         log.error("jobData launch: " + Arrays.toString(e.getStackTrace()));
      }
      
      if (success) {
         if (jobMonitor.isActiveJob(job))
            cpuActiveJobs += 1;
      } else {
         jobMonitor.removeFromJobList(job);
      }
      
      return cpuActiveJobs;
   }
   
   public Boolean check() {
      // Go through all the class variables and for ones called process_* if they
      // are non-null then invoke their check method
      for (Field field : this.getClass().getDeclaredFields()) {
         try {
            if (field.getName().startsWith("process_") && field.get(this) != null) {
               Method method = field.get(this).getClass().getMethod("check");
               return (Boolean) method.invoke(field.get(this));
            }
         } catch (Exception e) {
            log.error("jobData check: " + Arrays.toString(e.getStackTrace()));
         }
      }
      return false;
   }
   
   public backgroundProcess getProcess() {
      // Go through all the class variables and for ones called process_* if they
      // are non-null then invoke their getProcess method
      for (Field field : this.getClass().getDeclaredFields()) {
         try {
            if (field.getName().startsWith("process_") && field.get(this) != null) {
               Method method = field.get(this).getClass().getMethod("getProcess");
               return (backgroundProcess) method.invoke(field.get(this));
            }
         } catch (Exception e) {
            log.error("jobData getProcess: " + Arrays.toString(e.getStackTrace()));
         }
      }
      return null;      
   }

   public void kill() {
      // Go through all the class variables and for ones called process_* if they
      // are non-null then invoke their kill method
      for (Field field : this.getClass().getDeclaredFields()) {
         try {
            if (field.getName().startsWith("process_") && field.get(this) != null) {
               Method method = field.get(this).getClass().getMethod("kill");
               method.invoke(field.get(this));
               field.set(this, null);
            }
         } catch (Exception e) {
            log.error("jobData kill: " + Arrays.toString(e.getStackTrace()));
         }
      }
   }
   
   // Return the kmttg task class name corresponding to current job type string
   private String typeToTaskClassName() {
      String className = type;
      if (className.equals("playlist"))
         className = "NowPlaying";
      if (className.equals("javaplaylist"))
         className = "javaNowPlaying";
      return "com.tivo.kmttg.task." + className;
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
      else if (type.equals("remote")) {
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
      else if (type.equals("projectx")) {
         file = mpegFile_fix;
      }
      else if (type.equals("captions")) {
         file = srtFile;
      }
      else if (type.equals("comskip")) {
         file = edlFile;
         if (vprjFile != null)
            file = vprjFile;
         if (xclFile != null)
            file = xclFile;
      }
      else if (type.equals("adscan")) {
         file = vprjFile;
      }
      else if (type.equals("comskip_review")) {
         file = edlFile;
         if (vprjFile != null)
            file = vprjFile;
         if (xclFile != null)
            file = xclFile;
      }
      else if (type.equals("vrdreview")) {
         file = vprjFile;
      }
      else if (type.equals("comcut")) {
         file = mpegFile_cut;
      }
      else if (type.equals("projectxcut")) {
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
      else if (type.equals("slingbox")) {
         file = slingbox_file;
      }
      return file;
   }
      
	/**
	 * Does a shallow copy of this instance so the fields can be modified
	 * without touching the original
	 */
	public jobData clone() {
		return (jobData) clone(this);
	}
   
   @SuppressWarnings("unchecked")
   private static Object clone(Object o)
   {
      Object clone = null;

      try
      {
         clone = o.getClass().newInstance();
      }
      catch (InstantiationException e)
      {
         e.printStackTrace();
      }
      catch (IllegalAccessException e)
      {
         e.printStackTrace();
      }

      // Walk up the superclass hierarchy
      for (Class obj = o.getClass();
      !obj.equals(Object.class);
      obj = obj.getSuperclass())
      {
         java.lang.reflect.Field[] fields = obj.getDeclaredFields();
         for (int i = 0; i < fields.length; i++)
         {
            fields[i].setAccessible(true);
            try
            {
               // for each class/superclass, copy all fields
               // from this object to the clone
               // exclude copying the process
               if (fields[i].getName().contains("process"))
                  fields[i].set(clone, null);
               else
                  fields[i].set(clone, fields[i].get(o));
            }
            catch (IllegalArgumentException e){}
            catch (IllegalAccessException e){}
         }
      }
      return clone;
   }
}
