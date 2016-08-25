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
package com.tivo.kmttg.task;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.Serializable;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Stack;

import net.straylightlabs.tivolibre.TivoDecoder;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.traversal.DocumentTraversal;
import org.w3c.dom.traversal.NodeFilter;
import org.w3c.dom.traversal.TreeWalker;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.Entities;
import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.createMeta;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;

public class metadataTivo extends baseTask implements Serializable {
   private static final long serialVersionUID = 1L;
   private Thread thread = null;
   private Boolean thread_running = false;
   private Boolean success = false;
   private backgroundProcess process;
   public jobData job;
   private List<Document> docList = null;
   
   public metadataTivo(jobData job) {
      debug.print("job=" + job);
      this.job = job;      
   }
   
   public backgroundProcess getProcess() {
      return process;
   }
   
   public Boolean launchJob() {
      debug.print("");
      Boolean schedule = true;
      if ( file.isFile(job.metaFile) ) {
         if (config.OverwriteFiles == 0) {
            log.warn("SKIPPING METADATA GENERATION, FILE ALREADY EXISTS: " + job.metaFile);
            schedule = false;
         } else {
            log.warn("OVERWRITING EXISTING FILE: " + job.metaFile);
         }
      }

      if (schedule) {
         // Create sub-folders for output file if needed
         if ( ! jobMonitor.createSubFolders(job.metaFile, job) ) schedule = false;
      }
      
      if (schedule) {
         if ( start() ) {
            job.process = this;
            jobMonitor.updateJobStatus(job, "running");
            job.time                 = new Date().getTime();
         }
         return true;
      } else {
         return false;
      }      
   }
   
   public Boolean start() {
      debug.print("");
      log.print(">> CREATING " + job.metaFile + " ...");
      Runnable r = new Runnable() {
         public void run () {
            try {
               BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(job.tivoFile));
               TivoDecoder decoder = new TivoDecoder.Builder().input(inputStream).mak(config.MAK).build();
               if (decoder.decodeMetadata()) {
                  docList = decoder.getMetadata();
                  if (docList != null && docList.size() >= 2) {
                     success = true;
                  } else {
                     log.error("metadataTivo - unable to retrieve XML data");
                  }
               }
               inputStream.close();
               thread_running = false;
               decoder = null;
            } catch (Exception e) {
               log.error("metadataTivo - " + e.getMessage());
               success = false;
               thread_running = false;
               Thread.currentThread().interrupt();
            }
         }
      };
      thread_running = true;
      thread = new Thread(r);
      thread.start();

      return true;
   }
   
   public void kill() {
      debug.print("");
      thread.interrupt();
      log.warn("Killing '" + job.type + "' file: " + job.metaFile);
      thread_running = false;
   }
   
   // Check status of a currently running job
   // Returns true if still running, false if job finished
   // If job is finished then check result
   public Boolean check() {
      if (thread_running) {
         // Still running
         if (config.GUIMODE) {
            // Update STATUS column
            config.gui.jobTab_UpdateJobMonitorRowStatus(job, "running");
         }
         return true;
      } else {
         // Job finished
         jobMonitor.removeFromJobList(job);
         
         // Check for problems
         if (success && docList != null && docList.size() >= 2) {
            if (metaFileFromXmlDoc(docList.get(docList.size()-1), job.metaFile)) {
               log.warn("metadataTivo job completed: " + jobMonitor.getElapsedTime(job.time));
               log.print("---DONE--- job=" + job.type + " output=" + job.metaFile);
            } else {
               log.error("metaFileFromXmlFile failed to generate metadata file: " + job.metaFile);
            }
         } else {
            log.error("Failed to generate metadata file: " + job.metaFile);            
         }
      }
      
      return false;
   }
   
   // Create a pyTivo compatible metadata file from a .TiVo XML document
   @SuppressWarnings("unchecked")
   private Boolean metaFileFromXmlDoc(Document doc, String metaFile) {
      debug.print("Document=" + doc + " metaFile=" + metaFile);
      try {
         String[] nameValues = {
               "title", "seriesTitle", "description", "time",
               "movieYear", "isEpisode", "recordedDuration",
               "originalAirDate", "episodeTitle", "isEpisodic",
               "episodeNumber"
         };
         String[] valuesOnly = {"showingBits", "starRating", "tvRating", "mpaaRating"};
         String[] arrays = {
               "vActor", "vDirector", "vExecProducer", "vProducer",
               "vProgramGenre", "vSeriesGenre", "vAdvisory", "vHost",
               "vGuestStar", "vWriter", "vChoreographer"
         };                  
         
         Hashtable<String,Object> data = new Hashtable<String,Object>();

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
                     debug.print(name + "=" + value);
                     if (value.length() > 0)
                        values.add(value);
                  }
                  data.put(name, values);
               }
            }
            
            Node programNode = getNodeByName(doc, showingNode, "program");
            if (programNode != null) {
               // Look for programId under <showing><program><uniqueId>
               nlist = programNode.getChildNodes();
               if (nlist.getLength() > 0) {
                  for (int i=0; i<nlist.getLength(); ++i) {
                     if (nlist.item(i).getNodeName().equals("uniqueId"))
                        data.put("programId", nlist.item(i).getTextContent());
                  }
               }
               // Look for seriesId under <showing><program><series><uniqueId>
               Node seriesNode = getNodeByName(doc, programNode, "series");
               if (seriesNode != null) {
                  nlist = seriesNode.getChildNodes();
                  if (nlist.getLength() > 0) {
                     for (int i=0; i<nlist.getLength(); ++i) {
                        if (nlist.item(i).getNodeName().equals("uniqueId"))
                           data.put("seriesId", nlist.item(i).getTextContent());
                     }
                  }
               }
            }
         }
                                                
         // Post-process some of the data
         if ( data.containsKey("starRating") )
            data.put("starRating", "x" + data.get("starRating"));
         if ( data.containsKey("tvRating") )
            data.put("tvRating", "x" + data.get("tvRating"));
         // Not sure why I had isEpisodic override isEpisode, commenting out for now
         //if ( data.containsKey("isEpisodic") )
         //   data.put("isEpisode", data.get("isEpisodic"));
         if ( data.containsKey("description") ) {
            String desc = (String)data.get("description");
            desc = desc.replaceFirst("Copyright Tribune Media Services, Inc.", "");
            desc = desc.replaceFirst("Copyright Rovi, Inc.", "");
            data.put("description", desc);
         }
         
         if ( data.containsKey("mpaaRating") ) {
            Hashtable<String,String> map = new Hashtable<String,String>();
            map.put("1", "G1");
            map.put("2", "P2");
            map.put("3", "P3");
            map.put("4", "R4");
            map.put("5", "X5");
            map.put("6", "N6");
            map.put("7", "N8");
            String mpaaRating = map.get(data.get("mpaaRating"));
            if (mpaaRating != null)
               data.put("mpaaRating", mpaaRating);            
         }
                  
         // Now write all data to metaFile in pyTivo format
         BufferedWriter ofp = new BufferedWriter(new FileWriter(metaFile));
         
         String key;
         String eol = "\r\n";
         for (int i=0; i<nameValues.length; ++i) {
            key = nameValues[i];
            if (data.containsKey(key)) {
               if (key.equals("recordedDuration"))
                  ofp.write("iso_duration : " + data.get(key) + eol);
               else
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
         String[] additional = {"programId", "seriesId", "displayMajorNumber", "callsign"};
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
         String extra[] = createMeta.getExtraMetadata();
         if (extra != null) {
            for (int i=0; i<extra.length; ++i) {
               ofp.write(extra[i] + eol);
            }
         }
         ofp.close();
         
      }
      catch (Exception ex) {
         log.error(ex.toString());
         return false;
      }
      
      return true;
   }
   
   private Node getNodeByName(Document doc, Node n, String name) {
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
      
   public void printData(Hashtable<String,Object> data) {
      debug.print("data=" + data);
      String name;
      Object value;
      log.print("metadataTivo data:");
      for (Enumeration<String> e=data.keys(); e.hasMoreElements();) {
         name = e.nextElement();
         value = data.get(name);
         log.print(name + "=" + value);
      }
   }

}
