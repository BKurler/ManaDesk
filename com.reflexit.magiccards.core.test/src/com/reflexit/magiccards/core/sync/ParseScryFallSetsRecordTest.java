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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Calendar;

import junit.framework.TestCase;

import com.reflexit.magiccards.core.model.Edition;
import com.reflexit.magiccards.core.model.Editions;

/**
 * How one record of Scryfall's set list becomes an {@link Edition} (code, alternative
 * codes of the old sets, icon, type, block, release date, digital flag, new versus
 * known) and how the paging and bad input are handled. Offline: the set list is a small
 * JSON text. Works as plain JUnit.
 */
public class ParseScryFallSetsRecordTest extends TestCase {

	private static String record(String code, String name, String type, String block, String released, String icon,
			boolean digital, int cards) {
		StringBuilder sb = new StringBuilder("{");
		sb.append("\"code\":\"").append(code).append("\",");
		sb.append("\"name\":\"").append(name).append("\",");
		sb.append("\"set_type\":\"").append(type).append("\",");
		if (block != null)
			sb.append("\"block\":\"").append(block).append("\",");
		sb.append("\"released_at\":\"").append(released).append("\",");
		if (icon != null)
			sb.append("\"icon_svg_uri\":\"").append(icon).append("\",");
		sb.append("\"digital\":").append(digital).append(",");
		sb.append("\"card_count\":").append(cards).append("}");
		return sb.toString();
	}

	private static String simple(String code, String name) {
		return record(code, name, "expansion", null, "2020-05-01", "https://svgs.scryfall.io/sets/" + code + ".svg?1", false, 10);
	}

	private static String page(boolean hasMore, String next, String... records) {
		StringBuilder sb = new StringBuilder("{\"has_more\":").append(hasMore);
		if (next != null)
			sb.append(",\"next_page\":\"").append(next).append("\"");
		sb.append(",\"data\":[");
		for (int i = 0; i < records.length; i++)
			sb.append(i > 0 ? "," : "").append(records[i]);
		return sb.append("]}").toString();
	}

	private static ParseScryFallSets parser;

	private static String run(String json) throws IOException {
		parser = new ParseScryFallSets();
		return parser.processFromReader(new BufferedReader(new StringReader(json)), null);
	}

	private static Edition only() {
		assertEquals(1, parser.getAll().size());
		return parser.getAll().iterator().next();
	}

	// ---- one record

	public void testRecordFields() throws Exception {
		run(page(false, null, record("prz", "Rec Test Set", "core", "Test Block", "2021-03-09",
				"https://svgs.scryfall.io/sets/prz.svg?1650000", true, 5)));
		Edition ed = only();
		assertEquals("Rec Test Set", ed.getName());
		assertEquals("the code is upper-cased", "PRZ", ed.getMainAbbreviation());
		assertEquals("Core", ed.getType());
		assertEquals("Test Block", ed.getBlock());
		assertEquals("prz", ed.getIconAbbr());
		assertTrue(ed.isDigital());
		Calendar c = Calendar.getInstance();
		c.setTime(ed.getReleaseDate());
		assertEquals(2021, c.get(Calendar.YEAR));
		assertEquals(Calendar.MARCH, c.get(Calendar.MONTH));
		assertEquals(9, c.get(Calendar.DAY_OF_MONTH));
	}

	public void testNoBlockMeansTheNameIsTheBlock() throws Exception {
		run(page(false, null, simple("prb", "Rec Test No Block")));
		assertEquals("Rec Test No Block", only().getBlock());
	}

	public void testNameIsTrimmed() throws Exception {
		run(page(false, null, record("prt", "  Rec Test Trim  ", "core", null, "2020-01-01", null, false, 1)));
		assertEquals("Rec Test Trim", only().getName());
	}

	public void testUnknownSetTypeIsOther() throws Exception {
		run(page(false, null, record("pru", "Rec Test Type", "masters", null, "2020-01-01", null, false, 3)));
		assertEquals("Other", only().getType());
	}

	// ---- icon token

	public void testIconTokenComesFromTheSvgFileName() throws Exception {
		run(page(false, null, record("pri", "Rec Test Icon", "core", null, "2020-01-01",
				"https://svgs.scryfall.io/sets/tdom.svg?1700000000", false, 3)));
		assertEquals("tdom", only().getIconAbbr());
	}

	public void testIconTokenWithoutQuery() throws Exception {
		run(page(false, null, record("prq", "Rec Test Icon Plain", "core", null, "2020-01-01",
				"https://svgs.scryfall.io/sets/abc.svg", false, 3)));
		assertEquals("abc", only().getIconAbbr());
	}

	public void testUnexpectedIconAddressGivesNoIcon() throws Exception {
		run(page(false, null, record("prn", "Rec Test Icon None", "core", null, "2020-01-01", "not-an-address", false, 3)));
		assertNull(only().getIconAbbr());
		run(page(false, null, record("prm", "Rec Test Icon Png", "core", null, "2020-01-01", "https://x.io/sets/a.png", false, 3)));
		assertNull(only().getIconAbbr());
		run(page(false, null, simple("prl", "Rec Test Icon Absent").replace("\"icon_svg_uri\":\"https://svgs.scryfall.io/sets/prl.svg?1\",", "")));
		assertNull(only().getIconAbbr());
	}

	// ---- alternative codes of old sets

	public void testOldSetsKeepTheirAlternativeCode() throws Exception {
		String[][] cases = { { "lea", "1E" }, { "leb", "2E" }, { "2ed", "2U" }, { "arn", "AN" }, { "mir", "MI" },
				{ "por", "PO" }, { "usg", "UZ" }, { "8ed", "8E" }, { "unf", "UNA" }, { "dvd", "DD3_DVD" } };
		for (String[] c : cases) {
			run(page(false, null, simple(c[0], "Rec Test Old " + c[0])));
			Edition ed = only();
			assertEquals(c[0].toUpperCase(), ed.getMainAbbreviation());
			assertTrue(c[0], ed.abbreviationOf(c[1]));
		}
	}

	public void testAlternativeCodeLookupIgnoresCase() throws Exception {
		run(page(false, null, simple("LEA", "Rec Test Upper Code")));
		assertTrue(only().abbreviationOf("1E"));
	}

	public void testRecentSetHasNoAlternativeCode() throws Exception {
		run(page(false, null, simple("prk", "Rec Test Recent")));
		assertEquals("", only().getExtraAbbreviations());
	}

	// ---- which sets are kept, new versus known

	public void testNewVersusKnownSets() throws Exception {
		Editions.getInstance().addEdition("Rec Test Known", "RTK");
		run(page(false, null, simple("rt1", "Rec Test Known"), simple("rt2", "Rec Test Brand New")));
		assertEquals(2, parser.getAll().size());
		assertEquals(1, parser.getNew().size());
		assertEquals("Rec Test Brand New", parser.getNew().iterator().next().getName());
	}

	public void testEmptySetsAreLeftOut() throws Exception {
		run(page(false, null, record("pre", "Rec Test Empty", "core", null, "2020-01-01", null, false, 0)));
		assertTrue(parser.getAll().isEmpty());
		assertTrue(parser.getNew().isEmpty());
	}

	public void testNoRecordsAtAll() throws Exception {
		assertNull(run(page(false, null)));
		assertTrue(parser.getAll().isEmpty());
	}

	// ---- paging and bad input

	public void testLastPageReturnsNoNextAddress() throws Exception {
		assertNull(run(page(false, null, simple("pg1", "Rec Test Page One"))));
	}

	public void testMorePagesReturnTheNextAddress() throws Exception {
		assertEquals("https://api.scryfall.com/sets?page=2",
				run(page(true, "https://api.scryfall.com/sets?page=2", simple("pg2", "Rec Test Page Two"))));
		assertEquals(1, parser.getAll().size());
	}

	public void testInvalidJsonIsReportedAsNoResults() throws Exception {
		try {
			run("this is not json");
			fail("expected a failure");
		} catch (RuntimeException e) {
			assertEquals("No results", e.getMessage());
		}
	}

	// ---- search address

	public void testSearchQueryIsTheSetListAddress() {
		assertEquals(ParseScryFallSets.BASE_SEARCH_URL, new ParseScryFallSets().getSearchQuery("anything").toString());
		assertEquals("https://api.scryfall.com/sets", ParseScryFallSets.BASE_SEARCH_URL);
	}
}
