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

import java.io.InputStream;
import java.io.OutputStream;

import com.tivo.kmttg.util.log;

public class Piper implements java.lang.Runnable {
   private InputStream input;
   private OutputStream output;

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
   
   // For 3 or more pipes. Example:
   // InputStream in = Piper.pipe(p1, p2, p3);
   public static InputStream pipe(java.lang.Process... proc) throws java.lang.InterruptedException {
      // Start Piper between all processes
      java.lang.Process p1;
      java.lang.Process p2;
      for (int i = 0; i < proc.length; i++) {
          p1 = proc[i];
          // If there's one more process
          if (i + 1 < proc.length) {
              p2 = proc[i + 1];
              // Start piper
              new Thread(new Piper(p1.getInputStream(), p2.getOutputStream())).start();
          }
      }
      java.lang.Process last = proc[proc.length - 1];
      return last.getInputStream();
  }
   
}