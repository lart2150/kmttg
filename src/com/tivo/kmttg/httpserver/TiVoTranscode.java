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
import java.io.File;
import java.io.IOException;
import java.util.Stack;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.http;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;

public class TiVoTranscode extends Transcode {
   private Thread thread = null;
   String inputUrl = null;
   String tivo = "";
   
   public TiVoTranscode(String inputUrl, String name, String tivo) {
      super(inputUrl);
      if (!inputUrl.contains("x-tivo-mpeg-ts"))
         inputUrl += "&Format=video/x-tivo-mpeg-ts";
      this.inputUrl = inputUrl;
      this.name = name;
      this.tivo = tivo;
   }
   
   @Override
   public String hls() {
      format = "hls";
      String urlBase = config.httpserver_cache_relative;
      String args = TranscodeTemplates.hls(urlBase, maxrate);
      base = config.httpserver_cache;
      if (! file.isDir(base))
         new File(base).mkdirs();
      prefix = "t" + config.httpserver.transcode_counter;
      segmentFile = base + File.separator + prefix + ".m3u8";
      String textFile = segmentFile + ".txt";
      String segments = base + File.separator + prefix + "-%05d.ts";
      String[] ffArgs = args.split(" ");
      Stack<String> command = new Stack<String>();
      command.add(config.ffmpeg);
      command.add("-i");
      command.add("-");
      for (String c : ffArgs)
         command.add(c);
      command.add(segmentFile);
      command.add(segments);

      // Need 2 piped processes in addition to download thread
      log.print(">> Transcoding TiVo download to HLS " + inputUrl + " ...");
      
      java.lang.Runtime rt = java.lang.Runtime.getRuntime();
      String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
      String[] tivolibre = {
         javaBin,
         "-cp",
         config.programDir + File.separator + "kmttg.jar",
         "net.straylightlabs.tivolibre.DecoderApp",
         "--mak",
         config.MAK,
      };
      String[] ffmpeg = new String[command.size()];
      int i=0;
      for (String s : command)
         ffmpeg[i++] = s;
      try {         
         log.print(
            inputUrl + " | " +
            TranscodeTemplates.printArray(tivolibre) + " | " +
            TranscodeTemplates.printArray(ffmpeg)
         );
         
         p1 = rt.exec(tivolibre);
         p2 = rt.exec(ffmpeg);
         
         Piper pipe = new Piper(
            new BufferedInputStream(p1.getInputStream()),
            new BufferedOutputStream(p2.getOutputStream())
         );
         new Thread(pipe).start();
         RunnableInputDrainer des = new RunnableInputDrainer(p2.getErrorStream());
         new Thread(des).start();
         
         // Start java download attached to tivodecode pipe in a separate thread
         final String urlString = inputUrl;
         Runnable r = new Runnable() {
            public void run () {
               try {
                  http.downloadPiped(urlString, "tivo", config.MAK, p1.getOutputStream(), true, null);
               }
               catch (Exception e) {
                  error("downloadPiped - " + e.getMessage());
                  Thread.currentThread().interrupt();
                  return;
               }
            }
         };
         thread = new Thread(r);
         thread.start();
      } catch (IOException e) {
         error("hls - " + e.getMessage());
         return null;
      }
      
      returnFile = urlBase + prefix + ".m3u8";
      try {
         // Wait for segmentFile to get created
         int counter = 0; int max = config.httpserver_ffmpeg_wait;
         while( file.size(segmentFile) == 0 && counter < max ) {
            if (! thread.isAlive()) {
               error("ffmpeg transcode stopped");
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
         error("TiVoTranscode sleep - " + e.getMessage());
      }
      createTextFile(textFile, name);
      return returnFile;
   }
   
   @Override
   public String webm() {
      format = "webm";
      String args = TranscodeTemplates.webm(maxrate);
      base = config.httpserver_cache;
      if (! file.isDir(base))
         new File(base).mkdirs();
      prefix = "t" + config.httpserver.transcode_counter;
      returnFile = base + File.separator + prefix + ".webm";
      String textFile = returnFile + ".txt";
      String[] ffArgs = args.split(" ");
      Stack<String> command = new Stack<String>();
      command.add(config.ffmpeg);
      command.add("-i");
      command.add("-");
      for (String c : ffArgs)
         command.add(c);
      command.add(returnFile);

      // Need 2 piped processes in addition to download thread
      log.print(">> Transcoding TiVo download to webm " + inputUrl + " ...");
      
      java.lang.Runtime rt = java.lang.Runtime.getRuntime();
      String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
      String[] tivolibre = {
         javaBin,
         "-cp",
         config.programDir + File.separator + "kmttg.jar",
         "net.straylightlabs.tivolibre.DecoderApp",
         "--mak",
         config.MAK,
      };
      String[] ffmpeg = new String[command.size()];
      int i=0;
      for (String s : command)
         ffmpeg[i++] = s;
      try {         
         log.print(
            inputUrl + " | " +
            TranscodeTemplates.printArray(tivolibre) + " | " +
            TranscodeTemplates.printArray(ffmpeg)
         );
         
         p1 = rt.exec(tivolibre);
         p2 = rt.exec(ffmpeg);
         
         Piper pipe = new Piper(
            new BufferedInputStream(p1.getInputStream()),
            new BufferedOutputStream(p2.getOutputStream())
         );
         new Thread(pipe).start();
         RunnableInputDrainer des = new RunnableInputDrainer(p2.getErrorStream());
         new Thread(des).start();
         
         // Start java download attached to tivodecode pipe in a separate thread
         final String urlString = inputUrl;
         Runnable r = new Runnable() {
            public void run () {
               try {
                  http.downloadPiped(urlString, "tivo", config.MAK, p1.getOutputStream(), true, null);
               }
               catch (Exception e) {
                  error("downloadPiped - " + e.getMessage());
                  Thread.currentThread().interrupt();
                  return;
               }
            }
         };
         thread = new Thread(r);
         thread.start();
      } catch (IOException e) {
         error("webm - " + e.getMessage());
         return null;
      }
      
      try {
         // Wait for returnFile to get created
         int counter = 0; int max = config.httpserver_ffmpeg_wait;
         while( file.size(returnFile) == 0 && counter < max ) {
            if (! thread.isAlive()) {
               error("ffmpeg transcode stopped");
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
         error("TiVoTranscode sleep - " + e.getMessage());
      }
      createTextFile(textFile, name);
      return returnFile;
   }
   
   @Override
   public void kill() {
      super.kill();
      if (thread != null) {
         thread.interrupt();
      }
   }
   
   @Override
   public String getTivoName() {
      return tivo;
   }
}
