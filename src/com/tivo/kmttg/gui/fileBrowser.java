package com.tivo.kmttg.gui;

import java.io.File;

import javafx.stage.FileChooser;

import com.tivo.kmttg.main.config;

public class fileBrowser {
   public FileChooser Browser;
   
   fileBrowser() {
      Browser = new FileChooser();
      Browser.setInitialDirectory(new File(config.TIVOS.get("FILES")));
      FileChooser.ExtensionFilter videoFilter = new FileChooser.ExtensionFilter(
         "VIDEO FILES", "*.tivo", "*.mpeg", "*.mpg", "*.mpeg2", "*.asf", "*.avi", "*.dvr-ms",
         "*.flv", "*.m2p", "*.m4v", "*.mkv", "*.mov", "*.mp4", "*.mpeg4", "*.m2ts", "*.mts",
         "*.ogm", "*.tp", "*.ts", "*.vob", "*.wmv", "*.wtv", "*.xvid", "*.divx", "*.dvx",
         "*.vprj"
      );
      Browser.getExtensionFilters().add(videoFilter);
   }   
}
