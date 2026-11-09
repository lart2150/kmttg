package com.tivo.kmttg.httpserver;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sun.net.httpserver.HttpServer;
import com.tivo.kmttg.main.config;

/** Journal and HTTP ownership-check regressions; no real TiVo is needed. */
public class NativeHlsRecoveryTest {
   @TempDir Path dir;

   private NativeHlsRecovery.Entry entry(String uuid, String session) {
      return new NativeHlsRecovery.Entry(uuid, "tivo:hls." + session,
            "127.0.0.1", 9999999999L, 0L);
   }

   @Test void journalIsPrivateAndSurvivesRestart() throws Exception {
      String original = config.programDir;
      config.programDir = dir.toString();
      try {
         String uuid = UUID.randomUUID().toString();
         NativeHlsRecovery.save(uuid, "tivo:hls.57", "127.0.0.1");
         Path file = dir.resolve(".native-hls-sessions").resolve(uuid + ".session");
         assertTrue(Files.exists(file));
         NativeHlsRecovery.Entry loaded = NativeHlsRecovery.parse(file);
         assertEquals("tivo:hls.57", loaded.sessionId);
         assertEquals(uuid, loaded.uuid);
         assertEquals("127.0.0.1", loaded.ip);
         NativeHlsRecovery.remove(uuid);
         assertFalse(Files.exists(file));
      } finally {
         config.programDir = original;
      }
   }

   @Test void nonPosixFileSystemStillSupportsPrivateJournalWrites() throws Exception {
      // ZIPFS, like Windows' default filesystem provider, has no POSIX attributes.
      Path archive = dir.resolve("non-posix.zip");
      try (java.nio.file.FileSystem fs = java.nio.file.FileSystems.newFileSystem(
            java.net.URI.create("jar:" + archive.toUri()),
            java.util.Map.of("create", "true"))) {
         Path folder = fs.getPath("/journal");
         Files.createDirectories(folder);
         assertFalse(Files.getFileStore(folder).supportsFileAttributeView("posix"));
         NativeHlsRecovery.restrictPosixPermissions(folder, "rwx------");
         Path entry = Files.createTempFile(folder, "session-", ".tmp");
         NativeHlsRecovery.restrictPosixPermissions(entry, "rw-------");
         Files.writeString(entry, "test", StandardCharsets.US_ASCII);
         assertEquals("test", Files.readString(entry, StandardCharsets.US_ASCII));
      }
   }

   @Test void invalidOrTamperedJournalCannotBeUsedForRelease() throws Exception {
      Path file = dir.resolve(UUID.randomUUID() + ".session");
      Files.writeString(file, "something invalid", StandardCharsets.US_ASCII);
      assertThrows(java.io.IOException.class, () -> NativeHlsRecovery.parse(file));
      assertFalse(NativeHlsRecovery.valid(entry("bad-uuid", "2")));
      assertFalse(NativeHlsRecovery.valid(entry(UUID.randomUUID().toString(), "abc")));
   }

   @Test void neverReleaseAnActiveProcessSession() throws Exception {
      long pid = ProcessHandle.current().pid();
      long start = ProcessHandle.current().info().startInstant().orElseThrow()
            .toEpochMilli();
      NativeHlsRecovery.Entry owned = new NativeHlsRecovery.Entry(
            UUID.randomUUID().toString(), "tivo:hls.42", "127.0.0.1", pid, start);
      assertTrue(NativeHlsRecovery.ownerAlive(owned));
   }

   private void assertRecover(String expectedUuid, String expectedSession,
                              NativeHlsRecovery.Entry requested,
                              boolean shouldRelease) throws Exception {
      AtomicBoolean present = new AtomicBoolean(true);
      AtomicInteger releases = new AtomicInteger();
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/sysinfo/json/clients", exchange -> {
         String body = "{\"clients\":" + (present.get()
               ? "[{\"uuid\":\"" + expectedUuid + "\",\"sessionId\":" + expectedSession + "}]"
               : "[]") + "}";
         byte[] b = body.getBytes(StandardCharsets.UTF_8);
         exchange.getResponseHeaders().add("Content-Type", "application/json");
         exchange.sendResponseHeaders(200, b.length);
         try (var out = exchange.getResponseBody()) { out.write(b); }
      });
      server.createContext("/sysinfo/control", exchange -> {
         assertEquals("config=session&id=" + expectedSession + "&action=release",
               exchange.getRequestURI().getRawQuery());
         present.set(false);
         releases.incrementAndGet();
         byte[] b = "OK".getBytes(StandardCharsets.US_ASCII);
         exchange.sendResponseHeaders(200, b.length);
         try (var out = exchange.getResponseBody()) { out.write(b); }
      });
      server.start();
      try {
         assertTrue(NativeHlsRecovery.recover(requested, server.getAddress().getPort()));
         assertEquals(shouldRelease ? 1 : 0, releases.get());
      } finally {
         server.stop(0);
      }
   }

   @Test void matchingUuidAndSessionIdAreBothRequiredToRelease() throws Exception {
      String uuid = UUID.randomUUID().toString();
      assertRecover(uuid, "31", entry(uuid, "31"), true);
      assertRecover(uuid, "31", entry(UUID.randomUUID().toString(), "31"), false);
      assertRecover(uuid, "31", entry(uuid, "32"), false);
   }

   @Test void unresolvedDeviceCannotBeTreatedAsRecovered() {
      NativeHlsRecovery.Entry e = entry(UUID.randomUUID().toString(), "31");
      assertFalse(NativeHlsRecovery.recover(e, 9));
   }
}
