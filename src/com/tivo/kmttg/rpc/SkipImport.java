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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Stack;

import javafx.stage.FileChooser;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.tivoFileName;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class SkipImport {
   
   static public Boolean importEntry(String tivoName, Hashtable<String,String> entry) {
      if (! entry.containsKey("contentId")) {
         log.error("entry missing contentId: " + entry.get("title"));
         return false;
      }
      if (! entry.containsKey("offerId")) {
         log.error("entry missing offerId: " + entry.get("title"));
         return false;
      }
      if (! entry.containsKey("duration")) {
         log.error("entry missing duration: " + entry.get("title"));
         return false;
      }

      // Look for VPrj or edl file conforming to file naming template
      String name = tivoFileName.buildTivoFileName(entry);
      if (name != null) {
         String[] dirs = {config.outputDir, config.mpegDir};
         String[] exts = {".VPrj", ".edl"};
         Stack<Hashtable<String,Long>> cuts = null;
         String usedFile = null;
         for (String dir : dirs) {
            for (String ext : exts) {
               String cutFile = dir + File.separator + string.replaceSuffix(name, ext);
               if (usedFile == null && file.isFile(cutFile)) {
                  usedFile = cutFile;
               }
            }
         }
         
         if (usedFile == null) {
            log.warn("No file found automatically to import. Prompting for file.");
            // No file found automatically - so prompt user for one
            FileChooser FileBrowser = new FileChooser();
            FileBrowser.setInitialDirectory(new File(config.outputDir));
            FileBrowser.setTitle("Choose File");
            File selectedFile = FileBrowser.showOpenDialog(config.gui.getFrame());
            if (selectedFile == null)
               return false;
            else
               usedFile = selectedFile.getPath();
         }
         
         if (usedFile != null) {
            log.warn("Importing from file: " + usedFile);
            if (usedFile.toLowerCase().endsWith(".vprj"))
               cuts = vrdImport(usedFile, Long.parseLong(entry.get("duration")));
            if (usedFile.toLowerCase().endsWith(".edl"))
               cuts = edlImport(usedFile, Long.parseLong(entry.get("duration")));
         }
         
         if (cuts != null) {
            // If contentId entry already in table then remove it
            if (SkipManager.hasEntry(entry.get("contentId")))
               SkipManager.removeEntry(entry.get("contentId"));
            
            // Save entry to AutoSkip table with offset=0
            SkipManager.saveEntry(entry.get("contentId"), entry.get("offerId"), 0L, entry.get("title"), tivoName, cuts);
            return true;
         }
      }
      return false;
   }
   
   // Create skip entries based on VideoRedo .Vprj xml file with cut entries
   static public Stack<Hashtable<String,Long>> vrdImport(String vprjFile, Long duration) {
      Stack<Hashtable<String,Long>> cuts = new Stack<Hashtable<String,Long>>();
      try {
         DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
         DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
         // Read vprjFile into xml string to lowercase it
         String xml = new String(Files.readAllBytes(Paths.get(vprjFile)));
         Document doc = docBuilder.parse(new InputSource(new StringReader(xml.toLowerCase())));
         NodeList nList = doc.getElementsByTagName("cutlist");
         if (nList.getLength() > 0) {
            Node cutListNode = nList.item(0);
            NodeList cutList = cutListNode.getChildNodes();
            for (int i=0; i<cutListNode.getChildNodes().getLength(); ++i) {
               NodeList cut = cutList.item(i).getChildNodes();
               Hashtable<String,Long> h = new Hashtable<String,Long>();
               for (int j=0; j<cut.getLength(); ++j) {
                  Node attribute = cut.item(j);
                  if (attribute.getNodeName().equals("cuttimestart"))
                     h.put("start", Long.parseLong(attribute.getTextContent())/10000);
                  if (attribute.getNodeName().equals("cuttimeend"))
                     h.put("end", Long.parseLong(attribute.getTextContent())/10000);
               }
               if (h.containsKey("start") && h.containsKey("end")) {
                  if (h.get("start") != 0 || h.get("end") != 0)
                     cuts.push(h);
               }
            }
         }
      } catch (Exception e) {
         log.error("SkipImport vrdImport - " + e.getMessage());
         log.error(Arrays.toString(e.getStackTrace()));
      }
      return cutsToEntries(cuts, duration);
   }
   
   static public Stack<Hashtable<String,Long>> edlImport(String edlFile, Long duration) {
      Stack<Hashtable<String,Long>> cuts = new Stack<Hashtable<String,Long>>();
      try {
         BufferedReader ifp = new BufferedReader(new FileReader(edlFile));
         String line = null;
         while (( line = ifp.readLine()) != null) {
            if (line.matches("^\\d+.+$")) {
               Hashtable<String,Long> h = new Hashtable<String,Long>();
               String[] l = line.split("\\s+");
               float start = Float.parseFloat(l[0])*1000;
               float end = Float.parseFloat(l[1])*1000;
               if (start != 0 || end != 0) {
                  h.put("start", (long)start);
                  h.put("end", (long)end);
                  cuts.push(h);
               }
            }
         }
         ifp.close();
      } catch (Exception e) {
         log.error("SkipImport edlImport - " + e.getMessage());
         log.error(Arrays.toString(e.getStackTrace()));         
      }
      return cutsToEntries(cuts, duration);
   }
   
   // Convert a set of cut points to a set of show points
   static private Stack<Hashtable<String,Long>> cutsToEntries(Stack<Hashtable<String,Long>> cuts, Long duration) {
      Stack<Hashtable<String,Long>> entries = new Stack<Hashtable<String,Long>>();
      if (cuts != null && cuts.size() > 0) {
         for (int i=0; i<cuts.size(); ++i) {
            Hashtable<String,Long> h = new Hashtable<String,Long>();
            if (i==0)
               h.put("start", 0L);
            else
               h.put("start", cuts.get(i-1).get("end"));
            h.put("end", cuts.get(i).get("start"));
            if (h.get("start") != 0 || h.get("end") != 0)
               entries.push(h);
         }
         // Last entry
         if (cuts.size() > 0) {
            long last_end = cuts.get(cuts.size()-1).get("end");
            if (duration > last_end) {
               Hashtable<String,Long> h = new Hashtable<String,Long>();
               h.put("start", last_end);
               h.put("end", duration);
               entries.push(h);
            }
         }
      }
      return entries;
   }
   
   // Decide on video file name to use for export to Vprj or edl
   static private String videoToUseForExport(Hashtable<String,String> nplEntry) {
      String baseName = tivoFileName.buildTivoFileName(nplEntry);
      String mpegName = string.replaceSuffix(baseName, ".mpg");
      String tsName = string.replaceSuffix(baseName, ".ts");
      String tivoFile = config.outputDir + File.separator + baseName;
      String mpegFile = config.mpegDir + File.separator + mpegName;
      String tsFile = config.mpegDir + File.separator + tsName;
      String videoFile = tivoFile;
      if (file.isFile(mpegFile))
         videoFile = mpegFile;
      if (file.isFile(tsFile))
         videoFile = tsFile;
      return videoFile;
   }
   
   static private String buildVprjFile(Hashtable<String,String> nplEntry) {
      String baseName = tivoFileName.buildTivoFileName(nplEntry);
      String mpegName = string.replaceSuffix(baseName, ".mpg");
      String mpegFile = config.mpegDir + File.separator + mpegName;
      return string.replaceSuffix(mpegFile, ".VPrj");
   }
   
   static private String buildEdlFile(Hashtable<String,String> nplEntry) {
      String baseName = tivoFileName.buildTivoFileName(nplEntry);
      String mpegName = string.replaceSuffix(baseName, ".mpg");
      String mpegFile = config.mpegDir + File.separator + mpegName;
      return string.replaceSuffix(mpegFile, ".edl");
   }
   
   static public String vrdExport(Hashtable<String,String> nplEntry) {
      if (! nplEntry.containsKey("duration") || ! nplEntry.containsKey("contentId")) {
         log.error("Entry is missing duration and/or contentId");
         return null;
      }
      String vprjFile = null;
      Stack<Hashtable<String,Long>> entries = SkipManager.getEntry(nplEntry.get("contentId"));
      long duration = Long.parseLong(nplEntry.get("duration"));
      if (entries.size() > 0) {
         String videoFile = videoToUseForExport(nplEntry);
         vprjFile = buildVprjFile(nplEntry);
         if (! file.createDirIfNeeded(vprjFile)) {
            log.error("Can't create directory for: " + vprjFile);
            return null;
         }
         log.warn("AutoSkip exporting cut points to VRD VPrj file: " + vprjFile);
         log.warn("(video file used: " + videoFile + ")");
         try {
            BufferedWriter ofp = new BufferedWriter(new FileWriter(vprjFile, false));
            ofp.write("<VideoReDoProject Version=\"3\">\r\n");
            ofp.write("<Filename>" + videoFile + "</Filename>\r\n");
            ofp.write("<CutList>\r\n");
            Stack<Hashtable<String,Long>> cuts = entriesToCuts(entries, duration);
            int pad_start = config.autoskip_padding_start;
            int pad_stop = config.autoskip_padding_stop;
            for (Hashtable<String,Long> cut : cuts) {
               ofp.write("<Cut>");
               long start = (cut.get("start")-pad_start)*10000;
               if (start < 0) start = 0;
               ofp.write(" <CutTimeStart>" + start + "</CutTimeStart> ");
               long end = (cut.get("end")-pad_stop)*10000;
               if (end < 0) end = 0;
               ofp.write("<CutTimeEnd>" + end + "</CutTimeEnd> ");
               ofp.write("</Cut>\r\n");
            }
            ofp.write("</CutList>\r\n");
            ofp.write("</VideoReDoProject>\r\n");
            ofp.close();
         }
         catch (Exception ex) {
            log.error("Failed to write to file: " + vprjFile);
            log.error(ex.toString());
            vprjFile = null;
         }
      } else {
         log.error("No AutoSkip data available for this entry");
      }
      return vprjFile;
   }
   
   static public String edlExport(Hashtable<String,String> nplEntry) {
      if (! nplEntry.containsKey("duration") || ! nplEntry.containsKey("contentId")) {
         log.error("Entry is missing duration and/or contentId");
         return null;
      }
      String edlFile = null;
      Stack<Hashtable<String,Long>> entries = SkipManager.getEntry(nplEntry.get("contentId"));
      long duration = Long.parseLong(nplEntry.get("duration"));
      if (entries.size() > 0) {
         edlFile = buildEdlFile(nplEntry);
         if (! file.createDirIfNeeded(edlFile)) {
            log.error("Can't create directory for: " + edlFile);
            return null;
         }
         log.warn("AutoSkip exporting cut points to comskip edl file: " + edlFile);
         try {
            BufferedWriter ofp = new BufferedWriter(new FileWriter(edlFile, false));
            Stack<Hashtable<String,Long>> cuts = entriesToCuts(entries, duration);
            int pad_start = config.autoskip_padding_start;
            int pad_stop = config.autoskip_padding_stop;
            for (Hashtable<String,Long> cut : cuts) {
               float start = (float) ((cut.get("start")-pad_start)/1000.0);
               if (start < 0) start = 0;
               float end = (float) ((cut.get("end")-pad_stop)/1000.0);
               if (end < 0) end = 0;
               ofp.write("" + start + "  " + end + "  0\r\n");
            }
            ofp.close();
         }
         catch (Exception ex) {
            log.error("Failed to write to file: " + edlFile);
            log.error(ex.toString());
            edlFile = null;
         }
      } else {
         log.error("No AutoSkip data available for this entry");
      }
      return edlFile;
   }
   
   static private Stack<Hashtable<String,Long>> entriesToCuts(Stack<Hashtable<String,Long>> entries, long duration) {
      Stack<Hashtable<String,Long>> cuts = new Stack<Hashtable<String,Long>>();
      if (entries != null && entries.size() > 0) {
         for (int i=0; i<entries.size()-1; ++i) {
            long start = entries.get(i).get("start");
            long end = entries.get(i).get("end");
            if (i==0) {
               if (start != 0) {
                  Hashtable<String,Long> h = new Hashtable<String,Long>();
                  h.put("start", 0L); h.put("end", start);
                  cuts.push(h);
               }
            }
            Hashtable<String,Long> h = new Hashtable<String,Long>();
            h.put("start", end);
            start = entries.get(i+1).get("start");
            h.put("end", start);
            cuts.push(h);
         }
         long end = entries.get(entries.size()-1).get("end");
         if (end < duration) {
            Hashtable<String,Long> h = new Hashtable<String,Long>();
            h.put("start", end);
            h.put("end", duration);
            cuts.push(h);            
         }
      }
      return cuts;
   }
}
