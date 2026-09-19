package com.tivo.kmttg.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Guards the routing that sends a KMTTG_MUX profile to the in-process remux task instead of
// spawning an encoder. The keyword survives getCommandName untouched precisely because no
// substitution matches it, which is easy to break by adding one.
public class BuiltinMuxProfileTest {

   @TempDir
   Path encodeDir;

   private void writeProfile(String name, String command, String extension) throws IOException {
      String body = "<description>\n" + name + " test profile\n"
                  + "<command>\n" + command + "\n"
                  + "<extension>\n" + extension + "\n";
      Files.write(encodeDir.resolve(name + ".enc"), body.getBytes(StandardCharsets.UTF_8));
   }

   private String savedProfDir;
   private String savedFfmpeg;

   @AfterEach
   void restoreConfig() {
      // config is process-wide static and gradle runs the whole source set in one JVM, so
      // leaving these set decides how unrelated tests behave depending on ordering.
      config.encProfDir = savedProfDir;
      config.ffmpeg = savedFfmpeg;
      encodeConfig.parseEncodingProfiles();
   }

   @BeforeEach
   void parseProfiles() throws IOException {
      savedProfDir = config.encProfDir;
      savedFfmpeg = config.ffmpeg;
      writeProfile("mkv_copy", "KMTTG_MUX INPUT OUTPUT", "mkv");
      writeProfile("ff_copy", "FFMPEG -y -i INPUT -c copy -f mp4 OUTPUT", "mp4");
      config.encProfDir = encodeDir.toString();
      config.ffmpeg = "/does/not/matter/ffmpeg";
      encodeConfig.parseEncodingProfiles();
   }

   @Test
   void builtinMuxProfileIsRecognized() {
      assertTrue(encodeConfig.isValidEncodeName("mkv_copy"), "mkv_copy should parse");
      assertTrue(encodeConfig.isBuiltinMux("mkv_copy"));
      assertEquals("mkv", encodeConfig.getExtension("mkv_copy"));
   }

   @Test
   void externalEncoderProfileIsNotBuiltin() {
      assertTrue(encodeConfig.isValidEncodeName("ff_copy"), "ff_copy should parse");
      assertFalse(encodeConfig.isBuiltinMux("ff_copy"),
         "a profile that spawns ffmpeg must not route to the in-process remuxer");
   }

   @Test
   void keywordIsNotSubstitutedAway() {
      // If a future keyword substitution ever matched KMTTG_MUX, routing would silently fall
      // back to spawning it as a command name, which would fail at run time rather than here.
      assertEquals(encodeConfig.BUILTIN_MUX, encodeConfig.getCommandName("mkv_copy"));
   }

   @Test
   void inputAndOutputStillSubstituteForTheBuiltin() {
      // The remux task reads paths off the job rather than the argument list, but the profile
      // still declares INPUT and OUTPUT and they should behave like any other profile's.
      java.util.Stack<String> args =
         encodeConfig.getCommandArgs("mkv_copy", "in.ts", "out.mkv", null);
      assertEquals(2, args.size());
      assertEquals("in.ts", args.get(0));
      assertEquals("out.mkv", args.get(1));
   }
}
