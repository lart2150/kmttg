/*
 * Copyright 2008-Present Kevin Moye <moyekj@yahoo.com>.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.tivo.kmttg.gui;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import javax.swing.JOptionPane;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.log;

/**
 * Opens the existing local native-HLS browser player from a TiVo's Now Playing tab.
 * Streaming authorization, content-copy policy and session ownership remain on
 * NativeHlsWeb; the desktop only passes the selected recording identifier.
 */
public final class NativeHlsDesktop {
   private NativeHlsDesktop() {}

   /** Construct a loopback-only browser destination, never the TiVo HTTP key URL. */
   public static URI playerUri(String tivo, String recordingId, int port) {
      if (tivo == null || tivo.isBlank() ||
            recordingId == null || !recordingId.matches("[A-Za-z0-9:._-]+") ||
            port < 1 || port > 65535)
         throw new IllegalArgumentException("Invalid TiVo, recording, or web-server port");

      String query = "tivo=" + URLEncoder.encode(tivo, StandardCharsets.UTF_8)
            + "&recordingId=" + URLEncoder.encode(recordingId, StandardCharsets.UTF_8);
      return URI.create("http://127.0.0.1:" + port + "/web/NativeHls.html?" + query);
   }

   /** The normal kmttg web server supplies the session-scoped HLS proxy. */
   public static boolean isAvailable(String tivo) {
      return config.rpcEnabled(tivo) && config.httpserver != null
            && config.httpserver_port > 0;
   }

   public static void play(String tivo, String recordingId) {
      if (!isAvailable(tivo)) {
         JOptionPane.showMessageDialog(config.gui.getFrame(),
               "Enable the kmttg web server in File > Configure > Web Server, "
               + "then restart kmttg to stream recordings to this computer.",
               "Stream from TiVo", JOptionPane.INFORMATION_MESSAGE);
         return;
      }
      try {
         if (!Desktop.isDesktopSupported()
               || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
            throw new IOException("This desktop does not support opening a browser");
         Desktop.getDesktop().browse(playerUri(tivo, recordingId, config.httpserver_port));
      } catch (Exception e) {
         log.error("TiVo streaming browser launch failed: " + e.getMessage());
         JOptionPane.showMessageDialog(config.gui.getFrame(),
               "Could not open the TiVo streaming player: " + e.getMessage(),
               "Stream from TiVo", JOptionPane.ERROR_MESSAGE);
      }
   }
}
