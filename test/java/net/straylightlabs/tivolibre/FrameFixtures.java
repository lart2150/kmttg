package net.straylightlabs.tivolibre;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;

// What a FrameSink is handed - PesPayload, ElementaryStreamInfo, DecodeResult - are public
// final classes with package-private constructors, so nothing outside this package can build
// one and a consumer's tests have no way to drive a sink at all. Living in the library's own
// package in the test source set is that way in, and costs the library nothing.
public class FrameFixtures {

   public static ElementaryStreamInfo stream(int pid, int streamType) {
      return stream(pid, streamType, new byte[0]);
   }

   public static ElementaryStreamInfo stream(int pid, int streamType, byte[] descriptors) {
      return new ElementaryStreamInfo(pid, streamType, descriptors, 1);
   }

   // Always the complete set of streams known so far, never a delta: that is what the library
   // promises, so a test that announces a late stream has to re-list the early ones too.
   public static List<ElementaryStreamInfo> program(ElementaryStreamInfo... streams) {
      return Arrays.asList(streams);
   }

   public static PesPayload payload(int pid, long pts, byte[] data) {
      return new PesPayload(pid, 0xE0, data, pts, true, 0, false, 0,
         PesPayload.Completeness.COMPLETE);
   }

   // A unit whose PES header carried no timestamp. Real recordings are full of them and the
   // library invents none, so what to do about it is the consumer's decision.
   public static PesPayload withoutPts(int pid, byte[] data) {
      return new PesPayload(pid, 0xE0, data, 0, false, 0, false, 0,
         PesPayload.Completeness.COMPLETE);
   }

   // A region the decoder left out because it could not be decrypted, reported as the stream
   // resumes past it. The only kind that carries a size.
   public static DiscontinuityReason excision(long droppedBytes, long droppedPackets) {
      return DiscontinuityReason.excision(droppedBytes, droppedPackets);
   }

   public static DecodeResult cleanDecode() {
      return new DecodeResult(true, new ArrayList<Integer>(), 0, 0, 0, 0, 0);
   }

   // TivoMetadata has no public way in either, and it is the only source for a track language
   // on a TiVo recording, so a test of that rule has to be able to build one.
   public static TivoMetadata metadata(String xml) throws Exception {
      Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));
      return TivoMetadata.createFrom(Arrays.asList(doc));
   }
}
