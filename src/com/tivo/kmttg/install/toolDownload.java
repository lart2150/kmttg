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

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class toolDownload {
   String windows_file = "http://kmttg.googlecode.com/files/kmttg_win32_tools_v0p8l.zip";
   String mac_file = "http://kmttg.googlecode.com/files/kmttg_MacOSX_tools_v0p8l.zip";
   String localFileName = null;
   
   public String download(String dir, String os) {
      debug.print("dir=" + dir + " os=" + os);
      String urlString = null;
      if (os.equals("windows")) {
         urlString = windows_file;
      } else if (os.equals("mac")) {
         urlString = mac_file;
      }
      if (urlString != null) {
         if (downloadUrl(dir, urlString)) {
            return localFileName;
         } else {
            return null;
         }
      }
      return null;
   }
   
   public Boolean downloadUrl(String dir, String urlString) {
      debug.print("dir=" + dir + " urlString=" + urlString);
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
          
          localFileName = getFileName(dir, urlString);
          out = new RandomAccessFile(localFileName, "rw");
          
          Integer howManyBytes;
          Integer readSoFar = 0;
          byte[] bytesIn = new byte[BLOCK_SIZE];
          
          while ((howManyBytes = in.read(bytesIn)) >= 0) {
             out.write(bytesIn, 0, howManyBytes);
             readSoFar += howManyBytes;
             Float f = 100*readSoFar.floatValue()/size.floatValue();
             Integer pct = f.intValue();
             String title = String.format("download: %d%% %s", pct, config.kmttg);
             config.gui.progressBar_setValue(pct);
             config.gui.setTitle(title);
             config.gui.refresh();
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
      
   private String getFileName(String dir, String urlString) {
      debug.print("dir=" + dir + " urlString=" + urlString);
      String tempString;
      int lastSlash = urlString.lastIndexOf('/');
      
      if (lastSlash >= 0) {
         tempString = urlString.substring(lastSlash + 1);
      } else {
         tempString = new String("");
      }
      
      if (tempString.length() == 0) {
         tempString = new String("Default.txt");
      }
      tempString = dir + File.separator + tempString;
      
      return tempString;
   }

}
