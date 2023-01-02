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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;

import net.straylightlabs.tivolibre.TivoDecoder;

import com.tivo.kmttg.util.log;

public class http {
	private static final int READ_TIMEOUT = 120; // Timeout for InputStream reads
	private static final SSLSocketFactory TRUST_ANY = createSocketFactory();

	private static final HostnameVerifier VERIFY_ANY = new HostnameVerifier() {
		public boolean verify(String hostname, SSLSession session) {
			return true;
		}
	};

	private static SSLSocketFactory createSocketFactory() {
		TrustManager trustAny = new X509TrustManager() {
			public void checkClientTrusted(X509Certificate[] certs, String authType) {
			}

			public void checkServerTrusted(X509Certificate[] certs, String authType) {
			}

			public X509Certificate[] getAcceptedIssuers() {
				return null;
			}
		};
		try {
			SSLContext context = SSLContext.getInstance("SSL");
			context.init(null, new TrustManager[] { trustAny }, new SecureRandom());
			return context.getSocketFactory();
		} catch (Exception ex) {
			log.print("SSL Error: " + ex.getMessage());
			return null;
		}
	}

	private static URLConnection getConnection(URL url) throws Exception {
		URLConnection connection = url.openConnection();
		if (connection instanceof HttpsURLConnection) {
			HttpsURLConnection conn = (HttpsURLConnection) connection;
			conn.setHostnameVerifier(VERIFY_ANY);
			if (TRUST_ANY != null) {
				conn.setSSLSocketFactory(TRUST_ANY);
			}
		}
		return connection;
	}

	private static PoolingHttpClientConnectionManager insecureConnectionManager = null;

	private static PoolingHttpClientConnectionManager getInsecureConnectionManager()
			throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException {
		if (insecureConnectionManager == null) {
			insecureConnectionManager = PoolingHttpClientConnectionManagerBuilder.create()
					.setSSLSocketFactory(SSLConnectionSocketFactoryBuilder.create()
							.setSslContext(
									SSLContextBuilder.create().loadTrustMaterial(TrustAllStrategy.INSTANCE).build())
							.setHostnameVerifier(NoopHostnameVerifier.INSTANCE).build())
					.build();
		}

		return insecureConnectionManager;
	}

	private static CloseableHttpClient createInsecureHttpClient(String host, int port, final String username,
			final String password) throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException {
		BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
		credsProvider.setCredentials(new AuthScope(host, port),
				new UsernamePasswordCredentials(username, password.toCharArray()));
		CloseableHttpClient httpclient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider)
				.setDefaultCookieStore(new BasicCookieStore()).setConnectionManager(getInsecureConnectionManager())
				.build();
		return httpclient;
	}

	public static Boolean download(String urlString, String username, String password, String outFile, Boolean cookies,
			String offset) throws IOException, InterruptedException, Exception {
		CloseableHttpResponse in;
		URL url = new URL(urlString);

		CloseableHttpClient httpclient = createInsecureHttpClient(url.getHost(), url.getPort(), username, password);

		final HttpGet httpget = new HttpGet(urlString);
		if (offset != null) {
			httpget.setHeader("Range", "bytes=" + offset + "-");
		}

		in = httpclient.execute(httpget);
		if (in == null) {
			return false;
		} else {
			int BUFSIZE = 65536;
			byte[] buffer = new byte[BUFSIZE];
			int c;
			FileOutputStream out = null;
			try {
				out = new FileOutputStream(outFile);
				InputStream is = in.getEntity().getContent();
				while ((c = is.read(buffer, 0, BUFSIZE)) != -1) {
					if (Thread.interrupted()) {
						httpget.abort();
						out.close();
						in.close();
						throw new InterruptedException("Killed by user");
					}
					out.write(buffer, 0, c);
				}
				out.close();
				in.close();
			} catch (FileNotFoundException e) {
				log.error(urlString + ": " + e.getMessage());
				if (httpget != null)
					httpget.abort();
				if (out != null)
					out.close();
				if (in != null)
					in.close();
				throw new FileNotFoundException(e.getMessage());
			} catch (IOException e) {
				log.error(urlString + ": " + e.getMessage());
				if (httpget != null)
					httpget.abort();
				if (out != null)
					out.close();
				if (in != null)
					in.close();
				throw new IOException(e.getMessage());
			} catch (Exception e) {
				log.error(urlString + ": " + e.getMessage());
				if (httpget != null)
					httpget.abort();
				if (out != null)
					out.close();
				if (in != null)
					in.close();
				throw new Exception(e.getMessage(), e);
			} finally {
				if (out != null)
					out.close();
				if (in != null)
					in.close();
			}
		}

		return true;
	}

	@SuppressWarnings("resource")
	public static Boolean downloadPiped(String urlString, String username, String password, OutputStream out,
			Boolean cookies, String offset) throws IOException, InterruptedException, Exception {
		CloseableHttpResponse in;
		URL url = new URL(urlString);

		CloseableHttpClient httpclient = createInsecureHttpClient(url.getHost(), url.getPort(), username, password);

		final HttpGet httpget = new HttpGet(urlString);
		if (offset != null) {
			httpget.setHeader("Range", "bytes=" + offset + "-");
		}

		in = httpclient.execute(httpget);

		if (in == null)
			return false;

		int BUFSIZE = 65536;
		byte[] buffer = new byte[BUFSIZE];
		int c;
		try {
			InputStream is = in.getEntity().getContent();
			while ((c = is.read(buffer, 0, BUFSIZE)) != -1) {
				if (Thread.interrupted()) {
					httpget.abort();
					out.close();
					in.close();
					throw new InterruptedException("Killed by user");
				}
				out.write(buffer, 0, c);
			}
			out.close();
			in.close();
		} catch (FileNotFoundException e) {
			log.error(urlString + ": " + e.getMessage());
			if (httpget != null)
				httpget.abort();
			if (out != null)
				out.close();
			if (in != null)
				in.close();
			throw new FileNotFoundException(e.getMessage());
		} catch (IOException e) {
			log.error(urlString + ": " + e.getMessage());
			if (httpget != null)
				httpget.abort();
			if (out != null)
				out.close();
			if (in != null)
				in.close();
			throw new IOException(e.getMessage());
		} catch (Exception e) {
			log.error(urlString + ": " + e.getMessage());
			if (httpget != null)
				httpget.abort();
			if (out != null)
				out.close();
			if (in != null)
				in.close();
			throw new Exception(e.getMessage(), e);
		} finally {
			if (out != null)
				out.close();
			if (in != null)
				in.close();
		}

		return true;
	}

	public static Boolean downloadPipedStream(String urlString, String username, String password, Boolean cookies,
			jobData job) throws IOException, InterruptedException, Exception {

		BufferedInputStream in;
		CloseableHttpResponse response;
		int BUFFER_SIZE = 8192;
		URL url = new URL(urlString);

		CloseableHttpClient httpclient = createInsecureHttpClient(url.getHost(), url.getPort(), username, password);

		final HttpGet httpget = new HttpGet(urlString);

		response = httpclient.execute(httpget);

		in = new BufferedInputStream(response.getEntity().getContent());

		final BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(job.mpegFile));
		final PipedInputStream pipedIn = new PipedInputStream(BUFFER_SIZE);
		PipedOutputStream pipedOut = new PipedOutputStream(pipedIn);

		// Start a background tivolibre input pipe
		Runnable r = new Runnable() {
			public void run() {
				Boolean compat_mode = config.tivolibreCompat == 1;
				log.warn("tivolibre DirectShow compatilibity mode = " + compat_mode);
				TivoDecoder decoder = new TivoDecoder.Builder().input(pipedIn).output(out)
						.compatibilityMode(compat_mode).mak(config.MAK).build();
				decoder.decode();
			}
		};
		Thread thread = new Thread(r);
		thread.start();

		// Read from TiVo and pipe to tivolibre
		int BUFSIZE = 65536;
		long bytes = 0;
		byte[] buffer = new byte[BUFSIZE];
		int c;
		try {
			while ((c = in.read(buffer, 0, BUFSIZE)) != -1) {
				if (Thread.interrupted()) {
					httpget.abort();
					out.close();
					in.close();
					pipedOut.flush();
					pipedOut.close();
					pipedIn.close();
					response.close();
					throw new InterruptedException("Killed by user");
				}
				pipedOut.write(buffer, 0, c);
				bytes += c;
				if (job.limit > 0 && bytes > job.limit) {
					break;
				}
			}
			pipedOut.flush();
		} finally {
			pipedOut.close();
			pipedIn.close();
			out.close();
			in.close();
			thread.join();
			response.close();
		}

		return true;
	}

	// Check URL is alive with specificed connection timeout
	public static Boolean isAlive(String urlString, int timeout) {
		try {
			URL url = new URL(urlString);
			URLConnection conn = getConnection(url);
			conn.setConnectTimeout(timeout * 1000);
			conn.connect();
		} catch (Exception e) {
			log.error("isAlive: " + urlString + " - " + e.getMessage());
			return false;
		}
		return true;
	}

	public static String getLocalhostIP() {
		try {
			InetAddress localhost = InetAddress.getLocalHost();
			return localhost.getHostAddress();
		} catch (UnknownHostException e) {
			log.error("getLocalhostIP - " + e.getMessage());
		}
		return null;
	}

}
