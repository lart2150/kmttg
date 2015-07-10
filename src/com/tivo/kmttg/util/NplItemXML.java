package com.tivo.kmttg.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.http;

public class NplItemXML {
   
   // Query tivo web server to obtain byte offset for given individual show url
   public static String ByteOffset(String tivoName, String url) {
      String offset = null;
      Document doc = ItemXML(tivoName, url);
      if (doc != null) {
         NodeList nlist = doc.getElementsByTagName("ByteOffset");
         if (nlist.getLength() > 0) {
            return nlist.item(0).getTextContent();
         }
      }
      return offset;
   }
   
   // Get specific show XML from tivo web server given TiVo name and show url
   private static Document ItemXML(String tivoName, String url) {
      try {
         String IP = config.TIVOS.get(tivoName);
         String urlString = "https://" + IP;
         String wan_port = config.getWanSetting(tivoName, "https");
         if (wan_port != null)
            urlString += ":" + wan_port;
         urlString += "/TiVoConnect?Command=QueryItem&Url=" + URLEncoder.encode(url, "UTF-8");
         ByteArrayOutputStream info = new ByteArrayOutputStream();
         Boolean result = http.downloadPiped(urlString, "tivo", config.MAK, info, true, null);
         if (result) {
            // Read data from info
            byte[] b = info.toByteArray();
            return(Xml.getDocument(new ByteArrayInputStream(b)));
         }
      } catch (Exception e) {
         log.error("NplItemXML ItemXML - " + e.getMessage());
      }
      return null;
   }
}
