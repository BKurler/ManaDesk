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

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.CardCondition;
import com.reflexit.magiccards.core.model.CardFinish;
import com.reflexit.magiccards.core.model.DigitalSupport;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.unittesting.TestFileUtils;

import junit.framework.TestCase;

/**
 * An import never fails because of the kind of its target: {@link ImportUtils#forceKind} makes the
 * resolved cards fit a digital (or paper) list - another printing of the same name, paper-only details
 * dropped - and flags the ones that have no fitting printing. Plain JUnit, with the core test database
 * fixtures like {@code DigitalRulesTest}.
 */
public class ImportForceKindTest extends TestCase {
	private static boolean reset = true;
	private DataManager dm;
	private boolean wasEnabled;
	private final List<MagicCard> added = new ArrayList<MagicCard>();

	@Override
	protected void setUp() throws Exception {
		if (reset) {
			TestFileUtils.resetDb();
			reset = false;
		}
		dm = DataManager.getInstance();
		dm.waitForInit(10);
		// a digital-only printing is only kept in the database while the digital cards are enabled
		wasEnabled = DigitalSupport.isEnabled();
		DigitalSupport.setEnabled(true);
	}

	@Override
	protected void tearDown() throws Exception {
		for (MagicCard c : added)
			dm.getMagicDBStore().remove(c);
		added.clear();
		DigitalSupport.setEnabled(wasEnabled);
		super.tearDown();
	}

	private MagicCard printing(String id, String name, String set, String num, String digitalOnly, String online) {
		MagicCard c = new MagicCard();
		c.setCardId(id);
		c.setName(name);
		c.setSet(set);
		c.setCollNumber(num);
		c.set(MagicCardField.DIGITAL, digitalOnly);
		c.set(MagicCardField.ONLINE, online);
		return c;
	}

	private MagicCard inDb(MagicCard c) {
		dm.getMagicDBStore().add(c);
		added.add(c);
		return c;
	}

	// ------------------------------------------------------------ pickPrinting

	public void testPickPrintingPrefersTheSameSetAndNumber() {
		MagicCard a = printing("pk-a", "Pick", "Set A", "1", null, "true");
		MagicCard b = printing("pk-b", "Pick", "Set B", "7", null, "true");
		MagicCard b2 = printing("pk-b2", "Pick", "Set B", "9", null, "true");
		MagicCard wanted = printing("pk-w", "Pick", "Set B", "9", null, null);
		List<IMagicCard> candidates = Arrays.<IMagicCard> asList(a, b, b2);
		assertSame("same set and number", b2, ImportUtils.pickPrinting(wanted, candidates, true));
		wanted.setCollNumber("5");
		assertSame("same set", b, ImportUtils.pickPrinting(wanted, candidates, true));
		wanted.setSet("Set C");
		assertNull("never a printing of another set", ImportUtils.pickPrinting(wanted, candidates, true));
	}

	public void testPickPrintingOnlyTakesFittingPrintings() {
		MagicCard paperOnly = printing("pf-p", "Fit", "Set A", "1", null, null);
		MagicCard digitalOnly = printing("pf-d", "Fit", "Set A", "2", "true", null);
		List<IMagicCard> candidates = Arrays.<IMagicCard> asList(paperOnly, digitalOnly);
		assertSame(digitalOnly, ImportUtils.pickPrinting(paperOnly, candidates, true));
		assertSame(paperOnly, ImportUtils.pickPrinting(digitalOnly, candidates, false));
		assertNull(ImportUtils.pickPrinting(paperOnly, Arrays.<IMagicCard> asList(paperOnly), true));
		assertNull(ImportUtils.pickPrinting(paperOnly, null, true));
	}

	// -------------------------------------------------------------- forceKind

	public void testIntoADigitalListAPaperOnlyPrintingBecomesTheDigitalOneOfTheSameSet() {
		MagicCard paper = inDb(printing("fk-p", "Force Test Card", "Force Set P", "1", null, null));
		MagicCard digital = inDb(printing("fk-d", "Force Test Card", "Force Set P", "2", "true", null));
		MagicCardPhysical copy = new MagicCardPhysical(paper, null);
		ArrayList<MagicCardPhysical> imported = new ArrayList<MagicCardPhysical>(Arrays.asList(copy));

		ImportUtils.forceKind(imported, true);

		assertSame(digital, copy.getBase());
		assertNull(copy.getError());
	}

	public void testIntoAPaperListADigitalOnlyPrintingBecomesThePaperOneOfTheSameSet() {
		MagicCard paper = inDb(printing("fk2-p", "Force Test Card Two", "Force Set P", "2", null, null));
		MagicCard digital = inDb(printing("fk2-d", "Force Test Card Two", "Force Set P", "3", "true", null));
		MagicCardPhysical copy = new MagicCardPhysical(digital, null);

		ImportUtils.forceKind(new ArrayList<MagicCardPhysical>(Arrays.asList(copy)), false);

		assertSame(paper, copy.getBase());
	}

	public void testACardThatAlreadyFitsIsLeftAlone() {
		MagicCard both = inDb(printing("fk3-b", "Force Test Card Three", "Force Set P", "3", null, "true"));
		MagicCardPhysical copy = new MagicCardPhysical(both, null);

		ImportUtils.forceKind(new ArrayList<MagicCardPhysical>(Arrays.asList(copy)), true);

		assertSame(both, copy.getBase());
		assertNull(copy.getError());
	}

	public void testASetWithoutAFittingPrintingIsAnErrorNotAnotherSet() {
		MagicCard paper = inDb(printing("fk4-p", "Force Test Card Four", "Force Set P", "4", null, null));
		MagicCardPhysical copy = new MagicCardPhysical(paper, null);

		ImportUtils.forceKind(new ArrayList<MagicCardPhysical>(Arrays.asList(copy)), true);

		assertSame("the set has no digital printing", ImportError.SET_NOT_DIGITAL_ERROR, copy.getError());
		assertEquals("the set is the one of the file", "Force Set P", copy.getSet());
		// detached from the database card, so the import drops it (fixErrors) instead of failing when adding it
		assertNotSame(paper, copy.getBase());
		assertNull(dm.getMagicDBStore().getCard(copy.getCardId()));
		assertEquals("fk4-p", paper.getCardId());
	}

	public void testAPaperOnlyCardOfAnotherSetIsNotMovedToAnotherSet() {
		// paper-only in Fourth Edition, online in Magic 2014: the file says Fourth Edition, so the card is refused
		MagicCard fourth = inDb(printing("fk8-4", "Force Test Fog", "Force Fourth", "244", null, null));
		inDb(printing("fk8-m", "Force Test Fog", "Force M14", "171", null, "true"));
		MagicCardPhysical copy = new MagicCardPhysical(fourth, null);

		ImportUtils.forceKind(new ArrayList<MagicCardPhysical>(Arrays.asList(copy)), true);

		assertSame(ImportError.SET_NOT_DIGITAL_ERROR, copy.getError());
		assertEquals("Force Fourth", copy.getSet());
		assertEquals("244", copy.getCollectorId());
	}

	public void testAnUnresolvedCardIsSkipped() {
		MagicCard unknown = printing("fk5-x", "Unknown Card", "Nowhere", "1", null, null);
		MagicCardPhysical copy = new MagicCardPhysical(unknown, null);
		copy.setError(ImportError.NAME_NOT_FOUND_IN_SET_ERROR);

		ImportUtils.forceKind(new ArrayList<MagicCardPhysical>(Arrays.asList(copy)), true);

		assertSame("its own error is kept", ImportError.NAME_NOT_FOUND_IN_SET_ERROR, copy.getError());
	}

	public void testPaperOnlyDetailsAreDroppedForADigitalList() {
		MagicCard both = inDb(printing("fk6-b", "Force Test Card Six", "Force Set P", "6", null, "true"));
		MagicCardPhysical copy = new MagicCardPhysical(both, null);
		copy.setFinish(CardFinish.ETCHED);
		copy.setProxy(true);
		copy.setCondition(CardCondition.values()[0]);

		ImportUtils.forceKind(new ArrayList<MagicCardPhysical>(Arrays.asList(copy)), true);

		assertNull("etched back to automatic", copy.getRawFinish());
		assertFalse(copy.isProxy());
		assertNull(copy.getCondition());
	}

	public void testPaperDetailsAreKeptForAPaperList() {
		MagicCard paper = inDb(printing("fk7-p", "Force Test Card Seven", "Force Set P", "7", null, null));
		MagicCardPhysical copy = new MagicCardPhysical(paper, null);
		copy.setFinish(CardFinish.ETCHED);
		copy.setProxy(true);

		ImportUtils.forceKind(new ArrayList<MagicCardPhysical>(Arrays.asList(copy)), false);

		assertEquals(CardFinish.ETCHED, copy.getRawFinish());
		assertTrue(copy.isProxy());
	}
}
