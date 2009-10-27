package com.tivo.kmttg.main;

import java.util.Stack;

public class autoEntry {
   public String type = null;
   public String keyword = null;
   public Stack<String> keywords = new Stack<String>();
   
   // options
   public int metadata = 0;
   public int decrypt  = 0;
   public int qsfix    = 0;
   public int comskip  = 0;
   public int comcut   = 0;
   public int captions = 0;
   public int encode   = 0;
   public int push     = 0;
   public int custom   = 0;
   public String encode_name = null;
   
   public String toString() {
      return "{type=" + type + ", keyword=" + keyword + ", keywords=" + keywords + "}";
   }
}
