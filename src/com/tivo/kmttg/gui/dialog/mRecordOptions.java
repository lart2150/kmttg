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
package com.tivo.kmttg.gui.dialog;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.swing.SwingUtil;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.util.log;

public class mRecordOptions {
   JPanel components;
   JComboBox<String> often, day, channel;
   JComboBox<String> start_hour, start_min, start_ampm, dur_hour, dur_min;
   JCheckBox mon,tue,wed,thu,fri,sat,sun;
   String tivoName;
   Hashtable<String,JSONObject> channelHash = new Hashtable<String,JSONObject>();

   public mRecordOptions() {
      createComponents();
   }

   private void createComponents() {
      often = new JComboBox<String>();
      often.addItem("Once");
      often.addItem("Repeat");
      often.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            String newVal = (String)often.getSelectedItem();
            if (newVal != null) {
               if (newVal.equals("Once"))
                  enableDays(false);
               else
                  enableDays(true);
            }
         }
      });
      often.setSelectedItem("Once");
      channel = new JComboBox<String>();
      start_hour = new JComboBox<String>();
      start_min = new JComboBox<String>();
      start_ampm = new JComboBox<String>();
      dur_hour = new JComboBox<String>();
      dur_min = new JComboBox<String>();

      day = new JComboBox<String>();
      mon = new JCheckBox("Mon");
      tue = new JCheckBox("Tue");
      wed = new JCheckBox("Wed");
      thu = new JCheckBox("Thu");
      fri = new JCheckBox("Fri");
      sat = new JCheckBox("Sat");
      sun = new JCheckBox("Sun");

      dur_hour.addItem("00");
      for (int hour=1; hour<=12; hour++) {
         start_hour.addItem(String.format("%02d", hour));
         dur_hour.addItem(String.format("%02d", hour));
      }
      start_hour.setSelectedItem(start_hour.getItemAt(0));
      dur_hour.setSelectedItem(dur_hour.getItemAt(0));
      for (int min=0; min<=55; min += 5) {
         start_min.addItem(String.format("%02d", min));
         dur_min.addItem(String.format("%02d", min));
      }
      start_min.setSelectedItem(start_min.getItemAt(0));
      dur_min.setSelectedItem(dur_min.getItemAt(0));
      start_ampm.addItem("pm");
      start_ampm.addItem("am");
      start_ampm.setSelectedItem(start_ampm.getItemAt(0));
      dur_hour.setSelectedItem("01");

      JPanel days_panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
      days_panel.add(mon); days_panel.add(tue); days_panel.add(wed);
      days_panel.add(thu); days_panel.add(fri); days_panel.add(sat); days_panel.add(sun);
      enableDays(false);

      components = new JPanel(new MigLayout("gapx 5, gapy 5"));
      components.add(new JLabel("How Often"), "cell 0 0");
      components.add(often, "cell 1 0");
      components.add(new JLabel("Day"), "cell 0 1");
      components.add(day, "cell 1 1");
      components.add(new JLabel("Repeat"), "cell 0 2");
      components.add(days_panel, "cell 1 2");
      components.add(new JLabel("Channel"), "cell 0 3");
      components.add(channel, "cell 1 3");

      JPanel h1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
      h1.add(start_hour); h1.add(new JLabel(":")); h1.add(start_min); h1.add(start_ampm);

      components.add(new JLabel("Start Time"), "cell 0 4");
      components.add(h1, "cell 1 4");

      JPanel h2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
      h2.add(dur_hour); h2.add(new JLabel(":")); h2.add(dur_min);

      components.add(new JLabel("Duration"), "cell 0 5");
      components.add(h2, "cell 1 5");
   }

   private void enableDays(Boolean state) {
      if (day == null) return;
      day.setEnabled(!state);
      mon.setEnabled(state);
      tue.setEnabled(state);
      wed.setEnabled(state);
      thu.setEnabled(state);
      fri.setEnabled(state);
      sat.setEnabled(state);
      sun.setEnabled(state);
   }

   public void promptUser(String tivoName) {
      this.tivoName = tivoName;
      try {
         // Update list of channels in background mode
         getChannels(tivoName);

         // Update dates
         getDates();

         // Show dialog and get user response
         final JDialog dialog = new JDialog(config.gui.getFrame());
         dialog.setModal(true);
         SwingUtil.loadIcons(dialog);
         dialog.setTitle("Manual Recording - " + tivoName);

         final AtomicBoolean ok = new AtomicBoolean(false);
         JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
         JButton okButton = new JButton("OK");
         okButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               ok.set(true);
               dialog.setVisible(false);
            }
         });
         JButton cancelButton = new JButton("CANCEL");
         cancelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               ok.set(false);
               dialog.setVisible(false);
            }
         });
         buttons.add(okButton);
         buttons.add(cancelButton);

         JPanel root = new JPanel(new BorderLayout());
         root.add(components, BorderLayout.CENTER);
         root.add(buttons, BorderLayout.SOUTH);
         dialog.getContentPane().add(root);
         dialog.pack();
         dialog.setLocationRelativeTo(config.gui.getFrame());
         dialog.setVisible(true);
         dialog.dispose();

         if (ok.get()) {
            JSONObject json = getSettings();
            if (json != null)
               processResponse(json);
         }
      } catch (Exception e) {
         log.error("mRecordOptions.promptUser - " + e.getMessage());
      }
   }

   // Return time rounded down to nearest hour in nice display format
   private String getDisplayTime(long gmt) {
      SimpleDateFormat sdf = new SimpleDateFormat("E MM/dd/yyyy");
      return sdf.format(gmt);
   }

   private void getDates() {
      int numDays = 30;
      day.removeAllItems();
      long gmt = new Date().getTime();
      long increment = 24*60*60*1000;
      long stop = gmt + (long)numDays*increment;
      long time = gmt;
      while (time <= stop) {
         day.addItem(getDisplayTime(time));
         time += increment;
      }
      day.setSelectedItem(day.getItemAt(0));
   }

   private void getChannels(final String tivoName) {
      Runnable task = new Runnable() {
         @Override public void run() {
            log.warn("Getting channel list for '" + tivoName + "'");
            Remote r = config.initRemote(tivoName);
            if (r.success) {
               final JSONArray channels = r.ChannelList(null, true);
               r.disconnect();
               if (channels != null) {
                  SwingUtil.runLater(new Runnable() {
                     @Override public void run() {
                        try {
                           // Clear current list
                           channel.removeAllItems();
                           channelHash.clear();

                           for (int i=0; i<channels.length(); ++i) {
                              JSONObject json = channels.getJSONObject(i);
                              String item = "";
                              if (json.has("channelNumber")) {
                                 item += json.getString("channelNumber");
                              }
                              if (json.has("callSign")) {
                                 item += " " + json.getString("callSign");
                              }
                              channelHash.put(item, json);
                              channel.addItem(item);
                           }
                           if (channel.getItemCount() > 0)
                              channel.setSelectedItem(channel.getItemAt(0));
                        } catch (JSONException e) {
                           log.error("mRecordOptions.getChannels error - " + e.getMessage());
                        }
                     }
                  });
               }
            }
         }
      };
      new Thread(task).start();
   }

   private JSONObject getSettings() {
      // The returned JSONObject is idSetSource for Remote Command "Manual" call
      try {
         JSONObject json = new JSONObject();
         int hour = Integer.parseInt((String)start_hour.getSelectedItem());
         String ampm = (String)start_ampm.getSelectedItem();
         if (ampm.equals("pm") && hour < 12)
            hour += 12;
         String min = (String)start_min.getSelectedItem();

         if (((String)often.getSelectedItem()).equals("Once")) {
            json.put("type", "singleTimeChannelSource");
            // "time" needs to be in format: 2012-11-16 09:30:00
            // NOTE: Must also convert from local TZ to UTC
            String dayString = (String)day.getSelectedItem(); // Sun 05/04/2014
            dayString = dayString.substring(4);
            String d[] = dayString.split("/");
            String month = d[0];
            String mday = d[1];
            String year = d[2];
            String dateString = String.format("%s-%s-%s %02d:%s:00",year,month,mday,hour,min);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            sdf.setTimeZone(TimeZone.getDefault());
            Date d1 = sdf.parse(dateString);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            json.put("time", sdf.format(d1.getTime()));
         }
         else {
            // Repeat recording
            json.put("type", "repeatingTimeChannelSource");
            JSONArray dayOfWeek = new JSONArray();
            if (mon.isSelected())
               dayOfWeek.put("monday");
            if (tue.isSelected())
               dayOfWeek.put("tuesday");
            if (wed.isSelected())
               dayOfWeek.put("wednesday");
            if (thu.isSelected())
               dayOfWeek.put("thursday");
            if (fri.isSelected())
               dayOfWeek.put("friday");
            if (sat.isSelected())
               dayOfWeek.put("saturday");
            if (sun.isSelected())
               dayOfWeek.put("sunday");
            if (dayOfWeek.length() > 0)
               json.put("dayOfWeek", dayOfWeek);
            else {
               log.error("You must enable at least one day of the week for repeat recording.");
               return null;
            }
            json.put("timeOfDayLocal", String.format("%02d:%s:00",hour,min));
         }
         int duration = 0;
         duration += Integer.parseInt((String)dur_hour.getSelectedItem())*60*60;
         duration += Integer.parseInt((String)dur_min.getSelectedItem())*60;
         if (duration == 0) {
            log.error("Manual record duration must be > 0");
            return null;
         }
         json.put("duration", duration);
         json.put("channel", channelHash.get((String)channel.getSelectedItem()));
         return json;
      } catch (Exception e) {
         log.error("mRecordOptions.getSettings error - " + e.getMessage());
      }
      return null;
   }

   private void processResponse(final JSONObject json) {
      Runnable task = new Runnable() {
         @Override public void run() {
            log.warn("Scheduling manual recording for '" + tivoName + "'");
            Remote r = config.initRemote(tivoName);
            if (r.success) {
               try {
                  JSONObject result = r.Command("Manual", json);
                  if (result != null)
                     log.print(result.toString(3));
               } catch (JSONException e) {
                  log.error("mRecordOptions.processResponse error - " + e.getMessage());
               }
               r.disconnect();
            }
         }
      };
      new Thread(task).start();
   }

}
