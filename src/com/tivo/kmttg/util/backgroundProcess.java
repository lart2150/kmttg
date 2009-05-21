package com.tivo.kmttg.util;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Stack;

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
   
   public int Wait() {
      try {
         int r = proc.waitFor();
         stdoutHandler.run();
         stderrHandler.run();
         return r;
      } catch (InterruptedException e) {
         return -1;
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
   
   public String getStderr(int n) {
      debug.print("");
      try {
         return stderr.get(n);
      }
      catch (NoSuchElementException e) {
         return "";
      }      
   }
   
   public String getStdout(int n) {
      debug.print("");
      try {
         return stdout.get(n);
      }
      catch (NoSuchElementException e) {
         return "";
      }      
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
      for (int i=0; i<command.length; ++i) {
         if (command[i].matches("^.*\\s+.*$")) {
            c += "\"" + command[i] + "\" ";
         } else {
            c += command[i] + " ";
         }
      }
      return c;
   }

}
