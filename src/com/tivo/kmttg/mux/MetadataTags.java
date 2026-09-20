/*
 * Copyright 2008-Present Kevin Moye <moyekj@yahoo.com>.
 *
 * This file is part of kmttg package.
 *
 * kmttg is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this project.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tivo.kmttg.mux;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tivo.kmttg.mux.mkv.MkvMuxer;

import net.straylightlabs.tivolibre.TivoMetadata;

// Maps a recording's metadata onto Matroska tags. Two sources, because neither is complete:
// TivoMetadata carries what the .TiVo holds, and Supplement carries the fields kmttg knows
// from RPC/NPL that the recording does not contain at all - measured across nine recordings,
// callsign and episode/season numbers are simply absent from the file (planning/016, 017).
//
// Target levels matter: a series title and an episode title are the same tag name at
// different levels, 70 and 50. That is why this takes fields rather than rendered text.
public class MetadataTags {

   // What kmttg knows and the recording does not. Kept as plain fields rather than taking
   // jobData, so the mux package stays free of main and gui.
   public static class Supplement {
      public String callsign;
      public Integer seasonNumber;
      public Integer episodeNumber;
      public String title;        // fallback when the recording carried no metadata
      public String seriesId;
      // The content rating, as RPC states it: a bare code like "pg", "14" or "pg13". The
      // recording itself never carries one, so this is the only source there is.
      public String tvRating;
      public String mpaaRating;

      // Prefer the real season and episode when the NPL entry carried them; kmttg's
      // tivoFileName does the same and only falls back to splitting the packed string.
      public void setSeasonEpisode(String season, String episode) {
         Integer s = parse(season);
         Integer e = parse(episode);
         if (s != null) seasonNumber = s;
         if (e != null) episodeNumber = e;
      }

      // The fallback, matching tivoFileName exactly so a tag and a filename never disagree:
      // nothing under three digits, then "1102" is season 11 episode 02 and "302" is 3/02.
      public void setEpisodeNumber(String packed) {
         // Either real field wins. Requiring both meant an entry carrying only "season"
         // fell through and had its authoritative value replaced by a guess.
         if (seasonNumber != null || episodeNumber != null) return;
         if (packed == null) return;
         String p = packed.trim();
         if (p.length() <= 2 || ! p.matches("[0-9]+")) return;
         String season  = p.length() <= 3 ? p.substring(0, 1) : p.substring(0, 2);
         String episode = p.length() <= 3 ? p.substring(1)    : p.substring(2);
         seasonNumber  = parse(season);
         episodeNumber = parse(episode);
      }

      private static Integer parse(String v) {
         if (v == null || v.isEmpty()) return null;
         try {
            return Integer.valueOf(Integer.parseInt(v));
         } catch (NumberFormatException e) {
            return null;
         }
      }
   }

   public static List<MkvMuxer.Tag> build(TivoMetadata m, Supplement extra) {
      List<MkvMuxer.Tag> tags = new ArrayList<MkvMuxer.Tag>();
      if (m != null) addFromRecording(m, tags);
      if (extra != null) addFromKmttg(extra, tags);
      return tags;
   }

   // Matroska's Segment Title, which lives in Segment Information rather than in Tags and so
   // cannot come out of build(). Series and episode together: the tags carry them separately
   // at their own target levels, but this is the single line a player's title bar and the
   // MKVToolNix header editor show for the whole file, and an episode title on its own does
   // not say what the file is.
   public static String segmentTitle(TivoMetadata m, Supplement extra) {
      String series = null;
      String episode = null;
      if (m != null) {
         series  = trimmed(m.getSeriesTitle().orElse(m.getTitle().orElse(null)));
         episode = trimmed(m.getEpisodeTitle().orElse(null));
      }
      // The two-step path never sees onMetadata, so a remux of an already decrypted .ts has
      // only what kmttg knows - the same fallback the TITLE tag makes.
      if (series == null && extra != null) series = trimmed(extra.title);
      if (series == null) return episode;
      return episode == null ? series : series + " - " + episode;
   }

   // The broadcast this file was captured from, as epoch milliseconds, or null when the
   // recording does not say. Matroska's DateUTC means when the segment was created, and for a
   // remux that is more useful as the capture's own date than as the minute the remux ran.
   public static Long recordedDate(TivoMetadata m) {
      if (m == null || ! m.getAirDate().isPresent()) return null;
      return Long.valueOf(m.getAirDate().get().toInstant().toEpochMilli());
   }

   // What language the audio is in, as the three letter code Matroska's Language element
   // wants, or null when the recording does not say.
   //
   // The PMT would be the authoritative source, but TiVo's remux strips the ISO 639 language
   // descriptor: every elementary stream in every recording measured declares no descriptors
   // at all, a Spanish capture included. The recording's own metadata is what is left.
   // descriptionLanguage is strictly the language of the guide text rather than of the audio,
   // but the two track each other on a broadcast - "spa-ESP" on a Spanish capture, "eng-USA"
   // on every English one - and a good guess beats "und" for a player picking a track.
   public static String audioLanguage(TivoMetadata m) {
      return m == null ? null : languageFrom(m.getDocuments());
   }

   // Split out so it can be exercised on raw metadata text: only tivolibre can build a
   // TivoMetadata, so a test has no other way in.
   static String languageFrom(List<String> documents) {
      if (documents == null) return null;
      for (String doc : documents) {
         if (doc == null) continue;
         Matcher match = DESCRIPTION_LANGUAGE.matcher(doc);
         if (match.find()) return match.group(1).toLowerCase();
      }
      return null;
   }

   // Three letters and then a region that Matroska has nowhere to put: "eng-USA", "spa-ESP".
   private static final Pattern DESCRIPTION_LANGUAGE =
      Pattern.compile("<descriptionLanguage>\\s*([A-Za-z]{3})");

   private static String trimmed(String v) {
      if (v == null) return null;
      String t = v.trim();
      return t.isEmpty() ? null : t;
   }

   private static void addFromRecording(TivoMetadata m, List<MkvMuxer.Tag> tags) {
      String episodeTitle = m.getEpisodeTitle().orElse(null);
      String seriesTitle  = m.getSeriesTitle().orElse(m.getTitle().orElse(null));

      // A film has no episode title, so its name belongs at the episode level rather than
      // being announced as a series of one.
      if (episodeTitle != null && ! episodeTitle.isEmpty()) {
         add(tags, MkvMuxer.TARGET_COLLECTION, "TITLE", seriesTitle);
         add(tags, MkvMuxer.TARGET_EPISODE, "TITLE", episodeTitle);
      } else {
         add(tags, MkvMuxer.TARGET_EPISODE, "TITLE", seriesTitle);
      }

      add(tags, MkvMuxer.TARGET_EPISODE, "SYNOPSIS", m.getDescription().orElse(null));
      add(tags, MkvMuxer.TARGET_EPISODE, "CONTENT_TYPE", m.getShowType().orElse(null));

      // DATE_RELEASED means when the work came out, which for a film is its year and not the
      // night it was broadcast. kmttg's AtomicParsley call prefers the air date for --year,
      // which tags a 2003 film as 2020 - fine for a TV episode, wrong for a movie, and media
      // libraries sort on it. So movieYear wins whenever the recording has one.
      if (m.getMovieYear().isPresent()) {
         add(tags, MkvMuxer.TARGET_EPISODE, "DATE_RELEASED",
            String.valueOf(m.getMovieYear().getAsInt()));
      } else if (m.getOriginalAirDate().isPresent()) {
         add(tags, MkvMuxer.TARGET_EPISODE, "DATE_RELEASED",
            m.getOriginalAirDate().get().format(DateTimeFormatter.ISO_LOCAL_DATE));
      }
      // The broadcast this file was captured from is a different fact, and Matroska has a
      // tag that means exactly that. Keeping both loses nothing.
      if (m.getAirDate().isPresent()) {
         add(tags, MkvMuxer.TARGET_EPISODE, "DATE_RECORDED",
            m.getAirDate().get().format(DateTimeFormatter.ISO_LOCAL_DATE));
      }

      if (m.getMpaaRating().isPresent()) {
         add(tags, MkvMuxer.TARGET_EPISODE, "LAW_RATING", m.getMpaaRating().get());
      } else if (m.getTvRating().isPresent()) {
         add(tags, MkvMuxer.TARGET_EPISODE, "LAW_RATING", tvRating(m.getTvRating().getAsInt()));
      }

      if (m.getStarRating().isPresent()) {
         add(tags, MkvMuxer.TARGET_EPISODE, "RATING", stars(m.getStarRating().getAsInt()));
      }
      add(tags, MkvMuxer.TARGET_COLLECTION, "CATALOG_NUMBER", m.getSeriesId().orElse(null));

      // The credit lists. These are the reason to carry metadata in MKV at all: AtomicParsley
      // drops every one of them, because MP4 has nowhere useful to put them.
      credits(tags, "ACTOR", m.getActors());
      credits(tags, "ACTOR", m.getGuestStars());
      credits(tags, "DIRECTOR", m.getDirectors());
      credits(tags, "WRITTEN_BY", m.getWriters());
      credits(tags, "PRODUCER", m.getProducers());
      credits(tags, "EXECUTIVE_PRODUCER", m.getExecProducers());
      credits(tags, "HOST", m.getHosts());
      // Spelled without the second 'o' in the Matroska spec. Deliberate, not a typo here:
      // a player matching the spec will not find CHOREOGRAPHER.
      credits(tags, "CHOREGRAPHER", m.getChoreographers());

      // Genres are present but empty in every recording measured, so this normally adds
      // nothing. Kept for the day TiVo starts filling them in.
      for (String genre : m.getProgramGenres()) {
         add(tags, MkvMuxer.TARGET_COLLECTION, "GENRE", genre);
      }
   }

   private static void addFromKmttg(Supplement extra, List<MkvMuxer.Tag> tags) {
      // No standard Matroska tag names a broadcaster; TVCHANNEL is the established custom one.
      add(tags, MkvMuxer.TARGET_COLLECTION, "TVCHANNEL", extra.callsign);
      // Only a fallback. The two-step path never sees onMetadata, so without this a remux of
      // an already decrypted .ts would carry no title at all.
      if (! hasTag(tags, "TITLE")) {
         add(tags, MkvMuxer.TARGET_EPISODE, "TITLE", extra.title);
      }
      if (! hasTag(tags, "CATALOG_NUMBER")) {
         add(tags, MkvMuxer.TARGET_COLLECTION, "CATALOG_NUMBER", extra.seriesId);
      }
      // Only ever from here in practice: no .TiVo measured carries a rating of its own, so
      // the recording branch above almost never fills this in.
      if (! hasTag(tags, "LAW_RATING")) {
         add(tags, MkvMuxer.TARGET_EPISODE, "LAW_RATING",
            lawRating(extra.mpaaRating, extra.tvRating));
      }
      if (extra.seasonNumber != null && extra.seasonNumber > 0) {
         add(tags, MkvMuxer.TARGET_SEASON, "PART_NUMBER",
            String.valueOf(extra.seasonNumber));
      }
      if (extra.episodeNumber != null && extra.episodeNumber > 0) {
         add(tags, MkvMuxer.TARGET_EPISODE, "PART_NUMBER",
            String.valueOf(extra.episodeNumber));
      }
   }

   // RPC states a rating as a bare code - "pg", "14", "y7" for television, "pg13" for a film -
   // where Matroska wants the label a viewer would recognise. A film's rating wins when there
   // is one, the same order the recording branch uses. Unknown codes pass through rather than
   // being dropped: a rating we do not recognise is still a rating.
   static String lawRating(String mpaaRating, String tvRating) {
      // A film rating only wins when it is one this recognises. An mpaaRating the table has
      // never heard of is passed through rather than dropped, but not at the cost of a
      // perfectly good tvRating sitting next to it.
      String film = label(mpaaRating, MPAA_LABEL);
      if (film != null && known(film, MPAA_LABEL)) return film;
      String tv = label(tvRating, TV_LABEL);
      return tv != null ? tv : film;
   }

   private static boolean known(String label, String[][] table) {
      for (String[] row : table) {
         if (row[1].equals(label)) return true;
      }
      return false;
   }

   private static final String[][] TV_LABEL = {
      {"y7", "TV-Y7"}, {"y", "TV-Y"}, {"g", "TV-G"},
      {"pg", "TV-PG"}, {"14", "TV-14"}, {"ma", "TV-MA"},
   };

   private static final String[][] MPAA_LABEL = {
      {"g", "G"}, {"pg", "PG"}, {"pg13", "PG-13"},
      {"r", "R"}, {"nc17", "NC-17"}, {"x", "X"}, {"nr", "NR"},
   };

   private static String label(String value, String[][] table) {
      String raw = trimmed(value);
      if (raw == null) return null;
      String key = raw.toLowerCase().replace("-", "").replace("_", "");
      // "TV-14" and "14" are the same rating; the RPC sends the bare form but a value that
      // arrived already spelled out must not then miss the table.
      if (table == TV_LABEL && key.startsWith("tv")) key = key.substring(2);
      for (String[] row : table) {
         if (row[0].equals(key)) return row[1];
      }
      return raw;
   }

   // The recording carries TiVo's own rating code, not a label, so writing the bare number
   // leaves a player showing "5" where it should show "TV-14". Same table util/createMeta
   // already uses for AtomicParsley, repeated rather than imported because that class pulls
   // in main and this package deliberately does not depend on it.
   private static final String[] TV_RATING = {
      null, "TV-Y7", "TV-Y", "TV-G", "TV-PG", "TV-14", "TV-MA", "Unrated"
   };

   // The recording carries a code, not a star count: 1 through 7 for one star through four in
   // half steps, the same scale Advanced Search spells out as one..four. Writing the code bare
   // called a three star film a 5, which every reader takes at face value. Same class of bug
   // the tvRating table above exists to avoid.
   static String stars(int code) {
      if (code < 1 || code > 7) return null;
      double value = (code + 1) / 2.0;
      return value == Math.floor(value)
         ? String.valueOf((int)value)
         : String.valueOf(value);
   }

   private static String tvRating(int code) {
      return code > 0 && code < TV_RATING.length ? TV_RATING[code] : null;
   }

   private static boolean hasTag(List<MkvMuxer.Tag> tags, String name) {
      for (MkvMuxer.Tag t : tags) if (t.name.equals(name)) return true;
      return false;
   }

   private static void credits(List<MkvMuxer.Tag> tags, String name, List<String> people) {
      for (String person : people) {
         add(tags, MkvMuxer.TARGET_EPISODE, name, displayName(person));
      }
   }

   // The recording writes a name as "Surname|Forename". Three shapes turn up in the same
   // list, so this handles all of them rather than assuming two halves:
   //   "Li|Jet"        -> "Jet Li"
   //   "DMX|DMX"       -> "DMX"        a mononym, doubled
   //   "Drag-On|"      -> "Drag-On"    no forename at all
   static String displayName(String raw) {
      if (raw == null) return null;
      int bar = raw.indexOf('|');
      if (bar < 0) return raw.trim();
      String surname  = raw.substring(0, bar).trim();
      String forename = raw.substring(bar + 1).trim();
      if (forename.isEmpty()) return surname;
      if (surname.isEmpty()) return forename;
      if (surname.equalsIgnoreCase(forename)) return surname;
      return forename + " " + surname;
   }

   private static void add(List<MkvMuxer.Tag> tags, int target, String name, String value) {
      // An absent field means no tag. A tag reading "Unknown" is worse than a missing one.
      if (value == null || value.trim().isEmpty()) return;
      tags.add(new MkvMuxer.Tag(target, name, value.trim()));
   }
}
