package com.tivo.kmttg.gui;

import java.io.File;

import javax.swing.filechooser.FileFilter;

public class FileFilterSP extends FileFilter {

   //Accept all directories and .sp files
   public boolean accept(File f) {
       if (f.isDirectory()) {
           return true;
       }
       if (f.getName().toLowerCase().endsWith(".sp"))
          return true;

       return false;
   }

   //The description of this filter
   public String getDescription() {
       return "sp files";
   }
}
