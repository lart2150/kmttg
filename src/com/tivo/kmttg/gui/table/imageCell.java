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

import java.awt.Image;

/**
 * Value object for table image cells (up to 2 icons + a text label).
 * Rendered by com.tivo.kmttg.gui.swing.ImageCellRenderer.
 */
public class imageCell {
   private Image image = null;
   private Image image2 = null;
   private String label = "";
   public String imageName = "";

   public imageCell() {
   }

   public void setImage(Image img) {
      image = img;
   }

   public void setImage2(Image img) {
      image2 = img;
   }

   public void setLabel(String s) {
      label = s;
   }

   public Image getImage() {
      return image;
   }

   public Image getImage2() {
      return image2;
   }

   public String getLabel() {
      return label;
   }
}
