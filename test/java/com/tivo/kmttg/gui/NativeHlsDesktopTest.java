package com.tivo.kmttg.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import org.junit.jupiter.api.Test;

/** Desktop player link points only to the local, validated HLS web route. */
public class NativeHlsDesktopTest {
   @Test
   void routesRecordingToExistingLocalPlayer() {
      URI uri = NativeHlsDesktop.playerUri("Tilapia", "tivo:rc.123", 8181);
      assertEquals("http", uri.getScheme());
      assertEquals("127.0.0.1", uri.getHost());
      assertEquals(8181, uri.getPort());
      assertEquals("/web/NativeHls.html", uri.getPath());
      assertEquals("tivo=Tilapia&recordingId=tivo%3Arc.123", uri.getRawQuery());
   }

   @Test
   void encodesTiVoNamesAndNeverBuildsRemoteBrowserLinks() {
      URI uri = NativeHlsDesktop.playerUri("Living Room & Den", "tivo:rc.987", 49211);
      assertEquals("127.0.0.1", uri.getHost());
      assertTrue(uri.getRawQuery().contains("tivo=Living+Room+%26+Den"));
   }

   @Test
   void rejectsUnusableOrInjectedRecordingIDs() {
      assertThrows(IllegalArgumentException.class,
            () -> NativeHlsDesktop.playerUri("Tilapia", "../sysinfo/control", 8181));
      assertThrows(IllegalArgumentException.class,
            () -> NativeHlsDesktop.playerUri("Tilapia", "tivo:rc.1&admin=true", 8181));
      assertThrows(IllegalArgumentException.class,
            () -> NativeHlsDesktop.playerUri("Tilapia", null, 8181));
      assertThrows(IllegalArgumentException.class,
            () -> NativeHlsDesktop.playerUri("", "tivo:rc.1", 8181));
      assertThrows(IllegalArgumentException.class,
            () -> NativeHlsDesktop.playerUri("Tilapia", "tivo:rc.1", 0));
      assertThrows(IllegalArgumentException.class,
            () -> NativeHlsDesktop.playerUri("Tilapia", "tivo:rc.1", 65536));
   }
}
