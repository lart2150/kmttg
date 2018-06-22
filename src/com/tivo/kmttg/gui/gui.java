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
package com.tivo.kmttg.gui;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Stack;
import java.util.Timer;
import java.util.TimerTask;

import org.w3c.dom.NodeList;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.scene.web.WebView;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import com.sun.javafx.css.StyleManager;
import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
//import com.tivo.kmttg.gui.dialog.Pushes;
import com.tivo.kmttg.gui.dialog.ShowDetails;
import com.tivo.kmttg.gui.dialog.SkipDialog;
import com.tivo.kmttg.gui.dialog.autoLogView;
import com.tivo.kmttg.gui.dialog.configAuto;
import com.tivo.kmttg.gui.dialog.configMain;
import com.tivo.kmttg.gui.remote.remotegui;
import com.tivo.kmttg.gui.remote.util;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.table.jobTable;
import com.tivo.kmttg.gui.table.nplTable;
import com.tivo.kmttg.gui.table.nplTable.Tabentry;
import com.tivo.kmttg.install.mainInstall;
import com.tivo.kmttg.install.update;
import com.tivo.kmttg.main.auto;
import com.tivo.kmttg.main.autoConfig;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.encodeConfig;
import com.tivo.kmttg.main.jobData;
import com.tivo.kmttg.main.jobMonitor;
import com.tivo.kmttg.main.kmttg;
import com.tivo.kmttg.rpc.SkipManager;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class gui extends Application {

   private Stage jFrame = null;
   private configAuto config_auto = null;
   private String title = config.kmttg;
   private SplitPane jContentPane = null;
   private SplitPane splitBottom = null;
   private TabPane tabbed_panel = null;
   private MenuBar menuBar = null;
   private Menu fileMenu = null;
   private Menu jobMenu = null;
   private Menu autoMenu = null;
   private Menu serviceMenu = null;
   private Menu helpMenu = null;
   private MenuItem helpAboutMenuItem = null;
   private MenuItem helpUpdateMenuItem = null;
   private MenuItem helpToolsUpdateMenuItem = null;
   private MenuItem exitMenuItem = null;
   private MenuItem autoConfigMenuItem = null;
   private MenuItem runInGuiMenuItem = null;
   private CheckMenuItem loopInGuiMenuItem = null;
   private CheckMenuItem resumeDownloadsMenuItem = null;
   private CheckMenuItem toggleLaunchingJobsMenuItem = null;
   public  MenuItem addSelectedTitlesMenuItem = null;
   public  MenuItem addSelectedHistoryMenuItem = null;
   private MenuItem logFileMenuItem = null;
   private MenuItem configureMenuItem = null;
   private MenuItem refreshEncodingsMenuItem = null;
   private MenuItem serviceStatusMenuItem = null;
   private MenuItem serviceInstallMenuItem = null;
   private MenuItem serviceStartMenuItem = null;
   private MenuItem serviceStopMenuItem = null;
   private MenuItem serviceRemoveMenuItem = null;
   private MenuItem backgroundJobStatusMenuItem = null;
   private MenuItem backgroundJobEnableMenuItem = null;
   private MenuItem backgroundJobDisableMenuItem = null;
   private MenuItem saveMessagesMenuItem = null;
   private MenuItem clearMessagesMenuItem = null;
   //private MenuItem resetServerMenuItem = null;
   //private MenuItem pushesMenuItem = null;
   private MenuItem saveJobsMenuItem = null;
   private MenuItem loadJobsMenuItem = null;
   private MenuItem metadataMenuItem = null;
   public MenuItem searchMenuItem = null;
   private MenuItem autoSkipMenuItem = null;
   private Menu autoSkipServiceMenu = null;
   public MenuItem thumbsMenuItem = null;
   
   private ComboBox<String> encoding = null;
   private Label encoding_label = null;
   private Label encoding_description_label = null;
   public Button start = null;
   public Button cancel = null;
   public CheckBox metadata = null;
   public CheckBox decrypt = null;
   public CheckBox qsfix = null;
   public CheckBox twpdelete = null;
   public CheckBox rpcdelete = null;
   public CheckBox comskip = null;
   public CheckBox comcut = null;
   public CheckBox captions = null;
   public CheckBox encode = null;
   //public CheckBox push = null;
   public CheckBox custom = null;
   private WebView text = null;
   private textpane textp = null;
   private jobTable jobTab = null;
   private ProgressBar progressBar = null;
   public  ScrollPane jobPane = null;
   
   private Hashtable<String,tivoTab> tivoTabs = new Hashtable<String,tivoTab>();
   public static Hashtable<String,Image> Images;
   
   public remotegui remote_gui = null;
   public slingboxgui  slingbox_gui = null;
   
   public ShowDetails show_details = null;
   
   public Stage getFrame() {
      debug.print("");
      return jFrame;
   }
   
   public tivoTab getTab(String tabName) {
      debug.print("tabName=" + tabName);
      return tivoTabs.get(tabName);
   }
   
   public void Launch() {
      debug.print("");
      launch();
   }
   
   @Override
   public void start(Stage stage) {
      debug.print("stage=" + stage);
      jFrame = stage;
      Scene scene = new Scene(new VBox());
      MenuBar menubar = getMenuBar();
      
      // Load icons for system usage
      LoadIcons(jFrame);
      
      // Build main canvas components
      getContentPane();
      config.gui = this;
            
      VBox main_canvas = new VBox();
      main_canvas.setSpacing(5);
      main_canvas.getChildren().add(jContentPane);
      ((VBox) scene.getRoot()).getChildren().addAll(menubar, main_canvas);
      
      // Add additional rpc remote tab
      remote_gui = new remotegui(jFrame);
      addTabPane("Remote", tabbed_panel, remote_gui.getPanel());
      
      // Init TableMap utility class
      TableMap.init();
      
      setFontSize(scene, config.FontSize);
      jobTab_packColumns(5);
      addGlobalKeyListener(scene);
      jFrame.setScene(scene);      
      jFrame.setTitle(title);
      jFrame.setOnCloseRequest(new EventHandler<WindowEvent>() {
         @Override
         public void handle(WindowEvent event) {
            saveSettings();
            System.exit(0);
         }
      });
      jFrame.setWidth(1000);
      jFrame.setHeight(800);

      // Pack table columns when content pane resized
      scene.widthProperty().addListener(new ChangeListener<Number>() {
         @Override
         public void changed(ObservableValue<? extends Number> observableValue, Number oldSceneWidth, Number newSceneWidth) {
            TableUtil.autoSizeTableViewColumns(jobTab.JobMonitor, false);
         }
      });
      
      // Restore last GUI run settings from file
      Platform.runLater(new Runnable() {
         @Override public void run() {
            readSettings();               
            // Enable/disable options according to configuration
            refreshOptions(true);
         }
      });
      
      // Create and enable/disable component tooltips
      MyTooltip.enableToolTips(config.toolTips);
      setToolTips();
      
      // Set master flag indicating that kmttg is running in GUI mode
      config.GUIMODE = true;
      
      jFrame.show();
      
      // Initialize AutoSkip entries with possible auto starts
      // to after configuration reads and gui setups.
      for (int i = 0; i < config.getTivoNames().size(); i++) {
          String tivoName = config.getTivoNames().get(i);
          if (config.rpcEnabled(tivoName)) {
              // AutoSkip service capable
              config.gui.addAutoSkipServiceItem(tivoName);
          }
      }

      
      // Create NowPlaying icons
      CreateImages();
      
      // Init show_details dialog
      show_details = new ShowDetails(jFrame, null);
      
      // Start NPL jobs
      if (config.npl_when_started == 1) {
         Platform.runLater(new Runnable() {
            @Override public void run() {
               initialNPL(config.TIVOS);               
            }
         });
      }
      
      config.gui = this;
      
      setLookAndFeel(config.lookAndFeel);
      
      // Download tools if necessary
      mainInstall.install();

      // Invoke a 1000ms period timer for job monitor
      kmttg.timer = new Timer();
      kmttg.timer.schedule(
         new TimerTask() {
             @Override
             public void run() {
                Platform.runLater(new Runnable() {
                   @Override public void run() {
                      jobMonitor.monitor(config.gui);
                   }
                });
             }
         }
         ,0,
         1000
      );
      
      // Upon startup, try and load saved queue
      /* Intentionally disabled - only do this for auto transfers mode now
      if (config.persistQueue)
         jobMonitor.loadAllJobs(10);   // delay load to give gui time to setup*/
      kmttg._startingUp = false;
   }
   
   // Adds a universal key listener so that menu shortcuts work as expected
   public void addGlobalKeyListener(Scene scene) {
      debug.print("scene=" + scene);
      scene.setOnKeyPressed(new EventHandler<KeyEvent>() {
         public void handle(KeyEvent e) {
            String tabName = getCurrentTabName();
            if (tabName.equals("Remote")) {
               String subTabName = config.gui.remote_gui.getCurrentTabName();
               if (subTabName.equals("Remote")) {
                  // For Remote-Remote tab don't want to interfere with anything
                  return;
               }
            }
            
            // Proceed with handling menu keyboard accelerators
            if (e.getEventType() == KeyEvent.KEY_PRESSED && e.isControlDown()) {
               if (e.getCode() == KeyCode.L) {
                  clearMessagesMenuItem.fire();
                  e.consume();
               }
               if (e.getCode() == KeyCode.E) {
                  refreshEncodingsMenuItem.fire();
                  e.consume();
               }
               if (e.getCode() == KeyCode.M) {
                  saveMessagesMenuItem.fire();
                  e.consume();
               }
               if (e.getCode() == KeyCode.O) {
                  configureMenuItem.fire();
                  e.consume();
               }
               if (e.getCode() == KeyCode.R) {
                  metadataMenuItem.fire();
                  e.consume();
               }
               if (e.getCode() == KeyCode.S) {
                  searchMenuItem.fire();
                  e.consume();
               }
               if (e.getCode() == KeyCode.T) {
                  thumbsMenuItem.fire();
                  e.consume();
               }
            }
         }
      });           
   }

   public void setFontSize(Scene scene, int fontSize) {
      debug.print("scene=" + scene + " fontSize=" + fontSize);
      scene.getRoot().setStyle("-fx-font-size: " + fontSize + "pt;");
      //listFontFamilies();
   }

   public void setFontSize(Dialog<?> dialog, int fontSize) {
      debug.print("dialog=" + dialog + " fontSize=" + fontSize);
      dialog.getDialogPane().setStyle("-fx-font-size: " + fontSize + "pt;");
   }

   public void setFontSize(Alert alert, int fontSize) {
      debug.print("alert=" + alert + " fontSize=" + fontSize);
      alert.getDialogPane().setStyle("-fx-font-size: " + fontSize + "pt;");
   }
   
   public void listFontFamilies() {
      debug.print("");
      List<String> familiesList = Font.getFamilies();
      for (String family : familiesList) {
         System.out.println(family);
      }
   }
   
   public void setLookAndFeel(String name) {
      debug.print("name=" + name);
      if (name == null)
         name = "default.css";
      if (!name.endsWith(".css"))
         name += ".css";
      config.cssFile = name;
      File f = new File(config.cssDir + File.separator + config.cssFile);
      if ( ! f.exists() ) {
         log.warn("Unable to load css file: " + f.getAbsolutePath());
         config.cssFile = "default.css";
         f = new File(config.cssDir + File.separator + config.cssFile);         
         log.warn("Trying alternate default file: " + config.cssFile);
      }
      if (f.exists()) {
         // NOTE: This css will apply to any/all Stages
         Application.setUserAgentStylesheet(null);
         StyleManager.getInstance().addUserAgentStylesheet(f.toURI().toString());
      } else {
         log.error("Unable to load css file: " + f.getAbsolutePath());
      }
   }
   
   public List<String> getAvailableLooks() {
      debug.print("");
      // Parse available css files in css dir
      String dir = config.cssDir;
      
      File d = new File(dir);
      if ( ! d.isDirectory() ) {
         log.error("css dir not valid: " + dir);
         return null;
      }
      FilenameFilter filter = new FilenameFilter() {
         public boolean accept(File dir, String name) {
            debug.print("dir=" + dir + " name=" + name);
            File d = new File(dir.getPath() + File.separator + name);
            if (d.isDirectory()) {
               return false;
            }
            // .css files
            if ( name.toLowerCase().endsWith(".css") ) {
               if (name.toLowerCase().equals("kmttg.css"))
                  return false;
               return true;
            }
            return false;
         }
      };
     
      // Define list of filter entries
      List<String> css_list = new ArrayList<String>();
      File[] files = d.listFiles(filter);
      for (int i=0; i<files.length; i++) {
         css_list.add(files[i].getName());
      }
      
      // Sort encode list alphabetically
      Collections.sort(css_list);
      return css_list;
   }
   
   public void grabFocus() {
      debug.print("");
      if (jFrame != null)
         if(! jFrame.isFocused()) { jFrame.requestFocus(); }
   }
   
   private SplitPane getContentPane() {
      debug.print("");
      if (jContentPane == null) {
                  
         // CANCEL JOBS button
         cancel = new Button("CANCEL JOBS");
         cancel.setPadding(new Insets(1,2,1,2));
         cancel.setTooltip(getToolTip("cancel"));
         cancel.setMinWidth(100);
         cancel.setId("button_job_cancel");
         cancel.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               cancelCB();
            }
         });

         // START JOBS button
         start = new Button("START JOBS");
         start.setPadding(new Insets(1,2,1,2));
         start.setTooltip(getToolTip("start"));
         start.setId("button_job_start");
         start.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               String tivoName = getCurrentTabName();
               if (tivoName.equals("Remote"))
                  log.error("START JOBS invalid with Remote tab selected.");
               else
                  tivoTabs.get(tivoName).startCB();
            }
         });
         
         // Tasks
         metadata = new CheckBox("metadata"); metadata.setSelected(false);
         decrypt = new CheckBox("decrypt"); decrypt.setSelected(true);        
         qsfix = new CheckBox("QS Fix"); qsfix.setSelected(false);        
         qsfix.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               refreshOptions(false);
            }
         });
         twpdelete = new CheckBox("TWP Delete"); twpdelete.setSelected(false);        
         rpcdelete = new CheckBox("rpc Delete");  rpcdelete.setSelected(false);       
         comskip = new CheckBox("Ad Detect"); comskip.setSelected(false);        
         comcut = new CheckBox("Ad Cut"); comcut.setSelected(false);        
         captions = new CheckBox("captions"); captions.setSelected(false);        
         encode = new CheckBox("encode"); encode.setSelected(false);
         //push = new CheckBox("push"); push.setSelected(false);
         custom = new CheckBox("custom"); custom.setSelected(false);
         
         // Tasks row
         HBox tasks_panel = new HBox();
         tasks_panel.setAlignment(Pos.CENTER_LEFT);
         tasks_panel.setPadding(new Insets(0,0,0,5));
         tasks_panel.setSpacing(5);
         tasks_panel.getChildren().add(start);
         tasks_panel.getChildren().add(util.space(5));
         tasks_panel.getChildren().add(metadata);
         tasks_panel.getChildren().add(decrypt);
         tasks_panel.getChildren().add(qsfix);
         if (config.twpDeleteEnabled()) {
            tasks_panel.getChildren().add(twpdelete);            
         }
         if (config.rpcDeleteEnabled()) {
            tasks_panel.getChildren().add(rpcdelete);            
         }
         tasks_panel.getChildren().add(comskip);
         tasks_panel.getChildren().add(comcut);
         tasks_panel.getChildren().add(captions);
         tasks_panel.getChildren().add(encode);
         tasks_panel.getChildren().add(custom);
         //tasks_panel.getChildren().add(push);
         
         // Encoding row
         // Encoding label
         encoding_label = new Label("Encoding Profile:");
         encoding_label.setTextAlignment(TextAlignment.CENTER);
 
         // Encoding names combo box
         encoding = new ComboBox<String>();
         SetEncodings(encodeConfig.getValidEncodeNames());
         encoding.valueProperty().addListener(new ChangeListener<String>() {
            @Override public void changed(ObservableValue<? extends String> ov, String oldVal, String newVal) {
               if (newVal != null) {
                  encodingCB(encoding);
               }
            }
         });

         // Encoding description label
         String description = "";
         if (encodeConfig.getValidEncodeNames().size() > 0) {
            description = "  " + encodeConfig.getDescription(encodeConfig.getEncodeName());
         }
         encoding_description_label = new Label(description);

         HBox encoding_panel = new HBox();
         encoding_panel.setAlignment(Pos.CENTER_LEFT);
         encoding_panel.setPadding(new Insets(0,0,0,5));
         encoding_panel.setSpacing(5);
         encoding_panel.getChildren().add(encoding_label);
         encoding_panel.getChildren().add(encoding);
         encoding_panel.getChildren().add(encoding_description_label);
         
         // Job Monitor table
         jobTab = new jobTable();
         jobPane = new ScrollPane(jobTab.JobMonitor);
         jobPane.setFitToHeight(true);
         jobPane.setFitToWidth(true);
         
         // Progress Bar
         progressBar = new ProgressBar();
         progressBar.setId("progressbar_job");
         progressBar.setProgress(0);

         // Message area
         text = new WebView();
         textp = new textpane(text);
                  
         // Tabbed panel
         tabbed_panel = new TabPane();
         tabbed_panel.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
         tabbed_panel.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<Tab>() {
            @Override public void changed(ObservableValue<? extends Tab> ov, Tab oldVal, Tab newVal) {
               if (getCurrentTabName() != null && getCurrentTabName().equals("Remote")) {
                  // Set focus on remote pane
                  remote_gui.tabbed_panel.requestFocus();
               }
            }
         });

         // Add permanent tabs
         tivoTabs.put("FILES", new tivoTab("FILES"));
         addTabPane("FILES", tabbed_panel, tivoTabs.get("FILES").getPanel());
         
         // Add Tivo tabs
         SetTivos(config.TIVOS);
         
         // Cancel pane
         HBox cancel_pane_stretch = new HBox();
         cancel_pane_stretch.getChildren().add(progressBar);
         //cancel_pane_stretch.setAlignment(Pos.CENTER_LEFT);
         HBox cancel_pane = new HBox();
         cancel_pane.setPadding(new Insets(0,0,0,5));
         cancel_pane.setSpacing(5);
         cancel_pane.getChildren().addAll(cancel, cancel_pane_stretch);
         cancel.setMinWidth(Button.USE_PREF_SIZE); // Don't truncate button text
         HBox.setHgrow(cancel_pane_stretch, Priority.ALWAYS);  // stretch horizontally
         // Bind progressBar width to cancel_pane_stretch width so it will grow horizontally
         progressBar.prefWidthProperty().bind(
            cancel_pane.widthProperty().subtract(cancel.widthProperty())
         );
         
         // Create a split pane between job & messages pane
         splitBottom = new SplitPane();
         splitBottom.setOrientation(Orientation.VERTICAL);
         splitBottom.getItems().add(jobPane);
         splitBottom.getItems().add(text);
         splitBottom.setDividerPosition(0, 0.55);
         
         // bottomPane will consist of cancel_pane & splitBottom
         VBox bottomPane = new VBox();         
         bottomPane.getChildren().add(cancel_pane);
         bottomPane.getChildren().add(splitBottom);
         
         // topPane will consist of tasks & tabbed_panel
         VBox topPane = new VBox();
         topPane.getChildren().add(tasks_panel);
         HBox.setHgrow(tasks_panel, Priority.ALWAYS);  // stretch horizontally
         topPane.getChildren().add(encoding_panel);         
         topPane.getChildren().add(tabbed_panel);
         VBox.setVgrow(tabbed_panel, Priority.ALWAYS); // stretch vertically
         
         // Put all panels together
         jContentPane = new SplitPane();
         jContentPane.setOrientation(Orientation.VERTICAL);
         jContentPane.getItems().add(topPane);
         jContentPane.getItems().add(bottomPane);
         jContentPane.setDividerPosition(0, 0.57);
      }
      
      return jContentPane;
   }
   
   private MenuBar getMenuBar() {
      debug.print("");
      if (menuBar == null) {
         menuBar = new MenuBar();
         menuBar.getMenus().addAll(getFileMenu(), getAutoTransfersMenu());
         menuBar.getMenus().add(getHelpMenu());
      }
      return menuBar;
   }

   private Menu getFileMenu() {
      debug.print("");
      if (fileMenu == null) {
         fileMenu = new Menu("File");
         fileMenu.getItems().add(getConfigureMenuItem());
         fileMenu.getItems().add(getRefreshEncodingsMenuItem());
         fileMenu.getItems().add(getSaveMessagesMenuItem());
         fileMenu.getItems().add(getClearMessagesMenuItem());
         //fileMenu.getItems().add(getResetServerMenuItem());
         //if (config.getTivoUsername() != null)
         //   fileMenu.getItems().add(getPushesMenuItem());
         fileMenu.getItems().add(getResumeDownloadsMenuItem());
         fileMenu.getItems().add(getJobMenu());
         fileMenu.getItems().add(getMetadataMenuItem());
         fileMenu.getItems().add(getSearchMenuItem());
         if (config.rpcEnabled() && SkipManager.skipEnabled()) {
            fileMenu.getItems().add(getAutoSkipMenuItem());
            fileMenu.getItems().add(getAutoSkipServiceMenu());
         }
         //fileMenu.add(getThumbsMenuItem());
         // Create thumbs menu item but don't add to File menu
         getThumbsMenuItem();
         fileMenu.getItems().add(getExitMenuItem());
      }
      return fileMenu;
   }
   
   private Menu getJobMenu() {
      debug.print("");
      if (jobMenu == null) {
         jobMenu = new Menu("Jobs");
         jobMenu.getItems().add(getToggleLaunchingJobsMenuItem());
         jobMenu.getItems().add(getSaveJobsMenuItem());
         jobMenu.getItems().add(getLoadJobsMenuItem());
      }
      return jobMenu;
   }

   private Menu getAutoTransfersMenu() {
      debug.print("");
      if (autoMenu == null) {
         autoMenu = new Menu("Auto Transfers");
         autoMenu.getItems().add(getAutoConfigMenuItem());
         if (config.OS.equals("windows"))
            autoMenu.getItems().add(getServiceMenu());
         else
            autoMenu.getItems().add(getBackgroundJobMenu());
         autoMenu.getItems().add(getAddSelectedTitlesMenuItem());
         autoMenu.getItems().add(getAddSelectedHistoryMenuItem());
         autoMenu.getItems().add(getLogFileMenuItem());
         autoMenu.getItems().add(getRunInGuiMenuItem());
         autoMenu.getItems().add(getLoopInGuiMenuItem());
      }
      return autoMenu;
   }

   private Menu getHelpMenu() {
      debug.print("");
      if (helpMenu == null) {
         helpMenu = new Menu("Help");
         helpMenu.getItems().add(getHelpAboutMenuItem());
         helpMenu.getItems().add(getHelpUpdateMenuItem());
         if (config.OS.equals("windows") || config.OS.equals("mac"))
            helpMenu.getItems().add(getHelpToolsUpdateMenuItem());
      }
      return helpMenu;
   }

   private MenuItem getHelpAboutMenuItem() {
      debug.print("");
      if (helpAboutMenuItem == null) {
         helpAboutMenuItem = new MenuItem();
         helpAboutMenuItem.setText("About...");
         helpAboutMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               help.showHelp();
            }
         });
      }
      return helpAboutMenuItem;
   }

   private MenuItem getHelpUpdateMenuItem() {
      debug.print("");
      if (helpUpdateMenuItem == null) {
         helpUpdateMenuItem = new MenuItem();
         helpUpdateMenuItem.setText("Update kmttg...");
         helpUpdateMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               update.update_kmttg_background();
            }
         });
      }
      return helpUpdateMenuItem;
   }

   private MenuItem getHelpToolsUpdateMenuItem() {
      debug.print("");
      if (helpToolsUpdateMenuItem == null) {
         helpToolsUpdateMenuItem = new MenuItem();
         helpToolsUpdateMenuItem.setText("Update tools...");
         helpToolsUpdateMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               update.update_tools_background();
            }
         });
      }
      return helpToolsUpdateMenuItem;
   }

   private MenuItem getExitMenuItem() {
      debug.print("");
      if (exitMenuItem == null) {
         exitMenuItem = new MenuItem();
         exitMenuItem.setText("Exit");
         exitMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               saveSettings();
               System.exit(0);
            }
         });
      }
      return exitMenuItem;
   }

   private MenuItem getAutoConfigMenuItem() {
      debug.print("");
      if (autoConfigMenuItem == null) {
         autoConfigMenuItem = new MenuItem();
         autoConfigMenuItem.setText("Configure...");
         autoConfigMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               if (config_auto == null)
                  config_auto = new configAuto();
               config_auto.display(jFrame);
            }
         });
      }
      return autoConfigMenuItem;
   }

   private MenuItem getSaveMessagesMenuItem() {
      debug.print("");
      if (saveMessagesMenuItem == null) {
         saveMessagesMenuItem = new MenuItem();
         saveMessagesMenuItem.setText("Save messages to file");
         saveMessagesMenuItem.setAccelerator(KeyCombination.keyCombination("Ctrl+M"));
         saveMessagesMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               String file = config.programDir + File.separator + "kmttg.log";
               String eol = "\n";
               if (config.OS.equals("windows"))
                  eol = "\r\n";
               try {
                  NodeList list = text.getEngine().getDocument().getElementById("content").getChildNodes();
                  StringBuilder sb = new StringBuilder();
                  for (int i=0; i<list.getLength(); ++i) {
                     org.w3c.dom.Node node = list.item(i);
                     if (node.getNodeName().equalsIgnoreCase("pre")) {
                        String[] lines = node.getTextContent().split("\n");
                        for (String line : lines)
                           sb.append(line + eol);
                     }
                  }
                  BufferedWriter ofp = new BufferedWriter(new FileWriter(file));
                  ofp.write(sb.toString());
                  ofp.close();
                  log.warn("Saved output messages to file: " + file);
               } catch (IOException ex) {
                  log.error("Problem writing to file: " + file);
               }
            }
         });
      }
      return saveMessagesMenuItem;
   }

   private MenuItem getClearMessagesMenuItem() {
      debug.print("");
      if (clearMessagesMenuItem == null) {
         clearMessagesMenuItem = new MenuItem();
         clearMessagesMenuItem.setText("Clear all messages");
         clearMessagesMenuItem.setAccelerator(KeyCombination.keyCombination("Ctrl+L"));
         clearMessagesMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               textp.clear();
            }
         });
      }
      return clearMessagesMenuItem;
   }

   /*private MenuItem getResetServerMenuItem() {
      debug.print("");
      if (resetServerMenuItem == null) {
         resetServerMenuItem = new MenuItem();
         resetServerMenuItem.setText("Reset TiVo web server");
         resetServerMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               String tivoName = getSelectedTivoName();
               if (tivoName != null) {
                  String urlString = "http://" + config.TIVOS.get(tivoName) + "/TiVoConnect?Command=ResetServer";
                  // Add wan port if configured
                  String wan_port = config.getWanSetting(tivoName, "http");
                  if (wan_port != null)
                     urlString = string.addPort(urlString, wan_port);
                  try {
                     URL url = new URL(urlString);
                     log.warn("Resetting " + tivoName + " TiVo: " + urlString);
                     url.openConnection();
                  }
                  catch(Exception ex) {
                     log.error(ex.toString());
                  }
               } else {
                  log.error("This command must be run with a TiVo tab selected.");
               }
            }
         });
      }
      return resetServerMenuItem;
   }*/

   /*private MenuItem getPushesMenuItem() {
      debug.print("");
      if (pushesMenuItem == null) {
         pushesMenuItem = new MenuItem();
         pushesMenuItem.setText("Show pending pyTivo pushes");
         pushesMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               log.print(config.pyTivo_mind);
               String tivoName = getSelectedTivoName();
               if (tivoName == null)
                  log.error("This command must be run with a TiVo tab selected.");
               else {
                  config.middlemind_host = "middlemind.tivo.com";
                  if (config.pyTivo_mind.startsWith("staging"))
                     config.middlemind_host = "stagingmiddlemind.tivo.com";
                  log.warn("Querying middlemind host: " + config.middlemind_host);
                  new Pushes(tivoName, getFrame());
               }
            }
         });
      }
      return pushesMenuItem;
   }*/
   
   private MenuItem getToggleLaunchingJobsMenuItem() {
      debug.print("");
      if (toggleLaunchingJobsMenuItem == null) {
         toggleLaunchingJobsMenuItem = new CheckMenuItem();
         toggleLaunchingJobsMenuItem.setText("Do not launch queued jobs");
         toggleLaunchingJobsMenuItem.selectedProperty().addListener(new ChangeListener<Boolean>() {
            public void changed(ObservableValue<? extends Boolean> e, Boolean oldVal, Boolean newVal) {
               if (newVal) {
                  jobMonitor.NoNewJobs = true;
                  log.warn("Launching queued jobs disabled. Queued jobs will not be launched.");
               } else {
                  jobMonitor.NoNewJobs = false;
                  log.warn("Launching queued jobs enabled. Resuming normal job processing.");
               }
            }
         });
      }
      return toggleLaunchingJobsMenuItem;
   }

   private MenuItem getSaveJobsMenuItem() {
      debug.print("");
      if (saveJobsMenuItem == null) {
         saveJobsMenuItem = new MenuItem();
         saveJobsMenuItem.setText("Save queued jobs");
         saveJobsMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               jobMonitor.saveQueuedJobs();
            }   
         });
      }
      return saveJobsMenuItem;
   }

   private MenuItem getLoadJobsMenuItem() {
      debug.print("");
      if (loadJobsMenuItem == null) {
         loadJobsMenuItem = new MenuItem();
         loadJobsMenuItem.setText("Load queued jobs");
         loadJobsMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               jobMonitor.loadQueuedJobs();
            }
         });
      }
      return loadJobsMenuItem;
   }
   
   private MenuItem getRunInGuiMenuItem() {
      debug.print("");
      if (runInGuiMenuItem == null) {
         runInGuiMenuItem = new MenuItem();
         runInGuiMenuItem.setText("Run Once in GUI");
         runInGuiMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               autoRunInGUICB();
            }
         });
      }
      return runInGuiMenuItem;
   }
   
   private MenuItem getLoopInGuiMenuItem() {
      debug.print("");
      if (loopInGuiMenuItem == null) {
         loopInGuiMenuItem = new CheckMenuItem();
         loopInGuiMenuItem.setText("Loop in GUI");
         loopInGuiMenuItem.selectedProperty().addListener(new ChangeListener<Boolean>() {
            public void changed(ObservableValue<? extends Boolean> e, Boolean oldVal, Boolean newVal) {
               autoLoopInGUICB(newVal);
            }
         });
      }
      return loopInGuiMenuItem;
   }
   
   private MenuItem getResumeDownloadsMenuItem() {
      debug.print("");
      if (resumeDownloadsMenuItem == null) {
         resumeDownloadsMenuItem = new CheckMenuItem();
         resumeDownloadsMenuItem.setText("Resume Downloads");
         resumeDownloadsMenuItem.selectedProperty().addListener(new ChangeListener<Boolean>() {
            public void changed(ObservableValue<? extends Boolean> e, Boolean oldVal, Boolean newVal) {
               config.resumeDownloads = newVal;
            }
         });
      }
      return resumeDownloadsMenuItem;
   }
   
   private MenuItem getAddSelectedTitlesMenuItem() {
      debug.print("");
      if (addSelectedTitlesMenuItem == null) {
         addSelectedTitlesMenuItem = new MenuItem();
         addSelectedTitlesMenuItem.setText("Add selected titles");
         addSelectedTitlesMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               TableMap tmap = TableMap.getCurrent();
               if (tmap == null || (tmap != null && ! tmap.isRemote())) {
                  // Non remote table
                  tivoTabs.get(getSelectedTivoName()).autoSelectedTitlesCB();
                  return;
               }
               // Processing for remote tables
               if (tmap != null) {
                  int[] selected = tmap.getSelected();
                  if (selected != null && selected.length > 0) {
                     for (int row : selected) {
                        JSONObject json = tmap.getJson(row);
                        if (json != null && json.has("title")) {
                           try {
                              auto.autoAddTitleEntryToFile(json.getString("title"));
                           } catch (JSONException e1) {
                              log.error("Add selected titles json exception - " + e1.getMessage());
                           }
                        }
                     }
                  } else {
                     log.error("No show selected in table");
                     return;                        
                  }
               }
            }
         });
      }
      return addSelectedTitlesMenuItem;
   }

   private MenuItem getAddSelectedHistoryMenuItem() {
      debug.print("");
      if (addSelectedHistoryMenuItem == null) {
         addSelectedHistoryMenuItem = new MenuItem();
         addSelectedHistoryMenuItem.setText("Add selected to history file");
         addSelectedHistoryMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               String tivoName = getSelectedTivoName();
               if (tivoName != null) {
                  tivoTabs.get(tivoName).autoSelectedHistoryCB();
               } else {
                  log.error("This command must be run from a TiVo tab with selected tivo shows.");
               }
            }
         });
      }
      return addSelectedHistoryMenuItem;
   }

   private MenuItem getLogFileMenuItem() {
      debug.print("");
      if (logFileMenuItem == null) {
         logFileMenuItem = new MenuItem();
         logFileMenuItem.setText("Examine log file...");
         logFileMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               new autoLogView(jFrame);
            }
         });
      }
      return logFileMenuItem;
   }

   private MenuItem getConfigureMenuItem() {
      debug.print("");
      if (configureMenuItem == null) {
         configureMenuItem = new MenuItem();
         configureMenuItem.setText("Configure...");
         configureMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               configMain.display(jFrame);
            }
         });
         configureMenuItem.setAccelerator(KeyCombination.keyCombination("Ctrl+O"));
      }
      return configureMenuItem;
   }

   private MenuItem getRefreshEncodingsMenuItem() {
      debug.print("");
      if (refreshEncodingsMenuItem == null) {
         refreshEncodingsMenuItem = new MenuItem();
         refreshEncodingsMenuItem.setText("Refresh Encoding Profiles");
         refreshEncodingsMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               refreshEncodingProfilesCB();
            }
         });
         refreshEncodingsMenuItem.setAccelerator(KeyCombination.keyCombination("Ctrl+E"));
      }
      return refreshEncodingsMenuItem;
   }

   private Menu getServiceMenu() {
      debug.print("");
      if (serviceMenu == null) {
         serviceMenu = new Menu();
         serviceMenu.setText("Service");
         serviceMenu.getItems().add(getServiceStatusMenuItem());
         serviceMenu.getItems().add(getServiceInstallMenuItem());
         serviceMenu.getItems().add(getServiceStartMenuItem());
         serviceMenu.getItems().add(getServiceStopMenuItem());
         serviceMenu.getItems().add(getServiceRemoveMenuItem());
      }
      return serviceMenu;
   }

   private MenuItem getServiceStatusMenuItem() {
      debug.print("");
      if (serviceStatusMenuItem == null) {
         serviceStatusMenuItem = new MenuItem();
         serviceStatusMenuItem.setText("Status");
         serviceStatusMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               String query = auto.serviceStatus();
               if (query != null) {
                  log.warn(query);
               }
            }
         });
      }
      return serviceStatusMenuItem;
   }

   private MenuItem getServiceInstallMenuItem() {
      debug.print("");
      if (serviceInstallMenuItem == null) {
         serviceInstallMenuItem = new MenuItem();
         serviceInstallMenuItem.setText("Install");
         serviceInstallMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               String query = auto.serviceStatus();
               if (query != null) {                  
                  if (query.matches("^.+STATUS.+$")) {
                     log.warn("kmttg service already installed");
                     return;
                  }
                  auto.serviceCreate();
               }
            }
         });
      }
      return serviceInstallMenuItem;
   }

   private MenuItem getServiceStartMenuItem() {
      debug.print("");
      if (serviceStartMenuItem == null) {
         serviceStartMenuItem = new MenuItem();
         serviceStartMenuItem.setText("Start");
         serviceStartMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               String query = auto.serviceStatus();
               if (query != null) {                  
                  if (query.matches("^.+RUNNING$")) {
                     log.warn("kmttg service already running");
                     return;
                  }
               }
               auto.serviceStart();
            }
         });
      }
      return serviceStartMenuItem;
   }

   private MenuItem getServiceStopMenuItem() {
      debug.print("");
      if (serviceStopMenuItem == null) {
         serviceStopMenuItem = new MenuItem();
         serviceStopMenuItem.setText("Stop");
         serviceStopMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               String query = auto.serviceStatus();
               if (query != null) {                  
                  if (query.matches("^.+STOPPED$")) {
                     log.warn("kmttg service already stopped");
                     return;
                  }
               }
               auto.serviceStop();
            }
         });
      }
      return serviceStopMenuItem;
   }

   private MenuItem getServiceRemoveMenuItem() {
      debug.print("");
      if (serviceRemoveMenuItem == null) {
         serviceRemoveMenuItem = new MenuItem();
         serviceRemoveMenuItem.setText("Remove");
         serviceRemoveMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               String query = auto.serviceStatus();
               if (query != null) {
                  if (query.matches("^.+not been created.+$")) {
                     log.warn("kmttg service not installed");
                     return;
                  }
                  auto.serviceDelete();
               }
            }
         });
      }
      return serviceRemoveMenuItem;
   }

   private Menu getBackgroundJobMenu() {
      debug.print("");
      if (serviceMenu == null) {
         serviceMenu = new Menu();
         serviceMenu.setText("Background Job");
         serviceMenu.getItems().add(getBackgroundJobStatusMenuItem());
         serviceMenu.getItems().add(getBackgroundJobEnableMenuItem());
         serviceMenu.getItems().add(getBackgroundJobDisableMenuItem());
      }
      return serviceMenu;
   }

   private MenuItem getBackgroundJobStatusMenuItem() {
      debug.print("");
      if (backgroundJobStatusMenuItem == null) {
         backgroundJobStatusMenuItem = new MenuItem();
         backgroundJobStatusMenuItem.setText("Status");
         backgroundJobStatusMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               auto.unixAutoIsRunning(true);
            }
         });
      }
      return backgroundJobStatusMenuItem;
   }

   private MenuItem getBackgroundJobEnableMenuItem() {
      debug.print("");
      if (backgroundJobEnableMenuItem == null) {
         backgroundJobEnableMenuItem = new MenuItem();
         backgroundJobEnableMenuItem.setText("Enable");
         backgroundJobEnableMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               auto.unixAutoStart();
            }
         });
      }
      return backgroundJobEnableMenuItem;
   }

   private MenuItem getBackgroundJobDisableMenuItem() {
      debug.print("");
      if (backgroundJobDisableMenuItem == null) {
         backgroundJobDisableMenuItem = new MenuItem();
         backgroundJobDisableMenuItem.setText("Disable");
         backgroundJobDisableMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               auto.unixAutoKill();
            }
         });
      }
      return backgroundJobDisableMenuItem;
   }

   private MenuItem getMetadataMenuItem() {
      debug.print("");
      if (metadataMenuItem == null) {
         metadataMenuItem = new MenuItem();
         metadataMenuItem.setText("Download Metadata");
         metadataMenuItem.setAccelerator(KeyCombination.keyCombination("Ctrl+R"));
         metadataMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               String tivoName = getSelectedTivoName();
               if (tivoName == null) {
                  log.error("Please select 1 or more shows in a TiVo tab for metadata creation command");
                  return;
               }
               nplTable nplTab = tivoTabs.get(tivoName).getTable();
               int[] rows = nplTab.GetSelectedRows();
               if (rows.length > 0) {
                  int row;
                  for (int i=0; i<rows.length; i++) {
                     row = rows[i];
                     Stack<Hashtable<String,Object>> entries = new Stack<Hashtable<String,Object>>();
                     Stack<Hashtable<String,String>> rowData = nplTab.getRowData(row);
                     for (int j=0; j<rowData.size(); ++j) {
                        Hashtable<String,Object> h = new Hashtable<String,Object>();
                        h.put("tivoName", tivoName);
                        h.put("mode", "Download");
                        h.put("nodownload", true);
                        h.put("entry", rowData.get(j));
                        entries.add(h);
                     }
                     
                     // Launch metadata jobs
                     for (int j=0; j<entries.size(); ++j) {
                        Hashtable<String,Object> h = entries.get(j);
                        h.put("metadata",     true);
                        h.put("metadataTivo", false);
                        h.put("decrypt",      false);
                        h.put("qsfix",        false);
                        h.put("twpdelete",    false);
                        h.put("rpcdelete",    false);
                        h.put("comskip",      false);
                        h.put("comcut",       false);
                        h.put("captions",     false);
                        h.put("encode",       false);
                        h.put("custom",       false);
                        jobMonitor.LaunchJobs(h);
                     }
                  }
               }
            }
         });
      }
      return metadataMenuItem;
   }

   private MenuItem getSearchMenuItem() {
      debug.print("");
      if (searchMenuItem == null) {
         searchMenuItem = new MenuItem();
         searchMenuItem.setText("Search Table...");
         searchMenuItem.setAccelerator(KeyCombination.keyCombination("Ctrl+S"));
         searchMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               TableUtil.SearchGUI();
            }
         });
      }
      return searchMenuItem;
   }

   private MenuItem getAutoSkipMenuItem() {
      debug.print("");
      if (autoSkipMenuItem == null) {
         autoSkipMenuItem = new MenuItem();
         autoSkipMenuItem.setText("AutoSkip Table...");
         autoSkipMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               new SkipDialog(config.gui.getFrame());
            }
         });
      }
      return autoSkipMenuItem;
   }

   private Menu getAutoSkipServiceMenu() {
      debug.print("");
      if (autoSkipServiceMenu == null) {
         autoSkipServiceMenu = new Menu();
         autoSkipServiceMenu.setText("AutoSkip Service");
      }
      return autoSkipServiceMenu;
   }
   
   public void addAutoSkipServiceItem(String tivoName) {
      if ( ! SkipManager.skipEnabled() ) return;
      if (autoSkipServiceMenu == null)
         getAutoSkipServiceMenu();
      for (MenuItem item : autoSkipServiceMenu.getItems()) {
         if (item.getText().equals(tivoName))
            return;
      }
      CheckMenuItem item = new CheckMenuItem();
      item.setText(tivoName);
      item.selectedProperty().addListener(new ChangeListener<Boolean>() {
         public void changed(ObservableValue<? extends Boolean> e, Boolean oldVal, Boolean newVal) {
            if (! newVal) {
               SkipManager.stopService(tivoName);
               config.autoskip_ServiceItems.put(tivoName, false);
               config.save();
               return;
            }
            
            JSONArray skipData = SkipManager.getEntries();
            if (skipData == null) {
               log.warn("No skip table data available - ignoring skip service request");
               disableAutoSkipServiceItem(tivoName);
               config.autoskip_ServiceItems.put(tivoName, false);
               config.save();
               return;
            }
            SkipManager.startService(tivoName);
            Boolean b = config.autoskip_ServiceItems.get(tivoName);
            if( b == null || b == false) {
                config.autoskip_ServiceItems.put(tivoName, true);
                config.save();
            }
         }
      });
      autoSkipServiceMenu.getItems().add(item);
      Boolean b = config.autoskip_ServiceItems.get(tivoName);
      if (b != null && b) {
          item.setSelected(true);
      }
   }
   
   public void removeAutoSkipServiceItem(String tivoName) {
      for (MenuItem item : autoSkipServiceMenu.getItems()) {
         if (item.getText().equals(tivoName)) {
            autoSkipServiceMenu.getItems().remove(item);
         }            
      }
   }

   public void disableAutoSkipServiceItem(String tivoName) {
      for (MenuItem item : autoSkipServiceMenu.getItems()) {
         CheckMenuItem check = (CheckMenuItem)item;
         if (check.getText().equals(tivoName))
            check.setSelected(false);
      }
   }

   private MenuItem getThumbsMenuItem() {
      debug.print("");
      if (thumbsMenuItem == null) {
         thumbsMenuItem = new MenuItem();
         thumbsMenuItem.setText("Set Thumbs rating...");
         thumbsMenuItem.setAccelerator(KeyCombination.keyCombination("Ctrl+T"));
         thumbsMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               TableUtil.ThumbsGUI();
            }
         });
      }
      return thumbsMenuItem;
   }

   // This will decide which options are enabled based on current config settings
   // Options are disabled when associated config entry is not setup
   public void refreshOptions(Boolean refreshProfiles) {
      debug.print("refreshProfiles=" + refreshProfiles);
      if (config.VRD == 0 && ! file.isFile(config.ffmpeg)) {
         qsfix.setSelected(false);
         qsfix.setDisable(true);
      } else {
         qsfix.setDisable(false);
      }
      
      if (!config.twpDeleteEnabled()) {
         twpdelete.setSelected(false);
         twpdelete.setDisable(true);
      } else {
         twpdelete.setDisable(false);
      }
      
      if ( ! config.rpcDeleteEnabled() ) {
         rpcdelete.setSelected(false);
         rpcdelete.setDisable(true);
      } else {
         rpcdelete.setDisable(false);
      }

      if (! file.isFile(config.comskip)) {
         comskip.setSelected(false);
         comskip.setDisable(true);
      } else {
         comskip.setDisable(false);
      }

      if (config.VRD == 0 && ! file.isFile(config.ffmpeg)) {
         comcut.setSelected(false);
         comcut.setDisable(true);
      } else {
         comcut.setDisable(false);
      }

      if (! file.isFile(config.t2extract) && ! file.isFile(config.ccextractor)) {
         captions.setSelected(false);
         captions.setDisable(true);
      } else {
         captions.setDisable(false);
      }

      if (! file.isFile(config.ffmpeg) &&
          ! file.isFile(config.mencoder) &&
          ! file.isFile(config.handbrake) ) {
         encode.setSelected(false);
         encode.setDisable(true);
      } else {
         encode.setDisable(false);
      }
      
      /*if ( ! file.isFile(config.pyTivo_config) ) {
         push.setSelected(false);
         push.setDisable(true);
      } else {
         push.setDisable(false);
      }*/
      
      if ( ! com.tivo.kmttg.task.custom.customCommandExists() ) {
         custom.setSelected(false);
         custom.setDisable(true);
      } else {
         custom.setDisable(false);
      }
      
      // Refresh encoding profiles in case toggled between VRD & regular
      if (config.GUIMODE && refreshProfiles) refreshEncodingProfilesCB();
      
      // Add remote tab if appropriate
      if (config.GUIMODE && remote_gui == null) {
         remote_gui = new remotegui(jFrame);
         addTabPane("Remote", tabbed_panel, remote_gui.getPanel());
      }
      
      // Add slingbox tab if appropriate
      if (config.slingBox == 1) {
         if (slingbox_gui == null)
            slingbox_gui = new slingboxgui(jFrame);
         addTabPane("Slingbox", tabbed_panel, slingbox_gui.getPanel());
      }
      if (config.slingBox == 0 && slingbox_gui != null) {
         tabbed_panel.getTabs().remove((Object)slingbox_gui.getPanel());
      }
   }
   
   // Callback for "Refresh Encoding Profiles" File menu entry
   // This will re-parse encoding files and reset Encoding Profile list in GUI
   private void refreshEncodingProfilesCB() {
      debug.print("");
      log.warn("Refreshing encoding profiles");
      encodeConfig.parseEncodingProfiles();
   }
   
   // Callback for "Run Once in GUI" Auto Transfers menu entry
   // This is equivalent to a batch mode run but is performed in GUI
   public void autoRunInGUICB() {
      debug.print("");
      config.GUI_AUTO = 0;
      if ( ! autoConfig.parseAuto(config.autoIni) ) {
         log.error("Auto Transfers config has errors or is not setup");
         return;
      }
      if ( auto.getTitleEntries().isEmpty() && auto.getKeywordsEntries().isEmpty() ) {
         log.error("No keywords defined in " + config.autoIni + "... aborting");
         return;
      }
      Stack<String> tivoNames = auto.getTiVos();
      if (tivoNames.size() > 0) {
         for (int i=0; i<tivoNames.size(); i++) {
            // Queue up a nowplaying list job for this tivo
            config.GUI_AUTO++;
            tivoTab t = getTab(tivoNames.get(i));
            if (t != null) {
               jobMonitor.getNPL(tivoNames.get(i));
            }
         }
      }
   }

   // Callback for "Loop in GUI" Auto Transfers menu entry
   // This is equivalent to auto mode run but is performed in GUI
   public void autoLoopInGUICB(Boolean enabled) {
      debug.print("enabled=" + enabled);
      // This triggers jobMonitor to clear launch hash
      config.GUI_AUTO = -1;
      
      // If button enabled then start Loop in GUI mode, else exit that mode
      if (enabled) {
         // If kmttg service or background job running prompt user to stop it
         Boolean auto_running = false;
         String question = "";
         if (config.OS.equals("windows")) {
            // Query to stop windows service if it's running
            String query = auto.serviceStatus();
            if (query != null && query.matches("^.+RUNNING$")) {
               auto_running = true;
               question = "kmttg service is currently running. Stop the service?";
            }
         } else {
            auto_running = auto.unixAutoIsRunning(false);            
            question = "kmttg background job is currently running. Stop the job?";
         }
         if (auto_running) {
            Alert alert = new Alert(AlertType.CONFIRMATION);
            alert.setTitle("Confirm");
            setFontSize(alert, config.FontSize);
            alert.setContentText(question);
            Optional<ButtonType> result = alert.showAndWait();
            if (result.get() == ButtonType.OK) {
               if (config.OS.equals("windows")) {
                  auto.serviceStop();
               } else {
                  auto.unixAutoKill();
               }               
            }
         }

         // Start Loop in GUI mode
         config.GUI_LOOP = 1;
         log.warn("\nAuto Transfers Loop in GUI enabled");
      } else {
         // Stop Loop in GUI mode
         log.warn("\nAuto Transfers Loop in GUI disabled");
         config.GUI_LOOP = 0;
         log.stopLogger();
      }
   }
   
   // Encoding cyclic change callback
   // Set the description according to selected item
   private void encodingCB(ComboBox<String> combo) {
      debug.print("combo=" + combo);
      String encodeName = combo.getValue();
      config.encodeName = encodeName;
      String description = encodeConfig.getDescription(encodeName);
      // Set encoding_description_label accordingly
      encoding_description_label.setText("  " + description);
   }
 
   // Cancel button callback
   // Kill and remove selected jobs from job monitor
   private void cancelCB() {
      debug.print("");
      int[] rows = TableUtil.GetSelectedRows(jobTab.JobMonitor);

      if (rows.length > 0) {
         int row;
         for (int i=rows.length-1; i>=0; i--) {
            row = rows[i];
            jobData job = jobTab.GetSelectionData(row);
            if (job != null) jobMonitor.kill(job);
         }
      }
   }
   
   // Add a new tab pane
   private void addTabPane(String name, TabPane pane, Node content) {
      debug.print("name=" + name + " pane=" + pane + " content=" + content);
      // Prevent duplicates
      Boolean add = true;
      for (Tab t : pane.getTabs()) {
         if (t.getText().equals(name))
            add = false;
      }
      if (add) {
         Tab tab = new Tab();
         tab.setContent(content);
         tab.setText(name);
         pane.getTabs().add(tab);
      }
   }

   // Create tivo tabs as needed
   public void SetTivos(LinkedHashMap<String,String> values) {
      debug.print("values=" + values);
      if ( values.size() > 1 ) {
         String[] names = new String[values.size()-1];
         int i = 0;         
         for (String value : values.keySet()) {
            if (! value.equals("FILES") && ! value.equals("Remote")) {
               if (config.nplCapable(value)) {
                  names[i] = value;
                  i++;
               }
            }
         }
         
         // Remove unwanted tabs
         tivoTabRemoveExtra(names);
         
         // Add tabs
         for (int j=i-1; j>=0; j--) {
            tivoTabAdd(names[j]);
         }
         
         // remote gui
         if (remote_gui != null)
            remote_gui.setTivoNames();

      } else {
         // Remove all tivo tabs
         String itemName = tabbed_panel.getTabs().get(0).getText();
         while(! itemName.equals("FILES") && ! itemName.equals("Remote")) {
            tivoTabRemove(itemName);
         }
      }
   }

   // Start NPL jobs for 1st time
   public void initialNPL(LinkedHashMap<String,String> values) {
      debug.print("values=" + values);
      for (String value : values.keySet()) {
         if (! value.equals("FILES") && ! value.equals("Remote") && config.nplCapable(value)) {
            jobMonitor.getNPL(value);
         }
      }
   }
   
   public String getCurrentTabName() {
      debug.print("");
      return tabbed_panel.getSelectionModel().getSelectedItem().getText();
   }
   
   public String getSelectedTivoName() {
      debug.print("");
      String tabName = getCurrentTabName();
      if (! tabName.equals("FILES") && ! tabName.equals("Remote") && ! tabName.equals("Slingbox")) {
         return tabName;
      }
      return null;
   }
   
   public String getCurrentRemoteTivoName() {
      debug.print("");
      if (getCurrentTabName().equals("Remote"))
         return config.gui.remote_gui.getTivoName(config.gui.remote_gui.getCurrentTabName());
      return null;
   }
   
   public JSONObject getCurrentRemoteJson() {
      debug.print("");
      if (getCurrentTabName().equals("Remote"))
         return config.gui.remote_gui.getSelectedJSON(config.gui.remote_gui.getCurrentTabName());         
      return null;
   }
   
   // Check name against existing tabbed panel names
   private Boolean tivoTabExists(String name) {
      debug.print("name=" + name);
      int numTabs = tabbed_panel.getTabs().size();
      String tabName;
      for (int i=0; i<numTabs; i++) {
         tabName = tabbed_panel.getTabs().get(i).getText();
         if (tabName != null && tabName.equals(name)) {
            return true;
         }
      }
      return false;
   }
   
   private void tivoTabAdd(String name) {
      debug.print("name=" + name);
      if ( ! tivoTabExists(name) ) {
         tivoTab tab = new tivoTab(name);
         Tab tabpane = new Tab();
         tabpane.setContent(tab.getPanel());
         tabpane.setText(name);
         tabbed_panel.getTabs().add(0, tabpane);
         tivoTabs.put(name,tab);
      }
   }
   
   private void tivoTabRemove(String name) {
      debug.print("name=" + name);
      if (tivoTabs.containsKey(name)) {
         tabbed_panel.getTabs().remove((Object)tivoTabs.get(name));
         tivoTabs.remove(name);
      }
   }
   
   private void tivoTabRemoveExtra(String[] names) {
      debug.print("names=" + Arrays.toString(names));
      int numTabs = tabbed_panel.getTabs().size();
      if (numTabs > 0 && names.length > 0) {
         // Determine tabs we no longer want
         Stack<String> unwanted = new Stack<String>();
         String tabName;
         Boolean remove;
         for (int i=0; i<numTabs; i++) {
            tabName = tabbed_panel.getTabs().get(i).getText();
            if (tabName != null && ! tabName.equals("FILES") && ! tabName.equals("Remote")) {
               remove = true;
               for (int j=0; j<names.length; j++) {
                  if (names[j] != null && names[j].equals(tabName)) {
                     remove = false;
                  }
               }
               if (remove) {
                  unwanted.add(tabName);
               }
            }
         }
         // Now remove the unwanted tabs
         if (unwanted.size() > 0) {
            for (int i=0; i<unwanted.size(); i++) {
               tivoTabRemove(unwanted.get(i));
            }
         }
      }
   }
   
   // Set current tab to this tivo (if valid)
   public void SetTivo(String tivoName) {
      debug.print("tivoName=" + tivoName);
      for (int i=0; i<tabbed_panel.getTabs().size(); ++i) {
         if (tabbed_panel.getTabs().get(i).getText().equals(tivoName)) {
            tabbed_panel.getSelectionModel().select(i);
         }
      }
   }
   
   // Add a tivo
   public void AddTivo(String name, String ip) {
      debug.print("name=" + name + " ip=" + ip);
      tivoTabAdd(name);
      configMain.addTivo(name, ip);
   }
   
   // Set encoding ComboBox choices
   public void SetEncodings(final Stack<String> values) {
      debug.print("values=" + values);

      if (encoding != null) {
         Platform.runLater(new Runnable() {
            @Override public void run() {
               // Get existing setting in ComboBox
               String current = null;
               if (encoding.getItems().size() > 0) {
                  current = encoding.getValue();
               }
               Boolean valid = false;
               String[] names = new String[values.size()];
               for (int i=0; i<values.size(); ++i) {
                  names[i] = values.get(i);
                  if (current != null && current.equals(names[i]))
                     valid = true;
               }
               combobox.SetValues(encoding, names);
               if (! valid)
                  current = null;
               if (current != null)
                  encoding.setValue(current);
               else {
                  if (encoding.getItems().size() > 0)
                     encoding.setValue(encoding.getItems().get(0));
               }
            }
         });
      }
   }
   
   public void SetSelectedEncoding(final String name) {
      debug.print("name=" + name);
      Platform.runLater(new Runnable() {
          @Override public void run() {
		      if (encoding.getItems().size() > 0) {
		         encoding.setValue(name);
		      }
          }
      });
   }
   
   private void CreateImages() {
      debug.print("");
      Images = new Hashtable<String,Image>();
      String[] names = {
         "expires-soon-recording", "save-until-i-delete-recording",
         "in-progress-recording", "in-progress-transfer",
         "expired-recording", "suggestion-recording", "folder",
         "copy-protected", "running", "queued", "skipmode",
         "image-season-pass", "image-season-pass-wishlist",
         "image-single-explicit-record"
      };
      URL url;
      for (int i=0; i<names.length; i++) {
         try {
            // From jar file
            url = getClass().getResource("/" + names[i] + ".png");
            Images.put(names[i], new Image(url.toURI().toString()));
         } catch (Exception e) {
            // From eclipse
            Images.put(names[i], new Image(new File("images/" + names[i] + ".png").toURI().toString()));            
         }
      }
   }
   
   public static void LoadIcons(Stage stage) {
      String[] icons = {
            "TtGo_blue_16x16_8", "TtGo_blue_16x16_32",
            "TtGo_blue_32x32_8", "TtGo_blue_32x32_32",
            "TtGo_blue_48x48_8", "TtGo_blue_48x48_32" };
      try {
         for (int i = 0; i < icons.length; i++) {
            stage.getIcons().add(new Image(gui.class.getResourceAsStream("/" + icons[i] + ".png")));
         }
      } catch (Exception e) {
         debug.print(e.toString());
      }
   }
   
   // Save current GUI settings to a file
   public void saveSettings() {
      debug.print("");
      if (config.gui_settings != null) {
         if (slingbox_gui != null)
            slingbox_gui.updateConfig();
         try {
            double centerDivider = jContentPane.getDividerPositions()[0];
            double bottomDivider = splitBottom.getDividerPositions()[0];
            String tabName = tabbed_panel.getSelectionModel().getSelectedItem().getText();
            int width = (int)jFrame.getWidth(); if (width <0) width = 0;
            int height = (int)jFrame.getHeight(); if (height <0) height = 0;
            int x = (int)jFrame.getX(); if (x <0) x = 0;
            int y = (int)jFrame.getY(); if (y <0) y = 0;
            BufferedWriter ofp = new BufferedWriter(new FileWriter(config.gui_settings));            
            ofp.write("# kmttg gui preferences file\n");
            ofp.write("<GUI_LOOP>\n"            + config.GUI_LOOP            + "\n");
            ofp.write("<metadata>\n"            + metadata_setting()         + "\n");
            ofp.write("<decrypt>\n"             + decrypt_setting()          + "\n");
            ofp.write("<qsfix>\n"               + qsfix_setting()            + "\n");
            ofp.write("<twpdelete>\n"           + twpdelete_setting()        + "\n");
            ofp.write("<rpcdelete>\n"          + rpcdelete_setting()       + "\n");
            ofp.write("<comskip>\n"             + comskip_setting()          + "\n");
            ofp.write("<comcut>\n"              + comcut_setting()           + "\n");
            ofp.write("<captions>\n"            + captions_setting()         + "\n");
            ofp.write("<encode>\n"              + encode_setting()           + "\n");
            //ofp.write("<push>\n"                + push_setting()             + "\n");
            ofp.write("<custom>\n"              + custom_setting()           + "\n");
            ofp.write("<encode_name>\n"         + config.encodeName          + "\n");
            ofp.write("<toolTips>\n"            + config.toolTips            + "\n");
            ofp.write("<toolTipsDelay>\n"       + config.toolTipsDelay       + "\n");
            ofp.write("<toolTipsTimeout>\n"     + config.toolTipsTimeout     + "\n");
            ofp.write("<slingBox>\n"            + config.slingBox            + "\n");
            ofp.write("<slingBox_perl>\n"       + config.slingBox_perl       + "\n");
            ofp.write("<slingBox_dir>\n"        + config.slingBox_dir        + "\n");
            ofp.write("<slingBox_ip>\n"         + config.slingBox_ip         + "\n");
            ofp.write("<slingBox_port>\n"       + config.slingBox_port       + "\n");
            ofp.write("<slingBox_pass>\n"       + config.slingBox_pass       + "\n");
            ofp.write("<slingBox_res>\n"        + config.slingBox_res        + "\n");
            ofp.write("<slingBox_vbw>\n"        + config.slingBox_vbw        + "\n");
            ofp.write("<slingBox_type>\n"       + config.slingBox_type       + "\n");
            ofp.write("<slingBox_container>\n"  + config.slingBox_container  + "\n");
            ofp.write("<jobMonitorFullPaths>\n" + config.jobMonitorFullPaths + "\n");
            ofp.write("<width>\n"               + width                      + "\n");
            ofp.write("<height>\n"              + height                     + "\n");
            ofp.write("<x>\n"                   + x                          + "\n");
            ofp.write("<y>\n"                   + y                          + "\n");
            ofp.write("<centerDivider>\n"       + centerDivider              + "\n");
            ofp.write("<bottomDivider>\n"       + bottomDivider              + "\n");
            if (remote_gui != null) {
               int tabIndex_r = remote_gui.tabbed_panel.getSelectionModel().getSelectedIndex();
               ofp.write("<tab_remote>\n"       + tabIndex_r                 + "\n");
            }
            ofp.write("<tab>\n"                 + tabName                    + "\n");
            
            ofp.write("<columnOrder>\n");
            String name, colName;
            // NPL & Files tables
            for (Enumeration<String> e=tivoTabs.keys(); e.hasMoreElements();) {
               name = e.nextElement();
               String order[] = tivoTabs.get(name).getColumnOrder();
               colName = order[0];
               if (colName.equals("")) colName = "ICON";
               ofp.write(name + "=" + colName);
               for (int j=1; j<order.length; ++j) {
                  colName = order[j];
                  if (colName.equals("")) colName = "ICON";
                  ofp.write("," + colName);
               }
               ofp.write("\n");
            }
            // Job table
            String order[] = jobTab.getColumnOrder();
            ofp.write("JOBS=" + order[0]);
            for (int j=1; j<order.length; ++j) {
               ofp.write("," + order[j]);
            }
            ofp.write("\n\n");
            
            ofp.write("<columnWidths>\n");
            for (Enumeration<String> e=tivoTabs.keys(); e.hasMoreElements();) {
               name = e.nextElement();
               ObservableList<TreeTableColumn<Tabentry, ?>> cols = tivoTabs.get(name).getTable().NowPlaying.getColumns();
               int[] widths = new int[cols.size()];
               int i=0;
               for (TreeTableColumn<Tabentry, ?> col : cols) {
                  widths[i++] = (int)col.getWidth();
               }
               ofp.write(name + "=" + widths[0]);
               for (int j=1; j<widths.length; ++j) {
                  ofp.write("," + widths[j]);
               }
               ofp.write("\n");
            }
            
            writeWidths("jobTable", config.gui.jobTab.JobMonitor, ofp);
            if (remote_gui != null) {
               writeWidths("todoTable", remote_gui.todo_tab.tab.TABLE, ofp);
               writeWidths("spTable", remote_gui.sp_tab.tab.TABLE, ofp);
               writeWidths("premiereTable", remote_gui.premiere_tab.tab.TABLE, ofp);
               writeWidths("guideTable", remote_gui.guide_tab.tab.TABLE, ofp);
               writeWidths("deletedTable", remote_gui.deleted_tab.tab.TABLE, ofp);
               writeWidths("channelsTable", remote_gui.channels_tab.tab.TABLE, ofp);
               writeWidths("thumbsTable", remote_gui.thumbs_tab.tab.TABLE, ofp);
               writeWidths("cancelTable", remote_gui.cancel_tab.tab.TABLE, ofp);
               writeWidths("searchTable", remote_gui.search_tab.tab.TABLE, ofp);
               writeWidths("streamTable", remote_gui.stream_tab.tab.TABLE, ofp);
            }
            ofp.write("\n");
            
            ofp.write("<showFolders>\n");
            for (Enumeration<String> e=tivoTabs.keys(); e.hasMoreElements();) {
               name = e.nextElement();
               if ( ! name.equals("FILES") && ! name.equals("Remote") ) {
                  if (tivoTabs.get(name).showFolders()) {
                     ofp.write(name + "=" + 1 + "\n");
                  } else {
                     ofp.write(name + "=" + 0 + "\n");
                  }
               }
            }
            if (remote_gui != null) {
               String[]names = {
                  "todo", "sp", "cancel", "premiere", "search", "guide", "stream", "deleted", "thumbs", "rc", "info" 
               };
               ofp.write("\n<rpc_tivo>\n");
               for (String tab : names)
                  ofp.write(tab + "=" + remote_gui.getTivoName(tab) + "\n");
               ofp.write("\n<rpc_includePast>\n");
               if (remote_gui.cancel_tab.includeHistory.isSelected())
                  ofp.write("1\n");
               else
                  ofp.write("0\n");
               
               // Search max hits
               int max = (Integer) remote_gui.search_tab.max.getValue();
               ofp.write("\n<rpc_search_max>\n");
               ofp.write("" + max + "\n");
               
               // Search streaming settings
               ofp.write("\n<rpc_search_type>\n");
               ofp.write("" + remote_gui.search_tab.search_type.getValue());
               
               int includeFree = 0;
               if (remote_gui.search_tab.includeFree.isSelected())
                  includeFree = 1;
               ofp.write("\n<rpc_search_includeFree>\n");
               ofp.write("" + includeFree + "\n");
               
               int includePaid = 0;
               if (remote_gui.search_tab.includePaid.isSelected())
                  includePaid = 1;
               ofp.write("\n<rpc_search_includePaid>\n");
               ofp.write("" + includePaid + "\n");
               
               //int includeVod = 0;
               //if (remote_gui.search_tab.includeVod.isSelected())
               //   includeVod = 1;
               //ofp.write("\n<rpc_search_includeVod>\n");
               //ofp.write("" + includeVod + "\n");
               
               //int unavailable = 0;
               //if (remote_gui.search_tab.unavailable.isSelected())
               //   unavailable = 1;
               //ofp.write("\n<rpc_search_unavailable>\n");
               //ofp.write("" + unavailable + "\n");
               
               // Record dialog
               JSONObject json = util.recordOpt.getValues();
               if (json != null) {
                  try {
                     ofp.write("\n<rpc_recordOpt>\n");
                     String[] n = {"keepBehavior", "startTimePadding", "endTimePadding", "anywhere"};
                     for (int j=0; j<n.length; ++j) {
                        ofp.write(n[j] + "=" + json.get(n[j]) + "\n");
                     }
                  } catch (JSONException e) {
                     log.error(e.getMessage());
                     log.error(Arrays.toString(e.getStackTrace()));
                  }
               }
               
               // SP dialog
               json = util.spOpt.getValues();
               if (json != null) {
                  try {
                     ofp.write("\n<rpc_spOpt>\n");
                     String[] n = {"showStatus", "maxRecordings", "keepBehavior", "startTimePadding", "endTimePadding"};
                     for (int j=0; j<n.length; ++j) {
                        ofp.write(n[j] + "=" + json.get(n[j]) + "\n");
                     }
                  } catch (JSONException e) {
                     log.error(e.getMessage());
                     log.error(Arrays.toString(e.getStackTrace()));
                  }
               }
            }
            
            ofp.write("\n");
            ofp.close();
         }         
         catch (IOException ex) {
            log.error("Problem writing to file: " + config.gui_settings);
         }         
      }
   }
   
   // Read initial settings from file
   public void readSettings() {
      debug.print("");
      if (! file.isFile(config.gui_settings)) {
         return;
      }
      try {
         int width = -1;
         int height = -1;
         int x = -1;
         int y = -1;
         int value;
         double centerDivider = -1, bottomDivider = -1;
         BufferedReader ifp = new BufferedReader(new FileReader(config.gui_settings));
         String line = null;
         String key = null;
         JSONObject rpc_recordOpt = new JSONObject();
         JSONObject rpc_spOpt = new JSONObject();
         while (( line = ifp.readLine()) != null) {
            // Get rid of leading and trailing white space
            line = line.replaceFirst("^\\s*(.*$)", "$1");
            line = line.replaceFirst("^(.*)\\s*$", "$1");
            if (line.length() == 0) continue; // skip empty lines
            if (line.matches("^#.+")) continue; // skip comment lines
            if (line.matches("^<.+>")) {
               key = line.replaceFirst("<", "");
               key = key.replaceFirst(">", "");
               continue;
            }
            if (key.equals("GUI_LOOP")) {
               if (line.matches("1"))
                  loopInGuiMenuItem.setSelected(true);
            }
            if (key.equals("metadata")) {
               if (line.matches("1"))
                  metadata.setSelected(true);
               else
                  metadata.setSelected(false);
            }
            if (key.equals("decrypt")) {
               if (line.matches("1"))
                  decrypt.setSelected(true);
               else
                  decrypt.setSelected(false);
            }
            if (key.equals("qsfix")) {
               if (line.matches("1"))
                  qsfix.setSelected(true);
               else
                  qsfix.setSelected(false);
            }            
            if (key.equals("twpdelete")) {
               if (line.matches("1"))
                  twpdelete.setSelected(true);
               else
                  twpdelete.setSelected(false);
            }            
            if (key.equals("rpcdelete")) {
               if (line.matches("1"))
                  rpcdelete.setSelected(true);
               else
                  rpcdelete.setSelected(false);
            }            
            if (key.equals("comskip")) {
               if (line.matches("1"))
                  comskip.setSelected(true);
               else
                  comskip.setSelected(false);
            }
            if (key.equals("comcut")) {
               if (line.matches("1"))
                  comcut.setSelected(true);
               else
                  comcut.setSelected(false);
            }
            if (key.equals("captions")) {
               if (line.matches("1"))
                  captions.setSelected(true);
               else
                  captions.setSelected(false);
            }
            if (key.equals("encode")) {
               if (line.matches("1"))
                  encode.setSelected(true);
               else
                  encode.setSelected(false);
            }
            /*if (key.equals("push")) {
               if (line.matches("1"))
                  push.setSelected(true);
               else
                  push.setSelected(false);
            }*/
            if (key.equals("custom")) {
               if (line.matches("1"))
                  custom.setSelected(true);
               else
                  custom.setSelected(false);
            }
            if (key.equals("toolTips")) {
               if (line.matches("1"))
                  config.toolTips = 1;
               else
                  config.toolTips = 0;
            }
            if (key.equals("slingBox")) {
               if (line.matches("1"))
                  config.slingBox = 1;
               else
                  config.slingBox = 0;
            }
            if (key.equals("slingBox_pass"))
               config.slingBox_pass = line;
            if (key.equals("slingBox_ip"))
               config.slingBox_ip = line;
            if (key.equals("slingBox_port"))
               config.slingBox_port = line;
            if (key.equals("slingBox_perl"))
               config.slingBox_perl = line;
            if (key.equals("slingBox_dir"))
               config.slingBox_dir = line;
            if (key.equals("slingBox_res"))
               config.slingBox_res = line;
            if (key.equals("slingBox_vbw"))
               config.slingBox_vbw = line;
            if (key.equals("slingBox_type"))
               config.slingBox_type = line;
            if (key.equals("slingBox_container"))
               config.slingBox_container = line;
            if (key.equals("jobMonitorFullPaths")) {
               if (line.matches("1"))
                  config.jobMonitorFullPaths = 1;
               else
                  config.jobMonitorFullPaths = 0;
            }
            if (key.equals("encode_name")) {
               config.encodeName_orig = line;
               if (encodeConfig.isValidEncodeName(line)) {
                  config.encodeName = line;
                  // runlater needed else doesn't get set right at kmttg startup
                  final String line_final = line;
                  Platform.runLater(new Runnable() {
                     @Override public void run() {
                        config.encodeName = line_final;
                        encoding.setValue(line_final);
                     }
                  });
               }
            }
            if (key.equals("toolTipsDelay")) {
               try {
                  config.toolTipsDelay = Integer.parseInt(line);
               } catch (NumberFormatException e) {
                  config.toolTipsDelay = 2;
               }
            }
            if (key.equals("toolTipsTimeout")) {
               try {
                  config.toolTipsTimeout = Integer.parseInt(line);
               } catch (NumberFormatException e) {
                  config.toolTipsTimeout = 20;
               }
            }
            if (key.equals("width")) {
               try {
                  width = Integer.parseInt(line);
               } catch (NumberFormatException e) {
                  width = -1;
               }
            }
            if (key.equals("height")) {
               try {
                  height = Integer.parseInt(line);
               } catch (NumberFormatException e) {
                  height = -1;
               }
            }
            if (key.equals("x")) {
               try {
                  x = Integer.parseInt(line);
               } catch (NumberFormatException e) {
                  x = -1;
               }
            }
            if (key.equals("y")) {
               try {
                  y = Integer.parseInt(line);
               } catch (NumberFormatException e) {
                  y = -1;
               }
            }
            if (key.equals("tab_remote")) {
               try {
                  value = Integer.parseInt(line);
               } catch (NumberFormatException e) {
                  value = 0;
               }
               if (remote_gui != null) {
                  class backgroundRun implements Runnable {
                     int value;
                     public backgroundRun(int value) {
                        this.value = value;
                     }
                     @Override public void run() {
                        remote_gui.getPanel().getSelectionModel().select(value);
                     }
                  }
                  Platform.runLater(new backgroundRun(value));
               }
            }
            if (key.equals("centerDivider")) {
               try {
                  centerDivider = Double.parseDouble(line);
               } catch (NumberFormatException e) {
                  centerDivider = -1;
               }
            }
            if (key.equals("bottomDivider")) {
               try {
                  bottomDivider = Double.parseDouble(line);
               } catch (NumberFormatException e) {
                  bottomDivider = -1;
               }
            }
            if (key.equals("tab")) {
               SetTivo(line);
            }
            if (key.equals("columnOrder")) {
               String[] l = line.split("=");
               String[] order = l[1].split(",");
               if (tivoTabs.containsKey(l[0])) {
                  tivoTabs.get(l[0]).setColumnOrder(order);
               }
               if (l[0].equals("JOBS")) {
                  jobTab.setColumnOrder(order);
               }
            }
            if (key.equals("columnWidths")) {
               String[] l = line.split("=");
               String name = l[0];
               String[] order = l[1].split(",");
               int[] widths = new int[order.length];
               for (int i=0; i<order.length; ++i) {
                  widths[i] = Integer.parseInt(order[i]);
               }
               if (tivoTabs.containsKey(name)) {
                  ObservableList<TreeTableColumn<Tabentry, ?>> cols = tivoTabs.get(name).getTable().NowPlaying.getColumns();
                  int j=0;
                  for (TreeTableColumn<Tabentry, ?> col : cols) {
                     try {
                        col.setPrefWidth(widths[j++]);
                     } catch (Exception e) {
                        // This seems to fail with recent java releases for some reason
                     }
                  }
               }
               if (name.equals("jobTable")) {
                  setWidths(config.gui.jobTab.JobMonitor, widths);
               }
               if (remote_gui != null) {
                  if (name.equals("todoTable"))
                     setWidths(remote_gui.todo_tab.tab.TABLE, widths);
                  if (name.equals("spTable"))
                     setWidths(remote_gui.sp_tab.tab.TABLE, widths);
                  if (name.equals("premiereTable"))
                     setWidths(remote_gui.premiere_tab.tab.TABLE, widths);
                  if (name.equals("guideTable"))
                     setWidths(remote_gui.guide_tab.tab.TABLE, widths);
                  if (name.equals("deletedTable"))
                     setWidths(remote_gui.deleted_tab.tab.TABLE, widths);
                  if (name.equals("channelsTable"))
                     setWidths(remote_gui.channels_tab.tab.TABLE, widths);
                  if (name.equals("thumbsTable"))
                     setWidths(remote_gui.thumbs_tab.tab.TABLE, widths);
                  if (name.equals("cancelTable"))
                     setWidths(remote_gui.cancel_tab.tab.TABLE, widths);
                  if (name.equals("searchTable"))
                     setWidths(remote_gui.search_tab.tab.TABLE, widths);
                  if (name.equals("streamTable"))
                     setWidths(remote_gui.stream_tab.tab.TABLE, widths);
               }
            }
            if (key.equals("showFolders")) {
               String[] l = line.split("=");
               if (l[1].equals("1")) {
                  if (tivoTabs.containsKey(l[0]))
                     tivoTabs.get(l[0]).showFoldersSet(true);
               }
            }
            if (key.equals("rpc_tivo") && remote_gui != null) {
               String[] l = line.split("=");
               if (l.length == 2 && tivoTabs.containsKey(l[1]))
                  remote_gui.setTivoName(l[0], l[1]);
            }
            /*if (key.equals("rpc_web_bookmarks") && remote_gui != null) {
               if (line.matches("^html::.+$") || line.matches("^flash::.+$"))
                  remote_gui.bookmark_web.addItem(line);
               else
                  remote_gui.bookmark_web.addItem("html::" + line);
            }*/
            
            if (key.equals("rpc_search_max") && remote_gui != null) {
               try {
                  int max = Integer.parseInt(line);
                  remote_gui.search_tab.max.getValueFactory().setValue(max);
               }
               catch (NumberFormatException ex) {
                  // Don't do anything here
               }
            }
            
            if (key.equals("rpc_search_type") && remote_gui != null) {
               String search_type = string.removeLeadingTrailingSpaces(line);
               remote_gui.search_tab.search_type.getSelectionModel().select(search_type);
            }
            
            if (key.equals("rpc_search_includeFree") && remote_gui != null) {
               try {
                  int includeFree = Integer.parseInt(line);
                  remote_gui.search_tab.includeFree.setSelected(includeFree == 1);
               }
               catch (NumberFormatException ex) {
                  // Don't do anything here
               }
            }
            
            if (key.equals("rpc_search_includePaid") && remote_gui != null) {
               try {
                  int includePaid = Integer.parseInt(line);
                  remote_gui.search_tab.includePaid.setSelected(includePaid == 1);
               }
               catch (NumberFormatException ex) {
                  // Don't do anything here
               }
            }
            
            //if (key.equals("rpc_search_includeVod") && remote_gui != null) {
            //   try {
            //      int includeVod = Integer.parseInt(line);
            //      remote_gui.search_tab.includeVod.setSelected(includeVod == 1);
            //   }
            //   catch (NumberFormatException ex) {
            //      // Don't do anything here
            //   }
            //}
            
            //if (key.equals("rpc_search_unavailable") && remote_gui != null) {
            //   try {
            //      int unavailable = Integer.parseInt(line);
            //      remote_gui.search_tab.unavailable.setSelected(unavailable == 1);
            //   }
            //   catch (NumberFormatException ex) {
            //      // Don't do anything here
            //   }
            //}
            
            if (key.equals("rpc_includePast") && remote_gui != null) {
               if (line.matches("1"))
                  remote_gui.cancel_tab.includeHistory.setSelected(true);
            }
            
            if (key.equals("rpc_recordOpt") && remote_gui != null) {
               String[] l = line.split("=");
               if (l.length == 2) {
                  rpc_recordOpt.put(l[0], l[1]);
               }
            }
            if (key.equals("rpc_spOpt") && remote_gui != null) {
               String[] l = line.split("=");
               if (l.length == 2) {
                  rpc_spOpt.put(l[0], l[1]);
               }
            }
         }
         ifp.close();
         
         if (remote_gui != null) {
            if (rpc_recordOpt.length() > 0) {
               util.recordOpt.setValues(rpc_recordOpt);
            }
            if (rpc_spOpt.length() > 0) {
               util.spOpt.setValues(rpc_spOpt);
            }
         }
         
         if (width > 0 && height > 0) {
            jFrame.setWidth(width);
            jFrame.setHeight(height);
         }
         
         if (x >= 0 && y >= 0) {
            Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
            if (x < bounds.getMinX()) x = (int)bounds.getMinX();
            if (x > bounds.getMaxX()) x = (int)bounds.getMinX();
            if (y < bounds.getMinY()) y = (int)bounds.getMinY();
            if (y > bounds.getMaxY()) y = (int)bounds.getMinY();
            jFrame.setX(x);
            jFrame.setY(y);
         }
         
         class backgroundRun implements Runnable {
            double centerDivider, bottomDivider;
            public backgroundRun(double centerDivider, double bottomDivider) {
               this.centerDivider = centerDivider;
               this.bottomDivider = bottomDivider;
            }
            @Override public void run() {
               if (centerDivider > 0 && centerDivider < 1)
                  jContentPane.setDividerPosition(0, centerDivider);
               
               if (bottomDivider > 0 && bottomDivider < 1)
                  splitBottom.setDividerPosition(0, bottomDivider);
            }
         }
         Platform.runLater(new backgroundRun(centerDivider, bottomDivider));
      }         
      catch (Exception ex) {
         log.warn("Problem parsing config file: " + config.gui_settings);
         log.warn(Arrays.toString(ex.getStackTrace()));
      }
   }
   
   private void writeWidths(String name, TableView<?> table, BufferedWriter ofp) {
      try {
         ofp.write("" + name + "=");
         int i=0;
         for (TableColumn<?,?> col : table.getColumns()) {
            int w = (int)col.getWidth();
            if (i==0)
               ofp.write("" + w);
            else
               ofp.write("," + w);
            i++;
         }
         ofp.write("\n");
      } catch (Exception e) {
         log.error("writeWidths - " + e.getMessage());
      }
   }
   
   private void setWidths(TableView<?> table, int[] widths) {
      int i=0;
      for (TableColumn<?,?> col : table.getColumns()) {
         try {
            col.setPrefWidth(widths[i]);
         } catch (Exception e) {
            // Ignore exceptions
         }
         i++;
      }
   }
   
   private void writeWidths(String name, TreeTableView<?> table, BufferedWriter ofp) {
      try {
         ofp.write("" + name + "=");
         int i=0;
         for (TreeTableColumn<?,?> col : table.getColumns()) {
            int w = (int)col.getWidth();
            if (i==0)
               ofp.write("" + w);
            else
               ofp.write("," + w);
            i++;
         }
         ofp.write("\n");
      } catch (Exception e) {
         log.error("writeWidths - " + e.getMessage());
      }
   }
   
   private void setWidths(TreeTableView<?> table, int[] widths) {
      int i=0;
      for (TreeTableColumn<?,?> col : table.getColumns()) {
         try {
            col.setPrefWidth(widths[i]);
         } catch (Exception e) {
            // Ignore exceptions
         }
         i++;
      }
   }
   
   // Component tooltip setup
   public void setToolTips() {
      debug.print("");
      metadata.setTooltip(getToolTip("metadata"));
      decrypt.setTooltip(getToolTip("decrypt"));
      qsfix.setTooltip(getToolTip("qsfix"));
      twpdelete.setTooltip(getToolTip("twpdelete"));
      rpcdelete.setTooltip(getToolTip("rpcdelete"));
      comskip.setTooltip(getToolTip("comskip"));
      comcut.setTooltip(getToolTip("comcut"));
      captions.setTooltip(getToolTip("captions"));
      encode.setTooltip(getToolTip("encode"));
      //push.setTooltip(getToolTip("push"));
      custom.setTooltip(getToolTip("custom"));
      encoding.setTooltip(getToolTip("encoding"));
   }
     
   public Tooltip getToolTip(String component) {
      debug.print("component=" + component);
      String text = "";
      if (component.equals("tivos")) {
         text =  "<b>TIVOS</b><br>";
         text += "Select <b>FILES</b> mode or a <b>TiVo</b> on your network.<br>";
         text += "<b>FILES</b> mode allows you to select existing TiVo or mpeg2 files on your computer.<br>";
         text += "<b>TiVo</b> mode allows you to get a listing of all shows for a TiVo on your home network.";
      }
      else if (component.equals("add")) {
         text =  "<b>Add...</b><br>";
         text += "Brings up a file browser for selecting video files to process.<br>";
         text += "Selected files are added to files table below.<br>";
         text += "NOTE: For Mac OS you can get to other disk volumes by browsing to<br>";
         text += "<b>/Volumes</b> with the browser.";
      }
      else if (component.equals("remove")) {
         text =  "<b>Remove</b><br>";
         text += "Removes selected file entries from files table below.";
      }
      else if (component.equals("atomic")) {
         text =  "<b>Run AtomicParsley</b><br>";
         text += "Run AtomicParsley on files selected in table below.<br>";
         text += "This is only supported for mp4/m4v files ending in .mp4 or .m4v suffix.<br>";
         text += "Change <b>Files of Type</b> to <b>All Files</b> in File browser to see all file types.<br>";
         text += "NOTE: There must be accompanying pyTivo metadata .txt file for this command to work.";
      }
      else if (component.equals("partiallyViewed")) {
         text = "<b>Partially Viewed</b><br>";
         text += "If enabled then only shows that have been partially watched will be obtained on Refresh.<br>";
         text += "If you refresh with this option turned off then this can be used to toggle between full<br>";
         text += "listings and only partially watched show listings.<br>";
         text += "If you refresh with this option turned on, it's a quick way to get only partially viewed<br>";
         text += "shows compared to retrieving full list of shows.<br>";
         text += "NOTE: This option only applies when <b>Use RPC to get NPL when possible</b> option is enabled.";
      }
      else if (component.equals("pyTivo_stream")) {
         text =  "<b>pyTivo stream</b><br>";
         text += "Use TiVoCast HME app with pyTivo as a video server to stream a video file to<br>";
         text += "a series 4 or later TiVo. pyTivo.conf must be configured in kmttg and the video<br>";
         text += "file must be within a pyTivo video share folder structure.<br>";
         text += "NOTE: You must have at least 1 series 4 or later TiVo with rpc style communications<br>";
         text += "enabled to use as a destination TiVo.<br>";
         text += "VIDEO RESTRICTIONS: Source video must be either:<br>";
         text += "<b>Unencrypted mpeg2 program stream</b> (.TiVo files won't work).<br>";
         text += "<b>mp4 container with H.264 video and either AC3 or AAC audio</b><br>";
         text += "Any other type of video won't work, and transcoding pyTivo videos won't work.";
      }
      else if (component.equals("refresh")) {
         text =  "<b>Refresh List</b><br>";
         text += "Refresh Now Playing List for this TiVo.";
      }
      else if (component.equals("back")) {
         text =  "<b>Back</b><br>";
         text += "Exit folder view and return to top level Now Playing List for this TiVo.";
      }
      else if (component.equals("metadata")) {
         text =  "<b>metadata</b><br>";
         text += "Creates a <b>pyTivo</b> compatible metadata file.<br>";
         text += "This is a text file that accompanies video file that contains<br>";
         text += "extended program information about the video file.<br>";
         text += "Useful if you use pyTivo to copy video files back to your Tivos.<br>";
         text += "Under configuration <b>Program Options</b> tab there is an option<br>";
         text += "called <b>metadata files</b> where you can specify which video files<br>";
         text += "to create metadata files for.";
      }
      else if (component.equals("decrypt")) {
         text =  "<b>decrypt</b><br>";
         text += "Decrypts encrypted TiVo files that were downloaded from TiVos.<br>";
         text += "Converts video file to normal unencrypted mpeg2 program stream format<br>";
         text += "which can be played back by most video players without need to have Tivo<br>";
         text += "Desktop installed. NOTE: This is quick and does not affect video quality.<br>";
         text += "This is also necessary before doing any further video file processing<br>";
         text += "with kmttg, so most often you should leave this option enabled.";
      }
      else if (component.equals("qsfix")) {
         text =  "<b>QS Fix</b><br>";
         text += "If you have VideoRedo available and configured in kmttg, this<br>";
         text += "runs the extremely useful <b>VideoRedo Quick Stream Fix</b> utility.<br>";
         text += "Without VideoRedo this will run mpeg through <b>ffmpeg</b> remux.<br>";
         text += "If neither tool is configured then this task is unavailable.<br>";
         text += "This task cleans up any potential glitches/errors in mpeg2 video files.<br>";
         text += "Highly recommended step if you have VideoRedo and/or ffmpeg installed.<br>";
         text += "Very highly recommended step if you will be further processing mpeg2 files<br>";
         text += "for cutting out commercials and/or encoding to new formats.";
      }
      else if (component.equals("twpdelete")) {
         text =  "<b>TWP Delete</b><br>";
         text += "If you have TivoWebPlus configured on your TiVo(s) then if you enable this task<br>";
         text += "a TivoWebPlus http call to delete show on TiVo will be issued following<br>";
         text += "successful decrypt of a downloaded .TiVo file.";
      }
      else if (component.equals("rpcdelete")) {
         text =  "<b>rpc Delete</b><br>";
         text += "If you have Series 4 (Premiere) TiVo or later with Network Remote setting enabled<br>";
         text += "then if you enable this task, rpc style communications will be used to<br>";
         text += "delete show on TiVo following successful decrypt of a downloaded .TiVo file.";
      }
      else if (component.equals("comskip")) {
         text =  "<b>Ad Detect</b><br>";
         text += "Automated commercials detection tool (defaults to <b>comskip</b> tool).<br>";
         text += "NOTE: Typically automated commercial detection is NOT very accurate.<br>";
         text += "NOTE: If you have <b>VideoRedo</b> enabled you can choose to use.<br>";
         text += "VideoRedo <b>AdScan</b> instead of comskip if you wish.<br>";
         text += "With VideoRedo configured you can also use this step to create a <b>.VPrj</b><br>";
         text += "file that you can open up in VideoRedo as a starting point for manual<br>";
         text += "commercial editing. See documentation for more details.";
      }
      else if (component.equals("comcut")) {
         text =  "<b>Ad Cut</b><br>";
         text += "Automatically cut out commercials detected in <b>Ad Detect</b> step.<br>";
         text += "NOTE: By default uses <b>ffmpeg</b> program to make the cuts if available/configured<br>";
         text += "in kmttg and VideoRedo not available/configured.<br>";
         text += "If you have <b>VideoRedo</b> enabled then this step uses VideoRedo for making<br>";
         text += "the cuts which is a better solution than ffmpeg for preserving proper audio/video sync.";
      }
      else if (component.equals("captions")) {
         text =  "<b>captions</b><br>";
         text += "Generates a <b>.srt</b> captions file which is a text file containing<br>";
         text += "closed captioning text. This file can be used with several<br>";
         text += "video playback tools to display closed captions during playback.<br>";
         text += "Also for example <b>streambaby</b> can use this file.";
      }
      else if (component.equals("encode")) {
         text =  "<b>encode</b><br>";
         text += "Encode mpeg2 video file to a different video format.<br>";
         text += "Select video format desired using <b>Encoding Profile</b>.<br>";
         text += "Useful to create videos compatible with portable devices or<br>";
         text += "to reduce file sizes.";
      }
      else if (component.equals("push")) {
         text =  "<b>push</b><br>";
         text += "Contact pyTivo server to initiate a push of a video file to a TiVo.<br>";
         text += "pyTivo server must be running and the file to be pushed should<br>";
         text += "reside in a defined pyTivo share directory. In order for this task<br>";
         text += "to be available you must define path to pyTivo.conf file in kmttg<br>";
         text += "configuration. The TiVo you want to push to is also defined there.";
      }
      else if (component.equals("custom")) {
         text =  "<b>custom</b><br>";
         text += "Run a custom script/program that you define in kmttg configuration.<br>";
         text += "This task is always the last task to run in set of tasks<br>";
         text += "and is useful for post-processing purposes.";
      }
      else if (component.equals("encoding")) {
         text =  "<b>Encoding Profile</b><br>";
         text += "Choose one of the pre-defined encoding profiles to<br>";
         text += "use when running <b>encode</b> step to encode to a<br>";
         text += "different video format. By convention there are 2 different<br>";
         text += "prefix names used for encoding profiles by kmttg:<br>";
         text += "<b>ff_</b> indicates <b>ffmpeg</b> encoding tool is used.<br>";
         text += "<b>hb_</b> indicates <b>handbrake</b> encoding tool is used.<br>";
         text += "NOTE: You can create your own custom encoding profiles.";
      }
      else if (component.equals("encoding2")) {
          text =  "<b>2nd Encoding Profile</b><br>";
          text += "This will let you select a second encoding profile to use<br>";
          text += "that will create a second file.<br>";
          text += "Choose one of the pre-defined encoding profiles to<br>";
          text += "use when running <b>encode</b> step to encode to a<br>";
          text += "different video format. By convention there are 2 different<br>";
          text += "prefix names used for encoding profiles by kmttg:<br>";
          text += "<b>ff_</b> indicates <b>ffmpeg</b> encoding tool is used.<br>";
          text += "<b>hb_</b> indicates <b>handbrake</b> encoding tool is used.<br>";
          text += "NOTE: You can create your own custom encoding profiles.";
       }
      else if (component.equals("encoding2_suffix")) {
          text =  "<b>Second Encoding Suffix</b><br>";
          text += "This will add a suffix to your second encoding file to <br>";
          text += "differentiate it from the first encoding. It will only be used<br>";
          text += "if you select an encoding profile for a second encoding.<br>";
          text += "If you enter 'iPhone' here then your second encoding will turn<br>";
          text += "out being named 'filename_iPhone.ext'";
       }
      else if (component.equals("start")) {
         text =  "<b>START JOBS</b><br>";
         text += "Run selected tasks for all selected items in the programs/files table below.<br>";
         text += "First select 1 or more items in the list below to process.<br>";
         text += "NOTE: You can press <b>s</b> on keyboard when focus is in NPL table to activate this button";
      }
      else if (component.equals("cancel")) {
         text =  "<b>CANCEL JOBS</b><br>";
         text += "Cancel selected jobs in <b>JOB MONITOR</b> table below.<br>";
         text += "First select 1 or more running or queued jobs in list below to abort/cancel.<br>";
         text += "NOTE: You can press <b>c</b> on keyboard when focus is in JOBS table to activate this button";
      }
      else if (component.equals("JobMonitor")) {
         text =  "<b>JOB</b><br>";
         text += "Double click on a running job to see program output.";
      }
      else if (component.equals("disk_usage")) {
         text =  "<b>Disk Usage</b><br>";
         text += "Display disk usage statistics and channel bit rate information for this TiVo";
      }
      else if (component.equals("total_disk_space")) {
         text =  "<b>Total Disk Space (GB)</b><br>";
         text += "Enter total disk space capacity in GB for this TiVo and then press <b>Enter</b><br>";
         text += "to update this window and save the value.";
      }
      else if (component.equals("export_npl")) {
         text =  "<b>Export</b><br>";
         text += "Export NPL entries to a csv file which can be easily imported into an Excel<br>";
         text += "spreadsheet or equivalent.<br>";
         text += "NOTE: The list is exported as displayed, so if you want all individual entries<br>";
         text += "in the spreadsheet then disable <b>Show Folders</b> before exporting.";
      }
      else if (component.equals("prune_skipTable")) {
         text =  "<b>Prune skipTable</b><br>";
         text += "Remove entries in AutoSkip table that are deleted from this TiVo.<br>";
         text += "Instead of manually having to prune AutoSkip table this is useful to automatically<br>";
         text += "remove entries that are no longer useful.";
      }
      else if (component.equals("import_skip")) {
         text =  "<b>Import skip</b><br>";
         text += "For selected entries in the table import skip information from comskip or VideoRedo project<br>";
         text += "files into skip table. Files are attempted to be located automatically based on the current<br>";
         text += "naming template and the defined locations for <b>.TiVo Output Dir</b> and/or <b>.mpg Output Dir</b>.<br>";
         text += "If no file is located automatically then you are prompted to provide one.";
      }
      
      return MyTooltip.make(text);
   }
   
   // Abstraction methods
   public void setTitle(final String s) {
      debug.print("s=" + s);
      Platform.runLater(new Runnable() {
         @Override public void run() {
            jFrame.setTitle(s);
         }
      });
   }
   public void text_print(String s) {
      debug.print("s=" + s);
      textp.print(s);
   }
   public void text_warn(String s) {
      debug.print("s=" + s);
      textp.warn(s);
   }
   public void text_error(String s) {
      debug.print("s=" + s);
      textp.error(s);
   }
   public void text_print(Stack<String> s) {
      debug.print("s=" + s);
      textp.print(s);
   }
   public void text_warn(Stack<String> s) {
      debug.print("s=" + s);
      textp.warn(s);
   }
   public void text_error(Stack<String> s) {
      debug.print("s=" + s);
      textp.error(s);
   }
   public void jobTab_packColumns(int pad) {
      debug.print("pad=" + pad);
      if (jobTab != null && jobTab.JobMonitor != null)
         TableUtil.autoSizeTableViewColumns(jobTab.JobMonitor, true);
   }
   public jobData jobTab_GetRowData(int row) {
      debug.print("row=" + row);
      return jobTab.GetRowData(row);
   }
   public void jobTab_UpdateJobMonitorRowStatus(final jobData job, final String status) {
      debug.print("job=" + job + " status=" + status);
      Platform.runLater(new Runnable() {
         @Override public void run() {
            jobTab.UpdateJobMonitorRowStatus(job, status);
         }
      });
   }
   public void jobTab_UpdateJobMonitorRowOutput(final jobData job, final String status) {
      debug.print("job=" + job + " status=" + status);
      Platform.runLater(new Runnable() {
         @Override public void run() {
            jobTab.UpdateJobMonitorRowOutput(job, status);
         }
      });
   }
   public void jobTab_AddJobMonitorRow(final jobData job, final String source, final String output) {
      debug.print("job=" + job + " source=" + source + " output=" + output);
      Platform.runLater(new Runnable() {
         @Override public void run() {
            jobTab.AddJobMonitorRow(job, source, output);
         }
      });
   }
   public void jobTab_RemoveJobMonitorRow(final jobData job) {
      debug.print("job=" + job);
      Platform.runLater(new Runnable() {
         @Override public void run() {
            jobTab.RemoveJobMonitorRow(job);
         }
      });
   }
   public void progressBar_setValue(final int value) {
      debug.print("value=" + value);
      Platform.runLater(new Runnable() {
         @Override public void run() {
            progressBar.setProgress((double)value/100.0);
         }
      });
   }
   public void refresh() {
      debug.print("");
      jContentPane.requestLayout();
   }
   public void nplTab_SetNowPlaying(String tivoName, Stack<Hashtable<String,String>> entries) {
      debug.print("tivoName=" + tivoName + " entries=" + entries);
      if (tivoTabs.containsKey(tivoName)) {
         tivoTabs.get(tivoName).nplTab_SetNowPlaying(entries);
      }
   }
   public void nplTab_clear(String tivoName) {
      debug.print("tivoName=" + tivoName);
      if (tivoTabs.containsKey(tivoName)) {
         tivoTabs.get(tivoName).nplTab_clear();
      }
   }
   public void nplTab_UpdateStatus(String tivoName, String status) {
      debug.print("tivoName=" + tivoName + " status=" + status);
      if (tivoTabs.containsKey(tivoName)) {
         tivoTabs.get(tivoName).nplTab_UpdateStatus(status);
      }
   }
   
   // Returns state of checkbox options (as int for writing to auto.ini purposes)
   public int metadata_setting() {
      debug.print("");
      int selected = 0;
      if (metadata.isSelected()) selected = 1;
      return selected;
   }
   public int decrypt_setting() {
      debug.print("");
      int selected = 0;
      if (decrypt.isSelected()) selected = 1;
      return selected;
   }
   public int qsfix_setting() {
      debug.print("");
      int selected = 0;
      if (qsfix.isSelected()) selected = 1;
      return selected;
   }
   public int twpdelete_setting() {
      debug.print("");
      int selected = 0;
      if (twpdelete.isSelected()) selected = 1;
      return selected;
   }
   public int rpcdelete_setting() {
      debug.print("");
      int selected = 0;
      if (rpcdelete.isSelected()) selected = 1;
      return selected;
   }
   public int comskip_setting() {
      debug.print("");
      int selected = 0;
      if (comskip.isSelected()) selected = 1;
      return selected;
   }
   public int comcut_setting() {
      debug.print("");
      int selected = 0;
      if (comcut.isSelected()) selected = 1;
      return selected;
   }
   public int captions_setting() {
      debug.print("");
      int selected = 0;
      if (captions.isSelected()) selected = 1;
      return selected;
   }
   public int encode_setting() {
      debug.print("");
      int selected = 0;
      if (encode.isSelected()) selected = 1;
      return selected;
   }
   /*public int push_setting() {
      debug.print("");
      int selected = 0;
      if (push.isSelected()) selected = 1;
      return selected;
   }*/
   public int custom_setting() {
      debug.print("");
      int selected = 0;
      if (custom.isSelected()) selected = 1;
      return selected;
   }
   
   // Identify NPL table items associated with queued/running jobs
   public void updateNPLjobStatus(Hashtable<String,String> map) {
      debug.print("map=" + map);
      Stack<String> tivoNames = config.getNplTivoNames();
      if (tivoNames.size() > 0) {
         for (int i=0; i<tivoNames.size(); i++) {
            tivoTab t = getTab(tivoNames.get(i));
            if (t != null) {
               nplTable npl = t.getTable();
               npl.updateNPLjobStatus(map);
            }
         }
      }
   }
   
   public String getWebColor(Color color) {
      debug.print("color=" + color);
      String c = String.format( "#%02X%02X%02X",
            (int)( color.getRed() * 255 ),
            (int)( color.getGreen() * 255 ),
            (int)( color.getBlue() * 255 ) );
      return(c);
   }


}
