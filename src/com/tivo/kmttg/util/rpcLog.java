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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;

/**
 * Optional JSONL log of RPC requests/responses with timing, enabled via the
 * -rpcLog command line flag (analogous to -d / debug). Each call to
 * {@link #log} appends one self-contained JSON object on its own line:
 *
 *   {"ts":<epochMs>,"tivo":"<name>","type":"<cmd>","ms":<roundTrip>,
 *    "request":{...},"response":{...}}
 *
 * Intended for diagnosing slow RPC operations (e.g. Now Playing List fetches).
 * The TSN (bodyId) and MAK are sanitized to dummy values before each line is
 * written, so the log is safe to share.
 */
public class rpcLog {
   public static volatile boolean enabled = false;
   private static String file = null;
   private static BufferedWriter ofp = null;

   private static synchronized void init() {
      if (ofp != null)
         return;
      String dir = new File(
         rpcLog.class.getProtectionDomain().getCodeSource().getLocation().getPath()
      ).getParent();
      dir = urlDecode(dir);
      file = dir + File.separator + "rpc.jsonl";
      try {
         ofp = new BufferedWriter(new FileWriter(file));
         System.out.println("rpcLog: writing RPC request/response log to " + file);
      } catch (IOException ex) {
         System.out.println("rpcLog: problem opening log file: " + file + " - " + ex.getMessage());
      }
   }

   private static String urlDecode(String s) {
      try {
         return URLDecoder.decode(s, "UTF-8");
      } catch (UnsupportedEncodingException e) {
         return s;
      }
   }

   /**
    * Append one request/response entry. durationMs is the measured RPC
    * round-trip time. Safe to call from multiple threads. No-op unless enabled.
    */
   public static synchronized void log(String tivoName, String type, long durationMs,
         JSONObject request, JSONObject response) {
      if (! enabled)
         return;
      if (ofp == null)
         init();
      if (ofp == null)
         return;
      try {
         JSONObject entry = new JSONObject();
         entry.put("ts", System.currentTimeMillis());
         if (tivoName != null)
            entry.put("tivo", tivoName);
         entry.put("type", type);
         entry.put("ms", durationMs);
         if (request != null)
            entry.put("request", request);
         // Absence of "response" indicates a null/error response.
         if (response != null)
            entry.put("response", response);
         ofp.write(scrub(entry.toString()));
         ofp.write("\n");
         ofp.flush();
      } catch (Exception e) {
         System.out.println("rpcLog: failed to write entry - " + e.getMessage());
      }
   }

   // Replace sensitive values with same-shape dummies: any tsn:<digits>
   // (the bodyId) becomes tsn:0000..., and the MAK is zeroed out.
   // Package-private for unit testing.
   static String scrub(String s) {
      s = s.replaceAll("tsn:[0-9]+", "tsn:0000000000000000");
      String mak = config.MAK;
      if (mak != null && mak.length() > 0) {
         char[] zeros = new char[mak.length()];
         java.util.Arrays.fill(zeros, '0');
         s = s.replace(mak, new String(zeros));
      }
      return s;
   }

   public static synchronized void close() {
      if (ofp != null) {
         try {
            ofp.close();
         } catch (IOException e) {
            // ignore
         }
         ofp = null;
      }
   }
}
