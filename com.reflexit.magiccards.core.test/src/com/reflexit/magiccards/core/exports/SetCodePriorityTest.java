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
package com.reflexit.magiccards.core.exports;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.reflexit.magiccards.core.model.Edition;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;

import junit.framework.TestCase;

/**
 * A file without a Scryfall id: a set code is used before a set name, matched ignoring the case, whatever the
 * order of the columns; a code ManaDesk does not know falls back to the name. Freeform text is best effort.
 * Pure logic (plain JUnit): the set lookups are replaced by a small fixed table, no real set data is touched.
 */
public class SetCodePriorityTest extends TestCase {
	/** The table: name, main code, alternate code. */
	private static final String[][] SETS = { { "Weatherlight", "WTH", "WL" }, { "Magic 2010", "M10", null },
			{ "Magic 2015", "M15", null }, { "Ice Age", "ICE", "IA" } };

	private static List<Edition> editions() {
		List<Edition> list = new ArrayList<>();
		for (String[] s : SETS) {
			Edition ed = new Edition(s[0], s[1]);
			if (s[2] != null)
				ed.addAbbreviation(s[2]);
			list.add(ed);
		}
		return list;
	}

	private static String nameByCode(String code) {
		Edition ed = ImportUtils.findEditionByCode(editions(), code);
		return ed == null ? null : ed.getName();
	}

	private static boolean isName(String name) {
		for (String[] s : SETS)
			if (s[0].equalsIgnoreCase(name))
				return true;
		return false;
	}

	private List<IMagicCard> parse(IImportDelegate importer, String text) throws Exception {
		ImportData data = new ImportData();
		data.setText(text);
		importer.init(data);
		importer.run(ICoreProgressMonitor.NONE);
		if (data.getError() != null)
			throw new Exception(data.getError());
		@SuppressWarnings({ "unchecked", "rawtypes" })
		List<IMagicCard> list = (List) data.getList();
		return list;
	}

	private List<IMagicCard> deckbox(String text) throws Exception {
		return parse(new DeckBoxImportDelegate() {
			@Override
			protected String setNameByCode(String code) {
				return nameByCode(code);
			}

			@Override
			protected boolean isSetName(String name) {
				return isName(name);
			}
		}, text);
	}

	private static String setOf(List<IMagicCard> cards, int i) {
		return ((MagicCardPhysical) cards.get(i)).getSet();
	}

	public void testFindEditionByCode() {
		List<Edition> eds = editions();
		assertEquals("Weatherlight", ImportUtils.findEditionByCode(eds, "WTH").getName()); // exact
		assertEquals("Weatherlight", ImportUtils.findEditionByCode(eds, "wth").getName()); // any case
		assertEquals("Weatherlight", ImportUtils.findEditionByCode(eds, "wl").getName()); // alternate code
		assertEquals("Magic 2010", ImportUtils.findEditionByCode(eds, " m10 ").getName());
		assertNull(ImportUtils.findEditionByCode(eds, "cg"));
		assertNull(ImportUtils.findEditionByCode(eds, ""));
		assertNull(ImportUtils.findEditionByCode(eds, null));
		assertNull(ImportUtils.findEditionByCode(null, "wth"));
	}

	/** The code wins over a name that is not a known set (Deckbox says "Magic 2015 Core Set"). */
	public void testCodeWinsOverName() throws Exception {
		List<IMagicCard> c = deckbox("Count,Name,Edition,Edition Code,Card Number\n1,Elvish Mystic,Magic 2015 Core Set,m15,204\n");
		assertEquals("Magic 2015", setOf(c, 0));
	}

	/** The same, with the code column before the name column. */
	public void testColumnOrderDoesNotMatter() throws Exception {
		List<IMagicCard> c = deckbox("Count,Name,Edition Code,Edition,Card Number\n1,Elvish Mystic,m15,Magic 2015 Core Set,204\n");
		assertEquals("Magic 2015", setOf(c, 0));
	}

	/** A code that is a different set than the name: the code still wins. */
	public void testCodeWinsEvenOverAKnownName() throws Exception {
		List<IMagicCard> c = deckbox("Count,Name,Edition,Edition Code\n1,Abeyance,Ice Age,wl\n"
				+ "1,Abeyance,Weatherlight,ia\n");
		assertEquals("Weatherlight", setOf(c, 0));
		assertEquals("Ice Age", setOf(c, 1));
	}

	/** Deckbox's codes cg / gu / re / ssp_1 are not known to ManaDesk: the name is used, in either column order. */
	public void testUnknownCodeFallsBackToTheName() throws Exception {
		List<IMagicCard> c = deckbox("Count,Name,Edition,Edition Code\n1,Aluren,Ice Age,zz\n");
		assertEquals("Ice Age", setOf(c, 0));
		c = deckbox("Count,Name,Edition Code,Edition\n1,Aluren,zz,Ice Age\n");
		assertEquals("Ice Age", setOf(c, 0));
	}

	/** Nothing known at all: the name is kept as written, for the preview to flag. */
	public void testNeitherKnown() throws Exception {
		List<IMagicCard> c = deckbox("Count,Name,Edition,Edition Code\n1,Aluren,Some New Set,zz\n");
		assertEquals("Some New Set", setOf(c, 0));
	}

	public void testCodeOnlyAndNameOnly() throws Exception {
		List<IMagicCard> c = deckbox("Count,Name,Edition,Edition Code\n1,Aluren,,WTH\n");
		assertEquals("Weatherlight", setOf(c, 0));
		c = deckbox("Count,Name,Edition\n1,Aluren,Weatherlight\n");
		assertEquals("Weatherlight", setOf(c, 0));
	}

	/** One delegate parses several rows: a code found for one row never leaks into the next. */
	public void testRowsAreIndependent() throws Exception {
		List<IMagicCard> c = deckbox("Count,Name,Edition,Edition Code\n1,A,Whatever,wl\n1,B,Ice Age,\n");
		assertEquals("Weatherlight", setOf(c, 0));
		assertEquals("Ice Age", setOf(c, 1));
	}

	/** With a Scryfall id the set columns are not read at all (the id is exact). */
	public void testScryfallIdIgnoresTheSet() throws Exception {
		List<IMagicCard> c = deckbox("Count,Name,Edition,Edition Code,Card Number,Scryfall ID\n"
				+ "1,Abeyance,Weatherlight,wl,117,125a355d-bfcf-4125-aa6c-35e7dea6f63e\n");
		assertTrue(setOf(c, 0) == null || setOf(c, 0).isEmpty());
		assertEquals("125a355d-bfcf-4125-aa6c-35e7dea6f63e", c.get(0).getCardId());
	}

	/** ManaBox: the code wins, and a code that is not known falls back to the set name (it did not before). */
	public void testManaBox() throws Exception {
		String header = ManaBoxCsvTest.HEADER + "\n";
		IImportDelegate importer = new ManaBoxCsvImportDelegate() {
			@Override
			protected String setNameByCode(String code) {
				return nameByCode(code);
			}

			@Override
			protected boolean isSetName(String name) {
				return isName(name);
			}
		};
		List<IMagicCard> c = parse(importer,
				header + "Fog,M15,Some Other Name,1,normal,common,1,1,a1959de7-e945-438c-a90a-158e21c4d5bf,,false,false,false,"
						+ "near_mint,en,false,USD,2026-10-10T19:29:49.972Z\n"
						+ "Fog,NEW,Ice Age,1,normal,common,1,1,b1959de7-e945-438c-a90a-158e21c4d5bf,,false,false,false,"
						+ "near_mint,en,false,USD,2026-10-10T19:29:49.972Z\n");
		assertEquals("Magic 2015", setOf(c, 0));
		assertEquals("Ice Age", setOf(c, 1));
	}

	/** Freeform text has no header: what is between the parentheses is a name or a code, best effort. */
	public void testFreeformBestEffort() throws Exception {
		IImportDelegate importer = new FreeformImportDelegate() {
			@Override
			protected String setNameByCode(String code) {
				return nameByCode(code);
			}

			@Override
			protected boolean isSetName(String name) {
				return isName(name);
			}
		};
		List<IMagicCard> c = parse(importer, String.join("\n", Arrays.asList("4x Lightning Bolt (M10)",
				"2 Counterspell (Ice Age)", "1 Abeyance (wl)", "1 Fog (Nowhere)", "1 Plain Name")) + "\n");
		assertEquals("Magic 2010", setOf(c, 0)); // a code
		assertEquals("Ice Age", setOf(c, 1)); // a name
		assertEquals("Weatherlight", setOf(c, 2)); // a code in lower case
		assertEquals("Nowhere", setOf(c, 3)); // unknown: kept as written
		String none = setOf(c, 4);
		assertTrue(none == null || none.isEmpty()); // no set at all
		assertEquals("m10".toUpperCase(Locale.ROOT), "M10");
	}
}
