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
import java.util.List;

import com.reflexit.magiccards.ui.views.instances.InstancesListControl;

import junit.framework.TestCase;

/**
 * {@link InstancesListControl#compareLocation} - the Instances view defaults to
 * being grouped by the collection / deck a copy lives in. A copy with no
 * location must sort last and never throw (that used to NPE and kill the whole
 * sort).
 */
public class InstanceLocationSortTest extends TestCase {

	private static int cmp(String a, String b) {
		return InstancesListControl.compareLocation(a, b);
	}

	public void testAlphabeticalByLocation() {
		assertTrue(cmp("Collections/Main", "Collections/Trade") < 0);
		assertTrue(cmp("Decks/Zoo", "Collections/Main") > 0);
		assertEquals(0, cmp("Decks/Zoo", "Decks/Zoo"));
	}

	public void testNullLocationSortsLast() {
		assertTrue("a known location comes before an unknown one", cmp("Collections/Main", null) < 0);
		assertTrue("an unknown location comes after a known one", cmp(null, "Collections/Main") > 0);
	}

	public void testBothNullAreEqualAndDoNotThrow() {
		assertEquals(0, cmp(null, null));
	}

	public void testSortsAMixedListWithNullsLast() {
		List<String> locs = new ArrayList<>(Arrays.asList("Decks/B", null, "Collections/A", null, "Decks/A"));
		Collections.sort(locs, InstancesListControl::compareLocation);
		assertEquals(Arrays.asList("Collections/A", "Decks/A", "Decks/B", null, null), locs);
	}
}
