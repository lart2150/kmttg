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
