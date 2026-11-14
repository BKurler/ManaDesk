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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

/**
 * What a refresh of a deck's accessories list does, without any store: which token is "the same
 * accessory" ({@code key}) and which entries are added or removed ({@code plan}). Works as plain
 * JUnit.
 */
public class DeckAccessoriesPopulatorTest extends TestCase {

	private static MagicCard token(String name, String power, String toughness) {
		MagicCard c = new MagicCard();
		c.setName(name);
		c.setPower(power);
		c.setToughness(toughness);
		return c;
	}

	private static MagicCardPhysical entry(MagicCard card, int count) {
		MagicCardPhysical p = new MagicCardPhysical(card, Location.valueOf("deck-extra"), true);
		p.setCount(count);
		return p;
	}

	private static Map<String, IMagicCard> needed(MagicCard... cards) {
		Map<String, IMagicCard> m = new LinkedHashMap<>();
		for (MagicCard c : cards)
			m.put(DeckAccessoriesPopulator.key(c), c);
		return m;
	}

	// ---- which token is the same one

	public void testKeyIsNameAndPowerToughness() {
		assertEquals("beast|3/3", DeckAccessoriesPopulator.key(token("Beast", "3", "3")));
	}

	public void testKeyIgnoresCaseAndSpaces() {
		assertEquals(DeckAccessoriesPopulator.key(token("Beast", "3", "3")), DeckAccessoriesPopulator.key(token("  BEAST ", " 3", "3 ")));
	}

	public void testAnotherSizeIsAnotherAccessory() {
		assertFalse(DeckAccessoriesPopulator.key(token("Beast", "3", "3")).equals(DeckAccessoriesPopulator.key(token("Beast", "4", "4"))));
	}

	public void testEmblemsHaveNoPowerOrToughness() {
		assertEquals("emblem|/", DeckAccessoriesPopulator.key(token("Emblem", null, null)));
		assertEquals("|/", DeckAccessoriesPopulator.key(new MagicCard()));
	}

	// ---- adding

	public void testAMissingTokenIsAdded() {
		MagicCard beast = token("Beast", "3", "3");
		DeckAccessoriesPopulator.Plan plan = DeckAccessoriesPopulator.plan(new ArrayList<IMagicCard>(), needed(beast), false);
		assertEquals(Arrays.<IMagicCard> asList(beast), plan.add);
		assertTrue(plan.remove.isEmpty());
	}

	public void testATokenAlreadyListedIsNotAddedAgain() {
		MagicCard beast = token("Beast", "3", "3");
		DeckAccessoriesPopulator.Plan plan = DeckAccessoriesPopulator.plan(
				Arrays.asList(entry(token("Beast", "3", "3"), 0)), needed(beast), false);
		assertTrue(plan.add.isEmpty());
	}

	public void testCopiesOfAnotherPrintingCoverTheNeededToken() {
		// 2 Beasts the user owns cover the needed Beast: no new line
		DeckAccessoriesPopulator.Plan plan = DeckAccessoriesPopulator.plan(
				Arrays.asList(entry(token("Beast", "3", "3"), 2)), needed(token("Beast", "3", "3")), true);
		assertTrue(plan.add.isEmpty());
		assertTrue(plan.remove.isEmpty());
	}

	public void testANeededTokenOfAnotherSizeIsStillAdded() {
		MagicCard big = token("Beast", "4", "4");
		DeckAccessoriesPopulator.Plan plan = DeckAccessoriesPopulator.plan(
				Arrays.asList(entry(token("Beast", "3", "3"), 2)), needed(big), false);
		assertEquals(Arrays.<IMagicCard> asList(big), plan.add);
	}

	public void testSeveralNeededTokensKeepTheirOrder() {
		MagicCard a = token("Zombie", "2", "2");
		MagicCard b = token("Beast", "3", "3");
		DeckAccessoriesPopulator.Plan plan = DeckAccessoriesPopulator.plan(new ArrayList<IMagicCard>(), needed(a, b), false);
		assertEquals(Arrays.<IMagicCard> asList(a, b), plan.add);
	}

	// ---- removing

	public void testPopulateNeverRemoves() {
		DeckAccessoriesPopulator.Plan plan = DeckAccessoriesPopulator.plan(
				Arrays.asList(entry(token("Old", "1", "1"), 0)), needed(), false);
		assertTrue(plan.remove.isEmpty());
	}

	public void testRefreshRemovesACountZeroEntryTheDeckNoLongerNeeds() {
		MagicCardPhysical old = entry(token("Old", "1", "1"), 0);
		DeckAccessoriesPopulator.Plan plan = DeckAccessoriesPopulator.plan(Arrays.asList(old), needed(), true);
		assertEquals(Arrays.<IMagicCard> asList(old), plan.remove);
	}

	public void testAnEntryWithACountIsNeverRemovedWhateverTheDeckNeeds() {
		DeckAccessoriesPopulator.Plan plan = DeckAccessoriesPopulator.plan(
				Arrays.asList(entry(token("Old", "1", "1"), 3)), needed(), true);
		assertTrue(plan.remove.isEmpty());
	}

	public void testACountZeroLineNextToTheCopiesIsRemoved() {
		MagicCardPhysical zero = entry(token("Beast", "3", "3"), 0);
		MagicCardPhysical owned = entry(token("Beast", "3", "3"), 2);
		DeckAccessoriesPopulator.Plan plan = DeckAccessoriesPopulator.plan(Arrays.asList(zero, owned), needed(token("Beast", "3", "3")), true);
		assertEquals(Arrays.<IMagicCard> asList(zero), plan.remove);
		assertTrue(plan.add.isEmpty());
	}

	public void testNeededCountZeroEntryStaysAndIsNotAddedTwice() {
		MagicCardPhysical zero = entry(token("Beast", "3", "3"), 0);
		DeckAccessoriesPopulator.Plan plan = DeckAccessoriesPopulator.plan(Arrays.asList(zero), needed(token("Beast", "3", "3")), true);
		assertTrue(plan.remove.isEmpty());
		assertTrue(plan.add.isEmpty());
	}

	public void testTwoCountZeroLinesOfTheSameTokenKeepOne() {
		MagicCardPhysical first = entry(token("Beast", "3", "3"), 0);
		MagicCardPhysical second = entry(token("Beast", "3", "3"), 0);
		DeckAccessoriesPopulator.Plan plan = DeckAccessoriesPopulator.plan(Arrays.asList(first, second), needed(token("Beast", "3", "3")), true);
		assertEquals("the second line duplicates the first", Arrays.<IMagicCard> asList(second), plan.remove);
	}

	public void testRefreshAddsAndRemovesInOnePass() {
		MagicCardPhysical old = entry(token("Old", "1", "1"), 0);
		MagicCard fresh = token("Fresh", "2", "2");
		DeckAccessoriesPopulator.Plan plan = DeckAccessoriesPopulator.plan(Arrays.asList(old), needed(fresh), true);
		assertEquals(Arrays.<IMagicCard> asList(old), plan.remove);
		assertEquals(Arrays.<IMagicCard> asList(fresh), plan.add);
	}

	// ---- no deck

	public void testNoDeckMeansNothingToDo() {
		assertEquals(0, DeckAccessoriesPopulator.populate(null));
		int[] res = DeckAccessoriesPopulator.refresh(null);
		assertEquals(0, res[0]);
		assertEquals(0, res[1]);
		List<IMagicCard> none = new ArrayList<>();
		assertTrue(DeckAccessoriesPopulator.plan(none, needed(), true).add.isEmpty());
	}
}
