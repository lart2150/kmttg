package com.tivo.kmttg.main;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Date;
import java.util.Hashtable;

import com.tivo.kmttg.util.log;

public class beacon {
 
   static DatagramSocket server;
   static DatagramPacket packet;
   static int serverPort = 2190;
   static int listen_timeout = 100; // millisecs timeout for non-blocking receive
   byte[] data = new byte[1024];
   static int timeout = 5;          // ~mins after which beacon listening disabled
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
      // If running longer than timeout, disable
      if (server != null) {
         long now = new Date().getTime();
         if ( (now-start_time)/(1000*60) > timeout ) {
            server.close();
            server = null;
         }
      }
      
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
            if (h.containsKey("machine")) return h;
         }
      }
      return null;
   }
}


