package com.tivo.kmttg.main;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Hashtable;
import java.util.Stack;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import com.tivo.kmttg.util.log;

public class mdns {
   private JmDNS jmdns = null;
   private int timeout = 5;          // ~mins after which mdns listening disabled
   private long start_time;
   
   public mdns() {
      try {
         jmdns = JmDNS.create(InetAddress.getLocalHost());
         start_time = new Date().getTime();
      } catch (UnknownHostException e) {
         log.error("mdns error: " + e.getMessage());
      } catch (IOException e) {
         log.error(e.getMessage());
      }
   }
   
   public void close() {
      if (jmdns != null) jmdns.close();
      jmdns = null;
   }
   
   public void process() {
      // If running longer than timeout, disable
      if (jmdns != null) {
         long now = new Date().getTime();
         if ( (now-start_time)/(1000*60) > timeout ) {
            jmdns.close();
            jmdns = null;
         }
      }
      
      if (jmdns == null) return;
      ServiceInfo info[] = jmdns.list("_http._tcp.local.");
      if (info.length > 0) {
         Stack<String> tivoNames = config.getTivoNames();
         // Step through list of found host names
         for (int i=0; i<info.length; ++i) {
            String name = info[i].getName();
            if (name != null) {
               Boolean add = true;
               for (int j=0; j<tivoNames.size(); ++j) {
                  if ( tivoNames.get(j).matches(name) ) {
                     add = false;
                  }
               }
               if (add) {
                  // This tivo not part of current kmttg list so add it
                  String tsn = info[i].getPropertyString("TSN");
                  if (tsn != null) {
                     Hashtable<String,String> b = new Hashtable<String,String>();
                     b.put("ip", info[i].getHostAddress());
                     b.put("machine", name);
                     config.addTivo(b);
                  }
               }
            }
         }
      }
   }
}
