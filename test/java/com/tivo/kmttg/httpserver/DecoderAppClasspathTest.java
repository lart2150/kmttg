package com.tivo.kmttg.httpserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import org.junit.jupiter.api.Test;

/**
 * TiVoTranscode and Transcode spawn tivolibre's command line app out of the
 * shipped jar:
 *
 *    java -cp kmttg.jar net.straylightlabs.tivolibre.DecoderApp --mak ...
 *
 * Nothing kmttg compiles against pulls those classes in - they are runtimeOnly
 * dependencies that exist purely so the spawned process finds them - so a
 * dropped or downgraded dependency would only surface when a user hits
 * Transcode. These load them by name off the same runtime classpath.
 */
public class DecoderAppClasspathTest {

   private static Class<?> load(String name) {
      try {
         return Class.forName(name);
      }
      catch (ClassNotFoundException e) {
         fail("missing from the runtime classpath: " + name);
         return null;
      }
   }

   @Test
   public void decoderApp_isOnTheRuntimeClasspath() throws Exception {
      Class<?> app = load("net.straylightlabs.tivolibre.DecoderApp");
      Method main = app.getMethod("main", String[].class);
      assertTrue(Modifier.isStatic(main.getModifiers()));
      assertTrue(Modifier.isPublic(main.getModifiers()));
   }

   // DecoderApp parses its arguments with org.apache.commons.cli.help, which
   // only exists in commons-cli 1.10 and later
   @Test
   public void commonsCli_isNewEnoughForDecoderApp() {
      load("org.apache.commons.cli.help.HelpFormatter");
      load("org.apache.commons.cli.Options");
   }

   // DecoderApp casts the root logger to ch.qos.logback.classic.Logger to set
   // its level, so logback specifically has to be the bound provider - any
   // other slf4j backend, or none at all, throws there instead
   @Test
   public void logbackIsTheBoundSlf4jProvider() throws Exception {
      Class<?> logbackLogger = load("ch.qos.logback.classic.Logger");
      Class<?> factory = load("org.slf4j.LoggerFactory");
      Object root = factory.getMethod("getLogger", String.class).invoke(null, "ROOT");
      assertTrue(logbackLogger.isInstance(root),
         "root logger is a " + root.getClass().getName() + ", not a logback Logger");
   }

   // Shipped as images/logback.xml, which lands at the jar root. Without an
   // appender on stderr, logback's default writes to stdout - where the
   // spawned DecoderApp is writing the decoded MPEG that gets piped to ffmpeg.
   @Test
   public void logbackConfigKeepsOutputOffStdout() throws Exception {
      String xml;
      try (InputStream in = getClass().getResourceAsStream("/logback.xml")) {
         assertNotNull(in, "logback.xml is not at the classpath root");
         try (Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name())) {
            xml = scanner.useDelimiter("\\A").next();
         }
      }
      assertTrue(xml.contains("<appender-ref"), "root logger has no appender attached");
      assertTrue(xml.contains("System.err"), "console appender does not target stderr");
      assertEquals(0, countOccurrences(xml, "System.out"), "an appender still targets stdout");
   }

   private static int countOccurrences(String haystack, String needle) {
      int count = 0;
      for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length()))
         count++;
      return count;
   }
}
