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
import com.tivo.kmttg.main.encodeConfig;
import com.tivo.kmttg.main.http;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.main.telnet;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.util.createMeta;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.ffmpeg;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.mediainfo;
import com.tivo.kmttg.util.parseNPL;
import com.tivo.kmttg.util.string;

public class kmttgServer extends HTTPServer {
   private Stack<Transcode> transcodes = new Stack<Transcode>();
   public int transcode_counter = 0;
   
   /** Calls HTTPServer.addContentType for all the video file types important to kmttg.
    * You can call getContentType with a file or path to get the registered content type. */
   private static void addVideoContentTypes() {
       // moyekj added these
       addContentType("application/x-mpegurl", "m3u8");
       addContentType("video/webm", "webm");
       
       // all Hlsutils.isVideoFile suffixes:
       addContentType("video/mp4", "mp4"); // type per Internet
       addContentType("video/mpeg", "mpeg");
       addContentType("video/mpeg", "vob"); // another mpeg suffix per Internet
       addContentType("video/mpeg", "mpg");
       addContentType("video/mpeg", "mpeg2");
       addContentType("video/mpeg", "mp2");
       addContentType("video/x-msvideo", "avi"); // type per Internet
       addContentType("video/x-ms-wmv", "wmv"); // type per Internet
       addContentType("video/x-ms-asf", "asf"); // likely type per Internet
       addContentType("video/x-matroska", "mkv"); // likely type per Internet
       addContentType("video/mpeg", "tivo"); // type choice for streaming TO a tivo - makes it pass the "playable" test and TiVo does the rest.
       addContentType("video/mp4", "m4v"); // likely type per Internet
       addContentType("video/3gpp", "3gp"); // type per Internet
       addContentType("video/quicktime", "mov"); // type per Internet
       addContentType("video/x-flv", "flv"); // type per Internet
       addContentType("video/MP2T", "ts"); // type per Internet, but this is likely a ts-transferred mp4
   }
   
   public kmttgServer() {
      try {
         String baseDir = config.programDir;
         if ( ! file.isDir(baseDir)) {
            log.error("httpserver base directory not found: " + baseDir);
            return;
         }
         config.httpserver = new kmttgServer(config.httpserver_port);
         addVideoContentTypes();
         config.httpserver_cache_relative = "/web/cache/";
         VirtualHost host = config.httpserver.getVirtualHost(null);
         host.setAllowGeneratedIndex(true);
         // Root dir is always kmttg install dir
         host.addContext("/", new FileContextHandler(new File(baseDir), "/"));
         
         // Cache dir is configurable
         host.addContext(config.httpserver_cache_relative,
            new FileContextHandler(new File(config.httpserver_cache),
            config.httpserver_cache_relative)
         );
         
         // Make some video shares browsable
         if (config.httpserver_shares.isEmpty()) {
            // No custom shares defined - so use default list
            host.addContext("/mpegDir", new FileContextHandler(new File(config.mpegDir), "/mpegDir"));
            host.addContext("/mpegCutDir", new FileContextHandler(new File(config.mpegCutDir), "/mpegCutDir"));
            host.addContext("/encodeDir", new FileContextHandler(new File(config.encodeDir), "/encodeDir"));
         } else {
            // Custom shares defined - so use them
            for (String share : config.httpserver_shares.keySet()) {
               host.addContext(
                  "/" + share,
                  new FileContextHandler(new File(config.httpserver_shares.get(share)), "/" + share)
               );
            }
         }
         
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
      
      // Return json array of TiVos and if rpc enabled or not
      if (path.equals("/getTivos")) {
         handleTivos(resp);
         return;
      }
      
      // Get list of video files in kmttg video places
      if (path.equals("/getVideoFiles")) {
         handleVideoFiles(resp, false);
         return;
      }
      // list video file name and array of related suffixes.
      if (path.equals("/getVideoFileDetails")) {
          handleVideoFiles(resp, true);
          return;
       }
      
      // Get list of browser shares
      if (path.equals("/getBrowserShares")) {
         handleBrowserShares(resp);
         return;
      }
      
      // Get list of video files from a tivo
      if (path.equals("/getMyShows")) {
         handleMyShows(req, resp);
         return;
      }
      
      // Get todo list from a tivo
      if (path.equals("/getToDo")) {
         handleToDo(req, resp);
         return;
      }
      
      // reboot a tivo
      if (path.equals("/reboot")) {
         handleReboot(req, resp);
         return;
      }
      
      // Initiate and return transcoding video stream
      if (path.equals("/transcode")) {
         handleTranscode(req, resp);
         return;
      }
      
      // get job data from job monitor
      if (path.equals("/jobs")) {
         handleJobs(req, resp);
         return;
      }
      
      // start a single FILES/filename or tivo/json job
      if (path.equals("/startJob")) {
          startJob(req, resp);
          return;
      }
      
      // invoke the telnet class for a "search", "text", or a semicolon-separated "codes" list
      // (if you want to use RPC, pass space-separated events with /rpc?operation=keyEventMacro&sequence= 
      //  to type, have a sequence of space-separated single characters and use "forward" for space)
      if (path.equals("/ircode")) {
         ircode(req, resp);
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
            // params is already url decoded. decoding again would convert any literal "+" to a space and have issues for any literal "%".
            String tivo = params.get("tivo");
            
            if (operation.equals("keyEventMacro")) {
               // Special case
               String sequence = params.get("sequence");
               String[] s = sequence.split(" ");
               Remote r = new Remote(tivo);
               if (r.success) {
                  // Single character strings are sent as ascii character
                  // A SPACE would have to be "FORWARD". 
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
               String fileName = params.get("file");
               JSONArray a = JSONFile.readJSONArray(fileName);
               if ( a != null ) {
                  resp.send(200, a.toString());
               } else {
                  resp.sendError(500, "Failed to load SP file: " + fileName);
               }
               return;
            }
// receive a URL and print whatever is in "tivo" parameter.  Mainly for passing testing data from HTML5 apps.
//            if(operation.equals("testdata")) {
//            	log.print(tivo);
//            	resp.send(200,"");
//            	return;
//            }
            
            // General purpose remote operation
            JSONObject json;
            if (params.containsKey("json"))
               json = new JSONObject(params.get("json"));
            else
               json = new JSONObject();
            Remote r = new Remote(tivo);
            if (r.success) {
               JSONObject result = r.Command(operation, json);
               if (result == null) {
                  resp.sendError(500, "operation failed: " + operation);
               } else {
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
   
   // Return list TiVos known by kmttg and rpc flag for each
   public void handleTivos(Response resp) throws IOException {
      Stack<String> tivos = config.getTivoNames();
      JSONArray a = new JSONArray();
      try {
         for (String tivoName : tivos) {
            JSONObject json = new JSONObject();
            json.put("tivo", tivoName);
            if (config.rpcEnabled(tivoName))
               json.put("rpc", 1);
            else
               json.put("rpc", 0);
            if (config.nplCapable(tivoName))
                json.put("npl", 1);
             else
                json.put("npl", 0);
            a.put(json);
         }
         resp.send(200, a.toString());
      } catch (JSONException e) {
         resp.sendError(500, "handleTivos - Error obtaining tivo list");
         log.error("handleTivos - " + e.getMessage());
      }
   }
   
   private void handleBrowserShares(Response resp) throws IOException {
      JSONArray a = new JSONArray();
      if (config.httpserver_shares.isEmpty()) {
         // No custom shares defined - so use default list
         a.put("mpegDir");
         a.put("mpegCutDir");
         a.put("encodeDir");
      } else {
         // Custom shares defined - so use them
         for (String dir : config.httpserver_shares.keySet()) {
            a.put(dir);
         }
      }
      resp.send(200, a.toString());
   }
   
   // Return list of video files known to kmttg
   public void handleVideoFiles(Response resp, boolean addDetails) throws IOException {
      // LinkedHashMap is used to keep hash keys unique
      LinkedHashMap<String,Integer> dirs = new LinkedHashMap<String,Integer>();
      if (config.httpserver_shares.isEmpty()) {
         // No custom shares defined - so use default list
         dirs.put(config.outputDir,1);
         dirs.put(config.mpegDir,1);
         dirs.put(config.mpegCutDir,1);
         dirs.put(config.encodeDir,1);
      } else {
         // Custom shares defined - so use them
         for (String dir : config.httpserver_shares.keySet()) {
            dirs.put(config.httpserver_shares.get(dir), 1);
         }
      }
      LinkedHashMap<String,JSONArray> h = new LinkedHashMap<String,JSONArray>();
      for (String dir : dirs.keySet())
         getVideoFiles(dir, h);
      JSONArray a = new JSONArray();
      if(addDetails) {
    	  try {
		      for (String key : h.keySet()) {
	            JSONObject json = new JSONObject();
	            json.put("videoFile", key);
	            
	            // add some details from the metadata
                String encodeFile = key;
// from gui.tivotab.atomiccb:
                String metaFile = encodeFile + ".txt";
                if ( ! file.isFile(metaFile) ) {
                   metaFile = string.replaceSuffix(encodeFile, "_cut.mpg.txt");
                }
                if ( ! file.isFile(metaFile) ) {
                   metaFile = string.replaceSuffix(encodeFile, ".mpg.txt");
                }
                if ( ! file.isFile(metaFile) ) {
                   metaFile = string.replaceSuffix(encodeFile, ".TiVo.txt");
                }
        		if (file.isFile(metaFile)) {
	                addMetaToVideoFile(json, metaFile);
        		}
                
        		// report size in K, same as rpc recording json does.
	            json.put("size", file.size(key)/1024);
	            
	            String url = getShareUrlForPath(key);
	            if(url != null) {
	            	json.put("sharePath", url);
	            }
	            if(url != null) {
	            	json.put("format", getContentType(key, null));
	            }
	            if(h.get(key).length() > 0) {
	            	json.put("suffixes", h.get(key));
	            }
		        a.put(json);
			  }
          } catch (JSONException e) {
              resp.sendError(500, "handleVideoFiles - Error providing detailed files list");
              log.error("handleVideoFiles - " + e.getMessage());
          }
      } else {
	      for (String key : h.keySet()) {
	         a.put(key);
	      }
      }
      resp.send(200, a.toString());
   }

   /** 
    * Import metaFile data (using createMeta.readMetaFile) 
    * into the VideoFile JSON object in fields equivalent to a Recording
    */
	private void addMetaToVideoFile(JSONObject json, String metaFile) throws JSONException {
		if (file.isFile(metaFile)) {
			// directly mapped fields.
			Hashtable<String, String> metaToRecording = new Hashtable<String, String>();
		/* Stack<String> names:
		       "vActor", "vDirector", "vExecProducer", "vProducer",
		       "vProgramGenre", "vSeriesGenre", "vAdvisory", "vHost",
		       "vGuestStar", "vWriter", "vChoreographer"
		 */
		/* metadata example:
		title : 
		seriesTitle : (same as title)
		description :  
		time : 2019-03-03T13:00:00Z
		mpaaRating : G1
		isEpisode : true
		iso_duration : PT29M57S
		originalAirDate : 2018-04-07T00:00:00Z
		episodeTitle : Divide
		isEpisodic : true
		showingBits : 4641
		tvRating : x1
		displayMajorNumber : 1715
		callsign : DISNEYHD-E
		seriesId : SH0325106442
		programId : EP0325106442-0387057061
		 */
			metaToRecording.put("title", "title"); // actually this is seriesTitle + episodeTitle per pytivo docs.
		 	metaToRecording.put("episodeTitle", "subtitle");
		 	metaToRecording.put("seriesTitle", "collectionTitle");
			metaToRecording.put("description", "description");
			metaToRecording.put("seriesId", "__SeriesId__");
			metaToRecording.put("programId", "partnerCollectionId");
			metaToRecording.put("starRating", "starRating"); // readMetaFile cleans from wrong format e.g. x6 instead of 4.0 or four
			metaToRecording.put("movieYear", "movieYear");
			metaToRecording.put("episodeNumber", "CONVERTED"); // "seasonNumber" and "episodeNum" convert from in a single number.
			metaToRecording.put("time", "CONVERTED");// "startTime" convert from wrong format
			metaToRecording.put("iso_duration","CONVERTED"); // "duration" iso format convert to numeric total seconds.
			metaToRecording.put("originalAirDate", "originalAirdate");// TODO wrong format (need to drop T00:00:00Z) need to convert with createMeta.printableDateFromExtendedTime
			metaToRecording.put("tvRating", "tvRating"); // readMetaFile cleans from "x1" format, though
			metaToRecording.put("mpaaRating", "mpaaRating"); // readMetaFile cleans from "G1" format, though
			//Boolean("isEpisodic") -> "episodic"
			/*
	colorCode
	This is shown on the Details screen. It uses the second character to determine the color mode. The list is as follows:
	
	x1 = B & W
	x2 = Color and B & W
	x3 = Colorized
	x4 = Color Series
			 */
			
			Hashtable<String, Object> meta = createMeta.readMetaFile(metaFile);
			for(String metaKey : meta.keySet()) {
				String jsonKey = metaToRecording.get(metaKey);
				if(jsonKey != null) {
					Object value = meta.get(metaKey);
					if(value instanceof String) {
						String val = (String)value;
						
						if("iso_duration".equals(metaKey)) {
							json.put("duration", createMeta.jsonDurationFromIsoDuration(val));
						}
						else
						if("time".equals(metaKey)) {
							//startTime should be in format "yyyy-MM-dd HH:mm:ss"
							// "time" seems to show up in local timezone, not GMT, although pyTivo wiki claims it is GMT.
							json.put("startTime", createMeta.jsonDateFromExtendedLocalTime(val));
						}
						else
							//TODO it's possible this would show up as a Stack<String> when there are 2+ episode numbers.
						if("episodeNumber".equals(metaKey)) {
							// conversion copied from atomic task
							try {
							   String ep = val;
					           if (ep.length() <= 3) {
					              ep = ep.substring(1, ep.length());
					           } else {
					              ep = ep.substring(2, ep.length());
					           }

							  String season = val;
					          if (season.length() == 3)
					             season = season.substring(0, 1);
					          else if (season.length() == 4)
					             season = season.substring(0, 2);
					          else if (season.length() == 5)
					             season = season.substring(0, 2);
					          
					          json.put("seasonNumber", season);//season
					          json.put("episodeNum", new JSONArray().put(ep));//episode
							} catch(Exception e) {
								json.put(metaKey, val);
							}
						} else {
							
							json.put(jsonKey, val);
							
						}
					} else if(value instanceof Stack<?>) {
						JSONArray vals = new JSONArray();
						@SuppressWarnings("unchecked")
						Stack<String> stack = (Stack<String>) value;
						for(String val : stack) {
							vals.put(val);
						}
						json.put(jsonKey, vals);
					}
				}
			}
			try {
			   if(meta.get("callsign") != null) {
				   JSONObject channel = new JSONObject();
				   // channel number
				   if(meta.get("displayMajorNumber") != null) {
					   // *-x channel subnumber most likely
					   if(meta.get("displayMinorNumber") != null) {
						   channel.put("channelNumber", meta.get("displayMajorNumber")+"-"+meta.get("displayMinorNumber"));
					   } else {
						   channel.put("channelNumber", meta.get("displayMajorNumber"));
					   }
				   }
				   channel.put("name", meta.get("callsign"));
				   channel.put("callSign", meta.get("callsign"));
				   
				   json.put("channel", channel);
			   }
			} catch(Exception e) { }
			try {
				long bits = Long.parseLong(String.valueOf(meta.get("showingBits")));
				// per Pytivo docs:
				/*
				This tells the TiVo to display various combinations things in parentheses at the end of the description. It only accepts numerical digits, if any alpha digits are entered, common error* occurs. Field must have a value present or be omitted from the document completely. If field is present with no value entered, common error* occurs. More than one field can be present in one document, but only the last value will be used, any preceding will be ignored. The field is the sum of the following values:
				
				1 = CC
				2 = Stereo
				4 = Sub (subtitled)
				8 = In Prog
				16 = Class (classroom)
				32 = SAP
				64 = Blackout
				128 = Intercast
				256 = Three D
				512 = R (repeat)
				1024 = Letterbox
				4096 = HD (High Definition)
				65536 = S (sex rating)
				131072 = V (violence rating)
				262144 = L (language rating)
				524288 = D (dialog rating)
				1048576 = FV (fantasy violence rating)
						 */
				if((1 & bits) > 0) json.put("cc", true);
				if((512 & bits) > 0) json.put("repeat", true);
				if((4096 & bits) > 0) json.put("hdtv", true);
				
			} catch(Exception e) {}

		}
	}
   
   private String getShareUrlForPath(String path) {
	   String result = null;
       if (config.httpserver_shares.isEmpty()) {
       // No custom shares defined - so use default list
       //dirs.put(config.outputDir,null);
           if(path.startsWith(config.mpegDir)) {
        	   result= File.separator +"mpegDir"+path.substring(config.mpegDir.length());
           }
           if(path.startsWith(config.mpegCutDir)) {
        	   result= File.separator +"mpegCutDir"+path.substring(config.mpegCutDir.length());
           }
           if(path.startsWith(config.encodeDir)) {
        	   result= File.separator +"encodeDir"+path.substring(config.encodeDir.length());
           }
       } else {
	       // Custom shares defined - so use them
	       for (String dir : config.httpserver_shares.keySet()) {
	          String physical = config.httpserver_shares.get(dir);
	          if(path.startsWith(physical)) {
	        	  result= File.separator +dir+path.substring(physical.length());
	        	  break;
	          }
	       }
       }
       
		if(result != null)
		       result = result.replace(File.separatorChar, '/');

       return result;  
   }

public void handleMyShows(Request req, Response resp) throws IOException {
      Map<String,String> params = req.getParams();
      if (params.containsKey("tivo")) {
         String tivo = params.get("tivo");
         if (params.containsKey("xml")) {
            // Non RPC method requested returns XML
            int offset = 0;
            if (params.containsKey("offset"))
               offset = Integer.parseInt(params.get("offset"));
            String outputFile = file.makeTempFile("NPL");
            String ip = config.TIVOS.get(tivo);
            String url = "https://" + ip;
            String wan_port = config.getWanSetting(tivo, "https");
            if (wan_port != null)
               url += ":" + wan_port;
            url += "/TiVoConnect?Command=QueryContainer&Container=/NowPlaying&Recurse=Yes&AnchorOffset=" + offset;
            try {
               if (http.download(url, "tivo", config.MAK, outputFile, false, null) ) {
                  resp.send(200, Hlsutils.getTextFileContents(outputFile));
                  file.delete(outputFile);
               } else {
                  resp.sendError(400, "Failed to retrive NPL listings for TiVo: " + tivo);
               }
            } catch (Exception e) {
               resp.sendError(400, "Failed to retrive NPL listings for TiVo: " + tivo);
               log.error("handleMyShows - " + e.getMessage());
            }

         } else {
            // RPC method returns JSONArray
            Remote r = new Remote(tivo);
            if (r.success) {
               jobData job = new jobData();
               job.tivoName = tivo;
               job.getURLs = true; // This needed to get __url__ property
               JSONArray a = r.MyShows(job);
               r.disconnect();
               resp.send(200, a.toString());
            } else {
               resp.sendError(500, "Failed to get shows from tivo: " + tivo);
               return;
            }
         }
      } else {
         resp.sendError(400, "Request missing tivo parameter");
      }
   }
   
   public void handleToDo(Request req, Response resp) throws IOException {
      Map<String,String> params = req.getParams();
      if (params.containsKey("tivo")) {
         String tivo = params.get("tivo");
         Remote r = new Remote(tivo);
         if (r.success) {
            jobData job = new jobData();
            job.tivoName = tivo;
            JSONArray a = r.ToDo(job);
            r.disconnect();
            resp.send(200, a.toString());
         } else {
            resp.sendError(500, "Failed to get todo from tivo: " + tivo);
            return;
         }
      } else {
         resp.sendError(400, "Request missing tivo parameter");
      }
   }
   
   public void handleReboot(Request req, Response resp) throws IOException {
      Map<String,String> params = req.getParams();
      if (params.containsKey("tivo")) {
         String tivo = params.get("tivo");
         Remote r = new Remote(tivo);
         if (r.success) {
            r.reboot(tivo);
            resp.send(200, "Reboot sequence sent to TiVo: " + tivo);
         } else {
            resp.sendError(500, "Failed to send reboot sequence to TiVo: " + tivo);
            return;
         }
      } else {
         resp.sendError(400, "Request missing tivo parameter");
      }
   }
   
   private void getVideoFiles(String pathname, LinkedHashMap<String,JSONArray> h) {
      File f = new File(pathname);
      File[] listfiles = f.listFiles();
      for (int i = 0; i < listfiles.length; i++) {
         if (listfiles[i].isDirectory()) {
            File[] internalFile = listfiles[i].listFiles();
            for (int j = 0; j < internalFile.length; j++) {
               String selectedFile = internalFile[j].getAbsolutePath();
               if (Hlsutils.isVideoFile(selectedFile))
                  h.put(selectedFile, getRelatedFileSuffixes(selectedFile, internalFile));
               if (internalFile[j].isDirectory()) {
                  String name = selectedFile;
                  getVideoFiles(name, h);
               }
            }
         } else {
            String selectedFile = listfiles[i].getAbsolutePath();
            if (Hlsutils.isVideoFile(selectedFile))
               h.put(selectedFile, getRelatedFileSuffixes(selectedFile, listfiles));
         }
      }
   }
   
   /**
    * array of e.g. "mpg.txt" "edl" "srt" etc. suffixes that aren't video files or the original file.
    * @param fileAbsolutePath
    * @param candidateFiles
    * @return
    */
   private JSONArray getRelatedFileSuffixes(String fileAbsolutePath, File[] candidateFiles) {
	   JSONArray a = new JSONArray();
	   int lastDot = fileAbsolutePath.lastIndexOf('.');
	   String fileBase;
	   if(lastDot >=0) {
		   fileBase = fileAbsolutePath.substring(0, lastDot+1);
	   } else {
		   fileBase = fileAbsolutePath;
	   }
	   for(int i = 0 ; i < candidateFiles.length ; ++i) {
		   String candidate = candidateFiles[i].getAbsolutePath();
		   if(candidate.startsWith(fileBase)) {
			   String suffix = candidate.substring(fileBase.length());
			   if(!fileAbsolutePath.equals(candidate) && !Hlsutils.isVideoFile(candidate)) {
				   a.put(suffix);
			   }
		   }
	   }
	   return a;
   }
   
    private void ircode(Request req, Response resp) throws IOException {
        Map<String, String> params = req.getParams();
        if (params.containsKey("tivo")) {
            String tivo = params.get("tivo");
            String commands[] = null;
            if (params.containsKey("search")) {
                String search = params.get("search");
                if(search.length() > 0) {
                   int clears = 3;
                   commands = new String[search.length()+1+clears];
                   int i = 0;
                   commands[i++] = "SEARCH";
                   // search can show up with previous search which would be appended to.
                   for (int j = 0 ; j < clears ; ++j) {
                      commands[i++] = "CLEAR";
                   }
                   for(char c : search.toCharArray()) {
                      commands[i++] = String.valueOf(c);
                   }
                } else {
                   commands = new String[] {"SEARCH"};
                }
            }
            else
            if (params.containsKey("text")) {
                String text = params.get("text");
                if(text.length() > 0) {
                    commands = new String[text.length()];
                    int i = 0;
                    for(char c : text.toCharArray()) {
                       commands[i++] = String.valueOf(c);
                    }
                }
            }
            else
            if (params.containsKey("codes")) {
                String codes = params.get("codes");
                commands = codes.split(";");
            }
            if(commands != null) {
               try {
                  // 200 still wasn't enough interval - some keys were being lost.
                  new telnet(config.TIVOS.get(tivo), commands, 300);
               } catch (Exception e) {
                  resp.sendError(500, "ircode - " + e.getMessage());
                  return;
               }
               resp.send(200, "code sent");
               return;
            }
        }
        resp.sendError(400, "ircode request missing relevant parameters");
    }
    
    private void startJob(Request req, Response resp) throws IOException {
        Map<String, String> params = req.getParams();
        if (params.containsKey("tivo")) {
            String tivo = params.get("tivo");
            String settings = null;
            if (params.containsKey("settings")) {
               settings = params.get("settings");
            }
            if (params.containsKey("recording")) {
                String recording = params.get("recording");
                try {
                    startJob(tivo, recording, settings);
                } catch (Exception e) {
                    resp.sendError(500, "startJob - " + e.getMessage());
                    return;
                }
                resp.send(200, "Started job");
                return;
            }
        }
        resp.sendError(400, "startJob request missing relevant parameters");
    }

    // list of job settings json names used here and displayed in app.
    final String tsArg = "TS download";
    final String metaArg = "metadata";
    final String decArg = "decrypt";
    final String qsArg = "QS Fix";
    final String detArg = "Ad Detect";
    final String cutArg = "Ad Cut";
    final String ccArg = "captions";
    final String encArg = "encode";
    final String nameArg = "encodeName";
    final String encodeNamesArg = "ENCODE_NAMES";

    /** single-job equivalent of tivoTab.startCB */
    private void startJob(String tivoName, String recordingJsonOrFile, String settingsJson) throws JSONException {
        debug.print("");

        Stack<Hashtable<String, Object>> entries = new Stack<Hashtable<String, Object>>();
        if (tivoName.equals("FILES")) {
            Hashtable<String, Object> h = new Hashtable<String, Object>();
            h.put("tivoName", tivoName);
            h.put("mode", "FILES");
            String fileName = recordingJsonOrFile;
            if (fileName != null) {
                h.put("startFile", fileName);
                entries.add(h);
            }
        } else {
            JSONObject json = new JSONObject(recordingJsonOrFile);
            Hashtable<String, String> data = parseNPL.rpcToHashEntry(tivoName, json);

            Hashtable<String, Object> h = new Hashtable<String, Object>();
            h.put("tivoName", tivoName);
            h.put("mode", "Download");
            h.put("entry", data);
            entries.add(h);
        }
        String encodeName;
        int tsdownload;
        boolean metadata,
                decrypt,
                qsfix,
                twpdelete, 
                rpcdelete, 
                comskip, 
                comcut, 
                captions, 
                encode,
                //push,
                custom;
        if(settingsJson == null || settingsJson.trim().length() == 0) {
         settingsJson = "{}";
        }
        JSONObject settings = new JSONObject(settingsJson);
        
        // these job items are not included in JSON settings currently
        twpdelete = config.gui.twpdelete.isSelected(); 
        rpcdelete = config.gui.rpcdelete.isSelected(); 
        //push = config.gui.push.isSelected();
        custom = config.gui.custom.isSelected();
        
        if(settings.has(tsArg)) {
           if(settings.getBoolean(tsArg)) {
              tsdownload = 1;
           } else {
              tsdownload = 0;
           }
        } else {
           tsdownload = config.TSDownload;
        }
        if(settings.has(metaArg)) {
           metadata = settings.getBoolean(metaArg);
        } else {
           metadata = config.gui.metadata.isSelected();
        }
        if(settings.has(decArg)) {
           decrypt = settings.getBoolean(decArg);
        } else {
           decrypt = config.gui.decrypt.isSelected();
        }
        if(settings.has(qsArg)) {
           qsfix = settings.getBoolean(qsArg);
        } else {
           qsfix = config.gui.qsfix.isSelected();
        }
        if(settings.has(detArg)) {
           comskip = settings.getBoolean(detArg);
        } else {
           comskip = config.gui.comskip.isSelected();
        }
        if(settings.has(cutArg)) {
           comcut = settings.getBoolean(cutArg);
        } else {
           comcut = config.gui.comcut.isSelected();
        }
        if(settings.has(ccArg)) {
           captions = settings.getBoolean(ccArg);
        } else {
           captions = config.gui.captions.isSelected(); 
        }
        if(settings.has(encArg)) {
           encode = settings.getBoolean(encArg);
        } else {
           encode = config.gui.encode.isSelected();
        }
        if(settings.has(nameArg)) {
           encodeName = settings.getString(nameArg);
        } else {
           encodeName = null;
        }
    	
        // Launch jobs appropriately
        for (int j = 0; j < entries.size(); ++j) {
            Hashtable<String, Object> h = entries.get(j);
            if (tivoName.equals("FILES")) {
                h.put("metadataTivo", metadata);
                h.put("metadata", false);
            } else {
                h.put("metadata", metadata);
                h.put("metadataTivo", false);
            }
            h.put("TSDownload", tsdownload);
            h.put("decrypt", decrypt);
            h.put("qsfix", qsfix);
            h.put("twpdelete", twpdelete);
            h.put("rpcdelete", rpcdelete && config.rpcEnabled(tivoName));
            h.put("comskip", comskip);
            h.put("comcut", comcut);
            h.put("captions", captions);
            h.put("encode", encode);
            if(encodeName != null) {
               h.put("encodeName", encodeName);
            }
            // h.put("push", push);
            h.put("custom", custom);
            jobMonitor.LaunchJobs(h);
        }
    }

   
   private void handleJobs(Request req, Response resp) throws IOException {
      Map<String,String> params = req.getParams();
      
      if (params.containsKey("get")) {
         JSONArray jobs = new JSONArray();
         if (jobMonitor.JOBS != null) {
            try {
               for (int i=0; i<jobMonitor.JOBS.size(); ++i) {
                  jobData j = jobMonitor.JOBS.get(i);
                  JSONObject job = new JSONObject();
                  job.put("status", j.status);
                  job.put("type", j.type);
                  job.put("source", j.tivoName);
                  // the url_TiVoVideoDetails
                  job.put("sourceFile", j.source);
                  job.put("output", j.getOutputFile());
                  job.put("familyId", j.familyId);
                  jobs.put(job);
               }
            } catch (Exception e) {
               resp.sendError(500, "getJobs - " + e.getMessage());
               return;
            }
         }
         resp.send(200, jobs.toString());
         return;
      }
      
      if (params.containsKey("settings")) {
         JSONObject settings = new JSONObject();
// these job items are not included in JSON settings currently
//          twpdelete = config.gui.twpdelete.isSelected(); 
//          rpcdelete = config.gui.rpcdelete.isSelected(); 
//          //push = config.gui.push.isSelected();
//          custom = config.gui.custom.isSelected();
          
          try {
            settings.put(tsArg, (config.TSDownload > 0));
            settings.put(metaArg, config.gui.metadata.isSelected());
            settings.put(decArg, config.gui.decrypt.isSelected());
            settings.put(qsArg, config.gui.qsfix.isSelected());
            settings.put(detArg, config.gui.comskip.isSelected());
            settings.put(cutArg, config.gui.comcut.isSelected());
            settings.put(ccArg, config.gui.captions.isSelected()); 
            settings.put(encArg, config.gui.encode.isSelected());
            if(encodeConfig.getEncodeName() != null) {
               settings.put(nameArg, encodeConfig.getEncodeName());
            }
            settings.put(encodeNamesArg,config.ENCODE_NAMES);
            resp.send(200, settings.toString());
            return;
          } catch (Exception e) {
              resp.sendError(500, "getJobs - " + e.getMessage());
              return;
          }
      }
      
      if (params.containsKey("kill")) {
         String id = params.get("kill");
         if (jobMonitor.JOBS != null) {
            for (int i=0; i<jobMonitor.JOBS.size(); ++i) {
               jobData j = jobMonitor.JOBS.get(i);
               String jid = "" + j.familyId;
               if (jid.equals(id)) {
                  jobMonitor.kill(j);
                  resp.send(200, "Killed job: " + j.toString());
                  return;
               }
            }
         }
         resp.sendError(400, "could not find job id: " + id);
         return;
      }
      resp.sendError(400, "jobs request missing relevant parameters");
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
         String fileName = params.get("kill");
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
      if (params.containsKey("file") && params.containsKey("format")) {
         String fileName = params.get("file");
         if ( ! file.isFile(fileName) ) {
            resp.sendError(404, "Cannot find video file: '" + fileName + "'");
            return;
         }
         tc = alreadyRunning(fileName);
         if (tc != null) {
            if (tc.returnFile != null)
               returnFile = tc.returnFile;
         } else {
            String format = params.get("format");
            tc = new Transcode(fileName);
            if (maxrate != null)
               tc.maxrate = maxrate;
            tc.duration = -1; // Setting to -1 to deal with it a little later
            addTranscode(tc);
            if (format.equals("webm"))
               returnFile = tc.webm();
            else if (format.equals("hls"))
               returnFile = tc.hls();
            else {
               resp.sendError(500, "Unsupported transcode format: " + format);
               return;
            }
            if (tc.getErrors().length() > 0) {
               resp.sendError(500, tc.getErrors());
               return;
            }
         }
      }
      
      // TiVo download + transcode
      if (params.containsKey("url") && params.containsKey("format")
            && params.containsKey("name") && params.containsKey("tivo")) {
         String url = params.get("url");
         String tivo = params.get("tivo");
         if ( ! isOnlyTivo(tivo) ) {
            resp.sendError(500, "Only 1 tivo download at a time allowed: " + tivo);
            return;
         }
         tc = alreadyRunning(url);
         if (tc != null) {
            if (tc.returnFile != null)
               returnFile = tc.returnFile;
         } else {
            String format = params.get("format");
            String name = params.get("name");
            tc = new TiVoTranscode(url, name, tivo);
            if (maxrate != null)
               tc.maxrate = maxrate;
            if (params.containsKey("duration")) {
               tc.duration = Integer.parseInt(params.get("duration"));
            }
            addTranscode(tc);
            if (format.equals("webm"))
               returnFile = tc.webm();
            else if (format.equals("hls"))
               returnFile = tc.hls();
            else {
               resp.sendError(500, "Unsupported transcode format: " + format);
               return;
            }
            if (tc.getErrors().length() > 0) {
               resp.sendError(500, tc.getErrors());
               return;
            }
         }
      }
      
      if (returnFile != null) {
         // Transcode stream has been started, so send it out
         String fileName = null;
         if (params.containsKey("file"))
            fileName = params.get("file");
         String name = null;
         if (params.containsKey("name"))
            name = params.get("name");
         try {
            Boolean download = false;
            if (params.containsKey("download"))
               download = true;
            
            if (returnFile != null) {
               if (download) {
                  // download mode => simple response to client
                  String message = "transcoding: ";
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
                  if(params.containsKey("json")) {
                     String url = null;
                     if(params.containsKey("url"))
                        url = params.get("url");

                     JSONObject jsonResult = new JSONObject();
                     jsonResult.put("format",tc.format); // sort of from params
                     jsonResult.put("playPath",returnFile);// (relative) url to play
                     //jsonResult.put("segmentFile",tc.segmentFile); // disk location of file.

                     jsonResult.put("sourceFile",tc.inputFile);// from file or url parameter
                     jsonResult.put("videoFile",fileName);// from file parameter
                     jsonResult.put("__url__",url);// from url parameter

                     jsonResult.put("name",name); // from params (required if using url)
                     resp.send(200, jsonResult.toString());
                  } else {
                     resp.send(200, "<a href=\"" + returnFile + "\">" + message + "</a>");
                  }
               }
            }
         } catch (Exception e) {
            // This catches interruptions from client side so we can kill the transcode
            log.error("transcode - " + e.getMessage());
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
               if (tc.segmentFile != null) {
                  float time = Hlsutils.totalTime_m3u8(tc.segmentFile);
                  if (time > 0) {
                     json.put("time", time);
                  }
               }
               else if (tc.returnFile != null) {
                  float time = Hlsutils.totalTime_webm(tc);
                  if (time > 0) {
                     json.put("time", time);
                  }                  
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
         } // m3u8
         if (f.getAbsolutePath().endsWith(".webm")) {
            try {
               JSONObject json = new JSONObject();
               json.put("url", config.httpserver_cache_relative + string.basename(f.getAbsolutePath()));
               String textFile = f.getAbsolutePath() + ".txt";
               if (file.isFile(textFile))
                  json.put("name", Hlsutils.getTextFileContents(textFile));
               for (Transcode tc : transcodes) {
                  if (tc.returnFile != null && tc.returnFile.equals(f.getAbsolutePath())) {
                     json.put("running", 1);
                     json.put("duration", tc.duration);
                     float time = Hlsutils.totalTime_webm(tc);
                     if (time > 0) {
                        json.put("time", time);
                     }
                  }
               }
               a.put(json);
            } catch (JSONException e) {
               log.error("getCached - " + e.getMessage());
            }
         } // webm
      } // for File
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
            if (f.delete() && (f.getAbsolutePath().endsWith(".m3u8") || f.getAbsolutePath().endsWith(".webm")))
               count++;
         }
      } else {
         String prefix = target.replaceFirst(config.httpserver_cache_relative, "");
         prefix = prefix.replaceFirst("\\.m3u8", "");
         prefix = prefix.replaceFirst("\\.webm", "");
         for (File f : files) {
            String fileName = string.basename(f.getAbsolutePath());
            if (fileName.startsWith(prefix)) {
               if (f.delete() && (f.getAbsolutePath().endsWith(".m3u8") || f.getAbsolutePath().endsWith(".webm")))
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
            if (name.equals(tc.inputFile)) {
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
   
   // For transcodes with input file as source, try and get duration using mediainfo or ffmpeg
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
