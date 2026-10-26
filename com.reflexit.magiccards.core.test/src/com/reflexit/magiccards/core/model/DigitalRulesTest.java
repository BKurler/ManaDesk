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

import java.util.Arrays;
import java.util.Collections;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.MagicException;
import com.reflexit.magiccards.core.exports.ImportUtils;
import com.reflexit.magiccards.core.model.nav.CardCollection;
import com.reflexit.unittesting.TestFileUtils;

import junit.framework.TestCase;

/**
 * Digital decks and collections: the list flag, {@link DigitalRules} (kind of card vs
 * kind of list, nothing crossing between a paper and a digital list, no Etched /
 * Proxy / Condition in a digital one) and import name lookups matching only the
 * printings fit for the target list. Plain JUnit, with the core test database
 * fixtures like {@code CollectionTypeTest}.
 */
public class DigitalRulesTest extends TestCase {
	private static boolean reset = true;
	private static int n = 0;
	private DataManager dm;
	private CardCollection paper;
	private CardCollection digital;

	private final MagicCard paperCard = card("dr-paper", "Paper Only", null, null);
	private final MagicCard bothCard = card("dr-both", "Both Worlds", null, "true");
	private final MagicCard digitalCard = card("dr-digital", "Arena Only", "true", null);

	@Override
	protected void setUp() throws Exception {
		if (reset) {
			TestFileUtils.resetDb();
			reset = false;
		}
		dm = DataManager.getInstance();
		dm.waitForInit(10);
		dm.getLibraryCardStore();
		paper = create(false);
		digital = create(true);
	}

	@Override
	protected void tearDown() throws Exception {
		DigitalRules.setImportTarget(null);
		paper.remove();
		digital.remove();
		super.tearDown();
	}

	private CardCollection create(boolean isDigital) {
		n++;
		CardCollection cc = new CardCollection("digrules" + n + ".xml", dm.getModelRoot().getCollectionsContainer(),
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

	// ------------------------------------------------------------------- flag

	public void testListFlagPersistsAndDefaultsToPaper() {
		assertFalse(paper.isDigital());
		assertTrue(digital.isDigital());
		digital.close();
		assertTrue("not persisted", digital.isDigital());
		assertTrue(DigitalRules.isDigital(digital.getStore()));
		assertFalse(DigitalRules.isDigital(paper.getStore()));
		assertTrue(DigitalRules.isDigital(digital.getLocation()));
		assertFalse("an unknown list is paper", DigitalRules.isDigital((Location) null));
	}

	// ----------------------------------------------------- kind of card vs list

	public void testKindVeto() {
		// a digital list: digital-only and online-playable cards
		assertNull(DigitalRules.kindVeto(digitalCard, true));
		assertNull(DigitalRules.kindVeto(bothCard, true));
		assertEquals(DigitalRules.NO_PAPER_IN_DIGITAL, DigitalRules.kindVeto(paperCard, true));
		// a paper list: everything but digital-only cards
		assertNull(DigitalRules.kindVeto(paperCard, false));
		assertNull(DigitalRules.kindVeto(bothCard, false));
		assertEquals(DigitalRules.NO_DIGITAL_IN_PAPER, DigitalRules.kindVeto(digitalCard, false));
	}

	public void testAddVetoChecksTheListsKind() {
		assertNull(DigitalRules.addVeto(digitalCard, digital.getStore()));
		assertNull(DigitalRules.addVeto(bothCard, digital.getStore()));
		assertEquals(DigitalRules.NO_PAPER_IN_DIGITAL, DigitalRules.addVeto(paperCard, digital.getStore()));
		assertNull(DigitalRules.addVeto(paperCard, paper.getStore()));
		assertEquals(DigitalRules.NO_DIGITAL_IN_PAPER, DigitalRules.addVeto(digitalCard, paper.getStore()));
	}

	public void testCopyingFromTheDatabaseIsCheckedToo() {
		try {
			dm.copyCards(Collections.<IMagicCard> singletonList(paperCard), digital.getStore());
			fail("a paper-only card in a digital collection");
		} catch (MagicException e) {
			assertEquals(DigitalRules.NO_PAPER_IN_DIGITAL, e.getMessage());
		}
		try {
			dm.copyCards(Collections.<IMagicCard> singletonList(digitalCard), paper.getStore());
			fail("a digital-only card in a paper collection");
		} catch (MagicException e) {
			assertEquals(DigitalRules.NO_DIGITAL_IN_PAPER, e.getMessage());
		}
	}

	/** An import goes through the library store, which holds every list: the kind is the one of the copy's own list. */
	public void testAnImportThroughTheLibraryStoreIsCheckedAgainstItsOwnList() {
		MagicCardPhysical intoDigital = new MagicCardPhysical(digitalCard, digital.getLocation());
		MagicCardPhysical paperInDigital = new MagicCardPhysical(paperCard, digital.getLocation());
		MagicCardPhysical intoPaper = new MagicCardPhysical(digitalCard, paper.getLocation());
		assertNull(DigitalRules.addVeto(intoDigital, dm.getLibraryCardStore()));
		assertEquals(DigitalRules.NO_PAPER_IN_DIGITAL, DigitalRules.addVeto(paperInDigital, dm.getLibraryCardStore()));
		assertEquals(DigitalRules.NO_DIGITAL_IN_PAPER, DigitalRules.addVeto(intoPaper, dm.getLibraryCardStore()));
	}

	/** One card that does not fit never makes the whole import fail: it is left out and the others are added. */
	public void testAnImportLeavesOutTheCardsThatDoNotFitInsteadOfFailing() {
		MagicCardPhysical fits = new MagicCardPhysical(bothCard, digital.getLocation());
		fits.setCount(1);
		MagicCardPhysical doesNot = new MagicCardPhysical(paperCard, digital.getLocation());
		doesNot.setCount(1);
		java.util.List<MagicCardPhysical> imported = new java.util.ArrayList<MagicCardPhysical>(Arrays.asList(fits, doesNot));

		com.reflexit.magiccards.core.exports.ImportUtils.performImport(imported, dm.getLibraryCardStore());

		assertEquals(1, imported.size());
		assertSame(fits, imported.get(0));
		assertEquals(1, digital.getStore().size());
	}

	public void testAnAllowedCopyGoesThrough() {
		assertTrue(dm.copyCards(Collections.<IMagicCard> singletonList(bothCard), digital.getStore()));
		assertEquals(1, digital.getStore().size());
	}

	// ------------------------------------------------- nothing crosses lists

	public void testACopyNeverCrossesBetweenAPaperAndADigitalList() {
		MagicCardPhysical inPaper = new MagicCardPhysical(bothCard, paper.getLocation());
		inPaper.setCount(1);
		dm.add(inPaper);
		assertEquals(DigitalRules.NO_CROSS_KIND, DigitalRules.addVeto(inPaper, digital.getStore()));
		try {
			dm.moveCards(Collections.<IMagicCard> singletonList(inPaper), digital.getStore());
			fail("moved from a paper to a digital list");
		} catch (MagicException e) {
			assertEquals(DigitalRules.NO_CROSS_KIND, e.getMessage());
		}
		assertEquals("still in the paper list", 1, paper.getStore().size());
		assertEquals(0, digital.getStore().size());
	}

	public void testMoveToAnotherListChecksBeforeLeavingTheFirst() {
		MagicCardPhysical inPaper = new MagicCardPhysical(bothCard, paper.getLocation());
		inPaper.setCount(1);
		dm.add(inPaper);
		try {
			dm.move(inPaper, digital.getLocation());
			fail("moved from a paper to a digital list");
		} catch (MagicException e) {
			assertEquals(DigitalRules.NO_CROSS_KIND, e.getMessage());
		}
		assertEquals("the card did not leave its list", 1, paper.getStore().size());
	}

	// ----------------------------------------------- no paper-only details

	public void testNoEtchedProxyOrConditionInADigitalList() {
		MagicCardPhysical etched = new MagicCardPhysical(bothCard, digital.getLocation());
		etched.setFinish(CardFinish.ETCHED);
		assertEquals(DigitalRules.NO_PAPER_DETAILS, DigitalRules.addVeto(etched, digital.getStore()));
		MagicCardPhysical proxy = new MagicCardPhysical(bothCard, digital.getLocation());
		proxy.setProxy(true);
		assertEquals(DigitalRules.NO_PAPER_DETAILS, DigitalRules.addVeto(proxy, digital.getStore()));
		MagicCardPhysical graded = new MagicCardPhysical(bothCard, digital.getLocation());
		graded.setCondition(CardCondition.values()[0]);
		assertEquals(DigitalRules.NO_PAPER_DETAILS, DigitalRules.addVeto(graded, digital.getStore()));
		MagicCardPhysical foil = new MagicCardPhysical(bothCard, digital.getLocation());
		foil.setFinish(CardFinish.FOIL);
		assertNull("foil is fine", DigitalRules.addVeto(foil, digital.getStore()));
	}

	public void testFieldVeto() {
		assertEquals(DigitalRules.NO_PAPER_DETAILS,
				DigitalRules.fieldVeto(digital.getStore(), MagicCardField.FINISH, CardFinish.ETCHED));
		assertEquals(DigitalRules.NO_PAPER_DETAILS,
				DigitalRules.fieldVeto(digital.getStore(), MagicCardField.PROXY, Boolean.TRUE));
		assertNull(DigitalRules.fieldVeto(digital.getStore(), MagicCardField.PROXY, Boolean.FALSE));
		assertNull(DigitalRules.fieldVeto(digital.getStore(), MagicCardField.FINISH, CardFinish.FOIL));
		assertNull("a paper list takes anything",
				DigitalRules.fieldVeto(paper.getStore(), MagicCardField.FINISH, CardFinish.ETCHED));
		assertNull(DigitalRules.fieldVeto(null, MagicCardField.FINISH, CardFinish.ETCHED));
	}

	public void testSettingEtchedOnACopyInADigitalListIsRefused() {
		MagicCardPhysical c = new MagicCardPhysical(bothCard, digital.getLocation());
		c.setCount(1);
		dm.add(c);
		try {
			dm.setField(c, MagicCardField.FINISH, CardFinish.ETCHED);
			fail("etched in a digital list");
		} catch (MagicException e) {
			assertEquals(DigitalRules.NO_PAPER_DETAILS, e.getMessage());
		}
		assertTrue(c.getFinish() != CardFinish.ETCHED);
	}

	// ------------------------------------------------- changing the list's kind

	public void testAListOnlyChangesKindWhenEveryCardFits() {
		MagicCardPhysical both = new MagicCardPhysical(bothCard, paper.getLocation());
		MagicCardPhysical onlyPaper = new MagicCardPhysical(paperCard, paper.getLocation());
		MagicCardPhysical onlyDigital = new MagicCardPhysical(digitalCard, digital.getLocation());
		assertNull("empty", DigitalRules.kindChangeVeto(Collections.emptyList(), true));
		assertNull(DigitalRules.kindChangeVeto(Arrays.asList(both), true));
		assertNotNull(DigitalRules.kindChangeVeto(Arrays.asList(both, onlyPaper), true));
		assertNull(DigitalRules.kindChangeVeto(Arrays.asList(both, onlyPaper), false));
		assertNotNull(DigitalRules.kindChangeVeto(Arrays.asList(both, onlyDigital), false));
		MagicCardPhysical etched = new MagicCardPhysical(bothCard, paper.getLocation());
		etched.setFinish(CardFinish.ETCHED);
		assertNotNull("an etched copy cannot go digital", DigitalRules.kindChangeVeto(Arrays.asList(etched), true));
	}

	// ---------------------------------------------------- import name lookups

	public void testImportLookupOnlyMatchesPrintingsFitForTheTargetList() {
		ImportUtils.LookupHash lookup = new ImportUtils.LookupHash(null);
		MagicCard paperPrinting = card("lk-1", "Same Name", null, null);
		paperPrinting.setSet("Paper Set");
		MagicCard digitalPrinting = card("lk-2", "Same Name", "true", null);
		digitalPrinting.setSet("Digital Set");
		lookup.addToLookup(paperPrinting, "same name");
		lookup.addToLookup(digitalPrinting, "same name");

		assertEquals("no target: both", 2, lookup.getCandidates("Same Name").size());
		DigitalRules.setImportTarget(Boolean.FALSE);
		assertEquals(1, lookup.getCandidates("Same Name").size());
		assertSame(paperPrinting, lookup.getCandidates("Same Name").get(0));
		DigitalRules.setImportTarget(Boolean.TRUE);
		assertEquals(1, lookup.getCandidates("Same Name").size());
		assertSame(digitalPrinting, lookup.getCandidates("Same Name").get(0));
		DigitalRules.setImportTarget(null);
		assertEquals(2, lookup.getCandidates("Same Name").size());
	}

	public void testImportTargetIsPerThreadAndClearable() throws Exception {
		DigitalRules.setImportTarget(Boolean.TRUE);
		assertTrue(DigitalRules.isOutOfImportTarget(paperCard));
		assertFalse(DigitalRules.isOutOfImportTarget(digitalCard));
		final boolean[] other = new boolean[1];
		Thread t = new Thread(() -> other[0] = DigitalRules.isOutOfImportTarget(paperCard));
		t.start();
		t.join();
		assertFalse("another thread has no target", other[0]);
		DigitalRules.setImportTarget(null);
		assertFalse(DigitalRules.isOutOfImportTarget(paperCard));
	}

	public void testFitsList() {
		assertTrue(DigitalRules.fitsList(paperCard, null));
		assertTrue(DigitalRules.fitsList(paperCard, paper.getLocation()));
		assertFalse(DigitalRules.fitsList(paperCard, digital.getLocation()));
		assertTrue(DigitalRules.fitsList(bothCard, digital.getLocation()));
		assertFalse(DigitalRules.fitsList(digitalCard, paper.getLocation()));
	}
}
