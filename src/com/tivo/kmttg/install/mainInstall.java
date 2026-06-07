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
package com.tivo.kmttg.install;

import javax.swing.JOptionPane;

import com.tivo.kmttg.gui.swing.SwingUtil;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.file;

public class mainInstall {
   
   public static void install() {
      // If ffmpeg not defined then assume tools not installed
      // and download & install tools package
      // for windows & mac only
      if ( ! file.isFile(config.ffmpeg) ) {
         if (config.OS.equals("windows") || config.OS.equals("mac")) {
            boolean confirmation = JOptionPane.showConfirmDialog(
               config.gui==null?null:config.gui.getFrame(),
               "Required tools not detected. Download and install them?", "Confirm",
               JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION;
            if (confirmation) {
               Runnable task = new Runnable() {
                  @Override public void run() {
                     final String dir = config.programDir; // Install where jar file is
                     toolDownload t = new toolDownload();
                     final String download = t.download(dir, config.OS);
                     SwingUtil.runLater(new Runnable() {
                        @Override public void run() {
                           config.gui.progressBar_setValue(0);
                           config.gui.setTitle(config.kmttg);
                           if (download != null) {
                              // successful download, so unzip the file
                              if (Unzip.unzip(dir, download) ) {
                                 // Remove zip file
                                 file.delete(download);
                                 
                                 // Define default paths to installed programs
                                 config.parse();
                                 
                                 // Set Remote tivo names if relevant
                                 if (config.rpcEnabled())
                                    config.gui.remote_gui.setTivoNames();
                                 
                                 // Save settings
                                 config.save();
                                 
                                 // Refresh available options
                                 config.gui.refreshOptions(true);
                              }
                           }
                        }
                     });
                  }
               };
               new Thread(task).start();
            } // OK
         } // windows or mac
      } // ! tivodecode
      
      // Prompt for MAK if not set
      if (config.MAK == null || config.MAK.length() != 10) {
         SwingUtil.runLater(new Runnable() {
            @Override public void run() {
               String prompt = "Enter your 10 digit Tivo Media Acess Key (MAK):\n";
               prompt += "\nYou can find it on any of your Tivos under";
               prompt += "\nTivo Central-Messages&Settings-Account&System Information-Media Access Key";
               String result = JOptionPane.showInputDialog(
                  config.gui==null?null:config.gui.getFrame(),
                  prompt, "Enter 10 digit MAK", JOptionPane.QUESTION_MESSAGE);
               if (result != null){
                   if (result.length() > 0) {
                      config.MAK = result;
                      config.save();
                   }
               }
            }
         });
      }
   }
}
