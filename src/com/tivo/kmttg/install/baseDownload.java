package com.tivo.kmttg.install;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

import com.tivo.kmttg.util.debug;

public class baseDownload {
   public static String getBase() {
      debug.print("");
      String base = null;
      String base_url = "http://svn.code.sf.net/p/kmttg/code/trunk/baseDownload";
      try {
         URL url = new URL(base_url);
         URLConnection con = url.openConnection();
         BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
         String inputLine;
         while ((inputLine = in.readLine()) != null) 
            base = inputLine;
         in.close();
      } catch (Exception ex) {
         base = null;
      }
      return  base;
   }
}
