package com.tivo.kmttg.util;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.ssl.SSLContextBuilder;

import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.http;
import com.tivo.kmttg.rpc.MindVersionQuery;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.tools.FixtureSanitizer;

/**
 * One-off developer tool (NOT a unit test) that captures the XML a TiVo serves over its
 * HTTP interface into the fixtures under test/resources/fixtures/, the way
 * RpcFixtureCapture does for the RPC side.
 *
 * This is the older interface kmttg still depends on: javaNowPlaying reads the Now
 * Playing List from it, and createMeta builds the pyTivo metadata file out of a
 * TiVoVideoDetails document. Both are parsed by hand rather than by a schema, so what a
 * real box actually emits - the entity escaping, the element order, the fields it leaves
 * out on a movie or a partial recording - is the part worth keeping.
 *
 * Every TiVo in config.ini is captured, because the elements a box sends differ by model
 * and software level and two captures side by side are what shows that. Each set of files
 * is named after the model, so a second box adds to the fixtures rather than replacing the
 * first.
 *
 * Addresses and credentials are replaced with same-format dummies before anything is
 * written, so the fixtures carry no real ones. Recording titles are kept: they are
 * broadcast programs, and they are what makes a fixture worth reading.
 *
 * -PconfigDir must be a real kmttg installation directory, not this source tree: the
 * decoder certificate is read from there, and the cdata.p12 sitting in the repo root has a
 * stale cdata.password beside it, which fails the RPC half with "keystore password was
 * incorrect".
 *
 * Run via:  gradlew captureXmlFixtures -PconfigDir="C:\\path\\to\\kmttg"
 *           gradlew captureXmlFixtures -PconfigDir="." -Ptivo=Bolt -Plabel=bolt-21-9
 */
public class XmlFixtureCapture {

   // How many details documents to capture per box, out of the first page.
   private static final int MAX_DETAILS = 3;

   // What a box said about itself. Held together because the label is chosen from it, so it
   // all has to be known before the first file is named.
   public static class Box {
      public String name;            // as config.ini names it
      public String ip;
      public String label;           // what its fixtures are called
      public String httpd    = "unknown";   // the Server header, present on every response
      public String model    = "unknown";   // read from the service number prefix
      public String code     = "unknown";
      public String series   = "unknown";   // RPC only
      public String software = "unknown";   // RPC only; the Server header carries it too
      public String protocol = "unknown";
      public String maxMind  = "unknown";   // RPC only; newest grammar it will accept
      public String tsn;                    // never written, only scrubbed out of what is
      public boolean rpc = true;            // false on a box older than the RPC interface
   }

   public static void main(String[] args) throws Exception {
      // Same security relaxations kmttg.main applies so the TiVo's weak cert chain is
      // accepted (otherwise the TLS handshake is rejected).
      System.setProperty("https.cipherSuites", "SSL_RSA_WITH_RC4_128_SHA");
      java.security.Security.setProperty("jdk.certpath.disabledAlgorithms", "");
      java.security.Security.setProperty("jdk.tls.disabledAlgorithms", "SSLv3");

      String configDir = args.length > 0 ? args[0] : ".";
      // One box by name. Left out, every TiVo in config.ini is captured.
      String only      = args.length > 1 && args[1].length() > 0 ? args[1] : null;
      // Small enough to read back, large enough to show repeats of one series.
      int itemCount    = args.length > 2 ? Integer.parseInt(args[2]) : 8;
      // Names the capture. Worth setting when a second capture is meant to show a software
      // level rather than a model, and only meaningful for a single named box.
      String labelArg  = args.length > 3 && args[3].length() > 0 ? args[3] : null;

      config.programDir = configDir;
      String ini = configDir + File.separator + "config.ini";
      String mak = readIniValue(ini, "MAK");
      if (mak == null)
         throw new IllegalStateException("No MAK in " + ini);
      config.MAK = mak;

      File outDir = new File("test/resources/fixtures");
      outDir.mkdirs();
      List<Box> boxes = plan(ini, only, labelArg);
      for (Box box : boxes) {
         writeProvenance(outDir, box.label, box, itemCount);
         capture(new FixtureSanitizer(box.ip, mak, box.tsn), outDir, box.label, box.ip, itemCount);
      }
      System.out.println(boxes.isEmpty() ? "Nothing captured." : "Done.");
   }

   // Which boxes to capture, already probed and named: one per model, because four Bolts
   // serve the same documents as one and the second set would only be a second copy under a
   // different name. Keyed on the service number prefix rather than the model name, so a
   // model this has no name for still counts as its own.
   public static List<Box> plan(String iniPath, String only, String labelArg) throws Exception {
      Map<String,String> tivos = readTivos(iniPath);
      if (only != null) {
         if (! tivos.containsKey(only))
            throw new IllegalStateException(only + " is not a TiVo in " + iniPath + " (have: "
               + tivos.keySet() + ")");
         String ip = tivos.get(only);
         tivos.clear();
         tivos.put(only, ip);
      }
      if (tivos.isEmpty())
         throw new IllegalStateException("No TiVos in " + iniPath);

      List<Box> planned = new ArrayList<Box>();
      Set<String> seen = new LinkedHashSet<String>();
      for (Map.Entry<String,String> tivo : tivos.entrySet()) {
         String name = tivo.getKey(), ip = tivo.getValue();
         System.out.println("Reading " + name + " at " + ip + " ...");
         Box box = probe(name, ip);
         box.name = name;
         box.ip = ip;
         // A box that answered nothing is one that is off or on another network. Its
         // fixtures would be missing too, so say so and move to the next one.
         if (box.httpd.equals("unknown") && box.software.equals("unknown")) {
            System.out.println("  " + name + ": no answer - skipped");
            continue;
         }
         String kind = box.code.equals("unknown") ? slug(name) : box.code;
         if (! seen.add(kind)) {
            System.out.println("  " + name + ": already have a " + box.model + " - skipped");
            continue;
         }
         box.label = label(labelArg, box, name);
         planned.add(box);
      }
      return planned;
   }

   // The fixtures for one box.
   public static void capture(FixtureSanitizer san, File dir, String label, String ip, int itemCount) {
      // A run that finds fewer kinds of recording than the last one writes fewer files, and
      // whatever it does not overwrite would otherwise sit there being read as part of this
      // capture forever. Only this label's own files, so the other models keep theirs.
      for (File f : dir.listFiles()) {
         String name = f.getName();
         if (name.startsWith("npl_" + label + "_") || name.startsWith("videodetails_" + label + "_"))
            f.delete();
      }
      String base = "https://" + ip + "/TiVoConnect?Command=QueryContainer&Container=/NowPlaying";

      // Two consecutive pages of the flattened list, as javaNowPlaying fetches it. The pair
      // is the point: the continuation is driven by ItemCount and TotalItems in the container
      // header, and a single page never shows whether those line up.
      String page1 = fetch(san, dir, "npl_" + label + "_page1.xml",
         base + "&Recurse=Yes&ItemCount=" + itemCount + "&AnchorOffset=0");
      fetch(san, dir, "npl_" + label + "_page2.xml",
         base + "&Recurse=Yes&ItemCount=" + itemCount + "&AnchorOffset=" + itemCount);

      // One recording on its own, as jobMonitor asks for it to find the ByteOffset a resumed
      // download starts from. Same fields, different container.
      String download = firstUrl(page1, "download");
      if (download != null) {
         try {
            fetch(san, dir, "npl_" + label + "_item.xml", "https://" + ip
               + "/TiVoConnect?Command=QueryItem&Url=" + URLEncoder.encode(download, "UTF-8"));
         } catch (Exception e) {
            System.out.println("  npl_" + label + "_item.xml: " + e.getMessage() + " (skipped)");
         }
      } else {
         System.out.println("  npl_" + label + "_item.xml: no download url on the page (skipped)");
      }

      // The extended metadata createMeta turns into a pyTivo .txt file. Several, because the
      // fields present differ by what was recorded - an episodic show carries an episode
      // title and number, a movie carries a year and a star rating instead.
      List<String> details = variedDetailUrls(page1);
      for (int i = 0; i < details.size() && i < MAX_DETAILS; i++) {
         fetch(san, dir, "videodetails_" + label + "_" + (i + 1) + ".xml", details.get(i));
      }
      if (details.isEmpty())
         System.out.println("  videodetails_" + label + ": nothing to fetch (skipped)");
   }

   // What the box says about itself. The software level is in the Server header of every
   // response, the 401 included, so that half needs no credentials and survives a box whose
   // RPC will not talk. The model is read from the service number prefix in it.
   public static Box probe(String tivoName, String ip) {
      Box box = new Box();
      String header = serverHeader("https://" + ip + "/TiVoConnect?Command=QueryServer");
      if (header != null) box.httpd = header.trim();
      // Named off the header before anything has been asked of the box, because that is
      // what makes the next decision possible: RPC arrived with the Premiere, so on a
      // Series 3 or earlier there is nothing listening and connecting only spends a
      // timeout to learn what the prefix already said. A box that named no model is still
      // worth trying - that is the case where the RPC answer is the only one there is.
      identify(box);
      if (box.rpc) {
         describe(box, tivoName, ip);
         // Over a connection of its own, because the answer only carries the grammar version
         // when the question is asked at a newer schema version than kmttg itself speaks.
         String mind = MindVersionQuery.get(tivoName, ip, config.MAK, config.programDir);
         if (mind != null) box.maxMind = mind;
         // The software version and the service number carry the prefix too, so a box that
         // served no Server header is named by what the RPC just gave.
         identify(box);
      } else {
         System.out.println("  " + box.model + " predates the RPC interface - XML only");
      }
      try {
         ByteArrayOutputStream out = new ByteArrayOutputStream();
         if (http.downloadPiped("https://" + ip + "/TiVoConnect?Command=QueryServer",
               "tivo", config.MAK, out, false, null)) {
            Matcher m = Pattern.compile("<Version>([^<]+)</Version>")
               .matcher(new String(out.toByteArray(), "UTF-8"));
            if (m.find()) box.protocol = m.group(1);
         }
      } catch (Exception e) {
         System.out.println("  QueryServer: " + e.getMessage());
      }
      // Said out loud rather than left as an "unknown" line in a file nobody reads again:
      // a capture whose provenance is half missing is one nobody can attribute later.
      if (box.rpc && box.series.equals("unknown"))
         System.out.println("  NOTE: no RPC answer - series and software version not recorded");
      if (box.rpc && ! box.series.equals("unknown") && box.maxMind.equals("unknown"))
         System.out.println("  NOTE: the box answered RPC but named no maxMindVersion");
      if (box.tsn == null)
         System.out.println("  NOTE: no service number from RPC - it can only be taken out of"
            + " the fixtures by shape, not by value");
      if (box.httpd.equals("unknown"))
         System.out.println("  NOTE: no Server header - the model could not be read");
      return box;
   }

   // Series, software version and the service number, from the same RPC the System Info
   // dialog reads. The service number is never written anywhere: it is here so the sanitizer
   // can take it out of the documents by value rather than by guessing at its shape.
   private static void describe(Box box, String tivoName, String ip) {
      Remote r = null;
      try {
         r = new Remote(tivoName, ip, -1, config.MAK, null);
         if (! r.success) return;
         JSONObject json = new JSONObject();
         json.put("bodyId", r.bodyId_get());
         JSONObject response = r.Command("systemInformationGet", json);
         if (response == null) return;
         box.series = field(response, "platform");
         box.software = field(response, "softwareVersion");
         // deviceName is in here too and reads like a model ("Bolt"), but it is the name the
         // owner gave the box. So is the zip code beside it - neither goes in a fixture.
         box.tsn = field(response, "tivoServiceNumber");
         if (box.tsn.equals("unknown")) box.tsn = r.bodyId_get();
      } catch (Exception e) {
         System.out.println("  systemInformationGet: " + e.getMessage());
      } finally {
         if (r != null) r.disconnect();
      }
   }

   // The model, its series and whether RPC is worth trying, from whichever of the three
   // sources has an answer. Run again after the RPC half, which supplies two of them.
   private static void identify(Box box) {
      box.code = modelCode(box);
      box.model = modelName(box.code);
      if (box.series.equals("unknown")) box.series = modelSeries(box.code);
      box.rpc = ! box.series.equals("3") && ! box.series.equals("2");
   }

   // Named after the model, so the fixtures say what they are rather than what their owner
   // calls the box. A model with no name here is named by its service number prefix, and a
   // box that would say neither falls back to its name in config.ini.
   public static String label(String labelArg, Box box, String tivoName) {
      if (labelArg != null) return slug(labelArg);
      if (! box.model.equals("unknown")) return slug(box.model);
      if (! box.code.equals("unknown")) return slug(box.code);
      return slug(tivoName);
   }

   private static String slug(String s) {
      return s.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
   }

   // What produced this set of fixtures, so a difference between two of them can be
   // attributed rather than guessed at.
   public static void writeProvenance(File dir, String label, Box box, int itemCount) {
      String text = "# Written by XmlFixtureCapture - what the " + label + " fixtures came from\n"
         + "label: " + label + "\n"
         + "model: " + box.model + "\n"
         + "modelCode: " + box.code + "\n"
         + "series: " + box.series + "\n"
         + "software: " + box.software + "\n"
         + "httpd: " + box.httpd + "\n"
         + "protocol: " + box.protocol + "\n"
         + "maxMindVersion: " + box.maxMind + "\n"
         + "itemCount: " + itemCount + "\n"
         + "captured: " + java.time.LocalDate.now() + "\n";
      try {
         Files.write(new File(dir, "capture_" + label + ".txt").toPath(),
            text.getBytes(Charset.forName("UTF-8")));
         System.out.println("  capture_" + label + ".txt: " + box.model + ", " + box.httpd);
      } catch (Exception e) {
         System.out.println("  capture_" + label + ".txt: " + e.getMessage() + " (skipped)");
      }
   }

   // Service number prefix to model, from TiVo's own tables (support article 000001490 for
   // the Premiere on, 000001411 for Series 3 and earlier). A prefix names a series rather
   // than one box - every Bolt variant is 849, and the two Edge ones differ only in what
   // they tune - which is the level a fixture cares about. Where an article gives one name
   // to several prefixes the prefix is kept in the name here, so that two boxes captured
   // side by side do not write over each other's files.
   private static String modelName(String code) {
      switch (code) {
         case "D6E": case "D6F":                         return "Edge";
         case "849":                                     return "Bolt";
         case "840": case "846": case "848":             return "Roamio";
         case "746": case "748": case "750": case "758": return "Premiere";
         case "A92": case "A93": case "A95":             return "Mini";
         case "A94":                                     return "Stream";
         case "658":                                     return "TiVo HD XL";
         case "652":                                     return "TiVo HD";
         case "648":                                     return "Series3 HD";
         case "649":                                     return "Series2 DT";
         case "595":                                     return "Humax DVD Writer";
         case "590":                                     return "Humax Series2";
         case "565":                                     return "Toshiba DVD Writer";
         case "382":                                     return "Samsung DirecTV";
         case "357":                                     return "Hughes HD DTV";
         case "351":                                     return "Hughes DirecTV";
         case "321":                                     return "RCA DirecTV";
         case "301":                                     return "Philips DirecTV";
         case "275":                                     return "Pioneer DVD";
         case "264":                                     return "Toshiba DVD Player";
         case "151":                                     return "Hughes Satellite";
         case "121":                                     return "RCA DTV";
         case "542": case "540": case "240":
         case "230": case "140": case "130":
         case "110":                                     return "Series2 " + code;
         default:                                        return "unknown";
      }
   }

   // The series a prefix belongs to, for the two that have to be known before the box is
   // asked anything: RPC arrived with the Premiere, so a Series 3 or earlier serves the XML
   // interface and nothing else. Every prefix in both is here (support article 000001411) -
   // a Series 2 missing from it would be connected to, and the person running this would
   // wait out a timeout for it. Series 4 and up answer with their own platform, so they are
   // not listed: a number guessed here would be written into the provenance file as fact.
   private static String modelSeries(String code) {
      switch (code) {
         case "658": case "652": case "648":
            return "3";
         case "649": case "595": case "590": case "565": case "542": case "540":
         case "382": case "357": case "351": case "321": case "301":
         case "275": case "264": case "240": case "230":
         case "151": case "140": case "130": case "121": case "110":
            return "2";
         default:
            return "unknown";
      }
   }

   // The first field of a service number, which is the model: 849 on a Bolt. The Server
   // header and the RPC software version both end with it, and the number itself starts with
   // it - so it survives any one of the three being unavailable. Unlike the number it
   // prefixes, it names a model rather than a box, which is why it is safe to write down.
   // Either separator in the header: a Bolt serves tivo-httpd-1:<version>:849, and the
   // older boxes that make the header the only source hang the prefix off a dash instead.
   private static String modelCode(Box box) {
      String[][] sources = {
         {box.httpd,    "[:-]([A-Z0-9]{3})$"},
         {box.software, "-([A-Z0-9]{3})$"},
         {box.tsn,      "^(?:tsn:)?([A-Z0-9]{3})"}
      };
      for (String[] source : sources) {
         if (source[0] == null) continue;
         Matcher m = Pattern.compile(source[1]).matcher(source[0].trim().toUpperCase());
         if (m.find()) return m.group(1);
      }
      return "unknown";
   }

   private static String field(JSONObject json, String name) {
      try {
         if (json.has(name) && json.getString(name).length() > 0) return json.getString(name);
      } catch (Exception e) {
         // not a string; treat it as absent
      }
      return "unknown";
   }

   // Read off the 401 the TiVo answers with before any credential is offered, so this asks
   // for nothing and needs none. Built here rather than through http.downloadPiped, which
   // hands back the body and drops the headers.
   private static String serverHeader(String url) {
      try (CloseableHttpClient client = HttpClients.custom()
            .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
               .setTlsSocketStrategy(ClientTlsStrategyBuilder.create()
                  .setSslContext(SSLContextBuilder.create()
                     .loadTrustMaterial(TrustAllStrategy.INSTANCE).build())
                  .setTlsVersions(TLS.V_1_0, TLS.V_1_1, TLS.V_1_2)
                  // The TiVo's certificate names no host at all, and the built-in policy
                  // checks that below the verifier - so the verifier alone is not enough.
                  .setHostVerificationPolicy(HostnameVerificationPolicy.CLIENT)
                  .setHostnameVerifier(NoopHostnameVerifier.INSTANCE).buildClassic())
               .build())
            .build()) {
         ClassicHttpResponse response = client.executeOpen(null, new HttpGet(url), null);
         Header header = response.getFirstHeader("Server");
         String value = header == null ? null : header.getValue();
         response.close();
         return value;
      } catch (Exception e) {
         return null;
      }
   }

   // Fetches one document, scrubs it and writes it. Returns the raw text so the caller can
   // read urls back out of it; a failure is reported and skipped rather than abandoning the
   // run with the earlier fixtures already written.
   private static String fetch(FixtureSanitizer san, File dir, String name, String url) {
      try {
         ByteArrayOutputStream out = new ByteArrayOutputStream();
         if (! http.downloadPiped(url, "tivo", config.MAK, out, false, null)) {
            System.out.println("  " + name + ": fetch failed (skipped)");
            return null;
         }
         String xml = new String(out.toByteArray(), "UTF-8");
         Files.write(new File(dir, name).toPath(),
            san.scrub(xml).getBytes(Charset.forName("UTF-8")));
         System.out.println("  " + name + ": " + xml.length() + " bytes, "
            + count(xml, "<Item>") + " items");
         return xml;
      } catch (Exception e) {
         System.out.println("  " + name + ": " + e.getMessage() + " (skipped)");
         return null;
      }
   }

   // The urls in a container document that carry the given word, in the order they appear.
   // The container escapes them, so they need unescaping before being fetched.
   private static List<String> urls(String xml, String carrying) {
      List<String> found = new ArrayList<String>();
      if (xml == null) return found;
      Matcher m = Pattern.compile("<Url>([^<]*" + carrying + "[^<]*)</Url>").matcher(xml);
      while (m.find()) found.add(m.group(1).replace("&amp;", "&"));
      return found;
   }

   private static String firstUrl(String xml, String carrying) {
      List<String> found = urls(xml, carrying);
      return found.isEmpty() ? null : found.get(0);
   }

   // One details url per kind of recording the page holds, so the fixtures differ from each
   // other: a movie carries a year and a star rating where an episode carries an episode
   // title, and one still recording carries neither a duration nor an offset.
   private static List<String> variedDetailUrls(String xml) {
      List<String> picked = new ArrayList<String>();
      Set<String> kinds = new LinkedHashSet<String>();
      if (xml == null) return picked;
      Matcher item = Pattern.compile("<Item>.*?</Item>", Pattern.DOTALL).matcher(xml);
      while (item.find()) {
         String block = item.group();
         String url = firstUrl(block, "TiVoVideoDetails");
         if (url == null) continue;
         Matcher id = Pattern.compile("<ProgramId>(..)").matcher(block);
         String kind = block.contains("<InProgress>Yes</InProgress>") ? "recording"
                     : id.find() ? id.group(1) : "other";
         if (kinds.add(kind)) picked.add(url);
      }
      return picked;
   }

   private static int count(String s, String sub) {
      int n = 0;
      for (int i = s.indexOf(sub); i >= 0; i = s.indexOf(sub, i + sub.length())) n++;
      return n;
   }

   // Minimal config.ini reader: returns the line following <key>.
   public static String readIniValue(String path, String key) throws Exception {
      try (BufferedReader in = new BufferedReader(new FileReader(path))) {
         String line;
         boolean found = false;
         while ((line = in.readLine()) != null) {
            if (found) return line.trim();
            if (line.trim().equals("<" + key + ">")) found = true;
         }
      }
      return null;
   }

   // The <TIVOS> block as name to address. FILES is in there too - it is how kmttg carries
   // the local video directory - and it names a path rather than a box, so it is left out.
   public static Map<String,String> readTivos(String path) throws Exception {
      Map<String,String> tivos = new LinkedHashMap<String,String>();
      try (BufferedReader in = new BufferedReader(new FileReader(path))) {
         String line;
         boolean inBlock = false;
         while ((line = in.readLine()) != null) {
            String t = line.trim();
            if (t.equals("<TIVOS>")) { inBlock = true; continue; }
            if (inBlock) {
               if (t.startsWith("<")) break;
               String[] parts = t.split("\\s+");
               if (parts.length < 2) continue;
               String name = parts[0], address = parts[parts.length - 1];
               if (name.equals("FILES") || address.contains("\\") || address.contains("/"))
                  continue;
               tivos.put(name, address);
            }
         }
      }
      return tivos;
   }

}
