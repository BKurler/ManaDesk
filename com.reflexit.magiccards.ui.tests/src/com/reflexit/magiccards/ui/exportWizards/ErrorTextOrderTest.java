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
package com.reflexit.magiccards.ui.exportWizards;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

/** Sorting the import preview by Error: pure logic, plain JUnit (no SWT needed). */
public class ErrorTextOrderTest extends TestCase {
	/** card name -> its error as the Error column shows it (no entry: no error). */
	private final Map<Object, String> errors = new HashMap<>();

	@Override
	protected void setUp() {
		errors.put("Zzz", "Name not found");
		errors.put("Bad Moon", "Mismatched info");
		errors.put("Fog", "Name not found");
		errors.put("Aluren", " "); // blank is no error
	}

	private List<Object> sorted(boolean ascending, Object... cards) {
		List<Object> list = new ArrayList<>(Arrays.asList(cards));
		Collections.sort(list, new ErrorTextOrder(errors::get, ascending));
		return list;
	}

	public void testRowsWithAnErrorComeFirst() {
		List<Object> asc = sorted(true, "Island", "Zzz", "Plains", "Bad Moon", "Aluren", "Fog");
		// by the error text (Mismatched, then the two Name not found, in the file order), then the clean rows in the file order
		assertEquals(Arrays.<Object> asList("Bad Moon", "Zzz", "Fog", "Island", "Plains", "Aluren"), asc);
	}

	public void testDescendingIsTheReverse() {
		List<Object> desc = sorted(false, "Island", "Zzz", "Plains", "Bad Moon", "Fog");
		assertEquals(Arrays.<Object> asList("Island", "Plains", "Zzz", "Fog", "Bad Moon"), desc);
	}

	public void testNoErrorAtAllChangesNothing() {
		errors.clear();
		assertEquals(Arrays.<Object> asList("b", "a"), sorted(true, "b", "a"));
	}

	public void testComparatorIsConsistent() {
		// transitivity / sign symmetry, the contract TimSort checks
		ErrorTextOrder order = new ErrorTextOrder(errors::get, true);
		Object[] all = { "Zzz", "Bad Moon", "Fog", "Aluren", "Island" };
		for (Object a : all)
			for (Object b : all)
				assertEquals(Integer.signum(order.compare(a, b)), -Integer.signum(order.compare(b, a)));
	}
}
