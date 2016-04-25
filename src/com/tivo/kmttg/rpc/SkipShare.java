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
package com.tivo.kmttg.rpc;

import java.io.File;
import java.util.Hashtable;
import java.util.Stack;

import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.captions.ccdiff;
import com.tivo.kmttg.captions.srtSync;
import com.tivo.kmttg.install.Unzip;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.tivoFileName;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class SkipShare {
   
   public static void Import(
      String tivoName, JSONObject json, String shareSrt,
      String shareCut, String mySrt, Boolean debug) {
      try {
         srtSync csync = new srtSync(shareSrt, mySrt, false);
         if (csync.ccstack == null) return;
         Stack<Hashtable<String,Long>> points;
         Stack<Hashtable<String,Long>> points_adj = new Stack<Hashtable<String,Long>>();
         long duration = json.getLong("duration");
         if (shareCut.toLowerCase().endsWith(".vprj"))
            points = SkipImport.vrdImport(shareCut, duration);
         else
            points = SkipImport.edlImport(shareCut, duration);
         if (debug)
            printImportedCuts(points);
         if (points == null) return;
         
         int i=1;
         String line = "";
         for (Hashtable<String,Long> point : points) {
            long start = point.get("start");
            long end = point.get("end");
            long adjusted_start = 0, adjusted_end = 0;
            if (start > 0) {
               ccdiff cc = csync.findAfter(start);
               if (cc != null) {
                  if (debug) {
                     log.print("\ncc after start=" + com.tivo.kmttg.captions.util.toHourMinSec(start));
                     log.print(cc.toString());
                  }
                  adjusted_start = start + cc.startDiff();
               }
            }
            if (end > 0 && end < duration) {
               ccdiff cc = csync.findBefore(end);
               if (cc != null) {
                  if (debug) {
                     log.print("\ncc before end=" + com.tivo.kmttg.captions.util.toHourMinSec(end));
                     log.print(cc.toString());
                  }
                  adjusted_end = end + cc.stopDiff();
               }
            } else
               adjusted_end = end;
            if (adjusted_start == 0 && adjusted_end == 0)
               continue;
            if (adjusted_end - adjusted_start == duration)
               continue;
            if (i>1 && adjusted_start == 0)
               continue;
            else {
               Hashtable<String,Long> h = new Hashtable<String,Long>();
               h.put("start", adjusted_start);
               h.put("end", adjusted_end);
               points_adj.push(h);
               line = "" + i + ": adjusted start=" + com.tivo.kmttg.captions.util.toHourMinSec(adjusted_start) +
                     " adjusted end=" + com.tivo.kmttg.captions.util.toHourMinSec(adjusted_end);
               log.print(line);
               i++;
            }
         } // for
         
         String title = json.getString("title");
         if (json.has("subtitle"))
            title = title + " - " + json.getString("subtitle");
         String contentId = json.getString("contentId");
         if (SkipManager.hasEntry(contentId))
            SkipManager.removeEntry(contentId);
         SkipManager.saveEntry(contentId, json.getString("offerId"), 0L, title, tivoName, points_adj);
      } catch (Exception e) {
         log.error("SkipShare Import - " + e.getMessage());
      }
   }
   
   public static void tableImport(Hashtable<String,String> entry, String tivoName) {
      String name = tivoFileName.buildTivoFileName(entry);
      if (name != null) {
         try {
            String zip = "";
            String srt = "";
            String[] dirs = {config.outputDir, config.mpegDir};
            for (String dir : dirs) {
               String f = dir + File.separator + string.replaceSuffix(name, ".srt");
               if (file.isFile(f))
                  srt = f;
               f = dir + File.separator + string.replaceSuffix(name, ".zip");
               if (file.isFile(f))
                  zip = f;
            }
            new com.tivo.kmttg.gui.dialog.SkipShare(config.gui.getFrame(), tivoName, entry, zip, srt);
         } catch (Exception er) {
            log.error(er.getMessage());
         }
      }
   }
   
   public static Boolean ZipImport(
      String tivoName, JSONObject json, String zipFile,
      String srt_ref, Boolean debug) {
      try {
         File temp = new File(config.programDir + File.separator + "_SkipImport_");
         file.deleteDir(temp);
         if (temp.mkdir()) {
            String dir = temp.getAbsolutePath();
            if (Unzip.unzip(dir, zipFile)) {
               // Look for .srt and .vprj or .edl files
               String srt_zip = null;
               String cut_zip = null;
               File[] listOfFiles = temp.listFiles();
               for (File f : listOfFiles) {
                  if (f.isFile()) {
                     String name = f.getName().toLowerCase();
                     if (name.endsWith(".srt")) {
                        srt_zip = f.getAbsolutePath();
                     }
                     if (name.endsWith(".edl") || name.endsWith("vprj")) {
                        cut_zip = f.getAbsolutePath();
                     }
                  }
               }
               if (srt_zip != null && cut_zip != null) {
                  Import(tivoName, json, srt_zip, cut_zip, srt_ref, debug);
               }
            }
            file.deleteDir(temp);
         } else {
            log.error("Failed to make temp dir: " + temp.getAbsolutePath());
            return false;
         }
      } catch (Exception e) {
         log.error("SkipShare Import - " + e.getMessage());
         return false;
      }
      return true;
   }
   
   public static void printImportedCuts(Stack<Hashtable<String,Long>> points) {
      int i=1;
      log.print("---DEBUG: zip file cut points---");
      if (points == null) return;
      for (Hashtable<String,Long> point : points) {
         String line = "" + i + ": start=" + com.tivo.kmttg.captions.util.toHourMinSec(point.get("start"));
         line += " end=" + com.tivo.kmttg.captions.util.toHourMinSec(point.get("end"));
         if (point.get("start") > 0 || point.get("end") > 0) {
            log.print(line);
            i++;
         }
      }
   }
}
