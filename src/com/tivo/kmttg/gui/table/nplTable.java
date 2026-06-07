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
import java.awt.datatransfer.DataFlavor;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Stack;
import java.util.Vector;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.TransferHandler;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.TableMap;
import com.tivo.kmttg.gui.gui;
import com.tivo.kmttg.gui.comparator.ChannelComparator;
import com.tivo.kmttg.gui.comparator.DateComparator;
import com.tivo.kmttg.gui.comparator.DoubleComparator;
import com.tivo.kmttg.gui.comparator.DurationComparator;
import com.tivo.kmttg.gui.comparator.ImageComparator;
import com.tivo.kmttg.gui.comparator.ShowComparator;
import com.tivo.kmttg.gui.comparator.SizeComparator;
import com.tivo.kmttg.gui.sortable.sortableChannel;
import com.tivo.kmttg.gui.sortable.sortableDate;
import com.tivo.kmttg.gui.sortable.sortableDouble;
import com.tivo.kmttg.gui.sortable.sortableDuration;
import com.tivo.kmttg.gui.sortable.sortableShow;
import com.tivo.kmttg.gui.sortable.sortableSize;
import com.tivo.kmttg.gui.swing.KmttgTable;
import com.tivo.kmttg.gui.swing.RowColorer;
import com.tivo.kmttg.gui.swing.TreeTable;
import com.tivo.kmttg.gui.swing.TreeTable.TreeItem;
import com.tivo.kmttg.main.auto;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.rpc.SkipImport;
import com.tivo.kmttg.rpc.SkipManager;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.createMeta;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;


public class nplTable extends TableMap {
   public String tivoName = null;
   public TreeTable<Tabentry> NowPlaying = null;
   public String[] FILE_cols = {"FILE", "SIZE", "DIR"};
   private double[] FILE_weights = {41, 12, 47};
   public String[] TIVO_cols = {"", "SHOW", "DATE", "CHANNEL", "DUR", "SIZE", "Mbps"};
   private double[] TIVO_weights = {12, 32, 18, 17, 6, 9, 6};
   public String folderName = null;
   public int folderEntryNum = -1;
   private Stack<Hashtable<String,String>> entries = null;
   private Stack<Hashtable<String,String>> entries_viewed = null;
   private Hashtable<String,Stack<Hashtable<String,String>>> folders = null;
   private Vector<Hashtable<String,String>> sortedOrder = null;
   private JSONArray skipEntries = null;
   private String lastUpdated = null;
   // This needed to flag when calling updateNPLjobStatus so that multiple
   // selection event triggers can be avoided
   private Boolean UpdatingNPL = false;

   // Override TableMap methods
   @Override
   public void clear() {
      NowPlaying.getRoot().clearChildren();
   }
   @Override
   public TreeTable<?> getTreeTable() {
      return NowPlaying;
   }

   public nplTable(final String tivoName) {
      this.tivoName = tivoName;
      if (tivoName.equals("FILES"))
         NowPlaying = new TreeTable<Tabentry>(FILE_cols);
      else
         NowPlaying = new TreeTable<Tabentry>(TIVO_cols);
      NowPlaying.setRowColorer(new ColorRow()); // For row background color handling

      if (tivoName.equals("FILES")) {
         // Add ability to accept drag and drop files
         NowPlaying.table.setTransferHandler(new TransferHandler() {
            private static final long serialVersionUID = 1L;
            @Override
            public boolean canImport(TransferSupport support) {
               return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }
            @SuppressWarnings("unchecked")
            @Override
            public boolean importData(TransferSupport support) {
               try {
                  List<File> fileList = (List<File>)support.getTransferable()
                     .getTransferData(DataFlavor.javaFileListFlavor);
                  if (fileList != null && fileList.size() > 0) {
                     for (File f : fileList) {
                        AddNowPlayingFileRow(f);
                     }
                  }
                  return true;
               } catch (Exception e) {
                  return false;
               }
            }
         });

         NowPlaying.setComparator("SIZE", new SizeComparator());
         TableUtil.setWeights(NowPlaying, FILE_cols, FILE_weights, false);
      } else {
         NowPlaying.setComparator("", new ImageComparator());
         NowPlaying.setComparator("SHOW", new ShowComparator());
         NowPlaying.setComparator("DATE", new DateComparator());
         NowPlaying.setComparator("CHANNEL", new ChannelComparator());
         NowPlaying.setComparator("DUR", new DurationComparator());
         NowPlaying.setComparator("SIZE", new SizeComparator());
         NowPlaying.setComparator("Mbps", new DoubleComparator());
         // Default sort is descending date when no column sort is selected
         NowPlaying.setDefaultSort("DATE", false);
         KmttgTable.setColumnAlignment(NowPlaying.table, "DATE", JLabel.RIGHT);
         KmttgTable.setColumnAlignment(NowPlaying.table, "DUR", JLabel.CENTER);
         KmttgTable.setColumnAlignment(NowPlaying.table, "SIZE", JLabel.RIGHT);
         KmttgTable.setColumnAlignment(NowPlaying.table, "Mbps", JLabel.RIGHT);
         TableUtil.setWeights(NowPlaying, TIVO_cols, TIVO_weights, false);
      }

      // Want to resize columns + update job status whenever a tree is expanded
      NowPlaying.setOnExpand(new Runnable() {
         @Override public void run() {
            // Identify NPL table items associated with queued/running jobs
            jobMonitor.updateNPLjobStatus();
            TableUtil.autoSizeTableViewColumns(NowPlaying, false);
         }
      });

      // Add right mouse button handler
      TableUtil.AddRightMouseListener(NowPlaying);

      // Add keyboard listener
      NowPlaying.table.addKeyListener(new KeyAdapter() {
         @Override
         public void keyPressed(KeyEvent e) {
            KeyPressed(e);
         }
      });

      // Define selection listener to detect table row selection changes
      NowPlaying.table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
         @Override
         public void valueChanged(ListSelectionEvent e) {
            if (e.getValueIsAdjusting() || UpdatingNPL) return;
            int row = NowPlaying.table.getSelectionModel().getLeadSelectionIndex();
            if (row >= 0 && row < NowPlaying.getExpandedItemCount() && NowPlaying.isSelected(row))
               NowPlayingRowSelected(NowPlaying.getTreeItem(row).getValue());
         }
      });
   }

   public class Tabentry {
      // FILES mode entries
      private String fileName = "";
      private String dirName = "";

      // TIVO mode entries
      private imageCell image = new imageCell();
      private sortableShow show = null;
      private sortableDate date = null;
      private sortableChannel channel = null;
      private sortableDuration duration = null;
      private sortableSize size = null;
      private sortableDouble mbps = null;

      // Root node constructor
      public Tabentry(String s) {
         // Do nothing
      }

      // FILES mode constructor
      public Tabentry(File file) {
         if (!file.exists()) {
            log.warn(file.getName() + " was not found so it isn't being added. This *may* be a bug since you suppossedly selected it from a file chooser.");
            return;
         }
         fileName = file.getName();
         dirName = file.getParentFile().getPath();
         long size_long = file.length();
         if (size_long == 0) {
            log.warn("File size is 0. It may be empty");
         }
         size = new sortableSize(size_long);
      }

      // TIVO mode folder constructor
      public Tabentry(String fName, Stack<Hashtable<String,String>> folderEntry) {
         if (folderEntry.size() == 0)
            return;
         image.setImage(gui.Images.get("folder"));
         image.imageName = "folder";

         // For date, find most recent recording
         // For channel see if they are all from same channel
         String chan = "";
         if (folderEntry.size() > 0 && folderEntry.get(0).containsKey("channel")) {
            chan = folderEntry.get(0).get("channel");
         }
         Boolean sameChannel = true;
         Double rate_total = 0.0;
         Double rate;
         long gmt, largestGmt=0;
         int gmt_index=0;
         int clipDataNum=0;
         for (int i=0; i<folderEntry.size(); ++i) {
            Hashtable<String,String> entry = folderEntry.get(i);
            gmt = Long.parseLong(entry.get("gmt"));
            if (gmt > largestGmt) {
               largestGmt = gmt;
               gmt_index = i;
            }
            if (entry.containsKey("channel")) {
               if ( ! entry.get("channel").equals(chan) ) {
                  sameChannel = false;
               }
            }
            rate = 0.0;
            if (entry.containsKey("size") && entry.containsKey("duration")) {
               rate = bitRate(entry.get("size"), entry.get("duration"));
            }
            if (entry.containsKey("clipMetadataId"))
               clipDataNum++;
            rate_total += rate;
         }
         if (clipDataNum > 0) {
            image.setImage2(gui.Images.get("skipmode"));
            image.setLabel(" " + clipDataNum);
         }
         if (folderEntry.size() > 0) {
            rate_total /= folderEntry.size();
            show = new sortableShow(fName, folderEntry, gmt_index);
            date = new sortableDate(fName, folderEntry, gmt_index);

            if (sameChannel) {
               if ( folderEntry.get(0).containsKey("channelNum") && folderEntry.get(0).containsKey("channel")) {
                  channel = new sortableChannel(
                     folderEntry.get(0).get("channel"),folderEntry.get(0).get("channelNum")
                  );
               } else
                  channel = new sortableChannel("", "");
            } else {
               channel = new sortableChannel("<various>", "0");
            }

            duration = new sortableDuration(folderEntry);
            size = new sortableSize(folderEntry);
            mbps = new sortableDouble(rate_total);
         }
      }

      // TIVO mode non-folder constructor
      public Tabentry(Hashtable<String,String> entry) {
         if ( entry.containsKey("ExpirationImage") ) {
            image.setImage(gui.Images.get(entry.get("ExpirationImage")));
            image.imageName = entry.get("ExpirationImage");
         }
         if (entry.containsKey("clipMetadataId"))
            image.setImage2(gui.Images.get("skipmode"));
         image.setLabel(getPctWatched(entry));
         show = new sortableShow(entry);
         date = new sortableDate(entry);
         if ( entry.containsKey("channelNum") && entry.containsKey("channel") )
            channel = new sortableChannel(entry.get("channel"), entry.get("channelNum"));
         else
            channel = new sortableChannel("", "");
         duration = new sortableDuration(entry);
         size = new sortableSize(entry);
         Double rate = 0.0;
         if (entry.containsKey("size") && entry.containsKey("duration")) {
            rate = bitRate(entry.get("size"), entry.get("duration"));
         }
         mbps = new sortableDouble(rate);
      }

      public imageCell getIMAGE() {
         return image;
      }

      public sortableShow getSHOW() {
         return show;
      }

      public sortableDate getDATE() {
         return date;
      }

      public sortableChannel getCHANNEL() {
         return channel;
      }

      public sortableDuration getDUR() {
         return duration;
      }

      public sortableSize getSIZE() {
         return size;
      }

      public String getFILE() {
         return fileName;
      }

      public String getDIR() {
         return dirName;
      }

      public sortableDouble getMbps() {
         return mbps;
      }

      public void setIMAGE(java.awt.Image img) {
         image.setImage(img);
      }

      public String toString() {
         if (show == null)
            return "";
         return show.toString();
      }
   }

   // Row background color handling (replaces JavaFX ColorRowFactory)
   private class ColorRow implements RowColorer<Tabentry> {
      public Color getColor(Tabentry entry) {
         if (entry != null) {
            sortableDate d = entry.getDATE();
            if (d != null && d.data != null) {
               Color color = null;
               if (d.data.containsKey("CopyProtected"))
                  color = TableUtil.tableBkgndProtected;

               if (d.data.containsKey("ExpirationImage") &&
                   (d.data.get("ExpirationImage").equals("in-progress-recording") ||
                    d.data.get("ExpirationImage").equals("in-progress-transfer")))
                  color = TableUtil.tableBkgndRecording;

               if (config.showHistoryInTable == 1) {
                  if (d.data.containsKey("ProgramId") &&
                        auto.keywordMatchHistoryFast(d.data.get("ProgramId"), false))
                     color = TableUtil.tableBkgndInHistory;
                  if (d.data.containsKey("ProgramId_unique") &&
                        auto.keywordMatchHistoryFast(d.data.get("ProgramId_unique"), false))
                     color = TableUtil.tableBkgndInHistory;
               }
               return color;
            }
         }
         return null;
      }
   }

   private sortableDate getFirstSelected() {
      int[] selected = GetSelectedRows();
      if (selected == null || selected.length < 1)
         return null;
      return NowPlaying.getTreeItem(selected[0]).getValue().getDATE();

   }

   // Handle keyboard presses
   private void KeyPressed(KeyEvent e) {
      if (e.isControlDown())
         return;
      int keyCode = e.getKeyCode();
      if (keyCode == KeyEvent.VK_S) {
         // s key presses START JOBS button
         config.gui.start.doClick();
         return;
      }
      if (tivoName.equals("FILES"))
         return;
      if (keyCode == KeyEvent.VK_DELETE ||
               keyCode == KeyEvent.VK_BACK_SPACE ||
               keyCode == KeyEvent.VK_P) {
         Integer[] selected = TableUtil.highToLow(GetSelectedRows());
         if (selected != null && selected.length > 0) {
            if (keyCode == KeyEvent.VK_DELETE || keyCode == KeyEvent.VK_BACK_SPACE) {
               // Delete key has special action
               e.consume(); // Need this so as not to remove focus which is default key action
               String show_names = "";
               LinkedHashMap<String,Integer> urlsToDelete = new LinkedHashMap<String,Integer>();
               LinkedHashMap<String,Integer> idsToDelete = new LinkedHashMap<String,Integer>();
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
               if (lowest-1 < 0) {
                  if (NowPlaying.getRoot().getChildren().size() > 1)
                     lowest = 1;
                  else
                     lowest = 0;
               }
               else
                  lowest -= 1;
               if (lowest >= 0 && lowest < NowPlaying.getExpandedItemCount())
                  final_select = NowPlaying.getTreeItem(lowest).getValue().getDATE();

               for (int i=0; i<selected.length; ++i) {
                  int row = selected[i];
                  sortableDate s = NowPlaying.getTreeItem(row).getValue().getDATE();
                  if (s.folder) {
                     // Delete all shows in folder
                     for (int j=0; j<s.folderData.size(); j++) {
                        Hashtable<String,String> entry = s.folderData.get(j);
                        if (entry.containsKey("url")) {
                           log.warn("Delete url=" + entry.get("url"));
                           if (config.twpDeleteEnabled() && ! config.rpcEnabled(tivoName))
                              urlsToDelete.put(entry.get("url"), row);
                           if (config.rpcEnabled(tivoName)) {
                              id = rnpl.findRecordingId(tivoName, entry);
                              if (id != null) {
                                 show_names += "\n" + entry.get("title");
                                 urlsToDelete.put(entry.get("url"), -1);
                                 idsToDelete.put(id, -1);
                              }
                           }
                        }
                     } // for
                     // 1st entry gets folder row #
                     if (urlsToDelete.size() > 0) {
                        String first = (String) urlsToDelete.keySet().toArray()[0];
                        urlsToDelete.put(first, row);
                     }
                     if (idsToDelete.size() > 0) {
                        String first = (String) idsToDelete.keySet().toArray()[0];
                        idsToDelete.put(first, row);
                     }
                  } else {
                     // Delete individual show
                     if (config.twpDeleteEnabled() && ! config.rpcEnabled(tivoName)) {
                        if (s.data.containsKey("url")) {
                           urlsToDelete.put(s.data.get("url"), row);
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
                              show_names += "\n" + s.data.get("title");
                              urlsToDelete.put(s.data.get("url"), row);
                              idsToDelete.put(id, row);
                           }
                        }
                     }
                  } // else individual show
               } // for selected
               if (urlsToDelete.size() > 0) {
                  if (config.twpDeleteEnabled() && ! config.rpcEnabled(tivoName)) {
                     // USE TWP to remove items from entries stack
                     // NOTE: Always revert to top view (not inside a folder)
                     RemoveUrls(urlsToDelete);
                  }
                  if (config.rpcEnabled(tivoName)) {
                     // Use rpc remote protocol to remove items
                     log.warn("Deleting selected shows on TiVo '" + tivoName + "':" + show_names);
                     RemoveIds(urlsToDelete, idsToDelete);
                  }
               } // if urslToDelete

               // After table refresh this is the data of the row to look for to select
               if (final_select != null) {
                  NowPlaying.clearSelection();
                  if (final_select.folder) {
                     Hashtable<String,String> h = new Hashtable<String,String>();
                     h.put("folderName", final_select.folderName);
                     selectRowWithData(h);
                  } else {
                     selectRowWithData(final_select.data);
                  }
               }
            } // if keyCode == VK_DELETE

            if (keyCode == KeyEvent.VK_P) {
               // P key has special action
               if (config.rpcEnabled(tivoName)) {
                  int[] rows = GetSelectedRows();
                  if (rows == null) return;
                  JSONArray ids = new JSONArray();
                  Stack<String> titles = new Stack<String>();
                  for (int i=0; i<rows.length; ++i) {
                     int row = rows[i];
                     sortableDate s = NowPlaying.getTreeItem(row).getValue().getDATE();
                     if (s.folder) {
                        // NOTE: Do this backwards as usually most recent is 1st
                        for (int j=s.folderData.size()-1; j>=0; j--) {
                           Hashtable<String,String> entry = s.folderData.get(j);
                           String id = rnpl.findRecordingId(tivoName, entry);
                           if (id != null)
                              ids.put(id);
                           String title = "";
                           if (entry.containsKey("title"))
                              title = entry.get("title");
                           titles.add(title);
                        }
                     } else {
                        String id = rnpl.findRecordingId(tivoName, s.data);
                        if (id != null)
                           ids.put(id);
                        String title = "";
                        if (s.data.containsKey("title"))
                           title = s.data.get("title");
                        titles.add(title);
                     }
                  }
                  if (ids.length() > 0) {
                     PlayShow(ids, titles);
                  }
               }
            } // if keyCode == VK_P
         } // if selected != null
      } else if (keyCode == KeyEvent.VK_I) {
         // Print all data of selected row to log window by sorted keys
         sortableDate s = getFirstSelected(); if (s == null) return;
         if (! s.folder && s.data != null && s.data.containsKey("recordingId")) {
            config.gui.show_details.update(NowPlaying, tivoName, s.data.get("recordingId"));
         }
      } else if (keyCode == KeyEvent.VK_J) {
         // Print all data of selected row to log window by sorted keys
         sortableDate s = getFirstSelected(); if (s == null) return;
         if ( ! s.folder && s.data != null ) {
            Vector<String> v = new Vector<String>(s.data.keySet());
            Collections.sort(v);
            for (Enumeration<String> it = v.elements(); it.hasMoreElements();) {
              String name = it.nextElement();
              log.print(name + " = " + s.data.get(name));
            }
         }
      } else if (keyCode == KeyEvent.VK_N) {
         sortableDate s = getFirstSelected(); if (s == null) return;
         if (! s.folder && s.data != null && s.data.containsKey("collectionId"))
            TableUtil.PrintEpisodes(s.data.get("titleOnly"), s.data.get("collectionId"));
      } else if (keyCode == KeyEvent.VK_Q) {
         // Web query currently selected entry
         sortableDate s = getFirstSelected(); if (s == null) return;
         if ( ! s.folder && s.data != null && s.data.containsKey("title")) {
            TableUtil.webQuery(s.data.get("title"));
         }
      } else if (keyCode == KeyEvent.VK_R) {
         // Collect and print RPC data of selected row
         sortableDate s = getFirstSelected(); if (s == null) return;
         if ( ! s.folder && s.data != null ) {
            if (s.data.containsKey("recordingId")) {
               final String recordingId = s.data.get("recordingId");
               Runnable task = new Runnable() {
                  @Override public void run() {
                     Remote r = config.initRemote(tivoName);
                     if (r.success) {
                        try {
                           JSONObject json = new JSONObject();
                           json.put("recordingId", recordingId);
                           JSONObject result = r.Command("Search", json);
                           if (result != null && result.has("recording")) {
                              JSONArray a = result.getJSONArray("recording");
                              log.print(a.getJSONObject(0).toString(3));
                           }
                        }
                        catch (JSONException e) {
                           log.error(e.getMessage());
                        }
                        r.disconnect();
                     }
                  }
               };
               new Thread(task).start();
            }
         }
      } else if (keyCode == KeyEvent.VK_M) {
         sortableDate s = getFirstSelected(); if (s == null) return;
         createMeta.getExtendedMetadata(tivoName, s.data, true);
      } else if (keyCode == KeyEvent.VK_K) {
         sortableDate s = getFirstSelected(); if (s == null) return;
         if (s.data.containsKey("contentId")) {
            final String contentId = s.data.get("contentId");
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
         }
      } else if (keyCode == KeyEvent.VK_E) {
         // Operate on entire selected set
         Integer[] selected = TableUtil.highToLow(GetSelectedRows());
         if (selected == null) return;
         if (selected.length == 0) return;
         for (int i=0; i<selected.length; ++i) {
            int row = selected[i];
            sortableDate s = NowPlaying.getTreeItem(row).getValue().getDATE();
            if (s.folder) {
               log.warn("Skipping folder entry");
               continue;
            }
            if (s.data.containsKey("contentId") && s.data.containsKey("duration")) {
               if (config.VRD == 1)
                  SkipImport.vrdExport(s.data);
               else
                  SkipImport.edlExport(s.data);
            } else {
               log.warn("Entry missing contentId and/or duration - skipping");
            }
         }
      } else if (keyCode == KeyEvent.VK_T) {
         TableUtil.toggleTreeState(NowPlaying);
      }
      else if (keyCode == KeyEvent.VK_C) {
         // Import Skip Share
         sortableDate s = getFirstSelected(); if (s == null) return;
         if ( ! s.folder && s.data != null ) {
            com.tivo.kmttg.rpc.SkipShare.tableImport(s.data, tivoName);
         }
      }
      else if (keyCode == KeyEvent.VK_V) {
         // Operate on entire selected set
         Integer[] selected = TableUtil.highToLow(GetSelectedRows());
         if (selected == null) return;
         if (selected.length == 0) return;
         visualDetect(selected);
      }
      else if (keyCode == KeyEvent.VK_W) {
         visualDetectAll();
      }
      else if (keyCode == KeyEvent.VK_Z) {
         if (SkipManager.skipEnabled()) {
            if (SkipManager.isMonitoring(tivoName)) {
               log.print("Scheduling AutoSkip disable");
               SkipManager.disable(tivoName);
               return;
            }
            if (config.rpcEnabled(tivoName)) {
               sortableDate s = getFirstSelected(); if (s == null) return;
               log.print("Starting AutoSkip");
               SkipManager.skipPlay(tivoName, s.data);
            }
         }
      }
   }

   // Return current state of Display Folders boolean for this TiVo
   public Boolean showFolders() {
      if (tivoName.equals("FILES")) return false;
      return config.gui.getTab(tivoName).showFolders();
   }

   public String getColumnName(int c) {
      return NowPlaying.getColumnName(c);
   }

   public int[] GetSelectedRows() {
      debug.print("");
      int[] rows = TableUtil.GetSelectedRows(NowPlaying);
      if (rows.length <= 0)
         log.error("No rows selected");
      return rows;
   }

   public void NowPlayingRowSelected(Tabentry entry) {
      if (tivoName.equals("FILES")) {
         // FILES mode - don't do anything
      } else {
         // Now Playing mode
         // Get column items for selected row
         sortableDate s = entry.getDATE();
         if (s == null)
            return;
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

            if (s.data.containsKey("TimeOffset")) {
               if (! s.data.get("TimeOffset").startsWith("0")) {
                  int secs = Integer.parseInt(s.data.get("TimeOffset"));
                  int mins = secs/60;
                  secs -= mins*60;
                  message += ", PAUSE POINT: " + String.format("%d mins %d secs", mins, secs);
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

            if (config.gui.show_details.isShowing() && s.data.containsKey("recordingId"))
               config.gui.show_details.update(NowPlaying, tivoName, s.data.get("recordingId"));
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
      sortableDate s = NowPlaying.getTreeItem(row).getValue().getDATE();
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
      String fileName = NowPlaying.getTreeItem(row).getValue().getFILE();
      String dirName = NowPlaying.getTreeItem(row).getValue().getDIR();
      String fullName = dirName + s + fileName;
      return fullName;
   }

   // This is for non FILES tables
   public Stack<Hashtable<String,String>> getRowData(int row) {
      Stack<Hashtable<String,String>> data = new Stack<Hashtable<String,String>>();
      if (row < 0) return data;
      sortableDate s = NowPlaying.getTreeItem(row).getValue().getDATE();
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
      viewedFilter(h); // populates entries_viewed hash

      if (config.showHistoryInTable == 1) {
         // Update history hash (used for highlighting entries with IDs in auto.history)
         auto.keywordMatchHistoryFast("bogus", true);
      }

      // Update table listings
      RefreshNowPlaying(entries);

      // Adjust column widths to data
      TableUtil.autoSizeTableViewColumns(NowPlaying, false);
   }

   // Refresh table with given NowPlaying entries
   // (This can be flat top level display, top level folder display or individual folder item display)
   public void RefreshNowPlaying(Stack<Hashtable<String,String>> h) {
      debug.print("h=" + h);
      if (h == null) h = entries;
      if (h == null) return;

      // Update skipEntries
      if (SkipManager.skipEnabled()) {
         skipEntries = SkipManager.getEntries();
      }

      if (showFolders())
         displayFolderStructure();
      else
         displayFlatStructure(h);
      String message = getTotalsString(h);

      NowPlaying.sort();

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
         if (name != null && ! matches(name, special) ) {
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

   // Display only entries with pause points
   public void displayUpdate(Boolean viewed_filter) {
      if (entries == null || entries_viewed == null)
         return;

      if (config.showHistoryInTable == 1) {
         // Update history hash (used for highlighting entries with IDs in auto.history)
         auto.keywordMatchHistoryFast("bogus", true);
      }

      if (entries.size() == entries_viewed.size()) {
         log.warn("NOTE: Looks like NPL list is already filtered. Refresh with 'Partially Viewed' turned off to get unfiltered list back.");
      }
      if (viewed_filter) {
         folderize(entries_viewed);
         RefreshNowPlaying(entries_viewed);
      } else {
         folderize(entries);
         RefreshNowPlaying(entries);
      }

      // Adjust column widths to data
      TableUtil.autoSizeTableViewColumns(NowPlaying, false);
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
      if (tivoName.equals("FILES"))
         return "";
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
      if (config.diskSpace.containsKey(tivoName)) {
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

   // Set entries_viewed to only items with pause points
   private void viewedFilter(Stack<Hashtable<String,String>> h) {
      if (h==null)
         return;
      entries_viewed = new Stack<Hashtable<String,String>>();
      for (int i=0; i<h.size(); ++i) {
         Hashtable<String,String> entry = h.get(i);
         Boolean filter = true;
         if (entry.containsKey("ByteOffset") && ! entry.get("ByteOffset").startsWith("0"))
            filter = false;
         if (entry.containsKey("TimeOffset") && ! entry.get("TimeOffset").startsWith("0"))
            filter = false;
         if (! filter)
            entries_viewed.push(entry);
      }
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
      TreeItem<Tabentry> item = new TreeItem<Tabentry>( new Tabentry(entry) );
      NowPlaying.getRoot().addChild(item);

   }

   // Add a now playing folder entry to NowPlaying table
   public void AddNowPlayingRow(String fName, Stack<Hashtable<String,String>> folderEntry) {
      TreeItem<Tabentry> item = new TreeItem<Tabentry>( new Tabentry(fName, folderEntry) );
      for (int i=0; i<folderEntry.size(); ++i) {
         TreeItem<Tabentry> subitem = new TreeItem<Tabentry>( new Tabentry(folderEntry.get(i)) );
         item.addChild(subitem);
      }
      NowPlaying.getRoot().addChild(item);
   }

   // Add a selected file in FILES mode to NowPlaying table
   public void AddNowPlayingFileRow(File file) {
      TreeItem<Tabentry> item = new TreeItem<Tabentry>( new Tabentry(file) );
      NowPlaying.getRoot().addChild(item);
      // Adjust column widths to data
      TableUtil.autoSizeTableViewColumns(NowPlaying, false);
      // Select entry just added
      int last = NowPlaying.getRoot().getChildren().size()-1;
      if (last >= 0) {
         NowPlaying.clearSelection();
         NowPlaying.select(last);
         NowPlaying.table.requestFocus();
      }
   }

   public void RemoveSelectedRow(int row) {
      debug.print("row=" + row);
      if (row < 0) {
         log.error("Nothing selected");
         return;
      }
      RemoveRow(row);

   }

   public void RemoveRow(int row) {
      TreeItem<Tabentry> item = NowPlaying.getTreeItem(row);
      if (item != null) {
         TreeItem<Tabentry> parent = item.getParent();
         if (parent != NowPlaying.getRoot()) {
            // Update parent Tabentry to account for removed entry
            sortableDate date = parent.getValue().getDATE();
            Stack<Hashtable<String,String>> new_data = new Stack<Hashtable<String,String>>();
            for (Hashtable<String,String> hash : date.folderData) {
               if (hash != item.getValue().getDATE().data)
                  new_data.add(hash);
            }
            if (new_data.size() > 0)
               parent.setValue(new Tabentry(date.folderName, new_data));
            else {
               NowPlaying.getRoot().removeChild(parent);
            }
         }
         // Remove table entry
         parent.removeChild(item);

         // Update table label
         displayTotals(getTotalsString(entries));
      }
   }

   public void RemoveRows(Stack<Integer> rows) {
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
            RemoveRow(index);
            rows.remove(index);
         }
      }
   }

   public void SetNowPlayingHeaders(String[] headers) {
      debug.print("headers=" + Arrays.toString(headers));
      for (int i=0; i<headers.length; ++i) {
         SetHeaderText(headers[i], i);
      }
   }

   // Refresh all titles currently displayed in table for non-folder entries
   public void refreshTitles() {
      for (int row=0; row<NowPlaying.getRoot().getChildren().size(); ++row) {
         TreeItem<Tabentry> item = NowPlaying.getRoot().getChildren().get(row);
         sortableDate s = item.getValue().getDATE();
         if (! s.folder && s.data != null) {
            Tabentry entry = item.getValue();
            entry.show = new sortableShow(s.data);
            item.setValue(entry);
         }
      }
   }

   // Look for entry with given folder name and select it
   // (This used when returning back from folder mode to top level mode)
   public void SelectFolder(String folderName) {
      debug.print("folderName=" + folderName);
      for (int i=0; i<NowPlaying.getRoot().getChildren().size(); ++i) {
         TreeItem<Tabentry> item = NowPlaying.getRoot().getChildren().get(i);
         sortableDate s = item.getValue().getDATE();
         if (s.folder) {
            if (s.folderName.equals(folderName)) {
               NowPlaying.clearSelection();
               NowPlaying.select(item);
               return;
            }
         }
      }
   }

   private void selectRowWithData(Hashtable<String,String> data) {
      if (data.containsKey("folderName"))
         SelectFolder(data.get("folderName"));
      else {
         // Step through all table rows looking for entry with matching ProgramId_unique to select
         if (data.containsKey("ProgramId_unique")) {
            for (int i=0; i<NowPlaying.getExpandedItemCount(); ++i) {
               TreeItem<Tabentry> item = NowPlaying.getTreeItem(i);
               if (! item.isLeaf()) {
                  // Folder entry
                  for (int j=0; j<item.getChildren().size(); ++j) {
                     TreeItem<Tabentry> subitem = item.getChildren().get(j);
                     sortableDate r = subitem.getValue().getDATE();
                     if (r!= null && r.data != null && r.data.containsKey("ProgramId_unique")) {
                        if (r.data.get("ProgramId_unique").equals(data.get("ProgramId_unique"))) {
                           NowPlaying.select(subitem);
                           return;
                        }
                     }
                  }
               } else {
                  // Non-folder entry
                  sortableDate r = item.getValue().getDATE();
                  if (r!= null && r.data != null && r.data.containsKey("ProgramId_unique")) {
                     if (r.data.get("ProgramId_unique").equals(data.get("ProgramId_unique"))) {
                        NowPlaying.select(item);
                        return;
                     }
                  }
               }
            }
         }
      }
   }

   // Remove NPL entry containing given recordingId
   public void RemoveEntry(String recordingId) {
      log.print("RemoveEntry " + recordingId);
      for (int i=0; i<NowPlaying.getExpandedItemCount(); ++i) {
         TreeItem<Tabentry> item = NowPlaying.getTreeItem(i);
         if (! item.isLeaf()) {
            // Folder entry
            for (int j=0; j<item.getChildren().size(); ++j) {
               TreeItem<Tabentry> subitem = item.getChildren().get(j);
               sortableDate r = subitem.getValue().getDATE();
               if (r != null && r.data != null && r.data.containsKey("recordingId")) {
                  if (r.data.get("recordingId").equals(recordingId)) {
                     subitem.getParent().removeChild(subitem);
                     displayTotals(getTotalsString(entries));
                     return;
                  }
               }
            }
         } else {
            // Non-folder entry
            sortableDate r = item.getValue().getDATE();
            if (r!= null && r.data != null && r.data.containsKey("recordingId")) {
               if (r.data.get("recordingId").equals(recordingId)) {
                  item.getParent().removeChild(item);
                  displayTotals(getTotalsString(entries));
                  return;
               }
            }
         }
      }
   }

   public void SetHeaderText(String text, int col) {
      NowPlaying.setColumnName(col, text);
   }

   @SuppressWarnings("unchecked")
   private void RemoveUrls(LinkedHashMap<String,Integer> urls) {
      // First update table
      Stack<Hashtable<String,String>> copy = (Stack<Hashtable<String, String>>) entries.clone();
      entries.clear();
      Boolean include;
      for (int i=0; i<copy.size(); ++i) {
         include = true;
         if (copy.get(i).containsKey("url")) {
            for (String url : urls.keySet()) {
               if (copy.get(i).get("url").equals(url)) {
                  include = false;
               }
            }
         }
         if (include) {
            entries.add(copy.get(i));
         }
      }
      folderize(entries);

      // TWP delete calls
      for (String url : urls.keySet()) {
         file.TivoWebPlusDelete(url);
         int row = urls.get(url);
         if (row != -1)
            RemoveRow(row);
         // Intentionally put a delay here
         try {
            Thread.sleep(2000);
         } catch (InterruptedException e) {}
      }
   }

   @SuppressWarnings("unchecked")
   private void RemoveIds(LinkedHashMap<String,Integer> urls, LinkedHashMap<String,Integer> ids) {
      // First update table
      Stack<Hashtable<String,String>> copy = (Stack<Hashtable<String, String>>) entries.clone();
      entries.clear();
      Boolean include;
      for (int i=0; i<copy.size(); ++i) {
         include = true;
         if (copy.get(i).containsKey("url")) {
            for (String url : urls.keySet()) {
               if (copy.get(i).get("url").equals(url)) {
                  include = false;
               }
            }
         }
         if (include) {
            entries.add(copy.get(i));
         }
      }
      folderize(entries);

      // Remote delete calls
      JSONArray a = new JSONArray();
      JSONObject json = new JSONObject();
      for (String id : ids.keySet()) {
         a.put(id);
      }
      try {
         json.put("recordingId", a);
         Remote r = config.initRemote(tivoName);
         if (r.success) {
            if (r.Command("Delete", json) != null) {
               for (String id : ids.keySet()) {
                  int row = ids.get(id);
                  if (row != -1)
                     RemoveRow(row);
               }
            }
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

   private void PlayShow(JSONArray ids, Stack<String> titles) {
      JSONObject json = new JSONObject();
      try {
         if (ids.length() == 1) {
            log.warn("Playing show on TiVo '" + tivoName + "': " + titles.get(0));
            json.put("id", ids.get(0));
            Remote r = config.initRemote(tivoName);
            if (r.success) {
               r.Command("Playback", json);
               r.disconnect();
            }
         } else {
            log.warn("Playing group of shows on TiVo '" + tivoName + "':");
            for (int i=0; i<titles.size(); ++i)
               log.warn(titles.get(i));
            json.put("id", ids);
            Remote r = config.initRemote(tivoName);
            if (r.success) {
               r.Command("Playgroup", json);
               r.disconnect();
            }
         }
      } catch (JSONException e) {
         log.print("PlayShow failed - " + e.getMessage());
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
      Tabentry entry = null;
      for (int row=0; row<NowPlaying.getExpandedItemCount(); row++) {
         sortableDate s = NowPlaying.getTreeItem(row).getValue().getDATE();
         if (s != null && s.data != null) {
            if (s.data.containsKey("url_TiVoVideoDetails")) {
               String source = s.data.get("url_TiVoVideoDetails");
               if (map.containsKey(source)) {
                  // Has associated queued or running job, so set special icon
                  entry = NowPlaying.getTreeItem(row).getValue();
                  entry.setIMAGE(gui.Images.get(map.get(source)));
                  NowPlaying.getTreeItem(row).setValue(entry);
               } else {
                  // Has no associated queued or running job so reset icon
                  entry = NowPlaying.getTreeItem(row).getValue();
                  entry.setIMAGE(null);
                  NowPlaying.getTreeItem(row).setValue(entry);

                  // Set to ExpirationImage icon if available
                  if ( s.data.containsKey("ExpirationImage") ) {
                     entry = NowPlaying.getTreeItem(row).getValue();
                     entry.setIMAGE(gui.Images.get(s.data.get("ExpirationImage")));
                     NowPlaying.getTreeItem(row).setValue(entry);
                  }
               }
            }
         }
      }
      UpdatingNPL = false;
   }

   // Identify NPL table items containing skip data
   public void updateSkipStatus(String contentId) {
      UpdatingNPL = true;
      if (SkipManager.skipEnabled()) {
         skipEntries = SkipManager.getEntries();
      }
      for (int row=0; row<NowPlaying.getExpandedItemCount(); row++) {
         sortableDate s = NowPlaying.getTreeItem(row).getValue().getDATE();
         if (s != null && s.data != null) {
            if (s.data.containsKey("contentId")) {
               Tabentry entry = NowPlaying.getTreeItem(row).getValue();
               entry.getIMAGE().setLabel(getPctWatched(s.data));
               NowPlaying.getTreeItem(row).setValue(entry);
            }
         }
      }
      UpdatingNPL = false;
   }

   private String getValueAt(int row, int col) {
      Object o = NowPlaying.getValueAt(row, col);
      return o == null ? "" : o.toString();
   }

   private String getPctWatched(Hashtable<String,String> data) {
      String pct = "";
      if (data.containsKey("ByteOffset") && data.containsKey("size")) {
         if (! data.get("ByteOffset").startsWith("0")) {
            int percent = (int)(100*Double.valueOf(data.get("ByteOffset"))/Double.valueOf(data.get("size")));
            pct = "" + percent + "%";
         }
      }

      if (data.containsKey("TimeOffset") && data.containsKey("duration")) {
         if (! data.get("TimeOffset").startsWith("0")) {
            Double secs = Double.valueOf(data.get("TimeOffset"));
            Double duration = Double.valueOf(data.get("duration"))/1000;
            int percent = (int)(100*secs/duration);
            pct = "" + percent + "%";
         }
      }

      // If this offerId has an entry in AutoSkip.ini then mark with an S
      if (data.containsKey("offerId") && skipEntries != null) {
         for (int i=0; i<skipEntries.length(); ++i) {
            try {
               if (data.containsKey("offerId") && skipEntries.getJSONObject(i).has("offerId")) {
                  if (skipEntries.getJSONObject(i).getString("offerId").equals(data.get("offerId"))) {
                     pct = "S " + pct;
                  }
               }
            } catch (JSONException e) {
               log.error("getPctWatched - " + e.getMessage());
            }
         }
      }
      return pct;
   }

   // Export NPL entries to a csv file
   // NOTE: The current table structure + sorting is used
   public void exportNPL(String file) {
      int numCols = TIVO_cols.length;
      try {
         if (NowPlaying != null && NowPlaying.getRoot().getChildren().size() > 0) {
            log.warn("Exporting NPL table for: " + tivoName + " ...");
            BufferedWriter ofp = new BufferedWriter(new FileWriter(file));
            int col = 0;
            for (int c=0; c<numCols; ++c) {
               String name = NowPlaying.getColumnName(c);
               if (name.equals(""))
                  continue;
               if (name.equals("DUR"))
                  name = "DURATION";
               if (name.equals("Mbps"))
                  name = "BITRATE (Mbps)";
               if (name.equals("SIZE"))
                  name = "SIZE (GB)";
               ofp.write(name);
               if (col<numCols-1)
                  ofp.write(",");
               if (name.equals("DATE")) {
                  ofp.write("SORTABLE DATE");
                  if (col<numCols-1)
                     ofp.write(",");
               }
               col++;
            }
            ofp.write("\r\n");
            for (int row=0; row<NowPlaying.getExpandedItemCount(); row++) {
               for (int c=0; c<numCols; ++c) {
                  String colName = NowPlaying.getColumnName(c);
                  if (colName.equals("")) {
                     continue;
                  }
                  String val = getValueAt(row, c);
                  if (val == null) val = "";
                  Tabentry e = NowPlaying.getTreeItem(row).getValue();
                  val = val.trim();
                  if (val.equals("")) {
                     val = "NONE";
                     if (colName.equals("DUR") || colName.equals("SIZE") || colName.equals("Mbps"))
                        val = "0";
                  }
                  if (colName.equals("SIZE"))
                     val = val.replaceFirst(" GB", "");
                  ofp.write("\"" + val + "\",");
                  if (colName.equals("DATE")) {
                     // This is the SORTABLE DATE column
                     ofp.write("\"" + TableUtil.getSortableDate(e.date) + "\",");
                  }
               }
               ofp.write("\r\n");
            }
            ofp.close();
            log.warn("NPL csv export completed to file: " + file);
         } else {
            log.error("You must 1st refresh NPL");
         }
      } catch (IOException e) {
         log.error("exportNPL - " + e.getMessage());
      }
   }

   private void visualDetect(Integer[] selected) {
      if (config.visualDetect_running) {
         log.error("Please wait until current 'AutoSkip from SkipMode' run finishes before starting another");
         return;
      }
      Stack<Hashtable<String,String>> stack = new Stack<Hashtable<String,String>>();
      for (int i=0; i<selected.length; ++i) {
         int row = selected[i];
         sortableDate s = NowPlaying.getTreeItem(row).getValue().getDATE();
         if (s.folder) continue;
         if (s.data.containsKey("clipMetadataId") && s.data.containsKey("contentId")) {
            Boolean go = true;
            if (SkipManager.hasEntry(s.data.get("contentId"))) {
               // Confirmation dialog defaulting to Cancel
               Object[] options = {"OK", "Cancel"};
               int result = JOptionPane.showOptionDialog(
                  config.gui.getFrame(),
                  "Override existing AutoSkip data?", "Confirm",
                  JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
                  null, options, options[1]
               );
               go = result == JOptionPane.OK_OPTION;
            }
            if (go) {
               stack.push(s.data);
            }
         } else {
            log.error("No SkipMode data available for this show - skipping");
         }
      } // for
      if (stack.isEmpty())
         log.warn("No valid entries found to process in selected set");
      else
         SkipManager.visualDetect(tivoName, stack, true);
   }

   private void visualDetectAll() {
      // Build stack of eligible entries to run visualDetect on
      Stack<Hashtable<String,String>> stack = new Stack<Hashtable<String,String>>();
      for (Hashtable<String,String> entry : entries) {
         if ( ! shouldHideEntry(entry) && entry.containsKey("contentId")) {
            if (entry.containsKey("clipMetadataId") && ! SkipManager.hasEntry(entry.get("contentId"))) {
               stack.push(entry);
            }
         }
      }
      if (stack.isEmpty()) {
         log.error("No entries found for processing AutoSkip from SkipMode");
      } else {
         log.warn("" + stack.size() + " entries found to process for AutoSkip from SkipMode:");
         for (Hashtable<String,String> entry : stack)
            log.warn("   " + entry.get("title"));
         Object[] options = {"OK", "Cancel"};
         int result = JOptionPane.showOptionDialog(
            config.gui.getFrame(),
            "Run AutoSkip from SkipMode for " + stack.size() + " entries (show in message window)?",
            "Confirm",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
            null, options, options[1]
         );
         if (result != JOptionPane.OK_OPTION) {
            return;
         }
         SkipManager.visualDetect(tivoName, stack, true);
      }
   }

}
