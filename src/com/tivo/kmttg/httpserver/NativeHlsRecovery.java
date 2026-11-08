/*
 * Copyright 2008-Present Kevin Moye <moyekj@yahoo.com>.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.tivo.kmttg.httpserver;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.log;

/**
 * Durable ledger for native TiVo sessions that outlive a hard kmttg crash.
 *
 * Only releases a session if the TiVo itself reports BOTH the exact
 * recorded client UUID and numeric session ID. It never reclaims an active
 * process's session or a session on an unconfigured device. A failed network
 * request leaves the entry for another attempt, not a blind release.
 */
final class NativeHlsRecovery {
   private static final Pattern UUID_PATTERN =
         Pattern.compile("[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}");
   private static final Pattern SESSION_PATTERN =
         Pattern.compile("tivo:hls\\.[0-9]+");
   private static final Pattern IPV4_PATTERN =
         Pattern.compile("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}");
   private static final long PID = ProcessHandle.current().pid();
   private static final long PROCESS_START =
         ProcessHandle.current().info().startInstant().map(Instant::toEpochMilli).orElse(0L);
   private static final int PORT = 49152;

   static final class Entry {
      final String uuid, sessionId, ip;
      final long pid, started;

      Entry(String uuid, String sessionId, String ip, long pid, long started) {
         this.uuid = uuid;
         this.sessionId = sessionId;
         this.ip = ip;
         this.pid = pid;
         this.started = started;
      }

      String numericId() {
         return sessionId.substring("tivo:hls.".length());
      }
   }

   private NativeHlsRecovery() {}

   static Path directory() {
      return Path.of(config.programDir, ".native-hls-sessions");
   }

   static boolean valid(Entry entry) {
      return entry != null && UUID_PATTERN.matcher(entry.uuid).matches()
         && SESSION_PATTERN.matcher(entry.sessionId).matches()
         && IPV4_PATTERN.matcher(entry.ip).matches()
         && entry.pid > 0 && entry.started >= 0;
   }

   static void save(String uuid, String sessionId, String ip) throws IOException {
      Entry e = new Entry(uuid, sessionId, ip, PID, PROCESS_START);
      if (!valid(e)) throw new IOException("Invalid native HLS journal entry");
      Path dir = directory();
      Files.createDirectories(dir);
      try {
         Files.setPosixFilePermissions(dir,
               java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
      } catch (UnsupportedOperationException ignored) {}
      Path target = dir.resolve(uuid + ".session");
      Path tmp = Files.createTempFile(dir, ".pending-", ".tmp");
      try {
         Files.setPosixFilePermissions(tmp,
               java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
         Files.writeString(tmp, String.join("\n", e.uuid, e.sessionId, e.ip,
               Long.toString(e.pid), Long.toString(e.started)) + "\n",
               StandardCharsets.US_ASCII);
         try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
         } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, target);
         }
      } finally {
         Files.deleteIfExists(tmp);
      }
   }

   static void remove(String uuid) {
      if (!UUID_PATTERN.matcher(uuid).matches()) return;
      try {
         Files.deleteIfExists(directory().resolve(uuid + ".session"));
      } catch (IOException e) {
         log.warn("Native HLS journal cleanup failed: " + e.getMessage());
      }
   }

   static Entry parse(Path file) throws IOException {
      List<String> fields = Files.readAllLines(file, StandardCharsets.US_ASCII);
      if (fields.size() != 5) throw new IOException("Bad native HLS journal fields");
      try {
         Entry entry = new Entry(fields.get(0), fields.get(1), fields.get(2),
               Long.parseLong(fields.get(3)), Long.parseLong(fields.get(4)));
         if (!valid(entry) || !file.getFileName().toString().equals(entry.uuid + ".session"))
            throw new IOException("Invalid native HLS journal values");
         return entry;
      } catch (NumberFormatException e) {
         throw new IOException("Bad native HLS journal process identifier", e);
      }
   }

   static boolean ownerAlive(Entry entry) {
      Optional<ProcessHandle> p = ProcessHandle.of(entry.pid);
      if (p.isEmpty() || !p.get().isAlive()) return false;
      // If the PID has been reused, compare process creation time before preserving it.
      Optional<Instant> actualStart = p.get().info().startInstant();
      // If start times are unavailable, do not risk releasing another live process.
      return actualStart.isEmpty() || entry.started == 0L
         || actualStart.get().toEpochMilli() == entry.started;
   }

   static boolean configuredAddress(String ip) {
      try {
         InetAddress address = InetAddress.getByName(ip);
         if (!address.isSiteLocalAddress() && !address.isLoopbackAddress())
            return false;
         for (String configured : config.TIVOS.values()) {
            if (configured != null && ip.equals(
                  InetAddress.getByName(configured).getHostAddress()))
               return true;
         }
      } catch (Exception ignored) {}
      return false;
   }

   /** null means network/schema failure; false means no exact session; true means present. */
   static Boolean present(Entry entry, int port) throws IOException {
      HttpURLConnection c = (HttpURLConnection) new URL("http", entry.ip, port,
            "/sysinfo/json/clients").openConnection();
      c.setConnectTimeout(3000);
      c.setReadTimeout(3000);
      c.setInstanceFollowRedirects(false);
      try {
         if (c.getResponseCode() != 200) return null;
         byte[] raw;
         try (var in = c.getInputStream()) {
            raw = in.readNBytes(256 * 1024 + 1);
         }
         if (raw.length > 256 * 1024) return null;
         JSONObject listing = new JSONObject(new String(raw, StandardCharsets.UTF_8));
         JSONArray clients = listing.optJSONArray("clients");
         if (clients == null) return null;
         for (int i = 0; i < clients.length(); i++) {
            JSONObject client = clients.optJSONObject(i);
            if (client == null) continue;
            if (entry.uuid.equals(client.optString("uuid", ""))
               && entry.numericId().equals(client.optString("sessionId", "")))
               return true;
         }
         return false;
      } catch (Exception e) {
         return null;
      } finally {
         c.disconnect();
      }
   }

   static boolean release(Entry entry, int port) {
      HttpURLConnection c = null;
      try {
         c = (HttpURLConnection) new URL("http", entry.ip, port,
               "/sysinfo/control?config=session&id=" + entry.numericId()
                  + "&action=release").openConnection();
         c.setConnectTimeout(3000);
         c.setReadTimeout(3000);
         c.setInstanceFollowRedirects(false);
         return c.getResponseCode() == 200;
      } catch (IOException e) {
         return false;
      } finally {
         if (c != null) c.disconnect();
      }
   }

   static boolean recover(Entry entry, int port) {
      if (!valid(entry) || ownerAlive(entry)) return false;
      try {
         Boolean before = present(entry, port);
         if (before == null) return false;
         if (!before) return true; // Already released or TiVo rebooted.
         if (!release(entry, port)) return false;
         return Boolean.FALSE.equals(present(entry, port));
      } catch (IOException e) {
         return false;
      }
   }

   static void recoverOrphans() {
      Path dir = directory();
      if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) return;
      try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.session")) {
         for (Path path : files) {
            try {
               if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue;
               Entry entry = parse(path);
               if (!configuredAddress(entry.ip)) continue;
               if (recover(entry, PORT)) {
                  Files.deleteIfExists(path);
                  log.print("Recovered an abandoned native HLS session");
               }
            } catch (Exception e) {
               log.warn("Native HLS recovery skipped invalid entry: " + e.getMessage());
            }
         }
      } catch (IOException e) {
         log.warn("Native HLS recovery failed: " + e.getMessage());
      }
   }
}
