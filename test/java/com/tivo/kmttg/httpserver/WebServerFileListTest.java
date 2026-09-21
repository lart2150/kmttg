package com.tivo.kmttg.httpserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;

/**
 * Guards the file list the Browser and Stream pages are built from. It walks
 * the share directories, so whatever is sitting in them - a half downloaded
 * recording, an edit list, a metadata file kmttg wrote next to a video - is
 * input to it, and one unreadable entry must not cost the page its whole list.
 *
 * The details form also decides where a file can be played from, which is the
 * one field the page cannot work out for itself.
 */
public class WebServerFileListTest {

   @TempDir
   Path installDir;

   @TempDir
   Path shareDir;

   private int port;
   private String prevProgramDir, prevCache, prevOutputDir, prevMpegDir, prevMpegCutDir, prevEncodeDir;
   private int prevPort;
   private LinkedHashMap<String,String> prevShares;

   @BeforeEach
   public void startServer() throws IOException {
      Files.createDirectories(installDir.resolve("web/cache"));
      Files.write(installDir.resolve("index.html"), "<html>index</html>".getBytes(StandardCharsets.UTF_8));

      write("movie.mp4", repeat("video bytes ", 256)); // 3K, so the size in K is not 0
      // What kmttg leaves beside a recording it processed
      write("movie.mp4.txt", "title : Bob's Burgers\nepisodeTitle : Sheesh Cab Bob\n"
         + "seriesTitle : Bob's Burgers\ndescription : Bob drives a cab.\n"
         + "seriesId : SH01234567\niso_duration : PT29M57S\n"
         + "callsign : FOXHD\ndisplayMajorNumber : 711\nshowingBits : 4096\n");
      write("movie.edl", "0.0 30.0 3\n");
      write("notes.txt", "not a video");
      // /getVideoFiles descends one level, which is where the encode output of
      // a season lands
      write("Season 1/episode.mkv", "video bytes");   // name with a space in it

      prevProgramDir = config.programDir;
      prevCache = config.httpserver_cache;
      prevOutputDir = config.outputDir;
      prevMpegDir = config.mpegDir;
      prevMpegCutDir = config.mpegCutDir;
      prevEncodeDir = config.encodeDir;
      prevPort = config.httpserver_port;
      prevShares = new LinkedHashMap<String,String>(config.httpserver_shares);

      config.programDir = installDir.toString();
      config.httpserver_cache = installDir.resolve("web/cache").toString();
      // outputDir is walked for video files too but has no share of its own,
      // so leave it out and let the shares be the only source here
      config.outputDir = "";
      config.mpegDir = shareDir.toString();
      config.mpegCutDir = shareDir.toString();
      config.encodeDir = shareDir.toString();
      config.httpserver_shares.clear();
      config.httpserver_port = port = freePort();

      new kmttgServer();
      assertTrue(config.httpserver != null, "server failed to start");
   }

   @AfterEach
   public void stopServer() {
      if (config.httpserver != null) {
         config.httpserver.stop();
         config.httpserver = null;
      }
      config.programDir = prevProgramDir;
      config.httpserver_cache = prevCache;
      config.outputDir = prevOutputDir;
      config.mpegDir = prevMpegDir;
      config.mpegCutDir = prevMpegCutDir;
      config.encodeDir = prevEncodeDir;
      config.httpserver_port = prevPort;
      config.httpserver_shares.clear();
      config.httpserver_shares.putAll(prevShares);
   }

   @Test
   public void videoFiles_listsTheVideosAndNothingElse() throws Exception {
      String list = body("/getVideoFiles");
      assertTrue(list.contains("movie.mp4\""), "the video is missing: " + list);
      assertTrue(list.contains("episode.mkv"), "a video one level down is missing: " + list);
      assertFalse(list.contains("notes.txt"), "a non-video was listed: " + list);
      assertFalse(list.contains("movie.edl"), "an edit list was listed: " + list);
   }

   // The page needs the url to play from and the type to play it with; a file
   // that is right there but has no share url gets no play link at all
   @Test
   public void videoFileDetails_carryThePlayUrlAndTheType() throws Exception {
      JSONObject movie = entryEndingWith(details(), "movie.mp4");
      assertEquals("video/mp4", movie.getString("format"));
      assertEquals(3, movie.getInt("size"), "size is reported in K");
      assertEquals(3072, fetch(movie.getString("sharePath")).length(),
         "the share url did not play the file it was listed for");
   }

   // The files kmttg writes beside a recording - the metadata, the edit list,
   // captions - travel with it, so the page can offer them alongside
   @Test
   public void videoFileDetails_listTheFilesThatBelongWithTheVideo() throws Exception {
      JSONArray suffixes = entryEndingWith(details(), "movie.mp4").getJSONArray("suffixes");
      String all = suffixes.toString();
      assertTrue(all.contains("\"edl\""), "the edit list is missing: " + all);
      assertTrue(all.contains("\"mp4.txt\""), "the metadata file is missing: " + all);
      assertFalse(all.contains("mp4\""), "the video listed itself as its own extra: " + all);
   }

   // A local file is shown in the same table as a recording on a TiVo, so the
   // metadata kmttg wrote is read back into the same field names the rpc
   // listing uses
   @Test
   public void videoFileDetails_readTheMetadataWrittenBesideTheVideo() throws Exception {
      JSONObject movie = entryEndingWith(details(), "movie.mp4");
      assertEquals("Bob's Burgers", movie.getString("title"));
      assertEquals("Sheesh Cab Bob", movie.getString("subtitle"));
      assertEquals("Bob drives a cab.", movie.getString("description"));
      assertEquals("SH01234567", movie.getString("__SeriesId__"));
      assertEquals(1797, movie.getInt("duration"), "iso duration not converted to seconds");
      assertEquals("FOXHD", movie.getJSONObject("channel").getString("callSign"));
      assertEquals("711", movie.getJSONObject("channel").getString("channelNumber"));
      assertTrue(movie.getBoolean("hdtv"), "showingBits did not yield the HD flag");
   }

   // A recording that was never processed has no metadata file next to it, and
   // it still has to appear - with what is known about it and no more
   @Test
   public void videoFileDetails_stillDescribeAVideoWithNoMetadata() throws Exception {
      JSONObject episode = entryEndingWith(details(), "episode.mkv");
      assertTrue(episode.getString("sharePath").endsWith("/Season 1/episode.mkv"),
         "share url does not name the file: " + episode.getString("sharePath"));
      assertEquals("video/x-matroska", episode.getString("format"));
      assertEquals("video bytes", fetch(episode.getString("sharePath")));
      assertFalse(episode.has("title"), "invented a title: " + episode);
   }

   /* ---- helpers ---- */

   private JSONArray details() throws Exception {
      return new JSONArray(body("/getVideoFileDetails"));
   }

   private static JSONObject entryEndingWith(JSONArray files, String name) throws Exception {
      for (int i = 0; i < files.length(); ++i) {
         JSONObject json = files.getJSONObject(i);
         if (json.getString("videoFile").endsWith(name))
            return json;
      }
      throw new AssertionError(name + " is not in the file list: " + files);
   }

   // What the browser does with the share url it was handed
   private String fetch(String sharePath) throws IOException {
      return body(sharePath.replace(" ", "%20"));
   }

   private static String repeat(String s, int times) {
      StringBuilder out = new StringBuilder();
      for (int i = 0; i < times; i++)
         out.append(s);
      return out.toString();
   }

   private void write(String relative, String content) throws IOException {
      Path p = shareDir.resolve(relative);
      Files.createDirectories(p.getParent());
      Files.write(p, content.getBytes(StandardCharsets.UTF_8));
   }

   private String body(String path) throws IOException {
      HttpURLConnection c = (HttpURLConnection)
         new URL("http://localhost:" + port + path).openConnection();
      c.setConnectTimeout(5000);
      c.setReadTimeout(5000);
      c.setInstanceFollowRedirects(false);
      try (InputStream in = c.getResponseCode() < 400 ? c.getInputStream() : c.getErrorStream()) {
         assertEquals(200, c.getResponseCode(), path);
         return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
      } finally {
         c.disconnect();
      }
   }

   private static int freePort() throws IOException {
      try (ServerSocket s = new ServerSocket(0)) {
         return s.getLocalPort();
      }
   }
}
