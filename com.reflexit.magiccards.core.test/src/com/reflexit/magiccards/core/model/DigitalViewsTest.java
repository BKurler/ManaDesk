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

import java.util.HashMap;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.nav.CardCollection;
import com.reflexit.unittesting.TestFileUtils;

import junit.framework.TestCase;

/**
 * The views of the digital cards: the Paper / Digital card-kind filter, what the owned counts add
 * up for Collector (paper or digital, never both), the value columns of a digital list and the list
 * of digital decks / collections that blocks turning the digital cards off. Plain JUnit, with the
 * core test database fixtures like {@code DigitalRulesTest}.
 */
public class DigitalViewsTest extends TestCase {
	private static boolean reset = true;
	private static int n = 0;
	private DataManager dm;
	private CardCollection paper;
	private CardCollection digital;
	private boolean wasEnabled;

	@Override
	protected void setUp() throws Exception {
		if (reset) {
			TestFileUtils.resetDb();
			reset = false;
		}
		dm = DataManager.getInstance();
		dm.waitForInit(10);
		dm.getLibraryCardStore();
		wasEnabled = DigitalSupport.isEnabled();
	}

	@Override
	protected void tearDown() throws Exception {
		DigitalSupport.setEnabled(wasEnabled);
		dm.setOwnedKind(DataManager.OwnedKind.ALL);
		if (paper != null)
			paper.remove();
		if (digital != null)
			digital.remove();
		paper = digital = null;
		super.tearDown();
	}

	private CardCollection create(boolean isDigital) {
		n++;
		CardCollection cc = new CardCollection("digviews" + n + ".xml", dm.getModelRoot().getCollectionsContainer(),
				false, false, false);
		cc.persistInitialSettings(CollectionType.STANDARD, false);
		cc.persistDigital(isDigital);
		return cc;
	}

	private static MagicCard card(String id, String name, String digitalOnly, String online) {
		MagicCard c = new MagicCard();
		c.setCardId(id);
		c.setName(name);
		c.set(MagicCardField.DIGITAL, digitalOnly);
		c.set(MagicCardField.ONLINE, online);
		return c;
	}

	// ------------------------------------------------------------------ filter

	private static boolean hidden(MagicCardFilter filter, Object card) {
		return filter.isFiltered(card);
	}

	private static MagicCardFilter filterOf(boolean paper, boolean digital) {
		HashMap<String, String> map = new HashMap<String, String>();
		CardKinds kinds = CardKinds.getInstance();
		if (paper)
			map.put(kinds.getPrefConstant(CardKinds.PAPER), "true");
		if (digital)
			map.put(kinds.getPrefConstant(CardKinds.DIGITAL), "true");
		MagicCardFilter filter = new MagicCardFilter();
		filter.update(map);
		return filter;
	}

	public void testPaperFilterHidesOnlyTheDigitalOnlyCards() {
		MagicCardFilter filter = filterOf(true, false);
		assertFalse(hidden(filter, card("a", "A", null, null)));
		assertFalse("a paper card also online is a paper card", hidden(filter, card("b", "B", null, "true")));
		assertTrue(hidden(filter, card("c", "C", "true", null)));
	}

	public void testDigitalFilterShowsTheCardsOnMtgoOrArena() {
		MagicCardFilter filter = filterOf(false, true);
		assertTrue(hidden(filter, card("a", "A", null, null)));
		assertFalse(hidden(filter, card("b", "B", null, "true")));
		assertFalse(hidden(filter, card("c", "C", "true", null)));
	}

	public void testBothKindsShowEverythingAndNoneIsNoFilter() {
		MagicCardFilter both = filterOf(true, true);
		MagicCardFilter none = filterOf(false, false);
		for (MagicCard c : new MagicCard[] { card("a", "A", null, null), card("b", "B", null, "true"),
				card("c", "C", "true", null) }) {
			assertFalse(hidden(both, c));
			assertFalse(hidden(none, c));
		}
	}

	public void testKindFilterAppliesToACopyThroughItsPrinting() {
		MagicCardFilter filter = filterOf(true, false);
		assertTrue(hidden(filter, new MagicCardPhysical(card("c", "C", "true", null), null)));
		assertFalse(hidden(filter, new MagicCardPhysical(card("a", "A", null, null), null)));
	}

	public void testKindIdsAreAmongTheFilterIds() {
		assertTrue(FilterField.getAllIds().containsAll(CardKinds.getInstance().getIds()));
		assertTrue(FilterField.getAllIds().contains(CardKinds.paperId()));
	}

	// ---------------------------------------------------------------- group by

	public void testCardKindNamesTheGroups() {
		assertEquals("Paper", card("a", "A", null, null).get(MagicCardField.CARD_KIND));
		assertEquals("Paper + online", card("b", "B", null, "true").get(MagicCardField.CARD_KIND));
		assertEquals("Digital", card("c", "C", "true", null).get(MagicCardField.CARD_KIND));
		// a copy is grouped by its printing
		assertEquals("Digital", new MagicCardPhysical(card("c", "C", "true", null), null).get(MagicCardField.CARD_KIND));
		assertEquals("Card kind", MagicCardField.CARD_KIND.getLabel());
	}

	// ----------------------------------------------------------- owned counts

	public void testOwnedCountsFollowTheKindOfList() {
		paper = create(false);
		digital = create(true);
		MagicCard base = card("dv-both", "Digital Views Test", null, "true");
		base.setSet("Digital Views Test Set");
		base.setCollNumber("1");
		dm.getMagicDBStore().add(base);
		try {
			MagicCardPhysical inPaper = new MagicCardPhysical(base, paper.getLocation());
			inPaper.setCount(3);
			inPaper.setOwn(true);
			dm.add(inPaper);
			MagicCardPhysical inDigital = new MagicCardPhysical(base, digital.getLocation());
			inDigital.setCount(2);
			inDigital.setOwn(true);
			dm.add(inDigital);

			assertEquals("every list", 5, dm.getRealCards(base).getOwnCount());
			dm.setOwnedKind(DataManager.OwnedKind.PAPER);
			assertEquals("paper lists only", 3, dm.getRealCards(base).getOwnCount());
			dm.setOwnedKind(DataManager.OwnedKind.DIGITAL);
			assertEquals("digital lists only", 2, dm.getRealCards(base).getOwnCount());
			dm.setOwnedKind(DataManager.OwnedKind.ALL);
			assertEquals(5, dm.getRealCards(base).getOwnCount());
		} finally {
			dm.getMagicDBStore().remove(base);
		}
	}

	public void testChangingTheLinksDropsTheKindCache() {
		paper = create(false);
		MagicCard base = card("dv-cache", "Digital Cache Test", null, "true");
		base.setSet("Digital Views Test Set");
		base.setCollNumber("2");
		dm.getMagicDBStore().add(base);
		try {
			dm.setOwnedKind(DataManager.OwnedKind.PAPER);
			MagicCardPhysical first = new MagicCardPhysical(base, paper.getLocation());
			first.setCount(1);
			first.setOwn(true);
			dm.add(first);
			assertEquals(1, dm.getRealCards(base).getOwnCount());
			// the list keeps its own copy of what was added
			MagicCardPhysical stored = (MagicCardPhysical) dm.getLibraryCardStore().getCards("dv-cache").iterator().next();
			dm.setField(stored, MagicCardField.COUNT, 5);
			assertEquals("the new count, not the cached one", 5, dm.getRealCards(base).getOwnCount());
		} finally {
			dm.getMagicDBStore().remove(base);
		}
	}

	// ------------------------------------------------- printings have no copy details

	public void testAPrintingHasNoProxyOrConditionWhateverTheUsersCopiesAre() {
		paper = create(false);
		MagicCard base = card("dv-copy", "Copy Details Test", null, null);
		base.setSet("Digital Views Test Set");
		base.setCollNumber("3");
		dm.getMagicDBStore().add(base);
		try {
			MagicCardPhysical copy = new MagicCardPhysical(base, paper.getLocation());
			copy.setCount(1);
			copy.setOwn(true);
			copy.setProxy(true);
			copy.setCondition(CardCondition.values()[0]);
			dm.add(copy);
			// the copy has them ...
			MagicCardPhysical stored = (MagicCardPhysical) dm.getLibraryCardStore().getCards("dv-copy").iterator().next();
			assertEquals(Boolean.TRUE, stored.get(MagicCardField.PROXY));
			assertNotNull(stored.get(MagicCardField.CONDITION));
			// ... the printing does not
			assertNull(base.get(MagicCardField.PROXY));
			assertNull(base.get(MagicCardField.CONDITION));
		} finally {
			dm.getMagicDBStore().remove(base);
		}
	}

	// ------------------------------------------------------ Proxier / Buyer stock

	/** What you own for the Proxier and the Buyer is what sits in paper lists: digital copies cannot be boxed or printed. */
	public void testDeckNeedsCountsOnlyTheCopiesInPaperLists() {
		paper = create(false);
		digital = create(true);
		DigitalSupport.setEnabled(true);
		MagicCard base = card("dv-need", "Need Test", null, "true");
		base.setSet("Digital Views Test Set");
		base.setCollNumber("4");
		dm.getMagicDBStore().add(base);
		CardCollection deck = dm.getModelRoot().getDeckContainer().addDeck("digviews-need" + (++n), true, true);
		try {
			MagicCardPhysical wanted = new MagicCardPhysical(base, deck.getLocation());
			wanted.setCount(2);
			wanted.setOwn(false);
			dm.add(wanted);
			MagicCardPhysical inPaper = new MagicCardPhysical(base, paper.getLocation());
			inPaper.setCount(1);
			inPaper.setOwn(true);
			dm.add(inPaper);
			MagicCardPhysical inDigital = new MagicCardPhysical(base, digital.getLocation());
			inDigital.setCount(3);
			inDigital.setOwn(true);
			dm.add(inDigital);

			for (boolean exact : new boolean[] { true, false }) {
				java.util.List<com.reflexit.magiccards.core.model.nav.DeckNeeds.Row> rows = com.reflexit.magiccards.core.model.nav.DeckNeeds
						.compute(java.util.Collections.singletonList(deck), exact);
				assertEquals(1, rows.size());
				assertEquals("only the paper copy (exact=" + exact + ")", 1, rows.get(0).owned.genuine);
			}
			// whatever Collector has set
			dm.setOwnedKind(DataManager.OwnedKind.DIGITAL);
			assertEquals(1, com.reflexit.magiccards.core.model.nav.DeckNeeds
					.compute(java.util.Collections.singletonList(deck), true).get(0).owned.genuine);
		} finally {
			deck.remove();
			dm.getMagicDBStore().remove(base);
		}
	}

	// ----------------------------------------------------------- value columns

	public void testNoValueForACopyInADigitalListOnlyWhileEnabled() {
		paper = create(false);
		digital = create(true);
		MagicCard base = card("dv-val", "Value Test", null, "true");
		MagicCardPhysical inDigital = new MagicCardPhysical(base, digital.getLocation());
		MagicCardPhysical inPaper = new MagicCardPhysical(base, paper.getLocation());
		DigitalSupport.setEnabled(true);
		assertTrue(DigitalRules.hasNoValue(inDigital));
		assertFalse(DigitalRules.hasNoValue(inPaper));
		assertFalse("a printing in the database has a price", DigitalRules.hasNoValue(base));
		DigitalSupport.setEnabled(false);
		assertFalse(DigitalRules.hasNoValue(inDigital));
	}

	// ----------------------------------------------- turning the cards off

	public void testDigitalListNamesListsEveryDigitalDeckAndCollection() {
		paper = create(false);
		digital = create(true);
		java.util.List<String> names = DigitalRules.digitalListNames();
		assertTrue(names.toString(), names.contains(digital.getName()));
		assertFalse(names.toString(), names.contains(paper.getName()));
		digital.persistDigital(false);
		assertFalse(DigitalRules.digitalListNames().contains(digital.getName()));
	}
}
