package com.tivo.kmttg.rpc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;

// The MindRPC SchemaVersion a connection asks at. A TiVo silently leaves out fields newer than
// the version asked for, so a fixed version hides whatever newer software offers; bodyConfig's
// maxMindVersion says how high a box will go.
final class SchemaVersions {
   // The lowest version bodyConfig carries maxMindVersion at, as measured on a Bolt. Any
   // higher and a box whose maximum sits between would refuse the probe outright.
   static final int PROBE = 19;
   private static final Pattern HEADER = Pattern.compile("SchemaVersion: (\\d+)");
   // What each TiVo was found to take, so the probe is one round trip per TiVo per run.
   private static final Map<String,Integer> negotiated = new ConcurrentHashMap<String,Integer>();

   private SchemaVersions() {}

   static Integer cached(String key) {
      return negotiated.get(key);
   }

   static void remember(String key, int schema) {
      negotiated.put(key, schema);
   }

   static void forget(String key) {
      negotiated.remove(key);
   }

   // bodyConfig[0].maxMindVersion from a bodyConfigSearch reply, or 0 when it isn't there.
   static int maxMindVersion(String body) {
      try {
         JSONArray configs = new JSONObject(body).getJSONArray("bodyConfig");
         JSONObject config = configs.getJSONObject(0);
         return config.has("maxMindVersion") ? config.getInt("maxMindVersion") : 0;
      } catch (Exception e) {
         return 0;
      }
   }

   // The error a TiVo gives a SchemaVersion it will not take.
   static boolean isUnsupported(String body) {
      try {
         return isUnsupported(new JSONObject(body));
      } catch (Exception e) {
         return false;
      }
   }

   static boolean isUnsupported(JSONObject j) {
      try {
         return j.has("type") && j.getString("type").equals("error")
            && j.has("text") && j.getString("text").equals("Unsupported schema version");
      } catch (Exception e) {
         return false;
      }
   }

   // The SchemaVersion a framed request went out at, or 0 if it names none.
   static int of(String request) {
      Matcher m = HEADER.matcher(request.split("\r\n\r\n", 2)[0]);
      return m.find() ? Integer.parseInt(m.group(1)) : 0;
   }

   // The same framed request at another SchemaVersion. The header block changes length with
   // the number, so the start line's header length is worked out again.
   static String withSchema(String request, int schema) {
      int eol = request.indexOf("\r\n");
      String[] start = request.substring(0, eol).split(" ");
      int headLen = Integer.parseInt(start[1]);
      String headers = request.substring(eol + 2, eol + 2 + headLen);
      String rest = request.substring(eol + 2 + headLen);
      String rewritten = HEADER.matcher(headers).replaceFirst("SchemaVersion: " + schema);
      return start[0] + " " + rewritten.length() + " " + start[2] + "\r\n" + rewritten + rest;
   }
}
