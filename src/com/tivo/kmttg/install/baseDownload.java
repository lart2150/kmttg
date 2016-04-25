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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

import com.tivo.kmttg.util.debug;

public class baseDownload {
   public static String getBase() {
      debug.print("");
      String base = null;
      String base_url = "http://svn.code.sf.net/p/kmttg/code/trunk/baseDownload";
      try {
         URL url = new URL(base_url);
         URLConnection con = url.openConnection();
         BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
         String inputLine;
         while ((inputLine = in.readLine()) != null) 
            base = inputLine;
         in.close();
      } catch (Exception ex) {
         base = null;
      }
      return  base;
   }
}
