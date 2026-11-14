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

/** Sorting the import preview by Language: pure logic, plain JUnit (no SWT needed). */
public class DeclaredLanguageOrderTest extends TestCase {
	/** card name -> the language the file gave (only for the "yellow" cards). */
	private final Map<Object, String> declared = new HashMap<>();

	@Override
	protected void setUp() {
		declared.put("Bad Moon", "French");
		declared.put("Black Knight", "French");
		declared.put("Fog", "German");
		declared.put("Aluren", "Italian");
	}

	private List<Object> sorted(boolean ascending, Object... cards) {
		List<Object> list = new ArrayList<>(Arrays.asList(cards));
		Collections.sort(list, new DeclaredLanguageOrder(declared::get, ascending));
		return list;
	}

	public void testYellowCardsComeTogetherFirst() {
		// English cards (not declared) are mixed with the yellow ones in the file order
		List<Object> asc = sorted(true, "Island", "Bad Moon", "Plains", "Fog", "Swamp", "Black Knight", "Aluren");
		// the yellow block first, by the file's language (French, French, German, Italian); the others keep their order
		assertEquals(Arrays.<Object> asList("Bad Moon", "Black Knight", "Fog", "Aluren", "Island", "Plains", "Swamp"),
				asc);
	}

	public void testDescendingPutsThemLast() {
		List<Object> desc = sorted(false, "Island", "Bad Moon", "Plains", "Fog", "Swamp", "Aluren");
		assertEquals(Arrays.<Object> asList("Island", "Plains", "Swamp", "Aluren", "Fog", "Bad Moon"), desc);
	}

	public void testNeitherDeclaredIsEqual() {
		DeclaredLanguageOrder order = new DeclaredLanguageOrder(declared::get, true);
		assertEquals(0, order.compare("Island", "Plains")); // the normal Language order decides
		assertTrue(order.compare("Bad Moon", "Island") < 0);
		assertTrue(order.compare("Island", "Bad Moon") > 0);
		assertEquals(0, order.compare("Bad Moon", "Black Knight")); // same file language
		assertTrue(order.compare("Fog", "Bad Moon") > 0); // German after French
	}

	public void testNoDeclaredLanguageAtAll() {
		declared.clear();
		assertEquals(Arrays.<Object> asList("b", "a"), sorted(true, "b", "a")); // nothing moves
	}
}
