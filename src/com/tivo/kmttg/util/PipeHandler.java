package com.tivo.kmttg.util;

public class PipeHandler extends Thread {
   private volatile Process p1;
   private volatile Process p2;
   private volatile Boolean keepRunning = true;
      
   PipeHandler(Process p1, Process p2) {
     this.p1 = p1;
     this.p2 = p2;
   }
   
   public void run() {
      while(keepRunning) {
         try {
            Thread.sleep(100);
            int i = -1;
            byte[] buf = new byte[1024];
            while ((i = p1.getInputStream().read(buf)) != -1) {
               if (Thread.interrupted()) {
                  keepRunning = false;
               }
               p2.getOutputStream().write(buf, 0, i);
               p2.getOutputStream().flush();
            }
         } catch(Exception e) {
            log.error("PipeHandler exception: " + e.getMessage());
            killProcs();
            keepRunning = false;
         }
      }
      killProcs();
   }
   
   public void killProcs() {
      if (p1 != null) p1.destroy();
      if (p2 != null) p2.destroy();
   }
   
   public void stopRunning() {
      killProcs();
      keepRunning = false;
   }
}
