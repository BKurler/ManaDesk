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
package com.reflexit.magiccards.core.legality;

import static org.mockito.Mockito.mock;

import junit.framework.TestCase;

import com.reflexit.magiccards.core.model.CardGroup;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.Legality;
import com.reflexit.magiccards.core.model.LegalityMap;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.storage.MemoryCardStore;
import com.reflexit.magiccards.core.model.utils.CardStoreUtils.CardStats;

/**
 * What a {@link Format} says about cards and decks: the legality of a card, the messages
 * ("Banned for Modern"), the restricted rule, the size limits of the main deck and the
 * sideboard, the checks of a whole deck, and finding a format by name. The deck
 * construction rules of each format are in {@link FormatTest}. Pure logic; works as plain
 * JUnit.
 */
public class FormatValidationTest extends TestCase {

	private static MagicCard card(String name, boolean basicLand, LegalityMap legality) {
		MagicCard c = new MagicCard();
		c.setName(name);
		c.setType(basicLand ? "Basic Land - Forest" : "Instant");
		c.setCost(basicLand ? "" : "{R}");
		c.setProperty(MagicCardField.LEGALITY, legality);
		return c;
	}

	private static LegalityMap legalIn(Format... formats) {
		LegalityMap lm = LegalityMap.EMPTY;
		for (Format f : formats)
			lm = lm.put(f, Legality.LEGAL);
		return lm;
	}

	private static MagicCardPhysical copies(MagicCard base, int count) {
		MagicCardPhysical p = new MagicCardPhysical(base, Location.NO_WHERE);
		p.setCount(count);
		return p;
	}

	private static CardStats stats(int main, int side, int repeats) {
		CardStats s = mock(CardStats.class);
		s.mainCount = main;
		s.sideboardCount = side;
		s.maxRepeats = repeats;
		return s;
	}

	// ---- a card's legality

	public void testLegalityOfACardPerFormat() {
		MagicCard c = card("Bolt", false, legalIn(Format.MODERN).put(Format.STANDARD, Legality.BANNED));
		assertEquals(Legality.LEGAL, Format.MODERN.getLegality(c));
		assertEquals(Legality.BANNED, Format.STANDARD.getLegality(c));
	}

	public void testCardLegalityMessages() {
		MagicCard legal = card("A", false, legalIn(Format.MODERN));
		assertNull(Format.MODERN.validateCardLegality(legal));
		MagicCard banned = card("B", false, LegalityMap.EMPTY.put(Format.MODERN, Legality.BANNED));
		assertEquals("Banned for Modern", Format.MODERN.validateCardLegality(banned));
		MagicCard restricted = card("C", false, LegalityMap.EMPTY.put(Format.VINTAGE, Legality.RESTRICTED));
		assertNull("restricted is allowed", Format.VINTAGE.validateCardLegality(restricted));
		MagicCard notLegal = card("D", false, LegalityMap.EMPTY.put(Format.MODERN, Legality.NOT_LEGAL));
		assertEquals("Not legal for Modern", Format.MODERN.validateCardLegality(notLegal));
	}

	// ---- how many copies of a card

	public void testBasicLandsHaveNoLimit() {
		MagicCardPhysical forests = copies(card("Forest", true, legalIn(Format.VINTAGE)), 40);
		assertNull(Format.VINTAGE.validateCardCount(forests));
	}

	public void testRestrictedCardAllowsOnlyOne() {
		MagicCard base = card("Black Lotus", false, LegalityMap.EMPTY.put(Format.VINTAGE, Legality.RESTRICTED));
		assertNull(Format.VINTAGE.validateCardCount(copies(base, 1)));
		assertEquals("Restricted for Vintage. Only 1 Black Lotus is allowed", Format.VINTAGE.validateCardCount(copies(base, 2)));
	}

	public void testConstructedFormatsAllowFourCopiesOfACard() {
		assertNull(Format.MODERN.validateCardCount(copies(card("Bolt", false, legalIn(Format.MODERN)), 4)));
		assertNull(Format.MODERN.validateCardCount(4));
		assertEquals("Card count is 5 expected <= 4", Format.MODERN.validateCardCount(5));
		assertEquals("Card count is 5 expected <= 4",
				Format.MODERN.validateCardCount(copies(card("Bolt", false, legalIn(Format.MODERN)), 5)));
	}

	public void testAFormatKnownOnlyByNameHasNoCopyLimit() {
		Format other = Format.valueOf("Validation Test Format Two");
		assertNull(other.validateCardCount(99));
	}

	// ---- sizes

	public void testMainDeckSize() {
		assertEquals(60, Format.MODERN.getMainDeckCount());
		assertNull(Format.MODERN.validateDeckCount(60));
		assertNull(Format.MODERN.validateDeckCount(75));
		assertEquals("Deck card count is 59 expected >= 60", Format.MODERN.validateDeckCount(59));
		// a format Scryfall reports that has no rules of its own: 40 cards
		Format other = Format.valueOf("Validation Test Format Three");
		assertEquals(40, other.getMainDeckCount());
		assertNull(other.validateDeckCount(40));
		assertEquals("Deck card count is 39 expected >= 40", other.validateDeckCount(39));
	}

	public void testSideboardSize() {
		assertEquals(15, Format.MODERN.getSideboardCount());
		assertNull(Format.MODERN.validateSideboardCount(0));
		assertNull(Format.MODERN.validateSideboardCount(15));
		assertEquals("Sideboard card count is 16 expected <= 15", Format.MODERN.validateSideboardCount(16));
	}

	// ---- a whole deck

	private static MemoryCardStore<IMagicCard> deck(IMagicCard... cards) {
		MemoryCardStore<IMagicCard> s = new MemoryCardStore<IMagicCard>();
		for (IMagicCard c : cards)
			s.add(c);
		return s;
	}

	public void testNoStatsMeansNothingToCheck() {
		assertNull(Format.MODERN.validateLegality(deck(), null));
	}

	public void testALegalDeckHasNoError() {
		MemoryCardStore<IMagicCard> d = deck(copies(card("Bolt", false, legalIn(Format.MODERN)), 4),
				copies(card("Forest", true, legalIn(Format.MODERN)), 20));
		assertNull(Format.MODERN.validateLegality(d, stats(60, 15, 4)));
	}

	public void testABannedCardIsReportedFirst() {
		MemoryCardStore<IMagicCard> d = deck(copies(card("Bolt", false, legalIn(Format.MODERN)), 4),
				copies(card("Ban Me", false, LegalityMap.EMPTY.put(Format.MODERN, Legality.BANNED)), 1));
		assertEquals("Banned for Modern", Format.MODERN.validateLegality(d, stats(10, 99, 99)));
	}

	public void testRestrictedCopiesInTheStoreAreReported() {
		MemoryCardStore<IMagicCard> d = deck(copies(card("Lotus", false, LegalityMap.EMPTY.put(Format.VINTAGE, Legality.RESTRICTED)), 2));
		assertEquals("Restricted for Vintage. Only 1 Lotus is allowed", Format.VINTAGE.validateLegality(d, stats(60, 0, 2)));
	}

	public void testDeckSizeThenSideboardSizeAreChecked() {
		MemoryCardStore<IMagicCard> d = deck(copies(card("Bolt", false, legalIn(Format.MODERN)), 4));
		assertEquals("Deck card count is 30 expected >= 60", Format.MODERN.validateLegality(d, stats(30, 99, 4)));
		assertEquals("Sideboard card count is 20 expected <= 15", Format.MODERN.validateLegality(d, stats(60, 20, 4)));
	}

	// ---- a card or a group in a list

	public void testLegalAndBannedCardInAList() {
		assertNull(Format.MODERN.validateCardOrGroup(card("A", false, legalIn(Format.MODERN))));
		assertEquals("Banned for Modern",
				Format.MODERN.validateCardOrGroup(card("B", false, LegalityMap.EMPTY.put(Format.MODERN, Legality.BANNED))));
		MagicCardPhysical lotus = copies(card("Lotus", false, LegalityMap.EMPTY.put(Format.VINTAGE, Legality.RESTRICTED)), 3);
		assertEquals("Restricted for Vintage. Only 1 Lotus is allowed", Format.VINTAGE.validateCardOrGroup(lotus));
	}

	public void testGroupOfTheMainDeckIsCheckedForSize() {
		CardGroup main = new CardGroup(MagicCardField.SIDEBOARD, "Main Deck");
		main.add(copies(card("Bolt", false, legalIn(Format.MODERN)), 30));
		assertEquals("Deck card count is 30 expected >= 60", Format.MODERN.validateCardOrGroup(main));
		CardGroup bigEnough = new CardGroup(MagicCardField.SIDEBOARD, "Main Deck");
		bigEnough.add(copies(card("Bolt", false, legalIn(Format.MODERN)), 60));
		assertNull(Format.MODERN.validateCardOrGroup(bigEnough));
	}

	public void testGroupWithABannedCardIsReported() {
		CardGroup group = new CardGroup(MagicCardField.TYPE, "Instant");
		group.add(copies(card("Ban Me", false, LegalityMap.EMPTY.put(Format.MODERN, Legality.BANNED)), 1));
		assertEquals("Banned for Modern", Format.MODERN.validateCardOrGroup(group));
	}

	// ---- finding a format

	public void testFindingAFormatByName() {
		assertSame(Format.MODERN, Format.get("Modern"));
		assertSame(Format.STANDARD, Format.valueOf("Standard"));
		assertNull(Format.get("No Such Format Anywhere"));
		assertSame("Extended was renamed Modern", Format.MODERN, Format.get("Extended"));
	}

	public void testAnUnknownFormatIsCreatedOnceAndRemembered() {
		Format f = Format.valueOf("Validation Test Format");
		assertEquals("Validation Test Format", f.name());
		assertEquals("Validation Test Format", f.toString());
		assertTrue(f.ordinal() >= 100);
		assertSame(f, Format.valueOf("Validation Test Format"));
		assertSame(f, Format.get("Validation Test Format"));
		assertTrue(Format.getFormats().contains(f));
	}

	public void testEqualityIsByNameAndKind() {
		assertEquals(Format.MODERN, Format.get("Modern"));
		assertEquals(Format.MODERN.hashCode(), Format.get("Modern").hashCode());
		assertFalse(Format.MODERN.equals(Format.STANDARD));
		assertFalse(Format.MODERN.equals(null));
		assertFalse(Format.MODERN.equals("Modern"));
	}
}
