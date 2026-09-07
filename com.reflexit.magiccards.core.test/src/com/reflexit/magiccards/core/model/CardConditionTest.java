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

import junit.framework.TestCase;

/**
 * {@link CardCondition} + {@link CardConditions} + the {@link MagicCardField#CONDITION}
 * contract. The XML write path is {@code String.valueOf(condition)} and the read path is
 * {@link CardCondition#resolve}, so {@code toString() <-> resolve()} must round-trip exactly.
 */
public class CardConditionTest extends TestCase {

	public void testFiveGradesBestToWorst() {
		assertEquals(5, CardCondition.values().length);
		assertEquals(CardCondition.NEAR_MINT, CardCondition.values()[0]);
		assertEquals(CardCondition.DAMAGED, CardCondition.values()[4]);
	}

	public void testToStringIsTheAbbreviation() {
		// the canonical serialized form (XML + CSV export) is the acronym
		assertEquals("NM", CardCondition.NEAR_MINT.toString());
		assertEquals("MP", CardCondition.MODERATELY_PLAYED.toString());
		assertEquals("DMG", String.valueOf(CardCondition.DAMAGED));
		// the readable label is still available for the UI
		assertEquals("Near Mint", CardCondition.NEAR_MINT.getLabel());
	}

	public void testResolveRoundTripsEveryGrade() {
		for (CardCondition c : CardCondition.values()) {
			assertSame(c, CardCondition.resolve(c.toString()));   // "NM"  (serialized / read-back path)
			assertSame(c, CardCondition.resolve(c.getAbbr()));    // "NM"
			assertSame(c, CardCondition.resolve(c.getLabel()));   // "Near Mint" (filter checkbox label)
			assertSame(c, CardCondition.resolve(c.name()));       // "NEAR_MINT"
			assertSame(c, CardCondition.resolve(c.name().toLowerCase()));
		}
		assertSame(CardCondition.LIGHTLY_PLAYED, CardCondition.resolve("  lightly played "));
		assertSame(CardCondition.HEAVILY_PLAYED, CardCondition.resolve("HeavilyPlayed"));
	}

	public void testResolveBlankOrUnknownIsNull() {
		assertNull(CardCondition.resolve(null));
		assertNull(CardCondition.resolve(""));
		assertNull(CardCondition.resolve("   "));
		assertNull(CardCondition.resolve("—"));
		assertNull(CardCondition.resolve("Pristine"));
	}

	public void testCompareOrdersBestFirstAndNullLast() {
		assertTrue(CardCondition.compare(CardCondition.NEAR_MINT, CardCondition.DAMAGED) < 0);
		assertTrue(CardCondition.compare(CardCondition.DAMAGED, CardCondition.NEAR_MINT) > 0);
		assertEquals(0, CardCondition.compare(CardCondition.LIGHTLY_PLAYED, CardCondition.LIGHTLY_PLAYED));
		assertTrue("not graded sorts last", CardCondition.compare(CardCondition.DAMAGED, null) < 0);
		assertTrue("not graded sorts last", CardCondition.compare(null, CardCondition.NEAR_MINT) > 0);
		assertEquals(0, CardCondition.compare(null, null));
	}

	public void testSortsAMixedListWithNullsLast() {
		List<CardCondition> l = new ArrayList<CardCondition>(Arrays.asList(
				CardCondition.DAMAGED, null, CardCondition.NEAR_MINT, CardCondition.MODERATELY_PLAYED, null));
		Collections.sort(l, new java.util.Comparator<CardCondition>() {
			public int compare(CardCondition a, CardCondition b) {
				return CardCondition.compare(a, b);
			}
		});
		assertEquals(Arrays.asList(CardCondition.NEAR_MINT, CardCondition.MODERATELY_PLAYED,
				CardCondition.DAMAGED, null, null), l);
	}

	public void testSearchablePropertyHasFiveGradesPlusNotGraded() {
		CardConditions cc = CardConditions.getInstance();
		assertEquals(FilterField.CONDITION, cc.getFilterField());
		assertEquals(6, cc.getIds().size());
		int graded = 0;
		boolean sawNotGraded = false;
		for (String id : cc.getIds()) {
			String label = cc.getNameById(id);
			assertNotNull(label);
			if (CardConditions.NOT_GRADED.equals(label))
				sawNotGraded = true;
			else if (CardCondition.resolve(label) != null)
				graded++;
			else
				fail("unexpected filter label: " + label);
		}
		assertEquals(5, graded);
		assertTrue("the not-graded option must be present", sawNotGraded);
	}

	public void testNotGradedProducesADistinctRealFilterExpr() {
		com.reflexit.magiccards.core.model.expr.Expr notGraded = FilterField.CONDITION
				.valueExpr(CardConditions.NOT_GRADED);
		com.reflexit.magiccards.core.model.expr.Expr nearMint = FilterField.CONDITION.valueExpr("Near Mint");
		com.reflexit.magiccards.core.model.expr.Expr blank = FilterField.CONDITION.valueExpr("");
		assertNotNull(notGraded);
		assertNotNull(nearMint);
		assertNotSame("not-graded is a real expr, not the empty/no-op one", blank, notGraded);
		assertFalse("not-graded filters differently from a specific grade",
				notGraded.toString().equals(nearMint.toString()));
	}

	public void testMagicCardFieldMetadata() {
		assertTrue(MagicCardField.CONDITION.isPhysical());
		assertFalse(MagicCardField.CONDITION.isTransient());
		assertEquals("condition", MagicCardField.CONDITION.getTag());
	}
}
