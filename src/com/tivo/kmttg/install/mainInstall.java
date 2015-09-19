package com.tivo.kmttg.install;

import java.util.Optional;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Alert.AlertType;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.file;

public class mainInstall {
   
   public static void install() {
      // If ffmpeg not defined then assume tools not installed
      // and download & install tools package
      // for windows & mac only
      if ( ! file.isFile(config.ffmpeg) ) {
         if (config.OS.equals("windows") || config.OS.equals("mac")) {
            Alert alert = new Alert(AlertType.CONFIRMATION);
            alert.setTitle("Confirm");
            config.gui.setFontSize(alert, config.FontSize);
            alert.setContentText("Required tools not detected. Download and install them?");
            Optional<ButtonType> result = alert.showAndWait();
            if (result.get() == ButtonType.OK) {
               Task<Void> task = new Task<Void>() {
                  @Override public Void call() {
                     final String dir = config.programDir; // Install where jar file is
                     toolDownload t = new toolDownload();
                     final String download = t.download(dir, config.OS);
                     Platform.runLater(new Runnable() {
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
                     return null;
                  }
               };
               new Thread(task).start();
            } // OK
         } // windows or mac
      } // ! tivodecode
      
      // Prompt for MAK if not set
      if (config.MAK == null || config.MAK.length() != 10) {
         Platform.runLater(new Runnable() {
            @Override public void run() {
               String prompt = "Enter your 10 digit Tivo Media Acess Key (MAK):\n";
               prompt += "\nYou can find it on any of your Tivos under";
               prompt += "\nTivo Central-Messages&Settings-Account&System Information-Media Access Key";
               TextInputDialog d = new TextInputDialog("");
               d.setTitle(prompt);
               Optional<String> result = d.showAndWait();
               if (result.isPresent()){
                   if (result.get().length() > 0) {
                      config.MAK = result.get();
                      config.save();                      
                   }
               }
            }
         });
      }
   }
}
