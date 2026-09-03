/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.MagicLogger;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;

/**
 * Local cache of the Scryfall <em>Default Cards</em> bulk file
 * (<a href="https://scryfall.com/docs/api/bulk-data">bulk data</a>), pre-split
 * into one small gzip flat file per set.
 * <p>
 * <b>Model.</b> Scryfall publishes the bulk file a few times a day. On startup a
 * background job calls {@link #ensureSplitAll} which, when the local split is out
 * of date, downloads the new bulk file and splits every set into
 * {@code <state>/scryfall/sets/<code>.txt.gz}. From then on a set update is just
 * a file read.
 * <p>
 * When an update is requested and the split is stale, {@link #flatFileForSet}
 * downloads the new bulk file, extracts <em>only the requested set</em> so the
 * update finishes quickly, then kicks a daemon thread to split the rest.
 * <p>
 * The big {@code default-cards.jsonl.gz} is deleted after a full split; only the
 * ~30&nbsp;MB of per-set files stay on disk.
 */
public final class ScryfallBulkCache {

	private static final String BULK_INDEX_URL = "https://api.scryfall.com/bulk-data";
	private static final String BULK_TYPE = "default_cards";
	private static final long CHECK_TTL_MS = 60L * 60L * 1000L; // 1 hour

	/** Guards the freshness marker and the "full split running" flag. */
	private static final Object SPLIT_LOCK = new Object();
	/** Serializes single-set targeted extractions (not held during a full split). */
	private static final Object TARGETED_LOCK = new Object();
	private static final AtomicBoolean bgSplitRunning = new AtomicBoolean(false);
	private static boolean fullSplitInProgress = false;

	/** Set code -> updated_at it was individually (re)written at, before the marker caught up. */
	private static final Map<String, String> freshlySplit = new ConcurrentHashMap<>();

	// cached Scryfall bulk-data index
	private static volatile long lastIndexCheck = 0L;
	private static volatile String cachedRemoteUpdatedAt = null;
	private static volatile String cachedDownloadUri = null;

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

	private static File splitDir() {
		File d = new File(dir(), "sets");
		d.mkdirs();
		return d;
	}

	private static File splitMarkerFile() {
		return new File(splitDir(), ".updated_at");
	}

	private static File setFile(String codeLower) {
		return new File(splitDir(), codeLower + ".txt.gz");
	}

	// -------------------------------------------------------------- public API

	/**
	 * Ensure {@code <state>/scryfall/sets/} holds a complete split of the current
	 * Scryfall bulk file. No-op when already up to date or offline with an
	 * existing split. Safe to call from several threads; only one full split runs
	 * at a time.
	 */
	public static void ensureSplitAll(ICoreProgressMonitor pm) throws IOException {
		String remote;
		synchronized (SPLIT_LOCK) {
			// If another thread is already doing the full split, wait for it
			// rather than proceeding on a half-written split.
			while (fullSplitInProgress) {
				try {
					SPLIT_LOCK.wait();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
			remote = currentRemoteUpdatedAt();
			String have = readMarker(splitMarkerFile());
			boolean populated = hasSplitFiles();
			if (populated && (remote == null || remote.equals(have))) {
				// up to date: nothing to do
				sweepLeftovers();
				return;
			}
			if (remote == null) {
				throw new IOException("Cannot reach Scryfall bulk data and there is no local split to fall back on");
			}
			fullSplitInProgress = true;
		}
		try {
			trace("full split starting (updated_at " + readMarker(splitMarkerFile()) + " -> " + remote + ")");
			long t0 = System.currentTimeMillis();
			File bulk = getDefaultCardsFile(pm);
			java.util.Set<String> written = new ParseScryFallChecklist().splitAllFromBulk(bulk, splitDir());
			synchronized (SPLIT_LOCK) {
				pruneOrphans(written);
				writeMarker(splitMarkerFile(), remote);
				freshlySplit.clear();
			}
			bulk.delete();
			bulkMetaFile().delete();
			trace("full split done: " + written.size() + " set(s) in " + (System.currentTimeMillis() - t0) / 1000
					+ "s");
		} finally {
			synchronized (SPLIT_LOCK) {
				fullSplitInProgress = false;
				SPLIT_LOCK.notifyAll();
			}
		}
	}

	/**
	 * A readable {@code <code>.txt.gz} flat file for one set, refreshing just that
	 * set from a newly published bulk file if necessary. Returns {@code null} when
	 * the set has no paper cards, or when offline and nothing is cached.
	 */
	public static File flatFileForSet(String codeLower, ICoreProgressMonitor pm) throws IOException {
		File f = setFile(codeLower);
		String have = readMarker(splitMarkerFile());
		String remote = currentRemoteUpdatedAt();
		boolean splitFresh = have != null && (remote == null || remote.equals(have));

		if (f.isFile()) {
			if (splitFresh || (remote != null && remote.equals(freshlySplit.get(codeLower)))) {
				return f;
			}
		} else if (splitFresh) {
			// a complete split exists and this set is not in it -> no paper cards
			return null;
		}

		if (remote == null) {
			// offline: best effort
			return f.isFile() ? f : null;
		}

		synchronized (TARGETED_LOCK) {
			// re-check now that we hold the lock
			if (f.isFile() && remote.equals(freshlySplit.get(codeLower)))
				return f;
			trace("set '" + codeLower + "' stale/missing - targeted extract from bulk file");
			File bulk = getDefaultCardsFile(pm);
			Map<String, List<MagicCard>> g = new ParseScryFallChecklist().groupSetsFromBulk(bulk,
					Collections.singleton(codeLower));
			List<MagicCard> cards = g.get(codeLower);
			if (cards == null || cards.isEmpty()) {
				freshlySplit.put(codeLower, remote);
				kickBackgroundSplit();
				return null;
			}
			new ParseScryFallChecklist().writeSetFlatGz(cards, f);
			freshlySplit.put(codeLower, remote);
		}
		kickBackgroundSplit();
		return f;
	}

	/** Start (once) a low-priority daemon thread that completes the full split. */
	public static void kickBackgroundSplit() {
		if (!bgSplitRunning.compareAndSet(false, true))
			return;
		Thread t = new Thread(() -> {
			try {
				ensureSplitAll(ICoreProgressMonitor.NONE);
			} catch (Exception e) {
				MagicLogger.log(e);
			} finally {
				bgSplitRunning.set(false);
			}
		}, "scryfall-bulk-split");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		t.start();
	}

	// ----------------------------------------------------------- bulk download

	/**
	 * @return a local copy of the Scryfall Default Cards bulk file, downloading it
	 *         only when the local copy is missing or older than what Scryfall
	 *         publishes.
	 */
	public static synchronized File getDefaultCardsFile(ICoreProgressMonitor pm) throws IOException {
		File file = bulkFile();
		boolean haveLocal = file.isFile() && file.length() > 0;
		String localUpdatedAt = haveLocal ? readMarker(bulkMetaFile()) : null;

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
					cachedRemoteUpdatedAt = upd == null ? null : upd.toString();
					cachedDownloadUri = uri == null ? null : uri.toString();
					lastIndexCheck = System.currentTimeMillis();
					return;
				}
			}
		} catch (Exception e) {
			MagicLogger.log("Scryfall bulk index unreachable (" + e.getMessage() + ")");
		}
	}

	private static String currentRemoteUpdatedAt() {
		refreshIndex();
		return cachedRemoteUpdatedAt;
	}

	private static void download(URL uri, File target, ICoreProgressMonitor pm) throws IOException {
		pm.subTask("Downloading Scryfall card data (Default Cards)");
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

	private static boolean hasSplitFiles() {
		String[] names = splitDir().list((d, name) -> name.endsWith(".txt.gz"));
		return names != null && names.length > 0;
	}

	private static void pruneOrphans(java.util.Set<String> keep) {
		File[] files = splitDir().listFiles((d, name) -> name.endsWith(".txt.gz"));
		if (files == null)
			return;
		for (File f : files) {
			String code = f.getName().substring(0, f.getName().length() - ".txt.gz".length());
			if (!keep.contains(code))
				f.delete();
		}
	}

	/** Remove a leftover bulk file / partial downloads when a complete split exists. */
	private static void sweepLeftovers() {
		File[] files = dir().listFiles((d, name) -> name.endsWith(".part")
				|| name.equals("default-cards.jsonl.gz") || name.equals("default-cards.updated_at"));
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
