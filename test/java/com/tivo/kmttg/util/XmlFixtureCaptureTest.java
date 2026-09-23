package com.tivo.kmttg.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests {@link XmlFixtureCapture#readTivos}, which the Help menu capture uses to find the
 * TiVos in config.ini. It has to read names the way config.java does - everything but the
 * last field - or two TiVos whose names share a first word collapse into one.
 */
public class XmlFixtureCaptureTest {

   @Test
   public void namesWithSpacesAreKeptWhole(@TempDir Path dir) throws Exception {
      Path ini = dir.resolve("config.ini");
      Files.write(ini, ("<MAK>\n0000000000\n<TIVOS>\nFILES C:/Video Files\n"
         + "Living Room 192.168.1.20\nLiving Den 192.168.1.21\nBolt 192.168.1.22\n<next>\n")
         .getBytes(StandardCharsets.UTF_8));

      Map<String,String> tivos = XmlFixtureCapture.readTivos(ini.toString());

      assertEquals(3, tivos.size(), tivos.toString());
      assertEquals("192.168.1.20", tivos.get("Living Room"));
      assertEquals("192.168.1.21", tivos.get("Living Den"));
      assertEquals("192.168.1.22", tivos.get("Bolt"));
   }
}
