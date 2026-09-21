package com.tivo.kmttg.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Hashtable;
import java.util.Stack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tivo.kmttg.main.config;

// The two cut point formats kmttg shares with the outside world: VideoReDo .VPrj and comskip
// .edl. Both arrive as the commercials to remove and are stored as the show segments between
// them, by two separate pieces of arithmetic that have to stay each other's inverse - cut
// points exported and imported again must describe the same commercials.
public class SkipImportTest {

   @TempDir
   Path work;

   private static final long HALF_HOUR = 1800000L;
   private static final String AIRING =
      "tivo:of.ctd.394439967.26-6.terrestrial.2026-08-23-03-30-00.1800";

   private String savedProgramDir, savedOutputDir, savedMpegDir, savedFormat;
   private int savedPadStart, savedPadStop;
   private Boolean savedMode;

   @BeforeEach
   void redirectDirs() {
      savedProgramDir = config.programDir;
      savedOutputDir = config.outputDir;
      savedMpegDir = config.mpegDir;
      savedFormat = config.tivoFileNameFormat;
      savedPadStart = config.autoskip_padding_start;
      savedPadStop = config.autoskip_padding_stop;
      savedMode = config.GUIMODE;
      config.programDir = work.toString();
      config.outputDir = work.toString();
      config.mpegDir = work.toString();
      config.tivoFileNameFormat = "[title]";
      config.autoskip_padding_start = 0;
      config.autoskip_padding_stop = 0;
      config.GUIMODE = false;
   }

   @AfterEach
   void restore() {
      config.programDir = savedProgramDir;
      config.outputDir = savedOutputDir;
      config.mpegDir = savedMpegDir;
      config.tivoFileNameFormat = savedFormat;
      config.autoskip_padding_start = savedPadStart;
      config.autoskip_padding_stop = savedPadStop;
      config.GUIMODE = savedMode;
   }

   // comskip writes out the commercials; the AutoSkip table wants what is left of the show.
   // A break at the very head is the case to watch - the segment in front of it is zero
   // length, and an entry starting and ending at 0 would have AutoSkip jumping to a show
   // segment that is not there.
   @Test
   void edlCutsBecomeTheShowSegmentsBetweenThem() throws IOException {
      String edl = write("cheers.edl", "0.00\t30.50\t0\n600.00\t720.00\t0\n1500.00\t1560.00\t0\n");
      Stack<Hashtable<String,Long>> segments = SkipImport.edlImport(edl, HALF_HOUR);
      assertEquals(3, segments.size());
      assertSegment(segments, 0, 30500, 600000);
      assertSegment(segments, 1, 720000, 1500000);
      // whatever runs after the last break is show as well
      assertSegment(segments, 2, 1560000, HALF_HOUR);
   }

   // A recording cut off at its scheduled end, in the middle of the last break, has nothing
   // after that break to keep.
   @Test
   void noShowSegmentIsInventedPastTheEndOfTheRecording() throws IOException {
      String edl = write("t.edl", "600.00 720.00 0\n1740.00 1800.00 0\n");
      Stack<Hashtable<String,Long>> segments = SkipImport.edlImport(edl, HALF_HOUR);
      assertEquals(2, segments.size());
      assertSegment(segments, 0, 0, 600000);
      assertSegment(segments, 1, 720000, 1740000);
   }

   // VideoReDo counts in 100 ns ticks and capitalises its tags. The importer lowercases the
   // whole document before parsing, which is the only reason a project written by another
   // version of VRD still reads.
   @Test
   void vprjTimesAreHundredsOfNanosecondsAndTheTagsAreCaseFolded() throws IOException {
      String vprj = write("cheers.VPrj", vprjText());
      Stack<Hashtable<String,Long>> segments = SkipImport.vrdImport(vprj, HALF_HOUR);
      assertEquals(2, segments.size());
      assertSegment(segments, 0, 30500, 600000);
      assertSegment(segments, 1, 720000, HALF_HOUR);
   }

   // VRD 6 writes a UTF-8 BOM in front of the first tag, which is not XML and stops the
   // parser dead if it reaches it.
   @Test
   void aByteOrderMarkFromVideoRedo6DoesNotStopTheParse() throws IOException {
      Path p = work.resolve("bom.VPrj");
      byte[] bom = {(byte)0xEF, (byte)0xBB, (byte)0xBF};
      byte[] xml = ("<VideoReDoProject Version=\"3\">\n"
         + "<CutList>\n"
         + "<Cut> <CutTimeStart>0</CutTimeStart> <CutTimeEnd>305000000</CutTimeEnd> </Cut>\n"
         + "</CutList>\n"
         + "</VideoReDoProject>\n").getBytes(StandardCharsets.UTF_8);
      byte[] both = new byte[bom.length + xml.length];
      System.arraycopy(bom, 0, both, 0, bom.length);
      System.arraycopy(xml, 0, both, bom.length, xml.length);
      Files.write(p, both);

      Stack<Hashtable<String,Long>> segments = SkipImport.vrdImport(p.toString(), HALF_HOUR);
      assertEquals(1, segments.size());
      assertSegment(segments, 0, 30500, HALF_HOUR);
   }

   // What the Import skip button hands these when the user browses to the wrong file, or when
   // comskip found nothing and wrote an empty one. Both have to come back with no cut points
   // rather than throwing at a caller that has no catch of its own.
   @Test
   void aFileThatHoldsNoCutPointsImportsNothing() throws IOException {
      assertTrue(SkipImport.edlImport(write("notes.edl", "this is not an edl\n"), HALF_HOUR).isEmpty());
      assertTrue(SkipImport.edlImport(write("empty.edl", ""), HALF_HOUR).isEmpty());
      assertTrue(SkipImport.vrdImport(write("notes.VPrj", "this is not xml at all\n"), HALF_HOUR).isEmpty());
      // A project file that parses but describes something else entirely
      assertTrue(SkipImport.vrdImport(write("other.VPrj", "<Playlist><Item>1</Item></Playlist>\n"),
         HALF_HOUR).isEmpty());
      // And files that are not there - the recording may already have been deleted
      assertTrue(SkipImport.edlImport(work.resolve("gone.edl").toString(), HALF_HOUR).isEmpty());
      assertTrue(SkipImport.vrdImport(work.resolve("gone.VPrj").toString(), HALF_HOUR).isEmpty());
   }

   // comskip killed part way through writing its last line. The read stops there and keeps
   // what it already had, so the entry is short by one break rather than missing altogether -
   // AutoSkip then sails straight through that final commercial.
   @Test
   void aHalfWrittenLastLineCostsOnlyTheBreaksAfterIt() throws IOException {
      String edl = write("t.edl", "0.00 30.00 0\n600.00 660.00 0\n1500.00\n");
      Stack<Hashtable<String,Long>> segments = SkipImport.edlImport(edl, HALF_HOUR);
      assertEquals(2, segments.size());
      assertSegment(segments, 0, 30000, 600000);
      assertSegment(segments, 1, 660000, HALF_HOUR);
   }

   // The round trip a comskip review does: kmttg exports its cut points, VideoReDo or comskip
   // hands them back, and the table is rewritten from them. The exported text is also what a
   // third party tool reads, so the format is pinned here rather than only the numbers.
   @Test
   void exportingWhatWasImportedGivesBackTheSameCutPoints() throws IOException {
      String edl = write("in.edl", "0.00 30.50 0\n600.00 720.00 0\n1500.00 1560.00 0\n");
      Stack<Hashtable<String,Long>> original = SkipImport.edlImport(edl, HALF_HOUR);
      SkipManager.saveEntry("tivo:ct.1", AIRING, 0L, "Cheers", "Bolt", original);

      String out = SkipImport.edlExport(npl("Cheers"));
      assertNotNull(out);
      assertEquals("0.0  30.5  0\r\n600.0  720.0  0\r\n1500.0  1560.0  0\r\n", read(out));

      // and reading it back in reproduces the show segments it was written from
      Stack<Hashtable<String,Long>> again = SkipImport.edlImport(out, HALF_HOUR);
      assertEquals(original.size(), again.size());
      for (int i=0; i<original.size(); ++i)
         assertSegment(again, i, original.get(i).get("start"), original.get(i).get("end"));
   }

   // One show segment with a commercial in front of it: the cut before the first segment is
   // the only cut there is. It used to be written inside the loop over the gaps between
   // segments, which a single segment never enters, so the export came back empty and the
   // leading commercial survived into the encode.
   @Test
   void theLeadingCommercialIsExportedEvenWithOneShowSegment() throws IOException {
      SkipManager.saveEntry("tivo:ct.2", AIRING, 0L, "Cheers", "Bolt", segments(30000, HALF_HOUR));
      String out = SkipImport.edlExport(npl("Cheers"));
      assertNotNull(out);
      assertEquals("0.0  30.0  0\r\n", read(out));
   }

   // Padding is stored in msecs and applied on the way out, so a negative start pad leaves
   // that much commercial in front of the show - what somebody who does not fully trust the
   // cut points asks for. VPrj wants 100 ns ticks, so both conversions land on one line.
   @Test
   void exportedCutsCarryTheAutoSkipPadding() throws IOException {
      String edl = write("in.edl", "0.00 30.50 0\n600.00 720.00 0\n1500.00 1560.00 0\n");
      SkipManager.saveEntry("tivo:ct.3", AIRING, 0L, "Cheers", "Bolt",
         SkipImport.edlImport(edl, HALF_HOUR));
      config.autoskip_padding_start = -2000;
      config.autoskip_padding_stop = 3000;

      String xml = read(SkipImport.vrdExport(npl("Cheers")));
      assertTrue(xml.contains("<CutTimeStart>6020000000</CutTimeStart>"), xml);
      assertTrue(xml.contains("<CutTimeEnd>7170000000</CutTimeEnd>"), xml);

      // Padding the other way would put the first cut before the start of the file, which
      // VideoReDo will not open.
      config.autoskip_padding_start = 2000;
      xml = read(SkipImport.vrdExport(npl("Cheers")));
      assertTrue(xml.contains("<CutTimeStart>0</CutTimeStart>"), xml);
   }

   private static Stack<Hashtable<String,Long>> segments(long... bounds) {
      Stack<Hashtable<String,Long>> s = new Stack<Hashtable<String,Long>>();
      for (int i=0; i<bounds.length; i+=2) {
         Hashtable<String,Long> h = new Hashtable<String,Long>();
         h.put("start", bounds[i]);
         h.put("end", bounds[i+1]);
         s.push(h);
      }
      return s;
   }

   // Enough of an NPL row for the exporters: the file name template needs a title and the
   // recording time, and the table lookup needs the airing.
   private static Hashtable<String,String> npl(String title) {
      Hashtable<String,String> e = new Hashtable<String,String>();
      e.put("title", title);
      e.put("gmt", "1787620000000");
      e.put("duration", "" + HALF_HOUR);
      e.put("contentId", "tivo:ct.1");
      e.put("offerId", AIRING);
      return e;
   }

   private static String vprjText() {
      return "<VideoReDoProject Version=\"3\">\n"
         + "<Filename>C:\\video\\Cheers.mpg</Filename>\n"
         + "<CutList>\n"
         + "<Cut> <CutTimeStart>0</CutTimeStart> <CutTimeEnd>305000000</CutTimeEnd> </Cut>\n"
         + "<Cut> <CUTTIMESTART>6000000000</CUTTIMESTART> <cuttimeend>7200000000</cuttimeend> </Cut>\n"
         + "</CutList>\n"
         + "</VideoReDoProject>\n";
   }

   private String write(String name, String content) throws IOException {
      Path p = work.resolve(name);
      Files.write(p, content.getBytes(StandardCharsets.UTF_8));
      return p.toString();
   }

   private static String read(String path) throws IOException {
      return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
   }

   private static void assertSegment(Stack<Hashtable<String,Long>> s, int i, long start, long end) {
      assertEquals(start, s.get(i).get("start").longValue(), "segment " + i + " start");
      assertEquals(end, s.get(i).get("end").longValue(), "segment " + i + " end");
   }
}
