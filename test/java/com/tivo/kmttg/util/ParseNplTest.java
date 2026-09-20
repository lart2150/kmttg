package com.tivo.kmttg.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.SimpleDateFormat;
import java.util.Hashtable;
import java.util.Stack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONConverter;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Fixtures;

// The Now Playing List, both ways it arrives. parseString scrapes the TiVo's
// NPL XML by splitting the whole document on ">" and walking the pieces, so it
// is sensitive to element order and silently ignores anything it wasn't told
// about; uniquify then folds the repeated listings of one recording back
// together. NplEntryTest covers the copy protection side of rpcToHashEntry -
// the rest of that mapping is pinned here.
public class ParseNplTest {

   private static final String DOWNLOAD_URL =
      "http://192.168.1.10:80/download/Law%20And%20Order.TiVo?Container=%2FNowPlaying&amp;id=1234";

   // 0x5A2B3C4D seconds, and the same instant 3149 seconds earlier
   private static final long CAPTURE_GMT = 1512782925000L;
   private static final long SHOWING_GMT = 1512779776000L;

   private static final String FULL_ITEM =
      "<Item><Details>"
      + "<Title>Law &amp; Order</Title>"
      + "<ContentType>video/x-tivo-mpeg</ContentType>"
      + "<SourceSize>3221225472</SourceSize>"
      + "<Duration>1800000</Duration>"
      + "<CaptureDate>0x5A2B3C4D</CaptureDate>"
      + "<EpisodeTitle>Subterranean Homeboy Blues</EpisodeTitle>"
      + "<EpisodeNumber>2</EpisodeNumber>"
      + "<Description>Two men are shot on a subway. Copyright Tribune Media Services, Inc.</Description>"
      + "<SourceChannel>10</SourceChannel>"
      + "<SourceStation>NBC</SourceStation>"
      + "<HighDefinition>Yes</HighDefinition>"
      + "<ProgramId>EP0123456789</ProgramId>"
      + "<SeriesId>SH0123456</SeriesId>"
      + "<CopyProtected>No</CopyProtected>"
      + "<InProgress>No</InProgress>"
      + "<ByteOffset>512</ByteOffset>"
      + "</Details><Links><Content>"
      + "<Url>" + DOWNLOAD_URL + "</Url>"
      + "</Content><CustomIcon>"
      + "<Url>urn:tivo:image:expires-soon-recording</Url>"
      + "</CustomIcon><TiVoVideoDetails>"
      + "<Url>https://192.168.1.10:443/TiVoVideoDetails?id=1234</Url>"
      + "</TiVoVideoDetails></Links></Item>";

   // Everything optional left out: no episode, no duration, no channel, no
   // ProgramId and no date
   private static final String BARE_ITEM =
      "<Item><Details>"
      + "<Title>Short Thing</Title>"
      + "<SourceSize>1073741824</SourceSize>"
      + "</Details><Links><Content>"
      + "<Url>http://192.168.1.10:80/download/Short%20Thing.TiVo?Container=%2FNowPlaying&amp;id=99</Url>"
      + "</Content></Links></Item>";

   // A grouped series: no recording of its own, and its Url is a container
   // query rather than a download
   private static final String FOLDER_ITEM =
      "<Item><Details>"
      + "<Title>Cheers</Title>"
      + "<ContentType>x-tivo-container/tivo-videos</ContentType>"
      + "<SourceFormat>x-tivo-container/folder</SourceFormat>"
      + "<TotalItems>3</TotalItems>"
      + "</Details><Links><Content>"
      + "<Url>/TiVoConnect?Command=QueryContainer&amp;Container=%2FNowPlaying%2FCheers</Url>"
      + "</Content></Links></Item>";

   private int savedMinChanDigits;

   @BeforeEach
   void pinGlobals() {
      savedMinChanDigits = config.MinChanDigits;
      config.MinChanDigits = 1;
   }

   @AfterEach
   void restoreGlobals() {
      config.MinChanDigits = savedMinChanDigits;
   }

   // parseString reads only the FIRST line of the document, so the whole thing
   // has to be on one line for any of it to be seen at all.
   private static String npl(int totalItems, int itemCount, String... items) {
      StringBuilder sb = new StringBuilder();
      sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?><TiVoContainer><Details>");
      sb.append("<Title>Now Playing</Title>");
      sb.append("<TotalItems>").append(totalItems).append("</TotalItems>");
      sb.append("</Details><ItemStart>0</ItemStart>");
      sb.append("<ItemCount>").append(itemCount).append("</ItemCount>");
      for (String item : items)
         sb.append(item);
      sb.append("</TiVoContainer>");
      return sb.toString();
   }

   // parseNPL formats through the default zone and locale, so the expectation
   // has to be built the same way rather than written out as a string
   private static String date(long gmt) {
      return new SimpleDateFormat("E MM/dd/yyyy").format(gmt);
   }

   private static String dateLong(long gmt) {
      return new SimpleDateFormat("E MM/dd/yyyy hh:mm aa").format(gmt);
   }

   private static Hashtable<String,String> parseOne(String item) {
      Stack<Hashtable<String,String>> entries = new Stack<Hashtable<String,String>>();
      assertNotNull(parseNPL.parseString(npl(1, 1, item), "Bolt", entries), "parse failed");
      assertEquals(1, entries.size());
      return entries.get(0);
   }

   // ---- parseString -------------------------------------------------------

   @Test
   void aNormalRecordingBecomesOneEntry() {
      Hashtable<String,String> e = parseOne(FULL_ITEM);
      assertEquals("Bolt", e.get("tivoName"));
      // The episode title is folded into title at flush time; titleOnly keeps
      // the series name, and &amp; is decoded on the way through
      assertEquals("Law & Order - Subterranean Homeboy Blues", e.get("title"));
      assertEquals("Law & Order", e.get("titleOnly"));
      assertEquals("Subterranean Homeboy Blues", e.get("episodeTitle"));
      assertEquals("002", e.get("EpisodeNumber"));
      assertEquals("3221225472", e.get("size"));
      assertEquals("3.00 GB", e.get("sizeGB"));
      assertEquals("1800000", e.get("duration"));
      assertEquals("10", e.get("channelNum"));
      assertEquals("10", e.get("sortableChannel"));
      assertEquals("NBC", e.get("channel"));
      assertEquals("Yes", e.get("HD"));
      assertEquals("EP0123456789", e.get("ProgramId"));
      assertEquals("SH0123456", e.get("SeriesId"));
      assertEquals("512", e.get("ByteOffset"));
      assertEquals("EP0123456789_" + CAPTURE_GMT, e.get("ProgramId_unique"));
   }

   @Test
   void theTaglineAndTheUrlsAreCleanedUp() {
      Hashtable<String,String> e = parseOne(FULL_ITEM);
      // The rating service copyright is stripped out of the description
      assertEquals("Two men are shot on a subway. ", e.get("description"));
      // &amp; is turned back into & so the download URL is usable
      assertEquals("http://192.168.1.10:80/download/Law%20And%20Order.TiVo?Container=%2FNowPlaying&id=1234",
         e.get("url"));
      assertEquals("https://192.168.1.10:443/TiVoVideoDetails?id=1234", e.get("url_TiVoVideoDetails"));
      // The icon URL is not a download URL, it only names the expiration image
      assertEquals("expires-soon-recording", e.get("ExpirationImage"));
      assertFalse(e.containsKey("kuid"));
   }

   @Test
   void theXmlCopyFlagsAreStoredAsTheirLiteralValue() {
      // Unlike the RPC path, which only puts the key there when the answer is
      // no, the XML path stores whatever the TiVo said
      Hashtable<String,String> e = parseOne(FULL_ITEM);
      assertEquals("No", e.get("CopyProtected"));
      assertEquals("No", e.get("InProgress"));
   }

   @Test
   void aCaptureDateIsReadAsHexSecondsAndFormattedTwice() {
      Hashtable<String,String> e = parseOne(FULL_ITEM);
      assertEquals("0x5A2B3C4D", e.get("gmt_hex"));
      assertEquals("" + CAPTURE_GMT, e.get("gmt"));
      assertEquals(date(CAPTURE_GMT), e.get("date"));
      assertEquals(dateLong(CAPTURE_GMT), e.get("date_long"));
   }

   @Test
   void aShowingStartTimeOverridesTheCaptureDate() {
      // Introduced in TiVo 20.2.2 and relied on to come after CaptureDate
      Hashtable<String,String> e = parseOne(
         "<Item><Details><Title>Nova</Title>"
         + "<CaptureDate>0x5A2B3C4D</CaptureDate>"
         + "<ShowingStartTime>0x5A2B3000</ShowingStartTime>"
         + "</Details></Item>");
      assertEquals("0x5A2B3000", e.get("gmt_hex"));
      assertEquals("" + SHOWING_GMT, e.get("gmt"));
      assertEquals(date(SHOWING_GMT), e.get("date"));
   }

   @Test
   void anItemMissingItsOptionalFieldsGetsAFakeProgramId() {
      Hashtable<String,String> e = parseOne(BARE_ITEM);
      assertEquals("Short Thing", e.get("title"));
      assertEquals("Short Thing", e.get("titleOnly"));
      assertFalse(e.containsKey("episodeTitle"));
      assertFalse(e.containsKey("duration"));
      assertFalse(e.containsKey("channel"));
      assertFalse(e.containsKey("channelNum"));
      assertFalse(e.containsKey("HD"));
      assertEquals("1.00 GB", e.get("sizeGB"));
      // With no ProgramId the id comes out of the download URL plus the size,
      // and with no date the unique id trails a literal "null"
      assertEquals("99_1073741824", e.get("ProgramId"));
      assertEquals("99_1073741824_null", e.get("ProgramId_unique"));
   }

   @Test
   void aFolderItemYieldsNothingButItsTitle() {
      Hashtable<String,String> e = parseOne(FOLDER_ITEM);
      assertEquals("Cheers", e.get("title"));
      assertEquals("Bolt", e.get("tivoName"));
      // A container query is not a download URL, so there is no url to make an
      // id out of either
      assertFalse(e.containsKey("url"));
      assertFalse(e.containsKey("ProgramId"));
      assertEquals("null_null", e.get("ProgramId_unique"));
   }

   @Test
   void episodeNumbersArePaddedToThreeDigitsButNeverTruncated() {
      Stack<Hashtable<String,String>> entries = new Stack<Hashtable<String,String>>();
      String doc = npl(2, 2,
         "<Item><Details><Title>A</Title><EpisodeNumber>2</EpisodeNumber></Details></Item>",
         "<Item><Details><Title>B</Title><EpisodeNumber>1012</EpisodeNumber></Details></Item>");
      assertNotNull(parseNPL.parseString(doc, "Bolt", entries));
      assertEquals("002", entries.get(0).get("EpisodeNumber"));
      assertEquals("1012", entries.get(1).get("EpisodeNumber"));
   }

   @Test
   void oneItemDoesNotLeakIntoTheNext() {
      Stack<Hashtable<String,String>> entries = new Stack<Hashtable<String,String>>();
      assertNotNull(parseNPL.parseString(npl(2, 2, FULL_ITEM, BARE_ITEM), "Bolt", entries));
      assertEquals(2, entries.size());
      Hashtable<String,String> second = entries.get(1);
      assertEquals("Short Thing", second.get("title"));
      assertFalse(second.containsKey("episodeTitle"));
      assertFalse(second.containsKey("HD"));
      assertFalse(second.containsKey("gmt"));
      assertFalse(second.containsKey("ExpirationImage"));
   }

   @Test
   void theHeaderCountsComeBackToTheCaller() {
      Stack<Hashtable<String,String>> entries = new Stack<Hashtable<String,String>>();
      Hashtable<String,Integer> r = parseNPL.parseString(npl(37, 2, FULL_ITEM, BARE_ITEM), "Bolt", entries);
      assertEquals(2, r.get("ItemCount").intValue());
      assertEquals(37, r.get("TotalItems").intValue());
      assertEquals(2, entries.size());
   }

   @Test
   void aDocumentWithNoCountsParsesNoItemsAtAll() {
      // Item scanning starts where the header scan stopped, and with no
      // ItemCount to stop at that is the end of the document
      Stack<Hashtable<String,String>> entries = new Stack<Hashtable<String,String>>();
      Hashtable<String,Integer> r = parseNPL.parseString(
         "<TiVoContainer>" + FULL_ITEM + "</TiVoContainer>", "Bolt", entries);
      assertEquals(0, r.get("ItemCount").intValue());
      assertEquals(0, r.get("TotalItems").intValue());
      assertTrue(entries.isEmpty());
   }

   @Test
   void aMalformedDocumentReturnsNull() {
      Stack<Hashtable<String,String>> entries = new Stack<Hashtable<String,String>>();
      assertNull(parseNPL.parseString("", "Bolt", entries), "empty body");
      // Cut off mid-header, so the value the header scan wants isn't there
      assertNull(parseNPL.parseString("<TiVoContainer><Details><TotalItems", "Bolt", entries), "truncated");
      assertTrue(entries.isEmpty());
   }

   @Test
   void aBadNumberAbandonsTheParseButKeepsWhatWasAlreadyAdded() {
      // Entries are flushed as the next <Item> is reached, so a failure part
      // way through leaves the caller's stack half filled and a null result
      Stack<Hashtable<String,String>> entries = new Stack<Hashtable<String,String>>();
      String doc = npl(2, 2, FULL_ITEM,
         "<Item><Details><Title>B</Title><EpisodeNumber>N/A</EpisodeNumber></Details></Item>");
      assertNull(parseNPL.parseString(doc, "Bolt", entries));
      assertEquals(1, entries.size());
      assertEquals("Law & Order - Subterranean Homeboy Blues", entries.get(0).get("title"));
   }

   @Test
   void channelNumbersArePaddedToTheConfiguredWidth() {
      config.MinChanDigits = 4;
      Stack<Hashtable<String,String>> entries = new Stack<Hashtable<String,String>>();
      String doc = npl(2, 2,
         "<Item><Details><Title>A</Title><SourceChannel>10</SourceChannel></Details></Item>",
         "<Item><Details><Title>B</Title><SourceChannel>10-1</SourceChannel></Details></Item>");
      assertNotNull(parseNPL.parseString(doc, "Bolt", entries));
      assertEquals("0010", entries.get(0).get("channelNum"));
      assertEquals("10", entries.get(0).get("sortableChannel"));
      // On an OTA channel the padding is measured against the major number
      // only, and the sortable form gets a 3 digit minor
      assertEquals("0010-1", entries.get(1).get("channelNum"));
      assertEquals("10.001", entries.get(1).get("sortableChannel"));
   }

   // ---- uniquify ----------------------------------------------------------

   private static Hashtable<String,String> listing(String id, String duration) {
      Hashtable<String,String> h = new Hashtable<String,String>();
      h.put("ProgramId_unique", id);
      if (duration != null)
         h.put("duration", duration);
      return h;
   }

   @Test
   void aSingleShowIsKeptAndRememberedUnderBothKeys() {
      Stack<Hashtable<String,String>> entries = new Stack<Hashtable<String,String>>();
      entries.add(listing("EP1_100", "1800000"));
      Hashtable<String,Integer> unique = new Hashtable<String,Integer>();
      Stack<Hashtable<String,String>> kept = parseNPL.uniquify(entries, unique);
      assertEquals(1, kept.size());
      assertEquals(2, unique.size());
      assertTrue(unique.containsKey("EP1_100"));
      assertTrue(unique.containsKey("EP1_100_1800000"));
   }

   @Test
   void aSecondListingOfTheSameRecordingIsDropped() {
      Stack<Hashtable<String,String>> entries = new Stack<Hashtable<String,String>>();
      entries.add(listing("EP1_100", "1800000"));
      entries.add(listing("EP1_100", "1800000"));
      // A repeat with no duration at all can never be told apart, so it goes too
      entries.add(listing("EP1_100", null));
      Stack<Hashtable<String,String>> kept = parseNPL.uniquify(entries, new Hashtable<String,Integer>());
      assertEquals(1, kept.size());
      assertSame(entries.get(0), kept.get(0));
   }

   @Test
   void aPartialRecordingOfTheSameShowSurvives() {
      // Partials carry the same id but a shorter duration, and both belong in
      // the list
      Stack<Hashtable<String,String>> entries = new Stack<Hashtable<String,String>>();
      entries.add(listing("EP1_100", "1800000"));
      entries.add(listing("EP1_100", "600000"));
      Hashtable<String,Integer> unique = new Hashtable<String,Integer>();
      Stack<Hashtable<String,String>> kept = parseNPL.uniquify(entries, unique);
      assertEquals(2, kept.size());
      assertTrue(unique.containsKey("EP1_100_600000"));
      assertEquals(3, unique.size());
   }

   @Test
   void episodesOfOneSeriesAllSurviveAndTheInputIsLeftAlone() {
      Stack<Hashtable<String,String>> entries = new Stack<Hashtable<String,String>>();
      entries.add(listing("EP1_100", "1800000"));
      entries.add(listing("EP1_200", "1800000"));
      entries.add(listing("EP1_300", "1800000"));
      Stack<Hashtable<String,String>> kept = parseNPL.uniquify(entries, new Hashtable<String,Integer>());
      assertEquals(3, kept.size());
      assertEquals("EP1_200", kept.get(1).get("ProgramId_unique"));
      // The nulling out inside uniquify is local: the caller's stack is intact
      assertEquals(3, entries.size());
   }

   // ---- rpcToHashEntry ----------------------------------------------------

   private static JSONObject recording(int i) throws Exception {
      JSONArray myshows = Fixtures.load("myshows.json");
      return myshows.getJSONObject(i).getJSONArray("recording").getJSONObject(0);
   }

   @Test
   void aCapturedEpisodeMapsOntoTheSameKeysTheXmlUses() throws Exception {
      JSONObject json = recording(0);
      Hashtable<String,String> e = parseNPL.rpcToHashEntry("Bolt", json);
      assertEquals("Pati's Mexican Table", e.get("titleOnly"));
      assertEquals("Pati's Mexican Table - Time Stops at the Valley of the Ducks", e.get("title"));
      assertEquals("Time Stops at the Valley of the Ducks", e.get("episodeTitle"));
      assertEquals("WTTWDT", e.get("channel"));
      assertEquals("11-1", e.get("channelNum"));
      assertEquals("Yes", e.get("HD"));
      // size arrives in KB
      assertEquals("" + 1484800L * 1024, e.get("size"));
      assertEquals("1.42 GB", e.get("sizeGB"));
      assertEquals("15", e.get("season"));
      assertEquals("03", e.get("episode"));
      assertEquals("1503", e.get("EpisodeNumber"));
      assertEquals("2026-09-18", e.get("originalAirDate"));
      assertEquals("EP0177883040-0549647155", e.get("ProgramId"));
      assertEquals("SH0177883040", e.get("SeriesId"));
      assertEquals("tivo:rc.16718059", e.get("recordingId"));
      assertEquals("tivo:cl.177883040", e.get("collectionId"));
   }

   @Test
   void everyCapturedRecordingConvertsItsSizeAndTimesTheSameWay() throws Exception {
      JSONArray myshows = Fixtures.load("myshows.json");
      assertTrue(myshows.length() > 0, "expected My Shows entries");
      for (int i = 0; i < myshows.length(); i++) {
         JSONObject json = myshows.getJSONObject(i).getJSONArray("recording").getJSONObject(0);
         Hashtable<String,String> e = parseNPL.rpcToHashEntry("Bolt", json);
         long start = JSONConverter.getStartTime(json);
         assertEquals("" + json.getLong("size") * 1024, e.get("size"), "size at " + i);
         assertEquals("" + start, e.get("gmt"), "gmt at " + i);
         assertEquals(date(start), e.get("date"), "date at " + i);
         assertEquals(dateLong(start), e.get("date_long"), "date_long at " + i);
         // duration is seconds on the wire and milliseconds in the entry
         assertEquals("" + json.getInt("duration") * 1000, e.get("duration"), "duration at " + i);
         assertEquals(json.getString("partnerCollectionId") + "_" + start,
            e.get("ProgramId_unique"), "ProgramId_unique at " + i);
         // Both halves of an episode number are needed before either is set
         boolean numbered = json.has("seasonNumber") && json.has("episodeNum");
         assertEquals(numbered, e.containsKey("EpisodeNumber"), "EpisodeNumber at " + i);
         assertEquals(numbered, e.containsKey("season"), "season at " + i);
      }
   }

   @Test
   void aMovieKeepsItsYearAndHasNoEpisodeNumbering() throws Exception {
      Hashtable<String,String> e = parseNPL.rpcToHashEntry("Bolt", recording(4));
      assertEquals("Cradle 2 the Grave", e.get("title"));
      assertEquals("2003", e.get("movieYear"));
      assertFalse(e.containsKey("episodeTitle"));
      assertFalse(e.containsKey("EpisodeNumber"));
      // hdtv missing is not the same as hdtv false - neither sets HD
      assertFalse(e.containsKey("HD"));
      assertFalse(e.containsKey("suggestion"));
   }

   @Test
   void aRecordingWithSkipDataCarriesItsFirstClipId() throws Exception {
      // The recording has two clipMetadata blocks; only the first is kept
      Hashtable<String,String> e = parseNPL.rpcToHashEntry("Bolt", recording(5));
      assertEquals("tivo:cm.1637695", e.get("clipMetadataId"));
   }

   @Test
   void aChannelWithoutACallSignFallsBackToItsName() throws Exception {
      JSONObject json = recording(0);
      json.getJSONObject("channel").remove("callSign");
      json.getJSONObject("channel").put("name", "WTTW HD");
      assertEquals("WTTW HD", parseNPL.rpcToHashEntry("Bolt", json).get("channel"));
   }

   @Test
   void anInProgressRecordingIsMarkedAndATransferIsMarkedDifferently() throws Exception {
      JSONObject json = recording(0);
      json.put("state", "inProgress");
      Hashtable<String,String> e = parseNPL.rpcToHashEntry("Bolt", json);
      assertEquals("Yes", e.get("InProgress"));
      assertEquals("in-progress-recording", e.get("ExpirationImage"));

      // A PC push in flight looks like a recording in progress except for the
      // collection it claims to belong to
      json.put("collectionTitle", "pcBodySubscription");
      e = parseNPL.rpcToHashEntry("Bolt", json);
      assertEquals("Yes", e.get("InProgress"));
      assertEquals("in-progress-transfer", e.get("ExpirationImage"));
   }

   @Test
   void aKeepUntilIDeleteRecordingIsFlagged() throws Exception {
      JSONObject json = recording(0);
      json.put("deletionPolicy", "neverDelete");
      Hashtable<String,String> e = parseNPL.rpcToHashEntry("Bolt", json);
      assertEquals("save-until-i-delete-recording", e.get("ExpirationImage"));
      assertEquals("yes", e.get("kuid"));
   }

   @Test
   void aSuggestionIsFlaggedFromItsSubscription() throws Exception {
      JSONObject json = recording(0);
      json.getJSONArray("subscriptionIdentifier").getJSONObject(0).put("subscriptionType", "suggestion");
      Hashtable<String,String> e = parseNPL.rpcToHashEntry("Bolt", json);
      assertEquals("yes", e.get("suggestion"));
      assertEquals("suggestion-recording", e.get("ExpirationImage"));
   }

   @Test
   void theUrlsAddedByMyShowsAreMovedOffTheJson() throws Exception {
      // Remote.MyShows hangs the NPL XML urls off the json under private keys;
      // rpcToHashEntry takes them out again so they never reach the TiVo
      JSONObject json = recording(0);
      json.put("__url__", "http://192.168.1.10:80/download/x.TiVo?id=7");
      json.put("__url_TiVoVideoDetails__", "https://192.168.1.10:443/TiVoVideoDetails?id=7");
      Hashtable<String,String> e = parseNPL.rpcToHashEntry("Bolt", json);
      assertEquals("http://192.168.1.10:80/download/x.TiVo?id=7", e.get("url"));
      assertEquals("https://192.168.1.10:443/TiVoVideoDetails?id=7", e.get("url_TiVoVideoDetails"));
      assertFalse(json.has("__url__"));
      assertFalse(json.has("__url_TiVoVideoDetails__"));
   }
}
