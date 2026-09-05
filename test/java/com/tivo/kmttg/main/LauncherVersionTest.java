package com.tivo.kmttg.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests the JRE version parsing in the Java 8 launcher shim. This compiles at
 * release 11 like the rest of the suite, which is fine - it only calls the
 * shim. The shim's own bytecode level is checked by the verifyLauncherBytecode
 * Gradle task, which reads the shipped jar.
 */
public class LauncherVersionTest {

   @Test
   public void parseFeatureVersion_handlesLegacy1xForm() {
      assertEquals(8, Launcher.parseFeatureVersion("1.8.0_412"));
      assertEquals(8, Launcher.parseFeatureVersion("1.8"));
      assertEquals(7, Launcher.parseFeatureVersion("1.7.0_80"));
   }

   @Test
   public void parseFeatureVersion_handlesModernForm() {
      assertEquals(9, Launcher.parseFeatureVersion("9"));
      assertEquals(11, Launcher.parseFeatureVersion("11.0.23"));
      assertEquals(11, Launcher.parseFeatureVersion("11-ea"));
      assertEquals(17, Launcher.parseFeatureVersion("17"));
      assertEquals(21, Launcher.parseFeatureVersion("21.0.7+6-LTS"));
   }

   // -1 rather than an exception, so main() carries on and lets the class
   // loader decide instead of locking out a good JRE over an odd version string
   @Test
   public void parseFeatureVersion_returnsMinusOneOnJunk() {
      assertEquals(-1, Launcher.parseFeatureVersion(null));
      assertEquals(-1, Launcher.parseFeatureVersion(""));
      assertEquals(-1, Launcher.parseFeatureVersion("   "));
      assertEquals(-1, Launcher.parseFeatureVersion("weird"));
      assertEquals(-1, Launcher.parseFeatureVersion("-3"));
   }

   @Test
   public void parseFeatureVersion_trimsWhitespace() {
      assertEquals(8, Launcher.parseFeatureVersion("  1.8.0_412 "));
      assertEquals(21, Launcher.parseFeatureVersion("\t21.0.7\n"));
   }

   @Test
   public void runningJavaVersion_clearsTheMinimum() {
      int running = Launcher.runningJavaVersion();
      assertTrue(running >= 11, "expected the test JVM to be Java 11 or newer, got " + running);
   }
}
