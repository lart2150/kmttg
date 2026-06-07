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
import java.util.Hashtable;

import javax.swing.JTable;

import com.tivo.kmttg.gui.comparator.DoubleComparator;
import com.tivo.kmttg.gui.comparator.DurationComparator;
import com.tivo.kmttg.gui.sortable.sortableDouble;
import com.tivo.kmttg.gui.sortable.sortableDuration;
import com.tivo.kmttg.gui.swing.KmttgTable;
import com.tivo.kmttg.gui.swing.KmttgTableModel;

public class bitrateTable {
   // Model column names map to getXXX() getters via reflection; header text
   // (with spaces/parens) is applied separately below.
   private String[] TITLE_cols = {"CHANNEL", "SIZE_GB", "TIME", "RATE_Mbps", "RATE_GB_hour"};
   private String[] HEADER_cols = {"CHANNEL", "SIZE (GB)", "TIME", "RATE (Mbps)", "RATE (GB/hour)"};
   private double[] weights = {20, 20, 20, 20, 20};
   public JTable TABLE = null;
   public KmttgTableModel<Tabentry> MODEL = null;

   public bitrateTable() {
      MODEL = new KmttgTableModel<Tabentry>(TITLE_cols);
      for (String cName : TITLE_cols) {
         // NOTE: cName defines get<cName> method to use in Tabentry class
         if (cName.equals("CHANNEL")) {
            // Regular String sort
         }
         else if (cName.equals("TIME")) {
            MODEL.setComparator(cName, new DurationComparator());
         }
         else {
            MODEL.setComparator(cName, new DoubleComparator());
         }
      }
      TABLE = KmttgTable.create(MODEL, null);
      // Apply display header text
      for (int i=0; i<TITLE_cols.length; ++i) {
         int view = TABLE.convertColumnIndexToView(i);
         if (view >= 0)
            TABLE.getColumnModel().getColumn(view).setHeaderValue(HEADER_cols[i]);
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

      public sortableDouble getSIZE_GB() {
         return bytes;
      }

      public sortableDuration getTIME() {
         return duration;
      }

      public sortableDouble getRATE_Mbps() {
         return mbps;
      }

      public sortableDouble getRATE_GB_hour() {
         return GBph;
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
      MODEL.addRow(new Tabentry(channel, data.get("bytes"), data.get("duration")));
   }

   // Mbps = (bytes*8)/(1e6*secs)
   public static Double bitRate(Double bytes, Double secs) {
      return (bytes*8)/(1e6*secs);
   }
}
