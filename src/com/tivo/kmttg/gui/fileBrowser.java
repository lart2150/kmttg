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
      Browser.addChoosableFileFilter(new TivoFilter());
      Browser.addChoosableFileFilter(new videoFilter());
      //Browser.setAcceptAllFileFilterUsed(false);
   }
   
   class TivoFilter extends FileFilter {
      public boolean accept(File f) {
        if (f.isDirectory())
          return true;
        String s = f.getName().toLowerCase();
        String[] supported = {".tivo", ".mpeg", ".mpg", ".ts"};
        for (int i=0; i<supported.length; ++i) {
           if (s.endsWith(supported[i]))
              return true;
        }
        return false;
      }

      public String getDescription() {
        return "tivo, mpg and ts";
      }
    }
   
   class videoFilter extends FileFilter {
      public boolean accept(File f) {
        if (f.isDirectory())
          return true;
        String s = f.getName().toLowerCase();
        String[] valid = new String[] {
           ".tivo", ".mpeg", ".mpg", ".mpeg2", ".asf", ".avi", ".dvr-ms", ".flv",
           ".m2p", ".m4v", ".mkv", ".mov", ".mp4", ".mpeg4", ".m2ts", ".mts",
           ".ogm", ".tp", ".ts", ".vob", ".wmv", ".wtv", ".xvid", ".divx", ".dvx",
           ".vprj"
        };
        for (int i=0; i<valid.length; ++i)
        if ( s.endsWith(valid[i])) {
           return true;
        }
        return false;
      }

      public String getDescription() {
        return "video files";
      }
    }

}
