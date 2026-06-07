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

import java.awt.Color;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Hashtable;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONConverter;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.TableMap;
import com.tivo.kmttg.gui.gui;
import com.tivo.kmttg.gui.comparator.DateComparator;
import com.tivo.kmttg.gui.comparator.DurationComparator;
import com.tivo.kmttg.gui.comparator.ImageComparator;
import com.tivo.kmttg.gui.comparator.StringChannelComparator;
import com.tivo.kmttg.gui.sortable.sortableDate;
import com.tivo.kmttg.gui.sortable.sortableDuration;
import com.tivo.kmttg.gui.swing.KmttgTable;
import com.tivo.kmttg.gui.swing.KmttgTableModel;
import com.tivo.kmttg.gui.swing.RowColorer;
import com.tivo.kmttg.main.auto;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.rpc.id;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class todoTable extends TableMap {
   private String[] TITLE_cols = {"", "DATE", "SHOW", "CHANNEL", "DUR"};
   private double[] weights = {3, 17, 62, 12, 6};
   public JTable TABLE = null;
   public KmttgTableModel<Tabentry> MODEL = null;
   public Hashtable<String,JSONArray> tivo_data = new Hashtable<String,JSONArray>();
   private String currentTivo = null;
   private Boolean searchingRepeats = false;

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

   public todoTable() {
      MODEL = new KmttgTableModel<Tabentry>(TITLE_cols);
      MODEL.setComparator("", new ImageComparator());
      MODEL.setComparator("DATE", new DateComparator());
      MODEL.setComparator("DUR", new DurationComparator());
      MODEL.setComparator("CHANNEL", new StringChannelComparator());
      // Default sort is ascending date when no column sort is selected
      MODEL.setDefaultSort("DATE", true);
      TABLE = KmttgTable.create(MODEL, new ColorRow());
      KmttgTable.setImageColumn(TABLE, "");
      KmttgTable.setColumnAlignment(TABLE, "DATE", JLabel.RIGHT);
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

   // Row background color handling
   private class ColorRow implements RowColorer<Tabentry> {
      public Color getColor(Tabentry entry) {
         if (entry != null) {
            JSONObject json = entry.getDATE().json;
            if (json != null) {
               try {
                  Color color = null;
                  if (json.has("state")) {
                     if (json.getString("state").equals("inProgress"))
                        color = TableUtil.tableBkgndRecording;
                  }

                  if (config.showHistoryInTable == 1) {
                     if (json.has("partnerCollectionId")) {
                        // Series 4 and later TiVos
                        if (auto.keywordMatchHistoryFast(json.getString("partnerCollectionId"), false))
                           color = TableUtil.tableBkgndInHistory;
                     }
                     if (json.has("partnerContentId")) {
                        // This is for series 3 TiVos where programId is part of partnerContentId
                        // example: parternContentId = "epgProvider:ct.EP013898090001"
                        String programId = json.getString("partnerContentId");
                        programId = programId.replaceFirst("^.+\\.", "");
                        if (auto.keywordMatchHistoryFast(programId, false))
                           color = TableUtil.tableBkgndInHistory;
                     }
                  }
                  return color;
               } catch (JSONException e) {
                  log.error("todoTable ColorRow - " + e.getMessage());
               }
            }
         }
         return null;
      }
   }

   public static class Tabentry {
      imageCell image = new imageCell();
      public String title = "";
      public sortableDate date;
      public String channel = "";
      public sortableDuration duration;

      public Tabentry(JSONObject data) {
         try {
            String startString=null, endString=null;
            long start=0, end=0;
            if (data.has("scheduledStartTime")) {
               startString = data.getString("scheduledStartTime");
               start = JSONConverter.getLongDateFromString(startString);
               endString = data.getString("scheduledEndTime");
               end = JSONConverter.getLongDateFromString(endString);
            } else if (data.has("startTime")) {
               start = JSONConverter.getStartTime(data);
               end = JSONConverter.getEndTime(data);
            }
            title = JSONConverter.makeShowTitle(data);
            channel = JSONConverter.makeChannelName(data);

            date = new sortableDate(data, start);
            duration = new sortableDuration(end-start, false);
            if (data.has("subscriptionIdentifier")) {
               JSONObject id = data.getJSONArray("subscriptionIdentifier").getJSONObject(0);
               String type = id.getString("subscriptionType");
               if (type.equals("repeatingTimeChannel") || type.equals("seasonPass")) {
                  image.setImage(gui.Images.get("image-season-pass"));
                  image.imageName = "image-season-pass";
               }
               if (type.equals("wishList")) {
                  image.setImage(gui.Images.get("image-season-pass-wishlist"));
                  image.imageName = "image-season-pass-wishlist";
               }
               if (type.startsWith("single")) {
                  image.setImage(gui.Images.get("image-single-explicit-record"));
                  image.imageName = "image-single-explicit-record";
               }
            }
         } catch (Exception e) {
            log.error("todoTable Tabentry - " + e.getMessage());
         }
      }

      public imageCell getIMAGE() {
         return image;
      }

      public sortableDate getDATE() {
         return date;
      }

      public String getSHOW() {
         return title;
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
      return MODEL.getRow(row).getDATE().json;
   }

    public void AddRows(String tivoName, JSONArray data) {
       try {
          for (int i=0; i<data.length(); ++i) {
             AddRow(data.getJSONObject(i));
          }
          tivo_data.put(tivoName, data);
          currentTivo = tivoName;
          MODEL.sort();
          TableUtil.autoSizeTableViewColumns(TABLE, true);
          if (config.gui.remote_gui != null) {
             config.gui.remote_gui.setTivoName("todo", tivoName);
             refreshNumber();
          }
       } catch (JSONException e) {
          log.error("todoTable AddRows - " + e.getMessage());
       }
    }

    private void AddRow(JSONObject data) {
       debug.print("data=" + data);
       MODEL.addRow(new Tabentry(data));
    }

    private void TABLERowSelected(Tabentry entry) {
       if (searchingRepeats)
          return;
       // Get column items for selected row
       sortableDate s = entry.getDATE();
       if (s.folder) {
          // Folder entry - don't display anything
       } else {
          try {
             // Non folder entry so print single entry info
             sortableDuration dur = entry.getDUR();
             String message = TableUtil.makeShowSummary(s, dur);
             String title = "\nToDo: ";
             if (s.json.has("title"))
                title += s.json.getString("title");
             if (s.json.has("subtitle"))
                title += " - " + s.json.getString("subtitle");
             if (s.json.has("state") && s.json.getString("state").equals("inProgress"))
                title += " (currently recording)";
             log.warn(title);
             log.print(message);

             if (config.gui.show_details.isShowing())
                config.gui.show_details.update(TABLE, currentTivo, s.json);
          } catch (JSONException e) {
             log.error("TABLERowSelected - " + e.getMessage());
             return;
          }
       }
    }

    private void selectRow(JSONObject json) {
       for (int row=0; row<MODEL.size(); ++row) {
          JSONObject rowData = GetRowData(row);
          if (rowData == json) {
             TABLE.addRowSelectionInterval(row, row);
             TableUtil.scrollToCenter(TABLE, row);
             TABLE.requestFocus();
          }
       }
    }

    // Handle keyboard presses
    private void KeyPressed(KeyEvent e) {
       if (e.isControlDown())
          return;
       int keyCode = e.getKeyCode();
       if (keyCode == KeyEvent.VK_DELETE){
          // Delete key has special action
          e.consume(); // Need this so as not to remove focus which is default key action
          DeleteCB();
       }
       else if (keyCode == KeyEvent.VK_A) {
          int[] selected = TableUtil.GetSelectedRows(TABLE);
          if (selected == null || selected.length < 1)
             return;
          JSONObject json = GetRowData(selected[0]);
          if (json != null)
             auto.AddHistoryEntry(json);
       }
       else if (keyCode == KeyEvent.VK_C) {
          config.gui.remote_gui.todo_tab.cancel.doClick();
       }
       else if (keyCode == KeyEvent.VK_M) {
          config.gui.remote_gui.todo_tab.modify.doClick();
       }
       else if (keyCode == KeyEvent.VK_I) {
          int[] selected = TableUtil.GetSelectedRows(TABLE);
          if (selected == null || selected.length < 1)
             return;
          JSONObject json = GetRowData(selected[0]);
          if (json != null) {
             config.gui.show_details.update(TABLE, currentTivo, json);
          }
       } else if (keyCode == KeyEvent.VK_J) {
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
       } else if (keyCode == KeyEvent.VK_K) {
          int[] selected = TableUtil.GetSelectedRows(TABLE);
          if (selected == null || selected.length < 1)
             return;
          JSONObject json = GetRowData(selected[0]);
          if (json.has("contentId")) {
             try {
                final String contentId = json.getString("contentId");
                Runnable task = new Runnable() {
                   @Override public void run() {
                      Remote r = config.initRemote(currentTivo);
                      if (r.success) {
                         r.printClipData(contentId);
                         r.disconnect();
                      }
                   }
                };
                new Thread(task).start();
             } catch (JSONException e1) {
                log.error("KeyPressed K - " + e1.getMessage());
             }
          }
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

    public void DeleteCB() {
       Integer[] selected = TableUtil.highToLow(TableUtil.GetSelectedRows(TABLE));
       if (selected == null) {
          log.error("Must select 1 or more table rows.");
          return;
       }
       if (currentTivo == null) {
          log.error("Table not initialized");
          return;
       }
       String title;
       JSONObject json;
       Remote r = config.initRemote(currentTivo);
       if (r.success) {
          for (int row : selected) {
             json = GetRowData(row);
             if (json != null) {
                try {
                   title = json.getString("title");
                   if (json.has("subtitle"))
                      title += " - " + json.getString("subtitle");
                   log.warn("Cancelling ToDo show on TiVo '" + currentTivo + "': " + title);
                   JSONObject o = new JSONObject();
                   JSONArray a = new JSONArray();
                   a.put(json.getString("recordingId"));
                   o.put("recordingId", a);
                   if ( r.Command("Cancel", o) != null ) {
                      tivo_data.get(currentTivo).remove(row);
                      refreshNumber();
                   } else {
                      // Remove from selection list since cancel failed
                      TABLE.removeRowSelectionInterval(row, row);
                   }
                } catch (JSONException e1) {
                   log.error("ToDo cancel - " + e1.getMessage());
                }
             }
          }
          r.disconnect();
       }
       // Remove selected rows from table (highest first to keep indexes valid)
       Integer[] remaining = TableUtil.highToLow(TableUtil.GetSelectedRows(TABLE));
       for (int row : remaining)
          MODEL.removeRow(row);
       TABLE.clearSelection();
    }

    // Refresh the # SHOWS label in the ToDo tab
    private void refreshNumber() {
       config.gui.remote_gui.todo_tab.label.setText("" + tivo_data.get(currentTivo).length() + " SHOWS");
    }

    // Schedule a single recording
    public void recordSingle(String tivoName) {
       int[] selected = TableUtil.GetSelectedRows(TABLE);
       if (selected.length > 0) {
          int row;
          JSONArray entries = new JSONArray();
          JSONObject json;
          for (int i=0; i<selected.length; ++i) {
             row = selected[i];
             json = GetRowData(row);
             entries.put(json);
          }
          TableUtil.recordSingleCB(tivoName, entries);
       }
    }

    // Look for repeated recordings to trim
    public void trimRepeats(String tivoName) {
       if (! tivo_data.containsKey(tivoName))
          return;
       searchingRepeats = true;
       TABLE.clearSelection(); // Clear selection
       Hashtable<String,JSONObject> map = new Hashtable<String,JSONObject>();
       JSONArray shows = tivo_data.get(tivoName);

       try {
          int repeatCount = 0;
          for (int i=0; i<shows.length(); ++i) {
             JSONObject show = shows.getJSONObject(i);

             // Repeated programId
             String pid = id.programId(show);
             if (pid != null && show.has("subtitle")) {
                if (map.containsKey(pid)) {
                   log.warn("Repeat: " + TableUtil.makeShowSummary(show));
                   JSONObject first = map.get(pid);
                   log.warn("Same programId as: " + TableUtil.makeShowSummary(first));
                   selectRow(show);
                   repeatCount++;
                }
                else
                   map.put(pid, show);
             }

             // Repeated title + subtitle
             if (show.has("title") && show.has("subtitle")) {
                String title = show.getString("title") + " - " + show.getString("subtitle");
                if (map.containsKey(title)) {
                   log.warn("Repeat: " + TableUtil.makeShowSummary(show));
                   JSONObject first = map.get(title);
                   log.warn("Same title & subtitle as: " + TableUtil.makeShowSummary(first));
                   selectRow(show);
                   repeatCount++;
                }
                else
                   map.put(title, show);
             }
          }
          log.print("Number of repeat entries selected in table: " + repeatCount);
       } catch (JSONException e) {
          log.error("trimRepeats - " + e.getMessage());
       }
       searchingRepeats = false;
    }
}
