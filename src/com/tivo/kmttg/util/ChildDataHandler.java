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
package com.tivo.kmttg.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Stack;


public class ChildDataHandler extends Thread {
   InputStream inputStream;
   InputStreamReader inputStreamReader;
   BufferedReader bufferedReader;
   int MaxEntries = 1000; // Don't save more than this number of entries
   
   public Stack<String> output;
   public Stack<String> watch = null;
   
   public ChildDataHandler(InputStream inputStream, Stack<String> output) {
     this.inputStream = inputStream;
     this.output = output;
     inputStreamReader = new InputStreamReader(inputStream);
     bufferedReader = new BufferedReader(inputStreamReader);
   }
   
   public void run() {
     debug.print("");
     try {
       String line=null;
       while((line = bufferedReader.readLine()) != null) {
         // Limit number of entries saved
         if (output.size() > MaxEntries)
            output.remove(0);
         output.add(line);
         if (watch != null)
            watch.add(line);
       }
     } catch(Exception e) {
        output.add(e.getMessage());
     }
   }
   
   public void close() {
      if (inputStream != null) {
         try {
            inputStream.close();
         } catch (IOException e) {
            return;
         }
      }
   }
}
