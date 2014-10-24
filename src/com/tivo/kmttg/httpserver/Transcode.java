package com.tivo.kmttg.httpserver;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Stack;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.log;

public class Transcode {
   SocketProcessInputStream ss = null;
   backgroundProcess process = null;
   Process p1 = null;
   Process p2 = null;
   String inputFile = null;
   
   public Transcode(String inputFile) {
      this.inputFile = inputFile;
   }
   private static class RunnableInputDrainer implements Runnable {
      InputStream is;
      public RunnableInputDrainer(InputStream is) {
         this.is = is;
      }
      public void run() {
         try {
            byte[] b = new byte[2048];
            while(is.read(b) != -1) {}
         } catch (IOException e) {log.error("Drainer - " + e.getMessage());}         
      }
   }
   
   public SocketProcessInputStream webm() {
      try {
         ss = new SocketProcessInputStream();
      } catch (Exception e) {
         log.error("webm - " + e.getMessage());
         return null;
      }
      String sockStr = "tcp://127.0.0.1:" + ss.getPort();
      String[] ffArgs = {
         "-vcodec",
         "libvpx",
         "-cpu-used",
         "-5",
         "-deadline",
         "realtime",
         "-f",
         "webm",
         sockStr            
      };
      Stack<String> command = new Stack<String>();
      if (inputFile.toLowerCase().endsWith(".tivo")) {
         log.print(">> Transcoding TiVo file to webm " + inputFile + " ...");
         java.lang.Runtime rt = java.lang.Runtime.getRuntime();
         String[] tivodecode = {
            config.tivodecode,
            "--mak",
            config.MAK,
            "--no-verify",
            inputFile
         };
         Stack<String> ff = new Stack<String>();
         ff.add(config.ffmpeg);
         ff.add("-i");
         ff.add("-");
         for (String c : ffArgs)
            ff.add(c);
         String[] ffmpeg = new String[ff.size()];
         int i=0;
         for (String s : ff)
            ffmpeg[i++] = s;
         try {
            p1 = rt.exec(tivodecode);
            p2 = rt.exec(ffmpeg);
            RunnableInputDrainer des = new RunnableInputDrainer(p2.getErrorStream());
            new Thread(des).start();
         } catch (IOException e) {
            log.error("webm - " + e.getMessage());
            return null;
         }
         Piper pipe = new Piper(
            new BufferedInputStream(p1.getInputStream()),
            new BufferedOutputStream(p2.getOutputStream())
         );
         new Thread(pipe).start();
         ss.attachProcess(p1);
      } else {
         command.add(config.ffmpeg);
         command.add("-i");
         command.add(inputFile);
         for (String c : ffArgs)
            command.add(c);
         
         process = new backgroundProcess();
         log.print(">> Transcoding to webm " + inputFile + " ...");
         if ( process.run(command) ) {
            log.print(process.toString());
            ss.attachProcess(process.getProcess());
         } else {
            log.error("Failed to start command: " + process.toString());
            process.printStderr();
            process = null;
            return null;
         }
      }
      return ss;
   }
   
   public FileInputStream hls() {
      String base = config.programDir + File.separator + "web";
      String prefix = "test";
      String segmentFile = base + File.separator + prefix + ".m3u8";
      String segments = base + File.separator + prefix + "-%05d.ts";
      String[] ffArgs = {
         "-threads", "0", "-y", "-segment_format", "mpegts", "-f", "segment",
         "-map_metadata", "-1", "-vcodec", "libx264", "-map", "0:1", "-crf", "19",
         "-maxrate", "3000k", "-bufsize", "6000k", "-preset", "veryfast",
         "-x264opts", "cabac=0:8x8dct=1:bframes=0:subme=0:me_range=4:rc_lookahead=10:me=dia:no_chroma_me:8x8dct=0:partitions=none:bframes=3:cabac=1",
         "-flags", "-global_header", "-segment_time", "3", "-segment_start_number", "0",
         "-force_key_frames", "expr:gte(t,n_forced*3)", "-sn",
         "-acodec", "aac", "-map", "0:2", "-strict", "-2", "-cutoff", "15000", "-ac", "2",
         "-ab", "217k", "-segment_list", segmentFile, segments            
      }; 
      Stack<String> command = new Stack<String>();
      command.add(config.ffmpeg);
      command.add("-i");
      command.add(inputFile);
      for (String c : ffArgs)
         command.add(c);
      
      process = new backgroundProcess();
      log.print(">> Transcoding to hls " + inputFile + " ...");
      if ( process.run(command) ) {
         log.print(process.toString());
      } else {
         log.error("Failed to start command: " + process.toString());
         process.printStderr();
         process = null;
         return null;
      }
      try {
         Thread.sleep(2000);
         return new FileInputStream(segmentFile);
      } catch (Exception e) {
         log.error("hls - " + e.getMessage());
      }
      return null;
   }
   
   public void kill() {
      if (process != null) {
         process.kill();
         log.warn("Killing transcode: " + process.toString());
      }
      if (p1 != null)
         p1.destroy();
      if (p2 != null)
         p2.destroy();
   }

}
