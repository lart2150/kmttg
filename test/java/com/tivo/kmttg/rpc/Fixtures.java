package com.tivo.kmttg.rpc;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.main.config;

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
   public static final String BODY_ID = "tsn:0000000000000000ED87";

   /** Seed the shared bodyId map for an unconnected (IP="", port=0) Remote. */
   public static void seedBodyId() {
      config.bodyId_set("", 0, BODY_ID);
   }

   public static JSONArray load(String name) throws Exception {
      try (InputStream is = Fixtures.class.getResourceAsStream("/fixtures/" + name)) {
         assertNotNull(is, "missing fixture: /fixtures/" + name);
         String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
         return new JSONArray(json);
      }
   }
}
