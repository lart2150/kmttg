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
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Hashtable;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.swing.SwingUtil;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.util.log;

public class info {
   public JPanel panel = null;
   public JComboBox<String> tivo = null;
   public JButton reboot = null;
   public JTextArea text = null;
   public Hashtable<String,String> tivo_data = new Hashtable<String,String>();
   public Hashtable<String, JButton> buttons = new Hashtable<String, JButton>();

   public info (final JFrame frame) {

      // System Information tab items
      JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

      JLabel title = new JLabel("System Information");

      JLabel tivo_label = new JLabel("");

      tivo = new JComboBox<String>();
      tivo.addActionListener(new ActionListener() {
         @Override public void actionPerformed(ActionEvent e) {
            String newVal = (String)tivo.getSelectedItem();
            if (newVal != null) {
               // Clear text area
               text.setEditable(true);
               text.setText("");

               // Put cached info in text area if available
               String tivoName = newVal;
               if (config.gui.remote_gui != null)
                  config.gui.remote_gui.updateButtonStates(tivoName, "Info");
               if (tivoName != null && tivoName.length() > 0) {
                  if (tivo_data.containsKey(tivoName))
                     text.setText(tivo_data.get(tivoName));
               }
               text.setEditable(false);
            }
         }
      });
      tivo.setToolTipText(tooltip.getToolTip("tivo_info"));

      JButton refresh = new JButton("Refresh");
      refresh.setToolTipText(tooltip.getToolTip("refresh_info"));
      refresh.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            // Refresh info text
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0)
               RC_infoCB(tivoName);
         }
      });

      JButton netconnect = new JButton("Network Connect");
      netconnect.setToolTipText(tooltip.getToolTip("netconnect_info"));
      netconnect.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            // Initiate a net connect on selected TiVo
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               Remote r = config.initRemote(tivoName);
               if (r.success) {
                  JSONObject result = r.Command("PhoneHome", new JSONObject());
                  if (result == null)
                     log.error("NOTE: If this TiVo is in 'Pending Restart' state Network Connect fails.");
                  else
                     log.warn("Network Connection initiated on: " + tivoName);
                  r.disconnect();
               }
            }
         }
      });

      JButton connectStatus = new JButton("Connection Status");
      connectStatus.setToolTipText(tooltip.getToolTip("connectStatus"));
      connectStatus.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            // Print net connect status for selected TiVo
            String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               Remote r = config.initRemote(tivoName);
               if (r.success) {
                  try {
                     JSONObject json = new JSONObject();
                     json.put("bodyId", r.bodyId_get());
                     JSONObject result = r.Command("phoneHomeStatusEventRegister", json);
                     if (result != null) {
                        text.setEditable(true);
                        text.setText(result.toString(3));
                        text.setEditable(false);
                     }
                  } catch (Exception ex) {
                     log.error("connectStatus - " + ex.getMessage());
                  }
                  r.disconnect();
               }
            }
         }
      });

      reboot = new JButton("Reboot");
      reboot.setToolTipText(tooltip.getToolTip("reboot_info"));
      reboot.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            // Reboot selected TiVo
            final String tivoName = (String)tivo.getSelectedItem();
            if (tivoName != null && tivoName.length() > 0) {
               SwingUtil.runLater(new Runnable() {
                  @Override public void run() {
                     int result = JOptionPane.showConfirmDialog(
                        frame, "OK to reboot?", "Reboot " + tivoName + "?",
                        JOptionPane.OK_CANCEL_OPTION
                     );
                     if (result == JOptionPane.OK_OPTION) {
                        Remote r = config.initRemote(tivoName);
                        if (r.success) {
                           r.reboot(tivoName);
                        }
                     }
                  }
               });
            }
         }
      });

      row1.add(title);
      row1.add(tivo_label);
      row1.add(tivo);
      row1.add(util.space(40));
      row1.add(refresh);
      row1.add(netconnect);
      row1.add(connectStatus);
      row1.add(util.space(40));
      row1.add(reboot);

      text = new JTextArea();
      text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, text.getFont().getSize()));
      text.setEditable(false);
      text.setLineWrap(true); // This disables horizontal scrollbar
      text.setWrapStyleWord(true);

      panel = new JPanel(new BorderLayout());
      panel.add(row1, BorderLayout.NORTH);
      panel.add(new JScrollPane(text), BorderLayout.CENTER); // stretch vertically

   }
   private void RC_infoCB(final String tivoName) {
      Runnable task = new Runnable() {
         @Override public void run() {
            log.warn("Collecting info for TiVo: " + tivoName + " ...");
            Remote r = config.initRemote(tivoName);
            if (r.success) {
               JSONObject json = new JSONObject();
               JSONObject reply = r.Command("SysInfo", json);
               if (reply != null && reply.has("bodyConfig")) {
                  try {
                     String info = "";
                     // System info
                     json = reply.getJSONArray("bodyConfig").getJSONObject(0);
                     if (json.has("userDiskSize") && json.has("userDiskUsed")) {
                        Float sizeGB = (float)json.getLong("userDiskSize")/(1024*1024);
                        // Update diskSpace hash if necessary
                        Boolean update = true;
                        if (config.diskSpace.containsKey(tivoName)) {
                           if (Math.round(config.diskSpace.get(tivoName)) == Math.round(sizeGB))
                              update = false;
                        }
                        if (update) {
                           log.warn("Updating " + tivoName + " disk space to " + sizeGB + " GB");
                           config.diskSpace.put(tivoName, sizeGB);
                           config.save();
                        }
                        Float pct = (float)100*json.getLong("userDiskUsed")/json.getLong("userDiskSize");
                        String pct_string = String.format("%s (%5.2f%%)", json.get("userDiskUsed"), pct);
                        String size_string = String.format("%s (%5.2f GB)", json.get("userDiskSize"), sizeGB);
                        json.put("userDiskSize", size_string);
                        json.put("userDiskUsed", pct_string);
                     }
                     if (json.has("bodyId")) {
                        info += String.format("%-30s %s\n", "tsn",
                           json.getString("bodyId").replaceFirst("tsn:", "")
                        );
                     }
                     String[] fields = {
                        "softwareVersion", "userDiskSize", "userDiskUsed", "parentalControlsState"
                     };
                     for (int i=0; i<fields.length; ++i) {
                        if (json.has(fields[i]))
                           info += String.format("%-30s %s\n", fields[i], json.get(fields[i]));
                     }

                     // More detailed info
                     JSONObject j = new JSONObject();
                     j.put("bodyId", r.bodyId_get());
                     JSONObject response = r.Command("systemInformationGet", j);
                     if (response != null) {
                        String integers[] = {
                           "remoteAddress", "recordingCapacityHdHours",
                           "recordingCapacitySdHours", "freeDiskSpaceSdHours",
                           "freeDiskSpaceHdHours"
                        };
                        String strings[] = {
                           "internalTemperature",
                           "zipCode", "remoteBatteryLevel", "activeVideoOutputFormat",
                           "platform", "netflixEsn",
                           "hdcpVersionInfo", "collabSliceVersion",
                           "programInfoTo", "gcCompletionTime", "indexCompletionTime",
                           "dialInCode", "serviceState", "serviceLevel", "serviceMapInfo",
                        };
                        String jsons[] = { "serviceConnectionInfo", "vcmConnectionInfo" };
                        for (int i=0; i<strings.length; ++i) {
                           if (response.has(strings[i]))
                              info += String.format(
                                 "%-30s %s\n", strings[i], response.getString(strings[i])
                              );
                        }
                        for (int i=0; i<jsons.length; ++i) {
                           if (response.has(jsons[i])) {
                              response.getJSONObject(jsons[i]).remove("type");
                              info += String.format(
                                 "%s:\n%s\n", jsons[i], response.getJSONObject(jsons[i]).toString(3)
                              );
                           }
                        }
                        for (int i=0; i<integers.length; ++i) {
                           if (response.has(integers[i]))
                              info += String.format(
                                 "%-30s %d\n", integers[i], response.getInt(integers[i])
                              );
                        }
                     }

                     // What's On info
                     String [] whatson = getWhatsOn(tivoName);
                     if (whatson != null) {
                        info += String.format("%-30s ", "What's On");
                        for (int i=0; i<whatson.length; ++i) {
                           if (i>0)
                              info += "; ";
                           info += whatson[i];
                        }
                        info += "\n";
                     }
                     info += "\n";

                     if (! r.awayMode() ) {
                        // Tuner info
                        reply = r.Command("TunerInfo", new JSONObject());
                        if (reply != null && reply.has("state")) {
                           for (int i=0; i<reply.getJSONArray("state").length(); ++i) {
                              json = reply.getJSONArray("state").getJSONObject(i);
                              info += String.format("%-30s %s\n", "tunerId", json.getString("tunerId"));
                              if (json.has("channel")) {
                                 info += String.format("%-30s %s", "channelNumber",
                                    json.getJSONObject("channel").getString("channelNumber")
                                 );
                                 if (json.getJSONObject("channel").has("callSign"))
                                    info += " (" + json.getJSONObject("channel").getString("callSign") + ")";
                              }
                              info += "\n\n";
                           }
                        }
                     }

                     // Add info to text_info widget
                     text.setEditable(true);
                     text.setText(info);
                     text.setEditable(false);
                     tivo_data.put(tivoName, info);

                  } catch (JSONException e) {
                     log.error("RC_infoCB failed - " + e.getMessage());
                  }
               }
               r.disconnect();
            }
            log.warn("Done collecting info");
         }
      };
      new Thread(task).start();
   }

   private String[] getWhatsOn(String tivoName) {
      Remote r = config.initRemote(tivoName);
      if (r.success) {
         JSONObject result = r.Command("WhatsOn", new JSONObject());
         if (result != null && result.has("whatsOn")) {
            try {
               JSONArray a = result.getJSONArray("whatsOn");
               String display[] = new String[a.length()];
               for (int i=0; i<a.length(); ++i) {
                  JSONObject o = a.getJSONObject(i);
                  if (o.has("playbackType")) {
                     display[i] = "" + o.getString("playbackType");
                     if (! o.getString("playbackType").equals("idle") && o.has("channelIdentifier")) {
                        JSONObject c = o.getJSONObject("channelIdentifier");
                        if (c.has("channelNumber"))
                           display[i] = display[i] + " (channel " + c.getString("channelNumber") + ")";
                     }
                  }
               }
               r.disconnect();
               return display;
            } catch (JSONException e1) {
               log.error("getWhatsOn json error: " + e1.getMessage());
               r.disconnect();
               return null;
            }
         }
         r.disconnect();
      }
      return null;
   }

}
