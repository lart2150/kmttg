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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.concurrent.Task;
import javafx.embed.swing.JFXPanel;

import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.util.string;

public class encodeConfig {
   private static Boolean encodeName_reset = true;
   private static Boolean panel = false;

   // Determine list of encoding profiles to use
   public static void parseEncodingProfiles() {
      
      Stack<String> errors = new Stack<String>();
      
      // Clear out any previous settings
      config.ENCODE.clear();
      config.ENCODE_NAMES.clear();

      // First parse encoding profiles in "encode" folder
      String dir = config.encProfDir;
      
      File d = new File(dir);
      if ( ! d.isDirectory() ) {
         errors.add("Encoding profiles dir not valid: " + dir);
         log.error(errors);
         return;
      }
      FilenameFilter filter = new FilenameFilter() {
         public boolean accept(File dir, String name) {
            debug.print("dir=" + dir + " name=" + name);
            File d = new File(dir.getPath() + File.separator + name);
            if (d.isDirectory()) {
               return false;
            }
            // .enc files
            if ( name.toLowerCase().endsWith(".enc") ) {
               return true;
            }
            return false;
         }
      };
     
      // Define list of filter entries
      File[] files = d.listFiles(filter);
      for (int i=0; i<files.length; i++) {
         if ( ! parseEncFile(files[i]) ) {
            errors.add("Parsing error with profile: " + files[i].getName());
         }
      }
      
      // Sort encode list alphabetically
      List<String> encode_list = new ArrayList<String>(config.ENCODE.keySet());
      Collections.sort(encode_list);
      
      // Set ENCODE_NAMES to sorted list of entries
      if ( config.ENCODE.isEmpty() ) {
         errors.add("No valid encoding profiles found");
      } else {   
         for (String str : encode_list) {
            config.ENCODE_NAMES.add(str);
         }
         
         // Add VideoRedo profiles to list if configured
         if (config.VrdEncode == 1) {
            if (config.gui != null) {
               // In GUI mode add VRD encoding profiles in background/threaded mode
               // since this can take several seconds and would hang up GUI
               // The JFXPanel() call is a hack to initialize toolkit if it's not ready
               if (! panel) {
                  new JFXPanel();
                  panel = true;
               }
               Task<Boolean> worker = new Task<Boolean>() {
                  @Override
                  public Boolean call() {
                    return getVrdProfiles();
                  }
                  public void done() {
                    try {
                       if ( get() ) {
                          // Refresh Encoding Profile combo box
                          log.warn("VideoRedo Profiles refreshed");
                          config.gui.SetEncodings(getValidEncodeNames());
                          if (config.encodeName_orig != null && getDescription(config.encodeName_orig).length() > 0) {
                             if (encodeName_reset) {
                                config.gui.SetSelectedEncoding(config.encodeName_orig);
                                encodeName_reset = false;
                             }
                          }
                       }
                    } catch (Exception e) {
                       log.error(e.getMessage());
                    }
                  }
               };
               new Thread(worker).start();
            } else {
               // In non GUI mode we want don't want threaded run
               getVrdProfiles();
            }
         } else {
            // No VideoRedo profiles => update gui settings right away
            if (config.gui != null)
               config.gui.SetEncodings(getValidEncodeNames());
         }
         
         // Set config.encodeName to 1st entry
         if (config.encodeName.length() == 0)
            config.encodeName = config.ENCODE_NAMES.get(0);
      }
   }

   // Parse an individual .enc file
   private static Boolean parseEncFile(File f) {
      debug.print("f=" + f);
      Hashtable<String,String> h = new Hashtable<String,String>();
      String name = f.getName().replaceFirst("\\.enc", "");
      try {
         BufferedReader enc = new BufferedReader(new FileReader(f));
         String line = null;
         String key = null;
         while (( line = enc.readLine()) != null) {
            // Get rid of leading and trailing white space
            line = line.replaceFirst("^\\s*(.*$)", "$1");
            line = line.replaceFirst("^(.*)\\s*$", "$1");
            if (line.length() == 0) continue; // skip empty lines
            if (line.matches("^#.+")) continue; // skip comment lines
            if (line.matches("^<.+>")) {
               key = line.replaceFirst("<", "");
               key = key.replaceFirst(">", "");
               continue;
            }
            if (key.equals("description")) {
               h.clear();
               h.put("description", line);
            }
            if (key.equals("command")) {
               h.put("command", line);
            }
            if (key.equals("extension")) {
               h.put("extension", line);
            }
            if (h.containsKey("description") && h.containsKey("command") && h.containsKey("extension")) {
               config.ENCODE.put(name, h);
            }
         }
         enc.close();
         
      }         
      catch (IOException ex) {
         return false;
      }
      
      return true;
   }

   // Return currently setup encode profile name
   public static String getEncodeName() {
      debug.print("");
      if ( config.encodeName.length() == 0) {
         log.error("Encode name is not set");
         return "";
      }
      if ( ! isValidEncodeName(config.encodeName) ) {
         return "";
      }
      return config.encodeName;
   }

   // Return list of valid encode profile names
   public static Stack<String> getValidEncodeNames() {
      return config.ENCODE_NAMES;
   }
 
   // Check if given profile name is valid
   public static Boolean isValidEncodeName(String encodeName) {
      Boolean valid = false;
      for (int i=0; i<config.ENCODE_NAMES.size(); i++) {
         if (encodeName.equals(config.ENCODE_NAMES.get(i))) {
            valid = true;
         }
      }
      //if ( valid == false) {
      //   log.error("Invalid encode name selected: " + encodeName);
      //   log.error("Valid names are: " + getValidEncodeNames());
      //}
      return valid;
   }

   // Return description of currently selected profile
   public static String getDescription(String encodeName) {
      debug.print("");
      if ( config.ENCODE.containsKey(encodeName))
         return config.ENCODE.get(encodeName).get("description");
      else
         return "";
   }
 
   // Return file extension associated with selected profile
   public static String getExtension(String encodeName) {
      debug.print("");
      if ( config.ENCODE.containsKey(encodeName))
         return config.ENCODE.get(encodeName).get("extension");
      else
         return "";
   }

   // Return in string stack form the full encoding command
   public static Stack<String> getFullCommand(String encodeName, String inputFile, String outputFile, String srtFile) {
      debug.print("inputFile=" + inputFile + " outputFile=" + outputFile);
      String command = getCommandName(encodeName);
      Stack<String> full = getCommandArgs(encodeName, inputFile, outputFile, srtFile);
      full.add(0, command);
      return full;      
   }

   // Return encoder command
   public static String getCommandName(String encodeName) {
      debug.print("");
      String command = "";
      if ( ! config.ENCODE.isEmpty() ) {
         if (config.ENCODE.get(encodeName).containsKey("command")) {
            String[] l = config.ENCODE.get(encodeName).get("command").split("\\s+");
            command = l[0];
            command = command.replaceFirst("FFMPEG", escapeBackSlashes(config.ffmpeg));
            command = command.replaceFirst("MENCODER", escapeBackSlashes(config.mencoder));
            command = command.replaceFirst("HANDBRAKE", escapeBackSlashes(config.handbrake));
            command = command.replaceFirst("PERL", escapeBackSlashes(config.perl));
            command = command.replaceFirst("PWD", escapeBackSlashes(System.getProperty("user.dir") + File.separator));
         } else {
            // This could be a VideoRedo profile which has no command defined
            return null;
         }
      }
      return command;
   }
   
   // Splits the full encoding command into an array of the individual
   // arguments. Double-quoted strings are considered a single argument.
   public static String[] splitCommandArgs(String command) {
      ArrayList<String> args = new ArrayList<String>();

      Pattern regexp = Pattern.compile("([^\\s\"']+)|\"([^\"]*)\"");
      Matcher matcher = regexp.matcher(command);
      while ( matcher.find() ) {
         if ( matcher.group(1) != null) {
            args.add(matcher.group(1));
         } else if ( matcher.group(2) != null ) {
            args.add(matcher.group(2));
         }
      }
      return args.toArray(new String[args.size()]);
   }

   // Return encoder command arguments
   public static Stack<String> getCommandArgs(String encodeName, String inputFile, String outputFile, String srtFile) {
      debug.print("inputFile=" + inputFile + " outputFile=" + outputFile);
      Stack<String> args = new Stack<String>();
      if ( ! config.ENCODE.isEmpty() ) {
         String arg;
         String[] l = splitCommandArgs(config.ENCODE.get(encodeName).get("command"));
         for (int i=1; i<l.length; ++i) {
            arg = l[i];
            arg = arg.replaceAll("\"", "");
            arg = arg.replaceAll("FFMPEG", escapeBackSlashes(config.ffmpeg));
            arg = arg.replaceAll("MENCODER", escapeBackSlashes(config.mencoder));
            arg = arg.replaceAll("HANDBRAKE", escapeBackSlashes(config.handbrake));
            arg = arg.replaceAll("PERL", escapeBackSlashes(config.perl));
            arg = arg.replaceAll("PWD", escapeBackSlashes(System.getProperty("user.dir") + File.separator));
            arg = arg.replaceAll("INPUT", escapeBackSlashes(inputFile));
            arg = arg.replaceAll("OUTPUT", escapeBackSlashes(outputFile));
            arg = arg.replaceAll("CPU_CORES", ("" + config.cpu_cores));
            if (srtFile != null) {
               if (! file.isFile(srtFile))
                  srtFile = string.replaceSuffix(inputFile, ".srt");
               arg = arg.replaceAll("SRTFILE", escapeBackSlashes(srtFile));
            }
            args.add(arg);
         }
      }
      return args;
   }
   
   // It's crazy but in Java regex one backslash = 4 ...
   private static String escapeBackSlashes(String s) {
      return s.replaceAll("\\\\", "\\\\\\\\");
   }
   
   // Build list of encoding profile names from VRD
   private static Boolean getVrdProfiles() {
      Hashtable<String,Hashtable<String,String>> hlist;
      
      // This method uses VRD functions to get profiles
      hlist = vrdGetProfiles();
      if ( hlist == null ) {
         log.error("Encountered problems obtaining encoding profiles from VideoRedo");
         return false;
      } else {
         // Sort encode list alphabetically
         List<String> vrd_list = new ArrayList<String>(hlist.keySet());
         Collections.sort(vrd_list);
         
         // Add to main encode profiles hash
         for (String str : vrd_list) {
            config.ENCODE.put(str, hlist.get(str));
            config.ENCODE_NAMES.add(str);
         }
         return true;
      }
   }
   
   // Get list of output profiles from VRD with custom VBS script
   // Return retrieved list has hash table or null if something fails
   private static Hashtable<String,Hashtable<String,String>> vrdGetProfiles() {
      Hashtable<String,Hashtable<String,String>> hlist = new Hashtable<String,Hashtable<String,String>>();
      String s = File.separator;
      String vrdscript = config.programDir + "\\VRDscripts\\getProfiles.vbs";      
      if ( ! file.isFile(vrdscript) ) {
         log.error("File does not exist: " + vrdscript);
         log.error("Aborting. Fix incomplete kmttg installation");
         return null;
      }
      String cscript = System.getenv("SystemRoot") + s + "system32" + s + "cscript.exe";
      if (! file.isFile(cscript)) {
         log.error("cscript.exe path does not exist: " + cscript);
         return null;
      }

      // System call to ccsript to do most of the work      
      Stack<String> command = new Stack<String>();
      command.add(cscript);
      command.add("//nologo");
      command.add(vrdscript);
      backgroundProcess process = new backgroundProcess();
      if ( process.run(command) ) {
         // Wait for command to terminate
         process.Wait();
         
         Stack<String> l;
         // Look for errors
         l = process.getStderr();
         if (l.size() > 0) {
            log.error(l);
            return null;
         }
         
         // Parse stdout
         Pattern pname = Pattern.compile("^.*<Name>(.+)</Name>.*$");
         Pattern ptype = Pattern.compile("^.*<FileType>(.+)</FileType>.*$");
         l = process.getStdout();
         if (l.size() > 0) {
            String line, Name="", FileType="", extension;
            for (int i=0; i<l.size(); ++i) {
               extension = "";
               line = l.get(i);
               //System.out.println("line=" + line);
               // Get rid of leading and trailing white space
               line = line.replaceFirst("^\\s*(.*$)", "$1");
               line = line.replaceFirst("^(.*)\\s*$", "$1");
               Matcher mname = pname.matcher(line);
               Matcher mtype = ptype.matcher(line);
               if (mname.matches()) {
                  Name = mname.group(1);
               }
               if (mtype.matches() && Name.length() > 0) {
                  FileType = mtype.group(1);
                  extension = FileType.toLowerCase();
                  if (FileType.startsWith("DVRMS")) {
                     extension = "dvr-ms";
                  }
                  else if (FileType.startsWith("Elementary")) {
                     extension = "m2v";
                     if (Name.contains("H.264"))
                        extension = "m4v";
                  }
                  else if (FileType.startsWith("M2TS")) {
                     extension = "m2ts";
                  }
                  else if (FileType.startsWith("MP4")) {
                     extension = "mp4";
                  }
                  else if (FileType.startsWith("MPG")) {
                     extension = "mpg";
                  }
                  else if (FileType.startsWith("REC")) {
                     extension = "rec";
                  }
                  else if (FileType.startsWith("TiVo")) {
                     extension = "TiVo";
                  }
                  else if (FileType.startsWith("TS")) {
                     extension = "ts";
                  }
                  else if (FileType.startsWith("WMV")) {
                     extension = "wmv";
                  }
                  if (extension.length() > 0) {
                     Hashtable<String,String> h = new Hashtable<String,String>();
                     h.put("description", "VideoRedo " + extension + " profile");
                     h.put("extension", extension);
                     hlist.put(Name, h);
                  }
                  Name = "";
               }                                      
            }
            return hlist;
         }
      } else {
         log.error("Failed to launch command: " + process.toString());
         log.error(process.getStderr());
      }
      return null;
   }
}
