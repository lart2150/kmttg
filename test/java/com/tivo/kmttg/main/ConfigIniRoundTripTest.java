package com.tivo.kmttg.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// config.ini is the only state kmttg carries between runs, and the two halves that
// move it - a 400 line parse chain and a 290 line save chain - are written key by
// key with nothing tying them together. A setting either half forgets is invisible
// until a user's preference quietly reverts, so these tests take settings out
// through save() and back in rather than testing either half on its own.
public class ConfigIniRoundTripTest {

   @TempDir Path dir;

   // Every ini key whose parse block is a single assignment, paired with the field
   // it lands in. Derived from parseIni, and the round trip walks the whole list so
   // a key that save() stops writing fails here by name.
   private static final String[][] SETTINGS = {
      {"MAK", "MAK"},
      {"tivoFileNameFormat", "tivoFileNameFormat"},
      {"FontSize", "FontSize"},
      {"lookAndFeel", "lookAndFeel"},
      {"tableColAutoSize", "tableColAutoSize"},
      {"httpserver_enable", "httpserver_enable"},
      {"httpserver_port", "httpserver_port"},
      {"httpserver_share_filter", "httpserver_share_filter"},
      {"RemoveTivoFile", "RemoveTivoFile"},
      {"RemoveComcutFiles", "RemoveComcutFiles"},
      {"RemoveComcutFiles_mpeg", "RemoveComcutFiles_mpeg"},
      {"RemoveMpegFile", "RemoveMpegFile"},
      {"QSFixBackupMpegFile", "QSFixBackupMpegFile"},
      {"UseAdscan", "UseAdscan"},
      {"VrdReview", "VrdReview"},
      {"comskip_review", "comskip_review"},
      {"VrdReview_noCuts", "VrdReview_noCuts"},
      {"VrdQsFilter", "VrdQsFilter"},
      {"VrdDecrypt", "VrdDecrypt"},
      {"VrdEncode", "VrdEncode"},
      {"VrdAllowMultiple", "VrdAllowMultiple"},
      {"VrdCombineCutEncode", "VrdCombineCutEncode"},
      {"VrdQsfixMpeg2ps", "VrdQsfixMpeg2ps"},
      {"VrdOneAtATime", "VrdOneAtATime"},
      {"HideProtectedFiles", "HideProtectedFiles"},
      {"TiVoSort", "TiVoSort"},
      {"OverwriteFiles", "OverwriteFiles"},
      {"DeleteFailedDownloads", "DeleteFailedDownloads"},
      {"rpcnpl", "rpcnpl"},
      {"combine_download_decrypt", "combine_download_decrypt"},
      {"single_download", "single_download"},
      {"persistQueue", "persistQueue"},
      {"outputDir", "outputDir"},
      {"mpegDir", "mpegDir"},
      {"qsfixDir", "qsfixDir"},
      {"mpegCutDir", "mpegCutDir"},
      {"encodeDir", "encodeDir"},
      {"tivodecode", "tivodecode"},
      {"DsdDecrypt", "DsdDecrypt"},
      {"tivolibreDecrypt", "tivolibreDecrypt"},
      {"tivolibreCompat", "tivolibreCompat"},
      {"dsd", "dsd"},
      {"ffmpeg", "ffmpeg"},
      {"mediainfo", "mediainfo"},
      {"mencoder", "mencoder"},
      {"handbrake", "handbrake"},
      {"comskip", "comskip"},
      {"AtomicParsley", "AtomicParsley"},
      {"comskipIni", "comskipIni"},
      {"MaxJobs", "MaxJobs"},
      {"MinChanDigits", "MinChanDigits"},
      {"VRDexe", "VRDexe"},
      {"t2extract", "t2extract"},
      {"t2extract_args", "t2extract_args"},
      {"ccextractor", "ccextractor"},
      {"custom", "customCommand"},
      {"web_query", "web_query"},
      {"web_browser", "web_browser"},
      {"tivo_username", "tivo_username"},
      {"tivo_password", "tivo_password"},
      {"tivo_domain_token", "tivo_domain_token"},
      {"tivo_domain_token_expires", "tivo_domain_token_expires"},
      {"metadata_files", "metadata_files"},
      {"metadata_entries", "metadata_entries"},
      {"CheckDiskSpace", "CheckDiskSpace"},
      {"LowSpaceSize", "LowSpaceSize"},
      {"CheckBeacon", "CheckBeacon"},
      {"UseOldBeacon", "UseOldBeacon"},
      {"TivoWebPlusDelete", "TivoWebPlusDelete"},
      {"rpcOld", "rpcOld"},
      {"cpu_cores", "cpu_cores"},
      {"download_tries", "download_tries"},
      {"download_retry_delay", "download_retry_delay"},
      {"download_delay", "download_delay"},
      {"autoskip_enabled", "autoskip_enabled"},
      {"autoskip_import", "autoskip_import"},
      {"autoskip_cutonly", "autoskip_cutonly"},
      {"autoskip_save_skipmode", "autoskip_save_skipmode"},
      {"autoskip_fetch_skipmode", "autoskip_fetch_skipmode"},
      {"autoskip_stream_anchor", "autoskip_stream_anchor"},
      {"autoskip_prune", "autoskip_prune"},
      {"autoskip_batch_standby", "autoskip_batch_standby"},
      {"autoskip_indicate_skip", "autoskip_indicate_skip"},
      {"autoskip_chan_off", "autoskip_chan_off"},
      {"autoskip_chan_on", "autoskip_chan_on"},
      {"autoskip_jumpToEnd", "autoskip_jumpToEnd"},
      {"autoskip_padding_start", "autoskip_padding_start"},
      {"autoskip_padding_stop", "autoskip_padding_stop"},
      {"download_time_estimate", "download_time_estimate"},
      {"download_check_length", "download_check_length"},
      {"autoLogSizeMB", "autoLogSizeMB"},
      {"npl_when_started", "npl_when_started"},
      {"showHistoryInTable", "showHistoryInTable"},
   };

   private static Method parseIni;
   private List<Object[]> statics;

   // config carries ~200 mutable statics that the rest of the suite reads, and
   // parseIni writes straight into them, so the whole class is snapshotted and put
   // back instead of naming the fields any one test happens to touch.
   @BeforeEach
   void snapshotConfigStatics() throws Exception {
      statics = new ArrayList<Object[]>();
      for (Field f : config.class.getDeclaredFields()) {
         if (! Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) continue;
         f.setAccessible(true);
         Object value = f.get(null);
         Object contents = null;
         if (value instanceof Map)
            contents = new LinkedHashMap<Object,Object>((Map<?,?>)value);
         else if (value instanceof Collection)
            contents = new ArrayList<Object>((Collection<?>)value);
         statics.add(new Object[]{f, value, contents});
      }
   }

   @AfterEach
   @SuppressWarnings("unchecked")
   void restoreConfigStatics() throws Exception {
      for (Object[] row : statics) {
         Field f = (Field)row[0];
         f.set(null, row[1]);
         // The collections are mutated in place, so the reference alone is not enough
         if (row[2] instanceof Map) {
            Map<Object,Object> m = (Map<Object,Object>)row[1];
            m.clear();
            m.putAll((Map<Object,Object>)row[2]);
         } else if (row[2] instanceof Collection) {
            Collection<Object> c = (Collection<Object>)row[1];
            c.clear();
            c.addAll((Collection<Object>)row[2]);
         }
      }
   }

   // The public entry point, config.parse(), cannot be called here: defineDefaults()
   // rederives programDir from the jar location, empties the user's kmttg_temp dir,
   // reads ~/.tivodecode_mak, probes the filesystem for installed tools and can start
   // a beacon listener. parseIni on its own is the part that reads the file.
   private static boolean parse(Path ini) throws Exception {
      if (parseIni == null) {
         parseIni = config.class.getDeclaredMethod("parseIni", String.class);
         parseIni.setAccessible(true);
      }
      return ((Boolean)parseIni.invoke(null, ini.toString())).booleanValue();
   }

   private Path write(String name, String content) throws Exception {
      Path p = dir.resolve(name);
      Files.write(p, content.getBytes(StandardCharsets.UTF_8));
      return p;
   }

   private static String entry(String key, Object value) {
      return "<" + key + ">\n" + value + "\n\n";
   }

   private static Field field(String name) throws Exception {
      Field f = config.class.getDeclaredField(name);
      f.setAccessible(true);
      return f;
   }

   // A value distinct from both the default and every other setting's, so a key
   // wired to the wrong field shows up as a mismatch rather than a coincidence
   private static Object valueFor(Field f, int index) {
      Class<?> type = f.getType();
      if (type == int.class)     return Integer.valueOf(index + 3);
      if (type == long.class)    return Long.valueOf(index + 1000000L);
      if (type == boolean.class) return Boolean.TRUE;
      return "v" + index;
   }

   private static void clobber(Field f) throws Exception {
      Class<?> type = f.getType();
      if (type == int.class)          f.setInt(null, -99);
      else if (type == long.class)    f.setLong(null, -99L);
      else if (type == boolean.class) f.setBoolean(null, false);
      else                            f.set(null, "clobbered");
   }

   @Test
   void everySettingSurvivesSaveAndReparse() throws Exception {
      StringBuilder sb = new StringBuilder("# kmttg config.ini file\n");
      Object[] expected = new Object[SETTINGS.length];
      for (int i = 0; i < SETTINGS.length; i++) {
         Field f = field(SETTINGS[i][1]);
         expected[i] = valueFor(f, i);
         sb.append(entry(SETTINGS[i][0], expected[i]));
      }
      assertTrue(parse(write("config.ini", sb.toString())));

      for (int i = 0; i < SETTINGS.length; i++)
         assertEquals(expected[i], field(SETTINGS[i][1]).get(null), SETTINGS[i][0]);

      // Out through save() and back in, with the fields wiped in between so a
      // setting save() never writes cannot pass by simply still being in memory
      config.configIni = dir.resolve("saved.ini").toString();
      assertTrue(config.save());
      for (String[] s : SETTINGS)
         clobber(field(s[1]));
      assertTrue(parse(dir.resolve("saved.ini")));

      for (int i = 0; i < SETTINGS.length; i++)
         assertEquals(expected[i], field(SETTINGS[i][1]).get(null),
            SETTINGS[i][0] + " did not survive save and reparse");
   }

   @Test
   void tivoNamesKeepTheirSpacesThroughTheFixedWidthSaveFormat() throws Exception {
      // save() writes the TIVOS block as two %-20s columns and parse takes the last
      // whitespace token as the address, so a TiVo named "Living Room" has to
      // reassemble from the leading tokens
      Path files = Files.createDirectory(dir.resolve("videos"));
      config.outputDir = dir.toString();
      String ini = "<TIVOS>\n" +
                   "Living Room          192.168.1.5\n" +
                   "Bolt                 192.168.1.6\n" +
                   "FILES                " + files + "\n\n";
      assertTrue(parse(write("config.ini", ini)));
      assertEquals("192.168.1.5", config.TIVOS.get("Living Room"));
      assertEquals("192.168.1.6", config.TIVOS.get("Bolt"));
      assertEquals(files.toString(), config.TIVOS.get("FILES"));

      config.configIni = dir.resolve("saved.ini").toString();
      assertTrue(config.save());
      config.TIVOS.clear();
      assertTrue(parse(dir.resolve("saved.ini")));
      assertEquals("192.168.1.5", config.TIVOS.get("Living Room"));
      assertEquals("192.168.1.6", config.TIVOS.get("Bolt"));
      assertEquals(files.toString(), config.TIVOS.get("FILES"));
   }

   @Test
   void aFilesDirectoryThatNoLongerExistsFallsBackToTheOutputDir() throws Exception {
      config.outputDir = dir.toString();
      String ini = "<TIVOS>\nFILES                " + dir.resolve("gone") + "\n\n";
      assertTrue(parse(write("config.ini", ini)));
      assertEquals(dir.toString(), config.TIVOS.get("FILES"));
   }

   @Test
   void perTivoSettingsRoundTripUnderTheirOwnKeys() throws Exception {
      String ini = entry("tsn_Bolt", "8460001902767C7") +
                   entry("enableRpc_Bolt", "1") +
                   entry("limit_npl_Bolt", "50") +
                   entry("wan_Bolt_rpc", "1234");
      assertTrue(parse(write("config.ini", ini)));
      assertEquals("8460001902767C7", config.getTsn("Bolt"));
      assertTrue(config.rpcEnabled("Bolt"));
      assertEquals(50, config.getLimitNplSetting("Bolt"));
      assertEquals("1234", config.WAN.get("wan_Bolt_rpc"));

      config.configIni = dir.resolve("saved.ini").toString();
      assertTrue(config.save());
      config.TSN.clear();
      config.enableRpc.clear();
      config.limit_npl_fetches.clear();
      config.WAN.clear();
      assertTrue(parse(dir.resolve("saved.ini")));
      assertEquals("8460001902767C7", config.getTsn("Bolt"));
      assertTrue(config.rpcEnabled("Bolt"));
      assertEquals(50, config.getLimitNplSetting("Bolt"));
      assertEquals("1234", config.WAN.get("wan_Bolt_rpc"));
   }

   @Test
   void aLegacyIpadWanKeyIsMigratedToRpcOnTheWayIn() throws Exception {
      // kmttg renamed its remote access setting; an old config.ini still says ipad
      assertTrue(parse(write("config.ini", entry("wan_Bolt_ipad", "1234"))));
      assertEquals("1234", config.WAN.get("wan_Bolt_rpc"));
      assertFalse(config.WAN.containsKey("wan_Bolt_ipad"));
   }

   @Test
   void aLegacyIpadDeleteKeyIsSavedBackAsRpcDelete() throws Exception {
      assertTrue(parse(write("config.ini", entry("iPadDelete", "1"))));
      assertEquals(1, config.rpcDelete);

      config.configIni = dir.resolve("saved.ini").toString();
      assertTrue(config.save());
      String saved = new String(Files.readAllBytes(dir.resolve("saved.ini")), StandardCharsets.UTF_8);
      assertTrue(saved.contains("<rpcDelete>\n1\n"));
      assertFalse(saved.contains("<iPadDelete>"));
   }

   @Test
   void webSharesRoundTripByName() throws Exception {
      String ini = "<SHARES>\nmovies=" + dir.resolve("movies") + "\ntv=" + dir.resolve("tv") + "\n\n";
      assertTrue(parse(write("config.ini", ini)));
      assertEquals(dir.resolve("movies").toString(), config.httpserver_shares.get("movies"));

      config.configIni = dir.resolve("saved.ini").toString();
      assertTrue(config.save());
      config.httpserver_shares.clear();
      assertTrue(parse(dir.resolve("saved.ini")));
      assertEquals(dir.resolve("movies").toString(), config.httpserver_shares.get("movies"));
      assertEquals(dir.resolve("tv").toString(), config.httpserver_shares.get("tv"));
   }

   @Test
   void diskSpaceEntriesRoundTripAsFloats() throws Exception {
      assertTrue(parse(write("config.ini", "<diskSpace>\nBolt=12.5\nRoamio=3.0\n\n")));
      assertEquals(12.5f, config.diskSpace.get("Bolt").floatValue());

      config.configIni = dir.resolve("saved.ini").toString();
      assertTrue(config.save());
      config.diskSpace.clear();
      assertTrue(parse(dir.resolve("saved.ini")));
      assertEquals(12.5f, config.diskSpace.get("Bolt").floatValue());
      assertEquals(3.0f, config.diskSpace.get("Roamio").floatValue());
   }

   @Test
   void anUnparseableDiskSpaceValueIsSkippedRatherThanFatal() throws Exception {
      assertTrue(parse(write("config.ini", "<diskSpace>\nBolt=huge\nRoamio=3.0\n\n")));
      assertFalse(config.diskSpace.containsKey("Bolt"));
      assertEquals(3.0f, config.diskSpace.get("Roamio").floatValue());
   }

   @Test
   void qsfixDirFollowsMpegDirWhenTheFileOmitsIt() throws Exception {
      // The one setting that is not independent: without its own key it tracks mpegDir
      assertTrue(parse(write("config.ini", entry("mpegDir", "D:\\mpeg"))));
      assertEquals("D:\\mpeg", config.qsfixDir);

      assertTrue(parse(write("two.ini", entry("mpegDir", "D:\\mpeg") + entry("qsfixDir", "D:\\qs"))));
      assertEquals("D:\\qs", config.qsfixDir);
   }

   @Test
   void commentsAndBlankLinesAndStrayWhitespaceAreIgnored() throws Exception {
      String ini = "# a comment\n\n   \n<MAK>\n   1234567890\n\n# trailing note\n";
      assertTrue(parse(write("config.ini", ini)));
      assertEquals("1234567890", config.MAK);
   }

   @Test
   void aMissingConfigFileIsReportedRatherThanThrowing() throws Exception {
      assertFalse(parse(dir.resolve("absent.ini")));
   }

   @Test
   void oneUnparseableValueCostsNothingButItsOwnSetting() throws Exception {
      // A hand edited config.ini used to take kmttg down on startup: parseIni caught
      // IOException only, so the NumberFormatException went all the way out
      config.FontSize = 11;
      String ini = entry("MAK", "1234567890") + entry("FontSize", "large") + entry("MaxJobs", "4");
      assertTrue(parse(write("config.ini", ini)));
      assertEquals(11, config.FontSize);
      assertEquals("1234567890", config.MAK);
      assertEquals(4, config.MaxJobs, "settings after the bad line still load");
   }

   @Test
   void aShareLineWithoutASeparatorIsSkippedRatherThanFatal() throws Exception {
      String ini = "<SHARES>\nmovies\ntv=" + dir.resolve("tv") + "\n\n";
      assertTrue(parse(write("config.ini", ini)));
      assertFalse(config.httpserver_shares.containsKey("movies"));
      assertEquals(dir.resolve("tv").toString(), config.httpserver_shares.get("tv"));
   }

   @Test
   void theConfigFileIsClosedEvenWhenALineFails() throws Exception {
      Path ini = write("config.ini", entry("FontSize", "large"));
      assertTrue(parse(ini));
      // Windows refuses this while a reader is still open on the file
      Files.delete(ini);
   }

   @Test
   void theHttpServerCacheDirectoryIsCreatedWhenItIsMissing() throws Exception {
      Path cache = dir.resolve("web").resolve("cache");
      assertTrue(parse(write("config.ini", entry("httpserver_cache", cache))));
      assertTrue(Files.isDirectory(cache));
   }
}
