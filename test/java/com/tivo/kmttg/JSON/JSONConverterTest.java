package com.tivo.kmttg.JSON;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Hashtable;
import java.util.Locale;
import java.util.TimeZone;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tivo.kmttg.main.config;

/**
 * Tests the kmttg-specific layer over the json.org classes.
 *
 * This is the only part of the JSON package kmttg wrote, and it is where the
 * TiVo's own conventions get decoded: times arrive as GMT strings with no zone
 * on them, padding is carried separately from the duration, and an episode is
 * a season plus a one element array. Every method here swallows a JSONException
 * and logs it, so a response shaped differently comes back as 0 or "" rather
 * than throwing - which means the fallbacks are the contract and are pinned
 * here alongside the happy paths.
 *
 * The class fixes the default time zone and locale because printableTimeFromJSON
 * formats in local time; the epoch arithmetic itself is zone independent since
 * getLongDateFromString pins the input to GMT.
 */
public class JSONConverterTest {

   // 2026-06-07 11:00:00 GMT
   private static final long JUN_7 = 1780830000000L;

   private static TimeZone prevZone;
   private static Locale prevLocale;
   private Hashtable<String,String> prevPartners;

   @BeforeAll
   public static void fixZoneAndLocale() {
      prevZone = TimeZone.getDefault();
      prevLocale = Locale.getDefault();
      TimeZone.setDefault(TimeZone.getTimeZone("GMT"));
      Locale.setDefault(Locale.US);
   }

   @AfterAll
   public static void restoreZoneAndLocale() {
      TimeZone.setDefault(prevZone);
      Locale.setDefault(prevLocale);
   }

   @BeforeEach
   public void seedPartners() {
      // A non-empty table keeps getPartnerName off the path that opens an RPC
      // connection to refresh it
      prevPartners = config.partners;
      config.partners = new Hashtable<String,String>();
      config.partners.put("tivo:pt.123", "Netflix");
   }

   @AfterEach
   public void restorePartners() {
      config.partners = prevPartners;
   }

   @Test
   public void datesAreReadAsGmtWhateverTheHostZoneIs() {
      assertEquals(0L, JSONConverter.getLongDateFromString("1970-01-01 00:00:00"));
      assertEquals(JUN_7, JSONConverter.getLongDateFromString("2026-06-07 11:00:00"));
      // An unparseable date logs and reads as the epoch rather than throwing
      assertEquals(0L, JSONConverter.getLongDateFromString("not a date"));
   }

   @Test
   public void startTimeSubtractsTheRequestedPadding() throws Exception {
      assertEquals(JUN_7, JSONConverter.getStartTime(show("{\"startTime\":\"2026-06-07 11:00:00\"}")));
      assertEquals(JUN_7 - 120000,
         JSONConverter.getStartTime(show(
            "{\"startTime\":\"2026-06-07 11:00:00\",\"requestedStartPadding\":120}")));
      assertEquals(0L, JSONConverter.getStartTime(new JSONObject()));
   }

   @Test
   public void endTimeIsTheDurationPastTheAlreadyPaddedStart() throws Exception {
      assertEquals(JUN_7 + 3600000,
         JSONConverter.getEndTime(show(
            "{\"startTime\":\"2026-06-07 11:00:00\",\"duration\":3600}")));
      assertEquals(JUN_7 + 3660000,
         JSONConverter.getEndTime(show(
            "{\"startTime\":\"2026-06-07 11:00:00\",\"duration\":3600,\"requestedEndPadding\":60}")));
      // Start padding has already moved the start, so it drags the end with it
      assertEquals(JUN_7 - 120000 + 3600000,
         JSONConverter.getEndTime(show(
            "{\"startTime\":\"2026-06-07 11:00:00\",\"duration\":3600,\"requestedStartPadding\":120}")));
      // No duration is not an error, it is a zero
      assertEquals(0L, JSONConverter.getEndTime(show("{\"startTime\":\"2026-06-07 11:00:00\"}")));
   }

   @Test
   public void episodeNumberIsSeasonTimesOneHundredPlusEpisode() throws Exception {
      assertEquals(305, JSONConverter.getEpisodeNum(
         show("{\"seasonNumber\":3,\"episodeNum\":[5]}")));
      assertEquals(1012, JSONConverter.getEpisodeNum(
         show("{\"seasonNumber\":10,\"episodeNum\":[12]}")));
      // Either half missing means there is nothing to sort on
      assertEquals(0, JSONConverter.getEpisodeNum(show("{\"seasonNumber\":3}")));
      assertEquals(0, JSONConverter.getEpisodeNum(show("{\"episodeNum\":[5]}")));
      assertEquals(0, JSONConverter.getEpisodeNum(new JSONObject()));
   }

   @Test
   public void printableTimeFormatsTheStartInLocalTime() throws Exception {
      assertEquals("Sun 06/07/26 11:00 AM",
         JSONConverter.printableTimeFromJSON(show("{\"startTime\":\"2026-06-07 11:00:00\"}")));
      // No startTime falls through getStartTime's zero and prints the epoch
      assertEquals("Thu 01/01/70 12:00 AM",
         JSONConverter.printableTimeFromJSON(new JSONObject()));
   }

   @Test
   public void printableTimeOfANamedKeyIsBlankWhenItCannotBeRead() throws Exception {
      JSONObject json = show(
         "{\"actualStartTime\":\"2026-06-07 11:00:00\",\"junk\":\"nonsense\",\"num\":5}");
      assertEquals("Sun 06/07/26 11:00 AM",
         JSONConverter.printableTimeFromJSON(json, "actualStartTime"));
      assertEquals("", JSONConverter.printableTimeFromJSON(json, "missing"));
      // A key holding something that is not a string reads as blank...
      assertEquals("", JSONConverter.printableTimeFromJSON(json, "num"));
      // ...but a string that is not a date only fails in the parse, so it
      // prints the epoch instead
      assertEquals("Thu 01/01/70 12:00 AM",
         JSONConverter.printableTimeFromJSON(json, "junk"));
   }

   @Test
   public void channelNameJoinsNumberAndCallSign() throws Exception {
      assertEquals("2-1=WBBMDT", JSONConverter.makeChannelName(
         show("{\"channel\":{\"channelNumber\":\"2-1\",\"callSign\":\"WBBMDT\"}}")));
      assertEquals("2-1", JSONConverter.makeChannelName(
         show("{\"channel\":{\"channelNumber\":\"2-1\"}}")));
      // The '=' is written unconditionally, so a call sign with no number
      // beside it comes out with a leading separator
      assertEquals("=WBBMDT", JSONConverter.makeChannelName(
         show("{\"channel\":{\"callSign\":\"WBBMDT\"}}")));
      // The all-channels wildcard is spelled out rather than joined with '='
      assertEquals("All Channels", JSONConverter.makeChannelName(
         show("{\"channel\":{\"callSign\":\"All Channels\"}}")));
      assertEquals("", JSONConverter.makeChannelName(new JSONObject()));
   }

   @Test
   public void channelNameFallsBackToTheWishListSource() throws Exception {
      // A one pass keeps its channel one level down under idSetSource
      assertEquals("2-1=WBBMDT", JSONConverter.makeChannelName(show(
         "{\"idSetSource\":{\"channel\":{\"channelNumber\":\"2-1\",\"callSign\":\"WBBMDT\"}}}")));
      assertEquals("All Channels", JSONConverter.makeChannelName(show(
         "{\"idSetSource\":{\"consumptionSource\":\"linear\"}}")));
      assertEquals("", JSONConverter.makeChannelName(show(
         "{\"idSetSource\":{\"consumptionSource\":\"streaming\"}}")));
      assertEquals("", JSONConverter.makeChannelName(show("{\"idSetSource\":{}}")));
   }

   @Test
   public void showTitleGluesTheEpisodeIntoOneNumber() throws Exception {
      // Season and episode are printed run together, so s3e5 reads as 305
      assertEquals(" Cheers [Ep 305]", JSONConverter.makeShowTitle(
         show("{\"title\":\"Cheers\",\"seasonNumber\":3,\"episodeNum\":[5]}")));
      assertEquals(" Cheers [Ep 1012]", JSONConverter.makeShowTitle(
         show("{\"title\":\"Cheers\",\"seasonNumber\":10,\"episodeNum\":[12]}")));
      assertEquals(" Cheers - Coach Returns", JSONConverter.makeShowTitle(
         show("{\"title\":\"Cheers\",\"subtitle\":\"Coach Returns\"}")));
      assertEquals(" Casablanca [1942]", JSONConverter.makeShowTitle(
         show("{\"title\":\"Casablanca\",\"movieYear\":1942}")));
      assertEquals(" ", JSONConverter.makeShowTitle(new JSONObject()));
   }

   @Test
   public void manualRecordingsArePrefixed() throws Exception {
      assertEquals(" Manual: 6am News", JSONConverter.makeShowTitle(show(
         "{\"title\":\"6am News\",\"subscriptionIdentifier\":" +
         "[{\"subscriptionType\":\"repeatingTimeChannel\"}]}")));
      assertEquals(" Manual: 6am News", JSONConverter.makeShowTitle(show(
         "{\"title\":\"6am News\",\"subscriptionIdentifier\":" +
         "[{\"subscriptionType\":\"singleTimeChannel\"}]}")));
      // Any other subscription type is an ordinary one pass
      assertEquals(" Cheers", JSONConverter.makeShowTitle(show(
         "{\"title\":\"Cheers\",\"subscriptionIdentifier\":" +
         "[{\"subscriptionType\":\"seasonPass\"}]}")));
      assertEquals(" Cheers", JSONConverter.makeShowTitle(show(
         "{\"title\":\"Cheers\",\"subscriptionIdentifier\":[]}")));
   }

   @Test
   public void wishListSourcesAreRecognized() throws Exception {
      assertTrue(JSONConverter.isWL(show("{\"idSetSource\":{\"type\":\"wishListSource\"}}")));
      assertFalse(JSONConverter.isWL(show("{\"idSetSource\":{\"type\":\"seasonPassSource\"}}")));
      assertFalse(JSONConverter.isWL(show("{\"idSetSource\":{}}")));
      assertFalse(JSONConverter.isWL(new JSONObject()));
   }

   @Test
   public void tivoNameFlagAccumulatesAcrossTivos() throws Exception {
      JSONObject json = new JSONObject();
      JSONConverter.addTivoNameFlagtoJson(json, "inTodo", "Bolt");
      assertEquals("Bolt", json.getString("inTodo"));
      JSONConverter.addTivoNameFlagtoJson(json, "inTodo", "Roamio");
      assertEquals("Bolt, Roamio", json.getString("inTodo"));
   }

   @Test
   public void partnerNamesComeFromTheCachedTable() throws Exception {
      assertEquals("Netflix", JSONConverter.getPartnerName(show("{\"partnerId\":\"tivo:pt.123\"}")));
      // An id the table does not know is shown as-is
      assertEquals("tivo:pt.999", JSONConverter.getPartnerName(show("{\"partnerId\":\"tivo:pt.999\"}")));
      // brandingPartnerId is read second, so it wins
      assertEquals("Netflix", JSONConverter.getPartnerName(show(
         "{\"partnerId\":\"tivo:pt.999\",\"brandingPartnerId\":\"tivo:pt.123\"}")));
      assertEquals("", JSONConverter.getPartnerName(new JSONObject()));
      // A partnerId that is not a string is the one case that gives up entirely
      assertEquals("STREAMING", JSONConverter.getPartnerName(show("{\"partnerId\":5}")));
   }

   @Test
   public void sortByEpisodeRunsEarliestFirst() throws Exception {
      JSONArray sorted = JSONConverter.sortByEpisode(new JSONArray(
         "[{\"seasonNumber\":2,\"episodeNum\":[1]}," +
         " {\"seasonNumber\":1,\"episodeNum\":[10]}," +
         " {\"seasonNumber\":1,\"episodeNum\":[2]}]"));
      assertEquals(3, sorted.length());
      assertEquals(102, JSONConverter.getEpisodeNum(sorted.getJSONObject(0)));
      assertEquals(110, JSONConverter.getEpisodeNum(sorted.getJSONObject(1)));
      assertEquals(201, JSONConverter.getEpisodeNum(sorted.getJSONObject(2)));
   }

   @Test
   public void sortByStartDateRunsBothWays() throws Exception {
      String array =
         "[{\"title\":\"mid\",\"startTime\":\"2026-06-07 11:00:00\"}," +
         " {\"title\":\"late\",\"startTime\":\"2026-06-08 11:00:00\"}," +
         " {\"title\":\"early\",\"startTime\":\"2026-06-06 11:00:00\"}]";

      JSONArray latest = JSONConverter.sortByLatestStartDate(new JSONArray(array));
      assertEquals("late", latest.getJSONObject(0).getString("title"));
      assertEquals("mid", latest.getJSONObject(1).getString("title"));
      assertEquals("early", latest.getJSONObject(2).getString("title"));

      JSONArray oldest = JSONConverter.sortByOldestStartDate(new JSONArray(array));
      assertEquals("early", oldest.getJSONObject(0).getString("title"));
      assertEquals("mid", oldest.getJSONObject(1).getString("title"));
      assertEquals("late", oldest.getJSONObject(2).getString("title"));
   }

   @Test
   public void sortingDropsEntriesThatAreNotObjects() throws Exception {
      // Each sort pulls entries out with getJSONObject and logs what it cannot
      // read, so anything else silently disappears from the result
      JSONArray input = new JSONArray(
         "[{\"seasonNumber\":1,\"episodeNum\":[1]},\"not an object\"]");
      assertEquals(1, JSONConverter.sortByEpisode(input).length());
      assertEquals(1, JSONConverter.sortByLatestStartDate(input).length());
      assertEquals(1, JSONConverter.sortByOldestStartDate(input).length());
      assertEquals(0, JSONConverter.sortByEpisode(new JSONArray()).length());
   }

   private static JSONObject show(String source) throws JSONException {
      return new JSONObject(source);
   }
}
