package com.tivo.kmttg.rpc;

import java.util.HashMap;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.remote.remotecontrol;
import com.tivo.kmttg.util.log;

/**
 * Convert a channel change into a configured uiNavigate action
 */
public class ChannelNavigator {
	private static HashMap<String, String> channelMap;
	private final String tivo;
	private String lastChannelNumber = null;
	public ChannelNavigator(String tivo) {
		this.tivo = tivo;
		
		// one-time static map initialization:
		synchronized(ChannelNavigator.class) {
			if(channelMap == null) {
				channelMap = new HashMap<String, String>();

				JSONArray rc_apps = remotecontrol.readAppConfiguration();
				
				for(int i = 0 ; i < rc_apps.length() ; ++i) {
					try {
						JSONObject app = rc_apps.getJSONObject(i);
						// not explicitly disabled, and has channel & uri parameters (doesn't have to have a name)
						if(!(app.has("disabled") && app.getBoolean("disabled"))) {
							if(app.has("channel") && app.has("uri")) {
								channelMap.put(app.getString("channel"), app.getString("uri"));
							}
						}
					} catch(Exception e) {
				        log.error("ChannelNavigator " + e.getMessage());
					}
				}
			}
		}
	}
	
	/**
	 * if the channel is configured to navigate the user somewhere, then perform the navigation.
	 * If the channel has not changed since the last navigation, does nothing.
	 * @param channelNumber
	 * @return true if app was launched
	 */
	public boolean navigate(String channelNumber) {
		if(lastChannelNumber != null) {
			if(lastChannelNumber.equals(channelNumber)) {
				// do nothing
				return false;
			}
		}
		lastChannelNumber = channelNumber;
		if(channelMap.containsKey(channelNumber)) {
			new Remote(tivo).navigate(channelMap.get(channelNumber));
			return true;
		}
		return false;
	}

}
