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
package com.tivo.kmttg.main;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONFile;

public class SeasonPassExport {
	   public static void SaveToFile(String tivoName, String file) {
	   	    com.tivo.kmttg.rpc.Remote r = config.initRemote(tivoName);
	   	    JSONArray data = r.SeasonPasses(null);
	   	    JSONFile.write(data, file);
	   		System.exit(0);
	   }
}