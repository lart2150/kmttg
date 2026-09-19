package com.tivo.kmttg.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;

// The parts of the artwork lookup that do not need a TiVo. The rpc call itself is only
// exercised by a real run; these cover the choices around it, which is where the Show
// Information dialog and the remuxer differ - one wants a thumbnail, the other a poster.
public class ArtworkTest {

   private static JSONArray images(int... heights) throws Exception {
      JSONArray a = new JSONArray();
      for (int h : heights) {
         JSONObject o = new JSONObject();
         o.put("height", h);
         o.put("imageUrl", "http://example/img" + h + ".jpg");
         a.put(o);
      }
      return a;
   }

   @Test
   void picksTheHeightClosestToWhatTheCallerAsked() throws Exception {
      JSONArray a = images(100, 180, 400, 1080);
      assertEquals("http://example/img180.jpg", artwork.pickUrl(a, 180));
      assertEquals("http://example/img100.jpg", artwork.pickUrl(a, 90));
      assertEquals("http://example/img400.jpg", artwork.pickUrl(a, 420));
   }

   @Test
   void largestAskForGetsTheBiggestOnOffer() throws Exception {
      // The dialog shows a 180px thumbnail; a file embedding cover art wants the real
      // poster, which is the whole reason the height is a parameter.
      JSONArray a = images(100, 180, 400, 1080);
      assertEquals("http://example/img1080.jpg", artwork.pickUrl(a, artwork.LARGEST));
   }

   @Test
   void mimeTypeComesFromTheExtensionAndDefaultsToJpeg() {
      assertEquals("image/png", artwork.mimeType("http://x/a.png"));
      assertEquals("image/gif", artwork.mimeType("http://x/a.GIF"));
      assertEquals("image/jpeg", artwork.mimeType("http://x/a.jpg"));
      // A query string must not defeat the extension check
      assertEquals("image/png", artwork.mimeType("http://x/a.png?w=400&h=600"));
      // TiVo serves jpeg, so that is the fallback rather than a guess at binary
      assertEquals("image/jpeg", artwork.mimeType("http://x/image"));
      assertEquals("image/jpeg", artwork.mimeType(null));
   }

   @Test
   void theAttachmentIsNamedCoverSoPlayersFindIt() {
      // Matroska convention: players look for an attachment literally called cover.<ext>.
      assertEquals("cover.jpg", artwork.coverName("http://x/a.jpg"));
      assertEquals("cover.png", artwork.coverName("http://x/a.png"));
      assertEquals("cover.jpg", artwork.coverName(null));
   }

   @Test
   void nothingToLookUpMeansNoRpcConnection() {
      // Guards before initRemote: with no ids there is nothing to ask for, and opening a
      // connection per job to learn that would be wasteful.
      assertNull(artwork.findUrl("SomeTivo", null, null, 180));
      assertNull(artwork.findUrl(null, "content1", "collection1", 180));
   }

   @Test
   void fetchDeclinesRatherThanThrowsOnBadInput() {
      // Cover art is best effort; a failure here must never fail a remux.
      assertNull(artwork.fetch(null));
      assertNull(artwork.fetch(""));
      assertNull(artwork.fetch("not a url"));
   }
}
