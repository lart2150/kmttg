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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Stack;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.kmttg;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class Transcode {
   String returnFile = null;
   String segmentFile = null;
   int duration = 0;
   String name = null;
   backgroundProcess process = null;
   Process p1 = null;
   Process p2 = null;
   String inputFile = null;
   String base = config.httpserver_cache;
   String prefix = "";
   String lastStderr = "";
   int count = 0;
   String format = "";
   String maxrate = "3000k";
   Stack<String> errors = new Stack<String>();
   
   public Transcode(String inputFile) {
      this.inputFile = inputFile;
      this.name = inputFile;
      if (! file.isDir(base))
         new File(base).mkdirs();
      setCachePrefix(); // sets prefix variable
   }
   public class RunnableInputDrainer implements Runnable {
      InputStream is;
      public RunnableInputDrainer(InputStream is) {
         this.is = is;
      }
      public void run() {
         try {
            byte[] b = new byte[2048];
            while(is.read(b) != -1) {lastStderr = new String(b);}
         } catch (IOException e) {log.error("Drainer - " + e.getMessage());}         
      }
   }
   
   public String webm() {
      boolean isTivoFile = false;
      if (inputFile.toLowerCase().endsWith(".tivo"))
         isTivoFile = true;
      format = "webm";
      returnFile = base + File.separator + prefix + ".webm";
      String textFile = returnFile + ".txt";
      String args = TranscodeTemplates.webm(maxrate);
      String[] ffArgs = args.split(" ");
      Stack<String> command = new Stack<String>();
      command.add(config.ffmpeg);
      command.add("-i");
      if (isTivoFile)
         command.add("-");
      else
         command.add(inputFile);
      for (String c : ffArgs)
         command.add(c);
      command.add(returnFile);

      if (isTivoFile) {
         // Need 2 piped processes
         log.print(">> Transcoding TiVo file to webm " + inputFile + " ...");
         java.lang.Runtime rt = java.lang.Runtime.getRuntime();
         String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
         String[] tivolibre = {
            javaBin,
            "-cp",
            config.programDir + File.separator + "kmttg.jar",
            "net.straylightlabs.tivolibre.DecoderApp",
            "--mak",
            config.MAK,
            "--input",
            inputFile
         };
         String[] ffmpeg = new String[command.size()];
         int i=0;
         for (String s : command)
            ffmpeg[i++] = s;
         try {
            p1 = rt.exec(tivolibre);
            p2 = rt.exec(ffmpeg);
            log.print(
               TranscodeTemplates.printArray(tivolibre) + " | " +
               TranscodeTemplates.printArray(ffmpeg)
            );
            RunnableInputDrainer des = new RunnableInputDrainer(p2.getErrorStream());
            new Thread(des).start();
         } catch (IOException e) {
            error("webm - " + e.getMessage());
            return null;
         }
         Piper pipe = new Piper(
            new BufferedInputStream(p1.getInputStream()),
            new BufferedOutputStream(p2.getOutputStream())
         );
         new Thread(pipe).start();
      } else {
         process = new backgroundProcess();
         log.print(">> Transcoding to webm " + inputFile + " ...");
         if ( process.run(command) ) {
            log.print(process.toString());
         } else {
            error("Failed to start command: " + process.toString());
            process.printStderr();
            process = null;
            return null;
         }
      }
      createTextFile(textFile, inputFile);
      try {
         // Wait for returnFile to get created
         int counter = 0; int max = config.httpserver_ffmpeg_wait;
         while( file.size(returnFile) == 0 && counter < max ) {
            if (process != null && process.exitStatus() != -1) {
               error("webm ffmpeg transcode stopped: exit status = " + process.exitStatus());
               log.error(process.getStderr());
               errors.add(process.getStderrLast());
               return null;
            }
            Thread.sleep(1000);
            counter++;
         }
         if (counter >= max) {
            error("webm file not being created, assuming ffmpeg error");
            return null;
         }
      } catch (InterruptedException e) {
         error("Transcode sleep - " + e.getMessage());
      }
      return returnFile;
   }
   
   public String hls() {
      boolean isTivoFile = false;
      if (inputFile.toLowerCase().endsWith(".tivo"))
         isTivoFile = true;
      format = "hls";
      String urlBase = config.httpserver_cache_relative;
      String args = TranscodeTemplates.hls(urlBase, maxrate);
      segmentFile = base + File.separator + prefix + ".m3u8";
      String textFile = segmentFile + ".txt";
      String segments = base + File.separator + prefix + "-%05d.ts";
      String[] ffArgs = args.split(" ");
      Stack<String> command = new Stack<String>();
      command.add(config.ffmpeg);
      command.add("-i");
      if (isTivoFile)
         command.add("-");
      else
         command.add(inputFile);
      for (String c : ffArgs)
         command.add(c);
      command.add(segmentFile);
      command.add(segments);

      if (isTivoFile) {
         // Need 2 piped processes
         log.print(">> Transcoding TiVo file to HLS " + inputFile + " ...");
         java.lang.Runtime rt = java.lang.Runtime.getRuntime();
         String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
         String[] tivolibre = {
            javaBin,
            "-cp",
            config.programDir + File.separator + "kmttg.jar",
            "net.straylightlabs.tivolibre.DecoderApp",
            "--mak",
            config.MAK,
            "--input",
            inputFile
         };
         String[] ffmpeg = new String[command.size()];
         int i=0;
         for (String s : command)
            ffmpeg[i++] = s;
         try {
            p1 = rt.exec(tivolibre);
            p2 = rt.exec(ffmpeg);
            log.print(
               TranscodeTemplates.printArray(tivolibre) + " | " +
               TranscodeTemplates.printArray(ffmpeg)
            );
            RunnableInputDrainer des = new RunnableInputDrainer(p2.getErrorStream());
            new Thread(des).start();
         } catch (IOException e) {
            error("hls - " + e.getMessage());
            return null;
         }
         Piper pipe = new Piper(
            new BufferedInputStream(p1.getInputStream()),
            new BufferedOutputStream(p2.getOutputStream())
         );
         new Thread(pipe).start();
      } else {
         process = new backgroundProcess();
         log.print(">> Transcoding to hls " + inputFile + " ...");
         if ( process.run(command) ) {
            log.print(process.toString());
         } else {
            error("Failed to start command: " + process.toString());
            process.printStderr();
            process = null;
            return null;
         }
      }
      
      createTextFile(textFile, inputFile);
      returnFile = urlBase + prefix + ".m3u8";
      try {
         // Wait for segmentFile to get created
         int counter = 0; int max = config.httpserver_ffmpeg_wait;
         while( file.size(segmentFile) == 0 && counter < max ) {
            if (process != null && process.exitStatus() != -1) {
               error("hls ffmpeg transcode stopped");
               log.error(process.getStderr());
               errors.add(process.getStderrLast());
               return null;
            }
            Thread.sleep(1000);
            counter++;
         }
         if (counter >= max) {
            error("Segment file not being created, assuming ffmpeg error");
            return null;
         }
      } catch (InterruptedException e) {
         error("Transcode sleep - " + e.getMessage());
      }
      return returnFile;
   }
   
   public int exitStatus(Process proc) {
      try {
         int v = proc.exitValue();
         return v;
      }
      catch (IllegalThreadStateException i) {
         return -1;
      }
   }
   
   public void createTextFile(String textFile, String contents) {
      try {
         BufferedWriter ofp = new BufferedWriter(new FileWriter(textFile, false));
         ofp.write(contents + "\r\n");
         ofp.close();
      } catch (IOException e) {
         error("createTextFile - " + e.getMessage());
      }

   }
   
   // Determine unused video cache file prefix to use
   public void setCachePrefix() {
      String prefix_string = "t";
      Boolean go = true;
      int index = config.httpserver.transcode_counter;
      File[] files = new File(base).listFiles();
      String filePrefix;
      while (go) {
         filePrefix = prefix_string + index;
         boolean useThis = true;
         for (File f : files) {
            String basename = string.basename(f.getAbsolutePath());
            if (basename.startsWith(filePrefix)) {
               useThis = false;
            }
         }
         if (useThis) {
            prefix = prefix_string + index;
            return;
         }
         index++;
         if (index > 100) // prevent large number of cached files + inf loop
            go = false;
      }
      prefix = prefix_string + index;
   }
   
   public boolean isRunning() {
      // hls is special case since once files are created don't re-create
      if ( count > 0 && format.equals("hls"))
         return true;
      count++;
      boolean running = false;
      if (process != null && process.exitStatus() == -1)
         running = true;
      if (p1 != null && exitStatus(p1) == -1)
         running = true;
      if (p2 != null && exitStatus(p2) == -1)
         running = true;
      return running;
   }
   
   public void kill() {
      if (process != null) {
         if (! kmttg._shuttingDown)
            log.warn("Killing transcode: " + process.toString());
         process.kill();
      }
      if (p1 != null)
         p1.destroy();
      if (p2 != null)
         p2.destroy();
   }
   
   public void cleanup() {
      if (prefix.length() > 0 && base.length() > 0) {
         log.warn("Removing '" + prefix + "' transcode files in: " + base);
         File[] files = new File(base).listFiles();
         for (File f : files) {
            String basename = string.basename(f.getAbsolutePath());
            if (basename.startsWith(prefix)) {
               f.delete();
            }
         }
      }
   }
   
   // Extending methods should override appropriately
   public String getTivoName() {
      return null;
   }
   
   public void error(String message) {
      log.error(message);
      errors.add(message);
   }
   
   public String getErrors() {
      String message = "";
      for (String s : errors) {
         message += s + "\n";
      }
      return message;
   }
}
