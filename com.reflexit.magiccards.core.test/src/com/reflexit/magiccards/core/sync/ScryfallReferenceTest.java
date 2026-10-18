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

import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import com.reflexit.magiccards.core.sync.ScryfallReference.Symbol;

/**
 * The Scryfall reference lists (symbols, keyword abilities, card types): parsing,
 * and the bundled snapshot that makes a fresh install work off the shelf. Pure
 * logic - runs as a plain JUnit test (no network).
 */
public class ScryfallReferenceTest extends TestCase {

	public void testParseCatalog() {
		List<String> l = ScryfallReference.parseCatalog(
				"{\"object\":\"catalog\",\"total_values\":3,\"data\":[\"Flying\",\" Haste \",\"\",\"Level Up\"]}");
		assertEquals(3, l.size());
		assertEquals("Flying", l.get(0));
		assertEquals("Haste", l.get(1));
		assertEquals("Level Up", l.get(2));
	}

	public void testParseCatalogRejectsGarbage() {
		assertTrue(ScryfallReference.parseCatalog(null).isEmpty());
		assertTrue(ScryfallReference.parseCatalog("").isEmpty());
		assertTrue(ScryfallReference.parseCatalog("<html>rate limited</html>").isEmpty());
		assertTrue(ScryfallReference.parseCatalog("{\"object\":\"error\",\"status\":429}").isEmpty());
		assertTrue(ScryfallReference.parseCatalog("{\"data\":[]}").isEmpty());
	}

	public void testParseSymbology() {
		Map<String, Symbol> m = ScryfallReference.parseSymbology("{\"object\":\"list\",\"data\":["
				+ "{\"symbol\":\"{R}\",\"svg_uri\":\"https://svgs.scryfall.io/card-symbols/R.svg\",\"loose_variant\":null},"
				+ "{\"symbol\":\"{W/U}\",\"svg_uri\":\"https://svgs.scryfall.io/card-symbols/WU.svg?1734\",\"loose_variant\":\"{U/W}\"},"
				+ "{\"symbol\":\"{NOFILE}\",\"svg_uri\":null}]}");
		assertEquals(3, m.size()); // {R}, {W/U} and its loose variant
		assertEquals("R.svg", m.get("{R}").file);
		assertEquals("WU.svg", m.get("{W/U}").file); // query string dropped
		assertEquals("WU.svg", m.get("{U/W}").file);
		assertNull(m.get("{NOFILE}"));
	}

	public void testParseSymbologyRejectsGarbage() {
		assertTrue(ScryfallReference.parseSymbology(null).isEmpty());
		assertTrue(ScryfallReference.parseSymbology("not json").isEmpty());
		assertTrue(ScryfallReference.parseSymbology("{\"data\":[]}").isEmpty());
	}

	public void testFileOf() {
		assertEquals("R.svg", ScryfallReference.fileOf("https://svgs.scryfall.io/card-symbols/R.svg"));
		assertEquals("R.svg", ScryfallReference.fileOf("https://svgs.scryfall.io/card-symbols/R.svg?123"));
		assertNull(ScryfallReference.fileOf(null));
		assertNull(ScryfallReference.fileOf("https://svgs.scryfall.io/card-symbols/"));
	}

	// ---- the card's own keywords (parser)

	public void testKeywordsString() throws Exception {
		org.json.simple.JSONArray a = (org.json.simple.JSONArray) new org.json.simple.parser.JSONParser()
				.parse("[\"Trample\",\" Landfall \",\"\",\"Living weapon\"]");
		assertEquals("Trample|Landfall|Living weapon", ParseScryFallChecklist.keywordsString(a));
		assertEquals("", ParseScryFallChecklist.keywordsString(new org.json.simple.JSONArray()));
		assertEquals("", ParseScryFallChecklist.keywordsString(null));
	}

	// ---- the bundled snapshot

	public void testBundledSymbols() {
		assertEquals("R.svg", SymbolConverter.getScryfallFilename("{R}"));
		assertEquals("RGP.svg", SymbolConverter.getScryfallFilename("{R/G/P}"));
		assertEquals("2W.svg", SymbolConverter.getScryfallFilename("{2/W}"));
		assertEquals("T.svg", SymbolConverter.getScryfallFilename("{T}"));
		assertEquals("HALF.svg", SymbolConverter.getScryfallFilename("{½}"));
		assertEquals("INFINITY.svg", SymbolConverter.getScryfallFilename("{∞}"));
		assertNull(SymbolConverter.getScryfallFilename("{NOT-A-SYMBOL}"));
		assertTrue(ScryfallReference.symbols().size() >= 80);
	}

	public void testBundledSymbolsHaveUrls() {
		assertEquals("https://svgs.scryfall.io/card-symbols/R.svg",
				SymbolConverter.getScryfallManaURL("{R}").toExternalForm().replaceAll("\\?.*", ""));
	}

	public void testPngName() {
		assertEquals("RG.png", SymbolConverter.pngName("RG.svg"));
		assertEquals("RG.png", SymbolConverter.pngName("RG.png"));
	}

	public void testBundledCatalogs() {
		assertTrue(ScryfallReference.catalog(ScryfallReference.KEYWORD_ABILITIES).contains("Flying"));
		assertTrue(ScryfallReference.catalog(ScryfallReference.ABILITY_WORDS).contains("Landfall"));
		assertTrue(ScryfallReference.catalog(ScryfallReference.KEYWORD_ACTIONS).contains("Scry"));
		assertTrue(ScryfallReference.catalog(ScryfallReference.SUPERTYPES).contains("Legendary"));
		assertTrue(ScryfallReference.catalog(ScryfallReference.CARD_TYPES).contains("Creature"));
		assertTrue(ScryfallReference.catalog(ScryfallReference.CREATURE_TYPES).contains("Elf"));
		assertTrue(ScryfallReference.catalog(ScryfallReference.PLANESWALKER_TYPES).contains("Jace"));
		assertTrue(ScryfallReference.catalog(ScryfallReference.ENCHANTMENT_TYPES).contains("Saga"));
		for (String c : ScryfallReference.CATALOGS)
			assertFalse(c + " is empty", ScryfallReference.catalog(c).isEmpty());
	}

	public void testUnknownCatalogIsEmptyNotNull() {
		assertNotNull(ScryfallReference.catalog("no-such-catalog"));
		assertTrue(ScryfallReference.catalog("no-such-catalog").isEmpty());
	}
}
