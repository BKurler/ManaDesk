/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *     Rémi Dutil (2026) - testPricesGoToTheirOwnSource: TCGplayer and
 *                         Cardmarket prices in separate sources, no price
 *                         text in the card text.
 *     Rémi Dutil (2026) - testStoreLinksAreDirectNotScryfallPurchaseUris: the
 *                         card-text TCGplayer / Cardmarket links come from the
 *                         product ids, never Scryfall's affiliate URIs.
 *     Rémi Dutil (2026) - testPricesGoToTheirOwnSource: the price line is back
 *                         at the top of the card text (TCGplayer $ line, then Cardmarket € line).
 *******************************************************************************/
package com.reflexit.magiccards.core.sync;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;
import com.reflexit.magiccards.core.test.assist.AbstractMagicTest;
import com.reflexit.unittesting.TestFileUtils;

/**
 * Offline tests for the Scryfall "Default Cards" bulk-file parse
 * ({@link ParseScryFallChecklist#parseBulkGrouped}) that feeds the single
 * "Update Card Database" action. No network: a tiny fake {@code .jsonl.gz} is
 * built on disk.
 */
public class ScryfallBulkSplitTest extends AbstractMagicTest {

	@BeforeClass
	public static void setUpBeforeClass() {
		TestFileUtils.resetDb();
		DataManager.getInstance().waitForInit(10);
	}

	// ---------------------------------------------------------------- helpers

	/** A minimal but parseRecord-safe single-face "normal" Scryfall card object. */
	private static String cardJson(String set, String name, String collNum) {
		String id = name.replaceAll("\\s", "") + "-" + set;
		return "{\"id\":\"" + id + "\",\"lang\":\"en\",\"games\":[\"paper\"],\"layout\":\"normal\","
				+ "\"set\":\"" + set + "\",\"set_name\":\"" + set.toUpperCase() + " Set\",\"name\":\"" + name + "\","
				+ "\"mana_cost\":\"{G}\",\"type_line\":\"Creature\",\"rarity\":\"common\",\"artist\":\"Tester\","
				+ "\"collector_number\":\"" + collNum + "\",\"oracle_text\":\"Text\",\"full_art\":false,"
				+ "\"textless\":false,\"story_spotlight\":false,\"booster\":true,"
				+ "\"image_uris\":{\"normal\":\"https://example.com/" + collNum + ".jpg\"},"
				+ "\"scryfall_uri\":\"https://scryfall.com/x\"}";
	}

	private static File gzWithLines(String... lines) throws IOException {
		File f = File.createTempFile("bulk-test-", ".jsonl.gz");
		f.deleteOnExit();
		try (PrintStream out = new PrintStream(new GZIPOutputStream(new FileOutputStream(f)), false, "UTF-8")) {
			for (String l : lines)
				out.println(l);
		}
		return f;
	}

	private static Map<String, List<MagicCard>> parse(File bulk, java.util.Set<String> only) throws Exception {
		return new ParseScryFallChecklist().parseBulkGrouped(bulk, only, ICoreProgressMonitor.NONE);
	}

	// ------------------------------------------------------------------ tests

	@Test
	public void testFullPassGroupsEverySet() throws Exception {
		File bulk = gzWithLines(cardJson("aaa", "A1", "1"), cardJson("aaa", "A2", "2"), cardJson("bbb", "B1", "1"));

		Map<String, List<MagicCard>> g = parse(bulk, null);

		Assert.assertEquals("two sets", 2, g.size());
		Assert.assertEquals("aaa: 2 cards", 2, g.get("aaa").size());
		Assert.assertEquals("bbb: 1 card", 1, g.get("bbb").size());
	}

	@Test
	public void testFiltersToRequestedSet() throws Exception {
		File bulk = gzWithLines(cardJson("tst", "Card One", "1"), cardJson("tst", "Card Two", "2"),
				cardJson("oth", "Other Card", "1"));

		Map<String, List<MagicCard>> g = parse(bulk, Collections.singleton("tst"));

		Assert.assertNotNull(g.get("tst"));
		Assert.assertEquals("two 'tst' cards", 2, g.get("tst").size());
		Assert.assertTrue("'oth' not returned", g.get("oth") == null || g.get("oth").isEmpty());
	}

	@Test
	public void testPricesGoToTheirOwnSource() throws Exception {
		// both sources priced / Cardmarket only (a Europe-only printing)
		String both = cardJson("prc", "Priced Both", "1").replace("\"scryfall_uri\"",
				"\"prices\":{\"usd\":\"1.50\",\"usd_foil\":\"4.00\",\"eur\":\"1.20\",\"eur_foil\":null},"
						+ "\"scryfall_uri\"");
		String euOnly = cardJson("prc", "Euro Only", "2").replace("\"scryfall_uri\"",
				"\"prices\":{\"usd\":null,\"usd_foil\":null,\"eur\":\"0.80\"},\"scryfall_uri\"");
		Map<String, List<MagicCard>> g = parse(gzWithLines(both, euOnly), Collections.singleton("prc"));
		Assert.assertEquals(2, g.get("prc").size());

		com.reflexit.magiccards.core.model.xml.DbPricesMultiFileStore store = (com.reflexit.magiccards.core.model.xml.DbPricesMultiFileStore) com.reflexit.magiccards.core.model.xml.DbPricesMultiFileStore
				.getInstance();
		com.reflexit.magiccards.core.seller.IPriceProvider tcg = store
				.getSource(com.reflexit.magiccards.core.seller.PriceSources.TCGPLAYER);
		com.reflexit.magiccards.core.seller.IPriceProvider cm = store
				.getSource(com.reflexit.magiccards.core.seller.PriceSources.CARDMARKET);
		java.util.Currency usd = java.util.Currency.getInstance("USD");
		java.util.Currency eur = java.util.Currency.getInstance("EUR");

		Assert.assertEquals(1.50f, tcg.getDbPrice("PricedBoth-prc", usd), 0.001f);
		Assert.assertEquals(4.00f, tcg.getDbPriceFoil("PricedBoth-prc", usd), 0.001f);
		Assert.assertEquals(1.20f, cm.getDbPrice("PricedBoth-prc", eur), 0.001f);
		Assert.assertTrue("no Cardmarket foil price", cm.getDbPriceFoil("PricedBoth-prc", eur) <= 0);
		// no more EUR-converted-to-USD fallback: a Cardmarket-only card has no TCGplayer price
		Assert.assertTrue("no TCGplayer price", tcg.getDbPrice("EuroOnly-prc", usd) <= 0);
		Assert.assertEquals(0.80f, cm.getDbPrice("EuroOnly-prc", eur), 0.001f);
		// the price line opens the card text: each store in its own currency, no conversion
		for (MagicCard c : g.get("prc"))
			if (c.getName().equals("Priced Both"))
				Assert.assertTrue(c.getText(), c.getText().startsWith("N$ 1.50 F$ 4.00<br>N€ 1.20<br>"));
			else
				Assert.assertTrue(c.getText(), c.getText().startsWith("N€ 0.80<br>"));
	}

	@Test
	public void testStoreLinksAreDirectNotScryfallPurchaseUris() throws Exception {
		String scryfallAffiliate = "https://partner.tcgplayer.com/c/4931599/1830156/21018?subId1=api";
		String both = cardJson("lnk", "Linked Both", "1").replace("\"scryfall_uri\"",
				"\"tcgplayer_id\":563218,\"cardmarket_id\":790931,\"purchase_uris\":{\"tcgplayer\":\""
						+ scryfallAffiliate + "\",\"cardmarket\":\"https://www.cardmarket.com/x?referrer=scryfall\"},"
						+ "\"scryfall_uri\"");
		String etchedOnly = cardJson("lnk", "Etched Only", "2").replace("\"scryfall_uri\"",
				"\"tcgplayer_etched_id\":111,\"scryfall_uri\"");
		String none = cardJson("lnk", "No Ids", "3");
		Map<String, List<MagicCard>> g = parse(gzWithLines(both, etchedOnly, none), Collections.singleton("lnk"));
		String bothText = null, etchedText = null, noneText = null;
		for (MagicCard c : g.get("lnk"))
			if (c.getName().equals("Linked Both"))
				bothText = c.getText();
			else if (c.getName().equals("Etched Only"))
				etchedText = c.getText();
			else
				noneText = c.getText();
		Assert.assertTrue(bothText, bothText.contains(
				"<a href=\"https://www.tcgplayer.com/product/563218\">TCGplayer</a>"));
		Assert.assertTrue(bothText, bothText.contains(
				"<a href=\"https://www.cardmarket.com/en/Magic/Products?idProduct=790931\">Cardmarket</a>"));
		Assert.assertFalse("no Scryfall affiliate link", bothText.contains("partner.tcgplayer.com"));
		Assert.assertFalse("no Scryfall referrer", bothText.contains("referrer=scryfall"));
		Assert.assertTrue(etchedText, etchedText.contains("https://www.tcgplayer.com/product/111"));
		Assert.assertFalse(etchedText, etchedText.contains("Cardmarket"));
		Assert.assertFalse(noneText, noneText.contains("TCGplayer") || noneText.contains("Cardmarket"));
	}

	@Test
	public void testSkipsNonPaper() throws Exception {
		String digital = cardJson("tst", "Arena Card", "5").replace("[\"paper\"]", "[\"mtgo\",\"arena\"]");
		File bulk = gzWithLines(cardJson("tst", "Paper Card", "1"), digital);

		Map<String, List<MagicCard>> g = parse(bulk, Collections.singleton("tst"));

		Assert.assertEquals("only the paper card kept", 1, g.get("tst").size());
	}

	@Test
	public void testToleratesArrayWrappingAndBlankLines() throws Exception {
		File bulk = gzWithLines("[", "  " + cardJson("tst", "A", "1") + ",", "", "  " + cardJson("tst", "B", "2"), "]");

		Map<String, List<MagicCard>> g = parse(bulk, Collections.singleton("tst"));

		Assert.assertEquals(2, g.get("tst").size());
	}

	@Test
	public void testCancelledParseThrows() throws Exception {
		String[] many = new String[9000];
		for (int i = 0; i < many.length; i++)
			many[i] = cardJson("tst", "C" + i, String.valueOf(i));
		File bulk = gzWithLines(many);

		ICoreProgressMonitor cancelled = new ICoreProgressMonitor() {
			@Override public void beginTask(String name, int totalWork) {}
			@Override public void subTask(String name) {}
			@Override public void setTaskName(String name) {}
			@Override public void worked(int work) {}
			@Override public void internalWorked(double work) {}
			@Override public void done() {}
			@Override public void setCanceled(boolean value) {}
			@Override public boolean isCanceled() { return true; }
		};
		try {
			new ParseScryFallChecklist().parseBulkGrouped(bulk, null, cancelled);
			Assert.fail("expected InterruptedException");
		} catch (InterruptedException expected) {
			// good
		}
	}
}
