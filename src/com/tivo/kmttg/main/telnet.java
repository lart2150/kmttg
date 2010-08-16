package com.tivo.kmttg.main;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import com.tivo.kmttg.util.log;

public class telnet {
   private String IP = null;
   private int port = 31339;
   private Socket socket = null;
   private int timeout = 5;
   private PrintStream pout = null;
   private String valid[] = {
       "NUM0", "NUM1", "NUM2", "NUM3", "NUM4", "NUM5", "NUM6", "NUM7", "NUM8", "NUM9",
       "TIVO", "INFO", "LIVETV", "GUIDE", "WINDOW",
       "LEFT", "UP", "RIGHT", "DOWN", "SELECT", "THUMBSUP", "THUMBSDOWN",
       "CHANNELUP", "CHANNELDOWN", "RECORD", "CLEAR", "ENTER",
       "PLAY", "REVERSE", "PAUSE", "FORWARD", "REPLAY", "SLOW", "ADVANCE",
   };

   public telnet(String IP, String[] buttons) {
      this.IP = IP;
      send(buttons);
      disconnect();
   }
   
   private String mapButton(String code) {
      String mapped = code.toUpperCase();
      // [0-9] maps to NUM[0-9]
      if (code.matches("^\\d$")) {
         mapped = "NUM" + code;
      }
      
      // Check that mapped is among valid strings
      Boolean good = false;
      for (int i=0; i<valid.length; ++i) {
         if (mapped.equals(valid[i])) {
            good = true;
         }
      }
      if (good)
         return mapped;
      
      log.error("telnet - Unrecognized or invalid button code: " + mapped);
      return null;
   }
      
   private Boolean connect() {
      if (socket != null) {
         if (socket.isConnected()) return true;
      }
      try {
         socket = new Socket();
         InetSocketAddress socketAddress = new InetSocketAddress(IP, port);
         socket.connect(socketAddress, timeout*1000);
         if (socket.isConnected()) {
            socket.setKeepAlive(true);
            OutputStream out = socket.getOutputStream();
            pout = new PrintStream(out);
            return true;
         }
         return false;
      } catch (UnknownHostException e) {
         log.error("telnet - Unknown host: " + IP);
         return false;
      } catch (Exception e) {
         log.error("telnet - Failed to connect to host: " + IP);
         log.error("telnet - " + e.getMessage());
         return false;
      }
   }
  
   private void disconnect() {
      if (pout != null) {
         pout.close();
      }
      if (socket != null) {
         try {
            socket.close();
         } catch (IOException e) {
            log.error("telnet - " + e.getMessage());
         }
      }
   }
  
   private Boolean write(String s) {
      if (socket == null) {
         if ( ! connect() ) return false;
      }
      try {
         pout.println(s + "\r");
         Thread.sleep(100L);
      } catch (InterruptedException e) {
         log.error("telnet - " + e.getMessage());
         return false;
      }
      return true;
   }
  
   private Boolean send(String[] codes) {
      for (int i=0; i<codes.length; ++i) {
         String mapped = mapButton(codes[i]);
         if ( mapped == null ) return false;
         if ( ! write("IRCODE " + mapped ) ) return false;
      }
      return true;
   }

}
