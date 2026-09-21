package com.tivo.kmttg.rpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.tools.FixtureSanitizer;

/** Loads the captured (sanitized) RPC response fixtures from the test classpath. */
public final class Fixtures {
   private Fixtures() {}

   /**
    * The bodyId every captured request carries, after the TSN is sanitized on
    * the way into the log. The offline Remote doubles seed this so a shaped
    * request compares against a recorded one.
    *
    * config.bodyId is a static map keyed by IP+port, and the doubles are all
    * IP="" port=0, so they share one entry and nothing puts back what was there
    * before. Seeding one agreed value keeps that from mattering.
    */
   public static final String BODY_ID = FixtureSanitizer.BODY_ID;

   /** Seed the shared bodyId map for an unconnected (IP="", port=0) Remote. */
   public static void seedBodyId() {
      config.bodyId_set("", 0, BODY_ID);
   }

   /**
    * Every captured guide listing fixture, named guide_&lt;channelNumber&gt;.json. Which
    * channels those are is a property of the box the capture came from - RpcFixtureCapture
    * takes the first two it receives - so they are discovered rather than named here, and
    * a capture from another TiVo needs no test edited.
    */
   public static List<String> guideFixtures() throws Exception {
      List<String> found = new ArrayList<String>();
      for (String name : new File(Fixtures.class.getResource("/fixtures").toURI()).list()) {
         if (name.startsWith("guide_") && name.endsWith(".json")) found.add(name);
      }
      assertFalse(found.isEmpty(), "no captured guide fixtures to read");
      Collections.sort(found);
      return found;
   }

   /** The channel a guide fixture holds, which its name carries. */
   public static String guideChannel(String fixture) {
      return fixture.substring("guide_".length(), fixture.length() - ".json".length());
   }

   public static JSONArray load(String name) throws Exception {
      try (InputStream is = Fixtures.class.getResourceAsStream("/fixtures/" + name)) {
         assertNotNull(is, "missing fixture: /fixtures/" + name);
         String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
         return new JSONArray(json);
      }
   }
}
