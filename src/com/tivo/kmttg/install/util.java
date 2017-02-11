package com.tivo.kmttg.install;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tivo.kmttg.util.log;

public class util {
   public static String getRedirect(String filename) {
      // Determine if localFileName contains a redirect
      try {
         BufferedReader ifp = new BufferedReader(new FileReader(filename));
         String first = ifp.readLine();
         if ( first.toLowerCase().contains("html") ) {
            String line;
            while( (line = ifp.readLine()) != null ) {
               if (line.toLowerCase().contains("resource was found")) {
                  if (line.toLowerCase().contains("href")) {
                     Pattern p = Pattern.compile("href=\"(.+)\"");
                     Matcher m = p.matcher(line);
                     if (m.find()) {
                        line = m.group(1);
                        line = line.replaceFirst("https", "http");
                        ifp.close();
                        return line;
                     }
                  }
               }
            }
         }
         ifp.close();
      }
      catch (IOException ex) {
         log.error("getRedirect - " + ex.getMessage());
      }
      return null;
   }
}
