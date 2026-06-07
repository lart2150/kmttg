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
package com.tivo.kmttg.gui.remote;

import java.util.Stack;

import javax.swing.JButton;

public class PanelKey {
   public String actionName = null;
   public int key = -1;
   public Boolean isAscii = false;
   public String command = "";
   public JButton button = null;
   public static Stack<PanelKey> panelKeys = new Stack<PanelKey>();
   public static Stack<PanelKey> buttonKeys = new Stack<PanelKey>();

   public PanelKey(String actionName, int key, Boolean isAscii, String command) {
      this.actionName = actionName;
      this.key = key;
      this.isAscii = isAscii;
      this.command = command;
   }

   public PanelKey(String actionName, int key, JButton button) {
      this.actionName = actionName;
      this.key = key;
      this.button = button;
   }
}
