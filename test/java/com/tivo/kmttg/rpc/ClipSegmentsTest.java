package com.tivo.kmttg.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.TimeZone;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tivo.kmttg.main.config;

// Cover for where chapter cut points come from. Two sources with a preference order, and a
// deliberate third outcome of nothing at all - the interesting behaviour is which one answers,
// not the arithmetic, because neither source does any.
//
// The tivo.com tier is driven through a ReplayRemote, which is why ClipSegments takes a Remote
// rather than building one. The AutoSkip tier is a file, so it just needs programDir pointed
// at a temp dir - which works only because iniFile() resolves on each call.
public class ClipSegmentsTest {

   @TempDir
   Path work;

   // The offerId of the airing the clipmetadata_search fixture was captured against.
   private static final String RECORDED_AIRING =
      "tivo:of.ctd.10420179.2-1.terrestrial.2026-03-05-02-30-00.5400";

   private String savedProgramDir;
   private String savedUser;
   private String savedPass;

   @BeforeEach
   void redirectProgramDir() {
      savedProgramDir = config.programDir;
      savedUser = config.getTivoUsername();
      savedPass = config.getTivoPassword();
      config.programDir = work.toString();
      // No credentials: the tivo.com tier must decline before it tries to open a websocket,
      // so an unreachable network can never turn these into slow or flaky tests.
      config.setTivoUsername("");
      config.setTivoPassword("");
   }

   @AfterEach
   void restore() {
      config.programDir = savedProgramDir;
      config.setTivoUsername(savedUser == null ? "" : savedUser);
      config.setTivoPassword(savedPass == null ? "" : savedPass);
   }

   // Two show segments, in the shape visualDetect writes.
   private void writeAutoSkipEntry(String contentId) throws IOException {
      String eol = "\r\n";
      String entry = "<entry>" + eol
         + "contentId=" + contentId + eol
         + "offerId=tivo:of.test" + eol
         + "offset=0" + eol
         + "tivoName=Bolt" + eol
         + "title=Test Show" + eol
         + "0 900000" + eol
         + "1100000 1600000" + eol;
      Files.write(work.resolve("AutoSkip.ini"), entry.getBytes(StandardCharsets.UTF_8));
   }

   @Test
   void autoSkipEntryIsUsedAndReportedAsSuch() throws Exception {
      writeAutoSkipEntry("tivo:ct.1");
      ClipSegments.Result r = ClipSegments.get("Bolt", "tivo:ct.1", "tivo:rc.1", "tivo:cm.1", RECORDED_AIRING);
      assertNotNull(r);
      assertEquals(ClipSegments.SOURCE_AUTOSKIP, r.source);
      assertEquals(2, r.segments.size());
      assertEquals(0, r.segments.get(0).startMs);
      assertEquals(900000, r.segments.get(0).endMs);
      assertEquals(1100000, r.segments.get(1).startMs);
      assertEquals(1600000, r.segments.get(1).endMs);
   }

   @Test
   void autoSkipWinsOverTivoComEvenWithBothIdsPresent() throws Exception {
      // The recording carries the ids the tivo.com tier needs, and it is still not consulted.
      // Two of the six writers of this table are comskip review and VideoReDo review, so the
      // entry may be a correction somebody made by hand.
      writeAutoSkipEntry("tivo:ct.1");
      ClipSegments.Result r = ClipSegments.get("Bolt", "tivo:ct.1", "tivo:rc.1", "tivo:cm.1", RECORDED_AIRING);
      assertEquals(ClipSegments.SOURCE_AUTOSKIP, r.source);
   }

   @Test
   void noAutoSkipAndNoCredentialsMeansNoSegments() {
      // The quiet path: chapters are simply omitted rather than approximated.
      assertNull(ClipSegments.get("Bolt", "tivo:ct.1", "tivo:rc.1", "tivo:cm.1", RECORDED_AIRING));
   }

   @Test
   void missingIdsNeverReachTheNetwork() {
      assertNull(ClipSegments.get("Bolt", null, "tivo:rc.1", "tivo:cm.1", RECORDED_AIRING));
      assertNull(ClipSegments.get("Bolt", "tivo:ct.1", null, "tivo:cm.1", RECORDED_AIRING));
      assertNull(ClipSegments.get("Bolt", "tivo:ct.1", "tivo:rc.1", null, RECORDED_AIRING));
   }

   @Test
   void clipMetadataAdjustIsParsedOffTheRecordingsOwnClock() throws Exception {
      ReplayRemote r = new ReplayRemote(Fixtures.load("clipmetadata_adjust.json"));
      List<ClipSegments.Segment> s =
         ClipSegments.fromClipMetadataAdjust(r, "tivo:rc.16716059", "tivo:cm.1636994");
      assertNotNull(s);
      assertEquals(10, s.size());
      // Negative, and that is the point: the SkipMode window opens before the recording does,
      // so the first segment starts 27.7 s before the first frame. Clamping is the muxer's job.
      assertEquals(-27739, s.get(0).startMs);
      assertEquals(912511, s.get(0).endMs);
      assertEquals(1107328, s.get(1).startMs);
      // And the last end runs past the recording for the same reason.
      assertEquals(7232203, s.get(9).endMs);
   }

   @Test
   void tivosTrailingZeroLengthPaddingIsDropped() throws Exception {
      // Real responses end with a 0 -> 0 segment on a good fraction of recordings. Letting it
      // through made the ordering check reject the whole recording over the padding, which
      // silently cost 29 of 144 entries on a live run.
      ReplayRemote r = new ReplayRemote(Fixtures.load("clipmetadata_padded.json"));
      List<ClipSegments.Segment> s =
         ClipSegments.fromClipMetadataAdjust(r, "tivo:rc.1", "tivo:cm.1");
      assertEquals(3, s.size(), "the trailing 0 -> 0 marker must not survive");
      assertEquals(4115586, s.get(0).startMs);
      assertEquals(7726162, s.get(2).endMs);
      // And what survives must pass validation rather than trip the ordering check.
      assertNull(ClipSegments.rejectReason(s, 7800000));
   }

   @Test
   void clipMetadataAdjustCarriesTheBodyIdMiddlemindRoutesOn() throws Exception {
      // Without this the cloud answers routeNotFound - it means no route to a body, not an
      // unknown request type, and it is the whole reason this call looked unavailable.
      ReplayRemote r = new ReplayRemote(Fixtures.load("clipmetadata_adjust.json"));
      ClipSegments.fromClipMetadataAdjust(r, "tivo:rc.16716059", "tivo:cm.1636994");
      assertEquals(1, r.issued("clipMetadataAdjust"));
      String sent = r.lastRequest("clipMetadataAdjust").toString();
      assertTrue(sent.contains("bodyId"), "request must carry bodyId: " + sent);
      assertTrue(sent.contains("tivo:rc.16716059"));
      assertTrue(sent.contains("tivo:cm.1636994"));
   }

   @Test
   void anErrorOrEmptyResponseIsJustNoSegments() throws Exception {
      // Past the end of the trace ReplayRemote returns null, which is what a middlemindError
      // looks like to Remote.Command.
      ReplayRemote r = new ReplayRemote(Fixtures.load("clipmetadata_adjust.json"));
      ClipSegments.fromClipMetadataAdjust(r, "tivo:rc.1", "tivo:cm.1");
      assertNull(ClipSegments.fromClipMetadataAdjust(r, "tivo:rc.1", "tivo:cm.1"));
   }

   @Test
   void theRecordedAiringIsPickedNotWhicheverWasAuthoredFirst() throws Exception {
      // Ten clipMetadata for one episode, five airings over a week on three stations. The
      // fixture's first entry is the 03-05 airing, so match against the 03-09 one to prove
      // the offerStartTime is doing the work rather than the list order.
      ReplayRemote r = new ReplayRemote(Fixtures.load("clipmetadata_search.json"));
      assertEquals("tivo:cm.1526679", ClipSegments.chooseForAiring(r, "tivo:ct.537510368",
         "tivo:of.ctd.1.2-1.terrestrial.2026-03-09-03-00-00.5400", "tivo:cm.fallback"));
   }

   @Test
   void amongCopiesOfOneAiringTheFirstListedWins() throws Exception {
      // Each airing is authored two to four times over two days with identical offsets, and
      // the most recent copy is routinely one clipMetadataAdjust answers "Requested clip
      // metadata is not found" for. Preferring the newest cost two recordings their data.
      ReplayRemote r = new ReplayRemote(Fixtures.load("clipmetadata_search.json"));
      assertEquals("tivo:cm.1524227",
         ClipSegments.chooseForAiring(r, "tivo:ct.537510368", RECORDED_AIRING, "tivo:cm.fallback"));
   }

   @Test
   void dataForOtherAiringsOnlyIsStillUsed() throws Exception {
      // Refusing here was tried and was wrong: clipMetadataAdjust re-anchors whatever clip it
      // is handed onto the recording asked for, so a clip authored for a different broadcast -
      // even nine years earlier - still comes back fitting. Refusing cost 20 working
      // recordings on a live library. Matching an airing is a refinement, never a veto.
      ReplayRemote r = new ReplayRemote(Fixtures.load("clipmetadata_search.json"));
      assertEquals("tivo:cm.fallback", ClipSegments.chooseForAiring(r, "tivo:ct.537510368",
         "tivo:of.ctd.10420179.2-1.terrestrial.2026-06-01-01-00-00.5400", "tivo:cm.fallback"));
   }

   @Test
   void anAiringAMinuteOffIsStillTheSameBroadcast() throws Exception {
      // Guide data and the clip author do not always agree to the second, so the match has
      // slack. Two minutes is far short of the gap between reruns.
      ReplayRemote r = new ReplayRemote(Fixtures.load("clipmetadata_search.json"));
      assertEquals("tivo:cm.1524227", ClipSegments.chooseForAiring(r, "tivo:ct.537510368",
         "tivo:of.ctd.10420179.2-1.terrestrial.2026-03-05-02-31-00.5400", "tivo:cm.fallback"));
   }

   @Test
   void anOfferIdWithoutATimestampKeepsTheOldBehaviour() throws Exception {
      // Cannot tell which airing this is, so fall back to what the NPL offered rather than
      // refusing - an unreadable offerId must be no worse than before, not a regression.
      ReplayRemote r = new ReplayRemote(Fixtures.load("clipmetadata_search.json"));
      assertEquals("tivo:cm.fallback",
         ClipSegments.chooseForAiring(r, "tivo:ct.1", null, "tivo:cm.fallback"));
      assertEquals("tivo:cm.fallback",
         ClipSegments.chooseForAiring(r, "tivo:ct.1", "tivo:of.ctd.no.timestamp", "tivo:cm.fallback"));
      assertEquals(0, r.issued("clipMetadataSearch"), "nothing to match on, so do not ask");
   }

   @Test
   void airingTimesAreComparedInUtc() {
      // Both stamps are UTC. Reading either as local time would shift every comparison by the
      // offset, which for a show on the hour is enough to match a different airing entirely.
      TimeZone saved = TimeZone.getDefault();
      try {
         TimeZone.setDefault(TimeZone.getTimeZone("America/Chicago"));
         assertEquals(Instant.parse("2026-03-05T02:30:00Z").toEpochMilli(),
            ClipSegments.offerStartTime(RECORDED_AIRING));
      } finally {
         TimeZone.setDefault(saved);
      }
      assertEquals(0, ClipSegments.offerStartTime(null));
      assertEquals(0, ClipSegments.offerStartTime("tivo:of.ctd.no.timestamp"));
   }

   private static Hashtable<String,String> npl(String contentId, String clipMetadataId,
         String recordingId) {
      Hashtable<String,String> e = new Hashtable<String,String>();
      if (contentId != null)      e.put("contentId", contentId);
      if (clipMetadataId != null) e.put("clipMetadataId", clipMetadataId);
      if (recordingId != null)    e.put("recordingId", recordingId);
      e.put("title", "Show " + contentId);
      return e;
   }

   @Test
   void onlyRecordingsWithSkipModeAndNoEntryNeedFetching() throws Exception {
      writeAutoSkipEntry("tivo:ct.done");
      List<Hashtable<String,String>> npl = new ArrayList<Hashtable<String,String>>();
      npl.add(npl("tivo:ct.done", "tivo:cm.1", "tivo:rc.1"));   // already in the table
      npl.add(npl("tivo:ct.new",  "tivo:cm.2", "tivo:rc.2"));   // wanted
      npl.add(npl("tivo:ct.nosk", null,        "tivo:rc.3"));   // no SkipMode at all
      npl.add(npl("tivo:ct.norec","tivo:cm.4", null));          // unusable without recordingId

      List<Hashtable<String,String>> missing = ClipSegments.missingFromAutoSkip(npl);
      assertEquals(1, missing.size(), "only the one with SkipMode and no entry");
      assertEquals("tivo:ct.new", missing.get(0).get("contentId"));
   }

   @Test
   void nothingToFetchWhenTheTableIsAlreadyComplete() throws Exception {
      writeAutoSkipEntry("tivo:ct.done");
      List<Hashtable<String,String>> npl = new ArrayList<Hashtable<String,String>>();
      npl.add(npl("tivo:ct.done", "tivo:cm.1", "tivo:rc.1"));
      // An empty list is what stops a job being queued at all, so it matters that this is
      // empty rather than merely small.
      assertTrue(ClipSegments.missingFromAutoSkip(npl).isEmpty());
      assertTrue(ClipSegments.missingFromAutoSkip(null).isEmpty());
   }

   @Test
   void aRejectIsRememberedUnderTheIdTheScanWillLookUp() throws Exception {
      // The reject cache is written by the fetch and read by the scan, and they have to agree
      // on the key or nothing is ever skipped. The fetch picks an airing-matched id that is
      // normally NOT the one on the NPL entry, so keying the row on the chosen id silently
      // defeated the whole cache: every refused recording came back on every refresh.
      Hashtable<String,String> e = npl("tivo:ct.adrift", "tivo:cm.oldest", "tivo:rc.1");
      e.put("offerId", RECORDED_AIRING);
      e.put("duration", "1800000");   // segments land hours past this, so it is refused

      ReplayRemote r = new ReplayRemote(Fixtures.load("clipmetadata_search_then_adjust.json"));
      assertFalse(ClipSegments.fetchOne(r, "Bolt", e), "adrift segments must not be written");
      assertEquals("tivo:cm.oldest", SkipModeRejects.load().get("tivo:ct.adrift"));

      List<Hashtable<String,String>> npl = new ArrayList<Hashtable<String,String>>();
      npl.add(e);
      assertTrue(ClipSegments.missingFromAutoSkip(npl).isEmpty(),
         "a remembered reject must not be queued again on the next refresh");
   }

   @Test
   void aPreferredClipThatWillNotAdjustFallsBackToTheNplsOwn() throws Exception {
      // clipMetadataSearch lists ids that clipMetadataAdjust then refuses with "Requested clip
      // metadata is not found". Two real recordings lost their data that way: the preferred
      // clip 404s and the one on the NPL entry, a copy of the same airing, adjusts fine.
      Hashtable<String,String> e = npl("tivo:ct.fb", "tivo:cm.onnpl", "tivo:rc.1");
      e.put("offerId", RECORDED_AIRING);
      e.put("duration", "1800000");

      ReplayRemote r = new ReplayRemote(Fixtures.load("clipmetadata_adjust_fallback.json"));
      assertTrue(ClipSegments.fetchOne(r, "Bolt", e), "the fallback's segments must be saved");
      assertEquals(2, r.issued("clipMetadataAdjust"), "the refused id then the NPL's");
      assertEquals("tivo:cm.onnpl",
         r.lastRequest("clipMetadataAdjust").getString("clipMetadataId"));
      assertFalse(SkipManager.getEntry("tivo:ct.fb").isEmpty(), "AutoSkip entry written");
   }

   @Test
   void aBatchFetchWithoutCredentialsWritesNothing() {
      List<Hashtable<String,String>> npl = new ArrayList<Hashtable<String,String>>();
      npl.add(npl("tivo:ct.new", "tivo:cm.2", "tivo:rc.2"));
      // Declines before opening a websocket, so this cannot hang on a missing network.
      assertEquals(0, ClipSegments.fetchMissing("Bolt", npl, null));
   }

   @Test
   void segmentsAnchoredOutsideTheRecordingAreRejectedNotWritten() throws Exception {
      // What about a fifth of a real My Shows list does: clipMetadataAdjust returns a window
      // of the right size for the right airing, with its origin hours adrift. Every segment
      // then lands past the end of the file. Writing them would send Ad Skip to the wrong
      // places, so the entry is refused outright.
      List<ClipSegments.Segment> adrift = new ArrayList<ClipSegments.Segment>();
      adrift.add(new ClipSegments.Segment(7220822, 7785262));
      adrift.add(new ClipSegments.Segment(7995042, 8513105));
      assertTrue(! ClipSegments.saveToAutoSkip("tivo:ct.hp", "tivo:of.1", "High Potential",
         "Bolt", adrift, 3538000));
      assertTrue(! Files.exists(work.resolve("AutoSkip.ini")), "nothing should be written");
   }

   private static List<ClipSegments.Segment> segs(long... bounds) {
      List<ClipSegments.Segment> l = new ArrayList<ClipSegments.Segment>();
      for (int i=0; i<bounds.length; i+=2) l.add(new ClipSegments.Segment(bounds[i], bounds[i+1]));
      return l;
   }

   @Test
   void theRealCapturedResponsePassesEveryCheck() throws Exception {
      // The strongest regression guard available: all ten segments of a real, verified
      // clipMetadataAdjust response against that recording's real duration. If tightening a
      // bound ever starts rejecting this, it is rejecting data known to be correct - those
      // chapter marks were confirmed against black frames and in VLC.
      ReplayRemote r = new ReplayRemote(Fixtures.load("clipmetadata_adjust.json"));
      List<ClipSegments.Segment> real =
         ClipSegments.fromClipMetadataAdjust(r, "tivo:rc.16716059", "tivo:cm.1636994");
      assertEquals(10, real.size());
      assertNull(ClipSegments.rejectReason(real, 7199000),
         "the verified DWTS response must never be rejected");
   }

   @Test
   void realDataOverhangingBothEndsStillPasses() {
      // Measured on three verified recordings: the head sits at about -28 s and the tail about
      // +35 s, because the monitoring window guards the scheduled slot by 30 s at each end.
      // Tightening the bounds must never start rejecting this.
      assertNull(ClipSegments.rejectReason(segs(-27739, 912511, 6861410, 7232203), 7199000));
      assertNull(ClipSegments.rejectReason(segs(-28101, 611696, 5292000, 5432000), 5393000));
      // Start padding pushes the first segment well inside the file, which is also fine.
      assertNull(ClipSegments.rejectReason(segs(270000, 900000), 1800000));
   }

   @Test
   void aBoundaryFarOutsideTheRecordingIsRejected() {
      // The observed failure: a correctly sized window anchored hours adrift.
      assertNotNull(ClipSegments.rejectReason(segs(7220822, 7785262), 3538000));
      // And the subtler version the old check would have waved through - a first segment
      // inside the file but a tail 20 minutes past its end.
      assertNotNull(ClipSegments.rejectReason(segs(60000, 2400000), 1200000));
      // Just outside the guard but inside the slack: still accepted.
      assertNull(ClipSegments.rejectReason(segs(-60000, 1000000), 1200000));
   }

   @Test
   void segmentsThatRunBackwardsOrOverlapAreRejected() {
      assertNotNull(ClipSegments.rejectReason(segs(500000, 400000), 1800000));
      assertNotNull(ClipSegments.rejectReason(segs(0, 600000, 500000, 900000), 1800000));
      assertNotNull(ClipSegments.rejectReason(segs(0, 0), 1800000));
      // Butting exactly end-to-start is legal - a break of zero length, not an overlap.
      assertNull(ClipSegments.rejectReason(segs(0, 600000, 600000, 900000), 1800000));
   }

   @Test
   void moreContentThanRecordingIsRejected() {
      // The Matlock shape: every boundary lands inside the file, but the segments describe a
      // different airing's breaks and add up to more show than was recorded.
      assertNotNull(ClipSegments.rejectReason(
         segs(0, 900000, 900000, 1750000), 1200000));
   }

   @Test
   void orderingIsCheckedEvenWithNoDuration() {
      // Nothing to measure against, but a backwards list is still corrupt.
      assertNotNull(ClipSegments.rejectReason(segs(500000, 400000), 0));
      assertNull(ClipSegments.rejectReason(segs(7220822, 7785262), 0));
   }

   @Test
   void fitsRecordingIsTheSharedGateForChaptersAndAutoSkip() {
      List<ClipSegments.Segment> adrift = new ArrayList<ClipSegments.Segment>();
      adrift.add(new ClipSegments.Segment(7220822, 7785262));
      assertTrue(! ClipSegments.fitsRecording(adrift, 3538000), "starts past the end");

      List<ClipSegments.Segment> ok = new ArrayList<ClipSegments.Segment>();
      ok.add(new ClipSegments.Segment(-27739, 912511));
      ok.add(new ClipSegments.Segment(6861410, 7232203));
      assertTrue(ClipSegments.fitsRecording(ok, 7199000),
         "overhanging both ends by the window guard is normal, not adrift");

      // Unknown duration cannot be checked against, so the data is taken at face value.
      assertTrue(ClipSegments.fitsRecording(adrift, 0));
      assertTrue(! ClipSegments.fitsRecording(null, 7199000));
      assertTrue(! ClipSegments.fitsRecording(new ArrayList<ClipSegments.Segment>(), 7199000));
   }

   @Test
   void aGoodEntryReportsThatItWasWritten() throws Exception {
      List<ClipSegments.Segment> ok = new ArrayList<ClipSegments.Segment>();
      ok.add(new ClipSegments.Segment(-27739, 912511));
      ok.add(new ClipSegments.Segment(1107328, 1655561));
      assertTrue(ClipSegments.saveToAutoSkip("tivo:ct.ok", "tivo:of.1", "DWTS", "Bolt",
         ok, 7199000));
   }

   @Test
   void anUnknownDurationLeavesTheEndAlone() throws Exception {
      // 0 means "nobody told us how long the recording is", which has to mean no clamping
      // rather than clamping everything to zero and writing an empty entry.
      ReplayRemote r = new ReplayRemote(Fixtures.load("clipmetadata_adjust.json"));
      List<ClipSegments.Segment> s =
         ClipSegments.fromClipMetadataAdjust(r, "tivo:rc.16716059", "tivo:cm.1636994");
      ClipSegments.saveToAutoSkip("tivo:ct.1", "tivo:of.1", "DWTS", "Bolt", s, 0);

      String ini = new String(Files.readAllBytes(work.resolve("AutoSkip.ini")),
         StandardCharsets.UTF_8);
      assertTrue(ini.contains("6861410 7232203"), "end left as reported: " + ini);
      assertTrue(ini.contains("0 912511"), "the start is still clamped: " + ini);
   }

   @Test
   void savingToAutoSkipClampsToTheRecordingAndReplacesAnyExistingEntry() throws Exception {
      writeAutoSkipEntry("tivo:ct.1");
      ReplayRemote r = new ReplayRemote(Fixtures.load("clipmetadata_adjust.json"));
      List<ClipSegments.Segment> s =
         ClipSegments.fromClipMetadataAdjust(r, "tivo:rc.16716059", "tivo:cm.1636994");

      ClipSegments.saveToAutoSkip("tivo:ct.1", "tivo:of.1", "DWTS", "Bolt", s, 7199000);

      String ini = new String(Files.readAllBytes(work.resolve("AutoSkip.ini")),
         StandardCharsets.UTF_8);
      assertEquals(1, ini.split("<entry>", -1).length - 1,
         "the old entry must be replaced, not appended to - a duplicate reads first");
      assertTrue(ini.contains("0 912511"), "negative start clamped to 0: " + ini);
      assertTrue(ini.contains("6861410 7199000"), "end clamped to the duration: " + ini);
      // AutoSkip matches the recording being played back on offerId, so an entry saved
      // without one is listed in the Skip table and never fires.
      assertTrue(ini.contains("offerId=tivo:of.1"), "offerId must be stored: " + ini);

      // And it reads back as the AutoSkip tier, which is the point of writing it.
      ClipSegments.Result back = ClipSegments.get("Bolt", "tivo:ct.1", "tivo:rc.1", "tivo:cm.1", RECORDED_AIRING);
      assertEquals(ClipSegments.SOURCE_AUTOSKIP, back.source);
      assertEquals(10, back.segments.size());
      assertEquals(0, back.segments.get(0).startMs);
      assertEquals(7199000, back.segments.get(9).endMs);
   }
}
