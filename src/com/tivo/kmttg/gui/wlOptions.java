package com.tivo.kmttg.gui;

import java.util.Hashtable;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class wlOptions {
   JComponent[] components;
   JLabel label;
   JLabel l_help, l_title, l_keyword, l_title_keyword, l_actor, l_director;
   JTextField tf_title, tf_keyword, tf_title_keyword, tf_actor, tf_director;
   JCheckBox cb_autorecord;
   JLabel l_categories = null;
   JComboBox cb_categories = null;
   JSONArray wishlistCategories = null;
   
   public wlOptions() {      
      createComponents();      
   }
   
   private void createComponents() {
      label = new JLabel();
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
      // Intentionally disable for now since category Ids don't seem to work
      //createCategories();
      
      if (cb_categories != null) {
         components = new JComponent[] {
            label,
            l_help,
            l_title, tf_title,
            l_keyword, tf_keyword,
            l_title_keyword, tf_title_keyword,
            l_actor, tf_actor,
            l_director, tf_director,
            l_categories, cb_categories,
            cb_autorecord
         };
      } else {
         components = new JComponent[] {
            label,
            l_help,
            l_title, tf_title,
            l_keyword, tf_keyword,
            l_title_keyword, tf_title_keyword,
            l_actor, tf_actor,
            l_director, tf_director,
            cb_autorecord
         };
      }
   }
   
   private void createCategories() {
      if (wishlistCategories == null) {
         wishlistCategories = getWishlistCategoryIds(config.getTivoNames().firstElement());
      }
      if (wishlistCategories != null) {
         l_categories = new JLabel("Category");
         cb_categories = new JComboBox();
         cb_categories.addItem("");
         try {
            for (int i=0; i<wishlistCategories.length(); ++i) {
               JSONObject json = wishlistCategories.getJSONObject(i);
               if (json.has("subcategories")) {
                  JSONArray a = json.getJSONArray("subcategories");
                  for (int j=0; j<a.length(); ++j) {
                     String name = json.getString("name") + ": ";
                     name += a.getJSONObject(j).getString("name");
                     cb_categories.addItem(name);
                  }
               }
            }
         } catch (JSONException e) {
            log.error("createCategories - " + e.getMessage());
         }
      }
   }
   
   public Hashtable<String,String> promptUser(String title, Hashtable<String,String> h) {
      if (h != null && h.size() > 0) {
         clearFields();
         setValues(h);
      }
      label.setText(title);
      int response = JOptionPane.showConfirmDialog(
         null, components, "Create Wishlist", JOptionPane.OK_CANCEL_OPTION
      );
      if (response == JOptionPane.OK_OPTION) {
         if (h == null)
            h = new Hashtable<String,String>();
         String t = (String)tf_title.getText();
         String keyword = (String)tf_keyword.getText();
         String title_keyword = (String)tf_title_keyword.getText();
         String actor = (String)tf_actor.getText();
         String director = (String)tf_director.getText();
         String category = (String)cb_categories.getSelectedItem();
         
         // Check for minimum wishlist specification requirements
         if (keyword.length() < 1 &&
             title_keyword.length() < 1 &&
             actor.length() < 1 &&
             director.length() < 1 &&
             category.length() < 1) {
            log.error("Wishlist must contain at least 1 keyword");
            return null;
         }
         
         if (t.length() > 0) h.put("title", t);
         if (keyword.length() > 0) h.put("keyword", keyword);
         if (title_keyword.length() > 0) h.put("title_keyword", title_keyword);
         if (actor.length() > 0) h.put("actor", actor);
         if (director.length() > 0) h.put("director", director);
         if (category.length() > 0) h.put("categoryId", findCategoryId(category));
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
      label.setText("");
      tf_title.setText("");
      tf_keyword.setText("");
      tf_title_keyword.setText("");
      tf_actor.setText("");
      tf_director.setText("");
   }
   
   // Build list of top level categories plus sub-categories which can be used
   // for Wishlist creation
   private JSONArray getWishlistCategoryIds(String tivoName) {
      Remote r = new Remote(tivoName, true);
      if (r.success) {
         try {
            JSONObject json = new JSONObject();
            // 1st get top level categories and filter out those with partnerId
            json.put("orderBy", "label");
            json.put("topLevelOnly", true);
            json.put("noLimit", true);
            JSONObject result = r.Command("categorySearch", json);
            if (result != null && result.has("category")) {
               JSONArray top = result.getJSONArray("category");
               for (int i=0; i<top.length(); ++i) {
                  if (top.getJSONObject(i).has("partnerId"))
                     top.remove(i);
               }
               
               // Build initial return JSONArray with top level info
               JSONArray categories = new JSONArray();
               for (int i=0; i<top.length(); ++i) {
                  JSONObject j = new JSONObject();
                  j.put("name", top.getJSONObject(i).getString("label"));
                  j.put("categoryId", top.getJSONObject(i).getString("categoryId"));
                  categories.put(j);
               }
               
               // Now add in sub-categories
               for (int i=0; i<categories.length(); ++i) {
                  JSONObject j = new JSONObject();
                  j.put("orderBy", "label");
                  j.put("noLimit", true);
                  j.put("parentCategoryId", categories.getJSONObject(i).get("categoryId"));
                  result = r.Command("categorySearch", j);
                  if (result != null && result.has("category")) {
                     JSONArray a = result.getJSONArray("category");
                     JSONArray sa = new JSONArray();
                     for (int k=0; k<a.length(); ++k) {
                        JSONObject sj = new JSONObject();
                        sj.put("name", a.getJSONObject(k).getString("label"));
                        sj.put("categoryId", a.getJSONObject(k).getString("categoryId"));
                        sa.put(sj);
                     }
                     categories.getJSONObject(i).put("subcategories", sa);
                  }
               }               
               return categories;
            }
         } catch (JSONException e) {
            log.error("getWishlistCategoryIds - " + e.getMessage());
            return null;
         }
      }
      return null;
   }
   
   private String findCategoryId(String name) {
      String main, sub=null;
      if (name.contains(":")) {
         String[] s = name.split(":");
         main = s[0];
         sub = s[1];
         main = string.removeLeadingTrailingSpaces(main);
         sub = string.removeLeadingTrailingSpaces(sub);
      } else {
         main = name;
      }
      try {
         for (int i=0; i<wishlistCategories.length(); ++i) {
            JSONObject json = wishlistCategories.getJSONObject(i);
            if (json.getString("name").equals(main)) {
               if (sub == null)
                  return json.getString("categoryId");
               else {
                  JSONArray a = json.getJSONArray("subcategories");
                  for (int j=0; j<a.length(); ++j) {
                     if (a.getJSONObject(j).getString("name").equals(sub)) {
                        log.print("name=" + name + " categoryId=" + a.getJSONObject(j).getString("categoryId"));
                        return a.getJSONObject(j).getString("categoryId");
                     }
                  }
               }
            }
         }
      } catch (JSONException e) {
         log.error("findCategoryId - " + e.getMessage());
      }
      return null;
   }
}
