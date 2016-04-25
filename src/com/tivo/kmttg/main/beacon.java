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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Date;
import java.util.Hashtable;
import java.util.Stack;

import com.tivo.kmttg.util.log;

public class beacon {
 
   static DatagramSocket server;
   static DatagramPacket packet;
   static int serverPort = 2190;
   static int listen_timeout = 100; // millisecs timeout for non-blocking receive
   byte[] data = new byte[1024];
   static long start_time;
   
   public beacon() {
      try {
         server = new DatagramSocket(serverPort);
         server.setSoTimeout(listen_timeout);
         start_time = new Date().getTime();
         
      } catch (IOException e) {
         log.error(e.getMessage());
         server = null;
      }
   }
   
   public Hashtable<String,String> listen() {
      // Not disabled => listen in
      if (server != null) {
         try {
            packet = new DatagramPacket(data, data.length);
            server.receive(packet);
         } catch (Exception ex) {
            // IGNORE
         }
         if ( packet.getAddress() != null ) {
            Hashtable<String,String> h = new Hashtable<String,String>();
            String ip = packet.getAddress().toString().replaceFirst("/", "");
            h.put("ip", ip);
            String s = new String(packet.getData());
            String[] l = s.split("\n");
            String name, value;
            for (int i=0; i<l.length; ++i) {
               name  = l[i].replaceFirst("^(.+)=(.+)$", "$1");
               value = l[i].replaceFirst("^(.+)=(.+)$", "$2");
               h.put(name,value);
            }
            // Filter out pyTivo broadcasts (for tivos: platform = tcd/...)
            if (h.containsKey("platform")) {
               if (! h.get("platform").matches("^tcd.+$") ) return null;
               if (h.get("platform").contains("Silverstreak")) return null;
            }
            // Filter out TiVo Stream device with TSN starting with "A94"
            if (h.containsKey("identity") && h.get("identity").startsWith("A94")) return null;
            if (h.containsKey("machine")) return h;
         }
      }
      return null;
   }

   // Listen on tivo_beacon for any newly detected tivos
   public void tivoBeaconUpdate() {
      Hashtable<String,String> b = listen();
      if (b != null) {
         // Check against current Tivos list
         Stack<String> tivoNames = config.getTivoNames();
         Boolean add = true;
         for (int i=0; i<tivoNames.size(); ++i) {
            if ( tivoNames.get(i).matches(b.get("machine")) ) {
               add = false;
            }
         }
         if (add) {
            config.addTivo(b);
         } else {
            // Update existing IP if necessary (for case if DHCP updates IP of existing Tivo)
            String name = b.get("machine");
            String ip = b.get("ip");
            if (! ip.equals(config.TIVOS.get(name))) {
               log.warn("Updating IP for TiVo: " + name);
               config.TIVOS.put(name, ip);
               config.save();
            }
            // Update TSN if necessary
            if (b.containsKey("identity")) {
               String config_tsn = config.getTsn(name);
               String tsn = b.get("identity");
               if (config_tsn == null) {
                  config.setTsn(name, tsn);
                  config_tsn = tsn;
                  config.save();
               }
               if ( ! config_tsn.equals(tsn) ) {
                  config.setTsn(name, tsn);
                  config.save();
               }
            }
         }
      }
   }
}


