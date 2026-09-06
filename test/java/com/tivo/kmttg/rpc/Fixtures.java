package com.tivo.kmttg.rpc;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.tivo.kmttg.JSON.JSONArray;

/** Loads the captured (sanitized) RPC response fixtures from the test classpath. */
public final class Fixtures {
   private Fixtures() {}

   public static JSONArray load(String name) throws Exception {
      try (InputStream is = Fixtures.class.getResourceAsStream("/fixtures/" + name)) {
         assertNotNull(is, "missing fixture: /fixtures/" + name);
         String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
         return new JSONArray(json);
      }
   }
}
