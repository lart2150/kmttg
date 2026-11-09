package com.tivo.kmttg.rpc;


import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.protocol.RedirectLocations;

import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.util.log;
import com.tivo.kmttg.JSON.JSONArray;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GetDomainToken {
	// The mobile app's login, which skips online.tivo.com: the app is its own SAML service
	// provider, and these are what authenticationConfigurationGet {domain: tivo} answers.
	static final String MOBILE_IDP = "https://mobileidp.tivo.com";
	static final String MOBILE_ISSUER = "tivomobile-acs";
	// Not a real host - the app's webview catches the post to it and keeps the assertion.
	static final String MOBILE_ACS = "http://tivomobile-acs/sso";
	static final String MOBILE_DEVICE_TYPE = "webPlayer";
	// The token is opaque, so it lasts serviceLogin's domainTokenValidateInterval (seconds,
	// about 12 days) as it does in the app. A day if that's missing.
	static final long MOBILE_TOKEN_LIFE = 24L*60*60*1000;

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
		String responseBody = executeToString(httpget);
		String SAMLRequest = find("name=\\\"SAMLRequest\\\" value=\\\"([^\\\"]*)\\\"", responseBody);
		String RelayState = find("name=\\\"RelayState\\\" value=\\\"([^\\\"]*)\\\"", responseBody);
		String SamlPostUrl = find("method=\\\"POST\\\" action=\\\"([^\\\"]*)\\\"", responseBody);

        ClassicHttpRequest login = ClassicRequestBuilder.post()
                .setUri(new URI(SamlPostUrl))
                .addParameter("SAMLRequest", SAMLRequest)
                .addParameter("RelayState", RelayState)
                .build();
        HttpClientContext httpContext = HttpClientContext.create();
        responseBody = executeToString(login, httpContext);
        URI redirectUrl = lastRedirect(httpContext);

        String samlPage = idpLogin("https://tivoidp.tivo.com", responseBody, redirectUrl,
        		"[\"startURL=/idp/login?app=0spfI0000001IzF\"]",
        		"RelayState=web%3A%2Fstart%2Fwatch%2Fscreens%2Fall%3FforceAuth%3D1");
        String SAMLResponse = samlResponse(samlPage);
        String SAMLResponseAction = find("action=\\\"([^\\\"]*)\\\"", samlPage)
        		.replaceAll("&#x3a;", ":")
        		.replaceAll("&#x2f;", "/");

        login = ClassicRequestBuilder.post()
                .setUri(new URI(SAMLResponseAction))
                .addParameter("SAMLResponse", SAMLResponse)
                .build();

        responseBody = executeToString(login);
        for (Cookie cookie : this.cookieStore.getCookies()) {
        	if (cookie.getName().equals("domainToken")) {
        		config.setDomainToken(cookie.getValue(), cookie.getExpiryInstant().toEpochMilli());
        		config.save();
        		return cookie;
        	}
    	}
        return null;
	}

	// As getToken, but logging in the way the mobile app does. Null if serviceLogin said no.
	public String getMobileToken() throws Exception {
		JSONObject info = ServiceLoginTLS.serviceLogin(mobileSamlResponse(), MOBILE_DEVICE_TYPE);
		if (info == null || ! info.has("domainToken")) {
			log.error("tivo.com serviceLogin failed: " + elideTokens(info));
			return null;
		}
		String token = info.getString("domainToken");
		config.setDomainToken(token, mobileTokenExpiry(info, System.currentTimeMillis()));
		config.save();
		return token;
	}

	static long mobileTokenExpiry(JSONObject partnerBodyInfo, long now) {
		long exp = config.jwtExpiry(partnerBodyInfo.optString("domainToken"));
		if (exp > 0)
			return exp;
		long interval = partnerBodyInfo.optLong("domainTokenValidateInterval", 0);
		return now + (interval > 0 ? interval*1000 : MOBILE_TOKEN_LIFE);
	}

	static String mobileAuthnRequest(String id, Instant now) {
		return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
			+ "<samlp:AuthnRequest xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" ForceAuthn=\"true\" ID=\"" + id + "\""
			+ " IsPassive=\"false\" IssueInstant=\"" + now.truncatedTo(ChronoUnit.SECONDS) + "\""
			+ " ProtocolBinding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\" Version=\"2.0\""
			+ " AssertionConsumerServiceURL=\"" + MOBILE_ACS + "\">"
			+ "<samlp:Issuer xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:assertion\">" + MOBILE_ISSUER + "</samlp:Issuer>"
			+ "<saml2p:NameIDPolicy xmlns:saml2p=\"urn:oasis:names:tc:SAML:2.0:protocol\" AllowCreate=\"true\""
			+ " Format=\"urn:oasis:names:tc:SAML:2.0:nameid-format:transient\"/></samlp:AuthnRequest>";
	}

	// The IdP's answer to our own AuthnRequest: the base64 SAMLResponse its last page would
	// have posted to MOBILE_ACS. Good for about five minutes.
	String mobileSamlResponse() throws Exception {
		String id = "_" + (new SecureRandom().nextLong() & Long.MAX_VALUE);
		String authn = mobileAuthnRequest(id, Instant.now());
		ClassicHttpRequest post = ClassicRequestBuilder.post()
				.setUri(new URI(MOBILE_IDP + "/idp/endpoint/HttpPost"))
				.addParameter("SAMLRequest", Base64.getEncoder().encodeToString(authn.getBytes(StandardCharsets.UTF_8)))
				.addParameter("RelayState", "mobile")
				.build();
		HttpClientContext httpContext = HttpClientContext.create();
		String responseBody = executeToString(post, httpContext);
		URI page = lastRedirect(httpContext);
		// The login page's startURL is the IdP's own record of our request, inresponseto and all.
		// Split the raw query: decoded, the startURL's own &s would cut it short.
		String startUrl = null;
		String query = page.getRawQuery();
		for (String param : query == null ? new String[0] : query.split("&")) {
			if (param.startsWith("startURL="))
				startUrl = URLDecoder.decode(param.substring(9), StandardCharsets.UTF_8);
		}
		if (startUrl == null)
			throw new Exception("mobile IdP did not redirect to its login page: " + page);
		return samlResponse(idpLogin(MOBILE_IDP, responseBody, page, "[\"startURL=" + startUrl + "\"]", "RelayState=mobile"));
	}

	private static String samlResponse(String page) throws Exception {
		return find("name=\\\"SAMLResponse\\\" value=\\\"([^\\\"]*)\\\"", page);
	}

	// serviceLogin's answer for a log or console, its tokens replaced by their lengths.
	static String elideTokens(JSONObject info) throws Exception {
		if (info == null)
			return "no answer";
		JSONObject copy = new JSONObject(info.toString());
		for (String secret : new String[] {"domainToken", "accessToken", "partnerAuthToken"}) {
			if (copy.has(secret))
				copy.put(secret, copy.getString(secret).length() + " chars");
		}
		return copy.toString(2);
	}

	// TiVo's Salesforce login form, from its login page to the page that posts the SAMLResponse on.
	private String idpLogin(String idp, String loginPage, URI page, String startUrl, String relayState) throws Exception {
        String auraConfig = find("auraConfig = ([^;]*);", loginPage);
        JSONObject auraConfigObject = new JSONObject(auraConfig);
        JSONObject context = auraConfigObject.getJSONObject("context");

        JSONObject loaded = context.getJSONObject("loaded");

        JSONObject auraContext = new JSONObject("{\"mode\":\"PROD\",\"fwuid\":\"\",\"app\":\"siteforce:loginApp2\",\"loaded\":\"\",\"dn\":[],\"globals\":{},\"uad\":false}");
        auraContext.put("fwuid", context.get("fwuid"));
        auraContext.put("loaded", loaded);

        JSONObject auth = new JSONObject("{\"actions\":[{\"id\":\"87;a\",\"descriptor\":\"apex://Tivo_idp_LightningLoginFormController/ACTION$login\",\"callingDescriptor\":\"markup://c:tivo_idp_login_form\",\"params\": {}}]}");
        JSONObject authParams = auth.getJSONArray("actions").getJSONObject(0).getJSONObject("params");
        authParams.put("username", config.getTivoUsername());
        authParams.put("password", config.getTivoPassword());
        authParams.put("startUrl", startUrl);
        authParams.put("relayState", relayState);

        ClassicHttpRequest login = ClassicRequestBuilder.post()
                .setUri(new URI(idp + "/s/sfsites/aura?r=4&other.Tivo_idp_LightningLoginForm.login=1"))
                .addParameter("message", auth.toString())
                .addParameter("aura.context", auraContext.toString())
                .addParameter("aura.pageURI", page.getPath() + "?" + page.getQuery())
                .addParameter("aura.token", "undefined")
                .build();

        String responseBody = executeToString(login);
		JSONObject loginResponse = new JSONObject(responseBody);
		if (! (loginResponse.get("events") instanceof JSONArray) ) {
			throw new Exception("login failed");
		}
		JSONObject loginEvents =  loginResponse.getJSONArray("events").getJSONObject(0);
		String frontDoor = loginEvents.getJSONObject("attributes").getJSONObject("values").getString("url");

		responseBody = executeToString(new HttpGet(frontDoor));
		String frontDoorResponse = find("window\\.location\\.href\\s*=\\s*\\\"([^\\\"]*)\\\"", responseBody);

		return executeToString(new HttpGet(idp + frontDoorResponse));
	}

	private static String find(String regex, String text) throws Exception {
		Matcher matcher = Pattern.compile(regex, Pattern.MULTILINE).matcher(text);
		if (! matcher.find())
			throw new Exception("tivo.com login: expected page content missing (" + regex + ")");
		return matcher.group(1);
	}

	private static URI lastRedirect(HttpClientContext httpContext) {
        RedirectLocations redirectLocations = httpContext.getRedirectLocations();
        return redirectLocations.get(redirectLocations.size()-1);
	}

	// Execute a request and return the full response body as a String.
	private String executeToString(ClassicHttpRequest request) throws IOException {
		return httpClient.execute(request, response -> readResponseBody(response));
	}

	private String executeToString(ClassicHttpRequest request, HttpClientContext context) throws IOException {
		return httpClient.execute(request, context, response -> readResponseBody(response));
	}

	private String readResponseBody(ClassicHttpResponse response) throws IOException {
		ByteBuffer bb = ByteBuffer.allocate(1024*1024);
		InputStream is = response.getEntity().getContent();
		byte[] data = new byte[4096];
		int read = 0;

		while ((read = is.read(data, 0, data.length)) != -1) {
		    bb.put(data, 0, read);
		}
		return new String(bb.array());
	}

	// Dev check of the mobile login against a config.ini's tivo.com account: prints what
	// serviceLogin answers, token elided, and saves nothing.
	// cdata.p12 is read from the config.ini's folder.
	//   GetDomainToken mobile <config.ini> [deviceType]
    public static void main(String args[]) throws Exception {
    	if (args.length >= 2 && args[0].equals("mobile")) {
    		List<String> ini = Files.readAllLines(Paths.get(args[1]), StandardCharsets.UTF_8);
    		config.setTivoUsername(ini.get(ini.indexOf("<tivo_username>") + 1).trim());
    		config.setTivoPassword(ini.get(ini.indexOf("<tivo_password>") + 1).trim());
    		config.programDir = Paths.get(args[1]).toAbsolutePath().getParent().toString();
    		String saml = new GetDomainToken().mobileSamlResponse();
    		System.out.println("SAMLResponse: " + saml.length() + " chars");
    		JSONObject info = ServiceLoginTLS.serviceLogin(saml, args.length > 2 ? args[2] : MOBILE_DEVICE_TYPE);
    		if (info != null && info.has("domainToken")) {
    			String token = info.getString("domainToken");
    			config.setDomainToken(token, System.currentTimeMillis() + 60000);
    			TiVoRPCWS ws = TiVoRPCWS.init(null, config.middlemind_host, config.middlemind_port);
    			System.out.println("bodyAuthenticate with it: " + (ws.waitForReady() ? "ok" : "refused"));
    			JSONObject cat = new JSONObject("{\"topLevelOnly\":true,\"noLimit\":true,\"orderBy\":\"label\"}");
    			JSONObject cats = ws.sendRequestAndWaitForResponse(ws.RpcRequest("categorySearch", false, cat));
    			System.out.println("categorySearch on the websocket: " + (cats == null ? "no answer"
    					: cats.has("category") ? cats.getJSONArray("category").length() + " top level categories" : cats.toString()));
    			ws.close();
    			System.out.println("jwt exp: " + config.jwtExpiry(token));
    		}
    		System.out.println(elideTokens(info));
    		System.exit(0);
    	}
    	GetDomainToken dt = new GetDomainToken();
    	Cookie cookie = dt.getToken();
    	System.out.println(cookie);
    }
}
