package com.tivo.kmttg.mux;

import static com.tivo.kmttg.mux.EbmlReader.AUDIO;
import static com.tivo.kmttg.mux.EbmlReader.CHANNELS;
import static com.tivo.kmttg.mux.EbmlReader.CODEC_ID;
import static com.tivo.kmttg.mux.EbmlReader.CODEC_PRIVATE;
import static com.tivo.kmttg.mux.EbmlReader.DISPLAY_HEIGHT;
import static com.tivo.kmttg.mux.EbmlReader.DISPLAY_UNIT;
import static com.tivo.kmttg.mux.EbmlReader.DISPLAY_WIDTH;
import static com.tivo.kmttg.mux.EbmlReader.FLAG_DEFAULT;
import static com.tivo.kmttg.mux.EbmlReader.LANGUAGE;
import static com.tivo.kmttg.mux.EbmlReader.PIXEL_HEIGHT;
import static com.tivo.kmttg.mux.EbmlReader.PIXEL_WIDTH;
import static com.tivo.kmttg.mux.EbmlReader.SAMPLING_FREQ;
import static com.tivo.kmttg.mux.EbmlReader.TRACK_NAME;
import static com.tivo.kmttg.mux.EbmlReader.VIDEO;
import static com.tivo.kmttg.mux.MuxStreams.AC3;
import static com.tivo.kmttg.mux.MuxStreams.AUD;
import static com.tivo.kmttg.mux.MuxStreams.AUDIO_PID;
import static com.tivo.kmttg.mux.MuxStreams.BROKEN_SPS;
import static com.tivo.kmttg.mux.MuxStreams.H264;
import static com.tivo.kmttg.mux.MuxStreams.I_SLICE;
import static com.tivo.kmttg.mux.MuxStreams.MPEG2;
import static com.tivo.kmttg.mux.MuxStreams.PPS;
import static com.tivo.kmttg.mux.MuxStreams.SPS_720x480;
import static com.tivo.kmttg.mux.MuxStreams.VIDEO_PID;
import static com.tivo.kmttg.mux.MuxStreams.ac3FiveOne;
import static com.tivo.kmttg.mux.MuxStreams.ac3Unit;
import static com.tivo.kmttg.mux.MuxStreams.accessUnit;
import static com.tivo.kmttg.mux.MuxStreams.concat;
import static com.tivo.kmttg.mux.MuxStreams.languageDescriptor;
import static com.tivo.kmttg.mux.MuxStreams.mpeg2Inter;
import static com.tivo.kmttg.mux.MuxStreams.mpeg2Key;
import static net.straylightlabs.tivolibre.FrameFixtures.cleanDecode;
import static net.straylightlabs.tivolibre.FrameFixtures.payload;
import static net.straylightlabs.tivolibre.FrameFixtures.program;
import static net.straylightlabs.tivolibre.FrameFixtures.stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.straylightlabs.tivolibre.ElementaryStreamInfo;
import net.straylightlabs.tivolibre.FrameFixtures;
import net.straylightlabs.tivolibre.TivoMetadata;

// What the streams themselves say about a track, and what of it reaches the track header.
// Matroska puts Tracks ahead of the first cluster and will not let it be revised, so anything
// wrong here is wrong for the life of the file - and a wrong display size or a wrongly
// labelled language is the kind of wrong nobody notices until they are watching it.
public class MuxSinkTracksTest {

   @TempDir
   Path work;

   private static final long PTS = 8240316184L;

   @Test
   void theLanguageDescriptorNamesTheTrack() throws Exception {
      // A broadcast PMT puts the ISO 639 descriptor behind others, so the walk has to step
      // over what it does not recognise rather than only look at the front of the loop.
      byte[] registration = {0x05, 0x04, 'A', 'C', '-', '3'};
      byte[] f = muxWithAudio("lang.mkv", null,
         concat(registration, languageDescriptor("SPA")));
      assertEquals("spa", EbmlReader.trackEntries(f).get(1).stringOr(LANGUAGE, null));
   }

   @Test
   void aDescriptorTheRecordingCutShortLeavesTheTrackUndetermined() throws Exception {
      // A PMT can declare more descriptor bytes than its packet held, and the library hands
      // over what actually arrived. Taking the code out of a field nobody finished is
      // guessing, and "und" is a better answer than a language that might be the wrong one.
      byte[] truncated = {0x0A, 0x08, 'e', 'n', 'g'};
      assertEquals("und", EbmlReader.trackEntries(muxWithAudio("cut.mkv", null, truncated))
         .get(1).stringOr(LANGUAGE, null));
      // Three letters is the whole of the field, so anything shorter is not a language.
      byte[] tooShort = {0x0A, 0x02, 'e', 'n'};
      assertEquals("und", EbmlReader.trackEntries(muxWithAudio("short.mkv", null, tooShort))
         .get(1).stringOr(LANGUAGE, null));
   }

   @Test
   void theMetadataLanguageIsOnlyAppliedToASingleAudioTrack() throws Exception {
      // TiVo's remux strips the PMT's language descriptor, so the guide text is the only
      // source left. It says what the programme is in rather than what each track is in, so
      // on a broadcast carrying a SAP it would label both tracks alike - and a player set to
      // prefer that language would then pick whichever came first rather than the one it
      // wants. "und" on both is the worse label and the better answer.
      TivoMetadata spanish = FrameFixtures.metadata(
         "<showing><descriptionLanguage>spa-ESP</descriptionLanguage></showing>");

      byte[] one = muxWithAudio("one-audio.mkv", spanish, new byte[0]);
      assertEquals("spa", EbmlReader.trackEntries(one).get(1).stringOr(LANGUAGE, null));

      byte[] sap = muxWithAudio("sap.mkv", spanish, new byte[0], new byte[0]);
      assertEquals("und", EbmlReader.trackEntries(sap).get(1).stringOr(LANGUAGE, null));
      assertEquals("und", EbmlReader.trackEntries(sap).get(2).stringOr(LANGUAGE, null));
   }

   @Test
   void onlyTheFirstTrackOfEachKindIsTheDefaultOne() throws Exception {
      // Matroska reads an absent FlagDefault as "yes", so a second audio track has to say no
      // out loud or a player is told both are equally the one to pick.
      List<EbmlReader> tracks = EbmlReader.trackEntries(
         muxWithAudio("defaults.mkv", null, new byte[0], new byte[0]));
      assertEquals(3, tracks.size());
      assertFalse(tracks.get(0).has(FLAG_DEFAULT), "the video track says nothing, so it is");
      assertFalse(tracks.get(1).has(FLAG_DEFAULT), "nor does the first audio track");
      assertEquals(0, tracks.get(2).uintOr(FLAG_DEFAULT, 1), "the second one has to");
   }

   @Test
   void theAudioTrackIsNamedForItsChannelLayout() throws Exception {
      // The CodecID already says AC-3. What a viewer actually chooses on is whether this is
      // the 5.1 track, which is the part no other element states.
      File out = work.resolve("surround.mkv").toFile();
      MuxSink sink = new MuxSink(out);
      sink.onProgram(program(stream(AUDIO_PID, AC3)));
      sink.onPesPayload(payload(AUDIO_PID, PTS, ac3FiveOne()));
      sink.onEnd(cleanDecode());

      EbmlReader t = EbmlReader.trackEntries(Files.readAllBytes(out.toPath())).get(0);
      assertEquals("Surround 5.1", t.stringOr(TRACK_NAME, null));
      assertEquals(6, t.child(AUDIO).uintOr(CHANNELS, -1), "acmod 3/2 plus the LFE");
      assertEquals(48000.0, t.child(AUDIO).child(SAMPLING_FREQ).f64(), 0.001);
   }

   @Test
   void anAnamorphicAspectCodeDerivesTheDisplaySizeFromTheHeight() throws Exception {
      // aspect_ratio_information states a DISPLAY aspect, not a pixel one, so a 704 wide
      // picture is 16:9 by declaration rather than by its dimensions. Taking the dimensions
      // at face value plays the whole recording squashed.
      EbmlReader wide = video(muxVideo("wide.mkv", mpeg2Key(704, 480, 3)));
      assertEquals(704, wide.uintOr(PIXEL_WIDTH, -1));
      assertEquals(853, wide.uintOr(DISPLAY_WIDTH, -1));
      assertEquals(480, wide.uintOr(DISPLAY_HEIGHT, -1));

      EbmlReader fourThree = video(muxVideo("fourthree.mkv", mpeg2Key(704, 480, 2)));
      assertEquals(640, fourThree.uintOr(DISPLAY_WIDTH, -1));

      // Square pixels need no hint, and stating one is only a chance to state it wrong.
      EbmlReader square = video(muxVideo("square.mkv", mpeg2Key(640, 480, 1)));
      assertFalse(square.has(DISPLAY_WIDTH));
   }

   @Test
   void h264StatesTheAspectAsARatioRatherThanRoundedPixels() throws Exception {
      // 720 at SAR 40:33 is 872.7 display pixels, and rounding that to 873 turns a clean
      // 20:11 into 291:160. DisplayUnit 3 says the pair is a ratio rather than a size.
      byte[] f = muxH264("avc.mkv", accessUnit(AUD, SPS_720x480, PPS, I_SLICE));
      EbmlReader t = EbmlReader.trackEntries(f).get(0);
      assertEquals("V_MPEG4/ISO/AVC", t.stringOr(CODEC_ID, null));
      assertEquals(720, t.child(VIDEO).uintOr(PIXEL_WIDTH, -1));
      assertEquals(480, t.child(VIDEO).uintOr(PIXEL_HEIGHT, -1));
      assertEquals(20, t.child(VIDEO).uintOr(DISPLAY_WIDTH, -1));
      assertEquals(11, t.child(VIDEO).uintOr(DISPLAY_HEIGHT, -1));
      assertEquals(3, t.child(VIDEO).uintOr(DISPLAY_UNIT, -1));
   }

   @Test
   void theParameterSetsAreCollectedAcrossSeparateUnits() throws Exception {
      // avcC needs both sets and nothing makes a broadcast put them in one access unit, so a
      // track that only accepts a unit carrying both can wait out the whole recording.
      byte[] f = muxH264("split.mkv",
         accessUnit(AUD, SPS_720x480),
         accessUnit(PPS, I_SLICE));
      byte[] avcC = EbmlReader.trackEntries(f).get(0).child(CODEC_PRIVATE).data;
      assertEquals(1, avcC[0] & 0xFF, "configurationVersion");
      assertEquals(SPS_720x480.length, ((avcC[6] & 0xFF) << 8) | (avcC[7] & 0xFF));
      assertEquals(PPS.length, ppsLength(avcC));
   }

   @Test
   void aDamagedParameterSetDoesNotLockTheTrackOut() throws Exception {
      // Keeping the first SPS unconditionally meant one damaged set early in a recording shut
      // the track out for good: every later good one was skipped as already held, so the
      // track never configured and was dropped.
      byte[] f = muxH264("damaged.mkv",
         accessUnit(AUD, BROKEN_SPS, PPS),
         accessUnit(AUD, SPS_720x480, PPS, I_SLICE));
      EbmlReader t = EbmlReader.trackEntries(f).get(0);
      assertEquals(720, t.child(VIDEO).uintOr(PIXEL_WIDTH, -1));
      byte[] avcC = t.child(CODEC_PRIVATE).data;
      assertEquals(SPS_720x480.length, ((avcC[6] & 0xFF) << 8) | (avcC[7] & 0xFF),
         "the parameter set that parsed is the one that reached avcC");
   }

   // avcC: 6 bytes of header, then one SPS, then the PPS count and the first PPS length.
   private static int ppsLength(byte[] avcC) {
      int at = 8 + (((avcC[6] & 0xFF) << 8) | (avcC[7] & 0xFF));
      return ((avcC[at + 1] & 0xFF) << 8) | (avcC[at + 2] & 0xFF);
   }

   private static EbmlReader video(byte[] file) {
      return EbmlReader.trackEntries(file).get(0).child(VIDEO);
   }

   private byte[] muxVideo(String name, byte[] first) throws Exception {
      File out = work.resolve(name).toFile();
      MuxSink sink = new MuxSink(out);
      sink.onProgram(program(stream(VIDEO_PID, MPEG2)));
      sink.onPesPayload(payload(VIDEO_PID, PTS, first));
      sink.onPesPayload(payload(VIDEO_PID, PTS + 3003, mpeg2Inter()));
      sink.onEnd(cleanDecode());
      return Files.readAllBytes(out.toPath());
   }

   private byte[] muxH264(String name, byte[]... units) throws Exception {
      File out = work.resolve(name).toFile();
      MuxSink sink = new MuxSink(out);
      sink.onProgram(program(stream(VIDEO_PID, H264)));
      for (int i = 0; i < units.length; i++) {
         sink.onPesPayload(payload(VIDEO_PID, PTS + i * 3003L, units[i]));
      }
      sink.onEnd(cleanDecode());
      return Files.readAllBytes(out.toPath());
   }

   // One video track and an audio track per set of descriptors given, which is how a SAP
   // broadcast arrives: two AC-3 streams that the PMT describes separately.
   private byte[] muxWithAudio(String name, TivoMetadata metadata, byte[]... descriptors)
         throws Exception {
      File out = work.resolve(name).toFile();
      MuxSink sink = new MuxSink(out);
      if (metadata != null) sink.onMetadata(metadata);
      ElementaryStreamInfo[] streams = new ElementaryStreamInfo[1 + descriptors.length];
      streams[0] = stream(VIDEO_PID, MPEG2);
      for (int i = 0; i < descriptors.length; i++) {
         streams[i + 1] = stream(AUDIO_PID + i, AC3, descriptors[i]);
      }
      sink.onProgram(program(streams));
      sink.onPesPayload(payload(VIDEO_PID, PTS, mpeg2Key(704, 480, 3)));
      for (int i = 0; i < descriptors.length; i++) {
         sink.onPesPayload(payload(AUDIO_PID + i, PTS, ac3Unit(1)));
      }
      sink.onPesPayload(payload(VIDEO_PID, PTS + 3003, mpeg2Inter()));
      sink.onEnd(cleanDecode());
      return Files.readAllBytes(out.toPath());
   }
}
