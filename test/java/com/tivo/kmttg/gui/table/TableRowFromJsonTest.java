package com.tivo.kmttg.gui.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.Hashtable;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.gui;
import com.tivo.kmttg.gui.remote.remotegui;
import com.tivo.kmttg.gui.remote.search;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.rpc.Fixtures;
import com.tivo.kmttg.rpc.ReplayRemote;

/**
 * Tests the table row builders that turn a TiVo RPC response into what the GUI
 * actually displays. Each {@code Tabentry} reads specific keys out of the JSON
 * and swallows anything it does not understand (its constructor catches and
 * logs), so a response the TiVo shapes differently does not throw - it quietly
 * produces a blank or half filled row. These drive every row of the captured
 * fixtures through the real constructors and assert the cells came out
 * populated, which is the only way that failure mode shows up.
 *
 * The fixtures are the same sanitized captures the Remote replay tests use.
 */
public class TableRowFromJsonTest {

   private gui prevGui;
   private Boolean prevMode;
   private Hashtable<String,java.awt.Image> prevImages;

   @BeforeEach
   public void installGuiStub() {
      prevGui = config.gui;
      prevMode = config.GUIMODE;
      prevImages = gui.Images;
      // Several row builders call back into the gui singleton (flagIfInTodo)
      // and read the shared image table; a mock and an empty table keep them on
      // their normal path without a Swing frame.
      gui guiStub = mock(gui.class);
      guiStub.remote_gui = mock(remotegui.class);
      config.gui = guiStub;
      config.GUIMODE = false;
      gui.Images = new Hashtable<String,java.awt.Image>();
   }

   @AfterEach
   public void restoreGui() {
      config.gui = prevGui;
      config.GUIMODE = prevMode;
      gui.Images = prevImages;
   }

   // ---- ToDo rows ---------------------------------------------------------

   @Test
   public void todoRows_haveDateShowChannelAndDuration() throws Exception {
      JSONArray todo = Fixtures.load("todo.json");
      assertTrue(todo.length() > 0, "expected ToDo entries");

      for (int i = 0; i < todo.length(); i++) {
         todoTable.Tabentry row = new todoTable.Tabentry(todo.getJSONObject(i));

         assertFalse(row.getSHOW().trim().isEmpty(), "blank SHOW cell at row " + i);
         assertFalse(row.getCHANNEL().trim().isEmpty(), "blank CHANNEL cell at row " + i);
         assertNotNull(row.getDATE(), "null DATE cell at row " + i);
         // scheduledStartTime is what the ToDo response carries; a row whose
         // date did not parse sorts as "0" and shows up blank in the table.
         assertFalse("0".equals(row.getDATE().sortable), "unparsed DATE at row " + i);
         assertNotNull(row.getDUR(), "null DUR cell at row " + i);
         assertTrue(row.getDUR().sortable > 0, "non-positive duration at row " + i);
      }
   }

   @Test
   public void todoRows_pickAnIconFromTheSubscriptionType() throws Exception {
      // The icon column is driven by subscriptionIdentifier[0].subscriptionType,
      // which the ToDo capture carries in three flavours.
      JSONArray todo = Fixtures.load("todo.json");
      boolean sawSeasonPass = false, sawSingle = false;

      for (int i = 0; i < todo.length(); i++) {
         JSONObject entry = todo.getJSONObject(i);
         if (! entry.has("subscriptionIdentifier"))
            continue;
         String type = entry.getJSONArray("subscriptionIdentifier").getJSONObject(0)
               .getString("subscriptionType");
         String icon = new todoTable.Tabentry(entry).getIMAGE().imageName;

         if (type.equals("seasonPass") || type.equals("repeatingTimeChannel")) {
            assertEquals("image-season-pass", icon, "wrong icon for " + type + " at row " + i);
            sawSeasonPass = true;
         } else if (type.startsWith("single")) {
            assertEquals("image-single-explicit-record", icon, "wrong icon for " + type + " at row " + i);
            sawSingle = true;
         }
      }
      assertTrue(sawSeasonPass, "no one pass rows in the ToDo fixture");
      assertTrue(sawSingle, "no single recording rows in the ToDo fixture");
   }

   // ---- Deleted rows ------------------------------------------------------

   @Test
   public void deletedRows_haveBothTheRecordedAndDeletedDates() throws Exception {
      JSONArray deleted = Fixtures.load("deleted.json");
      assertTrue(deleted.length() > 0, "expected deleted entries");

      for (int i = 0; i < deleted.length(); i++) {
         deletedTable.Tabentry row = new deletedTable.Tabentry(deleted.getJSONObject(i));

         assertFalse(row.getSHOW().trim().isEmpty(), "blank SHOW cell at row " + i);
         assertFalse(row.getCHANNEL().trim().isEmpty(), "blank CHANNEL cell at row " + i);
         // The two date columns come from different keys - deletionTime and the
         // recording's own start - so a shape change tends to blank just one.
         assertFalse("0".equals(row.getDELETED().sortable), "unparsed DELETED date at row " + i);
         assertFalse("0".equals(row.getRECORDED().sortable), "unparsed RECORDED date at row " + i);
         assertTrue(row.getDUR().sortable > 0, "non-positive duration at row " + i);
      }
   }

   // ---- Cancelled / Won't record rows -------------------------------------

   @Test
   public void cancelledRows_haveDateShowChannelAndDuration() throws Exception {
      JSONArray cancelled = Fixtures.load("cancelled.json");
      assertTrue(cancelled.length() > 0, "expected cancelled entries");

      for (int i = 0; i < cancelled.length(); i++) {
         cancelledTable.Tabentry row = new cancelledTable.Tabentry(cancelled.getJSONObject(i));

         assertFalse(row.getSHOW().trim().isEmpty(), "blank SHOW cell at row " + i);
         assertFalse(row.getCHANNEL().trim().isEmpty(), "blank CHANNEL cell at row " + i);
         assertFalse("0".equals(row.getDATE().sortable), "unparsed DATE at row " + i);
         assertTrue(row.getDUR().sortable > 0, "non-positive duration at row " + i);
      }
   }

   // ---- Guide rows --------------------------------------------------------

   @Test
   public void guideRows_needStartTimeAndDuration() throws Exception {
      // Unlike the other tables this one reads startTime/duration without
      // checking has() first, so every guide offer has to carry both.
      for (String fixture : new String[] { "guide_2-1.json", "guide_5-1.json" }) {
         JSONArray offers = Fixtures.load(fixture);
         assertTrue(offers.length() > 0, "expected listings in " + fixture);

         for (int i = 0; i < offers.length(); i++) {
            guideTable.Tabentry row = new guideTable.Tabentry(offers.getJSONObject(i));

            assertFalse(row.getSHOW().trim().isEmpty(), "blank SHOW cell at " + i + " in " + fixture);
            assertNotNull(row.getDATE(), "null DATE cell at " + i + " in " + fixture);
            assertFalse("0".equals(row.getDATE().sortable), "unparsed DATE at " + i + " in " + fixture);
            assertNotNull(row.getDUR(), "null DUR cell at " + i + " in " + fixture);
            assertTrue(row.getDUR().sortable > 0, "non-positive duration at " + i + " in " + fixture);
         }
      }
   }

   // ---- Channel rows ------------------------------------------------------

   @Test
   public void channelRows_parseTheDashedChannelNumber() throws Exception {
      // Channel numbers arrive as "2-1"; the sort key is that read as 2.1, and
      // a number the parse chokes on takes the whole row down.
      JSONArray channels = Fixtures.load("channels.json");
      assertTrue(channels.length() > 0, "expected channels");

      for (int i = 0; i < channels.length(); i++) {
         JSONObject entry = channels.getJSONObject(i);
         channelsTable.Tabentry row = new channelsTable.Tabentry(entry);

         assertNotNull(row.getNUMBER(), "null NUMBER cell at row " + i);
         assertEquals(entry.getString("channelNumber"), row.getNUMBER().display,
            "NUMBER cell should display the channel number verbatim at row " + i);
         assertTrue(row.getNUMBER().sortable > 0, "unsortable channel number at row " + i);
         assertNotNull(row.getNAME(), "null NAME cell at row " + i);
         assertFalse(row.getNAME().toString().trim().isEmpty(), "blank NAME cell at row " + i);
         assertEquals(entry.getBoolean("isReceived"), row.getRECEIVED(),
            "RECEIVED checkbox out of step with the lineup at row " + i);
      }
   }

   // ---- Thumbs rows -------------------------------------------------------

   @Test
   public void thumbsRows_haveTypeShowAndRating() throws Exception {
      JSONArray thumbs = Fixtures.load("thumbs.json");
      assertTrue(thumbs.length() > 0, "expected thumbs entries");

      for (int i = 0; i < thumbs.length(); i++) {
         thumbsTable.Tabentry row = new thumbsTable.Tabentry(thumbs.getJSONObject(i));

         assertFalse(row.getTYPE().trim().isEmpty(), "blank TYPE cell at row " + i);
         assertNotNull(row.getSHOW(), "null SHOW cell at row " + i);
         assertFalse(row.getSHOW().toString().trim().isEmpty(), "blank SHOW cell at row " + i);
         int rating = Integer.parseInt(row.getRATING());
         assertTrue(rating >= -3 && rating <= 3, "RATING out of range at row " + i + ": " + rating);
      }
   }

   // ---- One pass rows -----------------------------------------------------

   @Test
   public void onePassRows_fillEveryColumn() throws Exception {
      JSONArray passes = Fixtures.load("seasonpasses.json");
      assertTrue(passes.length() > 0, "expected one passes");

      for (int i = 0; i < passes.length(); i++) {
         JSONObject pass = passes.getJSONObject(i);
         spTable.Tabentry row = new spTable.Tabentry(pass, i);

         assertEquals(i, row.getPRI().sortable, "PRI cell should be the priority passed in, row " + i);
         assertFalse(row.getSHOW().trim().isEmpty(), "blank SHOW cell at row " + i);
         assertFalse(row.getINCLUDE().trim().isEmpty(), "blank INCLUDE cell at row " + i);
         assertEquals(pass.getString("showStatus"), row.getRECORD(), "wrong RECORD cell at row " + i);
         assertEquals(pass.getString("keepBehavior"), row.getKEEP(), "wrong KEEP cell at row " + i);
         // Paddings are shown in minutes, the JSON carries seconds.
         assertEquals("" + pass.getInt("startTimePadding") / 60, row.getSTART(),
            "wrong START padding at row " + i);
         assertEquals("" + pass.getInt("endTimePadding") / 60, row.getEND(),
            "wrong END padding at row " + i);
         assertEquals("" + pass.getInt("maxRecordings"), row.getNUM(), "wrong NUM cell at row " + i);
      }
   }

   @Test
   public void onePassRows_showChannelOrAllChannels() throws Exception {
      // A pass restricted to one channel shows "<number>=<callSign>"; an
      // unrestricted linear pass shows "All Channels".
      JSONArray passes = Fixtures.load("commands_seasonpasses_job.json")
            .getJSONObject(0).getJSONObject("response").getJSONArray("subscription");
      boolean sawRestricted = false, sawAllChannels = false;

      for (int i = 0; i < passes.length(); i++) {
         JSONObject pass = passes.getJSONObject(i);
         String channel = new spTable.Tabentry(pass, i).getCHANNEL();
         JSONObject source = pass.getJSONObject("idSetSource");

         if (source.has("channel")) {
            JSONObject c = source.getJSONObject("channel");
            assertEquals(c.getString("channelNumber") + "=" + c.getString("callSign"), channel,
               "wrong CHANNEL cell at row " + i);
            sawRestricted = true;
         } else if ("linear".equals(source.opt("consumptionSource"))) {
            assertEquals("All Channels", channel, "wrong CHANNEL cell at row " + i);
            sawAllChannels = true;
         }
      }
      assertTrue(sawRestricted, "no channel-restricted pass in the fixture");
      assertTrue(sawAllChannels, "no all-channels pass in the fixture");
   }

   @Test
   public void onePassRows_appendTheUpcomingCountToTheTitle() throws Exception {
      // SeasonPasses(job) grafts __upcoming onto each pass, and the SHOW cell
      // carries that count in parentheses.
      config.GUIMODE = false;
      JSONArray passes = new ReplayRemote(Fixtures.load("commands_seasonpasses_job.json"))
            .SeasonPasses(new jobData());

      boolean sawCount = false;
      for (int i = 0; i < passes.length(); i++) {
         JSONObject pass = passes.getJSONObject(i);
         String show = new spTable.Tabentry(pass, i).getSHOW();
         if (pass.has("__upcoming")) {
            assertEquals(pass.getString("title") + " (" + pass.getJSONArray("__upcoming").length() + ")",
               show, "wrong upcoming count in SHOW cell at row " + i);
            sawCount = true;
         } else {
            assertEquals(pass.getString("title"), show, "unexpected suffix in SHOW cell at row " + i);
         }
      }
      assertTrue(sawCount, "expected at least one pass with upcoming recordings");
   }

   // ---- Search result rows ------------------------------------------------

   @Test
   public void searchRows_collapseMultipleAirtimesIntoAFolder() throws Exception {
      // searchKeywords hands the table collections of offers: more than one
      // airing becomes a folder row titled "<show> (n)", a single airing is
      // shown as the offer itself. Build the input the way the search tab does.
      search searchTab = mock(search.class);
      searchTab.search_type = new JComboBox<>(new String[] { "keywords" });
      searchTab.includeFree = new JCheckBox();
      searchTab.includePaid = new JCheckBox();
      config.gui.remote_gui.search_tab = searchTab;
      JSONArray collections = new ReplayRemote(Fixtures.load("commands_searchcheer.json"))
            .searchKeywords("cheer", null, 1000);
      assertTrue(collections.length() > 0, "expected search results to build rows from");

      boolean sawFolder = false, sawSingle = false;
      for (int i = 0; i < collections.length(); i++) {
         JSONObject c = collections.getJSONObject(i);
         int airings = c.getJSONArray("entries").length();
         searchTable.Tabentry row = new searchTable.Tabentry(c, true);

         assertFalse(row.getSHOW().trim().isEmpty(), "blank SHOW cell at row " + i);
         assertNotNull(row.getDATE(), "null DATE cell at row " + i);
         assertFalse("0".equals(row.getDATE().sortable), "unparsed DATE at row " + i);
         if (airings > 1) {
            assertTrue(row.getSHOW().endsWith(" (" + airings + ")"),
               "folder row should carry its airing count, got " + row.getSHOW());
            assertEquals("folder", row.getIMAGE().imageName, "folder row missing its icon at " + i);
            sawFolder = true;
         } else {
            // A single airing is unwrapped, so the row shows the offer's own
            // title and the channel it airs on.
            assertTrue(row.getSHOW().contains(c.getString("title")),
               "single row lost its title at " + i + ": " + row.getSHOW());
            assertFalse(row.getCHANNEL().trim().isEmpty(), "blank CHANNEL cell at row " + i);
            sawSingle = true;
         }
      }
      assertTrue(sawFolder, "no multi-airing collection in the search fixture");
      assertTrue(sawSingle, "no single-airing collection in the search fixture");
   }

   // ---- The show summary printed under every table -------------------------

   @Test
   public void showSummary_combinesDateChannelAndTitle() throws Exception {
      // Guide offers carry startTime, which is the key makeShowSummary needs
      // for its date half; ToDo entries do not, and are expected to lose it.
      JSONArray offers = Fixtures.load("guide_2-1.json");
      JSONObject offer = offers.getJSONObject(0);

      String summary = TableUtil.makeShowSummary(offer);

      assertTrue(summary.contains(offer.getString("title")), "summary lost the title: " + summary);
      assertTrue(summary.contains(offer.getJSONObject("channel").getString("callSign")),
         "summary lost the channel: " + summary);
      assertFalse(summary.trim().startsWith(offer.getString("title")),
         "summary should lead with the air date: " + summary);
   }
}
