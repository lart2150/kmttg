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

import java.util.Hashtable;
import java.util.Stack;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

import com.sun.javafx.charts.Legend;
import com.tivo.kmttg.gui.gui;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.table.bitrateTable;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class freeSpace {
   private String tivoName = null;
   private Stage frame = null;
   private Stage dialog = null;
   private TextField space = null;
   private bitrateTable tab = null;
   private Label totals1 = null;
   private Label totals2 = null;
   private float disk_space = 0;
   private Hashtable<String,Hashtable<String,Double>> chanData = new Hashtable<String,Hashtable<String,Double>>();
   private Hashtable<String,Object> totalsData = new Hashtable<String,Object>();
   private MyPie chart = null;
   private String[] labels = {"Keep Until I Delete", "Keep Until Space Needed", "Suggestions", "Free Space"};
   private Color[] colors = {Color.GREEN, Color.YELLOW, Color.ORANGE, Color.BLUE};
   
   class MyPie extends PieChart {
      public Legend legend;
      
      public MyPie() {
         super();
         legend = (Legend) getLegend();
      }
   }

   class PieData {
      ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();
      ObservableList<Color> pieChartColors = FXCollections.observableArrayList();

      public void add(PieChart.Data data, Color color){
         pieChartData.add(data);
         pieChartColors.add(color);
      }
   }   
  
   public freeSpace(String tivoName, Stage frame) {
      this.tivoName = tivoName;
      this.frame = frame;
      init();
   }
   
   private void init() {      
      chart = new MyPie();
      chart.setPrefWidth(frame.getWidth());
      chart.getStyleClass().add("piechart_shows");
      chart.setTitle(tivoName + " Disk Space Usage");
      chart.setPrefHeight(300);
      chart.setLegendSide(Side.RIGHT);
      chart.setLabelsVisible(false);

      // Populate dataset
      if ( ! setData() ) {
         log.error("Failed to obtain data for TiVo: " + tivoName);
         return;
      }
      
      // Dialog configuration      
      VBox content = new VBox();
      content.setAlignment(Pos.CENTER);
      content.setSpacing(1);
            
      // Row 1
      HBox row1 = new HBox();
      row1.setSpacing(5);
      // Free space
      Label space_label = new Label("Total Disk Space (GB):");
      space = new TextField();
      space.addEventHandler(KeyEvent.KEY_PRESSED, new EventHandler<KeyEvent>() {
         public void handle(KeyEvent event) {
            if (event.isControlDown())
               return;
            if( event.getCode() == KeyCode.ENTER ) {
               setData();
               updateLabels();
               config.diskSpace.put(tivoName, getDiskSpace());
               config.save();
            }
         }
      });
      space.setText(String.format("%.1f",getDiskSpace()));
      space.setTooltip(config.gui.getToolTip("total_disk_space"));
      row1.getChildren().addAll(space_label, space);
      
      // totals
      totals1 = new Label("");
      totals1.setAlignment(Pos.CENTER_LEFT);
      totals1.getStyleClass().add("piechart_shows");

      totals2 = new Label("");
      totals1.setAlignment(Pos.CENTER_LEFT);
      totals2.getStyleClass().add("piechart_shows");

      updateLabels();
      
      // Build content layout
      content.getChildren().addAll(row1, chart, totals1, totals2);
      
      // bitrateTable
      tab = new bitrateTable();
      tab.AddRows(chanData);
      ScrollPane tabScroll = new ScrollPane(tab.TABLE);
      tabScroll.setPrefHeight(200);
      tabScroll.setFitToHeight(true);
      tabScroll.setFitToWidth(true);
      content.getChildren().add(tabScroll);
     
      // create and display dialog window
      dialog = new Stage();
      dialog.initOwner(frame);
      gui.LoadIcons(dialog);
      dialog.setTitle(tivoName + " Disk Usage Analysis");
      dialog.setScene(new Scene(content));
      config.gui.setFontSize(dialog.getScene(), config.FontSize);
      dialog.show();
      TableUtil.autoSizeTableViewColumns(tab.TABLE, true);
   }
   
   public Boolean setData() {
      // Init data to 0
      Hashtable<String,Float> data = new Hashtable<String,Float>();      
      data.put("suggestions", (float)0.0);
      data.put("kuid",        (float)0.0);
      data.put("kusn",        (float)0.0);
      
      // Init totalsData to 0
      totalsData.put("bytes", (double)0.0);
      totalsData.put("duration", (double)0.0);
      
      Stack<Hashtable<String,String>> entries = config.gui.getTab(tivoName).getTable().getEntries();
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
            
      // Compute free space
      float available = getDiskSpace();
      float used = data.get("suggestions") + data.get("kuid") + data.get("kusn");
      float small = (float)0.001;
      if (available < small) {
         // Available not specified, so set to close to total used
         available = used - small;
         config.diskSpace.put(tivoName, available);
      }
      float free = available - used;
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
      String[] keys = {"kuid", "kusn", "suggestions", "free"};
      for (int i=0; i<keys.length; ++i) {
         legendLabels[i] = String.format(
            "%s: %.2f GB (%.1f%%)",
            labels[i], data.get(keys[i]), data.get(keys[i])*100/available
         );
         dataset.add(new PieChart.Data(legendLabels[i],data.get(keys[i])), colors[i]);
      }
      
      // Update Pie Chart data
      chart.setData(dataset.pieChartData);
      
      // Update pie colors to be what we want
      for (PieChart.Data d : dataset.pieChartData) {
         int idx = dataset.pieChartData.indexOf(d);
         Color color = dataset.pieChartColors.get(idx);
         d.getNode().setStyle(
            "-fx-pie-color: " + color.toString().replace("0x", "#") + ";"
         );
         chart.legend.getItems().get(idx).setSymbol(new Rectangle(8, 8, color));
     }
      
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
      if (dialog != null) dialog.close();
      chanData.clear();
      totalsData.clear();
   }
}
