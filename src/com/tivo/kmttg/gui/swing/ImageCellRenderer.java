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
package com.tivo.kmttg.gui.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

import com.tivo.kmttg.gui.table.imageCell;

/**
 * Renderer for imageCell values: up to 2 icons followed by a text label.
 */
public class ImageCellRenderer extends JPanel implements TableCellRenderer {
   private static final long serialVersionUID = 1L;
   private JLabel icon1 = new JLabel();
   private JLabel icon2 = new JLabel();
   private JLabel text = new JLabel();

   public ImageCellRenderer() {
      super(new FlowLayout(FlowLayout.LEFT, 1, 0));
      setOpaque(true);
      add(icon1);
      add(icon2);
      add(text);
   }

   @Override
   public Component getTableCellRendererComponent(JTable table, Object value,
         boolean isSelected, boolean hasFocus, int row, int column) {
      icon1.setIcon(null);
      icon2.setIcon(null);
      text.setText("");
      if (value instanceof imageCell) {
         imageCell cell = (imageCell) value;
         if (cell.getImage() != null)
            icon1.setIcon(new ImageIcon(cell.getImage()));
         if (cell.getImage2() != null)
            icon2.setIcon(new ImageIcon(cell.getImage2()));
         text.setText(cell.getLabel());
      }
      if (isSelected) {
         setBackground(table.getSelectionBackground());
         text.setForeground(table.getSelectionForeground());
      } else {
         setBackground(table.getBackground());
         text.setForeground(table.getForeground());
      }
      text.setFont(table.getFont());
      return this;
   }

   // prepareRenderer sets foreground on the returned component (this
   // panel); propagate to the text label so row coloring stays readable
   @Override
   public void setForeground(Color color) {
      super.setForeground(color);
      if (text != null)
         text.setForeground(color);
   }
}
