/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - "Work Offline" replaced by the debug-only
 *                         setSimulateWebDown(); no-hang policy: 8s connect
 *                         timeout, no retry on connection-level failures, a
 *                         host that failed is skipped for 60s
 *                         (checkWebAccess()); new isReachable() quick
 *                         connect-only probe and isWebUnavailable() error
 *                         classification.
 */
package com.reflexit.magiccards.core.sync;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.security.cert.X509Certificate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.MagicLogger;
import com.reflexit.magiccards.core.WebUnavailableException;

/**
 * All web access goes through here. The web may be unreachable at any time and
 * callers must cope quietly, so a connection failure never "hangs": the connect
 * timeout is short, a connection-level failure is not retried, and a host that
 * just failed is skipped (instant {@link WebUnavailableException}) for
 * {@link #HOST_DOWN_MS} instead of making every card image / set icon wait for
 * its own timeout.
 */
public class WebUtils {
	/** Testing only (debug launches): every web access fails as if the network
	 *  were down - see the "Simulate web not working" preference. */
	private static volatile boolean simulateWebDown = false;

	private static final int CONNECT_TIMEOUT_MS = 8 * 1000;
	private static final int READ_TIMEOUT_MS = 30 * 1000;
	/** How long a host that failed to connect is considered down. */
	private static final long HOST_DOWN_MS = 60 * 1000L;
	private static final Map<String, Long> downHosts = new ConcurrentHashMap<>();

	public static boolean isSimulateWebDown() {
		return simulateWebDown;
	}

	public static void setSimulateWebDown(boolean simulate) {
		simulateWebDown = simulate;
		downHosts.clear();
	}

	/**
	 * Fail fast, without touching the network, when {@code url} is a web URL
	 * that is known not to be reachable right now (simulated outage, or its host
	 * failed to connect less than {@link #HOST_DOWN_MS} ago). Non-web URLs
	 * ({@code file:}, {@code jar:}, ...) always pass.
	 */
	public static void checkWebAccess(URL url) throws WebUnavailableException {
		if (!isWebUrl(url))
			return;
		String host = url.getHost();
		if (simulateWebDown)
			throw new WebUnavailableException(host);
		Long until = downHosts.get(host);
		if (until != null) {
			if (System.currentTimeMillis() < until)
				throw new WebUnavailableException(host);
			downHosts.remove(host);
		}
	}

	/**
	 * Quick probe (connect timeout only, no retry, no request sent): can
	 * {@code url}'s server be connected to at all? A slow or unhappy server still
	 * counts as reachable. Blocking - never call it from the UI thread.
	 */
	public static boolean isReachable(URL url) {
		if (!isWebUrl(url))
			return true;
		try {
			checkWebAccess(url);
			URLConnection con = url.openConnection();
			con.setConnectTimeout(CONNECT_TIMEOUT_MS);
			con.setReadTimeout(CONNECT_TIMEOUT_MS);
			con.connect(); // TCP (+ TLS handshake for https)
			if (con instanceof HttpURLConnection)
				((HttpURLConnection) con).disconnect();
			return true;
		} catch (IOException e) {
			if (isWebUnavailable(e)) {
				markHostDown(url, e);
				return false;
			}
			return true; // connected, something else went wrong - let the real request report it
		}
	}

	/**
	 * {@code true} when {@code e} (or one of its causes) means "the web / host
	 * cannot be reached" rather than a real error: callers use it to stay silent
	 * or show a friendly "web not accessible" message instead of a stack trace.
	 */
	public static boolean isWebUnavailable(Throwable e) {
		for (Throwable t = e; t != null; t = t.getCause()) {
			if (t instanceof WebUnavailableException || t instanceof UnknownHostException
					|| t instanceof ConnectException || t instanceof NoRouteToHostException
					|| t instanceof SocketTimeoutException)
				return true;
			if (t.getCause() == t)
				break;
		}
		return false;
	}

	private static boolean isWebUrl(URL url) {
		if (url == null)
			return false;
		String p = url.getProtocol();
		return "http".equalsIgnoreCase(p) || "https".equalsIgnoreCase(p);
	}

	private static void markHostDown(URL url, IOException e) {
		if (e instanceof WebUnavailableException)
			return; // already known / simulated
		String host = url.getHost();
		if (downHosts.put(host, System.currentTimeMillis() + HOST_DOWN_MS) == null)
			MagicLogger.log("Web not accessible (" + host + "): " + e + " - skipping it for "
					+ HOST_DOWN_MS / 1000 + "s");
	}

	private static TrustManager[] trustedCerts = new TrustManager[] {
			new X509TrustManager() {
				@Override
				public java.security.cert.X509Certificate[] getAcceptedIssuers() {
					return null;
				}

				@Override
				public void checkClientTrusted(X509Certificate[] certs, String authType) {
				}

				@Override
				public void checkServerTrusted(X509Certificate[] certs, String authType) {
				}
			}
	};

	/**
	 * Open the specified URL. Works with HTTPS as well.<br/>
	 * Fails fast with {@link WebUnavailableException} when the host cannot be
	 * reached (see {@link #checkWebAccess(URL)}).<br/>
	 * <br/>
	 * HTTPS warning: Just to open and be able to read the content of resulting response. This implementation
	 * accept every server certificates therefore, as an example, it is not save against
	 * man-in-the-middle-attacks. Do not use with sensitive data thats needs a secure connection.
	 *
	 * @param url
	 *            Requested URL.
	 * @return response of request with the specified URL as an open stream.
	 * @throws IOException
	 */
	public static InputStream openUrl(URL url) throws IOException {
		return openUrl(url, 3);
	}

	public static InputStream openUrl(URL url, int maxAttempts) throws IOException {
		IOException rt = null;
		// 3 attempts
		for (int i = 0; i < maxAttempts; i++) {
			checkWebAccess(url);
			try {
				URLConnection openConnection = url.openConnection();
				// Checking if it is a HttpsURLConnection first and HttpURLConnection next.
				// Don't change this order due to HttpsURLConnection extends HttpURLConnection.
				if (openConnection instanceof HttpsURLConnection) {
					// HTTPS
					// Do stuff that trust everything.
					// MAYBE: HTTPS security
					try {
						// Do basics
						configureConnectionDefaults(openConnection);
						// Context stuff
						SSLContext ctx = SSLContext.getInstance("TLS");
						ctx.init(null, trustedCerts, null);
						
						HttpsURLConnection con = (HttpsURLConnection) openConnection;
						// Additional HTTPS connection configuration
						con.setSSLSocketFactory(ctx.getSocketFactory());
						con.setInstanceFollowRedirects(true);
						con.setRequestMethod("GET");
						con.connect();						// MAYBE: HTTPS response code handling
						// System.err.println(con.getResponseCode()+": "+url.toExternalForm());
					} catch (IOException e) {
						throw e;
					} catch (Exception e) {
						throw new IOException(e);
					}
				} else if (openConnection instanceof HttpURLConnection) {
					// Do basics
					configureConnectionDefaults(openConnection);
					// HTTP
					HttpURLConnection con = (HttpURLConnection) openConnection;
					con.setInstanceFollowRedirects(true);
					con.connect();					// MAYBE: HTTP response code handling
					int code = con.getResponseCode();
					String message = con.getResponseMessage();
					if (code<200 || code>=300)
						throw new IOException("Connection error "+code+": "+message);
				
					//System.err.println(code+": "+message+": "+url.toExternalForm());
				} else {
					// Not HTTP nor HTTPS connection, it can be local file i.e. file://
					i = maxAttempts; // we will try to open it only once
				}
				InputStream openStream = openConnection.getInputStream();
				return openStream;
			} catch (IOException e) {
				if (isWebUnavailable(e)) {
					// unreachable: retrying would only multiply the wait
					markHostDown(url, e);
					throw e instanceof WebUnavailableException ? e : new WebUnavailableException(url.getHost(), e);
				}
				MagicLogger.log("Connection error on url " + url + ": " + e.getMessage() + ". Attempt " + i);
				rt = e;
				continue;
			}
		}
		if (rt != null) {
			MagicLogger.log("Connection error on url " + url + ": " + rt.getMessage() + ". Giving up");
			throw rt;
		}
		throw new RuntimeException("Not possible");
	}

	public static BufferedReader openUrlReader(URL url, int attempts) throws IOException {
		InputStream openStream = openUrl(url, attempts);
		BufferedReader st = new BufferedReader(new InputStreamReader(openStream, FileUtils.CHARSET_UTF_8),
				FileUtils.DEFAULT_BUFFER_SIZE);
		return st;
	}

	/**
	 * Open an URL and return its content.
	 *
	 * @param url
	 * @return response content of specified URL.
	 * @throws IOException
	 *             If connection could not be established or content could not be read.
	 */
	public static String openUrlText(URL url) throws IOException {
		return openUrlText(url, 3);
	}

	public static String openUrlText(URL url, int attempts) throws IOException {
		MagicLogger.traceStart("reading: " + url.toExternalForm());
		try {
			return FileUtils.readStreamAsStringAndClose(openUrl(url, attempts));
		} finally {
			MagicLogger.traceEnd("reading: " + url.toExternalForm());
		}
	}

	/**
	 * Configure specified URLConnection with some defaults like "User-Agent", "Accept-Charset", timeouts,
	 * aso.
	 *
	 * @param connection
	 */
	private static void configureConnectionDefaults(URLConnection connection) {
		connection.setRequestProperty("User-Agent",
				"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:40.0) Gecko/20100101 Firefox/40.0");
		connection.setRequestProperty("Accept-Charset", FileUtils.UTF8);
		connection.setRequestProperty("Accept-Language", "en_US");
		connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
		connection.setReadTimeout(READ_TIMEOUT_MS);
	}

	public static Map<String, String> splitQuery(URL url) throws UnsupportedEncodingException {
		Map<String, String> query_pairs = new LinkedHashMap<String, String>();
		String query = url.getQuery();
		String[] pairs = query.split("&");
		for (String pair : pairs) {
			int idx = pair.indexOf("=");
			query_pairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
					URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
		}
		return query_pairs;
	}
}
