/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *     Rémi Dutil (2026) - DeckNeeds (shared by the Proxier and Buyer views):
 *                         wanted / owned / boxed / shortfall /
 *                         genuineShortfall / per-list breakdown.
 *******************************************************************************/
package com.reflexit.magiccards.core.model.nav;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.nav.DeckNeeds.Row;
import com.reflexit.magiccards.core.model.storage.IStorageInfo;
import com.reflexit.unittesting.TestFileUtils;

import junit.framework.TestCase;

public class DeckNeedsTest extends TestCase {
	static final String CARD_ID = "8b069e6a-2c0e-4fc9-8e19-08bf1245a6c0"; // Mystic Decree
	private static boolean reset = true;
	private static int n = 0;
	private DataManager dm;
	private CardCollection wantDeck;
	private CardCollection stock;
	private CardCollection boxedDeck;

	@Override
	protected void setUp() throws Exception {
		if (reset) {
			TestFileUtils.resetDb();
			reset = false;
		}
		dm = DataManager.getInstance();
		dm.waitForInit(10);
		dm.getLibraryCardStore();
		wantDeck = create(true, true);
		stock = create(false, false);
		boxedDeck = null;
	}

	@Override
	protected void tearDown() throws Exception {
		wantDeck.remove();
		stock.remove();
		if (boxedDeck != null)
			boxedDeck.remove();
		super.tearDown();
	}

	private CardCollection create(boolean deck, boolean virtual) {
		n++;
		CardCollection cc = dm.getModelRoot().getDeckContainer().addDeck("needs" + n, false, virtual);
		IStorageInfo info = cc.getStorageInfo();
		info.setVirtual(virtual);
		info.setType(deck ? IStorageInfo.DECK_TYPE : IStorageInfo.COLLECTION_TYPE);
		return cc;
	}

	private MagicCardPhysical add(CardCollection cc, int count, boolean own, boolean proxy) {
		IMagicCard base = dm.getMagicDBStore().getCard(CARD_ID);
		assertNotNull(base);
		MagicCardPhysical c = new MagicCardPhysical(base, cc.getLocation());
		c.setOwn(own);
		c.setProxy(proxy);
		c.setCount(count);
		dm.add(c);
		return c;
	}

	private Row row(List<CardCollection> sources) {
		List<Row> rows = DeckNeeds.compute(sources, false);
		assertEquals(1, rows.size());
		return rows.get(0);
	}

	public void testNothingOwnedEverythingIsShort() {
		add(wantDeck, 3, false, false);
		Row r = row(Collections.singletonList(wantDeck));
		assertEquals(3, r.wanted);
		int owned = r.owned.total();
		assertEquals(Math.max(0, 3 - owned), r.shortfall());
		assertEquals(1, r.perList.size());
		assertEquals(3, r.perList.values().iterator().next().needed);
	}

	public void testProxiesCoverSlotsUnlessReplaced() {
		add(wantDeck, 3, false, false);
		Row before = row(Collections.singletonList(wantDeck));
		int g0 = before.owned.genuine;
		int p0 = before.owned.proxy;
		add(stock, 1, true, false); // one genuine copy
		add(stock, 1, true, true); // one proxy copy
		Row r = row(Collections.singletonList(wantDeck));
		assertEquals(g0 + 1, r.owned.genuine);
		assertEquals(p0 + 1, r.owned.proxy);
		// proxies cover a slot for "To Print" / plain "To Buy"...
		assertEquals(Math.max(0, 3 - (g0 + 1) - (p0 + 1)), r.shortfall());
		// ...but not when proxies are to be replaced (genuine copies only)
		assertEquals(Math.max(0, 3 - (g0 + 1)), r.genuineShortfall());
	}

	public void testBoxedDeckClaimsStock() {
		add(wantDeck, 2, false, false);
		add(stock, 2, true, false);
		Row free = row(Collections.singletonList(wantDeck));
		boxedDeck = create(true, true);
		add(boxedDeck, 1, false, false); // a virtual slot in a boxed deck still claims a real copy
		boxedDeck.getStorageInfo().setBoxed(true);
		Row r = row(Collections.singletonList(wantDeck));
		assertEquals(free.boxed.genuine + 1, r.boxed.genuine);
		assertEquals(Math.max(0, free.available().genuine - 1), r.available().genuine);
		assertTrue(r.shortfall() >= free.shortfall());
	}

	public void testByFinishKeepsFoilAndNonFoilApart() {
		MagicCardPhysical foilWant = new MagicCardPhysical(dm.getMagicDBStore().getCard(CARD_ID),
				wantDeck.getLocation());
		foilWant.setOwn(false);
		foilWant.setCount(2);
		foilWant.setFinish(com.reflexit.magiccards.core.model.CardFinish.FOIL);
		dm.add(foilWant);
		add(wantDeck, 1, false, false); // non-foil want
		add(stock, 3, true, false); // 3 genuine NON-foil owned
		List<Row> rows = DeckNeeds.compute(Collections.singletonList(wantDeck), false, true);
		assertEquals(2, rows.size());
		Row nonFoil = null;
		Row foil = null;
		for (Row r : rows)
			if (r.finish == com.reflexit.magiccards.core.model.CardFinish.FOIL)
				foil = r;
			else
				nonFoil = r;
		assertNotNull(foil);
		assertNotNull(nonFoil);
		assertEquals(2, foil.wanted);
		assertEquals(1, nonFoil.wanted);
		// non-foil copies don't cover the foil ones
		assertEquals(0, foil.owned.genuine);
		assertEquals(2, foil.shortfall());
		assertTrue(nonFoil.owned.genuine >= 3);
		assertEquals(0, nonFoil.shortfall());
		// without byFinish: one pooled row, as the Proxier sees it
		assertEquals(1, DeckNeeds.compute(Collections.singletonList(wantDeck), false, false).size());
	}

	public void testSourcesArePooledAndListedSeparately() {
		CardCollection other = create(true, true);
		try {
			add(wantDeck, 2, false, false);
			add(other, 1, false, false);
			Row r = row(Arrays.asList(wantDeck, other));
			assertEquals(3, r.wanted);
			assertEquals(2, r.perList.size());
		} finally {
			other.remove();
		}
	}
}
