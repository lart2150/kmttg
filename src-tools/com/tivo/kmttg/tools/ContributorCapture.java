package com.tivo.kmttg.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.RpcFixtureCapture;
import com.tivo.kmttg.util.XmlFixtureCapture;

/**
 * The entry point of the standalone capture jar: drop it beside a config.ini, run it, and
 * it produces one zip of test fixtures to send back.
 *
 * kmttg's tests run against documents captured from a real TiVo, and what a box sends
 * differs by model and software level. Only the models somebody has can be captured, so
 * this exists to let an Edge, a Roamio or a Premiere owner produce the same set a Bolt
 * owner already has.
 *
 * It only ever reads. The TiVo is asked for its Now Playing list, the metadata behind a
 * few of those recordings, and the read-only RPC calls kmttg itself makes for the To Do
 * list, season passes and thumbs ratings. Nothing is recorded, deleted or scheduled.
 *
 * The RPC interface arrived with the Premiere, so a Series 3 or earlier is read over HTTP
 * alone. Those are the models the project is least likely to get fixtures for any other
 * way, so the box is named off its Server header before it is asked anything and the RPC
 * half is skipped rather than left to time out.
 *
 * Every identifier goes through FixtureSanitizer on the way to disk, and the summary
 * printed at the end says what the zip holds so it can be looked at before being sent.
 */
public class ContributorCapture {

   // One page of the Now Playing list. Enough to hold a few series and usually a movie.
   private static final int NPL_ITEMS  = 8;
   // Per RPC list. The same figure the project's own fixtures were captured with.
   private static final int RPC_ENTRIES = 40;

   public static void main(String[] args) throws Exception {
      // Same security relaxations kmttg applies so the TiVo's weak cert chain is accepted.
      System.setProperty("https.cipherSuites", "SSL_RSA_WITH_RC4_128_SHA");
      java.security.Security.setProperty("jdk.certpath.disabledAlgorithms", "");
      java.security.Security.setProperty("jdk.tls.disabledAlgorithms", "SSLv3");

      File configDir = configDir(args);
      File ini = new File(configDir, "config.ini");
      if (! ini.isFile()) {
         System.out.println("No config.ini found in " + configDir.getAbsolutePath());
         System.out.println();
         System.out.println("Put this jar in your kmttg folder - the one holding config.ini -");
         System.out.println("and run it again, or give that folder as an argument:");
         System.out.println("    java -jar kmttg-fixture-capture.jar \"C:\\path\\to\\kmttg\"");
         return;
      }
      System.out.println("kmttg fixture capture");
      System.out.println("Reading " + ini.getAbsolutePath());
      System.out.println();

      // programDir is where kmttg reads the decoder certificate from, and the RPC half does
      // not connect without it. The bundled one in this jar is the fallback.
      config.programDir = configDir.getAbsolutePath();
      String mak = XmlFixtureCapture.readIniValue(ini.getAbsolutePath(), "MAK");
      if (mak == null || mak.length() != 10) {
         System.out.println("No usable MAK in config.ini - kmttg needs one to read a TiVo.");
         return;
      }
      config.MAK = mak;

      // plan() throws when config.ini names no TiVos at all, which is as likely here as a
      // box being switched off - and the person reading this is not the one who would make
      // sense of a stack trace.
      List<XmlFixtureCapture.Box> boxes;
      try {
         boxes = XmlFixtureCapture.plan(ini.getAbsolutePath(), null, null);
      } catch (Exception e) {
         System.out.println();
         System.out.println(e.getMessage());
         System.out.println("Open kmttg, add your TiVo under Configure, and run this again.");
         return;
      }
      if (boxes.isEmpty()) {
         System.out.println();
         System.out.println("No TiVo answered. Check the box is on and on this network.");
         return;
      }

      File out = new File(configDir, "kmttg-fixtures");
      delete(out);
      List<String> labels = new ArrayList<String>();
      for (XmlFixtureCapture.Box box : boxes) {
         System.out.println();
         System.out.println(box.model + " (" + box.name + ")");
         File dir = new File(out, box.label);
         dir.mkdirs();
         labels.add(box.label);

         XmlFixtureCapture.writeProvenance(dir, box.label, box, NPL_ITEMS);
         XmlFixtureCapture.capture(new FixtureSanitizer(box.ip, mak, box.tsn), dir,
            box.label, box.ip, NPL_ITEMS);
         // A Series 3 or earlier has no RPC interface at all, and a set of XML fixtures
         // from one is the whole capture rather than half of a failed one - so it is not
         // attempted and not reported as a failure.
         if (! box.rpc) {
            System.out.println("  A " + box.model + " has no RPC interface - the XML"
               + " fixtures above are the capture.");
            continue;
         }
         // The RPC half is the one that needs the certificate, so it is the one that fails
         // on a box kmttg itself could not talk to either. The XML fixtures are worth
         // sending on their own, so a failure here does not lose them.
         try {
            RpcFixtureCapture.run(box.name, box.ip, mak, RPC_ENTRIES, null, dir);
         } catch (Exception e) {
            System.out.println("  RPC capture failed (" + e.getMessage() + ")");
            System.out.println("  The XML fixtures above are still worth sending.");
         }
      }

      File zip = new File(configDir, "kmttg-fixtures-" + join(labels) + "-"
         + java.time.LocalDate.now() + ".zip");
      zip(out, zip);
      delete(out);
      summary(zip);
   }

   // Where config.ini is: said on the command line, else beside the jar, else where the
   // command was run from. Beside the jar is the case worth getting right - it is what
   // "drop it in your kmttg folder and double click it" produces.
   private static File configDir(String[] args) {
      if (args.length > 0 && args[0].length() > 0) return new File(args[0]);
      try {
         File jar = new File(ContributorCapture.class.getProtectionDomain()
            .getCodeSource().getLocation().toURI());
         File dir = jar.isFile() ? jar.getParentFile() : jar;
         if (dir != null && new File(dir, "config.ini").isFile()) return dir;
      } catch (Exception e) {
         // not running from a jar, or no permission to ask - fall through
      }
      return new File(".").getAbsoluteFile().getParentFile();
   }

   private static void summary(File zip) {
      System.out.println();
      System.out.println("Written: " + zip.getAbsolutePath());
      System.out.println();
      System.out.println("What is in it:");
      System.out.println("  capture_*.txt        which model and software version it came from");
      System.out.println("  npl_*.xml            one page of your Now Playing list");
      System.out.println("  videodetails_*.xml   the metadata behind a few of those recordings");
      System.out.println("  *.json               the To Do list, season passes, thumbs and");
      System.out.println("                       channel list, as kmttg's own RPC calls return them");
      System.out.println();
      System.out.println("Your MAK, service number, IP address and zip code are replaced with");
      System.out.println("dummy values. Programme titles, channels and call signs are kept - they");
      System.out.println("are what the tests read. It is all plain text: open the zip and look");
      System.out.println("before sending it on.");
   }

   private static String join(List<String> labels) {
      StringBuilder sb = new StringBuilder();
      for (String l : labels) {
         if (sb.length() > 0) sb.append("-");
         sb.append(l);
      }
      return sb.length() == 0 ? "none" : sb.toString();
   }

   private static void zip(File dir, File zipFile) throws Exception {
      try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
         add(zos, dir, "");
      }
   }

   private static void add(ZipOutputStream zos, File file, String path) throws Exception {
      if (file.isDirectory()) {
         File[] kids = file.listFiles();
         if (kids == null) return;
         for (File kid : kids)
            add(zos, kid, path.isEmpty() ? kid.getName() : path + "/" + kid.getName());
         return;
      }
      zos.putNextEntry(new ZipEntry(path));
      byte[] buf = new byte[8192];
      try (InputStream in = new FileInputStream(file)) {
         for (int n = in.read(buf); n > 0; n = in.read(buf)) zos.write(buf, 0, n);
      }
      zos.closeEntry();
   }

   private static void delete(File file) {
      if (file.isDirectory()) {
         File[] kids = file.listFiles();
         if (kids != null) for (File kid : kids) delete(kid);
      }
      file.delete();
   }
}
