/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *     Rémi Dutil (2026) - per-store buy list text (TCGplayer / Cardmarket /
 *                         Card Kingdom / plain), exact printing on and off.
 *******************************************************************************/
package com.reflexit.magiccards.core.exports;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.reflexit.magiccards.core.model.CardFinish;

/** No database: lines are built directly. */
public class BuyListFormatTest {

	private static final BuyListLine BOLT_SLD_FOIL = new BuyListLine("Lightning Bolt", 2, "sld", "Secret Lair Drop",
			"84", CardFinish.FOIL);
	private static final BuyListLine RING_ETCHED = new BuyListLine("Sol Ring", 1, "CMR", "Commander Legends", "472",
			CardFinish.ETCHED);
	private static final BuyListLine NO_PRINTING = new BuyListLine("Counterspell", 4, null, null, null, null);

	private static final List<BuyListLine> LIST = Arrays.asList(BOLT_SLD_FOIL, RING_ETCHED, NO_PRINTING);

	@Test
	public void tcgplayerExactUsesBracketedSetAndCollectorNumber() {
		Assert.assertEquals("2 Lightning Bolt [SLD] 84\n1 Sol Ring [CMR] 472\n4 Counterspell",
				BuyListFormat.TCGPLAYER.format(LIST, true));
	}

	@Test
	public void anyPrintingIsNamesOnly() {
		Assert.assertEquals("2 Lightning Bolt\n1 Sol Ring\n4 Counterspell", BuyListFormat.TCGPLAYER.format(LIST, false));
		// any printing = any finish too: no finish markers
		Assert.assertEquals("2 Lightning Bolt\n1 Sol Ring\n4 Counterspell", BuyListFormat.PLAIN.format(LIST, false));
	}

	@Test
	public void cardmarketUsesExpansionName() {
		Assert.assertEquals("2 Lightning Bolt (Secret Lair Drop)\n1 Sol Ring (Commander Legends)\n4 Counterspell",
				BuyListFormat.CARDMARKET.format(LIST, true));
	}

	@Test
	public void cardKingdomIsAlwaysNamesOnly() {
		Assert.assertEquals("2 Lightning Bolt\n1 Sol Ring\n4 Counterspell",
				BuyListFormat.CARD_KINGDOM.format(LIST, true));
		Assert.assertFalse(BuyListFormat.CARD_KINGDOM.supportsPrinting());
	}

	@Test
	public void faceToFaceIsNamesOnly() {
		Assert.assertEquals("2 Lightning Bolt\n1 Sol Ring\n4 Counterspell",
				BuyListFormat.FACE_TO_FACE.format(LIST, true));
		Assert.assertFalse(BuyListFormat.FACE_TO_FACE.supportsPrinting());
	}

	@Test
	public void quebecStoresAreNamesOnlySplitByFinish() {
		for (BuyListFormat f : new BuyListFormat[] { BuyListFormat.MYTHIC_STORE, BuyListFormat.IMAGINAIRE }) {
			Assert.assertEquals("2 Lightning Bolt\n1 Sol Ring\n4 Counterspell", f.format(LIST, true));
			Assert.assertFalse(f.supportsPrinting());
			Assert.assertTrue(f.supportsFinish());
			List<BuyListFormat.Group> g = f.groups(LIST, true);
			Assert.assertEquals(2, g.size());
			Assert.assertEquals("Non-foil", g.get(0).getTitle());
			Assert.assertEquals("4 Counterspell", g.get(0).getText());
			Assert.assertEquals("Foil", g.get(1).getTitle()); // foil + etched
			Assert.assertEquals("2 Lightning Bolt\n1 Sol Ring", g.get(1).getText());
			Assert.assertTrue(g.get(1).getHint().contains("Foil"));
			// exact printing: two lists unless combined; any printing: any finish, one list
			Assert.assertEquals(1, f.groups(LIST, true, true).size());
			Assert.assertEquals(1, f.groups(LIST, false).size());
		}
	}

	@Test
	public void plainMarksFinish() {
		Assert.assertEquals("2 Lightning Bolt (SLD) 84 *F*\n1 Sol Ring (CMR) 472 *E*\n4 Counterspell",
				BuyListFormat.PLAIN.format(LIST, true));
		Assert.assertTrue(BuyListFormat.PLAIN.supportsFinish());
	}

	@Test
	public void identicalLinesAreMergedAndEmptyOnesSkipped() {
		List<BuyListLine> l = Arrays.asList(new BuyListLine("Sol Ring", 1, "CMR", "Commander Legends", "472", null),
				new BuyListLine("Sol Ring", 3, "CMR", "Commander Legends", "472", null),
				new BuyListLine("Sol Ring", 2, "C21", "Commander 2021", "263", null),
				new BuyListLine("Island", 0, null, null, null, null), new BuyListLine("", 5, null, null, null, null));
		Assert.assertEquals("4 Sol Ring [CMR] 472\n2 Sol Ring [C21] 263", BuyListFormat.TCGPLAYER.format(l, true));
		// any printing: all Sol Rings become one line
		Assert.assertEquals("6 Sol Ring", BuyListFormat.TCGPLAYER.format(l, false));
	}

	@Test
	public void tcgplayerSplitsByFinish() {
		List<BuyListFormat.Group> g = BuyListFormat.TCGPLAYER.groups(LIST, true);
		Assert.assertEquals(2, g.size());
		Assert.assertEquals("Non-foil", g.get(0).getTitle());
		Assert.assertEquals("4 Counterspell", g.get(0).getText());
		Assert.assertTrue(g.get(0).getHint().contains("Normal"));
		Assert.assertEquals("Foil", g.get(1).getTitle());
		// etched goes with foil (Mass Entry only has Normal / Foil)
		Assert.assertEquals("2 Lightning Bolt [SLD] 84\n1 Sol Ring [CMR] 472", g.get(1).getText());
	}

	@Test
	public void tcgplayerOmitsEmptyGroupAndAnyPrintingIsOneList() {
		List<BuyListFormat.Group> g = BuyListFormat.TCGPLAYER.groups(Arrays.asList(NO_PRINTING), true);
		Assert.assertEquals(1, g.size());
		Assert.assertEquals("Non-foil", g.get(0).getTitle());
		// any printing = any finish: one list
		g = BuyListFormat.TCGPLAYER.groups(LIST, false);
		Assert.assertEquals(1, g.size());
		Assert.assertEquals("2 Lightning Bolt\n1 Sol Ring\n4 Counterspell", g.get(0).getText());
		// exact printing, combined: one list
		Assert.assertEquals(1, BuyListFormat.TCGPLAYER.groups(LIST, true, true).size());
	}

	@Test
	public void everyStoreSplitsByFinishUnlessCombined() {
		// split is the standard: Cardmarket and Plain too
		List<BuyListFormat.Group> g = BuyListFormat.CARDMARKET.groups(LIST, true);
		Assert.assertEquals(2, g.size());
		Assert.assertEquals("4 Counterspell", g.get(0).getText());
		Assert.assertEquals("On Cardmarket, choose Foil", g.get(1).getHint());
		Assert.assertEquals(2, BuyListFormat.PLAIN.groups(LIST, true).size());
		// combined: one list, no hint; Plain keeps its *F* / *E* markers
		g = BuyListFormat.PLAIN.groups(LIST, true, true);
		Assert.assertEquals(1, g.size());
		Assert.assertEquals("", g.get(0).getHint());
		Assert.assertEquals("2 Lightning Bolt (SLD) 84 *F*\n1 Sol Ring (CMR) 472 *E*\n4 Counterspell", g.get(0).getText());
		Assert.assertEquals(1, BuyListFormat.TCGPLAYER.groups(LIST, true, true).size());
		Assert.assertTrue(BuyListFormat.CARDMARKET.groups(java.util.Collections.<BuyListLine> emptyList(), true)
				.isEmpty());
	}

	@Test
	public void tcgplayerUsesProductIdWhenKnown() {
		// MB2 208 is "Manabond (Future Sight)" on TCGplayer - the id avoids the name entirely
		BuyListLine manabond = new BuyListLine("Manabond", 1, "MB2", "Mystery Booster 2", "208", CardFinish.NONFOIL,
				563218);
		BuyListLine bolt = new BuyListLine("Lightning Bolt", 2, "M11", "Magic 2011", "149", CardFinish.FOIL, 35427);
		List<BuyListFormat.Group> g = BuyListFormat.TCGPLAYER.groups(Arrays.asList(manabond, bolt, NO_PRINTING),
				true);
		Assert.assertEquals("1-563218\n4 Counterspell", g.get(0).getText());
		Assert.assertEquals(Arrays.asList("1-563218", "4 Counterspell"), g.get(0).getLines());
		Assert.assertEquals("2-35427", g.get(1).getText());
		// any printing: names, so TCGplayer may pick any (cheapest) printing
		Assert.assertEquals("1 Manabond\n2 Lightning Bolt\n4 Counterspell",
				BuyListFormat.TCGPLAYER.format(Arrays.asList(manabond, bolt, NO_PRINTING), false));
		// other stores ignore the id
		Assert.assertEquals("1 Manabond (Mystery Booster 2)",
				BuyListFormat.CARDMARKET.format(Arrays.asList(manabond), true));
	}

	@Test
	public void sameProductIdIsMerged() {
		List<BuyListLine> l = Arrays.asList(
				new BuyListLine("Manabond", 1, "MB2", null, "208", CardFinish.NONFOIL, 563218),
				new BuyListLine("Manabond", 2, "MB2", null, "208", CardFinish.NONFOIL, 563218));
		Assert.assertEquals("3-563218", BuyListFormat.TCGPLAYER.format(l, true));
	}

	@Test
	public void quantitiesAboveNineKeepWholeNumber() {
		List<BuyListLine> l = Arrays.asList(new BuyListLine("Island", 12, null, null, null, null),
				new BuyListLine("Island", 30, null, null, null, null));
		Assert.assertEquals("42 Island", BuyListFormat.PLAIN.format(l, true));
	}
}
