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
package com.reflexit.magiccards.ui.views.collector;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jface.preference.IPreferenceStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.reflexit.magiccards.core.model.CardFinish;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.preferences.PreferenceConstants;

/**
 * What the Collector's Progress and Progress4 columns say for one printing: yes / partial / no (or
 * "X / Y (Z%)" for Progress4), depending on the copies owned, whether proxies count and whether
 * each finish is its own slot. The printing is given its copies directly (no collection is
 * loaded) and the two Collector settings are set for each test and put back after. JUnit
 * Plug-in Test (the column reads the workbench colors and the UI plug-in preferences).
 */
public class ProgressColumnTest {
	private IPreferenceStore store;
	private boolean wasProxies;
	private boolean wasFinishes;
	private final ProgressColumn progress = new ProgressColumn();
	private final ProgressColumn progress4 = new Progress4Column();

	@Before
	public void remember() {
		store = MagicUIActivator.getDefault().getPreferenceStore();
		wasProxies = store.getBoolean(PreferenceConstants.COLLECTOR_COUNT_PROXIES);
		wasFinishes = store.getBoolean(PreferenceConstants.COLLECTOR_COUNT_FINISHES_SEPARATELY);
		settings(false, false);
	}

	@After
	public void restore() {
		settings(wasProxies, wasFinishes);
	}

	private void settings(boolean proxies, boolean byFinish) {
		store.setValue(PreferenceConstants.COLLECTOR_COUNT_PROXIES, proxies);
		store.setValue(PreferenceConstants.COLLECTOR_COUNT_FINISHES_SEPARATELY, byFinish);
	}

	/** A printing (nonfoil and foil) that owns exactly these copies. */
	private static MagicCard printing(final MagicCardPhysical... copies) {
		return new MagicCard() {
			@Override
			public Collection<MagicCardPhysical> getPhysicalCards() {
				List<MagicCardPhysical> list = new ArrayList<>();
				for (MagicCardPhysical p : copies)
					list.add(p);
				return list;
			}
		};
	}

	private static MagicCardPhysical own(MagicCard c, int count, CardFinish finish, boolean proxy) {
		MagicCardPhysical p = new MagicCardPhysical(c, null);
		p.setOwn(true);
		p.setCount(count);
		p.setFinish(finish);
		p.setProxy(proxy);
		return p;
	}

	private static MagicCard owning(int count, CardFinish finish, boolean proxy) {
		final MagicCard base = new MagicCard();
		return printing(own(base, count, finish, proxy));
	}

	// ---- plain Progress

	@Test
	public void testNothingOwnedIsNo() {
		assertEquals("no", progress.getSizeCountText(printing()));
	}

	@Test
	public void testOwningAGenuineCopyIsYes() {
		assertEquals("yes (1)", progress.getSizeCountText(owning(1, CardFinish.NONFOIL, false)));
		assertEquals("yes (3)", progress.getSizeCountText(owning(3, CardFinish.NONFOIL, false)));
	}

	@Test
	public void testAProxyDoesNotCountUnlessAsked() {
		MagicCard card = owning(1, CardFinish.NONFOIL, true);
		assertEquals("no", progress.getSizeCountText(card));
		settings(true, false);
		assertEquals("yes (1)", progress.getSizeCountText(card));
	}

	@Test
	public void testACopyThatIsNotOwnedIsIgnored() {
		MagicCard base = new MagicCard();
		MagicCardPhysical wanted = own(base, 2, CardFinish.NONFOIL, false);
		wanted.setOwn(false);
		assertEquals("no", progress.getSizeCountText(printing(wanted)));
	}

	@Test
	public void testSeveralCopiesAreAdded() {
		MagicCard base = new MagicCard();
		MagicCard card = printing(own(base, 1, CardFinish.NONFOIL, false), own(base, 2, CardFinish.FOIL, false));
		assertEquals("yes (3)", progress.getSizeCountText(card));
	}

	// ---- finishes as slots

	@Test
	public void testOneFinishOfTwoIsPartialWhenFinishesAreSeparate() {
		settings(false, true);
		assertEquals("partial (1)", progress.getSizeCountText(owning(1, CardFinish.NONFOIL, false)));
	}

	@Test
	public void testBothFinishesIsYesWhenFinishesAreSeparate() {
		settings(false, true);
		MagicCard base = new MagicCard();
		MagicCard card = printing(own(base, 1, CardFinish.NONFOIL, false), own(base, 1, CardFinish.FOIL, false));
		assertEquals("yes (2)", progress.getSizeCountText(card));
	}

	@Test
	public void testNothingOwnedIsStillNoWhenFinishesAreSeparate() {
		settings(false, true);
		assertEquals("no", progress.getSizeCountText(printing()));
	}

	// ---- Progress4: a full playset

	@Test
	public void testProgress4ShowsAFraction() {
		assertEquals("0 / 4 (0%)", progress4.getSizeCountText(printing()));
		assertEquals("2 / 4 (50%)", progress4.getSizeCountText(owning(2, CardFinish.NONFOIL, false)));
		assertEquals("4 / 4 (100%)", progress4.getSizeCountText(owning(4, CardFinish.NONFOIL, false)));
	}

	@Test
	public void testProgress4DoesNotCountMoreThanAPlayset() {
		assertEquals("4 / 4 (100%)", progress4.getSizeCountText(owning(6, CardFinish.NONFOIL, false)));
	}

	@Test
	public void testProgress4WithFinishesWantsAPlaysetOfEach() {
		settings(false, true);
		assertEquals("2 / 8 (25%)", progress4.getSizeCountText(owning(2, CardFinish.NONFOIL, false)));
		MagicCard base = new MagicCard();
		MagicCard card = printing(own(base, 4, CardFinish.NONFOIL, false), own(base, 4, CardFinish.FOIL, false));
		assertEquals("8 / 8 (100%)", progress4.getSizeCountText(card));
	}

	@Test
	public void testProgress4WithFinishesRoundsThePercentDown() {
		settings(false, true);
		assertEquals("1 / 8 (12%)", progress4.getSizeCountText(owning(1, CardFinish.NONFOIL, false)));
	}

	// ---- one copy (a row of a deck or collection)

	@Test
	public void testACopyIsYesOnceItReachesTheTarget() {
		MagicCard base = new MagicCard();
		assertEquals("yes (2)", progress.getSizeCountText(own(base, 2, CardFinish.NONFOIL, false)));
		assertEquals("partial (2)", progress4.getSizeCountText(own(base, 2, CardFinish.NONFOIL, false)));
		assertEquals("yes (4)", progress4.getSizeCountText(own(base, 4, CardFinish.NONFOIL, false)));
	}

	@Test
	public void testACopyThatIsNotOwnedOrEmptyIsNo() {
		MagicCard base = new MagicCard();
		MagicCardPhysical virtual = own(base, 2, CardFinish.NONFOIL, false);
		virtual.setOwn(false);
		assertEquals("no", progress.getSizeCountText(virtual));
		assertEquals("no", progress.getSizeCountText(own(base, 0, CardFinish.NONFOIL, false)));
	}

	@Test
	public void testSomethingElseIsNo() {
		assertEquals("no", progress.getSizeCountText("text"));
		assertEquals("no", progress.getSizeCountText(null));
	}

	// ---- the rest of the column

	@Test
	public void testWhichPercentIsUsedFollowsTheSettings() {
		assertEquals(MagicCardField.PERCENT_COMPLETE_GENUINE, progress.getPercentKey());
		settings(true, false);
		assertEquals(MagicCardField.PERCENT_COMPLETE, progress.getPercentKey());
		settings(false, true);
		assertEquals(MagicCardField.PERCENT_COMPLETE_GENUINE_BY_FINISH, progress.getPercentKey());
		settings(true, true);
		assertEquals(MagicCardField.PERCENT_COMPLETE_BY_FINISH, progress.getPercentKey());
		assertEquals(MagicCardField.PERCENT4_COMPLETE_BY_FINISH, progress4.getPercentKey());
	}

	@Test
	public void testSizesOfSomethingThatIsNotAGroup() {
		MagicCard card = new MagicCard();
		assertEquals(1, progress.getTotal(card));
		assertEquals(0, progress.getProgressSize(card));
		assertEquals(130, progress.getColumnWidth());
	}

	@Test
	public void testTheCellTextOfACardIsItsLeafText() {
		assertEquals("yes (1)", progress.getText(owning(1, CardFinish.NONFOIL, false)));
		assertEquals("1 / 4 (25%)", progress4.getText(owning(1, CardFinish.NONFOIL, false)));
	}
}
