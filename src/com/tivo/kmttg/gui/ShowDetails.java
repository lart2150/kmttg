package com.tivo.kmttg.gui;

import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.util.log;

public class ShowDetails {
   private JDialog dialog = null;
   private JLabel mainTitle = null;
   private JLabel subTitle = null;
   private JLabel time = null;
   private JLabel channel = null;
   private JTextArea description = null;
   private JTextArea otherInfo = null;
   private JTextArea actorInfo = null;
   private JLabel image = null;
   private Color backgroundColor = new Color(16,0,76); // dark blue
   private Color actorColor = new Color(0,233,255); // light blue
   private Color titleColor = Color.yellow;
   private Color textColor = Color.white;
   private Color otherColor = new Color(204,204,204); // light grey
   private int textRowLimit = 40;
   
   ShowDetails(JFrame frame, JSONObject json) {
      create(frame);
   }
   
   private void create(JFrame frame) {
      if (dialog == null) {
         mainTitle = new JLabel("");
         mainTitle.setForeground(Color.yellow);
         // Increase font size
         mainTitle.setFont(
            new Font(
               mainTitle.getFont().getFamily(),
               mainTitle.getFont().getStyle(),
               mainTitle.getFont().getSize()+5
            )
         );
         
         subTitle = new JLabel("");
         subTitle.setForeground(titleColor);
         
         time = new JLabel("");
         time.setForeground(otherColor);
         
         channel = new JLabel("");
         channel.setForeground(otherColor);
         
         description = new JTextArea();
         description.setBackground(backgroundColor);
         description.setForeground(textColor);
         description.setColumns(textRowLimit);
         description.setLineWrap(true);
         description.setWrapStyleWord(true);
         
         otherInfo = new JTextArea();
         otherInfo.setBackground(backgroundColor);
         otherInfo.setForeground(otherColor);
         otherInfo.setColumns(textRowLimit);
         otherInfo.setLineWrap(true);
         otherInfo.setWrapStyleWord(true);
         
         actorInfo = new JTextArea();
         actorInfo.setBackground(backgroundColor);
         actorInfo.setForeground(actorColor);
         actorInfo.setColumns(textRowLimit);
         actorInfo.setLineWrap(true);
         actorInfo.setWrapStyleWord(true);
         
         image = new JLabel("");
         image.setForeground(titleColor);
         
         // Start of layout management
         GridBagConstraints c = new GridBagConstraints();
         int gy = 0;
         c.insets = new Insets(0, 2, 0, 2);
         c.ipady = 0;
         c.weighty = 0.0;  // default to no vertical stretch
         c.weightx = 0.0;  // default to no horizontal stretch
         c.gridx = 0;
         c.gridy = gy;
         c.gridwidth = 1;
         c.gridheight = 1;
         c.anchor = GridBagConstraints.WEST;
         c.fill = GridBagConstraints.HORIZONTAL;
         
         JPanel left_panel = new JPanel(new GridBagLayout());         
         JPanel right_panel = new JPanel(new GridBagLayout());         
         JPanel main_panel = new JPanel(new GridBagLayout());         
         
         left_panel.add(mainTitle, c);
         
         gy++;
         c.gridx = 0;
         c.gridy = gy;
         left_panel.add(subTitle, c);
         
         gy++;
         c.gridx = 0;
         c.gridy = gy;
         left_panel.add(time, c);
         
         gy++;
         c.gridx = 0;
         c.gridy = gy;
         left_panel.add(channel, c);
         
         gy++;
         c.gridx = 0;
         c.gridy = gy;
         left_panel.add(description, c);
         
         gy++;
         c.gridx = 0;
         c.gridy = gy;
         left_panel.add(otherInfo, c);
         
         gy++;
         c.gridx = 0;
         c.gridy = gy;
         left_panel.add(actorInfo, c);
         
         gy = 0;
         c.gridx = 0;
         c.gridy = gy;
         right_panel.add(image, c);
         
         left_panel.setBackground(backgroundColor);
         right_panel.setBackground(backgroundColor);
         main_panel.setBackground(backgroundColor);
         
         main_panel.add(left_panel, c);
         c.gridx = 1;
         main_panel.add(right_panel, c);
         
         dialog = new JDialog(frame, false); // non-modal dialog
         // Avoid stealing focus away from tables
         dialog.setFocusableWindowState(false);
         dialog.setFocusable(false);
         dialog.setTitle("Show information");
         dialog.setContentPane(main_panel);
         dialog.setLocationRelativeTo(frame.getJMenuBar().getComponent(0));
      }
   }
   
   public void update(String tivoName, String recordingId) {
      if ( ! config.rpcEnabled(tivoName) )
         return;
      JSONObject json = new JSONObject();
      try {
         json.put("levelOfDetail", "medium");
         json.put("recordingId", recordingId);
         update(tivoName, json);
      } catch (JSONException e) {
         log.error("ShowDetails update - " + e.getMessage());
      }
   }
   
   // Update dialog components with given JSON (runs as background task)
   public void update(final String tivoName, final JSONObject initialJson) {
      if ( ! config.rpcEnabled(tivoName) )
         return;
      if (initialJson == null)
         return;
      class backgroundRun extends SwingWorker<Object, Object> {
         protected Object doInBackground() {
            try {
               JSONObject json = initialJson;
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
                     else if (json.has("offerId")) {
                        j.put("offerId", json.getString("offerId"));
                        result = r.Command("offerSearch", j);
                        if (result == null)
                           return null;
                        if (result.has("offer"))
                           json = result.getJSONArray("offer").getJSONObject(0);
                        else {
                           if (! json.has("title"))
                              return null;
                        }
                     }
                  } else {
                     return null;
                  }
               }
               
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
                  JSONArray credit = json.getJSONArray("credit");
                  // actors
                  for (int i=0; i<credit.length(); ++i) {
                     JSONObject a = credit.getJSONObject(i);
                     if (i>0) separator = ", ";
                     if (a.getString("role").equals("actor")) {
                        if (a.has("first") && a.has("last"))
                           actors += separator + a.getString("first") + " " + a.getString("last");
                     }
                  }
                  // hosts
                  Boolean pyTivo = false;
                  for (int i=0; i<credit.length(); ++i) {
                     JSONObject a = credit.getJSONObject(i);
                     if (a.getString("role").equals("host") && a.has("first")) {
                        if (a.getString("first").equals("container"))
                           pyTivo = true;
                     }
                  }
                  if (!pyTivo) {
                     for (int i=0; i<credit.length(); ++i) {
                        JSONObject a = credit.getJSONObject(i);
                        if (a.getString("role").equals("host")) {
                           if (a.has("first") && a.has("last"))
                              actors += separator + a.getString("first") + " " + a.getString("last");
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
                  image.setText("");
                  image.setIcon(null);
               }
               //log.print(json.toString(3));
            } catch (JSONException e) {
               log.error("ShowDetails update - " + e.getMessage());
            }
            dialog.invalidate();
            dialog.pack();
            display(true);
            return null;
         }
      }
      backgroundRun b = new backgroundRun();
      b.execute();
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
   
   public void display(Boolean show) {
      dialog.setVisible(show);
   }
   
   public Boolean isShowing() {
      return dialog.isShowing();
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
      try {
         image.setIcon(new ImageIcon(new URL(urlString)));
      } catch (MalformedURLException e) {
         log.error("ShowDetails importImage - " + e.getMessage());
      }
   }
}
