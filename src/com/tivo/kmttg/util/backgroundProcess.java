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
package com.tivo.kmttg.util;

import java.io.IOException;
import java.io.OutputStream;
import java.util.NoSuchElementException;
import java.util.Stack;
import java.util.concurrent.TimeoutException;

public class backgroundProcess {
   private Process proc;
   private ChildDataHandler stderrHandler;
   private ChildDataHandler stdoutHandler;
   private Stack<String> stderr = new Stack<String>();
   private Stack<String> stdout = new Stack<String>();
   private String[] command;
   

   public Boolean run(Stack<String> command) {
      debug.print("command=" + command);
      this.command = new String[command.size()];
      for (int i=0; i<command.size(); i++) {
         this.command[i] = command.get(i);
      }
      try {
         proc = Runtime.getRuntime().exec(this.command);
         stderrHandler = new ChildDataHandler(proc.getErrorStream(), stderr);
         stdoutHandler = new ChildDataHandler(proc.getInputStream(), stdout);

         // Capture stdout/stderr to string stacks
         stderrHandler.start();
         stdoutHandler.start();
      } catch (IOException e) {
         stderr.add(e.getMessage());
         return false;
      } catch (NullPointerException e) {
         stderr.add(e.getMessage());
         return false;
      }
      return true;
   }
   
   // Wait with no timeout
   public int Wait() {
      try {
         int r = proc.waitFor();
         stdoutHandler.close();
         stderrHandler.close();
         return r;
      } catch (InterruptedException e) {
         return -1;
      }
   }
   
   // Wait with timeout
   public int Wait(long timeout) throws IOException, InterruptedException, TimeoutException {
      
      class Worker extends Thread {
         private final Process process;
         private Integer exit;
         private Worker(Process process) {
           this.process = process;
         }
         public void run() {
           try { 
             exit = process.waitFor();
           } catch (InterruptedException ignore) {
             return;
           }
         }  
      }
      
      Worker worker = new Worker(proc);
      worker.start();
      try {
        worker.join(timeout);
        if (worker.exit != null)
          return worker.exit;
        else
          throw new TimeoutException();
      } catch(InterruptedException ex) {
        worker.interrupt();
        Thread.currentThread().interrupt();
        throw ex;
      } finally {
        proc.destroy();
        stdoutHandler.close();
        stderrHandler.close();
      }
    }
   
   public Boolean isRunning() {
      debug.print("");
      try {
         proc.exitValue();
         return true;
      }
      catch (IllegalThreadStateException i) {
         return false;
      }
   }
   
   // Return -1 if still running, exit code otherwise
   public int exitStatus() {
      //debug.print("");
      try {
         int v = proc.exitValue();
         return v;
      }
      catch (IllegalThreadStateException i) {
         return -1;
      }
   }
   
   public void kill() {
      debug.print("");
      proc.destroy();
   }
   
   public Stack<String> getStderr() {
      debug.print("");
      return stderr;
   }
   
   public Stack<String> getStdout() {
      debug.print("");
      return stdout;
   }
   
   public String getStderrLast() {
      debug.print("");
      try {
         return stderr.lastElement();
      }
      catch (NoSuchElementException n) {
         return "";
      }
   }
   
   public String getStdoutLast() {
      debug.print("");
      try {
         return stdout.lastElement();
      }
      catch (NoSuchElementException n) {
         return "";
      }
   }

   // NOTE: This is used by taskInfo
   public void setStdoutWatch(Stack<String> s) {
      debug.print("s=" + s);
      stdoutHandler.watch = s;
   }

   // NOTE: This is used by taskInfo
   public void setStderrWatch(Stack<String> s) {
      debug.print("s=" + s);
      stderrHandler.watch = s;
   }
   
   public void printStderr() {
      debug.print("");
      log.error(stderr);
   }
   
   public void printStdout() {
      debug.print("");
      log.print(stdout);
   }
   
   public String toString() {
      debug.print("");
      String c = "";
      if (command != null && command.length > 0) {
         for (int i=0; i<command.length; ++i) {
            if (command[i].matches("^.*\\s+.*$")) {
               c += "\"" + command[i] + "\" ";
            } else {
               c += command[i] + " ";
            }
         }
      }
      return c;
   }
   
   public OutputStream getOutputStream() {
      return proc.getOutputStream();
   }
   
   public Process getProcess() {
      return proc;
   }

}
