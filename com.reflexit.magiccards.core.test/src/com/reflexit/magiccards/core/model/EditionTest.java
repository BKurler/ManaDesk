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
package com.reflexit.magiccards.core.model;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;

import junit.framework.TestCase;

/**
 * {@link Edition} on its own (not through {@link Editions}): names, abbreviations (main,
 * extra, fake), icon abbreviation of the special sets, set type parsing, the hidden flag,
 * aliases, release date and equality. Pure logic; works as plain JUnit.
 */
public class EditionTest extends TestCase {

	// ---- constructor

	public void testNameAndAbbreviation() {
		Edition ed = new Edition("Alpha Test", "ALT");
		assertEquals("Alpha Test", ed.getName());
		assertEquals("Alpha Test", ed.toString());
		assertEquals("ALT", ed.getMainAbbreviation());
		assertEquals(1, ed.getAbbreviations().length);
		assertEquals("", ed.getExtraAbbreviations());
		assertEquals("", ed.getExtraAliases());
		assertEquals(0, ed.getAliases().length);
		assertEquals("?", ed.getType());
		assertFalse(ed.isUnknown());
		assertFalse(ed.isDigital());
		assertNull(ed.getReleaseDate());
		assertNotNull(ed.getImageFiles());
	}

	public void testHtmlApostropheInNameIsDecoded() {
		assertEquals("Urza's Test", new Edition("Urza&#39;s Test", "UT1").getName());
	}

	public void testMissingOrBadAbbreviationBecomesAFakeOne() {
		assertEquals("_No_Abbr_Set", new Edition("No Abbr Set", null).getMainAbbreviation());
		// an abbreviation with a space is refused
		assertEquals("_Bad_Abbr", new Edition("Bad Abbr", "A B").getMainAbbreviation());
	}

	public void testEachEditionHasItsOwnId() {
		Edition a = new Edition("Id Test A", "ITA");
		Edition b = new Edition("Id Test B", "ITB");
		assertTrue(b.getId() > a.getId());
	}

	// ---- abbreviations

	public void testAbbreviationOf() {
		Edition ed = new Edition("Abbr Of", "AOF");
		assertTrue(ed.abbreviationOf("AOF"));
		assertFalse(ed.abbreviationOf("aof"));
		assertFalse(ed.abbreviationOf("XXX"));
		assertFalse(ed.abbreviationOf(null));
	}

	public void testAddAbbreviationReplacesAFakeOne() {
		Edition ed = new Edition("Fake Then Real", null);
		ed.addAbbreviation("FTR");
		assertEquals("FTR", ed.getMainAbbreviation());
		assertEquals(1, ed.getAbbreviations().length);
	}

	public void testAddAbbreviationAppendsAndIgnoresDuplicates() {
		Edition ed = new Edition("Add Abbr", "AAB");
		ed.addAbbreviation("AB2");
		ed.addAbbreviation("AB3");
		ed.addAbbreviation("AB2"); // duplicate
		ed.addAbbreviation("AAB"); // the main one again
		assertEquals("AAB", ed.getMainAbbreviation());
		assertEquals("AB2,AB3", ed.getExtraAbbreviations());
		assertEquals(3, ed.getAbbreviations().length);
		assertTrue(ed.abbreviationOf("AB3"));
	}

	public void testAddAbbreviationRefusesNullAndSpaces() {
		Edition ed = new Edition("Refuse Abbr", "RFA");
		ed.addAbbreviation(null);
		ed.addAbbreviation("A B");
		assertEquals(1, ed.getAbbreviations().length);
	}

	// ---- icons and file names

	public void testIconAbbreviationDropsTheSpecialSetPrefix() {
		// Scryfall prefixes the code of promo / token / art series... sets with one letter
		assertEquals("LEA", new Edition("Alpha Promos", "PLEA").getIconAbbreviation());
		assertEquals("DOM", new Edition("Dominaria Tokens", "TDOM").getIconAbbreviation());
		assertEquals("DOM", new Edition("Dominaria Art Series", "ADOM").getIconAbbreviation());
		assertEquals("UST", new Edition("Unstable Minigames", "MUST").getIconAbbreviation());
		assertEquals("ARC", new Edition("Archenemy Schemes", "OARC").getIconAbbreviation());
		assertEquals("PC2", new Edition("Planechase Planes", "OPC2").getIconAbbreviation());
		assertEquals("SLD", new Edition("Secret Lair Sheets", "SSLD").getIconAbbreviation());
	}

	public void testIconAbbreviationOfAnOrdinarySetIsTheMainOne() {
		assertEquals("DOM", new Edition("Dominaria", "DOM").getIconAbbreviation());
	}

	public void testIconAbbrIsStoredAsGiven() {
		Edition ed = new Edition("Icon Test", "ICT");
		assertNull(ed.getIconAbbr());
		ed.setIconAbbr("ict2");
		assertEquals("ict2", ed.getIconAbbr());
	}

	public void testBaseFileNameIsTheMainAbbreviationMadeFileSafe() {
		assertEquals("DOM", new Edition("Dominaria File", "DOM").getBaseFileName());
		String con = new Edition("Con Set", "CON").getBaseFileName();
		assertFalse("a Windows reserved name must be changed", "CON".equals(con));
		assertEquals(new Edition("Con Icon", "CON").getBaseFileName(), new Edition("Con Icon2", "CON").getIconBaseFileName());
	}

	// ---- set type

	public void testParseSetType() {
		String[][] cases = { { "Core", "Core" }, { "core", "Core" }, { "expansion", "Expansion" }, { "Expansion", "Expansion" },
				{ "reprint", "Reprint" }, { "starter", "Starter" }, { "online", "Online" }, { "modifiers", "Modifiers" },
				{ "un_set", "Un_set" }, { "token", "Token" }, { "promo", "Promo" }, { "Promo", "Promo" }, { "?", "?" }, { "", "?" },
				{ "masters", "Other" }, { "funny", "Other" } };
		for (String[] c : cases)
			assertEquals(c[0], c[1], Edition.parseSetType(c[0]));
		assertEquals("?", Edition.parseSetType(null));
	}

	public void testSetType() {
		Edition ed = new Edition("Type Test", "TYT");
		ed.setType("expansion");
		assertEquals("Expansion", ed.getType());
		ed.setType("something new");
		assertEquals("Other", ed.getType());
	}

	public void testDigitalFlag() {
		Edition ed = new Edition("Digital Test", "DGT");
		ed.setDigital(true);
		assertTrue(ed.isDigital());
		ed.setDigital(false);
		assertFalse(ed.isDigital());
	}

	// ---- block, aliases, formats

	public void testBlockDefaultsToTheName() {
		Edition ed = new Edition("Block Test", "BLT");
		assertEquals("Block Test", ed.getBlock());
		ed.setBlock("Ravnica");
		assertEquals("Ravnica", ed.getBlock());
	}

	public void testNameAliases() {
		Edition ed = new Edition("Alias Test", "ALS");
		ed.setNameAliases(new String[] { "A One", "A Two", "A Three" });
		assertEquals(3, ed.getAliases().length);
		assertEquals("A One,A Two,A Three", ed.getExtraAliases());
		// the array is copied
		String[] src = { "X" };
		ed.setNameAliases(src);
		src[0] = "changed";
		assertEquals("X", ed.getExtraAliases());
	}

	public void testAddNameAliasOnAnEmptyList() {
		Edition ed = new Edition("Alias One", "AL1");
		ed.addNameAlias("First");
		assertEquals("First", ed.getExtraAliases());
		ed.addNameAlias("First"); // duplicate
		assertEquals(1, ed.getAliases().length);
	}

	public void testAddNameAliasKeepsTheOnesAlreadyThere() {
		// a set renamed more than once: every former name stays an alias
		Edition ed = new Edition("Alias Many", "ALM");
		ed.addNameAlias("Old One");
		ed.addNameAlias("Old Two");
		ed.addNameAlias("Old Three");
		assertEquals("Old One,Old Two,Old Three", ed.getExtraAliases());
		ed.addNameAlias("Old Two"); // duplicate, whatever its place
		assertEquals(3, ed.getAliases().length);
	}

	public void testAddNameAliasAfterSetNameAliases() {
		Edition ed = new Edition("Alias Mixed", "ALX");
		ed.setNameAliases(new String[] { "From File" });
		ed.addNameAlias("Renamed Later");
		assertEquals("From File,Renamed Later", ed.getExtraAliases());
	}

	public void testNoFormatMeansLegacy() {
		Edition ed = new Edition("Format Test", "FMT");
		assertEquals("", ed.getFormatString());
		assertEquals(com.reflexit.magiccards.core.legality.Format.LEGACY, ed.getFormat());
		assertSame(LegalityMap.EMPTY, ed.getLegalityMap());
	}

	// ---- release date

	public void testReleaseDateFromTextAndDate() throws ParseException {
		Edition ed = new Edition("Release Test", "RLT");
		ed.setReleaseDate("January 2020"); // the format of editions.txt: month and year
		Calendar c = Calendar.getInstance();
		c.setTime(ed.getReleaseDate());
		assertEquals(2020, c.get(Calendar.YEAR));
		assertEquals(Calendar.JANUARY, c.get(Calendar.MONTH));
		assertEquals(1, c.get(Calendar.DAY_OF_MONTH));
		ed.setReleaseDate("?");
		assertNull(ed.getReleaseDate());
		ed.setReleaseDate("");
		assertNull(ed.getReleaseDate());
		Date d = new Date();
		ed.setReleaseDate(d);
		assertSame(d, ed.getReleaseDate());
	}

	public void testComingSoonBoundaries() {
		Date today = new Date();
		Date tomorrow = new Date(today.getTime() + 86400000L);
		Date yesterday = new Date(today.getTime() - 86400000L);
		assertTrue(Edition.isComingSoon(tomorrow, today));
		assertFalse(Edition.isComingSoon(yesterday, today));
		assertFalse("released today is not coming soon", Edition.isComingSoon(today, today));
		assertFalse("no date: not coming soon", Edition.isComingSoon(null, today));
	}

	// ---- equality

	public void testEqualityIsByName() {
		Edition a = new Edition("Same Name", "SN1");
		Edition b = new Edition("Same Name", "SN2");
		Edition c = new Edition("Other Name", "SN1");
		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
		assertFalse(a.equals(c));
		assertFalse(a.equals(null));
		assertFalse(a.equals("Same Name"));
		assertTrue(a.equals(a));
	}
}
