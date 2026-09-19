package com.tivo.kmttg.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

// Cover for the playlists a TiVo streaming session serves. Both fixtures are verbatim captures
// from a live session, the variant one truncated after the point that matters - segment 0x13 -
// because the real one runs to 0x6E2 and the interesting behaviour is all in the first twenty.
public class HlsPlaylistTest {

   private String fixture(String name) throws Exception {
      try (InputStream is = HlsPlaylistTest.class.getResourceAsStream("/fixtures/" + name)) {
         assertNotNull(is, "missing fixture: /fixtures/" + name);
         return new String(is.readAllBytes(), StandardCharsets.UTF_8);
      }
   }

   @Test
   public void readsEveryVariant() throws Exception {
      List<HlsPlaylist.Variant> variants = HlsPlaylist.variants(fixture("hls_master.m3u8"));
      assertEquals(7, variants.size());
      assertEquals(934000L, variants.get(0).bandwidth);
      assertEquals("2078994755/705246205/playlist.m3u8", variants.get(0).uri);
   }

   // The box lists variants best-first rather than cheapest-first, so picking one means
   // comparing rather than taking an end of the list.
   @Test
   public void picksTheCheapestVariantToTranscode() throws Exception {
      List<HlsPlaylist.Variant> variants = HlsPlaylist.variants(fixture("hls_master.m3u8"));
      HlsPlaylist.Variant lowest = HlsPlaylist.lowestBandwidth(variants);
      assertEquals(440650L, lowest.bandwidth);
      assertEquals("2078994755/704936205/playlist.m3u8", lowest.uri);
   }

   @Test
   public void readsKeyAndSegmentsFromTheVariant() throws Exception {
      HlsPlaylist.Media media = HlsPlaylist.media(fixture("hls_variant.m3u8"));
      assertEquals("keyfile.kbin", media.keyUri);
      assertEquals(2, media.targetDurationSec);
      assertEquals(20, media.segments.size());
      assertEquals("0", media.segments.get(0));
      assertEquals("13", media.segments.get(19));
   }

   // The whole reason this is worth a test: the names are hex. A decimal reading agrees for
   // the first ten and then silently drifts, and the resulting IV is wrong in a way that still
   // decrypts to a readable stream.
   @Test
   public void segmentNamesAreHexadecimal() throws Exception {
      HlsPlaylist.Media media = HlsPlaylist.media(fixture("hls_variant.m3u8"));
      // Consecutive segments must produce consecutive sequence numbers all the way through
      // the A..F run, which is exactly where a decimal parse falls apart.
      for (int i = 0; i < media.segments.size(); ++i) {
         assertEquals(i, HlsPlaylist.sequenceNumber(media.segments.get(i)),
            "segment " + media.segments.get(i) + " should be sequence " + i);
      }
      assertEquals(10, HlsPlaylist.sequenceNumber("A"));
      assertEquals(19, HlsPlaylist.sequenceNumber("13"));
      assertEquals(1762, HlsPlaylist.sequenceNumber("6E2"));
   }

   @Test
   public void rejectsASegmentNameThatIsNotANumber() {
      assertEquals(-1, HlsPlaylist.sequenceNumber("keyfile.kbin"));
      assertEquals(-1, HlsPlaylist.sequenceNumber(null));
   }

   // The sequence number occupies the low bytes of a 16 byte IV. Getting the position wrong is
   // the failure that hides, because the high bytes are zero either way.
   @Test
   public void buildsABigEndianIvFromTheSequenceNumber() {
      byte[] iv = HlsPlaylist.iv(0x13);
      assertEquals(16, iv.length);
      for (int i = 0; i < 15; ++i) assertEquals(0, iv[i], "byte " + i + " should be zero");
      assertEquals(0x13, iv[15]);

      byte[] big = HlsPlaylist.iv(0x6E2);
      assertEquals(0x06, big[14]);
      assertEquals((byte) 0xE2, big[15]);
   }

   @Test
   public void survivesPlaylistsItCannotUnderstand() {
      assertTrue(HlsPlaylist.variants(null).isEmpty());
      assertTrue(HlsPlaylist.variants("#EXTM3U\n").isEmpty());
      assertNull(HlsPlaylist.lowestBandwidth(HlsPlaylist.variants("")));
      HlsPlaylist.Media empty = HlsPlaylist.media("#EXTM3U\n#EXT-X-ENDLIST\n");
      assertNull(empty.keyUri);
      assertTrue(empty.segments.isEmpty());
   }

   // A STREAM-INF with nothing after it must not take the next tag as a URI.
   @Test
   public void ignoresAVariantTagWithNoUri() {
      List<HlsPlaylist.Variant> v = HlsPlaylist.variants(
         "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=100\n#EXT-X-ENDLIST\n");
      assertTrue(v.isEmpty());
   }
}
