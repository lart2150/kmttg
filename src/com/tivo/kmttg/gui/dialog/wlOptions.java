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
package com.tivo.kmttg.gui.dialog;

import java.util.Arrays;
import java.util.Hashtable;
import java.util.Optional;

import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
//import com.tivo.kmttg.JSON.JSONException;
//import com.tivo.kmttg.JSON.JSONObject;
//import com.tivo.kmttg.main.config;
//import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.util.log;
//import com.tivo.kmttg.util.string;

public class wlOptions {
   VBox components;
   Label label;
   Label l_help, l_title, l_keyword, l_title_keyword, l_actor, l_director;
   TextField tf_title, tf_keyword, tf_title_keyword, tf_actor, tf_director;
   CheckBox cb_autorecord;
   Label l_categories = null;
   ChoiceBox<String> cb_categories = null;
   JSONArray wishlistCategories = null;
   
   public wlOptions() {      
      createComponents();      
   }
   
   private void createComponents() {
      label = new Label();
      l_help = new Label("KEYWORD LOGIC: keywords=>REQ, -keywords=>NOT, (keywords)=>OPT");
      l_title = new Label("Wishlist Title");
      tf_title = new TextField(); tf_title.setPrefWidth(15);
      l_keyword = new Label("Keywords (keywords1,keywords2...)");
      tf_keyword = new TextField(); tf_keyword.setPrefWidth(30);     
      l_title_keyword = new Label("Title Keywords (keywords1,keywords2...)");
      tf_title_keyword = new TextField(); tf_title_keyword.setPrefWidth(30);
      l_actor = new Label("Actor (First Last,First2 Last2...)");
      tf_actor = new TextField(); tf_actor.setPrefWidth(30);    
      l_director = new Label("Director (First Last, First2 Last2...)");
      tf_director = new TextField(); tf_director.setPrefWidth(30);    
      cb_autorecord = new CheckBox("Auto Record"); cb_autorecord.setSelected(false);
      // Intentionally disable for now since category Ids don't seem to work
      //createCategories();
      
      components = new VBox();
      components.setSpacing(5);
      if (cb_categories != null) {
         components.getChildren().addAll(
            label,
            l_help,
            l_title, tf_title,
            l_keyword, tf_keyword,
            l_title_keyword, tf_title_keyword,
            l_actor, tf_actor,
            l_director, tf_director,
            l_categories, cb_categories,
            cb_autorecord
         );
      } else {
         components.getChildren().addAll(
            label,
            l_help,
            l_title, tf_title,
            l_keyword, tf_keyword,
            l_title_keyword, tf_title_keyword,
            l_actor, tf_actor,
            l_director, tf_director,
            cb_autorecord
         );
      }
   }
   
   /*private void createCategories() {
      if (wishlistCategories == null) {
         wishlistCategories = getWishlistCategoryIds(config.getTivoNames().firstElement());
      }
      if (wishlistCategories != null) {
         l_categories = new Label("Category");
         cb_categories = new ChoiceBox();
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
   }*/
   
   @SuppressWarnings("static-access")
   public JSONObject promptUser(String title, Hashtable<String,String> hash) {
      if (hash != null && hash.size() > 0) {
         clearFields();
         setValues(hash);
      }
      label.setText(title);
      Dialog<?> dialog = new Dialog<>();
      dialog.initOwner(config.gui.getFrame());
      config.gui.LoadIcons((Stage) dialog.getDialogPane().getScene().getWindow());
      config.gui.setFontSize(dialog, config.FontSize);
      dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
      dialog.setTitle("Create Wishlist");
      dialog.getDialogPane().setContent(components);
      Optional<?> response = dialog.showAndWait();
      if (response != null && response.get().equals(ButtonType.OK)) {
         Hashtable<String,String> h = new Hashtable<String,String>();
         String t = (String)tf_title.getText();
         String keyword = (String)tf_keyword.getText();
         String title_keyword = (String)tf_title_keyword.getText();
         String actor = (String)tf_actor.getText();
         String director = (String)tf_director.getText();
         //String category = (String)cb_categories.getSelectedItem();
         
         // Check for minimum wishlist specification requirements
         if (keyword.length() < 1 &&
             title_keyword.length() < 1 &&
             actor.length() < 1 &&
             director.length() < 1 ) {
            log.error("Wishlist must contain at least 1 keyword");
            return null;
         }
         
         if (t.length() > 0) h.put("title", t);
         if (keyword.length() > 0) h.put("keyword", keyword);
         if (title_keyword.length() > 0) h.put("title_keyword", title_keyword);
         if (actor.length() > 0) h.put("actor", actor);
         if (director.length() > 0) h.put("director", director);
         //if (category.length() > 0) h.put("categoryId", findCategoryId(category));
         if (cb_autorecord.isSelected())
            h.put("autorecord", "yes");
         return hashToJson(h);
      } else {
         return null;
      }
   }
   
   private JSONObject hashToJson(Hashtable<String,String> h) {
      try {
         JSONObject json = new JSONObject();
         // Title is always required
         json.put("title", h.get("title"));
         
         // auto record
         if (h.containsKey("autorecord"))
            json.put("autoRecord", true);
         
         if (h.containsKey("categoryId"))
            json.put("categoryId", h.get("categoryId"));
         
         String []s = {"title_keyword", "keyword"};
         for (int j=0; j<s.length; ++j) {
            if (h.containsKey(s[j])) {
               JSONArray opt = new JSONArray();
               JSONArray val = new JSONArray();
               String []fields = h.get(s[j]).split(",");
               for (int i=0; i<fields.length; ++i) {
                  String field = fields[i];
                  if (field.length() > 0) {
                     String type = "required";
                     if (field.startsWith("(")) {
                        type = "optional";
                        field = field.replaceAll("\\(", "");
                        field = field.replaceAll("\\)", "");
                     }
                     if (field.startsWith("-")) {
                        type = "not";
                        field = field.replaceFirst("-", "");
                     }
                     opt.put(type);
                     val.put(field);
                     if (s[j].equals("title_keyword")) {
                        json.put("titleKeywordOp", opt);
                        json.put("titleKeyword", val);
                     }
                     if (s[j].equals("keyword")) {
                        json.put("keywordOp", opt);
                        json.put("keyword", val);
                     }
                  }
               }
            }
         }
         
         String []s2 = {"actor", "director"};
         for (int j=0; j<s.length; ++j) {
            if (h.containsKey(s2[j])) {
               JSONArray opt = new JSONArray();
               JSONArray val = new JSONArray();
               String []fields = h.get(s2[j]).split(",");
               for (int i=0; i<fields.length; ++i) {
                  String field = fields[i];
                  if (field.length() > 0) {
                     String type = "required";
                     if (field.startsWith("(")) {
                        type = "optional";
                        field = field.replaceAll("\\(", "");
                        field = field.replaceAll("\\)", "");
                     }
                     if (field.startsWith("-")) {
                        type = "not";
                        field = field.replaceFirst("-", "");
                     }
                     String []name = field.split("\\s+");
                     if (name.length == 2) {
                        JSONObject js = new JSONObject();
                        js.put("type", "credit");
                        js.put("role", s2[j]);
                        js.put("first", name[0]);
                        js.put("last", name[1]);
                        opt.put(type);
                        val.put(js);
                        json.put("creditOp", opt);
                        json.put("credit", val);
                     } else {
                        log.error("Ignoring actor/director not specified as 'First Last'");
                     }
                  }
               }
            }
         }
         return json;
      } catch (JSONException e) {
         log.error(e.getMessage());
         log.error(Arrays.toString(e.getStackTrace()));
      }
      return null;
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
   /*private JSONArray getWishlistCategoryIds(String tivoName) {
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
   }*/
   
   /*private String findCategoryId(String name) {
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
   }*/
}
