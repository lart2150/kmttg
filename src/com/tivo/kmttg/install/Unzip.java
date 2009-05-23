package com.tivo.kmttg.install;

import java.io.*;
import java.util.*;
import java.util.zip.*;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class Unzip {

@SuppressWarnings("unchecked")
public static Boolean unzip(String dir, String file) {
   debug.print("dir=" + dir + " file=" + file);
    Enumeration entries;
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
        copyInputStream(
           zipFile.getInputStream(entry),
           new BufferedOutputStream(new FileOutputStream(fullName))
        );
        
        // Set all files as executable
        if ( ! config.OS.equals("windows") )
           Runtime.getRuntime().exec("chmod 775 " + fullName);
      }

      zipFile.close();      
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
