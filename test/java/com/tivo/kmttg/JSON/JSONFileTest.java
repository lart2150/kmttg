package com.tivo.kmttg.JSON;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the small file wrapper the season pass export, the channel/thumbs
 * caches and the web server all persist through.
 *
 * Every method here returns a status instead of throwing - false or null on
 * any failure - so callers that never check are the risk, and what a failure
 * actually returns is worth pinning. The reader is also strict about which
 * shape it is asked for: a file holding an array does not come back from
 * readJSONObject, it comes back null.
 */
public class JSONFileTest {

   @TempDir
   Path dir;

   @Test
   public void objectsSurviveAWriteAndReadRoundTrip() throws Exception {
      JSONObject json = new JSONObject("{\"title\":\"Cheers\",\"channel\":{\"num\":\"2-1\"}}");
      String file = dir.resolve("object.json").toString();

      assertTrue(JSONFile.write(json, file));
      JSONObject back = JSONFile.readJSONObject(file);
      assertNotNull(back);
      assertEquals("Cheers", back.getString("title"));
      assertEquals("2-1", back.getJSONObject("channel").getString("num"));
   }

   @Test
   public void arraysSurviveAWriteAndReadRoundTrip() throws Exception {
      JSONArray json = new JSONArray("[{\"a\":1},{\"a\":2}]");
      String file = dir.resolve("array.json").toString();

      assertTrue(JSONFile.write(json, file));
      JSONArray back = JSONFile.readJSONArray(file);
      assertNotNull(back);
      assertEquals(2, back.length());
      assertEquals(2, back.getJSONObject(1).getInt("a"));
   }

   @Test
   public void writeOverwritesRatherThanAppends() throws Exception {
      String file = dir.resolve("twice.json").toString();
      assertTrue(JSONFile.write(new JSONObject("{\"a\":1,\"b\":2}"), file));
      assertTrue(JSONFile.write(new JSONObject("{\"a\":9}"), file));

      JSONObject back = JSONFile.readJSONObject(file);
      assertEquals(1, back.length());
      assertEquals(9, back.getInt("a"));
   }

   @Test
   public void writtenTextIsTheCompactJsonForm() throws Exception {
      JSONObject json = new JSONObject("{\"a\":1}");
      String file = dir.resolve("compact.json").toString();
      JSONFile.write(json, file);
      assertEquals("{\"a\":1}",
         new String(Files.readAllBytes(Path.of(file)), Charset.defaultCharset()));
   }

   @Test
   public void readingWhatIsNotThereReturnsNull() {
      String missing = dir.resolve("missing.json").toString();
      assertNull(JSONFile.readJSONObject(missing));
      assertNull(JSONFile.readJSONArray(missing));
   }

   @Test
   public void readingTheWrongShapeReturnsNull() throws Exception {
      String object = dir.resolve("o.json").toString();
      String array = dir.resolve("a.json").toString();
      JSONFile.write(new JSONObject("{\"a\":1}"), object);
      JSONFile.write(new JSONArray("[1]"), array);

      assertNull(JSONFile.readJSONArray(object));
      assertNull(JSONFile.readJSONObject(array));
   }

   @Test
   public void readingGarbageReturnsNull() throws Exception {
      Path file = dir.resolve("garbage.json");
      Files.write(file, "not json at all".getBytes(Charset.defaultCharset()));
      assertNull(JSONFile.readJSONObject(file.toString()));
      assertNull(JSONFile.readJSONArray(file.toString()));
   }

   @Test
   public void writingWhereTheDirectoryDoesNotExistReportsFalse() throws Exception {
      String path = dir.resolve("no-such-dir").resolve("x.json").toString();
      assertFalse(JSONFile.write(new JSONObject("{\"a\":1}"), path));
      assertFalse(JSONFile.write(new JSONArray("[1]"), path));
      assertFalse(new File(path).exists());
   }
}
