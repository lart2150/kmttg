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

import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

public class imageCell extends HBox {
   private ImageView image = new ImageView();
   private ImageView image2 = new ImageView();
   private Label label = new Label("");
   public String imageName = "";
   
   public imageCell() {
      super();
      setSpacing(1);
      getChildren().addAll(image, image2, label);
   }
   
   public void setImage(Image img) {
      image.setImage(img);
   }
   
   public void setImage2(Image img) {
      image2.setImage(img);
   }
   
   public void setLabel(String s) {
      label.setText(s);
   }

}
