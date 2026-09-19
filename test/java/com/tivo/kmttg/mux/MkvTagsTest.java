package com.tivo.kmttg.mux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tivo.kmttg.mux.mkv.MkvMuxer;

// Cover for the metadata elements: Tags, Attachments and the SeekHead that lets a player find
// them without scanning. All three are written after the clusters, so unlike Tracks they cost
// no second pass - but the SeekHead is written before them and patched, which is the part with
// something to get wrong.
public class MkvTagsTest {

   @TempDir
   Path work;

   private static MkvMuxer.Track videoTrack() {
      MkvMuxer.Track t = new MkvMuxer.Track();
      t.number = 1;
      t.type = MkvMuxer.TYPE_VIDEO;
      t.codecId = "V_MPEG2";
      t.width = 720;
      t.height = 480;
      return t;
   }

   private byte[] muxWithMetadata(boolean withTags, boolean withArt) throws Exception {
      File out = work.resolve("tagged.mkv").toFile();
      MkvMuxer m = new MkvMuxer(out);
      m.addTrack(videoTrack());
      if (withTags) {
         m.addTag(new MkvMuxer.Tag(MkvMuxer.TARGET_COLLECTION, "TITLE", "Father Brown"));
         m.addTag(new MkvMuxer.Tag(MkvMuxer.TARGET_EPISODE, "TITLE", "The Forensic Nun"));
         m.addTag(new MkvMuxer.Tag(MkvMuxer.TARGET_EPISODE, "SYNOPSIS", "A nun investigates."));
         m.addTag(new MkvMuxer.Tag(MkvMuxer.TARGET_EPISODE, "ACTOR", "Mark Williams"));
         m.addTag(new MkvMuxer.Tag(MkvMuxer.TARGET_EPISODE, "ACTOR", "Sorcha Cusack"));
      }
      if (withArt) {
         m.addAttachment("cover.jpg", "image/jpeg", new byte[]{(byte)0xFF, (byte)0xD8, 1, 2, 3});
      }
      m.addSample(1, 0, true, new byte[]{0, 0, 1, 0x00, 0x00, 0x08, 9, 9});
      m.addSample(1, 33, false, new byte[]{0, 0, 1, 0x00, 0x00, 0x10, 9, 9});
      m.close();
      return Files.readAllBytes(out.toPath());
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

   private static final byte[] SEEK_HEAD   = {0x11, 0x4D, (byte)0x9B, 0x74};
   private static final byte[] TAGS        = {0x12, 0x54, (byte)0xC3, 0x67};
   private static final byte[] ATTACHMENTS = {0x19, 0x41, (byte)0xA4, 0x69};

   @Test
   void tagsAndAttachmentsAreWritten() throws Exception {
      byte[] f = muxWithMetadata(true, true);
      // Twice each, and that is the point: once as a SeekID inside the SeekHead pointing at
      // the element, and once as the element itself. One occurrence would mean the SeekHead
      // never reserved an entry for it.
      assertEquals(2, count(f, TAGS), "Tags: a SeekHead reference plus the element");
      assertEquals(2, count(f, ATTACHMENTS), "Attachments: a SeekHead reference plus the element");
      assertTrue(new String(f, "ISO-8859-1").contains("The Forensic Nun"));
      assertTrue(new String(f, "ISO-8859-1").contains("Sorcha Cusack"),
         "both ACTOR values must survive; Matroska tags are multi-valued and MP4 drops them");
      assertTrue(new String(f, "ISO-8859-1").contains("cover.jpg"));
   }

   @Test
   void seekHeadIsAlwaysPresentAndPrecedesWhatItPointsAt() throws Exception {
      byte[] f = muxWithMetadata(true, true);
      assertEquals(1, count(f, SEEK_HEAD), "exactly one SeekHead");
      int seek = indexOf(f, SEEK_HEAD);
      assertTrue(seek < 200, "SeekHead belongs at the head of the segment, not buried");
      // Compare against the LAST occurrence, which is the element. The first is the SeekID
      // inside the SeekHead itself, so comparing against that would pass trivially and prove
      // nothing about whether the pointer actually precedes its target.
      assertTrue(seek < lastIndexOf(f, TAGS), "SeekHead must precede the Tags element");
      assertTrue(seek < lastIndexOf(f, ATTACHMENTS), "SeekHead must precede Attachments");
      // And the elements themselves belong after the media, not in the header.
      assertTrue(lastIndexOf(f, TAGS) > 200, "Tags are written after the clusters");
   }

   @Test
   void noMetadataMeansNoEmptyElements() throws Exception {
      byte[] f = muxWithMetadata(false, false);
      assertEquals(0, count(f, TAGS), "an empty Tags element would be noise");
      assertEquals(0, count(f, ATTACHMENTS));
      assertEquals(1, count(f, SEEK_HEAD), "SeekHead still wanted for Info, Tracks and Cues");
   }

   @Test
   void metadataMustBeDeclaredBeforeTheHeader() throws Exception {
      // The SeekHead reserves one entry per element up front, so a late tag would have
      // nowhere to be pointed from. Failing loudly beats writing an unreachable element.
      File out = work.resolve("late.mkv").toFile();
      MkvMuxer m = new MkvMuxer(out);
      m.addTrack(videoTrack());
      m.addSample(1, 0, true, new byte[]{0, 0, 1, 0x00, 0x00, 0x08, 9});
      try {
         m.addTag(new MkvMuxer.Tag(MkvMuxer.TARGET_EPISODE, "TITLE", "too late"));
         org.junit.jupiter.api.Assertions.fail("should have refused a tag after the header");
      } catch (IllegalStateException expected) {
         // as designed
      }
      m.close();
   }

   private static int lastIndexOf(byte[] hay, byte[] needle) {
      outer:
      for (int i = hay.length - needle.length; i >= 0; i--) {
         for (int j = 0; j < needle.length; j++) if (hay[i+j] != needle[j]) continue outer;
         return i;
      }
      return -1;
   }

   private static int indexOf(byte[] hay, byte[] needle) {
      outer:
      for (int i = 0; i + needle.length <= hay.length; i++) {
         for (int j = 0; j < needle.length; j++) if (hay[i+j] != needle[j]) continue outer;
         return i;
      }
      return -1;
   }
}
