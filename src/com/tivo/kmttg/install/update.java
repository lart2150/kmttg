package com.tivo.kmttg.install;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.NoRouteToHostException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.swing.JOptionPane;

import com.tivo.kmttg.gui.SwingWorker;
import com.tivo.kmttg.gui.help;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;

public class update {
   
   public static void update_kmttg_background() {
      class backgroundRun extends SwingWorker<Object, Object> {
         protected Object doInBackground() {
            update_kmttg();
            return null;
         }
      }
      backgroundRun b = new backgroundRun();
      b.execute();
   }
   
   public static void update_tools_background() {
      class backgroundRun extends SwingWorker<Object, Object> {
         protected Object doInBackground() {
            toolDownload t = new toolDownload();
            String version;
            if (config.OS.equals("windows"))
               version = t.windows_file;
            else
               version = t.mac_file;
            int response = JOptionPane.showConfirmDialog(
               config.gui.getJFrame(),
               "Install tools file: " + version + " ?",
               "Confirm",
               JOptionPane.YES_NO_OPTION,
               JOptionPane.QUESTION_MESSAGE
            );
            if (response == JOptionPane.YES_OPTION) {
               String zipFile = t.download(config.programDir, config.OS);
               config.gui.progressBar_setValue(0);
               config.gui.setTitle(config.kmttg);
               if (zipFile != null) {
                  if (Unzip.unzip(config.programDir, zipFile) ) {
                     log.warn("Tools update complete");
                     file.delete(zipFile);
                  }
               }
            }
            return null;
         }
      }
      backgroundRun b = new backgroundRun();
      b.execute();
   }
   
   private static void update_kmttg() {
      String kmttg_jar = config.programDir + "/kmttg.jar";
      if (file.isFile(kmttg_jar)) {
         String[] s = config.kmttg.split("\\s+");
         String installed_version = s[1];
         String current_version = help.getVersion();
         if (installed_version != null && current_version != null) {
            if (installed_version.equals(current_version)) {
               log.print("You are running up to date version: " + installed_version);
            } else {
               log.print("Installed version: " + installed_version);
               log.print("Available version: " + current_version);
               
               // Ask user to install new version
               int response = JOptionPane.showConfirmDialog(
                  config.gui.getJFrame(),
                  "Install new version: " + current_version + " ?",
                  "Confirm",
                  JOptionPane.YES_NO_OPTION,
                  JOptionPane.QUESTION_MESSAGE
               );
               if (response == JOptionPane.YES_OPTION) {
                  String url = "http://sourceforge.net/projects/kmttg/files/kmttg_" +
                     current_version + ".zip/download?use_mirror=autoselect";
                  String zipFile = downloadUrl(config.programDir, url);
                  if (zipFile != null) {
                     if ( unzip(config.programDir, zipFile) ) {
                        log.print("Successfully updated kmttg installation.");
                        file.delete(zipFile);
                        // Ask user if OK to exit kmttg
                        int response2 = JOptionPane.showConfirmDialog(
                           config.gui.getJFrame(),
                           "OK to exit kmttg?",
                           "Confirm",
                           JOptionPane.YES_NO_OPTION,
                           JOptionPane.QUESTION_MESSAGE
                        );
                        if (response2 == JOptionPane.YES_OPTION) {
                           System.exit(0);
                        }
                     } else {
                        log.error("Trouble unzipping file: " + zipFile);
                     }
                  }
               }
            }
         } else {
            log.error("Can't determine installed and/or available versions");
         }
      } else {
         log.error("Cannot find kmttg.jar to determine installed version");
      }
   }
      
   private static String downloadUrl(String dir, String urlString) {
      BufferedInputStream in = null;
      RandomAccessFile out = null;
      int BLOCK_SIZE = 4096;
      try {
          URL url = new URL(urlString);
          log.print("Downloading file: " + urlString + " ...");
          URLConnection con = url.openConnection();
          
          in = new BufferedInputStream(con.getInputStream());
          
          String localFileName = getFileName(dir, urlString);
          out = new RandomAccessFile(localFileName, "rw");
          
          Integer howManyBytes;
          byte[] bytesIn = new byte[BLOCK_SIZE];          
          while ((howManyBytes = in.read(bytesIn)) >= 0) {
             out.write(bytesIn, 0, howManyBytes);
          }
          
          // Done
          in.close();
          out.close();            
          log.print("Download completed successfully");
          
          return localFileName;
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
      return null;
   }
   
   private static String getFileName(String dir, String urlString) {
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
   
   @SuppressWarnings("unchecked")
   private static Boolean unzip(String dir, String file) {
       Enumeration entries;
       ZipFile zipFile;

       try {
         zipFile = new ZipFile(file);

         entries = zipFile.entries();

         String fullName;
         while(entries.hasMoreElements()) {
           ZipEntry entry = (ZipEntry)entries.nextElement();
           fullName = dir + File.separator + entry.getName();

           if(entry.isDirectory()) {
             // This is not robust, just for demonstration purposes.
              log.print("Extracting directory: " + fullName);
              (new File(fullName)).mkdir();
              continue;
           }

           log.print("Extracting file: " + fullName);
           copyInputStream(
              zipFile.getInputStream(entry),
              new BufferedOutputStream(new FileOutputStream(fullName))
           );
         }
         zipFile.close();      
               
         // Set all files as executable if non-Windows platform
         if ( ! config.OS.equals("windows") ) {
            String[] command = new String[] {"chmod", "-R", "ugo+x", dir};
            Runtime.getRuntime().exec(command);
         }

         return true;
         
       } catch (IOException ioe) {
          log.error(ioe.getMessage());
          return false;
       }
     }

     private static void copyInputStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int len;
      
        while((len = in.read(buffer)) >= 0)
          out.write(buffer, 0, len);
      
        in.close();
        out.close();
     }
}
