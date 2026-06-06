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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.Stack;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTable;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONFile;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.PopupHandler;
import com.tivo.kmttg.gui.TableMap;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.swing.KmttgTable;
import com.tivo.kmttg.gui.swing.KmttgTableModel;
import com.tivo.kmttg.gui.swing.SwingUtil;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.rpc.id;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class thumbsTable extends TableMap {
   private String currentTivo = null;
   public JTable TABLE = null;
   public KmttgTableModel<Tabentry> MODEL = null;
   public String[] TITLE_cols = {"TYPE", "SHOW", "RATING"};
   private double[] weights = {15, 75, 10};
   public String folderName = null;
   public int folderEntryNum = -1;
   public Hashtable<String,JSONArray> tivo_data = new Hashtable<String,JSONArray>();
   private Boolean loaded = false;
   private String loadedPrefix = "Loaded: ";

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
      setLoaded(false);
   }
   @Override
   public JTable getTable() {
      return TABLE;
   }

   public thumbsTable() {
      MODEL = new KmttgTableModel<Tabentry>(TITLE_cols);
      MODEL.setComparator("RATING", new RatingComparator());
      TABLE = KmttgTable.create(MODEL, null);
      KmttgTable.setColumnAlignment(TABLE, "RATING", JLabel.CENTER);
      TableUtil.setWeights(TABLE, TITLE_cols, weights, false);

      // Add keyboard listener
      TABLE.addKeyListener(new KeyAdapter() {
         @Override
         public void keyPressed(KeyEvent e) {
            KeyPressed(e);
         }
      });

      // Mouse listener: trigger edit prompt for single click in RATING cell,
      // and pass along right mouse button click
      TABLE.addMouseListener(new MouseAdapter() {
         @Override
         public void mousePressed(MouseEvent event) {
            PopupHandler.hide();
            if (event.isPopupTrigger()) {
               PopupHandler.display(TABLE, event);
            }
         }
         @Override
         public void mouseReleased(MouseEvent event) {
            if (event.isPopupTrigger())
               PopupHandler.display(TABLE, event);
         }
         @Override
         public void mouseClicked(MouseEvent event) {
            if (event.isPopupTrigger())
               return;
            int viewRow = TABLE.rowAtPoint(event.getPoint());
            int viewCol = TABLE.columnAtPoint(event.getPoint());
            if (viewRow < 0 || viewCol < 0)
               return;
            int col = TABLE.convertColumnIndexToModel(viewCol);
            if (TITLE_cols[col].equals("RATING")) {
               editRating(viewRow);
            }
         }
      });
   }

   // Prompt for a new RATING value for given row (replaces inline text edit)
   private void editRating(int row) {
      Tabentry entry = MODEL.getRow(row);
      String result = JOptionPane.showInputDialog(
         config.gui.getFrame(),
         "Thumbs Rating (-3 to 3):",
         entry.rating
      );
      if (result == null)
         return;
      int val = 1;
      try {
         val = Integer.parseInt(result);
      } catch (NumberFormatException e) {
         log.warn("Illegal value - setting to 1");
         val = 1;
      }
      if (val < -3) {
         log.warn("Illegal value - setting to -3");
         val = -3;
      }
      if (val > 3) {
         val = 3;
         log.warn("Illegal value - setting to 3");
      }
      // Update row Tabentry value
      entry.rating = "" + val;
      MODEL.updateRow(row);
   }

   public static class jsonString {
      String display;
      JSONObject json;
      public jsonString(JSONObject json, String title) {
         this.display = title;
         this.json = json;
      }
      public String toString() {
         return display;
      }
   }

   private class RatingComparator implements Comparator<Object> {
      public int compare(Object o1, Object o2) {
         Integer i1 = Integer.parseInt(o1.toString());
         Integer i2 = Integer.parseInt(o2.toString());
         if (i1 > i2) return 1;
         if (i1 < i2) return -1;
         return 0;
      }
   }

   public static class Tabentry {
      public String type = "";
      public jsonString show = null;
      public String rating = "0";

      public Tabentry(JSONObject entry) {
         try {
            if (entry.has("collectionType"))
               type = entry.getString("collectionType");
            if (entry.has("title"))
               show = new jsonString(entry, entry.getString("title"));
            if (entry.has("thumbsRating"))
               rating = "" + entry.getInt("thumbsRating");
         } catch (JSONException e1) {
            log.error("thumbsTable Tabentry - " + e1.getMessage());
         }
      }

      public String getTYPE() {
         return type;
      }

      public jsonString getSHOW() {
         return show;
      }

      public String getRATING() {
         return rating;
      }

      public String toString() {
         return show.toString();
      }
   }

   public JSONObject GetRowData(int row) {
      return MODEL.getRow(row).getSHOW().json;
   }

   public String GetValueAt(int row, int col) {
      return MODEL.getValueAt(row, col).toString();
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
      else if (keyCode == KeyEvent.VK_N) {
         int[] selected = TableUtil.GetSelectedRows(TABLE);
         if (selected == null || selected.length < 1)
            return;
         TableUtil.PrintEpisodes(GetRowData(selected[0]));
      }
      else if (keyCode == KeyEvent.VK_C) {
         config.gui.remote_gui.thumbs_tab.copy.doClick();
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
               TableUtil.webQuery(title);
            } catch (JSONException e1) {
               log.error("KeyPressed Q - " + e1.getMessage());
            }
         }
      }
   }

   // Update table to display given entries
   public void AddRows(String tivoName, JSONArray data) {
      try {
         Stack<JSONObject> o = new Stack<JSONObject>();
         for (int i=0; i<data.length(); ++i)
            o.add(data.getJSONObject(i));

         // Update table
         Refresh(o);
         MODEL.sort();
         TableUtil.autoSizeTableViewColumns(TABLE, true);
         if (tivoName != null) {
            tivo_data.put(tivoName, data);
            currentTivo = tivoName;
         }
         if (config.gui.remote_gui != null && tivoName != null) {
            config.gui.remote_gui.setTivoName("thumbs", tivoName);
            refreshNumber();
         }
      } catch (JSONException e) {
         log.error("Thumbs AddRows - " + e.getMessage());
      }
   }

   // Refresh table with given given entries
   public void Refresh(Stack<JSONObject> o) {
      clear();
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
      for (int i=0; i<o.size(); ++i) {
         AddTABLERow(o.get(i));
      }
   }

   private void updateShowRows(String prefix) {
      for (int row=0; row<MODEL.size(); ++row) {
         Tabentry e = MODEL.getRow(row);
         e.show.display = prefix + e.show.display;
      }
   }

   // Add a non folder entry to TABLE table
   public void AddTABLERow(JSONObject entry) {
      debug.print("entry=" + entry);
      MODEL.addRow(new Tabentry(entry));
   }

   // Refresh the # SHOWS label in the ToDo tab
   private void refreshNumber() {
      config.gui.remote_gui.thumbs_tab.label.setText("" + tivo_data.get(currentTivo).length() + " THUMBS");
   }

   public void refreshThumbs(String tivoName) {
      clear();
      setLoaded(false);
      jobData job = new jobData();
      job.source         = tivoName;
      job.tivoName       = tivoName;
      job.type           = "remote";
      job.name           = "Remote";
      job.remote_thumbs  = true;
      job.thumbs         = this;
      jobMonitor.submitNewJob(job);
   }

   // For each row value different that current database, update thumbs value
   public void updateThumbs(final String tivoName) {
      if (isTableLoaded()) {
         log.error("Cannot update a loaded table");
         return;
      }
      try {
         JSONArray changed = new JSONArray();
         for (int row=0; row<MODEL.size(); ++row) {
            String table_value = GetValueAt(row, TableUtil.getColumnIndex(TABLE, "RATING"));
            JSONObject json = GetRowData(row);
            if (json != null) {
               String data_value = "" + json.getInt("thumbsRating");
               if (! table_value.equals(data_value)) {
                  // Make a copy of json so we don't change it
                  JSONObject j = new JSONObject(json.toString());
                  j.put("thumbsRating", Integer.parseInt(table_value));
                  changed.put(j);
               }
            }
         }
         if (changed.length() > 0) {
            // There are table changes, so update in the background
            class backgroundRun implements Runnable {
               JSONArray changed;
               public backgroundRun(JSONArray changed) {
                  this.changed = changed;
               }
               @Override
               public void run() {
                 try {
                     Remote r = config.initRemote(tivoName);
                     if (r.success) {
                        for (int i=0; i<changed.length(); ++i) {
                           JSONObject j = changed.getJSONObject(i);
                           log.print("Updating '" + j.getString("title") + "' to thumbs rating: " + j.getInt("thumbsRating"));
                           JSONObject o = new JSONObject();
                           o.put("bodyId", r.bodyId_get());
                           o.put("collectionId", j.getString("collectionId"));
                           o.put("thumbsRating", j.getInt("thumbsRating"));
                           JSONObject result = r.Command("userContentStore", o);
                           if (result != null) {
                              log.print("Thumbs rating updated");
                           }
                        }
                        r.disconnect();
                     }
                  } catch (JSONException e) {
                     log.error("updateThumbs (1) - " + e.getMessage());
                  }
                  // Now refresh the thumbs table
                  SwingUtil.runLater(new Runnable() {
                     @Override public void run() {
                        refreshThumbs(tivoName);
                     }
                  });
               }
            }
            backgroundRun b = new backgroundRun(changed);
            new Thread(b).start();
         }
      } catch (Exception e) {
         log.error("updateThumbs (2) - " + e.getMessage());
      }
   }

   public void saveThumbs(String tivoName, String file) {
      if (isTableLoaded()) {
         log.error("Cannot save a loaded table");
         return;
      }
      if (tivo_data.containsKey(tivoName) && tivo_data.get(tivoName).length() > 0) {
         log.warn("Saving '" + tivoName + "' Thumbs list to file: " + file);
         JSONFile.write(tivo_data.get(tivoName), file);
      } else {
         log.error("No data available to save.");
      }
   }

   public void loadThumbs(String file) {
      log.print("Loading Thumbs data from file: " + file);
      JSONArray data = JSONFile.readJSONArray(file);
      if (data != null && data.length() > 0) {
         // Clear table and display loaded data
         clear();
         AddRows(null, data);
         updateShowRows(loadedPrefix);
         TableUtil.autoSizeTableViewColumns(TABLE, true);
         setLoaded(true);
      }
   }

   public void copyThumbs(final String tivoName) {
      Runnable task = new Runnable() {
         @Override public void run() {
            int[] selected = TableUtil.GetSelectedRows(TABLE);
            if (selected.length > 0) {
               int row;
               JSONObject json, result;
               Remote r = config.initRemote(tivoName);
               if (r.success) {
                  log.print("Copying thumbs ratings to TiVo: " + tivoName);
                  for (int i=0; i<selected.length; ++i) {
                     row = selected[i];
                     json = GetRowData(row);
                     if (json != null) {
                        try {
                           log.print("Copying: " + json.getString("title"));
                           JSONObject o = new JSONObject();
                           o.put("bodyId", r.bodyId_get());
                           o.put("collectionId", json.getString("collectionId"));
                           o.put("thumbsRating", json.getInt("thumbsRating"));
                           result = r.Command("userContentStore", o);
                           if (result != null)
                              log.print("success");
                        } catch (JSONException e) {
                           log.error("thumbsCopy - " + e.getMessage());
                        }
                     }
                  }
                  r.disconnect();
               }
            }
         }
      };
      new Thread(task).start();
   }

   public Boolean isTableLoaded() {
      return loaded;
   }

   private void setLoaded(Boolean flag) {
      if (flag) {
         loaded = true;
      } else {
         loaded = false;
      }
   }

   public void updateLoadedStatus() {
      if (MODEL.size() > 0) {
         int col = TableUtil.getColumnIndex(TABLE, "SHOW");
         String title = GetValueAt(0,col);
         if (title != null && title.startsWith(loadedPrefix))
            setLoaded(true);
         else
            setLoaded(false);
      }
   }
}
