package com.tivo.kmttg.gui.table;

import java.util.Comparator;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.sortable.sortableString;
import com.tivo.kmttg.rpc.SkipMode;
import com.tivo.kmttg.util.log;

public class skipTable {
   private String[] TITLE_cols = {"SHOW", "TIVO", "CONTENTID", "AD1"};
   public TableView<Tabentry> TABLE = null;
   
   class offsetComparator implements Comparator<String> {
      public int compare(String s1, String s2) {
         if (s1 != null && s2 != null) {
            long s1num = Long.parseLong(s1);
            long s2num = Long.parseLong(s2);
            if (s1num > s2num) return 1;
            if (s1num < s2num) return -1;
         }
         return 0;
      }
   }

   public skipTable() {
      TABLE = new TableView<Tabentry>();
      TABLE.setEditable(true); // Allow editing
      for (String colName : TITLE_cols) {
         // Regular String sort
         String cName = colName;
         if (colName.equals("SHOW")) {
            TableColumn<Tabentry,sortableString> col = new TableColumn<Tabentry,sortableString>(colName);
            col.setCellValueFactory(new PropertyValueFactory<Tabentry,sortableString>(cName));
            TABLE.getColumns().add(col);            
         } else {
            TableColumn<Tabentry,String> col = new TableColumn<Tabentry,String>(colName);
            col.setCellValueFactory(new PropertyValueFactory<Tabentry,String>(cName));
            TABLE.getColumns().add(col);
         }
      }
      
      // Define selection listener to detect table row selection changes
      TABLE.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<Tabentry>() {
         @Override
         public void changed(ObservableValue<? extends Tabentry> obs, Tabentry oldSelection, Tabentry newSelection) {
            if (newSelection != null) {
               TABLERowSelected(newSelection);
            }
         }
      });
   }

   public class Tabentry {
      public sortableString show = new sortableString();
      public String contentId = "";
      public String tivo = "";
      public String ad1 = "";

      public Tabentry(JSONObject json) {
         try {
            show.json = json;
            show.display = json.getString("title");
            contentId = json.getString("contentId");
            tivo = json.getString("tivoName");
            ad1 = adStart(json.getString("ad1"));
         } catch (Exception e) {
            log.error("pushTable Tabentry - " + e.getMessage());
         }
      }

      public sortableString getSHOW() {
         return show;
      }
      
      public String getCONTENTID() {
         return contentId;
      }
      
      public String getTIVO() {
         return tivo;
      }

      public String getAD1() {
         return ad1;
      }      
   }
   
   private String adStart(String ad1) {
      return SkipMode.toMinSec(Long.parseLong(ad1));
   }

   public TableView<?> getTable() {
      return TABLE;
   }

   public void clear() {
      TABLE.getItems().clear();
   }

   public void AddRows(JSONArray data) {
      for (int i=0; i<data.length(); ++i) {
         try {
            AddRow(data.getJSONObject(i));
         } catch (JSONException e) {
            log.error("skipTable AddRows - " + e.getMessage());
            return;
         }
      }
      TableUtil.autoSizeTableViewColumns(TABLE, true);
   }

   public void AddRow(JSONObject json) {
      TABLE.getItems().add(new Tabentry(json));
   }

   public void RemoveRow(int row) {
      TABLE.getItems().remove(row);
   }

   public JSONObject GetRowData(int row) {
      return TABLE.getItems().get(row).getSHOW().json;
   }
   
   public String GetValueAt(int row, int col) {
      return TABLE.getColumns().get(col).getCellData(row).toString();
   }
   
   public void changeTable() {
      try {
         JSONArray changed = new JSONArray();
         for (int row=0; row<TABLE.getItems().size(); ++row) {
            String table_value = GetValueAt(row, TableUtil.getColumnIndex(TABLE, "OFFSET"));
            JSONObject json = GetRowData(row);
            if (json != null) {
               String data_value = json.getString("offset");
               if (! table_value.equals(data_value)) {
                  // Make a copy of json so we don't change it
                  JSONObject j = new JSONObject(json.toString());
                  j.put("offset", table_value);
                  changed.put(j);
               }
            }
         }
         if (changed.length() > 0) {
            for (int i=0; i<changed.length(); ++i) {
               JSONObject j = changed.getJSONObject(i);
               SkipMode.changeEntry(j.getString("contentId"), j.getString("offset"), j.getString("title"));
            }
         }
      } catch (Exception e) {
         log.error("changeTable - " + e.getMessage());
      }
   }
   
   private void TABLERowSelected(Tabentry entry) {
      try {
         JSONObject json = entry.getSHOW().json;
         log.print("\nSkipMode data for '" + json.getString("title") + "'");
         JSONArray cuts = json.getJSONArray("cuts");
         int index = 0;
         long offset = Long.parseLong(json.getString("offset"));
         for (int i=0; i<cuts.length(); ++i) {
            JSONObject j = cuts.getJSONObject(i);
            long start = j.getLong("start");
            if (index > 0)
               start += offset;
            long end = j.getLong("end") + offset;
            String message = "" + index + ": start=";
            message += SkipMode.toMinSec(start);
            message += " end=";
            message += SkipMode.toMinSec(end);         
            log.print(message);
            index++;
         }
      } catch (JSONException e) {
         log.error("skipTable TABLERowSelected - " + e.getMessage());
      }
   }
}
