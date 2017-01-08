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

import java.io.Serializable;
import java.util.Hashtable;
import java.util.Stack;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.table.cancelledTable;
import com.tivo.kmttg.gui.table.channelsTable;
import com.tivo.kmttg.gui.table.deletedTable;
import com.tivo.kmttg.gui.table.guideTable;
import com.tivo.kmttg.gui.table.premiereTable;
import com.tivo.kmttg.gui.table.searchTable;
import com.tivo.kmttg.gui.table.spTable;
import com.tivo.kmttg.gui.table.streamTable;
import com.tivo.kmttg.gui.table.thumbsTable;
import com.tivo.kmttg.gui.table.todoTable;
import com.tivo.kmttg.task.NowPlaying;
import com.tivo.kmttg.task.adcut;
import com.tivo.kmttg.task.adscan;
import com.tivo.kmttg.task.atomic;
import com.tivo.kmttg.task.autotune;
import com.tivo.kmttg.task.baseTask;
import com.tivo.kmttg.task.captions;
import com.tivo.kmttg.task.comskip;
import com.tivo.kmttg.task.comskip_review;
import com.tivo.kmttg.task.custom;
import com.tivo.kmttg.task.decrypt;
import com.tivo.kmttg.task.dsd;
import com.tivo.kmttg.task.encode;
import com.tivo.kmttg.task.ffcut;
import com.tivo.kmttg.task.fffix;
import com.tivo.kmttg.task.javaNowPlaying;
import com.tivo.kmttg.task.javadownload;
import com.tivo.kmttg.task.javametadata;
import com.tivo.kmttg.task.jdownload_decrypt;
import com.tivo.kmttg.task.metadataTivo;
//import com.tivo.kmttg.task.push;
import com.tivo.kmttg.task.qsfix;
import com.tivo.kmttg.task.remote;
import com.tivo.kmttg.task.skipdetect;
import com.tivo.kmttg.task.slingbox;
import com.tivo.kmttg.task.tdownload_decrypt;
import com.tivo.kmttg.task.tivolibre;
import com.tivo.kmttg.task.vrdencode;
import com.tivo.kmttg.task.vrdreview;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.log;

public class jobData implements Serializable, Cloneable {
   private static final long serialVersionUID = 1L;   
   public baseTask process = null;
   
   // Common to all jobs
   public String startFile = null;
   public String  source = null;
   public Integer monitor = null;
   public Long    time = null;
   public String  status = null;
   public String  tivoName = null;
   public String  type = null;
   public String  name = null;
   public Float   familyId = null;
   public String  job_name = null;
   
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
   public String title = null;
   public Long time1=null, time2=null, size1=null, size2=null;
   public String rate = "n/a";
   public Boolean getURLs = false;
   public Boolean partiallyViewed = false;
   public Integer download_duration = 0;
   public Integer TSDownload = 0;

   // AutoSkip related
   public long limit = 0;
   public String offset = null;
   public String SkipPoint = null;
   public String contentId = null;
   public String offerId = null;
   public Boolean autoskip = false;
   public Boolean exportSkip = false;

   // pyTivo push related
   //public String pyTivo_tivo = null;
   
   // TWP delete related
   public Boolean twpdelete = false;
   
   // rpc delete related
   public Boolean rpcdelete = false;
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
   
   // qsfix related (value can be "decrypt" if using qsfix as decrypt with VRD)
   public String qsfix_mode = "qsfix";
   
   public Boolean remote_sp = false;
   public spTable sp = null;
   
   public Boolean remote_stream = false;
   public streamTable stream = null;
   
   public Boolean remote_thumbs = false;
   public thumbsTable thumbs = null;
   
   public Boolean remote_channelsTable = false;
   public channelsTable channelsTable = null;
   
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
   
   public Boolean remote_adv_search = false;
   public JSONObject remote_adv_search_json = null;
   public String[] remote_adv_search_chans = null;
   public String remote_adv_search_cat = null;
   
   public Boolean remote_guideChannels = false;
   public guideTable gTable = null;
   
   public Boolean remote_rnpl = false;
   public JSONArray rnpl = null;
   public Stack<Hashtable<String,String>> auto_entries = null;
   
   public Boolean remote_premiere = false;
   public Boolean remote_channels = false;
   public premiereTable premiere = null;
      
   // Slingbox related
   public Boolean slingbox_raw = false;
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
         "javametadata",
         "metadataTivo",
         "autotune",
         "remote",
         "skipdetect",
         "javadownload",
         "jdownload_decrypt",
         "tdownload_decrypt",
         "decrypt",
         "tivolibre",
         "dsd",
         "qsfix",
         "fffix",
         "comskip",
         "adscan",
         "comskip_review",
         "vrdreview",
         "ffcut",
         "adcut",
         "captions",
         "encode",
         "vrdencode",
         "atomic",
         "custom",
         //"push",
         "slingbox"
      };
   }
      
   public String toString() {
      return "{source=" + source + " tivoName=" + tivoName + " type=" + type + " status=" + status + " familyId=" + familyId + "}";
   }
   
   // NOTE: With a couple of exceptions relies on job.type to be a task name under com.tivo.kmttg.task
   public static int launch(jobData job, int cpuActiveJobs) {
      if (job.type.equals("playlist"))
         job.process = new NowPlaying(job);
      if (job.type.equals("javaplaylist"))
         job.process = new javaNowPlaying(job);
      if (job.type.equals("adcut"))
         job.process = new adcut(job);
      if (job.type.equals("adscan"))
         job.process = new adscan(job);
      if (job.type.equals("atomic"))
         job.process = new atomic(job);
      if (job.type.equals("autotune"))
         job.process = new autotune(job);
      if (job.type.equals("captions"))
         job.process = new captions(job);
      if (job.type.equals("comskip_review"))
         job.process = new comskip_review(job);
      if (job.type.equals("comskip"))
         job.process = new comskip(job);
      if (job.type.equals("custom"))
         job.process = new custom(job);
      if (job.type.equals("decrypt"))
         job.process = new decrypt(job);
      if (job.type.equals("tivolibre"))
         job.process = new tivolibre(job);
      if (job.type.equals("skipdetect"))
         job.process = new skipdetect(job);
      if (job.type.equals("dsd"))
         job.process = new dsd(job);
      if (job.type.equals("encode"))
         job.process = new encode(job);
      if (job.type.equals("javadownload"))
         job.process = new javadownload(job);
      if (job.type.equals("javametadata"))
         job.process = new javametadata(job);
      if (job.type.equals("jdownload_decrypt"))
         job.process = new jdownload_decrypt(job);
      if (job.type.equals("tdownload_decrypt"))
         job.process = new tdownload_decrypt(job);
      if (job.type.equals("metadataTivo"))
         job.process = new metadataTivo(job);
      if (job.type.equals("fffix"))
         job.process = new fffix(job);
      if (job.type.equals("ffcut"))
         job.process = new ffcut(job);
      //if (job.type.equals("push"))
      //   job.process = new push(job);
      if (job.type.equals("qsfix"))
         job.process = new qsfix(job);
      if (job.type.equals("remote"))
         job.process = new remote(job);
      if (job.type.equals("slingbox"))
         job.process = new slingbox(job);
      if (job.type.equals("vrdencode"))
         job.process = new vrdencode(job);
      if (job.type.equals("vrdreview"))
         job.process = new vrdreview(job);
      if (job.process == null) {
         log.error("job.type=" + job.type + " not mapped");
         jobMonitor.removeFromJobList(job);
         return cpuActiveJobs;
      }
      Boolean success = job.process.launchJob();      
      if (success) {
         if (jobMonitor.isActiveJob(job))
            cpuActiveJobs += 1;
      } else {
         jobMonitor.removeFromJobList(job);
      }
      
      return cpuActiveJobs;
   }
   
   public Boolean check() {
      return this.process.check();
   }
   
   public backgroundProcess getProcess() {
      return this.process.getProcess();
   }

   public void kill() {
      this.process.kill();
   }
   
   // Return the kmttg task class name corresponding to current job type string
   /*private String typeToTaskClassName() {
      String className = type;
      if (className.equals("playlist"))
         className = "NowPlaying";
      if (className.equals("javaplaylist"))
         className = "javaNowPlaying";
      return "com.tivo.kmttg.task." + className;
   }*/
            
   public String getOutputFile() {
      String file = "";
      if (type.equals("playlist")) {
         file = tivoName;
      }
      else if (type.equals("javaplaylist")) {
         file = tivoName;
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
      else if (type.equals("skipdetect")) {
         file = title;
      }
      else if (type.equals("javadownload")) {
         file = tivoFile;
      }
      else if (type.equals("jdownload_decrypt")) {
         file = mpegFile;
      }
      else if (type.equals("tdownload_decrypt")) {
         file = mpegFile;
      }
      else if (type.equals("decrypt")) {
         file = mpegFile;
      }
      else if (type.equals("tivolibre")) {
         file = mpegFile;
      }
      else if (type.equals("dsd")) {
         file = mpegFile;
      }
      else if (type.equals("qsfix")) {
         file = mpegFile_fix;
      }
      else if (type.equals("fffix")) {
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
      else if (type.equals("comskip_review")) {
         file = edlFile;
         if (vprjFile != null)
            file = vprjFile;
      }
      else if (type.equals("vrdreview")) {
         file = vprjFile;
      }
      else if (type.equals("ffcut")) {
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
      //else if (type.equals("push")) {
      //   file = videoFile;
      //}
      else if (type.equals("custom")) {
    	  // NOTE: Must assign an output file of some sort to prevent job duplication
    	  // across different Tivos
         file = mpegFile;
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
      for (Class<? extends Object> obj = o.getClass();
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
