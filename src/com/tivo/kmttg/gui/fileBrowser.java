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
         "VIDEO FILES",
         "*.tivo", "*.TiVo", "*.TIVO",
         "*.mpeg", "*.MPEG",
         "*.mpg", "*.MPG","*.mpeg2", "*.MPEG2",
         "*.asf", "*.ASF",
         "*.avi", "*.AVI",
         "*.dvr-ms", "*.DVR-MS",
         "*.flv", "*.FLV",
         "*.m2p", "*.M2P",
         "*.m4v", "*.M4V",
         "*.mkv", "*.MKV",
         "*.mov", "*.MOV",
         "*.mp4", "*.mpeg4", "*.MP4", "*.MPEG4",
         "*.m2ts", "*.mts", "*.M2TS", "*.MTS",
         "*.ogm", "*.OGM",
         "*.tp", "*.TP",
         "*.ts", "*.TS",
         "*.vob", "*.VOB",
         "*.wmv", "*.WMV",
         "*.wtv", "*.WTV",
         "*.xvid", "*.XVID",
         "*.divx", "*.DIVX",
         "*.dvx", "*.DVX",
         "*.vprj", "*.Vprj", "*.VPRJ"
      );
      Browser.getExtensionFilters().add(videoFilter);
      Browser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ALL FILES", "*"));
   }   
}
