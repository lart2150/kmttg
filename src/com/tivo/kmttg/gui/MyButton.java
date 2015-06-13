package com.tivo.kmttg.gui;

import javafx.scene.control.Button;
import javafx.scene.effect.DropShadow;

public class MyButton extends Button {
   public MyButton(String name) {
      super(name);
      setEffect(new DropShadow());
   }
   
   public MyButton() {
      super();
      setEffect(new DropShadow());
   }
}
