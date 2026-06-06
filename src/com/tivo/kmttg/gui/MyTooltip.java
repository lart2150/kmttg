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

import javax.swing.ToolTipManager;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;

/**
 * Tooltip helpers. Swing tooltips natively support html markup, so make()
 * simply wraps the text in html tags. Delay/timeout settings are handled
 * globally by ToolTipManager.
 */
public class MyTooltip {
   private static Boolean initialized = false;

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

   // Returns html tooltip text for given limited html markup (<br>, <b>),
   // or null if tooltips are disabled.
   public static String make(String text) {
      if (! initialized)
         init();
      if (config.toolTips == 0)
         return null;
      return "<html>" + text + "</html>";
   }

   public static void setTooltipDelay(int open_secs, int timeout_secs) {
      // Snappy initial delay (the configured value defaults to 2s, which
      // feels like tooltips are missing). Cap it so tooltips appear
      // promptly, but still honor a smaller user-requested value.
      ToolTipManager.sharedInstance().setInitialDelay(Math.min(open_secs * 1000, 600));
      // Keep a long dismiss delay so kmttg's multi-line tooltips stay
      // readable.
      ToolTipManager.sharedInstance().setDismissDelay(timeout_secs * 1000);
   }

   public static void disable() {
      config.toolTips = 0;
      ToolTipManager.sharedInstance().setEnabled(false);
   }

   public static void enable() {
      config.toolTips = 1;
      ToolTipManager.sharedInstance().setEnabled(true);
   }

   public static void enableToolTips(int on) {
      if (on == 1)
         enable();
      else
         disable();
   }
}
