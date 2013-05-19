package com.tivo.kmttg.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Stack;
import java.util.Vector;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import org.jdesktop.swingx.JXTable;
import org.jdesktop.swingx.decorator.Sorter;

import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.*;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.createMeta;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;

public class nplTable {
   public String tivoName = null;
   public JXTable NowPlaying = null;
   public JScrollPane nplScroll = null;
   public String[] FILE_cols = {"FILE", "SIZE", "DIR"};
   public String[] TIVO_cols = {"", "SHOW", "DATE", "CHANNEL", "DUR", "SIZE", "Mbps"};
   public Boolean inFolder = false;
   public String folderName = null;
   public int folderEntryNum = -1;
   private Stack<Hashtable<String,String>> entries = null;
   private Hashtable<String,Stack<Hashtable<String,String>>> folders = null;
   private Vector<Hashtable<String,String>> sortedOrder = null;
   private String lastUpdated = null;
   // This needed to flag when calling updateNPLjobStatus so that multiple
   // selection event triggers can be avoided
   private Boolean UpdatingNPL = false;
         
   nplTable(String tivoName) {
      this.tivoName = tivoName;
      Object[][] data = {}; 
      if (tivoName.equals("FILES")) {
         NowPlaying = new JXTable(data, FILE_cols);
         NowPlaying.setModel(new FilesTableModel(data, FILE_cols));
      }
      else {
         NowPlaying = new JXTable(data, TIVO_cols);
         NowPlaying.setModel(new NplTableModel(data, TIVO_cols));
      }
      nplScroll = new JScrollPane(NowPlaying);
      
      // Add listener for click handling (for folder entries)
      NowPlaying.addMouseListener(
         new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
               MouseClicked(e);
            }
         }
      );
      
      // Add keyboard listener (for delete & space keys)
      NowPlaying.addKeyListener(
         new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
               KeyPressed(e);
            }
         }
      );
      
      // Define custom column sorting routines
      Comparator<Object> sortableComparator = new Comparator<Object>() {
         public int compare(Object o1, Object o2) {
            if (o1 instanceof sortableDate && o2 instanceof sortableDate) {
               sortableDate s1 = (sortableDate)o1;
               sortableDate s2 = (sortableDate)o2;
               long l1 = Long.parseLong(s1.sortable);
               long l2 = Long.parseLong(s2.sortable);
               if (l1 > l2) return 1;
               if (l1 < l2) return -1;
               return 0;
            }
            if (o1 instanceof sortableSize && o2 instanceof sortableSize) {
               sortableSize s1 = (sortableSize)o1;
               sortableSize s2 = (sortableSize)o2;
               if (s1.sortable > s2.sortable) return 1;
               if (s1.sortable < s2.sortable) return -1;
               return 0;
            }
            if (o1 instanceof sortableShow && o2 instanceof sortableShow) {
               sortableShow s1 = (sortableShow)o1;
               sortableShow s2 = (sortableShow)o2;
               int e1=-1, e2=-1;
               if (s1.episodeNum.length() > 0 && s2.episodeNum.length() > 0) {
                  e1 = Integer.parseInt(s1.episodeNum);
                  e2 = Integer.parseInt(s2.episodeNum);
               }
               if (inFolder) {
                  // Sort by episodeNum if available
                  // Else alphabetical sort
                  int result=0;
                  if (e1 > 0 && e2 >0) {
                     if (e1 > e2) result = 1;
                     if (e1 < e2) result = -1;
                  } else {
                     result = s1.title.compareToIgnoreCase(s2.title);
                  }
                  return result;
               } else {
                  // Sort 1st by titleOnly, then by date
                  int result = s1.titleOnly.compareToIgnoreCase(s2.titleOnly);
                  if (result == 0) {
                     if (e1 > e2) result = 1;
                     if (e1 < e2) result = -1;
                  }
                  if (result == 0) {
                     if (s1.gmt > s2.gmt) result = 1;
                     if (s1.gmt < s2.gmt) result = -1;
                  }
                  return result;
               }
            }
            if (o1 instanceof sortableDuration && o2 instanceof sortableDuration) {
               sortableDuration d1 = (sortableDuration)o1;
               sortableDuration d2 = (sortableDuration)o2;
               if (d1.sortable > d2.sortable) return 1;
               if (d1.sortable < d2.sortable) return -1;
               return 0;
            }
            if (o1 instanceof sortableDouble && o2 instanceof sortableDouble) {
               sortableDouble d1 = (sortableDouble)o1;
               sortableDouble d2 = (sortableDouble)o2;
               if (d1.sortable > d2.sortable) return 1;
               if (d1.sortable < d2.sortable) return -1;
               return 0;
            }
            return 0;
         }
      };
      
      // Use custom sorting routines for certain columns
      if (tivoName.equals("FILES")) {
         Sorter sorter = NowPlaying.getColumnExt(1).getSorter();
         sorter.setComparator(sortableComparator);
      } else {
         Sorter sorter = NowPlaying.getColumnExt(1).getSorter();
         sorter.setComparator(sortableComparator);
         sorter = NowPlaying.getColumnExt(2).getSorter();
         sorter.setComparator(sortableComparator);
         sorter = NowPlaying.getColumnExt(4).getSorter();
         sorter.setComparator(sortableComparator);
         sorter = NowPlaying.getColumnExt(5).getSorter();
         sorter.setComparator(sortableComparator);
         sorter = NowPlaying.getColumnExt(6).getSorter();
         sorter.setComparator(sortableComparator);
      }   
      
      // Define selection listener to detect table row selection changes
      NowPlaying.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
         public void valueChanged(ListSelectionEvent e) {
            if (e.getValueIsAdjusting() || UpdatingNPL) return;
            ListSelectionModel rowSM = (ListSelectionModel)e.getSource();
            int row = rowSM.getMinSelectionIndex();
            if (row > -1) {
               NowPlayingRowSelected(row);
            }
         }
      });
                        
      // Change color & font
      TableColumn tm;
      if (tivoName.equals("FILES")) {
         tm = NowPlaying.getColumnModel().getColumn(0);
         tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));
         
         tm = NowPlaying.getColumnModel().getColumn(1);
         tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndDarker, config.tableFont));
         // Right justify file size
         ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.RIGHT);
         
         tm = NowPlaying.getColumnModel().getColumn(2);
         tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));         
      } else {
         // Allow icons in column 0
         NowPlaying.setDefaultRenderer(Icon.class, new IconCellRenderer());
         
         tm = NowPlaying.getColumnModel().getColumn(1);
         tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndDarker, config.tableFont));
         
         tm = NowPlaying.getColumnModel().getColumn(2);
         tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));
         // Right justify dates
         ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.RIGHT);
         
         tm = NowPlaying.getColumnModel().getColumn(3);
         tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndDarker, config.tableFont));
         
         tm = NowPlaying.getColumnModel().getColumn(4);
         tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));
         // Center justify duration
         ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.CENTER);
         
         tm = NowPlaying.getColumnModel().getColumn(5);
         tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndDarker, config.tableFont));
         // Right justify file size
         ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.RIGHT);
         
         tm = NowPlaying.getColumnModel().getColumn(6);
         tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));
         // Right justify Mbps
         ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.RIGHT);
      }
               
      //NowPlaying.setFillsViewportHeight(true);
      NowPlaying.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
   }
   

   // Custom table cell renderer to allow for icons
   class IconCellRenderer extends DefaultTableCellRenderer {
      private static final long serialVersionUID = 1L;

      protected void setValue(Object value) {
         debug.print("value=" + value);
         if (value instanceof Icon) {
            setIcon((Icon)value);
            super.setValue(null);
         } else {
            setIcon(null);
            super.setValue(value);
         }
      }
      
      public Component getTableCellRendererComponent
      (JTable table, Object value, boolean isSelected,
       boolean hasFocus, int row, int column) {
         Component cell = super.getTableCellRendererComponent(
            table, value, isSelected, hasFocus, row, column
         );
         if ( ! isSelected ) {
            if (column % 2 == 0)
               cell.setBackground(config.tableBkgndLight);
            else
               cell.setBackground(config.tableBkgndDarker);
         }            
         return cell;
      }
   }
   
   // Override some default table model actions
   class NplTableModel extends DefaultTableModel {
      private static final long serialVersionUID = 1L;

      public NplTableModel(Object[][] data, Object[] columnNames) {
         super(data, columnNames);
      }
      
      @SuppressWarnings("unchecked")
      // This is used to define columns as specific classes
      public Class getColumnClass(int col) {
         if (col == 0) {
            return Icon.class;
         }
         if (col == 1) {
            return sortableShow.class;
         }
         if (col == 2) {
            return sortableDate.class;
         }
         if (col == 4) {
            return sortableDuration.class;
         }
         if (col == 5) {
            return sortableSize.class;
         }
         if (col == 6) {
            return sortableDouble.class;
         }
         return Object.class;
      } 
      
      // Set all cells uneditable
      public boolean isCellEditable(int row, int column) {        
         return false;
      }
   }   
   
   // Override some default table model actions
   class FilesTableModel extends DefaultTableModel {
      private static final long serialVersionUID = 1L;

      public FilesTableModel(Object[][] data, Object[] columnNames) {
         super(data, columnNames);
      }
      
      @SuppressWarnings("unchecked")
      // This is used to define columns as specific classes
      public Class getColumnClass(int col) {
         if (col == 1) {
            return sortableSize.class;
         }
         return Object.class;
      } 
      
      // Set all cells uneditable
      public boolean isCellEditable(int row, int column) {        
         return false;
      }
   }
   
   /**
   * Applied background color to single column of a JTable
   * in order to distinguish it apart from other columns.
   */ 
   class ColorColumnRenderer extends DefaultTableCellRenderer 
   {
      /**
       * 
       */
      private static final long serialVersionUID = 1L;
      Color bkgndColor;
      Font font;
      
      public ColorColumnRenderer(Color bkgnd, Font font) {
         super(); 
         bkgndColor = bkgnd;
         this.font = font;
      }
      
      public Component getTableCellRendererComponent
          (JTable table, Object value, boolean isSelected,
           boolean hasFocus, int row, int column) 
      {
         Component cell = super.getTableCellRendererComponent
            (table, value, isSelected, hasFocus, row, column);
         
         if ( ! isSelected ) {
            if ( getColumnIndex("DATE") != -1 ) {
               // Download mode
               sortableDate d = (sortableDate)table.getValueAt(row, getColumnIndex("DATE"));
               if (column % 2 == 0)
                  cell.setBackground(config.tableBkgndLight);
               else
                  cell.setBackground(config.tableBkgndDarker);
               
               if ( d != null && ! d.folder && d.data.containsKey("CopyProtected") )
                  cell.setBackground( config.tableBkgndProtected );
               
               if ( d != null && ! d.folder && d.data.containsKey("ExpirationImage") &&
                   (d.data.get("ExpirationImage").equals("in-progress-recording") ||
                    d.data.get("ExpirationImage").equals("in-progress-transfer")))
                  cell.setBackground( config.tableBkgndRecording );
               
            } else {
               // FILES mode
               if (column % 2 == 0)
                  cell.setBackground(config.tableBkgndLight);
               else
                  cell.setBackground(config.tableBkgndDarker);
            }
         }
         
         cell.setFont(config.tableFont);
        
         return cell;
      }
   }   
   
   // Mouse event handler
   // This will display folder entries in table if folder entry single-clicked
   // Otherwise obtains and displays extended show info if right button clicked
   private void MouseClicked(MouseEvent e) {
      if( ! tivoName.equals("FILES") && e.getClickCount() == 1 ) {
         int row = NowPlaying.rowAtPoint(e.getPoint());
         sortableDate s = (sortableDate)NowPlaying.getValueAt(row,getColumnIndex("DATE"));
         if (s.folder) {
            setFolderState(true);
            folderName = s.folderName;
            folderEntryNum = row;
            RefreshNowPlaying(s.folderData);
         } else {
            if (SwingUtilities.isRightMouseButton(e)) {
               if (s.data != null) {
                  createMeta.getExtendedMetadata(tivoName, s.data, true);
                  // De-select all
                  NowPlaying.getSelectionModel().clearSelection();
               }
               // Select row
               NowPlaying.setRowSelectionInterval(row, row);
            }
         }
      }
   }
   
   // Handle delete keyboard presses
   private void KeyPressed(KeyEvent e) {
      int keyCode = e.getKeyCode();
      if (keyCode == KeyEvent.VK_DELETE || keyCode == KeyEvent.VK_SPACE) {
         int[] selected = GetSelectedRows();         
         if (selected != null && selected.length > 0) {
            if (keyCode == KeyEvent.VK_DELETE) {
               // Delete key has special action
               String show_names = "";
               Stack<String> urlsToDelete = new Stack<String>();
               Stack<String> idsToDelete = new Stack<String>();
               String id;
               
               // Figure out what selection should be if all selected rows are deleted
               sortableDate final_select = null;
               int lowest = -1;
               for (int i=0; i<selected.length; ++i) {
                  if (lowest == -1)
                     lowest = selected[i];
                  if (selected[i] < lowest)
                     lowest = selected[i];
               }
               if (lowest-1 < 0)
                  lowest = 0;
               else
                  lowest -= 1;
               final_select = (sortableDate)NowPlaying.getValueAt(lowest,getColumnIndex("DATE"));
               
               for (int i=0; i<selected.length; ++i) {
                  int row = selected[i];
                  sortableDate s = (sortableDate)NowPlaying.getValueAt(row,getColumnIndex("DATE"));
                  if (s.folder) {
                     // Delete all shows in folder
                     for (int j=0; j<s.folderData.size(); j++) {
                        Hashtable<String,String> entry = s.folderData.get(j);
                        if (entry.containsKey("url")) {
                           log.warn("Delete url=" + entry.get("url"));
                           if (config.TivoWebPlusDelete == 1 && ! config.rpcEnabled(tivoName))
                              urlsToDelete.add(entry.get("url"));
                           if (config.rpcEnabled(tivoName)) {
                              id = rnpl.findRecordingId(tivoName, entry);
                              if (id != null) {
                                 show_names += entry.get("title");
                                 urlsToDelete.add(entry.get("url"));
                                 idsToDelete.add(id);
                              }
                           }
                        }
                     } // for
                  } else {
                     // Delete individual show
                     if (config.TivoWebPlusDelete == 1 && ! config.rpcEnabled(tivoName)) {
                        if (s.data.containsKey("url")) {
                           urlsToDelete.add(s.data.get("url"));
                        }
                     }
                     if (config.rpcEnabled(tivoName)) {
                        id = rnpl.findRecordingId(tivoName, s.data);
                        if (id != null) {
                           if (s.data.containsKey("InProgress") && s.data.get("InProgress").equals("Yes")) {
                              // Still recording => stop recording instead of deleting
                              if (StopRecording(id)) {
                                 log.warn("Stopped recording: " + s.data.get("title"));
                                 s.data.put("InProgress", "No");
                                 s.data.remove("ExpirationImage");
                                 RefreshNowPlaying(entries);
                              }
                           } else {
                              // Not recording so go ahead and delete it
                              show_names += s.data.get("title");
                              urlsToDelete.add(s.data.get("url"));
                              idsToDelete.add(id);
                           }
                        }
                     }
                  } // else individual show
               } // for selected
               if (urlsToDelete.size() > 0) {
                  if (config.TivoWebPlusDelete == 1 && ! config.rpcEnabled(tivoName)) {
                     // USE TWP to remove items from entries stack
                     // NOTE: Always revert to top view (not inside a folder)
                     RemoveUrls(urlsToDelete);
                     RefreshTable();
                  }
                  if (config.rpcEnabled(tivoName)) {
                     // Use iPad remote protocol to remove items
                     log.warn("Deleting selected shows on TiVo '" + tivoName + "':\n" + show_names);
                     RemoveIds(urlsToDelete, idsToDelete);
                     RefreshTable();
                  }
               } // if urslToDelete
               
               // After table refresh this is the data of the row to look for to select
               if (final_select != null) {
                  if (final_select.folder) {
                     Hashtable<String,String> h = new Hashtable<String,String>();
                     h.put("folderName", final_select.folderName);
                     selectRowWithData(h);
                  } else {
                     selectRowWithData(final_select.data);
                  }
               }
            } // if keyCode == KeyEvent.VK_DELETE
            
            if (keyCode == KeyEvent.VK_SPACE) {
               // Space key has special action
               String id;
               int row = selected[0];
               sortableDate s = (sortableDate)NowPlaying.getValueAt(row,getColumnIndex("DATE"));
               if ( ! s.folder ) {
                  // Play individual show
                  if (config.rpcEnabled(tivoName)) {
                     id = rnpl.findRecordingId(tivoName, s.data);
                     if (id != null) {
                        // Use iPad remote protocol to play given item
                        String title = "";
                        if (s.data.containsKey("title"))
                           title += s.data.get("title");
                        log.warn("Playing show on TiVo '" + tivoName + "': " + title);
                        PlayShow(id);
                     }
                  }
               }
            } // if keyCode == KeyEvent.VK_SPACE            
         } // if selected != null
      } else if (keyCode == KeyEvent.VK_J) {
         // Print all data of selected row to log window by sorted keys
         int[] selected = GetSelectedRows();
         if (selected == null || selected.length < 1)
            return;
         sortableDate s = (sortableDate)NowPlaying.getValueAt(selected[0],getColumnIndex("DATE"));
         if ( ! s.folder && s.data != null ) {
            Vector<String> v = new Vector<String>(s.data.keySet());
            Collections.sort(v);            
            for (Enumeration<String> it = v.elements(); it.hasMoreElements();) {
              String name = it.nextElement();
              log.print(name + " = " + s.data.get(name));
            }
         }
      } else if (keyCode == KeyEvent.VK_Q) {
         // Web query currently selected entry
         int[] selected = GetSelectedRows();
         if (selected == null || selected.length < 1)
            return;
         sortableDate s = (sortableDate)NowPlaying.getValueAt(selected[0],getColumnIndex("DATE"));
         if ( ! s.folder && s.data != null && s.data.containsKey("title")) {
            TableUtil.webQuery(s.data.get("title"));
         }
      } else {
         // Pass along keyboard action for unimplemented key press
         e.consume();
      }
   }
   
   public void RefreshTable() {
      folderize(entries);
      setFolderState(false);
      RefreshNowPlaying(entries);
   }
   
   public void setFolderState(Boolean state) {
      if (state) {
         inFolder = true;
         config.gui.getTab(tivoName).showFoldersVisible(false);
         config.gui.getTab(tivoName).showDiskUsageVisible(false);
         config.gui.getTab(tivoName).getRefreshButton().setText("Back");
         config.gui.getTab(tivoName).getRefreshButton().setToolTipText(config.gui.getToolTip("back"));
      } else {
         inFolder = false;
         config.gui.getTab(tivoName).showFoldersVisible(true);
         config.gui.getTab(tivoName).showDiskUsageVisible(true);
         config.gui.getTab(tivoName).getRefreshButton().setText("Refresh");
         config.gui.getTab(tivoName).getRefreshButton().setToolTipText(config.gui.getToolTip("refresh"));         
      }
   }
   
   // Return current state of Display Folders boolean for this TiVo
   public Boolean showFolders() {
      if (tivoName.equals("FILES")) return false;
      return config.gui.getTab(tivoName).showFolders();
   }
   
   public String getColumnName(int c) {
      return (String)NowPlaying.getColumnModel().getColumn(c).getHeaderValue();
   }
   
   public int getColumnIndex(String name) {
      String cname;
      for (int i=0; i<NowPlaying.getColumnCount(); i++) {
         cname = (String)NowPlaying.getColumnModel().getColumn(i).getHeaderValue();
         if (cname.equals(name)) return i;
      }
      return -1;
   }
   
   public int[] GetSelectedRows() {
      debug.print("");
      int[] rows = NowPlaying.getSelectedRows();
      if (rows.length <= 0)
         log.error("No rows selected");
      return rows;
   }
   
   public void NowPlayingRowSelected(int row) {
      debug.print("row=" + row);
      if (row == -1) return;
      if (tivoName.equals("FILES")) {
         // FILES mode - don't do anything
      } else {
         // Now Playing mode
         // Get column items for selected row 
         sortableDate s = (sortableDate)NowPlaying.getValueAt(row,getColumnIndex("DATE"));
         if (s.folder) {
            // Folder entry - don't display anything
         } else {
            // Non folder entry so print single entry info
            String t = s.data.get("date_long");
            String channelNum = null;
            if ( s.data.containsKey("channelNum") ) {
               channelNum = s.data.get("channelNum");
            }
            String channel = null;
            if ( s.data.containsKey("channel") ) {
               channel = s.data.get("channel");
            }
            String description = null;
            if ( s.data.containsKey("description") ) {
               description = s.data.get("description");
            }
            int duration = Integer.parseInt(s.data.get("duration"));
            String d = String.format("%d mins", secsToMins((long)duration/1000));
            String message = "Recorded " + t;
            if (channelNum != null && channel != null) {
               message += " on " + channelNum + "=" + channel;
            }
            message += ", Duration=" + d;
            
            if (s.data.containsKey("EpisodeNumber"))
               message += ", EpisodeNumber=" + s.data.get("EpisodeNumber");
            
            if (s.data.containsKey("ByteOffset") && s.data.containsKey("size")) {
               if (! s.data.get("ByteOffset").startsWith("0")) {
                  Double pct = Double.valueOf(s.data.get("ByteOffset"))/Double.valueOf(s.data.get("size"));
                  message += ", PAUSE POINT: " + String.format("%.1f%%", pct*100);
               }
            }
            
            if (s.data.containsKey("originalAirDate")) {
               message += ", originalAirDate=" + s.data.get("originalAirDate");
            }

            if (s.data.containsKey("movieYear")) {
               message += ", movieYear=" + s.data.get("movieYear");
            }
            
            if (description != null) {
               message += "\n" + description;
            }
      
            log.warn("\n" + s.data.get("title"));
            log.print(message);
         }
      }
   }
   
   // Now Playing mode get selection data at given row
   public Hashtable<String,String> NowPlayingGetSelectionData(int row) {
      debug.print("row=" + row);
      // Get column items for selected row 
      if (row < 0) {
         log.error("Nothing selected");
         return null;
      }
      sortableDate s = (sortableDate)NowPlaying.getValueAt(row, getColumnIndex("DATE"));
      if (s.folder) {
         log.warn("Cannot process a folder entry");
         return null;
      }
      return s.data;
   }
   
   // FILES mode get selection data
   // Return full path file name of selected row
   public String NowPlayingGetSelectionFile(int row) {
      debug.print("row=" + row);
      if (row < 0) {
         log.error("Nothing selected");
         return null;
      }
      String s = java.io.File.separator;
      String fileName = (String)NowPlaying.getValueAt(row, getColumnIndex("FILE"));
      String dirName = (String)NowPlaying.getValueAt(row, getColumnIndex("DIR"));
      String fullName = dirName + s + fileName;
      return fullName;
   }
   
   // This is for non FILES tables
   public Stack<Hashtable<String,String>> getRowData(int row) {
      Stack<Hashtable<String,String>> data = new Stack<Hashtable<String,String>>();
      if (row < 0) return data;
      sortableDate s = (sortableDate)NowPlaying.getValueAt(row, getColumnIndex("DATE"));
      if (s.folder) {
         for (int i=0; i<s.folderData.size(); ++i)
            data.add(s.folderData.get(i));
      } else {
         data.add(s.data);
      }
      return data;
   }

   // Update table to display NowPlaying entries
   // (Called only when NowPlaying task completes)
   public void SetNowPlaying(Stack<Hashtable<String,String>> h) {
      debug.print("h=" + h);
      
      // Update lastUpdated since this is a brand new listing
      lastUpdated = " (Last updated: " + getStatusTime(new Date().getTime()) + ")";
      
      // Reset local entries/folders hashes to new entries
      entries = h;
      folderize(h); // populates folders hash
      
      // Update table listings
      RefreshNowPlaying(entries);
            
      // Adjust column widths to data
      packColumns(NowPlaying, 2);
   }
   
   // Refresh table with given NowPlaying entries
   // (This can be flat top level display, top level folder display or individual folder item display)
   public void RefreshNowPlaying(Stack<Hashtable<String,String>> h) {
      debug.print("h=" + h);
      if (h == null) h = entries;
      if (h == null) return;
      String message = "";
      if (NowPlaying != null) {
         if ( ! showFolders() ) {
            // Not showing folders
            message = displayFlatStructure(h);
         } else {
            // Folder based structure
            if (inFolder) {
               // Display a particular folder
               message = displayFlatStructure(h);
            } else {
               // Top level folder structure
               displayFolderStructure();
               message = getTotalsString(h);
            }
         }
      }
      
      // Display totals message
      displayTotals(message); 
      
      // Identify NPL table items associated with queued/running jobs
      jobMonitor.updateNPLjobStatus();
   }
   
   // Update table display to show either top level flat structure or inside a folder
   public String displayFlatStructure(Stack<Hashtable<String,String>> h) {
      debug.print("h=" + h);
      String message;
      clear();
      Hashtable<String,String> entry;
      for (int i=0; i<h.size(); ++i) {
         entry = h.get(i);
         if ( ! shouldHideEntry(entry) )
            AddNowPlayingRow(entry);
      }
      
      // Return message indicating size totals of displayed items
      message = getTotalsString(h);
      if (inFolder) {
         message = "'" + folderName + "' " + message;
      }
      return message;
   }
   
   // Update table display to show top level folderized NPL entries
   public void displayFolderStructure() {
      debug.print("");
      clear();
      //String[] special = {"TiVo Suggestions", "HD Channels"};
      String[] special = {"TiVo Suggestions"};
      // Folder based structure
      int size;
      String name;
      Hashtable<String,String> entry;
      // Add all folders except suggestions which are saved for last
      for (int i=0; i<sortedOrder.size(); ++i) {
         name = sortedOrder.get(i).get("__folderName__");
         if (! matches(name, special) ) {
            size = folders.get(name).size();
            if (size > 1) {
               // Display as a folder
               AddNowPlayingRow(name, folders.get(name));
            } else {
               // Single entry
               entry = folders.get(name).get(0);
               if ( ! shouldHideEntry(entry) )
                  AddNowPlayingRow(entry);
            }
         }
      }
      for (int i=0; i<special.length; i++) {
         if (folders.containsKey(special[i])) {
            AddNowPlayingRow(special[i], folders.get(special[i]));
         }
      }
   }
   
   // Simple string matching to string array
   public static Boolean matches(String test, String[] array) {
      for (int i=0; i<array.length; ++i) {
         if (test.matches(array[i])) return true;
      }
      return false;
   }
   
   // Compute total size and duration of all given items and return as a string
   private String getTotalsString(Stack<Hashtable<String,String>> h) {
      debug.print("h=" + h);
      // If limiting NPL fetches then no message
      if (config.getLimitNplSetting(tivoName) > 0) {
         return "";
      }
      String message;
      long totalSize = 0;
      //long totalSecs = 0;
      Hashtable<String,String> entry;
      for (int i=0; i<h.size(); ++i) {
         entry = h.get(i);
         if (entry.containsKey("size")) totalSize += Long.parseLong(entry.get("size"));
         //if (entry.containsKey("duration")) totalSecs += Long.parseLong(entry.get("duration"))/1000;
      }
      message = String.format(
         "%d SHOWS, %.0f GB USED",
         h.size(), totalSize/Math.pow(2,30)
      );
      if (! inFolder && config.diskSpace.containsKey(tivoName)) {
         float disk = config.diskSpace.get(tivoName);
         Double free = disk - totalSize/Math.pow(2,30);
         if (free < 0.0) free = 0.0;
         message += String.format(", %.0f GB FREE", free);
      }
      return message;
   }
   
   // Display size/duration totals
   private void displayTotals(String message) {
      debug.print("message=" + message);
      log.warn(message);
      if (config.GUIMODE) {
         // NOTE: tivoName surrounded by \Q..\E to escape any special regex chars
         String status = message.replaceFirst("\\Q"+tivoName+"\\E", "");
         status += lastUpdated;
         config.gui.nplTab_UpdateStatus(tivoName, status);
      }
   }
   
   // Create data structure to organize NPL in folder format
   @SuppressWarnings("unchecked")
   private void folderize(Stack<Hashtable<String,String>> entries) {
      debug.print("entries=" + entries);
      folders = new Hashtable<String,Stack<Hashtable<String,String>>>();
      String name;
      Boolean suggestion;
      for (int i=0; i<entries.size(); i++) {
         suggestion = false;
         // Categorize by suggestions
         if (entries.get(i).containsKey("suggestion")) {
            suggestion = true;
            name = "TiVo Suggestions";
            if ( ! folders.containsKey(name) ) {
               // Init new stack
               Stack<Hashtable<String,String>> stack = new Stack<Hashtable<String,String>>();
               folders.put(name, stack);
            }
            folders.get(name).add(entries.get(i));
         }
         
         // Categorize by titleOnly (not including suggestions)
         if (!suggestion && entries.get(i).containsKey("titleOnly")) {
            name = entries.get(i).get("titleOnly");
            if ( ! folders.containsKey(name) ) {
               // Init new stack
               Stack<Hashtable<String,String>> stack = new Stack<Hashtable<String,String>>();
               folders.put(name, stack);
            }
            folders.get(name).add(entries.get(i));
         }
         
         // Categorize by HD channels (includes suggestions)
         /*if (entries.get(i).containsKey("HD")) {
            if (entries.get(i).get("HD").equals("Yes")) {
               name = "HD Channels";
               if ( ! folders.containsKey(name) ) {
                  // Init new stack
                  Stack<Hashtable<String,String>> stack = new Stack<Hashtable<String,String>>();
                  folders.put(name, stack);
               }
               folders.get(name).add(entries.get(i));
            }
         }*/
      }
      
      // Define default sort order for all folder entries
      // Sort by largest gmt first except put Suggestions last
      Comparator<Hashtable<String,String>> folderSort = new Comparator<Hashtable<String,String>>() {
         public int compare(Hashtable<String,String> o1, Hashtable<String,String> o2) {
            long gmt1 = Long.parseLong(o1.get("gmt"));
            long gmt2 = Long.parseLong(o2.get("gmt"));
            if (gmt1 < gmt2) return 1;
            if (gmt1 > gmt2) return -1;
            return 0;
         }
      };      
      Hashtable<String,String> entry;
      sortedOrder = new Vector<Hashtable<String,String>>();
      for (Enumeration<String> e=folders.keys(); e.hasMoreElements();) {
         name = e.nextElement();
         entry = (Hashtable<String, String>) folders.get(name).get(0).clone();
         if ( ! shouldHideEntry(entry) ) {
            entry.put("__folderName__", name);
            sortedOrder.add(entry);
         }
      }
      Collections.sort(sortedOrder, folderSort);
   }
      
   // Convert seconds to mins
   private long secsToMins(Long secs) {
      debug.print("secs=" + secs);
      long mins = secs/60;
      if (mins > 0) {
         secs -= mins*60;
      }
      // Round mins +1 if secs > 30
      if (secs > 30) {
         mins += 1;
      }
      return mins;
   }   
   // Add a now playing non folder entry to NowPlaying table
   public void AddNowPlayingRow(Hashtable<String,String> entry) {
      debug.print("entry=" + entry);
      int cols = TIVO_cols.length;
      Object[] data = new Object[cols];
      // Initialize to empty strings
      for (int i=0; i<cols; ++i) {
         data[i] = "";
      }
      if ( entry.containsKey("ExpirationImage") ) {
         data[0] = gui.Images.get(entry.get("ExpirationImage"));
      }
      data[1] = new sortableShow(entry);
      data[2] = new sortableDate(entry);
      String channel = "";
      if ( entry.containsKey("channelNum") ) {
         channel = " " + entry.get("channelNum");
      }
      if ( entry.containsKey("channel") ) {
         channel += "=" + entry.get("channel"); 
      }
      if (channel.length() > 0) channel += " ";
      data[3] = channel;
      data[4] = new sortableDuration(entry);
      data[5] = new sortableSize(entry);
      Double rate = 0.0;
      if (entry.containsKey("size") && entry.containsKey("duration")) {
         rate = bitRate(entry.get("size"), entry.get("duration"));
      }
      data[6] = new sortableDouble(rate);
      AddRow(NowPlaying, data);
      
      // Adjust column widths to data
      packColumns(NowPlaying, 2);

   }   

   // Add a now playing folder entry to NowPlaying table
   public void AddNowPlayingRow(String fName, Stack<Hashtable<String,String>> folderEntry) {
      debug.print("folderEntry=" + folderEntry);
      int cols = TIVO_cols.length;
      Object[] data = new Object[cols];
      // Initialize to empty strings
      for (int i=0; i<cols; ++i) {
         data[i] = "";
      }
      // Put folder icon as entry 0
      data[0] = gui.Images.get("folder");
      
      // For date, find most recent recording
      // For channel see if they are all from same channel
      String channel = "";
      if (folderEntry.get(0).containsKey("channel")) {
         channel = folderEntry.get(0).get("channel");
      }
      Boolean sameChannel = true;
      Double rate_total = 0.0;
      Double rate;
      long gmt, largestGmt=0;
      int gmt_index=0;
      for (int i=0; i<folderEntry.size(); ++i) {
         gmt = Long.parseLong(folderEntry.get(i).get("gmt"));
         if (gmt > largestGmt) {
            largestGmt = gmt;
            gmt_index = i;
         }
         if (folderEntry.get(i).containsKey("channel")) {
            if ( ! folderEntry.get(i).get("channel").equals(channel) ) {
               sameChannel = false;
            }
         }
         rate = 0.0;
         if (folderEntry.get(i).containsKey("size") && folderEntry.get(i).containsKey("duration")) {
            rate = bitRate(folderEntry.get(i).get("size"), folderEntry.get(i).get("duration"));
         }
         rate_total += rate;
      }
      if (folderEntry.size() > 0) {
         rate_total /= folderEntry.size();
      }
      data[1] = new sortableShow(fName, folderEntry, gmt_index);
      data[2] = new sortableDate(fName, folderEntry, gmt_index);
      
      if (sameChannel) {
         if ( folderEntry.get(0).containsKey("channelNum") ) {
            channel = folderEntry.get(0).get("channelNum");
         }
         if ( folderEntry.get(0).containsKey("channel") ) {
            channel += "=" + folderEntry.get(0).get("channel"); 
         }
      } else {
         channel = "<various>";
      }
      data[3] = " " + channel + " ";
      
      data[4] = new sortableDuration(folderEntry);
      data[5] = new sortableSize(folderEntry);
      data[6] = new sortableDouble(rate_total);
      AddRow(NowPlaying, data);
      
      // Adjust column widths to data
      packColumns(NowPlaying, 2);
   }
 
   // Add a selected file in FILES mode to NowPlaying table
   public void AddNowPlayingFileRow(File file) {
      debug.print("file=" + file);
      int cols = FILE_cols.length;
      Object[] data = new Object[cols];
      String fileName = file.getName();
      String baseDir = file.getParentFile().getPath();
      long size = file.length();
      
      data[0] = fileName;
      Hashtable<String,String> h = new Hashtable<String,String>();
      h.put("size", "" + size);
      double GB = Math.pow(2,30);
      h.put("sizeGB", String.format("%.2f GB", (double)size/GB));
      data[1] = new sortableSize(h);
      data[2] = baseDir;
      AddRow(NowPlaying, data);
      
      // Adjust column widths to data
      packColumns(NowPlaying, 2);
   }

   public void RemoveSelectedRow(int row) {
      debug.print("row=" + row);
      if (row < 0) {
         log.error("Nothing selected");
         return;
      }
      RemoveRow(NowPlaying, row);

   }
   
   public void RemoveRow(JXTable table, int row) {
      debug.print("table=" + table + " row=" + row);
      DefaultTableModel dm = (DefaultTableModel)table.getModel();
      dm.removeRow(table.convertRowIndexToModel(row));
   }
   
   public void RemoveRows(JXTable table, Stack<Integer> rows) {
      debug.print("table=" + table + " rows=" + rows);
      DefaultTableModel dm = (DefaultTableModel)table.getModel();
      // Must remove by highest index first
      int row, index;
      while(rows.size() > 0) {
         row = -1;
         index = -1;
         for (int i=0; i<rows.size(); ++i) {
            if(rows.get(i) > row) {
               row = rows.get(i);
               index = i;
            }
         }
         if (index > -1) {
            dm.removeRow(table.convertRowIndexToModel(row));
            rows.remove(index);
         }
      }
   }
      
   public void SetNowPlayingHeaders(String[] headers) {
      debug.print("headers=" + headers);
      for (int i=0; i<headers.length; ++i) {
         SetHeaderText(NowPlaying, headers[i], i);
      }
      packColumns(NowPlaying,2);
   }
   
   public void clear() {
      debug.print("");
      DefaultTableModel model = (DefaultTableModel)NowPlaying.getModel(); 
      model.setNumRows(0);
   }
   
   public void AddRow(JXTable table, Object[] data) {
      debug.print("data=" + data);
      DefaultTableModel dm = (DefaultTableModel)table.getModel();
      dm.addRow(data);
   }
   
   // Refresh all titles currently displayed in table for non-folder entries
   public void refreshTitles() {
      for (int row=0; row<NowPlaying.getRowCount(); ++row) {
         sortableDate s = (sortableDate)NowPlaying.getValueAt(row,getColumnIndex("DATE"));
         if (! s.folder && s.data != null)
            NowPlaying.setValueAt(new sortableShow(s.data), row, getColumnIndex("SHOW"));
      }
   }

   // Look for entry with given folder name and select it
   // (This used when returning back from folder mode to top level mode)
   public void SelectFolder(String folderName) {
      debug.print("folderName=" + folderName);
      for (int i=0; i<NowPlaying.getRowCount(); ++i) {
         sortableDate s = (sortableDate)NowPlaying.getValueAt(i,getColumnIndex("DATE"));
         if (s.folder) {
            if (s.folderName.equals(folderName)) {
               NowPlaying.clearSelection();
               try {
                  NowPlaying.setRowSelectionInterval(i,i);
                  NowPlaying.scrollRectToVisible(NowPlaying.getCellRect(i, 0, true));
               }
               catch (Exception e) {
                  // This is here because JXTable seems to have a problem sometimes after table cleared
                  // and an item is selected. This prevents nasty stack trace problem from being
                  // printed to message window
                  System.out.println("Exception: " + e.getMessage());
               }
               return;
            }
         }
      }
   }
   
   private void selectRowWithData(Hashtable<String,String> data) {
      if (data.containsKey("folderName"))
         SelectFolder(data.get("folderName"));
      else {
         // Step through all table rows looking for entry with matching ProgramId to select
         if (data.containsKey("ProgramId")) {
            for (int i=0; i<NowPlaying.getRowCount(); ++i) {
               sortableDate r = (sortableDate)NowPlaying.getValueAt(i, getColumnIndex("DATE"));
               if (r.folder) {
                  // Inside a folder so search all folder entries
                  for (int j=0; j<r.folderData.size(); j++) {
                     Hashtable<String,String> entry = r.folderData.get(j);
                     if (entry.containsKey("ProgramId")) {
                        if (entry.get("ProgramId").equals(data.get("ProgramId"))) {
                           setFolderState(true);
                           folderName = r.folderName;
                           folderEntryNum = i;
                           RefreshNowPlaying(r.folderData);
                           NowPlaying.setRowSelectionInterval(j, j);
                        }
                     }
                  }
               } else {
                  if (r.data.containsKey("ProgramId")) {
                     if (r.data.get("ProgramId").equals(data.get("ProgramId")))
                        NowPlaying.setRowSelectionInterval(i, i);
                  }
               }
            }
         }
      }
   }
   
   public void SetHeaderText(JXTable table, String text, int col) {
      debug.print("table=" + table + " text=" + text + " col=" + col);
      table.getColumnModel().getColumn(col).setHeaderValue(text);
   }

   // Pack all table columns to fit widest cell element
   public void packColumns(JXTable table, int margin) {
      debug.print("table=" + table + " margin=" + margin);
      if (config.tableColAutoSize == 1) {
         for (int c=0; c<table.getColumnCount(); c++) {
             packColumn(table, c, 2);
         }
      }
   }
   
   @SuppressWarnings("unchecked")
   private void RemoveUrls(Stack<String> urls) {
      // First update table
      Stack<Hashtable<String,String>> copy = (Stack<Hashtable<String, String>>) entries.clone();
      entries.clear();
      Boolean include;
      for (int i=0; i<copy.size(); ++i) {
         include = true;
         String url;
         if (copy.get(i).containsKey("url")) {
            for (int j=0; j<urls.size(); ++j) {
               url = urls.get(j);
               if (copy.get(i).get("url").equals(url)) {
                  include = false;
               }
            }
         }
         if (include) {
            entries.add(copy.get(i));
         }
      }
      
      // TWP delete calls
      for (int i=0; i<urls.size(); ++i) {
         file.TivoWebPlusDelete(urls.get(i));
      }
   }
   
   @SuppressWarnings("unchecked")
   private void RemoveIds(Stack<String> urls, Stack<String> ids) {
      // First update table
      Stack<Hashtable<String,String>> copy = (Stack<Hashtable<String, String>>) entries.clone();
      entries.clear();
      Boolean include;
      for (int i=0; i<copy.size(); ++i) {
         include = true;
         String url;
         if (copy.get(i).containsKey("url")) {
            for (int j=0; j<urls.size(); ++j) {
               url = urls.get(j);
               if (copy.get(i).get("url").equals(url)) {
                  include = false;
               }
            }
         }
         if (include) {
            entries.add(copy.get(i));
         }
      }
      
      // Remote delete calls
      JSONArray a = new JSONArray();
      JSONObject json = new JSONObject();
      for (int i=0; i<ids.size(); ++i) {
         a.put(ids.get(i));
      }
      try {
         json.put("recordingId", a);
         Remote r = config.initRemote(tivoName);
         if (r.success) {
            r.Command("Delete", json);
            r.disconnect();
         }
      } catch (JSONException e) {
         log.print("RemoveIds failed - " + e.getMessage());
      }
   }
   
   private Boolean StopRecording(String id) {
      Boolean deleted = false;
      JSONObject json = new JSONObject();
      try {
         json.put("recordingId", id);
         Remote r = config.initRemote(tivoName);
         if (r.success) {
            JSONObject result = r.Command("StopRecording", json);
            if(result != null && result.has("type") && result.getString("type").equals("success"))
               deleted = true;
            r.disconnect();
         }
      } catch (JSONException e) {
         log.print("StopRecording failed - " + e.getMessage());
      }
      return deleted;
   }
   
   private void PlayShow(String id) {
      JSONObject json = new JSONObject();
      try {
         json.put("id", id);
         Remote r = config.initRemote(tivoName);
         if (r.success) {
            r.Command("Playback", json);
            r.disconnect();
         }
      } catch (JSONException e) {
         log.print("PlayShow failed - " + e.getMessage());
      }      
   }
   
   // Sets the preferred width of the visible column specified by vColIndex. The column
   // will be just wide enough to show the column head and the widest cell in the column.
   // margin pixels are added to the left and right
   // (resulting in an additional width of 2*margin pixels).
   public void packColumn(JXTable table, int vColIndex, int margin) {
      debug.print("table=" + table + " vColIndex=" + vColIndex + " margin=" + margin);
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
       
       // Adjust last columns (FILES) or SHOW column (Tivos) to fit available space
       int last;
       if (tivoName.equals("FILES")) {
          last = table.getColumnCount()-1;
       } else {
          last = getColumnIndex("SHOW");
       }
       if (vColIndex == last) {
          int twidth = table.getPreferredSize().width;
          int awidth = config.gui.getJFrame().getWidth();
          int offset = 3*nplScroll.getVerticalScrollBar().getPreferredSize().width+2*margin;
          if ((awidth-offset) > twidth) {
             width += awidth-offset-twidth;
             col.setPreferredWidth(width);
          }
       }
   }
   
   // Compute and return all table column widths as an integer array
   public int[] getColWidths() {
      int[] widths = new int[NowPlaying.getColumnCount()];
      DefaultTableColumnModel colModel = (DefaultTableColumnModel)NowPlaying.getColumnModel();
      for (int i=0; i<widths.length; ++i) {
         TableColumn col = colModel.getColumn(i);
         widths[i] = col.getWidth();
      }
      return widths;
   }
   
   // Compute and return all table column widths as an integer array
   public void setColWidths(int[] widths) {
      if (widths.length != NowPlaying.getColumnCount()) {
         return;
      }
      DefaultTableColumnModel colModel = (DefaultTableColumnModel)NowPlaying.getColumnModel();
      for (int i=0; i<widths.length; ++i) {
         TableColumn col = colModel.getColumn(i);
         col.setPreferredWidth(widths[i]);
      }
   }
   
   private String getStatusTime(long gmt) {
      debug.print("gmt=" + gmt);
      SimpleDateFormat sdf = new SimpleDateFormat("hh:mm aa");
      return sdf.format(gmt);
   }
   
   public Stack<Hashtable<String,String>> getEntries() {
      return entries;
   }
   
   public static Double bitRate(String size, String duration) {
      Double rate = 0.0;
      try {
         Double bytes = Double.parseDouble(size);
         Double secs = Double.parseDouble(duration)/1000;
         rate = (bytes*8)/(1e6*secs);
      }
      catch (Exception ex) {
         log.error(ex.getMessage());
         rate = 0.0;
      }
      return rate;
   }
      
   // Return true if this entry should not be displayed, false otherwise
   private Boolean shouldHideEntry(Hashtable<String,String> entry) {
      return config.HideProtectedFiles == 1 && entry.containsKey("CopyProtected");
   }
   
   // Identify NPL table items associated with queued/running jobs
   public void updateNPLjobStatus(Hashtable<String,String> map) {
      UpdatingNPL = true;
      for (int row=0; row<NowPlaying.getRowCount(); row++) {
         sortableDate s = (sortableDate)NowPlaying.getValueAt(row,getColumnIndex("DATE"));
         if (s != null && s.data != null) {
            if (s.data.containsKey("url_TiVoVideoDetails")) {
               String source = s.data.get("url_TiVoVideoDetails");
               if (map.containsKey(source)) {
                  // Has associated queued or running job, so set special icon
                  NowPlaying.setValueAt(gui.Images.get(map.get(source)), row, getColumnIndex(""));
               } else {
                  // Has no associated queued or running job so reset icon
                  NowPlaying.setValueAt(null, row, getColumnIndex(""));
                  
                  // Set to ExpirationImage icon if available
                  if ( s.data.containsKey("ExpirationImage") ) {
                     NowPlaying.setValueAt(gui.Images.get(s.data.get("ExpirationImage")), row, getColumnIndex(""));
                  }
               }
            }
         }
      }
      UpdatingNPL = false;
   }
}
