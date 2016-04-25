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

import java.io.*;
import java.util.*;
import java.util.zip.*;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class Unzip {

@SuppressWarnings("resource")
public static Boolean unzip(String dir, String file) {
   debug.print("dir=" + dir + " file=" + file);
    Enumeration<?> entries;
    ZipFile zipFile;

    try {
      zipFile = new ZipFile(file);

      entries = zipFile.entries();

      String fullName;
      while(entries.hasMoreElements()) {
        ZipEntry entry = (ZipEntry)entries.nextElement();
        fullName = dir + File.separator + entry.getName();

        if(entry.isDirectory()) {
          // This is not robust, just for demonstration purposes.
           log.print("Extracting directory: " + fullName);
           (new File(fullName)).mkdir();
           continue;
        }

        log.print("Extracting file: " + fullName);
        BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(fullName));
        copyInputStream(zipFile.getInputStream(entry), out);
        out.close();
      }
      zipFile.close();      
            
      // Set all files as executable
      if ( ! config.OS.equals("windows") ) {
         String[] command = new String[] {"chmod", "-R", "ugo+x", dir};
         Runtime.getRuntime().exec(command);
      }

      return true;
      
    } catch (IOException ioe) {
      log.error(ioe.getMessage());
      return false;
    }
  }

  private static void copyInputStream(InputStream in, OutputStream out) throws IOException {
     debug.print("in=" + in + " out=" + out);
     byte[] buffer = new byte[1024];
     int len;
   
     while((len = in.read(buffer)) >= 0)
       out.write(buffer, 0, len);
   
     in.close();
     out.close();
  }

}
