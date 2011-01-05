package com.tivo.kmttg.util;

import java.util.Stack;

// Wrapper to handle different kinds of process threads (used by taskInfo)
public class genericProcess {
   private backgroundProcess background = null;
   private pipedProcesses piped = null;
   
   public genericProcess(backgroundProcess background) {
      this.background = background;
   }
   
   public genericProcess(pipedProcesses piped) {
      this.piped = piped;
   }
   
   public Stack<String> getStderr() {
      if (background != null)
         return background.getStderr();
      else if (piped != null)
         return piped.getStderr();
      return null;
   }
   
   public Stack<String> getStdout() {
      if (background != null)
         return background.getStdout();
      else if (piped != null)
         return piped.getStdout();
      return null;
   }
   
   public void setStdoutWatch(Stack<String> s) {
      if (background != null)
         background.setStdoutWatch(s);
      else if (piped != null)
         piped.setStdoutWatch(s);
   }
   
   public void setStderrWatch(Stack<String> s) {
      if (background != null)
         background.setStderrWatch(s);
      else if (piped != null)
         piped.setStderrWatch(s);
   }
   
   public int exitStatus() {
      if (background != null)
         return background.exitStatus();
      else if (piped != null)
         return piped.exitStatus();
      return 1;
   }
}
