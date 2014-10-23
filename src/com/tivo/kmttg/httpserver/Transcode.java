package com.tivo.kmttg.httpserver;

import java.util.Stack;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.log;

public class Transcode {
   SocketProcessInputStream ss = null;
   backgroundProcess process = null;
   String inputFile = null;
   
   public Transcode(String inputFile) {
      this.inputFile = inputFile;
   }
   
   public SocketProcessInputStream webm() {
      try {
         ss = new SocketProcessInputStream();
      } catch (Exception e) {
         log.error("webm - " + e.getMessage());
         return null;
      }
      String sockStr = "tcp://127.0.0.1:" + ss.getPort();
      Stack<String> command = new Stack<String>();
      command.add(config.ffmpeg);
      command.add("-i");
      command.add(inputFile);
      command.add("-vcodec");
      command.add("libvpx");
      command.add("-cpu-used");
      command.add("-5");
      command.add("-deadline");
      command.add("realtime");
      command.add("-f");
      command.add("webm");
      command.add(sockStr);
      
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

      return ss;
   }
   
   public void kill() {
      process.kill();
      log.warn("Killing 'webm transcode': " + process.toString());
   }

}
