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
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class CollectorNumberTest {

	private static List<String> sorted(String... in) {
		List<String> l = new ArrayList<>(Arrays.asList(in));
		l.sort(CollectorNumber.ORDER);
		return l;
	}

	@Test
	public void numericOrderNotLexical() {
		Assert.assertEquals(Arrays.asList("2", "10", "100"), sorted("100", "2", "10"));
	}

	@Test
	public void abradeInStrixhavenMysticalArchive() {
		// the case from the bug report - 37 must be first, not 167
		Assert.assertEquals(Arrays.asList("37", "102", "167"), sorted("167", "37", "102"));
	}

	@Test
	public void suffixLettersBreakTiesAfterTheNumber() {
		Assert.assertEquals(Arrays.asList("10", "10a", "10b", "11"), sorted("11", "10b", "10", "10a"));
	}

	@Test
	public void nonNumericSortAfterNumbered() {
		Assert.assertEquals(Arrays.asList("5", "37", "GN2", "★90"), sorted("★90", "GN2", "37", "5"));
	}

	@Test
	public void nullAndEmptySafe() {
		Assert.assertEquals(0, CollectorNumber.compare(null, ""));
		Assert.assertTrue(CollectorNumber.compare("1", null) < 0);
	}
}
