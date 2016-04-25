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
      if (detailMessage.contains("TreeTableViewArrayListSelectionModel"))
         return;
      
      log.error(detailMessage);
   }

}