/*
 * Contributors:
 *     Rémi Dutil (2026) - created: one test per registered Format, so a
 *                         future change to Format.java's static initializer
 *                         (adding/removing/reshaping a format) is caught
 *                         here, not just by manually building a deck in the
 *                         running app
 *     Rémi Dutil (2026) - color identity rule (CommanderFormat.
 *                         validateColorIdentity): fits / outside / colorless
 *                         cards / colorless commander / unknown identity /
 *                         Oathbreaker planeswalker + signature spell /
 *                         owned copies / message listing.
 *     Rémi Dutil (2026) - registered in AllLocalTests; counts built-in formats
 *                         only (other tests add formats on the fly); new
 *                         testFormatCreatedOnTheFlyStaysOutOfTheChain.
 */
package com.reflexit.magiccards.core.legality;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;

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
		// other tests may add formats on the fly (Format.valueOf): count the built-in ones only
		Collection<Format> formats = Format.getFormats();
		int builtIn = 0;
		for (Format f : formats)
			if (f.ordinal() < 100)
				builtIn++;
		assertEquals("built-in formats", ALL_FORMAT_NAMES.length, builtIn);
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

	/** A format Scryfall reports that isn't registered is created on the fly - after
	 *  the built-in ones, never inside the Standard..Vintage legality chain. */
	public void testFormatCreatedOnTheFlyStaysOutOfTheChain() {
		Format f = Format.valueOf("Some Future Format");
		assertTrue("ordinal " + f.ordinal(), f.ordinal() >= 100);
	}

	// --- color identity (Commander family) --------------------------------

	private static MagicCard card(String name, String identity, String type) {
		MagicCard c = new MagicCard();
		c.setName(name);
		c.setType(type);
		if (identity != null)
			c.set(MagicCardField.COLOR_IDENTITY, identity);
		return c;
	}

	private static MagicCard card(String name, String identity) {
		return card(name, identity, "Creature");
	}

	private static List<IMagicCard> list(IMagicCard... cards) {
		return Arrays.asList(cards);
	}

	private static final MagicCard ATRAXA = card("Atraxa", "{W}{U}{B}{G}", "Legendary Creature");
	private static final MagicCard KARN = card("Karn", "{C}", "Legendary Creature");

	public void testIdentityCardsInsideTheCommanderColorsAreLegal() {
		assertNull(CommanderFormat.validateColorIdentity(list(ATRAXA),
				list(card("Swords", "{W}"), card("Counterspell", "{U}"), card("Hybrid", "{W}{B}"))));
	}

	public void testIdentityCardOutsideIsReportedByName() {
		String err = CommanderFormat.validateColorIdentity(list(ATRAXA),
				list(card("Counterspell", "{U}"), card("Lightning Bolt", "{R}")));
		assertNotNull(err);
		assertTrue(err, err.contains("Lightning Bolt is outside"));
		assertTrue(err, err.contains("White, Blue, Black, Green"));
		assertFalse(err, err.contains("Counterspell"));
	}

	public void testIdentityColorlessCardsFitAnyCommander() {
		assertNull(CommanderFormat.validateColorIdentity(list(ATRAXA), list(card("Sol Ring", "{C}"))));
		assertNull(CommanderFormat.validateColorIdentity(list(KARN), list(card("Sol Ring", "{C}"))));
	}

	public void testIdentityColorlessCommanderAllowsOnlyColorless() {
		String err = CommanderFormat.validateColorIdentity(list(KARN), list(card("Forest", "{G}")));
		assertNotNull(err);
		assertTrue(err, err.contains("Forest") && err.contains("(colorless)"));
	}

	public void testIdentityUnknownIsNotChecked() {
		// a card never synced from Scryfall: no identity, not reported
		assertNull(CommanderFormat.validateColorIdentity(list(ATRAXA), list(card("Old Card", null))));
		// no commander identity known: nothing to check against
		assertNull(CommanderFormat.validateColorIdentity(list(card("Mystery", null)), list(card("Bolt", "{R}"))));
		assertNull(CommanderFormat.validateColorIdentity(Collections.<IMagicCard> emptyList(),
				list(card("Bolt", "{R}"))));
	}

	public void testIdentityPartnersUnion() {
		// two commanders in the zone: their colors add up
		assertNull(CommanderFormat.validateColorIdentity(list(card("Red One", "{R}"), card("Blue One", "{U}")),
				list(card("Izzet Spell", "{U}{R}"))));
	}

	public void testIdentityOathbreakerPlaneswalkerSetsTheColors() {
		MagicCard walker = card("Chandra", "{R}", "Legendary Planeswalker - Chandra");
		// a signature spell outside the planeswalker's colors is reported too
		String err = CommanderFormat.validateColorIdentity(list(walker, card("Counterspell", "{U}", "Instant")),
				list(card("Lightning Bolt", "{R}")));
		assertNotNull(err);
		assertTrue(err, err.contains("Counterspell"));
		assertFalse(err, err.contains("Lightning Bolt"));
		// and it doesn't widen the deck's colors
		err = CommanderFormat.validateColorIdentity(list(walker, card("Fire Spell", "{R}", "Instant")),
				list(card("Opt", "{U}")));
		assertNotNull(err);
		assertTrue(err, err.contains("Opt"));
	}

	public void testIdentityOwnedCopiesUseTheirPrintingIdentity() {
		MagicCardPhysical bolt = new MagicCardPhysical(card("Lightning Bolt", "{R}"), Location.NO_WHERE);
		MagicCardPhysical opt = new MagicCardPhysical(card("Opt", "{U}"), Location.NO_WHERE);
		String err = CommanderFormat.validateColorIdentity(list(card("Blue Leader", "{U}")), list(opt, bolt));
		assertNotNull(err);
		assertTrue(err, err.contains("Lightning Bolt") && !err.contains("Opt"));
	}

	public void testIdentityLongListIsShortened() {
		IMagicCard[] reds = new IMagicCard[8];
		for (int i = 0; i < reds.length; i++)
			reds[i] = card("Red " + i, "{R}");
		String err = CommanderFormat.validateColorIdentity(list(card("Blue Leader", "{U}")), list(reds));
		assertNotNull(err);
		assertTrue(err, err.contains("Red 0, Red 1, Red 2, Red 3, Red 4 and 3 more are outside"));
	}

	public void testIdentityCheckListsTheCardsOutside() {
		MagicCard opt = card("Opt", "{U}");
		MagicCard bolt = card("Lightning Bolt", "{R}");
		CommanderFormat.IdentityCheck check = CommanderFormat.checkColorIdentity(list(card("Blue Leader", "{U}")),
				list(opt, bolt));
		assertNotNull(check);
		assertEquals(1, check.outside.size());
		assertSame(bolt, check.outside.get(0)); // the card itself - the Legality tab marks it
		assertEquals("Blue", check.colorNames());
		assertNull(CommanderFormat.checkColorIdentity(list(card("Mystery", null)), list(bolt)));
	}

	public void testEveryCommanderFamilyFormatChecksIdentity() {
		for (String name : new String[] { "Commander", "Duel Commander", "Pauper Commander", "PreDH", "Brawl",
				"Standard Brawl", "Oathbreaker" })
			assertTrue(name, Format.get(name) instanceof CommanderFormat);
	}
}
