package com.tivo.kmttg.mux;

import static com.tivo.kmttg.mux.EbmlReader.CODEC_ID;
import static com.tivo.kmttg.mux.EbmlReader.CUES;
import static com.tivo.kmttg.mux.EbmlReader.CUE_POINT;
import static com.tivo.kmttg.mux.EbmlReader.CUE_TRACK;
import static com.tivo.kmttg.mux.EbmlReader.CUE_TRACK_POS;
import static com.tivo.kmttg.mux.EbmlReader.TRACK_NUMBER;
import static com.tivo.kmttg.mux.MuxStreams.AC3;
import static com.tivo.kmttg.mux.MuxStreams.AUDIO_PID;
import static com.tivo.kmttg.mux.MuxStreams.MPEG2;
import static com.tivo.kmttg.mux.MuxStreams.PRIVATE;
import static com.tivo.kmttg.mux.MuxStreams.PRIVATE_PID;
import static com.tivo.kmttg.mux.MuxStreams.VIDEO_PID;
import static com.tivo.kmttg.mux.MuxStreams.ac3Unit;
import static com.tivo.kmttg.mux.MuxStreams.mpeg2Inter;
import static com.tivo.kmttg.mux.MuxStreams.mpeg2Key;
import static com.tivo.kmttg.mux.MuxStreams.undescribable;
import static net.straylightlabs.tivolibre.FrameFixtures.cleanDecode;
import static net.straylightlabs.tivolibre.FrameFixtures.payload;
import static net.straylightlabs.tivolibre.FrameFixtures.program;
import static net.straylightlabs.tivolibre.FrameFixtures.stream;
import static net.straylightlabs.tivolibre.FrameFixtures.withoutPts;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tivo.kmttg.mux.mkv.MkvMuxer;

// Whole recordings driven through the sink - onProgram, payloads, onEnd - and then read back
// out of the .mkv. This is the class that decides which streams become tracks and when the
// header can be written, and every one of those decisions is irreversible once the first
// cluster is out, so they are worth pinning against a real file rather than a mock.
public class MuxSinkTest {

   @TempDir
   Path work;

   // Where the reference recording's video actually starts: ~25 hours in, which is what the
   // rebase exists for.
   private static final long PTS = 8240316184L;

   @Test
   void theTiVoPrivateStreamIsNotATrackAndTheCuesStillFollowTheVideo() throws Exception {
      // The private data pid is announced ahead of the media in the PMT, so carrying it would
      // shift every track number behind it - and the cue track with them.
      File out = work.resolve("program.mkv").toFile();
      MuxSink sink = new MuxSink(out);
      sink.onProgram(program(stream(PRIVATE_PID, PRIVATE), stream(VIDEO_PID, MPEG2),
         stream(AUDIO_PID, AC3)));
      sink.onPesPayload(payload(VIDEO_PID, PTS, mpeg2Key(1920, 1080, 3)));
      sink.onPesPayload(payload(AUDIO_PID, PTS, ac3Unit(1)));
      sink.onPesPayload(payload(VIDEO_PID, PTS + 3003, mpeg2Inter()));
      sink.onEnd(cleanDecode());

      byte[] f = Files.readAllBytes(out.toPath());
      List<EbmlReader> tracks = EbmlReader.trackEntries(f);
      assertEquals(2, tracks.size(), "private data carries no CodecID and cannot be a track");
      assertEquals(1, tracks.get(0).uintOr(TRACK_NUMBER, -1));
      assertEquals("V_MPEG2", tracks.get(0).stringOr(CODEC_ID, null));
      assertEquals(2, tracks.get(1).uintOr(TRACK_NUMBER, -1));
      assertEquals("A_AC3", tracks.get(1).stringOr(CODEC_ID, null));
      assertEquals(1, cueTrack(f), "a seek has to land on the video track");
   }

   @Test
   void aTrackThatNeverDescribesItselfIsDroppedAndTheRestRenumbered() throws Exception {
      // An audio pid ahead of the video in the PMT that never carries a parseable syncframe -
      // which is what a second audio track whose packets never decrypted looks like. The cue
      // track is the thing that breaks here: numbering after addTrack left it pointing at
      // whichever track inherited number 1, which is the audio one.
      File out = work.resolve("dropped.mkv").toFile();
      MuxSink sink = new MuxSink(out);
      int deadPid = 0x33;
      sink.onProgram(program(stream(deadPid, AC3), stream(VIDEO_PID, MPEG2),
         stream(AUDIO_PID, AC3)));
      sink.onPesPayload(payload(VIDEO_PID, PTS, mpeg2Key(704, 480, 3)));
      sink.onPesPayload(payload(AUDIO_PID, PTS, ac3Unit(1)));
      for (int i = 1; i < 8; i++) {
         sink.onPesPayload(payload(deadPid, PTS + i * 90000L, undescribable(64)));
         sink.onPesPayload(payload(VIDEO_PID, PTS + i * 90000L, mpeg2Inter()));
      }
      // The PMT that announces a stream appearing late names the dropped pid again beside it.
      // That pid was there from the start and has already been accounted for, so it must not
      // be reported a second time under a reason that is not what happened to it.
      sink.onProgram(program(stream(deadPid, AC3), stream(VIDEO_PID, MPEG2),
         stream(AUDIO_PID, AC3), stream(0x36, AC3)));
      sink.onEnd(cleanDecode());

      assertEquals(2, sink.getNotes().size(), sink.getNotes().toString());
      assertTrue(sink.getNotes().get(0).contains("pid=0x0033 never described itself"),
         sink.getNotes().toString());
      assertTrue(sink.getNotes().get(1).contains("pid=0x0036 appeared after the header"),
         sink.getNotes().toString());
      byte[] f = Files.readAllBytes(out.toPath());
      List<EbmlReader> tracks = EbmlReader.trackEntries(f);
      assertEquals(2, tracks.size());
      assertEquals(1, tracks.get(0).uintOr(TRACK_NUMBER, -1));
      assertEquals("V_MPEG2", tracks.get(0).stringOr(CODEC_ID, null));
      assertEquals(2, tracks.get(1).uintOr(TRACK_NUMBER, -1));
      assertEquals(1, cueTrack(f), "the cue track must be the video, not the number it left");
      for (EbmlReader.Block b : EbmlReader.blocks(f)) {
         assertTrue(b.track == 1 || b.track == 2, "a block on a track nothing declared: " + b.track);
      }
   }

   @Test
   void aPidArrivingAfterTheHeaderIsNotedOnceAndCarriesNothing() throws Exception {
      File out = work.resolve("late.mkv").toFile();
      MuxSink sink = new MuxSink(out);
      sink.onProgram(program(stream(PRIVATE_PID, PRIVATE), stream(VIDEO_PID, MPEG2),
         stream(AUDIO_PID, AC3)));
      sink.onPesPayload(payload(VIDEO_PID, PTS, mpeg2Key(704, 480, 3)));
      sink.onPesPayload(payload(AUDIO_PID, PTS, ac3Unit(1)));   // the header goes out here

      // A second audio language turning up part way in. The list is cumulative, so everything
      // announced at the start is named again alongside it - and none of that is new, whatever
      // the muxer decided to do with it. Twice, because a PMT repeats.
      int lateAudio = 0x36;
      for (int i = 0; i < 2; i++) {
         sink.onProgram(program(stream(PRIVATE_PID, PRIVATE), stream(VIDEO_PID, MPEG2),
            stream(AUDIO_PID, AC3), stream(lateAudio, AC3)));
      }
      sink.onPesPayload(payload(lateAudio, PTS + 90000, ac3Unit(1)));
      sink.onPesPayload(payload(VIDEO_PID, PTS + 90000, mpeg2Inter()));
      sink.onEnd(cleanDecode());

      assertEquals(1, sink.getNotes().size(),
         "one stream appeared late; the rest were there all along: " + sink.getNotes());
      assertTrue(sink.getNotes().get(0).contains("pid=0x0036"), sink.getNotes().get(0));
      assertTrue(sink.getNotes().get(0).contains("appeared after the header"));

      byte[] f = Files.readAllBytes(out.toPath());
      assertEquals(2, EbmlReader.trackEntries(f).size());
      assertFalse(sink.getSampleCounts().containsKey(Integer.valueOf(lateAudio)),
         "its payloads go nowhere; the note is the only trace");
   }

   @Test
   void aRecordingWithNothingDescribableFailsAndLeavesNoFileBehind() throws Exception {
      File out = work.resolve("nothing.mkv").toFile();
      MuxSink sink = new MuxSink(out);
      sink.onProgram(program(stream(VIDEO_PID, MPEG2)));
      for (int i = 0; i < 8; i++) {
         sink.onPesPayload(payload(VIDEO_PID, PTS + i * 90000L, undescribable(64)));
      }
      assertTrue(sink.isFailed());
      assertEquals("No describable tracks found", sink.getFailure());
      // close() would write a header, and a muxer holding no track throws rather than writing
      // one - an unchecked exception out of a callback and into the decode loop.
      assertDoesNotThrow(() -> sink.onEnd(cleanDecode()));
      assertEquals(0, out.length(), "nothing may be left that looks like a playable file");
   }

   @Test
   void theWindowClosesOnTimeAndTheBufferedPayloadsAreStillWritten() throws Exception {
      // An audio pid that is announced and then never says anything: the shape of a track
      // whose packets were all inside an excised region. Waiting for it would buffer the
      // whole recording, so the window closes on its own and the track is dropped.
      File out = work.resolve("span.mkv").toFile();
      MuxSink sink = new MuxSink(out);
      sink.onProgram(program(stream(VIDEO_PID, MPEG2), stream(AUDIO_PID, AC3)));
      sink.onPesPayload(payload(VIDEO_PID, PTS, mpeg2Key(704, 480, 3)));
      for (int i = 1; i < 8; i++) {
         sink.onPesPayload(payload(VIDEO_PID, PTS + i * 90000L, mpeg2Inter()));
      }
      sink.onEnd(cleanDecode());

      byte[] f = Files.readAllBytes(out.toPath());
      assertEquals(1, EbmlReader.trackEntries(f).size());
      assertEquals(8, EbmlReader.blocks(f).size(),
         "what was buffered while waiting is emitted, not thrown away");
      assertTrue(sink.getNotes().toString().contains("pid=0x0034 never described itself"),
         sink.getNotes().toString());
   }

   @Test
   void theWindowAlsoClosesOnCountWhenTheTimestampsBarelyMove() throws Exception {
      // The two limits are independent: this stream never spans five seconds, so only the
      // count can end the wait.
      File out = work.resolve("count.mkv").toFile();
      MuxSink sink = new MuxSink(out);
      sink.onProgram(program(stream(VIDEO_PID, MPEG2), stream(AUDIO_PID, AC3)));
      sink.onPesPayload(payload(VIDEO_PID, PTS, mpeg2Key(704, 480, 3)));
      for (int i = 1; i < 1999; i++) {
         sink.onPesPayload(payload(VIDEO_PID, PTS + i * 100L, mpeg2Inter()));
      }
      assertFalse(out.exists(), "still buffering: no file exists before the track list is final");
      sink.onPesPayload(payload(VIDEO_PID, PTS + 1999 * 100L, mpeg2Inter()));
      assertTrue(out.exists());
      sink.onEnd(cleanDecode());
      assertEquals(2000, EbmlReader.blocks(Files.readAllBytes(out.toPath())).size());
   }

   @Test
   void aRecordingShorterThanTheWindowIsStartedAtTheEnd() throws Exception {
      // One second of video and an audio track that never describes itself. The window never
      // closes, so without onEnd noticing there would be no file and no explanation - just
      // "remux failed on: X" against a recording that was perfectly good.
      File out = work.resolve("short.mkv").toFile();
      MuxSink sink = new MuxSink(out);
      sink.onProgram(program(stream(VIDEO_PID, MPEG2), stream(AUDIO_PID, AC3)));
      sink.onPesPayload(payload(VIDEO_PID, PTS, mpeg2Key(704, 480, 3)));
      sink.onPesPayload(payload(VIDEO_PID, PTS + 45000, mpeg2Inter()));
      sink.onPesPayload(payload(VIDEO_PID, PTS + 90000, mpeg2Inter()));
      assertFalse(out.exists());
      sink.onEnd(cleanDecode());

      assertFalse(sink.isFailed());
      byte[] f = Files.readAllBytes(out.toPath());
      assertEquals(3, EbmlReader.blocks(f).size());
      assertEquals(1000, sink.getDurationMs());
      assertEquals(1, sink.getCueCount(), "the one keyframe is still cued, so the file seeks");
   }

   @Test
   void payloadsWithNoTimestampAreDroppedAndCounted() throws Exception {
      // A muxer cannot place a picture it has no time for, and a guessed one becomes a sync
      // error nobody can trace back. Losing them quietly would be worse than losing them.
      File out = work.resolve("nopts.mkv").toFile();
      MuxSink sink = new MuxSink(out);
      sink.onProgram(program(stream(VIDEO_PID, MPEG2)));
      sink.onPesPayload(payload(VIDEO_PID, PTS, mpeg2Key(704, 480, 3)));
      for (int i = 0; i < 3; i++) {
         sink.onPesPayload(withoutPts(VIDEO_PID, mpeg2Inter()));
      }
      sink.onPesPayload(payload(VIDEO_PID, PTS + 3003, mpeg2Inter()));
      sink.onEnd(cleanDecode());

      assertEquals(3, sink.getDiscardedNoPts());
      assertEquals(2, EbmlReader.blocks(Files.readAllBytes(out.toPath())).size());
   }

   @Test
   void whatKmttgKnowsIsDeclaredAheadOfTheHeaderAndReachesTheFile() throws Exception {
      // Chapters, cover art and the tags each reserve a SeekHead entry, so the muxer refuses
      // them once the header is out - and it refuses with an IllegalStateException, which
      // from inside a payload callback is an unchecked throw into the decode loop rather than
      // a failed remux. The sink declaring them in start() is what keeps that from happening.
      File out = work.resolve("extras.mkv").toFile();
      MuxSink sink = new MuxSink(out);
      MetadataTags.Supplement extra = new MetadataTags.Supplement();
      extra.title = "Father Brown";
      extra.callsign = "KQED";
      sink.setSupplement(extra);
      sink.setChapters(Arrays.asList(new MkvMuxer.Chapter(0, 500, "Segment 1")));
      sink.setCoverArt("cover.jpg", "image/jpeg", new byte[]{(byte)0xFF, (byte)0xD8, 1, 2, 3});
      sink.onProgram(program(stream(VIDEO_PID, MPEG2)));
      sink.onPesPayload(payload(VIDEO_PID, PTS, mpeg2Key(704, 480, 3)));
      sink.onPesPayload(payload(VIDEO_PID, PTS + 90000, mpeg2Inter()));
      sink.onEnd(cleanDecode());

      assertFalse(sink.isFailed());
      String f = new String(Files.readAllBytes(out.toPath()), "ISO-8859-1");
      assertTrue(f.contains("Father Brown"), "the title reaches both the tags and Info");
      assertTrue(f.contains("KQED"), "the callsign is only ever in what kmttg knows");
      assertTrue(f.contains("Segment 1"), "the SkipMode marks reach the chapter menu");
      assertTrue(f.contains("cover.jpg"), "the poster is attached, not tagged");
   }

   private static long cueTrack(byte[] f) {
      EbmlReader cues = EbmlReader.segment(f).child(CUES);
      return cues.child(CUE_POINT).child(CUE_TRACK_POS).uintOr(CUE_TRACK, -1);
   }
}
