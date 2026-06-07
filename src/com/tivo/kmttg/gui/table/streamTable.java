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

import javax.swing.JLabel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONConverter;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.TableMap;
import com.tivo.kmttg.gui.gui;
import com.tivo.kmttg.gui.comparator.DateComparator;
import com.tivo.kmttg.gui.comparator.ImageComparator;
import com.tivo.kmttg.gui.sortable.sortableDate;
import com.tivo.kmttg.gui.swing.KmttgTable;
import com.tivo.kmttg.gui.swing.SwingUtil;
import com.tivo.kmttg.gui.swing.TreeTable;
import com.tivo.kmttg.gui.swing.TreeTable.TreeItem;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.rpc.id;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class streamTable extends TableMap {
   private String currentTivo = null;
   public TreeTable<Tabentry> TABLE = null;
   public String[] TITLE_cols = {"", "CREATED", "ITEM", "SOURCE"};
   private double[] weights = {9, 17, 59, 15};
   public String folderName = null;
   public int folderEntryNum = -1;
   public Hashtable<String,JSONArray> tivo_data = new Hashtable<String,JSONArray>();
   public Hashtable<String,JSONArray> episode_data = new Hashtable<String,JSONArray>();

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
      TABLE.getRoot().clearChildren();
      episode_data = new Hashtable<String,JSONArray>();
   }
   @Override
   public TreeTable<?> getTreeTable() {
      return TABLE;
   }

   public streamTable() {
      TABLE = new TreeTable<Tabentry>(TITLE_cols);
      TABLE.setComparator("", new ImageComparator());
      TABLE.setComparator("CREATED", new DateComparator());
      KmttgTable.setColumnAlignment(TABLE.table, "CREATED", JLabel.RIGHT);
      TableUtil.setWeights(TABLE, TITLE_cols, weights, false);

      // Want to resize columns whenever a tree is expanded
      TABLE.setOnExpand(new Runnable() {
         @Override public void run() {
            TableUtil.autoSizeTableViewColumns(TABLE, true);
         }
      });

      // Add keyboard listener
      TABLE.table.addKeyListener(new KeyAdapter() {
         @Override
         public void keyPressed(KeyEvent e) {
            KeyPressed(e);
         }
      });

      // Define selection listener to detect table row selection changes
      TABLE.table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
         @Override
         public void valueChanged(ListSelectionEvent e) {
            if (e.getValueIsAdjusting())
               return;
            int row = TABLE.table.getSelectionModel().getLeadSelectionIndex();
            if (row >= 0 && row < TABLE.getExpandedItemCount() && TABLE.isSelected(row))
               TABLERowSelected(row, TABLE.getTreeItem(row).getValue());
         }
      });

      // Add right mouse button handler
      TableUtil.AddRightMouseListener(TABLE);
   }

   public static class Tabentry {
      public imageCell image = new imageCell();
      public String title = "";
      public sortableDate created = null;
      public String source = "";

      public Tabentry(JSONObject entry) {
         try {
            long start = -1;
            if (entry.has("startTime"))
               start = JSONConverter.getLongDateFromString(entry.getString("startTime"));
            title = JSONConverter.makeShowTitle(entry);
            if (entry.has("isFolder") && entry.getBoolean("isFolder")) {
               image.setImage(gui.Images.get("folder"));
               image.imageName = "folder";
               created = new sortableDate(title, entry, start);
            } else {
               created = new sortableDate(entry, start);
            }
            source = JSONConverter.getPartnerName(entry);
         } catch (JSONException e1) {
            log.error("streamTable Tabentry - " + e1.getMessage());
         }
      }

      public Tabentry(String entry) {
         title = entry;
      }

      public imageCell getIMAGE() {
         return image;
      }

      public sortableDate getCREATED() {
         return created;
      }

      public String getITEM() {
         return title;
      }

      public String getSOURCE() {
         return source;
      }

      public String toString() {
         return title;
      }
   }

   public JSONObject GetRowData(int row) {
      return TABLE.getTreeItem(row).getValue().getCREATED().json;
   }

   private void TABLERowSelected(int row, Tabentry entry) {
      // Get column items for selected row
      sortableDate s = entry.getCREATED();
      if (s.folder) {
         // For folder item selection add child nodes if not already
         TreeItem<Tabentry> item = TABLE.getTreeItem(row);
         if (item.getChildren().size() == 0)
            updateFolder(item, entry);
      } else {
         try {
            // Non folder entry so print single entry info
            String message = TableUtil.makeShowSummary(s, null);
            String title = "\nStreaming: ";
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
   }

   // Update table to display given entries
   // data is a JSONArray of channel JSON objects
   public void AddRows(String tivoName, JSONArray data) {
      Refresh(data);
      TABLE.sort();
      TableUtil.autoSizeTableViewColumns(TABLE, true);

      // Save the data
      currentTivo = tivoName;
      tivo_data.put(tivoName, data);

      // Update stream tab to show this tivoName
      if (config.gui.remote_gui != null)
         config.gui.remote_gui.setTivoName("stream", tivoName);
   }

   // Refresh whole table
   public void Refresh(JSONArray data) {
      if (data == null) {
         if (currentTivo != null)
            AddRows(currentTivo, tivo_data.get(currentTivo));
         return;
      }
      if (TABLE != null) {
         // Top level folder structure
         clear();
         // Add all folders
         for (int i=0; i<data.length(); ++i) {
            try {
               AddTABLERow(data.getJSONObject(i));
            } catch (JSONException e) {
               log.error("Refresh - " + e.getMessage());
            }
         }

         TABLE.getRoot().setExpanded(true);
      }
   }

   // Add row to table
   public void AddTABLERow(JSONObject entry) {
      TreeItem<Tabentry> item = new TreeItem<Tabentry>( new Tabentry(entry) );
      TABLE.getRoot().addChild(item);
   }

   private void BePatient(final TreeItem<Tabentry> item, final String title) {
      SwingUtil.runLater(new Runnable() {
         @Override public void run() {
            String message = "PLEASE BE PATIENT - getting '" + title + "' episodes";
            item.addChild(new TreeItem<Tabentry>(new Tabentry(message)));
            item.setExpanded(true);
            TableUtil.autoSizeTableViewColumns(TABLE, true);
         }
      });
   }


   // Look for entry with given folder name and select it
   // (This used when returning back from folder mode to top level mode)
   public void SelectFolder(String folderName) {
      debug.print("folderName=" + folderName);
      for (int i=0; i<TABLE.getRoot().getChildren().size(); ++i) {
         sortableDate s = TABLE.getRoot().getChildren().get(i).getValue().getCREATED();
         if (s.folder) {
            if (s.folderName.equals(folderName)) {
               TABLE.clearSelection();
               TABLE.select(TABLE.getRoot().getChildren().get(i));
               return;
            }
         }
      }
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
      else if (keyCode == KeyEvent.VK_N) {
         int[] selected = TableUtil.GetSelectedRows(TABLE);
         if (selected == null || selected.length < 1)
            return;
         TableUtil.PrintEpisodes(GetRowData(selected[0]));
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
      }
      else if (keyCode == KeyEvent.VK_Q) {
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
      else if (keyCode == KeyEvent.VK_R) {
         config.gui.remote_gui.stream_tab.remove.doClick();
      } else if (keyCode == KeyEvent.VK_T) {
         TableUtil.toggleTreeState(TABLE);
      }
   }

   // Get all episodes for a specific collectionId
   public void updateFolder(final TreeItem<Tabentry> item, Tabentry entry) {
      try {
         final String tivoName = currentTivo;
         JSONObject json = entry.getCREATED().json;
         final String title = json.getString("title");
         final String collectionId = json.getString("collectionId");
         final String partnerId = json.getString("partnerId");
         if (episode_data.containsKey(collectionId)) {
            Refresh(episode_data.get(collectionId));
            return;
         }
         BePatient(item, title);
         Runnable task = new Runnable() {
            @Override public void run() {
               Remote r = config.initRemote(tivoName);
               if (r.success) {
                  log.warn(">> Collecting episode data for: " + title);
                  final JSONArray entries = r.getEpisodes(collectionId);
                  r.disconnect();
                  item.clearChildren();
                  if (entries != null && entries.length() > 0) {
                     try {
                        for (int i=0; i<entries.length(); ++i)
                           entries.getJSONObject(i).put("partnerId", partnerId);
                     } catch (JSONException e) {
                        log.error("streamTable updateFolder - " + e.getMessage());
                     }
                  }
                  episode_data.put(collectionId, entries);
                  if (entries.length() > 0) {
                     SwingUtil.runLater(new Runnable() {
                        @Override public void run() {
                           try {
                              for (int i=0; i<entries.length(); ++i) {
                                 item.addChild(new TreeItem<Tabentry>(new Tabentry(entries.getJSONObject(i))));
                              }
                              item.setExpanded(true);
                              TableUtil.autoSizeTableViewColumns(TABLE, true);
                           } catch (JSONException e) {
                              log.error("streamTable updateFolder - " + e.getMessage());
                           }
                        }
                     });
                  }
               } // if r.success
            }
         };
         new Thread(task).start();
      } catch (JSONException e) {
         log.error("streamTable updateFolder - " + e.getMessage());
      }
   }

   // Attempt to remove currently selected top view table item(s)
   public void removeButtonCB() {
      final String tivoName = currentTivo;
      final int[] selected = TableUtil.GetSelectedRows(TABLE);
      if (selected.length > 0) {
         Runnable task = new Runnable() {
            @Override public void run() {
               try {
                  Boolean removed = false;
                  Remote r = config.initRemote(tivoName);
                  if (r.success) {
                     int row;
                     for (int i=0; i<selected.length; ++i) {
                        row = selected[i];
                        JSONObject json = GetRowData(row);
                        if (json.has("isFolder") && json.getBoolean("isFolder")) {
                           // A One Pass streaming entry is removed by unsubscribing Season Pass
                           if (json.has("collectionId")) {
                              JSONObject sp = r.findSP(json.getString("collectionId"));
                              if (sp != null) {
                                 if (sp.has("subscriptionId")) {
                                    JSONObject o = new JSONObject();
                                    o.put("subscriptionId", sp.getString("subscriptionId"));
                                    JSONObject result = r.Command("Unsubscribe", o);
                                    if (result != null) {
                                       removed = true;
                                       log.warn("Removed streaming One Pass: " + json.getString("title"));
                                    }
                                 }
                              }
                           }
                        } else {
                           // A non-One Pass entry can be removed
                           if (json.has("contentId")) {
                              JSONObject o = new JSONObject();
                              o.put("contentId", json.getString("contentId"));
                              JSONObject result = r.Command("ContentLocatorRemove", o);
                              if (result != null) {
                                 removed = true;
                                 log.warn("Removed streaming item: " + json.getString("title"));
                              }
                           }
                        }
                     } // for
                     r.disconnect();
                     if (removed) {
                        // Force a table refresh if any items were removed
                        config.gui.remote_gui.stream_tab.refresh.doClick();
                     }
                  } // if r.success
               } catch (JSONException e) {
                  log.error("removeButtonCB - " + e.getMessage());
               }
            }
         };
         new Thread(task).start();
      }
   }

}
