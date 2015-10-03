package com.tivo.kmttg.gui.table;

import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

public class imageCell extends HBox {
   private ImageView image = new ImageView();
   private Label label = new Label("");
   public String imageName = "";
   
   public imageCell() {
      super();
      setSpacing(3);
      getChildren().addAll(image, label);
   }
   
   public void setImage(Image img) {
      image.setImage(img);
   }
   
   public void setLabel(String s) {
      label.setText(s);
   }

}
