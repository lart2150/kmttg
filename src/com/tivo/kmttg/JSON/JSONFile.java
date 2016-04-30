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
package com.tivo.kmttg.JSON;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;

import com.tivo.kmttg.util.log;

public class JSONFile {
   public static Boolean write(JSONObject json, String fileName) {
      try {
         Writer os = new BufferedWriter(new FileWriter(fileName));
         os.write(json.toString());
         os.close();
         return true;
      } catch (Exception e) {
         log.error("write - " + e.getMessage());
         return false;
      }
   }
   
   public static Boolean write(JSONArray json, String fileName) {
      try {
         Writer os = new BufferedWriter(new FileWriter(fileName));
         os.write(json.toString());
         os.close();
         return true;
      } catch (Exception e) {
         log.error("write - " + e.getMessage());
         return false;
      }
   }
   
   public static JSONObject readJSONObject(String fileName) {
      try {
         Reader is = new BufferedReader(new FileReader(fileName));
         JSONObject json = new JSONObject(new JSONTokener(is));
         is.close();
         return json;
      } catch (Exception e) {
         log.error("readJSONObject - " + e.getMessage());
         return null;
      }
   }
   
   public static JSONArray readJSONArray(String fileName) {
      try {
         Reader is = new BufferedReader(new FileReader(fileName));
         JSONArray json = new JSONArray(new JSONTokener(is));
         is.close();
         return json;
      } catch (Exception e) {
         log.error("readJSONArray - " + e.getMessage());
         return null;
      }
   }
}
