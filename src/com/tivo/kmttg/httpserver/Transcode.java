package com.tivo.kmttg.httpserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
      private BufferedReader reader;
      public RunnableInputDrainer(InputStream is, boolean dump) {
         this.reader = new BufferedReader(new InputStreamReader(is));
      }
      @SuppressWarnings("unused")
      public RunnableInputDrainer(InputStream is) {
         this(is, false);
      }
      public void run() {
         try {
            String line;
            while((line = reader.readLine()) != null) {log.print(line);}
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
         try {
            p1 = rt.exec(tivodecode);
            p2 = rt.exec((String[])ff.toArray());
            RunnableInputDrainer des = new RunnableInputDrainer(p2.getErrorStream(), false);
            new Thread(des).start();
         } catch (IOException e) {
            log.error("webm - " + e.getMessage());
            return null;
         }
         Piper pipe = new Piper(p1.getInputStream(), p2.getOutputStream());
         new Thread(pipe).start();
         ss.attachProcess(p2);
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
