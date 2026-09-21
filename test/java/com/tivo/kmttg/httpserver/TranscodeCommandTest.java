package com.tivo.kmttg.httpserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers the two pieces of the streaming path that are plain functions: the
 * ffmpeg argument templates, and the playlist reading behind the Stream page's
 * cached and running lists.
 *
 * The templates are built as one string and handed to ffmpeg as
 * args.split(" "), so a stray double space becomes an empty argument and
 * ffmpeg refuses to start - the user sees a transcode that fails immediately
 * with nothing in the page to explain it.
 */
public class TranscodeCommandTest {

   // What the Stream page's rate menu offers, plus the bare form the handler
   // also accepts
   private static final String[] RATES = { "500k", "1000k", "2000k", "3000k", "5000k", "3000" };

   @Test
   public void transcodeArgs_splitIntoWholeArguments() {
      for (String rate : RATES) {
         for (String args : new String[] {
               TranscodeTemplates.hls("/web/cache/", rate),
               TranscodeTemplates.webm(rate) }) {
            for (String arg : args.split(" "))
               assertFalse(arg.isEmpty(), "empty argument in: " + args);
            assertFalse(args.startsWith(" ") || args.endsWith(" "), "padded: " + args);
         }
      }
   }

   // The rate reaches ffmpeg as one argument, which is why the handler refuses
   // a maxrate with a space in it
   @Test
   public void transcodeArgs_carryTheRateAsItsOwnArgument() {
      for (String rate : RATES) {
         assertEquals(rate, argAfter(TranscodeTemplates.hls("/web/cache/", rate), "-maxrate"));
         assertEquals(rate, argAfter(TranscodeTemplates.webm(rate), "-b"));
      }
   }

   // The playlist ffmpeg writes has to reference its segments by a url the
   // browser can fetch, not by their path on disk, or playback 404s
   @Test
   public void hlsArgs_pointTheSegmentListAtTheCacheUrl() {
      String args = TranscodeTemplates.hls("/web/cache/", "3000k");
      assertEquals("/web/cache/", argAfter(args, "-segment_list_entry_prefix"));
      // The caller appends the playlist file, so this has to be the last
      // argument for it to land as -segment_list's value
      assertTrue(args.endsWith(" -segment_list"), "segment list is not last in: " + args);
   }

   // crf is derived from the rate, and x264 only accepts 0 through 51
   @Test
   public void hlsArgs_keepCrfWithinTheRangeX264Accepts() {
      for (String rate : RATES) {
         int crf = Integer.parseInt(argAfter(TranscodeTemplates.hls("/web/cache/", rate), "-crf"));
         assertTrue(crf >= 0 && crf <= 51, rate + " gave crf " + crf);
      }
   }

   // The quality asked of x264 has to rise with the bit rate, not fall - crf
   // counts the other way round, so the arithmetic that derives it inverts
   @Test
   public void hlsArgs_giveAHigherRateTheBetterQuality() {
      int low = Integer.parseInt(argAfter(TranscodeTemplates.hls("/web/cache/", "500k"), "-crf"));
      int high = Integer.parseInt(argAfter(TranscodeTemplates.hls("/web/cache/", "5000k"), "-crf"));
      assertTrue(high < low, "5000k gave crf " + high + ", 500k gave " + low);
   }

   // The gate on what may be transcoded, browsed and listed. A recording named
   // by a TiVo arrives however the TiVo spelled it
   @Test
   public void videoFiles_areRecognisedByExtensionWhateverTheCase() {
      for (String name : new String[] {
            "show.mp4", "show.MP4", "show.TiVo", "show.mkv", "show.ts", "show.mpg",
            "/mnt/share/Bob's Burgers.mov", "C:\\videos\\show.M4V" })
         assertTrue(Hlsutils.isVideoFile(name), name + " was not taken for a video");
   }

   @Test
   public void nonVideoFiles_areNotRecognised() {
      for (String name : new String[] {
            "show.mp4.txt", "show.edl", "notes.txt", "show.mp3", "mp4", "show.mp4x",
            "config.ini", "" })
         assertFalse(Hlsutils.isVideoFile(name), name + " was taken for a video");
   }

   @Test
   public void playlistTime_sumsTheSegmentsWrittenSoFar(@TempDir Path dir) throws IOException {
      Path m3u8 = write(dir, "#EXTM3U\n#EXT-X-TARGETDURATION:10\n"
         + "#EXTINF:10.005000,\n/web/cache/t0-00000.ts\n"
         + "#EXTINF:9.995000,\n/web/cache/t0-00001.ts\n");
      assertEquals(20.0, Hlsutils.totalTime_m3u8(m3u8.toString()), 0.001);
   }

   // The page polls this while ffmpeg is still appending to the playlist, so a
   // line can arrive half written. Anything unreadable is worth zero seconds -
   // it used to throw, and the poll came back as a server error instead of a
   // progress figure.
   @Test
   public void playlistTime_ignoresALineItCannotRead(@TempDir Path dir) throws IOException {
      Path m3u8 = write(dir, "#EXTM3U\n#EXTINF:10.0,\n/web/cache/t0-00000.ts\n#EXTINF:");
      assertEquals(10.0, Hlsutils.totalTime_m3u8(m3u8.toString()), 0.001);
   }

   // RFC8216 allows a title after the duration, and a playlist kmttg did not
   // write itself can carry one
   @Test
   public void playlistTime_readsADurationThatCarriesATitle(@TempDir Path dir) throws IOException {
      Path m3u8 = write(dir, "#EXTM3U\n#EXTINF:10.0,Bob's Burgers\n/web/cache/t0-00000.ts\n");
      assertEquals(10.0, Hlsutils.totalTime_m3u8(m3u8.toString()), 0.001);
   }

   // A transcode whose playlist is gone still has to answer, because cleanup
   // asks about every transcode it holds, running or not
   @Test
   public void playlistOfAFileThatIsNotThere_isEmptyAndUnterminated() {
      assertEquals(0.0, Hlsutils.totalTime_m3u8("no-such-playlist.m3u8"), 0.001);
      assertTrue(Hlsutils.isPartial("no-such-playlist.m3u8"));
      assertTrue(Hlsutils.isPartial(null), "a transcode with no playlist yet");
   }

   // #EXT-X-ENDLIST is what tells a player the stream has ended - without it
   // the player sits waiting for a segment that is never coming
   @Test
   public void playlistIsPartialUntilItIsTerminated(@TempDir Path dir) throws IOException {
      Path m3u8 = write(dir, "#EXTM3U\n#EXTINF:10.0,\n/web/cache/t0-00000.ts\n");
      assertTrue(Hlsutils.isPartial(m3u8.toString()));
      Hlsutils.fixPartial(m3u8.toString());
      assertFalse(Hlsutils.isPartial(m3u8.toString()), "fixPartial did not terminate it");
   }

   /* ---- helpers ---- */

   private static Path write(Path dir, String content) throws IOException {
      Path m3u8 = dir.resolve("t0.m3u8");
      Files.write(m3u8, content.getBytes(StandardCharsets.UTF_8));
      return m3u8;
   }

   private static String argAfter(String args, String option) {
      String[] tokens = args.split(" ");
      for (int i = 0; i < tokens.length - 1; i++)
         if (tokens[i].equals(option))
            return tokens[i + 1];
      throw new AssertionError(option + " is not in: " + args);
   }
}
