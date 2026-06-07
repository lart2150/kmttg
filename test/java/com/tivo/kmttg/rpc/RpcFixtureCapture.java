package com.tivo.kmttg.rpc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;

/**
 * One-off developer tool (NOT a unit test) that connects to a live TiVo and
 * captures the safe read-only RPC responses used to generate the test
 * fixtures under src/test/resources/fixtures/.
 *
 * Sensitive values (the TiVo Service Number / bodyId and the MAK) are
 * sanitized to same-format dummy values before anything is written to disk,
 * so the captured fixtures never contain real credentials.
 *
 * Run via:  gradlew captureRpcFixtures -PconfigDir="C:\\path\\to\\kmttg" -Ptivo=Bolt
 */
public class RpcFixtureCapture {

   public static void main(String[] args) throws Exception {
      // Same security relaxations kmttg.main applies so the TiVo's weak cert
      // chain is accepted (otherwise the TLS handshake is rejected).
      System.setProperty("https.cipherSuites", "SSL_RSA_WITH_RC4_128_SHA");
      java.security.Security.setProperty("jdk.certpath.disabledAlgorithms", "");
      java.security.Security.setProperty("jdk.tls.disabledAlgorithms", "SSLv3");

      String configDir = args.length > 0 ? args[0] : ".";
      String tivoName  = args.length > 1 ? args[1] : "Bolt";
      // Keep fixtures small/representative rather than dumping the whole TiVo.
      int maxEntries   = args.length > 2 ? Integer.parseInt(args[2]) : 40;

      // Point kmttg at the given config dir (for the cdata cert fallback) and
      // read just the values we need from config.ini ourselves, so we don't
      // depend on config.parse() (which would reset programDir).
      config.programDir = configDir;
      String mak = readIniValue(configDir + File.separator + "config.ini", "MAK");
      String ip  = readTivoIp(configDir + File.separator + "config.ini", tivoName);
      if (mak == null || ip == null)
         throw new IllegalStateException("Could not read MAK / " + tivoName + " IP from config.ini");

      System.out.println("Connecting to " + tivoName + " at " + ip + " ...");
      RecordingRemote r = new RecordingRemote(tivoName, ip, -1, mak, null);
      if (!r.success)
         throw new IllegalStateException("RPC connection/auth failed");

      String tsn = r.bodyId_get(); // e.g. "tsn:8460001234567890"
      Sanitizer san = new Sanitizer(tsn, mak);

      File outDir = new File("test/resources/fixtures");
      outDir.mkdirs();

      // MyShows is huge (one Search per recording) - keep its response fixture
      // but don't record its command trace (it isn't replayed).
      r.setRecording(false);
      capture(san, outDir, "myshows.json", r.MyShows(null), maxEntries);
      r.setRecording(true);

      // For the replay-target methods, capture BOTH the response fixture (for
      // the existing parse tests) and the command trace (for the replay tests).
      // The command logs are trimmed to the first few pages: ReplayRemote
      // returns null past the recorded requests, which terminates the read loop.
      captureWithCommands(san, outDir, "todo",         r, () -> r.ToDo(null),          maxEntries, 8);
      captureWithCommands(san, outDir, "deleted",      r, () -> r.DeletedShows(null),  maxEntries, 3);
      captureWithCommands(san, outDir, "seasonpasses", r, () -> r.SeasonPasses(null),  maxEntries, 1);
      captureWithCommands(san, outDir, "cancelled",    r, () -> r.CancelledShows(null),maxEntries, 3);
      captureWithCommands(san, outDir, "thumbs",       r, () -> r.getThumbs(null),     maxEntries, 2);

      // Channel list: the high-level ChannelList() reaches into GUI state, so
      // issue the underlying RPC command directly here.
      JSONObject chReq = new JSONObject();
      chReq.put("noLimit", "true");
      chReq.put("bodyId", r.bodyId_get());
      JSONArray channels = arr(r.Command("channelSearch", chReq), "channel");
      capture(san, outDir, "channels.json", channels, maxEntries);

      // Keyword search ("bob"): searchKeywords() also needs GUI state, so issue
      // the underlying OfferSearch command directly.
      JSONObject sReq = new JSONObject();
      sReq.put("keyword", "bob");
      sReq.put("count", 50);
      sReq.put("offset", 0);
      capture(san, outDir, "search.json", arr(r.Command("OfferSearch", sReq), "offer"), maxEntries);

      // Guide listings (gridRowSearch) for specific channels on a specific day.
      // The high-level guide path reaches into GUI state, so issue the command
      // directly. Date is fixed so the fixtures are deterministic.
      String guideDate = "2026-06-07";
      // TiVo stores channel numbers with a dash (displayed as 2.1 / 5.1)
      String[] guideChannels = { "2-1", "5-1" };
      long dayStart = java.time.LocalDate.parse(guideDate)
            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
      long dayEnd = dayStart + 24L * 60 * 60 * 1000;
      for (String chanNum : guideChannels) {
         JSONObject channel = findChannel(channels, chanNum);
         if (channel == null) {
            System.out.println("  guide_" + chanNum + ".json: channel not found (skipped)");
            continue;
         }
         JSONObject gReq = new JSONObject();
         gReq.put("bodyId", r.bodyId_get());
         gReq.put("levelOfDetail", "medium");
         gReq.put("orderBy", new JSONArray("[\"channelNumber\"]"));
         gReq.put("minEndTime", rnpl.getStringFromLongDate(dayStart));
         gReq.put("maxStartTime", rnpl.getStringFromLongDate(dayEnd));
         JSONObject anchor = new JSONObject();
         anchor.put("channelNumber", channel.getString("channelNumber"));
         anchor.put("type", "channelIdentifier");
         anchor.put("sourceType", channel.getString("sourceType"));
         gReq.put("anchorChannelIdentifier", anchor);
         JSONObject gRes = r.Command("gridRowSearch", gReq);
         JSONArray offers = null;
         if (gRes != null && gRes.has("gridRow"))
            offers = gRes.getJSONArray("gridRow").getJSONObject(0).getJSONArray("offer");
         capture(san, outDir, "guide_" + chanNum + ".json", offers, maxEntries);
      }

      r.disconnect();
      System.out.println("Done.");
   }

   private static void capture(Sanitizer san, File dir, String name, JSONArray data, int max) throws Exception {
      if (data == null) {
         System.out.println("  " + name + ": NULL (skipped)");
         return;
      }
      // Trim to a representative sample to keep fixtures small
      JSONArray trimmed = data;
      if (data.length() > max) {
         trimmed = new JSONArray();
         for (int i = 0; i < max; i++) trimmed.put(data.get(i));
      }
      String json = san.scrub(trimmed.toString(2));
      try (FileWriter w = new FileWriter(new File(dir, name))) {
         w.write(json);
      }
      System.out.println("  " + name + ": " + trimmed.length() + " of " + data.length() + " entries written");
   }

   /**
    * Runs a high-level read call, writing both its response fixture
    * (&lt;name&gt;.json) and the RPC command trace it issued, trimmed to the first
    * maxCmds commands (commands_&lt;name&gt;.json), for the replay tests.
    */
   private static void captureWithCommands(Sanitizer san, File dir, String name, RecordingRemote r,
         java.util.function.Supplier<JSONArray> call, int maxEntries, int maxCmds) throws Exception {
      r.reset();
      JSONArray data = call.get();
      capture(san, dir, name + ".json", data, maxEntries);

      JSONArray fullLog = r.log();
      JSONArray log = fullLog;
      if (fullLog.length() > maxCmds) {
         log = new JSONArray();
         for (int i = 0; i < maxCmds; i++) log.put(fullLog.get(i));
      }
      String json = san.scrub(log.toString(2));
      try (FileWriter w = new FileWriter(new File(dir, "commands_" + name + ".json"))) {
         w.write(json);
      }
      System.out.println("  commands_" + name + ".json: " + log.length() + " of " + fullLog.length() + " commands written");
   }

   // Extract a named array from a Command response (null-safe).
   private static JSONArray arr(JSONObject result, String key) throws Exception {
      if (result != null && result.has(key))
         return result.getJSONArray(key);
      return null;
   }

   // Find a channel object by channelNumber in the channelSearch results.
   private static JSONObject findChannel(JSONArray channels, String channelNumber) throws Exception {
      if (channels == null) return null;
      for (int i = 0; i < channels.length(); i++) {
         JSONObject ch = channels.getJSONObject(i);
         if (ch.has("channelNumber") && ch.getString("channelNumber").equals(channelNumber)
               && ch.has("sourceType"))
            return ch;
      }
      return null;
   }

   // Minimal config.ini reader: returns the line following <key>.
   private static String readIniValue(String path, String key) throws Exception {
      try (BufferedReader in = new BufferedReader(new FileReader(path))) {
         String line;
         boolean found = false;
         while ((line = in.readLine()) != null) {
            if (found) return line.trim();
            if (line.trim().equals("<" + key + ">")) found = true;
         }
      }
      return null;
   }

   // Reads the <TIVOS> block and returns the IP for the given tivo name.
   private static String readTivoIp(String path, String tivoName) throws Exception {
      try (BufferedReader in = new BufferedReader(new FileReader(path))) {
         String line;
         boolean inBlock = false;
         while ((line = in.readLine()) != null) {
            String t = line.trim();
            if (t.equals("<TIVOS>")) { inBlock = true; continue; }
            if (inBlock) {
               if (t.startsWith("<")) break;
               String[] parts = t.split("\\s+");
               if (parts.length >= 2 && parts[0].equals(tivoName))
                  return parts[parts.length - 1];
            }
         }
      }
      return null;
   }

   /** Replaces the real TSN and MAK with same-format dummy values. */
   private static class Sanitizer {
      private final String tsnDigits;
      private final String mak;

      Sanitizer(String tsn, String mak) {
         // tsn looks like "tsn:8460001234567890" - keep only the digits
         this.tsnDigits = (tsn != null) ? tsn.replaceAll("[^0-9]", "") : "";
         this.mak = mak;
      }

      String scrub(String s) {
         if (tsnDigits.length() > 0) {
            String dummy = repeat('0', tsnDigits.length());
            s = s.replace(tsnDigits, dummy);
         }
         if (mak != null && mak.length() > 0) {
            s = s.replace(mak, repeat('0', mak.length()));
         }
         // Catch any other tsn:<digits> sequences just in case
         s = s.replaceAll("tsn:[0-9]+", "tsn:0000000000000000");
         return s;
      }

      private static String repeat(char c, int n) {
         StringBuilder sb = new StringBuilder();
         for (int i = 0; i < n; i++) sb.append(c);
         return sb.toString();
      }
   }
}
