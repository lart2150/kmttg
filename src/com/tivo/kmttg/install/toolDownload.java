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
package com.tivo.kmttg.install;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.NoRouteToHostException;
import java.net.URL;
import java.net.URLConnection;

import javafx.application.Platform;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class toolDownload {
   String windows_file = "kmttg_win32_tools_v2.1n.zip";
   String mac_file = "kmttg_MacOSX_tools_v2.1e.zip";
   String tools_version = "tools_version";
   
   public String download(String dir, String os) {
      String base = baseDownload.getBase();
      if (base == null) {
         log.error("toolDownload - error retrieving base download URL");
         return null;
      }
      base += "/tools/";
      debug.print("dir=" + dir + " os=" + os);
      String urlString = null;
      String localFileName = null;
      if (os.equals("windows")) {
         urlString = base + windows_file;
         localFileName = config.programDir + File.separator + windows_file;
      } else if (os.equals("mac")) {
         urlString = base + mac_file;
         localFileName = config.programDir + File.separator + mac_file;
      }
      if (urlString != null) {
         if (downloadUrl(urlString, localFileName)) {
            // Determine if localFileName contains a redirect
            String redirect = util.getRedirect(localFileName);
            if (redirect != null) {
               if (downloadUrl(redirect, localFileName)) {
                  return localFileName;
               } else {
                  return null;
               }
            }
            return localFileName;
         } else {
            return null;
         }
      }
      return null;
   }
   
   @SuppressWarnings("resource")
   private Boolean downloadUrl(String urlString, String localFileName) {
      BufferedInputStream in = null;
      RandomAccessFile out = null;
      Integer size = 0;
      int BLOCK_SIZE = 4096;
      try {
          URL url = new URL(urlString);
          log.warn("Downloading file: " + urlString + " ...");
          URLConnection con = url.openConnection();
          size = con.getContentLength();
          
          in = new BufferedInputStream(con.getInputStream());          
          out = new RandomAccessFile(localFileName, "rw");
          
          Integer howManyBytes;
          Integer readSoFar = 0;
          byte[] bytesIn = new byte[BLOCK_SIZE];
          int last_pct = 0;
          
          while ((howManyBytes = in.read(bytesIn)) >= 0) {
             out.write(bytesIn, 0, howManyBytes);
             readSoFar += howManyBytes;
             Float f = 100*readSoFar.floatValue()/size.floatValue();
             final Integer pct = f.intValue();
             final String title = String.format("download: %d%% %s", pct, config.kmttg);
             if (pct % 5 == 0 && pct > last_pct) {
                last_pct += 5;
                Platform.runLater(new Runnable() {
                   @Override public void run() {
                      config.gui.progressBar_setValue(pct);
                      config.gui.setTitle(title);
                   }
                });
             }
          }
          
          // Done
          in.close();
          out.close();            
          log.warn("Download completed successfully");
          
          return true;
      }
      catch (MalformedURLException e) {
         log.error(e.toString() + " - " + urlString);
      } 
      catch(NoRouteToHostException e)  {
         log.error("URL cannot be reached: " + urlString);
      }
      catch(ConnectException e)  {
         log.error("Connection error: " + e.getMessage());
      }
      catch(FileNotFoundException e)  {
         log.error("File or Path not found: " + e.getMessage());
      }
      catch(Exception e) {
         log.error(e.toString());
      }
      finally {
         try  {  in.close(); out.close();  }  catch(Exception ee)  {}
      }
      return false;
   }   
}
