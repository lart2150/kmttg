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

import java.net.DatagramSocket;
import java.net.InetAddress;
//import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Stack;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import com.tivo.kmttg.util.log;

public class mdns {
   private JmDNS jmdns = null;
   private Hashtable<String,ServiceInfo> SERVICE = new Hashtable<String,ServiceInfo>();
   //private int timeout = 5;          // ~mins after which mdns listening disabled
   //private long start_time;
   
   public mdns() {
      try {
         DatagramSocket socket = new DatagramSocket();
         socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
         InetAddress IP = socket.getLocalAddress();
         if (! IP.isReachable(3000))
            IP = InetAddress.getLocalHost();
         if (! IP.isReachable(3000))
            log.error("mdns error: Can't determine valid IP for host running kmttg");
         //log.print("IP=" + socket.getLocalAddress().getHostAddress());
         jmdns = JmDNS.create(IP);
         socket.close();
         //start_time = new Date().getTime();
      } catch (Exception e) {
         log.error("mdns error: " + e.getMessage());
      } 
   }
   
   public void close() {
      if (jmdns != null) {
         try {
            jmdns.close();
         } catch (Exception e) {
            log.error("jmdns close - " + e.getMessage());
         }
      }
      jmdns = null;
   }
   
   public void process() {
      /* INTENTIONALLY DISABLED TIMEOUT - WANT POTENTIAL IP UPDATES TO HAPPEN
      // If running longer than timeout, disable
      if (jmdns != null) {
         long now = new Date().getTime();
         if ( (now-start_time)/(1000*60) > timeout ) {
            close();
         }
      }
      */
      
      if (jmdns == null) return;
      
      // Uncomment this to print/log specific service information
      //printService("_tivo-mindrpc._tcp.local.");
      //printService("_tivo-videostream._tcp.local.");
      //printService("_http._tcp.local.");
      
      ServiceInfo info[] = jmdns.list("_http._tcp.local.");
      if (info.length > 0) {
         Stack<String> tivoNames = config.getTivoNames();
         // Step through list of found host names
         for (int i=0; i<info.length; ++i) {
            // No tsn => not a tivo
            String tsn = info[i].getPropertyString("TSN");
            String platform = info[i].getPropertyString("platform");
            // Ignore certain devices like TiVo Stream which starts with TSN "A94"
            if (tsn != null && tsn.startsWith("A94"))
               tsn = null;
            if (platform != null && platform.contains("Silver"))
               tsn = null;
            if (tsn != null) {
               Boolean add = true;
               String name = info[i].getName();
               if (name != null) {
                  // Check against current tivo list
                  for (int j=0; j<tivoNames.size(); ++j) {
                     if ( tivoNames.get(j).equals(name) ) {
                        add = false;
                     }
                  }
                  // Update existing IP if necessary (for case if DHCP updates IP of existing Tivo)
                  if (add == false) {
                     if (! info[i].getHostAddress().equals(config.TIVOS.get(name))) {
                        log.warn("Updating IP for TiVo: " + name);
                        config.TIVOS.put(name, info[i].getHostAddress());
                        config.save();
                     }
                  }
                  // Update TSN if necessary
                  if (add == false) {
                     String config_tsn = config.getTsn(name);
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
               } else {
                  add = false;
               }
               if (add) {
                  // This tivo not part of current kmttg list so add it
                  Hashtable<String,String> b = new Hashtable<String,String>();
                  b.put("ip", info[i].getHostAddress());
                  b.put("machine", name);
                  b.put("identity", tsn);
                  config.addTivo(b);
               }
            }
         }
      }
   }
   
   // This method useful for discovering RPC servers on the LAN
   // Sample names:
   // "_tivo-mindrpc._tcp.local."
   // "_tivo-videostream._tcp.local."
   public void printService(String service) {
      ServiceInfo info[] = jmdns.list(service);
      if (info.length > 0) {
         for (int i=0; i<info.length; ++i) {
            if ( ! SERVICE.containsKey(info[i].getName()) ) {
               SERVICE.put(info[i].getName(), info[i]);
               log.warn("MDNS: " + info[i].getName() + " (" + info[i].getHostAddress() + ":" + info[i].getPort() + ")");
               Enumeration<?> e = info[i].getPropertyNames();
               while (e.hasMoreElements()) {
                  String key = (String) e.nextElement();
                  log.print(key + "=" + info[i].getPropertyString(key));
               }
            }
         }
      }
   }
}
