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
import java.awt.Image;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.tivo.kmttg.util.debug;

/**
 * Small set of Swing helpers used throughout the GUI. This centralizes the
 * patterns that were previously expressed with JavaFX Platform.runLater and
 * Alert dialogs so converted call sites stay one-liners.
 */
public class SwingUtil {

   // Equivalent of JavaFX Platform.runLater - safe to call from any thread
   public static void runLater(Runnable r) {
      SwingUtilities.invokeLater(r);
   }

   // Run on the EDT and wait for completion. If already on the EDT just run.
   public static void runAndWait(Runnable r) {
      if (SwingUtilities.isEventDispatchThread()) {
         r.run();
         return;
      }
      try {
         SwingUtilities.invokeAndWait(r);
      } catch (Exception e) {
         debug.print("runAndWait - " + e.toString());
      }
   }

   // OK/Cancel confirmation dialog. Returns true if user pressed OK.
   public static boolean confirm(Component parent, String title, String message) {
      int answer = JOptionPane.showConfirmDialog(
         parent, message, title, JOptionPane.OK_CANCEL_OPTION
      );
      return answer == JOptionPane.OK_OPTION;
   }

   public static void info(Component parent, String title, String message) {
      JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE);
   }

   public static void warning(Component parent, String title, String message) {
      JOptionPane.showMessageDialog(parent, message, title, JOptionPane.WARNING_MESSAGE);
   }

   public static void error(Component parent, String title, String message) {
      JOptionPane.showMessageDialog(parent, message, title, JOptionPane.ERROR_MESSAGE);
   }

   // #RRGGBB string for given color (used for styled log text)
   public static String webColor(Color color) {
      return String.format("#%02X%02X%02X",
         color.getRed(), color.getGreen(), color.getBlue());
   }

   // Slightly darker/lighter shade of given background for alternating
   // table row striping (works for both light and dark themes)
   public static Color alternateRowColor(Color bg) {
      boolean dark = (bg.getRed() + bg.getGreen() + bg.getBlue()) / 3 < 128;
      float factor = dark ? 1.25f : 0.94f;
      int r = Math.min(255, Math.round(bg.getRed() * factor));
      int g = Math.min(255, Math.round(bg.getGreen() * factor));
      int b = Math.min(255, Math.round(bg.getBlue() * factor));
      if (dark && r + g + b == 0) { r = 32; g = 32; b = 32; }
      return new Color(r, g, b);
   }

   // kmttg application icons used on all windows
   private static List<Image> icons = null;
   public static List<Image> getIcons() {
      if (icons == null) {
         icons = new ArrayList<Image>();
         String[] names = {
            "TtGo_blue_16x16_8", "TtGo_blue_16x16_32",
            "TtGo_blue_32x32_8", "TtGo_blue_32x32_32",
            "TtGo_blue_48x48_8", "TtGo_blue_48x48_32" };
         for (String name : names) {
            try {
               icons.add(ImageIO.read(SwingUtil.class.getResourceAsStream("/" + name + ".png")));
            } catch (Exception e) {
               debug.print(e.toString());
            }
         }
      }
      return icons;
   }

   public static void loadIcons(Window window) {
      window.setIconImages(getIcons());
   }
}
