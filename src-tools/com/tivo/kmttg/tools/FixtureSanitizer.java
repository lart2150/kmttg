package com.tivo.kmttg.tools;

/**
 * Takes the identifying values out of a captured document before it is written.
 *
 * One class for both capture tools, because a fixture is only as clean as the weaker of
 * the two: the RPC side once wrote the TiVo service number with only its digits masked,
 * which left the letters on the end of the number in every fixture it produced.
 *
 * What survives on purpose: recording titles, episode names, descriptions, channel numbers
 * and call signs. They are broadcast programs rather than anything about the owner, and
 * they are what makes a fixture worth reading. A call sign does say roughly where the box
 * is, which is why the zip code beside it keeps only its leading digit - zeroing that as
 * well would hide nothing the call signs do not already give away.
 */
public class FixtureSanitizer {

   /** What every captured bodyId is replaced with, whichever box it came from. */
   public static final String BODY_ID = "tsn:0000000000000000";

   private final String ip;
   private final String mak;
   private final String tsn;

   /** tsn may be null, and may be given as "tsn:849...", "849-0001-..." or bare. */
   public FixtureSanitizer(String ip, String mak, String tsn) {
      this.ip = ip;
      this.mak = mak;
      this.tsn = tsn == null ? null
               : tsn.replaceFirst("^tsn:", "").replaceAll("[^A-Za-z0-9]", "");
   }

   public String scrub(String s) {
      if (s == null) return null;
      // Download and details urls carry the box's address. 192.168.1.10 is what the
      // hand-written fixtures in ParseNplTest already use.
      if (notEmpty(ip)) s = s.replace(ip, "192.168.1.10");
      if (notEmpty(mak)) s = s.replace(mak, mask(mak));
      if (notEmpty(tsn)) {
         s = s.replace(tsn, mask(tsn));
         s = s.replace(dashed(tsn), mask(dashed(tsn)));
      }
      // The bodyId, by shape rather than by value, and always to the same dummy rather than
      // to a mask of whatever was there. Every request in a command trace carries it and the
      // shape tests compare it against one constant (Fixtures.BODY_ID), so a capture from
      // somebody else's box has to produce the identical string or those tests fail on their
      // fixtures. Letters are in the pattern because a digits-only one stops at the first
      // one and leaves the rest of the number behind.
      s = s.replaceAll("tsn:[0-9A-Za-z]+", BODY_ID);
      s = s.replaceAll("\\b[0-9]{3}-[0-9]{4}-[0-9]{4}-[0-9A-Za-z]{4}\\b", "000-0000-0000-0000");
      // systemInformationGet answers with these beside the number: where the box is, which
      // streaming account it is signed in to, and what its owner calls it.
      s = s.replaceAll("(?<pre>\"zipCode\"\\s*:\\s*\"[0-9])[^\"]*(?<post>\")", "${pre}0000${post}");
      s = s.replaceAll("(\"netflixEsn\"\\s*:\\s*\")[^\"]*(\")", "$1TIVRTLSER6NF-000-0000000000000$2");
      s = s.replaceAll("(\"deviceName\"\\s*:\\s*\")[^\"]*(\")", "$1TiVo$2");
      return s;
   }

   private static boolean notEmpty(String s) {
      return s != null && s.length() > 0;
   }

   /** Same shape, no information: digits become 0 and letters X. */
   public static String mask(String s) {
      return s.replaceAll("[0-9]", "0").replaceAll("[A-Za-z]", "X");
   }

   /** 84900010000XX00 as 849-0001-0000-XX00, which is how the box prints it. */
   private static String dashed(String s) {
      if (s.length() != 15) return s;
      return s.substring(0, 3) + "-" + s.substring(3, 7) + "-"
           + s.substring(7, 11) + "-" + s.substring(11);
   }
}
