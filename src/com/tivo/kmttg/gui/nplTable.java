package com.tivo.kmttg.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
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
import org.jdesktop.swingx.JXTable;
import org.jdesktop.swingx.decorator.Sorter;

import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.*;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class nplTable {
   public String tivoName = null;
   public JXTable NowPlaying = null;
   public JScrollPane nplScroll = null;
   public String[] FILE_cols = {"FILE", "SIZE", "DIR"};
   public String[] TIVO_cols = {"", "DATE", "CHANNEL", "SIZE", "SHOW"};
   public Boolean inFolder = false;
   public String folderName = null;
   public int folderEntryNum = -1;
   private Stack<Hashtable<String,String>> entries = null;
   private Hashtable<String,Stack<Hashtable<String,String>>> folders = null;
   private Vector<Hashtable<String,String>> sortedOrder = null;
   private String lastUpdated = null;
         
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
      
      // Add listener for double-click handling (for folder entries)
      NowPlaying.addMouseListener(
         new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
               MouseClicked(e);
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
               // Sort 1st by titleOnly, then by date
               sortableShow s1 = (sortableShow)o1;
               sortableShow s2 = (sortableShow)o2;
               int result = s1.titleOnly.compareToIgnoreCase(s2.titleOnly);
               if (result != 0) return result;
               if (s1.gmt > s2.gmt) return 1;
               if (s1.gmt < s2.gmt) return -1;
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
         sorter = NowPlaying.getColumnExt(3).getSorter();
         sorter.setComparator(sortableComparator);
         sorter = NowPlaying.getColumnExt(4).getSorter();
         sorter.setComparator(sortableComparator);
      }      
      
      // Define selection listener to update dialog fields according
      // to selected row
      NowPlaying.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
         public void valueChanged(ListSelectionEvent e) {
            if (e.getValueIsAdjusting()) return;
            NowPlayingRowSelected(NowPlaying.getSelectedRow());
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
         // Right justify dates
         ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.RIGHT);
         
         tm = NowPlaying.getColumnModel().getColumn(2);
         tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));
         
         tm = NowPlaying.getColumnModel().getColumn(3);
         tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndDarker, config.tableFont));
         // Right justify file size
         ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.RIGHT);
         
         tm = NowPlaying.getColumnModel().getColumn(4);
         tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));
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
            return sortableDate.class;
         }
         if (col == 3) {
            return sortableSize.class;
         }
         if (col == 4) {
            return sortableShow.class;
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
               if ( ! d.folder && d.data.containsKey("CopyProtected") ) {
                  cell.setBackground( config.tableBkgndProtected );
               } else if ( ! d.folder && d.data.containsKey("ExpirationImage") &&
                          d.data.get("ExpirationImage").equals("in-progress-recording")) {
                  cell.setBackground( config.tableBkgndRecording );
               } else {
                  if (column % 2 == 0)
                     cell.setBackground(config.tableBkgndLight);
                  else
                     cell.setBackground(config.tableBkgndDarker);
               }
            } else {
               // FILES mode
               if (column % 2 == 0)
                  cell.setBackground(config.tableBkgndLight);
               else
                  cell.setBackground(config.tableBkgndDarker);
            }
         }
         
         cell.setFont(font);
        
         return cell;
      }
   }   
   
   // Mouse event handler
   // This will display folder entries in table if folder entry single-clicked
   private void MouseClicked(MouseEvent e) {
      if( ! tivoName.equals("FILES") && e.getClickCount() == 1 ) {
         int row = NowPlaying.rowAtPoint(e.getPoint());
         sortableDate s = (sortableDate)NowPlaying.getValueAt(row,getColumnIndex("DATE"));
         if (s.folder) {
            setFolderState(true);
            folderName = s.folderName;
            folderEntryNum = row;
            RefreshNowPlaying(s.folderData);
         }
      }
   }
   
   public void setFolderState(Boolean state) {
      if (state) {
         inFolder = true;
         config.gui.getTab(tivoName).showFoldersVisible(false);
         config.gui.getTab(tivoName).getRefreshButton().setText("Return");
         config.gui.getTab(tivoName).getRefreshButton().setToolTipText(config.gui.getToolTip("return"));
      } else {
         inFolder = false;
         config.gui.getTab(tivoName).showFoldersVisible(true);
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
         config.gui.text_error("No rows selected");
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
               channelNum = s.data.get("channel");
            }
            String description = null;
            if ( s.data.containsKey("description") ) {
               description = s.data.get("description");
            }
            int duration = Integer.parseInt(s.data.get("duration"));
            String d = String.format("%d mins", duration/(1000*60));
            String message = "Recorded " + t;
            if (channelNum != null && channel != null) {
               message += " on " + channelNum + "=" + channel;
            }
            message += ", Duration = " + d;
            
            if (description != null) {
               message += "\n" + description;
            }
      
            config.gui.text_warn("\n" + s.data.get("title"));
            config.gui.text_print(message);
         }
      }
   }
   
   // Now Playing mode get selection data at given row
   public Hashtable<String,String> NowPlayingGetSelectionData(int row) {
      debug.print("row=" + row);
      // Get column items for selected row 
      if (row < 0) {
         config.gui.text_error("Nothing selected");
         return null;
      }
      sortableDate s = (sortableDate)NowPlaying.getValueAt(row, getColumnIndex("DATE"));
      if (s.folder) {
         config.gui.text_warn("Cannot process a folder entry");
         return null;
      }
      return s.data;
   }
   
   // FILES mode get selection data
   // Return full path file name of selected row
   public String NowPlayingGetSelectionFile(int row) {
      debug.print("row=" + row);
      if (row < 0) {
         config.gui.text_error("Nothing selected");
         return null;
      }
      String s = java.io.File.separator;
      String fileName = (String)NowPlaying.getValueAt(row, getColumnIndex("FILE"));
      String dirName = (String)NowPlaying.getValueAt(row, getColumnIndex("DIR"));
      String fullName = dirName + s + fileName;
      return fullName;
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
   }
   
   // Refresh table with given NowPlaying entries
   // (This can be flat top level display, top level folder display or individual folder item display)
   public void RefreshNowPlaying(Stack<Hashtable<String,String>> h) {
      debug.print("h=" + h);
      if (h == null) h = entries;
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
      clear(NowPlaying);
      Hashtable<String,String> entry;
      for (int i=0; i<h.size(); ++i) {
         entry = h.get(i);
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
      clear(NowPlaying);
      String[] special = {"TiVo Suggestions", "HD Channels"};
      // Folder based structure
      int size;
      String name;
      Hashtable<String,String> entry;
      // Add all folders except suggestions & HD channels which are saved for last
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
      String message;
      long totalSize = 0;
      long totalSecs = 0;
      Hashtable<String,String> entry;
      for (int i=0; i<h.size(); ++i) {
         entry = h.get(i);
         if (entry.containsKey("size")) totalSize += Long.parseLong(entry.get("size"));
         if (entry.containsKey("duration")) totalSecs += Long.parseLong(entry.get("duration"))/1000;
      }
      message = String.format(
         "TOTALS: %d shows, %.2f GB, %s total time\n",
         h.size(), totalSize/Math.pow(2,30), secsToHoursMins(totalSecs)
      );
      return message;
   }
   
   // Display size/duration totals
   private void displayTotals(String message) {
      debug.print("message=" + message);
      log.warn(message);
      if (config.GUI) {
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
         if (entries.get(i).containsKey("HD")) {
            if (entries.get(i).get("HD").equals("Yes")) {
               name = "HD Channels";
               if ( ! folders.containsKey(name) ) {
                  // Init new stack
                  Stack<Hashtable<String,String>> stack = new Stack<Hashtable<String,String>>();
                  folders.put(name, stack);
               }
               folders.get(name).add(entries.get(i));
            }
         }
      }
      
      // Define default sort order for all folder entries
      // Sort by largest gmt first except put Suggestions & HD Channels last
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
         entry.put("__folderName__", name);
         sortedOrder.add(entry);
      }
      Collections.sort(sortedOrder, folderSort);
   }
   
   // Convert seconds to hours:mins
   private String secsToHoursMins(Long secs) {
      debug.print("secs=" + secs);
      Long hours = secs/3600;
      Long mins  = secs/60 - hours*60;
      return String.format("%02d:%02d", hours, mins);
   }  
   
   // Add a now playing non folder entry to NowPlaying table
   public void AddNowPlayingRow(Hashtable<String,String> entry) {
      debug.print("entry=" + entry);
      int cols = 5;
      Object[] data = new Object[cols];
      // Initialize to empty strings
      for (int i=0; i<cols; ++i) {
         data[i] = "";
      }
      if ( entry.containsKey("ExpirationImage") ) {
         data[0] = gui.Images.get(entry.get("ExpirationImage"));
      }
      data[1] = new sortableDate(entry);
      String channel = "";
      if ( entry.containsKey("channelNum") ) {
         channel = entry.get("channelNum");
      }
      if ( entry.containsKey("channel") ) {
         channel += "=" + entry.get("channel"); 
      }
      data[2] = channel;
      data[3] = new sortableSize(entry);
      data[4] = new sortableShow(entry);
      AddRow(NowPlaying, data);
      
      // Adjust column widths to data
      packColumns(NowPlaying, 2);

   }   

   // Add a now playing folder entry to NowPlaying table
   public void AddNowPlayingRow(String fName, Stack<Hashtable<String,String>> folderEntry) {
      debug.print("folderEntry=" + folderEntry);
      int cols = 5;
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
      }
      data[1] = new sortableDate(fName, folderEntry, gmt_index);
      
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
      data[2] = channel;
      
      data[3] = new sortableSize(folderEntry);
      data[4] = new sortableShow(fName, folderEntry, gmt_index);
      AddRow(NowPlaying, data);
      
      // Adjust column widths to data
      packColumns(NowPlaying, 2);
   }
 
   // Add a selected file in FILES mode to NowPlaying table
   public void AddNowPlayingFileRow(File file) {
      debug.print("file=" + file);
      int cols = 3;
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
         config.gui.text_error("Nothing selected");
         return;
      }
      RemoveRow(NowPlaying, row);

   }
   public void RemoveRow(JXTable table, int row) {
      debug.print("table=" + table + " row=" + row);
      DefaultTableModel dm = (DefaultTableModel)table.getModel();
      dm.removeRow(row);
   }
   
   public void SetNowPlayingHeaders(String[] headers) {
      debug.print("headers=" + headers);
      for (int i=0; i<headers.length; ++i) {
         SetHeaderText(NowPlaying, headers[i], i);
      }
      packColumns(NowPlaying,2);
   }
   
   public void clear(JXTable table) {
      debug.print("table=" + table);
      DefaultTableModel model = (DefaultTableModel)table.getModel(); 
      model.setNumRows(0);
   }
   
   public void AddRow(JXTable table, Object[] data) {
      debug.print("data=" + data);
      DefaultTableModel dm = (DefaultTableModel)table.getModel();
      dm.addRow(data);
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
   
   public void SetHeaderText(JXTable table, String text, int col) {
      debug.print("table=" + table + " text=" + text + " col=" + col);
      table.getColumnModel().getColumn(col).setHeaderValue(text);
   }

   // Pack all table columns to fit widest cell element
   public void packColumns(JXTable table, int margin) {
      debug.print("table=" + table + " margin=" + margin);
      for (int c=0; c<table.getColumnCount(); c++) {
          packColumn(table, c, 2);
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
       
       // Adjust last column to fit available
       int last = table.getColumnCount()-1;
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
   
   private String getStatusTime(long gmt) {
      debug.print("gmt=" + gmt);
      SimpleDateFormat sdf = new SimpleDateFormat("hh:mm aa");
      return sdf.format(gmt);
   }
   
   public Stack<Hashtable<String,String>> getEntries() {
      return entries;
   }
   
   // Identify NPL table items associated with queued/running jobs
   public void updateNPLjobStatus(Hashtable<String,String> map) {
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
   }

}
