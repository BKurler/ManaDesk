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
 * What a {@link SortOrder} remembers: the list of sort fields (top one first), their
 * direction, the limit on how many are kept, copying, equality, and the text form that is
 * saved in the preferences. How cards compare is in {@link SortOrderTest}. Pure logic; works
 * as plain JUnit.
 */
public class SortOrderStateTest extends TestCase {
	private final SortOrder order = new SortOrder();

	// ---- a new order

	public void testNewOrderSortsByNameOnly() {
		assertTrue(order.isEmpty());
		assertEquals(2, order.size()); // name, then id: the fixed tie-breakers
		assertTrue(order.isAccending());
		assertTrue(order.isTop(MagicCardField.NAME));
		assertEquals("", order.getStringValue());
		assertEquals("", order.toString());
		assertSame(order, order.getComparator());
	}

	// ---- adding fields

	public void testSetSortFieldPutsTheFieldOnTop() {
		order.setSortField(MagicCardField.COST, false);
		assertFalse(order.isEmpty());
		assertEquals(3, order.size());
		assertTrue(order.isTop(MagicCardField.COST));
		assertFalse(order.isAccending());
		assertTrue(order.hasSortField(MagicCardField.COST));
		assertFalse(order.isAccending(MagicCardField.COST));
		order.setSortField(MagicCardField.RARITY, true);
		assertTrue(order.isTop(MagicCardField.RARITY));
		assertTrue(order.isAccending());
		assertEquals("the field that came first is still there", 4, order.size());
		assertTrue(order.hasSortField(MagicCardField.COST));
	}

	public void testPositionCountsFromTheTop() {
		order.setSortField(MagicCardField.COST, true);
		order.setSortField(MagicCardField.RARITY, true);
		order.setSortField(MagicCardField.TYPE, true);
		assertEquals(1, order.getPosition(MagicCardField.TYPE));
		assertEquals(2, order.getPosition(MagicCardField.RARITY));
		assertEquals(3, order.getPosition(MagicCardField.COST));
		assertEquals(-1, order.getPosition(MagicCardField.POWER));
	}

	public void testFieldsNotInTheOrder() {
		assertNull(order.getComparator(MagicCardField.POWER));
		assertFalse(order.hasSortField(MagicCardField.POWER));
		assertFalse(order.isAccending(MagicCardField.POWER));
		assertFalse(order.isTop(MagicCardField.POWER));
	}

	public void testChoosingAFieldAgainMovesItToTheTopWithTheNewDirection() {
		order.setSortField(MagicCardField.COST, true);
		order.setSortField(MagicCardField.RARITY, true);
		order.setSortField(MagicCardField.COST, false);
		assertEquals(4, order.size());
		assertTrue(order.isTop(MagicCardField.COST));
		assertFalse(order.isAccending());
		assertEquals(2, order.getPosition(MagicCardField.RARITY));
	}

	public void testOnlyTheLastFewFieldsAreKept() {
		MagicCardField[] fields = { MagicCardField.COST, MagicCardField.RARITY, MagicCardField.TYPE, MagicCardField.POWER,
				MagicCardField.TOUGHNESS, MagicCardField.SET, MagicCardField.CMC, MagicCardField.COLOR };
		for (MagicCardField f : fields)
			order.setSortField(f, true);
		// 9 entries at most (2 fixed + 7): the oldest field is dropped
		assertEquals(9, order.size());
		assertFalse("the oldest is gone", order.hasSortField(MagicCardField.COST));
		assertTrue(order.hasSortField(MagicCardField.RARITY));
		assertTrue(order.isTop(MagicCardField.COLOR));
	}

	// ---- clearing and copying

	public void testClearKeepsOnlyTheFixedTieBreakers() {
		order.setSortField(MagicCardField.COST, false);
		order.setSortField(MagicCardField.RARITY, true);
		order.clear();
		assertTrue(order.isEmpty());
		assertEquals(2, order.size());
		assertFalse(order.hasSortField(MagicCardField.COST));
		assertTrue(order.isTop(MagicCardField.NAME));
	}

	public void testSetFromCopiesTheOtherOrder() {
		SortOrder other = new SortOrder();
		other.setSortField(MagicCardField.COST, false);
		other.setSortField(MagicCardField.RARITY, true);
		order.setSortField(MagicCardField.POWER, true);
		order.setFrom(other);
		assertEquals(other, order);
		assertFalse(order.hasSortField(MagicCardField.POWER));
		assertEquals(other.getStringValue(), order.getStringValue());
	}

	// ---- text form

	public void testTextFormListsTheTopFieldFirstWithItsDirection() {
		order.setSortField(MagicCardField.COST, false);
		order.setSortField(MagicCardField.RARITY, true);
		assertEquals("RARITY^/COSTv/", order.getStringValue());
		assertEquals(order.getStringValue(), order.toString());
	}

	public void testTextFormRoundTrips() {
		order.setSortField(MagicCardField.COST, false);
		order.setSortField(MagicCardField.RARITY, true);
		order.setSortField(MagicCardField.TYPE, false);
		SortOrder back = SortOrder.valueOf(order.getStringValue());
		assertEquals(order, back);
		assertEquals(order.getStringValue(), back.getStringValue());
		assertTrue(back.isTop(MagicCardField.TYPE));
		assertFalse(back.isAccending(MagicCardField.TYPE));
		assertTrue(back.isAccending(MagicCardField.RARITY));
		assertFalse(back.isAccending(MagicCardField.COST));
	}

	public void testReadingTheTextForm() {
		assertTrue(SortOrder.valueOf(null).isEmpty());
		assertTrue(SortOrder.valueOf("").isEmpty());
		assertTrue(SortOrder.valueOf("   ").isEmpty());
		assertTrue("empty parts are skipped", SortOrder.valueOf("/ /").isEmpty());
		SortOrder noDirection = SortOrder.valueOf("COST");
		assertTrue("no sign means ascending", noDirection.isAccending(MagicCardField.COST));
		SortOrder spaced = SortOrder.valueOf(" COSTv / RARITY^ ");
		assertFalse(spaced.isAccending(MagicCardField.COST));
		assertTrue(spaced.isAccending(MagicCardField.RARITY));
		assertTrue("the first one in the text is on top", spaced.isTop(MagicCardField.COST));
	}

	public void testReadingAnUnknownFieldFails() {
		try {
			SortOrder.valueOf("NO_SUCH_FIELD^");
			fail();
		} catch (IllegalArgumentException e) {
			// expected
		}
	}

	// ---- equality

	public void testEquality() {
		SortOrder a = new SortOrder();
		SortOrder b = new SortOrder();
		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
		a.setSortField(MagicCardField.COST, true);
		assertFalse(a.equals(b));
		b.setSortField(MagicCardField.COST, true);
		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
		b.setSortField(MagicCardField.COST, false);
		assertFalse("the direction of the top field counts", a.equals(b));
		assertFalse(a.equals(null));
		assertFalse(a.equals("COST^/"));
		assertTrue(a.equals(a));
	}

	// ---- comparing is stable for the same object

	public void testAnObjectEqualsItself() {
		Object o = new Object();
		assertEquals(0, order.compare(o, o));
	}
}
