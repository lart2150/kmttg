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
package com.tivo.kmttg.gui.remote;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Hashtable;
import java.util.Stack;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.MyListView;
import com.tivo.kmttg.gui.swing.SwingUtil;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.table.premiereTable;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;

public class premiere {
   public JPanel panel = null;
   public premiereTable tab = null;
   public JComboBox<String> tivo = null;
   public JComboBox<String> days = null;
   public MyListView channels = null;
   public Hashtable<String,JSONArray> channel_info = new Hashtable<String,JSONArray>();
   public JButton record = null;
   public JButton recordSP = null;
   public JButton wishlist = null;

   public premiere(final JFrame frame) {

      // Premiere tab items
      JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

      JLabel title = new JLabel("Season Premieres");

      JLabel tivo_label = new JLabel();

      tivo = new JComboBox<String>();
      tivo.addActionListener(new ActionListener() {
         @Override public void actionPerformed(ActionEvent e) {
            String newVal = (String)tivo.getSelectedItem();
            if (newVal != null) {
               // Clear channel list
               channels.getItems().clear();

               final String tivoName = newVal;
               if (config.gui.remote_gui != null)
                  config.gui.remote_gui.updateButtonStates(tivoName, "Season Premieres");
               // Load channel list for this TiVo
               SwingUtil.runLater(new Runnable() {
                  @Override
                  public void run() {
                     loadChannelInfo(tivoName);
                  }
               });
            }
         }
      });
      tivo.setToolTipText(tooltip.getToolTip("tivo_premiere"));

      JLabel days_label = new JLabel("Days");
      days = new JComboBox<String>();
      days.setToolTipText(tooltip.getToolTip("premiere_days"));
      for (int i=1; i<=12; ++i) {
         days.addItem("" + i);
      }
      days.setSelectedItem("12");

      JButton refresh = new JButton("Search");
      refresh.setToolTipText(tooltip.getToolTip("refresh_premiere"));
      refresh.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            // Refresh table
            TableUtil.clear(tab.TABLE);
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               // This updates premiere_channel_info "isSelected" settings
               if ( ! updateSelectedChannels(tivoName) )
                  return;

               // Save channel information to file
               saveChannelInfo(tivoName);

               // Now search for Premieres in background mode
               jobData job = new jobData();
               job.source          = tivoName;
               job.tivoName        = tivoName;
               job.type            = "remote";
               job.name            = "Remote";
               job.remote_premiere = true;
               job.premiere        = tab;
               jobMonitor.submitNewJob(job);
            }
         }
      });

      record = new JButton("Record");
      record.setToolTipText(tooltip.getToolTip("record_premiere"));
      record.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0)
               tab.recordSingle(tivoName);
         }
      });

      recordSP = new JButton("Season Pass");
      recordSP.setToolTipText(tooltip.getToolTip("recordSP_premiere"));
      recordSP.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0)
               tab.recordSP(tivoName);
         }
      });

      wishlist = new JButton("WL");
      wishlist.setToolTipText(tooltip.getToolTip("wishlist_search"));
      wishlist.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               int[] selected = TableUtil.GetSelectedRows(tab.TABLE);
               JSONObject json = null;
               if (selected.length > 0)
                  json = tab.GetRowData(selected[0]);
               config.gui.remote_gui.createWishlist(tivoName, json);
            }
         }
      });

      JButton channels_update = new JButton("Update Channels");
      channels_update.setToolTipText(tooltip.getToolTip("premiere_channels_update"));
      channels_update.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               // Build list of received channels for this TiVo
               jobData job = new jobData();
               job.source          = tivoName;
               job.tivoName        = tivoName;
               job.type            = "remote";
               job.name            = "Remote";
               job.remote_channels = true;
               jobMonitor.submitNewJob(job);
            }
         }
      });

      channels = new MyListView();
      channels.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
      channels.setToolTipText(tooltip.getToolTip("premiere_channels"));

      row1.add(title);
      row1.add(tivo_label);
      row1.add(tivo);
      row1.add(refresh);
      row1.add(days);
      row1.add(days_label);
      row1.add(record);
      row1.add(recordSP);
      row1.add(wishlist);
      row1.add(util.space(40));
      row1.add(channels_update);

      tab = new premiereTable();

      JScrollPane chanScroll = new JScrollPane(channels);
      chanScroll.setMinimumSize(new Dimension(150, 0));
      chanScroll.setPreferredSize(new Dimension(150, 0));

      JPanel row2 = new JPanel(new BorderLayout(5, 0));
      row2.add(new JScrollPane(tab.TABLE), BorderLayout.CENTER);
      row2.add(chanScroll, BorderLayout.EAST);

      panel = new JPanel(new BorderLayout());
      panel.add(row1, BorderLayout.NORTH);
      panel.add(row2, BorderLayout.CENTER);
   }

   // Read channel info from a file
   // Columns are:
   // channelNumber, callSign, channelId, stationId, sourceType, isSelected
   public void loadChannelInfo(String tivoName) {
      String fileName = config.programDir + File.separator + tivoName + ".channels";
      JSONArray fullList = null;
      if (file.isFile(fileName)) {
         try {
            JSONArray a = new JSONArray();
            String line;
            Stack<Integer> selected = new Stack<Integer>();
            int count = 0;
            BufferedReader ifp = new BufferedReader(new FileReader(fileName));
            while ( (line=ifp.readLine()) != null ) {
               line = line.replaceFirst("^\\s*(.*$)", "$1");
               line = line.replaceFirst("^(.*)\\s*$", "$1");
               if (line.length() == 0) continue; // skip empty lines
               if (line.matches("^#.+")) continue; // skip comment lines
               String Fields[] = line.split(", ");
               JSONObject json = new JSONObject();
               if (Fields.length == 6) {
                  json.put("channelNumber", Fields[0]);
                  json.put("callSign", Fields[1]);
                  json.put("channelId", Fields[2]);
                  json.put("stationId", Fields[3]);
                  json.put("sourceType", Fields[4]);
                  json.put("isSelected", Fields[5]);
                  if (Fields[5].equals("true"))
                     selected.add(count);
                  a.put(json);
                  count++;
               } else {
                  // Legacy file format has only 4 fields
                  // So get channel list to fill in missing information
                  if (fullList == null) {
                     log.print("Legacy channel file - fixing to new format");
                     Remote r = config.initRemote(tivoName);
                     if (r.success) {
                        fullList = r.ChannelList(null, true);
                        r.disconnect();
                     }
                  }
                  json.put("channelNumber", Fields[0]);
                  json.put("callSign", Fields[1]);
                  json.put("sourceType", Fields[2]);
                  json.put("isSelected", Fields[3]);
                  if (fullList != null) {
                     for (int i=0; i<fullList.length(); ++i) {
                        JSONObject c = fullList.getJSONObject(i);
                        if (c.has("isReceived") && c.getBoolean("isReceived")) {
                           if (c.has("channelNumber")) {
                              String channelNumber = c.getString("channelNumber");
                              if (channelNumber.equals(Fields[0])) {
                                 if (c.has("channelId"))
                                    json.put("channelId", c.getString("channelId"));
                                 if (c.has("stationId"))
                                    json.put("stationId", c.getString("stationId"));
                              }
                           }
                        }
                     }
                  }
                  if (Fields[3].equals("true"))
                     selected.add(count);
                  a.put(json);
                  count++;
               }
            }
            ifp.close();
            if (a.length() > 0) {
               putChannelData(tivoName, a);
            }
            if (selected.size() > 0) {
               for (int i=0; i<selected.size(); ++i) {
                  channels.addSelectionInterval(selected.get(i), selected.get(i));
               }
            }
         } catch (Exception e1) {
            log.error("loadChannelInfo - " + e1.getMessage());
         }
      }
   }

   public Boolean updateSelectedChannels(String tivoName) {
      try {
         // Reset "isSelected" entries for premiere_channel_info for this TiVo
         if (channel_info.containsKey(tivoName)) {
            for (int i=0; i<channel_info.get(tivoName).length(); ++i) {
               channel_info.get(tivoName).getJSONObject(i).put("isSelected", "false");
            }

            // Set "isSelected" to true for selected ones
            int[] sel = channels.getSelectedIndices();
            if (sel.length < 1) {
               log.error("No channels selected in channel list for processing.");
               return false;
            }
            for (int row : sel) {
               channel_info.get(tivoName).getJSONObject(row).put("isSelected", "true");
            }
         } else {
            log.error("No channel information available - use Channels button to get list of channels");
            return false;
         }
      } catch (JSONException e1) {
         log.error("channelInfoToArray - " + e1.getMessage());
         return false;
      }
      return true;
   }

   // Return channel information for selected entries in channel list
   // NOTE: This is called from remote "premieres" task
   public JSONArray getSelectedChannelData(String tivoName) {
      JSONArray a = new JSONArray();
      try {
         if (channel_info.containsKey(tivoName)) {
            int[] sel = channels.getSelectedIndices();
            for (int row : sel) {
               a.put(channel_info.get(tivoName).getJSONObject(row));
            }
         }
      } catch (JSONException e) {
         log.error("getSelectedChannelData - " + e.getMessage());
      }
      return a;
   }

   // Populate channel list for given TiVo in Premieres tab
   // NOTE: This is called from remote "channels" task
   public void putChannelData(String tivoName, JSONArray channelInfo) {
      // 1st save selected channels list in a hash for easy access
      int[] sel = channels.getSelectedIndices();
      Hashtable<String,Boolean> h = new Hashtable<String,Boolean>();
      for (int row : sel) {
         String channelNumber = channels.getItems().get(row);
         h.put(channelNumber, true);
      }

      // Now reset GUI list and global
      channel_info.put(tivoName, channelInfo);
      channels.getItems().clear();
      try {
         String channelNumber, callSign;
         DefaultListModel<String> oblist = channels.getItems();
         for (int i=0; i<channelInfo.length(); ++i) {
            JSONObject c = channelInfo.getJSONObject(i);
            if (c.has("channelNumber") && c.has("callSign")) {
               channelNumber = c.getString("channelNumber");
               callSign = c.getString("callSign");
               oblist.addElement(channelNumber + "=" + callSign);
            }
         }

         // Re-select channels if available
         for (int k=0; k<channels.getItems().size(); ++k) {
            channelNumber = channels.getItems().get(k);
            JSONObject json = channel_info.get(tivoName).getJSONObject(k);
            if (h.containsKey(channelNumber)) {
               channels.addSelectionInterval(k, k);
               json.put("isSelected", "true");
            } else {
               json.put("isSelected", "false");
            }
         }
      } catch (JSONException e) {
         log.error("putChannelData - " + e.getMessage());
      }
   }

   // Write channel info to a file
   // Columns are:
   // channelNumber, callSign, channelId, stationId, sourceType, isSelected
   public void saveChannelInfo(String tivoName) {
       try {
         JSONObject json;
         if (channel_info.containsKey(tivoName)) {
            String fileName = config.programDir + File.separator + tivoName + ".channels";
            log.warn("Saving channel info to file: " + fileName);
            BufferedWriter ofp = new BufferedWriter(new FileWriter(fileName));
            String eol = "\r\n";
            ofp.write("#channelNumber, callSign, channelId, stationId, sourceType, isSelected" + eol);
            for (int i=0; i<channel_info.get(tivoName).length(); ++i) {
               json = channel_info.get(tivoName).getJSONObject(i);
               String channelId = "none";
               String stationId = "none";
               if (json.has("channelId"))
                  channelId = json.getString("channelId");
               if (json.has("stationId"))
                  stationId = json.getString("stationId");
               ofp.write(json.getString("channelNumber"));
               ofp.write(", " + json.getString("callSign"));
               ofp.write(", " + channelId);
               ofp.write(", " + stationId);
               ofp.write(", " + json.getString("sourceType"));
               ofp.write(", " + json.getString("isSelected") + eol);
            } // for
            ofp.close();
         }
      } catch (Exception e1) {
         log.error("saveChannelInfo - " + e1.getMessage());
      }
   }

   // NOTE: This called as part of a background job
   public void TagPremieresWithSeasonPasses(JSONArray data) {
      log.warn("Collecting information on existing Season Passes...");
      for (String tivoName : util.getFilteredTivoNames()) {
         Remote r = config.initRemote(tivoName);
         if (r.success) {
            JSONArray existing = r.SeasonPasses(null);
            if (existing != null) {
               // Add special json entry to mark entries that already have season passes
               String sp_id, entry_id;
               try {
                  for (int i=0; i<existing.length(); ++i) {
                     JSONObject sp_json = existing.getJSONObject(i);
                     if (sp_json.has("idSetSource")) {
                        JSONObject sp_source = sp_json.getJSONObject("idSetSource");
                        if (sp_source.has("collectionId")) {
                           sp_id = sp_source.getString("collectionId");
                           for (int j=0; j<data.length(); ++j) {
                              JSONObject entry_json = data.getJSONObject(j);
                              if (entry_json.has("collectionId")) {
                                 entry_id = entry_json.getString("collectionId");
                                 if (sp_id.equals(entry_id)) {
                                    // Add flag to JSON object indicating it's already a scheduled SP on this TiVo
                                    if (data.getJSONObject(j).has("__SPscheduled__")) {
                                       data.getJSONObject(j).put("__SPscheduled__",
                                          data.getJSONObject(j).getString("__SPscheduled__") +
                                          ", " + tivoName
                                       );
                                    } else {
                                       data.getJSONObject(j).put("__SPscheduled__", tivoName);
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               } catch (JSONException e1) {
                  log.error("RC keyPressed - " + e1.getMessage());
               }
            }
            r.disconnect();
         }
      }
   }
}
