package com.tivo.kmttg.rpc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.tools.FixtureSanitizer;

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
      // The task fills every positional slot, so an unasked-for date arrives empty
      String guideDate = args.length > 3 && args[3].length() > 0 ? args[3] : null;

      // Point kmttg at the given config dir (for the cdata cert fallback) and
      // read just the values we need from config.ini ourselves, so we don't
      // depend on config.parse() (which would reset programDir).
      config.programDir = configDir;
      String mak = readIniValue(configDir + File.separator + "config.ini", "MAK");
      String ip  = readTivoIp(configDir + File.separator + "config.ini", tivoName);
      if (mak == null || ip == null)
         throw new IllegalStateException("Could not read MAK / " + tivoName + " IP from config.ini");

      run(tivoName, ip, mak, maxEntries, guideDate, new File("test/resources/fixtures"));
   }

   /**
    * The capture itself, against one box, into one directory. Split out from main so the
    * contributor tool can run it beside the XML capture for each box it found.
    */
   public static void run(String tivoName, String ip, String mak, int maxEntries,
         String guideDate, File outDir) throws Exception {
      System.out.println("Connecting to " + tivoName + " at " + ip + " ...");
      RecordingRemote r = new RecordingRemote(tivoName, ip, -1, mak, null);
      if (!r.success)
         throw new IllegalStateException("RPC connection/auth failed");

      String tsn = r.bodyId_get(); // e.g. "tsn:846000123456AB12"
      // The address as well: a response naming the box's own host would otherwise keep it,
      // and which values are in reach should not depend on which capture wrote the file.
      FixtureSanitizer san = new FixtureSanitizer(ip, mak, tsn);

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
      JSONArray todo = captureWithCommands(san, outDir, "todo", r, () -> r.ToDo(null), maxEntries, 8);
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
      // directly. Today by default: a TiVo only holds guide data a few days
      // back, so a date pinned in the source can only go stale and then return
      // nothing. Pass one to reproduce an old capture while it is still in range.
      // TiVo stores channel numbers with a dash (displayed as 2.1 / 5.1)
      String[] guideChannels = { "2-1", "5-1" };
      // TodoFlagTest needs a guide listing that the ToDo fixture also contains,
      // so take the day from the ToDo list itself rather than picking one and
      // hoping. Only the entries that were actually written count.
      if (guideDate == null) guideDate = todoDate(todo, guideChannels[0], maxEntries);
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
         // A row with no offer at all is what an out-of-range date returns, and
         // throwing here would abandon the run after the other fixtures are
         // already written. Skip it and leave that fixture as it was.
         JSONArray offers = null;
         if (gRes != null && gRes.has("gridRow")) {
            JSONArray rows = gRes.getJSONArray("gridRow");
            if (rows.length() > 0 && rows.getJSONObject(0).has("offer"))
               offers = rows.getJSONObject(0).getJSONArray("offer");
         }
         if (offers == null)
            System.out.println("  guide_" + chanNum + ".json: no offers for " + guideDate + " (skipped)");
         capture(san, outDir, "guide_" + chanNum + ".json", offers, maxEntries);
      }

      r.disconnect();
      System.out.println("Done.");
   }

   private static void capture(FixtureSanitizer san, File dir, String name, JSONArray data, int max) throws Exception {
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
      FixtureJson.write(new File(dir, name), FixtureJson.prune(new JSONArray(san.scrub(trimmed.toString()))));
      System.out.println("  " + name + ": " + trimmed.length() + " of " + data.length() + " entries written");
   }

   /**
    * Runs a high-level read call, writing both its response fixture
    * (&lt;name&gt;.json) and the RPC command trace it issued, trimmed to the first
    * maxCmds commands (commands_&lt;name&gt;.json), for the replay tests.
    */
   /**
    * The day of the first written ToDo entry on the given channel, which is the
    * day whose guide listings will overlap it. Falls back to today when nothing
    * is scheduled there - the guide still captures, but TodoFlagTest will have
    * no match to find, so say so rather than let it surface as a test failure.
    */
   private static String todoDate(JSONArray todo, String chanNum, int maxEntries) {
      int n = todo == null ? 0 : Math.min(todo.length(), maxEntries);
      for (int i = 0; i < n; i++) {
         try {
            JSONObject t = todo.getJSONObject(i);
            if (! t.has("startTime")) continue;
            if (! chanNum.equals(t.getJSONObject("channel").getString("channelNumber"))) continue;
            String date = t.getString("startTime").substring(0, 10);
            System.out.println("Guide date " + date + " (from the ToDo entry on " + chanNum + ")");
            return date;
         } catch (Exception e) {
            // entry shaped differently than expected; try the next one
         }
      }
      String today = java.time.LocalDate.now().toString();
      System.out.println("WARNING: nothing on " + chanNum + " in the ToDo fixture - "
         + "capturing guide for " + today + ", and TodoFlagTest will not find an overlap");
      return today;
   }

   private static JSONArray captureWithCommands(FixtureSanitizer san, File dir, String name, RecordingRemote r,
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
      FixtureJson.write(new File(dir, "commands_" + name + ".json"),
            FixtureJson.prune(new JSONArray(san.scrub(log.toString()))));
      System.out.println("  commands_" + name + ".json: " + log.length() + " of " + fullLog.length() + " commands written");
      return data;
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
}
