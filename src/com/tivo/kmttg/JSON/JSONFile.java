package com.tivo.kmttg.JSON;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import com.tivo.kmttg.util.log;

public class JSONFile {
   public static Boolean write(JSONObject json, String fileName) {
      try {
         OutputStream os = new FileOutputStream(fileName);
         os.write(json.toString().getBytes());
         os.close();
         return true;
      } catch (Exception e) {
         log.error("write - " + e.getMessage());
         return false;
      }
   }
   
   public static Boolean write(JSONArray json, String fileName) {
      try {
         OutputStream os = new FileOutputStream(fileName);
         os.write(json.toString().getBytes());
         os.close();
         return true;
      } catch (Exception e) {
         log.error("write - " + e.getMessage());
         return false;
      }
   }
   
   public static JSONObject readJSONObject(String fileName) {
      try {
         InputStream is = new FileInputStream(fileName);
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
         InputStream is = new FileInputStream(fileName);
         JSONArray json = new JSONArray(new JSONTokener(is));
         is.close();
         return json;
      } catch (Exception e) {
         log.error("readJSONArray - " + e.getMessage());
         return null;
      }
   }
}
