/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *     Rémi Dutil (2026) - installLocalBulk() (offline import) + remoteBulkSizeMB()
 *                         / hasLocalBulk() for the first-run download prompt
 *******************************************************************************/
package com.reflexit.magiccards.core.sync;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.MagicLogger;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;

/**
 * Local cache of the Scryfall <em>Default Cards</em> bulk file
 * (<a href="https://scryfall.com/docs/api/bulk-data">bulk data</a>).
 * <p>
 * Scryfall re-publishes the bulk file a few times a day. {@link #getDefaultCardsFile}
 * returns a local copy, downloading a fresh one only when Scryfall's
 * {@code updated_at} moved. The file is kept on disk between runs so a repeated
 * "Update Card Database" with nothing new remote is instant. The whole card
 * database update parses that one file - there is no per-set split any more.
 */
public final class ScryfallBulkCache {

	private static final String BULK_INDEX_URL = "https://api.scryfall.com/bulk-data";
	private static final String BULK_TYPE = "default_cards";
	private static final long CHECK_TTL_MS = 60L * 60L * 1000L; // 1 hour

	// cached Scryfall bulk-data index
	private static volatile long lastIndexCheck = 0L;
	private static volatile String cachedRemoteUpdatedAt = null;
	private static volatile String cachedDownloadUri = null;
	private static volatile long cachedRemoteSize = -1L;

	private ScryfallBulkCache() {
	}

	// ------------------------------------------------------------------ paths

	private static File dir() {
		File d = new File(FileUtils.getStateLocationFile(), "scryfall");
		d.mkdirs();
		return d;
	}

	private static File bulkFile() {
		// Scryfall serves the bulk data as gzip-compressed JSON Lines.
		return new File(dir(), "default-cards.jsonl.gz");
	}

	private static File bulkMetaFile() {
		return new File(dir(), "default-cards.updated_at");
	}

	// -------------------------------------------------------------- public API

	/**
	 * @return {@code true} when Scryfall has published a bulk file newer than the
	 *         local copy (or there is no local copy) and we can reach it. Cheap -
	 *         only the small bulk-data index is fetched, and that at most once per
	 *         {@link #CHECK_TTL_MS}.
	 */
	public static boolean isRemoteBulkNewer() {
		refreshIndex();
		if (cachedRemoteUpdatedAt == null)
			return false; // offline / unreachable
		File f = bulkFile();
		if (!f.isFile() || f.length() == 0)
			return true;
		return !cachedRemoteUpdatedAt.equals(readMarker(bulkMetaFile()));
	}

	/** {@code true} once a Default Cards bulk file has been downloaded (or imported). */
	public static boolean hasLocalBulk() {
		File f = bulkFile();
		return f.isFile() && f.length() > 0;
	}

	/**
	 * Approximate size (MB) of the current remote Default Cards bulk file, from the
	 * Scryfall bulk-data index; {@code -1} when the index could not be reached.
	 */
	public static long remoteBulkSizeMB() {
		refreshIndex();
		return cachedRemoteSize > 0 ? cachedRemoteSize / (1024 * 1024) : -1L;
	}

	/**
	 * The next {@link #getDefaultCardsFile} call must use the on-disk file exactly
	 * as it is (a user import), not check freshness and re-download over it.
	 */
	private static volatile boolean useLocalOnce = false;

	/**
	 * Use {@code src} (a Scryfall bulk JSON-Lines file the user downloaded
	 * elsewhere - Default Cards, All Cards, …) as the local bulk file. The very
	 * next update parses <em>this</em> file; after that, normal freshness checks
	 * against Scryfall's Default Cards resume.
	 */
	public static synchronized void installLocalBulk(File src) throws IOException {
		if (src == null || !src.isFile() || src.length() == 0)
			throw new IOException("Not a usable card-data file: " + src);
		Files.copy(src.toPath(), bulkFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
		// stamp it with the current remote marker (best effort) so it is treated as
		// current; blank if Scryfall is unreachable.
		refreshIndex();
		writeMarker(bulkMetaFile(), cachedRemoteUpdatedAt == null ? "" : cachedRemoteUpdatedAt);
		useLocalOnce = true;
		trace("bulk file: IMPORTED from " + src + " (" + mb(bulkFile().length()) + ")");
	}

	/**
	 * A local copy of the Scryfall Default Cards bulk file, downloading a fresh one
	 * only when the local copy is missing or older than what Scryfall publishes.
	 * Honours {@code pm.isCanceled()} during the download.
	 *
	 * @throws IOException when there is no local copy and Scryfall is unreachable
	 */
	public static synchronized File getDefaultCardsFile(ICoreProgressMonitor pm) throws IOException {
		File file = bulkFile();
		boolean haveLocal = file.isFile() && file.length() > 0;
		String localUpdatedAt = haveLocal ? readMarker(bulkMetaFile()) : null;

		if (haveLocal && useLocalOnce) {
			useLocalOnce = false;
			trace("bulk file: using the imported file as-is");
			return file;
		}

		refreshIndex();
		String remoteUpdatedAt = cachedRemoteUpdatedAt;
		String downloadUri = cachedDownloadUri;

		if (haveLocal && (downloadUri == null || remoteUpdatedAt == null
				|| remoteUpdatedAt.equals(localUpdatedAt))) {
			return file;
		}
		if (downloadUri == null) {
			throw new IOException("Cannot reach Scryfall bulk data and there is no local copy to fall back on");
		}

		trace("bulk file: DOWNLOAD new copy: " + localUpdatedAt + " -> " + remoteUpdatedAt);
		long t0 = System.currentTimeMillis();
		download(new URL(downloadUri), file, pm);
		writeMarker(bulkMetaFile(), remoteUpdatedAt);
		trace("bulk file: DOWNLOAD done in " + (System.currentTimeMillis() - t0) / 1000 + "s, " + mb(file.length()));
		return file;
	}

	// ----------------------------------------------------------- bulk download

	private static void refreshIndex() {
		if (cachedRemoteUpdatedAt != null && System.currentTimeMillis() - lastIndexCheck < CHECK_TTL_MS)
			return;
		try {
			JSONObject index = (JSONObject) new JSONParser().parse(WebUtils.openUrlText(new URL(BULK_INDEX_URL)));
			for (Object o : (JSONArray) index.get("data")) {
				JSONObject entry = (JSONObject) o;
				if (BULK_TYPE.equals(entry.get("type"))) {
					Object upd = entry.get("updated_at");
					// Scryfall now only publishes the gzipped JSON Lines file;
					// "download_uri" (the old plain .json array) is gone.
					Object uri = entry.get("jsonl_download_uri");
					if (uri == null)
						uri = entry.get("download_uri");
					Object size = entry.get("size");
					cachedRemoteUpdatedAt = upd == null ? null : upd.toString();
					cachedDownloadUri = uri == null ? null : uri.toString();
					cachedRemoteSize = size instanceof Number ? ((Number) size).longValue() : -1L;
					lastIndexCheck = System.currentTimeMillis();
					return;
				}
			}
		} catch (Exception e) {
			MagicLogger.log("Scryfall bulk index unreachable (" + e.getMessage() + ")");
		}
	}

	private static void download(URL uri, File target, ICoreProgressMonitor pm) throws IOException {
		pm.subTask("Downloading Scryfall card data (Default Cards)");
		sweepPartials();
		File part = File.createTempFile("default-cards-", ".part", target.getParentFile());
		try {
			try (InputStream in = WebUtils.openUrl(uri);
					OutputStream out = new BufferedOutputStream(new FileOutputStream(part),
							FileUtils.DEFAULT_BUFFER_SIZE)) {
				byte[] buf = new byte[FileUtils.DEFAULT_BUFFER_SIZE];
				int n;
				while ((n = in.read(buf)) != -1) {
					if (pm.isCanceled())
						throw new IOException("Cancelled");
					out.write(buf, 0, n);
				}
			}
			Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} finally {
			part.delete();
		}
	}

	// --------------------------------------------------------------- helpers

	/** Remove leftover partial downloads from a cancelled / crashed run. */
	private static void sweepPartials() {
		File[] files = dir().listFiles((d, name) -> name.endsWith(".part"));
		if (files == null)
			return;
		for (File f : files)
			f.delete();
	}

	private static String readMarker(File f) {
		try {
			String s = new String(Files.readAllBytes(f.toPath()), FileUtils.CHARSET_UTF_8).trim();
			return s.isEmpty() ? null : s;
		} catch (IOException e) {
			return null;
		}
	}

	private static void writeMarker(File f, String value) {
		try {
			Files.write(f.toPath(), (value == null ? "" : value).getBytes(FileUtils.CHARSET_UTF_8));
		} catch (IOException e) {
			MagicLogger.log("Cannot write Scryfall marker " + f.getName() + ": " + e.getMessage());
		}
	}

	private static String mb(long bytes) {
		return (bytes / (1024 * 1024)) + " MB";
	}

	private static void trace(String msg) {
		System.err.println("[ScryfallBulk] " + msg);
	}
}
