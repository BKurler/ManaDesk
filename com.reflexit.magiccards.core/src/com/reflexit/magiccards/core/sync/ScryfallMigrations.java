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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.MagicLogger;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;

/**
 * Scryfall sometimes retires a card id: a duplicate is <em>merged</em> into another
 * card, or a card is <em>deleted</em>
 * (<a href="https://scryfall.com/docs/api/migrations">/migrations</a>). A deck or
 * collection that still holds the old id would then point at a card the database no
 * longer has. This keeps the merges (old id to new id) so such a card can be linked to
 * the card that replaced it - see {@code DataManager#reconcile}.
 * <p>
 * The list is fetched when the card database is updated and kept in the state folder;
 * without it (never fetched, offline) nothing is remapped and the cards link the way
 * they always did.
 */
public final class ScryfallMigrations {

	private static final String URL_FIRST = "https://api.scryfall.com/migrations";
	private static final int MAX_PAGES = 100; // safety net, there are a handful
	private static final long PAUSE_MS = 100L; // Scryfall asks for 50-100 ms between requests

	private static final Object LOCK = new Object();
	private static Map<String, String> merges = null;

	private ScryfallMigrations() {
	}

	/**
	 * @return the id of the card that replaced {@code oldId} (following a chain of
	 *         merges), or {@code null} if Scryfall did not merge that id
	 */
	public static String newIdFor(String oldId) {
		if (oldId == null)
			return null;
		return resolve(loaded(), oldId);
	}

	/** Fetches the merges from Scryfall and keeps them. Best effort. @return {@code true} if the list changed */
	public static boolean refresh(ICoreProgressMonitor pm) {
		Map<String, String> fetched = new HashMap<String, String>();
		try {
			String url = URL_FIRST;
			for (int page = 0; url != null && page < MAX_PAGES; page++) {
				if (pm != null && pm.isCanceled())
					return false;
				String next = parsePage(WebUtils.openUrlText(new URL(url), 2), fetched);
				url = next;
				if (url != null)
					Thread.sleep(PAUSE_MS);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		} catch (IOException e) {
			if (!WebUtils.isWebUnavailable(e))
				MagicLogger.log("Scryfall migrations: " + e.getMessage());
			return false;
		}
		if (fetched.isEmpty())
			return false; // never replace what we have by nothing
		try {
			if (!write(file(), fetched))
				return false;
		} catch (IOException e) {
			MagicLogger.log(e);
			return false;
		}
		synchronized (LOCK) {
			merges = fetched;
		}
		return true;
	}

	// ----------------------------------------------------------------- parsing

	/**
	 * Adds the merges of one page of Scryfall's answer to {@code into}.
	 *
	 * @return the url of the next page, or {@code null} when this is the last one
	 */
	static String parsePage(String json, Map<String, String> into) {
		try {
			JSONObject root = (JSONObject) new JSONParser().parse(json);
			Object data = root.get("data");
			if (data instanceof JSONArray) {
				for (Object o : (JSONArray) data) {
					if (!(o instanceof JSONObject))
						continue;
					JSONObject m = (JSONObject) o;
					Object strategy = m.get("migration_strategy");
					Object oldId = m.get("old_scryfall_id");
					Object newId = m.get("new_scryfall_id");
					if ("merge".equals(strategy) && oldId instanceof String && newId instanceof String
							&& !oldId.equals(newId))
						into.put((String) oldId, (String) newId);
				}
			}
			Object more = root.get("has_more");
			Object next = root.get("next_page");
			return Boolean.TRUE.equals(more) && next instanceof String ? (String) next : null;
		} catch (Exception e) {
			return null;
		}
	}

	/** Follows old -> new -> newer..., with a guard against a cycle. */
	static String resolve(Map<String, String> map, String id) {
		String cur = map.get(id);
		for (int i = 0; cur != null && i < 10; i++) {
			String more = map.get(cur);
			if (more == null)
				return cur;
			cur = more;
		}
		return null; // a cycle: do not guess
	}

	// ------------------------------------------------------------------- files

	private static File file() {
		File d = new File(FileUtils.getStateLocationFile(), "scryfall");
		d.mkdirs();
		return new File(d, "migrations.tsv");
	}

	private static Map<String, String> loaded() {
		synchronized (LOCK) {
			if (merges == null)
				merges = read(file());
			return merges;
		}
	}

	/** One {@code old<TAB>new} per line. */
	static Map<String, String> read(File f) {
		Map<String, String> res = new HashMap<String, String>();
		try {
			if (f.isFile()) {
				List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
				for (String line : lines) {
					int tab = line.indexOf('\t');
					if (tab > 0 && tab < line.length() - 1)
						res.put(line.substring(0, tab).trim(), line.substring(tab + 1).trim());
				}
			}
		} catch (IOException e) {
			MagicLogger.log(e);
		}
		return res;
	}

	static boolean write(File f, Map<String, String> map) throws IOException {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String> e : map.entrySet())
			sb.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
		byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
		if (f.isFile() && java.util.Arrays.equals(Files.readAllBytes(f.toPath()), bytes))
			return false; // nothing new
		File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
		Files.write(tmp.toPath(), bytes);
		Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
		return true;
	}
}
