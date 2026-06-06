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

import java.awt.Color;
import java.util.Stack;

import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import com.tivo.kmttg.gui.swing.SwingUtil;
import com.tivo.kmttg.util.debug;

/**
 * Colored, scrolling message/log pane (JTextPane based).
 * Formerly implemented with a JavaFX WebView + DOM manipulation.
 */
public class textpane {
   private JTextPane p;
   private JScrollPane scroll;
   private int BUFFER_SIZE = 250000; // Limit to this many characters
   // null style = current theme foreground; blue/red chosen to be readable
   // on both light and dark themes
   private SimpleAttributeSet BLACK = null;
   private SimpleAttributeSet BLUE, RED;

   public textpane() {
      BLUE = makeStyle(new Color(0x4d, 0x8a, 0xf0));
      RED = makeStyle(new Color(0xe0, 0x52, 0x52));
      p = new JTextPane();
      p.setEditable(false);
      scroll = new JScrollPane(p);
   }

   private SimpleAttributeSet makeStyle(Color color) {
      SimpleAttributeSet style = new SimpleAttributeSet();
      StyleConstants.setForeground(style, color);
      return style;
   }

   // Component to embed in main window (text pane wrapped in scroll pane)
   public JScrollPane getPane() {
      return scroll;
   }

   public void print(String s) {
      appendText(BLACK, s);
   }

   public void warn(String s) {
      appendText(BLUE, s);
   }

   public void error(String s) {
      appendText(RED, s);
      java.awt.Toolkit.getDefaultToolkit().beep();
   }

   public void print(Stack<String> s) {
      for (int i = 0; i < s.size(); ++i)
         appendText(BLACK, s.get(i));
   }

   public void warn(Stack<String> s) {
      for (int i = 0; i < s.size(); ++i)
         appendText(BLUE, s.get(i));
   }

   public void error(Stack<String> s) {
      for (int i = 0; i < s.size(); ++i)
         appendText(RED, s.get(i));
      java.awt.Toolkit.getDefaultToolkit().beep();
   }

   public void appendText(final SimpleAttributeSet given, final String s) {
      SwingUtil.runLater(new Runnable() {
         @Override public void run() {
            try {
               // null style = theme default text color (evaluated at append
               // time so it tracks light/dark theme switches)
               SimpleAttributeSet style = given;
               if (style == null)
                  style = makeStyle(p.getForeground());
               StyledDocument doc = p.getStyledDocument();
               String text = s;
               if (doc.getLength() > 0)
                  text = "\n" + text;
               limitBuffer(doc, text.length());
               doc.insertString(doc.getLength(), text, style);
               // Auto-scroll to bottom
               p.setCaretPosition(doc.getLength());
            } catch (Exception e) {
               debug.print("textpane appendText - " + e.toString());
            }
         }
      });
   }

   // Limit text pane buffer size by truncating total data size to
   // BUFFER_SIZE or less if needed
   private void limitBuffer(StyledDocument doc, int incomingDataSize) {
      try {
         int overLength = doc.getLength() + incomingDataSize - BUFFER_SIZE;
         if (overLength > 0 && doc.getLength() >= overLength) {
            doc.remove(0, overLength);
         }
      } catch (Exception e) {
         debug.print("textpane limitBuffer - " + e.toString());
      }
   }

   // Full current text contents (used by Save messages to file)
   public String getText() {
      try {
         StyledDocument doc = p.getStyledDocument();
         return doc.getText(0, doc.getLength());
      } catch (Exception e) {
         return "";
      }
   }

   public void clear() {
      SwingUtil.runLater(new Runnable() {
         @Override public void run() {
            try {
               StyledDocument doc = p.getStyledDocument();
               doc.remove(0, doc.getLength());
            } catch (Exception e) {
               debug.print("textpane clear - " + e.toString());
            }
         }
      });
   }
}
