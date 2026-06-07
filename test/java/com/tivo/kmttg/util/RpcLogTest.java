package com.tivo.kmttg.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tivo.kmttg.main.config;

/**
 * Verifies that the -rpcLog sanitizer strips the sensitive TSN (bodyId) and MAK
 * before anything is written to the log file.
 */
public class RpcLogTest {

   @Test
   public void scrub_sanitizesTsnAndMak() {
      String prevMak = config.MAK;
      try {
         config.MAK = "5550001111";
         String line = "{\"type\":\"MyShows\",\"request\":{\"bodyId\":\"tsn:8460009999999999\"},"
               + "\"response\":{\"mak\":\"5550001111\",\"nested\":\"tsn:8460009999999999\"}}";

         String out = rpcLog.scrub(line);

         // TSN (bodyId) replaced everywhere it appears, same-shape dummy.
         assertFalse(out.contains("tsn:8460009999999999"), "real TSN leaked");
         assertTrue(out.contains("tsn:0000000000000000"), "TSN not replaced with dummy");
         // MAK zeroed out (same length), original gone.
         assertFalse(out.contains("5550001111"), "real MAK leaked");
         assertTrue(out.contains("0000000000"), "MAK not replaced with zeros");
      } finally {
         config.MAK = prevMak;
      }
   }

   @Test
   public void scrub_emptyMakIsNoOpForMak() {
      String prevMak = config.MAK;
      try {
         config.MAK = "";
         // With no MAK set, scrubbing must not blow up or mangle the line; only
         // the tsn pattern is touched.
         String out = rpcLog.scrub("{\"bodyId\":\"tsn:8460009999999999\"}");
         assertTrue(out.contains("tsn:0000000000000000"), "TSN should still be sanitized");
      } finally {
         config.MAK = prevMak;
      }
   }
}
