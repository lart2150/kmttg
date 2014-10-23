package com.tivo.kmttg.httpserver;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;

public class AvailableSocket {
   public  static final int MAX_SOCKETS = 500;
   public static final int FIRST_TRY_SOCKET = 8500;
   private static final int END_TRY_SOCKET = FIRST_TRY_SOCKET + MAX_SOCKETS;
   
   private static int maxSockets = MAX_SOCKETS;
   private static int socketStart = FIRST_TRY_SOCKET;
   private static int socketEnd = END_TRY_SOCKET;
   private static Set<Integer> usedSockets = new HashSet<Integer>();
   
   private int socket = -1;
   private InetAddress bindAddr;
   public AvailableSocket(InetAddress bind) throws SocketNotAvailable {
      close();
      this.bindAddr = bind;
      int s = socketStart;
      while(s < socketEnd && !open(s)) 
         s++;
      if (socket == -1)
         throw new SocketNotAvailable("No sockets available");
   }
   
   public AvailableSocket() throws SocketNotAvailable, UnknownHostException {
      this(InetAddress.getByName(null));
   }
   public synchronized boolean open(int s) {
      if (usedSockets.contains(new Integer(s)))
            return false;
      try {
         ServerSocket server = new ServerSocket(s, 1, bindAddr );
         server.close();
      } catch(IOException e) {
         return false;
      }
      socket = s;
      usedSockets.add(new Integer(s));
      return true;
   }
   public synchronized void close() {
      if (socket != -1) {
         usedSockets.remove(new Integer(socket));
         socket = -1;
      }
   }
   protected void finalize() throws Throwable {
        super.finalize();
        close();
   }
   
   public int getSocket() {
      return socket;
   }
   
   /*
   public static void setSocketRange(int start, int end) {
      socketStart = start;
      socketEnd = end;
   }
   */
   
   public static void setStartSocket(int start) {
      socketStart = start;
      socketEnd = socketStart + maxSockets;
   }
   
   public static void setMaxSockets(int range) {
      maxSockets = range;
      socketEnd = socketStart + maxSockets;
   }
   
   public class SocketNotAvailable extends Exception {

      /**
       * 
       */
      private static final long serialVersionUID = 1L;

      public SocketNotAvailable(final String e) {
         super(e);
      }
   }
}
