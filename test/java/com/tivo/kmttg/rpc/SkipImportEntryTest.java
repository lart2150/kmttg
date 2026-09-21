package com.tivo.kmttg.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Stack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tivo.kmttg.main.config;

// The Import skip button: take the cut points somebody already made for this recording - by
// comskip, or by hand in VideoReDo - and put them in the AutoSkip table. It finds the file by
// the recording's own name, and only asks the user when it cannot. These tests stay on the
// side where it finds one, because the other side opens a file chooser.
public class SkipImportEntryTest {

   @TempDir
   Path work;

   private static final long HALF_HOUR = 1800000L;
   private static final String AIRING =
      "tivo:of.ctd.394439967.26-6.terrestrial.2026-08-23-03-30-00.1800";

   private Path tivoDir, mpegDir;
   private String savedProgramDir, savedOutputDir, savedMpegDir, savedFormat;
   private int savedEnabled;
   private Boolean savedMode;
   private LinkedHashMap<String,String> savedTivos;
   private Hashtable<String,String> savedRpc;

   @BeforeEach
   void redirectDirs() throws IOException {
      savedProgramDir = config.programDir;
      savedOutputDir = config.outputDir;
      savedMpegDir = config.mpegDir;
      savedFormat = config.tivoFileNameFormat;
      savedEnabled = config.autoskip_enabled;
      savedMode = config.GUIMODE;
      savedTivos = new LinkedHashMap<String,String>(config.TIVOS);
      savedRpc = new Hashtable<String,String>(config.enableRpc);
      tivoDir = Files.createDirectory(work.resolve("tivo"));
      mpegDir = Files.createDirectory(work.resolve("mpeg"));
      config.programDir = work.toString();
      config.outputDir = tivoDir.toString();
      config.mpegDir = mpegDir.toString();
      config.tivoFileNameFormat = "[title]";
      config.GUIMODE = false;
      // hasEntry, which decides whether the old entry is removed first, is gated on AutoSkip
      // being on and a TiVo being RPC capable - as it is wherever this button is offered
      config.autoskip_enabled = 1;
      config.TIVOS.put("Bolt", "192.168.1.5");
      config.enableRpc.put("Bolt", "1");
   }

   @AfterEach
   void restore() {
      config.programDir = savedProgramDir;
      config.outputDir = savedOutputDir;
      config.mpegDir = savedMpegDir;
      config.tivoFileNameFormat = savedFormat;
      config.autoskip_enabled = savedEnabled;
      config.GUIMODE = savedMode;
      config.TIVOS.clear();
      config.TIVOS.putAll(savedTivos);
      config.enableRpc.clear();
      config.enableRpc.putAll(savedRpc);
   }

   // All three are read unconditionally further down - duration by Long.parseLong - so this
   // is the difference between a message and an exception out of a button handler that has
   // no catch of its own.
   @Test
   void anEntryMissingWhatTheTableIsKeyedOnIsRefused() throws IOException {
      writeCutFile(mpegDir, "Cheers.edl", "0.00 30.00 0\n");
      for (String missing : new String[]{"contentId", "offerId", "duration"}) {
         Hashtable<String,String> e = npl();
         e.remove(missing);
         assertFalse(SkipImport.importEntry("Bolt", e), "missing " + missing);
      }
      assertFalse(Files.exists(work.resolve("AutoSkip.ini")), "nothing should be written");
   }

   // comskip leaves its edl beside the video file, so the usual case never prompts for
   // anything. The entry lands under the airing, with no offset, because the cut points are
   // already on this recording's clock.
   @Test
   void theCutFileBesideTheRecordingIsFoundWithoutPrompting() throws IOException {
      writeCutFile(mpegDir, "Cheers.edl", "0.00 30.00 0\n600.00 660.00 0\n");
      assertTrue(SkipImport.importEntry("Bolt", npl()));

      Stack<Hashtable<String,Long>> cuts = SkipManager.getEntry(AIRING);
      assertEquals(2, cuts.size());
      assertEquals(30000L, cuts.get(0).get("start").longValue());
      assertEquals(600000L, cuts.get(0).get("end").longValue());
      assertEquals(660000L, cuts.get(1).get("start").longValue());
      assertEquals(HALF_HOUR, cuts.get(1).get("end").longValue());
      assertTrue(raw().contains("offset=0"), raw());
   }

   // Both tools may have run on the same recording. The VideoReDo project is the one somebody
   // may have corrected by hand, so it is preferred to comskip's raw output.
   @Test
   void aVideoRedoProjectIsPreferredToAComskipEdl() throws IOException {
      writeCutFile(mpegDir, "Cheers.edl", "0.00 30.00 0\n");
      writeCutFile(mpegDir, "Cheers.VPrj",
         "<VideoReDoProject Version=\"3\">\n<CutList>\n"
         + "<Cut> <CutTimeStart>0</CutTimeStart> <CutTimeEnd>450000000</CutTimeEnd> </Cut>\n"
         + "</CutList>\n</VideoReDoProject>\n");
      assertTrue(SkipImport.importEntry("Bolt", npl()));

      Stack<Hashtable<String,Long>> cuts = SkipManager.getEntry(AIRING);
      assertEquals(1, cuts.size());
      assertEquals(45000L, cuts.get(0).get("start").longValue(), "the VPrj's 45 s, not the edl's 30 s");
   }

   // comskip is routinely re-run with different settings. Every entry lookup takes the first
   // match in the file, so a second import that appended would be ignored in favour of the
   // stale one it was meant to correct.
   @Test
   void importingAgainReplacesTheEntryRatherThanAddingASecond() throws IOException {
      writeCutFile(mpegDir, "Cheers.edl", "0.00 30.00 0\n");
      assertTrue(SkipImport.importEntry("Bolt", npl()));
      writeCutFile(mpegDir, "Cheers.edl", "0.00 45.00 0\n");
      assertTrue(SkipImport.importEntry("Bolt", npl()));

      assertEquals(1, countEntries(raw()), "one entry per airing: " + raw());
      assertEquals(45000L, SkipManager.getEntry(AIRING).get(0).get("start").longValue());
   }

   // The import removes the old entry before writing the new one, so an import that found
   // nothing to write used to leave the recording with no cut points at all - and with a
   // cut-less entry in its place, which still flags the recording as done in My Shows and
   // which the Skip dialog will not list for deletion. Every other importer in kmttg checks
   // the cut points arrived before touching the table.
   @Test
   void aCutFileWithNothingInItLeavesTheExistingEntryAlone() throws IOException {
      writeCutFile(mpegDir, "Cheers.edl", "0.00 30.00 0\n600.00 660.00 0\n");
      assertTrue(SkipImport.importEntry("Bolt", npl()));

      // The user browses to the wrong recording's project file, or comskip found nothing
      writeCutFile(mpegDir, "Cheers.edl", "no cut points here\n");
      assertFalse(SkipImport.importEntry("Bolt", npl()), "nothing was imported");

      assertEquals(1, countEntries(raw()));
      assertEquals(2, SkipManager.getEntry(AIRING).size(), "the good cut points must survive");
      assertTrue(SkipManager.hasEntry(AIRING));
   }

   private void writeCutFile(Path dir, String name, String content) throws IOException {
      Files.write(dir.resolve(name), content.getBytes(StandardCharsets.UTF_8));
   }

   private String raw() throws IOException {
      return new String(Files.readAllBytes(work.resolve("AutoSkip.ini")), StandardCharsets.UTF_8);
   }

   private static int countEntries(String text) {
      int n = 0, i = 0;
      while ((i = text.indexOf("<entry>", i)) >= 0) { n++; i += 7; }
      return n;
   }

   // An NPL row as the table hands one over: the file name template needs the title and the
   // recording time, and everything below needs the ids.
   private static Hashtable<String,String> npl() {
      Hashtable<String,String> e = new Hashtable<String,String>();
      e.put("title", "Cheers");
      e.put("gmt", "1787620000000");
      e.put("duration", "" + HALF_HOUR);
      e.put("contentId", "tivo:ct.20825");
      e.put("offerId", AIRING);
      return e;
   }
}
