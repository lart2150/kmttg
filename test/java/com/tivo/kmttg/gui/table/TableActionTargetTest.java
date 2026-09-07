package com.tivo.kmttg.gui.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Stack;

import javax.swing.JLabel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.dialog.ShowDetails;
import com.tivo.kmttg.gui.gui;
import com.tivo.kmttg.gui.remote.deleted;
import com.tivo.kmttg.gui.remote.remotegui;
import com.tivo.kmttg.gui.swing.TreeTable.TreeItem;
import com.tivo.kmttg.gui.tivoTab;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Fixtures;

/**
 * Pins down what a table action operates on once it has left the click that
 * started it. Both tables hand an action row #s, which stop meaning anything
 * as soon as the user re-sorts or filters, or - on NPL - as soon as stopping an
 * in progress recording rebuilds the table. The actions have to resolve the
 * selection to shows up front and address the table by identity after that.
 */
public class TableActionTargetTest {

   private gui prevGui;
   private Boolean prevMode;
   private Hashtable<String,java.awt.Image> prevImages;
   private String prevRpc;

   @BeforeEach
   public void installGuiStub() {
      prevGui = config.gui;
      prevMode = config.GUIMODE;
      prevImages = gui.Images;
      prevRpc = config.enableRpc.get(TIVO);
      // The tables report counts through the remote gui and print the selected
      // show through the details dialog; mocks keep them on their normal path
      // without a frame.
      gui guiStub = mock(gui.class);
      guiStub.remote_gui = mock(remotegui.class);
      guiStub.show_details = mock(ShowDetails.class);
      guiStub.remote_gui.deleted_tab = mock(deleted.class);
      guiStub.remote_gui.deleted_tab.label = new JLabel();
      config.gui = guiStub;
      config.GUIMODE = false;
      gui.Images = new Hashtable<String,java.awt.Image>();
      config.enableRpc.put(TIVO, "1");
   }

   @AfterEach
   public void restoreGui() {
      config.gui = prevGui;
      config.GUIMODE = prevMode;
      gui.Images = prevImages;
      if (prevRpc == null)
         config.enableRpc.remove(TIVO);
      else
         config.enableRpc.put(TIVO, prevRpc);
   }

   private static final String TIVO = "TestTiVo";

   // ---- Deleted tab -------------------------------------------------------

   private deletedTable deletedTab() throws Exception {
      deletedTable tab = new deletedTable();
      tab.AddRows(TIVO, Fixtures.load("deleted.json"));
      assertTrue(tab.MODEL.size() > 10, "expected a full deleted list to work with");
      return tab;
   }

   private static String recordingId(JSONObject json) throws Exception {
      return json.getString("recordingId");
   }

   // Titles currently in the table, top to bottom
   private static Stack<String> titles(deletedTable tab) {
      Stack<String> o = new Stack<String>();
      for (int row = 0; row < tab.MODEL.size(); ++row)
         o.add(tab.MODEL.getRow(row).getSHOW());
      return o;
   }

   @Test
   public void deletedSelection_isResolvedToShowsNotRowNumbers() throws Exception {
      deletedTable tab = deletedTab();
      tab.TABLE.setRowSelectionInterval(3, 5);
      Stack<JSONObject> selected = tab.selectedJson();
      assertEquals(3, selected.size());

      // Re-sort the way a header click would. This is the state the old code
      // resolved its row #s in, one round trip late.
      tab.MODEL.setDefaultSort("SHOW", true);
      tab.MODEL.sort();

      assertNotEquals(recordingId(selected.get(0)), recordingId(tab.GetRowData(3)),
         "fixture rows 3-5 did not move on re-sort, so this proves nothing");
      for (int i = 0; i < selected.size(); ++i)
         assertTrue(tab.findRow(recordingId(selected.get(i))) >= 0,
            "selected show is no longer findable in the table");
   }

   @Test
   public void deletedRemoveEntry_dropsTheSelectedShowAfterAResort() throws Exception {
      deletedTable tab = deletedTab();
      tab.TABLE.setRowSelectionInterval(3, 5);
      Stack<JSONObject> selected = tab.selectedJson();
      int total = tab.MODEL.size();

      tab.MODEL.setDefaultSort("CHANNEL", true);
      tab.MODEL.sort();
      for (int i = 0; i < selected.size(); ++i)
         tab.removeEntry(TIVO, recordingId(selected.get(i)));

      assertEquals(total - selected.size(), tab.MODEL.size(), "wrong number of rows removed");
      for (int i = 0; i < selected.size(); ++i) {
         String id = recordingId(selected.get(i));
         assertEquals(-1, tab.findRow(id), "deleted show is still in the table");
         assertFalse(cached(tab, id), "deleted show is still in the cached list");
      }
   }

   @Test
   public void deletedRemoveEntry_dropsAShowTheFilterIsHiding() throws Exception {
      // Same race, filter flavour: the show being deleted can be filtered out
      // of the table before the TiVo answers, and it still has to go.
      deletedTable tab = deletedTab();
      tab.TABLE.setRowSelectionInterval(0, 0);
      String id = recordingId(tab.selectedJson().get(0));

      tab.setFilter("no such show");
      assertEquals(0, tab.MODEL.size(), "filter did not empty the table");
      tab.removeEntry(TIVO, id);

      tab.setFilter("");
      assertEquals(-1, tab.findRow(id), "deleted show came back when the filter was cleared");
      assertFalse(cached(tab, id), "deleted show is still in the cached list");
   }

   @Test
   public void deletedRemoveEntry_leavesTheOtherShowsAlone() throws Exception {
      deletedTable tab = deletedTab();
      tab.TABLE.setRowSelectionInterval(7, 7);
      String id = recordingId(tab.selectedJson().get(0));
      Stack<String> before = titles(tab);
      String removed = tab.MODEL.getRow(7).getSHOW();

      tab.removeEntry(TIVO, id);

      before.remove(7);
      assertEquals(before, titles(tab), "removing row 7 disturbed the rest of the table");
      assertTrue(removed.length() > 0);
   }

   @Test
   public void deletedFilter_showsNothingForATivoNotFetchedYet() throws Exception {
      // Switching to a TiVo with no list fetched clears the table. Typing in
      // the filter must not bring the previous TiVo's shows back, because the
      // buttons act on the TiVo the combo box now names.
      deletedTable tab = deletedTab();
      tab.clear();
      tab.setFilter("ghosts");
      assertTrue(tab.MODEL.size() > 0,
         "the table refills from whichever TiVo it last displayed - that is the trap");

      tab.clear();
      tab.setCurrentTivo("OtherTiVo");
      tab.setFilter("elsbeth");

      assertEquals(0, tab.MODEL.size(), "the previous TiVo's shows came back under another TiVo");
   }

   @Test
   public void deletedRemoveEntry_leavesAnotherTivosRowsAlone() throws Exception {
      // recordingIds are per TiVo, so an id deleted on A can name a different
      // show on B. Switching the combo mid-delete must not take out B's row.
      deletedTable tab = deletedTab();
      tab.TABLE.setRowSelectionInterval(2, 2);
      String id = recordingId(tab.selectedJson().get(0));
      int total = tab.MODEL.size();

      // The user switches to another TiVo while the worker is still going. Its
      // list happens to be the same capture, so the id is present here too.
      tab.AddRows("OtherTiVo", Fixtures.load("deleted.json"));
      tab.removeEntry(TIVO, id);

      assertEquals(total, tab.MODEL.size(), "the other TiVo's row was removed instead");
      assertTrue(tab.findRow(id) >= 0, "the other TiVo's show was dropped from the table");
      assertFalse(cached(tab, id), "the deleted show is still in the acting TiVo's cached list");
   }

   private static boolean cached(deletedTable tab, String recordingId) throws Exception {
      JSONArray data = tab.tivo_data.get(TIVO);
      for (int i = 0; i < data.length(); ++i) {
         if (recordingId.equals(data.getJSONObject(i).optString("recordingId")))
            return true;
      }
      return false;
   }

   // ---- NPL ---------------------------------------------------------------

   private static Hashtable<String,String> show(String title, String episode, long gmt) {
      Hashtable<String,String> h = new Hashtable<String,String>();
      h.put("title", title + " - " + episode);
      h.put("titleOnly", title);
      h.put("episodeTitle", episode);
      h.put("gmt", "" + gmt);
      h.put("duration", "1800000");
      h.put("size", "1000000000");
      h.put("sizeGB", "1.0 GB");
      h.put("date_long", "some day");
      h.put("url", "http://tivo/" + title + "/" + episode);
      h.put("recordingId", "tivo:rc." + title.replaceAll("\\W", "") + episode);
      return h;
   }

   private nplTable nplTab(boolean folders, Stack<Hashtable<String,String>> entries) {
      tivoTab tabStub = mock(tivoTab.class);
      when(tabStub.showFolders()).thenReturn(folders);
      when(config.gui.getTab(TIVO)).thenReturn(tabStub);
      nplTable npl = new nplTable(TIVO);
      npl.SetNowPlaying(entries);
      return npl;
   }

   // Two shows per series so each series shows up as a folder row
   private static Stack<Hashtable<String,String>> twoFolders() {
      Stack<Hashtable<String,String>> entries = new Stack<Hashtable<String,String>>();
      entries.add(show("Ghosts", "Gate-gate", 1000));
      entries.add(show("Ghosts", "Polar Opposites", 2000));
      entries.add(show("Elsbeth", "Catch and Kill", 3000));
      entries.add(show("Elsbeth", "Murder From Scratch", 4000));
      return entries;
   }

   @Test
   public void nplCollectDeletes_givesEverySelectedFolderItsOwnRow() throws Exception {
      // Both folders have to come out of the table. Keying the removal off the
      // 1st entry of the whole map instead of the 1st of each folder left the
      // 2nd folder overwriting the 1st, so one row stayed behind.
      nplTable npl = nplTab(true, twoFolders());
      assertEquals(2, npl.NowPlaying.getRoot().getChildren().size(), "expected two folder rows");
      TreeItem<nplTable.Tabentry> first = npl.NowPlaying.getTreeItem(0);
      TreeItem<nplTable.Tabentry> second = npl.NowPlaying.getTreeItem(1);

      LinkedHashMap<String,TreeItem<nplTable.Tabentry>> urls = deleteMap();
      LinkedHashMap<String,TreeItem<nplTable.Tabentry>> ids = deleteMap();
      npl.collectDeletes(new Integer[] { 1, 0 }, urls, ids, stopMap());

      assertEquals(4, ids.size(), "expected all four shows to be deleted");
      assertTrue(ids.values().contains(first), "1st folder lost its table row");
      assertTrue(ids.values().contains(second), "2nd folder lost its table row");

      for (TreeItem<nplTable.Tabentry> item : ids.values())
         npl.RemoveItem(item);
      assertEquals(0, npl.NowPlaying.getRoot().getChildren().size(), "a folder row stayed behind");
   }

   @Test
   public void nplCollectDeletes_holdsBackInProgressRecordings() throws Exception {
      // An in progress show is stopped rather than deleted, and stopping
      // rebuilds the table - so it must not be mixed in with the deletes.
      Stack<Hashtable<String,String>> entries = new Stack<Hashtable<String,String>>();
      entries.add(show("Ghosts", "Gate-gate", 1000));
      Hashtable<String,String> recording = show("Elsbeth", "Catch and Kill", 2000);
      recording.put("InProgress", "Yes");
      entries.add(recording);
      nplTable npl = nplTab(false, entries);

      LinkedHashMap<String,TreeItem<nplTable.Tabentry>> ids = deleteMap();
      LinkedHashMap<String,Hashtable<String,String>> toStop = stopMap();
      npl.collectDeletes(new Integer[] { 1, 0 }, deleteMap(), ids, toStop);

      assertEquals(1, toStop.size(), "the in progress recording was not held back");
      assertSame(recording, toStop.values().iterator().next());
      assertEquals(1, ids.size(), "the in progress recording was queued for deletion too");
      assertTrue(ids.containsKey(entries.get(0).get("recordingId")));
   }

   @Test
   public void nplRemoveItem_ignoresARowAlreadyGone() throws Exception {
      // Every show in a folder maps to the same folder item, so the delete loop
      // asks for it once per show. Only the 1st ask may remove anything.
      nplTable npl = nplTab(false, twoFolders());
      int total = npl.NowPlaying.getRoot().getChildren().size();
      TreeItem<nplTable.Tabentry> item = npl.NowPlaying.getTreeItem(2);

      npl.RemoveItem(item);
      npl.RemoveItem(item);
      npl.RemoveItem(item);

      assertEquals(total - 1, npl.NowPlaying.getRoot().getChildren().size(),
         "removing the same item twice took out extra rows");
   }

   @Test
   public void nplRemoveItem_dropsTheSameShowAfterAResort() throws Exception {
      nplTable npl = nplTab(false, twoFolders());
      TreeItem<nplTable.Tabentry> item = npl.NowPlaying.getTreeItem(0);
      String title = item.getValue().getSHOW().title;

      npl.NowPlaying.setDefaultSort("SHOW", true);
      npl.NowPlaying.sort();
      assertNotEquals(title, npl.NowPlaying.getTreeItem(0).getValue().getSHOW().title,
         "the table did not re-sort, so this proves nothing");
      npl.RemoveItem(item);

      for (TreeItem<nplTable.Tabentry> row : npl.NowPlaying.getRoot().getChildren())
         assertNotEquals(title, row.getValue().getSHOW().title, "the wrong show was removed");
   }

   @Test
   public void nplRemoveEntry_updatesTheFolderItLeaves() throws Exception {
      // Delete-after-download removes a show by recordingId. Taking the child
      // out without rebuilding the folder row left it reporting the old count
      // and size.
      nplTable npl = nplTab(true, twoFolders());
      TreeItem<nplTable.Tabentry> folder = npl.NowPlaying.getTreeItem(0);
      assertEquals(2, folder.getValue().getSHOW().numEntries);
      long size = folder.getValue().getSIZE().sortable;
      String id = folder.getValue().getDATE().folderData.get(0).get("recordingId");

      npl.RemoveEntry(id);

      assertEquals(1, folder.getValue().getSHOW().numEntries,
         "folder row still reports the show that was removed");
      assertTrue(folder.getValue().getSIZE().sortable < size,
         "folder row still reports the size of the show that was removed");
   }

   private static LinkedHashMap<String,TreeItem<nplTable.Tabentry>> deleteMap() {
      return new LinkedHashMap<String,TreeItem<nplTable.Tabentry>>();
   }

   private static LinkedHashMap<String,Hashtable<String,String>> stopMap() {
      return new LinkedHashMap<String,Hashtable<String,String>>();
   }
}
