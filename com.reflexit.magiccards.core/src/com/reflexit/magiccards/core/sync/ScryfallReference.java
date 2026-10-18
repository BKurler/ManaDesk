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
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.MagicLogger;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;

/**
 * The small reference lists Magic keeps adding to - mana/other symbols, keyword
 * abilities, ability words, card types and subtypes - read from Scryfall
 * (<a href="https://scryfall.com/docs/api/card-symbols">/symbology</a> and
 * <a href="https://scryfall.com/docs/api/catalogs">/catalog/*</a>) instead of
 * being hard-coded.
 *
 * <p>
 * Two copies exist, the same way the card database works:
 * <ul>
 * <li>a <b>bundled snapshot</b> (the {@code reference/*.json} resources next to
 * this class) so a fresh install works off the shelf, and
 * <li>a <b>refreshed copy</b> in the plug-in state folder, written by
 * {@link #refresh} when the user runs "Update Card Database". When present and
 * valid it wins over the snapshot.
 * </ul>
 * A failed or partial refresh never replaces a good file, and a corrupt refreshed
 * file silently falls back to the snapshot.
 */
public final class ScryfallReference {

	/** Set to {@code true} to trace what is loaded / refreshed. */
	static boolean TRACE_REFERENCE = false;

	public static final String SYMBOLOGY = "symbology";
	public static final String KEYWORD_ABILITIES = "keyword-abilities";
	public static final String ABILITY_WORDS = "ability-words";
	public static final String KEYWORD_ACTIONS = "keyword-actions";
	public static final String SUPERTYPES = "supertypes";
	public static final String CARD_TYPES = "card-types";
	public static final String ARTIFACT_TYPES = "artifact-types";
	public static final String BATTLE_TYPES = "battle-types";
	public static final String CREATURE_TYPES = "creature-types";
	public static final String ENCHANTMENT_TYPES = "enchantment-types";
	public static final String LAND_TYPES = "land-types";
	public static final String PLANESWALKER_TYPES = "planeswalker-types";
	public static final String SPELL_TYPES = "spell-types";

	/** Every catalog we keep, in the order they are fetched. */
	public static final String[] CATALOGS = { KEYWORD_ABILITIES, ABILITY_WORDS, KEYWORD_ACTIONS, SUPERTYPES, CARD_TYPES,
			ARTIFACT_TYPES, BATTLE_TYPES, CREATURE_TYPES, ENCHANTMENT_TYPES, LAND_TYPES, PLANESWALKER_TYPES,
			SPELL_TYPES };

	private static final String API = "https://api.scryfall.com/";
	private static final long PAUSE_MS = 100L; // Scryfall asks for 50-100 ms between requests

	/** One card symbol, e.g. {@code {R/G/P}} drawn from {@code RGP.svg}. */
	public static final class Symbol {
		public final String symbol;
		public final String file;
		public final String svgUri;

		Symbol(String symbol, String file, String svgUri) {
			this.symbol = symbol;
			this.file = file;
			this.svgUri = svgUri;
		}
	}

	private static final Object LOCK = new Object();
	/** name -> parsed catalog values (immutable). */
	private static Map<String, List<String>> catalogs = new LinkedHashMap<String, List<String>>();
	private static Map<String, Symbol> symbols = null;
	private static volatile long version = 1;

	private ScryfallReference() {
	}

	// -------------------------------------------------------------- public API

	/**
	 * Bumped every time a refresh changes what the getters return - lets other
	 * classes cache something derived from the lists and rebuild it only when needed.
	 */
	public static long version() {
		return version;
	}

	/** @return the values of a Scryfall catalog, in Scryfall's order; never {@code null} */
	public static List<String> catalog(String name) {
		synchronized (LOCK) {
			List<String> l = catalogs.get(name);
			if (l == null) {
				l = loadCatalog(name);
				catalogs.put(name, l);
			}
			return l;
		}
	}

	/** @return every symbol, keyed by its text ({@code "{R}"}); never {@code null} */
	public static Map<String, Symbol> symbols() {
		synchronized (LOCK) {
			if (symbols == null)
				symbols = loadSymbols();
			return symbols;
		}
	}

	/** @return the Scryfall SVG file name of a symbol ({@code "{R/G}"} -> {@code "RG.svg"}), or {@code null} */
	public static String symbolFile(String symbol) {
		Symbol s = symbols().get(symbol);
		return s == null ? null : s.file;
	}

	/**
	 * Fetches every list from Scryfall and keeps it for the next start. Best effort:
	 * a list that cannot be fetched or does not look right is left as it was.
	 *
	 * @return {@code true} if at least one list changed
	 */
	public static boolean refresh(ICoreProgressMonitor pm) {
		File dir = refreshedDir();
		boolean changed = false;
		List<String> names = new ArrayList<String>();
		names.add(SYMBOLOGY);
		Collections.addAll(names, CATALOGS);
		for (String name : names) {
			if (pm != null && pm.isCanceled())
				break;
			try {
				String json = WebUtils.openUrlText(new URL(API + urlPath(name)), 2);
				if (!isValid(name, json)) {
					MagicLogger.log("Scryfall " + name + ": unexpected answer, keeping the previous list");
					continue;
				}
				if (writeIfChanged(new File(dir, fileName(name)), json))
					changed = true;
				Thread.sleep(PAUSE_MS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			} catch (IOException e) {
				if (WebUtils.isWebUnavailable(e))
					break; // no web: the other lists would fail too
				MagicLogger.log("Scryfall " + name + ": " + e.getMessage());
			}
		}
		if (changed) {
			synchronized (LOCK) {
				catalogs.clear();
				symbols = null;
				version++;
			}
		}
		if (TRACE_REFERENCE)
			MagicLogger.trace("ScryfallReference.refresh: changed=" + changed);
		return changed;
	}

	/** Downloads every list into {@code dir} (to refresh the bundled snapshot - see {@code SymbolConverter.main}). */
	public static void downloadSnapshot(File dir) throws IOException {
		dir.mkdirs();
		List<String> names = new ArrayList<String>();
		names.add(SYMBOLOGY);
		Collections.addAll(names, CATALOGS);
		for (String name : names) {
			String json = WebUtils.openUrlText(new URL(API + urlPath(name)), 2);
			if (!isValid(name, json))
				throw new IOException("Unexpected answer for " + name);
			Files.write(new File(dir, fileName(name)).toPath(), json.getBytes(StandardCharsets.UTF_8));
			try {
				Thread.sleep(PAUSE_MS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	// ----------------------------------------------------------------- parsing

	/** @return the {@code data} strings of a Scryfall catalog answer; empty if it is not one */
	static List<String> parseCatalog(String json) {
		List<String> res = new ArrayList<String>();
		try {
			JSONObject root = (JSONObject) new JSONParser().parse(json);
			Object data = root.get("data");
			if (data instanceof JSONArray) {
				for (Object o : (JSONArray) data) {
					if (o instanceof String && !((String) o).trim().isEmpty())
						res.add(((String) o).trim());
				}
			}
		} catch (Exception e) {
			// not a catalog
		}
		return Collections.unmodifiableList(res);
	}

	/** @return the symbols of a Scryfall {@code /symbology} answer; empty if it is not one */
	static Map<String, Symbol> parseSymbology(String json) {
		Map<String, Symbol> res = new LinkedHashMap<String, Symbol>();
		try {
			JSONObject root = (JSONObject) new JSONParser().parse(json);
			Object data = root.get("data");
			if (data instanceof JSONArray) {
				for (Object o : (JSONArray) data) {
					if (!(o instanceof JSONObject))
						continue;
					JSONObject obj = (JSONObject) o;
					String symbol = (String) obj.get("symbol");
					String svg = (String) obj.get("svg_uri");
					String file = fileOf(svg);
					if (symbol == null || file == null)
						continue;
					res.put(symbol, new Symbol(symbol, file, svg));
					// "{U/W}" for "{W/U}": same picture
					Object loose = obj.get("loose_variant");
					if (loose instanceof String && !res.containsKey(loose))
						res.put((String) loose, new Symbol((String) loose, file, svg));
				}
			}
		} catch (Exception e) {
			// not a symbology
		}
		return Collections.unmodifiableMap(res);
	}

	/** {@code ".../card-symbols/R.svg?1734"} -> {@code "R.svg"} */
	static String fileOf(String svgUri) {
		if (svgUri == null)
			return null;
		int q = svgUri.indexOf('?');
		if (q >= 0)
			svgUri = svgUri.substring(0, q);
		int i = svgUri.lastIndexOf('/');
		if (i < 0 || i == svgUri.length() - 1)
			return null;
		return svgUri.substring(i + 1);
	}

	private static boolean isValid(String name, String json) {
		if (json == null)
			return false;
		return SYMBOLOGY.equals(name) ? !parseSymbology(json).isEmpty() : !parseCatalog(json).isEmpty();
	}

	// ------------------------------------------------------------------- files

	private static String urlPath(String name) {
		return SYMBOLOGY.equals(name) ? "symbology" : "catalog/" + name;
	}

	private static String fileName(String name) {
		return SYMBOLOGY.equals(name) ? "symbology.json" : "catalog-" + name + ".json";
	}

	/** Where refreshed lists are kept. */
	static File refreshedDir() {
		File d = new File(new File(FileUtils.getStateLocationFile(), "scryfall"), "reference");
		d.mkdirs();
		return d;
	}

	private static List<String> loadCatalog(String name) {
		List<String> l = parseCatalog(readText(name));
		if (l.isEmpty())
			l = parseCatalog(readBundled(name));
		return l;
	}

	private static Map<String, Symbol> loadSymbols() {
		Map<String, Symbol> m = parseSymbology(readText(SYMBOLOGY));
		if (m.isEmpty())
			m = parseSymbology(readBundled(SYMBOLOGY));
		return m;
	}

	/** The refreshed copy if there is one, else {@code null}. */
	private static String readText(String name) {
		try {
			File f = new File(refreshedDir(), fileName(name));
			if (f.isFile() && f.length() > 0)
				return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
		} catch (Throwable e) {
			// fall back to the snapshot
		}
		return null;
	}

	private static String readBundled(String name) {
		try (InputStream in = ScryfallReference.class.getResourceAsStream("reference/" + fileName(name))) {
			if (in == null) {
				MagicLogger.log("Missing bundled reference list: " + fileName(name));
				return null;
			}
			return FileUtils.readStreamAsStringAndClose(in);
		} catch (IOException e) {
			MagicLogger.log(e);
			return null;
		}
	}

	/** Writes {@code text} to {@code f} (via a temp file) unless that is what is already there. */
	private static boolean writeIfChanged(File f, String text) throws IOException {
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		if (f.isFile() && java.util.Arrays.equals(Files.readAllBytes(f.toPath()), bytes))
			return false;
		File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
		Files.write(tmp.toPath(), bytes);
		Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
		return true;
	}
}
