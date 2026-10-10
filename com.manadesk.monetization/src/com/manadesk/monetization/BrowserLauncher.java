/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil
 * All rights reserved.
 *
 * This file is NOT open-source.
 * Permission is granted to use this file ONLY as part of the ManaDesk application.
 * Modification, redistribution, or reuse of this file or its contents is prohibited.
 * You may NOT replace affiliate identifiers, ad URLs, or donation links.
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk: opens a web link in the user's own
 *                  browser without losing its query string.
 *     Rémi Dutil (2026) - opens links directly through java.awt.Desktop first;
 *                  the auto-forwarding local page is only a last resort
 *                  (affiliate networks forbid automatic redirects - Impact
 *                  stand-down policy).
 *******************************************************************************/
package com.manadesk.monetization;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.eclipse.swt.program.Program;

/**
 * Opens a link in the user's default browser, query string intact. SWT's
 * {@link Program#launch} lost everything after the first {@code &} on Windows
 * (TCGplayer Mass Entry opened empty), so links go, in order, through:
 * <ol>
 * <li>{@link Desktop#browse(URI)} - hands the whole URI to the system
 * browser;</li>
 * <li>a local HTML page forwarding to the full URL - only when Desktop is not
 * available. Not used for affiliate links in normal operation: affiliate
 * networks (Impact's stand-down policy) forbid automatic redirects;</li>
 * <li>{@link Program#launch} for links without a query string.</li>
 * </ol>
 */
public final class BrowserLauncher {
	private static final long KEEP_MS = 24L * 60 * 60 * 1000;

	private BrowserLauncher() {
	}

	/** @return {@code false} when the browser could not be started */
	public static boolean open(String url) {
		if (url == null || !MonetizationConfig.isWebUrl(url))
			return false;
		try {
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
				// '|' (Mass Entry's line separator) is not legal in a java.net.URI
				Desktop.getDesktop().browse(new URI(url.replace("|", "%7C")));
				return true;
			}
		} catch (Exception e) {
			MonetizationPlugin.warn("Could not open " + url + " through the desktop browser", e);
		}
		if (url.indexOf('&') < 0 && url.indexOf('|') < 0)
			return Program.launch(url);
		try {
			return Program.launch(redirectPage(url).getAbsolutePath());
		} catch (IOException e) {
			MonetizationPlugin.warn("Could not write the redirect page, opening the link directly", e);
			return Program.launch(url);
		}
	}

	/** A one-off HTML file forwarding to {@code url} (old ones are swept). */
	static File redirectPage(String url) throws IOException {
		File dir = new File(System.getProperty("java.io.tmpdir"), "manadesk-links");
		if (!dir.isDirectory() && !dir.mkdirs())
			throw new IOException("Cannot create " + dir);
		sweep(dir);
		File page = File.createTempFile("open-", ".html", dir);
		String attr = url.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
		String js = url.replace("\\", "\\\\").replace("'", "\\'").replace("<", "\\x3c");
		String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
				+ "<meta http-equiv=\"refresh\" content=\"0;url=" + attr + "\">"
				+ "<title>Opening...</title></head><body style=\"font-family:sans-serif\">"
				+ "<script>window.location.replace('" + js + "');</script>"
				+ "<p>Opening <a href=\"" + attr + "\">the page</a>...</p></body></html>";
		Files.write(page.toPath(), html.getBytes(StandardCharsets.UTF_8));
		return page;
	}

	private static void sweep(File dir) {
		File[] old = dir.listFiles((d, n) -> n.startsWith("open-") && n.endsWith(".html"));
		if (old == null)
			return;
		long limit = System.currentTimeMillis() - KEEP_MS;
		for (File f : old)
			if (f.lastModified() < limit)
				f.delete();
	}
}
