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
package com.tivo.kmttg.gui.table;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Hashtable;
import java.util.Stack;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableColumn;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONConverter;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.PopupHandler;
import com.tivo.kmttg.gui.TableMap;
import com.tivo.kmttg.gui.help;
import com.tivo.kmttg.gui.remote.util;
import com.tivo.kmttg.gui.sortable.sortableDate;
import com.tivo.kmttg.gui.sortable.sortableDuration;
import com.tivo.kmttg.gui.swing.KmttgTableModel;
import com.tivo.kmttg.gui.swing.SwingUtil;
import com.tivo.kmttg.gui.swing.TreeTable;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.tivoFileName;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.rpc.id;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class TableUtil {
   private static JDialog searchDialog = null;
   private static JTextField searchField = null;
   private static JButton find = null;
   private static JDialog thumbsDialog = null;
   private static JComboBox<String> thumbsChoice = null;
   private static int search_x = -1;
   private static int search_y = -1;
   public static Color tableBkgndDarker = new Color(235,235,235); // light grey
   public static Color tableBkgndLight = Color.WHITE;
   public static Color tableBkgndProtected = new Color(191,156,94); // tan
   public static Color tableBkgndRecording = new Color(149, 151, 221); // light blue
   public static Color tableBkgndInHistory = new Color(250, 252, 164); // light yellow
   public static Color lightRed = new Color(250, 190, 190); // light red

   public static String getColumnName(JTable TABLE, int c) {
      return TABLE.getModel().getColumnName(c);
   }

   public static int getColumnIndex(JTable TABLE, String name) {
      String cname;
      for (int i=0; i<TABLE.getModel().getColumnCount(); i++) {
         cname = TABLE.getModel().getColumnName(i);
         if (cname.equals(name)) return i;
      }
      return -1;
   }

   public static int getColumnIndex(TreeTable<?> TABLE, String name) {
      return getColumnIndex(TABLE.table, name);
   }

   public static int[] GetSelectedRows(JTable TABLE) {
      debug.print("");
      return TABLE.getSelectedRows();
   }

   public static int[] GetSelectedRows(TreeTable<?> TABLE) {
      debug.print("");
      return TABLE.table.getSelectedRows();
   }

   // Toggle between fully expanded and fully collapsed tree states
   public static <T> void toggleTreeState(TreeTable<T> TABLE) {
      Boolean fullyExpanded = true;
      for (TreeTable.TreeItem<T> item : TABLE.getRoot().getChildren()) {
         if (item.getChildren().size() > 0 && ! item.isExpanded())
            fullyExpanded = false;
      }
      for (TreeTable.TreeItem<T> item : TABLE.getRoot().getChildren()) {
         item.setExpanded(! fullyExpanded);
      }
   }

   public static Integer[] highToLow(int[] unsorted) {
      Integer[] sorted = new Integer[unsorted.length];
      int i=0;
      for (int selected : unsorted)
         sorted[i++] = selected;
      Arrays.sort(sorted, Collections.reverseOrder());
      return sorted;
   }

   // Make any selected TABLE row visible in viewport
   public static void selectedVisible(JTable TABLE) {
      Integer[] selected = highToLow(GetSelectedRows(TABLE));
      if (selected != null && selected.length > 0)
         scrollToCenter(TABLE, selected[0]);
   }

   // Make any selected TABLE row visible in viewport
   public static void selectedVisible(TreeTable<?> TABLE) {
      Integer[] selected = highToLow(GetSelectedRows(TABLE));
      if (selected != null && selected.length > 0)
         scrollToCenter(TABLE, selected[0]);
   }

   public static void DeselectRow(JTable TABLE, int row) {
      TABLE.removeRowSelectionInterval(row, row);
   }

   public static void clear(JTable TABLE) {
      debug.print("");
      ((KmttgTableModel<?>)TABLE.getModel()).clear();
   }

   public static void RemoveRow(JTable table, int row) {
      ((KmttgTableModel<?>)table.getModel()).removeRow(row);
   }

   public static void scrollToCenter(final JTable table, int rowIndex) {
      Rectangle rect = table.getCellRect(rowIndex, 0, true);
      table.scrollRectToVisible(rect);
   }

   public static void scrollToCenter(final TreeTable<?> table, int rowIndex) {
      table.scrollToCenter(rowIndex);
   }

   public static void setWeights(JTable TABLE, String[] names, double[] weights, Boolean force) {
      if (!force && config.tableColAutoSize == 0)
         return;
      setWeightsImpl(TABLE, names, weights);
   }

   public static void setWeights(TreeTable<?> TABLE, String[] names, double[] weights, Boolean force) {
      if (!force && config.tableColAutoSize == 0)
         return;
      setWeightsImpl(TABLE.table, names, weights);
   }

   // Distribute column widths according to relative weights. With
   // AUTO_RESIZE_ALL_COLUMNS JTable preserves relative proportions when the
   // table itself is resized.
   private static void setWeightsImpl(JTable TABLE, String[] names, double[] weights) {
      TABLE.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
      int c = 0;
      double sum = 0;
      Hashtable<String,Double> h = new Hashtable<String,Double>();
      for (double weight : weights) {
         String name = names[c++];
         if (name.length() == 0)
            name = "";
         h.put(name, weight);
         sum += weight;
      }
      for (int i=0; i<TABLE.getColumnModel().getColumnCount(); i++) {
         TableColumn col = TABLE.getColumnModel().getColumn(i);
         String cname = TABLE.getModel().getColumnName(col.getModelIndex());
         Double weight = h.get(cname);
         if (weight != null)
            col.setPreferredWidth((int)(1000 * weight / sum));
      }
   }

   // Historical no-op carried over from JavaFX implementation (built-in
   // column resize policies handle this)
   public static void autoSizeTableViewColumns(final JTable tableView, Boolean force) {
      debug.print("tableView=" + tableView + " force=" + force);
   }

   public static void autoSizeTableViewColumns(final TreeTable<?> tableView, Boolean force) {
      debug.print("tableView=" + tableView + " force=" + force);
   }

   // Bring up a dialog to allow searching SHOW column of given table
   public static void SearchGUI() {
      if (searchDialog == null) {
         // Dialog not created yet, so do so
         JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
         find = new JButton("FIND");
         find.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               String text = searchField.getText();
               if (text.length() > 0) {
                  // Issue warning and return for irrelevant tabs/tables
                  String irrelevant = "Currently selected tab doesn't contain a suitable table to search.";
                  String tabName = config.gui.getCurrentTabName();
                  if (tabName.equals("FILES") || tabName.equals("Slingbox")) {
                     log.warn(irrelevant);
                     return;
                  }
                  if (tabName.equals("Remote")) {
                     TableMap tmap = TableMap.getCurrent();
                     String colName = "SHOW";
                     if (config.gui.remote_gui.getCurrentTabName().equals("Streaming"))
                        colName = "ITEM";
                     if (tmap != null) {
                        if (tmap.getTable() != null)
                           Search(tmap.getTable(), text, colName);
                        if (tmap.getTreeTable() != null)
                           Search(tmap.getTreeTable(), text, colName);
                     }
                  } else {
                     Search(config.gui.getTab(tabName).getTable().NowPlaying, text, "SHOW");
                  }
               }
            }
         });
         searchField = new JTextField(15);
         searchField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               find.doClick();
            }
         });
         JButton close = new JButton("CLOSE");
         close.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               search_x = searchDialog.getX(); search_y = searchDialog.getY();
               searchDialog.setVisible(false);
            }
         });
         panel.add(find);
         panel.add(searchField);
         panel.add(close);
         searchDialog = new JDialog(config.gui.getFrame()); // Non modal
         SwingUtil.loadIcons(searchDialog);
         searchDialog.setTitle("Search Table");
         searchDialog.getContentPane().add(panel);
         searchDialog.pack();
         searchDialog.setLocationRelativeTo(config.gui.getFrame());
      }

      // Dialog already created, so display it and highlight any existing search text
      if (search_x != -1)
         searchDialog.setLocation(search_x, search_y);
      searchDialog.setVisible(true);
      searchField.requestFocus();
      searchField.selectAll();
   }

   // Perform a search in given TABLE column name for searchString
   private static void Search(JTable TABLE, String searchString, String colName) {
      int lastRow = TABLE.getRowCount()-1;
      int startRow = TABLE.getSelectionModel().getLeadSelectionIndex();
      if (startRow < 0)
         startRow = 0;
      startRow += 1;
      if (startRow > lastRow)
         startRow = 0;
      TABLE.clearSelection(); // Clear selection
      Boolean result = searchMatch(TABLE, colName, searchString, startRow, lastRow);
      if (!result && startRow > 0) {
         searchMatch(TABLE, colName, searchString, 0, startRow);
      }
   }

   public static Boolean searchMatch(JTable TABLE, String colName, String searchString, int start, int stop) {
      String v;
      KmttgTableModel<?> model = (KmttgTableModel<?>)TABLE.getModel();
      for (int row=start; row<=stop; row++) {
         Object o = model.getRow(row);
         v = o == null ? null : o.toString();
         if ( v == null ) {
            log.error("searchMatch: Unimplemented SHOW type found");
         } else {
            v = v.toLowerCase();
            if (v.matches("^.*" + searchString.toLowerCase() + ".*$")) {
               // scroll to and set selection to given row
               scrollToCenter(TABLE, row);
               TABLE.addRowSelectionInterval(row, row);
               TABLE.requestFocus();
               return true;
            }
         }
      }
      return false;
   }

   // Perform a search in given TABLE column name for searchString
   private static void Search(TreeTable<?> TABLE, String searchString, String colName) {
      int lastRow = TABLE.getExpandedItemCount()-1;
      int startRow = TABLE.table.getSelectionModel().getLeadSelectionIndex();
      if (startRow < 0)
         startRow = 0;
      startRow += 1;
      if (startRow > lastRow)
         startRow = 0;
      TABLE.clearSelection(); // Clear selection
      Boolean result = searchMatch(TABLE, colName, searchString, startRow, lastRow);
      if (!result && startRow > 0) {
         searchMatch(TABLE, colName, searchString, 0, startRow);
      }
   }

   public static Boolean searchMatch(final TreeTable<?> TABLE, String colName, String searchString, int start, int stop) {
      String v;
      for (int row=start; row<=stop; row++) {
         Object o = TABLE.getTreeItem(row).getValue();
         v = o == null ? null : o.toString();
         if ( v == null ) {
            log.error("searchMatch: Unimplemented SHOW type found");
         } else {
            v = v.toLowerCase();
            if (v.matches("^.*" + searchString.toLowerCase() + ".*$")) {
               // scroll to and set selection to given row
               scrollToCenter(TABLE, row);
               TABLE.select(row);
               TABLE.table.requestFocus();
               return true;
            }
         }
      }
      return false;
   }

   // Bring up set thumbs dialog
   public static void ThumbsGUI() {
      final String tivoName = config.gui.getCurrentRemoteTivoName();
      if (tivoName == null) {
         log.error("Setting thumbs is only valid from Remote tables");
         return;
      }
      final JSONObject json = config.gui.getCurrentRemoteJson();
      if (json == null)
         return;
      if (thumbsDialog == null) {
         // Dialog not created yet, so do so
         JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
         JLabel rating = new JLabel("Thumbs Rating: ");
         thumbsChoice = new JComboBox<String>();
         for (int i=-3; i<=3; ++i)
            thumbsChoice.addItem("" + i);
         JButton setButton = new JButton("SET");
         setButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               Runnable task = new Runnable() {
                  @Override public void run() {
                     // Determine tivoName and json of currently selected table
                     String tivoName = config.gui.getCurrentRemoteTivoName();
                     if (tivoName == null)
                        return;
                     JSONObject json = config.gui.getCurrentRemoteJson();
                     if (json == null)
                        return;
                     String setting = "" + thumbsChoice.getSelectedItem();
                     int thumbsRating = Integer.parseInt(setting);
                     Remote r = config.initRemote(tivoName);
                     if (r.success) {
                        Boolean result = r.setThumbsRating(json, thumbsRating, true);
                        r.disconnect();
                        String title = "";
                        try {
                           if (json.has("title"))
                              title = json.getString("title");
                        } catch (JSONException e) {
                           log.error("ThumbsGUI SET - " + e.getMessage());
                        }
                        if (result) {
                           log.warn("Successfully set thumbs rating for '" + title + "' to: " + thumbsRating);
                        }
                        else
                           log.error("Failed to set thumbs rating for '" + title + "'");
                     }
                  }
               };
               if (thumbsDialog != null) {
                  SwingUtil.runLater(new Runnable() {
                     @Override public void run() {
                        thumbsDialog.setVisible(false);
                     }
                  });
               }
               new Thread(task).start();
            }
         });
         row1.add(setButton);
         row1.add(rating);
         row1.add(thumbsChoice);
         thumbsDialog = new JDialog(config.gui.getFrame());
         SwingUtil.loadIcons(thumbsDialog);
         thumbsDialog.getContentPane().add(row1);
         thumbsDialog.setTitle("Thumbs Rating");
         thumbsDialog.pack();
         thumbsDialog.setLocationRelativeTo(config.gui.getFrame());
         thumbsDialog.setVisible(true);
      }

      // Set default rating
      if (json != null) {
         Runnable task = new Runnable() {
            @Override public void run() {
               int rating = 0;
               Remote r = config.initRemote(tivoName);
               if (r.success) {
                  rating = r.getThumbsRating(json);
                  r.disconnect();
               }
               final int rating_final = rating;
               SwingUtil.runLater(new Runnable() {
                  @Override public void run() {
                     thumbsChoice.setSelectedItem("" + rating_final);
                  }
               });
            }
         };
         new Thread(task).start();
      }

      // Set title
      String title = "Set thumbs: ";
      try {
         if (json != null && json.has("title"))
            title += json.getString("title");
      } catch (JSONException e) {
         log.error("ThumbsGUI - " + e.getMessage());
      }
      thumbsDialog.setTitle(title);

      // Display dialog and set default thumbs rating
      thumbsDialog.setVisible(true);
   }

   // Add right mouse button listener
   public static void AddRightMouseListener(final JTable TABLE) {
      TABLE.addMouseListener(new MouseAdapter() {
         @Override
         public void mousePressed(MouseEvent event) {
            PopupHandler.hide();
            if (event.isPopupTrigger())
               PopupHandler.display(TABLE, event);
         }
         @Override
         public void mouseReleased(MouseEvent event) {
            if (event.isPopupTrigger())
               PopupHandler.display(TABLE, event);
         }
      });
   }

   // Add right mouse button listener
   public static void AddRightMouseListener(final TreeTable<?> TABLE) {
      TABLE.table.addMouseListener(new MouseAdapter() {
         @Override
         public void mousePressed(MouseEvent event) {
            PopupHandler.hide();
            if (event.isPopupTrigger())
               PopupHandler.display(TABLE, event);
         }
         @Override
         public void mouseReleased(MouseEvent event) {
            if (event.isPopupTrigger())
               PopupHandler.display(TABLE, event);
         }
      });
   }


   public static String getSortableDate(sortableDate s) {
      SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmm");
      long gmt = Long.parseLong(s.sortable);
      return sdf.format(gmt);
   }

   public static String currentYearMonthDay() {
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy_MM_dd");
      return sdf.format(new Date().getTime());
   }

   private static String makeDate(JSONObject json) {
      if (json.has("startTime")) {
         SimpleDateFormat sdf = new SimpleDateFormat("E MM/dd/yy hh:mm a");
         long start = JSONConverter.getStartTime(json);
         return sdf.format(start);
      }
      return "";
   }

   public static String makeShowSummary(JSONObject json) {
      String date = makeDate(json);
      String channel = JSONConverter.makeChannelName(json);
      String title = JSONConverter.makeShowTitle(json);
      return date + " " + channel + " " + title;
   }

   // Used by TABLERowSelected callbacks for printing show info to message window
   public static String makeShowSummary(sortableDate s, sortableDuration dur) {
      try {
         JSONObject o;
         String channelNum = null;
         String channel = null;
         if (s.json.has("channel")) {
            o = s.json.getJSONObject("channel");
            if ( o.has("channelNumber") ) {
               channelNum = o.getString("channelNumber");
            }
            if ( o.has("callSign") ) {
               channel = o.getString("callSign");
            }
         }
         String description = null;
         if ( s.json.has("description") ) {
            description = s.json.getString("description");
         }
         String d = "";
         if (dur != null && dur.sortable != null && dur.sortable > 0) {
            d = rnpl.msecsToMins(dur.sortable);
         }
         String message = "";
         if (s.display != null && ! s.sortable.equals("0"))
            message = s.display;
         if (channelNum != null && channel != null) {
            message += " on " + channelNum + "=" + channel;
         }
         if (d.length() > 0)
            message += ", Duration = " + d;

         if (s.json.has("seasonNumber"))
            message += ", season " + s.json.get("seasonNumber");
         if (s.json.has("episodeNum"))
            message += " episode " + s.json.getJSONArray("episodeNum").get(0);
         if (s.json.has("originalAirdate"))
            message += ", originalAirdate: " + s.json.getString("originalAirdate");
         if (s.json.has("movieYear"))
            message += ", movieYear: " + s.json.get("movieYear");

         if (description != null) {
            message += "\n" + description;
         }
         return message;
      } catch (Exception e) {
         log.error("makeShowSummary - " + e.getMessage());
      }
      return "";
   }

   // Check if given json is a show scheduled to record on this TiVo
   private static Boolean isRecordingScheduled(JSONObject json) {
      try {
         if (json != null && json.has("state")) {
            if (json.getString("state").equals("scheduled") || json.getString("state").equals("inProgress"))
               return(true);
         }
      } catch (JSONException e) {
         log.error("isRecordingScheduled error - " + e.getMessage());
      }
      return(false);
   }

   // Main engine for single show scheduling. This can be a new show
   // or an existing show for which to modify recording options.
   private static Boolean recordSingle(final String tivoName, final JSONObject json) {
      try {
         if (json.has("partnerId") && ! json.has("channel")) {
            Boolean streaming = true;
            if (json.has("collectionType") && json.getString("collectionType").equals("webVideo"))
               streaming = false;
            if (streaming) {
               // Streaming only entry requires ContentLocatorStore
               if (json.has("collectionId")) {
                  return bookmarkIfPossible(tivoName, json);
               } else {
                  log.warn("Missing collectionId for streaming title");
                  return false;
               }
            }
         }
         String title = "UNTITLED";
         if (json.has("title"))
            title = json.getString("title");
         String message = "";
         if (json.has("contentId") && json.has("offerId")) {
            JSONObject existing = null;
            if ( isRecordingScheduled(json) )
               existing = json;
            message = "(" + tivoName + ") " + "Schedule Recording: ";
            if (existing != null) {
               message = "(" + tivoName + ") " + "Modify Recording: ";
            }
            message += "'" + title + "'";
            final JSONObject o = util.recordOpt.promptUser(
               message, existing
            );
            if (o != null) {
               Boolean anywhere = false;
               if (o.has("_anywhere_")) {
                  anywhere = true;
                  o.remove("_anywhere_");
               }
               o.put("contentId", json.getString("contentId"));
               o.put("offerId", json.getString("offerId"));
               final String _title = title;
               if (! anywhere) {
                  // Attempt to schedule on tivoName only
                  if (existing == null)
                     message = "Scheduled recording: '" + title + "' on Tivo: " + tivoName;
                  else
                     message = "Modified recording: '" + title + "' on Tivo: " + tivoName;
                  final String _message = message;
                  class backgroundRun implements Runnable {
                     JSONObject json;
                     public backgroundRun(JSONObject json) {
                        this.json = json;
                     }
                     @Override
                     public void run() {
                        Remote r = config.initRemote(tivoName);
                        if (r.success) {
                           JSONObject result = r.Command("Singlerecording", o);
                           if (result == null) {
                              log.error("Failed to schedule/modify recording for: '" + _title + "'");
                           } else {
                              String conflicts = rnpl.recordingConflicts(result, json);
                              if (conflicts == null) {
                                 log.warn(_message);
                                 // Set thumbs rating if it doesn't exist for this collection
                                 r.setThumbsRating(json, 1, false);
                              } else {
                                 log.error(conflicts);
                              }
                           }
                           r.disconnect();
                        }
                     }
                  }
                  backgroundRun b = new backgroundRun(json);
                  new Thread(b).start();
               } else {
                  if (existing == null) {
                     // Attempt to schedule using all RPC enabled TiVos
                     class backgroundRun implements Runnable {
                        JSONObject json;
                        public backgroundRun(JSONObject json) {
                           this.json = json;
                        }
                        @Override
                        public void run() {
                           Stack<String> tivo_stack = config.getTivoNames();
                           Stack<String> tivos = new Stack<String>();
                           tivos.add(tivoName); // Put original target tivo 1st in stack
                           // RPC only TiVos get priority
                           for (int i=0; i<tivo_stack.size(); ++i) {
                              if ( config.rpcEnabled(tivo_stack.get(i)) ) {
                                 if (tivos.search(tivo_stack.get(i)) == -1)
                                    tivos.add(tivo_stack.get(i));
                              }
                           }
                           // Series 3 TiVos are last resort (if tivo.com username & password are available)
                           if (config.mindEnabled(tivoName)) {
                              for (int i=0; i<tivo_stack.size(); ++i) {
                                 if (tivos.search(tivo_stack.get(i)) == -1)
                                    tivos.add(tivo_stack.get(i));
                              }
                           }
                           for (int i=0; i<tivos.size(); ++i) {
                              String name = tivos.get(i);
                              String message = "Scheduled recording: '" + _title + "' on Tivo: " + name;
                              Remote r = config.initRemote(name);
                              if (r.success) {
                                 JSONObject result = r.Command("Singlerecording", o);
                                 if (result == null) {
                                    log.error("Failed attempt to schedule recording on '" + name + "' for: '" + _title + "'");
                                 } else {
                                    String conflicts = rnpl.recordingConflicts(result, json);
                                    if (conflicts == null) {
                                       log.warn(message);
                                       // Set thumbs rating if it doesn't exist for this collection
                                       r.setThumbsRating(json, 1, false);
                                       return;
                                    } else {
                                       log.warn("Cannot schedule '" + _title + "' on '" + name + "' due to conflicts");
                                    }
                                 }
                                 r.disconnect();
                              }
                           }
                        }
                     }
                     backgroundRun b = new backgroundRun(json);
                     new Thread(b).start();
                  }
               }
            }
         } else {
            // This is likely unavailable content
            if (json.has("collectionType") && json.getString("collectionType").equals("series")) {
               // for type series bring up SP form
               Remote r = config.initRemote(tivoName);
               if (r.success) {
                  JSONArray existing = r.SeasonPasses(null);
                  if (existing != null) {
                     r.SPschedule(tivoName, json, existing);
                  }
                  r.disconnect();
                  return(true);
               }
               return(false);
            }

            if (json.has("collectionId")) {
               // Non-series type with collectionId may be possible to bookmark
               return bookmarkIfPossible(tivoName, json);
            }

            // Exhausted all possibilities so error out
            log.error("Missing contentId and/or offerId for: '" + title + "'");
            return(false);
         }
      } catch (JSONException e) {
         log.error("recordSingle failed - " + e.getMessage());
         return(false);
      }
      return(true);
   }

   private static Boolean bookmarkIfPossible(String tivoName, JSONObject json) {
      class backgroundRun implements Runnable {
         String tivoName;
         JSONObject json;
         public backgroundRun(String tivoName, JSONObject json) {
            this.tivoName = tivoName;
            this.json = json;
         }
         @Override
         public void run() {
            JSONObject result;
            Remote r = config.initRemote(tivoName);
            if (r.success) {
               try {
                  if (! json.has("contentId")) {
                     // If contentId is missing then look for one
                     JSONObject j = new JSONObject();
                     j.put("bodyId", r.bodyId_get());
                     j.put("collectionId", json.getString("collectionId"));
                     result = r.Command("contentSearch", j);
                     if (result != null && result.has("content")) {
                        JSONObject o = result.getJSONArray("content").getJSONObject(0);
                        if (o.has("contentId"))
                           json.put("contentId", o.getString("contentId"));
                     }
                  }
                  // Without contentId we can't proceed
                  if (! json.has("contentId")) {
                     log.error("Unable to determine/find contentId");
                     r.disconnect();
                     return;
                  }
                  // Have contentId and collectionId, so proceed with adding content locator
                  String title = "UNTITLED";
                  if (json.has("title"))
                     title = json.getString("title");
                  JSONObject j = new JSONObject();
                  j.put("contentId", json.getString("contentId"));
                  j.put("collectionId", json.getString("collectionId"));
                  result = r.Command("ContentLocatorStore", j);
                  if (result != null) {
                     log.warn("Added bookmark to My Shows: '" + title + "' on Tivo: " + tivoName);
                     // Set thumbs rating if it doesn't exist for this collection
                     r.setThumbsRating(json, 1, false);
                     r.disconnect();
                  } else {
                     log.error("Failed to create content locator for: " + title);
                     r.disconnect();
                  }
               } catch (JSONException e) {
                  log.error("bookmarkIfPossible - " + e.getMessage());
               }
            } // if r.success
         } // run
      } // class backgroundRun

      backgroundRun b = new backgroundRun(tivoName, json);
      new Thread(b).start();
      return true;
   }

   // Method used by various RPC tables for single item recording
   public static void recordSingleCB(final String tivoName, final JSONArray entries) {
      if (entries.length() > 0) {
         JSONObject json;
         for (int i=0; i<entries.length(); ++i) {
            try {
               json = entries.getJSONObject(i);
               if (json != null) {
                  if (recordSingle(tivoName, json) && ! isRecordingScheduled(json)) {
                     // Add to todo list for this tivo
                     util.addEntryToTodo(tivoName, json);
                     JSONConverter.addTivoNameFlagtoJson(json, "__inTodo__", tivoName);
                  }
               }
            } catch (JSONException e) {
               log.error("recordSingleCB error - " + e.getMessage());
            }
         }
      }
   }

   // Send url to web browser
   static public void webQuery(String title) {
      try {
         String url = config.web_query + URLEncoder.encode(title, "UTF-8");
         help.showInBrowser(url);
      } catch (UnsupportedEncodingException e) {
         log.error("webQuery - " + e.getMessage());
      }
   }


   static public void PrintEpisodes(JSONObject json) {
      if (json == null) return;
      String collectionId = null;
      String title = null;
      try {
         if (json.has("collectionId")) {
            collectionId = json.getString("collectionId");
         } else {
            if (json.has("idSetSource")) {
               JSONObject idSetSource = json.getJSONObject("idSetSource");
               if (idSetSource.has("collectionId"))
                  collectionId = idSetSource.getString("collectionId");
            }
         }
         if (json.has("title"))
            title = json.getString("title");
      } catch (JSONException e) {
         log.error("PrintEpisodes - " + e.getMessage());
      }
      if (collectionId == null) {
         log.warn("No collectionId available for this entry");
         return;
      }
      if (title == null)
         title = collectionId;
      PrintEpisodes(title, collectionId);
   }

   static public void PrintEpisodes(String title, String collectionId) {
      String tivoName = config.getFirstRpcEnabled();
      if (tivoName == null)
         return;
      log.warn(">> Collecting episode data for: " + title);
      Runnable task = new Runnable() {
         @Override public void run() {
            Remote r = new Remote(tivoName, true);
            if (r.success) {
               try {
                  JSONArray entries = r.getEpisodes(collectionId);
                  r.disconnect();
                  JSONArray episodes = new JSONArray();
                  for (int i=0; i<entries.length(); ++i) {
                     JSONObject entry = entries.getJSONObject(i);
                     if (entry.has("description")) {
                        episodes.put(entry);
                     }
                  }
                  if (episodes.length() > 0) {
                     SwingUtil.runLater(new Runnable() {
                        @Override public void run() {
                           PrintEpisodes_GUI(title, episodes);
                        }
                     });
                  } else {
                     log.warn("No episodes found for: " + title);
                  }
               } catch (JSONException e) {
                  log.error("searchTable GetEpisodes - " + e.getMessage());
               }
            }
         }
      };
      new Thread(task).start();
   }

   static public void PrintEpisodes_GUI(String title, JSONArray episodes) {
      String[] choices = {
         "Output to table",
         "Output CSV File",
         "Output to table and CSV File"
      };

      Object result = JOptionPane.showInputDialog(
         config.gui.getFrame(),
         "Episode Output for: " + title + "\nChoose output:",
         "Choose Output",
         JOptionPane.QUESTION_MESSAGE,
         null, choices, choices[0]
      );
      if (result != null) {
         switch ((String)result) {
            case "Output to table and CSV File":
               PrintEpisodes_table(episodes);
               PrintEpisodes_csv(title, episodes);
               break;
            case "Output CSV File":
               PrintEpisodes_csv(title, episodes);
               break;
            case "Output to table":
               PrintEpisodes_table(episodes);
               break;
         }
      }
   }

   static public void PrintEpisodes_csv(String title, JSONArray episodes) {
      config.gui.remote_gui.Browser.resetChoosableFileFilters();
      config.gui.remote_gui.Browser.addChoosableFileFilter(new FileNameExtensionFilter("CSV Files", "csv"));
      config.gui.remote_gui.Browser.setDialogTitle("Export to csv file");
      config.gui.remote_gui.Browser.setSelectedFile(new File(
         config.programDir, tivoFileName.removeSpecialChars(title) + "" + ".csv"
      ));
      int response = config.gui.remote_gui.Browser.showSaveDialog(config.gui.getFrame());
      if (response == javax.swing.JFileChooser.APPROVE_OPTION) {
         String file = config.gui.remote_gui.Browser.getSelectedFile().getAbsolutePath();
         try {
            BufferedWriter ofp = new BufferedWriter(new FileWriter(file));
            ofp.write("EPISODE NAME,PROGRAMID,SERIESID\r\n");
            for (int i=0; i<episodes.length(); ++i) {
               JSONObject episode = episodes.getJSONObject(i);
               String programId = id.programId(episode);
               String seriesId = id.seriesId(episode);
               if (programId == null) programId = "NONE";
               if (seriesId == null) seriesId = "NONE";
               ofp.write("\"" + JSONConverter.makeShowTitle(episode) + "\"");
               ofp.write("," + programId);
               ofp.write("," + seriesId);
               ofp.write("\r\n");
            }
            ofp.close();
            log.print("Output " + "'" + title + "' episodes to csv file: " + file);
         } catch (Exception e) {
            log.error("PrintEpisodes_GUI - " + e.getMessage());
         }
      }
   }

   static public void PrintEpisodes_table(JSONArray episodes) {
      try {
         streamTable tab = config.gui.remote_gui.stream_tab.tab;
         String tivoName = config.gui.remote_gui.getTivoName("Streaming");
         tab.AddRows(tivoName, episodes);
         config.gui.remote_gui.getPanel().setSelectedIndex(6);
         config.gui.SetTivo("Remote");
      } catch (Exception e) {
         log.error("PrintEpisodes_table - " + e.getMessage());
      }
   }

   static public void PrintClipData(String tivoName, JSONObject json) {
      if (json != null && json.has("contentId")) {
         try {
            final String contentId = json.getString("contentId");
            Runnable task = new Runnable() {
               @Override public void run() {
                  Remote r = config.initRemote(tivoName);
                  if (r.success) {
                     r.printClipData(contentId);
                     r.disconnect();
                  }
               }
            };
            new Thread(task).start();
         } catch (JSONException e) {
            log.error("PrintClipData - " + e.getMessage());
         }
      }
   }

}
