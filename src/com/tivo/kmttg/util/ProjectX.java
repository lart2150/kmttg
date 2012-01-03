package com.tivo.kmttg.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProjectX {
   // Obtain pct complete from projectx stdout
   public static int projectxGetPct(backgroundProcess process) {
      String last = process.getStdoutLast();
      if (last.matches("")) return -1;
      Pattern pat = Pattern.compile("(\\d+)\\s*%");
      Matcher match = pat.matcher(last);
      if (match.find()) {
         String pct_str = match.group(1);
         try {
            return (int)Float.parseFloat(pct_str);
         }
         catch (NumberFormatException n) {
            return -1;
         }
      }
      return -1;
   }
   
   // Parse projectx demux log file looking for all output files
   // ---> new File: ./sample.m2v
   // ---> new File: './sample.ac3'
   public static Stack<String> getOutputFiles(String logFile) {
      Stack<String> outputFiles = new Stack<String>();
      try {
         BufferedReader ini = new BufferedReader(new FileReader(logFile));
         String line = null;
         Pattern p = Pattern.compile("^.+new\\s+File:\\s+(.+)$");
         Matcher m;
         Boolean print = false;
         while (( line = ini.readLine()) != null) {
            // Get rid of leading and trailing white space
            line = line.replaceFirst("^\\s*(.*$)", "$1");
            line = line.replaceFirst("^(.*)\\s*$", "$1");
            if (line.length() == 0) continue; // skip empty lines
            if (line.startsWith("summary"))
               print = true;
            if (print)
               log.print(line);
            m = p.matcher(line);
            if (m.matches()) {
               String f = m.group(1);
               // Strip off quotes
               if (f.matches("^'.+'$") || f.matches("^\".+\"$"))
                  f = f.substring(1, f.length()-1);
               outputFiles.add(f);
            }
         }
         ini.close();
         if (outputFiles.size() == 0)
            return null;
      }         
      catch (IOException ex) {
         log.error("Problem parsing demux log file: " + logFile);
         return null;
      }

      return outputFiles;
   }

   // Clean up temp files created by projectx
   // These files are in format for example: long_timestamp_issue.$ppes$1-E3F464
   public static void cleanUpTempFiles(String fileName) {
      File dir = new File(string.dirname(fileName));
      File[] listOfFiles = dir.listFiles();
      String prefix = string.replaceSuffix(string.basename(fileName), "");
      Pattern p = Pattern.compile("^" + prefix + "\\.\\$.+$");
      Matcher m;
      if (listOfFiles != null) {
         for(int i=0; i<listOfFiles.length; ++i) {
            if (listOfFiles[i].isFile()) {
               m = p.matcher(listOfFiles[i].getName());
               if (m.matches()) {
                  if ( ! file.delete(listOfFiles[i].getAbsolutePath()) ) {
                     try {
                        // Sleep 1 second and try deleting again
                        Thread.sleep(1000);
                        file.delete(listOfFiles[i].getAbsolutePath());
                     } catch (InterruptedException e) {
                        log.error(e.getMessage());
                     }
                  }
               }
            }
         }
      }
   }
}
