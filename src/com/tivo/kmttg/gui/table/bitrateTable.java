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

import java.util.Arrays;
import java.util.Comparator;
import java.util.Hashtable;

import com.tivo.kmttg.gui.sortable.sortableDouble;
import com.tivo.kmttg.gui.sortable.sortableDuration;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.Callback;

public class bitrateTable {
   private String[] TITLE_cols = {"CHANNEL", "SIZE (GB)", "TIME", "RATE (Mbps)", "RATE (GB/hour)"};
   private double[] weights = {20, 20, 20, 20, 20};
   public TableView<Tabentry> TABLE = null;

   public bitrateTable() {
      TABLE = new TableView<Tabentry>();
      TABLE.setEditable(false);
      for (String colName : TITLE_cols) {
         // NOTE: cName defines get<cName> method to use in Tabentry class
         String cName = colName.replaceAll(" ", "_");
         cName = cName.replaceAll("\\(", "");
         cName = cName.replaceAll("\\)", "");
         cName = cName.replaceAll("/", "_");
         if (cName.equals("CHANNEL")) {
            TableColumn<Tabentry,String> col = new TableColumn<Tabentry,String>(colName);
            col.setCellValueFactory(new PropertyValueFactory<Tabentry,String>(cName));
            TABLE.getColumns().add(col);
         }
         else {
            TableColumn<Tabentry,Double> col = new TableColumn<Tabentry,Double>(colName);
            col.setCellValueFactory(new PropertyValueFactory<Tabentry,Double>(cName));
            col.setComparator(new DoubleComparator());
            if (cName.equals("TIME"))
               col.setCellFactory(new DurationCellFactory());
            else
               col.setCellFactory(new DoubleCellFactory());
            TABLE.getColumns().add(col);
         }
      }            
      TableUtil.setWeights(TABLE, TITLE_cols, weights, true);
   }

   public static class Tabentry {
      public final String channel;
      public final sortableDouble bytes;
      public final sortableDuration duration;
      public final sortableDouble mbps;
      public final sortableDouble GBph;

      private Tabentry(String channel, Double bytes, Double duration) {
         this.channel = channel;
         this.bytes = new sortableDouble(bytes/Math.pow(2,30));
         this.duration = new sortableDuration(duration.longValue()*1000);
         this.mbps = new sortableDouble(bitRate(bytes, duration));
         this.GBph = new sortableDouble((bytes/Math.pow(2,30))/(duration/3600.0));
      }

      public String getCHANNEL() {
         return channel;
      }

      public Double getSIZE_GB() {
         return bytes.sortable;
      }

      public Double getTIME() {
         return duration.sortable.doubleValue();
      }

      public Double getRATE_Mbps() {
         return mbps.sortable;
      }

      public Double getRATE_GB_hour() {
         return GBph.sortable;
      }
   }

   public class DoubleCellFactory implements Callback<TableColumn<Tabentry, Double>, TableCell<Tabentry, Double>> {
      public TableCell<Tabentry, Double> call(TableColumn<Tabentry, Double> param) {
         TableCell<Tabentry, Double> cell = new TableCell<Tabentry, Double>() {
            @Override
            public void updateItem(final Double item, boolean empty) {
               if (item != null) {
                  setText(new sortableDouble(item).toString());
               }
            }
         };
         return cell;
      }
   }   

   public class DurationCellFactory implements Callback<TableColumn<Tabentry, Double>, TableCell<Tabentry, Double>> {
      public TableCell<Tabentry, Double> call(TableColumn<Tabentry, Double> param) {
         TableCell<Tabentry, Double> cell = new TableCell<Tabentry, Double>() {
            @Override
            public void updateItem(final Double item, boolean empty) {
               if (item != null) {
                  setText(new sortableDuration(item.longValue()).toString());
               }
            }
         };
         return cell;
      }
   }   

   // Define custom column sorting routines
   public class DoubleComparator implements Comparator<Double> {
      public int compare(Double o1, Double o2) {
         return o1 < o2 ? -1 : o1 == o2 ? 0 : 1;
      }
   }

   public void AddRows(Hashtable<String,Hashtable<String,Double>> chanData) {
      // Add rows to table in channel name alphabetical order
      Object[] channels = chanData.keySet().toArray();
      Arrays.sort(channels);
      for (int i=0; i<channels.length; ++i) {
         AddRow((String)channels[i], chanData.get(channels[i]));
      }
   }

   public void AddRow(String channel, Hashtable<String,Double> data) {
      TABLE.getItems().add(new Tabentry(channel, data.get("bytes"), data.get("duration")));
   }

   // Mbps = (bytes*8)/(1e6*secs)
   public static Double bitRate(Double bytes, Double secs) {
      return (bytes*8)/(1e6*secs);
   }
}
