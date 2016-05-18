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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Stack;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.MyTooltip;
import com.tivo.kmttg.gui.gui;
import com.tivo.kmttg.gui.remote.util;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class AdvSearch {
   private Stage dialog = null;
   private VBox content = null;
   private ChoiceBox<String> savedEntries = null;
   private ChoiceBox<String> creditKeywordRole = null;
   private ChoiceBox<String> collectionType = null;
   private ChoiceBox<String> minStarRating = null;
   private ListView<String> category = null;
   private TextField title = null;
   private TextField titleKeyword = null;
   private TextField subtitleKeyword = null;
   private TextField keywords = null;
   private TextField subtitle = null;
   private TextField descriptionKeyword = null;
   private TextField channels = null;
   private TextField originalAirYear = null;
   private TextField creditKeyword = null;
   private ChoiceBox<String> hdtv = null;
   private CheckBox receivedChannelsOnly = null;
   private CheckBox favoriteChannelsOnly = null;
   private String tivoName = null;
   private String saveFile = "wishlists.ini";
   private LinkedHashMap<String,JSONObject> entries = new LinkedHashMap<String,JSONObject>();
   private static double pos_x = -1;
   private static double pos_y = -1;

   public void display(Stage frame, String tivoName, int max_search) {
      this.tivoName = tivoName;
      // Create dialog if not already created
      if (dialog == null) {
         create(frame);
         
         // Parse saveFile to define current configuration
         readFile(config.programDir + File.separator + saveFile);
      }
      
      // Display the dialog
      if (config.getTivoUsername() == null)
         category.setDisable(true);
      else
         category.setDisable(false);
      if (pos_x != -1)
         dialog.setX(pos_x);
      if (pos_y != -1)
         dialog.setY(pos_y);
      dialog.show();
      Platform.runLater(new Runnable() {
         @Override
         public void run() {
            title.requestFocus();
         }
      });
   }
  
   private void create(Stage frame) {      
      // Create all the components of the dialog
      Label savedEntries_label = new Label("Saved entries");
      savedEntries = new ChoiceBox<String>();
      savedEntries.getItems().add("Default");
      savedEntries.setValue("Default");
      savedEntries.setTooltip(getToolTip("savedEntries"));
      savedEntries.valueProperty().addListener(new ChangeListener<String>() {
         @Override public void changed(ObservableValue<? extends String> ov, String oldVal, String newVal) {
             if (newVal != null)
                setValues(newVal);
         }
      });

      creditKeywordRole = new ChoiceBox<String>();
      creditKeywordRole.getItems().addAll("actor", "director", "producer", "executiveProducer", "writer");
      creditKeywordRole.setValue("actor");
      creditKeywordRole.setTooltip(getToolTip("creditKeywordRole"));
      
      Label collectionType_label = new Label("Genre");
      collectionType = new ChoiceBox<String>();
      collectionType.getItems().addAll("ALL", "movie", "series", "special");
      collectionType.setValue("ALL");
      collectionType.setTooltip(getToolTip("collectionType"));
      
      Label minStarRating_label = new Label("Minimum rating");
      minStarRating = new ChoiceBox<String>();
      minStarRating.getItems().addAll(
         "ALL",
         "one", "onePointFive",
         "two", "twoPointFive",
         "three", "threePointFive",
         "four"
      );
      minStarRating.setValue("ALL");
      minStarRating.setTooltip(getToolTip("minStarRating"));
      
      Label category_label = new Label("Category");
      category = new ListView<String>();
      category.setPrefHeight(100);
      category.getItems().add("ALL");
      addCategories(tivoName); // This runs in background mode
      category.getSelectionModel().select("ALL");
      category.scrollTo("ALL");
      category.setTooltip(getToolTip("category"));
                        
      Label title_label = new Label("Title");
      title = new TextField();
      title.setPrefWidth(30);
      title.setTooltip(getToolTip("title"));
      
      Label titleKeyword_label = new Label("Title keyword");
      titleKeyword = new TextField();
      titleKeyword.setPrefWidth(30);
      titleKeyword.setTooltip(getToolTip("titleKeyword"));
      
      Label subtitleKeyword_label = new Label("Subtitle keyword");
      subtitleKeyword = new TextField();
      subtitleKeyword.setPrefWidth(30);
      subtitleKeyword.setTooltip(getToolTip("subtitleKeyword"));

      Label keywords_label = new Label("Keywords");
      keywords = new TextField();
      keywords.setPrefWidth(30);
      keywords.setTooltip(getToolTip("keywords"));

      Label subtitle_label = new Label("Subtitle");
      subtitle = new TextField();
      subtitle.setPrefWidth(30);
      subtitle.setTooltip(getToolTip("subtitle"));

      Label descriptionKeyword_label = new Label("Description keyword");
      descriptionKeyword = new TextField();
      descriptionKeyword.setPrefWidth(30);
      descriptionKeyword.setTooltip(getToolTip("descriptionKeyword"));

      Label channels_label = new Label("Restrict channels");
      channels = new TextField();
      channels.setPrefWidth(30);
      channels.setTooltip(getToolTip("channels"));

      Label originalAirYear_label = new Label("Year");
      originalAirYear = new TextField();
      originalAirYear.setPrefWidth(30);
      originalAirYear.setTooltip(getToolTip("originalAirYear"));

      creditKeyword = new TextField();
      creditKeyword.setPrefWidth(30);
      creditKeyword.setTooltip(getToolTip("creditKeyword"));
      
      Label hdtv_label = new Label("Recording types");
      hdtv = new ChoiceBox<String>();
      hdtv.getItems().addAll("both", "HD", "SD");
      hdtv.setTooltip(getToolTip("hdtv"));
      hdtv.setValue("HD");
      
      receivedChannelsOnly = new CheckBox("Received channels only");
      receivedChannelsOnly.setTooltip(getToolTip("receivedChannelsOnly"));
      receivedChannelsOnly.setSelected(true);
      
      favoriteChannelsOnly = new CheckBox("Favorite channels only");
      favoriteChannelsOnly.setTooltip(getToolTip("favoriteChannelsOnly"));
      favoriteChannelsOnly.setSelected(false);
            
      Button search = new Button("Search");
      search.setTooltip(getToolTip("search"));
      search.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            SearchCB();
         }
      });
      
      Button save = new Button("Save...");
      save.setTooltip(getToolTip("save"));
      save.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            String text = (String)savedEntries.getSelectionModel().getSelectedItem();
            if (text.equals("Default"))
               text = "";
            TextInputDialog d = new TextInputDialog(text);
            d.setTitle("Enter wishlist name");
            Optional<String> result = d.showAndWait();
            if (result.isPresent()){
                if (result.get().length() > 0)
                   addEntry(result.get());
            }
         }
      });
      
      Button delete = new Button("Delete");
      delete.setTooltip(getToolTip("delete"));
      delete.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            String entry = (String)savedEntries.getSelectionModel().getSelectedItem();
            if (! entry.equals("Default"))
               deleteEntry(entry);
         }
      });
      
      Button close = new Button("Close");
      close.setTooltip(getToolTip("close"));
      close.setOnAction(new EventHandler<ActionEvent>() {
         public void handle(ActionEvent e) {
            pos_x = dialog.getX(); pos_y = dialog.getY();
            dialog.hide();
         }
      });
      
      // layout manager start
      content = new VBox();
      content.setPadding(new Insets(5,5,5,5));
      content.setSpacing(5);
            
      HBox row = new HBox();
      row.setSpacing(5);
      row.getChildren().addAll(savedEntries_label, savedEntries, save, delete);
      content.getChildren().add(row);
      
      // Grid pane layout with 2 columns
      GridPane grid = new GridPane();
      int gy = 0;
      grid.setHgap(5);
      // Setup Col 1 fixed, Col 2 fill horizontally
      ColumnConstraints noFill = new ColumnConstraints();
      noFill.setHgrow(Priority.NEVER);
      ColumnConstraints fillColumn = new ColumnConstraints();
      fillColumn.setFillWidth(true);
      fillColumn.setHgrow(Priority.ALWAYS);
      grid.getColumnConstraints().addAll(noFill, fillColumn); 
      
      grid.add(title_label, 0, gy);
      grid.add(title, 1, gy);
      
      gy++;
      grid.add(titleKeyword_label, 0, gy);
      grid.add(titleKeyword, 1, gy);
      
      gy++;
      grid.add(subtitle_label, 0, gy);
      grid.add(subtitle, 1, gy);
      
      gy++;
      grid.add(subtitleKeyword_label, 0, gy);
      grid.add(subtitleKeyword, 1, gy);
      
      gy++;
      grid.add(keywords_label, 0, gy);
      grid.add(keywords, 1, gy);
      
      gy++;
      grid.add(descriptionKeyword_label, 0, gy);
      grid.add(descriptionKeyword, 1, gy);
      
      gy++;
      grid.add(channels_label, 0, gy);
      grid.add(channels, 1, gy);
      
      gy++;
      grid.add(originalAirYear_label, 0, gy);
      grid.add(originalAirYear, 1, gy);
      
      gy++;
      grid.add(creditKeywordRole, 0, gy);
      grid.add(creditKeyword, 1, gy);
      
      gy++;
      row = new HBox();
      row.setSpacing(5);
      row.getChildren().addAll(collectionType, hdtv_label, hdtv);
      grid.add(collectionType_label, 0, gy);
      grid.add(row, 1, gy);
      
      gy++;
      row = new HBox();
      row.setSpacing(5);
      row.getChildren().addAll(category);
      grid.add(category_label, 0, gy);
      grid.add(row, 1, gy);
      
      gy++;
      row = new HBox();
      row.setSpacing(5);
      row.getChildren().addAll(minStarRating);
      grid.add(minStarRating_label, 0, gy);
      grid.add(row, 1, gy);
      
      content.getChildren().add(grid);
      
      row = new HBox();
      row.setSpacing(5);
      row.getChildren().addAll(receivedChannelsOnly, favoriteChannelsOnly);
      content.getChildren().add(row);
      
      row = new HBox();
      row.setAlignment(Pos.CENTER);
      row.setSpacing(5);
      row.getChildren().addAll(search, util.space(50), close);
      content.getChildren().add(row);
   
      // create dialog window
      dialog = new Stage();
      dialog.setOnCloseRequest(new EventHandler<WindowEvent>() {
         @Override
         public void handle(WindowEvent arg0) {
            pos_x = dialog.getX(); pos_y = dialog.getY();
         }
      });

      dialog.setTitle("Advanced Search");
      dialog.setScene(new Scene(content));
      config.gui.setFontSize(dialog.getScene(), config.FontSize);
      dialog.initOwner(frame);
      gui.LoadIcons(dialog);
      dialog.show();
   }
   
   private void deleteEntry(String entry) {
      entries.remove(entry);
      savedEntries.getItems().remove(savedEntries.getSelectionModel().getSelectedIndex());
      saveEntries();
   }
   
   private void readFile(String inputFile) {
      if (! file.isFile(inputFile))
         return;
      try {
         BufferedReader ini = new BufferedReader(new FileReader(inputFile));
         String line = null;
         String key = null;
         JSONObject json = new JSONObject();         
         while (( line = ini.readLine()) != null) {
            // Get rid of leading and trailing white space
            line = line.replaceFirst("^\\s*(.*$)", "$1");
            line = line.replaceFirst("^(.*)\\s*$", "$1");
            if (line.length() == 0) continue; // skip empty lines
            if (line.matches("^<.+>")) {
               if (key != null && json.length() > 0) {
                  entries.put(key, json);
                  uniqueEntry(key);
               }
               key = line.replaceFirst("<", "");
               key = key.replaceFirst(">", "");
               json = new JSONObject();
               continue;
            }
            
            String name = line.replaceFirst("=.+", "");
            String value = line.replaceFirst("^.+=", "");
            json.put(name, value);
         } // while
         // Last entry
         if (key != null && json.length() > 0) {
            entries.put(key, json);
            uniqueEntry(key);
         }
         ini.close();
         if (savedEntries.getItems().size() > 1)
            savedEntries.getSelectionModel().select(1);
      } catch (Exception e) {
         log.error("AdvSearch readFile error - " + e.getMessage());
      }
   }
   
   private void saveEntries() {
      String wFile = config.programDir + File.separator + saveFile;
      if (entries.isEmpty()) {
         log.warn("No entries to save - removing file: " + wFile);
         file.delete(wFile);
         return;
      }
      try {
         BufferedWriter ofp = new BufferedWriter(new FileWriter(wFile, false));
         String eol = "\r\n";
         for (String entry : entries.keySet()) {
            JSONObject json = entries.get(entry);
            ofp.write("<" + entry + ">" + eol);
            String[] items = {
               "title", "titleKeyword", "subtitleKeyword", "keywords", "subtitle",
               "descriptionKeyword", "channels", "originalAirYear",
               "creditKeywordRole", "creditKeyword", "collectionType", "category", "hdtv",
               "receivedChannelsOnly", "favoriteChannelsOnly", "minStarRating"
            };
            for (String item : items) {
               if (json.has(item))
                  ofp.write(item + "=" + json.getString(item) + eol);
            }
         }
         ofp.close();
         log.warn("Saved to file: " + wFile);
      } catch (Exception e) {
         log.error("AdvSearch saveEntries error - " + e.getMessage());
      }
   }
   
   private void addEntry(String entry) {
      try {
         log.warn("Saving wishlist entry: " + entry);
         JSONObject json = new JSONObject();
         json.put("title", string.removeLeadingTrailingSpaces(title.getText()));
         json.put("titleKeyword", string.removeLeadingTrailingSpaces(titleKeyword.getText()));
         json.put("subtitleKeyword", string.removeLeadingTrailingSpaces(subtitleKeyword.getText()));
         json.put("subtitle", string.removeLeadingTrailingSpaces(subtitle.getText()));
         json.put("descriptionKeyword", string.removeLeadingTrailingSpaces(descriptionKeyword.getText()));
         json.put("originalAirYear", string.removeLeadingTrailingSpaces(originalAirYear.getText()));
         json.put("creditKeyword", string.removeLeadingTrailingSpaces(creditKeyword.getText()));
         json.put("creditKeywordRole", creditKeywordRole.getSelectionModel().getSelectedItem());
         json.put("collectionType", collectionType.getSelectionModel().getSelectedItem());
         json.put("minStarRating", minStarRating.getSelectionModel().getSelectedItem());
         json.put("category", category.getSelectionModel().getSelectedItem());
         json.put("keywords", string.removeLeadingTrailingSpaces(keywords.getText()));
         json.put("channels", string.removeLeadingTrailingSpaces(channels.getText()));
         json.put("hdtv", hdtv.getSelectionModel().getSelectedItem());
         if (receivedChannelsOnly.isSelected())
            json.put("receivedChannelsOnly", "on");
         else
            json.put("receivedChannelsOnly", "off");
         if (favoriteChannelsOnly.isSelected())
            json.put("favoriteChannelsOnly", "on");
         else
            json.put("favoriteChannelsOnly", "off");
         entries.put(entry, json);
         saveEntries();
         uniqueEntry(entry);
      } catch (JSONException e) {
         log.error("AdvSearch addEntry error - " + e.getMessage());
      }
   }
   
   private void uniqueEntry(String entry) {
      Boolean add = true;
      for (int i=0; i<savedEntries.getItems().size(); ++i) {
         if (savedEntries.getItems().get(i).equals(entry))
            add = false;
      }
      if (add)
         savedEntries.getItems().add(entry);
      savedEntries.getSelectionModel().select(entry);
   }
   
   private void setValues(String entry) {
      if (entry.equals("Default"))
         resetToDefaults();
      else {
         try {
            JSONObject json = entries.get(entry);
            String text;
            text = "";
            if (json.has("title"))
               text = json.getString("title");
            title.setText(text);
            
            text = "";
            if (json.has("titleKeyword"))
               text = json.getString("titleKeyword");
            titleKeyword.setText(text);
            
            text = "";
            if (json.has("subtitleKeyword"))
               text = json.getString("subtitleKeyword");
            subtitleKeyword.setText(text);
            
            text = "";
            if (json.has("subtitle"))
               text = json.getString("subtitle");
            subtitle.setText(text);
            
            text = "";
            if (json.has("descriptionKeyword"))
               text = json.getString("descriptionKeyword");
            descriptionKeyword.setText(text);
            
            text = "";
            if (json.has("originalAirYear"))
               text = json.getString("originalAirYear");
            originalAirYear.setText(text);
            
            text = "";
            if (json.has("creditKeyword"))
               text = json.getString("creditKeyword");
            creditKeyword.setText(text);
           
            text = "actor";
            if (json.has("creditKeywordRole"))
               text = json.getString("creditKeywordRole");
            creditKeywordRole.getSelectionModel().select(text);
            
            text = "ALL";
            if (json.has("collectionType"))
               text = json.getString("collectionType");
            collectionType.getSelectionModel().select(text);
            
            text = "ALL";
            if (json.has("minStarRating"))
               text = json.getString("minStarRating");
            minStarRating.getSelectionModel().select(text);
            
            text = "ALL";
            if (json.has("category"))
               text = json.getString("category");
            category.getSelectionModel().select(text);
            category.scrollTo(text);
            
            text = "";
            if (json.has("keywords"))
               text = json.getString("keywords");
            keywords.setText(text);
            
            text = "";
            if (json.has("channels"))
               text = json.getString("channels");
            channels.setText(text);
            
            text = "HD";
            if (json.has("hdtv"))
               text = json.getString("hdtv");
            hdtv.getSelectionModel().select(text);
            
            receivedChannelsOnly.setSelected(json.getString("receivedChannelsOnly").equals("on"));
            favoriteChannelsOnly.setSelected(json.getString("favoriteChannelsOnly").equals("on"));
         } catch (JSONException e) {
            log.error("AdvSearch setValues error - " + e.getMessage());
         }
      }
   }
   
   private void resetToDefaults() {
      title.setText("");
      titleKeyword.setText("");
      subtitleKeyword.setText("");
      subtitle.setText("");
      descriptionKeyword.setText("");
      originalAirYear.setText("");
      creditKeyword.setText("");
      creditKeywordRole.getSelectionModel().select("actor");
      collectionType.getSelectionModel().select("ALL");
      minStarRating.getSelectionModel().select("ALL");
      category.getSelectionModel().select("ALL");
      category.scrollTo("ALL");
      keywords.setText("");
      channels.setText("");
      hdtv.getSelectionModel().select("HD");
      receivedChannelsOnly.setSelected(true);
      favoriteChannelsOnly.setSelected(false);
   }
   
   private void SearchCB() {
      try {
         String text;
         JSONObject json = new JSONObject();
         json.put("format", "idSet");
         json.put("namespace", "refserver");
         json.put("searchable", true);
         Date now = new Date();
         json.put("minStartTime", rnpl.getStringFromLongDate(now.getTime()));

         String type = (String)(collectionType.getSelectionModel().getSelectedItem());
         String cat = (String)category.getSelectionModel().getSelectedItem();

         text = string.removeLeadingTrailingSpaces(title.getText());
         if (text != null && text.length() > 0) {
            json.put("title", text);
         }
         text = string.removeLeadingTrailingSpaces(titleKeyword.getText());
         if (text != null && text.length() > 0) {
            json.put("titleKeyword", text);
         }
         text = string.removeLeadingTrailingSpaces(subtitleKeyword.getText());
         if (text != null && text.length() > 0) {
            json.put("subtitleKeyword", text);
         }
         text = string.removeLeadingTrailingSpaces(subtitle.getText());
         if (text != null && text.length() > 0) {
            json.put("subtitle", text);
         }
         text = string.removeLeadingTrailingSpaces(descriptionKeyword.getText());
         if (text != null && text.length() > 0) {
            json.put("descriptionKeyword", text);
         }
         if (cat != null && cat.equals("ALL"))
            cat = null;
         if (config.getTivoUsername() == null) {
            cat = null;
            category.getSelectionModel().select("ALL");
            category.scrollTo("ALL");
         }
         text = string.removeLeadingTrailingSpaces(originalAirYear.getText());
         if (text != null && text.length() > 0) {
            if (type.equals("movie") || (cat != null && cat.equals("Movies")))
               json.put("movieYear", text);
            else
               json.put("originalAirYear", text);
         }
         text = string.removeLeadingTrailingSpaces(creditKeyword.getText());
         if (text != null && text.length() > 0) {
            JSONArray creditArray = rnpl.parseCreditString(text, (String)creditKeywordRole.getSelectionModel().getSelectedItem());
            json.put("credit", creditArray);
         }
         text = (String)collectionType.getSelectionModel().getSelectedItem();
         if (! text.equals("ALL")) {
            json.put("collectionType", text);
         }
         text = (String)minStarRating.getSelectionModel().getSelectedItem();
         if (! text.equals("ALL")) {
            if (type.equals("movie") || cat != null)
               json.put("minStarRating", text);
         }
         text = string.removeLeadingTrailingSpaces(keywords.getText());
         if (text != null && text.length() > 0) {
            if (text.contains("(") || text.contains("-") || text.contains("+") || text.contains("*"))
               json.put("advancedKeyword", true);
            json.put("keyword", text);
         }
         String[] chans = null;
         text = string.removeLeadingTrailingSpaces(channels.getText());
         if (text != null && text.length() > 0) {
            chans = text.split("\\s+");
         }
         text = (String)hdtv.getSelectionModel().getSelectedItem();
         if (text.equals("HD"))
            json.put("hdtv", true);
         if (text.equals("SD"))
            json.put("hdtv", false);
         if (favoriteChannelsOnly.isSelected())
            json.put("favoriteChannelsOnly", true);
         if (! receivedChannelsOnly.isSelected())
            json.put("receivedChannelsOnly", false);

         //log.print(json.toString(3)); // debugging
         jobData job = new jobData();
         job.source                 = tivoName;
         job.tivoName               = tivoName;
         job.type                   = "remote";
         job.name                   = "Remote";
         job.search                 = config.gui.remote_gui.search_tab.tab;
         job.remote_search_max      = config.gui.remote_gui.search_tab.max.getValue();
         job.remote_adv_search      = true;
         job.remote_adv_search_json = json;
         if (chans != null)
            job.remote_adv_search_chans = chans;
         if (cat != null)
            job.remote_adv_search_cat = cat;
         jobMonitor.submitNewJob(job);
      } catch (JSONException e) {
         log.error("AdvSearch SearchCB error - " + e.getMessage());
      }
   }
   
   // This runs in background mode so as not to hang up GUI
   private void addCategories(final String tivoName) {
      Task<Void> task = new Task<Void>() {
         @Override public Void call() {
            Remote r = config.initRemote(tivoName);
            if (r.success) {
               final Stack<String> categoryNames = r.getCategoryNames(tivoName);
               if (categoryNames != null) {
                  class backgroundRun implements Runnable {
                     @Override public void run() {
                        for (String categoryName : categoryNames) {
                           category.getItems().add(categoryName);
                        }
                        // Now that categories are populated, set fields for 1st savedEntries name
                        if (savedEntries.getItems().size() > 1) {
                           setValues(savedEntries.getItems().get(1));
                        }
                     }
                  }
                  Platform.runLater(new backgroundRun());
               }
               r.disconnect();
            }
            return null;
         }
      };
      new Thread(task).start();
   }
      
   private Tooltip getToolTip(String component) {
      String text = "";
      if (component.equals("title")) {
         text =  "<b>Title</b><br>";
         text += "Match this show title exactly.<br>";
         text += "NOTE: Case insensitive.";
      }
      else if (component.equals("savedEntries")) {
         text =  "<b>Saved Entries</b><br>";
         text += "Contains previously saved search configurations. Select a named configuration<br>";
         text += "to set dialog entry settings to that saved configuration.<br>";
         text += "Select <b>Default</b> to reset all dialog entries to default/empty config.";
      }
      else if (component.equals("titleKeyword")) {
         text =  "<b>Title Keyword</b><br>";
         text += "Match this keyword in show main title text.<br>";
         text += "Cannot be used with other keyword types.<br>";
         text += "NOTE: Wildcard <b>*</b> is allowed with at least 1 alphanumeric character.<br>";
         text += "NOTE: Case insensitive.";
      }
      else if (component.equals("subtitleKeyword")) {
         text =  "<b>Subtitle Keyword</b><br>";
         text += "Match this keyword in show subtitle text.<br>";
         text += "Cannot be used with other keyword types.<br>";
         text += "NOTE: Wildcard <b>*</b> is allowed with at least 1 alphanumeric character.<br>";
         text += "NOTE: Case insensitive.";
      }
      else if (component.equals("keywords")) {
         text =  "<b>Keywords</b><br>";
         text += "This can be of two forms:<br>";
         text += "1. A simple keyword or phrase to match titles, subtitles, or descriptions of shows.<br>";
         text += "2. A complex list of space separated keywords with boolean operators:<br>";
         text += "<b>+keyword</b>: + prefix indicates required keyword (AND).<br>";
         text += "<b>-keyword</b>: - prefix indicates required missing keyword (NOT).<br>";
         text += "<b>(keyword)</b>: keyword inside parentheses indicate optional (OR).<br>";
         text += "<b>keyword*</b>: * char is wildcard but requires 1 alphanumeric char with it.<br>";
         text += "You can have multiple keyword operators each separated by a space.<br>";
         text += "Cannot be used with other keyword types.<br>";
         text += "NOTE: Case insensitive.";
      }
      else if (component.equals("subtitle")) {
         text =  "<b>Subtitle</b><br>";
         text += "Match this show subtitle exactly.<br>";
         text += "NOTE: Case insensitive.";
      }
      else if (component.equals("descriptionKeyword")) {
         text =  "<b>Description keyword</b><br>";
         text += "Match this keyword or phrase in show description text.<br>";
         text += "NOTE: Wildcard <b>*</b> is allowed with at least 1 alphanumeric character.<br>";
         text += "NOTE: Subtitle text is also considered as part of description.<br>";
         text += "Cannot be used with other keyword types.<br>";
         text += "NOTE: Case insensitive.";
      }
      else if (component.equals("channels")) {
         text =  "<b>Restrict channels</b><br>";
         text += "A list of one or more channel numbers to restrict matches to.<br>";
         text += "For more than 1 channel separate each channel number by a space.";
      }
      else if (component.equals("originalAirYear")) {
         text =  "<b>Year</b><br>";
         text += "Match only shows whose original air date happened in given year.<br>";
         text += "If movie genre is selected, it matches the year the movie was released.<br>";
         text += "NOTE: You can use a single year here (multiple years not supported).";
      }
      else if (component.equals("creditKeywordRole")) {
         text =  "<b>Other</b><br>";
         text += "Set auxilary matching criteria to this selected role.";
      }
      else if (component.equals("creditKeyword")) {
         text =  "<b>Other keyword</b><br>";
         text += "Match this exact given name in role selected to the left of this field.<br>";
         text += "Enter in form of <b>FirstName LastName</b>. EXAMPLE: <b>clint eastwood</b><br>";
         text += "If only 1 string is given then it is assumed to be <b>LastName</b>.<br>";
         text += "If you want to specify more than 1 name then use a comma between names, e.g.:<br>";
         text += "<b>clint eastwood, tommy jones</b><br>";
         text += "NOTE: Multiple names will match using OR logic, not AND.<br>";
         text += "NOTE: Case insensitive.";
      }
      else if (component.equals("collectionType")) {
         text =  "<b>Genre</b><br>";
         text += "Limit matches to shows in this genre.<br>";
         text += "Default is <b>ALL</b> which means show can be in any genre, else<br>";
         text += "match the specific genre selected in this list.<br>";
         text += "Selecting Genre=movie without providing any other keywords is equivalent<br>";
         text += "to selection Category=Movies without keywords.<br>";
         text += "<b>NOTE: To search Genre=movie without any other keywords provided,<br>";
         text += "kmttg has to be configured with your tivo.com username & password<br>";
         text += "(located under config->Tivos tab)</b>.";
      }
      else if (component.equals("minStarRating")) {
         text =  "<b>Minimum rating</b><br>";
         text += "Minimum star rating for movies.<br>";
         text += "NOTE: This is only used if Genre=movie or Category is not set to ALL.<br>";
         text += "Default is <b>ALL</b> which means any rating.";
      }
      else if (component.equals("category")) {
         text =  "<b>Category</b><br>";
         text += "Limit matches to shows in this category.<br>";
         text += "Default is <b>ALL</b> which means show can be in any category, else<br>";
         text += "match the specific category selected in this list.<br>";
         text += "<b>NOTE: This field is only available if kmttg has access to your tivo.com<br>";
         text += "username & password (located under config->Tivos tab)</b>.";
      }
      else if (component.equals("hdtv")) {
         text =  "<b>Recording types</b><br>";
         text += "both = match both HD and SD recordings.<br>";
         text += "HD = match only HD recordings.<br>";
         text += "SD = match only SD recordings.";
      }
      else if (component.equals("receivedChannelsOnly")) {
         text =  "<b>Received channels only</b><br>";
         text += "When disabled also include channels turned off in <b>Channel List</b>";
      }
      else if (component.equals("favoriteChannelsOnly")) {
         text =  "<b>Favorite channels only</b><br>";
         text += "When enabled match only channels set in <b>Favorites List</b>";
      }
      else if (component.equals("search")) {
         text =  "<b>Search</b><br>";
         text += "Initiate search using given search criteria.<br>";
         text += "Matches will be listed in Remote <b>Search</b> table.";
      }
      else if (component.equals("save")) {
         text =  "<b>Save...</b><br>";
         text += "Save the current search criteria with a name of your choosing.<br>";
         text += "The name you use will be added to <b>Saved entries</b> pulldown so<br>";
         text += "that you can restore the associated search criteria settings at any time.<br>";
         text += "By saving search criteria you are effectively creating portable wishlist<br>";
         text += "entries that won't be lost when replacing TiVo units.";
      }
      else if (component.equals("delete")) {
         text =  "<b>Delete</b><br>";
         text += "Remove a previously saved wishlist entry.<br>";
         text += "NOTE: The <b>Default</b> entry cannot be deleted.";
      }
      else if (component.equals("close")) {
         text =  "<b>Close</b><br>";
         text += "Close the <b>Advanced Search</b> dialog window.";
      }
      
      return MyTooltip.make(text);
   }
}
