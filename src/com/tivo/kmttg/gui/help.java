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
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Enumeration;
import java.util.LinkedHashMap;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;

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
import com.tivo.kmttg.util.GetKeyStore;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class help {
   private static Stage dialog = null;
   private static VBox content = null;
   
   static String getKeyExpires() {
      GetKeyStore getKeyStore;
      try {
         getKeyStore = new GetKeyStore(null, config.programDir);
         KeyStore keyStore = getKeyStore.getKeyStore();

         Enumeration<String> aliases = keyStore.aliases();

         while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            X509Certificate crt = (X509Certificate) keyStore.getCertificate(alias);
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MMM d YYYY");
            return simpleDateFormat.format(crt.getNotAfter());
         }

         return "No certs in cstore";
      } catch (Exception e) {
         System.out.println("Error Loading cert");
         System.out.println(e);
         return "Error Loading Cert";
      }
   }
   
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
               showInBrowser("https://github.com/lart2150/kmttg/releases/latest");
            }
         });
         row.getChildren().addAll(lab1, link1);
         content.getChildren().add(row);
         
         HBox certRow = new HBox();
         certRow.setSpacing(5);
         certRow.setAlignment(Pos.CENTER);
         certRow.getChildren().addAll(
               new Label("Certificate Expires: "),
               new Label(help.getKeyExpires())
         );
         content.getChildren().add(certRow);

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
         Scene scene = new Scene(content);
         config.gui.addScene(scene);
         dialog.setScene(scene);
         config.gui.setFontSize(dialog.getScene(), config.FontSize);
      }
      dialog.show();         
   }
   
   public static String getVersion() {
      debug.print("");
      HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
      HttpClient httpClient = httpClientBuilder.build();
      String version = null;
      String version_url = "https://raw.githubusercontent.com/lart2150/kmttg/master/version";
      try {
    	 HttpGet httpget = new HttpGet(version_url);
    	 CloseableHttpResponse response = (CloseableHttpResponse) httpClient.execute(httpget);
         BufferedReader in = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
         version = in.readLine();
         in.close();
      } catch (Exception ex) {
    	  log.error(ex.getMessage());
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
