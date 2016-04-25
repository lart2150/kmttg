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

public class sortableSize {
   public String display;
   public long sortable;
   
   // Single entry constructor
   public sortableSize(Hashtable<String,String> entry) {
      display = (String)entry.get("sizeGB") + " ";
      sortable = Long.parseLong(entry.get("size"));
   }
   
   // Folder entry constructor
   public sortableSize(Stack<Hashtable<String,String>> folderEntry) {
      sortable = 0;
      for (int i=0; i<folderEntry.size(); ++i) {
         sortable += Long.parseLong(folderEntry.get(i).get("size"));
      }      
      display = String.format("%.2f GB ", sortable/Math.pow(2,30));
   }  
   
   public sortableSize(long size) {
      sortable = size;
      display = String.format("%.2f GB ", sortable/Math.pow(2,30));
   }
   
   public String toString() {
      return display;
   }
}

