package com.tivo.kmttg.rpc;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class NativeHlsCapabilityTest {
   @Test
   void onlyKnownIntegratedTranscoderModelsAreOffered() {
      for (String tsn : new String[] {
            "8400000000", "8480000000", "8490000000",
            "D6E0000000", "D6F0000000", "tsn:8490000000",
            "tsn:d6e0000000", " 8480000000 " })
         assertTrue(NativeHlsCapability.hasBuiltInTranscoder(tsn), tsn);

      for (String tsn : new String[] {
            "8460000000", "7460000000", "7480000000",
            "7500000000", "7580000000", "A920000000",
            "A940000000", "D180000000", "unknown", "", "tsn:",
            "  " })
         assertFalse(NativeHlsCapability.hasBuiltInTranscoder(tsn), tsn);
      assertFalse(NativeHlsCapability.hasBuiltInTranscoder(null));
   }
}
