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
package com.tivo.kmttg.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
//import java.net.Authenticator;
import java.net.HttpURLConnection;
//import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.tivoTab;
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
   
   public static Boolean createDirIfNeeded(String f) {
      debug.print("f=" + f);
      String baseDir = string.dirname(f);
      if ( ! file.isDir(baseDir) ) {
         if ( ! new File(baseDir).mkdirs() ) {
            log.error("Failed to create path: " + baseDir);
            return false;
         }
      }
      return true;
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
   
   // Java 1.6 or later compatible free space
   public static long freeSpace(String f) {
      long bad = 0;
      if ( ! file.isDir(f) ) return bad;
      return new File(f).getFreeSpace();
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
   
   static public boolean deleteDir(File path) {
      if( path.exists() ) {
         File[] files = path.listFiles();
         for(int i=0; i<files.length; i++) {
            if (files[i].isDirectory()) {
               deleteDir(files[i]);
            } else {
               files[i].delete();
            }
         }
      }
      return( path.delete() );
   }
   
   public static Boolean copy(String source, String dest) {
      try {
         InputStream in = new FileInputStream(source);
         OutputStream out = new FileOutputStream(dest);
         byte[] buf = new byte[1024];
         int len;
         while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
         }
         in.close();
         out.close(); 
      } catch (Exception e) {
         log.error("file copy: " + e.getMessage());
         log.error(Arrays.toString(e.getStackTrace()));
         return false;
      }
      return true;
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
   
   public static void cleanUpFiles(String prefix) {
      log.print("Cleaning up files with prefix: " + prefix);
      File folderToScan = new File(config.mpegDir); 
      File[] listOfFiles = folderToScan.listFiles();
      for (File f : listOfFiles) {
         if (f.isFile() && f.getName().startsWith(prefix)) {
            f.delete();
         }
      }
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
   
   // Use rpc protocol to delete a show
   public static Boolean rpcDelete(String tivoName, String recordingId) {
      if (recordingId == null) {
         log.error("rpc Delete got null recordingId");
         return false;
      }
      JSONArray a = new JSONArray();
      JSONObject json = new JSONObject();
      a.put(recordingId);
      try {
         json.put("recordingId", a);
         log.warn(">> Attempting rpc delete for id: " + recordingId);
         Remote r = config.initRemote(tivoName);
         if (r.success) {
            if (r.Command("Delete", json) != null) {
               log.warn(">> rpc delete succeeded.");
               // Delete entry from TiVo tab if currently displayed
               if (config.GUIMODE) {
                  Stack<String> tivoNames = config.getNplTivoNames();
                  if (tivoNames.size() > 0) {
                     for (String tivo : tivoNames) {
                        if (tivo.equals(tivoName)) {
                           tivoTab t = config.gui.getTab(tivoName);
                           if (t != null) {
                              t.getTable().RemoveEntry(recordingId);
                           }
                        }
                     }
                  }
               }
            }
            r.disconnect();
         }
      } catch (JSONException e) {
         log.error("rpc delete failed - " + e.getMessage());
         return false;
      }
      return true;
   }

   // vrdreview output location can vary according to user
   // This function will look for them in .mpg cut dir and then .mpg dir
   public static String vrdreviewFileSearch(String startFile) {
      String baseFile = string.basename(startFile);
      String s;
      String sep = File.separator;
      Stack<String> tryit = new Stack<String>();
      
      // This block honors sub-folders in file naming above mpegCutDir
      // (mpegCutDir could be same as mpegDir, so look for cut files 1st)
      s = config.mpegCutDir + sep + string.replaceSuffix(startFile, "_cut.mpg");
      tryit.add(s);
      s = config.mpegCutDir + sep + string.replaceSuffix(startFile, "_cut.ts");
      tryit.add(s);
      s = config.mpegCutDir + sep + string.replaceSuffix(startFile, ".mpg");
      tryit.add(s);
      s = config.mpegCutDir + sep + string.replaceSuffix(startFile, ".ts");
      tryit.add(s);
      
      // This block looks at top mpegCutDir level
      // (mpegCutDir could be same as mpegDir, so look for cut files 1st)
      s = config.mpegCutDir + sep + string.replaceSuffix(baseFile, "_cut.mpg");
      tryit.add(s);
      s = config.mpegCutDir + sep + string.replaceSuffix(baseFile, "_cut.ts");
      tryit.add(s);
      s = config.mpegCutDir + sep + string.replaceSuffix(baseFile, ".mpg");
      tryit.add(s);
      s = config.mpegCutDir + sep + string.replaceSuffix(baseFile, ".ts");
      tryit.add(s);
      
      // This block honors sub-folders in file naming above mpegDir
      s = config.mpegDir + sep + string.replaceSuffix(startFile, "_cut.mpg");
      tryit.add(s);
      s = config.mpegDir + sep + string.replaceSuffix(startFile, "_cut.ts");
      tryit.add(s);
      s = config.mpegDir + sep + string.replaceSuffix(startFile, " (02).mpg");
      tryit.add(s);
      s = config.mpegDir + sep + string.replaceSuffix(startFile, " (02).ts");
      tryit.add(s);
      s = config.mpegDir + sep + string.replaceSuffix(startFile, ".mpg");
      tryit.add(s);
      s = config.mpegDir + sep + string.replaceSuffix(startFile, ".ts");
      tryit.add(s);
      
      // This block looks at top mpegDir level
      s = config.mpegDir + sep + string.replaceSuffix(baseFile, "_cut.mpg");
      tryit.add(s);
      s = config.mpegDir + sep + string.replaceSuffix(baseFile, "_cut.ts");
      tryit.add(s);
      s = config.mpegDir + sep + string.replaceSuffix(baseFile, " (02).mpg");
      tryit.add(s);
      s = config.mpegDir + sep + string.replaceSuffix(baseFile, " (02).ts");
      tryit.add(s);
      s = config.mpegDir + sep + string.replaceSuffix(baseFile, ".mpg");
      tryit.add(s);
      s = config.mpegDir + sep + string.replaceSuffix(baseFile, ".ts");
      tryit.add(s);

      for (String f : tryit) {
         if (file.isFile(f)) {
            return f;
         }
      }
      return null;
   }
}
