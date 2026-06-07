package com.tivo.kmttg.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tivo.kmttg.main.config;

/**
 * Verifies the recordingId -> mfs id cache: in-memory lookups, per-TiVo keying,
 * and persistence across a simulated restart.
 */
public class MfsCacheTest {

   @Test
   public void persistsAndReloadsPerTivo(@TempDir Path dir) {
      String prevProgramDir = config.programDir;
      try {
         config.programDir = dir.toString();
         MfsCache.reset();

         assertNull(MfsCache.get("Bolt", "tivo:rc.1"), "miss expected on empty cache");

         MfsCache.put("Bolt", "tivo:rc.1", "100");
         MfsCache.put("Bolt", "tivo:rc.2", "200");
         assertEquals("100", MfsCache.get("Bolt", "tivo:rc.1"));

         // Same recordingId on a different TiVo must not collide.
         assertNull(MfsCache.get("Roamio", "tivo:rc.1"), "keys must be per-TiVo");

         MfsCache.save();

         // Simulate a restart: drop in-memory state, force a reload from disk.
         MfsCache.reset();
         assertEquals("100", MfsCache.get("Bolt", "tivo:rc.1"), "should reload from disk");
         assertEquals("200", MfsCache.get("Bolt", "tivo:rc.2"));
         assertNull(MfsCache.get("Bolt", "tivo:rc.404"), "unknown id still misses");
      } finally {
         config.programDir = prevProgramDir;
         MfsCache.reset();
      }
   }
}
