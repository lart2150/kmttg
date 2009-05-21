package com.tivo.kmttg.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Stack;


public class ChildDataHandler extends Thread {
   InputStream inputStream;
   public Stack<String> output;
   
   ChildDataHandler(InputStream inputStream, Stack<String> output) {
     this.inputStream = inputStream;
     this.output = output;
   }
   
   public void run() {
     debug.print("");
     try {
       InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
       BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
       String line=null;
       while((line = bufferedReader.readLine()) != null) {
         output.add(line);
       }
     } catch(Exception e) {
        output.add(e.getMessage());
     }
   }
}
