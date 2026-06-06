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
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Hashtable;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONConverter;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONFile;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.TableMap;
import com.tivo.kmttg.gui.remote.util;
import com.tivo.kmttg.gui.sortable.sortableInt;
import com.tivo.kmttg.gui.swing.KmttgTable;
import com.tivo.kmttg.gui.swing.KmttgTableModel;
import com.tivo.kmttg.gui.swing.RowColorer;
import com.tivo.kmttg.gui.swing.SwingUtil;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.rpc.id;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.log;

public class spTable extends TableMap {
   private String[] TITLE_cols = {"PRI", "SHOW", "INCLUDE", "SEASON", "CHANNEL", "RECORD", "KEEP", "NUM", "START", "END"};
   private double[] weights = {4, 34, 9, 8, 13, 10, 7, 5, 5, 5};
   public JTable TABLE = null;
   public KmttgTableModel<Tabentry> MODEL = null;
   public Hashtable<String,JSONArray> tivo_data = new Hashtable<String,JSONArray>();
   private String currentTivo = null;
   private Boolean loaded = false;

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

   public spTable() {
      MODEL = new KmttgTableModel<Tabentry>(TITLE_cols);
      MODEL.setSortingEnabled(false); // SP priority order matters
      // Column sorting is disabled for all columns in this table (SP order matters)
      TABLE = KmttgTable.create(MODEL, new ColorRow());
      // -fx-alignment: CENTER for all columns except SHOW and CHANNEL
      for (String colName : TITLE_cols) {
         if (! colName.equals("SHOW") && ! colName.equals("CHANNEL"))
            KmttgTable.setColumnAlignment(TABLE, colName, JLabel.CENTER);
      }
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
               if (config.gui.show_details.isShowing())
                  ShowDetails();
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
            JSONObject json = entry.getPRI().json;
            if (json != null && json.has("__conflicts"))
               return TableUtil.lightRed;
         }
         return null;
      }
   }

   public static class Tabentry {
      public sortableInt priority = null;
      public String show = "";
      public String include = "";
      public String season = "";
      public String channel = "";
      public String record = "";
      public String keep = "";
      public String num = "";
      public String start = "";
      public String end = "";

      public Tabentry(JSONObject data, int pri) {
         try {
            priority = new sortableInt(data, pri);
            JSONObject o = new JSONObject();
            JSONObject o2 = new JSONObject();
            show = "";
            if (data.has("title"))
               show += data.getString("title");
            // Manual recordings need more information added
            if (show.equals(" Manual")) {
               String time = data.getJSONObject("idSetSource").getString("timeOfDayLocal");
               time = time.replaceFirst(":\\d+$", "");
               String days = data.getJSONObject("idSetSource").getJSONArray("dayOfWeek").toString();
               days = days.replaceAll("\"", "");
               show += " (" + time + ", " + days + ")";
            }
            // Add upcoming episode counts to title if available
            if (data.has("__upcoming")) {
               int count = data.getJSONArray("__upcoming").length();
               show += " (" + count + ")";
            }

            include = "linear";
            if (data.has("idSetSource")) {
               o = data.getJSONObject("idSetSource");
               if (o.has("consumptionSource"))
                  include = o.getString("consumptionSource");
            }

            season = "1";
            if (data.has("idSetSource")) {
               o = data.getJSONObject("idSetSource");
               if (o.has("startSeasonOrYear"))
                  season = "" + o.getInt("startSeasonOrYear");
               if (o.has("type")) {
                  if (o.getString("type").equals("wishListSource"))
                     season = "0";
               }
            }

            channel = "";
            if (data.has("idSetSource")) {
               o = data.getJSONObject("idSetSource");
               if (o.has("channel")) {
                  o2 = o.getJSONObject("channel");
                  if (o2.has("channelNumber"))
                     channel += o2.getString("channelNumber");
                  if (o2.has("callSign")) {
                     String callSign = o2.getString("callSign");
                     if (callSign.toLowerCase().equals("all channels"))
                        channel += callSign;
                     else
                        channel += "=" + callSign;
                  }
               } else {
                  if (o.has("consumptionSource")) {
                     if (! o.getString("consumptionSource").equals("onDemand"))
                        channel += "All Channels";
                  }
               }
            }
            num = "0";
            if (data.has("maxRecordings"))
               num = "" + data.getInt("maxRecordings");
            start = "0";
            if (data.has("startTimePadding"))
               start = "" + data.getInt("startTimePadding")/60;
            end = "0";
            if (data.has("endTimePadding"))
               end = "" + data.getInt("endTimePadding")/60;
            record = "";
            if (data.has("showStatus"))
               record = data.getString("showStatus");
            keep = "";
            if (data.has("keepBehavior"))
               keep = data.getString("keepBehavior");
         } catch (Exception e) {
            log.error("spTable Tabentry - " + e.getMessage());
         }
      }

      public sortableInt getPRI() {
         return priority;
      }

      public String getSHOW() {
         return show;
      }

      public String getINCLUDE() {
         return include;
      }

      public String getSEASON() {
         return season;
      }

      public String getCHANNEL() {
         return channel;
      }

      public String getRECORD() {
         return record;
      }

      public String getKEEP() {
         return keep;
      }

      public String getNUM() {
         return num;
      }

      public String getSTART() {
         return start;
      }

      public String getEND() {
         return end;
      }

      public String toString() {
         return show;
      }
   }

    // Add given data to table
    public Boolean AddRows(JSONArray data) {
       try {
          for (int i=0; i<data.length(); ++i) {
             AddRow(data.getJSONObject(i), i+1);
          }
          TableUtil.autoSizeTableViewColumns(TABLE, true);
       } catch (JSONException e) {
          log.error("spTable AddRows - " + e.getMessage());
          return false;
       }
       return true;
    }

    // Add rows and save to tivo_data
    public void AddRows(String tivoName, JSONArray data) {
       if (AddRows(data)) {
          tivo_data.put(tivoName, data);
          currentTivo = tivoName;
          if (config.gui.remote_gui != null)
             config.gui.remote_gui.setTivoName("sp", tivoName);
       }
    }

    private void AddRow(JSONObject data, int priority) {
       try {
          data.put("__priority__", priority);
       } catch (JSONException e) {
          log.error("AddRow - " + e.getMessage());
       }
       MODEL.addRow(new Tabentry(data, priority));
    }

    public void setSelectedRow(int row) {
       TABLE.addRowSelectionInterval(row, row);
       TableUtil.scrollToCenter(TABLE, row);
    }

    public JSONObject GetRowData(int row) {
       return MODEL.getRow(row).getPRI().json;
    }

    public String GetRowTitle(int row) {
       String s = (String) MODEL.getRow(row).getSHOW();
       if (s != null)
          return s;
       return null;
    }

    public void updateTitleCols(String name) {
       for (int row=0; row<MODEL.size(); ++row) {
          Tabentry e = MODEL.getRow(row);
          e.show = name + e.show;
       }
    }

    private void InsertRow(int row, JSONObject data) {
       try {
          MODEL.getRows().add(row, new Tabentry(data, data.getInt("__priority__")));
          MODEL.fireTableDataChanged();
       } catch (JSONException e) {
          log.error("spTable InsertRow - " + e.getMessage());
       }
    }

    public void RemoveRow(int row) {
       MODEL.removeRow(row);
    }

    private void changeRowPrompt(int from) {
       int from_prompt = from + 1;
       String result = JOptionPane.showInputDialog(
          config.gui.getFrame(),
          "Eenter desired new priority #:",
          "Change Priority",
          JOptionPane.QUESTION_MESSAGE
       );
       if (result != null) {
           String answer = result;
           try {
              int to = Integer.parseInt(answer) - 1;
              if (to >= 0 && to < MODEL.size()) {
                 if (to != from) {
                    JSONObject data = GetRowData(from);
                    RemoveRow(from);
                    InsertRow(to, data);
                    TABLE.addRowSelectionInterval(to, to);
                    TableUtil.scrollToCenter(TABLE, to);
                 }
              } else {
                 log.error("Invalid priority number entered: " + answer);
              }
           } catch (NumberFormatException e) {
              log.error("Invalid priority number entered: " + answer);
           }
       }
    }

    public Boolean isTableLoaded() {
       return loaded;
    }

    public void setLoaded(Boolean flag) {
       if (flag) {
          loaded = true;
       } else {
          loaded = false;
       }
    }

    public void updateLoadedStatus() {
       if (MODEL.size() > 0) {
          String title = GetRowTitle(0);
          if (title != null && title.startsWith(" Loaded"))
             setLoaded(true);
          else
             setLoaded(false);
       }
    }

    private Boolean removeJson(String tivoName, JSONObject json) {
       Boolean removed = false;
       try {
          for (int i=0; i<tivo_data.get(tivoName).length(); ++i) {
            if (tivo_data.get(tivoName).get(i) == json) {
                tivo_data.get(tivoName).remove(i);
                removed = true;
                break;
            }
          }
       } catch (JSONException e) {
          log.print("removeJson - " + e.getMessage());
       }
       return removed;
    }

    // Return array of subscriptionId's according to current table row order
    public JSONArray GetOrderedIds() {
       int count = MODEL.size();
       if (count == 0) {
          log.error("Table is empty");
          return null;
       }
       if (isTableLoaded()) {
          log.error("Cannot re-order SPs from loaded file.");
          return null;
       }
       JSONArray array = new JSONArray();
       sortableInt s;
       for (int row=0; row<count; ++row) {
          s = MODEL.getRow(row).getPRI();
          if (s != null && s.json.has("subscriptionId")) {
             try {
                array.put(s.json.getString("subscriptionId"));
             } catch (JSONException e) {
                log.error("GetOrderedIds - " + e.getMessage());
                return null;
             }
          }
       }
       if (array.length() == count)
          return array;
       else
          return null;
    }

    private void ShowDetails() {
       if (currentTivo == null)
          return;
       int[] selected = TableUtil.GetSelectedRows(TABLE);
       if (selected == null || selected.length < 1)
          return;
       config.gui.show_details.update(TABLE, currentTivo, GetRowData(selected[0]));
    }

    // Handle keyboard presses
    private void KeyPressed(KeyEvent e) {
       if (e.isControlDown())
          return;
       int keyCode = e.getKeyCode();
       if (keyCode == KeyEvent.VK_J) {
          // Print json of selected row to log window
          int[] selected = TableUtil.GetSelectedRows(TABLE);
          if (selected == null || selected.length < 1)
             return;
          JSONObject json = GetRowData(selected[0]);
          if (json != null) {
             rnpl.pprintJSON(json);
             if (json.has("idSetSource")) {
                try {
                  id.printIds(json.getJSONObject("idSetSource"));
               } catch (JSONException e1) {}
             }
          }
       }
       else if (keyCode == KeyEvent.VK_N) {
          int[] selected = TableUtil.GetSelectedRows(TABLE);
          if (selected == null || selected.length < 1)
             return;
          TableUtil.PrintEpisodes(GetRowData(selected[0]));
       }
       else if (keyCode == KeyEvent.VK_C) {
          config.gui.remote_gui.sp_tab.copy.doClick();
       }
       else if (keyCode == KeyEvent.VK_M) {
          config.gui.remote_gui.sp_tab.modify.doClick();
       }
       else if (keyCode == KeyEvent.VK_P) {
          int[] selected = TableUtil.GetSelectedRows(TABLE);
          if (selected == null || selected.length < 1)
             return;
          changeRowPrompt(selected[0]);
       }
       else if (keyCode == KeyEvent.VK_U) {
          config.gui.remote_gui.sp_tab.upcoming.doClick();
       }
       else if (keyCode == KeyEvent.VK_O) {
          config.gui.remote_gui.sp_tab.conflicts.doClick();
       }
       else if (keyCode == KeyEvent.VK_I) {
          ShowDetails();
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
       else if (keyCode == KeyEvent.VK_Z) {
          // Check OnePass stationId vs available guide channel data
          String tivoName = config.gui.remote_gui.getTivoName("sp");
          if (tivoName != null)
             CheckOnePasses(tivoName);
       }
       else if (keyCode == KeyEvent.VK_DELETE) {
          // Remove selected row from TiVo and table
          e.consume(); // Need this so as not to remove focus which is default key action
          SPListDelete();
       }
       else if (keyCode == KeyEvent.VK_UP) {
          // Move selected row up
          int[] selected = TableUtil.GetSelectedRows(TABLE);
          if (selected == null || selected.length < 0) {
             log.error("No rows selected");
             return;
          }
          if (isTableLoaded()) {
             log.error("Cannot re-order loaded season passes");
             return;
          }
          int row;
          for (int i=0; i<selected.length; ++i) {
             row = selected[i];
             if (row-1 >= 0) {
                JSONObject data = GetRowData(row);
                RemoveRow(row);
                InsertRow(row-1, data);
             }
          }
       }
       else if (keyCode == KeyEvent.VK_DOWN) {
          // Move selected row down
          int[] selected = TableUtil.GetSelectedRows(TABLE);
          if (selected == null || selected.length < 0) {
             log.error("No rows selected");
             return;
          }
          if (isTableLoaded()) {
             log.error("Cannot re-order loaded season passes");
             return;
          }
          int row;
          for (int i=0; i<selected.length; ++i) {
             row = selected[i];
             if (row < MODEL.size()-1) {
                JSONObject data = GetRowData(row);
                RemoveRow(row);
                InsertRow(row+1, data);
                TABLE.addRowSelectionInterval(row, row);
             }
          }
       }
    }

    // Delete selected Season Pass entries from TiVo and table
    public void SPListDelete() {
       // Remove selected row from TiVo and table
       int[] selected = TableUtil.GetSelectedRows(TABLE);
       if (selected == null || selected.length < 1) {
          log.error("No rows selected");
          return;
       }
       if (currentTivo == null) {
          log.error("Table not initialized");
          return;
       }
       int smallest = -1;
       int row;
       JSONObject json;
       String title;
       Remote r = config.initRemote(currentTivo);
       if (r.success) {
          for (int i=0; i<selected.length; ++i) {
             row = selected[i];
             if (smallest == -1)
                smallest = row;
             if (row < smallest)
                smallest = row;
             json = GetRowData(row);
             if (json != null) {
                try {
                   title = json.getString("title");
                   if (isTableLoaded()) {
                      log.error("Cannot unsubscribe loaded Season Passes. Refresh list for TiVo passes");
                   } else {
                      log.warn("Deleting SP on TiVo '" + currentTivo + "': " + title);
                      JSONObject o = new JSONObject();
                      o.put("subscriptionId", json.getString("subscriptionId"));
                      if ( r.Command("Unsubscribe", o) != null ) {
                         RemoveRow(row);
                         smallest -= 1;
                         if (smallest < 0)
                            smallest = 0;
                         if (MODEL.size() > 0)
                            setSelectedRow(smallest);
                         // Find and remove data entry
                         removeJson(currentTivo, json);
                      }
                   }
                } catch (JSONException e1) {
                   log.error("SP delete - " + e1.getMessage());
                }
             }
          }
          r.disconnect();
       }
    }

    public void SPListSave(String tivoName, String file) {
       if (tivo_data.containsKey(tivoName) && tivo_data.get(tivoName).length() > 0) {
          log.warn("Saving '" + tivoName + "' SP list to file: " + file);
          JSONFile.write(tivo_data.get(tivoName), file);
       } else {
          log.error("No data available to save.");
       }
    }

    public void SPListLoad(String file) {
       log.print("Loading SP data from file: " + file);
       JSONArray data = JSONFile.readJSONArray(file);
       if (data != null && data.length() > 0) {
          // Remove __upcoming && __conflicts entries if there are any
          try {
             for (int i=0; i<data.length(); ++i) {
                if (data.getJSONObject(i).has("__upcoming"))
                   data.getJSONObject(i).remove("__upcoming");
                if (data.getJSONObject(i).has("__conflicts"))
                   data.getJSONObject(i).remove("__conflicts");
             }
          } catch (JSONException e1) {
             log.error("SPListLoad - " + e1.getMessage());
          }

          // Now clear table and display loaded data
          clear();
          AddRows(data);
          updateTitleCols(" Loaded:");
          setLoaded(true);
       }
    }

    // NOTE: The current table structure + sorting is used
    public void SPListExport(String tivoName, String file) {
       if (tivo_data.containsKey(tivoName) && tivo_data.get(tivoName).length() > 0) {
          try {
             log.warn("Exporting '" + tivoName + "' SP list to csv file: " + file);
             BufferedWriter ofp = new BufferedWriter(new FileWriter(file));
             int col = 0;
             int numCols = TITLE_cols.length;
             // Write column headers
             for (String name : TITLE_cols) {
                ofp.write(name);
                if (col<numCols-1)
                   ofp.write(",");
             }
             ofp.write("\r\n");
             for (int row=0; row<MODEL.size(); row++) {
                for (int c=0; c<TITLE_cols.length; c++) {
                   String colName = TITLE_cols[c];
                   String val = MODEL.getValueAt(row, c).toString();
                   val = val.trim();
                   if (colName.equals("SHOW")) {
                      val = val.replaceFirst("\\(\\d+\\)$", "");
                   }
                   ofp.write("\"" + val + "\",");
                }
                ofp.write("\r\n");
             }
             ofp.close();
          } catch (Exception e) {
             log.error("SPListExport - " + e.getMessage());
          }
       } else {
         log.error("No data available to export.");
       }
    }

    public void SPListCopy(final String tivoName) {
       Runnable task = new Runnable() {
          @Override public void run() {
             //SeasonPasses
             int[] selected = TableUtil.GetSelectedRows(TABLE);
             if (selected.length > 0) {
                int row;
                JSONArray existing;
                JSONObject json, result;
                Remote r = config.initRemote(tivoName);
                if (r.success) {
                   // First load existing SPs from tivoName to check against
                   existing = r.SeasonPasses(null);
                   if (existing == null) {
                      log.error("Failed to grab existing SPs to check against for TiVo: " + tivoName);
                      r.disconnect();
                      return;
                   }
                   // Now proceed with subscriptions
                   log.print("Copying Season Passes to TiVo: " + tivoName);
                   for (int i=0; i<selected.length; ++i) {
                      row = selected[i];
                      json = GetRowData(row);
                      if (json != null) {
                         try {
                            // Check against existing
                            String title = json.getString("title");
                            String channel = "";
                            if (json.has("channel")) {
                               JSONObject o = json.getJSONObject("channel");
                               if (o.has("callSign"))
                                  channel = o.getString("callSign");
                            }
                            Boolean schedule = true;
                            for (int j=0; j<existing.length(); ++j) {
                               JSONObject e = existing.getJSONObject(j);
                               if(title.equals(e.getString("title"))) {
                                  if (channel.length() > 0 && e.has("idSetSource")) {
                                     JSONObject id = e.getJSONObject("idSetSource");
                                     if (id.has("channel")) {
                                        JSONObject c = id.getJSONObject("channel");
                                        String callSign = "";
                                        if (c.has("callSign"))
                                           callSign = c.getString("callSign");
                                        if (channel.equals(callSign)) {
                                           schedule = false;
                                        }
                                     }
                                  } else {
                                     schedule = false;
                                  }
                               }
                            }

                            // OK to subscribe
                            if (schedule) {
                               log.print("Scheduling: " + json.getString("title"));
                               result = r.Command("Seasonpass", json);
                               if (result != null)
                                  log.print("success");
                            } else {
                               log.warn("Existing SP with same title + callSign found, not scheduling: " +
                                  json.getString("title")
                               );
                            }
                         } catch (JSONException e) {
                            log.error("SPListCopy - " + e.getMessage());
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

    public void SPListModify(final String tivoName) {
       if (isTableLoaded()) {
          log.error("Cannot modify SPs from loaded file.");
          return;
       }
       int[] selected = TableUtil.GetSelectedRows(TABLE);
       if (selected.length > 0) {
          final int row = selected[0];
          final JSONObject json = GetRowData(row);
          if (json != null) {
             try {
                final String title = json.getString("title");
                final JSONObject result = util.spOpt.promptUser(
                   tivoName, "(" + tivoName + ")" + "Modify SP - " + title, json, JSONConverter.isWL(json)
                );
                if (result != null) {
                   Runnable task = new Runnable() {
                      @Override public void run() {
                         Remote r = config.initRemote(tivoName);
                         if (r.success) {
                            if (r.Command("ModifySP", result) != null) {
                               log.warn("Modified SP '" + title + "' for TiVo: " + tivoName);
                            }

                            // Update SP table
                            log.warn(">> Updating table, please be patient...");
                            final JSONArray a = r.SeasonPasses(new jobData());
                            if( a != null) {
                               log.warn(">> Finished updating table");
                               SwingUtil.runLater(new Runnable() {
                                  @Override public void run() {
                                     clear();
                                     AddRows(tivoName, a);
                                     setSelectedRow(row);
                                  }
                               });
                            }
                            r.disconnect();
                         }
                      }
                   };
                   new Thread(task).start();
                } // result != null
             } catch (JSONException e) {
                log.error("SPListModify error: " + e.getMessage());
             }
          } // json != null
       } // selected.length > 0
    }

    // Check OnePass stationId vs available guide channel data
    private void CheckOnePasses(String tivoName) {
       log.print("Checking OnePasses for TiVo: " + tivoName + " ...");
       Runnable task = new Runnable() {
          @Override public void run() {
             Remote r = config.initRemote(tivoName);
             if (r.success) {
                r.checkOnePasses(tivoName);
                r.disconnect();
             }
          }
       };
       new Thread(task).start();
    }

    // Update SP priority order to match current SP table
    public void SPReorderCB(String tivoName) {
       JSONArray order = GetOrderedIds();
       if (order != null) {
          jobData job = new jobData();
          job.source           = tivoName;
          job.tivoName         = tivoName;
          job.type             = "remote";
          job.name             = "Remote";
          job.remote_spreorder = true;
          job.remote_orderIds  = order;
          jobMonitor.submitNewJob(job);
       }
    }

    public static void SaveToFile(String tivoName, String file) {
    	try  {
			spTable tab = new spTable();

        jobData job = new jobData();
        job.source      = tivoName;
        job.tivoName    = tivoName;
        job.type        = "remote";
        job.name        = "Remote";
        job.remote_sp   = true;
        job.sp          = tab;
        log.warn("Querying '" + tivoName + "' for SP list");
        jobMonitor.submitNewJob(job);

        while (job.check()) {
        	log.warn("tick");
        	try {
        		Thread.sleep(5000);
        	} catch (Exception e) {}
        }
        tab.SPListSave(tivoName, file);
    	} catch (Exception e) {
    		e.printStackTrace();
    		System.exit(-1);
    	}
    }
}
