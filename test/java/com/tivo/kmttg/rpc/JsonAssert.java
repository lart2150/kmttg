package com.tivo.kmttg.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Iterator;
import java.util.TreeSet;

import com.tivo.kmttg.JSON.JSONObject;

/**
 * Comparing two {@link JSONObject}s. They have no equals(), and toString()
 * follows hash order, so neither is usable as an assertion on its own.
 */
final class JsonAssert {
   private JsonAssert() {}

   static TreeSet<String> keys(JSONObject json) {
      TreeSet<String> keys = new TreeSet<String>();
      for (Iterator<?> it = json.keys(); it.hasNext(); )
         keys.add((String) it.next());
      return keys;
   }

   /**
    * Asserts the two carry the same keys and the same value under each. Values
    * are compared as strings, so a nested object or array has to match its
    * whole rendering - which is what a captured request needs.
    */
   static void assertSameJson(JSONObject expected, JSONObject actual, String where) throws Exception {
      assertEquals(keys(expected), keys(actual), where + ": wrong set of keys");
      for (String key : keys(expected))
         assertEquals(String.valueOf(expected.get(key)), String.valueOf(actual.get(key)),
            where + ": wrong value for " + key);
   }
}
