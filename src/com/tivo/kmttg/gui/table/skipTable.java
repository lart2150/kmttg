package com.tivo.kmttg.gui.table;

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
   private String[] TITLE_cols = {"CONTENTID", "TITLE", "OFFSET"};
   public TableView<Tabentry> TABLE = null;

   public skipTable() {
      TABLE = new TableView<Tabentry>();
      TABLE.setEditable(true); // Allow editing
      for (String colName : TITLE_cols) {
         // Regular String sort
         String cName = colName;
         if (colName.equals("CONTENTID")) {
            TableColumn<Tabentry,sortableString> col = new TableColumn<Tabentry,sortableString>(colName);
            col.setCellValueFactory(new PropertyValueFactory<Tabentry,sortableString>(cName));
            TABLE.getColumns().add(col);            
         } else if (colName.equals("OFFSET")) {
            TableColumn<Tabentry,String> col = new TableColumn<Tabentry,String>(colName);
            col.setCellValueFactory(new PropertyValueFactory<Tabentry,String>(cName));
            col.setCellFactory(TextFieldTableCell.<Tabentry>forTableColumn());
            // This column is editable
            col.setOnEditCommit( new EventHandler<CellEditEvent<Tabentry, String>>() {
               @Override
               public void handle(CellEditEvent<Tabentry, String> event) {
                  int row = event.getTablePosition().getRow();
                  Tabentry entry = event.getTableView().getItems().get(row);
                  // Update row Tabentry value
                  entry.offset = event.getNewValue();
                  TABLE.getItems().set(row, entry);
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
      public sortableString contentId = new sortableString();
      public String title = "";
      public String offset = "";

      public Tabentry(JSONObject json) {
         try {
            contentId.json = json;
            contentId.display = json.getString("contentId");
            title = json.getString("title");
            offset = json.getString("offset");
         } catch (Exception e) {
            log.error("pushTable Tabentry - " + e.getMessage());
         }
      }

      public sortableString getCONTENTID() {
         return contentId;
      }

      public String getTITLE() {
         return title;
      }

      public String getOFFSET() {
         return offset;
      }      
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
      return TABLE.getItems().get(row).getCONTENTID().json;
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
               SkipMode.changeEntry(j.getString("contentId"), j.getString("offset"));
            }
         }
      } catch (Exception e) {
         log.error("changeTable - " + e.getMessage());
      }
   }
}
