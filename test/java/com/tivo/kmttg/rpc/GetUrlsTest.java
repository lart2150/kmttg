package com.tivo.kmttg.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;

/**
 * Tests {@link Remote#getURLs}, which turns a recordingId into the two URLs the
 * rest of kmttg downloads through. The TiVo will not serve a recording by its
 * recordingId - it wants the mfs id, which only an idSearch knows - so every
 * RPC-mode download depends on this resolving, stripping the "mfs:rc." prefix
 * and pasting the result into a URL. Get any of it wrong and the NPL fetch
 * aborts (getURLs returning false makes MyShows return null) or the download
 * 404s much later with nothing pointing back here.
 *
 * The idSearch responses are replayed from a real capture.
 */
public class GetUrlsTest {

   private static final String TIVO = "Bolt";
   // The first recording in the recorded trace, and the id it resolved to.
   private static final String RECORDING_ID = "tivo:rc.16717699";
   private static final String MFS_ID = "35122";

   private String prevProgramDir;

   @BeforeEach
   public void isolateCacheAndTivo(@TempDir Path dir) {
      prevProgramDir = config.programDir;
      config.programDir = dir.toString();
      MfsCache.reset();
      config.TIVOS.put(TIVO, "192.168.1.50");
   }

   @AfterEach
   public void restore() {
      config.programDir = prevProgramDir;
      MfsCache.reset();
      config.TIVOS.remove(TIVO);
      config.setWanSetting(TIVO, "http", "");
      config.setWanSetting(TIVO, "https", "");
   }

   private static JSONObject recording(String recordingId, String title) throws Exception {
      JSONObject json = new JSONObject();
      json.put("recordingId", recordingId);
      if (title != null)
         json.put("title", title);
      return json;
   }

   @Test
   public void resolvesTheMfsIdAndBuildsBothUrls() throws Exception {
      ReplayRemote r = new ReplayRemote(Fixtures.load("commands_geturls.json"));
      JSONObject json = recording(RECORDING_ID, "Today");

      assertTrue(r.getURLs(TIVO, json), "getURLs failed on a recorded idSearch");

      assertEquals(1, r.issued("idSearch"), "expected one lookup");
      assertEquals(RECORDING_ID, r.lastRequest("idSearch").getString("objectId"),
         "should have asked for the recording's own id");
      assertEquals("mfs", r.lastRequest("idSearch").getString("namespace"),
         "the mfs namespace is what makes this return a download id");
      // The response is "mfs:rc.35122"; everything downstream wants the bare
      // number, so the prefix has to come off.
      assertEquals("http://192.168.1.50:80/download/Today.TiVo?Container=%2FNowPlaying&id=" + MFS_ID,
         json.getString("__url__"));
      assertEquals("https://192.168.1.50:443/TiVoVideoDetails?id=" + MFS_ID,
         json.getString("__url_TiVoVideoDetails__"));
   }

   @Test
   public void usesTheMfsIdAsTheFilenameWhenThereIsNoTitle() throws Exception {
      ReplayRemote r = new ReplayRemote(Fixtures.load("commands_geturls.json"));
      JSONObject json = recording(RECORDING_ID, null);

      assertTrue(r.getURLs(TIVO, json));

      assertTrue(json.getString("__url__").contains("/download/" + MFS_ID + ".TiVo"),
         "expected the mfs id as the filename, got " + json.getString("__url__"));
   }

   @Test
   public void urlEncodesATitleWithSpacesAndPunctuation() throws Exception {
      // Titles go straight into the path, so anything unencoded breaks the GET.
      ReplayRemote r = new ReplayRemote(Fixtures.load("commands_geturls.json"));
      JSONObject json = recording(RECORDING_ID, "Bob's Burgers & Friends");

      assertTrue(r.getURLs(TIVO, json));

      String url = json.getString("__url__");
      assertFalse(url.contains(" "), "unencoded space in " + url);
      assertFalse(url.contains("&Friends"), "unencoded ampersand in " + url);
      assertTrue(url.contains("Bob%27s+Burgers"), "unexpected encoding in " + url);
   }

   @Test
   public void honoursTheWanPortOverrides() throws Exception {
      // Remote access puts the TiVo behind forwarded ports.
      config.setWanSetting(TIVO, "http", "8080");
      config.setWanSetting(TIVO, "https", "8443");
      ReplayRemote r = new ReplayRemote(Fixtures.load("commands_geturls.json"));
      JSONObject json = recording(RECORDING_ID, "Today");

      assertTrue(r.getURLs(TIVO, json));

      assertTrue(json.getString("__url__").startsWith("http://192.168.1.50:8080/"),
         "wrong http port in " + json.getString("__url__"));
      assertTrue(json.getString("__url_TiVoVideoDetails__").startsWith("https://192.168.1.50:8443/"),
         "wrong https port in " + json.getString("__url_TiVoVideoDetails__"));
   }

   @Test
   public void cachesTheLookupSoASecondPassIssuesNoRpc() throws Exception {
      // A full NPL refresh calls this once per recording; without the cache
      // that is one extra round trip each, on every refresh and every restart.
      ReplayRemote r = new ReplayRemote(Fixtures.load("commands_geturls.json"));

      assertTrue(r.getURLs(TIVO, recording(RECORDING_ID, "Today")));
      assertEquals(MFS_ID, MfsCache.get(TIVO, RECORDING_ID), "should have cached the mfs id");

      JSONObject again = recording(RECORDING_ID, "Today");
      assertTrue(r.getURLs(TIVO, again), "a cached id should still produce URLs");

      assertEquals(1, r.issued("idSearch"), "the second call must not hit the TiVo");
      assertTrue(again.getString("__url__").endsWith("id=" + MFS_ID));
   }

   @Test
   public void failsWithoutInventingAUrlWhenTheIdCannotBeResolved() throws Exception {
      // An empty trace stands in for a TiVo that has no mfs id for the
      // recording. MyShows turns this false into a null NPL rather than
      // handing back rows whose download URL points nowhere.
      ReplayRemote r = new ReplayRemote(new JSONArray());
      JSONObject json = recording("tivo:rc.404", "Gone");

      assertFalse(r.getURLs(TIVO, json), "unresolved id should report failure");

      assertEquals(1, r.issued("idSearch"));
      assertFalse(json.has("__url__"), "no URL should be grafted on");
      assertFalse(json.has("__url_TiVoVideoDetails__"), "no details URL should be grafted on");
   }
}
