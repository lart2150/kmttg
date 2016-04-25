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
