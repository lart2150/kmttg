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
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.Stack;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.table.TableUtil;
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

            // ShowingStartTime (assumes it's after CaptureDate and will override)
            // This was introduced by TiVo in 20.2.2 software
            if (l.matches("^<ShowingStartTime.*$")) {
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
               value = value.replaceFirst("Copyright Rovi, Inc.", "");
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
            // This intentionally commented out so that people wanting to see
            // expiration icons instead of copy protect can do so
            /*if (h.containsKey("CopyProtected")) {
               // Give preference to show transferring status over copy protected
               Boolean flag = true;
               if (h.containsKey("ExpirationImage")) {
                  if (h.get("ExpirationImage").equals("in-progress-recording"))
                     flag = false;
                  if (h.get("ExpirationImage").equals("in-progress-transfer"))
                     flag = false;                  
               } 
               // This intentionally commented out so that people wanting to see
               // expiration icons instead of copy protect can do so
               if (flag)
                  h.put("ExpirationImage", "copy-protected");
            }*/
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
      catch (Exception ex) {
         log.error("Error parsing NPL XML");
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
   
   public static Stack<Hashtable<String,String>> uniquify(Stack<Hashtable<String,String>> ENTRIES, Hashtable<String,Integer> unique) {
      Stack<Hashtable<String,String>> UNIQUE = new Stack<Hashtable<String,String>>();
      for (int i=0; i<ENTRIES.size(); ++i) {
         String ProgramId_unique = ENTRIES.get(i).get("ProgramId_unique");
         if (! unique.containsKey(ProgramId_unique)) {
            unique.put(ProgramId_unique, 1);
            UNIQUE.add(ENTRIES.get(i));
            if (ENTRIES.get(i).containsKey("duration")) {
               String duration = ENTRIES.get(i).get("duration");
               String key = ProgramId_unique + "_" + duration;
               unique.put(key, 1);
            }
         } else {
            // Already have this id, but do some further checking in case we need to include it in UNIQUE stack
            // Partial recordings have different durations and should not be uniquified
            if (ENTRIES.get(i).containsKey("duration")) {
               String duration = ENTRIES.get(i).get("duration");
               String key = ProgramId_unique + "_" + duration;
               if ( ! unique.containsKey(key) ) {
                  unique.put(key, 1);
                  UNIQUE.add(ENTRIES.get(i));
               }
            }
         }
      }
      ENTRIES = null;
      return UNIQUE;
   }
   
   public static Hashtable<String,String> rpcToHashEntry(String tivoName, JSONObject json) {
      Hashtable<String,String> entry = new Hashtable<String,String>();
      try {
         entry.put("tivoName", tivoName);
         if (json.has("title")) {
            entry.put("titleOnly", json.getString("title"));
            entry.put("title", json.getString("title"));
         }
         if (json.has("subtitle")) {
            entry.put("episodeTitle", json.getString("subtitle"));
            if (json.has("title"))
               entry.put("title", json.getString("title") + " - " + json.getString("subtitle"));
         }
         if (json.has("description")) {
            entry.put("description", json.getString("description"));
         }
         if (json.has("recordingId")) {
            entry.put("recordingId", json.getString("recordingId"));
         }
         if (json.has("collectionId")) {
            entry.put("collectionId", json.getString("collectionId"));
         }
         if (json.has("contentId")) {
            entry.put("contentId", json.getString("contentId"));
         }
         if (json.has("offerId")) {
            entry.put("offerId", json.getString("offerId"));
         }
         if (json.has("clipMetadata")) {
            JSONArray a = json.getJSONArray("clipMetadata");
            if (a.length() > 0) {
               entry.put("clipMetadataId", a.getJSONObject(0).getString("clipMetadataId"));
            }
         }
         if (json.has("hdtv")) {
            if (json.getBoolean("hdtv"))
               entry.put("HD", "Yes");
         }
         if (json.has("size")) {
            long sizeKB = json.getLong("size");
            long size = sizeKB*1024;
            entry.put("size", "" + size);
            double GB = Math.pow(2,30);
            entry.put("sizeGB", String.format("%.2f GB", size/GB));
         }
         if (json.has("originalAirdate")) {
            entry.put("originalAirDate", json.getString("originalAirdate"));
         }
         if (json.has("channel")) {
            JSONObject chan = json.getJSONObject("channel");
            if (chan.has("callSign"))
               entry.put("channel", chan.getString("callSign"));
            else
               if (chan.has("name"))
                  entry.put("channel", chan.getString("name"));
            if (chan.has("channelNumber"))
               entry.put("channelNum", chan.getString("channelNumber"));
         }
         if (json.has("episodeNum") && json.has("seasonNumber")) {
            entry.put("season", String.format("%02d", json.get("seasonNumber")));
            entry.put("episode", String.format("%02d", json.getJSONArray("episodeNum").get(0)));
            entry.put(
               "EpisodeNumber",
               "" + json.get("seasonNumber") +
               String.format("%02d", json.getJSONArray("episodeNum").get(0))
            );
         }
         if (json.has("startTime") && json.has("duration")) {
            long start = TableUtil.getStartTime(json);
            entry.put("gmt", "" + start);
            SimpleDateFormat sdf = new SimpleDateFormat("E MM/dd/yyyy");
            entry.put("date", sdf.format(start));
            sdf = new SimpleDateFormat("E MM/dd/yyyy hh:mm aa");
            entry.put("date_long", sdf.format(start));
            entry.put("duration", "" + json.getInt("duration")*1000);
         }
         if (json.has("partnerCollectionId")) {
            entry.put("ProgramId", json.getString("partnerCollectionId"));
            if (entry.containsKey("gmt"))
               entry.put("ProgramId_unique", entry.get("ProgramId") + "_" + entry.get("gmt"));
         }
         if (json.has("__SeriesId__")) {
            // This data not in TiVo database - added by Remote MyShows method
            entry.put("SeriesId", json.getString("__SeriesId__"));
         }
         if (json.has("drm")) {
            JSONObject drm = json.getJSONObject("drm");
            if (drm.has("tivoToGo")) {
               if (! drm.getBoolean("tivoToGo")) {
                  //entry.put("ExpirationImage", "copy-protected");
                  entry.put("CopyProtected", "Yes");
               }
            }
         }
         if (json.has("desiredDeletion")) {
            long desired = TableUtil.getLongDateFromString(json.getString("desiredDeletion"));
            long now = new Date().getTime();
            long diff = desired - now;
            double hours = diff/(1000*60*60);
            if (hours > 0 && hours < 24)
               entry.put("ExpirationImage", "expires-soon-recording");
            if (hours < 0)
               entry.put("ExpirationImage", "expired-recording");
         }
         if (json.has("deletionPolicy")) {
            String policy = json.getString("deletionPolicy");
            if(policy.equals("neverDelete")) {
               entry.put("ExpirationImage", "save-until-i-delete-recording");
               entry.put("kuid", "yes");                  
            }
         }
         if (json.has("bookmarkPosition")) {
            // NOTE: Don't have ByteOffset available, so use TimeOffset (secs) instead
            entry.put("TimeOffset", "" + json.getInt("bookmarkPosition")/1000);
            
            /* This intentionally removed in favor of getting ByteOffset from XML
            if (json.has("size") && json.has("duration")) {
               // Estimate ByteOffset based on TimeOffset
               // size (KB) * 1024 * bookmarkPosition/(duration(s)*1000)
               long size = (long)json.getLong("size")*1024;
               long duration = (long)json.getLong("duration")*1000;
               long bookmarkPosition = (long)json.getLong("bookmarkPosition");
               long ByteOffset = bookmarkPosition*size/duration;
               entry.put("ByteOffset", "" + ByteOffset);
            }*/
         } else {
            /* Comment this out since watchedTime can't be set after AutoSkip from SkipMode
            if (json.has("watchedTime")) {
               entry.put("TimeOffset", "" + json.getInt("watchedTime")*60);
               // If TimeOffset > duration then remove
               if (json.has("duration")) {
                  long duration = json.getLong("duration");
                  if(json.getInt("watchedTime")*60 > duration)
                     entry.remove("TimeOffset");
               }
            }*/
         }
         if (json.has("subscriptionIdentifier")) {
            JSONArray a = json.getJSONArray("subscriptionIdentifier");
            for (int j=0; j<a.length(); ++j) {
               JSONObject o = a.getJSONObject(j);
               if (o.has("subscriptionType")) {
                  if (o.getString("subscriptionType").startsWith("suggestion")) {
                     entry.put("ExpirationImage", "suggestion-recording");
                     entry.put("suggestion", "yes");                        
                  }
               }
            }
         }
         // In progress recordings trump any other kind of icon
         if (json.has("state")) {
            if (json.getString("state").equals("inProgress")) {
               entry.put("ExpirationImage", "in-progress-recording");
               entry.put("InProgress", "Yes");
               if (json.has("collectionTitle")) {
                  String c = json.getString("collectionTitle");
                  if (c.equals("pcBodySubscription"))
                     entry.put("ExpirationImage", "in-progress-transfer");
               }
            }
         }
         if (json.has("movieYear")) {
            entry.put("movieYear", "" + json.get("movieYear"));
         }
         if (json.has("__url__")) {
            entry.put("url", json.getString("__url__"));
            json.remove("__url__");
         }
         if (json.has("__url_TiVoVideoDetails__")) {
            entry.put("url_TiVoVideoDetails", json.getString("__url_TiVoVideoDetails__"));
            json.remove("__url_TiVoVideoDetails__");
         }
      } catch (Exception e) {
         log.error("rpcToHashEntry - " + e.getMessage());
         entry = null;
      }
      return entry;
   }

}
