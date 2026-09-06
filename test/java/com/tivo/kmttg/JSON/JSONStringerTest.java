package com.tivo.kmttg.JSON;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.StringWriter;

import org.junit.jupiter.api.Test;

/**
 * Tests the streaming writer, through the JSONStringer that wraps it.
 *
 * Nothing in kmttg builds JSON this way today - it goes through JSONObject -
 * but the writer ships in the package and is the only piece that validates
 * structure as it is written rather than at the end. The state machine is the
 * whole class: a value where a key belongs, or a close that does not match the
 * open, has to fail at that call rather than emit broken text.
 */
public class JSONStringerTest {

   @Test
   public void buildsAnObjectInCascadeStyle() throws Exception {
      String out = new JSONStringer()
         .object()
            .key("title").value("Cheers")
            .key("season").value(3L)
            .key("hd").value(true)
         .endObject()
         .toString();
      assertEquals("{\"title\":\"Cheers\",\"season\":3,\"hd\":true}", out);
   }

   @Test
   public void buildsNestedArraysAndObjects() throws Exception {
      String out = new JSONStringer()
         .object()
            .key("eps").array().value(1L).value(2L).endArray()
            .key("channel").object().key("num").value("2-1").endObject()
         .endObject()
         .toString();
      assertEquals("{\"eps\":[1,2],\"channel\":{\"num\":\"2-1\"}}", out);
      assertEquals(2, new JSONObject(out).length());
   }

   @Test
   public void arrayCanBeTheOutermostValue() throws Exception {
      assertEquals("[1,\"two\"]",
         new JSONStringer().array().value(1L).value("two").endArray().toString());
   }

   @Test
   public void valuesCoverEveryOverload() throws Exception {
      String out = new JSONStringer().array()
         .value(true).value(1.5d).value(7L)
         .value((Object)null).value((Object)"s").value(new JSONArray("[1]"))
         .endArray().toString();
      assertEquals("[true,1.5,7,null,\"s\",[1]]", out);
      assertEquals(6, new JSONArray(out).length());
   }

   @Test
   public void anUnfinishedDocumentStringifiesAsNull() throws Exception {
      // Not 'd' (done) mode yet, so there is deliberately nothing to hand back
      assertNull(new JSONStringer().object().key("a").value(1L).toString());
      assertNull(new JSONStringer().toString());
   }

   @Test
   public void valuesAndKeysOutOfSequenceAreRejected() {
      assertThrows(JSONException.class, () -> new JSONStringer().value("bare"));
      assertThrows(JSONException.class, () -> new JSONStringer().key("k"));
      assertThrows(JSONException.class,
         () -> new JSONStringer().object().value("no key first"));
      assertThrows(JSONException.class,
         () -> new JSONStringer().array().key("no keys in arrays"));
      assertThrows(JSONException.class,
         () -> new JSONStringer().object().key(null));
      // A repeated key would produce an object that cannot be read back
      assertThrows(JSONException.class,
         () -> new JSONStringer().object().key("a").value(1L).key("a"));
   }

   @Test
   public void mismatchedClosesAreRejected() {
      assertThrows(JSONException.class, () -> new JSONStringer().object().endArray());
      assertThrows(JSONException.class, () -> new JSONStringer().array().endObject());
      assertThrows(JSONException.class, () -> new JSONStringer().endObject());
      assertThrows(JSONException.class, () -> new JSONStringer().endArray());
      // Nothing may follow the outermost close
      assertThrows(JSONException.class,
         () -> new JSONStringer().array().endArray().value(1L));
   }

   @Test
   public void nestingDeeperThanTheStackIsRejected() {
      assertThrows(JSONException.class, () -> {
         JSONStringer s = new JSONStringer();
         for (int i = 0; i < 25; ++i)
            s.array();
      });
   }

   @Test
   public void nonFiniteNumbersAreRejected() {
      assertThrows(JSONException.class, () -> new JSONStringer().array().value(Double.NaN));
   }

   @Test
   public void theWriterUnderneathCanBeSuppliedDirectly() throws Exception {
      StringWriter out = new StringWriter();
      new JSONWriter(out).object().key("a").value(1L).endObject();
      assertEquals("{\"a\":1}", out.toString());
   }
}
