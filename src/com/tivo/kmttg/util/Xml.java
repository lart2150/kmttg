package com.tivo.kmttg.util;

import java.io.ByteArrayInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Xml {
   // Create Document from xml string
   public static Document getDocument(ByteArrayInputStream is) {
      try {
         DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
         DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
         return docBuilder.parse(is);
      } catch (Exception e) {
         log.error("xml Document error: " + e.toString());
         return null;
      }
   }
   
   // Search for given element name in xml doc
   public static String getElement(Document doc, String elementName) {
      NodeList rdList = doc.getElementsByTagName(elementName);
      if (rdList.getLength() > 0) {
         String value;
         Node n = rdList.item(0);
         if ( n != null) {
            value = n.getTextContent();
            value = Entities.replaceHtmlEntities(value);
            return value;
         }
      }
      return null;
   }
}
