/*
 * Copyright 2008-Present Kevin Moye <moyekj@yahoo.com>.
 *
 * This file is part of kmttg package.
 *
 * kmttg is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this project.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tivo.kmttg.gui.table;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Hashtable;
import java.util.Stack;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONConverter;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.TableMap;
import com.tivo.kmttg.gui.comparator.DateComparator;
import com.tivo.kmttg.gui.comparator.DurationComparator;
import com.tivo.kmttg.gui.comparator.StringChannelComparator;
import com.tivo.kmttg.gui.sortable.sortableDate;
import com.tivo.kmttg.gui.sortable.sortableDuration;
import com.tivo.kmttg.gui.swing.KmttgTable;
import com.tivo.kmttg.gui.swing.KmttgTableModel;
import com.tivo.kmttg.gui.swing.SwingUtil;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.rpc.id;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.log;

public class deletedTable extends TableMap {
   private String currentTivo = null;
   public JTable TABLE = null;
   public KmttgTableModel<Tabentry> MODEL = null;
   public String[] TITLE_cols = {"SHOW", "DELETED", "RECORDED", "CHANNEL", "DUR"};
   private double[] weights = {45, 17, 17, 15, 6};
   public String folderName = null;
   public int folderEntryNum = -1;
   public Hashtable<String,JSONArray> tivo_data = new Hashtable<String,JSONArray>();
   private String filterText = ""; // lower-cased SHOW-column filter ("" => show all)

   // TableMap overrides
   @Override
   public JSONObject getJson(int row) {
      return GetRowData(row);
   }
   @Override
   public int[] getSelected() {
      return TableUtil.GetSelectedRows(TABLE);
   }
   @Override
   public Boolean isRemote() {
      return true;
   }
   @Override
   public void clear() {
      MODEL.clear();
   }
   @Override
   public JTable getTable() {
      return TABLE;
   }

   public deletedTable() {
      MODEL = new KmttgTableModel<Tabentry>(TITLE_cols);
      MODEL.setComparator("DELETED", new DateComparator());
      MODEL.setComparator("RECORDED", new DateComparator());
      MODEL.setComparator("DUR", new DurationComparator());
      MODEL.setComparator("CHANNEL", new StringChannelComparator());
      // Default sort is descending date when no column sort is selected
      MODEL.setDefaultSort("DELETED", false);
      TABLE = KmttgTable.create(MODEL, null);
      KmttgTable.setColumnAlignment(TABLE, "DELETED", JLabel.RIGHT);
      KmttgTable.setColumnAlignment(TABLE, "RECORDED", JLabel.RIGHT);
      KmttgTable.setColumnAlignment(TABLE, "DUR", JLabel.CENTER);
      TableUtil.setWeights(TABLE, TITLE_cols, weights, false);

      // Add keyboard listener
      TABLE.addKeyListener(new KeyAdapter() {
         @Override
         public void keyPressed(KeyEvent e) {
            KeyPressed(e);
         }
      });

      // Define selection listener to detect table row selection changes
      TABLE.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
         @Override
         public void valueChanged(ListSelectionEvent e) {
            if (e.getValueIsAdjusting())
               return;
            int row = TABLE.getSelectionModel().getLeadSelectionIndex();
            if (row >= 0 && row < MODEL.size() && TABLE.isRowSelected(row)) {
               TABLERowSelected(MODEL.getRow(row));
            }
         }
      });

      // Add right mouse button handler
      TableUtil.AddRightMouseListener(TABLE);
   }

   public static class Tabentry {
      public String title = "";
      public sortableDate deleted;
      public sortableDate recorded;
      public String channel = "";
      public sortableDuration duration;

      public Tabentry(JSONObject entry) {
         try {
            String startString=null, endString=null, delString=null;
            long start=0, end=0, del=0;
            if (entry.has("scheduledStartTime")) {
               startString = entry.getString("scheduledStartTime");
               start = JSONConverter.getLongDateFromString(startString);
               endString = entry.getString("scheduledEndTime");
               end = JSONConverter.getLongDateFromString(endString);
            } else {
               start = JSONConverter.getStartTime(entry);
               end = JSONConverter.getEndTime(entry);
            }
            if (entry.has("deletionTime")) {
               delString = entry.getString("deletionTime");
               del = JSONConverter.getLongDateFromString(delString);
            }
            title = JSONConverter.makeShowTitle(entry);
            channel = JSONConverter.makeChannelName(entry);

            deleted = new sortableDate(entry, del);
            recorded = new sortableDate(new JSONObject(), start);
            duration = new sortableDuration(end-start, false);
         } catch (JSONException e1) {
            log.error("AddTABLERow - " + e1.getMessage());
         }
      }

      public String getSHOW() {
         return title;
      }

      public sortableDate getDELETED() {
         return deleted;
      }

      public sortableDate getRECORDED() {
         return recorded;
      }

      public String getCHANNEL() {
         return channel;
      }

      public sortableDuration getDUR() {
         return duration;
      }

      public String toString() {
         return title;
      }
   }

   public JSONObject GetRowData(int row) {
      return MODEL.getRow(row).getDELETED().json;
   }

   // Handle keyboard presses
   private void KeyPressed(KeyEvent e) {
      if (e.isControlDown())
         return;
      int keyCode = e.getKeyCode();
      if (keyCode == KeyEvent.VK_I) {
         int[] selected = TableUtil.GetSelectedRows(TABLE);
         if (selected == null || selected.length < 1)
            return;
         JSONObject json = GetRowData(selected[0]);
         if (json != null) {
            config.gui.show_details.update(TABLE, currentTivo, json);
         }
      }
      else if (keyCode == KeyEvent.VK_R) {
         config.gui.remote_gui.deleted_tab.recover.doClick();
      }
      else if (keyCode == KeyEvent.VK_DELETE) {
         e.consume(); // Need this so as not to remove focus which is default key action
         config.gui.remote_gui.deleted_tab.permDelete.doClick();
      }
      else if (keyCode == KeyEvent.VK_J) {
         // Print json of selected row to log window
         int[] selected = TableUtil.GetSelectedRows(TABLE);
         if (selected == null || selected.length < 1)
            return;
         JSONObject json = GetRowData(selected[0]);
         if (json != null) {
            rnpl.pprintJSON(json);
            id.printIds(json);
         }
      } else if (keyCode == KeyEvent.VK_N) {
         int[] selected = TableUtil.GetSelectedRows(TABLE);
         if (selected == null || selected.length < 1)
            return;
         TableUtil.PrintEpisodes(GetRowData(selected[0]));
      } else if (keyCode == KeyEvent.VK_Q) {
         // Web query currently selected entry
         int[] selected = TableUtil.GetSelectedRows(TABLE);
         if (selected == null || selected.length < 1)
            return;
         JSONObject json = GetRowData(selected[0]);
         if (json != null && json.has("title")) {
            try {
               String title = json.getString("title");
               if (json.has("subtitle"))
                  title = title + " - " + json.getString("subtitle");
               TableUtil.webQuery(title);
            } catch (JSONException e1) {
               log.error("KeyPressed Q - " + e1.getMessage());
            }
         }
      }
   }

   private void TABLERowSelected(Tabentry entry) {
      sortableDate s = entry.getDELETED();
      // Get column items for selected row
      try {
         // Non folder entry so print single entry info
         sortableDuration dur = entry.getDUR();
         String message = TableUtil.makeShowSummary(s, dur);
         String title = "\nDeleted: ";
         if (s.json.has("title"))
            title += s.json.getString("title");
         if (s.json.has("subtitle"))
            title += " - " + s.json.getString("subtitle");
         log.warn(title);
         log.print(message);

         if (config.gui.show_details.isShowing())
            config.gui.show_details.update(TABLE, currentTivo, s.json);
      } catch (JSONException e) {
         log.error("TABLERowSelected - " + e.getMessage());
         return;
      }
   }

   // Update table to display given entries
   public void AddRows(String tivoName, JSONArray data) {
      tivo_data.put(tivoName, data);
      currentTivo = tivoName;
      displayData(data, true);
      if (config.gui.remote_gui != null)
         config.gui.remote_gui.setTivoName("deleted", tivoName);
   }

   // Set the SHOW-column filter and re-display the current TiVo's list. An
   // empty/blank filter shows everything; otherwise only rows whose show title
   // contains the text (case-insensitive) are shown.
   public void setFilter(String text) {
      filterText = (text == null) ? "" : text.trim().toLowerCase();
      if (currentTivo != null && tivo_data.containsKey(currentTivo))
         displayData(tivo_data.get(currentTivo), false);
   }

   // Populate the table from data, keeping only entries that match the current
   // filter. autoSize is skipped on filter changes so columns don't jump while
   // the user types.
   private void displayData(JSONArray data, boolean autoSize) {
      try {
         Stack<JSONObject> o = new Stack<JSONObject>();
         for (int i=0; i<data.length(); ++i) {
            JSONObject entry = data.getJSONObject(i);
            if (matchesFilter(entry))
               o.add(entry);
         }
         Refresh(o);
         MODEL.sort();
         if (autoSize)
            TableUtil.autoSizeTableViewColumns(TABLE, true);
         refreshNumber();
      } catch (JSONException e) {
         log.print("Deleted displayData - " + e.getMessage());
      }
   }

   private boolean matchesFilter(JSONObject entry) {
      if (filterText.length() == 0)
         return true;
      String title = JSONConverter.makeShowTitle(entry);
      return title != null && title.toLowerCase().contains(filterText);
   }

   // Refresh table with given given entries
   public void Refresh(Stack<JSONObject> o) {
      if (o == null) {
         if (currentTivo != null)
            AddRows(currentTivo, tivo_data.get(currentTivo));
         return;
      }
      if (TABLE != null) {
         displayFlatStructure(o);
      }
   }

   // Update table display to show top level flat structure
   private void displayFlatStructure(Stack<JSONObject> o) {
      clear();
      for (int i=0; i<o.size(); ++i) {
         AddTABLERow(o.get(i));
      }
   }

   // Add a non folder entry to TABLE table
   public void AddTABLERow(JSONObject entry) {
      MODEL.addRow(new Tabentry(entry));
   }

   // Refresh the # SHOWS label. When a filter is active, show "shown of total".
   private void refreshNumber() {
      if (config.gui.remote_gui == null || currentTivo == null || !tivo_data.containsKey(currentTivo))
         return;
      SwingUtil.runLater(new Runnable() {
         @Override public void run() {
            int total = tivo_data.get(currentTivo).length();
            int shown = MODEL.size();
            String text = (shown == total)
                  ? (total + " SHOWS")
                  : (shown + " of " + total + " SHOWS");
            config.gui.remote_gui.deleted_tab.label.setText(text);
         }
      });
   }

   // Remove the cached entry with the given recordingId from the current TiVo's
   // full data. Matched by recordingId rather than row index because the model
   // is sorted/filtered relative to the cached JSONArray.
   private void removeFromCache(String recordingId) {
      if (recordingId == null || currentTivo == null)
         return;
      JSONArray data = tivo_data.get(currentTivo);
      if (data == null)
         return;
      for (int i = 0; i < data.length(); ++i) {
         try {
            JSONObject o = data.getJSONObject(i);
            if (o.has("recordingId") && recordingId.equals(o.getString("recordingId"))) {
               data.remove(i);
               return;
            }
         } catch (JSONException e) {
            // skip malformed entry
         }
      }
   }

   // Undelete selected recordings
   public void recoverSingle(final String tivoName) {
      // Get selection set ordered highest to lowest
      final Integer[] sorted_final = TableUtil.highToLow(TableUtil.GetSelectedRows(TABLE));
      if (sorted_final.length == 0)
         return;
      log.print("Recovering individual recordings on TiVo: " + tivoName);
      Runnable task = new Runnable() {
         @Override public void run() {
            Remote r = config.initRemote(tivoName);
            if (r.success) {
               for (final int row : sorted_final) {
                  try {
                     JSONObject json = GetRowData(row);
                     final String title = json.getString("title");
                     if (json != null) {
                        final String recordingId = json.getString("recordingId");
                        JSONObject o = new JSONObject();
                        JSONArray a = new JSONArray();
                        a.put(recordingId);
                        o.put("recordingId", a);
                        final JSONObject result = r.Command("Undelete", o);
                        SwingUtil.runLater(new Runnable() {
                           @Override
                           public void run() {
                              if (result == null) {
                                 TABLE.removeRowSelectionInterval(row, row);
                                 log.error("Failed to recover recording: '" + title + "'");
                              } else {
                                 log.warn("Recovered recording: '" + title + "' on TiVo: " + tivoName);
                                 MODEL.removeRow(row);
                                 removeFromCache(recordingId);
                                 refreshNumber();
                              }
                           }
                        });
                     }
                  } catch (JSONException e) {
                     log.error("recoverSingle failed - " + e.getMessage());
                  }
               }
               r.disconnect();
            }
         }
      };
      new Thread(task).start();
   }

   // Permanently delete selected recordings
   public void permanentlyDelete(final String tivoName) {
      // Get selection set ordered highest to lowest
      final Integer[] sorted_final = TableUtil.highToLow(TableUtil.GetSelectedRows(TABLE));
      if (sorted_final.length == 0)
         return;
      log.print("Permanently deleting individual recordings on TiVo: " + tivoName);
      Runnable task = new Runnable() {
         @Override public void run() {
            JSONObject json;
            Remote r = config.initRemote(tivoName);
            if (r.success) {
               for (final int row : sorted_final) {
                  try {
                     json = GetRowData(row);
                     if (json != null) {
                        String title = "";
                        if (json.has("title"))
                           title = json.getString("title");
                        if (json.has("subtitle"))
                           title += " - " + json.getString("subtitle");
                        final String title_final = title;
                        final String recordingId = json.getString("recordingId");
                        JSONObject o = new JSONObject();
                        JSONArray a = new JSONArray();
                        a.put(recordingId);
                        o.put("recordingId", a);
                        final JSONObject result = r.Command("PermanentlyDelete", o);
                        SwingUtil.runLater(new Runnable() {
                           @Override
                           public void run() {
                              if (result == null) {
                                 TABLE.removeRowSelectionInterval(row, row);
                                 log.error("Failed to permanently delete recording: '" + title_final + "'");
                              } else {
                                 log.warn("Permanently deleted recording: '" + title_final + "' on TiVo: " + tivoName);
                                 MODEL.removeRow(row);
                                 removeFromCache(recordingId);
                                 refreshNumber();
                              }
                           }
                        });
                     }
                  } catch (JSONException e) {
                     log.error("permanentlyDelete failed - " + e.getMessage());
                  }
               }
               r.disconnect();
            }
         }
      };
      new Thread(task).start();
   }
}
