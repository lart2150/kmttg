package com.tivo.kmttg.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import com.tivo.kmttg.main.config;

public class ColorColumnRenderer extends DefaultTableCellRenderer {
   private static final long serialVersionUID = 1L;
   Color bkgndColor;
   Font font;
   
   public ColorColumnRenderer(Color bkgnd, Font font) {
      super(); 
      bkgndColor = bkgnd;
      this.font = font;
   }
   
   public Component getTableCellRendererComponent
       (JTable table, Object value, boolean isSelected,
        boolean hasFocus, int row, int column) 
   {
      Component cell = super.getTableCellRendererComponent
         (table, value, isSelected, hasFocus, row, column);
      
      if ( ! isSelected ) {
         if (column % 2 == 0)
            cell.setBackground(config.tableBkgndLight);
         else
            cell.setBackground(config.tableBkgndDarker);            
      }         
      cell.setFont(config.tableFont);
     
      return cell;
   }
}
