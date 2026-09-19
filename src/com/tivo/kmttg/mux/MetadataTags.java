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
         add(tags, MkvMuxer.TARGET_EPISODE, "RATING",
            String.valueOf(m.getStarRating().getAsInt()));
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
      if (extra.seasonNumber != null && extra.seasonNumber > 0) {
         add(tags, MkvMuxer.TARGET_SEASON, "PART_NUMBER",
            String.valueOf(extra.seasonNumber));
      }
      if (extra.episodeNumber != null && extra.episodeNumber > 0) {
         add(tags, MkvMuxer.TARGET_EPISODE, "PART_NUMBER",
            String.valueOf(extra.episodeNumber));
      }
   }

   // The recording carries TiVo's own rating code, not a label, so writing the bare number
   // leaves a player showing "5" where it should show "TV-14". Same table util/createMeta
   // already uses for AtomicParsley, repeated rather than imported because that class pulls
   // in main and this package deliberately does not depend on it.
   private static final String[] TV_RATING = {
      null, "TV-Y7", "TV-Y", "TV-G", "TV-PG", "TV-14", "TV-MA", "Unrated"
   };

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
