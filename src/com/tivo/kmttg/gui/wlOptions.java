package com.tivo.kmttg.gui;

import java.util.Hashtable;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

import com.tivo.kmttg.util.log;

public class wlOptions {
   JComponent[] components;
   JLabel l_help, l_title, l_keyword, l_title_keyword, l_actor, l_director;
   JTextField tf_title, tf_keyword, tf_title_keyword, tf_actor, tf_director;
   JCheckBox cb_autorecord;
   
   public wlOptions() {      
      createComponents();      
   }
   
   private void createComponents() {
      l_help = new JLabel("KEYWORD LOGIC: keywords=>REQ, -keywords=>NOT, (keywords)=>OPT");
      l_title = new JLabel("Wishlist Title");
      tf_title = new JTextField(15);      
      l_keyword = new JLabel("Keywords (keywords1,keywords2...)");
      tf_keyword = new JTextField(30);      
      l_title_keyword = new JLabel("Title Keywords (keywords1,keywords2...)");
      tf_title_keyword = new JTextField(30);
      l_actor = new JLabel("Actor (First Last,First2 Last2...)");
      tf_actor = new JTextField(30);      
      l_director = new JLabel("Director (First Last, First2 Last2...)");
      tf_director = new JTextField(30);      
      cb_autorecord = new JCheckBox("Auto Record", false);
      
      components = new JComponent[] {
         l_help,
         l_title, tf_title,
         l_keyword, tf_keyword,
         l_title_keyword, tf_title_keyword,
         l_actor, tf_actor,
         l_director, tf_director,
         cb_autorecord
      };
   }
   
   public Hashtable<String,String> promptUser(String title, Hashtable<String,String> h) {
      if (h != null && h.size() > 0) {
         clearFields();
         setValues(h);
      }
      int response = JOptionPane.showConfirmDialog(
         null, components, title, JOptionPane.OK_CANCEL_OPTION
      );
      if (response == JOptionPane.OK_OPTION) {
         if (h == null)
            h = new Hashtable<String,String>();
         String t = (String)tf_title.getText();
         String keyword = (String)tf_keyword.getText();
         String title_keyword = (String)tf_title_keyword.getText();
         String actor = (String)tf_actor.getText();
         String director = (String)tf_director.getText();
         
         // Check for minimum wishlist specification requirements
         if (keyword.length() < 1 && title_keyword.length() < 1 && actor.length() < 1 && director.length() < 1) {
            log.error("Wishlist must contain at least 1 keyword");
            return null;
         }
         
         if (t.length() > 0) h.put("title", t);
         if (keyword.length() > 0) h.put("keyword", keyword);
         if (title_keyword.length() > 0) h.put("title_keyword", title_keyword);
         if (actor.length() > 0) h.put("actor", actor);
         if (director.length() > 0) h.put("director", director);
         if (cb_autorecord.isSelected())
            h.put("autorecord", "yes");
         return h;
      } else {
         return null;
      }
   }
   
   private void setValues(Hashtable<String,String> h) {
      if (h.containsKey("title") && h.get("title").length() > 0)
         tf_title.setText(h.get("title"));
      if (h.containsKey("keyword") && h.get("keyword").length() > 0)
         tf_keyword.setText(h.get("keyword"));
      if (h.containsKey("title_keyword") && h.get("title_keyword").length() > 0)
         tf_title_keyword.setText(h.get("title_keyword"));
      if (h.containsKey("actor") && h.get("actor").length() > 0)
         tf_actor.setText(h.get("actor"));
      if (h.containsKey("director") && h.get("director").length() > 0)
         tf_director.setText(h.get("director"));
   }
   
   private void clearFields() {
      tf_title.setText("");
      tf_keyword.setText("");
      tf_title_keyword.setText("");
      tf_actor.setText("");
      tf_director.setText("");
   }
}
