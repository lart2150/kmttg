package com.tivo.kmttg.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

// Cover for the parts of the streaming session that are decidable without a TiVo: the client
// identity it presents, and reading the box's error codes back out of a refusal.
public class HlsStreamTest {

   // The uuid is the only handle the box gives back on our own sessions - it echoes it in
   // /sysinfo/json/clients - and the orphan sweep releases a session by matching it. So it has
   // to come out the same every time for a given TiVo, including after a crash and restart.
   @Test
   public void clientUuidIsStableForATivo() {
      String first = HlsStream.clientUuid("84900019045ED87");
      assertEquals(first, HlsStream.clientUuid("84900019045ED87"));
   }

   // Scoped to the box, so a kmttg talking to two TiVos does not present one identity to both
   // and then reclaim the wrong box's session.
   @Test
   public void clientUuidDiffersPerTivo() {
      assertNotEquals(HlsStream.clientUuid("84900019045ED87"),
         HlsStream.clientUuid("84900019045ED88"));
   }

   // The field is called clientUuid. The box accepts any string today, so this is about not
   // relying on that.
   @Test
   public void clientUuidIsAWellFormedUuid() {
      String uuid = HlsStream.clientUuid("84900019045ED87");
      assertEquals(36, uuid.length());
      // Round trips through the parser, and back to the same text.
      assertEquals(uuid, UUID.fromString(uuid).toString());
      assertTrue(uuid.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
         "not a uuid: " + uuid);
   }

   // The 400 bodies carry the reason, and the difference between them decides whether a
   // recording is retried or written off - so the code has to survive being read back.
   @Test
   public void readsTheTranscoderErrorOutOfARefusal() {
      String body = "{ \"contextid\":\"3452677828\", \"eTranscoderError\":\"0x0501\","
         + " \"strTranscoderError\":\"Transcoder Session Not Found\", \"httpErrCode\":\"409\" }";
      assertEquals(0x0501, HlsStream.transcoderError(body));
      assertEquals("SESSION_NOT_FOUND", HlsStream.transcoderErrorName(0x0501));
   }

   @Test
   public void namesTheCodesThatDecideRetryVersusGiveUp() {
      // Not transcoded yet - the one 400 that means "wait", not "no".
      assertEquals("BADLY_FORMED_REQUEST", HlsStream.transcoderErrorName(0x0202));
      assertEquals("MAX_SESSIONS_EXCEEDED", HlsStream.transcoderErrorName(0x0206));
      // Permanent for the recording, whatever kmttg does next.
      assertEquals("SESSION_CONTENT_PROTECTED", HlsStream.transcoderErrorName(0x0504));
      assertEquals("SESSION_RECORDING_TOO_LONG", HlsStream.transcoderErrorName(0x0503));
      // Anything unrecognised still has to print as something a log reader can chase.
      assertEquals("0x777", HlsStream.transcoderErrorName(0x777));
   }

   @Test
   public void aBodyThatIsNotAnErrorReportsNoCode() {
      assertEquals(0, HlsStream.transcoderError(null));
      assertEquals(0, HlsStream.transcoderError("#EXTM3U"));
      assertEquals(0, HlsStream.transcoderError("{}"));
   }

   @Test
   public void decryptRefusesInputItCannotUse() {
      assertNull(HlsStream.decrypt(null, new byte[16], 0));
      assertNull(HlsStream.decrypt(new byte[188], null, 0));
      // Shorter than one AES block, so there is nothing whole to decrypt.
      assertNull(HlsStream.decrypt(new byte[8], new byte[16], 0));
   }

   // The rule the whole retryLater machinery exists to enforce: a refusal that is about the
   // recording may be remembered, and anything about the box must not be. Getting this
   // backwards writes a permanent SkipModeRejects row for a transient failure, and the
   // recording is never streamed again until TiVo reissues its clipMetadata.
   @Test
   public void onlyRefusalsAboutTheRecordingArePermanent() {
      assertTrue(HlsStream.permanent(0x0502), "SESSION_RECORDING_NOT_FOUND");
      assertTrue(HlsStream.permanent(0x0503), "SESSION_RECORDING_TOO_LONG");
      assertTrue(HlsStream.permanent(0x0504), "SESSION_CONTENT_PROTECTED");
      assertTrue(HlsStream.permanent(0x0505), "SESSION_CONTENT_INVALID_FORMAT");

      assertFalse(HlsStream.permanent(0x0206), "MAX_SESSIONS_EXCEEDED is the box being busy");
      assertFalse(HlsStream.permanent(0x0506), "SESSION_RTT_FAILED is the network");
      assertFalse(HlsStream.permanent(0x0501), "SESSION_NOT_FOUND mid-read is the session dying");
      assertFalse(HlsStream.permanent(0x0202), "BADLY_FORMED_REQUEST here means not yet ready");
      assertFalse(HlsStream.permanent(0), "an unrecognised refusal must not be written off");
   }

   // Only Disabled is a settled answer. The others clear on their own, so a refresh taken
   // while the box was booting must not write off every recording in it.
   @Test
   public void namesTheStreamingStatesThatResolveThemselves() {
      assertEquals("Ready", HlsStream.streamingStateName(6));
      assertEquals("Disabled", HlsStream.streamingStateName(4));
      assertEquals("Initializing", HlsStream.streamingStateName(0));
      assertEquals("RebootRequired", HlsStream.streamingStateName(2));
      assertEquals("ThermalShutdown", HlsStream.streamingStateName(7));
      assertTrue(HlsStream.streamingStateName(99).contains("99"));
   }
}
