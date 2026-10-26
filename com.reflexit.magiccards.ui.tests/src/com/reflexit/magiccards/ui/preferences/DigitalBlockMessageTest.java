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
package com.reflexit.magiccards.ui.preferences;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

/**
 * The message shown when the digital cards cannot be turned off because digital decks or collections
 * exist: how many, and the first names. Plain JUnit (no display needed).
 */
public class DigitalBlockMessageTest {

	@Test
	public void testOneList() {
		String m = MagicPreferencePage.digitalBlockMessage(Arrays.asList("Arena Deck"));
		assertTrue(m, m.contains("1 digital deck or collection exists"));
		assertTrue(m, m.contains("Arena Deck"));
	}

	@Test
	public void testSeveralListsShowTheFirstFiveNames() {
		String m = MagicPreferencePage
				.digitalBlockMessage(Arrays.asList("a1", "a2", "a3", "a4", "a5", "a6", "a7"));
		assertTrue(m, m.contains("7 digital decks and collections exist"));
		assertTrue(m, m.contains("a5"));
		assertFalse(m, m.contains("a6"));
		assertTrue(m, m.contains("..."));
	}

	@Test
	public void testTellsWhatToDo() {
		String m = MagicPreferencePage.digitalBlockMessage(Arrays.asList("x"));
		assertTrue(m, m.contains("Delete them first"));
	}
}
