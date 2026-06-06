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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONConverter;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.swing.SwingUtil;
import com.tivo.kmttg.gui.swing.TreeTable;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.util.log;

public class ShowDetails {
   private JDialog dialog = null;
   private JLabel mainTitle = null;
   private JLabel subTitle = null;
   private JLabel time = null;
   private JLabel channel = null;
   private JLabel description = null;
   private JLabel otherInfo = null;
   private JLabel actorInfo = null;
   private JLabel image = null;
   private int x=-1, y=-1;

   public ShowDetails(JFrame frame, JSONObject json) {
      create(frame);
   }

   private void create(JFrame frame) {
      int minWidth = 400;
      if (dialog == null) {
         mainTitle = makeWrapLabel(minWidth);
         // Increase font size
         mainTitle.setFont(
            mainTitle.getFont().deriveFont(
               mainTitle.getFont().getSize2D()+5
            )
         );

         subTitle = makeWrapLabel(minWidth);

         time = new JLabel("");

         channel = new JLabel("");

         description = makeWrapLabel(minWidth);

         otherInfo = makeWrapLabel(minWidth);

         actorInfo = makeWrapLabel(minWidth);

         image = new JLabel("");

         // Start of layout management
         JPanel left_panel = new JPanel();
         left_panel.setLayout(new BoxLayout(left_panel, BoxLayout.Y_AXIS));
         left_panel.add(mainTitle);
         left_panel.add(subTitle);
         left_panel.add(time);
         left_panel.add(channel);
         left_panel.add(description);
         left_panel.add(otherInfo);
         left_panel.add(actorInfo);

         JPanel right_panel = new JPanel();
         right_panel.setLayout(new BoxLayout(right_panel, BoxLayout.Y_AXIS));
         right_panel.add(image);

         JPanel main_panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
         main_panel.add(left_panel);
         main_panel.add(right_panel);

         dialog = new JDialog(frame);
         dialog.setResizable(false);
         dialog.setTitle("Show information");
         SwingUtil.loadIcons(dialog);
         // This so we can restore original dialog position when re-opened
         dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent arg0) {
               x = dialog.getX(); y = dialog.getY();
            }
         });
         JPanel root = new JPanel(new BorderLayout());
         root.add(main_panel, BorderLayout.CENTER);
         dialog.getContentPane().add(root);
      }
   }

   // Build a fixed-width wrapping label (was JavaFX
   // setMinWidth/setMaxWidth/setWrapText). HTML body enables Swing wrapping.
   private JLabel makeWrapLabel(int width) {
      JLabel label = new JLabel("");
      label.setPreferredSize(new Dimension(width, label.getPreferredSize().height));
      return label;
   }

   // Set wrapping label text using html so the label wraps at its width
   private void setWrapText(JLabel label, String text) {
      label.setText("<html><body style='width: 395px'>" + text + "</body></html>");
   }

   public void update(JTable node, String tivoName, String recordingId) {
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

   public void update(TreeTable<?> node, String tivoName, String recordingId) {
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
   public void update(final JTable node, final String tivoName, final JSONObject initialJson) {
      updateImpl(node, null, tivoName, initialJson);
   }

   // Update dialog components with given JSON (runs as background task)
   public void update(final TreeTable<?> node, final String tivoName, final JSONObject initialJson) {
      updateImpl(null, node, tivoName, initialJson);
   }

   // Common background-update implementation. The node argument (JTable or
   // TreeTable) is only used to restore keyboard focus after the dialog shows.
   private void updateImpl(final JTable jtableNode, final TreeTable<?> treeNode, final String tivoName, final JSONObject initialJson) {
      if ( ! config.rpcEnabled(tivoName) )
         return;
      if (initialJson == null)
         return;
      Runnable task = new Runnable() {
         @Override public void run() {
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
                           return;
                        if (result.has("recording"))
                           json = result.getJSONArray("recording").getJSONObject(0);
                        else {
                           if (! json.has("title"))
                              return;
                        }
                     }
                     else if (json.has("contentId")) {
                        j.put("contentId", json.getString("contentId"));
                        result = r.Command("contentSearch", j);
                        if (result == null)
                           return;
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
                              return;
                        }
                     }
                  } else {
                     return;
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
                        return;
                     if (result.has("collection")) {
                        json = result.getJSONArray("collection").getJSONObject(0);
                     }
                     else {
                        if (! json.has("title"))
                           return;
                     }
                  }
               }

               //log.print(json.toString(3));
            } catch (JSONException e) {
               log.error("ShowDetails update - " + e.getMessage());
               return;
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
                     setWrapText(mainTitle, title);

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
                     setWrapText(subTitle, subtitle);

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
                        t = JSONConverter.printableTimeFromJSON(json);
                        if (json.has("duration")) {
                           long s = JSONConverter.getStartTime(json);
                           long e = JSONConverter.getEndTime(json);
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
                     setWrapText(description, desc);

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
                     setWrapText(otherInfo, other);

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
                     setWrapText(actorInfo, actors);

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
                  dialog.pack();
                  if (x != -1 && ! dialog.isShowing()) {
                     dialog.setLocation(x, y);
                  }
                  dialog.setVisible(true);
                  SwingUtil.runLater(new Runnable() {
                     @Override public void run() {
                        if (jtableNode != null)
                           jtableNode.requestFocus();
                        else if (treeNode != null)
                           treeNode.table.requestFocus();
                     }
                  });
               }
            }
            SwingUtil.runLater(new backgroundRun(json));
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
      image.setIcon(null);
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

   private void setImage(final String urlString) {
      try {
         Image img = ImageIO.read(new URL(urlString));
         final ImageIcon icon = (img == null) ? null : new ImageIcon(img);
         SwingUtil.runLater(new Runnable() {
            @Override public void run() {
               image.setIcon(icon);
            }
         });
      } catch (Exception e) {
         log.error("ShowDetails setImage - " + e.getMessage());
      }
   }
}
