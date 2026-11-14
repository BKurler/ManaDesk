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

import java.io.ByteArrayOutputStream;
import java.util.List;

import com.reflexit.magiccards.core.model.CardCondition;
import com.reflexit.magiccards.core.model.CardFinish;
import com.reflexit.magiccards.core.model.ForTradeRules;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;
import com.reflexit.magiccards.core.test.assist.MemCardHandler;

import junit.framework.TestCase;

/** Deckbox CSV import / export (pure logic: plain JUnit, no Eclipse runtime needed). */
public class DeckBoxCsvTest extends TestCase {
	/** The short (15 column) header of the older Deckbox exports: the importer reads the columns by name. */
	static final String HEADER = "Count,Tradelist Count,Name,Edition,Card Number,Condition,Language,Foil,Signed,"
			+ "Artist Proof,Altered Art,Misprint,Promo,Textless,My Price";

	private ImportData data;

	private List<IMagicCard> parse(String text) throws Exception {
		DeckBoxImportDelegate importer = new DeckBoxImportDelegate();
		data = new ImportData();
		data.setText(text);
		importer.init(data);
		importer.run(ICoreProgressMonitor.NONE);
		if (data.getError() != null)
			throw new Exception(data.getError());
		@SuppressWarnings({ "unchecked", "rawtypes" })
		List<IMagicCard> list = (List) data.getList();
		return list;
	}

	private String export(MemCardHandler store, DeckBoxExportDelegate exporter, boolean header, String id)
			throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		exporter.setReportType(ImportExportFactory.createReportType(id));
		exporter.init(out, header, store);
		exporter.run(ICoreProgressMonitor.NONE);
		return out.toString("UTF-8");
	}

	private static MemCardHandler store(MagicCardPhysical... cards) {
		MemCardHandler store = new MemCardHandler();
		for (MagicCardPhysical c : cards)
			store.getCardStore().add(c);
		store.update();
		return store;
	}

	private static MagicCardPhysical card(String name, String set, String num) {
		MagicCard base = new MagicCard();
		base.setName(name);
		base.setSet(set);
		base.setCollNumber(num);
		return new MagicCardPhysical(base, null);
	}

	public void testInventoryRows() throws Exception {
		String text = HEADER + "\n" //
				+ "3,0,Platinum Angel,Magic 2010,218,Near Mint,English,,,,,,,,0\n" //
				+ "\n" // blank lines are tolerated
				+ "\"1\",0,\"Aang, at the Crossroads // Aang, Destined Savior\",Avatar: The Last Airbender,203,"
				+ "Good (Lightly Played),English,foil,,,,,,,$0.48\n" //
				+ "2,0,Fog,Fourth Edition,244,Played,Russian,,signed,proof,altered,misprint,promo,textless,1.5\n";
		List<IMagicCard> c = parse(text);
		assertEquals(3, c.size());
		MagicCardPhysical angel = (MagicCardPhysical) c.get(0);
		assertEquals("Platinum Angel", angel.getName());
		assertEquals("Magic 2010", angel.getSet());
		assertEquals("218", angel.getCollectorId());
		assertEquals(3, angel.getCount());
		assertEquals(CardCondition.NEAR_MINT, angel.getCondition());
		assertEquals(CardFinish.NONFOIL, angel.getFinish());
		assertNull(data.getDeclaredLanguage(angel));
		MagicCardPhysical aang = (MagicCardPhysical) c.get(1);
		assertEquals("Aang, at the Crossroads // Aang, Destined Savior", aang.getName());
		assertEquals(CardCondition.LIGHTLY_PLAYED, aang.getCondition());
		assertEquals(CardFinish.FOIL, aang.getFinish());
		assertEquals(0.48f, aang.getPrice(), 0.001);
		MagicCardPhysical fog = (MagicCardPhysical) c.get(2);
		assertEquals(CardCondition.MODERATELY_PLAYED, fog.getCondition());
		assertEquals("Russian", data.getDeclaredLanguage(fog)); // reported in the preview
		for (String tag : new String[] { "signed", "proof", "altered", "misprint" })
			assertTrue(tag, fog.isSpecialTag(tag));
		// Promo and Textless are deprecated on Deckbox: not read, even when the file has them
		assertFalse(fog.isSpecialTag("promo"));
		assertFalse(fog.isSpecialTag("textless"));
		assertFalse(angel.isSpecialTag("signed"));
	}

	/** The header and rows of a real Deckbox inventory export (20 columns, with the Scryfall ID). */
	static final String REAL_HEADER = "Count,Tradelist Count,Name,Edition,Edition Code,Card Number,Condition,Language,"
			+ "Foil,Signed,Artist Proof,Altered Art,Misprint,Promo,Textless,Printing Id,Printing Note,Tags,My Price,"
			+ "Scryfall ID";

	public void testRealExport() throws Exception {
		String text = REAL_HEADER + "\n"
				+ "1,1,Abeyance,Weatherlight,wl,117,,,,,,,,,,12311,,\"\",$0.00,125a355d-bfcf-4125-aa6c-35e7dea6f63e\n"
				+ "1,1,Ajani Goldmane,Magic 2010,m10,1,,,foil,,,,,,,15894,,\"\",$0.00,46c50891-af21-4427-a495-0e66aef54809\n"
				+ "1,1,Ashes to Ashes,Fourth Edition,4e,3,,French,,,,,,,,10870,,\"\",$0.00,"
				+ "28f40650-9dd5-473d-a660-448672d475a5\n"
				+ "7,7,Forest,Amonkhet,akh,254,,,,,,,,,,0,Full Art,\"\",$0.00,2c2fc9f7-21af-4416-abf5-6fcb4f543680\n"
				+ "1,1,\"Athreos, Shroud-Veiled\",,,,,,,,,,,,,0,,\"\",$0.00,871c19fc-7d99-4022-9e13-d679a9e3547e\n"
				+ "1,1,\"Cosima, God of the Voyage // The Omenkeel\",Kaldheim,khm,303,,,,,,,,,,0,Showcase,\"\",$0.00,"
				+ "9b660733-6ed4-49a6-ae4c-e9e4f9c851cf\n"
				+ "\n"; // the file ends with an empty line
		List<IMagicCard> c = parse(text);
		assertEquals(6, c.size());
		MagicCardPhysical abeyance = (MagicCardPhysical) c.get(0);
		assertEquals("Abeyance", abeyance.getName());
		assertEquals("125a355d-bfcf-4125-aa6c-35e7dea6f63e", abeyance.getCardId());
		// Deckbox's number (117) and Edition Code (wl) are not Scryfall's: they are not read when the id is there
		assertTrue(abeyance.getCollectorId() == null || abeyance.getCollectorId().isEmpty());
		assertTrue(abeyance.getSet() == null || abeyance.getSet().isEmpty());
		assertEquals(1, abeyance.getCount());
		assertEquals(0f, abeyance.getPrice(), 0.001); // "$0.00" is no price
		assertEquals(CardFinish.FOIL, ((MagicCardPhysical) c.get(1)).getFinish());
		MagicCardPhysical ashes = (MagicCardPhysical) c.get(2);
		assertEquals("28f40650-9dd5-473d-a660-448672d475a5", ashes.getCardId());
		assertEquals("French", data.getDeclaredLanguage(ashes)); // the id is the English printing
		assertEquals(7, ((MagicCardPhysical) c.get(3)).getCount());
		assertEquals("871c19fc-7d99-4022-9e13-d679a9e3547e", c.get(4).getCardId()); // no Edition: the id is enough
		assertEquals("Athreos, Shroud-Veiled", c.get(4).getName());
		assertEquals("Cosima, God of the Voyage // The Omenkeel", c.get(5).getName());
	}

	/** Without a Scryfall ID column the card is found by its Name, Edition and Card Number. */
	public void testWithoutScryfallIdKeepsEditionAndNumber() throws Exception {
		MagicCardPhysical c = (MagicCardPhysical) parse(
				HEADER + "\n4,0,Reya Dawnbringer,Duel Decks: Divine vs. Demonic,13,Near Mint,English,,,,,,,,0\n").get(0);
		assertEquals("Duel Decks: Divine vs. Demonic", c.getSet());
		assertEquals("13", c.getCollectorId());
	}

	public void testPriceCells() {
		assertEquals(0f, DeckBoxImportDelegate.price("$0.00"), 0.001);
		assertEquals(2.5f, DeckBoxImportDelegate.price("$2.50"), 0.001);
		assertEquals(1234.5f, DeckBoxImportDelegate.price("$1,234.50"), 0.001);
		assertEquals(1.5f, DeckBoxImportDelegate.price("1.5"), 0.001);
		assertEquals(0f, DeckBoxImportDelegate.price(""), 0.001);
		assertEquals(0f, DeckBoxImportDelegate.price(null), 0.001);
	}

	private static ReportType type(String label, IImportDelegate delegate) {
		ReportType t = ImportExportFactory.createReportType("detect-test " + label);
		t.setExtension("csv");
		t.setImportDelegate(delegate);
		return t;
	}

	/**
	 * A Deckbox export has a "Scryfall ID" column, so the ManaBox importer parses it too (same cards, no
	 * errors): on a tie the first registered wins, so Deckbox is registered before ManaBox in plugin.xml - and
	 * a ManaBox file is rejected by the (strict) Deckbox importer. This is the plugin.xml order.
	 */
	public void testAutoDetectDeckBoxBeforeManaBox() {
		List<ReportType> registered = java.util.Arrays.asList(type("Deckbox", new DeckBoxImportDelegate()),
				type("ManaBox", new ManaBoxCsvImportDelegate()));
		String deckbox = REAL_HEADER + "\n"
				+ "2,2,Abeyance,Weatherlight,wl,117,,,foil,,,,,,,12311,,\"\",$0.00,125a355d-bfcf-4125-aa6c-35e7dea6f63e\n";
		assertEquals("detect-test Deckbox", ReportType.autoDetectType(deckbox, registered).getLabel());
		String manabox = ManaBoxCsvTest.HEADER + "\nFog,4ED,Fourth Edition,244,foil,common,2,39943,"
				+ "a1959de7-e945-438c-a90a-158e21c4d5bf,0.28,false,false,false,near_mint,en,false,USD,"
				+ "2026-10-10T19:29:49.972Z\n";
		assertEquals("detect-test ManaBox", ReportType.autoDetectType(manabox, registered).getLabel());
		// a wishlist has no Scryfall ID: ManaBox rejects it
		assertEquals("detect-test Deckbox", ReportType.autoDetectType(
				"Count,Name,Foil,Textless,Promo,Signed,Edition,Condition,Language,Card Number\n"
						+ "3,Platinum Angel,,,,,Magic 2010,Near Mint,English,218\n",
				registered).getLabel());
	}

	/** The columns are matched by name: wishlist (no Tradelist Count), other order, extra columns. */
	public void testWishlistOtherOrderAndExtraColumns() throws Exception {
		String text = "Count,Name,Foil,Textless,Promo,Signed,Edition,Condition,Language,Card Number,Rarity\n"
				+ "4,Reya Dawnbringer,,,,,Duel Decks: Divine vs. Demonic,Near Mint,English,13,Mythic\n";
		MagicCardPhysical reya = (MagicCardPhysical) parse(text).get(0);
		assertEquals("Reya Dawnbringer", reya.getName());
		assertEquals("Duel Decks: Divine vs. Demonic", reya.getSet());
		assertEquals("13", reya.getCollectorId());
		assertEquals(4, reya.getCount());
		assertEquals(0, reya.getForTrade());
	}

	public void testTradelistCount() throws Exception {
		String text = HEADER + "\n4,1,Reya Dawnbringer,Duel Decks: Divine vs. Demonic,13,Near Mint,English,,,,,,,,0\n"
				+ "2,2,Fog,Fourth Edition,244,Near Mint,English,,,,,,,,0\n";
		List<IMagicCard> c = parse(text);
		// 4 with 1 for trade: 3 not for trade + 1 for trade; 2 with 2: all for trade
		int total = 0, trade = 0;
		for (IMagicCard m : c) {
			MagicCardPhysical p = (MagicCardPhysical) m;
			total += p.getCount();
			if (p.getForTrade() > 0)
				trade += p.getCount();
		}
		assertEquals(6, total);
		assertEquals(3, trade);
	}

	/**
	 * Tradelist Count only decides the "fortrade" tag, whole pile: 0 = no tag, a part of the pile = a tagged pile and
	 * a not tagged one, Count or more = the whole pile tagged. It never raises Count.
	 */
	public void testTradelistCountNeverRaisesCount() throws Exception {
		List<IMagicCard> c = parse(HEADER + "\n2,5,Fog,Fourth Edition,244,Near Mint,English,,,,,,,,0\n"
				+ "3,0,Reya Dawnbringer,Duel Decks: Divine vs. Demonic,13,Near Mint,English,,,,,,,,0\n"
				+ "3,1,Platinum Angel,Magic 2010,218,Near Mint,English,,,,,,,,0\n");
		int fog = 0, reya = 0, angelTrade = 0, angelOther = 0;
		for (IMagicCard m : c) {
			MagicCardPhysical p = (MagicCardPhysical) m;
			assertEquals(p.getName(), ForTradeRules.hasTag(p.getSpecial()), p.getForTrade() > 0);
			if (p.getName().equals("Fog")) {
				fog += p.getCount();
				assertEquals(2, p.getForTrade());
			} else if (p.getName().equals("Reya Dawnbringer")) {
				reya += p.getCount();
				assertEquals(0, p.getForTrade());
			} else if (p.getForTrade() > 0) {
				angelTrade += p.getCount();
			} else {
				angelOther += p.getCount();
			}
		}
		assertEquals("Count 2 stays 2 although Tradelist Count says 5", 2, fog);
		assertEquals(3, reya);
		assertEquals(1, angelTrade);
		assertEquals(2, angelOther);
	}

	/**
	 * Into a For Trade collection the Tradelist Count is ignored: whatever its value (0, part of the pile, more than
	 * Count), the pile is not split, keeps its Count and always has the fortrade tag. A proxy is left alone.
	 */
	public void testForTradeDestinationIgnoresTradelistCount() throws Exception {
		data = new ImportData();
		data.setForTradeDestination(true);
		DeckBoxImportDelegate importer = new DeckBoxImportDelegate();
		data.setText(HEADER + "\n3,0,Fog,Fourth Edition,244,Near Mint,English,,,,,,,,0\n"
				+ "4,1,Reya Dawnbringer,Duel Decks: Divine vs. Demonic,13,Near Mint,English,,,,,,,,0\n"
				+ "2,5,Platinum Angel,Magic 2010,218,Near Mint,English,,,,,,,,0\n"
				+ "2,2,Ajani Goldmane,Magic 2010,1,Proxy,English,,,,,,,,0\n");
		importer.init(data);
		importer.run(ICoreProgressMonitor.NONE);
		assertNull(data.getError());
		List<?> list = data.getList();
		assertEquals("no pile is split", 4, list.size());
		int[] counts = { 3, 4, 2, 2 };
		for (int i = 0; i < 4; i++) {
			MagicCardPhysical p = (MagicCardPhysical) list.get(i);
			assertEquals(p.getName(), counts[i], p.getCount());
			if (i < 3) {
				assertTrue(p.getName() + ": always tagged", ForTradeRules.hasTag(p.getSpecial()));
				assertEquals(p.getName(), counts[i], p.getForTrade());
			} else {
				assertTrue("a proxy", p.isProxy());
				assertFalse("a proxy is left alone", ForTradeRules.hasTag(p.getSpecial()));
				assertEquals(0, p.getForTrade());
			}
		}
	}

	public void testNotDeckBoxIsRejected() {
		for (String bad : new String[] { "Name,Set code,Quantity\nFog,4ED,1\n", "Count,Name\n1,Fog\n" }) {
			try {
				parse(bad);
				fail("a non-Deckbox CSV was accepted: " + bad);
			} catch (Exception expected) {
				assertTrue(expected.getMessage(), expected.getMessage().contains("Deckbox"));
			}
		}
	}

	public void testConditionGrades() {
		assertEquals(CardCondition.NEAR_MINT, DeckBoxImportDelegate.condition("Mint"));
		assertEquals(CardCondition.NEAR_MINT, DeckBoxImportDelegate.condition("Near Mint"));
		assertEquals(CardCondition.LIGHTLY_PLAYED, DeckBoxImportDelegate.condition("Good (Lightly Played)"));
		assertEquals(CardCondition.MODERATELY_PLAYED, DeckBoxImportDelegate.condition("Played"));
		assertEquals(CardCondition.HEAVILY_PLAYED, DeckBoxImportDelegate.condition("Heavily Played"));
		assertEquals(CardCondition.DAMAGED, DeckBoxImportDelegate.condition("Poor"));
		assertEquals(CardCondition.DAMAGED, DeckBoxImportDelegate.condition("DMG")); // ManaDesk's own
		assertNull(DeckBoxImportDelegate.condition(""));
		assertNull(DeckBoxImportDelegate.condition(null));
		// every ManaDesk grade exports to a Deckbox grade that reads back the same
		for (CardCondition g : CardCondition.values())
			assertEquals(g, DeckBoxImportDelegate.condition(DeckBoxExportDelegate.condition(g)));
		assertEquals("Near Mint", DeckBoxExportDelegate.condition(null));
	}

	/** One import row with this Condition cell, parsed by the real importer. */
	private MagicCardPhysical rowWithCondition(String condition) throws Exception {
		List<IMagicCard> c = parse(HEADER + "\n2,0,Fog,Fourth Edition,244," + condition + ",English,,,,,,,,0\n");
		assertEquals(1, c.size());
		return (MagicCardPhysical) c.get(0);
	}

	/**
	 * Every value the Condition column can hold (the 7 conditions of Deckbox, its exact labels, then the ways a
	 * file can differ: case, spaces, ManaDesk's own names and abbreviations), through the whole importer.
	 * Expected: the grade, or {@code null} for "not graded", and whether the copy is a Proxy.
	 */
	public void testEveryConditionCell() throws Exception {
		Object[][] table = {
				// the seven conditions of Deckbox, as Deckbox writes them
				{ "Mint", CardCondition.NEAR_MINT, false }, //
				{ "Near Mint", CardCondition.NEAR_MINT, false }, //
				{ "Good (Lightly Played)", CardCondition.LIGHTLY_PLAYED, false }, //
				{ "Played", CardCondition.MODERATELY_PLAYED, false }, //
				{ "Heavily Played", CardCondition.HEAVILY_PLAYED, false }, //
				{ "Poor", CardCondition.DAMAGED, false }, //
				{ "Proxy", null, true }, //
				// the same, written differently
				{ "near mint", CardCondition.NEAR_MINT, false }, //
				{ "NEAR MINT", CardCondition.NEAR_MINT, false }, //
				{ "  Near Mint  ", CardCondition.NEAR_MINT, false }, //
				{ "good (lightly played)", CardCondition.LIGHTLY_PLAYED, false }, //
				{ "Good", CardCondition.LIGHTLY_PLAYED, false }, //
				{ "played", CardCondition.MODERATELY_PLAYED, false }, //
				{ "heavily played", CardCondition.HEAVILY_PLAYED, false }, //
				{ "poor", CardCondition.DAMAGED, false }, //
				{ "proxy", null, true }, //
				{ "PROXY", null, true }, //
				{ " Proxy ", null, true }, //
				// ManaDesk's own names and abbreviations
				{ "NM", CardCondition.NEAR_MINT, false }, //
				{ "LP", CardCondition.LIGHTLY_PLAYED, false }, //
				{ "MP", CardCondition.MODERATELY_PLAYED, false }, //
				{ "HP", CardCondition.HEAVILY_PLAYED, false }, //
				{ "DMG", CardCondition.DAMAGED, false }, //
				{ "Lightly Played", CardCondition.LIGHTLY_PLAYED, false }, //
				{ "Moderately Played", CardCondition.MODERATELY_PLAYED, false }, //
				{ "Damaged", CardCondition.DAMAGED, false }, //
				// nothing, or something no grade exists for: not graded, never a proxy
				{ "", null, false }, //
				{ "   ", null, false }, //
				{ "Something Else", null, false }, //
				{ "Proxy Card", null, false }, // only the word Proxy is a proxy
		};
		for (Object[] row : table) {
			MagicCardPhysical card = rowWithCondition((String) row[0]);
			String what = "Condition '" + row[0] + "'";
			assertEquals(what, row[1], card.getCondition());
			assertEquals(what + " proxy", ((Boolean) row[2]).booleanValue(), card.isProxy());
		}
	}

	/** A Proxy is not graded, is not offered for trade, and keeps what else the row says. */
	public void testProxyRow() throws Exception {
		List<IMagicCard> c = parse(HEADER + "\n3,3,Platinum Angel,Magic 2010,218,Proxy,English,foil,signed,,,,,,$1.50\n");
		assertEquals(1, c.size());
		MagicCardPhysical card = (MagicCardPhysical) c.get(0);
		assertTrue(card.isProxy());
		assertNull(card.getCondition());
		assertEquals(3, card.getCount());
		assertEquals("a proxy is never for trade", 0, card.getForTrade());
		assertEquals(CardFinish.FOIL, card.getFinish());
		assertTrue(card.isSpecialTag("signed"));
		assertEquals(1.5f, card.getPrice(), 0.001);
	}

	/** Proxies and genuine copies of the same card, in the same file, stay apart. */
	public void testProxyAndGenuineRowsStayApart() throws Exception {
		List<IMagicCard> c = parse(HEADER + "\n1,0,Fog,Fourth Edition,244,Near Mint,English,,,,,,,,0\n"
				+ "1,0,Fog,Fourth Edition,244,Proxy,English,,,,,,,,0\n"
				+ "1,0,Fog,Fourth Edition,244,Played,English,,,,,,,,0\n");
		assertEquals(3, c.size());
		assertFalse(((MagicCardPhysical) c.get(0)).isProxy());
		assertEquals(CardCondition.NEAR_MINT, ((MagicCardPhysical) c.get(0)).getCondition());
		assertTrue(((MagicCardPhysical) c.get(1)).isProxy());
		assertNull(((MagicCardPhysical) c.get(1)).getCondition());
		assertFalse(((MagicCardPhysical) c.get(2)).isProxy());
		assertEquals(CardCondition.MODERATELY_PLAYED, ((MagicCardPhysical) c.get(2)).getCondition());
	}

	/** The Condition column is not required, and can be anywhere in the file (the columns are read by name). */
	public void testConditionColumnAbsentOrElsewhere() throws Exception {
		MagicCardPhysical none = (MagicCardPhysical) parse("Count,Name,Edition\n1,Fog,Fourth Edition\n").get(0);
		assertNull(none.getCondition());
		assertFalse(none.isProxy());
		MagicCardPhysical first = (MagicCardPhysical) parse("Condition,Count,Name,Edition\nProxy,1,Fog,Fourth Edition\n")
				.get(0);
		assertTrue(first.isProxy());
		MagicCardPhysical graded = (MagicCardPhysical) parse("Count,Name,Edition,Condition\n1,Fog,Fourth Edition,Poor\n")
				.get(0);
		assertEquals(CardCondition.DAMAGED, graded.getCondition());
		// a short row, with no value for the Condition column at all
		MagicCardPhysical shortRow = (MagicCardPhysical) parse("Count,Name,Edition,Condition\n1,Fog,Fourth Edition\n")
				.get(0);
		assertNull(shortRow.getCondition());
		assertFalse(shortRow.isProxy());
	}

	/** Quoted values, as a spreadsheet saves a file. */
	public void testQuotedConditionCells() throws Exception {
		MagicCardPhysical lp = (MagicCardPhysical) parse(
				"Count,Name,Edition,Condition\n1,\"Fog\",\"Fourth Edition\",\"Good (Lightly Played)\"\n").get(0);
		assertEquals(CardCondition.LIGHTLY_PLAYED, lp.getCondition());
		MagicCardPhysical proxy = (MagicCardPhysical) parse(
				"Count,Name,Edition,Condition\n1,\"Fog\",\"Fourth Edition\",\"Proxy\"\n").get(0);
		assertTrue(proxy.isProxy());
	}

	/** One Proxy row and one genuine row imported into a collection (not virtual) or a deck (virtual). */
	private List<IMagicCard> parseInto(boolean virtual) throws Exception {
		DeckBoxImportDelegate importer = new DeckBoxImportDelegate();
		ImportData d = new ImportData();
		d.setVirtual(virtual);
		d.setOwnershipFixed(true); // the destination decides the ownership, as in the wizard
		d.setText(HEADER + "\n1,0,Fog,Fourth Edition,244,Proxy,English,,,,,,,,0\n"
				+ "1,0,Fog,Fourth Edition,244,Near Mint,English,,,,,,,,0\n");
		importer.init(d);
		importer.run(ICoreProgressMonitor.NONE);
		assertNull(d.getError());
		@SuppressWarnings({ "unchecked", "rawtypes" })
		List<IMagicCard> list = (List) d.getList();
		assertEquals(2, list.size());
		return list;
	}

	/** In a collection the Proxy copy is owned, and stays a proxy; the genuine copy is owned and not a proxy. */
	public void testProxyIntoACollection() throws Exception {
		List<IMagicCard> c = parseInto(false);
		MagicCardPhysical proxy = (MagicCardPhysical) c.get(0);
		MagicCardPhysical genuine = (MagicCardPhysical) c.get(1);
		assertTrue(proxy.isProxy());
		assertTrue(proxy.isOwn());
		assertFalse(genuine.isProxy());
		assertTrue(genuine.isOwn());
	}

	/** In a deck (virtual) both are virtual: the Proxy flag stays on the copy. */
	public void testProxyIntoADeck() throws Exception {
		List<IMagicCard> c = parseInto(true);
		MagicCardPhysical proxy = (MagicCardPhysical) c.get(0);
		MagicCardPhysical genuine = (MagicCardPhysical) c.get(1);
		assertTrue(proxy.isProxy());
		assertFalse(proxy.isOwn());
		assertFalse(genuine.isProxy());
		assertFalse(genuine.isOwn());
	}

	/** The rows of a real Deckbox export that carry a Condition: all seven conditions of Deckbox. */
	public void testRealExportConditions() throws Exception {
		String text = REAL_HEADER + "\n"
				+ "1,1,Abeyance,Weatherlight,wl,117,Mint,,,,,,,,,12311,,,$0.00,125a355d-bfcf-4125-aa6c-35e7dea6f63e\n"
				+ "1,1,Aether Sting,Urza's Destiny,cg,76,Near Mint,,,,,,,,,1562,,,$0.00,66a09917-50ce-4b51-a5cf-e28e88a45762\n"
				+ "1,1,Ajani Goldmane,Magic 2010,m10,1,Good (Lightly Played),,foil,,,,,,,15894,,,$0.00,"
				+ "46c50891-af21-4427-a495-0e66aef54809\n"
				+ "1,1,Akroan Horse,Theros,ths,210,Played,,foil,,,,,,,21943,,,$0.00,ec95a60f-5164-47f9-9430-11b114c87815\n"
				+ "1,1,Al-abara's Carpet,Legends,le,213,Heavily Played,,,,,,,,,3413,,,$0.00,"
				+ "5d5aae6e-fe20-4363-9589-5a54bcbbb77e\n"
				+ "2,2,Altar of Bone,Ice Age,ia,359,Poor,,,,,,,,,9503,,,$0.00,75d5b014-8675-4d91-a539-ac5c31d44b35\n"
				+ "2,2,Animate Dead,Revised Edition,3e,1,Proxy,,,,,,,,,4847,,,$0.00,eed73f84-ad08-44f8-a4fe-cd324ec1da92\n";
		List<IMagicCard> c = parse(text);
		assertEquals(7, c.size());
		CardCondition[] expected = { CardCondition.NEAR_MINT, CardCondition.NEAR_MINT, CardCondition.LIGHTLY_PLAYED,
				CardCondition.MODERATELY_PLAYED, CardCondition.HEAVILY_PLAYED, CardCondition.DAMAGED, null };
		for (int i = 0; i < 7; i++) {
			MagicCardPhysical card = (MagicCardPhysical) c.get(i);
			assertEquals(card.getName(), expected[i], card.getCondition());
			assertEquals(card.getName(), i == 6, card.isProxy()); // only the last one is a proxy
			assertNull("every real value is known: " + card.getName(), data.getUnknownCondition(card));
		}
		assertEquals(2, ((MagicCardPhysical) c.get(6)).getCount());
		assertEquals(0, ((MagicCardPhysical) c.get(6)).getForTrade()); // Tradelist Count 2, but a proxy
		assertTrue(data.getUnknownConditions().isEmpty());
	}

	/**
	 * A text in the Condition column that is no known grade (and not Proxy): the copy is imported ungraded, and
	 * the text is remembered for the preview (yellow empty cell + a warning below the table). It never goes to
	 * the comment.
	 */
	public void testUnknownConditionIsRememberedNotStored() throws Exception {
		List<IMagicCard> c = parse(HEADER + "\n1,0,Fog,Fourth Edition,244,Pristine,English,,,,,,,,0\n"
				+ "1,0,Fog,Fourth Edition,244,Near Mint,English,,,,,,,,0\n"
				+ "1,0,Fog,Fourth Edition,244,,English,,,,,,,,0\n"
				+ "1,0,Fog,Fourth Edition,244,Proxy,English,,,,,,,,0\n"
				+ "1,0,Fog,Fourth Edition,244,Proxy Card,English,,,,,,,,0\n");
		assertEquals(5, c.size());
		MagicCardPhysical unknown = (MagicCardPhysical) c.get(0);
		assertNull(unknown.getCondition());
		assertFalse(unknown.isProxy());
		assertEquals("Pristine", data.getUnknownCondition(unknown));
		assertNull("the comment is not touched", unknown.getComment());
		assertNull(data.getUnknownCondition(c.get(1))); // a known grade
		assertNull(data.getUnknownCondition(c.get(2))); // nothing in the cell
		assertNull(data.getUnknownCondition(c.get(3))); // Proxy
		assertEquals("Proxy Card", data.getUnknownCondition(c.get(4)));
		assertEquals(2, data.getUnknownConditions().size());
	}

	/** Deckbox's Tags are free text: read as the comment, as written (not as Special tags, which have a syntax). */
	public void testTagsBecomeTheComment() throws Exception {
		List<IMagicCard> c = parse(REAL_HEADER + "\n"
				+ "1,0,Fog,Fourth Edition,,244,,,,,,,,,,0,,\"Box 3, shelf A\",$0.00,a1959de7-e945-438c-a90a-158e21c4d5bf\n"
				+ "1,0,Fog,Fourth Edition,,244,,,,,,,,,,0,,-new=1+x,$0.00,b1959de7-e945-438c-a90a-158e21c4d5bf\n"
				+ "1,0,Fog,Fourth Edition,,244,,,,,,,,,,0,,,$0.00,c1959de7-e945-438c-a90a-158e21c4d5bf\n"
				+ "1,0,Fog,Fourth Edition,,244,,,,,,,,,,0,,\"\",$0.00,d1959de7-e945-438c-a90a-158e21c4d5bf\n");
		assertEquals(4, c.size());
		MagicCardPhysical a = (MagicCardPhysical) c.get(0);
		assertEquals("Box 3, shelf A", a.getComment());
		assertTrue("a comma of the text is not a separator of Special tags", a.getSpecial() == null || a.getSpecial().isEmpty());
		MagicCardPhysical b = (MagicCardPhysical) c.get(1);
		assertEquals("-new=1+x", b.getComment());
		assertTrue("text that looks like a Special tag is not one", b.getSpecial() == null || b.getSpecial().isEmpty());
		assertNull(((MagicCardPhysical) c.get(2)).getComment()); // no tags
		assertNull(((MagicCardPhysical) c.get(3)).getComment()); // an empty quoted cell
	}

	public void testProxyWord() {
		assertTrue(DeckBoxImportDelegate.isProxy("Proxy"));
		assertTrue(DeckBoxImportDelegate.isProxy(" pRoXy "));
		assertFalse(DeckBoxImportDelegate.isProxy("Proxies"));
		assertFalse(DeckBoxImportDelegate.isProxy(""));
		assertFalse(DeckBoxImportDelegate.isProxy(null));
	}

	public void testFlagCells() {
		assertTrue(DeckBoxImportDelegate.isSet("signed"));
		assertTrue(DeckBoxImportDelegate.isSet("1"));
		assertFalse(DeckBoxImportDelegate.isSet(""));
		assertFalse(DeckBoxImportDelegate.isSet(" "));
		assertFalse(DeckBoxImportDelegate.isSet("false"));
		assertFalse(DeckBoxImportDelegate.isSet("0"));
		assertFalse(DeckBoxImportDelegate.isSet(null));
	}

	public void testExportRoundTrip() throws Exception {
		MagicCardPhysical fog = card("Fog", "Fourth Edition", "244");
		fog.setCount(2);
		fog.setFinish(CardFinish.FOIL);
		fog.setCondition(CardCondition.HEAVILY_PLAYED);
		fog.setSpecialTag("altered");
		fog.setSpecialTag("proof");
		fog.setPrice(1.5f);
		MagicCardPhysical plain = card("Platinum Angel", "Magic 2010", "218");
		String[] lines = export(store(fog, plain), new DeckBoxExportDelegate(), true, "testdeckbox").split("\r?\n");
		assertEquals(DeckBoxExportDelegate.HEADER, lines[0]);
		// Count,Tradelist Count,Name,Edition,Edition Code,Card Number,Condition,Language,Foil,Signed,Artist Proof,
		// Altered Art,Misprint,Tags,My Price,Scryfall ID
		String[] c1 = cells(lines[1]);
		assertEquals(16, c1.length);
		assertEquals("2", c1[0]);
		assertEquals("0", c1[1]);
		assertEquals("Fog", c1[2]);
		assertEquals("Fourth Edition", c1[3]);
		assertEquals("244", c1[5]);
		assertEquals("Heavily Played", c1[6]);
		assertEquals("English", c1[7]);
		assertEquals("foil", c1[8]);
		assertEquals("", c1[9]);
		assertEquals("proof", c1[10]);
		assertEquals("altered", c1[11]);
		assertEquals("$1.50", c1[14]);
		String[] c2 = cells(lines[2]);
		assertEquals("Platinum Angel", c2[2]);
		assertEquals("Near Mint", c2[6]);
		assertEquals("$0.00", c2[14]);
		// ...and it reads back
		MagicCardPhysical back = (MagicCardPhysical) parse(lines[0] + "\n" + lines[1] + "\n").get(0);
		assertEquals("Fog", back.getName());
		assertEquals("Fourth Edition", back.getSet());
		assertEquals("244", back.getCollectorId());
		assertEquals(2, back.getCount());
		assertEquals(CardFinish.FOIL, back.getFinish());
		assertEquals(CardCondition.HEAVILY_PLAYED, back.getCondition());
		assertTrue(back.isSpecialTag("altered"));
		assertTrue(back.isSpecialTag("proof"));
		assertFalse(back.isSpecialTag("signed"));
		assertEquals(1.5f, back.getPrice(), 0.001);
	}

	public void testExportForTradeAndFoil() throws Exception {
		MagicCardPhysical fog = card("Fog", "Fourth Edition", "244");
		fog.setCount(3);
		fog.setSpecialTag("fortrade");
		fog.setFinish(CardFinish.FOIL);
		String[] lines = export(store(fog), new DeckBoxExportDelegate(), false, "testdeckbox2").split("\r?\n");
		assertEquals(1, lines.length); // no header
		String[] c = cells(lines[0]);
		assertEquals(16, c.length);
		assertEquals("3", c[0]);
		assertEquals("3", c[1]); // all 3 for trade
		assertEquals("Fog", c[2]);
		assertEquals("244", c[5]);
		assertEquals("Near Mint", c[6]);
		assertEquals("foil", c[8]);
	}

	/** The cells of a line without a quoted comma: the Deckbox export writes plain values for these cards. */
	private static String[] cells(String line) {
		return line.split(",", -1);
	}

	/**
	 * The header is the one of a real Deckbox export without the columns Deckbox does not need (Promo, Textless,
	 * Printing Id, Printing Note): 16 columns, fixed - the user does not choose them.
	 */
	public void testHeaderIsTheRealDeckboxHeaderWithoutTheUnusedColumns() {
		String expected = REAL_HEADER.replace("Promo,", "").replace("Textless,", "").replace("Printing Id,", "")
				.replace("Printing Note,", "");
		assertEquals(expected, DeckBoxExportDelegate.HEADER);
		assertEquals(16, DeckBoxExportDelegate.Column.values().length);
		assertTrue(DeckBoxExportDelegate.HEADER.endsWith(",Tags,My Price,Scryfall ID"));
		assertFalse(new DeckBoxExportDelegate().isColumnChoiceSupported());
		assertFalse(new DeckBoxWishlistExportDelegate().isColumnChoiceSupported());
	}

	/** Every applicable column is written: the Scryfall ID, the Tags (comment), the Proxy condition, the language. */
	public void testExportAllApplicableColumns() throws Exception {
		MagicCardPhysical fog = card("Fog", "Fourth Edition", "244");
		fog.getBase().setCardId("a1959de7-e945-438c-a90a-158e21c4d5bf");
		fog.setCount(2);
		fog.setComment("Box 3, shelf A");
		fog.setSpecialTag("signed");
		MagicCardPhysical proxy = card("Platinum Angel", "Magic 2010", "218");
		proxy.setProxy(true);
		proxy.setCount(2);
		proxy.setSpecialTag("fortrade"); // a proxy is never for trade
		String[] lines = export(store(fog, proxy), new DeckBoxExportDelegate(), true, "testdeckbox4").split("\r?\n");
		assertEquals(3, lines.length); // the comment is quoted, on one line
		assertTrue(lines[1], lines[1].contains("\"Box 3, shelf A\""));
		assertTrue(lines[1], lines[1].endsWith(",a1959de7-e945-438c-a90a-158e21c4d5bf"));
		assertTrue(lines[1], lines[1].contains(",signed,")); // Signed
		String[] p = cells(lines[2]);
		assertEquals("Platinum Angel", p[2]);
		assertEquals("a proxy is the Proxy condition", "Proxy", p[6]);
		assertEquals("a proxy is never for trade", "0", p[1]);
		assertEquals("no Scryfall ID", "", p[15]);
		// the importer reads the proxy and the comment back
		List<IMagicCard> back = parse(lines[0] + "\n" + lines[1] + "\n" + lines[2] + "\n");
		MagicCardPhysical b0 = (MagicCardPhysical) back.get(0);
		assertEquals("Box 3, shelf A", b0.getComment());
		assertTrue(b0.isSpecialTag("signed"));
		assertTrue(((MagicCardPhysical) back.get(1)).isProxy());
	}

	/** A card kept in English with the language the file gave ("lang=French") is exported in that language. */
	public void testExportLanguageOfTheLangTag() throws Exception {
		MagicCardPhysical fog = card("Fog", "Fourth Edition", "244");
		fog.setSpecialTag("lang=French");
		MagicCardPhysical english = card("Platinum Angel", "Magic 2010", "218");
		String[] lines = export(store(fog, english), new DeckBoxExportDelegate(), false, "testdeckbox5").split("\r?\n");
		assertEquals("French", cells(lines[0])[7]);
		assertEquals("English", cells(lines[1])[7]);
		assertEquals("French", DeckBoxExportDelegate.language(fog, fog));
	}

	public void testWishlistExport() throws Exception {
		MagicCardPhysical fog = card("Fog", "Fourth Edition", "244");
		String[] lines = export(store(fog), new DeckBoxWishlistExportDelegate(), true, "testdeckbox3").split("\r?\n");
		assertEquals(DeckBoxWishlistExportDelegate.HEADER_W, lines[0]);
		assertEquals("the inventory columns but the Tradelist Count",
				DeckBoxExportDelegate.HEADER.replace("Tradelist Count,", ""), lines[0]);
		String[] c = cells(lines[1]);
		assertEquals(15, c.length);
		assertEquals("1", c[0]);
		assertEquals("Fog", c[1]);
		assertEquals("Fourth Edition", c[2]);
		assertEquals("244", c[4]);
		assertEquals("Near Mint", c[5]);
		// the importer reads the wishlist back
		MagicCardPhysical back = (MagicCardPhysical) parse(lines[0] + "\n" + lines[1] + "\n").get(0);
		assertEquals("Fog", back.getName());
		assertEquals(0, back.getForTrade());
	}
}
