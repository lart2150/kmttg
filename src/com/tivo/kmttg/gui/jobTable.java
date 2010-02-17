package com.tivo.kmttg.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;

import org.jdesktop.swingx.JXTable;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.util.debug;

public class jobTable {
   private String[] TITLE_cols = {"STATUS", "JOB", "SOURCE", "OUTPUT"};

   public JXTable JobMonitor = null;
   
   jobTable() {
      Object[][] data = {};
      JobMonitor = new JXTable(data, TITLE_cols);
      // Disable sorting
      JobMonitor.setSortable(false);
      TableModel myModel = new MyTableModel(data, TITLE_cols);
      JobMonitor.setModel(myModel);
            
      // Change color & font
      TableColumn tm;
      tm = JobMonitor.getColumnModel().getColumn(0);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));
      tm = JobMonitor.getColumnModel().getColumn(1);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndDarker, config.tableFont));
      tm = JobMonitor.getColumnModel().getColumn(2);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndLight, config.tableFont));
      tm = JobMonitor.getColumnModel().getColumn(3);
      tm.setCellRenderer(new ColorColumnRenderer(config.tableBkgndDarker, config.tableFont));
               
      //JobMonitor.setFillsViewportHeight(true);
      JobMonitor.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
      JobMonitor.addMouseListener(
         new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
               MouseClicked(e);
            }
         }
      );
   }
   
   // Mouse event handler - for double click
   // This will create a taskInfo stdout/stderr monitor window for a running job
   private void MouseClicked(MouseEvent e) {
      if(e.getClickCount() == 2) {
         int row = JobMonitor.rowAtPoint(e.getPoint());
         jobData job = GetRowData(row);
         if (job.status.equals("running")) {
            new taskInfo(
               config.gui.getJFrame(),
               job.type + ": " + "Tivo=" +
               (String)JobMonitor.getValueAt(row, getColumnIndex("SOURCE")) +
               "---Output=" +
               (String)JobMonitor.getValueAt(row, getColumnIndex("OUTPUT")),
               job.getProcess()
            );
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
         if (col == 1) {
            return jobEntry.class;
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
     
          if (bkgndColor != null && ! isSelected)
             cell.setBackground( bkgndColor );
          
          cell.setFont(config.tableFont);
         
          return cell;
       }
    } 
    
    public int getColumnIndex(String name) {
       String cname;
       for (int i=0; i<JobMonitor.getColumnCount(); i++) {
          cname = (String)JobMonitor.getColumnModel().getColumn(i).getHeaderValue();
          if (cname.equals(name)) return i;
       }
       return -1;
    }
        
    public int[] GetSelectedRows() {
       int[] rows = JobMonitor.getSelectedRows();
       if (rows.length <= 0)
          config.gui.text_error("No rows selected");
       return rows;
    }
    
    // Return job hash of selected entry
    public jobData GetSelectionData(int row) {
       // Get column items for selected row 
       if (row < 0) {
          config.gui.text_error("Nothing selected");
          return null;
       }
       jobEntry s = (jobEntry)JobMonitor.getValueAt(row, getColumnIndex("JOB"));
       return s.job;
    }
    
    // Return job hash of selected entry
    public jobData GetRowData(int row) {
       // Get column items for given row 
       if ( JobMonitor.getRowCount() > row ) {
          jobEntry s = (jobEntry)JobMonitor.getValueAt(row, getColumnIndex("JOB"));
          return s.job;
       }
       return null;
    }
    
    public void AddJobMonitorRow(jobData job, String source, String output) {
       debug.print("job=" + job + " source=" + source + " output=" + output);
       Object[] info = new Object[TITLE_cols.length];
       info[0] = job.status;
       info[1] = new jobEntry(job);
       info[2] = source;
       info[3] = output;
       
       // Insert location depends on familyId if it exists
       if (job.familyId != null) {
          // Determine insertion location
          int index = -1;
          Float id;
          for (int i=0; i<JobMonitor.getRowCount(); ++i) {
             id = GetRowData(i).familyId;
             if (id != null && id > job.familyId) {
                index = i;
                break;
             }
          }
          if (index != -1)
             InsertRow(JobMonitor, info, index);
          else
             AddRow(JobMonitor, info);
       } else {
          AddRow(JobMonitor, info);
       }
       
       // Adjust column widths to data
       packColumns(JobMonitor, 2);
    }
    
    public void RemoveJobMonitorRow(jobData job) {
       debug.print("job=" + job);
       TableModel model = JobMonitor.getModel(); 
       int numrows = model.getRowCount(); 
       for(int i=0; i<numrows; i++) {
          jobEntry e = (jobEntry)JobMonitor.getValueAt(i,getColumnIndex("JOB"));
          if (e.job == job) {
             RemoveRow(JobMonitor, i);
             return;
          }
       }
    }
    
    public void UpdateJobMonitorRowStatus(jobData job, String status) {
       //debug.print("job=" + job);
       TableModel model = JobMonitor.getModel(); 
       int numrows = model.getRowCount(); 
       for(int i=0; i<numrows; i++) {
          jobEntry e = (jobEntry)JobMonitor.getValueAt(i,getColumnIndex("JOB"));
          if (e.job == job) {
             JobMonitor.setValueAt(status, i, getColumnIndex("STATUS"));
             packColumns(JobMonitor,2);
             return;
          }
       }
    }
       
    public void clear(JTable table) {
       debug.print("table=" + table);
       DefaultTableModel model = (DefaultTableModel)table.getModel();
       model.setNumRows(0);
    }
    
    public void AddRow(JTable table, Object[] data) {
       debug.print("table=" + table + " data=" + data);
       DefaultTableModel dm = (DefaultTableModel)table.getModel();
       dm.addRow(data);
    }
    
    public void InsertRow(JTable table, Object[] data, int row) {
       debug.print("table=" + table + " data=" + data);
       DefaultTableModel dm = (DefaultTableModel)table.getModel();
       dm.insertRow(row, data);
    }
    
    public void RemoveRow(JTable table, int row) {
       debug.print("table=" + table + " row=" + row);
       DefaultTableModel dm = (DefaultTableModel)table.getModel();
       dm.removeRow(row);
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
        
        // Adjust last column to fill available width
        int last = table.getColumnCount()-1;
        if (vColIndex == last) {
           int twidth = table.getPreferredSize().width;
           int awidth = config.gui.getJFrame().getWidth();
           int offset = 2*config.gui.jobPane.getVerticalScrollBar().getPreferredSize().width+2*margin;
           if ((awidth-offset) > twidth) {
              width += awidth-offset-twidth;
              col.setPreferredWidth(width);
           }
        }
    }

}
