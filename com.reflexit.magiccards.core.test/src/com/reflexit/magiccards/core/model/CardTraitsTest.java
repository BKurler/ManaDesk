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
import java.util.List;

import junit.framework.TestCase;

/** The Traits list: labels, storage, search by label. Pure logic - runs as a plain JUnit test. */
public class CardTraitsTest extends TestCase {

	private static MagicCard card(String extra, String reserved, String reprint) {
		MagicCard c = new MagicCard();
		c.set(MagicCardField.EXTRA_TRAITS, extra);
		c.set(MagicCardField.RESERVED, reserved);
		c.set(MagicCardField.REPRINT, reprint);
		return c;
	}

	public void testDescribeJoinsOwnFieldFactsThenStoredLabels() {
		assertEquals("Reserved List, Reprint, Showcase, Borderless", CardTraits.describe(card("Showcase|Borderless", "true", "true")));
		assertEquals("Showcase", CardTraits.describe(card("Showcase", null, null)));
		assertNull(CardTraits.describe(card(null, null, null)));
	}

	public void testStorageRoundTrip() {
		List<String> labels = Arrays.asList("Full Art", "Watermark: Selesnya");
		String stored = CardTraits.toStored(labels);
		assertEquals("Full Art|Watermark: Selesnya", stored);
		assertEquals(labels, CardTraits.parseStored(stored));
		assertNull(CardTraits.toStored(Arrays.<String> asList()));
		assertTrue(CardTraits.parseStored(null).isEmpty());
	}

	public void testCanonicalLabelIgnoresCaseAndSpaces() {
		assertEquals("Extended Art", CardTraits.canonicalLabel("extended  ART"));
		assertEquals("Reserved List", CardTraits.canonicalLabel("reserved list"));
		assertEquals("Watermark: Selesnya", CardTraits.canonicalLabel("watermark: selesnya"));
		assertNull(CardTraits.canonicalLabel("flying"));
		assertNull(CardTraits.canonicalLabel("watermark:"));
		assertNull(CardTraits.canonicalLabel(null));
	}

	public void testLabelPatternMatchesWholeLabelsOnly() {
		assertTrue(CardTraits.labelPattern("Full Art").matcher("Reprint, Full Art, Textless").find());
		assertTrue(CardTraits.labelPattern("Full Art").matcher("Full Art").find());
		assertFalse(CardTraits.labelPattern("Art").matcher("Full Art").find());
		assertFalse(CardTraits.labelPattern("Frame").matcher("1993 Frame").find());
	}

	public void testPromoLabels() {
		assertEquals("Prerelease", CardTraits.promoLabel("prerelease"));
		assertEquals("Buy-a-Box", CardTraits.promoLabel("buyabox"));
		assertEquals("Surge Foil", CardTraits.promoLabel("surgefoil"));
		assertEquals("Universes Beyond", CardTraits.promoLabel("universesbeyond"));
		assertEquals("Playtest", CardTraits.promoLabel("playtest"));
	}

	public void testBoosterfunIsNoise() {
		assertNull(CardTraits.promoLabel("boosterfun")); // says nothing the other traits don't say better
		assertNull(CardTraits.promoLabel(null));
		assertNull(CardTraits.promoLabel(""));
	}

	public void testFinalFantasyGamesAndUnknownTypes() {
		assertEquals("Final Fantasy VII", CardTraits.promoLabel("ffvii"));
		assertEquals("Final Fantasy XIV", CardTraits.promoLabel("ffxiv"));
		// a type Scryfall adds later still shows up
		assertEquals("Newthing", CardTraits.promoLabel("newthing"));
	}

	public void testPromoLabelsAreSearchable() {
		assertEquals("Prerelease", CardTraits.canonicalLabel("PRERELEASE"));
		assertEquals("Surge Foil", CardTraits.canonicalLabel("surge foil"));
		assertTrue(CardTraits.suggestions().contains("Playtest"));
	}

	public void testSuggestionsHaveEveryKnownLabelAndTheFirstPrintingSearch() {
		List<String> s = CardTraits.suggestions();
		assertTrue(s.contains("Showcase") && s.contains("Reserved List") && s.contains("Oversized"));
		assertTrue(s.contains("First Printing"));
	}
}
