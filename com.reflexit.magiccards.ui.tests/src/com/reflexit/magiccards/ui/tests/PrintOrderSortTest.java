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

package com.reflexit.magiccards.ui.tests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import com.reflexit.magiccards.ui.views.printings.PrintingListControl;

import junit.framework.TestCase;

/**
 * {@link PrintingListControl#comparePrintOrder} - the Printings view is sorted
 * oldest print first. Regression: it used to
 * {@code edition.getReleaseDate().compareTo(...)} and threw NPE (killing the
 * whole sort) whenever an edition had no known release date.
 */
public class PrintOrderSortTest extends TestCase {

	@SuppressWarnings("deprecation")
	private static Date d(int year, int month, int day) {
		return new Date(year - 1900, month - 1, day);
	}

	private static int cmp(Date ra, int na, String sa, Date rb, int nb, String sb) {
		return PrintingListControl.comparePrintOrder(ra, na, sa, rb, nb, sb);
	}

	public void testOlderReleaseComesFirst() {
		assertTrue(cmp(d(1993, 8, 1), 1, "LEA", d(2003, 7, 1), 1, "8ED") < 0);
		assertTrue(cmp(d(2020, 1, 1), 1, "X", d(1994, 1, 1), 1, "Y") > 0);
	}

	public void testNullReleaseDateSortsLastNeverThrows() {
		// the actual regression: one side has no release date
		assertTrue("known date must come before unknown", cmp(d(1999, 1, 1), 5, "A", null, 1, "Z") < 0);
		assertTrue("unknown date must come after known", cmp(null, 1, "Z", d(1999, 1, 1), 5, "A") > 0);
	}

	public void testBothNullFallBackToCollectorNumberThenSet() {
		assertTrue(cmp(null, 3, "A", null, 10, "A") < 0);
		assertTrue(cmp(null, 10, "A", null, 3, "A") > 0);
		assertTrue(cmp(null, 7, "AAA", null, 7, "ZZZ") < 0); // same number -> set name
	}

	public void testSameReleaseDateLowerCollectorNumberFirst() {
		Date same = d(2015, 6, 1);
		assertTrue(cmp(same, 12, "S", same, 200, "S") < 0);
		assertTrue(cmp(same, 200, "S", same, 12, "S") > 0);
	}

	public void testSortsAListWithMixedKnownAndUnknownDates() {
		Date[] dates = new Date[] { d(2010, 1, 1), null, d(1995, 1, 1), null };
		List<Integer> idx = new ArrayList<>(Arrays.asList(0, 1, 2, 3));
		Collections.sort(idx, (x, y) -> cmp(dates[x], x, "S" + x, dates[y], y, "S" + y));
		// 1995 (2), then 2010 (0), then the two null-dated by collnum: 1, 3
		assertEquals(Arrays.asList(2, 0, 1, 3), idx);
	}
}
