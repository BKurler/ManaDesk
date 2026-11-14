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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import com.reflexit.magiccards.core.exports.ImportUtils;

/**
 * The old two-letter set codes (extra abbreviations) and the former set names (name
 * aliases) as {@link Editions} reads, looks up, replaces (database update) and writes
 * them in editions.txt. Works on the shared {@link Editions} instance, so every set here
 * has a name and codes no real set uses. Pure logic; works as plain JUnit.
 */
public class EditionsAliasesTest extends TestCase {
	private final Editions eds = Editions.getInstance();
	private final List<File> tmp = new ArrayList<File>();
	private final List<Edition> added = new ArrayList<Edition>();

	@Override
	protected void tearDown() throws Exception {
		for (Edition ed : added)
			eds.remove(ed);
		for (File f : tmp)
			f.delete();
	}

	/** One editions.txt line: name|main|extra abbreviations|release|type|block|icon|name aliases|digital. */
	private Edition load(String line) throws IOException {
		String name = line.substring(0, line.indexOf('|'));
		eds.loadEditions(new ByteArrayInputStream((line + "\n").getBytes(StandardCharsets.UTF_8)));
		Edition ed = eds.getEditionByName(name);
		assertNotNull(line, ed);
		added.add(ed);
		return ed;
	}

	// ---- reading editions.txt

	public void testExtraAbbreviationsAreReadAndLookedUp() throws IOException {
		Edition ed = load("Alias Test Alpha|AZA|A1,A2|October 1993|Core|Alias Block|aza||");
		assertEquals("AZA", ed.getMainAbbreviation());
		assertEquals("A1,A2", ed.getExtraAbbreviations());
		// any of the codes finds the set, the old two-letter ones included
		assertSame(ed, eds.getEditionByAbbr("AZA"));
		assertSame(ed, eds.getEditionByAbbr("A1"));
		assertSame(ed, eds.getEditionByAbbr("A2"));
		assertEquals("Alias Test Alpha", eds.getNameByAbbr("A2"));
		assertNull(eds.getEditionByAbbr("A3"));
		assertNull(eds.getEditionByAbbr(null));
	}

	public void testNameAliasesAreReadAndLookedUp() throws IOException {
		Edition ed = load("Alias Test Beta|AZB||October 1993|Core|Alias Block|azb|Former Beta,Beta Old|");
		assertEquals("Former Beta,Beta Old", ed.getExtraAliases());
		assertSame(ed, eds.getEditionByName("Alias Test Beta"));
		assertSame("a former name finds the set", ed, eds.getEditionByName("Former Beta"));
		assertSame(ed, eds.getEditionByName("Beta Old"));
		assertNull(eds.getEditionByName("Never Called That"));
		assertSame("ignoring case", ed, eds.getEditionByNameIgnoreCase("former BETA"));
		assertSame(ed, eds.getEditionByNameIgnoreCase("alias test beta"));
		assertNull(eds.getEditionByNameIgnoreCase("Never Called That"));
		assertEquals("an alias is not a set name of its own", "Alias Test Beta",
				eds.getEditionByNameAlways("Former Beta").getName());
	}

	public void testAnAliasIsNotListedAsASet() throws IOException {
		load("Alias Test Gamma|AZC||October 1993|Core|Alias Block|azc|Former Gamma|");
		assertTrue(eds.getNames().contains("Alias Test Gamma"));
		assertFalse(eds.getNames().contains("Former Gamma"));
	}

	public void testOldFilesWithoutTheAliasColumnStillLoad() throws IOException {
		Edition ed = load("Alias Test Delta|AZD|D1|October 1993|Core|Alias Block");
		assertEquals("D1", ed.getExtraAbbreviations());
		assertEquals("", ed.getExtraAliases());
	}

	public void testSetWithoutAnyExtraCodeOrAlias() throws IOException {
		Edition ed = load("Alias Test Eps|AZE||October 1993|Core|Alias Block|aze||");
		assertEquals("", ed.getExtraAbbreviations());
		assertSame(ed, eds.getEditionByAbbr("AZE"));
	}

	// ---- the database update replaces a set with the one Scryfall describes

	public void testTheDatabaseUpdateReplacesTheSetAndBringsTheOldCodes() throws IOException {
		Edition before = load("Alias Test Zeta|AZZ||October 1993|Core|Alias Block|azz||");
		assertNull(eds.getEditionByAbbr("Z1"));
		// what the update builds from a Scryfall record: same name, main code, plus the old code
		Edition fresh = new Edition("Alias Test Zeta", "AZZ");
		fresh.addAbbreviation("Z1");
		eds.addEdition(fresh);
		added.add(fresh);
		assertNotSame(before, eds.getEditionByName("Alias Test Zeta"));
		assertSame(fresh, eds.getEditionByName("Alias Test Zeta"));
		assertSame(fresh, eds.getEditionByAbbr("Z1"));
		assertSame(fresh, eds.getEditionByAbbr("AZZ"));
		assertEquals("only one set of that name remains", 1, countNamed("Alias Test Zeta"));
	}

	private int countNamed(String name) {
		int n = 0;
		for (Edition e : eds.getAllEditions())
			if (e.getName().equals(name))
				n++;
		return n;
	}

	// ---- writing editions.txt

	private String savedLine(String name) throws IOException {
		File f = File.createTempFile("editions-test", ".txt");
		tmp.add(f);
		eds.save(f);
		for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8))
			if (line.startsWith(name + "|"))
				return line;
		fail("not saved: " + name);
		return null;
	}

	public void testSaveWritesExtraCodesAndAliases() throws Exception {
		load("Alias Test Eta|AZH|H1,H2|October 1993|Core|Alias Block|azh|Former Eta,Eta Old|");
		assertEquals("Alias Test Eta|AZH|H1,H2|October 1993|Core|Alias Block|azh|Former Eta,Eta Old|", savedLine("Alias Test Eta"));
	}

	public void testSaveAndReloadKeepsCodesAndAliases() throws Exception {
		load("Alias Test Theta|AZT|T1|October 1993|Core|Alias Block|azt|Former Theta|digital");
		String line = savedLine("Alias Test Theta");
		eds.remove(eds.getEditionByName("Alias Test Theta"));
		Edition again = load(line);
		assertEquals("T1", again.getExtraAbbreviations());
		assertEquals("Former Theta", again.getExtraAliases());
		assertTrue(again.isDigital());
		assertSame(again, eds.getEditionByAbbr("T1"));
	}

	public void testSavedFileListsEverySetOnceWithNineFields() throws Exception {
		load("Alias Test Iota|AZI|I1|October 1993|Core|Alias Block|azi||");
		File f = File.createTempFile("editions-test", ".txt");
		tmp.add(f);
		eds.save(f);
		for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8))
			assertEquals(line, 8, line.length() - line.replace("|", "").length());
	}

	// ---- the import accepts a former set name

	public void testImportAcceptsAFormerSetName() throws IOException {
		load("Alias Test Kappa|AZK||October 1993|Core|Alias Block|azk|Former Kappa|");
		java.util.List<IMagicCard> cards = new ArrayList<IMagicCard>();
		cards.add(cardOf("Former Kappa"));
		cards.add(cardOf("Alias Test Kappa"));
		cards.add(cardOf("alias test kappa"));
		cards.add(cardOf("No Such Set Anywhere"));
		Map<String, String> bad = ImportUtils.getSetCandidates(cards);
		assertFalse("a former name is accepted as it is", bad.containsKey("Former Kappa"));
		assertFalse("the current name is fine", bad.containsKey("Alias Test Kappa"));
		assertEquals("a different spelling is proposed the real name", "Alias Test Kappa", bad.get("alias test kappa"));
		assertTrue("an unknown set is reported, with no proposal", bad.containsKey("No Such Set Anywhere"));
		assertNull(bad.get("No Such Set Anywhere"));
	}

	private static IMagicCard cardOf(String set) {
		MagicCard c = new MagicCard();
		c.setName("Some Card");
		c.setSet(set);
		return c;
	}
}
