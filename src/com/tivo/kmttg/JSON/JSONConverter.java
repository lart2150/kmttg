package com.tivo.kmttg.JSON;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.util.log;

public class JSONConverter {
	// For a given array of JSON objects sort by episode numbers - earliest 1st
	static public JSONArray sortByEpisode(JSONArray array) {
		class EpComparator implements Comparator<JSONObject> {
			public int compare(JSONObject j1, JSONObject j2) {
				int ep1 = getEpisodeNum(j1);
				int ep2 = getEpisodeNum(j2);
				if (ep1 > ep2) {
					return 1;
				} else if (ep1 < ep2) {
					return -1;
				} else {
					return 0;
				}
			}
		}
		List<JSONObject> arrayList = new ArrayList<JSONObject>();
		for (int i = 0; i < array.length(); ++i) {
			try {
				arrayList.add(array.getJSONObject(i));
			} catch (JSONException e) {
				log.error("sortByEpisode - " + e.getMessage());
			}
		}
		JSONArray sorted = new JSONArray();
		EpComparator comparator = new EpComparator();
		Collections.sort(arrayList, comparator);
		for (JSONObject ajson : arrayList) {
			sorted.put(ajson);
		}
		return sorted;
	}

	// For a given array of JSON objects sort by start date - most recent 1st
	static public JSONArray sortByLatestStartDate(JSONArray array) {
		class DateComparator implements Comparator<JSONObject> {
			public int compare(JSONObject j1, JSONObject j2) {
				long start1 = JSONConverter.getStartTime(j1);
				long start2 = JSONConverter.getStartTime(j2);
				if (start1 < start2) {
					return 1;
				} else if (start1 > start2) {
					return -1;
				} else {
					return 0;
				}
			}
		}
		List<JSONObject> arrayList = new ArrayList<JSONObject>();
		for (int i = 0; i < array.length(); ++i)
			try {
				arrayList.add(array.getJSONObject(i));
			} catch (JSONException e) {
				log.error("sortByStartDate - " + e.getMessage());
			}
		JSONArray sorted = new JSONArray();
		DateComparator comparator = new DateComparator();
		Collections.sort(arrayList, comparator);
		for (JSONObject ajson : arrayList) {
			sorted.put(ajson);
		}
		return sorted;
	}

	// For a given array of JSON objects sort by start date - oldest 1st
	static public JSONArray sortByOldestStartDate(JSONArray array) {
		class DateComparator implements Comparator<JSONObject> {
			public int compare(JSONObject j1, JSONObject j2) {
				long start1 = JSONConverter.getStartTime(j1);
				long start2 = JSONConverter.getStartTime(j2);
				if (start1 > start2) {
					return 1;
				} else if (start1 < start2) {
					return -1;
				} else {
					return 0;
				}
			}
		}
		List<JSONObject> arrayList = new ArrayList<JSONObject>();
		for (int i = 0; i < array.length(); ++i)
			try {
				arrayList.add(array.getJSONObject(i));
			} catch (JSONException e) {
				log.error("sortByStartDate - " + e.getMessage());
			}
		JSONArray sorted = new JSONArray();
		DateComparator comparator = new DateComparator();
		Collections.sort(arrayList, comparator);
		for (JSONObject ajson : arrayList) {
			sorted.put(ajson);
		}
		return sorted;
	}

	public static String makeChannelName(JSONObject entry) {
		String channel = "";
		try {
			if (entry.has("channel")) {
				JSONObject o = entry.getJSONObject("channel");
				if (o.has("channelNumber"))
					channel += o.getString("channelNumber");
				if (o.has("callSign")) {
					String callSign = o.getString("callSign");
					if (callSign.toLowerCase().equals("all channels"))
						channel += callSign;
					else
						channel += "=" + callSign;
				}
			} else {
				if (entry.has("idSetSource")) {
					JSONObject idSetSource = entry.getJSONObject("idSetSource");
					if (idSetSource.has("channel"))
						channel = makeChannelName(idSetSource);
					else {
						if (idSetSource.has("consumptionSource")) {
							if (idSetSource.getString("consumptionSource").equals("linear"))
								channel += "All Channels";
						}
					}
				}
			}
		} catch (JSONException e) {
			log.error("makeChannelName - " + e.getMessage());
		}
		return channel;
	}

	public static String makeShowTitle(JSONObject entry) {
		String title = " ";
		try {
			if (entry.has("title"))
				title += entry.getString("title");
			if (entry.has("seasonNumber") && entry.has("episodeNum")) {
				title += " [Ep " + entry.get("seasonNumber")
						+ String.format("%02d]", entry.getJSONArray("episodeNum").get(0));
			}
			if (entry.has("movieYear"))
				title += " [" + entry.get("movieYear") + "]";
			if (entry.has("subtitle"))
				title += " - " + entry.getString("subtitle");
			if (entry.has("subscriptionIdentifier")) {
				JSONArray a = entry.getJSONArray("subscriptionIdentifier");
				if (a.length() > 0) {
					if (a.getJSONObject(0).has("subscriptionType")) {
						String type = a.getJSONObject(0).getString("subscriptionType");
						if (type.equals("singleTimeChannel") || type.equals("repeatingTimeChannel"))
							title = " Manual:" + title;
					}
				}
			}
		} catch (JSONException e) {
			log.error("makeShowTitle - " + e.getMessage());
		}
		return title;
	}

	/**
	 * Gets the start time as a local Date
	 * @param entry
	 * @return
	 */
	public static String printableTimeFromJSON(JSONObject entry) {
		long start = getStartTime(entry);
		SimpleDateFormat sdf = new SimpleDateFormat("E MM/dd/yy hh:mm a");
		return sdf.format(start);
	}
	
	/**
	 * Returns the selected key as a local timestamp or a empty string on error
	 * @param entry json object
	 * @param key json key
	 * @return
	 */
	  public static String printableTimeFromJSON(JSONObject entry, String key) {
	     if (entry.has(key)) {
         try {
            String dateString = entry.getString(key);
            long start = getLongDateFromString(dateString);
            SimpleDateFormat sdf = new SimpleDateFormat("E MM/dd/yy hh:mm a");
            return sdf.format(start);
         } catch (JSONException e) {
         }
	     }
	     return "";
	   }

	// Return friendly name of a partner based on id, such as Netflix, Hulu, etc.
	static public String getPartnerName(JSONObject entry) {
		try {
			if (config.partners.size() == 0) {
				log.warn("Refreshing partner names");
				Remote r = config.initRemote(config.gui.remote_gui.getTivoName("search"));
				if (r.success) {
					JSONObject json = new JSONObject();
					json.put("bodyId", r.bodyId_get());
					json.put("noLimit", true);
					json.put("levelOfDetail", "high");
					JSONObject result = r.Command("partnerInfoSearch", json);
					if (result != null && result.has("partnerInfo")) {
						JSONArray info = result.getJSONArray("partnerInfo");
						for (int i = 0; i < info.length(); ++i) {
							JSONObject j = info.getJSONObject(i);
							if (j.has("partnerId") && j.has("displayName")) {
								config.partners.put(j.getString("partnerId"), j.getString("displayName"));
							}
						}
					}
					r.disconnect();
				}
			}

			String partnerId = "";
			if (entry.has("partnerId"))
				partnerId = entry.getString("partnerId");
			if (entry.has("brandingPartnerId"))
				partnerId = entry.getString("brandingPartnerId");
			String name = partnerId;
			if (config.partners.containsKey(partnerId))
				name = config.partners.get(partnerId);
			return name;
		} catch (JSONException e1) {
			log.error("getPartnerName - " + e1.getMessage());
			return "STREAMING";
		}
	}

	public static Boolean isWL(JSONObject json) {
		Boolean WL = false;
		try {
			if (json.has("idSetSource")) {
				JSONObject idSetSource = json.getJSONObject("idSetSource");
				if (idSetSource.has("type") && idSetSource.getString("type").equals("wishListSource"))
					WL = true;
			}
		} catch (JSONException e) {
			log.error("isWL - " + e.getMessage());
		}
		return WL;
	}

	static public void addTivoNameFlagtoJson(JSONObject json, String flag, String tivoName) {
		try {
			if (json.has(flag))
				json.put(flag, json.getString(flag) + ", " + tivoName);
			else
				json.put(flag, tivoName);
		} catch (JSONException e) {
			log.error("addTivoNameFlagtoJson - " + e.getMessage());
		}
	}

	public static int getEpisodeNum(JSONObject json) {
		try {
			if (json.has("seasonNumber") && json.has("episodeNum")) {
				int seasonNumber = json.getInt("seasonNumber");
				int episodeNum = json.getJSONArray("episodeNum").getInt(0);
				return 100 * seasonNumber + episodeNum;
			}
		} catch (Exception e) {
			log.error("getEpisodeNum - " + e.getMessage());
		}
		return 0;
	}

	public static long getEndTime(JSONObject json) {
		try {
			long start = getStartTime(json);
			long end = start + json.getInt("duration") * 1000;
			if (json.has("requestedEndPadding"))
				end += json.getInt("requestedEndPadding") * 1000;
			return end;
		} catch (Exception e) {
			log.error("getEndTime - " + e.getMessage());
			return 0;
		}
	}

	public static long getStartTime(JSONObject json) {
		try {
			if (json.has("startTime")) {
				String startString = json.getString("startTime");
				long start = getLongDateFromString(startString);
				if (json.has("requestedStartPadding"))
					start -= json.getInt("requestedStartPadding") * 1000;
				return start;
			} else {
				return 0;
			}
		} catch (Exception e) {
			log.error("getStartTime - " + e.getMessage());
			return 0;
		}
	}

	public static long getLongDateFromString(String date) {
		try {
			SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzz");
			Date d = format.parse(date + " GMT");
			return d.getTime();
		} catch (ParseException e) {
			log.error("getLongDateFromString - " + e.getMessage());
			return 0;
		}
	}
}
