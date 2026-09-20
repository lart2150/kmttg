package com.tivo.kmttg.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Hashtable;
import java.util.Stack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.main.jobData;

// The .txt sidecar is what pyTivo and the encoders read back, so the key names,
// their order and the rating codes are a published format rather than an
// internal detail. These drive createMetaFile from a TiVoVideoDetails xml on
// disk and read the result back - the download that normally fetches that xml
// is the only part that needs a tivo, and is left out.
public class CreateMetaTest {

   @TempDir
   Path dir;

   private String previousEntries;

   @BeforeEach
   public void saveConfig() {
      previousEntries = config.metadata_entries;
      config.metadata_entries = "";
   }

   @AfterEach
   public void restoreConfig() {
      config.metadata_entries = previousEntries;
   }

   @Test
   public void createMetaFileWritesThePyTivoKeysInTemplateOrder() throws IOException {
      jobData job = job(details(PROGRAM));
      job.episodeNumber = "3";
      job.displayMajorNumber = "11-1";
      job.callsign = "WTTWDT";
      job.seriesId = "SH0177883040";
      job.ProgramId = "EP0177883040";

      assertTrue(createMeta.createMetaFile(job, null));
      assertEquals(lines(
         "title : Patis Mexican Table",
         "seriesTitle : Patis Mexican Table",
         // the copyright notice is cut out of the middle, trailing space and all
         "description : Wonder is everywhere. ",
         "time : 2026-09-19T21:00:00Z",
         "mpaaRating : P3",
         "movieYear : 1984",
         // isEpisodic overrides the isEpisode the xml carries
         "isEpisode : true",
         "iso_duration : PT0H29M57S",
         "originalAirDate : 2026-09-18T00:00:00Z",
         "episodeTitle : Time Stops at the Valley of the Ducks",
         "isEpisodic : true",
         "showingBits : 4102",
         "starRating : x6",
         "tvRating : x4",
         "episodeNumber : 3",
         "displayMajorNumber : 11-1",
         "callsign : WTTWDT",
         "seriesId : SH0177883040",
         "programId : EP0177883040",
         "vActor : Jinich|Pati",
         "vActor : Smith|John",
         "vDirector : Kubrick|Stanley"), read(job.metaFile));
   }

   // TiVo emits both of these unescaped in titles and descriptions, which is not
   // valid xml - the parse has to survive it rather than losing the recording
   @Test
   public void aBareAmpersandInTheXmlIsRepairedBeforeParsing() throws IOException {
      for (String title : new String[] {"Tom & Jerry", "Tom &&amp; Jerry"}) {
         jobData job = job(details("<title>" + title + "</title>"));
         assertTrue(createMeta.createMetaFile(job, null), title);
         assertEquals(lines("title : Tom & Jerry", DURATION), read(job.metaFile), title);
      }
   }

   @Test
   public void theCopyrightNoticeIsStrippedFromTheDescription() throws IOException {
      jobData job = job(details("<description>A show. Copyright Rovi, Inc.</description>"));
      assertTrue(createMeta.createMetaFile(job, null));
      assertEquals(lines("description : A show. ", DURATION), read(job.metaFile));
   }

   @Test
   public void everyMpaaRatingBecomesItsPyTivoCode() throws IOException {
      String[][] cases = {
         {"G", "G1"}, {"PG", "P2"}, {"PG_13", "P3"}, {"R", "R4"},
         {"X", "X5"}, {"NC_17", "N6"}, {"NR", "N8"},
         // one the map has no answer for is written through untouched
         {"AO", "AO"}
      };
      for (String[] c : cases) {
         jobData job = job(details("<mpaaRating>" + c[0] + "</mpaaRating>"));
         assertTrue(createMeta.createMetaFile(job, null), c[0]);
         assertEquals(lines("mpaaRating : " + c[1], DURATION), read(job.metaFile), c[0]);
      }
   }

   // A programId with an underscore is kmttg's own unique-ified form, not
   // something pyTivo should be told about
   @Test
   public void aProgramIdCarryingTheUniqueSuffixIsNotWritten() throws IOException {
      jobData job = job(details("<title>Show</title>"));
      job.ProgramId = "EP0177883040_1600000000000";
      assertTrue(createMeta.createMetaFile(job, null));
      assertEquals(lines("title : Show", DURATION), read(job.metaFile));
   }

   @Test
   public void extraMetadataEntriesFromTheConfigAreAppended() throws IOException {
      config.metadata_entries = "colorCode : x4, partCount: 2";
      jobData job = job(details("<title>Show</title>"));
      assertTrue(createMeta.createMetaFile(job, null));
      assertEquals(lines("title : Show", DURATION, "colorCode : x4", "partCount : 2"), read(job.metaFile));
   }

   // A setting that is not name:value pairs is refused whole, so a typo drops
   // the extras rather than writing a broken line into the sidecar
   @Test
   public void anExtraMetadataSettingThatIsNotNameValuePairsIsDropped() throws IOException {
      for (String entries : new String[] {"colorCode", "colorCode : x4 : extra", "a : b, oops"}) {
         config.metadata_entries = entries;
         jobData job = job(details("<title>Show</title>"));
         assertTrue(createMeta.createMetaFile(job, null), entries);
         assertEquals(lines("title : Show", DURATION), read(job.metaFile), entries);
      }
   }

   @Test
   public void theDownloadedXmlAndCookieFileAreRemovedAfterwards() throws IOException {
      jobData job = job(details("<title>Show</title>"));
      Path cookie = dir.resolve("cookie.txt");
      Files.write(cookie, "session".getBytes(Charset.defaultCharset()));

      assertTrue(createMeta.createMetaFile(job, cookie.toString()));
      assertFalse(Files.exists(dir.resolve("details.xml")), "the details xml was left behind");
      assertFalse(Files.exists(cookie), "the cookie file was left behind");
   }

   @Test
   public void aSourceThatCannotBeParsedFailsWithoutLeavingAMetadataFile() throws IOException {
      for (String xml : new String[] {"not xml at all", "<showing><title>unclosed</showing>"}) {
         jobData job = job(xml);
         assertFalse(createMeta.createMetaFile(job, null), xml);
         assertFalse(Files.exists(Path.of(job.metaFile)), xml);
         assertFalse(Files.exists(dir.resolve("details.xml")), xml);
      }
      // ...and the same when the download never produced the xml at all
      jobData job = job(details("<title>Show</title>"));
      Files.delete(dir.resolve("details.xml"));
      assertFalse(createMeta.createMetaFile(job, null));
   }

   @Test
   public void readMetaFileCollectsRepeatedNamesIntoAStack() throws IOException {
      Path meta = write("read.txt", lines(
         "title : Show",
         "vActor : Jinich|Pati",
         "vActor : Smith|John",
         "vActor : Doe|Jane",
         "description : a : b"));
      Hashtable<String,Object> data = createMeta.readMetaFile(meta.toString());

      assertEquals("Show", data.get("title"));
      // the split takes the first " : " only, so a value may contain one
      assertEquals("a : b", data.get("description"));
      Stack<?> actors = (Stack<?>)data.get("vActor");
      assertEquals(3, actors.size());
      assertEquals("Jinich|Pati", actors.get(0));
      assertEquals("Doe|Jane", actors.get(2));
   }

   @Test
   public void readMetaFileNormalizesTheRatingsToTheMetadataStandard() throws IOException {
      Path meta = write("ratings.txt", lines("tvRating : x4", "mpaaRating : P3", "starRating : x6"));
      Hashtable<String,Object> data = createMeta.readMetaFile(meta.toString());
      assertEquals("TV-PG", data.get("tvRating"));
      assertEquals("PG-13", data.get("mpaaRating"));
      assertEquals("4", data.get("starRating"));
   }

   @Test
   public void readMetaFileReturnsNothingForAFileItCannotRead() {
      assertTrue(createMeta.readMetaFile(dir.resolve("absent.txt").toString()).isEmpty());
   }

   // The two halves have to agree: what createMetaFile writes is what the
   // encoders later read back through readMetaFile
   @Test
   public void aSidecarWrittenHereReadsBackThroughReadMetaFile() throws IOException {
      jobData job = job(details(PROGRAM));
      assertTrue(createMeta.createMetaFile(job, null));
      Hashtable<String,Object> data = createMeta.readMetaFile(job.metaFile);

      assertEquals("Patis Mexican Table", data.get("title"));
      assertEquals("Time Stops at the Valley of the Ducks", data.get("episodeTitle"));
      assertEquals("PT0H29M57S", data.get("iso_duration"));
      assertEquals("4102", data.get("showingBits"));
      // written as the x-prefixed TiVo codes, read back as the human form
      assertEquals("TV-PG", data.get("tvRating"));
      assertEquals("PG-13", data.get("mpaaRating"));
      assertEquals("4", data.get("starRating"));
      assertEquals(2, ((Stack<?>)data.get("vActor")).size());
   }

   @Test
   public void tvRatingsMapToTheirHumanForm() {
      assertEquals("TV-Y7", createMeta.tvRating2contentRating("TV-Y7"));
      assertEquals("TV-Y7", createMeta.tvRating2contentRating("x1"));
      assertEquals("TV-G", createMeta.tvRating2contentRating("tvg"));
      assertEquals("TV-14", createMeta.tvRating2contentRating("14"));
      assertEquals("TV-MA", createMeta.tvRating2contentRating("TV-MA"));
      assertEquals("Unrated", createMeta.tvRating2contentRating("NR"));
      assertEquals("PG-13", createMeta.tvRating2contentRating("PG-13"));
   }

   @Test
   public void mpaaRatingsMapToTheirHumanForm() {
      assertEquals("G", createMeta.mpaaRating2contentRating("G1"));
      assertEquals("PG-13", createMeta.mpaaRating2contentRating("pg13"));
      assertEquals("R", createMeta.mpaaRating2contentRating("R4"));
      assertEquals("NC-17", createMeta.mpaaRating2contentRating("N6"));
      assertEquals("Unrated", createMeta.mpaaRating2contentRating("unrated"));
      assertEquals("TV-14", createMeta.mpaaRating2contentRating("TV-14"));
   }

   @Test
   public void starRatingsMapToNumbers() {
      assertEquals("0.5", createMeta.starRating2numericStars("xPointFive"));
      assertEquals("2.5", createMeta.starRating2numericStars("TWOPOINTFIVE"));
      assertEquals("4", createMeta.starRating2numericStars("x6"));
      assertEquals("1", createMeta.starRating2numericStars("x0"));
      assertEquals("x9", createMeta.starRating2numericStars("x9"));
   }

   @Test
   public void isoDurationsBecomeSeconds() {
      assertEquals(1797, createMeta.jsonDurationFromIsoDuration("PT29M57S"));
      assertEquals(3600, createMeta.jsonDurationFromIsoDuration("PT1H0M0S"));
      assertEquals(-1, createMeta.jsonDurationFromIsoDuration("29M57S"));
      assertEquals(-1, createMeta.jsonDurationFromIsoDuration(""));
   }

   // The trailing Z is real: the xml time is GMT and comes out in local time,
   // which is what makes the [startTime] file name keyword match the guide
   @Test
   public void anExtendedTimeIsReadAsGmtAndPrintedLocally() {
      long gmt = Instant.parse("2013-02-28T18:15:23Z").toEpochMilli();
      assertEquals(new SimpleDateFormat("yyyy-MM-dd_HHmm").format(gmt),
         createMeta.printableDateFromExtendedTime("2013-02-28T18:15:23Z"));
      // an unparseable one falls back to the epoch rather than throwing
      assertEquals(new SimpleDateFormat("yyyy-MM-dd_HHmm").format(0L),
         createMeta.printableDateFromExtendedTime("not a date"));
   }

   @Test
   public void anExtendedTimeAlreadyInLocalTimeRoundTrips() {
      assertEquals("2013-02-28 18:15:23", createMeta.jsonDateFromExtendedLocalTime("2013-02-28T18:15:23Z"));
   }

   // The only route to the tivo in this class, and it is guarded: with nothing
   // to fetch or the metadata already in hand it must not reach for the network
   @Test
   public void extendedMetadataIsNotFetchedWhenThereIsNothingToAskFor() {
      Hashtable<String,String> entry = new Hashtable<String,String>();
      entry.put("title", "Show");
      createMeta.getExtendedMetadata("Bolt", entry, false);
      assertEquals(1, entry.size());

      entry.put("url_TiVoVideoDetails", "https://192.0.2.1/TiVoVideoDetails?id=1");
      entry.put("metadata", "acquired");
      createMeta.getExtendedMetadata("Bolt", entry, false);
      assertEquals(3, entry.size());
   }

   /* ---- helpers ---- */

   private static final String EOL = "\r\n";

   // recordedDuration is read from the whole document rather than from under
   // <showing>, so it lands in every sidecar these build, renamed
   private static final String DURATION = "iso_duration : PT0H29M57S";

   private static final String PROGRAM =
      "<time>2026-09-19T21:00:00Z</time>" +
      "<showingBits value=\"4102\"/>" +
      "<program>" +
      "<seriesTitle>Patis Mexican Table</seriesTitle>" +
      "<title>Patis Mexican Table</title>" +
      "<episodeTitle>Time Stops at the Valley of the Ducks</episodeTitle>" +
      "<description>Wonder is everywhere. Copyright Tribune Media Services, Inc.</description>" +
      "<isEpisode>false</isEpisode>" +
      "<isEpisodic>true</isEpisodic>" +
      "<originalAirDate>2026-09-18T00:00:00Z</originalAirDate>" +
      "<movieYear>1984</movieYear>" +
      "<mpaaRating>PG_13</mpaaRating>" +
      "<starRating value=\"6\"/>" +
      "<tvRating value=\"4\"/>" +
      // no whitespace between the entries: every child node of an array element
      // is taken as a value, blank text included
      "<vActor><element>Jinich|Pati</element><element>Smith|John</element></vActor>" +
      "<vDirector><element>Kubrick|Stanley</element></vDirector>" +
      "</program>";

   // recordedDuration sits outside <showing>, as it does in a real details xml
   private static String details(String showing) {
      return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
         "<TvBusEnvelope>" +
         "<recordedDuration>PT0H29M57S</recordedDuration>" +
         "<showing>" + showing + "</showing>" +
         "</TvBusEnvelope>";
   }

   private jobData job(String xml) throws IOException {
      jobData job = new jobData();
      job.metaTmpFile = write("details.xml", xml).toString();
      job.metaFile = dir.resolve("show.txt").toString();
      return job;
   }

   private Path write(String name, String content) throws IOException {
      Path p = dir.resolve(name);
      Files.write(p, content.getBytes("UTF-8"));
      return p;
   }

   // createMetaFile writes through a FileWriter, so read it back the same way
   private static String read(String file) throws IOException {
      return new String(Files.readAllBytes(Path.of(file)), Charset.defaultCharset());
   }

   private static String lines(String... lines) {
      StringBuilder sb = new StringBuilder();
      for (String line : lines)
         sb.append(line).append(EOL);
      return sb.toString();
   }
}
