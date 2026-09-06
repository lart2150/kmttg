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
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;

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
   // Vertical rhythm: a tight gap between the lines of one block, a wider one
   // between blocks. Both are applied as a border under the field, so a field
   // with nothing in it contributes no gap at all.
   private static final int GAP_TIGHT = 3;
   private static final int GAP_BLOCK = 10;

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
   // Bumped per show so a slow artwork fetch cannot land on a later one
   private int imageRequest = 0;
   // One worker, so arrowing down a table queues lookups instead of opening a
   // connection per row. Superseded ones drop out before they connect.
   private final ExecutorService imageFetcher =
      Executors.newSingleThreadExecutor(new ThreadFactory() {
         @Override public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "ShowDetails-artwork");
            t.setDaemon(true);
            return t;
         }
      });

   public ShowDetails(JFrame frame, JSONObject json) {
      create(frame);
   }

   private void create(JFrame frame) {
      if (dialog == null) {
         JPanel root = buildContent();
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
         dialog.getContentPane().add(root);
      }
   }

   // Builds the content pane and the labels it holds. Package private so the
   // layout can be rendered and measured without opening a dialog.
   JPanel buildContent() {
      int minWidth = 400;
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
      left_panel.setAlignmentY(JPanel.TOP_ALIGNMENT);
      // BoxLayout centres anything narrower than the column, which would
      // stagger the short lines (time, channel) against the wrapped ones
      for (JLabel field : new JLabel[]
            {mainTitle, subTitle, time, channel, description, otherInfo, actorInfo}) {
         field.setAlignmentX(JLabel.LEFT_ALIGNMENT);
         left_panel.add(field);
      }

      JPanel right_panel = new JPanel();
      right_panel.setLayout(new BoxLayout(right_panel, BoxLayout.Y_AXIS));
      right_panel.setAlignmentY(JPanel.TOP_ALIGNMENT);
      right_panel.add(image);

      // BoxLayout, not FlowLayout: FlowLayout centres each component in the
      // row whatever alignmentY says, which floats the artwork half way down
      // beside the text instead of starting it level with the title.
      JPanel main_panel = new JPanel();
      main_panel.setLayout(new BoxLayout(main_panel, BoxLayout.X_AXIS));
      main_panel.add(left_panel);
      main_panel.add(right_panel);

      JPanel root = new JPanel(new BorderLayout());
      // Breathing room between the text and the window frame
      root.setBorder(BorderFactory.createEmptyBorder(GAP_BLOCK, GAP_BLOCK, GAP_BLOCK, GAP_BLOCK));
      root.add(main_panel, BorderLayout.CENTER);
      return root;
   }

   // Build a fixed-width wrapping label (was JavaFX
   // setMinWidth/setMaxWidth/setWrapText). HTML enables Swing wrapping, and
   // the height has to be measured from the laid out html - not pinned with
   // setPreferredSize, which freezes it at whatever the label reported while
   // it was still empty, i.e. zero, and hides the text for good.
   // Package private and static so the sizing can be asserted without a frame
   static JLabel makeWrapLabel(final int width) {
      return new JLabel("") {
         private static final long serialVersionUID = 1L;
         @Override public Dimension getPreferredSize() {
            View view = (View)getClientProperty(BasicHTML.propertyKey);
            if (view == null) // plain (or empty) text sizes itself
               return super.getPreferredSize();
            // The gap under the field lives in the border, so it has to come
            // out of the width the html lays out in and back into the height
            Insets in = getInsets();
            view.setSize(width - in.left - in.right, 0);
            return new Dimension(width,
               (int)Math.ceil(view.getPreferredSpan(View.Y_AXIS)) + in.top + in.bottom);
         }
      };
   }

   // Set wrapping label text using html so the label wraps at its width. The
   // text is show data, so it is escaped - an unescaped '<' in a title would
   // be read as markup and swallow what follows.
   static void setWrapText(JLabel label, String text) {
      // No html when there is nothing to show, so an unused field stays at
      // zero height instead of leaving a hole where it would have been
      label.setText(text == null || text.isEmpty() ? "" : "<html>" + escapeHtml(text) + "</html>");
   }

   // The two plain (non wrapping) fields
   static void setPlainText(JLabel label, String text) {
      label.setText(text == null ? "" : text);
   }

   // Spacing is worked out once the fields are filled, because the gap that
   // separates two blocks has to sit under the last field of the block that
   // actually has text - otherwise a show with no subtitle gets only the tight
   // gap under its title and the heading runs into the air time.
   void applyFieldSpacing() {
      applyFieldSpacing(new JLabel[][] {
         {mainTitle, subTitle},
         {time, channel},
         {description},
         {otherInfo, actorInfo},
      });
   }

   // Split out from the fields so the rule can be asserted without a dialog
   static void applyFieldSpacing(JLabel[][] blocks) {
      // Which field ends the dialog depends on what the show actually has, not
      // on where it sits in the list: a row with no rating and no cast ends at
      // the description, and that field must not add a gap on top of the
      // window padding below it.
      JLabel lastFilled = null;
      for (JLabel[] block : blocks)
         for (JLabel field : block)
            if (! field.getText().isEmpty())
               lastFilled = field;

      for (JLabel[] block : blocks) {
         JLabel lastInBlock = null;
         for (JLabel field : block) {
            setGap(field, GAP_TIGHT);
            if (! field.getText().isEmpty())
               lastInBlock = field;
         }
         if (lastInBlock != null && lastInBlock != lastFilled)
            setGap(lastInBlock, GAP_BLOCK);
      }
      if (lastFilled != null)
         lastFilled.setBorder(null); // the window padding is the gap below it
   }

   // An empty field carries no gap at all, so it takes up no space
   private static void setGap(JLabel label, int gapBelow) {
      label.setBorder(label.getText().isEmpty() ? null
         : BorderFactory.createEmptyBorder(0, 0, gapBelow, 0));
   }

   private static String escapeHtml(String text) {
      return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
   }

   // The gap between the text and the artwork belongs to the artwork, so a
   // show with no image does not leave a strip of empty space beside the text.
   private void setShowImage(ImageIcon icon) {
      image.setIcon(icon);
      image.setBorder(icon == null ? null
         : BorderFactory.createEmptyBorder(0, GAP_BLOCK, 0, 0));
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
                     // Every exit below is a return, so the socket is only
                     // released if the disconnect is in a finally
                     try {
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
                     } finally {
                        r.disconnect();
                     }
                  } else {
                     return;
                  }
               } // json levelOfDetail

               if (json.has("idSetSource") && json.getJSONObject("idSetSource").has("collectionId")) {
                  // For SP table
                  Remote r = config.initRemote(tivoName);
                  if (r.success) {
                     try {
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
                     } finally {
                        r.disconnect();
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
                     setPlainText(channel, chan);

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
                     setPlainText(time, t);

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
                           if (a.optString("role", "").equals("actor")) {
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
                           if (a.optString("role", "").equals("host") && a.has("first")) {
                              if (a.getString("first").equals("container"))
                                 pyTivo = true;
                              if (a.has("last") && a.getString("last").contains("TRANSCODE"))
                                 pyTivo = true;
                           }
                        }
                        if (!pyTivo) {
                           for (int i=0; i<credit.length(); ++i) {
                              JSONObject a = credit.getJSONObject(i);
                              if (a.optString("role", "").equals("host")) {
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

                     // Right panel image. Artwork already fetched this session
                     // goes in now - it is a map read, and setting it after
                     // the pack would resize the window under the user.
                     setShowImage(cachedImage(json));
                  } catch (JSONException e) {
                     log.error("ShowDetails update - " + e.getMessage());
                     return;
                  }
                  applyFieldSpacing(); // now that we know which fields are set
                  dialog.pack();
                  if (x != -1 && ! dialog.isShowing()) {
                     dialog.setLocation(x, y);
                  }
                  dialog.setVisible(true);
                  if (image.getIcon() == null) {
                     // Only once the window is up, so the text is not held
                     // back by an rpc connect and an image download
                     loadImageInBackground(tivoName, json);
                  } else {
                     ++imageRequest; // nothing in flight should overwrite it
                  }
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

   // Fetches the artwork on its own thread and adds it to the dialog whenever
   // it arrives, so the window opens on the text instead of waiting on the
   // network. The lookup and the fetch both used to run on the EDT, which
   // froze the ui and then delivered the icon after the dialog had already
   // been packed without it - and the dialog is not resizable, so there was
   // no room for it and it never appeared.
   private void loadImageInBackground(final String tivoName, final JSONObject json) {
      // The dialog is reused for whatever row is asked for next, so a fetch
      // that finishes late must not stamp its image onto a different show
      final int request = ++imageRequest;
      imageFetcher.execute(new Runnable() {
         @Override public void run() {
            if (request != imageRequest)
               return; // superseded before we got started - don't even connect
            final ImageIcon icon = resolveImage(tivoName, json);
            if (icon == null)
               return;
            SwingUtil.runLater(new Runnable() {
               @Override public void run() {
                  if (request != imageRequest)
                     return; // a newer show is on screen
                  setShowImage(icon);
                  dialog.pack(); // make room for what just arrived
               }
            });
         }
      });
   }

   /** Artwork already in hand for this row, without touching the network. */
   private ImageIcon cachedImage(JSONObject json) {
      try {
         if (json.has("image"))
            return ShowImageCache.getIcon(pickImageUrl(json.getJSONArray("image")));
      } catch (JSONException e) {
         log.error("ShowDetails cachedImage - " + e.getMessage());
      }
      return ShowImageCache.getIcon(ShowImageCache.getUrl(imageKey(json)));
   }

   private ImageIcon resolveImage(String tivoName, JSONObject json) {
      try {
         if (json.has("image")) // already in hand, no lookup needed
            return loadImage(pickImageUrl(json.getJSONArray("image")));
      } catch (JSONException e) {
         log.error("ShowDetails resolveImage - " + e.getMessage());
      }
      return searchImage(tivoName, json);
   }

   // What the artwork lookup is cached under. The collection comes first
   // because the artwork belongs to the series, not the episode - the urls
   // are .../images-production/collection/<collectionId>/..., and every
   // episode of a series answers with the same set. contentId is the fallback
   // for a row that carries no collection.
   static String imageKey(JSONObject json) {
      try {
         if (json.has("collectionId"))
            return json.getString("collectionId");
         if (json.has("contentId"))
            return json.getString("contentId");
      } catch (JSONException e) {
         log.error("ShowDetails imageKey - " + e.getMessage());
      }
      return null;
   }

   // Use contentId or collectionId to find an image for the given sourceJson
   private ImageIcon searchImage(String tivoName, JSONObject sourceJson) {
      String key = imageKey(sourceJson);
      String url = ShowImageCache.getUrl(key);
      if (url != null) // another episode of this series already looked it up
         return loadImage(url);

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
                     url = pickImageUrl(content.getJSONArray("image"));
                  }
               }
            }
            else if (sourceJson.has("collectionId")) {
               json.put("collectionId", sourceJson.getString("collectionId"));
               JSONObject result = r.Command("collectionSearch", json);
               if (result != null && result.has("collection")) {
                  JSONObject collection = result.getJSONArray("collection").getJSONObject(0);
                  if (collection.has("image")) {
                     url = pickImageUrl(collection.getJSONArray("image"));
                  }
               }
            }
         } catch (JSONException e) {
            log.error("ShowDetails searchImage - " + e.getMessage());
         } finally {
            r.disconnect();
         }
      }
      // Only a real answer is cached. Recording a miss would also record a
      // TiVo that was asleep, and the series would stay pictureless for the
      // rest of the session even once it came back.
      ShowImageCache.putUrl(key, url);
      return loadImage(url);
   }

   // The url closest to the height the dialog wants to show
   private static String pickImageUrl(JSONArray imageArray) {
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
         // Now pick according to selected height
         return imageArray.getJSONObject(index).getString("imageUrl");
      } catch (JSONException e) {
         log.error("ShowDetails pickImageUrl - " + e.getMessage());
         return null;
      }
   }

   private ImageIcon loadImage(final String urlString) {
      if (urlString == null)
         return null;
      ImageIcon cached = ShowImageCache.getIcon(urlString);
      if (cached != null) // same artwork, already downloaded and decoded
         return cached;
      try {
         Image img = ImageIO.read(new URL(urlString));
         if (img == null)
            return null;
         ImageIcon icon = new ImageIcon(img);
         ShowImageCache.putIcon(urlString, icon);
         return icon;
      } catch (Exception e) {
         log.error("ShowDetails loadImage - " + e.getMessage());
         return null;
      }
   }
}
