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
package com.tivo.kmttg.install;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class baseDownload {
   public static String getBase() {
      debug.print("");
      String base = null;
      String base_url = "https://raw.githubusercontent.com/lart2150/kmttg/refs/heads/master/baseDownload";
      try {
         HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
         HttpClient httpClient = httpClientBuilder.build();
         HttpGet httpget = new HttpGet(base_url);
         try (CloseableHttpResponse response = (CloseableHttpResponse) httpClient.execute(httpget)) {
         	base = EntityUtils.toString(response.getEntity()).trim();
         }
      } catch (Exception ex) {
         base = null;
         log.error("Error getting tool download path");
      }
      return  base;
   }
}
