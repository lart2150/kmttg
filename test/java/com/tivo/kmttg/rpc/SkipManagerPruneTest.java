package com.tivo.kmttg.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Stack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tivo.kmttg.main.config;

// Pruning is the only thing that ever takes a row out of AutoSkip.ini on its own, driven by a
// Now Playing list that was just fetched. It deletes, so what it declines to delete matters as
// much as what it does: another TiVo's rows, and a list that came back empty.
public class SkipManagerPruneTest {

   @TempDir
   Path work;

   private static final String AIRING_A =
      "tivo:of.ctd.394439967.26-6.terrestrial.2026-08-23-03-30-00.1800";
   private static final String AIRING_B =
      "tivo:of.ctd.366821959.48-3.terrestrial.2026-08-23-03-30-00.1800";
   private static final String AIRING_C =
      "tivo:of.ctd.394439967.26-6.terrestrial.2026-08-20-03-00-00.1800";

   private String savedProgramDir;
   private int savedEnabled;
   private Boolean savedMode;
   private LinkedHashMap<String,String> savedTivos;
   private Hashtable<String,String> savedRpc;

   @BeforeEach
   void redirectProgramDir() {
      savedProgramDir = config.programDir;
      savedEnabled = config.autoskip_enabled;
      savedMode = config.GUIMODE;
      savedTivos = new LinkedHashMap<String,String>(config.TIVOS);
      savedRpc = new Hashtable<String,String>(config.enableRpc);
      config.programDir = work.toString();
      config.GUIMODE = false;
      // removeEntry is reached through hasEntry, which wants AutoSkip on and an RPC TiVo
      config.autoskip_enabled = 1;
      config.TIVOS.put("Bolt", "192.168.1.5");
      config.enableRpc.put("Bolt", "1");
   }

   @AfterEach
   void restore() {
      config.programDir = savedProgramDir;
      config.autoskip_enabled = savedEnabled;
      config.GUIMODE = savedMode;
      config.TIVOS.clear();
      config.TIVOS.putAll(savedTivos);
      config.enableRpc.clear();
      config.enableRpc.putAll(savedRpc);
   }

   // Rows are pruned against the Now Playing list of the TiVo that wrote them. The other
   // TiVo's recordings are not in this list and are not gone - they were never asked about.
   @Test
   void pruningDropsOnlyTheRecordingsThisTiVoNoLongerHas() {
      save(AIRING_A, "Bolt", "Cheers");     // deleted since it was written
      save(AIRING_B, "Bolt", "Frasier");    // still in My Shows
      save(AIRING_C, "Roamio", "Wings");    // another TiVo's row

      SkipManager.pruneEntries("Bolt", npl(AIRING_B));

      assertFalse(SkipManager.hasEntry(AIRING_A), "the deleted recording's row goes");
      assertTrue(SkipManager.hasEntry(AIRING_B));
      assertTrue(SkipManager.hasEntry(AIRING_C), "another TiVo's row is not this TiVo's business");
   }

   // An RPC call that came back empty, or a TiVo that was busy. Reading that as "every
   // recording was deleted" would throw away the whole table.
   @Test
   void anEmptyOrAbsentNowPlayingListIsNeverReadAsEverythingDeleted() {
      save(AIRING_A, "Bolt", "Cheers");
      save(AIRING_B, "Bolt", "Frasier");

      SkipManager.pruneEntries("Bolt", new Stack<Hashtable<String,String>>());
      SkipManager.pruneEntries("Bolt", null);

      assertTrue(SkipManager.hasEntry(AIRING_A));
      assertTrue(SkipManager.hasEntry(AIRING_B));
   }

   // The same episode recorded twice off two stations shares a contentId, so only the airing
   // can say which of the two rows belongs to the recording still in My Shows.
   @Test
   void oneAiringOfAnEpisodeCanBePrunedWhileTheOtherStays() {
      save(AIRING_A, "Bolt", "Cheers");
      save(AIRING_C, "Bolt", "Cheers");

      SkipManager.pruneEntries("Bolt", npl(AIRING_C));

      assertFalse(SkipManager.hasEntry(AIRING_A));
      assertTrue(SkipManager.hasEntry(AIRING_C));
      assertEquals(900000L, SkipManager.getEntry(AIRING_C).get(0).get("end").longValue());
   }

   // Pruning walks the same list the Skip dialog shows, which leaves out entries with no cut
   // pairs - so a row a failed import left behind outlives the recording it was written for,
   // keeps its marker in My Shows, and cannot be reached from the dialog either.
   @Test
   void anEntryWithNoCutPairsIsInvisibleToPruning() throws Exception {
      SkipManager.saveEntry("tivo:ct.1", AIRING_A, 0L, "Ghost", "Bolt",
         new Stack<Hashtable<String,Long>>());
      save(AIRING_B, "Bolt", "Frasier");

      SkipManager.pruneEntries("Bolt", npl(AIRING_B));

      assertTrue(SkipManager.hasEntry(AIRING_A), "nothing can prune it");
      assertEquals(1, SkipManager.getEntries().length(), "and nothing lists it");
      assertEquals(AIRING_B, SkipManager.getEntries().getJSONObject(0).getString("offerId"));
   }

   private static void save(String offerId, String tivoName, String title) {
      Stack<Hashtable<String,Long>> cuts = new Stack<Hashtable<String,Long>>();
      Hashtable<String,Long> h = new Hashtable<String,Long>();
      h.put("start", 0L);
      h.put("end", 900000L);
      cuts.push(h);
      SkipManager.saveEntry("tivo:ct.20825", offerId, 0L, title, tivoName, cuts);
   }

   // A Now Playing list holding just these airings
   private static Stack<Hashtable<String,String>> npl(String... offerIds) {
      Stack<Hashtable<String,String>> stack = new Stack<Hashtable<String,String>>();
      for (String offerId : offerIds) {
         Hashtable<String,String> e = new Hashtable<String,String>();
         e.put("offerId", offerId);
         stack.push(e);
      }
      return stack;
   }
}
