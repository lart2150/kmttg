package com.tivo.kmttg.mux;

import static com.tivo.kmttg.mux.MuxStreams.AC3;
import static com.tivo.kmttg.mux.MuxStreams.AUD;
import static com.tivo.kmttg.mux.MuxStreams.AUDIO_PID;
import static com.tivo.kmttg.mux.MuxStreams.H264;
import static com.tivo.kmttg.mux.MuxStreams.I_SLICE;
import static com.tivo.kmttg.mux.MuxStreams.MPEG2;
import static com.tivo.kmttg.mux.MuxStreams.PPS;
import static com.tivo.kmttg.mux.MuxStreams.SPS_720x480;
import static com.tivo.kmttg.mux.MuxStreams.VIDEO_PID;
import static com.tivo.kmttg.mux.MuxStreams.ac3Unit;
import static com.tivo.kmttg.mux.MuxStreams.accessUnit;
import static com.tivo.kmttg.mux.MuxStreams.mpeg2Inter;
import static com.tivo.kmttg.mux.MuxStreams.mpeg2Key;
import static net.straylightlabs.tivolibre.FrameFixtures.cleanDecode;
import static net.straylightlabs.tivolibre.FrameFixtures.excision;
import static net.straylightlabs.tivolibre.FrameFixtures.payload;
import static net.straylightlabs.tivolibre.FrameFixtures.program;
import static net.straylightlabs.tivolibre.FrameFixtures.stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Where the samples land on the file's clock. A recording starts tens of thousands of seconds
// into a 33 bit counter that wraps, its streams do not start together, and one payload unit is
// not always one sample - so the timestamp a block ends up with has several ways to be wrong
// and only one to be right.
public class MuxSinkTimelineTest {

   @TempDir
   Path work;

   // The reference recording: video starts at ~25 hours, and audio leads it by 49,230 ticks.
   private static final long VIDEO_PTS = 8240365414L;
   private static final long AUDIO_PTS = 8240316184L;

   @Test
   void theEarliestTimestampAnywhereBecomesTheZeroOfTheFile() throws Exception {
      // Rebasing per stream would zero both and destroy the offset between them, which IS the
      // A/V sync: here audio leads video by 547 ms and has to keep leading it by 547 ms.
      File out = work.resolve("rebase.mkv").toFile();
      MuxSink sink = new MuxSink(out);
      sink.onProgram(program(stream(VIDEO_PID, MPEG2), stream(AUDIO_PID, AC3)));
      sink.onPesPayload(payload(VIDEO_PID, VIDEO_PTS, mpeg2Key(704, 480, 3)));
      sink.onPesPayload(payload(AUDIO_PID, AUDIO_PTS, ac3Unit(1)));
      sink.onEnd(cleanDecode());

      List<EbmlReader.Block> blocks = EbmlReader.blocks(Files.readAllBytes(out.toPath()));
      assertEquals(0, onTrack(blocks, 2).get(0).ms, "the earliest payload is the file's zero");
      assertEquals(547, onTrack(blocks, 1).get(0).ms);
      assertEquals(0, sink.getClampedTimestamps(), "nothing can precede the minimum");
   }

   @Test
   void payloadsStraddlingThe33BitWrapStayInOrderOnTheFilesClock() throws Exception {
      // The 33 bit counter wraps every 26.5 hours and a recording can straddle it while the
      // muxer is still buffering, before anything has been written and before the base is
      // locked. Taken at face value the payloads past it are timed 26.5 hours BEFORE the ones
      // ahead of them, which rebases to a negative time and clamps the lot onto zero.
      File out = work.resolve("wrap.mkv").toFile();
      MuxSink sink = new MuxSink(out);
      // The audio track never describes itself, so nothing is written until onEnd and all
      // four payloads are still in the buffer when the wrap is passed.
      sink.onProgram(program(stream(VIDEO_PID, MPEG2), stream(AUDIO_PID, AC3)));
      long range = 1L << 33;
      sink.onPesPayload(payload(VIDEO_PID, range - 9000, mpeg2Key(704, 480, 3)));
      sink.onPesPayload(payload(VIDEO_PID, range - 4500, mpeg2Inter()));
      sink.onPesPayload(payload(VIDEO_PID, 0, mpeg2Inter()));
      sink.onPesPayload(payload(VIDEO_PID, 4500, mpeg2Inter()));
      sink.onEnd(cleanDecode());

      List<EbmlReader.Block> blocks = EbmlReader.blocks(Files.readAllBytes(out.toPath()));
      assertEquals(4, blocks.size());
      assertEquals(0, blocks.get(0).ms);
      assertEquals(50, blocks.get(1).ms);
      assertEquals(100, blocks.get(2).ms, "the payload past the wrap carries on, not back");
      assertEquals(150, blocks.get(3).ms);
      assertEquals(0, sink.getClampedTimestamps(), "nothing had to be clamped onto zero");
   }

   @Test
   void everySyncframeInAUnitGetsItsOwnBlockAndItsOwnTime() throws Exception {
      // One AC-3 payload unit routinely holds several syncframes under a single PTS. Each is
      // a block of its own, timed from the unit's PTS by its position: stamping them all
      // alike would pile 96 ms of audio onto one instant.
      File out = work.resolve("ac3.mkv").toFile();
      MuxSink sink = new MuxSink(out);
      sink.onProgram(program(stream(AUDIO_PID, AC3)));
      sink.onPesPayload(payload(AUDIO_PID, AUDIO_PTS, ac3Unit(3)));
      sink.onEnd(cleanDecode());

      List<EbmlReader.Block> blocks = EbmlReader.blocks(Files.readAllBytes(out.toPath()));
      assertEquals(3, blocks.size());
      // 1536 samples at 48 kHz is 32 ms, so the three are 32 ms apart, not 0 ms apart.
      assertEquals(0, blocks.get(0).ms);
      assertEquals(32, blocks.get(1).ms);
      assertEquals(64, blocks.get(2).ms);
      for (EbmlReader.Block b : blocks) {
         assertEquals(1536, b.data.length, "one syncframe per block, not the whole unit");
         assertEquals(0x0B, b.data[0] & 0xFF);
         assertEquals(0x77, b.data[1] & 0xFF);
      }
   }

   @Test
   void aParameterSetOnlyAccessUnitIsSkippedRatherThanWrittenEmpty() throws Exception {
      // Broadcast H.264 repeats the parameter sets ahead of a random access point, and AUD,
      // SPS and PPS are all dropped on the way into Matroska - which leaves an access unit
      // with nothing in it, and Matroska has no empty block to write it as.
      File out = work.resolve("avc.mkv").toFile();
      MuxSink sink = new MuxSink(out);
      sink.onProgram(program(stream(VIDEO_PID, H264)));
      sink.onPesPayload(payload(VIDEO_PID, VIDEO_PTS, accessUnit(AUD, SPS_720x480, PPS)));
      sink.onPesPayload(payload(VIDEO_PID, VIDEO_PTS + 3003, accessUnit(AUD, I_SLICE)));
      sink.onEnd(cleanDecode());

      List<EbmlReader.Block> blocks = EbmlReader.blocks(Files.readAllBytes(out.toPath()));
      assertEquals(1, blocks.size(), "only the unit that carried a slice is a sample");
      assertEquals(33, blocks.get(0).ms);
      assertTrue(blocks.get(0).key, "an I slice is a random access point even with no IDR");
      assertEquals(Long.valueOf(1), sink.getSampleCounts().get(Integer.valueOf(VIDEO_PID)));
   }

   @Test
   void aPayloadUnitWithNoBytesInItIsNotWrittenAsAnEmptyBlock() throws Exception {
      // A unit cut off right behind its PES header carries a timestamp and no elementary
      // bytes at all, which is what an excision leaves where it landed. Matroska has no empty
      // block to write that as - the same rule the parameter-set case above turns on.
      File out = work.resolve("empty.mkv").toFile();
      MuxSink sink = new MuxSink(out);
      sink.onProgram(program(stream(VIDEO_PID, MPEG2)));
      sink.onPesPayload(payload(VIDEO_PID, VIDEO_PTS, mpeg2Key(704, 480, 3)));
      sink.onPesPayload(payload(VIDEO_PID, VIDEO_PTS + 3003, new byte[0]));
      sink.onPesPayload(payload(VIDEO_PID, VIDEO_PTS + 6006, mpeg2Inter()));
      sink.onEnd(cleanDecode());

      for (EbmlReader.Block b : EbmlReader.blocks(Files.readAllBytes(out.toPath()))) {
         assertTrue(b.data.length > 0, "a block with no frame in it is not a legal SimpleBlock");
      }
   }

   @Test
   void anExcisedRegionStaysAGapAndIsNotedWhereItHappened() throws Exception {
      // This is a remux, so a cut region stays as many seconds of timeline as it was. Closing
      // it up would move everything after it against the SkipMode marks and the captions.
      File out = work.resolve("gap.mkv").toFile();
      MuxSink sink = new MuxSink(out);
      sink.onProgram(program(stream(VIDEO_PID, MPEG2)));
      sink.onPesPayload(payload(VIDEO_PID, VIDEO_PTS, mpeg2Key(704, 480, 3)));
      sink.onPesPayload(payload(VIDEO_PID, VIDEO_PTS + 90000, mpeg2Inter()));
      sink.onDiscontinuity(VIDEO_PID, excision(9200000, 48936));
      sink.onPesPayload(payload(VIDEO_PID, VIDEO_PTS + 40 * 90000L, mpeg2Key(704, 480, 3)));
      sink.onEnd(cleanDecode());

      List<EbmlReader.Block> blocks = EbmlReader.blocks(Files.readAllBytes(out.toPath()));
      assertEquals(3, blocks.size());
      assertEquals(40000, blocks.get(2).ms, "39 seconds missing is still 39 seconds of file");
      // The note is read against a player's seek bar, so it names where the recording had
      // reached rather than a byte offset nobody can go and look at.
      assertEquals(1, sink.getNotes().size());
      assertTrue(sink.getNotes().get(0).startsWith("pid=0x0031 at ~00:00:01.000"),
         sink.getNotes().get(0));
      assertTrue(sink.getNotes().get(0).contains("EXCISION"), sink.getNotes().get(0));
   }

   private static List<EbmlReader.Block> onTrack(List<EbmlReader.Block> blocks, int track) {
      List<EbmlReader.Block> out = new ArrayList<EbmlReader.Block>();
      for (EbmlReader.Block b : blocks) {
         if (b.track == track) out.add(b);
      }
      return out;
   }
}
