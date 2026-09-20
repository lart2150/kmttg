package com.tivo.kmttg.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

// The build's version is declared twice. build.gradle stamps it into the jar manifest and
// the release zip name; config.kmttg is what the running copy reports, and update.java
// splits it to decide whether the published build is newer. Bumping one and not the other
// ships a jar that offers an update to the version it is already running. The published
// version file is deliberately left out of this - it is the release trigger and is meant to
// lag the source until a build is actually uploaded.
public class VersionConsistencyTest {

   private static final Pattern GRADLE_VERSION = Pattern.compile("^version\\s*=\\s*'([^']+)'");

   @Test
   public void buildGradleAndConfigDeclareTheSameVersion() throws Exception {
      String declared = buildGradleVersion();
      assertEquals(declared, configVersion(),
         "build.gradle and config.kmttg disagree - update.java compares config.kmttg against "
         + "the published version file, so the shipped jar would misreport itself");
   }

   // update.java does config.kmttg.split("\\s+")[1] with no length check
   @Test
   public void configVersionIsTheSecondWordSoTheUpdateCheckCanReadIt() {
      String[] parts = config.kmttg.split("\\s+");
      assertTrue(parts.length > 1, "config.kmttg must be a name and a version: " + config.kmttg);
      assertTrue(parts[1].startsWith("v"), "expected a v prefixed version: " + parts[1]);
   }

   private static String buildGradleVersion() throws Exception {
      File gradle = findUpwards("build.gradle");
      assertNotNull(gradle, "could not find build.gradle from " + new File(".").getAbsolutePath());
      for (String line : Files.readAllLines(gradle.toPath(), StandardCharsets.UTF_8)) {
         Matcher m = GRADLE_VERSION.matcher(line.trim());
         if (m.find())
            return m.group(1);
      }
      throw new AssertionError("no version declaration found in " + gradle);
   }

   private static String configVersion() {
      String[] parts = config.kmttg.split("\\s+");
      return parts.length > 1 ? parts[1] : config.kmttg;
   }

   // The working directory differs between a Gradle run and an IDE run, so walk up for it
   private static File findUpwards(String name) {
      File dir = new File(".").getAbsoluteFile();
      while (dir != null) {
         File candidate = new File(dir, name);
         if (candidate.isFile())
            return candidate;
         dir = dir.getParentFile();
      }
      return null;
   }
}
