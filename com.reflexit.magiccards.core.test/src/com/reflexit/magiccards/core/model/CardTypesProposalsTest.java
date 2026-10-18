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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import junit.framework.TestCase;

/**
 * The type auto-complete list is built from Scryfall's catalogs. Pure logic -
 * runs as a plain JUnit test.
 */
public class CardTypesProposalsTest extends TestCase {

	public void testProposalsCoverEveryGroup() {
		Set<String> p = new HashSet<String>(Arrays.asList(CardTypes.getProposals()));
		assertTrue(p.contains("Legendary")); // supertype
		assertTrue(p.contains("Creature")); // card type
		assertTrue(p.contains("Saga")); // enchantment type
		assertTrue(p.contains("Forest")); // land type
		assertTrue(p.contains("Elf")); // creature type
		assertTrue(p.contains("Jace")); // planeswalker type
		assertTrue(p.contains("Adventure")); // spell type
	}

	public void testNoDuplicates() {
		Set<String> seen = new HashSet<String>();
		for (String s : CardTypes.buildProposals())
			assertTrue("duplicate " + s, seen.add(s.toLowerCase(Locale.ROOT)));
	}

	public void testSupertypesFirst() {
		assertEquals("Basic", CardTypes.buildProposals()[0]);
	}
}
