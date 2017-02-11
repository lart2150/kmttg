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
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.NoRouteToHostException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;

import com.tivo.kmttg.gui.help;
import com.tivo.kmttg.main.auto;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;

public class update {
   
   public static void update_kmttg_background() {
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
               Alert alert = new Alert(AlertType.CONFIRMATION);
               alert.setTitle("Confirm");
               config.gui.setFontSize(alert, config.FontSize);
               alert.setContentText("Install new version: " + current_version + " ?");
               Optional<ButtonType> result = alert.showAndWait();
               if (result.get() == ButtonType.OK) {
                  final String fname = "kmttg_" + current_version + ".zip";
                  String base = baseDownload.getBase();
                  if (base == null) {
                     log.error("toolDownload - error retrieving base download URL");
                     return;
                  }
                  final String url = base + "/" + fname;
                  auto.serviceStopIfNeeded();
                  Task<Void> task = new Task<Void>() {
                     @Override public Void call() {
                        String filename = config.programDir + File.separator + fname;
                        String zipFile = downloadUrl(filename, url);
                        if (zipFile != null) {
                           // Determine if zipFile contains a redirect
                           String redirect = util.getRedirect(zipFile);
                           if (redirect != null) {
                              zipFile = downloadUrl(filename, redirect);
                           }
                        }
                        if (zipFile != null) {
                           if ( unzip(config.programDir, zipFile) ) {
                              log.print("Successfully updated kmttg installation.");
                              file.delete(zipFile);
                              // NOTE: With Java 8 runlater doesn't work, so just restart without asking
                              restartApplication();
                              // Ask user if OK to restart kmttg
                              /*Platform.runLater(new Runnable() {
                                 @Override public void run() {
                                    Alert alert = new Alert(AlertType.CONFIRMATION);
                                    alert.setTitle("Confirm");
                                    config.gui.setFontSize(alert, config.FontSize);
                                    alert.setContentText("OK to restart kmttg?");
                                    Optional<ButtonType> result = alert.showAndWait();
                                    if (result.get() == ButtonType.OK) {
                                       //System.exit(0);
                                       restartApplication();
                                    }
                                 }
                              });*/
                           } else {
                              log.error("Trouble unzipping file: " + zipFile);
                           }
                        }
                        return null;
                     }
                  };
                  new Thread(task).start();
               }
            }
         } else {
            log.error("Can't determine installed and/or available versions");
         }
      } else {
         log.error("Cannot find kmttg.jar to determine installed version");
      }
   }
   
   public static void update_tools_background() {
      final toolDownload t = new toolDownload();
      String version;
      if (config.OS.equals("windows"))
         version = t.windows_file;
      else
         version = t.mac_file;
      final String installedVersionFile = config.programDir + File.separator + t.tools_version;
      String query = "Install tools file: " + version + " ?";
      if (file.isFile(installedVersionFile)) {
         String lastVersion = getToolsVersion(installedVersionFile);
         if (lastVersion != null)
            query = "Last installed file: " + lastVersion + "\n" + query;
      }
      // Ask user to install new version
      Alert alert = new Alert(AlertType.CONFIRMATION);
      alert.setTitle("Confirm");
      config.gui.setFontSize(alert, config.FontSize);
      alert.setContentText(query);
      Optional<ButtonType> result = alert.showAndWait();
      if (result.get() == ButtonType.OK) {
         class backgroundRun extends Task<Void> {
            String version;
            public backgroundRun(String version) {
               this.version = version;
            }
            @Override protected Void call() {
               String zipFile = t.download(config.programDir, config.OS);
               Platform.runLater(new Runnable() {
                  @Override public void run() {
                     config.gui.progressBar_setValue(0);
                     config.gui.setTitle(config.kmttg);
                  }
               });
               if (zipFile != null) {
                  // Remove config.programDir/__MACOSX if it exists
                  String macosx = config.programDir + File.separator + "__MACOSX";
                  if (file.isDir(macosx)) {
                     String[] command = new String[] {"/bin/rm", "-fr", macosx};
                     try {
                        log.warn("Removing: " + macosx);
                        Runtime.getRuntime().exec(command);
                     } catch (IOException e) {
                        // fail silently
                     }                     
                  }
                  if (Unzip.unzip(config.programDir, zipFile) ) {
                     log.warn("Tools update complete");
                     file.delete(zipFile);
                     writeToolsVersion(installedVersionFile, version);
                  }
               }
               return null;
            }
         };
         backgroundRun b = new backgroundRun(version);
         new Thread(b).start();
      }
   }
      
   @SuppressWarnings("resource")
   private static String downloadUrl(String localFileName, String urlString) {
      BufferedInputStream in = null;
      RandomAccessFile out = null;
      int BLOCK_SIZE = 4096;
      try {
          URL url = new URL(urlString);
          log.print("Downloading file: " + urlString + " ...");
          URLConnection con = url.openConnection();
          
          in = new BufferedInputStream(con.getInputStream());          
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
      
   @SuppressWarnings("resource")
   private static Boolean unzip(String dir, String file) {
       Enumeration<?> entries;
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
           BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(fullName));
           copyInputStream(zipFile.getInputStream(entry), out);
           out.close();
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
     
     private static String getToolsVersion(String fileName) {
        String version = null;
        try {
           BufferedReader in = new BufferedReader(new FileReader(new File(fileName)));
           version = in.readLine();
           in.close();
        } catch (Exception e) {
           log.error("getToolsVersion - " + e.getMessage());
        }
        return version;
     }
     
     private static Boolean writeToolsVersion(String fileName, String version) {
        try {
           BufferedWriter writer = new BufferedWriter(new FileWriter(fileName));
           writer.write(version + "\n");
           writer.close();
           return true;
        } catch (Exception e) {
           log.error("getToolsVersion - " + e.getMessage());
        }
        return false;
     }
          
     private static void restartApplication() {
        try {
           final String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
           final File currentJar = new File(update.class.getProtectionDomain().getCodeSource().getLocation().toURI());
     
           /* is it a jar file? */
           if(!currentJar.getName().endsWith(".jar"))
              return;
     
           /* Build command: java -jar application.jar */
           final ArrayList<String> command = new ArrayList<String>();
           command.add(javaBin);
           command.add("-jar");
           command.add(currentJar.getPath());
     
           final ProcessBuilder builder = new ProcessBuilder(command);
           builder.start();
           System.exit(0);
        } catch (Exception e) {
           log.error("restartApplication - " + e.getMessage());
        }
     }

}
