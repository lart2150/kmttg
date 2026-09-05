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

import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * Jar entry point, and the only class in kmttg.jar compiled to Java 8 bytecode.
 * update.update_kmttg_background unzips a new release over the install
 * directory and relaunches with a detached ProcessBuilder whose output nobody
 * reads, so a user still on Java 8 would just see kmttg vanish. This class
 * loads on any JRE, checks the version and says what to do about it.
 *
 * Java 8 APIs only, and no compile time reference to anything outside the JDK:
 * the rest of kmttg is Java 11 bytecode, so the real main class has to be
 * reached reflectively. build.gradle's verifyLauncherBytecode enforces this.
 */
public final class Launcher {

   // Keep in sync with options.release in build.gradle (verifyLauncherBytecode checks)
   private static final int MIN_JAVA = 11;
   private static final String APP_MAIN_CLASS = "com.tivo.kmttg.main.kmttg";
   private static final String LAST_JAVA8_RELEASE = "v2.9.5-l";

   // Exit 2 = JRE too old. wrapper.conf maps it to SHUTDOWN so the service
   // doesn't sit in a restart loop.
   private static final int EXIT_UNSUPPORTED_JRE = 2;
   private static final int EXIT_STARTUP_FAILED = 1;

   // Set -Dkmttg.test.javaVersion=8 to exercise the old JRE path on a new JVM
   private static final String VERSION_OVERRIDE_PROPERTY = "kmttg.test.javaVersion";

   private Launcher() {
   }

   public static void main(String[] args) {
      if (args == null)
         args = new String[0];

      int running = runningJavaVersion();
      if (running > 0 && running < MIN_JAVA) {
         reportUnsupportedJre(running, args);
         System.exit(EXIT_UNSUPPORTED_JRE);
         return;
      }

      Method appMain;
      try {
         appMain = Class.forName(APP_MAIN_CLASS).getMethod("main", String[].class);
      } catch (Throwable t) {
         // An unreadable version string lands here instead of the check above
         if (isUnsupportedClassVersion(t)) {
            reportUnsupportedJre(running, args);
            System.exit(EXIT_UNSUPPORTED_JRE);
            return;
         }
         System.err.println("kmttg: could not load " + APP_MAIN_CLASS);
         t.printStackTrace();
         System.exit(EXIT_STARTUP_FAILED);
         return;
      }

      try {
         // The (Object) cast stops varargs from spreading args out
         appMain.invoke(null, (Object) args);
      } catch (InvocationTargetException e) {
         Throwable cause = (e.getCause() != null) ? e.getCause() : e;
         if (isUnsupportedClassVersion(cause)) {
            reportUnsupportedJre(running, args);
            System.exit(EXIT_UNSUPPORTED_JRE);
            return;
         }
         // Re-throw instead of logging, so myExceptionHandler still sees it
         if (cause instanceof RuntimeException)
            throw (RuntimeException) cause;
         if (cause instanceof Error)
            throw (Error) cause;
         throw new RuntimeException(cause);
      } catch (IllegalAccessException e) {
         System.err.println("kmttg: cannot invoke " + APP_MAIN_CLASS + ".main - " + e);
         e.printStackTrace();
         System.exit(EXIT_STARTUP_FAILED);
      }
   }

   // 8 for "1.8.0_412", 11 for "11.0.23". -1 when it can't be read, in which
   // case main carries on and lets the class loader decide. Runtime.version()
   // would be simpler but it needs Java 9.
   static int runningJavaVersion() {
      String override = System.getProperty(VERSION_OVERRIDE_PROPERTY);
      if (override != null && override.trim().length() > 0)
         return parseFeatureVersion(override);
      String v = System.getProperty("java.specification.version");
      if (v == null || v.trim().length() == 0)
         v = System.getProperty("java.version");
      return parseFeatureVersion(v);
   }

   static int parseFeatureVersion(String value) {
      if (value == null)
         return -1;
      String v = value.trim();
      if (v.startsWith("1."))
         v = v.substring(2);
      int end = 0;
      // Not Character.isDigit - that accepts non-ASCII digits parseInt rejects
      while (end < v.length() && v.charAt(end) >= '0' && v.charAt(end) <= '9')
         end++;
      if (end == 0)
         return -1;
      try {
         return Integer.parseInt(v.substring(0, end));
      } catch (NumberFormatException e) {
         return -1;
      }
   }

   // Only UnsupportedClassVersionError counts - a NoClassDefFoundError from a
   // genuinely missing dependency must not be reported as a JRE problem.
   private static boolean isUnsupportedClassVersion(Throwable t) {
      Set<Throwable> seen = new HashSet<Throwable>();
      while (t != null && seen.add(t)) {
         if (t instanceof UnsupportedClassVersionError)
            return true;
         t = t.getCause();
      }
      return false;
   }

   private static void reportUnsupportedJre(int running, String[] args) {
      String detected = (running > 0)
            ? ("Java " + running + " (" + System.getProperty("java.version") + ")")
            : ("Java " + System.getProperty("java.version"));

      String message =
         productName() + " needs Java " + MIN_JAVA + " or newer.\n" +
         "\n" +
         "It was started with " + detected + "\n" +
         "\n" +
         "kmttg's files were updated successfully, but this Java is too old to\n" +
         "run them.  To finish the update:\n" +
         "\n" +
         "  1. Install a current Java - Java 21 or Java 25 is recommended.\n" +
         "     Free builds: https://adoptium.net\n" +
         "  2. Start kmttg again.\n" +
         "\n" +
         "Your config.ini, auto.ini, queue and downloads are untouched.\n" +
         "If you would rather go back, the last release that runs on Java 8 is\n" +
         LAST_JAVA8_RELEASE + ":\n" +
         "https://github.com/lart2150/kmttg/releases/tag/" + LAST_JAVA8_RELEASE;

      // Always to stderr as well - the console or service log is the only copy
      // anyone can paste into a bug report
      System.err.println();
      System.err.println(message);
      System.err.println();
      System.err.flush();

      if (wantsDialog(args))
         showDialog(message);
   }

   // "kmttg v2.10.0-l" from the jar manifest, or just "kmttg" when running from
   // a classes directory
   private static String productName() {
      try {
         Package p = Launcher.class.getPackage();
         String v = (p == null) ? null : p.getImplementationVersion();
         if (v != null && v.trim().length() > 0)
            return "kmttg " + v.trim();
      } catch (Throwable ignored) {
      }
      return "kmttg";
   }

   // These are the flags that clear gui_mode in kmttg.getopt - keep in sync. A
   // modal dialog from the service or from a scripted "kmttg -v" would hang
   // forever with nothing visible on screen.
   private static boolean wantsDialog(String[] args) {
      for (int i = 0; i < args.length; i++) {
         String a = args[i];
         if (a.equals("-a") || a.equals("-b") || a.equals("-c") || a.equals("-h")
               || a.equals("-k") || a.equals("-s") || a.equals("-sp")
               || a.equals("-spf") || a.equals("-v"))
            return false;
      }
      try {
         return !GraphicsEnvironment.isHeadless();
      } catch (Throwable t) {
         return false;
      }
   }

   private static void showDialog(String message) {
      try {
         // Default look and feel - FlatLaf is Java 11 bytecode
         javax.swing.JOptionPane.showMessageDialog(
               null, message,
               "kmttg - Java " + MIN_JAVA + " or newer required",
               javax.swing.JOptionPane.ERROR_MESSAGE);
      } catch (Throwable t) {
         System.err.println("kmttg: (could not show a dialog: " + t + ")");
      }
   }
}
