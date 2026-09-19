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
import java.net.URI;
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
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.ssl.SSLContextBuilder;

import com.tivo.kmttg.mux.MuxSink;
import com.tivo.kmttg.util.file;

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
					.setTlsSocketStrategy(ClientTlsStrategyBuilder.create()
							.setSslContext(
									SSLContextBuilder.create().loadTrustMaterial(TrustAllStrategy.INSTANCE).build())
							.setTlsVersions(TLS.V_1_0, TLS.V_1_1, TLS.V_1_2)
							.setHostVerificationPolicy(HostnameVerificationPolicy.CLIENT)
							.setHostnameVerifier(NoopHostnameVerifier.INSTANCE).buildClassic())
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
		ClassicHttpResponse in;
		URL url = new URI(urlString).toURL();

		CloseableHttpClient httpclient = createInsecureHttpClient(url.getHost(), url.getPort(), username, password);

		final HttpGet httpget = new HttpGet(urlString);
		if (offset != null) {
			httpget.setHeader("Range", "bytes=" + offset + "-");
		}

		in = httpclient.executeOpen(null, httpget, null);
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
		ClassicHttpResponse in;
		URL url = new URI(urlString).toURL();

		CloseableHttpClient httpclient = createInsecureHttpClient(url.getHost(), url.getPort(), username, password);

		final HttpGet httpget = new HttpGet(urlString);
		if (offset != null) {
			httpget.setHeader("Range", "bytes=" + offset + "-");
		}

		in = httpclient.executeOpen(null, httpget, null);

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
			jobData job, String offset) throws IOException, InterruptedException, Exception {

		BufferedInputStream in;
		ClassicHttpResponse response;
		int BUFFER_SIZE = 8192;
		URL url = new URI(urlString).toURL();

		CloseableHttpClient httpclient = createInsecureHttpClient(url.getHost(), url.getPort(), username, password);

		final HttpGet httpget = new HttpGet(urlString);
		if (offset != null) {
			httpget.setHeader("Range", "bytes=" + offset + "-");
		}

		// job.muxOnly says nothing downstream reads the decrypted stream, so it is not written:
		// the decoder gets a frame sink and no output stream, and the only file this job
		// produces is the MKV. TivoDecoder accepts one, the other or both.
		final Boolean streamOnly = job.muxOnly && job.muxFile != null;

		// Optionally mux straight to MKV from the same decode. job.muxFile is set when the
		// download job is standing in for a separate remux job: the decoder feeds the file
		// and the muxer at once, so nothing is read back off disk afterwards.
		//
		// Built before the TiVo connection is opened rather than after: the cover art lookup is
		// an rpc round trip plus an image download, and running that with the transfer already
		// open leaves the TiVo holding a stream nobody is reading for as long as it takes.
		final MuxSink muxSink;
		if (job.muxFile != null) {
			// The mkv lands under encodeDir, not next to the mpeg, and file naming can put it in
			// a sub-folder that does not exist yet. A remux job does this from launchJob; nothing
			// did on this path, so the muxer simply failed to open its own output.
			jobMonitor.createSubFolders(job.muxFile, job);
			muxSink = new MuxSink(new java.io.File(job.muxFile));
			muxSink.setSupplement(com.tivo.kmttg.task.remux.supplement(job));
			// Same reason as the cover art for being on this side of the connection: looking up
			// SkipMode segments is an rpc round trip and may open a websocket to tivo.com.
			com.tivo.kmttg.task.remux.attachChapters(muxSink, job);
			com.tivo.kmttg.task.remux.attachCoverArt(muxSink, job);
			// A streaming job already announced this file as its one output; only say it here
			// when the muxer is the second consumer of the decode.
			if (! streamOnly) log.print(">> ALSO REMUXING TO " + job.muxFile + " ...");
		} else {
			muxSink = null;
		}

		response = httpclient.executeOpen(null, httpget, null);

		in = new BufferedInputStream(response.getEntity().getContent());

		// Only once the TiVo has actually answered. Clearing it above, next to the rest of the
		// muxer setup, would mean a TiVo that never answers costs the user the file they had.
		if (muxSink != null) file.delete(job.muxFile);

		final BufferedOutputStream out = streamOnly
				? null : new BufferedOutputStream(new FileOutputStream(job.mpegFile));
		final PipedInputStream pipedIn = new PipedInputStream(BUFFER_SIZE);
		PipedOutputStream pipedOut = new PipedOutputStream(pipedIn);

		// Start a background tivolibre input pipe
		Runnable r = new Runnable() {
			public void run() {
				Boolean compat_mode = config.tivolibreCompat == 1;
				log.warn("tivolibre DirectShow compatilibity mode = " + compat_mode);
				TivoDecoder.Builder b = new TivoDecoder.Builder().input(pipedIn)
						.compatibilityMode(compat_mode).mak(config.MAK);
				if (out != null) b.output(out);
				if (muxSink != null) b.frameSink(muxSink);
				b.build().decode();
			}
		};
		Thread thread = new Thread(r);
		thread.start();

		// Read from TiVo and pipe to tivolibre
		int BUFSIZE = 65536;
		long bytes = 0;
		byte[] buffer = new byte[BUFSIZE];
		int c;
		Boolean downloadFinished = false;
		try {
			while ((c = in.read(buffer, 0, BUFSIZE)) != -1) {
				if (Thread.interrupted()) {
					httpget.abort();
					if (out != null) out.close();
					in.close();
					pipedOut.flush();
					pipedOut.close();
					pipedIn.close();
					response.close();
					throw new InterruptedException("Killed by user");
				}
				pipedOut.write(buffer, 0, c);
				bytes += c;
				// The only progress a streaming job has: with no .ts there is no growing file
				// for the job monitor to size.
				job.streamedBytes = bytes;
				if (job.limit > 0 && bytes > job.limit) {
					break;
				}
			}
			pipedOut.flush();
			downloadFinished = true;
		} finally {
			try {
				try {
					pipedOut.close();
					pipedIn.close();
					if (out != null) out.close();
					in.close();
				} finally {
					// The pipes are shut by now so the decoder is on its way out. Joining from
					// here rather than below the closes means one of them throwing - a full disk
					// flushing the last buffer - cannot leave the decode thread running.
					thread.join();
				}
				response.close();
			} finally {
				// Has to run on every way out, not after the try: a cancel throws from inside the
				// loop, and cleanup placed below it left a .part behind for every killed download.
				finishMux(job, muxSink, downloadFinished);
			}
		}

		return true;
	}

	// The decoder's own finally has ended the sink by now, so the muxed file is closed and can
	// be moved into place. Failure here costs the download only when the .ts was written too,
	// in which case a later remux job can still produce the MKV from it. A streaming job has
	// no such fallback, and the empty-output check in tdownload_decrypt fails and retries it.
	private static void finishMux(jobData job, MuxSink muxSink, Boolean downloadFinished) {
		if (job.muxFile == null) return;
		// A no-op once the decoder ended the sink, but it releases the handle if it did not -
		// and on Windows an open handle is what stops the delete below from working.
		muxSink.abort();
		if (! downloadFinished) {
			log.warn("remux during download did not finish - discarding " + job.muxFile);
			file.delete(job.muxFile);
		} else if (muxSink.isFailed()) {
			log.error("remux during download failed: " + muxSink.getFailure());
			file.delete(job.muxFile);
		} else if (file.isFile(job.muxFile) && ! file.isEmpty(job.muxFile)) {
			// A job that also wrote a .ts names that as its own output, so the MKV needs saying
			// here. A streaming job has no other output and the task announces this one itself.
			if (! job.muxOnly) log.print("---DONE--- remuxed to " + job.muxFile);
		} else {
			log.error("remux during download produced nothing");
			file.delete(job.muxFile);
		}
		com.tivo.kmttg.task.remux.reportNotes(muxSink);
	}

	// Check URL is alive with specificed connection timeout
	public static Boolean isAlive(String urlString, int timeout) {
		try {
			URL url = new URI(urlString).toURL();
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
