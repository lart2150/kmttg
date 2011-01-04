package com.tivo.kmttg.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.NoSuchElementException;
import java.util.Stack;

// NOTE: stdout/stdin for 2 processes are piped to each other
public class pipedProcesses {
   private Process proc1=null, proc2=null;
   private ChildDataHandler stderrHandler1, stderrHandler2;
   private ChildDataHandler stdoutHandler2;
   private PipeHandler pipeHandler;
   private Stack<String> stderr1 = new Stack<String>();
   private Stack<String> stderr2 = new Stack<String>();
   private Stack<String> stdout2 = new Stack<String>();
   private String[] command1, command2;
   
   // Method to start 2 new runtime commands
   public Boolean run(Stack<String> command1, Stack<String>command2) {
      // Convert String Stack to String array for both commands
      this.command1 = new String[command1.size()];
      for (int i=0; i<command1.size(); i++) {
         this.command1[i] = command1.get(i);
      }      
      this.command2 = new String[command2.size()];
      for (int i=0; i<command2.size(); i++) {
         this.command2[i] = command2.get(i);
      }
      
      // Start 1st process
      try {
         proc1 = Runtime.getRuntime().exec(this.command1);
         stderrHandler1 = new ChildDataHandler(proc1.getErrorStream(), stderr1);

         // Capture stderr to string stacks
         stderrHandler1.start();
      } catch (IOException e) {
         stderr1.add(e.getMessage());
         return false;
      } catch (NullPointerException e) {
         stderr1.add(e.getMessage());
         return false;
      }
      
      // Start 2nd process
      try {
         proc2 = Runtime.getRuntime().exec(this.command2);
         stderrHandler2 = new ChildDataHandler(proc2.getErrorStream(), stderr2);
         stdoutHandler2 = new ChildDataHandler(proc2.getInputStream(), stdout2);

         // Capture stdout/stderr to string stacks
         stderrHandler2.start();
         stdoutHandler2.start();
      } catch (IOException e) {
         stderr2.add(e.getMessage());
         kill();
         return false;
      } catch (NullPointerException e) {
         stderr2.add(e.getMessage());
         kill();
         return false;
      }
      
      // Pipe proc1 stdout to proc2 stdin
      pipeHandler = new PipeHandler(proc1.getInputStream(), proc2);
      pipeHandler.start();
      
      return true;
   }
   
   // Method to start only 2nd runtime command and pipe to existing inputStream
   public Boolean run(InputStream inputStream, Stack<String>command2) {
      // Convert String Stack to String array for both commands
      this.command2 = new String[command2.size()];
      for (int i=0; i<command2.size(); i++) {
         this.command2[i] = command2.get(i);
      }      
      
      // Start 2nd process
      try {
         proc2 = Runtime.getRuntime().exec(this.command2);
         stderrHandler2 = new ChildDataHandler(proc2.getErrorStream(), stderr2);

         // Capture stdout/stderr to string stacks
         stderrHandler2.start();
         stdoutHandler2.start();
      } catch (IOException e) {
         stderr2.add(e.getMessage());
         return false;
      } catch (NullPointerException e) {
         stderr2.add(e.getMessage());
         return false;
      }
      
      // Pipe inputStream to proc2 stdin
      pipeHandler = new PipeHandler(inputStream, proc2);
      pipeHandler.start();
      
      return true;
   }
      
   // Return true if proc1 still running
   public Boolean isRunning() {
      debug.print("");
      try {
         proc1.exitValue();
         return true;
      }
      catch (IllegalThreadStateException i) {
         return false;
      }
   }
   
   // Return -1 if proc1 still running, exit code otherwise
   public int exitStatus() {
      try {
         int v = proc1.exitValue();
         return v;
      }
      catch (IllegalThreadStateException i) {
         return -1;
      }
   }
   
   public void kill() {
      debug.print("");
      if (proc1 != null)
         proc1.destroy();
      if (proc2 != null)
         proc2.destroy();
      proc1 = null;
      proc2 = null;
      pipeHandler = null;
   }
   
   public Stack<String> getStdout() {
      debug.print("");
      if (proc2 != null)
         return stdout2;
      return null;
   }
   
   public Stack<String> getStderr() {
      debug.print("");
      if (proc1 != null)
         return stderr1;
      else if (proc2 != null)
         return stderr2;
      return null;
   }
   
   public String getStderrLast() {
      debug.print("");
      try {
         if (proc1 != null)
            return stderr1.lastElement();
         else if (proc2 != null)
            return stderr2.lastElement();
         return "";
      }
      catch (NoSuchElementException n) {
         return "";
      }
   }

   // NOTE: This is used by taskInfo
   public void setStdoutWatch(Stack<String> s) {
      debug.print("s=" + s);
      stdoutHandler2.watch = s;
   }

   // NOTE: This is used by taskInfo
   public void setStderrWatch(Stack<String> s) {
      debug.print("s=" + s);
      if (proc1 != null)
         stderrHandler1.watch = s;
      else if (proc2 != null)
         stderrHandler2.watch = s;
   }
   
   public void printStdout() {
      debug.print("");
      if (proc2 != null)
         log.print(stdout2);
   }
   
   public void printStderr() {
      debug.print("");
      if (proc1 != null)
         log.error(stderr1);
      if (proc2 != null)
         log.error(stderr2);
   }
   
   public String toString() {
      debug.print("");
      String c = "";
      if (command1 != null && command1.length > 0) {
         for (int i=0; i<command1.length; ++i) {
            if (command1[i].matches("^.*\\s+.*$")) {
               c += "\"" + command1[i] + "\" ";
            } else {
               c += command1[i] + " ";
            }
         }
      }
      if (command2 != null && command2.length > 0) {
         if (c.length() > 0)
            c += " | ";
         for (int i=0; i<command2.length; ++i) {
            if (command2[i].matches("^.*\\s+.*$")) {
               c += "\"" + command2[i] + "\" ";
            } else {
               c += command2[i] + " ";
            }
         }
      }
      return c;
   }

}
