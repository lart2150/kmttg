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
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedHashMap;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class help {
   private static Stage dialog = null;
   private static VBox content = null;
   
   static void showHelp() {
      debug.print("");
      if (dialog == null) {
         dialog = new Stage();
         dialog.initOwner(config.gui.getFrame());
         gui.LoadIcons(dialog);
         dialog.initModality(Modality.NONE); // Non modal
         dialog.setTitle("About kmttg");
         content = new VBox();
         content.setPadding(new Insets(0,0,5,0));
         content.setAlignment(Pos.CENTER);
         
         Label title = new Label(config.kmttg);
         title.setStyle("-fx-font-weight: bold");
         content.getChildren().add(title);
         
         final String version = getVersion();
         
         HBox row = new HBox();
         row.setSpacing(5);
         row.setAlignment(Pos.CENTER);
         Label lab1 = new Label("Latest version: ");
         Hyperlink link1 = new Hyperlink();
         link1.setStyle("-fx-text-fill: black;");
         link1.setText(version);
         link1.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent e) {
               showInBrowser("http://sourceforge.net/projects/kmttg/files/kmttg_" + version + ".zip");
            }
         });
         row.getChildren().addAll(lab1, link1);
         content.getChildren().add(row);
                  
         final LinkedHashMap<String,String> links = new LinkedHashMap<String,String>();
         links.put("kmttg Home Page", "http://sourceforge.net/p/kmttg/wiki/Home");
         links.put("kmttg downloads", "http://sourceforge.net/projects/kmttg/files");
         links.put("Release Notes", "http://sourceforge.net/p/kmttg/wiki/release_notes");
         links.put("kmttg configuration", "http://sourceforge.net/p/kmttg/wiki/configuring_kmttg");
         links.put("kmttg operation", "http://sourceforge.net/p/kmttg/wiki/using_kmttg");
         links.put("Setting up Auto Transfers", "http://sourceforge.net/p/kmttg/wiki/auto_transfers");
         links.put("Windows Installation", "http://sourceforge.net/p/kmttg/wiki/windows_installation");
         links.put("Mac OSX Installation", "http://sourceforge.net/p/kmttg/wiki/mac_osx_installation");
         links.put("Linux Installation", "http://sourceforge.net/p/kmttg/wiki/linux_installation");
         GridPane grid = new GridPane();
         grid.setHgap(5);
         grid.setAlignment(Pos.CENTER);
         int col = 0;
         int gy = 0;
         for (String s : links.keySet()) {
            Hyperlink link = new Hyperlink();
            link.setText(s);
            link.setStyle("-fx-text-fill: black;");
            link.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent e) {
                   Hyperlink h = (Hyperlink)e.getSource();
                   showInBrowser(links.get(h.getText()));
                }
            });
            grid.add(link, col, gy);
            if (col == 0)
               col = 1;
            else {
               col = 0;
               gy++;
            }
         }
         content.getChildren().add(grid);
                  
         Button ok = new Button("OK");
         ok.setPrefWidth(100);
         ok.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent e) {
               dialog.hide();
            }
         });
         content.getChildren().add(ok);
         dialog.setScene(new Scene(content));
         config.gui.setFontSize(dialog.getScene(), config.FontSize);
      }
      dialog.show();         
   }
   
   public static String getVersion() {
      debug.print("");
      String version = null;
      String version_url = "http://svn.code.sf.net/p/kmttg/code/trunk/version";
      try {
         URL url = new URL(version_url);
         URLConnection con = url.openConnection();
         BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
         String inputLine;
         while ((inputLine = in.readLine()) != null) 
            version = inputLine;
         in.close();
      } catch (Exception ex) {
         version = null;
      }
      return version;
   }
   
   public static boolean showInBrowser(String url) {
      debug.print("url=" + url);
      log.warn("Web browser query: " + url);
      String os = System.getProperty("os.name").toLowerCase();
      Runtime rt = Runtime.getRuntime();
      try {
         if (os.indexOf( "win" ) >= 0) {
            String[] cmd = new String[3];
            cmd[0] = "rundll32";
            cmd[1] = "url.dll,FileProtocolHandler";
            cmd[2] = url;
            rt.exec(cmd);
         } else if (os.indexOf("mac") >= 0) {
            rt.exec(new String[] {"open", url});
         } else {
            if (config.web_browser.length() > 0) {
               // Call user provided browser
               rt.exec(new String[] {config.web_browser, url});
            } else {
               //prioritized 'guess' of users' preference
               String[] browsers = {"epiphany", "firefox", "mozilla", "opera", "konqueror", "netscape", "links", "lynx"};
     
               StringBuffer cmd = new StringBuffer();
               for (int i=0; i<browsers.length; i++)
                  cmd.append( (i==0  ? "" : " || " ) + browsers[i] +" \"" + url + "\" ");
     
               rt.exec(new String[] { "sh", "-c", cmd.toString() });
            }
         }
      }
      catch (IOException e) {
         e.printStackTrace();  
         return false;
      }
      return true;
   }
}
