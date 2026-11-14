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

import junit.framework.TestCase;

/**
 * The comparator of one sort field, for the fields {@link MagicCardComparatorTest} does not
 * reach: card condition, proxy flag, legality, toughness, card color type, missing
 * values, direction, equality and the text form. Pure logic; works as plain JUnit.
 */
public class MagicCardComparatorFieldsTest extends TestCase {

	private static MagicCard card(String name, String type, String cost) {
		MagicCard c = new MagicCard();
		c.setName(name);
		c.setType(type);
		c.setCost(cost);
		return c;
	}

	private static MagicCardPhysical copy(String name) {
		return new MagicCardPhysical(card(name, "Instant", "{R}"), Location.NO_WHERE);
	}

	private static MagicCardComparator up(MagicCardField f) {
		return new MagicCardComparator(f, true);
	}

	private static int sign(int d) {
		return Integer.signum(d);
	}

	// ---- construction, direction, equality, text

	public void testNullFieldIsRefused() {
		try {
			new MagicCardComparator(null, true);
			fail();
		} catch (NullPointerException e) {
			// expected
		}
	}

	public void testDirectionAndReverse() {
		MagicCardComparator c = up(MagicCardField.NAME);
		assertTrue(c.isAccending());
		assertSame(MagicCardField.NAME, c.getField());
		assertEquals("NAME^", c.toString());
		c.reverse();
		assertFalse(c.isAccending());
		assertEquals("NAMEv", c.toString());
		MagicCard a = card("A", "Instant", "{R}");
		MagicCard b = card("B", "Instant", "{R}");
		assertTrue("descending: B first", c.compare(a, b) > 0);
		c.reverse();
		assertTrue("ascending: A first", c.compare(a, b) < 0);
	}

	public void testEqualityIsByFieldOnly() {
		assertEquals(up(MagicCardField.COST), new MagicCardComparator(MagicCardField.COST, false));
		assertEquals(up(MagicCardField.COST).hashCode(), new MagicCardComparator(MagicCardField.COST, false).hashCode());
		assertFalse(up(MagicCardField.COST).equals(up(MagicCardField.NAME)));
		assertFalse(up(MagicCardField.COST).equals(null));
		assertFalse(up(MagicCardField.COST).equals("COST"));
	}

	public void testComparingThingsThatAreNotCardsOrTheSameObject() {
		MagicCardComparator c = up(MagicCardField.NAME);
		Object o = "text";
		assertEquals(0, c.compare(o, o));
		assertEquals("not cards: no opinion", 0, c.compare("a", "b"));
		MagicCard a = card("A", "Instant", "{R}");
		assertEquals(0, c.compare(a, a));
	}

	// ---- missing values go last

	public void testAMissingValueSortsAfterAPresentOne() {
		MagicCard withPower = card("A", "Creature", "{1}");
		withPower.setPower("2");
		MagicCard without = card("B", "Creature", "{1}");
		MagicCardComparator c = up(MagicCardField.POWER);
		assertTrue(c.compare(withPower, without) < 0);
		assertTrue(c.compare(without, withPower) > 0);
		MagicCard also = card("C", "Creature", "{1}");
		assertEquals("two missing values tie", 0, c.compare(without, also));
	}

	// ---- power and toughness

	public void testToughnessIsComparedAsANumber() {
		MagicCard nine = card("A", "Creature", "{1}");
		nine.setToughness("9");
		MagicCard ten = card("B", "Creature", "{1}");
		ten.setToughness("10");
		assertTrue("10 is more than 9, not less as text", up(MagicCardField.TOUGHNESS).compare(nine, ten) < 0);
	}

	// ---- condition

	public void testConditionFromBestToWorst() {
		MagicCardPhysical nm = copy("A");
		nm.setCondition(CardCondition.NEAR_MINT);
		MagicCardPhysical dmg = copy("B");
		dmg.setCondition(CardCondition.DAMAGED);
		MagicCardComparator c = up(MagicCardField.CONDITION);
		assertTrue(c.compare(nm, dmg) < 0);
		assertTrue(c.compare(dmg, nm) > 0);
		MagicCardPhysical nm2 = copy("C");
		nm2.setCondition(CardCondition.NEAR_MINT);
		assertEquals(0, c.compare(nm, nm2));
	}

	public void testAnUngradedCopyGoesAfterGradedOnes() {
		MagicCardPhysical graded = copy("A");
		graded.setCondition(CardCondition.DAMAGED);
		MagicCardPhysical ungraded = copy("B");
		MagicCardComparator c = up(MagicCardField.CONDITION);
		assertTrue(c.compare(graded, ungraded) < 0);
		assertTrue(c.compare(ungraded, graded) > 0);
	}

	// ---- proxy

	public void testGenuineCopiesComeBeforeProxies() {
		MagicCardPhysical genuine = copy("A");
		genuine.setProxy(false);
		MagicCardPhysical proxy = copy("B");
		proxy.setProxy(true);
		MagicCardComparator c = up(MagicCardField.PROXY);
		assertTrue(c.compare(genuine, proxy) < 0);
		assertTrue(c.compare(proxy, genuine) > 0);
		c.reverse();
		assertTrue("descending: proxies first", c.compare(proxy, genuine) < 0);
	}

	public void testACopyWithoutTheFlagIsGenuine() {
		MagicCardPhysical unset = copy("A"); // never flagged: no value at all
		MagicCardPhysical explicitlyGenuine = copy("B");
		explicitlyGenuine.setProxy(false);
		MagicCardPhysical proxy = copy("C");
		proxy.setProxy(true);
		MagicCardComparator c = up(MagicCardField.PROXY);
		assertEquals(0, c.compare(unset, explicitlyGenuine));
		assertTrue(c.compare(unset, proxy) < 0);
		assertTrue(c.compare(proxy, unset) > 0);
	}

	public void testTwoProxiesTie() {
		MagicCardPhysical proxy = copy("A");
		proxy.setProxy(true);
		MagicCardPhysical proxy2 = copy("B");
		proxy2.setProxy(true);
		assertEquals(0, up(MagicCardField.PROXY).compare(proxy, proxy2));
	}

	// ---- legality

	public void testLegalityByTheFirstFormatTheCardIsLegalIn() {
		MagicCard standard = card("A", "Instant", "{R}");
		standard.setProperty(MagicCardField.LEGALITY, LegalityMap.createFromLegal("Standard"));
		MagicCard modern = card("B", "Instant", "{R}");
		modern.setProperty(MagicCardField.LEGALITY, LegalityMap.createFromLegal("Modern"));
		MagicCardComparator c = up(MagicCardField.LEGALITY);
		assertTrue("Standard before Modern", c.compare(standard, modern) < 0);
		assertTrue(c.compare(modern, standard) > 0);
	}

	// ---- card color type

	public void testColorTypeSortsCostlessCardsFirst() {
		MagicCard land = card("Forest", "Basic Land - Forest", "");
		MagicCard spell = card("Bolt", "Instant", "{R}");
		MagicCardComparator c = up(MagicCardField.CTYPE);
		assertTrue("costless (lands) first", c.compare(land, spell) < 0);
		assertTrue(c.compare(spell, land) > 0);
	}

	public void testColorTypeOtherwiseByText() {
		MagicCard mono = card("Bolt", "Instant", "{R}");
		MagicCard multi = card("Bear", "Creature - Bear", "{R}{G}");
		MagicCardComparator c = up(MagicCardField.CTYPE);
		assertTrue("mono before multi", c.compare(mono, multi) < 0);
		assertTrue(c.compare(multi, mono) > 0);
		assertEquals("same color type", 0, c.compare(mono, card("Shock", "Instant", "{G}")));
	}

	// ---- color identity and cost sort the same way for the same input

	public void testColorIdentityIsOrderedAndConsistent() {
		MagicCard red = card("Bolt", "Instant", "{R}");
		MagicCard green = card("Bear", "Creature - Bear", "{G}");
		MagicCardComparator c = up(MagicCardField.COLOR_IDENTITY);
		assertEquals(sign(c.compare(red, green)), -sign(c.compare(green, red)));
		assertEquals(0, c.compare(red, card("Shock", "Instant", "{R}")));
	}

	public void testCostOrderIsAntiSymmetric() {
		MagicCard one = card("A", "Instant", "{R}");
		MagicCard two = card("B", "Instant", "{G}");
		MagicCardComparator c = up(MagicCardField.COST);
		assertEquals(sign(c.compare(one, two)), -sign(c.compare(two, one)));
		assertEquals(0, c.compare(one, card("C", "Sorcery", "{R}")));
	}

	// ---- mana value falls back to type, then cost

	public void testManaValueTiesAreBrokenByColorTypeThenCost() {
		MagicCard land = card("Forest", "Basic Land - Forest", "");
		MagicCard artifact = card("Rock", "Artifact", "");
		MagicCard cheap = card("A", "Instant", "{R}");
		MagicCard cheapGreen = card("B", "Instant", "{G}");
		MagicCardComparator c = up(MagicCardField.CMC);
		assertTrue("cmc 0 before cmc 1", c.compare(land, cheap) < 0);
		assertEquals("same cmc, different color", sign(c.compare(cheap, cheapGreen)), -sign(c.compare(cheapGreen, cheap)));
		assertFalse(c.compare(cheap, cheapGreen) == 0);
		assertTrue(c.compare(land, artifact) != 0 || land.getColorType().equals(artifact.getColorType()));
	}

	// ---- generic fields use the value's own order

	public void testOtherFieldsUseTheNaturalOrderOfTheirValue() {
		MagicCard a = card("Alpha", "Instant", "{R}");
		MagicCard b = card("Beta", "Instant", "{R}");
		assertTrue(up(MagicCardField.NAME).compare(a, b) < 0);
		assertTrue(up(MagicCardField.NAME).compare(b, a) > 0);
	}
}
