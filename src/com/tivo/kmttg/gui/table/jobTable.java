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

import java.util.Arrays;
import java.util.Stack;

import javafx.event.EventHandler;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.jobEntry;
import com.tivo.kmttg.gui.taskInfo;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class jobTable {
   private String[] TITLE_cols = {"STATUS", "JOB", "SOURCE", "OUTPUT"};
   private double[] weights = {25, 15, 15, 45};

   public TableView<Tabentry> JobMonitor = null;
   
   public jobTable() {
      JobMonitor = new TableView<Tabentry>();
      JobMonitor.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE); // Allow multiple row selection
      for (String colName : TITLE_cols) {
         // Regular String sort
         TableColumn<Tabentry,String> col = new TableColumn<Tabentry,String>(colName);
         col.setCellValueFactory(new PropertyValueFactory<Tabentry,String>(colName));
         col.setComparator(null); // Disable column sorting
         JobMonitor.getColumns().add(col);
      }
      TableUtil.setWeights(JobMonitor, TITLE_cols, weights, false);
      JobMonitor.setOnMousePressed(new EventHandler<MouseEvent>() {
         @Override 
         public void handle(MouseEvent event) {
             MouseClicked(event);
         }
      });
      
      JobMonitor.setOnKeyPressed(new EventHandler<KeyEvent>() {
         public void handle(KeyEvent e) {
            if (e.isControlDown())
               return;
            if (e.getCode() == KeyCode.C) {
               // c key presses CANCEL JOBS button
               config.gui.cancel.fire();
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
         Tabentry entry = JobMonitor.getSelectionModel().getSelectedItem();
         int row = JobMonitor.getSelectionModel().getSelectedIndex();
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
       jobEntry s = JobMonitor.getItems().get(row).getJobEntry();
       return s.job;
    }
    
    // Return job hash of selected entry
    public jobData GetRowData(int row) {
       // Get column items for given row 
       if ( JobMonitor.getItems().size() > row ) {
          jobEntry s = JobMonitor.getItems().get(row).getJobEntry();
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
          for (int i=0; i<JobMonitor.getItems().size(); ++i) {
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
       int numrows = JobMonitor.getItems().size(); 
       for(int i=0; i<numrows; i++) {
          jobEntry e = JobMonitor.getItems().get(i).getJobEntry();
          if (e.job == job) {
             RemoveRow(i);
             return;
          }
       }
    }
    
    public void UpdateJobMonitorRowStatus(jobData job, String status) {
       //debug.print("job=" + job);
       int numrows = JobMonitor.getItems().size(); 
       for(int row=0; row<numrows; row++) {
          Tabentry entry = JobMonitor.getItems().get(row);
          jobEntry e = entry.getJobEntry();
          if (e.job == job) {
             entry.status = status;
             JobMonitor.getItems().set(row, entry);
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
       int numrows = JobMonitor.getItems().size(); 
       for(int row=0; row<numrows; row++) {
          Tabentry entry = JobMonitor.getItems().get(row);
          jobEntry e = entry.getJobEntry();
          if (e.job == job) {
             entry.output = text;
             JobMonitor.getItems().set(row, entry);
             return;
          }
       }
    }
       
    public void clear(TableView<?> table) {
       table.getItems().clear();
    }
    
    public void AddRow(jobData job, String source, String output) {
       JobMonitor.getItems().add(new Tabentry(job, source, output));
    }
    
    public void InsertRow(jobData job, String source, String output, int row) {
       JobMonitor.getItems().add(row, new Tabentry(job, source, output));
    }
    
    public void RemoveRow(int row) {
       JobMonitor.getItems().remove(row);
    }
    
    // Return current column name order as a string array
    public String[] getColumnOrder() {
       int size = JobMonitor.getColumns().size();
       String[] order = new String[size];
       for (int i=0; i<size; ++i) {
          order[i] = JobMonitor.getColumns().get(i).getText();
       }
       return order;
    }
    
    // Change table column order according to given string array order
    public void setColumnOrder(String[] order) {
       debug.print("order=" + Arrays.toString(order));
       
       // Don't do anything if column counts don't match up
       if (JobMonitor.getColumns().size() != order.length) return;
       
       // Re-order to desired positions
       String colName;
       int index;
       for (int i=0; i<order.length; ++i) {
          colName = order[i];
          if (colName.equals("ICON")) colName = "";
          index = TableUtil.getColumnIndex(JobMonitor, colName);
          if ( index != -1)
             moveColumn(index, i);
       }
    }
    
    public void moveColumn(int from, int to) {
       int num = JobMonitor.getColumns().size();
       Stack<TableColumn<Tabentry,?>> order = new Stack<TableColumn<Tabentry,?>>();
       for (int i=0; i<num; ++i) {
          int index = i;
          if (index == from)
             index = to;
          else if (index == to)
             index = from;
          TableColumn<Tabentry,?> col = JobMonitor.getColumns().get(index);
          order.push(col);
       }
       JobMonitor.getColumns().clear();
       for (TableColumn<Tabentry,?> col : order) {
          JobMonitor.getColumns().add(col);
       }
    }
}
