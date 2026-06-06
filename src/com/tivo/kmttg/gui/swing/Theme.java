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
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.log;

/**
 * FlatLaf based theme handling. Replaces the former JavaFX css stylesheet
 * system (css/*.css) with the built in FlatLaf themes. Historical css file
 * names from old config files are mapped to the closest new theme so
 * existing config.ini lookAndFeel entries continue to work.
 */
public class Theme {
   private static final String[] NAMES = {
      "Light", "Dark", "IntelliJ", "Darcula", "macOS Light", "macOS Dark"
   };

   public static List<String> getAvailableLooks() {
      List<String> list = new ArrayList<String>();
      for (String name : NAMES)
         list.add(name);
      return list;
   }

   // Map a theme name (or historical css file name) to a current theme name
   public static String normalize(String name) {
      if (name == null)
         return "Light";
      if (name.endsWith(".css"))
         name = name.substring(0, name.length() - 4);
      for (String n : NAMES) {
         if (n.equalsIgnoreCase(name))
            return n;
      }
      // Historical css themes -> closest FlatLaf theme
      if (name.equalsIgnoreCase("navy"))
         return "Dark";
      return "Light"; // default, white, metro_light, blue, green, orange, purple
   }

   // Apply named theme to entire application (live update of all windows).
   // Safe to call before any windows exist (initial startup) or after.
   public static void apply(String name) {
      name = normalize(name);
      config.lookAndFeel = name; // persist normalized name
      try {
         switch (name) {
            case "Dark":        UIManager.setLookAndFeel(new FlatDarkLaf()); break;
            case "IntelliJ":    UIManager.setLookAndFeel(new FlatIntelliJLaf()); break;
            case "Darcula":     UIManager.setLookAndFeel(new FlatDarculaLaf()); break;
            case "macOS Light": UIManager.setLookAndFeel(new FlatMacLightLaf()); break;
            case "macOS Dark":  UIManager.setLookAndFeel(new FlatMacDarkLaf()); break;
            default:            UIManager.setLookAndFeel(new FlatLightLaf()); break;
         }
         applyFontSize(config.FontSize);
         applyTableDefaults();
         FlatLaf.updateUI();
      } catch (Exception e) {
         log.error("Trouble setting look and feel: " + e.toString());
      }
   }

   // Table styling carried over from the former kmttg.css:
   // visible cell grid color derived from the theme (FlatLaf's default is
   // nearly invisible)
   private static void applyTableDefaults() {
      Color bg = UIManager.getColor("Table.background");
      Color fg = UIManager.getColor("Table.foreground");
      if (bg == null) bg = Color.WHITE;
      if (fg == null) fg = Color.BLACK;
      UIManager.put("Table.gridColor", new ColorUIResource(blend(bg, fg, 0.20f)));
   }

   // Mix fraction f of c2 into c1
   private static Color blend(Color c1, Color c2, float f) {
      return new Color(
         Math.round(c1.getRed()   * (1 - f) + c2.getRed()   * f),
         Math.round(c1.getGreen() * (1 - f) + c2.getGreen() * f),
         Math.round(c1.getBlue()  * (1 - f) + c2.getBlue()  * f)
      );
   }

   // Global font size in points - replaces JavaFX "-fx-font-size" root style.
   // NOTE: JavaFX font sizes were in points (96 dpi -> 1pt = 4/3 px) while
   // Swing font sizes are effectively pixels, so convert to keep the same
   // visual size for existing FontSize settings.
   public static void setFontSize(int fontSize) {
      applyFontSize(fontSize);
      FlatLaf.updateUI();
   }

   private static void applyFontSize(int fontSize) {
      int pixels = Math.round(fontSize * 96f / 72f);
      Font font = UIManager.getFont("defaultFont");
      if (font == null)
         font = new Font(Font.DIALOG, Font.PLAIN, pixels);
      UIManager.put("defaultFont", new FontUIResource(font.deriveFont((float) pixels)));
      // Table cells and headers were bold in the former kmttg.css
      UIManager.put("Table.font", new FontUIResource(font.deriveFont(Font.BOLD, pixels)));
      UIManager.put("TableHeader.font", new FontUIResource(font.deriveFont(Font.BOLD, pixels)));
   }
}
