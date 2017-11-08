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

import java.util.Stack;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.tivo.kmttg.main.config;

import javafx.application.Platform;
import javafx.scene.paint.Color;
import javafx.scene.web.WebView;

public class textpane {
   private WebView p;
   private int BUFFER_SIZE = 250000; // Limit to this many characters
   private String BLACK, BLUE, RED;
  
   textpane(WebView p) {
      BLACK = config.gui.getWebColor(Color.BLACK);
      BLUE = config.gui.getWebColor(Color.BLUE);
      RED = config.gui.getWebColor(Color.RED);
      this.p = p;
      this.p.getEngine().loadContent("<body><div id=\"content\"></div></body>");
   }
   
   public WebView getPane() {
      return p;
   }
  
   public void print(String s) {
      appendText(BLACK, s);
      scroll();
   }
  
   public void warn(String s) {
      appendText(BLUE, s);
      scroll();
   }
  
   public void error(String s) {
      appendText(RED, s);
      java.awt.Toolkit.getDefaultToolkit().beep();
      scroll();
   }
  
   public void print(Stack<String> s) {
      for (int i=0; i<s.size(); ++i)
         appendText(BLACK, s.get(i));
      scroll();
   }
  
   public void warn(Stack<String> s) {
      for (int i=0; i<s.size(); ++i)
         appendText(BLUE, s.get(i));
      scroll();
   }
  
   public void error(Stack<String> s) {
      for (int i=0; i<s.size(); ++i)
         appendText(RED, s.get(i));
      java.awt.Toolkit.getDefaultToolkit().beep();
      scroll();
   }
  
   private void scroll() {
      if (p != null) {
         Platform.runLater(new Runnable() {
            @Override public void run() {
               try {
                  p.getEngine().executeScript("window.scrollTo(0,document.body.scrollHeight);");
               } catch (Exception e) {}
            }
         });
      }
   }
  
   public void appendText(String color, String s) {
      if (p != null && p.getEngine() != null) {
         Document doc = p.getEngine().getDocument();
         if (doc == null)
            return;
         Element content = doc.getElementById("content");
         limitBuffer(content, s.length());
         // Use <pre> tag so as to preserve whitespace
         Element pre = doc.createElement("pre");
         pre.setTextContent(s);
         // NOTE: display: inline prevents newline from being added for <pre> tag
         // NOTE: white-space: pre-wrap allows horizontal work wrapping to avoid horizontal scrollbar
         pre.setAttribute("style", "font-size: " + config.FontSize + "pt; white-space: pre-wrap; display: inline; color:" + color);
         if (content.getChildNodes().getLength() > 0)
            content.appendChild(doc.createElement("br"));
         content.appendChild(pre);
      }
   }
  
   // Limit text pane buffer size by truncating total data size to
   // BUFFER_SIZE or less if needed
   private void limitBuffer(Element content, int incomingDataSize) {
      if (p != null) {
         int doc_length = content.getTextContent().getBytes().length;
         int overLength = doc_length + incomingDataSize - BUFFER_SIZE;
         if (overLength > 0 && doc_length >= overLength) {
            NodeList list = content.getChildNodes();
            int removed=0;
            while( list.getLength() > 0 && removed < overLength ) {
               removed += list.item(0).getTextContent().length();
               content.removeChild(list.item(0));
            }
         }
      }
   }
   
   public void clear() {
      if (p != null) {
         Element content = p.getEngine().getDocument().getElementById("content");
         NodeList list = content.getChildNodes();
         while (list.getLength() > 0) {
            content.removeChild(list.item(0));
         }
      }

   }
}

