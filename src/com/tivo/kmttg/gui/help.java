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
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Enumeration;
import java.util.LinkedHashMap;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import net.miginfocom.swing.MigLayout;

import com.tivo.kmttg.gui.swing.SwingUtil;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.GetKeyStore;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class help {
   private static JDialog dialog = null;
   private static JPanel content = null;
   
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
         dialog = new JDialog(config.gui.getFrame()); // Non modal
         SwingUtil.loadIcons(dialog);
         dialog.setTitle("About kmttg");
         content = new JPanel();
         content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

         JLabel title = new JLabel(config.kmttg);
         title.setFont(title.getFont().deriveFont(Font.BOLD));
         title.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
         content.add(title);

         final String version = getVersion();

         JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
         JLabel lab1 = new JLabel("Latest version: ");
         JButton link1 = makeLink(version);
         link1.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               showInBrowser("https://github.com/lart2150/kmttg/releases/latest");
            }
         });
         row.add(lab1);
         row.add(link1);
         content.add(row);

         JPanel certRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
         certRow.add(new JLabel("Certificate Expires: "));
         certRow.add(new JLabel(help.getKeyExpires()));
         content.add(certRow);

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
         JPanel grid = new JPanel(new MigLayout("gapx 5"));
         int col = 0;
         int gy = 0;
         for (String s : links.keySet()) {
            JButton link = makeLink(s);
            link.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                   JButton h = (JButton)e.getSource();
                   showInBrowser(links.get(h.getText()));
                }
            });
            grid.add(link, "cell " + col + " " + gy);
            if (col == 0)
               col = 1;
            else {
               col = 0;
               gy++;
            }
         }
         content.add(grid);

         JButton ok = new JButton("OK");
         ok.setPreferredSize(new java.awt.Dimension(100, ok.getPreferredSize().height));
         ok.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
         ok.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               dialog.setVisible(false);
            }
         });
         content.add(ok);
         dialog.getContentPane().add(content);
         dialog.pack();
         dialog.setLocationRelativeTo(config.gui.getFrame());
      }
      dialog.setVisible(true);
   }

   // Borderless button used as a hyperlink replacement
   private static JButton makeLink(String text) {
      JButton link = new JButton(text);
      link.setBorderPainted(false);
      link.setContentAreaFilled(false);
      link.setForeground(Color.BLUE);
      link.setHorizontalAlignment(SwingConstants.LEFT);
      link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      return link;
   }
   
   public static String getVersion() {
      debug.print("");
      HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
      HttpClient httpClient = httpClientBuilder.build();
      String version = null;
      String version_url = "https://raw.githubusercontent.com/lart2150/kmttg/master/version";
      try {
    	 HttpGet httpget = new HttpGet(version_url);
         version = httpClient.execute(httpget, response -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
               return in.readLine();
            }
         });
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
