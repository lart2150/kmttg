package com.tivo.kmttg.rpc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Stack;

import javafx.stage.FileChooser;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
            if (usedFile.endsWith(".VPrj"))
               cuts = vrdImport(usedFile, Long.parseLong(entry.get("duration")), false);
            if (usedFile.endsWith(".edl"))
               cuts = edlImport(usedFile, Long.parseLong(entry.get("duration")), false);
         }
         
         if (cuts != null) {
            // If contentId entry already in table then remove it
            if (AutoSkip.hasEntry(entry.get("contentId")))
               AutoSkip.removeEntry(entry.get("contentId"));
            
            // Save entry to AutoSkip table with offset=0
            AutoSkip.saveEntry(entry.get("contentId"), entry.get("offerId"), 0L, entry.get("title"), tivoName, cuts);
            return true;
         }
      }
      return false;
   }
   
   // Create skip entries based on VideoRedo .Vprj xml file with cut entries
   static public Stack<Hashtable<String,Long>> vrdImport(String vprjFile, Long duration, Boolean ignoreFirst) {
      Stack<Hashtable<String,Long>> cuts = new Stack<Hashtable<String,Long>>();
      try {
         DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
         DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
         Document doc = docBuilder.parse(vprjFile);
         NodeList nList = doc.getElementsByTagName("CutList");
         if (nList.getLength() > 0) {
            Node cutListNode = nList.item(0);
            NodeList cutList = cutListNode.getChildNodes();
            for (int i=0; i<cutListNode.getChildNodes().getLength(); ++i) {
               NodeList cut = cutList.item(i).getChildNodes();
               Hashtable<String,Long> h = new Hashtable<String,Long>();
               for (int j=0; j<cut.getLength(); ++j) {
                  Node attribute = cut.item(j);
                  if (attribute.getNodeName().equals("CutTimeStart"))
                     h.put("start", Long.parseLong(attribute.getTextContent())/10000);
                  if (attribute.getNodeName().equals("CutTimeEnd"))
                     h.put("end", Long.parseLong(attribute.getTextContent())/10000);
               }
               if (h.containsKey("start") && h.containsKey("end"))
                  cuts.push(h);
            }
         }
      } catch (Exception e) {
         log.error("SkipImport vrdImport - " + e.getMessage());
         log.error(Arrays.toString(e.getStackTrace()));
      }
      return cutsToEntries(cuts, duration, ignoreFirst);
   }
   
   static public Stack<Hashtable<String,Long>> edlImport(String edlFile, Long duration, Boolean ignoreFirst) {
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
               h.put("start", (long)start);
               h.put("end", (long)end);
               cuts.push(h);
            }
         }
         ifp.close();
      } catch (Exception e) {
         log.error("SkipImport edlImport - " + e.getMessage());
         log.error(Arrays.toString(e.getStackTrace()));         
      }
      return cutsToEntries(cuts, duration, ignoreFirst);
   }
   
   // Convert a set of cut points to a set of show points
   static private Stack<Hashtable<String,Long>> cutsToEntries(Stack<Hashtable<String,Long>> cuts, Long duration, Boolean ignoreFirst) {
      Stack<Hashtable<String,Long>> entries = new Stack<Hashtable<String,Long>>();
      if (cuts != null && cuts.size() > 0) {
         for (int i=0; i<cuts.size()-1; ++i) {
            Hashtable<String,Long> h = new Hashtable<String,Long>();
            if (i==0)
               h.put("start", 0L);
            else {
               if (ignoreFirst)
                  h.put("start", cuts.get(i).get("end"));
               else
                  h.put("start", cuts.get(i-1).get("end"));
            }
            if (ignoreFirst)
               h.put("end", cuts.get(i+1).get("start"));
            else
               h.put("end", cuts.get(i).get("start"));
            entries.push(h);
         }
         if (! ignoreFirst && cuts.size() > 1) {
            Hashtable<String,Long> h = new Hashtable<String,Long>();
            h.put("start", cuts.get(cuts.size()-2).get("end"));
            h.put("end", cuts.get(cuts.size()-1).get("start"));
            entries.push(h);
         }
         if (cuts.size() > 0) {
            Hashtable<String,Long> h = new Hashtable<String,Long>();
            h.put("start", cuts.get(cuts.size()-1).get("end"));
            h.put("end", duration);
            entries.push(h);
         }
      }
      return entries;
   }
}
