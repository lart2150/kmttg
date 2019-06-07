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
package com.tivo.kmttg.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Stack;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.traversal.DocumentTraversal;
import org.w3c.dom.traversal.NodeFilter;
import org.w3c.dom.traversal.TreeWalker;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.http;
import com.tivo.kmttg.main.jobData;

public class createMeta {
   private static HashMap<String,String> tvRatings = null;
   private static HashMap<String,String> humanTvRatings = null;
   private static HashMap<String,String> mpaaRatings = null;
   private static HashMap<String,String> humanMpaaRatings = null;
   private static HashMap<String,String> starRatings = null;
   
   // Create a pyTivo compatible metadata file from a TiVoVideoDetails xml download
   @SuppressWarnings("unchecked")
   public static Boolean createMetaFile(jobData job, String cookieFile) {
      debug.print("");
      String outputFile = job.metaTmpFile;
      try {
         String[] nameValues = {
               "title", "seriesTitle", "description", "time",
               "mpaaRating", "movieYear", "isEpisode", "recordedDuration",
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
         // Convert file to String so we can parse out pad input entities such as &
         String inputStr = new String(Files.readAllBytes(Paths.get(outputFile)), "UTF-8");
         // Get rid of bad xml contents that TiVo sometimes generates: replace &&amp; with &amp;
         inputStr = inputStr.replaceAll("&&amp;", "&amp;");
         // & in xml needs to be &amp, TiVo has bug where it doesn't always have that
         inputStr = inputStr.replaceAll("\\s+&\\s+", " &amp; ");
         Document doc = docBuilder.parse(new ByteArrayInputStream(inputStr.getBytes("UTF-8")));

         // Search for <recordedDuration> elements
         NodeList rdList = doc.getElementsByTagName("recordedDuration");
         if (rdList.getLength() > 0) {
            String value;
            Node n = rdList.item(0);
            if ( n != null) {
               value = n.getTextContent();
               value = Entities.replaceHtmlEntities(value);
               data.put("recordedDuration", value);
               debug.print("recordedDuration" + "=" + value);
            }
         }
         
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
                     if (value.length() > 0) {
                        values.add(value);
                        debug.print(name + "=" + value);
                     }
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
         if ( data.containsKey("description") ) {
            String desc = (String)data.get("description");
            desc = desc.replaceFirst("Copyright Tribune Media Services, Inc.", "");
            desc = desc.replaceFirst("Copyright Rovi, Inc.", "");
            data.put("description", desc);
         }
         
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
         if ( job.ProgramId != null  && ! job.ProgramId.contains("_")) // Don't include bogus programId values
            data.put("programId", job.ProgramId);
         
         // Now write all data to metaFile in pyTivo format
         BufferedWriter ofp = new BufferedWriter(new FileWriter(job.metaFile));
         
         String key;
         String eol = "\r\n";
         for (int i=0; i<nameValues.length; ++i) {
            key = nameValues[i];
            if (data.containsKey(key)) {
               if (data.get(key).toString().length() > 0) {
                  if (key.equals("recordedDuration"))
                     ofp.write("iso_duration : " + data.get(key) + eol);
                  else
                     ofp.write(key + " : " + data.get(key) + eol);
               }
            }
         }
         for (int i=0; i<valuesOnly.length; ++i) {
            key = valuesOnly[i];
            if (data.containsKey(key)) {
               if (data.get(key).toString().length() > 0)
                  ofp.write(key + " : " + data.get(key) + eol);
            }
         }
         String[] additional = {"episodeNumber", "displayMajorNumber", "callsign", "seriesId", "programId"};
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
         
         // Extra name : value data specified in kmttg config
         String extra[] = getExtraMetadata();
         if (extra != null) {
            for (int i=0; i<extra.length; ++i) {
               ofp.write(extra[i] + eol);
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
   
   /**
    * Read a .txt metadata file into a Hashtable of names where the values are 
    * String values or Stacks of String values for e.g. 2 or more vActor fields.
    * Also does cleaning to more closely match metadata standard.
    * @param metaFile
    * @return empty Hashtable if there were any errors.
    */
   public static Hashtable<String, Object> readMetaFile(String metaFile) {
	   Hashtable<String, Object> result = new Hashtable<String, Object>();
	   try {
		   BufferedReader reader = new BufferedReader(new FileReader(new File(metaFile)));
		   try {
			   for(String line = reader.readLine(); line != null ; line = reader.readLine()) {
				   String pair[] = line.split(" : ", 2);
				   if(pair.length == 2) {
					   String name = pair[0];
					   String value = pair[1];
					   // "vName" type entry names are often vectors of multiple entries.
					   if(result.containsKey(name)) {
						   if(result.get(name) instanceof String) {
							   String firstValue = (String) result.get(name);
							   Stack<String> stack = new Stack<String>();
							   stack.add(firstValue);
							   result.put(name, stack);
						   }
						   if(result.get(name) instanceof Stack<?>) {
							   @SuppressWarnings("unchecked")
							   Stack<String> stack = (Stack<String>)result.get(name);
							   stack.add(value);
						   }
					   } else {
						   result.put(name, value);
					   }
				   }
			   }
		   } finally {
			   reader.close();
		   }
		   // Clean some of the data to the pyTivo metadata standard
		   String key;
		   key = "tvRating";
		   if(result.containsKey(key) && result.get(key) instanceof String) {
			   result.put(key, createMeta.tvRating2contentRating((String) result.get(key)));
		   }

		   key = "mpaaRating";
		   if(result.containsKey(key) && result.get(key) instanceof String) {
			   result.put(key, createMeta.mpaaRating2contentRating((String) result.get(key)));
		   }
		   
		   key = "starRating";
		   if(result.containsKey(key) && result.get(key) instanceof String) {
			   result.put(key, createMeta.starRating2numericStars((String) result.get(key)));
		   }
		   
	   } catch (Exception e){
	   }
	   return result;
   }
   /*
per pytivo wiki on Metadata:
1 Keywords
1.1 time : yyyy-mm-ddThh:mm:ssZ (GMT/Zulu time)
1.2 originalAirDate : yyyy-mm-ddThh:mm:ssZ (GMT/Zulu time)
1.3 movieYear
1.4 seriesTitle
1.5 title
1.6 episodeTitle
1.7 description
1.8 isEpisode
1.9 seriesId : SH is for general TV shows. MV is for movies (e.g. Amazon Unbox). SP is for sports. TS is for Tivocast content. SeriesId can be located at zap2it.com for a show. These values will all start with EP and must be changed to SH before using.
1.10 episodeNumber
1.11 displayMajorNumber
1.12 callsign
1.13 showingBits : 1 = CC 2 = Stereo 4 = Sub (subtitled) 8 = In Prog 16 = Class (classroom) 32 = SAP 64 = Blackout 128 = Intercast 256 = Three D 512 = R (repeat) 1024 = Letterbox 4096 = HD (High Definition) 65536 = S (sex rating) 131072 = V (violence rating) 262144 = L (language rating) 524288 = D (dialog rating) 1048576 = FV (fantasy violence rating)
1.14 displayMinorNumber : (wiki doesn't know what this is, I think it's part of the channel number "3-2" major is 3, minor is 2)
1.15 tvRating : with or without the "TV-" prefix. Y7 Y G PG 14 MA NR
1.16 starRating : 1 1.5 2 2.5 3 3.5 4
1.17 mpaaRating : G PG PG-13 R X NC-17 NR
1.18 colorCode : x1 = B & W x2 = Color and B & W x3 = Colorized x4 = Color Series 
1.19 vProgramGenre
1.20 vSeriesGenre
1.21 vActor : lastname|firstname
1.22 vGuestStar, vDirector, vExecProducer, vProducer, vWriter, vHost, vChoreographer
1.23 partCount
1.24 partIndex
    */
      
   public static Node getNodeByName(Document doc, Node n, String name) {
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
   
   public static String[] getExtraMetadata() {
      if (config.metadata_entries != null && config.metadata_entries.length() > 0) {
         String entries = string.removeLeadingTrailingSpaces(config.metadata_entries);
         String tokens[] = entries.split(",");
         String data[] = new String[tokens.length];
         for (int i=0; i<tokens.length; ++i) {
            String nv[] = tokens[i].split(":");
            if (nv.length == 2) {
               String name = string.removeLeadingTrailingSpaces(nv[0]);
               String value = string.removeLeadingTrailingSpaces(nv[1]);
               data[i] = name + " : " + value;
            } else {
               log.error("Invalid setting for 'extra metadata entries': " + config.metadata_entries);
               return null;
            }
         }
         return data;
      }
      return null;
   }
   
   private static void initHashes() {
      if (tvRatings == null) {
         tvRatings = new HashMap<String,String>();
         tvRatings.put("TV-Y7", "1");
         tvRatings.put("TVY7",  "1");
         tvRatings.put("Y7",    "1");
         tvRatings.put("X1",    "1");

         tvRatings.put("TV-Y",  "2");
         tvRatings.put("TVY",   "2");
         tvRatings.put("Y",     "2");
         tvRatings.put("X2",    "2");

         tvRatings.put("TV-G",  "3");
         tvRatings.put("TVG",   "3");
         tvRatings.put("G",     "3");
         tvRatings.put("X3",    "3");

         tvRatings.put("TV-PG", "4");
         tvRatings.put("TVPG",  "4");
         tvRatings.put("PG",    "4");
         tvRatings.put("X4",    "4");

         tvRatings.put("TV-14", "5");
         tvRatings.put("TV14",  "5");
         tvRatings.put("14",    "5");
         tvRatings.put("X5",    "5");

         tvRatings.put("TV-MA", "6");
         tvRatings.put("TVMA",  "6");
         tvRatings.put("MA",    "6");
         tvRatings.put("X6",    "6");

         tvRatings.put("TV-NR", "7");
         tvRatings.put("TVNR",  "7");
         tvRatings.put("NR",    "7");
         tvRatings.put("X7",    "7");
         tvRatings.put("X0",    "7");
      }
      if (humanTvRatings == null) {
         humanTvRatings = new HashMap<String,String>();
         humanTvRatings.put("1", "TV-Y7");
         humanTvRatings.put("2", "TV-Y");
         humanTvRatings.put("3", "TV-G");
         humanTvRatings.put("4", "TV-PG");
         humanTvRatings.put("5", "TV-14");
         humanTvRatings.put("6", "TV-MA");
         humanTvRatings.put("7", "Unrated");
      }
      if (mpaaRatings == null) {
         mpaaRatings = new HashMap<String,String>();
         mpaaRatings.put("G",       "1");
         mpaaRatings.put("G1",      "1");

         mpaaRatings.put("PG",      "2");
         mpaaRatings.put("P2",      "2");

         mpaaRatings.put("PG-13",   "3");
         mpaaRatings.put("PG13",    "3");
         mpaaRatings.put("P3",      "3");

         mpaaRatings.put("R",       "4");
         mpaaRatings.put("R4",      "4");

         mpaaRatings.put("X",       "5");
         mpaaRatings.put("X5",      "5");

         mpaaRatings.put("NC-17",   "6");
         mpaaRatings.put("NC17",    "6");
         mpaaRatings.put("N6",      "6");

         mpaaRatings.put("NR",      "8");
         mpaaRatings.put("UNRATED", "8");
         mpaaRatings.put("N8",      "8");
         mpaaRatings.put("8",       "8");
      }
      if (humanMpaaRatings == null) {
         humanMpaaRatings = new HashMap<String,String>();
         humanMpaaRatings.put("1", "G");
         humanMpaaRatings.put("2", "PG");
         humanMpaaRatings.put("3", "PG-13");
         humanMpaaRatings.put("4", "R");
         humanMpaaRatings.put("5", "X");
         humanMpaaRatings.put("6", "NC-17");
         humanMpaaRatings.put("8", "Unrated");
      }
      if (starRatings == null) {
    	  starRatings = new HashMap<String,String>();
    	  starRatings.put("POINTFIVE",      "0.5");
    	  starRatings.put("ZEROPOINTFIVE",  "0.5");
    	  starRatings.put("ONE",            "1");
    	  starRatings.put("ONEPOINTFIVE",   "1.5");
    	  starRatings.put("TWO",            "2");
    	  starRatings.put("TWOPOINTFIVE",   "2.5");
    	  starRatings.put("THREE",          "3");
    	  starRatings.put("THREEPOINTFIVE", "3.5");
    	  starRatings.put("FOUR",           "4");
    	  
    	  starRatings.put("XPOINTFIVE",      "0.5");
    	  starRatings.put("XZEROPOINTFIVE",  "0.5");
    	  starRatings.put("XONE",            "1");
    	  starRatings.put("XONEPOINTFIVE",   "1.5");
    	  starRatings.put("XTWO",            "2");
    	  starRatings.put("XTWOPOINTFIVE",   "2.5");
    	  starRatings.put("XTHREE",          "3");
    	  starRatings.put("XTHREEPOINTFIVE", "3.5");
    	  starRatings.put("XFOUR",           "4");
    	  
    	  // assumed mapping based on x6 for 4... but I've seen x7 on a recording (that should also be 4 stars).
    	  // what would be 0.5 stars? that's usually possible.
    	  starRatings.put("X0",       "1");
    	  starRatings.put("X1",       "1.5");
    	  starRatings.put("X2",       "2");
    	  starRatings.put("X3",       "2.5");
    	  starRatings.put("X4",       "3");
    	  starRatings.put("X5",       "3.5");
    	  starRatings.put("X6",       "4");
    	  // X7 = 4.5? X8 = 5.0?
      }
   }
   
   // This used for mapping to AtomicParsley --contentRating argument
   public static String tvRating2contentRating(String tvRating) {
      initHashes();
      String upperRating = tvRating.toUpperCase();
      String intermediate = null;
      if (tvRatings.containsKey(upperRating))
         intermediate = tvRatings.get(upperRating);
      if (intermediate != null && humanTvRatings.containsKey(intermediate))
         return humanTvRatings.get(intermediate);
      return tvRating;
   }
   
   // This used for mapping to AtomicParsley --contentRating argument   
   public static String mpaaRating2contentRating(String mpaaRating) {
      initHashes();
      String upperRating = mpaaRating.toUpperCase();
      String intermediate = null;
      if (mpaaRatings.containsKey(upperRating))
         intermediate = mpaaRatings.get(upperRating);
      if (intermediate != null && humanMpaaRatings.containsKey(intermediate))
         return humanMpaaRatings.get(intermediate);
      return mpaaRating;
   }
   
   /** map various text ratings to a numeric 1, 1.5, etc. rating */
   public static String starRating2numericStars(String starRating) {
      initHashes();
      String upperRating = starRating.toUpperCase();
      if (starRatings.containsKey(upperRating))
         return starRatings.get(upperRating);
      return starRating;
   }
   
   // Get extended metatadata and store in given Hashtable
   public static void getExtendedMetadata(String tivoName, Hashtable<String,String> data, Boolean verbose) {
      if (! data.containsKey("metadata") && data.containsKey("url_TiVoVideoDetails")) {
         if (verbose)
            log.warn("Obtaining extended metadata for: " + data.get("title"));
         // Obtain and add metadata
         ByteArrayOutputStream info = new ByteArrayOutputStream();
         try {
            String url = data.get("url_TiVoVideoDetails");
            String wan_port = config.getWanSetting(tivoName, "https");
            if (wan_port != null)
               url = string.addPort(url, wan_port);
            Boolean result = http.downloadPiped(url, "tivo", config.MAK, info, false, null);
            if(result) {
               // Read data from info
               byte[] b = info.toByteArray();
               metadataFromXML(b, data);
               data.put("metadata", "acquired");
               if (verbose)
                  log.warn("extended metadata acquired");
            }
         } catch (Exception e1) {
            log.error("extended metadata error: " + e1.getMessage());
         }
      }
   }
   
   // Extract data of interest from extended metadata and add to Hashtable
   private static void metadataFromXML(byte[] b, Hashtable<String,String> h) {
      Document doc = Xml.getDocument(new ByteArrayInputStream(b));
      if (doc != null) {
         // startTime is under main branch
         NodeList nlist = doc.getElementsByTagName("startTime");
         if (nlist.getLength() > 0) {
            String startTime = nlist.item(0).getTextContent();            
            h.put("startTime", "" + printableDateFromExtendedTime(startTime));
         }
         // Search for everything else under <showing>
         nlist = doc.getElementsByTagName("showing");
         if (nlist.getLength() > 0) {
            Node showingNode = nlist.item(0);
            Node n = createMeta.getNodeByName(doc, showingNode, "originalAirDate");
            if ( n != null) {
               String oad = n.getTextContent();
               // Strip off time portion. Example: 2012-11-08T00:00:00Z
               oad = oad.replaceFirst("T.+$", "");
               h.put("originalAirDate", oad);
            }
         }
      }
   }

   public static String DocToString(Document doc) {
      try {
      Transformer transformer = TransformerFactory.newInstance().newTransformer();
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      StreamResult result = new StreamResult(new StringWriter());
      DOMSource source = new DOMSource(doc);
      transformer.transform(source, result);
      return result.getWriter().toString();
      } catch (Exception e) {
         log.error("DocToString - " + e.getMessage());
         return null;
      }
   }
   
   // Convert given time from TiVo Extended XML to long
   // Sample date: 2013-02-28T18:15:23Z
   private static long getLongDateFromExtendedTime(String date) {
      try {
         SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z' zzz");
         Date d = format.parse(date + " GMT");
         return d.getTime();
      } catch (ParseException e) {
         log.error("getLongDateFromExtendedTime - " + e.getMessage());
         return 0;
      }
   }

   /**
    * same as {@link #getLongDateFromExtendedTime(String)} but doesn't assume GMT timezone.
    * @param date
    * @return
    */
   private static long getLongDateFromExtendedLocalTime(String date) {
      try {
         SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
         Date d = format.parse(date);
         return d.getTime();
      } catch (ParseException e) {
         log.error("getLongDateFromExtendedTime - " + e.getMessage());
         return 0;
      }
   }
   
   // Return date format such as 2013-02-28_1815
   public static String printableDateFromExtendedTime(String date) {
      long start = getLongDateFromExtendedTime(date);
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HHmm");
      return sdf.format(start);
   }
   
   /** 
    * use {@link #getLongDateFromExtendedLocalTime(String)} 
    * and format the result as 2013-02-28 18:15:00 
    */
   public static String jsonDateFromExtendedLocalTime(String date) {
      long start = getLongDateFromExtendedLocalTime(date);
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      return sdf.format(start);
   }
   
   /** get the seconds from the PT29M57S type format */
   public static long jsonDurationFromIsoDuration(String duration) {
	   try {
		   java.time.Duration d = java.time.Duration.parse(duration);
		   return d.getSeconds();
	   } catch(Exception e) {
		   return -1;
	   }
   }
}
