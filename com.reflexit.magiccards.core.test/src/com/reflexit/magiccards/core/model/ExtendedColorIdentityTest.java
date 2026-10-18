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
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import junit.framework.TestCase;

/**
 * Extended Color Identity = Scryfall's color identity + the colors the card can fetch.
 * Pure logic - runs as a plain JUnit test.
 */
public class ExtendedColorIdentityTest extends TestCase {

	private static Set<String> fetch(String oracle) {
		return new HashSet<String>(Colors.getFetchableColors(oracle));
	}

	private static Set<String> set(String... tags) {
		return new HashSet<String>(Arrays.asList(tags));
	}

	public void testFetchLand() {
		assertEquals(set("W", "U"), fetch("{T}, Pay 1 life, Sacrifice Flooded Strand: Search your library for a Plains or "
				+ "Island card, put it onto the battlefield, then shuffle."));
	}

	public void testFetchSpellNamingSeveralTypes() {
		assertEquals(set("W", "U", "B", "R"), fetch("Search your library for a Plains, Island, Swamp, or Mountain card, "
				+ "put it onto the battlefield tapped, then shuffle."));
	}

	public void testBasicLandWithNoTypeNamesNoColor() {
		assertTrue(fetch("Search your library for a basic land card, put it onto the battlefield tapped.").isEmpty());
	}

	public void testReminderTextIsIgnored() {
		assertTrue(fetch("Landfall (Search your library for a Plains card.)").isEmpty());
	}

	public void testOnlyTheSearchSentenceCounts() {
		// the Forest is in another sentence than the search
		assertTrue(fetch("Search your library for a creature card. Put a Forest onto the battlefield.").isEmpty());
	}

	public void testMentioningALandTypeIsNotFetching() {
		// Crypt Ghast: taps a Swamp for mana, searches for nothing
		assertTrue(fetch("Extort ({W/B}, pay: ...)\nWhenever you tap a Swamp for mana, add an additional {B}.").isEmpty());
	}

	public void testLandTypeCycling() {
		assertEquals(set("W"), fetch("Plainscycling {2} ({2}, Discard this card: Search your library for a Plains card.)"));
	}

	public void testNullAndEmpty() {
		assertTrue(fetch(null).isEmpty());
		assertTrue(fetch("").isEmpty());
	}

	private static Collection<String> tags(String cost) {
		return Colors.getColorPresense(cost);
	}

	public void testExtendedIdentityAddsFetchedColorsToTheOfficialOnes() {
		String farseek = "Search your library for a Plains, Island, Swamp, or Mountain card, put it onto the "
				+ "battlefield tapped, then shuffle.";
		Collection<String> t = tags(Colors.getExtendedIdentityCost("{G}", farseek));
		assertTrue(t.containsAll(Arrays.asList("W", "U", "B", "R", "G")));
	}

	public void testFetchLandIsNotColorless() {
		String r = Colors.getExtendedIdentityCost("{C}", "Search your library for a Plains or Island card.");
		assertTrue(r, r.contains("{W}") && r.contains("{U}"));
		assertFalse("a fetch land has colors, so it is not colorless any more", r.contains("{C}"));
	}

	public void testColorlessStaysColorless() {
		assertEquals("{C}", Colors.getExtendedIdentityCost("{C}", "{T}: Add {C}."));
	}

	public void testOfficialIdentityIsKeptAsIs() {
		// Crimson Kobolds: cost {0}, no text, but Scryfall says it is red
		assertEquals("{R}", Colors.getExtendedIdentityCost("{R}", ""));
	}

	private static Set<String> reminder(String oracle) {
		return new HashSet<String>(Colors.getReminderTextColors(oracle));
	}

	public void testReminderTextSymbolsCount() {
		// Syndic of Tithes: Extort's hybrid {W/B}
		assertEquals(set("W", "B"), reminder("Extort ({W/B}, pay: ...)"));
		// Trinisphere's example
		assertEquals(set("B"), reminder("...costs {3} to cast. (...a spell that would cost {1}{B} with ...)"));
		// phyrexian, two-brid and half mana count for their color; generic, colorless, snow, tap for none
		assertEquals(set("G"), reminder("(Pay {G/P}, {2}, {C}, {S}, {T}, {X})"));
		assertEquals(set("W"), reminder("({2/W}, {HW})"));
	}

	public void testSymbolsOutsideReminderTextAreNotThisMethodsBusiness() {
		// they are in Scryfall's own identity already
		assertTrue(reminder("{T}: Add {G}.").isEmpty());
	}

	public void testExtendedIdentityIncludesReminderTextColors() {
		String r = Colors.getExtendedIdentityCost("{W}", "Extort ({W/B}, pay: ...)");
		assertTrue(r, r.contains("{W}") && r.contains("{B}"));
		// Trinisphere: colorless officially, black by its reminder text
		assertEquals("{B}", Colors.getExtendedIdentityCost("{C}", "(a spell that would cost {1}{B} ...)"));
	}

	public void testUnknownOfficialIdentityGivesNullSoTheCallerFallsBack() {
		assertNull(Colors.getExtendedIdentityCost(null, "Search your library for a Plains card."));
	}
}
