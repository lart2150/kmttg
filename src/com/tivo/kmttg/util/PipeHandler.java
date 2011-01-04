package com.tivo.kmttg.util;

import java.io.InputStream;
import java.io.OutputStream;

public class PipeHandler extends Thread {
   private final InputStream in;
   private final OutputStream out;
   private final Process proc2;
      
   PipeHandler(InputStream in, Process proc2) {
     this.in = in;
     this.out = proc2.getOutputStream();
     this.proc2 = proc2;
   }
   
   public void run() {
     try {
        int i = -1;
        byte[] buf = new byte[1024];
        while ((i = in.read(buf)) != -1) {
           if (Thread.interrupted()) {
              out.close();
              in.close();
              proc2.destroy();
              throw new InterruptedException("Killed by user");
           }
           out.write(buf, 0, i);
           out.flush();
        }
        // Kill proc2 now that InputStream is complete
        proc2.destroy();
     } catch(Exception e) {
        log.error("PipeHandler exception: " + e.getMessage());
     }
   }
}
