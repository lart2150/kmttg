package com.tivo.kmttg.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
//import java.net.Authenticator;
import java.net.HttpURLConnection;
//import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Remote;

public class file {
   
   public static Boolean isFile(String f) {
      debug.print("f=" + f);
      try {
         return new File(f).isFile();
      }
      catch (NullPointerException e) {
         return false;
      }
   }
   
   public static Boolean isDir(String d) {
      debug.print("d=" + d);
      try {
         return new File(d).isDirectory();
      }
      catch (NullPointerException e) {
         return false;
      }
   }
   
   public static long size(String f) {
      debug.print("f=" + f);
      try {
         return new File(f).length();
      }
      catch (NullPointerException e) {
         return 0;
      }
      catch (SecurityException e) {
         return 0;
      }
   }
   
   // Java 1.5 compatible free space calculator
   public static long freeSpace(String f) {
      long bad = 0;
      if ( ! file.isDir(f) ) return bad;
      long free;
      Stack<String> command = new Stack<String>();
      if (config.OS.matches("windows")) {
         // Use 'dir' command to get free space
         command.add("cmd");
         command.add("/c");
         command.add("dir");
         command.add(f);
         backgroundProcess process = new backgroundProcess();
         if ( process.run(command) ) {
            if ( process.Wait() == 0 ) {
               Stack<String> l = process.getStdout();
               if (l.size() > 0) {
                  String free_string = l.lastElement();
                  if (free_string.matches(".*bytes\\s+free")) {
                     String[] ll = free_string.split("\\s+");
                     free_string = ll[ll.length-3].replaceAll(",", "");
                     try {
                        free = Long.parseLong(free_string);
                        return free;
                     } catch (NumberFormatException e) {
                        return bad;
                     }
                  } else {
                     return bad;
                  }
               }
            }
         }         
      } else {
         // Use 'df' command to get free space
         backgroundProcess process = new backgroundProcess();
         command.add("/bin/df");
         command.add("-k");
         command.add(f);
         if ( process.run(command) ) {
            if ( process.Wait() == 0 ) {
               Stack<String> l = process.getStdout();
               if (l.size() > 0) {
                  String free_string = l.lastElement();
                  String[] ll = free_string.split("\\s+");
                  if (ll.length-3 >= 0) {
                     free_string = ll[ll.length-3];
                     try {
                        free = Long.parseLong(free_string);
                        free = (long) (free * Math.pow(2, 10));
                        return free;
                     } catch (NumberFormatException e) {
                        return bad;
                     }
                  }
               }
            }
         }         
      }
      return bad;
   }

   public static Boolean isEmpty(String f) {
      debug.print("f=" + f);
      if (size(f) == 0) {
         return true;
      } else {
         return false;
      }
   }
   
   public static Boolean delete(String f) {
      debug.print("f=" + f);
      try {
         return new File(f).delete();
      }
      catch (NullPointerException e) {
         log.error(e.getMessage());
         return false;
      }
   }
   
   public static Boolean rename(String fold, String fnew) {
      debug.print("fold=" + fold + " fnew=" + fnew);
      try {
         return new File(fold).renameTo(new File(fnew));
      }
      catch (NullPointerException e) {
         log.error(e.getMessage());
         return false;
      }
   }
      
   // Create a new empty file
   public static Boolean create(String fileName) {
      debug.print("fileName=" + fileName);
      try {
         File f = new File(fileName);
         return f.createNewFile();
      } catch (IOException e) {
         log.error(e.getMessage());
         return false;
      }
   }
   
   public static String makeTempFile(String prefix) {
      debug.print("prefix=" + prefix);
      try {
         File tmp = File.createTempFile(prefix, ".tmp", new File(config.tmpDir));
         tmp.deleteOnExit();
         return tmp.getPath();
      } catch (IOException e) {
         log.error(e.getMessage());
         return null;
      }
   }
   
   public static String makeTempFile(String prefix, String suffix) {
      debug.print("prefix=" + prefix);
      try {
         File tmp = File.createTempFile(prefix, suffix, new File(config.tmpDir));
         tmp.deleteOnExit();
         return tmp.getPath();
      } catch (IOException e) {
         log.error(e.getMessage());
         return null;
      }
   }
   
   // Locate full path of an executable using "which"
   // Return null if not found
   public static String unixWhich(String c) {
      if (c != null) {
         Stack<String> command = new Stack<String>();
         command.add("/usr/bin/which");
         command.add(c);
         backgroundProcess process = new backgroundProcess();
         if ( process.run(command) ) {
            if ( process.Wait() == 0 ) {
               String result = process.getStdoutLast();
               if (result.length() > 0 && file.isFile(result)) {
                  return result;
               }
            }
         }
      }
      return null;
   }
   
   // TivoWebPlus file delete function
   // Note that show id needs to be extracted from given download_url
   // in order to construct the actual delete url
   // Sample download url looks like:
   // http://10.0.0.53:80/download/XXI%20Winter%20Olympics.TiVo?Container=%2FNowPlaying&id=1242283
   // So TWP delete url for above would be:
   // http://10.0.0.53:8080/confirm/del/1242283
   public static void TivoWebPlusDelete(String download_url) {
      if (download_url == null) return;
      int port = 8080;
      Pattern p = Pattern.compile("http://(\\S+):.+&id=(.+)$");
      Matcher m = p.matcher(download_url);
      if (m.matches()) {
         String ip = m.group(1);
         final String id = m.group(2);
         final String urlString = "http://" + ip + ":" + port + "/confirm/del/" + id;
         log.warn(">> Issuing TivoWebPlus show delete request: " + urlString);
         try {
            // Run the http request in separate thread so as not to hang up the main program
            final URL url = new URL(urlString);
            class AutoThread implements Runnable {
               AutoThread() {}       
               public void run () {
                  int timeout = 10;
                  try {
                     String data = "u2=bnowshowing";
                     data += "&sub=Delete";
                     data += "&" + URLEncoder.encode("fsida(" + id + ")", "UTF-8")  + "=on";
                     data += "&submit=Confirm_Delete";
                     HttpURLConnection c = (HttpURLConnection) url.openConnection();
                     c.setRequestMethod("POST");
                     c.setReadTimeout(timeout*1000);
                     c.setDoOutput(true);
                     /* If authentication needed
                     final String login ="oztivo";
                     final String password ="moyekj";
                     Authenticator.setDefault(new Authenticator() {
                         protected PasswordAuthentication getPasswordAuthentication() {
                             return new PasswordAuthentication (login, password.toCharArray());
                         }
                     });
                     */
                     c.connect();
                     BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(c.getOutputStream()));
                     bw.write(data);
                     bw.flush();
                     bw.close();
                     String response = c.getResponseMessage();
                     if (response.equals("OK")) {
                        log.print(">> TivoWebPlus delete succeeded.");
                     } else {
                        log.error("TWP Delete: Received unexpected response for: " + urlString);
                        log.error(response);
                     }
                  }
                  catch (Exception e) {
                     log.error("TWP Delete: connection failed: " + urlString);
                     log.error(e.toString());
                  }
               }
            }
            AutoThread t = new AutoThread();
            Thread thread = new Thread(t);
            thread.start();
         }
         catch (Exception e) {
            log.error("TWP Delete: connection failed: " + urlString);
            log.error(e.toString());
         }
      }
   }
   
   // Use iPad protocol to delete a show
   public static Boolean iPadDelete(String tivoName, String recordingId) {
      if (recordingId == null) {
         log.error("iPad Delete got null recordingId");
         return false;
      }
      JSONArray a = new JSONArray();
      JSONObject json = new JSONObject();
      a.put(recordingId);
      try {
         json.put("recordingId", a);
         log.warn(">> Attempting iPad delete for id: " + recordingId);
         Remote r = new Remote(config.TIVOS.get(tivoName), config.MAK);
         if (r.success) {
            if (r.Key("delete", json) != null)
               log.warn(">> iPad delete succeeded.");
            r.disconnect();
         }
      } catch (JSONException e) {
         log.error("iPad delete failed - " + e.getMessage());
         return false;
      }
      return true;
   }

}
