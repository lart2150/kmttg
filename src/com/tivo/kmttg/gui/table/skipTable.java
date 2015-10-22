package com.tivo.kmttg.gui.table;

import java.util.Comparator;

import javafx.event.EventHandler;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn.CellEditEvent;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.MouseEvent;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.sortable.sortableString;
import com.tivo.kmttg.rpc.SkipMode;
import com.tivo.kmttg.util.log;

public class skipTable {
   private String[] TITLE_cols = {"SHOW", "CONTENTID", "AD1_ORIG", "OFFSET", "AD1_ADJ"};
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
         } else if (colName.equals("OFFSET")) {
            TableColumn<Tabentry,String> col = new TableColumn<Tabentry,String>(colName);
            col.setCellValueFactory(new PropertyValueFactory<Tabentry,String>(cName));
            col.setCellFactory(TextFieldTableCell.<Tabentry>forTableColumn());
            col.setComparator(new offsetComparator());
            // This column is editable
            col.setOnEditCommit( new EventHandler<CellEditEvent<Tabentry, String>>() {
               @Override
               public void handle(CellEditEvent<Tabentry, String> event) {
                  int row = event.getTablePosition().getRow();
                  Tabentry entry = event.getTableView().getItems().get(row);
                  try {
                  // Update row Tabentry value
                  entry.offset = event.getNewValue();
                  entry.ad1_adj = adAdjusted(entry.show.json.getString("ad1"), entry.offset);
                  TABLE.getItems().set(row, entry);
                  } catch (JSONException e) {
                     log.error("skipTable - " + e.getMessage());
                  }
               }
            });
            TABLE.getColumns().add(col);
         } else {
            TableColumn<Tabentry,String> col = new TableColumn<Tabentry,String>(colName);
            col.setCellValueFactory(new PropertyValueFactory<Tabentry,String>(cName));
            TABLE.getColumns().add(col);
         }
      }
      
      // Mouse listener for single click in RATING column
      TABLE.setOnMousePressed(new EventHandler<MouseEvent>() {
         @Override 
         public void handle(MouseEvent event) {
            // Trigger edit for single click in RATING cell
            if (event.getClickCount() == 1 && event.getTarget().getClass() == TextFieldTableCell.class) {
               @SuppressWarnings("unchecked")
               TablePosition<Tabentry,?> pos = TABLE.getSelectionModel().getSelectedCells().get(0);
               TABLE.edit(pos.getRow(), pos.getTableColumn());
            }
         }
      });
   }

   public class Tabentry {
      public sortableString show = new sortableString();
      public String contentId = "";
      public String ad1_orig = "";
      public String offset = "";
      public String ad1_adj = "";

      public Tabentry(JSONObject json) {
         try {
            show.json = json;
            show.display = json.getString("title");
            contentId = json.getString("contentId");
            offset = json.getString("offset");
            String ad1 = json.getString("ad1");
            ad1_orig = adStart(ad1);
            ad1_adj = adAdjusted(ad1, offset);
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

      public String getOFFSET() {
         return offset;
      }      

      public String getAD1_ORIG() {
         return ad1_orig;
      }      

      public String getAD1_ADJ() {
         return ad1_adj;
      }      
   }
   
   private String adStart(String ad1) {
      return SkipMode.toMinSec(Long.parseLong(ad1));
   }
   
   private String adAdjusted(String ad1, String offset) {
      return SkipMode.toMinSec(Long.parseLong(ad1) + Long.parseLong(offset));
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
}
