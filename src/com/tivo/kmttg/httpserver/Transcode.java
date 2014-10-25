package com.tivo.kmttg.httpserver;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Stack;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class Transcode {
   SocketProcessInputStream ss = null;
   String returnFile = null;
   backgroundProcess process = null;
   Process p1 = null;
   Process p2 = null;
   String inputFile = null;
   String base = "";
   String prefix = "";
   int count = 0;
   String format = "";
   
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
      String args = "-threads 0 -y -vcodec libvpx -crf 19 -sn -acodec libvorbis -ac 2 -ab 217k -f webm " + sockStr;
      String[] ffArgs = args.split(" ");
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
   
   public String hls() {
      format = "hls";
      String args = "-ss 0 -threads 0 -y -map_metadata -1 -vcodec libx264 -crf 19";
      args += " -maxrate 3000k -bufsize 6000k -preset veryfast";
      args += " -x264opts cabac=0:8x8dct=1:bframes=0:subme=0:me_range=4:rc_lookahead=10:me=dia:no_chroma_me:8x8dct=0:partitions=none:bframes=3:cabac=1";
      args += " -flags -global_header -force_key_frames expr:gte(t,n_forced*3) -sn";
      args += " -acodec aac -strict -2 -cutoff 15000 -ac 2 -ab 217k";
      args += " -segment_format mpegts -f segment -segment_time 30 -segment_start_number 0";
      //args += " -segment_list_entry_prefix /web/ -segment_wrap 10 -segment_list_flags +live -segment_list";
      args += " -segment_list_entry_prefix /web/ -segment_list_flags +live -segment_list";
      base = config.programDir + File.separator + "web";
      prefix = string.basename(inputFile) + config.httpserver.transcode_counter;
      String segmentFile = base + File.separator + prefix + ".m3u8";
      String segments = base + File.separator + prefix + "-%05d.ts";
      String[] ffArgs = args.split(" ");
      Stack<String> command = new Stack<String>();
      command.add(config.ffmpeg);
      command.add("-i");
      command.add(inputFile);
      for (String c : ffArgs)
         command.add(c);
      command.add(segmentFile);
      command.add(segments);
      
      process = new backgroundProcess();
      log.print(">> Transcoding to hls " + inputFile + " ...");
      if ( process.run(command) ) {
         log.print(process.toString());
         returnFile = "/web/" + prefix + ".m3u8";
         try {
            Thread.sleep(2000);
         } catch (InterruptedException e) {
            log.error("Transcode sleep - " + e.getMessage());
         }
      } else {
         log.error("Failed to start command: " + process.toString());
         process.printStderr();
         process = null;
         return null;
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
   
   public boolean isRunning() {
      // hls is special case since once files are created don't re-create
      if (count > 0 && format.equals("hls"))
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

}
