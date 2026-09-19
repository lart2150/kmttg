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
package com.tivo.kmttg.rpc;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

// Artwork lookup over rpc, separated from the Show Information dialog so a job can use it
// too. The dialog wants a ~180px thumbnail to display; a job embedding cover art in a file
// wants the largest available, so the desired height is a parameter rather than a constant.
public class artwork {
   // Big enough that "closest to" always picks the largest image on offer.
   public static final int LARGEST = 100000;

   // Ask the TiVo for the artwork url of a show. contentId identifies the episode and
   // collectionId the series; either will do, and the series image is the usual answer.
   public static String findUrl(String tivoName, String contentId, String collectionId,
         int desiredHeight) {
      debug.print("tivoName=" + tivoName + " contentId=" + contentId
         + " collectionId=" + collectionId);
      if (tivoName == null || (contentId == null && collectionId == null)) return null;

      Remote r = config.initRemote(tivoName);
      if (r == null || ! r.success) return null;
      String url = null;
      try {
         JSONObject json = new JSONObject();
         JSONObject template = new JSONObject();
         template.put("type", "responseTemplate");
         template.put("typeName", "category");
         template.put("fieldName", new JSONArray("[\"image\"]"));
         json.put("responseTemplate", template);
         if (contentId != null) {
            json.put("contentId", contentId);
            JSONObject result = r.Command("contentSearch", json);
            if (result != null && result.has("content")) {
               JSONObject content = result.getJSONArray("content").getJSONObject(0);
               if (content.has("image"))
                  url = pickUrl(content.getJSONArray("image"), desiredHeight);
            }
         }
         if (url == null && collectionId != null) {
            json.put("collectionId", collectionId);
            json.remove("contentId");
            JSONObject result = r.Command("collectionSearch", json);
            if (result != null && result.has("collection")) {
               JSONObject collection = result.getJSONArray("collection").getJSONObject(0);
               if (collection.has("image"))
                  url = pickUrl(collection.getJSONArray("image"), desiredHeight);
            }
         }
      } catch (JSONException e) {
         log.error("artwork findUrl - " + e.getMessage());
      } finally {
         r.disconnect();
      }
      return url;
   }

   // The url whose height is closest to what the caller wants.
   public static String pickUrl(JSONArray imageArray, int desiredHeight) {
      try {
         int diff = Integer.MAX_VALUE;
         int index = 0;
         for (int i = 0; i < imageArray.length(); ++i) {
            JSONObject j = imageArray.getJSONObject(i);
            int h = j.getInt("height");
            if (Math.abs(desiredHeight - h) < diff) {
               index = i;
               diff = Math.abs(desiredHeight - h);
            }
         }
         return imageArray.getJSONObject(index).getString("imageUrl");
      } catch (JSONException e) {
         log.error("artwork pickUrl - " + e.getMessage());
         return null;
      }
   }

   // The image as bytes, which is what an attachment needs. Deliberately not an ImageIcon:
   // decoding and re-encoding would throw away the original file and its compression.
   public static byte[] fetch(String url) {
      debug.print("url=" + url);
      if (url == null || url.isEmpty()) return null;
      HttpURLConnection conn = null;
      try {
         conn = (HttpURLConnection) new URL(url).openConnection();
         conn.setConnectTimeout(10000);
         conn.setReadTimeout(30000);
         if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
            log.warn("artwork fetch got " + conn.getResponseCode() + " for " + url);
            return null;
         }
         ByteArrayOutputStream out = new ByteArrayOutputStream();
         InputStream in = conn.getInputStream();
         try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
         } finally {
            in.close();
         }
         return out.size() > 0 ? out.toByteArray() : null;
      } catch (Exception e) {
         log.warn("artwork fetch failed for " + url + " - " + e.getMessage());
         return null;
      } finally {
         if (conn != null) conn.disconnect();
      }
   }

   // Media type from the url's extension. Matroska wants a real one on the attachment, and
   // TiVo serves jpeg in practice, so that is the fallback rather than a guess at binary.
   public static String mimeType(String url) {
      if (url != null) {
         String u = url.toLowerCase();
         int q = u.indexOf('?');
         if (q > 0) u = u.substring(0, q);
         if (u.endsWith(".png")) return "image/png";
         if (u.endsWith(".gif")) return "image/gif";
         if (u.endsWith(".webp")) return "image/webp";
      }
      return "image/jpeg";
   }

   // Matroska convention: an attachment named cover.<ext> is the file's poster art, and
   // players look for exactly that name.
   public static String coverName(String url) {
      String mime = mimeType(url);
      if (mime.equals("image/png")) return "cover.png";
      if (mime.equals("image/gif")) return "cover.gif";
      if (mime.equals("image/webp")) return "cover.webp";
      return "cover.jpg";
   }
}
