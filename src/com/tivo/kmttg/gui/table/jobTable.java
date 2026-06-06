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

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;

import javax.swing.JTable;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.jobEntry;
import com.tivo.kmttg.gui.taskInfo;
import com.tivo.kmttg.gui.swing.KmttgTable;
import com.tivo.kmttg.gui.swing.KmttgTableModel;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class jobTable {
   private String[] TITLE_cols = {"STATUS", "JOB", "SOURCE", "OUTPUT"};
   private double[] weights = {25, 15, 15, 45};

   public JTable JobMonitor = null;
   public KmttgTableModel<Tabentry> MODEL = null;

   public jobTable() {
      MODEL = new KmttgTableModel<Tabentry>(TITLE_cols);
      MODEL.setSortingEnabled(false); // Job queue order is meaningful
      JobMonitor = KmttgTable.create(MODEL, null);
      TableUtil.setWeights(JobMonitor, TITLE_cols, weights, false);
      JobMonitor.addMouseListener(new MouseAdapter() {
         @Override
         public void mousePressed(MouseEvent event) {
             MouseClicked(event);
         }
      });

      JobMonitor.addKeyListener(new KeyAdapter() {
         @Override
         public void keyPressed(KeyEvent e) {
            if (e.isControlDown())
               return;
            if (e.getKeyCode() == KeyEvent.VK_C) {
               // c key presses CANCEL JOBS button
               config.gui.cancel.doClick();
            }
         }
      });
   }

   public static class Tabentry {
      public String status = "";
      public jobEntry jobentry;
      public String source = "";
      public String output = "";

      public Tabentry(jobData job, String source, String output) {
         status = job.status;
         jobentry = new jobEntry(job);
         this.source = source;
         this.output = output;
      }

      public String getSTATUS() {
         return status;
      }

      public String getJOB() {
         return jobentry.toString();
      }

      public String getSOURCE() {
         return source;
      }

      public String getOUTPUT() {
         return output;
      }

      public jobEntry getJobEntry() {
         return jobentry;
      }
   }

   // Mouse event handler - for double click
   // This will create a taskInfo stdout/stderr monitor window for a running job
   private void MouseClicked(MouseEvent e) {
      if(e.getClickCount() == 2) {
         int row = JobMonitor.getSelectionModel().getLeadSelectionIndex();
         if (row < 0 || row >= MODEL.size())
            return;
         Tabentry entry = MODEL.getRow(row);
         if (entry != null) {
            jobData job = GetRowData(row);
            if (job.status.equals("running") && job.getProcess() != null) {
               new taskInfo(
                  config.gui.getFrame(),
                  job.type + ": " + "Tivo=" +
                  entry.source +
                  "---Output=" +
                  entry.output,
                  job.getProcess()
               );
            }
         }
      }
   }

    // Return job hash of selected entry
    public jobData GetSelectionData(int row) {
       // Get column items for selected row
       if (row < 0) {
          log.error("Nothing selected");
          return null;
       }
       jobEntry s = MODEL.getRow(row).getJobEntry();
       return s.job;
    }

    // Return job hash of selected entry
    public jobData GetRowData(int row) {
       // Get column items for given row
       if ( MODEL.size() > row ) {
          jobEntry s = MODEL.getRow(row).getJobEntry();
          return s.job;
       }
       return null;
    }

    public void AddJobMonitorRow(jobData job, String source, String output) {
       debug.print("job=" + job + " source=" + source + " output=" + output);

       // Insert location depends on familyId if it exists
       if (job.familyId != null) {
          // Determine insertion location
          int index = -1;
          Float id;
          for (int i=0; i<MODEL.size(); ++i) {
             id = GetRowData(i).familyId;
             if (id != null && id > job.familyId) {
                index = i;
                break;
             }
          }
          if (index != -1)
             InsertRow(job, source, output, index);
          else
             AddRow(job, source, output);
       } else {
          AddRow(job, source, output);
       }

       // Adjust column widths to data
       TableUtil.autoSizeTableViewColumns(JobMonitor, true);
    }

    public void RemoveJobMonitorRow(jobData job) {
       debug.print("job=" + job);
       int numrows = MODEL.size();
       for(int i=0; i<numrows; i++) {
          jobEntry e = MODEL.getRow(i).getJobEntry();
          if (e.job == job) {
             RemoveRow(i);
             return;
          }
       }
    }

    public void UpdateJobMonitorRowStatus(jobData job, String status) {
       //debug.print("job=" + job);
       int numrows = MODEL.size();
       for(int row=0; row<numrows; row++) {
          Tabentry entry = MODEL.getRow(row);
          jobEntry e = entry.getJobEntry();
          if (e.job == job) {
             entry.status = status;
             MODEL.updateRow(row);
             return;
          }
       }
    }

    /*private int getColumnIndex(String name) {
       String cname;
       for (int i=0; i<JobMonitor.getColumns().size(); i++) {
          cname = (String)JobMonitor.getColumns().get(i).getText();
          if (cname.equals(name)) return i;
       }
       return -1;
    }*/

    public void UpdateJobMonitorRowOutput(jobData job, String text) {
       int numrows = MODEL.size();
       for(int row=0; row<numrows; row++) {
          Tabentry entry = MODEL.getRow(row);
          jobEntry e = entry.getJobEntry();
          if (e.job == job) {
             entry.output = text;
             MODEL.updateRow(row);
             return;
          }
       }
    }

    public void clear(JTable table) {
       ((KmttgTableModel<?>)table.getModel()).clear();
    }

    public void AddRow(jobData job, String source, String output) {
       MODEL.addRow(new Tabentry(job, source, output));
    }

    public void InsertRow(jobData job, String source, String output, int row) {
       MODEL.getRows().add(row, new Tabentry(job, source, output));
       MODEL.fireTableRowsInserted(row, row);
    }

    public void RemoveRow(int row) {
       MODEL.removeRow(row);
    }

    // Return current column name order as a string array (view order)
    public String[] getColumnOrder() {
       TableColumnModel cm = JobMonitor.getColumnModel();
       int size = cm.getColumnCount();
       String[] order = new String[size];
       for (int i=0; i<size; ++i) {
          order[i] = cm.getColumn(i).getHeaderValue().toString();
       }
       return order;
    }

    // Change table column order according to given string array order
    public void setColumnOrder(String[] order) {
       debug.print("order=" + Arrays.toString(order));

       // Don't do anything if column counts don't match up
       if (JobMonitor.getColumnModel().getColumnCount() != order.length) return;

       // Re-order to desired positions
       String colName;
       for (int i=0; i<order.length; ++i) {
          colName = order[i];
          if (colName.equals("ICON")) colName = "";
          int index = viewIndexOf(colName);
          if ( index != -1 && index != i)
             JobMonitor.getColumnModel().moveColumn(index, i);
       }
    }

    // Find current view index of column with given header name
    private int viewIndexOf(String colName) {
       TableColumnModel cm = JobMonitor.getColumnModel();
       for (int i=0; i<cm.getColumnCount(); ++i) {
          TableColumn col = cm.getColumn(i);
          if (col.getHeaderValue().toString().equals(colName))
             return i;
       }
       return -1;
    }
}
