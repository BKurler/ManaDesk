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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.MagicException;
import com.reflexit.magiccards.core.exports.ImportUtils;
import com.reflexit.magiccards.core.model.nav.CardCollection;
import com.reflexit.magiccards.core.model.xml.SingleFileCardStorage;
import com.reflexit.unittesting.TestFileUtils;

import junit.framework.TestCase;

/**
 * "For Trade" = the "fortrade" Special tag. Free in a Standard collection; in a For Trade collection it is added to
 * every card that comes in, it cannot be removed, and a proxy is refused. Plain JUnit (the collections live in the
 * test database).
 */
public class ForTradeRulesTest extends TestCase {
	private static boolean reset = true;
	private static int n = 0;
	private DataManager dm;
	private CardCollection trade;
	private CardCollection std;
	private CardCollection wish;

	@Override
	protected void setUp() throws Exception {
		if (reset) {
			TestFileUtils.resetDb();
			reset = false;
		}
		dm = DataManager.getInstance();
		dm.waitForInit(10);
		dm.getLibraryCardStore();
		trade = create(CollectionType.FOR_TRADE);
		std = create(CollectionType.STANDARD);
	}

	@Override
	protected void tearDown() throws Exception {
		for (CardCollection cc : new CardCollection[] { trade, std, wish })
			if (cc != null)
				cc.remove();
		trade = std = wish = null;
		super.tearDown();
	}

	private CardCollection create(CollectionType type) {
		n++;
		CardCollection cc = new CardCollection("ftrade" + n + ".xml", dm.getModelRoot().getCollectionsContainer(), false,
				type.isVirtual(), false);
		cc.persistInitialSettings(type, false);
		return cc;
	}

	private IMagicCard anyCard() {
		for (Object o : dm.getMagicDBStore())
			return (IMagicCard) o;
		MagicCard mc = new MagicCard();
		mc.setName("Test Card");
		mc.setCardId("for-trade-test");
		return mc;
	}

	private MagicCardPhysical card(CardCollection in, int count, boolean proxy) {
		MagicCardPhysical c = new MagicCardPhysical(anyCard(), in.getLocation());
		c.setOwn(true);
		c.setCount(count);
		c.setProxy(proxy);
		return c;
	}

	private MagicCardPhysical only(CardCollection cc) {
		List<MagicCardPhysical> all = new ArrayList<>();
		for (Object o : cc.getStore())
			if (o instanceof MagicCardPhysical)
				all.add((MagicCardPhysical) o);
		assertEquals(1, all.size());
		return all.get(0);
	}

	public void testCardAddedToForTradeIsTagged() {
		dm.add(card(trade, 3, false));
		MagicCardPhysical c = only(trade);
		assertTrue(ForTradeRules.hasTag(c.getSpecial()));
		assertEquals("the whole pile", 3, c.getForTrade());
	}

	public void testStandardCollectionIsNotTagged() {
		dm.add(card(std, 3, false));
		MagicCardPhysical c = only(std);
		assertFalse(ForTradeRules.hasTag(c.getSpecial()));
		assertEquals(0, c.getForTrade());
		// the tag is free there: the user adds and removes it
		dm.setField(c, MagicCardField.SPECIAL, "fortrade");
		assertEquals(3, c.getForTrade());
		dm.setField(c, MagicCardField.SPECIAL, "");
		assertEquals(0, c.getForTrade());
	}

	/** Move in: tagged. Move out to a Standard collection: the tag stays. */
	public void testMoveInTagsAndMoveOutKeepsTheTag() {
		dm.add(card(std, 2, false));
		assertTrue(dm.moveCards(Collections.<IMagicCard> singletonList(only(std)), trade.getStore()));
		assertEquals(0, std.getStore().size());
		MagicCardPhysical inTrade = only(trade);
		assertTrue(ForTradeRules.hasTag(inTrade.getSpecial()));
		assertTrue(dm.moveCards(Collections.<IMagicCard> singletonList(inTrade), std.getStore()));
		MagicCardPhysical back = only(std);
		assertTrue("the tag is kept", ForTradeRules.hasTag(back.getSpecial()));
		assertEquals(2, back.getForTrade());
	}

	public void testProxyIsRefused() {
		MagicCardPhysical proxy = card(trade, 1, true);
		assertEquals(ForTradeRules.NO_PROXY_FOR_TRADE, ForTradeRules.addVeto(proxy, trade.getStore()));
		try {
			dm.add(proxy);
			fail("a proxy cannot be traded");
		} catch (MagicException e) {
			assertEquals(ForTradeRules.NO_PROXY_FOR_TRADE, e.getMessage());
		}
		assertEquals(0, trade.getStore().size());
		// a genuine copy is fine
		assertNull(ForTradeRules.addVeto(card(trade, 1, false), trade.getStore()));
		// moving a proxy in is refused too, and it stays where it was
		dm.add(card(std, 1, true));
		try {
			dm.moveCards(Collections.<IMagicCard> singletonList(only(std)), trade.getStore());
			fail("a proxy cannot be traded");
		} catch (MagicException e) {
			assertEquals(ForTradeRules.NO_PROXY_FOR_TRADE, e.getMessage());
		}
		assertEquals(1, std.getStore().size());
		assertEquals(0, trade.getStore().size());
	}

	/** The tag on a proxy elsewhere is ignored: nothing is for trade. */
	public void testTagOnAProxyIsIgnored() {
		dm.add(card(std, 2, true));
		MagicCardPhysical proxy = only(std);
		dm.setField(proxy, MagicCardField.SPECIAL, "fortrade"); // no error
		assertEquals(0, proxy.getForTrade());
		assertFalse("not tagged by the rules", ForTradeRules.tag(card(trade, 1, true)));
	}

	public void testTagCannotBeRemovedInForTrade() {
		dm.add(card(trade, 2, false));
		MagicCardPhysical c = only(trade);
		for (String text : new String[] { "", "foil", null }) {
			try {
				dm.setField(c, MagicCardField.SPECIAL, text);
				fail("the tag is required: " + text);
			} catch (MagicException e) {
				assertEquals(ForTradeRules.NO_REMOVE_TAG, e.getMessage());
			}
		}
		assertTrue(ForTradeRules.hasTag(c.getSpecial()));
		// other tags are free as long as the tag stays
		dm.setField(c, MagicCardField.SPECIAL, "fortrade,signed");
		assertTrue(c.isSpecialTag("signed"));
		assertEquals(2, c.getForTrade());
		// no card becomes a proxy there
		try {
			dm.setField(c, MagicCardField.PROXY, Boolean.TRUE);
			fail("a proxy cannot be traded");
		} catch (MagicException e) {
			assertEquals(ForTradeRules.NO_PROXY_FOR_TRADE, e.getMessage());
		}
		assertFalse(c.isProxy());
	}

	/** The Edit dialog's text: tags to add, "-tag" to remove. */
	public void testSpecialVetoOfTheEditDialog() {
		dm.add(card(trade, 1, false));
		dm.add(card(std, 1, false));
		MagicCardPhysical inTrade = only(trade);
		MagicCardPhysical inStd = only(std);
		assertEquals(ForTradeRules.NO_REMOVE_TAG, ForTradeRules.specialVeto(inTrade, "-fortrade"));
		assertEquals(ForTradeRules.NO_REMOVE_TAG, ForTradeRules.specialVeto(inTrade, "foil,-fortrade"));
		assertNull(ForTradeRules.specialVeto(inTrade, "+foil"));
		assertNull(ForTradeRules.specialVeto(inTrade, "-foil"));
		assertNull("an empty text changes nothing", ForTradeRules.specialVeto(inTrade, ""));
		assertNull("free in a Standard collection", ForTradeRules.specialVeto(inStd, "-fortrade"));
	}

	/** Becoming a For Trade collection: every card is tagged; not with a proxy in it. */
	public void testBecomingForTrade() {
		dm.add(card(std, 2, false));
		MagicCardPhysical c = only(std);
		assertFalse(ForTradeRules.hasTag(c.getSpecial()));
		assertNull(OwnershipRules.typeChangeVeto(std.getStorageInfo(), std.getStore(), CollectionType.FOR_TRADE));
		assertEquals(1, ForTradeRules.tagAll(std.getStore()));
		assertEquals(0, ForTradeRules.tagAll(std.getStore())); // already done
		assertEquals(2, c.getForTrade());
		// a proxy in the collection: it cannot become For Trade
		dm.add(card(std, 1, true));
		String veto = OwnershipRules.typeChangeVeto(std.getStorageInfo(), std.getStore(), CollectionType.FOR_TRADE);
		assertNotNull(veto);
		assertTrue(veto, veto.contains("proxy"));
		assertEquals(CollectionType.STANDARD, std.getCollectionType());
	}

	/** A For Trade collection opened with untagged cards (older data) tags them when it is loaded. */
	public void testOpeningTagsTheCardsOfAForTradeCollection() throws Exception {
		MagicCardPhysical raw = card(trade, 2, false);
		SingleFileCardStorage storage = (SingleFileCardStorage) OwnershipRules.getStorageInfo(trade.getStore());
		storage.add(raw); // straight into the storage: no rule runs, as in a file written before the rules
		assertFalse(ForTradeRules.hasTag(raw.getSpecial()));
		storage.save();
		// a fresh load of the same file, as when the collection is opened
		SingleFileCardStorage reopened = new SingleFileCardStorage(storage.getFile(), trade.getLocation(), true);
		assertEquals(CollectionType.FOR_TRADE, reopened.getCollectionType());
		int seen = 0;
		for (Object o : reopened) {
			MagicCardPhysical c = (MagicCardPhysical) o;
			assertTrue("tagged when opened", ForTradeRules.hasTag(c.getSpecial()));
			assertEquals(2, c.getForTrade());
			seen++;
		}
		assertEquals(1, seen);
		// a Standard collection is left alone
		MagicCardPhysical plain = card(std, 1, false);
		SingleFileCardStorage stdStorage = (SingleFileCardStorage) OwnershipRules.getStorageInfo(std.getStore());
		stdStorage.add(plain);
		stdStorage.save();
		for (Object o : new SingleFileCardStorage(stdStorage.getFile(), std.getLocation(), true))
			assertFalse(ForTradeRules.hasTag(((MagicCardPhysical) o).getSpecial()));
	}

	public void testProxiesVeto() {
		assertNull(ForTradeRules.proxiesVeto(null));
		assertNull(ForTradeRules.proxiesVeto(Arrays.asList("a card", card(std, 1, false))));
		assertNotNull(ForTradeRules.proxiesVeto(Arrays.asList(card(std, 1, true))));
	}

	/** Decks and Wishlist/To Print collections have no rule of their own. */
	public void testOtherListsBehaveLikeStandard() {
		wish = create(CollectionType.WISH);
		MagicCardPhysical c = card(wish, 1, true);
		c.setOwn(false);
		assertFalse(ForTradeRules.isForTradeList(wish.getStore()));
		assertNull(ForTradeRules.addVeto(c, wish.getStore()));
		ForTradeRules.prepare(c, wish.getStore());
		assertFalse(ForTradeRules.hasTag(c.getSpecial()));
		assertNull(ForTradeRules.fieldVeto(wish.getStore(), MagicCardField.SPECIAL, ""));
		CardCollection deck = dm.getModelRoot().getDeckContainer().addDeck("ftrade-deck" + (++n), true, false);
		try {
			assertFalse(ForTradeRules.isForTradeList(deck.getStore()));
			assertNull(ForTradeRules.fieldVeto(deck.getStore(), MagicCardField.SPECIAL, ""));
		} finally {
			deck.remove();
		}
	}

	/** An import into a For Trade collection: tagged; a proxy is left out (not a failure of the whole import). */
	public void testImportIntoForTrade() {
		List<Object> list = new ArrayList<>();
		MagicCardPhysical genuine = card(trade, 1, false);
		MagicCardPhysical proxy = card(trade, 1, true);
		list.add(genuine);
		list.add(proxy);
		ImportUtils.performImport(list, trade.getStore());
		assertEquals(1, trade.getStore().size());
		assertTrue(ForTradeRules.hasTag(only(trade).getSpecial()));
		assertFalse(list.contains(proxy));
	}

	/** The For Trade column only shows getForTrade(): a tagged pile reports its whole count, an untagged one 0. */
	public void testTaggedPileIsWhollyForTrade() {
		MagicCardPhysical c = card(std, 4, false);
		assertEquals(0, c.getForTrade());
		c.setSpecialTag("fortrade");
		assertEquals(4, c.getForTrade());
		c.setCount(5);
		assertEquals(5, c.getForTrade());
	}
}
