package com.tivo.kmttg.gui;

import java.io.File;

import javax.swing.filechooser.FileFilter;

public class FileFilterSP extends FileFilter {
   String ext = ".sp";
   public FileFilterSP(String ext) {
      this.ext = ext;
   }
   
   //Accept all directories and files with ext extension
   public boolean accept(File f) {
       if (f.isDirectory()) {
           return true;
       }
       if (f.getName().toLowerCase().endsWith(ext))
          return true;

       return false;
   }

   //The description of this filter
   public String getDescription() {
       return ext + " files";
   }
}
