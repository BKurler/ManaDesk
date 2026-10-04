/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *     Rémi Dutil (2026) - network-free tests of WebUtils' "web not
 *                         accessible" handling: simulated outage fails web
 *                         URLs instantly but leaves file: URLs alone, a
 *                         refused connection fails fast with no retry and the
 *                         host is remembered as down, isWebUnavailable()
 *                         classification.
 *******************************************************************************/
package com.reflexit.magiccards.core.sync;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import com.reflexit.magiccards.core.WebUnavailableException;

/**
 * No real network: {@link WebUtils}' "web not accessible" handling - the
 * debug-only simulated outage, fail-fast on a refused connection (no retry, no
 * hang) and the per-host "known down" memory.
 */
public class WebUtilsTest {

	@After
	public void tearDown() {
		WebUtils.setSimulateWebDown(false); // also forgets the hosts marked down
	}

	@Test
	public void simulatedOutageFailsWebUrlsInstantly() throws Exception {
		WebUtils.setSimulateWebDown(true);
		long t0 = System.currentTimeMillis();
		try {
			WebUtils.openUrl(new URL("https://api.scryfall.com/bulk-data"));
			Assert.fail("expected WebUnavailableException");
		} catch (WebUnavailableException e) {
			// expected
		}
		Assert.assertTrue("must not wait on the network", System.currentTimeMillis() - t0 < 1000);
		Assert.assertFalse(WebUtils.isReachable(new URL("https://scryfall.com/")));
	}

	@Test
	public void simulatedOutageLeavesLocalFilesAlone() throws Exception {
		File f = File.createTempFile("webutils", ".txt");
		f.deleteOnExit();
		try (FileOutputStream out = new FileOutputStream(f)) {
			out.write("hello".getBytes("UTF-8"));
		}
		WebUtils.setSimulateWebDown(true);
		try (InputStream in = WebUtils.openUrl(f.toURI().toURL())) {
			Assert.assertEquals('h', in.read());
		}
		Assert.assertTrue(WebUtils.isReachable(f.toURI().toURL()));
	}

	@Test
	public void refusedConnectionFailsFastAndHostIsRemembered() throws Exception {
		int port;
		try (ServerSocket s = new ServerSocket(0)) {
			port = s.getLocalPort(); // free port, nothing listens once closed
		}
		URL url = new URL("http://127.0.0.1:" + port + "/x");
		try {
			WebUtils.openUrl(url); // 3 attempts by default - must not retry a refused connection
			Assert.fail("expected WebUnavailableException");
		} catch (WebUnavailableException e) {
			Assert.assertTrue(e.getCause() instanceof ConnectException);
		}
		// the host is now known down: the next call does not even try
		try {
			WebUtils.checkWebAccess(new URL("http://127.0.0.1:" + port + "/other"));
			Assert.fail("expected the host to be remembered as down");
		} catch (WebUnavailableException e) {
			Assert.assertNull("no connection attempted", e.getCause());
		}
	}

	@Test
	public void classifiesNoWebErrors() {
		Assert.assertTrue(WebUtils.isWebUnavailable(new WebUnavailableException("h")));
		Assert.assertTrue(WebUtils.isWebUnavailable(new UnknownHostException("h")));
		Assert.assertTrue(WebUtils.isWebUnavailable(new SocketTimeoutException("connect timed out")));
		Assert.assertTrue(WebUtils.isWebUnavailable(new IOException("wrapped", new ConnectException("refused"))));
		Assert.assertFalse(WebUtils.isWebUnavailable(new IOException("Connection error 404: Not Found")));
		Assert.assertFalse(WebUtils.isWebUnavailable(null));
	}
}
