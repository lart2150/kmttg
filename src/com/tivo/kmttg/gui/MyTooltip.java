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
package com.tivo.kmttg.gui;

import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;

import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Tooltip;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

public class MyTooltip {
   private static Boolean initialized = false;
   private static int close_delay = 100;
   
   public static void init() {
      // Quick parse of config.ini to see if tooltips should be disabled
      // I need to do this because main gui is built before config.ini is parsed
      if (file.isFile(config.gui_settings)) {
         try {
               String line, key = "";
               BufferedReader ifp = new BufferedReader(new FileReader(config.gui_settings));
               while (( line = ifp.readLine()) != null) {
                  // Get rid of leading and trailing white space
                  line = line.replaceFirst("^\\s*(.*$)", "$1");
                  line = line.replaceFirst("^(.*)\\s*$", "$1");
                  if (line.length() == 0) continue; // skip empty lines
                  if (line.matches("^#.+")) continue; // skip comment lines
                  if (line.matches("^<.+>")) {
                     key = line.replaceFirst("<", "");
                     key = key.replaceFirst(">", "");
                     continue;
                  }
                  if (key.equals("toolTips")) {
                     if (line.matches("1"))
                        config.toolTips = 1;
                     else
                        config.toolTips = 0;
                  }
                  if (key.equals("toolTipsDelay")) {
                     try {
                        config.toolTipsDelay = Integer.parseInt(line);
                     } catch (NumberFormatException e) {
                        config.toolTipsDelay = 2;
                     }
                  }
                  if (key.equals("toolTipsTimeout")) {
                     try {
                        config.toolTipsTimeout = Integer.parseInt(line);
                     } catch (NumberFormatException e) {
                        config.toolTipsTimeout = 20;
                     }
                  }
               }
               ifp.close();
            } catch (Exception e) {
            log.error("MyTooltip init - " + e.getMessage());
         }

      }
      setTooltipDelay(config.toolTipsDelay, config.toolTipsTimeout);
      initialized = true;
   }
   
   // Parse html syntax and return a tooltip made up of TextFlow elements
   // Currently only supports <br> and <b>...</b> html quantifiers
   public static Tooltip make(String text) {
      if (! initialized)
         init();
      if (config.toolTips == 0)
         return null;
      Tooltip  tip = new Tooltip();
      tip.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
      TextFlow tf = new TextFlow();
      String[] lines = text.split("<br>");
      for (String line : lines) {
         if (line.contains("<b>")) {
            String text_split = line;
            text_split = text_split.replaceAll("<b>", "\nBOLD");
            text_split = text_split.replaceAll("</b>", "\n");
            String[] split = text_split.split("\n");
            for (String s : split) {
               if (s.length() > 0) {
                  Text t = new Text();
                  if (s.startsWith("BOLD")) {
                     s = s.replaceFirst("BOLD", "");
                     t.setFont(Font.font("System", FontWeight.BOLD, 12));
                  } else {
                     t.setFont(Font.font("System", FontWeight.NORMAL, 12));
                  }
                  t.setText(s);
                  tf.getChildren().add(t);
               }
            }
            tf.getChildren().add(new Text("\n"));
         } else {
           Text t = new Text();
            t.setFont(Font.font("System", FontWeight.NORMAL, 12));
            t.setText(line + "\n");
            tf.getChildren().add(t);
         }
      }
      tip.setGraphic(tf);
      tip.setMaxHeight(50);
      tip.getStyleClass().add("tooltips");
      return tip;      
   }
   
   public static void setTooltipDelay(int open_secs, int timeout_secs) {
      // This only works for Java 8, so don't execute for Java 9 or later (Java 8=1.8)
      if (! System.getProperty("java.version").startsWith("1."))
         return;
      setupCustomTooltipBehavior(open_secs*1000, timeout_secs*1000, close_delay);
   }
   
   public static void disable() {
      config.toolTips = 0;
   }
   
   public static void enable() {
      config.toolTips = 1;
   }
   
   public static void enableToolTips(int on) {
      if (on == 1)
         enable();
      else
         disable();
   }
   
   private static void setupCustomTooltipBehavior(int openDelayInMillis, int visibleDurationInMillis, int closeDelayInMillis) {
      try {           
          Class<?> TTBehaviourClass = null;
          Class<?>[] declaredClasses = Tooltip.class.getDeclaredClasses();
          for (Class<?> c:declaredClasses) {
              if (c.getCanonicalName().equals("javafx.scene.control.Tooltip.TooltipBehavior")) {
                  TTBehaviourClass = c;
                  break;
              }
          }
          if (TTBehaviourClass == null) {
              // abort
              return;
          }
          Constructor<?> constructor = TTBehaviourClass.getDeclaredConstructor(
                  Duration.class, Duration.class, Duration.class, boolean.class);
          if (constructor == null) {
              // abort
              return;
          }
          constructor.setAccessible(true);
          Object newTTBehaviour = constructor.newInstance(
                  new Duration(openDelayInMillis), new Duration(visibleDurationInMillis), 
                  new Duration(closeDelayInMillis), false);
          if (newTTBehaviour == null) {
              // abort
              return;
          }
          Field ttbehaviourField = Tooltip.class.getDeclaredField("BEHAVIOR");
          if (ttbehaviourField == null) {
              // abort
              return;
          }
          ttbehaviourField.setAccessible(true);
           
          // Cache the default behavior if needed.
          //Object defaultTTBehavior = ttbehaviourField.get(Tooltip.class);
          ttbehaviourField.set(Tooltip.class, newTTBehaviour);
           
      } catch (Exception e) {
          log.error("Aborted setup due to error:" + e.getMessage());
      }
  }
   
}
