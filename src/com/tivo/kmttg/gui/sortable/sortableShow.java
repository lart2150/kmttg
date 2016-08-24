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
package com.tivo.kmttg.gui.sortable;

import java.util.Hashtable;
import java.util.Stack;

public class sortableShow {
   public String titleOnly;
   public String TiVoTitle;
   public String title;
   public String episodeTitle = "";
   public String episodeNum = "";
   public String movieYear = null;
   public long gmt;
   public Boolean folder = false;
   public int numEntries = 0;
   public String folderName = "";
   public String[] ARTICLES = {"a ","an ", "the "};
   
   // Single entry constructor
   public sortableShow(Hashtable<String,String> entry) {
      title = (String)entry.get("title");
      titleOnly = (String)entry.get("titleOnly");
      TiVoTitle = removeLeadingArticles(titleOnly);
      gmt = Long.parseLong(entry.get("gmt"));
      if (entry.containsKey("episodeTitle"))
         episodeTitle = entry.get("episodeTitle");
      if (entry.containsKey("EpisodeNumber"))
         episodeNum = entry.get("EpisodeNumber");
      if (episodeNum.matches("^[0]+$"))
         episodeNum = "";
      if (entry.containsKey("movieYear"))
         movieYear = entry.get("movieYear");
   }
   
   // Folder entry constructor
   public sortableShow(String folderName, Stack<Hashtable<String,String>> folderEntry, int gmt_index) {
      folder = true;
      this.folderName = folderName;
      numEntries = folderEntry.size();
      gmt = Long.parseLong(folderEntry.get(gmt_index).get("gmt"));
      titleOnly = folderEntry.get(0).get("titleOnly");
      TiVoTitle = removeLeadingArticles(titleOnly);
      title = titleOnly;
   }
   
   public String removeLeadingArticles(String title) {
      if (title == null) return "";
      String stitle = title.toLowerCase();
      for (String article : ARTICLES) {
         if (stitle.startsWith(article)) {
            stitle = stitle.substring(article.length());
            break;
         }
      }
      return stitle;
   }   
   
   public String toString() {
      if (folder) {
         return folderName + " (" + numEntries + ")";
      } else {
         if (episodeNum.length() > 0 && titleOnly.length() > 0) {
            String s = titleOnly + " [Ep " + episodeNum + "]";
            if (episodeTitle.length() > 0)
               s += " - " + episodeTitle;
            return s;
         }
         if (movieYear != null) {
            String s = titleOnly + " [" + movieYear + "]";
            return s;
         }
         return title;
      }
   }
}
