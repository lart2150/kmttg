package com.tivo.kmttg.gui;

import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Stack;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import org.jdesktop.swingx.JXTable;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class TableUtil {
   private static JDialog searchDialog = null;
   private static JTextField searchField = null;
   private static JButton find = null;
   private static JDialog thumbsDialog = null;
   private static JComboBox thumbsChoice = null;
   
   public static String getColumnName(JXTable TABLE, int c) {
      return (String)TABLE.getColumnModel().getColumn(c).getHeaderValue();
   }
   
   public static int getColumnIndex(JXTable TABLE, String name) {
      String cname;
      for (int i=0; i<TABLE.getColumnCount(); i++) {
         cname = (String)TABLE.getColumnModel().getColumn(i).getHeaderValue();
         if (cname.equals(name)) return i;
      }
      return -1;
   }
   
   public static int[] GetSelectedRows(JXTable TABLE) {
      debug.print("");
      int[] rows = TABLE.getSelectedRows();
      if (rows.length <= 0)
         log.error("No rows selected");
      return rows;
   }
   
   public static void DeselectRow(JXTable TABLE, int row) {
      TABLE.removeRowSelectionInterval(row,row);
   }
   
   public static void clear(JXTable TABLE) {
      debug.print("");
      TABLE.clearSelection();
      DefaultTableModel model = (DefaultTableModel)TABLE.getModel(); 
      model.setNumRows(0);
   }
   
   public static void AddRow(JXTable table, Object[] data) {
      debug.print("data=" + data);
      DefaultTableModel dm = (DefaultTableModel)table.getModel();
      dm.addRow(data);
   }
   
   public static void RemoveRow(JXTable table, int row) {
      DefaultTableModel dm = (DefaultTableModel)table.getModel();
      dm.removeRow(table.convertRowIndexToModel(row));
   }
   
   public static JSONObject GetRowData(JXTable TABLE, int row, String colName) {
      sortableDate s = (sortableDate) TABLE.getValueAt(row, getColumnIndex(TABLE, colName));
      if (s != null)
         return s.json;
      return null;
   }    

   // Pack all table columns to fit widest cell element
   public static void packColumns(JXTable table, int margin) {
      debug.print("table=" + table + " margin=" + margin);
      //if (config.tableColAutoSize == 1) {
         for (int c=0; c<table.getColumnCount(); c++) {
             packColumn(table, c, 2);
         }
      //}
   }

   // Sets the preferred width of the visible column specified by vColIndex. The column
   // will be just wide enough to show the column head and the widest cell in the column.
   // margin pixels are added to the left and right
   // (resulting in an additional width of 2*margin pixels).
   public static void packColumn(JXTable table, int vColIndex, int margin) {
       DefaultTableColumnModel colModel = (DefaultTableColumnModel)table.getColumnModel();
       TableColumn col = colModel.getColumn(vColIndex);
       int width = 0;
   
       // Get width of column header
       TableCellRenderer renderer = col.getHeaderRenderer();
       if (renderer == null) {
           renderer = table.getTableHeader().getDefaultRenderer();
       }
       Component comp = renderer.getTableCellRendererComponent(
           table, col.getHeaderValue(), false, false, 0, 0);
       width = comp.getPreferredSize().width;
   
       // Get maximum width of column data
       for (int r=0; r<table.getRowCount(); r++) {
           renderer = table.getCellRenderer(r, vColIndex);
           comp = renderer.getTableCellRendererComponent(
               table, table.getValueAt(r, vColIndex), false, false, r, vColIndex);
           width = Math.max(width, comp.getPreferredSize().width);
       }
   
       // Add margin
       width += 2*margin;
              
       // Set the width
       col.setPreferredWidth(width);
   }
   
   // Compute and return all table column widths as an integer array
   public static int[] getColWidths(JXTable TABLE) {
      int[] widths = new int[TABLE.getColumnCount()];
      DefaultTableColumnModel colModel = (DefaultTableColumnModel)TABLE.getColumnModel();
      for (int i=0; i<widths.length; ++i) {
         TableColumn col = colModel.getColumn(i);
         widths[i] = col.getWidth();
      }
      return widths;
   }
   
   // Compute and return all table column widths as an integer array
   public static void setColWidths(JXTable TABLE, int[] widths) {
      if (widths.length != TABLE.getColumnCount()) {
         return;
      }
      DefaultTableColumnModel colModel = (DefaultTableColumnModel)TABLE.getColumnModel();
      for (int i=0; i<widths.length; ++i) {
         TableColumn col = colModel.getColumn(i);
         col.setPreferredWidth(widths[i]);
      }
   }
   
   public static void scrollToCenter(JXTable table, int rowIndex) {
      if (!(table.getParent() instanceof JViewport)) {
        return;
      }
      int vColIndex = 0;
      JViewport viewport = (JViewport) table.getParent();
      Rectangle rect = table.getCellRect(rowIndex, vColIndex, true);
      Rectangle viewRect = viewport.getViewRect();
      rect.setLocation(rect.x - viewRect.x, rect.y - viewRect.y);

      int centerX = (viewRect.width - rect.width) / 2;
      int centerY = (viewRect.height - rect.height) / 2;
      if (rect.x < centerX) {
        centerX = -centerX;
      }
      if (rect.y < centerY) {
        centerY = -centerY;
      }
      rect.translate(centerX, centerY);
      viewport.scrollRectToVisible(rect);
    }
   
   // Bring up a dialog to allow searching SHOW column of given table
   public static void SearchGUI() {
      if (searchDialog == null) {
         // Dialog not created yet, so do so
         JPanel panel = new JPanel();
         panel.setLayout(new BoxLayout(panel, BoxLayout.LINE_AXIS));
         find = new JButton("FIND");
         find.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
               String text = searchField.getText();
               if (text.length() > 0) {
                  JXTable TABLE;
                  // Issue warning and return for irrelevant tabs/tables
                  String irrelevant = "Currently selected tab doesn't contain a suitable table to search.";
                  String tabName = config.gui.getCurrentTabName();
                  if (tabName.equals("FILES") || tabName.equals("Slingbox")) {
                     log.warn(irrelevant);
                     return;
                  }
                  if (tabName.equals("Remote")) {
                     String subTabName = config.gui.remote_gui.getCurrentTabName();
                     if (subTabName.equals("Remote") || subTabName.equals("Info")) {
                        log.warn(irrelevant);
                        return;
                     }
                     TABLE = config.gui.remote_gui.getCurrentTable();
                  } else {
                     TABLE = config.gui.getTab(tabName).getTable().NowPlaying;
                  }
                  if (TABLE != null) {
                     Search(TABLE, text, "SHOW");
                  }
               }
            }
         });
         searchField = new JTextField(20);
         searchField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
               find.doClick();
            }
         });
         JButton close = new JButton("CLOSE");
         close.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
               searchDialog.setVisible(false);
            }
         });
         panel.add(find);
         panel.add(searchField);
         panel.add(close);
         searchDialog = new JDialog(config.gui.getJFrame(), false); // non-modal dialog
         searchDialog.setTitle("Search Table");
         searchDialog.setContentPane(panel);
         searchDialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
         searchDialog.setLocationRelativeTo(config.gui.captions);
         searchDialog.pack();
      }
      
      // Dialog already created, so display it and highlight any existing search text
      searchField.requestFocus();
      searchField.selectAll();
      searchDialog.setVisible(true);
   }
   
   // Perform a search in given TABLE column name for searchString
   private static void Search(JXTable TABLE, String searchString, String colName) {
      int startRow = 0;
      int[] sel = TABLE.getSelectedRows();
      if (sel.length > 0)
         startRow = sel[0] + 1;
      Boolean result = searchMatch(TABLE, colName, searchString, startRow, TABLE.getRowCount()-1);
      if (!result && startRow > 0) {
         searchMatch(TABLE, colName, searchString, 0, startRow);
      }
   }

   public static Boolean searchMatch(JXTable TABLE, String colName, String searchString, int start, int stop) {
      String v;
      for (int row=start; row<=stop; row++) {
         Object o = TABLE.getValueAt(row, getColumnIndex(TABLE, colName));
         v = null;
         if (o instanceof String)
            v = (String)o;
         if (o instanceof sortableShow)
            v = o.toString();
         if ( v == null ) {
            log.error("searchMatch: Unimplemented SHOW type found");
         } else {
            v = v.toLowerCase();
            if (v.matches("^.*" + searchString.toLowerCase() + ".*$")) {
               // scroll to and set selection to given row
               scrollToCenter(TABLE, row);
               TABLE.setRowSelectionInterval(row, row);
               TABLE.requestFocus();
               return true;
            }
         }
      }
      return false;      
   }
   
   // Bring up set thumbs dialog
   public static void ThumbsGUI() {      
      // Determine tivoName and json of currently selected table
      String tabName = config.gui.remote_gui.getCurrentTabName();
      final String tivoName = config.gui.remote_gui.getTivoName(tabName);
      if (tivoName == null)
         return;
      final JSONObject json = config.gui.remote_gui.getSelectedJSON(tabName);
      if (json == null)
         return;
      if (thumbsDialog == null) {
         // Dialog not created yet, so do so
         JPanel row1 = new JPanel();
         row1.setLayout(new BoxLayout(row1, BoxLayout.LINE_AXIS));
         JLabel rating = new JLabel("Thumbs Rating: ");
         thumbsChoice = new JComboBox();
         for (int i=-3; i<=3; ++i)
            thumbsChoice.addItem(i);
         JButton setButton = new JButton("SET");
         setButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
               class backgroundRun extends SwingWorker<Object, Object> {
                  protected Object doInBackground() {
                     // Determine tivoName and json of currently selected table
                     String tabName = config.gui.remote_gui.getCurrentTabName();
                     String tivoName = config.gui.remote_gui.getTivoName(tabName);
                     if (tivoName == null)
                        return null;
                     JSONObject json = config.gui.remote_gui.getSelectedJSON(tabName);
                     if (json == null)
                        return null;
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
                           if (json.has("subtitle"))
                              title = title + " - " + json.getString("subtitle");
                        } catch (JSONException e) {
                           log.error("ThumbsGUI SET - " + e.getMessage());
                        }
                        if (result) {
                           log.warn("Successfully set thumbs rating for '" + title + "' to: " + thumbsRating);
                        }
                        else
                           log.error("Failed to set thumbs rating for '" + title + "'");
                     }
                     return null;
                  }
               }
               if (thumbsDialog != null) {
                  thumbsDialog.setVisible(false);
               }
               backgroundRun b = new backgroundRun();
               b.execute();
            }
         });
         row1.add(setButton);
         row1.add(rating);
         row1.add(thumbsChoice);
         thumbsDialog = new JDialog(config.gui.getJFrame(), false); // non-modal dialog
         thumbsDialog.setTitle("Thumbs Rating");
         thumbsDialog.setContentPane(row1);
         thumbsDialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
         thumbsDialog.setLocationRelativeTo(config.gui.captions);
         //thumbsDialog.setMinimumSize(new Dimension(300,100));
         thumbsDialog.pack();
      }
      
      // Set default rating
      thumbsChoice.setSelectedItem(0);
      if (json != null && json.has("collectionId")) {
         class backgroundRun extends SwingWorker<Object, Object> {
            protected Object doInBackground() {
               int rating = 0;
               Remote r = config.initRemote(tivoName);
               if (r.success) {
                  rating = r.getThumbsRating(json);
                  r.disconnect();
               }
               thumbsChoice.setSelectedItem(rating);
               return null;
            }
         }
         backgroundRun b = new backgroundRun();
         b.execute();
      }
      
      // Set title
      String title = "Set thumbs: ";
      try {
         if (json != null && json.has("title"))
            title += json.getString("title");
         if (json != null && json.has("subtitle"))
            title += " - " + json.getString("subtitle");
      } catch (JSONException e) {
         log.error("ThumbsGUI - " + e.getMessage());
      }
      thumbsDialog.setTitle(title);
      
      // Display dialog and set default thumbs rating
      thumbsDialog.requestFocus();
      thumbsDialog.setVisible(true);
   }

   // Add right mouse button listener
   public static void AddRightMouseListener(final JXTable TABLE) {            
      TABLE.addMouseListener(
         new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
               if (SwingUtilities.isRightMouseButton(e)) {
                  PopupHandler.display(TABLE, e);
               }
            }
         }
      );
   }
   
   public static long getLongDateFromString(String date) {
      try {
         SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzz");
         Date d = format.parse(date + " GMT");
         return d.getTime();
      } catch (ParseException e) {
        log.error("getLongDateFromString - " + e.getMessage());
        return 0;
      }
   }
   
   public static String printableTimeFromJSON(JSONObject entry) {
      long start = getStartTime(entry);
      SimpleDateFormat sdf = new SimpleDateFormat("E MM/dd/yy hh:mm a");
      return sdf.format(start);
   }
      
   public static long getStartTime(JSONObject json) {
      try {
         if (json.has("startTime")) {
            String startString = json.getString("startTime");
            long start = getLongDateFromString(startString);
            if (json.has("requestedStartPadding"))
               start -= json.getInt("requestedStartPadding")*1000;
            return start;
         } else {
            return 0;
         }
      } catch (Exception e) {
         log.error("getStartTime - " + e.getMessage());
         return 0;
      }
   }
   
   public static long getEndTime(JSONObject json) {
      try {
         long start = getStartTime(json);
         long end = start + json.getInt("duration")*1000;
         if (json.has("requestedEndPadding"))
            end += json.getInt("requestedEndPadding")*1000;
         return end;
      } catch (Exception e) {
         log.error("getEndTime - " + e.getMessage());
         return 0;
      }
   }
   
   public static Boolean isWL(JSONObject json) {
      Boolean WL = false;
      try {
         if (json.has("idSetSource")) {
            JSONObject idSetSource = json.getJSONObject("idSetSource");
            if (idSetSource.has("type") && idSetSource.getString("type").equals("wishListSource"))
               WL = true;
         }
      } catch (JSONException e) {
         log.error("isWL - " + e.getMessage());
      }
      return WL;
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
   
   public static String makeShowTitle(JSONObject entry) {
      String title = " ";
      try {
         if (entry.has("title"))
            title += entry.getString("title");
         if (entry.has("seasonNumber") && entry.has("episodeNum")) {
            title += " [Ep " + entry.get("seasonNumber") +
            String.format("%02d]", entry.getJSONArray("episodeNum").get(0));
         }
         if (entry.has("movieYear"))
            title += " [" + entry.get("movieYear") + "]";
         if (entry.has("subtitle"))
            title += " - " + entry.getString("subtitle");
         if (entry.has("subscriptionIdentifier")) {
            JSONArray a = entry.getJSONArray("subscriptionIdentifier");
            if (a.length() > 0) {
               if (a.getJSONObject(0).has("subscriptionType")) {
                  String type = a.getJSONObject(0).getString("subscriptionType");
                  if (type.equals("singleTimeChannel") || type.equals("repeatingTimeChannel"))
                     title = " Manual:" + title;
               }
            }
         }
      } catch (JSONException e) {
         log.error("makeShowTitle - " + e.getMessage());
      }
      return title;
   }
   
   public static String makeChannelName(JSONObject entry) {
      String channel = " ";
      try {
         if (entry.has("channel")) {
            JSONObject o = entry.getJSONObject("channel");
            if (o.has("channelNumber"))
               channel += o.getString("channelNumber");
            if (o.has("callSign")) {
               String callSign = o.getString("callSign");
               if (callSign.toLowerCase().equals("all channels"))
                  channel += callSign;
               else
                  channel += "=" + callSign;
            }
         } else {
            if (entry.has("idSetSource")) {
               JSONObject idSetSource = entry.getJSONObject("idSetSource");
               if (idSetSource.has("channel"))
                  channel = makeChannelName(idSetSource);
               else {
                  if (idSetSource.has("consumptionSource")) {
                     if (idSetSource.getString("consumptionSource").equals("linear"))
                        channel += "All Channels";
                  }
               }
            }
         }
      } catch (JSONException e) {
         log.error("makeChannelName - " + e.getMessage());
      }
      return channel;
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
               if (json.has("contentId") && json.has("collectionId")) {
                  String title = "UNTITLED";
                  if (json.has("title"))
                     title = json.getString("title");
                  JSONObject j = new JSONObject();
                  j.put("contentId", json.getString("contentId"));
                  j.put("collectionId", json.getString("collectionId"));
                  Remote r = config.initRemote(tivoName);
                  if (r.success) {
                     JSONObject result = r.Command("ContentLocatorStore", j);
                     if (result != null) {
                        log.warn("Added streaming title to My Shows: '" + title + "' on Tivo: " + tivoName);
                        // Set thumbs rating if it doesn't exist for this collection
                        r.setThumbsRating(json, 1, false);
                        r.disconnect();
                        return true;
                     } else {
                        log.error("Failed to create content locator for: " + title);
                        r.disconnect();
                        return false;
                     }
                  }
               } else {
                  log.warn("Missing contentId and/or collectionId for streaming title");
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
            final JSONObject o = config.gui.remote_gui.recordOpt.promptUser(
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
                  class backgroundRun extends SwingWorker<Object, Object> {
                     JSONObject json;
                     public backgroundRun(JSONObject json) {
                        this.json = json;
                     }
                     protected Object doInBackground() {
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
                                 return(false);
                              }
                           }
                           r.disconnect();
                        }
                        return null;
                     }
                  }
                  backgroundRun b = new backgroundRun(json);
                  b.execute();
               } else {
                  if (existing == null) {
                     // Attempt to schedule using all RPC enabled TiVos
                     class backgroundRun extends SwingWorker<Object, Object> {
                        JSONObject json;
                        public backgroundRun(JSONObject json) {
                           this.json = json;
                        }
                        protected Object doInBackground() {
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
                                       return(true);
                                    } else {
                                       log.warn("Cannot schedule '" + _title + "' on '" + name + "' due to conflicts");
                                    }
                                 }
                                 r.disconnect();
                              }
                           }
                           return null;
                        }
                     }
                     backgroundRun b = new backgroundRun(json);
                     b.execute();                     
                  }
               }
            }
         } else {
            // This is likely unavailable content, so for type series bring up SP form instead
            if (json.has("collectionType") && json.getString("collectionType").equals("series")) {
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
            log.error("Missing contentId and/or offerId for: '" + title + "'");
            return(false);
         }
      } catch (JSONException e) {
         log.error("recordSingle failed - " + e.getMessage());
         return(false);
      }
      return(true);
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
                     config.gui.remote_gui.addEntryToTodo(tivoName, json);
                     addTivoNameFlagtoJson(json, "__inTodo__", tivoName);
                  }
               }
            } catch (JSONException e) {
               log.error("recordSingleCB error - " + e.getMessage());
            }
         }
      }
   }
   
   // For a given array of JSON objects sort by start date - most recent 1st
   static public JSONArray sortByLatestStartDate(JSONArray array) {
      class DateComparator implements Comparator<JSONObject> {      
         public int compare(JSONObject j1, JSONObject j2) {
            long start1 = getStartTime(j1);
            long start2 = getStartTime(j2);
            if (start1 < start2){
               return 1;
            } else if (start1 > start2){
               return -1;
            } else {
               return 0;
            }
         }
      }
      List<JSONObject> arrayList = new ArrayList<JSONObject>();
      for (int i=0; i<array.length(); ++i)
         try {
            arrayList.add(array.getJSONObject(i));
         } catch (JSONException e) {
            log.error("sortByStartDate - " + e.getMessage());
         }
      JSONArray sorted = new JSONArray();
      DateComparator comparator = new DateComparator();
      Collections.sort(arrayList, comparator);
      for (JSONObject ajson : arrayList) {
         sorted.put(ajson);
      }
      return sorted;
   }
   
   // For a given array of JSON objects sort by start date - oldest 1st
   static public JSONArray sortByOldestStartDate(JSONArray array) {
      class DateComparator implements Comparator<JSONObject> {      
         public int compare(JSONObject j1, JSONObject j2) {
            long start1 = getStartTime(j1);
            long start2 = getStartTime(j2);
            if (start1 > start2){
               return 1;
            } else if (start1 < start2){
               return -1;
            } else {
               return 0;
            }
         }
      }
      List<JSONObject> arrayList = new ArrayList<JSONObject>();
      for (int i=0; i<array.length(); ++i)
         try {
            arrayList.add(array.getJSONObject(i));
         } catch (JSONException e) {
            log.error("sortByStartDate - " + e.getMessage());
         }
      JSONArray sorted = new JSONArray();
      DateComparator comparator = new DateComparator();
      Collections.sort(arrayList, comparator);
      for (JSONObject ajson : arrayList) {
         sorted.put(ajson);
      }
      return sorted;
   }
   
   // For a given array of JSON objects sort by episode numbers - earliest 1st
   static public JSONArray sortByEpisode(JSONArray array) {
      class EpComparator implements Comparator<JSONObject> {      
         public int compare(JSONObject j1, JSONObject j2) {
            int ep1 = getEpisodeNum(j1);
            int ep2 = getEpisodeNum(j2);
            if (ep1 > ep2){
               return 1;
            } else if (ep1 < ep2){
               return -1;
            } else {
               return 0;
            }
         }
      }
      List<JSONObject> arrayList = new ArrayList<JSONObject>();
      for (int i=0; i<array.length(); ++i) {
         try {
            arrayList.add(array.getJSONObject(i));
         } catch (JSONException e) {
            log.error("sortByEpisode - " + e.getMessage());
         }
      }
      JSONArray sorted = new JSONArray();
      EpComparator comparator = new EpComparator();
      Collections.sort(arrayList, comparator);
      for (JSONObject ajson : arrayList) {
         sorted.put(ajson);
      }
      return sorted;
   }
   
   public static int getEpisodeNum(JSONObject json) {
      try {
         if (json.has("seasonNumber") && json.has("episodeNum")) {
            int seasonNumber = json.getInt("seasonNumber");
            int episodeNum = json.getJSONArray("episodeNum").getInt(0);
            return 100*seasonNumber + episodeNum;
         }
      } catch (Exception e) {
         log.error("getEpisodeNum - " + e.getMessage());
      }
      return 0;
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
   
   static public void addTivoNameFlagtoJson(JSONObject json, String flag, String tivoName) {
      try {
         if (json.has(flag))
            json.put(flag, json.getString(flag) + ", " + tivoName);
         else
            json.put(flag, tivoName);
      } catch (JSONException e) {
         log.error("addTivoNameFlagtoJson - " + e.getMessage());
      }
   }
   
   // Return friendly name of a partner based on id, such as Netflix, Hulu, etc.
   static public String getPartnerName(JSONObject entry) {
      try {
         if (config.partners.size() == 0) {
            log.warn("Refreshing partner names");
            Remote r = config.initRemote(config.gui.remote_gui.getTivoName("search"));
            if (r.success) {
               JSONObject json = new JSONObject();
               json.put("bodyId", r.bodyId_get());
               json.put("noLimit", true);
               json.put("levelOfDetail", "high");
               JSONObject result = r.Command("partnerInfoSearch", json);
               if (result != null && result.has("partnerInfo")) {
                  JSONArray info = result.getJSONArray("partnerInfo");
                  for (int i=0; i<info.length(); ++i) {
                     JSONObject j = info.getJSONObject(i);
                     if (j.has("partnerId") && j.has("displayName")) {
                        config.partners.put(j.getString("partnerId"), j.getString("displayName"));
                     }
                  }
               }                 
               r.disconnect();
            }
         }
   
         String partnerId = "";
         if (entry.has("partnerId"))
            partnerId = entry.getString("partnerId");
         if (entry.has("brandingPartnerId"))
            partnerId = entry.getString("brandingPartnerId");
         String name = partnerId;
         if (config.partners.containsKey(partnerId))
            name = config.partners.get(partnerId);
         return name;
      } catch (JSONException e1) {
         log.error("getPartnerName - " + e1.getMessage());
         return "STREAMING";
      }
   }
}
