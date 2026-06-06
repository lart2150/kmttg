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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Stack;

import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.swing.SwingUtil;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.table.bitrateTable;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class freeSpace {
   private String tivoName = null;
   private JFrame frame = null;
   private JDialog dialog = null;
   private JTextField space = null;
   private bitrateTable tab = null;
   private JLabel totals1 = null;
   private JLabel totals2 = null;
   private float disk_space = 0;
   private Hashtable<String,Hashtable<String,Double>> chanData = new Hashtable<String,Hashtable<String,Double>>();
   private Hashtable<String,Object> totalsData = new Hashtable<String,Object>();
   private PieChartPanel chart = null;
   private String[] labels = {"Keep Until I Delete", "Keep Until Space Needed", "Suggestions", "Deleted", "Free Space"};
   private Color[] colors = {Color.GREEN, Color.YELLOW, Color.ORANGE, Color.RED, Color.BLUE};

   // Simple value+color holder for a single pie slice
   class PieDatum {
      String label;
      double value;
      Color color;
      PieDatum(String label, double value, Color color) {
         this.label = label;
         this.value = value;
         this.color = color;
      }
   }

   class PieData {
      List<PieDatum> pieChartData = new ArrayList<PieDatum>();

      public void add(PieDatum data){
         pieChartData.add(data);
      }
   }

   // Lightweight pie chart with right-hand legend (replaces JavaFX PieChart)
   class PieChartPanel extends JPanel {
      private static final long serialVersionUID = 1L;
      private List<PieDatum> data = new ArrayList<PieDatum>();
      private String title = "";

      PieChartPanel() {
         setPreferredSize(new Dimension(400, 300));
      }

      public void setData(List<PieDatum> data) {
         this.data = data;
         repaint();
      }

      public void setTitle(String title) {
         this.title = title;
         repaint();
      }

      @Override
      protected void paintComponent(Graphics g) {
         super.paintComponent(g);
         Graphics2D g2 = (Graphics2D)g;
         g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
         int w = getWidth();
         int h = getHeight();
         // Title
         if (title != null && title.length() > 0) {
            int tw = g2.getFontMetrics().stringWidth(title);
            g2.setColor(Color.BLACK);
            g2.drawString(title, (w - tw)/2, 15);
         }
         double total = 0;
         for (PieDatum d : data)
            total += d.value;
         // Pie occupies left portion, legend on the right
         int diameter = Math.min(w/2, h - 40);
         if (diameter < 10) diameter = 10;
         int px = 10;
         int py = 25;
         double start = 0;
         if (total > 0) {
            for (PieDatum d : data) {
               double extent = d.value * 360.0 / total;
               g2.setColor(d.color);
               g2.fillArc(px, py, diameter, diameter, (int)Math.round(start), (int)Math.round(extent));
               start += extent;
            }
         }
         // Legend
         int lx = px + diameter + 20;
         int ly = py + 5;
         for (PieDatum d : data) {
            g2.setColor(d.color);
            g2.fillRect(lx, ly, 12, 12);
            g2.setColor(Color.BLACK);
            g2.drawString(d.label, lx + 18, ly + 11);
            ly += 18;
         }
      }
   }

   public freeSpace(String tivoName, JFrame frame) {
      this.tivoName = tivoName;
      this.frame = frame;
      init();
   }

   private void init() {
      chart = new PieChartPanel();
      chart.setPreferredSize(new Dimension(frame.getWidth(), 300));
      chart.setTitle(tivoName + " Disk Space Usage");

      // Populate dataset
      if ( ! setData() ) {
         log.error("Failed to obtain data for TiVo: " + tivoName);
         return;
      }

      // Dialog configuration
      JPanel content = new JPanel();
      content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

      // Row 1
      JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
      // Free space
      JLabel space_label = new JLabel("Total Disk Space (GB):");
      space = new JTextField(10);
      space.addKeyListener(new KeyAdapter() {
         public void keyPressed(KeyEvent event) {
            if (event.isControlDown())
               return;
            if( event.getKeyCode() == KeyEvent.VK_ENTER ) {
               setData();
               updateLabels();
               config.diskSpace.put(tivoName, getDiskSpace());
               config.save();
            }
         }
      });
      space.setText(String.format("%.1f",getDiskSpace()));
      space.setToolTipText(config.gui.getToolTip("total_disk_space"));
      row1.add(space_label);
      row1.add(space);

      // totals
      totals1 = new JLabel("");
      totals1.setHorizontalAlignment(SwingConstants.LEFT);

      totals2 = new JLabel("");
      totals2.setHorizontalAlignment(SwingConstants.LEFT);

      updateLabels();

      // Build content layout
      content.add(row1);
      content.add(chart);
      content.add(totals1);
      content.add(totals2);

      // bitrateTable
      tab = new bitrateTable();
      tab.AddRows(chanData);
      JScrollPane tabScroll = new JScrollPane(tab.TABLE);
      tabScroll.setPreferredSize(new Dimension(frame.getWidth(), 200));
      content.add(tabScroll);

      // create and display dialog window
      dialog = new JDialog(frame);
      int width = frame.getWidth();
      if (width > 1000) {
         width = 1000;
      }
      SwingUtil.loadIcons(dialog);
      dialog.setTitle(tivoName + " Disk Usage Analysis");
      dialog.getContentPane().add(content);
      dialog.pack();
      dialog.setSize(width, dialog.getHeight());
      dialog.setLocationRelativeTo(frame);
      dialog.setVisible(true);
      TableUtil.autoSizeTableViewColumns(tab.TABLE, true);
   }

   public Boolean setData() {
      // Init data to 0
      Hashtable<String,Float> data = new Hashtable<String,Float>();
      data.put("suggestions", (float)0.0);
      data.put("kuid",        (float)0.0);
      data.put("kusn",        (float)0.0);
      data.put("deleted",     (float)0.0);

      // Init totalsData to 0
      totalsData.put("bytes", (double)0.0);
      totalsData.put("duration", (double)0.0);

      Stack<Hashtable<String,String>> entries = config.gui.getTab(tivoName).getTable().getEntries();
      JSONArray tivoDeleted = config.gui.remote_gui.deleted_tab.tab.tivo_data.get(tivoName);
      if (entries == null) return false;
      Double duration, bytes;
      float sizeGB;
      totalsData.put("recordings", entries.size());
      for (int i=0; i<entries.size(); i++) {
         duration = 0.0;
         bytes = 0.0;
         sizeGB = (float)0.0;
         if (entries.get(i).containsKey("duration")) {
            duration = Double.parseDouble(entries.get(i).get("duration"))/1000.0;
         }
         if (entries.get(i).containsKey("size")) {
            bytes = Double.parseDouble(entries.get(i).get("size"));
            sizeGB = (float) (bytes/Math.pow(2,30));
         }
         // Channel bit rates
         if (entries.get(i).containsKey("channel")) {
            String channel = entries.get(i).get("channel");
            if ( ! chanData.containsKey(channel) ) {
               chanData.put(channel, new Hashtable<String,Double>());
               chanData.get(channel).put("bytes", 0.0);
               chanData.get(channel).put("duration", 0.0);
            }
            chanData.get(channel).put("bytes",    chanData.get(channel).get("bytes")+bytes);
            chanData.get(channel).put("duration", chanData.get(channel).get("duration")+duration);
         }

         // Duration totals
         totalsData.put("duration", (Double)totalsData.get("duration")+duration);
         totalsData.put("bytes", (Double)totalsData.get("bytes")+bytes);

         // Disk space allocation data
         if (entries.get(i).containsKey("suggestion")) {
            data.put("suggestions", data.get("suggestions") + sizeGB);
            continue;
         }
         if (entries.get(i).containsKey("kuid")) {
            data.put("kuid", data.get("kuid") + sizeGB);
            continue;
         }
         data.put("kusn", data.get("kusn") + sizeGB);
      }
      if (tivoDeleted != null) {
         for (int i=0; i<tivoDeleted.length(); i++) {
            try {
               JSONObject show = tivoDeleted.getJSONObject(i);
               if (show.has("size")) {
                  sizeGB = (float) ((float)show.getInt("size")/(float)1048576);
                  data.put("deleted", data.get("deleted") + sizeGB);
               }
            } catch (JSONException e) {
               // TODO Auto-generated catch block
               e.printStackTrace();
            }
         }
      }

      // Compute free space
      float available = getDiskSpace();
      float used = data.get("suggestions") + data.get("kuid") + data.get("kusn");
      float small = (float)0.001;
      if (available < small) {
         // Available not specified, so set to close to total used
         available = used - small;
         config.diskSpace.put(tivoName, available);
      }
      float free = available - used - data.get("deleted");
      if (free < 0) {
         // Set disk space available to used space if used > available
         disk_space = used;
         free = 0;
      }
      data.put("free", free);
      totalsData.put("free", free);

      int numSets = data.size();
      String[] legendLabels = new String[numSets];
      PieData dataset = new PieData();
      String[] keys = {"kuid", "kusn", "suggestions", "deleted", "free"};
      for (int i=0; i<keys.length; ++i) {
         legendLabels[i] = String.format(
            "%s: %.2f GB (%.1f%%)",
            labels[i], data.get(keys[i]), data.get(keys[i])*100/available
         );
         dataset.add(new PieDatum(legendLabels[i], data.get(keys[i]), colors[i]));
      }

      // Update Pie Chart data
      chart.setData(dataset.pieChartData);

      // Complete Totals data (recordings, bytes, duration set so far)
      totalsData.put("rate", bitrateTable.bitRate((Double)totalsData.get("bytes"),(Double)totalsData.get("duration")));
      totalsData.put("rate", String.format("%.2f", (Double)totalsData.get("rate")));
      totalsData.put("remaining",
         timeRemaining(
            (Double)totalsData.get("bytes"),
            (Float)totalsData.get("free")*Math.pow(2,30),
            (Double)totalsData.get("duration")
         )
      );
      totalsData.put("remaining", secsToHoursMins((Double)totalsData.get("remaining")));

      totalsData.put("recordings", "" + totalsData.get("recordings"));
      totalsData.put("bytes", String.format("%.2f", (Double)totalsData.get("bytes")/Math.pow(2,30)));
      totalsData.put("duration", secsToHoursMins((Double)totalsData.get("duration")));

      return true;
   }

   private void updateLabels() {
      totals1.setText(
         "Recordings: " + totalsData.get("recordings") +
         ", Space used: " + totalsData.get("bytes") + " GB" +
         ", Total time: " + totalsData.get("duration")
      );
      totals2.setText(
         "Average Bit Rate: " + totalsData.get("rate") + " Mbps" +
         ", Free Space: " + String.format("%.2f GB", (Float)totalsData.get("free")) +
         ", Recording Time Remaining: " + totalsData.get("remaining")
      );

   }

   private float getDiskSpace() {
      if (config.diskSpace.containsKey(tivoName)) {
         disk_space = config.diskSpace.get(tivoName);
      }
      float available = disk_space;
      if (space != null) {
         String free_space = string.removeLeadingTrailingSpaces(space.getText());
         if (free_space.length() > 0) {
            try {
               available = Float.parseFloat(free_space);
            } catch(NumberFormatException e) {
               log.error("Disk space specification does not evaluate to a number: " + free_space);
               available = 0;
            }
         }
      }
      return available;
   }

   private Double timeRemaining(Double totalBytes, Double freeBytes, Double totalSecs) {
      return totalSecs*freeBytes/totalBytes;
   }

   private String secsToHoursMins(Double secs) {
      debug.print("secs=" + secs);
      Integer hours = (int) (secs/3600);
      Integer mins  = (int) (secs/60 - hours*60);
      return String.format("%02dh : %02dm", hours, mins);
   }

   public void destroy() {
      if (dialog != null) dialog.dispose();
      chanData.clear();
      totalsData.clear();
   }
}
