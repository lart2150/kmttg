package com.tivo.kmttg.gui.remote;

import java.util.Stack;

import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;

public class PanelKey {
   public String actionName = null;
   public KeyCode key = null;
   public Boolean isAscii = false;
   public String command = "";
   public Button button = null;
   public static Stack<PanelKey> panelKeys = new Stack<PanelKey>();
   public static Stack<PanelKey> buttonKeys = new Stack<PanelKey>();
   
   public PanelKey(String actionName, KeyCode key, Boolean isAscii, String command) {
      this.actionName = actionName;
      this.key = key;
      this.isAscii = isAscii;
      this.command = command;
   }
   
   public PanelKey(String actionName, KeyCode key, Button button) {
      this.actionName = actionName;
      this.key = key;
      this.button = button;
   }
}
