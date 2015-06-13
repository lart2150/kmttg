package com.tivo.kmttg.main;

import java.io.PrintWriter;
import java.io.StringWriter;

import javafx.application.Platform;

import com.tivo.kmttg.util.log;

public class myExceptionHandler implements Thread.UncaughtExceptionHandler {

   public void uncaughtException(final Thread t, final Throwable e) {
       if (Platform.isFxApplicationThread()) {
           showException(t, e);
       } else {
          Platform.runLater(new Runnable() {
               public void run() {
                   showException(t, e);
               }
           });
       }
   }

   private void showException(Thread t, Throwable e) {
      StringWriter sw =  new StringWriter();
      PrintWriter pw = new PrintWriter(sw,true);
      String detailMessage;
      try {
         e.printStackTrace(pw);
         detailMessage = sw.getBuffer().toString();
      } catch (Exception ee) {
         detailMessage = ee.getMessage();
      }
      // Filter out certain messages
      if( detailMessage.contains("java.lang.ClassCastException"))
         return;
      if (detailMessage.contains("org.jdesktop.swingx.decorator"))
         return;
      
      log.error(detailMessage);
   }

}