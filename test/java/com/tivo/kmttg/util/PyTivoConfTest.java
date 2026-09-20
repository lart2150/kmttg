package com.tivo.kmttg.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Hashtable;
import java.util.Stack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tivo.kmttg.main.config;

// Covers the two pure pieces of util.pyTivo: the pyTivo.conf reader that
// decides which shares kmttg can push to, and the Windows volume fixup it
// uses on every path it reads. Everything else in pyTivo talks to a running
// pyTivo server. parsePyTivoConf also adopts the tivo.com credentials out of
// the file, so those are saved and restored here along with config.OS.
public class PyTivoConfTest {

   @TempDir
   Path tmp;

   private String prevOS, prevUser, prevPassword;
   private Boolean prevGuiMode;

   @BeforeEach
   public void saveConfig() {
      prevOS = config.OS;
      prevUser = config.getTivoUsername();
      prevPassword = config.getTivoPassword();
      prevGuiMode = config.GUIMODE;
      // Nothing set, so the parser is free to adopt what the conf file carries
      config.setTivoUsername("");
      config.setTivoPassword("");
      // Off windows the paths go through the parser untouched, which keeps the
      // expected values below plain; the volume fixup is covered on its own
      config.OS = "other";
      // The failure paths call log.error(), which posts to the Swing gui when this is on
      config.GUIMODE = false;
   }

   @AfterEach
   public void restoreConfig() {
      config.OS = prevOS;
      config.GUIMODE = prevGuiMode;
      config.setTivoUsername(prevUser == null ? "" : prevUser);
      config.setTivoPassword(prevPassword == null ? "" : prevPassword);
   }

   @Test
   public void parseReturnsEveryVideoShareInFileOrder() throws IOException {
      String conf = write(
         "[Server]",
         "tivo_username = me@example.com",
         "tivo_password = hunter2",
         "",
         "[Movies]",
         "type = video",
         "path = " + dir("movies"),
         "",
         "[Shows]",
         "type = video",
         "path = " + dir("shows"));

      Stack<Hashtable<String,String>> shares = pyTivo.parsePyTivoConf(conf);

      assertNotNull(shares);
      assertEquals(2, shares.size());
      assertEquals("Movies", shares.get(0).get("share"));
      assertEquals(dir("movies"), shares.get(0).get("path"));
      assertEquals("Shows", shares.get(1).get("share"));
      assertEquals(dir("shows"), shares.get(1).get("path"));
   }

   // A share is only usable if it has both a name and a path, so a video
   // section missing either one is dropped rather than half-populated
   @Test
   public void parseDropsSharesThatAreNotVideoOrHaveNoPath() throws IOException {
      String conf = write(
         "[Server]",
         "tivo_username = me@example.com",
         "tivo_password = hunter2",
         "",
         "[Photos]",
         "type = photos",
         "path = " + dir("photos"),
         "",
         "[NoPath]",
         "type = video",
         "",
         "[Movies]",
         "type = video",
         "path = " + dir("movies"));

      Stack<Hashtable<String,String>> shares = pyTivo.parsePyTivoConf(conf);

      assertEquals(1, shares.size());
      assertEquals("Movies", shares.get(0).get("share"));
   }

   // An empty path indexed charAt(-1), and the whole-file catch turned that into a null
   // return - one blank line in the conf file used to cost every share in it
   @Test
   public void aShareWithABlankPathCostsOnlyItself() throws IOException {
      String conf = write(
         "[Server]",
         "tivo_username = me@example.com",
         "tivo_password = hunter2",
         "",
         "[Blank]",
         "type = video",
         "path = ",
         "",
         "[Movies]",
         "type = video",
         "path = " + dir("movies"));

      Stack<Hashtable<String,String>> shares = pyTivo.parsePyTivoConf(conf);

      assertNotNull(shares, "a blank path took the whole file down");
      assertEquals(1, shares.size());
      assertEquals("Movies", shares.get(0).get("share"));
      assertEquals(dir("movies"), shares.get(0).get("path"));
   }

   @Test
   public void parseSkipsCommentsAndBlankLinesAndIgnoresIndentation() throws IOException {
      String conf = write(
         "# pyTivo configuration",
         "",
         "[Server]",
         "   tivo_username = me@example.com",
         "   tivo_password = hunter2",
         "",
         "# the only share",
         "[Movies]",
         "   type = video",
         "   path = " + dir("movies"));

      Stack<Hashtable<String,String>> shares = pyTivo.parsePyTivoConf(conf);

      assertEquals(1, shares.size());
      assertEquals(dir("movies"), shares.get(0).get("path"));
   }

   // kmttg appends its own separator when it builds a push path, so a
   // trailing one in the conf file has to come off here
   @Test
   public void parseStripsATrailingFileSeparatorFromThePath() throws IOException {
      String conf = write(
         "[Server]",
         "tivo_username = me@example.com",
         "tivo_password = hunter2",
         "",
         "[Movies]",
         "type = video",
         "path = " + dir("movies") + File.separator);

      Stack<Hashtable<String,String>> shares = pyTivo.parsePyTivoConf(conf);

      assertEquals(dir("movies"), shares.get(0).get("path"));
   }

   @Test
   public void parseAdoptsTheCredentialsWhenKmttgHasNone() throws IOException {
      String conf = write(
         "[Server]",
         "tivo_username = me@example.com",
         "tivo_password = hunter2",
         "",
         "[Movies]",
         "type = video",
         "path = " + dir("movies"));

      assertNotNull(pyTivo.parsePyTivoConf(conf));
      assertEquals("me@example.com", config.getTivoUsername());
      assertEquals("hunter2", config.getTivoPassword());
   }

   @Test
   public void parseLeavesCredentialsKmttgAlreadyHasAlone() throws IOException {
      config.setTivoUsername("mine@example.com");
      config.setTivoPassword("mypassword");
      String conf = write(
         "[Server]",
         "tivo_username = other@example.com",
         "tivo_password = otherpassword",
         "",
         "[Movies]",
         "type = video",
         "path = " + dir("movies"));

      assertNotNull(pyTivo.parsePyTivoConf(conf));
      assertEquals("mine@example.com", config.getTivoUsername());
      assertEquals("mypassword", config.getTivoPassword());
   }

   // Pushes need both, so a conf without them is refused outright - the
   // shares it did find are thrown away with it
   @Test
   public void parseReturnsNullWhenACredentialIsMissing() throws IOException {
      String conf = write(
         "[Server]",
         "tivo_username = me@example.com",
         "",
         "[Movies]",
         "type = video",
         "path = " + dir("movies"));

      assertNull(pyTivo.parsePyTivoConf(conf));
   }

   @Test
   public void parseReturnsNullForAMissingConfFile() {
      assertNull(pyTivo.parsePyTivoConf(tmp.resolve("nothere.conf").toString()));
   }

   @Test
   public void parseIgnoresSettingsThatArriveBeforeAnySection() throws IOException {
      String conf = write(
         "type = video",
         "path = " + dir("movies"),
         "",
         "[Server]",
         "tivo_username = me@example.com",
         "tivo_password = hunter2");

      Stack<Hashtable<String,String>> shares = pyTivo.parsePyTivoConf(conf);

      assertNotNull(shares);
      assertTrue(shares.isEmpty(), "a share was invented out of the pre-section lines");
   }

   // Every path read out of the conf file goes through the volume fixup, so a
   // share configured as C:\ comes back as c:\ for the path comparisons kmttg
   // makes against it later
   @Test
   public void parseLowercasesTheVolumeOfEachSharePathOnWindows() throws IOException {
      config.OS = "windows";
      String conf = write(
         "[Server]",
         "tivo_username = me@example.com",
         "tivo_password = hunter2",
         "",
         "[Movies]",
         "type = video",
         "path = C:\\Videos\\Movies");

      Stack<Hashtable<String,String>> shares = pyTivo.parsePyTivoConf(conf);

      assertEquals("c:\\Videos\\Movies", shares.get(0).get("path"));
   }

   @Test
   public void lowerCaseVolumeLowercasesTheDriveLetterOnWindows() {
      config.OS = "windows";
      assertEquals("c:\\Videos\\Show.mpg", pyTivo.lowerCaseVolume("C:\\Videos\\Show.mpg"));
      assertEquals("c:\\Videos\\Show.mpg", pyTivo.lowerCaseVolume("c:\\Videos\\Show.mpg"));
   }

   // Only the volume is touched; the rest of the path keeps its case, which
   // matters because these paths are handed back to pyTivo verbatim
   @Test
   public void lowerCaseVolumeLeavesEverythingWithoutAVolumeAlone() {
      config.OS = "windows";
      assertEquals("\\\\server\\Videos", pyTivo.lowerCaseVolume("\\\\server\\Videos"));
      assertEquals("/Videos/Show.mpg", pyTivo.lowerCaseVolume("/Videos/Show.mpg"));
      assertEquals("", pyTivo.lowerCaseVolume(""));
   }

   @Test
   public void lowerCaseVolumeIsANoOpOffWindows() {
      config.OS = "linux";
      assertEquals("C:\\Videos\\Show.mpg", pyTivo.lowerCaseVolume("C:\\Videos\\Show.mpg"));
   }

   private String dir(String name) {
      return tmp.resolve(name).toString();
   }

   private String write(String... lines) throws IOException {
      Path conf = tmp.resolve("pyTivo.conf");
      Files.write(conf, String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
      return conf.toString();
   }
}
