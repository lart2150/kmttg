package com.tivo.kmttg.mux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tivo.kmttg.mux.mkv.MkvMuxer;

// Cover for Chapters, which carry the SkipMode segments. The part with something to get wrong
// is the clamping: a SkipMode window opens 30 s before the recording and closes 30 s after it,
// so the first and last chapter always land outside the file and have to be brought back in
// without leaving a run of empty ones behind.
public class MkvChaptersTest {

   @TempDir
   Path work;

   private static final byte[] CHAPTERS = {0x10, 0x43, (byte)0xA7, 0x70};

   private static MkvMuxer.Track videoTrack() {
      MkvMuxer.Track t = new MkvMuxer.Track();
      t.number = 1;
      t.type = MkvMuxer.TYPE_VIDEO;
      t.codecId = "V_MPEG2";
      t.width = 720;
      t.height = 480;
      return t;
   }

   // Two samples one frame apart, so the file's last timestamp is 33 ms.
   private byte[] muxWithChapters(List<MkvMuxer.Chapter> chapters) throws Exception {
      File out = work.resolve("chapters.mkv").toFile();
      MkvMuxer m = new MkvMuxer(out);
      m.addTrack(videoTrack());
      if (chapters != null) m.addChapters(chapters);
      m.addSample(1, 0, true, new byte[]{0, 0, 1, 0x00, 0x00, 0x08, 9, 9});
      m.addSample(1, 33, false, new byte[]{0, 0, 1, 0x00, 0x00, 0x10, 9, 9});
      m.close();
      return Files.readAllBytes(out.toPath());
   }

   private static List<MkvMuxer.Chapter> list(MkvMuxer.Chapter... c) {
      List<MkvMuxer.Chapter> l = new ArrayList<MkvMuxer.Chapter>();
      for (MkvMuxer.Chapter x : c) l.add(x);
      return l;
   }

   @Test
   void chaptersAreWrittenAndReachableFromTheSeekHead() throws Exception {
      byte[] f = muxWithChapters(list(
         new MkvMuxer.Chapter(0, 20, "Segment 1"),
         new MkvMuxer.Chapter(20, 33, "Commercials 1")));
      // Twice: once as a SeekID inside the SeekHead, once as the element itself. One
      // occurrence would mean the SeekHead never reserved an entry for it.
      assertEquals(2, count(f, CHAPTERS), "Chapters: a SeekHead reference plus the element");
      String s = new String(f, "ISO-8859-1");
      assertTrue(s.contains("Segment 1"));
      assertTrue(s.contains("Commercials 1"));
   }

   @Test
   void noChaptersMeansNoEmptyElement() throws Exception {
      assertEquals(0, count(muxWithChapters(null), CHAPTERS),
         "an empty Chapters element would be noise");
   }

   @Test
   void chaptersOutsideTheRecordingAreClampedNotWritten() throws Exception {
      // What a real SkipMode window looks like against a short file: the first segment starts
      // before the recording, and everything from the second break on is past its end.
      byte[] f = muxWithChapters(list(
         new MkvMuxer.Chapter(-28000, 20, "Segment 1"),
         new MkvMuxer.Chapter(20, 5000, "Commercials 1"),
         new MkvMuxer.Chapter(5000, 9000, "Segment 2"),
         new MkvMuxer.Chapter(9000, 12000, "Commercials 2")));
      String s = new String(f, "ISO-8859-1");
      assertTrue(s.contains("Segment 1"), "the overhanging first chapter is kept, clamped");
      assertTrue(s.contains("Commercials 1"), "a chapter that only overhangs the end is kept");
      assertTrue(! s.contains("Segment 2"),
         "a chapter entirely past the end would show as a zero length entry in the menu");
      assertTrue(! s.contains("Commercials 2"));
   }

   @Test
   void everythingPastTheEndStillLeavesAValidElement() throws Exception {
      // Degenerate, but the SeekHead already reserved an entry: writing nothing here would
      // leave that entry pointing at position zero. The placeholder is deliberately NOT named
      // after the marks that collapsed - everything landing past the end means they did not
      // describe this recording, so calling the whole file "Segment 1" would assert something
      // about it that is not true.
      byte[] f = muxWithChapters(list(new MkvMuxer.Chapter(60000, 70000, "Segment 1")));
      assertEquals(2, count(f, CHAPTERS), "the reserved SeekHead entry must still be patched");
      String s = new String(f, "ISO-8859-1");
      assertTrue(s.contains("Chapter 1"), "a neutral placeholder is written");
      assertTrue(! s.contains("Segment 1"), "the collapsed mark's name is not reused");
   }

   @Test
   void chaptersMustBeDeclaredBeforeTheHeader() throws Exception {
      File out = work.resolve("late.mkv").toFile();
      MkvMuxer m = new MkvMuxer(out);
      m.addTrack(videoTrack());
      m.addSample(1, 0, true, new byte[]{0, 0, 1, 0x00, 0x00, 0x08, 9});
      try {
         m.addChapters(list(new MkvMuxer.Chapter(0, 10, "too late")));
         org.junit.jupiter.api.Assertions.fail("should have refused chapters after the header");
      } catch (IllegalStateException expected) {
         // as designed
      }
      m.close();
   }

   private static int count(byte[] hay, byte[] needle) {
      int n = 0;
      outer:
      for (int i = 0; i + needle.length <= hay.length; i++) {
         for (int j = 0; j < needle.length; j++) if (hay[i+j] != needle[j]) continue outer;
         n++;
      }
      return n;
   }
}
