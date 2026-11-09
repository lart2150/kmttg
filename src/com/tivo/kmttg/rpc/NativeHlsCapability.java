package com.tivo.kmttg.rpc;

import java.util.Locale;

/**
 * Conservative UI hint for built-in TiVo HLS transcoding.
 * The TiVo's /sysinfo/json/svcinfo remains the authority at stream startup.
 * Unknown TSN prefixes are not presumed to have a transcoder.
 */
public final class NativeHlsCapability {
   private NativeHlsCapability() {}

   public static boolean hasBuiltInTranscoder(String tsn) {
      if (tsn == null) return false;
      String number = tsn.trim();
      if (number.regionMatches(true, 0, "tsn:", 0, 4))
         number = number.substring(4);
      if (number.length() < 3) return false;

      // Roamio Pro/Plus, BOLT and EDGE. The 846 four-tuner Roamio,
      // Premiere and Minis have no built-in HLS transcoder.
      switch (number.substring(0, 3).toUpperCase(Locale.ROOT)) {
         case "840":
         case "848":
         case "849":
         case "D6E":
         case "D6F":
            return true;
         default:
            return false;
      }
   }
}
