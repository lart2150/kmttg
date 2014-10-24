package com.tivo.kmttg.httpserver;

import com.tivo.kmttg.util.log;

public class Piper implements java.lang.Runnable {
   private java.io.InputStream input;
   private java.io.OutputStream output;

   public Piper(java.io.InputStream input, java.io.OutputStream output) {
      this.input = input;
      this.output = output;
   }

   public void run() {
      try {
         byte[] b = new byte[512];
         int read = 1;
         while (read > -1) {
            read = input.read(b, 0, b.length);
            if (read > -1) {
               output.write(b, 0, read);
            }
         }
      } catch (Exception e) {
         log.error("Piper broken pipe - " + e.getMessage());
      } finally {
         try {
            input.close();
         } catch (Exception e) {
         }
         try {
            output.close();
         } catch (Exception e) {
         }
      }
   }
}