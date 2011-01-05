package com.tivo.kmttg.util;

import java.io.InputStream;
import java.io.OutputStream;

public class PipeHandler extends Thread {
   private InputStream in;
   private OutputStream out;
      
   PipeHandler(InputStream in, OutputStream out) {
     this.in = in;
     this.out = out;
   }
   
   public void run() {
     try {
        int i = -1;
        byte[] buf = new byte[1024];
        while ((i = in.read(buf)) != -1) {
           if (Thread.interrupted()) {
              out.close();
              in.close();
              throw new InterruptedException("Killed by user");
           }
           out.write(buf, 0, i);
           out.flush();
        }
     } catch(Exception e) {
        log.error("PipeHandler exception: " + e.getMessage());
     }
   }
}
