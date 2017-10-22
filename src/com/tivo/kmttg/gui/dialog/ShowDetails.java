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

import java.util.HashSet;
import java.util.Set;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.gui;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.util.log;

public class ShowDetails {
   private Stage dialog = null;
   private Label mainTitle = null;
   private Label subTitle = null;
   private Label time = null;
   private Label channel = null;
   private Label description = null;
   private Label otherInfo = null;
   private Label actorInfo = null;
   private Label image = null;
   private double x=-1, y=-1;
   
   public ShowDetails(Stage frame, JSONObject json) {
      create(frame);
   }
   
   private void create(Stage frame) {
      int minWidth = 400;
      if (dialog == null) {
         mainTitle = new Label("");
         mainTitle.setMinWidth(minWidth);
         mainTitle.setMaxWidth(minWidth);
         mainTitle.setWrapText(true);
         mainTitle.getStyleClass().add("show_details_title");
         // Increase font size
         mainTitle.setFont(
            new Font(
               mainTitle.getFont().getFamily(),
               mainTitle.getFont().getSize()+5
            )
         );
         
         subTitle = new Label("");
         subTitle.setMinWidth(minWidth);
         subTitle.setMaxWidth(minWidth);
         subTitle.setWrapText(true);
         subTitle.getStyleClass().add("show_details_title");
         
         time = new Label("");
         time.getStyleClass().add("show_details_other");
         
         channel = new Label("");
         channel.getStyleClass().add("show_details_other");
         
         description = new Label("");
         description.setMinWidth(minWidth);
         description.setMaxWidth(minWidth);
         description.setWrapText(true);
         description.getStyleClass().add("show_details_text");
         
         otherInfo = new Label("");
         otherInfo.setMinWidth(minWidth);
         otherInfo.setMaxWidth(minWidth);
         otherInfo.setWrapText(true);
         otherInfo.getStyleClass().add("show_details_other");
         
         actorInfo = new Label("");
         actorInfo.setMinWidth(minWidth);
         actorInfo.setMaxWidth(minWidth);
         actorInfo.setWrapText(true);
         actorInfo.getStyleClass().add("show_details_actor");
         
         image = new Label("");
         image.getStyleClass().add("show_details_title");
         
         // Start of layout management         
         VBox left_panel = new VBox();
         left_panel.setPadding(new Insets(0,0,0,3));
         left_panel.getStyleClass().add("show_details_bg");
         left_panel.setSpacing(2);
         left_panel.getChildren().addAll(mainTitle, subTitle, time, channel, description, otherInfo, actorInfo);         
         
         VBox right_panel = new VBox();         
         right_panel.setAlignment(Pos.CENTER);
         right_panel.getStyleClass().add("show_details_bg");
         right_panel.setSpacing(2);         
         right_panel.getChildren().add(image);         
         
         HBox main_panel = new HBox();
         main_panel.setSpacing(0);
         main_panel.getChildren().addAll(left_panel, right_panel);
         
         dialog = new Stage();
         dialog.setResizable(false);
         dialog.setTitle("Show information");
         dialog.initOwner(frame);
         gui.LoadIcons(dialog);
         // This so we can restore original dialog position when re-opened
         dialog.setOnCloseRequest(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent arg0) {
               x = dialog.getX(); y = dialog.getY();
            }
         });
         Scene scene = new Scene(new VBox());
         config.gui.setFontSize(scene, config.FontSize);
         ((VBox) scene.getRoot()).getChildren().add(main_panel);
         dialog.setScene(scene);
      }
   }
   
   public void update(Node node, String tivoName, String recordingId) {
      if ( ! config.rpcEnabled(tivoName) )
         return;
      JSONObject json = new JSONObject();
      try {
         json.put("levelOfDetail", "medium");
         json.put("recordingId", recordingId);
         update(node, tivoName, json);
      } catch (JSONException e) {
         log.error("ShowDetails update - " + e.getMessage());
      }
   }
   
   // Update dialog components with given JSON (runs as background task)
   public void update(final Node node, final String tivoName, final JSONObject initialJson) {
      if ( ! config.rpcEnabled(tivoName) )
         return;
      if (initialJson == null)
         return;
      Task<Void> task = new Task<Void>() {
         @Override public Void call() {
            JSONObject json = initialJson;
            try {
               // Need high level of detail
               if (json.has("levelOfDetail") && ! json.getString("levelOfDetail").equals("high")) {
                  Remote r = config.initRemote(tivoName);
                  if (r.success) {
                     JSONObject j = new JSONObject();
                     j.put("bodyId", r.bodyId_get());
                     j.put("levelOfDetail", "high");
                     JSONObject result;
                     if (json.has("recordingId")) {
                        j.put("recordingId", json.getString("recordingId"));
                        result = r.Command("recordingSearch", j);
                        if (result == null)
                           return null;
                        if (result.has("recording"))
                           json = result.getJSONArray("recording").getJSONObject(0);
                        else {
                           if (! json.has("title"))
                              return null;
                        }
                     }
                     else if (json.has("contentId")) {
                        j.put("contentId", json.getString("contentId"));
                        result = r.Command("contentSearch", j);
                        if (result == null)
                           return null;
                        if (result.has("content")) {
                           JSONObject content = result.getJSONArray("content").getJSONObject(0);
                           for (int ii=0; ii<content.names().length(); ii++) {
                              String name = content.names().getString(ii);
                              if (! json.has(name)) {
                                 json.put(name, content.get(name));
                              }
                           }
                        }
                        else {
                           if (! json.has("title"))
                              return null;
                        }
                     }
                  } else {
                     return null;
                  }
               } // json levelOfDetail
               
               if (json.has("idSetSource") && json.getJSONObject("idSetSource").has("collectionId")) {
                  // For SP table
                  Remote r = config.initRemote(tivoName);
                  if (r.success) {
                     JSONObject j = new JSONObject();
                     j.put("bodyId", r.bodyId_get());
                     j.put("count", 1);
                     j.put("levelOfDetail", "high");
                     j.put("collectionId", json.getJSONObject("idSetSource").getString("collectionId"));
                     JSONObject result = r.Command("collectionSearch", j);
                     if (result == null)
                        return null;
                     if (result.has("collection")) {
                        json = result.getJSONArray("collection").getJSONObject(0);
                     }
                     else {
                        if (! json.has("title"))
                           return null;
                     }
                  }
               }
               
               //log.print(json.toString(3));
            } catch (JSONException e) {
               log.error("ShowDetails update - " + e.getMessage());
               return null;
            }
            class backgroundRun implements Runnable {
               JSONObject json;
               public backgroundRun(JSONObject json) {
                  this.json = json;
               }
               @Override public void run() {
                  try {
                     // Title
                     String title = "";
                     if (json.has("title"))
                        title = json.getString("title");
                     if (json.has("movieYear"))
                        title += " (" + json.get("movieYear") + ")";
                     mainTitle.setText(title);            
                     
                     // Subtitle (possibly with season & episode information)
                     String subtitle = "";
                     if (json.has("subtitle"))
                        subtitle = "\"" + json.getString("subtitle") + "\"";
                     if (json.has("starRating"))
                        subtitle += "Stars: " + starsToNum(json.getString("starRating"));
                     if (json.has("seasonNumber") && json.has("episodeNum")) {
                        subtitle += " (Sea " + json.get("seasonNumber") +
                        " Ep " + json.getJSONArray("episodeNum").get(0) + ")";
                     }
                     subTitle.setText(subtitle);
                     
                     // channel
                     String chan = "";
                     if (json.has("channel")) {
                        JSONObject c = json.getJSONObject("channel");
                        if (c.has("channelNumber"))
                        chan = c.getString("channelNumber");
                        if (c.has("callSign"))
                           chan += " " + c.getString("callSign");
                     }
                     channel.setText(chan);
            
                     // time
                     String t = "";
                     if (json.has("startTime")) {
                        t = TableUtil.printableTimeFromJSON(json);
                        if (json.has("duration")) {
                           long s = TableUtil.getStartTime(json);
                           long e = TableUtil.getEndTime(json);
                           t += " (" + (int)Math.ceil((e-s)/60000.0) + " mins)";
                        }
                     }
                     time.setText(t);
                        
                     // description
                     String desc = "";
                     if (json.has("description")) {
                        desc = json.getString("description");
                        if (json.has("cc") && json.getBoolean("cc"))
                           desc += " (CC)";
                     }
                     description.setText(desc);
                     
                     // otherInfo
                     String other = "";
                     if (json.has("mpaaRating"))
                        other += "Rated " + json.getString("mpaaRating").toUpperCase() + "; ";
                     else if (json.has("tvRating"))
                        other += "TV " + json.getString("tvRating").toUpperCase() + "; ";
                     if (json.has("category")) {
                        JSONArray cat = json.getJSONArray("category");
                        Set<String> c = new HashSet<String>();
                        for (int i=0; i<cat.length(); ++i) {
                           if (cat.getJSONObject(i).has("label"))
                              c.add(cat.getJSONObject(i).getString("label"));
                        }
                        for (String s : c)
                           other += s + "; ";
                     }
                     if (json.has("hdtv") && json.getBoolean("hdtv"))
                        other += "HD; ";
                     if (json.has("originalAirdate")) {
                        other += "First Aired: " + json.getString("originalAirdate") + "; ";
                     }
                     if (other.length() > 0)
                        other = other.substring(0, other.length()-2);
                     otherInfo.setText(other);
                     
                     // actorInfo
                     String actors = "";
                     if (json.has("credit")) {
                        String separator = "";
                        int count = 0;
                        JSONArray credit = json.getJSONArray("credit");
                        // actors
                        for (int i=0; i<credit.length(); ++i) {
                           JSONObject a = credit.getJSONObject(i);
                           if (a.getString("role").equals("actor")) {
                              if (a.has("first") && a.has("last")) {
                                 if (count>0) separator = ", ";
                                 actors += separator + a.getString("first") + " " + a.getString("last");
                                 count++;
                              }
                           }
                        }
                        // hosts
                        Boolean pyTivo = false;
                        for (int i=0; i<credit.length(); ++i) {
                           JSONObject a = credit.getJSONObject(i);
                           if (a.getString("role").equals("host") && a.has("first")) {
                              if (a.getString("first").equals("container"))
                                 pyTivo = true;
                              if (a.has("last") && a.getString("last").contains("TRANSCODE"))
                                 pyTivo = true;
                           }
                        }
                        if (!pyTivo) {
                           for (int i=0; i<credit.length(); ++i) {
                              JSONObject a = credit.getJSONObject(i);
                              if (a.getString("role").equals("host")) {
                                 if (a.has("first") && a.has("last")) {
                                    if (count>0) separator = ", ";
                                    actors += separator + a.getString("first") + " " + a.getString("last");
                                    count++;
                                 }
                              }
                           }                     
                        }
                     }
                     actorInfo.setText(actors);
                     
                     // Right panel image
                     if (json.has("image")) {
                        image.setText("");
                        setImage(json.getJSONArray("image"));
                     }
                     else {
                        searchImage(tivoName, json);
                     }
                  } catch (JSONException e) {
                     log.error("ShowDetails update - " + e.getMessage());
                     return;
                  }
                  dialog.sizeToScene();
                  if (x != -1 && ! dialog.isShowing()) {
                     dialog.setX(x); dialog.setY(y);
                  }
                  dialog.show();
                  Platform.runLater(new Runnable() {
                     @Override public void run() {
                        node.requestFocus();
                     }
                  });
               }
            }
            Platform.runLater(new backgroundRun(json));
            return null;
         }
      };
      new Thread(task).start();
   }
   
   private String starsToNum(String name) {
      name = name.toLowerCase();
      name = name.replace("zero", "0");
      name = name.replace("one", "1");
      name = name.replace("two", "2");
      name = name.replace("three", "3");
      name = name.replace("four", "4");
      name = name.replace("five", "5");
      name = name.replace("point", ".");
      return name;
   }
   
   public Boolean isShowing() {
      return dialog.isShowing();
   }
   
   // Use contentId or collectionId to find and set image from given sourceJson
   private void searchImage(String tivoName, JSONObject sourceJson) {
      image.setText("");
      image.setGraphic(null);
      Remote r = config.initRemote(tivoName);
      if (r.success) {
         try {
            JSONObject json = new JSONObject();
            JSONObject template = new JSONObject();
            template.put("type", "responseTemplate");
            template.put("typeName", "category");
            template.put("fieldName", new JSONArray("[\"image\"]"));
            json.put("responseTemplate", template);
            if (sourceJson.has("contentId")) {
               json.put("contentId", sourceJson.getString("contentId"));
               JSONObject result = r.Command("contentSearch", json);
               if (result != null && result.has("content")) {
                  JSONObject content = result.getJSONArray("content").getJSONObject(0);
                  if (content.has("image")) {
                     setImage(content.getJSONArray("image"));
                  }
               }
            }
            else if (sourceJson.has("collectionId")) {
               json.put("collectionId", sourceJson.getString("collectionId"));
               JSONObject result = r.Command("collectionSearch", json);
               if (result != null && result.has("collection")) {
                  JSONObject collection = result.getJSONArray("collection").getJSONObject(0);
                  if (collection.has("image")) {
                     setImage(collection.getJSONArray("image"));
                  }
               }               
            }
         } catch (JSONException e) {
            log.error("ShowDetails searchImage - " + e.getMessage());
         }
         r.disconnect();
      }
   }
   
   private void setImage(JSONArray imageArray) {
      try {
         int diff = 500;
         int desired = 180;
         int index = 0;
         // 1st find closest to desired height
         for (int i=0; i<imageArray.length(); ++i) {
            JSONObject j = imageArray.getJSONObject(i);
            int h = j.getInt("height");
            if (Math.abs(desired-h) < diff) {
               index = i;
               diff = Math.abs(desired-h);
            }
         }
         // Now set according to selected height
         setImage(imageArray.getJSONObject(index).getString("imageUrl"));
      } catch (JSONException e) {
         log.error("ShowDetails setImage - " + e.getMessage());
      }
   }
   
   private void setImage(String urlString) {
      image.setGraphic(new ImageView(new Image(urlString)));
   }
}
