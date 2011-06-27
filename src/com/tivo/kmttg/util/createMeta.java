package com.tivo.kmttg.util;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Stack;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.traversal.DocumentTraversal;
import org.w3c.dom.traversal.NodeFilter;
import org.w3c.dom.traversal.TreeWalker;

import com.tivo.kmttg.main.jobData;

public class createMeta {
   
   // Create a pyTivo compatible metadata file from a TiVoVideoDetails xml download
   @SuppressWarnings("unchecked")
   public static Boolean createMetaFile(jobData job, String cookieFile) {
      debug.print("");
      String outputFile = job.metaTmpFile;
      try {
         String[] nameValues = {
               "title", "seriesTitle", "description", "time",
               "mpaaRating", "movieYear", "isEpisode",
               "originalAirDate", "episodeTitle", "isEpisodic"
         };
         String[] valuesOnly = {"showingBits", "starRating", "tvRating"};
         String[] arrays = {
               "vActor", "vDirector", "vExecProducer", "vProducer",
               "vProgramGenre", "vSeriesGenre", "vAdvisory", "vHost",
               "vGuestStar", "vWriter", "vChoreographer"
         };
         
         Hashtable<String,Object> data = new Hashtable<String,Object>();
         DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
         DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
         Document doc = docBuilder.parse(outputFile);
         
         // Search for everything under <showing>
         NodeList nlist = doc.getElementsByTagName("showing");
         if (nlist.getLength() > 0) {
            Node showingNode = nlist.item(0);
            String name, value;
            
            // First process nameValues
            for (int k=0; k<nameValues.length; k++) {
               name = nameValues[k];
               Node n = getNodeByName(doc, showingNode, name);
               if ( n != null) {
                  value = n.getTextContent();
                  value = Entities.replaceHtmlEntities(value);
                  data.put(name, value);
                  debug.print(name + "=" + value);
               }
            }
            
            // Process valuesOnly which have a "value" node
            for (int k=0; k<valuesOnly.length; k++) {
               name = valuesOnly[k];
               Node n = getNodeByName(doc, showingNode, name);
               if ( n != null) {
                  value = n.getAttributes().getNamedItem("value").getNodeValue();
                  data.put(name, value);
                  debug.print(name + "=" + value);
               }
            }
            
            // Process arrays which have 1 or more values
            for (int k=0; k<arrays.length; k++) {
               name = arrays[k];
               Node n = getNodeByName(doc, showingNode, name);
               if ( n != null) {
                  Stack<String> values = new Stack<String>();
                  NodeList children = n.getChildNodes();
                  for (int c=0; c<children.getLength(); c++) {
                     value = children.item(c).getTextContent();
                     values.add(value);
                     debug.print(name + "=" + value);
                  }
                  data.put(name, values);
               }
            }
         }
                  
         // Post-process some of the data
         if ( data.containsKey("starRating") )
            data.put("starRating", "x" + data.get("starRating"));
         if ( data.containsKey("tvRating") )
            data.put("tvRating", "x" + data.get("tvRating"));
         // Override isEpisode since it seems to be wrong most of the time
         if ( data.containsKey("isEpisodic") )
            data.put("isEpisode", data.get("isEpisodic"));
         if ( data.containsKey("description") )
            data.put("description", ((String) (data.get("description"))).replaceFirst("Copyright Tribune Media Services, Inc.", ""));
         
         if ( data.containsKey("mpaaRating") ) {
            Hashtable<String,String> map = new Hashtable<String,String>();
            map.put("G", "G1");
            map.put("PG", "P2");
            map.put("PG_13", "P3");
            map.put("R", "R4");
            map.put("X", "X5");
            map.put("NC_17", "N6");
            map.put("NR", "N8");
            String mpaaRating = map.get(data.get("mpaaRating"));
            if (mpaaRating != null)
               data.put("mpaaRating", mpaaRating);            
         }
         
         // Add additional data
         if ( job.episodeNumber != null && job.episodeNumber.length() > 0 )
            data.put("episodeNumber", job.episodeNumber);
         if ( job.displayMajorNumber != null ) {
            // Doesn't like sub-channel #s so strip them out
            // NOTE: New versions of pyTivo are fine with dashes now
            //data.put("displayMajorNumber", job.displayMajorNumber.replaceFirst("^(.+)-.+$", "$1"));
            data.put("displayMajorNumber", job.displayMajorNumber);
         }
         if ( job.callsign != null )
            data.put("callsign", job.callsign);
         if ( job.seriesId != null )
            data.put("seriesId", job.seriesId);
         
         // Now write all data to metaFile in pyTivo format
         BufferedWriter ofp = new BufferedWriter(new FileWriter(job.metaFile));
         
         String key;
         String eol = "\r\n";
         for (int i=0; i<nameValues.length; ++i) {
            key = nameValues[i];
            if (data.containsKey(key)) {
               if (data.get(key).toString().length() > 0)
                  ofp.write(key + " : " + data.get(key) + eol);
            }
         }
         for (int i=0; i<valuesOnly.length; ++i) {
            key = valuesOnly[i];
            if (data.containsKey(key)) {
               if (data.get(key).toString().length() > 0)
                  ofp.write(key + " : " + data.get(key) + eol);
            }
         }
         String[] additional = {"episodeNumber", "displayMajorNumber", "callsign", "seriesId"};
         for (int i=0; i<additional.length; ++i) {
            key = additional[i];
            if (data.containsKey(key)) {
               if (data.get(key).toString().length() > 0)
                  ofp.write(key + " : " + data.get(key) + eol);
            }
         }
         for (int i=0; i<arrays.length; i++) {
            key = arrays[i];
            if (data.containsKey(key)) {
               Stack<String> values = (Stack<String>)data.get(key);
               for (int j=0; j<values.size(); ++j) {
                  ofp.write(key + " : " + values.get(j) + eol);
               }
            }
         }
         ofp.close();
         
      }
      catch (Exception ex) {
         log.error(ex.toString());
         if (cookieFile != null) file.delete(cookieFile);
         file.delete(outputFile);
         return false;
      }
      
      if (cookieFile != null) file.delete(cookieFile);
      file.delete(outputFile);
      return true;
   }
      
   private static Node getNodeByName(Document doc, Node n, String name) {
      DocumentTraversal docTraversal = (DocumentTraversal)doc;
      TreeWalker iter = docTraversal.createTreeWalker(
         n,
         NodeFilter.SHOW_ALL,                                                 
         null,
         false
      );      
      Node node = null;
      while ( (node=iter.nextNode()) != null ) {
         if (node.getNodeName().equals(name))
            return node;
      }
      return node;
   }
   
   public static void printData(Hashtable<String,Object> data) {
      debug.print("");
      String name;
      Object value;
      log.print("metadata data:");
      for (Enumeration<String> e=data.keys(); e.hasMoreElements();) {
         name = e.nextElement();
         value = data.get(name);
         log.print(name + "=" + value);
      }
   }

}
