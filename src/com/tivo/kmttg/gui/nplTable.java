package com.tivo.kmttg.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.Hashtable;
import java.util.Stack;

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
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class nplTable {
   public String tivoName = null;
   public JXTable NowPlaying = null;
   public JScrollPane nplScroll = null;
   public String[] FILE_cols = {"", "FILE", "SIZE",    "DIR",  ""};
   public String[] TIVO_cols = {"", "DATE", "CHANNEL", "SIZE", "SHOW"};
      
   nplTable(String tivoName) {
      this.tivoName = tivoName;
      Object[][] data = {};        
      NowPlaying = new JXTable(data, FILE_cols);
      nplScroll = new JScrollPane(NowPlaying);

      TableModel myModel = new MyTableModel(data, FILE_cols);
      NowPlaying.setModel(myModel);
      
      // Allow icons in column 0
      NowPlaying.setDefaultRenderer(Icon.class, new IconCellRenderer());
      
      // Define custom column sorting routines for columns 1 & 3
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
      
      // Use custom sorting routine for columns 1, 3 & 4
      Sorter sorter = NowPlaying.getColumnExt(1).getSorter();
      sorter.setComparator(sortableComparator);
      sorter = NowPlaying.getColumnExt(3).getSorter();
      sorter.setComparator(sortableComparator);
      sorter = NowPlaying.getColumnExt(4).getSorter();
      sorter.setComparator(sortableComparator);
      
      
      // Define selection listener to update dialog fields according
      // to selected row
      NowPlaying.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
         public void valueChanged(ListSelectionEvent e) {
            NowPlayingRowSelected(NowPlaying.getSelectedRow());
         }
      });
                  
      // Change color & font
      TableColumn tm;
      tm = NowPlaying.getColumnModel().getColumn(1);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndDarker, config.tableFont));
      // Right justify dates for Tivo tables only
      if (! tivoName.equals("FILES"))
         ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.RIGHT);
      tm = NowPlaying.getColumnModel().getColumn(2);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));
      tm = NowPlaying.getColumnModel().getColumn(3);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndDarker, config.tableFont));
      // Right justify file size for Tivo tables only
      if (! tivoName.equals("FILES"))
         ((JLabel) tm.getCellRenderer()).setHorizontalAlignment(JLabel.RIGHT);
      tm = NowPlaying.getColumnModel().getColumn(4);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));
               
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
   }
   
   // Override some default table model actions
   class MyTableModel extends DefaultTableModel {
      private static final long serialVersionUID = 1L;

      public MyTableModel(Object[][] data, Object[] columnNames) {
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
            if ( table.getValueAt(row, 1) instanceof sortableDate ) {
               // Download mode
               sortableDate d = (sortableDate)table.getValueAt(row, 1);
               if (d.data.containsKey("CopyProtected")) {
                  cell.setBackground( config.tableBkgndProtected );
               } else if (d.data.containsKey("ExpirationImage") &&
                          d.data.get("ExpirationImage").equals("in-progress-recording")) {
                  cell.setBackground( config.tableBkgndRecording );
               } else {    
                  cell.setBackground( bkgndColor );
               }
            } else {
               // FILES mode
               cell.setBackground( bkgndColor );
            }
         }
         
         cell.setFont(font);
        
         return cell;
      }
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
         sortableDate s = (sortableDate)NowPlaying.getValueAt(row,1);
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
   
   // Now Playing mode get selection data at given row
   public Hashtable<String,String> NowPlayingGetSelectionData(int row) {
      debug.print("row=" + row);
      // Get column items for selected row 
      if (row < 0) {
         config.gui.text_error("Nothing selected");
         return null;
      }
      sortableDate s = (sortableDate)NowPlaying.getValueAt(row, 1);
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
      String fileName = (String)NowPlaying.getValueAt(row, 1);
      String dirName = (String)NowPlaying.getValueAt(row, 3);
      String fullName = dirName + s + fileName;
      return fullName;
   }
  
   public void SetNowPlaying(Stack<Hashtable<String,String>> h) {
      debug.print("h=" + h);
      long totalSize = 0;
      long totalSecs = 0;
      if (NowPlaying != null) {
         clear(NowPlaying);
         Hashtable<String,String> entry;
         for (int i=0; i<h.size(); ++i) {
            entry = h.get(i);
            if (entry.containsKey("size")) totalSize += Long.parseLong(entry.get("size"));
            if (entry.containsKey("duration")) totalSecs += Long.parseLong(entry.get("duration"))/1000;
            AddNowPlayingRow(entry);
         }
         String message = String.format(
            "%s TOTALS: %d shows, %.2f GB, %s total time\n",
               tivoName, h.size(), totalSize/Math.pow(2,30), secsToHoursMins(totalSecs)
         );
         log.warn(message);
         if (config.GUI) {
            // NOTE: tivoName surrounded by \Q..\E to escape any special regex chars
            String status = message.replaceFirst("\\Q"+tivoName+"\\E", "");
            status += " (Last updated: " + getStatusTime(new Date().getTime()) + ")";
            config.gui.nplTab_UpdateStatus(tivoName, status);
         }
      }
   }
   
   // Convert seconds to hours:mins
   private String secsToHoursMins(Long secs) {
      debug.print("secs=" + secs);
      Long hours = secs/3600;
      Long mins  = secs/60 - hours*60;
      return String.format("%02d:%02d", hours, mins);
   }  
   
   // Add a now playing entry to NowPlaying table
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
 
   // Add a selected file in FILES mode to NowPlaying table
   public void AddNowPlayingFileRow(File file) {
      debug.print("file=" + file);
      int cols = 4;
      Object[] data = new Object[cols];
      String fileName = file.getName();
      String baseDir = file.getParentFile().getPath();
      long size = file.length();
      
      data[0] = "";
      data[1] = fileName;
      Hashtable<String,String> h = new Hashtable<String,String>();
      h.put("size", "" + size);
      double GB = Math.pow(2,30);
      h.put("sizeGB", String.format("%.2f GB", (double)size/GB));
      data[2] = new sortableSize(h);
      data[3] = baseDir;
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
   public void RemoveRow(JTable table, int row) {
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
   
   public void clear(JTable table) {
      debug.print("table=" + table);
      TableModel model = table.getModel(); 
      int numrows = model.getRowCount(); 
      for(int i = numrows - 1; i >=0; i--)
         ((DefaultTableModel) model).removeRow(i); 
   }
   
   public void AddRow(JTable table, Object[] data) {
      debug.print("data=" + data);
      DefaultTableModel dm = (DefaultTableModel)table.getModel();
      dm.addRow(data);
   }
   
   public void SetHeaderText(JTable table, String text, int col) {
      debug.print("table=" + table + " text=" + text + " col=" + col);
      table.getColumnModel().getColumn(col).setHeaderValue(text);
   }

   // Pack all table columns to fit widest cell element
   public void packColumns(JTable table, int margin) {
      debug.print("table=" + table + " margin=" + margin);
      for (int c=0; c<table.getColumnCount(); c++) {
          packColumn(table, c, 2);
      }
   }
   
   // Sets the preferred width of the visible column specified by vColIndex. The column
   // will be just wide enough to show the column head and the widest cell in the column.
   // margin pixels are added to the left and right
   // (resulting in an additional width of 2*margin pixels).
   public void packColumn(JTable table, int vColIndex, int margin) {
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

}
