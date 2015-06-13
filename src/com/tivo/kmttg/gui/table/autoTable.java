package com.tivo.kmttg.gui.table;

import com.tivo.kmttg.gui.dialog.autoTableEntry;
import com.tivo.kmttg.main.autoConfig;
import com.tivo.kmttg.main.autoEntry;

import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

public class autoTable {
   public TableView<Tabentry> TABLE = null;

   public autoTable() {
      TABLE = new TableView<Tabentry>();
      TABLE.setEditable(false);
      TableColumn<Tabentry,autoTableEntry> col1 = new TableColumn<Tabentry,autoTableEntry>("Type");
      col1.setCellValueFactory(new PropertyValueFactory<Tabentry,autoTableEntry>("Type"));
      col1.setComparator(null); // Disable column sorting
      TABLE.getColumns().add(col1);
      TableColumn<Tabentry,String> col2 = new TableColumn<Tabentry,String>("Keywords");
      col2.setCellValueFactory(new PropertyValueFactory<Tabentry,String>("Keywords"));
      col2.setComparator(null); // Disable column sorting
      TABLE.getColumns().add(col2);
   }

   public static class Tabentry {
      public autoTableEntry type;
      public String keywords;

      private Tabentry(autoEntry entry) {
         type = new autoTableEntry(entry);
         if (entry.type.equals("title")) {
            keywords = entry.keyword;
         } else {
            keywords = autoConfig.keywordsToString(entry.keywords);
         }
      }

      public autoTableEntry getType() {
         return type;
      }

      public String getKeywords() {
         return keywords;
      }
   }
   
   public autoEntry GetRowData(int row) {
      if (row >= TABLE.getItems().size())
         return null;
      return TABLE.getItems().get(row).getType().entry;
   }
   
   public int[] getSelectedRows() {
      return TableUtil.GetSelectedRows(TABLE);
   }
   
   public void RemoveRow(int row) {
      TABLE.getItems().remove(row);
      resize();
   }

   public void clear() {
      TABLE.getItems().clear();
   }

   public void AddRow(autoEntry entry) {
      TABLE.getItems().add(new Tabentry(entry));
      resize();
   }
   
   public void resize() {
      TableUtil.autoSizeTableViewColumns(TABLE, true);
   }
}
