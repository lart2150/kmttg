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
      this.inputUrl = inputUrl;
      this.name = name;
      this.tivo = tivo;
   }
   
   @Override
   public String hls() {
      format = "hls";
      String urlBase = config.httpserver_cache_relative;
      String args = TranscodeTemplates.hls(urlBase);
      base = config.httpserver_cache;
      if (! file.isDir(base))
         new File(base).mkdirs();
      prefix = "t" + config.httpserver.transcode_counter;
      String segmentFile = base + File.separator + prefix + ".m3u8";
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
      String[] tivodecode = {
         config.tivodecode,
         "--mak",
         config.MAK,
         "--no-verify",
         "-"
      };
      String[] ffmpeg = new String[command.size()];
      int i=0;
      for (String s : command)
         ffmpeg[i++] = s;
      try {         
         log.print(
            inputUrl + " | " +
            TranscodeTemplates.printArray(tivodecode) + " | " +
            TranscodeTemplates.printArray(ffmpeg)
         );
         
         p1 = rt.exec(tivodecode);
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
                  log.error("downloadPiped - " + e.getMessage());
                  Thread.currentThread().interrupt();
                  return;
               }
            }
         };
         thread = new Thread(r);
         thread.start();
      } catch (IOException e) {
         log.error("hls - " + e.getMessage());
         return null;
      }
      
      returnFile = urlBase + prefix + ".m3u8";
      try {
         // Wait for segmentFile to get created
         int counter = 0;
         while( file.size(segmentFile) == 0 && counter < 10 ) {
            Thread.sleep(1000);
            counter++;
         }
      } catch (InterruptedException e) {
         log.error("TiVoTranscode sleep - " + e.getMessage());
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
