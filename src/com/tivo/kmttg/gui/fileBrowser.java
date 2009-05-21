package com.tivo.kmttg.gui;

import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

import com.tivo.kmttg.main.config;

public class fileBrowser {
   public JFileChooser Browser;
   
   fileBrowser() {
      Browser = new JFileChooser(config.TIVOS.get("FILES"));
      Browser.setFileSelectionMode(JFileChooser.FILES_ONLY);
      Browser.setMultiSelectionEnabled(true);
      Browser.addChoosableFileFilter(new TextFilter());
      //Browser.setAcceptAllFileFilterUsed(false);
   }
   
   class TextFilter extends FileFilter {

      public boolean accept(File f) {
        if (f.isDirectory())
          return true;
        String s = f.getName().toLowerCase();
        if ( s.endsWith(".tivo") || s.endsWith(".mpeg") || s.endsWith(".mpg") ) {
           return true;
        }
        return false;
      }

      public String getDescription() {
        return "tivo and mpg";
      }
    }

}
