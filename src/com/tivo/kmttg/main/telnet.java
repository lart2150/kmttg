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
package com.tivo.kmttg.main;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import com.tivo.kmttg.util.log;

public class telnet {
   public static final int DEFAULT_BUTTON_INTERVAL = 100;
   
   private String IP = null;
   private int port = 31339;
   private Socket socket = null;
   private int timeout = 5;
   private int button_interval = DEFAULT_BUTTON_INTERVAL;
   private PrintStream pout = null;
   public Boolean success = true;
   private String valid[] = {
       "NUM0", "NUM1", "NUM2", "NUM3", "NUM4", "NUM5", "NUM6", "NUM7", "NUM8", "NUM9",
       "TIVO", "INFO", "LIVETV", "GUIDE", "WINDOW",
       "LEFT", "UP", "RIGHT", "DOWN", "SELECT", "THUMBSUP", "THUMBSDOWN",
       "CHANNELUP", "CHANNELDOWN", "RECORD", "CLEAR", "ENTER",
       "PLAY", "REVERSE", "PAUSE", "FORWARD", "REPLAY", "SLOW", "ADVANCE",
       "CC_ON", "CC_OFF", "STANDBY", "NOWSHOWING", "FIND_REMOTE",
       "NETFLIX", "VIDEO_ON_DEMAND", "EXIT", "SEARCH"
   };
   
   // Constructor for dual tuner channel change
   public telnet(String IP, int pause_time, int button_interval, String[] seq1, String[] seq2) {
      try {
         telnet t1 = new telnet(IP, seq1, button_interval);
         if (t1.success) {
            Thread.sleep(1000*pause_time);
            t1 = new telnet(IP, seq2, button_interval);
            success = t1.success;
         } else {
            success = false;
         }
      } catch (Exception e) {
         log.error("telnet - " + e.getMessage());
         success = false;
      }
   }

   // Constructor for single sequence
   public telnet(String IP, String[] buttons) {
	   this(IP, buttons, DEFAULT_BUTTON_INTERVAL);
   }
   /** Constructor for single sequence with custom interval */
   public telnet(String IP, String[] buttons, int button_interval) {
      this.IP = IP;
      this.button_interval = button_interval;
      success = send(buttons);
      disconnect();
   }
   
   /** Constructor to run a single unvalidated, unmapped command */
   public telnet(String IP, String command) {
      this.IP = IP;
      success = write(command);
      disconnect();
   }
   
   private String mapButton(String code) {
      String mapped = "";
      if (code.matches("^[a-z]$")) {
         mapped = "KEYBOARD " + code.toUpperCase();
         return mapped;
      }
      if (code.matches("^[A-Z]$")) {
         mapped = "KEYBOARD LSHIFT\rKEYBOARD " + code;
         return mapped;
      }
      if (code.length() == 1) {
         boolean shift = true;
         switch(code.charAt(0)) {
         case '_': code = "MINUS";break;
         case '+': code = "EQUALS";break;
         case '{': code = "LBRACKET";break;
         case '}': code = "RBRACKET";break;
         case '|': code = "BACKSLASH";break;
         case ':': code = "SEMICOLON";break;
         case '"': code = "QUOTE";break;
         case '<': code = "COMMA";break;
         case '>': code = "PERIOD";break;
         case '?': code = "SLASH";break;
         case '~': code = "BACKQUOTE";break;
         case '!': code = "NUM1";break;
         case '@': code = "NUM2";break;
         case '#': code = "NUM3";break;
         case '$': code = "NUM4";break;
         case '%': code = "NUM5";break;
         case '^': code = "NUM6";break;
         case '&': code = "NUM7";break;
         case '*': code = "NUM8";break;
         case '(': code = "NUM9";break;
         case ')': code = "NUM0";break;
         default: shift = false;break;
         }
         if (shift) {
            mapped = "KEYBOARD LSHIFT\rKEYBOARD "+code;
            return mapped;
         }
      }
      mapped = code.toUpperCase();
      // [0-9] maps to NUM[0-9]
      if (mapped.matches("^\\d$")) {
         mapped = "NUM" + code;
      }
      
      // - and . map to ADVANCE
      if (mapped.equals("-") || mapped.equals(".")) {
         mapped = "ADVANCE";
      }
      
      // space maps to FORWARD
      if (mapped.matches("^\\s+$")) {
         mapped = "FORWARD";
      }
      
      // Check that mapped is among valid strings
      Boolean good = false;
      for (int i=0; i<valid.length; ++i) {
         if (mapped.equals(valid[i])) {
            good = true;
         }
      }
      
      if (good) {
         mapped = "IRCODE " + mapped;
         return mapped;
      }
      
      log.error("telnet - Unrecognized or invalid button code: " + mapped);
      success = false;
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
         Thread.sleep(button_interval);
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
         if ( ! write(mapped ) ) return false;
      }
      return true;
   }

}
