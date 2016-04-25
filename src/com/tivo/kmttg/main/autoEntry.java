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
package com.tivo.kmttg.main;

import java.util.Stack;

public class autoEntry {
   public String type = null;
   public String keyword = null;
   public Stack<String> keywords = new Stack<String>();
   
   // options
   public String tivo  = "all";
   public int metadata  = 0;
   public int decrypt   = 0;
   public int qsfix     = 0;
   public int twpdelete = 0;
   public int rpcdelete = 0;
   public int comskip   = 0;
   public int comcut    = 0;
   public int captions  = 0;
   public int encode    = 0;
   public int push      = 0;
   public int custom    = 0;
   public int enabled   = 1;
   public String encode_name = null;
   public String encode_name2 = null;
   public String encode_name2_suffix = null;
   public String comskipIni = "none";
   public int suggestionsFilter = 0;
   public int useProgramId_unique = 0;
   public String channelFilter = null;
   public String tivoFileNameFormat = null;
   
   public String toString() {
      return "{type=" + type + ", tivo=" + tivo + ", keyword=" + keyword + ", keywords=" + keywords + "}";
   }
}
