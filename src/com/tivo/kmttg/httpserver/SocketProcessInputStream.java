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
package com.tivo.kmttg.httpserver;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

import com.tivo.kmttg.httpserver.AvailableSocket.SocketNotAvailable;
import com.tivo.kmttg.util.log;

public class SocketProcessInputStream extends InputStream  {

   boolean hasError = false;
   AvailableSocket avail;
   ServerSocket serverSocket;
   Socket socket;
   InputStream stream;
   Process _process;
   int timeout = 10 * 1000;
   public SocketProcessInputStream() throws SocketNotAvailable, IOException  {
      avail = new AvailableSocket();
      serverSocket = new ServerSocket(avail.getSocket(), 1, InetAddress.getByName(null));
   }
   @Override
   public void close() {
      if (getProcess() != null) {
         log.print("Destroying running process...");
         getProcess().destroy();
      }
      _process = null;
      if (serverSocket != null)
         try {
            serverSocket.close();
         } catch (IOException e) {

         }
      serverSocket = null;
      if (avail != null) {
         avail.close();
      }
      avail = null;
      if (socket != null)
         try {
            socket.close();
         } catch (IOException e) {
         }
      socket = null;
      if (stream != null) {
         try {
            stream.close();
         } catch (IOException e) {
   
         }
      }
      stream = null;
   }

   void possiblyInitSocket() throws IOException {
      if (stream != null)
         return;
      if (hasError)
         throw new IOException("Already exceptioned");
      long myTimeout = timeout;
      while(!hasError && myTimeout > 0 && socket == null) {
         try {
            serverSocket.setSoTimeout(100);
            socket = serverSocket.accept();
         } catch (SocketTimeoutException e) {
            myTimeout -= 100;
            Process p = getProcess();
            if (p != null) {
               try {
                  p.exitValue();
                  hasError = true;
               } catch(IllegalThreadStateException se) {
                  // this is actually good...
               }
            }
         }
      }
      if (socket == null) {
         hasError = true;
         log.warn("Socket was never accepted!");
         throw new IOException("socket not accepted");
      }

      stream = new BufferedInputStream(socket.getInputStream());
   }
   
   @Override
   public int read() throws IOException {
      possiblyInitSocket();
      return stream.read();
   }
   @Override
   public int  read(byte[] b) throws IOException {
      possiblyInitSocket();      
      return stream.read(b);
   }
   @Override
   public int  read(byte[] b, int off, int len) throws IOException {
      possiblyInitSocket();
      return stream.read(b, off, len);
   }
   public int getPort() {
      return serverSocket.getLocalPort();
   }
   public synchronized void attachProcess(Process p) {
      _process = p;
   }
   
   public synchronized Process getProcess() {
      return _process;
   }

}
