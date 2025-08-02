package com.tivo.kmttg.rpc;


import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.protocol.RedirectLocations;

import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.JSON.JSONArray;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GetDomainToken {
	private HttpClient httpClient;
	private BasicCookieStore cookieStore;
	public GetDomainToken() {
		HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
		cookieStore = new BasicCookieStore();
		httpClientBuilder.setDefaultCookieStore(cookieStore);
		httpClientBuilder.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36");
		httpClient = httpClientBuilder.build();
	}
	
	public Cookie getToken() throws Exception {
		HttpGet httpget = new HttpGet("https://online.tivo.com/start/watch/JustForMeTVE?forceAuth=1");
		CloseableHttpResponse response = (CloseableHttpResponse) httpClient.execute(httpget);
		
		String responseBody = readResponseBody(response);
		//System.out.println(responseBody);
		
		Pattern pattern = Pattern.compile("name=\\\"SAMLRequest\\\" value=\\\"([^\\\"]*)\\\"", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(responseBody);
        matcher.find();
        String SAMLRequest = matcher.group(1);
        
        pattern = Pattern.compile("name=\\\"RelayState\\\" value=\\\"([^\\\"]*)\\\"", Pattern.MULTILINE);
        matcher = pattern.matcher(responseBody);
        matcher.find();
        String RelayState = matcher.group(1);
        
        pattern = Pattern.compile("method=\\\"POST\\\" action=\\\"([^\\\"]*)\\\"", Pattern.MULTILINE);
        matcher = pattern.matcher(responseBody);
        matcher.find();
        String SamlPostUrl = matcher.group(1);
        
        ClassicHttpRequest login = ClassicRequestBuilder.post()
                .setUri(new URI(SamlPostUrl))
                .addParameter("SAMLRequest", SAMLRequest)
                .addParameter("RelayState", RelayState)
                .build();
        HttpClientContext httpContext = HttpClientContext.create();
        response = (CloseableHttpResponse) httpClient.execute(login, httpContext);
        RedirectLocations redirectLocations = httpContext.getRedirectLocations();
        URI redirectUrl = redirectLocations.get(redirectLocations.size()-1);
        
        
		responseBody = readResponseBody(response);
		
        pattern = Pattern.compile("auraConfig = ([^;]*);", Pattern.MULTILINE);
        matcher = pattern.matcher(responseBody);
        matcher.find();
        String auraConfig = matcher.group(1);
        JSONObject auraConfigObject = new JSONObject(auraConfig);
        JSONObject context = auraConfigObject.getJSONObject("context");
        
        JSONObject loaded = context.getJSONObject("loaded");
        
        JSONObject auraContext = new JSONObject("{\"mode\":\"PROD\",\"fwuid\":\"\",\"app\":\"siteforce:loginApp2\",\"loaded\":\"\",\"dn\":[],\"globals\":{},\"uad\":false}");
        auraContext.put("fwuid", context.get("fwuid"));
        auraContext.put("loaded", loaded);
        //System.out.println(auraContext);
        
        JSONObject auth = new JSONObject("{\"actions\":[{\"id\":\"87;a\",\"descriptor\":\"apex://Tivo_idp_LightningLoginFormController/ACTION$login\",\"callingDescriptor\":\"markup://c:tivo_idp_login_form\",\"params\": {\"username\":username,\"password\":password,\"startUrl\":\"[\\\"startURL=/idp/login?app=0spfI0000001IzF\\\"]\",\"relayState\":\"RelayState=web%3A%2Fstart%2Fwatch%2Fscreens%2Fall%3FforceAuth%3D1\"}}]}");
        JSONObject authParams = auth.getJSONArray("actions").getJSONObject(0).getJSONObject("params");
        authParams.put("username", config.getTivoUsername());
        authParams.put("password", config.getTivoPassword());
        
        login = ClassicRequestBuilder.post()
                .setUri(new URI("https://tivoidp.tivo.com/s/sfsites/aura?r=4&other.Tivo_idp_LightningLoginForm.login=1"))
                .addParameter("message", auth.toString())
                .addParameter("aura.context", auraContext.toString())
                .addParameter("aura.pageURI", redirectUrl.getPath() + "?" + redirectUrl.getQuery())
                .addParameter("aura.token", "undefined")
                .build();
        
        response = (CloseableHttpResponse) httpClient.execute(login);
		responseBody = readResponseBody(response);
		//System.out.println(responseBody);
		JSONObject loginResponse = new JSONObject(responseBody);
		if (! (loginResponse.get("events") instanceof JSONArray) ) {
			throw new Exception("login failed");
		}
		JSONObject loginEvents =  loginResponse.getJSONArray("events").getJSONObject(0);
		String frontDoor = loginEvents.getJSONObject("attributes").getJSONObject("values").getString("url");
		
		httpget = new HttpGet(frontDoor);
		response = (CloseableHttpResponse) httpClient.execute(httpget);
		responseBody = readResponseBody(response);
		
        pattern = Pattern.compile("window\\.location\\.href=\\\"([^\\\"]*)\\\"", Pattern.MULTILINE);
        matcher = pattern.matcher(responseBody);
        matcher.find();
        String frontDoorResponse = matcher.group(1);
        
		httpget = new HttpGet("https://tivoidp.tivo.com" + frontDoorResponse);
		response = (CloseableHttpResponse) httpClient.execute(httpget);
		responseBody = readResponseBody(response);
		
        pattern = Pattern.compile("name=\\\"SAMLResponse\\\" value=\\\"([^\\\"]*)\\\"", Pattern.MULTILINE);
        matcher = pattern.matcher(responseBody);
        matcher.find();
        String SAMLResponse = matcher.group(1);
        
        pattern = Pattern.compile("action=\\\"([^\\\"]*)\\\"", Pattern.MULTILINE);
        matcher = pattern.matcher(responseBody);
        matcher.find();
        String SAMLResponseAction = matcher.group(1)
        		.replaceAll("&#x3a;", ":")
        		.replaceAll("&#x2f;", "/");
        
        login = ClassicRequestBuilder.post()
                .setUri(new URI(SAMLResponseAction))
                .addParameter("SAMLResponse", SAMLResponse)
                .build();
        
        response = (CloseableHttpResponse) httpClient.execute(login);
        responseBody = readResponseBody(response);
        for (Cookie cookie : this.cookieStore.getCookies()) {
        	if (cookie.getName().equals("domainToken")) {
        		config.setDomainToken(cookie.getValue(), cookie.getExpiryDate().getTime());
        		config.save();
        		return cookie;
        	}
    	}
        return null;
	}

	private String readResponseBody(CloseableHttpResponse response) throws UnsupportedOperationException, IOException {
		ByteBuffer bb = ByteBuffer.allocate(1024*1024);
		InputStream is = response.getEntity().getContent();
		byte[] data = new byte[4096];
		int read = 0;

		while ((read = is.read(data, 0, data.length)) != -1) {
		    bb.put(data, 0, read);
		}
		return new String(bb.array());
	}
    
    public static void main(String args[]) throws Exception {
    	//config.parse();
    	GetDomainToken dt = new GetDomainToken();
    	Cookie cookie = dt.getToken();
    	System.out.println(cookie);
    	cookie.getExpiryDate().getTime();
    	cookie.getValue();
    }
}
