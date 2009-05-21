package com.tivo.kmttg.install;

import javax.swing.JOptionPane;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;

public class mainInstall {
   
   public static void install() {
      debug.print("");
      String dir = config.programDir; // Install where jar file is
            
      // If tivodecode not defined then assume tools not installed
      // and download & install tools package
      // for windows & mac only
      if ( ! file.isFile(config.tivodecode) ) {
         if (config.OS.equals("windows") || config.OS.equals("mac")) {
            int response = JOptionPane.showConfirmDialog(
               config.gui.getJFrame(),
               "Required tools not detected. Download and install them?",
               "Confirm",
               JOptionPane.YES_NO_OPTION,
               JOptionPane.QUESTION_MESSAGE
            );
            if (response == JOptionPane.YES_OPTION) {
               toolDownload t = new toolDownload();
               String download = t.download(dir, config.OS);
               config.gui.progressBar_setValue(0);
               config.gui.setTitle(config.kmttg);
               if (download != null) {
                  // successful download, so unzip the file
                  if (Unzip.unzip(dir, download) ) {
                     // Remove zip file
                     file.delete(download);
                     
                     // Save settings
                     config.save(config.configIni);
                     
                     // Refresh available options
                     config.gui.refreshOptions();
                  }
               }
            }
         }
      }
      
      // Prompt for MAK if not set
      if (config.MAK == null || config.MAK.length() != 10) {
         String prompt = "Enter your 10 digit Tivo Media Acess Key (MAK):\n";
         prompt += "\nYou can find it on any of your Tivos under";
         prompt += "\nTivo Central-Messages&Settings-Account&System Information-Media Access Key";
         String mak = JOptionPane.showInputDialog(config.gui.getJFrame(), prompt);
         if (mak != null) {
            config.MAK = mak;
            config.save(config.configIni);
         }       
      }
   }    

}
