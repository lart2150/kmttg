package com.tivo.kmttg.gui;

import java.awt.Color;
import java.util.Stack;

import javax.swing.JTextPane;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;

public class textpane {
   private JTextPane p;
   private int BUFFER_SIZE = 250000; // Limit text pane buffer size to ~10MB RAM
   
   textpane(JTextPane p) {
      this.p = p;
   }
   
   public void print(String s) {
      appendText(Color.black, s + "\n");
   }
   
   public void warn(String s) {
      appendText(Color.blue, s + "\n");
   }
   
   public void error(String s) {
      appendText(Color.red, s + "\n");
      java.awt.Toolkit.getDefaultToolkit().beep();
   }
   
   public void print(Stack<String> s) {
      for (int i=0; i<s.size(); ++i)
         appendText(Color.black, s.get(i) + "\n");
   }
   
   public void warn(Stack<String> s) {
      for (int i=0; i<s.size(); ++i)
         appendText(Color.blue, s.get(i) + "\n");
   }
   
   public void error(Stack<String> s) {
      for (int i=0; i<s.size(); ++i)
         appendText(Color.red, s.get(i) + "\n");
      java.awt.Toolkit.getDefaultToolkit().beep();
   }
   
   public void appendText(Color c, String s) {
      p.setEditable(true);
      StyleContext sc = StyleContext.getDefaultStyleContext();
      AttributeSet aset = sc.addAttribute(
         SimpleAttributeSet.EMPTY, StyleConstants.Foreground, c
      );
      
      // Limit total text pane buffer size
      limitBuffer(s.length());

      int len = p.getDocument().getLength();
      p.setCaretPosition(len);
      p.setCharacterAttributes(aset, false);
      p.replaceSelection(s);
      p.setEditable(false);
   }
   
   // Limit text pane buffer size by truncating total data size to
   // BUFFER_SIZE or less if needed
   private void limitBuffer(int incomingDataSize) {
      Document doc = p.getStyledDocument();
      int overLength = doc.getLength() + incomingDataSize - BUFFER_SIZE;
      if (overLength > 0 && doc.getLength() >= overLength) {
         try {
            doc.remove(0, overLength);
         } catch (Exception e) {
            e.printStackTrace();
         }
      }
   }
   
}

