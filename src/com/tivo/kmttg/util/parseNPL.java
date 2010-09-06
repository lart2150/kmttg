package com.tivo.kmttg.util;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Hashtable;
import java.util.Stack;

import com.tivo.kmttg.main.config;

public class parseNPL {
   
   public static Hashtable<String,Integer> parseFile(String file, String tivoName, Stack<Hashtable<String,String>>ENTRIES) {
      debug.print("file=" + file);
      int TotalItems=0, ItemCount=0, offset=0;
      String ll, l, value;
      Hashtable<String,String> h = new Hashtable<String,String>();
      try {
         BufferedReader xml = new BufferedReader(
            new InputStreamReader(new FileInputStream(file),"UTF8")
         );
         ll = xml.readLine();
         xml.close();
         String[] line = ll.split(">");
         // First get TotalItems & ItemCount
         int go = 1;
         while (go == 1) {
            if (offset < line.length) {
               l = line[offset++];
               if (l.matches("^<TotalItems.*$")) {
                  value = line[offset].replaceFirst("^(.+)<\\/.+$", "$1");
                  TotalItems = Integer.parseInt(value);
                  debug.print("TotalItems=" + TotalItems);
               }
               if (l.matches("^<ItemCount.*$")) {
                  value = line[offset].replaceFirst("^(.+)<\\/.+$", "$1");
                  ItemCount = Integer.parseInt(value);
                  go = 0;
                  debug.print("ItemCount=" + ItemCount);
               }
            } else {
               go = 0;
            }
         }
         
         // Now parse all items tagged with <Item>
         for (int j=offset; j<line.length; ++j) {            
            l = line[j];
            if (l.matches("^<Item.*$")) {
               // Start of a new program item
               if ( ! h.isEmpty() ) {
                  if (h.containsKey("episodeTitle")) {
                     h.put("title", h.get("title") + " - " + h.get("episodeTitle"));
                  }
                  h.put("tivoName", tivoName);                  
                  // If ProgramId doesn't exist then grab id from url instead
                  // Also add ProgramId_unique
                  checkProgramId(h);                  
                  ENTRIES.add(h);
               }
               h = new Hashtable<String,String>();
            }

            // Title
            if (l.matches("^<Title.*$")) {
               j++;
               value = line[j].replaceFirst("^(.+)<\\/.+$", "$1");
               value = Entities.replaceHtmlEntities(value);
               h.put("title", value);
               h.put("titleOnly", value);
            }

            // Copy Protected
            if (l.matches("^<CopyProtected.*$")) {
               j++;
               value = line[j].replaceFirst("^(.+)<\\/.+$", "$1");
               h.put("CopyProtected", value);
            }

            // Size
            if (l.matches("^<SourceSize.*$")) {
               j++;
               value = line[j].replaceFirst("^(.+)<\\/.+$", "$1");
               h.put("size", "" + Long.parseLong(value));
               double GB = Math.pow(2,30);
               h.put("sizeGB", String.format("%.2f GB", Float.parseFloat(value)/GB));
            }

            // Duration
            if (l.matches("^<Duration.*$")) {
               j++;
               value = line[j].replaceFirst("^(.+)<\\/.+$", "$1");
               h.put("duration", value);
            }

            // CaptureDate
            if (l.matches("^<CaptureDate.*$")) {
               j++;
               value = line[j].replaceFirst("^(.+)<\\/.+$", "$1");
               h.put("gmt_hex", value);
               String hex = value;
               hex = hex.replaceFirst("^0x(.+)$", "$1");
               int dec = Integer.parseInt(hex,16);
               long gmt = (long)dec*1000;
               h.put("gmt", "" + gmt);
               h.put("date", getTime(gmt));
               h.put("date_long", getDetailedTime(gmt));
            }
            
            // EpisodeTitle
            if (l.matches("^<EpisodeTitle.*$")) {
               j++;
               value = line[j].replaceFirst("^(.+)<\\/.+$", "$1");
               value = Entities.replaceHtmlEntities(value);
               h.put("episodeTitle", value);
            }            

            // EpisodeNumber (store as 3 digits)
            if (l.matches("^<EpisodeNumber.*$")) {
               j++;
               value = line[j].replaceFirst("^(.+)<\\/.+$", "$1");
               value = Entities.replaceHtmlEntities(value);
               h.put("EpisodeNumber", String.format("%03d", Integer.parseInt(value)));
            }

            // Description
            if (l.matches("^<Description.*$")) {
               j++;
               value = line[j].replaceFirst("^(.+)<\\/.+$", "$1");
               value = Entities.replaceHtmlEntities(value);
               value = value.replaceFirst("Copyright Tribune Media Services, Inc.", "");
               h.put("description", value);
            }

            // Channel #
            if (l.matches("^<SourceChannel.*$")) {
               j++;
               value = line[j].replaceFirst("^(.+)<\\/.+$", "$1");
               int len = value.length();
               String channelNum=value, sortableChannel=value;
               if (value.matches("^.+-.+$")) {
                  // OTA style channel #
                  String majorString = value.replaceFirst("(.+)-.+$", "$1");
                  len = majorString.length();
                  int major = Integer.parseInt(majorString);
                  int minor = Integer.parseInt(value.replaceFirst(".+-(.+)$", "$1"));
                  sortableChannel = String.format("%d.%03d", major, minor);
               }
               if (config.MinChanDigits > len) {
                  // Pad with 0s up to MinChanDigits length
                  for (int k=0; k<config.MinChanDigits-len; ++k)
                     channelNum = "0" + channelNum;
               }
               h.put("channelNum", channelNum);
               h.put("sortableChannel", sortableChannel);
            }

            // Channel Name
            if (l.matches("^<SourceStation.*$")) {
               j++;
               value = line[j].replaceFirst("^(.+)<\\/.+$", "$1");
               h.put("channel", value);
            }

            // Recording in progress
            if (l.matches("^<InProgress.*$")) {
               j++;
               value = line[j].replaceFirst("^(.+)<\\/.+$", "$1");
               h.put("InProgress", value);
            }

            // HD tag
            if (l.matches("^<HighDefinition.*$")) {
               j++;
               value = line[j].replaceFirst("^(.+)<\\/.+$", "$1");
               h.put("HD", value);
            }

            // ProgramId
            if (l.matches("^<ProgramId.*$")) {
               j++;
               value = line[j].replaceFirst("^(.+)<\\/.+$", "$1");
               h.put("ProgramId", value);
            }

            // SeriesId
            if (l.matches("^<SeriesId.*$")) {
               j++;
               value = line[j].replaceFirst("^(.+)<\\/.+$", "$1");
               h.put("SeriesId", value);
            }

            // ByteOffset
            if (l.matches("^<ByteOffset.*$")) {
               j++;
               value = line[j].replaceFirst("^(.+)<\\/.+$", "$1");
               h.put("ByteOffset", value);
            }

            // URL
            if (l.matches("^<Url.*$")) {
               j++;
               value = line[j];
               // Download URL
               if (value.matches("^.*download.*$")) {
                  value = value.replaceFirst("^(.+)<\\/.+$", "$1");
                  value = value.replaceFirst("&amp;", "&");
                  if (config.wan_http_port.length() > 0) {
                     value = value.replaceFirst(":80", ":" + config.wan_http_port);
                  }
                  h.put("url", value);
               }
               
               // TivoVideoDetails URL
               if (value.matches("^.*TiVoVideoDetails.*$")) {
                  value = value.replaceFirst("^(.+)<\\/.+$", "$1");
                  value = value.replaceFirst("&amp;", "&");
                  h.put("url_TiVoVideoDetails", value);
               }
               
               // Expiration Image Type
               if (value.matches("^.*save-until-i-delete-recording.*$")) {
                  h.put("ExpirationImage", "save-until-i-delete-recording");
                  h.put("kuid", "yes");
               }
               if (value.matches("^.*in-progress-recording.*$")) {
                  h.put("ExpirationImage", "in-progress-recording");
               }
               if (value.matches("^.*in-progress-transfer.*$")) {
                  h.put("ExpirationImage", "in-progress-transfer");
               }
               if (value.matches("^.*expires-soon-recording.*$")) {
                  h.put("ExpirationImage", "expires-soon-recording");
               }
               if (value.matches("^.*expired-recording.*$")) {
                  h.put("ExpirationImage", "expired-recording");
               }
               if (value.matches("^.*suggestion-recording.*$")) {
                  h.put("ExpirationImage", "suggestion-recording");
                  h.put("suggestion", "yes");
               }
            }
            
            // Set copy-protect icon if copy-protected
            if (h.containsKey("CopyProtected")) {
               // Give preference to show transferring status over copy protected
               Boolean flag = true;
               if (h.containsKey("ExpirationImage")) {
                  if (h.get("ExpirationImage").equals("in-progress-recording"))
                     flag = false;
                  if (h.get("ExpirationImage").equals("in-progress-transfer"))
                     flag = false;                  
               }                  
               if (flag)
                  h.put("ExpirationImage", "copy-protected");
            }
         }
         // Add last entry
         if ( ! h.isEmpty() ) {
            if (h.containsKey("episodeTitle")) {
               h.put("title", h.get("title") + " - " + h.get("episodeTitle"));
            }
            h.put("tivoName", tivoName);
            // If programId doesn't exist then grab id from url instead
            // Also add ProgramId_unique
            checkProgramId(h);
            ENTRIES.add(h);
         }
      }
      catch (IOException ex) {
         log.error(ex.toString());
         return null;
      }
      Hashtable<String,Integer> r = new Hashtable<String,Integer>();
      r.put("ItemCount", ItemCount);
      r.put("TotalItems", TotalItems);
      return r;
   }   

   // If ProgramId doesn't exist then make a fake one out of url id & size
   // Also add ProgramId_unique
   private static void checkProgramId(Hashtable<String,String> h) {      
      if (! h.containsKey("ProgramId")) {
         if (h.containsKey("url") && h.containsKey("size")) {
            String id = h.get("url");
            id = id.replaceFirst("^.+id=(.+)$", "$1");
            if (id.length() > 0) {
               h.put("ProgramId", id + "_" + h.get("size"));
            }
         }
      }
      
      // Add ProgramId_unique
      h.put("ProgramId_unique", h.get("ProgramId") + "_" + h.get("gmt"));
   }
   
   private static String getTime(long gmt) {
      debug.print("gmt=" + gmt);
      SimpleDateFormat sdf = new SimpleDateFormat("E MM/dd/yyyy");
      return sdf.format(gmt);
   }
   
   private static String getDetailedTime(long gmt) {
      debug.print("gmt=" + gmt);
      SimpleDateFormat sdf = new SimpleDateFormat("E MM/dd/yyyy hh:mm aa");
      return sdf.format(gmt);
   }

}
