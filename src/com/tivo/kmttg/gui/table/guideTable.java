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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.Stack;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONConverter;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.TableMap;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.comparator.DateComparator;
import com.tivo.kmttg.gui.comparator.DurationComparator;
import com.tivo.kmttg.gui.remote.util;
import com.tivo.kmttg.gui.sortable.sortableDate;
import com.tivo.kmttg.gui.sortable.sortableDuration;
import com.tivo.kmttg.gui.swing.KmttgTable;
import com.tivo.kmttg.gui.swing.KmttgTableModel;
import com.tivo.kmttg.gui.swing.RowColorer;
import com.tivo.kmttg.gui.swing.SwingUtil;
import com.tivo.kmttg.main.auto;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.rpc.id;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.log;

public class guideTable extends TableMap {
   private String currentTivo = null;
   public JTable TABLE = null;
   public KmttgTableModel<Tabentry> MODEL = null;
   public String[] TITLE_cols = {"DATE", "SHOW", "DUR"};
   private double[] weights = {18, 76, 6};
   public String folderName = null;
   public int folderEntryNum = -1;
   public Hashtable<String,JSONArray> tivo_data = new Hashtable<String,JSONArray>();

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

   public guideTable() {
      MODEL = new KmttgTableModel<Tabentry>(TITLE_cols);
      MODEL.setComparator("DATE", new DateComparator());
      MODEL.setComparator("DUR", new DurationComparator());
      // Default sort is descending date when no column sort is selected
      MODEL.setDefaultSort("DATE", false);
      TABLE = KmttgTable.create(MODEL, new ColorRow());
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
      public java.awt.Color getColor(Tabentry entry) {
         if (entry != null) {
            // Mark rows that are already in To Do
            JSONObject json = entry.getDATE().json;
            if (json != null && json.has("__inTodo__"))
               return TableUtil.tableBkgndProtected;
            // Mark rows with entries in auto history file
            if (config.showHistoryInTable == 1) {
               try {
                  if (json != null && json.has("partnerContentId")) {
                     String programId = json.getString("partnerContentId");
                     programId = programId.replaceFirst("^.+\\.", "");
                     if (auto.keywordMatchHistoryFast(programId, false))
                        return TableUtil.tableBkgndInHistory;
                  }
               } catch (JSONException e) {
                  log.error("guideTable ColorRowFactory - " + e.getMessage());
               }
            }
         }
         return null;
      }
   }

   public static class Tabentry {
      public String title = "";
      public sortableDate date = null;
      public sortableDuration duration = null;

      // Root node constructor
      public Tabentry(String s) {
         // Do nothing
      }

      public Tabentry(JSONObject entry) {
         try {
            // If entry is in 1 of todo lists then add special __inTodo__ JSON entry
            config.gui.remote_gui.flagIfInTodo(entry, false);
            String startString = entry.getString("startTime");
            long start = JSONConverter.getLongDateFromString(startString);
            long dur = entry.getLong("duration")*1000;
            title = JSONConverter.makeShowTitle(entry);
            date = new sortableDate(entry, start);
            duration = new sortableDuration(dur, false);
         } catch (JSONException e1) {
            log.error("AddTABLERow - " + e1.getMessage());
         }
      }

      public sortableDate getDATE() {
         return date;
      }

      public String getSHOW() {
         return title;
      }

      public sortableDuration getDUR() {
         return duration;
      }

      public String toString() {
         return title;
      }
   }

   // This used by remote guide.java when TiVo selection changes
   public void AddRows(String tivoName, JSONArray data) {
      if (data == null) {
         if (currentTivo != null)
            AddRows(currentTivo, tivo_data.get(currentTivo));
         return;
      }

      // update remotegui entries
      if (TABLE != null) {
         // Clear table and update channel list
         clear();
         updateChannels_gui(tivoName, data);
      }
   }

   public JSONObject GetRowData(int row) {
      return MODEL.getRow(row).getDATE().json;
   }

   private void TABLERowSelected(Tabentry entry) {
      // Get column items for selected row
      sortableDate s = entry.getDATE();
      try {
         sortableDuration dur = entry.getDUR();
         String message = TableUtil.makeShowSummary(s, dur);
         String title = "\nGuide: ";
         if (s.json.has("title"))
            title += s.json.getString("title");
         if (s.json.has("subtitle"))
            title += " - " + s.json.getString("subtitle");
         if (s.json.has("__inTodo__"))
            title += " (to be recorded on " + s.json.getString("__inTodo__") + ")";
         log.warn(title);
         log.print(message);

         if (config.gui.show_details.isShowing())
            config.gui.show_details.update(TABLE, currentTivo, s.json);
      } catch (JSONException e) {
         log.error("TABLERowSelected - " + e.getMessage());
         return;
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
      else if (keyCode == KeyEvent.VK_A) {
         int[] selected = TableUtil.GetSelectedRows(TABLE);
         if (selected == null || selected.length < 1)
            return;
         JSONObject json = GetRowData(selected[0]);
         if (json != null)
            auto.AddHistoryEntry(json);
      }
      else if (keyCode == KeyEvent.VK_P) {
         config.gui.remote_gui.guide_tab.recordSP.doClick();
      }
      else if (keyCode == KeyEvent.VK_R) {
         config.gui.remote_gui.guide_tab.record.doClick();
      }
      else if (keyCode == KeyEvent.VK_W) {
         config.gui.remote_gui.guide_tab.wishlist.doClick();
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

   // Return time rounded down to nearest hour in nice display format
   private String getDisplayTime(long gmt) {
      // Round down to nearest hour
      gmt -= gmt % (1000 * 60 * 1);
      SimpleDateFormat sdf = new SimpleDateFormat("E MM/dd/yyyy h a");
      return sdf.format(gmt);
   }

   // Return a range of dates as an array
   private Stack<String> getDisplayTimeRange(long gmt, int hourIncrement, int numDays) {
      Stack<String> range = new Stack<String>();
      long increment = hourIncrement*60*60*1000;
      long stop = gmt + (long)numDays*24*60*60*1000;
      long time = gmt;
      while (time <= stop) {
         range.add(getDisplayTime(time));
         time += increment;
      }
      return range;
   }

   // This converts time as displayed in guide tab combo box to long
   public long displayTimeToLong(String date) {
      try {
         SimpleDateFormat sdf = new SimpleDateFormat("E MM/dd/yyyy h a");
         // First convert to long and then to rpc time format (which is in GMT)
         return sdf.parse(date).getTime();
      } catch (ParseException e) {
         log.error("displayTimeToLong error: " + e.getMessage());
      }
      return 0;

   }

   // Refresh a combo box with new date range
   public void setChoiceBoxDates(JComboBox<String> widget, int hourIncrement, int numDays) {
      // 1st get current setting to restore selection if still relevant
      String current = (String) widget.getSelectedItem();

      long gmt = new Date().getTime();
      widget.removeAllItems();
      Stack<String> dates = getDisplayTimeRange(gmt, hourIncrement, numDays);
      for(int i=0; i<dates.size(); ++i) {
         widget.addItem(dates.get(i));
         if (dates.get(i).equals(current))
            widget.setSelectedIndex(i);
      }
      if (dates.size() > 0)
         widget.setSelectedItem(dates.get(0));
   }

   public void updateChannels(final String tivoName, Boolean force) {
      // If not in force mode, use cached data if available
      if (! force && tivo_data.containsKey(tivoName)) {
         updateChannels_gui(tivoName, tivo_data.get(tivoName));
         return;
      }

      // No data available so queue up a job to get channel list
      // NOTE: The "remote" task is responsible for updating chan_data
      // once this job completes.
      log.warn("Obtaining list of channels for TiVo: " + tivoName);
      jobData job = new jobData();
      job.source                = tivoName;
      job.tivoName              = tivoName;
      job.type                  = "remote";
      job.name                  = "Remote";
      job.gTable                = this;
      job.remote_guideChannels  = true;
      jobMonitor.submitNewJob(job);
   }

   // This is called after "remote" task completes and updates the
   // guide channel list in remotegui according to JSONArray data
   public void updateChannels_gui(String tivoName, JSONArray data) {
      currentTivo = tivoName;
      tivo_data.put(tivoName, data);
      try {
         for (int i=0; i<data.length(); ++i) {
            JSONObject chan = data.getJSONObject(i);
            if (chan.has("channelNumber") && chan.has("callSign")) {
               String name = chan.getString("channelNumber") + "=" + chan.getString("callSign");
               config.gui.remote_gui.guide_tab.ChanList.getItems().addElement(name);
            }
         }
      } catch (JSONException e) {
         log.error("updateChannels_gui - " + e.getMessage());
      }
   }

   // Based on "channelNumber=callSign" setting in remotegui list get the channel json data
   // from tivo_data
   private JSONObject getChanInfo(String tivoName, String chanName) {
      if (tivo_data.containsKey(tivoName)) {
         JSONArray a = tivo_data.get(tivoName);
         String[] l = chanName.split("=");
         String channelNumber = l[0];
         String callSign = l[1];
         try {
            for (int i=0; i<a.length(); ++i) {
               JSONObject chan = a.getJSONObject(i);
               if (chan.has("channelNumber") && chan.has("callSign")) {
                  if (chan.getString("channelNumber").equals(channelNumber) && chan.getString("callSign").equals(callSign)) {
                     return chan;
                  }
               }
            }
         } catch (JSONException e) {
            log.error("getChanInfo - " + e.getMessage());
         }
      }
      return null;
   }

   // This function called from remotegui to update channel information in table
   public void updateTable(final String tivoName, String chanName) {
      JSONObject chan_data = getChanInfo(tivoName, chanName);
      if (chan_data != null) {
         String startDisplayString = config.gui.remote_gui.getGuideStartTime();
         int range = config.gui.remote_gui.guide_tab.range;
         long start = displayTimeToLong(startDisplayString);
         String minEndTime = rnpl.getStringFromLongDate(start);
         long stop = start + range*60*60*1000;
         String maxStartTime = rnpl.getStringFromLongDate(stop);
         String isReceived = null;
         if (! config.gui.remote_gui.AllChannels())
            isReceived = "true";
         updateFolder(tivoName, range, start, minEndTime, stop, maxStartTime, isReceived, chan_data);
      } else {
         log.error("chan_data missing information for channel: " + chanName);
      }
   }

   public void updateFolder(final String tivoName, final int range, final long start,
         final String minEndTime, final long stop, final String maxStartTime, final String isReceived, final JSONObject chan) {
      Runnable task = new Runnable() {
         @Override public void run() {
            Remote r = config.initRemote(tivoName);
            if (r.success) {
               try {
                  JSONObject json = new JSONObject();
                  json.put("bodyId", r.bodyId_get());
                  json.put("levelOfDetail", "medium");
                  if (isReceived != null)
                     json.put("isReceived", isReceived);
                  json.put("orderBy", new JSONArray("[\"channelNumber\"]"));
                  json.put("maxStartTime", maxStartTime);
                  json.put("minEndTime", minEndTime);
                  JSONObject channel = new JSONObject();
                  channel.put("channelNumber", chan.getString("channelNumber"));
                  channel.put("type", "channelIdentifier");
                  channel.put("sourceType", chan.getString("sourceType"));
                  json.put("anchorChannelIdentifier", channel);
                  final JSONObject result = r.Command("gridRowSearch", json);
                  r.disconnect();
                  if( result != null ) {
                     if (result.has("gridRow")) {
                        SwingUtil.runLater(new Runnable() {
                           @Override public void run() {
                              try {
                                 clear();
                                 JSONArray matches = result.getJSONArray("gridRow").getJSONObject(0).getJSONArray("offer");
                                 util.updateTodoIfNeeded("Guide");
                                 for (int i=0; i<matches.length(); ++i) {
                                    MODEL.addRow(new Tabentry(matches.getJSONObject(i)));
                                 }
                                 MODEL.sort();
                                 TableUtil.autoSizeTableViewColumns(TABLE, true);
                              } catch (Exception e) {
                                 if (e.getMessage().contains("not found")) {
                                    log.error("No guide data available for selected date.");
                                 } else
                                    log.error("guideTable updateFolder - " + e.getMessage());
                              }
                           }
                        });
                     } else {
                        log.error(
                           "No guide data available: start=" + minEndTime +
                           ", channel=" + channel.getString("channelNumber")
                        );

                     }
                  }
               } catch (JSONException e1) {
                  log.error("updateFolder - " + e1.getMessage());
               }
            } // if r.success
         }
      };
      new Thread(task).start();
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

   // Create a Season Pass
   public void recordSP(final String tivoName) {
      final int[] selected = TableUtil.GetSelectedRows(TABLE);
      // First check if all selected entries are of type 'series'
      for (int i=0; i<selected.length; ++i) {
         int row = selected[i];
         JSONObject json = GetRowData(row);
         if (json != null) {
            try {
               String type = json.getString("collectionType");
               if (! type.equals("series")) {
                  log.error("Selected entry not of type 'series': " + json.getString("title"));
                  return;
               }
            } catch (JSONException e) {
               log.error("search_sp_recordCB - " + e.getMessage());
               return;
            }
         }
      }

      // Proceed with SP scheduling
      Runnable task = new Runnable() {
         @Override public void run() {
            int[] selected = TableUtil.GetSelectedRows(TABLE);
            if (selected.length > 0) {
               int row;
               JSONArray existing;
               JSONObject json;
               Remote r = config.initRemote(tivoName);
               if (r.success) {
                  // First load existing SPs from tivoName to check against
                  log.warn("Checking existing season passes on '" + tivoName + "' ...");
                  existing = r.SeasonPasses(null);
                  if (existing == null) {
                     log.error("Failed to grab existing SPs to check against for TiVo: " + tivoName);
                     r.disconnect();
                     return;
                  }
                  // Now proceed with subscriptions
                  for (int i=0; i<selected.length; ++i) {
                     row = selected[i];
                     json = GetRowData(row);
                     if (json != null)
                        r.SPschedule(tivoName, json, existing);
                  }
               }
            }
         }
      };
      new Thread(task).start();
   }
}
