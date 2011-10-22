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
