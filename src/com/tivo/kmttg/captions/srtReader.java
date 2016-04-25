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
package com.tivo.kmttg.captions;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tivo.kmttg.util.log;

public class srtReader {
   public Stack<cc> ccstack;
   
   public srtReader(String srtFile) {
      if (! read(srtFile))
         ccstack = null;
   }
   
   private Boolean read(String srtFile) {
      ccstack = new Stack<cc>();
      String line = null;
      String text="";
      String timeFormat = "([0-9]*):([0-9][0-9]):([0-9][0-9]),([0-9][0-9][0-9])\\s+-->\\s+([0-9]*):([0-9][0-9]):([0-9][0-9]),([0-9][0-9][0-9])";
      Pattern p = Pattern.compile(timeFormat);
      Matcher m;
      long t1=0, t2=0;
      int HH, MM, SS, mmm;
      try {
         BufferedReader ifp = new BufferedReader(new FileReader(srtFile));
         while (( line = ifp.readLine()) != null) {
            // Get rid of leading and trailing white space
            line = line.replaceFirst("^\\s*", "");
            line = line.replaceFirst("\\s*$", "");
            if (line.length() == 0) continue; // skip empty lines
            if (line.matches("^[0-9]+$")) continue; // skip lines starting with integer
            if (line.matches("^[0-9]+:[0-9]+:[0-9].*$")) {
               // New time entry: HH:MM:SS,mmm --> HH:MM:SS,mmm
               if( t2 != 0) {
                  cc captions = new cc();
                  captions.start = t1;
                  captions.stop = t2;
                  captions.text = text;
                  ccstack.push(captions);
                  t1=0; t2=0;
                  text = "";
               }
               m = p.matcher(line);
               if (m.matches() && m.groupCount() == 8) {
                  HH = Integer.parseInt(m.group(1));
                  MM = Integer.parseInt(m.group(2));
                  SS = Integer.parseInt(m.group(3));
                  mmm = Integer.parseInt(m.group(4));
                  t1 = mmm + 1000*(SS+60*MM+3600*HH);
                  HH = Integer.parseInt(m.group(5));
                  MM = Integer.parseInt(m.group(6));
                  SS = Integer.parseInt(m.group(7));
                  mmm = Integer.parseInt(m.group(8));
                  t2 = mmm + 1000*(SS+60*MM+3600*HH);
               }
               continue;
            }
            text = text + line + " ";
         } // while
         ifp.close();
      } catch (Exception e) {
         log.error("srtReader - " + e.getMessage());
         return false;
      }
      return true;
   }
   
   // Print content of ccstack for debug
   public void print() {
      if (ccstack != null) {
         int i=1;
         for (cc caption : ccstack) {
            System.out.println(i);
            System.out.println(caption);
            i++;
         }
      }
   }

}
