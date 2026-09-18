/*
 * Contributors:
 *     Rémi Dutil (2026) - created: one test per registered Format, so a
 *                         future change to Format.java's static initializer
 *                         (adding/removing/reshaping a format) is caught
 *                         here, not just by manually building a deck in the
 *                         running app
 */
package com.reflexit.magiccards.core.legality;

import java.util.Collection;

import junit.framework.TestCase;

/**
 * Structural deck-construction rules (main deck size / sideboard size /
 * per-name card count cap) for every {@link Format} registered in
 * {@code Format}'s static initializer.
 */
public class FormatTest extends TestCase {

	// --- registration ---------------------------------------------------

	private static final String[] ALL_FORMAT_NAMES = { "Standard", "Pioneer", "Modern", "Commander", "Legacy",
			"Vintage", "Future", "Historic", "Timeless", "Explorer", "Pauper", "Penny Dreadful", "Alchemy",
			"Old School", "Premodern", "Gladiator", "Duel Commander", "Pauper Commander", "PreDH", "Brawl",
			"Standard Brawl", "Oathbreaker" };

	public void testEveryFormatIsRegistered() {
		Collection<Format> formats = Format.getFormats();
		assertEquals("Format.getFormats() count", ALL_FORMAT_NAMES.length, formats.size());
		for (String name : ALL_FORMAT_NAMES)
			assertNotNull("Format \"" + name + "\" must be registered", Format.get(name));
	}

	/** Standard..Vintage (ordinal 1-6, plus Commander at 4) inherit a
	 *  neighboring format's legality when their own is unknown
	 *  (LegalityMap#completeL()'s ordinal-order chain). Every format
	 *  registered afterwards must sit outside that range - see Format.java's
	 *  own header comment on this. */
	public void testFormatsAddedAfterTheOriginalSixStayOutOfTheSanOrdinalChain() {
		for (Format f : Format.getFormats()) {
			if (f == Format.STANDARD || f == Format.PIONEER || f == Format.MODERN || f == Format.LEGACY
					|| f == Format.VINTAGE || "Commander".equals(f.name()))
				continue;
			assertTrue(f.name() + " must have ordinal >= SAN_ORDINAL, was " + f.ordinal(),
					f.ordinal() >= Format.SAN_ORDINAL);
		}
	}

	// --- plain constructed formats: 60 main min / 15 sideboard max / 4-of max

	public void testStandard() {
		assertPlainConstructed("Standard");
	}

	public void testPioneer() {
		assertPlainConstructed("Pioneer");
	}

	public void testModern() {
		assertPlainConstructed("Modern");
	}

	public void testLegacy() {
		assertPlainConstructed("Legacy");
	}

	public void testVintage() {
		assertPlainConstructed("Vintage");
	}

	public void testFuture() {
		assertPlainConstructed("Future");
	}

	public void testHistoric() {
		assertPlainConstructed("Historic");
	}

	public void testTimeless() {
		assertPlainConstructed("Timeless");
	}

	public void testExplorer() {
		assertPlainConstructed("Explorer");
	}

	public void testPauper() {
		assertPlainConstructed("Pauper");
	}

	public void testPennyDreadful() {
		assertPlainConstructed("Penny Dreadful");
	}

	public void testAlchemy() {
		assertPlainConstructed("Alchemy");
	}

	public void testOldSchool() {
		assertPlainConstructed("Old School");
	}

	public void testPremodern() {
		assertPlainConstructed("Premodern");
	}

	/** 60-card minimum main deck, 15-card maximum sideboard, 4-of maximum per
	 *  name - the plain constructed shape every "vanilla" format above shares.
	 *  Per-card legality (which of these a given card is actually legal in)
	 *  comes entirely from Scryfall's own data (ParseScryFallChecklist#
	 *  BuildLegalities()) - nothing further to test at this structural level. */
	private static void assertPlainConstructed(String name) {
		Format f = Format.get(name);
		assertNotNull("Format \"" + name + "\" must be registered", f);
		assertTrue(name + " must be a ConstructedFormat", f instanceof ConstructedFormat);
		assertEquals(name + ": main deck minimum", 60, f.getMainDeckCount());
		assertEquals(name + ": sideboard maximum", 15, f.getSideboardCount());
		assertNull(name + ": 60 main is legal", f.validateDeckCount(60));
		assertNotNull(name + ": 59 main is not enough", f.validateDeckCount(59));
		assertNull(name + ": 15-card sideboard is legal", f.validateSideboardCount(15));
		assertNotNull(name + ": 16-card sideboard is too many", f.validateSideboardCount(16));
		assertNull(name + ": 4 copies of a card is legal", f.validateCardCount(4));
		assertNotNull(name + ": 5 copies of a card is too many", f.validateCardCount(5));
	}

	// --- Commander family: 99 main + 1 commander-zone card, singleton -------

	public void testCommander() {
		assertCommanderShape("Commander", 99, 1);
	}

	public void testDuelCommander() {
		assertCommanderShape("Duel Commander", 99, 1);
	}

	public void testPauperCommander() {
		assertCommanderShape("Pauper Commander", 99, 1);
	}

	public void testPreDH() {
		assertCommanderShape("PreDH", 99, 1);
	}

	// --- Brawl family: 59 main + 1 commander-zone card = 60 total, singleton

	public void testBrawl() {
		assertCommanderShape("Brawl", 59, 1);
	}

	public void testStandardBrawl() {
		assertCommanderShape("Standard Brawl", 59, 1);
	}

	// --- Oathbreaker: 58 main + 2 commander-zone cards (the Oathbreaker
	// planeswalker and its signature spell), singleton ----------------------

	public void testOathbreaker() {
		assertCommanderShape("Oathbreaker", 58, 2);
	}

	/** Exactly {@code mainCount} main deck cards (not "at least", unlike a
	 *  plain constructed format), exactly {@code zoneCount} cards in the
	 *  commander zone (the deck's sideboard slot), singleton (max 1 copy per
	 *  name - basic lands are separately exempted by
	 *  {@link Format#validateCardCount(com.reflexit.magiccards.core.model.IMagicCard)},
	 *  not tested here since it needs a real card object). */
	private static void assertCommanderShape(String name, int mainCount, int zoneCount) {
		Format f = Format.get(name);
		assertNotNull("Format \"" + name + "\" must be registered", f);
		assertTrue(name + " must be a CommanderFormat", f instanceof CommanderFormat);
		assertEquals(name + ": main deck count", mainCount, f.getMainDeckCount());
		assertEquals(name + ": commander zone (sideboard) count", zoneCount, f.getSideboardCount());
		assertNull(name + ": exactly " + mainCount + " main is legal", f.validateDeckCount(mainCount));
		assertNotNull(name + ": one card short is not legal", f.validateDeckCount(mainCount - 1));
		assertNotNull(name + ": one card over is not legal", f.validateDeckCount(mainCount + 1));
		assertNull(name + ": exactly " + zoneCount + " in the commander zone is legal",
				f.validateSideboardCount(zoneCount));
		assertNotNull(name + ": an empty commander zone is not legal", f.validateSideboardCount(0));
		assertNotNull(name + ": one too many in the commander zone is not legal",
				f.validateSideboardCount(zoneCount + 1));
		assertNull(name + ": singleton - 1 copy is legal", f.validateCardCount(1));
		assertNotNull(name + ": singleton - 2 copies is not legal", f.validateCardCount(2));
	}

	// --- Gladiator: 100-card singleton, no commander, no sideboard ---------

	public void testGladiator() {
		Format f = Format.get("Gladiator");
		assertNotNull("Format \"Gladiator\" must be registered", f);
		assertTrue("Gladiator must be a SingletonFormat", f instanceof SingletonFormat);
		assertEquals("Gladiator: main deck count", 100, f.getMainDeckCount());
		assertEquals("Gladiator: sideboard count", 0, f.getSideboardCount());
		assertNull("Gladiator: exactly 100 main is legal", f.validateDeckCount(100));
		assertNotNull("Gladiator: 99 main is not legal", f.validateDeckCount(99));
		assertNotNull("Gladiator: 101 main is not legal", f.validateDeckCount(101));
		assertNull("Gladiator: an empty sideboard is legal", f.validateSideboardCount(0));
		assertNotNull("Gladiator: any sideboard card is not legal", f.validateSideboardCount(1));
		assertNull("Gladiator: singleton - 1 copy is legal", f.validateCardCount(1));
		assertNotNull("Gladiator: singleton - 2 copies is not legal", f.validateCardCount(2));
	}
}
