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
   
   public Stack<String> output;
   
   ChildDataHandler(InputStream inputStream, Stack<String> output) {
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
         output.add(line);
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
