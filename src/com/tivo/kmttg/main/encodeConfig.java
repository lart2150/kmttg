package com.tivo.kmttg.main;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Stack;

import com.tivo.kmttg.util.backgroundProcess;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.file;
import com.tivo.kmttg.util.log;

public class encodeConfig {

   // Determine list of encoding profiles to use
   public static void parseEncodingProfiles() {
      
      Stack<String> errors = new Stack<String>();
      
      // Clear out any previous settings
      config.ENCODE.clear();
      config.ENCODE_NAMES.clear();
     
      if (config.VrdEncode == 1) {
         // Use VRD encoding profiles
         Stack<String> result = getVrdProfiles();
         if (result.size() > 0) {
            log.error(result);
            return;
         }
      } else {
         // Use encoding profiles in "encode" folder
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
      }
      
      // Set ENCODE_NAMES to sorted list of entries
      if ( config.ENCODE.isEmpty() ) {
         errors.add("No valid encoding profiles found");
      } else {
         List<String> l = new ArrayList<String>(config.ENCODE.keySet());
         Collections.sort(l);
   
         for (String str : l) {
            config.ENCODE_NAMES.add(str);
         }
         
         // Set config.encodeName to 1st entry
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
      if (valid == false) {
         log.error("Invalid encode name selected: " + encodeName);
         log.error("Valid names are: " + getValidEncodeNames());
      }
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
         String[] l = config.ENCODE.get(encodeName).get("command").split("\\s+");
         command = l[0];
         command = command.replaceFirst("FFMPEG", escapeBackSlashes(config.ffmpeg));
         command = command.replaceFirst("MENCODER", escapeBackSlashes(config.mencoder));
         command = command.replaceFirst("HANDBRAKE", escapeBackSlashes(config.handbrake));
         command = command.replaceFirst("PERL", escapeBackSlashes(config.perl));
         command = command.replaceFirst("PWD", escapeBackSlashes(System.getProperty("user.dir") + File.separator));
      }
      return command;
   }

   // Return encoder command arguments
   public static Stack<String> getCommandArgs(String encodeName, String inputFile, String outputFile, String srtFile) {
      debug.print("inputFile=" + inputFile + " outputFile=" + outputFile);
      Stack<String> args = new Stack<String>();
      if ( ! config.ENCODE.isEmpty() ) {
         String arg;
         String[] l = config.ENCODE.get(encodeName).get("command").split("\\s+");
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
   private static Stack<String> getVrdProfiles() {
      Stack<String> errors = new Stack<String>();
      
      // This method parses xml file for profiles
      //if ( ! parseVrdXmlFile() ) {
      //   errors.add("Encountered problems parsing VideoRedo profiles xml file");
      //}
      
      // This method uses VRD functions to get profiles
      if ( ! vrdGetProfiles() ) {
         errors.add("Encountered problems obtaining encoding profiles from VideoRedo");
      }

      return errors;
   }
   
   // Parse VRD profile xml file and extract encoding information
   private static Boolean parseVrdXmlFile() {
      
      String VrdProfilesXml = "";      
      String UserProfile = System.getenv("USERPROFILE");
      if (UserProfile != null && file.isDir(UserProfile)) {
         String xml = UserProfile + "\\Documents\\VideoReDo\\OutputProfiles.xml";
         if (file.isFile(xml)) {
            VrdProfilesXml = xml;
         } else {
            xml = UserProfile + "\\My Documents\\VideoReDo\\OutputProfiles.xml";
            if (file.isFile(xml))
               VrdProfilesXml = xml;
         }
      }

      if ( ! file.isFile(VrdProfilesXml) ) {
         log.error("VideoRedo OutputProfiles.xml file not found");
         return false;
      }
            
      try {         
         BufferedReader xml = new BufferedReader(new FileReader(VrdProfilesXml));
         String line, Name="", FileType="", extension;
         while ( (line = xml.readLine()) != null ) {
            extension = "";
            // Get rid of leading and trailing white space
            line = line.replaceFirst("^\\s*(.*$)", "$1");
            line = line.replaceFirst("^(.*)\\s*$", "$1");
                                   
            // Now parse all items tagged with <Name> or <FileType>            
            if (line.matches("^<Name.+$")) {
               Name = line.replaceFirst("^<Name>(.+)</.+$", "$1");
            }
            if (line.matches("^<FileType.+$")) {
               FileType = line.replaceFirst("^<FileType>(.+)</.+$", "$1");
               if (FileType.length() > 0 && Name.length() > 0) {
                  // Filter out all entries except MP4 & WMV types
                  if (FileType.startsWith("MP4")) {
                     extension = "mp4";
                  }
                  if (FileType.startsWith("WMV")) {
                     extension = "wmv";
                  }
                  if (extension.length() > 0) {
                     Hashtable<String,String> h = new Hashtable<String,String>();
                     h.put("description", "VideoRedo " + extension + " profile");
                     h.put("extension", extension);
                     config.ENCODE.put(Name, h);
                  }
                  Name = "";
               }
            }
         }
         xml.close();                           
      }
      catch (IOException ex) {
         log.error(ex.toString());
         return false;
      }
      
      return true;
   }
   
   // Get list of output profiles from VRD with custom VBS script
   // Update ENCODE & ENCODE_NAMES according to retrieved list
   private static Boolean vrdGetProfiles() {
      String s = File.separator;
      String vrdscript = createGetProfilesScript();
      if (vrdscript == null || ! file.isFile(vrdscript))
         return false;
      String cscript = System.getenv("SystemRoot") + s + "system32" + s + "cscript.exe";
      if (! file.isFile(cscript)) {
         log.error("cscript.exe path does not exist: " + cscript);
         return false;
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
            file.delete(vrdscript);
            return false;
         }
         
         // Parse stdout
         l = process.getStdout();
         if (l.size() > 0) {
            String line, Name="", FileType="", extension;
            for (int i=0; i<l.size(); ++i) {
               extension = "";
               line = l.get(i);
               // Get rid of leading and trailing white space
               line = line.replaceFirst("^\\s*(.*$)", "$1");
               line = line.replaceFirst("^(.*)\\s*$", "$1");
                                      
               // Now parse all items tagged with <Name> or <FileType>            
               if (line.matches("^<Name.+$")) {
                  Name = line.replaceFirst("^<Name>(.+)</.+$", "$1");
               }
               if (line.matches("^<FileType.+$")) {
                  FileType = line.replaceFirst("^<FileType>(.+)</.+$", "$1");
                  if (FileType.length() > 0 && Name.length() > 0) {
                     // Filter out all entries except MP4 & WMV types
                     if (FileType.startsWith("MP4")) {
                        extension = "mp4";
                     }
                     if (FileType.startsWith("WMV")) {
                        extension = "wmv";
                     }
                     if (extension.length() > 0) {
                        Hashtable<String,String> h = new Hashtable<String,String>();
                        h.put("description", "VideoRedo " + extension + " profile");
                        h.put("extension", extension);
                        config.ENCODE.put(Name, h);
                     }
                     Name = "";
                  }
               }
            }
            file.delete(vrdscript);
            return true;
         }
      } else {
         log.error("Failed to launch command: " + process.toString());
         log.error(process.getStderr());
      }
      file.delete(vrdscript);
      return false;
   }
   
   // Create custom VB script that uses VideoRedo methods to print encoding profiles to stdout
   private static String createGetProfilesScript() {
      String script = file.makeTempFile("VRD", ".vbs");
      String eol = "\r";
      try {
         BufferedWriter ofp = new BufferedWriter(new FileWriter(script));
         ofp.write("'Create VideoReDo object." + eol);
         ofp.write("Set VideoReDoSilent = wscript.CreateObject( \"VideoReDo.VideoReDoSilent\" )" + eol);
         ofp.write("set VideoReDo = VideoReDoSilent.VRDInterface" + eol);
         ofp.write("" + eol);
         ofp.write("' Get number of profiles available." + eol);
         ofp.write("numProfiles = VideoReDo.GetProfilesCount()" + eol);
         ofp.write("if ( numProfiles > 0 ) then" + eol);
         ofp.write("   for i = 1 to numProfiles" + eol);
         ofp.write("      if (VideoReDo.IsProfileEnabled(i)) then" + eol);
         ofp.write("         wscript.echo(VideoReDo.GetProfileXML(i))" + eol);
         ofp.write("      end if" + eol);
         ofp.write("   next" + eol);
         ofp.write("end if" + eol);
         ofp.write("" + eol);
         ofp.write("' Close VRD" + eol);
         ofp.write("VideoReDo.Close()" + eol);
         ofp.write("" + eol);
         ofp.write("' Exit with status 0" + eol);
         ofp.write("wscript.quit 0" + eol);
         ofp.close();
      }
      catch (Exception ex) {
         log.error(ex.toString());
         return null;
      }

      return script;
   }

}
