package com.tivo.kmttg.gui;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.TimeZone;

import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.util.log;

public class mRecordOptions {
   JComponent[] components;
   JComboBox often, day, channel;
   JComboBox start_hour, start_min, start_ampm, dur_hour, dur_min;
   JCheckBox mon,tue,wed,thu,fri,sat,sun;
   String tivoName;
   Hashtable<String,JSONObject> channelHash = new Hashtable<String,JSONObject>();
   
   public mRecordOptions() {
      createComponents();      
   }
   
   private void createComponents() {
      often = new JComboBox(new String[] {"Once", "Repeat"});
      often.addItemListener(new ItemListener() {
         public void itemStateChanged(ItemEvent e) {
             if (e.getStateChange() == ItemEvent.SELECTED) {
               if (often.getSelectedItem().equals("Once"))
                  enableDays(false);
               else
                  enableDays(true);
            }
         }
      });
      often.setSelectedItem("Once");
      channel = new JComboBox();
      start_hour = new JComboBox();
      start_min = new JComboBox();
      start_ampm = new JComboBox();
      dur_hour = new JComboBox();
      dur_min = new JComboBox();
      
      day = new JComboBox();
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
      for (int min=0; min<=55; min += 5) {
         start_min.addItem(String.format("%02d", min));
         dur_min.addItem(String.format("%02d", min));
      }
      start_ampm.addItem("pm");
      start_ampm.addItem("am");
      dur_hour.setSelectedItem("01");
      
      Dimension space_5 = new Dimension(5,0);
      
      JPanel often_panel = new JPanel();
      often_panel.setLayout(new FlowLayout(FlowLayout.LEFT));
      often_panel.add(new JLabel("How Often"));
      often_panel.add(Box.createRigidArea(space_5));
      often_panel.add(often);
      
      JPanel day_panel = new JPanel();
      day_panel.setLayout(new FlowLayout(FlowLayout.LEFT));
      day_panel.add(new JLabel("Day"));
      day_panel.add(Box.createRigidArea(space_5));
      day_panel.add(day);
      
      JPanel days_panel = new JPanel();
      days_panel.setLayout(new FlowLayout(FlowLayout.LEFT));
      days_panel.add(new JLabel("Repeat"));
      days_panel.add(Box.createRigidArea(space_5));
      days_panel.add(mon);
      days_panel.add(tue);
      days_panel.add(wed);
      days_panel.add(thu);
      days_panel.add(fri);
      days_panel.add(sat);
      days_panel.add(sun);
      enableDays(false);
      
      JPanel chan_panel = new JPanel();
      chan_panel.setLayout(new FlowLayout(FlowLayout.LEFT));
      chan_panel.add(new JLabel("Channel"));
      chan_panel.add(Box.createRigidArea(space_5));
      chan_panel.add(channel);
      
      JPanel start_panel = new JPanel();
      start_panel.setLayout(new FlowLayout(FlowLayout.LEFT));
      start_panel.add(new JLabel("Start Time"));
      start_panel.add(Box.createRigidArea(space_5));
      start_panel.add(start_hour);
      start_panel.add(new JLabel(":"));
      start_panel.add(start_min);
      start_panel.add(start_ampm);
      
      JPanel stop_panel = new JPanel();
      stop_panel.setLayout(new FlowLayout(FlowLayout.LEFT));
      stop_panel.add(new JLabel("Duration"));
      stop_panel.add(Box.createRigidArea(space_5));
      stop_panel.add(dur_hour);
      stop_panel.add(new JLabel(":"));
      stop_panel.add(dur_min);
      
      components = new JComponent[] {
         often_panel,
         day_panel,
         days_panel,
         chan_panel,
         start_panel,
         stop_panel
      };
   }
   
   private void enableDays(Boolean state) {
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
         int response = JOptionPane.showConfirmDialog(
            null, components, "Manual Recording - " + tivoName, JOptionPane.OK_CANCEL_OPTION
         );
         if (response == JOptionPane.OK_OPTION) {
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
   }
   
   private void getChannels(final String tivoName) {
      class backgroundRun extends SwingWorker<Object, Object> {
         protected Object doInBackground() {
            log.warn("Getting channel list for '" + tivoName + "'");
            Remote r = config.initRemote(tivoName);
            if (r.success) {
               JSONArray channels = r.ChannelList(null);
               r.disconnect();
               if (channels != null) {
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
                  } catch (JSONException e) {
                     log.error("mRecordOptions.getChannels error - " + e.getMessage());
                  }
               }
            }
            return null;
         }
      }
      backgroundRun b = new backgroundRun();
      b.execute();
   }
   
   private JSONObject getSettings() {
      // The returned JSONObject is idSetSource for Remote Command "Manual" call
      try {
         JSONObject json = new JSONObject();
         int hour = Integer.parseInt((String)start_hour.getSelectedItem());
         if (start_ampm.getSelectedItem().equals("pm"))
            hour += 12;
         String min = (String)start_min.getSelectedItem();
         
         if (often.getSelectedItem().equals("Once")) {
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
      class backgroundRun extends SwingWorker<Object, Object> {
         protected Object doInBackground() {
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
            return null;
         }
      }
      backgroundRun b = new backgroundRun();
      b.execute();
   }
   
}
