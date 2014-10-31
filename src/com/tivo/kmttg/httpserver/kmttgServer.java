package com.tivo.kmttg.httpserver;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Stack;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONFile;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.auto;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.ffmpeg;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.mediainfo;
import com.tivo.kmttg.util.string;

public class kmttgServer extends HTTPServer {
   private Stack<Transcode> transcodes = new Stack<Transcode>();
   public int transcode_counter = 0;
   
   public kmttgServer() {
      try {
         String baseDir = config.programDir;
         if ( ! file.isDir(baseDir)) {
            log.error("httpserver base directory not found: " + baseDir);
            return;
         }
         config.httpserver = new kmttgServer(config.httpserver_port);
         config.httpserver_home = baseDir + File.separator + "web";
         config.httpserver_cache = config.httpserver_home + File.separator + "cache";
         config.httpserver_cache_relative = "/web/cache/";
         VirtualHost host = config.httpserver.getVirtualHost(null);
         host.setAllowGeneratedIndex(true);
         host.addContext("/", new FileContextHandler(new File(baseDir), "/"));
         config.httpserver.start();
      } catch (IOException e) {
         log.error("HTTPServer Init - " + e.getMessage());
         config.httpserver = null;
         if (!config.GUIMODE)
            auto.process_web = false;
      }
   }
   
   public kmttgServer(int port) {
      super(port);
   }
   
   // Override parent method to handle special requests
   // Intercept certain special paths, pass along the rest
   protected void serve(Request req, Response resp) throws IOException {
      String path = req.getPath();
      debug.print("req path: " + path);
      
      // Clear up finished transcodes
      cleanup();
      
      // Handle rpc requests
      if (path.equals("/rpc")) {
         handleRpc(req, resp);
         return;
      }
      
      // Return list of rpc enabled TiVos known by kmttg
      if (path.equals("/getRpcTivos")) {
         handleRpcTivos(resp);
         return;
      }
      
      // Get list of video files in kmttg video places
      if (path.equals("/getVideoFiles")) {
         handleVideoFiles(resp);
         return;
      }
      
      // Get list of video files from a tivo
      if (path.equals("/getMyShows")) {
         handleMyShows(req, resp);
         return;
      }
      
      // Initiate and return transcoding video stream
      if (path.equals("/transcode")) {
         handleTranscode(req, resp);
         return;
      }
      
      // This is normal/default handling
      ContextHandler handler = req.getVirtualHost().getContext(path);
      if (handler == null) {
          resp.sendError(404);
          return;
      }
      // serve request
      int status = 404;
      // add directory index if necessary
      if (path.endsWith("/")) {
          String index = req.getVirtualHost().getDirectoryIndex();
          if (index != null) {
              req.setPath(path + index);
              status = handler.serve(req, resp);
              req.setPath(path);
          }
      }
      if (status == 404)
          status = handler.serve(req, resp);
      if (status > 0)
          resp.sendError(status);
   }
   
   // Handle rpc requests
   // Sample rpc request: /rpc?tivo=Roamio&operation=SysInfo
   public void handleRpc(Request req, Response resp) throws IOException {
      Map<String,String> params = req.getParams();
      if (params.containsKey("operation") && params.containsKey("tivo")) {
         try {
            String operation = params.get("operation");
            String tivo = string.urlDecode(params.get("tivo"));
            
            if (operation.equals("keyEventMacro")) {
               // Special case
               String sequence = string.urlDecode(params.get("sequence"));
               String[] s = sequence.split(" ");
               Remote r = new Remote(tivo);
               if (r.success) {
                  r.keyEventMacro(s);
                  resp.send(200, "");
               } else {
                  resp.sendError(500, "RPC call failed to TiVo: " + tivo);
               }
               return;
            }
            
            if (operation.equals("SPSave")) {
               // Special case
               String fileName = config.programDir + File.separator + tivo + ".sp";
               Remote r = new Remote(tivo);
               if (r.success) {
                  JSONArray a = r.SeasonPasses(null);
                  if ( a != null ) {
                     if ( ! JSONFile.write(a, fileName) ) {
                        resp.sendError(500, "Failed to write to file: " + fileName);
                     }
                  } else {
                     resp.sendError(500, "Failed to retriev SP list for tivo: " + tivo);
                     r.disconnect();
                     return;
                  }
                  r.disconnect();
                  resp.send(200, "Saved SP to file: " + fileName);
               } else {
                  resp.sendError(500, "RPC call failed to TiVo: " + tivo);
               }
               return;
            }
            
            if (operation.equals("SPFiles")) {
               // Special case - return all .sp files available for loading
               File dir = new File(config.programDir);
               File [] files = dir.listFiles(new FilenameFilter() {
                   public boolean accept(File dir, String name) {
                       return name.endsWith(".sp");
                   }
               });
               JSONArray a = new JSONArray();
               for (File f : files) {
                  a.put(f.getAbsolutePath());
               }
               resp.send(200, a.toString());
               return;
            }
            
            if (operation.equals("SPLoad")) {
               // Special case
               String fileName = string.urlDecode(params.get("file"));
               JSONArray a = JSONFile.readJSONArray(fileName);
               if ( a != null ) {
                  resp.send(200, a.toString());
               } else {
                  resp.sendError(500, "Failed to load SP file: " + fileName);
               }
               return;
            }
            
            // General purpose remote operation
            JSONObject json;
            if (params.containsKey("json"))
               json = new JSONObject(string.urlDecode(params.get("json")));
            else
               json = new JSONObject();
            Remote r = new Remote(tivo);
            if (r.success) {
               JSONObject result = r.Command(operation, json);
               if (result != null) {
                  resp.send(200, result.toString());
               }
               r.disconnect();
            }
         } catch (Exception e) {
            resp.sendError(500, e.getMessage());
         }
      } else {
         resp.sendError(400, "RPC request missing 'operation' and/or 'tivo'");
      }
   }
   
   // Return list of rpc enabled TiVos known by kmttg
   public void handleRpcTivos(Response resp) throws IOException {
      Stack<String> tivos = config.getTivoNames();
      JSONArray a = new JSONArray();
      for (String tivoName : tivos) {
         if (config.rpcEnabled(tivoName))
            a.put(tivoName);
      }
      resp.send(200, a.toString());
   }
   
   // Return list of video files known to kmttg
   public void handleVideoFiles(Response resp) throws IOException {
      // LinkedHashMap is used to keep hash keys unique
      LinkedHashMap<String,Integer> dirs = new LinkedHashMap<String,Integer>();
      dirs.put(config.outputDir,1);
      dirs.put(config.mpegDir,1);
      dirs.put(config.mpegCutDir,1);
      dirs.put(config.encodeDir,1);
      LinkedHashMap<String,Integer> h = new LinkedHashMap<String,Integer>();
      for (String dir : dirs.keySet())
         getVideoFiles(dir, h);
      JSONArray a = new JSONArray();
      for (String key : h.keySet()) {
         a.put(key);
      }
      resp.send(200, a.toString());
   }
   
   public void handleMyShows(Request req, Response resp) throws IOException {
      Map<String,String> params = req.getParams();
      if (params.containsKey("tivo")) {
         String tivo = string.urlDecode(params.get("tivo"));
         Remote r = new Remote(tivo);
         if (r.success) {
            jobData job = new jobData();
            job.tivoName = tivo;
            job.getURLs = true; // This needed to get __url__ property
            if (params.containsKey("offset"))
               job.myshows_offset = Integer.parseInt(params.get("offset"));
            if (params.containsKey("limit"))
               job.myshows_limit = Integer.parseInt(params.get("limit"));
            JSONArray a = r.MyShows(job);
            r.disconnect();
            resp.send(200, a.toString());
         } else {
            resp.sendError(500, "Failed to get shows from tivo: " + tivo);
            return;
         }
      } else {
         resp.sendError(400, "Request missing tivo parameter");
      }
   }
   
   private void getVideoFiles(String pathname, LinkedHashMap<String,Integer> h) {
      File f = new File(pathname);
      File[] listfiles = f.listFiles();
      for (int i = 0; i < listfiles.length; i++) {
         if (listfiles[i].isDirectory()) {
            File[] internalFile = listfiles[i].listFiles();
            for (int j = 0; j < internalFile.length; j++) {
               if (isVideoFile(internalFile[j].getAbsolutePath()))
                  h.put(internalFile[j].getAbsolutePath(), 1);
               if (internalFile[j].isDirectory()) {
                  String name = internalFile[j].getAbsolutePath();
                  getVideoFiles(name, h);
               }
            }
         } else {
            if (isVideoFile(listfiles[i].getAbsolutePath()))
               h.put(listfiles[i].getAbsolutePath(), 1);
         }
      }
   }
   
   private boolean isVideoFile(String fileName) {
      boolean videoFile = false;
      String[] extensions = {
         "mp4","mpeg","vob","mpg","mpeg2","mp2","avi","wmv",
         "asf","mkv","tivo","m4v","3gp","mov","flv","ts"
      };
      for (String extension : extensions) {
         if (fileName.toLowerCase().endsWith("." + extension))
            videoFile = true;
      }
      return videoFile;
   }

   // Transcoding video handler
   public void handleTranscode(Request req, Response resp) throws IOException {
      Map<String,String> params = req.getParams();
      
      String maxrate = null;
      if (params.containsKey("maxrate"))
         maxrate = params.get("maxrate");
      
      if (params.containsKey("killall")) {
         int num = killTranscodes();
         resp.send(200, "Killed " + num + " jobs");
         return;
      }
      
      if (params.containsKey("kill")) {
         String fileName = string.urlDecode(params.get("kill"));
         String jobName = killTranscode(fileName);
         if (jobName == null)
            resp.sendError(500, "Failed to kill job: " + fileName);
         else
            resp.send(200, "Killed job: " + jobName);
         return;
      }
      
      if (params.containsKey("running")) {
         JSONArray a = getRunning();
         if (a.length() == 0)
            a.put("NONE");
         resp.send(200, a.toString());
         return;
      }
      
      if (params.containsKey("getCached")) {
         JSONArray a = getCached();
         if (a.length() == 0)
            a.put("NONE");
         resp.send(200, a.toString());
         return;
      }
      
      if (params.containsKey("removeCached")) {
         int removed = removeCached(params.get("removeCached"));
         String message = "Removed " + removed + " cached items";
         resp.send(200, message);
         return;
      }
      
      // File transcode
      Transcode tc = null;
      String returnFile = null;
      SocketProcessInputStream ss = null;
      long length = 0;
      if (params.containsKey("file") && params.containsKey("format")) {
         String fileName = string.urlDecode(params.get("file"));
         if ( ! file.isFile(fileName) ) {
            resp.sendError(404, "Cannot find video file: '" + fileName + "'");
            return;
         }
         tc = alreadyRunning(fileName);
         if (tc != null) {
            if (tc.ss != null)
               ss = tc.ss;
            if (tc.returnFile != null)
               returnFile = tc.returnFile;
         } else {
            String format = string.urlDecode(params.get("format"));
            length = new File(fileName).length();
            tc = new Transcode(fileName);
            if (maxrate != null)
               tc.maxrate = maxrate;
            if (tc.getErrors().length() > 0) {
               resp.sendError(500, tc.getErrors());
               return;
            }
            tc.duration = -1; // Setting to -1 to deal with it a little later
            addTranscode(tc);
            if (format.equals("webm"))
               ss = tc.webm();
            else if (format.equals("hls"))
               returnFile = tc.hls();
            else {
               resp.sendError(500, "Unsupported transcode format: " + format);
               return;
            }
         }
      }
      
      // TiVo download + transcode
      if (params.containsKey("url") && params.containsKey("format")
            && params.containsKey("name") && params.containsKey("tivo")) {
         String url = params.get("url");
         String tivo = string.urlDecode(params.get("tivo"));;
         if ( ! isOnlyTivo(tivo) ) {
            resp.sendError(500, "Only 1 download at a time allowed: " + tivo);
            return;
         }
         tc = alreadyRunning(url);
         if (tc != null) {
            if (tc.returnFile != null)
               returnFile = tc.returnFile;
         } else {
            String format = string.urlDecode(params.get("format"));
            String name = string.urlDecode(params.get("name"));
            tc = new TiVoTranscode(url, name, tivo);
            if (maxrate != null)
               tc.maxrate = maxrate;
            if (tc.getErrors().length() > 0) {
               resp.sendError(500, tc.getErrors());
               return;
            }
            if (params.containsKey("duration")) {
               tc.duration = Integer.parseInt(params.get("duration"));
            }
            addTranscode(tc);
            if (format.equals("hls"))
               returnFile = tc.hls();
            else {
               resp.sendError(500, "Unsupported transcode format: " + format);
               return;
            }
         }
      }
      
      if (ss != null || returnFile != null) {
         // Transcode stream has been started, so send it out
         String fileName = null;
         if (params.containsKey("file"))
            fileName = string.urlDecode(params.get("file"));
         String name = null;
         if (params.containsKey("name"))
            name = string.urlDecode(params.get("name"));
         try {
            Boolean download = false;
            if (params.containsKey("download"))
               download = true;
            
            if (ss != null) {
               if (download) {
                  // download mode => simple response to client
                  if (alreadyRunning(fileName) != null)
                     resp.send(200, "already running: " + name);
                  else
                     resp.send(200, "download started for: " + name);
               } else {
                  // Streaming mode => send back stream
                  resp.sendBody(ss, length, null);
               }
            }
            if (returnFile != null) {
               if (download) {
                  // download mode => simple response to client
                  String message = "downloading: ";
                  if (name != null)
                     message += name;
                  if (fileName != null)
                     message += fileName;
                  resp.send(200, message);
               } else {
                  // Streaming mode => response is a link tag for Play start
                  String message = "Play ";
                  if (name != null)
                     message += name;
                  if (fileName != null)
                     message += fileName;
                  resp.send(200, "<a href=\"" + returnFile + "\">" + message + "</a>");
               }
            }
         } catch (Exception e) {
            // This catches interruptions from client side so we can kill the transcode
            log.error("transcode - " + e.getMessage());
            if (ss != null)
               ss.close();
            tc.kill();
         }
         
         // Calculate duration for file-based transcodes
         if ( tc != null && tc.duration == -1) {
            setDuration(tc);
         }
      } else {
         resp.sendError(500, "Error starting transcode");
      }      
   }
   
   // Can only download 1 file from a tivo at a time
   private Boolean isOnlyTivo(String tivo) {
      Boolean isonly = true;
      for (Transcode tc : transcodes) {
         if (tc.getTivoName() != null) {
            if (tc.getTivoName().equals(tivo))
               isonly = false;
         }
      }
      return isonly;
   }
   
   private void addTranscode(Transcode tc) {
      transcodes.add(tc);
      transcode_counter++;
   }
   
   // Restrict to only 1 transcode at a time per input file
   private Transcode alreadyRunning(String fileName) {
      for (Transcode tc : transcodes) {
         if (tc.inputFile.equals(fileName)) {
            if (tc.isRunning())
               return tc;
         }
      }
      return null;
   }
   
   private JSONArray getRunning() {
      JSONArray a = new JSONArray();
      try {
         for (Transcode tc : transcodes) {
            if (tc.isRunning()) {
               JSONObject json = new JSONObject();
               json.put("name", tc.name);
               json.put("inputFile", tc.inputFile);
               float time = Hlsutils.totalTime_m3u8(tc.segmentFile);
               if (time > 0) {
                  json.put("time", time);
               }
               json.put("duration", tc.duration);
               a.put(json);
            }
         }
      } catch (JSONException e) {
         log.error("getRunning - " + e.getMessage());
      }
      return a;
   }
   
   private JSONArray getCached() {
      JSONArray a = new JSONArray();
      String base = config.httpserver_cache;
      if (! file.isDir(base))
         return a;
      File[] files = new File(base).listFiles();
      for (File f : files) {
         if (f.getAbsolutePath().endsWith(".m3u8")) {
            try {
               JSONObject json = new JSONObject();
               json.put("url", config.httpserver_cache_relative + string.basename(f.getAbsolutePath()));
               float time = Hlsutils.totalTime_m3u8(f.getAbsolutePath());
               if (time > 0) {
                  json.put("time", time);
               }
               String textFile = f.getAbsolutePath() + ".txt";
               if (file.isFile(textFile))
                  json.put("name", Hlsutils.getTextFileContents(textFile));
               if (Hlsutils.isPartial(f.getAbsolutePath())) {
                  Boolean running = false;
                  for (Transcode tc : transcodes) {
                     if (tc.segmentFile != null && tc.segmentFile.equals(f.getAbsolutePath())) {
                        json.put("running", 1);
                        json.put("duration", tc.duration);
                        running = true;
                     }
                  }
                  if ( ! running ) {
                     // Add m3u8 termination to incomplete m3u8 file
                     Hlsutils.fixPartial(f.getAbsolutePath());
                     if (Hlsutils.isPartial(f.getAbsolutePath()))
                        json.put("partial", 1);
                  }
               }
               a.put(json);
            } catch (JSONException e) {
               log.error("getCached - " + e.getMessage());
            }
         }
      }
      return a;
   }
   
   private int removeCached(String target) {
      int count = 0;
      String base = config.httpserver_cache;
      if (! file.isDir(base))
         return count;
      
      File[] files = new File(base).listFiles();
      if (target.equals("all")) {
         for (File f : files) {
            if (f.delete() && f.getAbsolutePath().endsWith(".m3u8"))
               count++;
         }
      } else {
         String prefix = target.replaceFirst(config.httpserver_cache_relative, "");
         prefix = prefix.replaceFirst("\\.m3u8", "");
         for (File f : files) {
            String fileName = string.basename(f.getAbsolutePath());
            if (fileName.startsWith(prefix)) {
               if (f.delete() && f.getAbsolutePath().endsWith(".m3u8"))
                  count++;               
            }
         }
      }
      return count;
   }
   
   // Remove finished processes from transcodes stack
   void cleanup() {
      for (int i=0; i<transcodes.size(); ++i) {
         Boolean removed = false;
         Transcode tc = transcodes.get(i);
         if (! tc.isRunning()) {
            transcodes.remove(i);
            removed = true;
         }
         if ( ! removed && ! Hlsutils.isPartial(tc.segmentFile) ) {
            // Segment file is terminated, so job must have finished
            transcodes.remove(i);
         }
      }
   }
  
   public String killTranscode(String name) {
      String jobName = null;
      log.warn("killTranscode - " + name);
      name = name.replaceFirst(config.httpserver_cache_relative, "");
      for (int i=0; i<transcodes.size(); ++i) {
         Transcode tc = transcodes.get(i);
         String prefix = tc.prefix;
         if (name.startsWith(prefix)) {
            tc.kill();
            transcodes.remove(i);
            jobName = tc.name;
         }
      }
      if (jobName == null) {
         // name might be the original full path inputFile
         for (int i=0; i<transcodes.size(); ++i) {
            Transcode tc = transcodes.get(i);
            if (name.equals(string.urlDecode(tc.inputFile))) {
               tc.kill();
               transcodes.remove(i);
               jobName = tc.name;
            }
         }
      }
      return jobName;
   }
   
   public int killTranscodes() {
      int killed = 0;
      for(Transcode tc : transcodes) {
         tc.kill();
         killed++;
      }
      cleanup();
      transcodes.clear();
      return killed;
   }
   
   // For transcodes with input file as source, try and get duration usinf mediainfo or ffmpeg
   public void setDuration(Transcode tc) {
      Hashtable<String,String> info = null;
      if (file.isFile(config.mediainfo)) {
         info = mediainfo.getVideoInfo(tc.inputFile);
      } else if (file.isFile(config.ffmpeg)) {
         info = ffmpeg.getVideoInfo(tc.inputFile);
      }
      if (info != null && info.containsKey("duration")) {
         tc.duration = Integer.parseInt(info.get("duration"));
      }
   }
}