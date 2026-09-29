/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk
 *     Rémi Dutil (2026) - testBareLinesWithoutQuantityAreRejectedWhenDb-
 *                         Unavailable(): locks in that a bare (no leading
 *                         count) line is never trusted on shape alone - a
 *                         real run against mtgdecks.net showed some sites'
 *                         card rows have no leading count at all, so bare
 *                         lines are now accepted too, but only once the
 *                         database confirms the name (see DeckTextExtractor
 *                         resolveName's trustShapeIfDbUnavailable param);
 *                         this test's environment has no database, so it
 *                         also verifies the conservative fallback.
 *     Rémi Dutil (2026) - testQuantityLinesWithNonBreakingSpacesStillMatch():
 *                         a real mtgdecks.net capture used non-breaking
 *                         spaces (U+00A0) between the qty/name columns -
 *                         Java's \s does not match those by default, so
 *                         every card row silently failed to match at all
 *                         (not a DB lookup failure, as first assumed) until
 *                         DeckTextExtractor.normalizeSpaces() was added.
 *                         Built with (char) 0x00A0 rather than a " "
 *                         escape inside a string literal - the latter is
 *                         prone to being silently decoded into the actual
 *                         (invisible, easy to mistake for a plain space)
 *                         character by whatever transmits the edit, which is
 *                         exactly what happened the first time this test was
 *                         written.
 *     Rémi Dutil (2026) - updated testExtractsOnlyTheDecklistBlock() and
 *                         added testCleansTrailingJunkAndDropsCategory-
 *                         Headers(): extractDeckSection() no longer returns
 *                         accepted lines verbatim - it rebuilds "<qty>
 *                         <name>" pairs from the already-resolved name (see
 *                         DeckTextExtractor's own header for why: a real run
 *                         showed FreeformImportDelegate treating a raw
 *                         "Basking Broodscale  $0.39" line as one literal,
 *                         unresolvable card name). A "Sideboard (15)"-style
 *                         header is now normalized to the bare word
 *                         "Sideboard"; any other non-matching line (category
 *                         headers like "Artifact [7]", blank lines) is
 *                         dropped instead of passed through.
 *     Rémi Dutil (2026) - testNeutralGapLinesDoNotBreakARun(): a run against
 *                         TCGplayer's deck page found a fourth real-world
 *                         shape - qty and name on separate lines, each row
 *                         followed by a blank line and a standalone price
 *                         line, up to 6 non-matching lines between two real
 *                         card names (far past the old MAX_GAP=2). Blank/
 *                         standalone-price/standalone-quantity lines no
 *                         longer count against the gap budget at all; only a
 *                         genuinely unrecognized line (a category header)
 *                         still does. This test uses same-line qty+name
 *                         matches (trusted by shape alone) rather than
 *                         TCGplayer's actual separate-line shape, since the
 *                         latter needs a live database to recognize a bare
 *                         name line at all (verified against the real site
 *                         instead, not reproducible in this sandboxed suite).
 *     Rémi Dutil (2026) - testCountRecognizedCards*(): countRecognizedCards()
 *                         now returns total/unique (see DeckTextExtractor's
 *                         own header - a real TCGplayer deck reported "30"
 *                         (a bare row count) but actually imported 60+15
 *                         cards). The "bare match needs a preceding
 *                         standalone quantity" dedup fix added alongside it
 *                         (rejecting a stray repeated card-name mention
 *                         elsewhere on a page) is NOT separately testable
 *                         here - it only ever applies to bare matches, which
 *                         this sandboxed suite (no database) already
 *                         rejects unconditionally regardless of a preceding
 *                         quantity; verified against the real TCGplayer page
 *                         instead.
 *     Rémi Dutil (2026) - testMaybeboardSectionIsExcludedFromTheRun(): a real
 *                         deckstats.net capture over-imported - its
 *                         "Maybeboard" section (cards NOT in the deck) uses
 *                         the exact same row shape as Sideboard and bled into
 *                         the same run. Locks in that a Maybeboard/
 *                         Considering/Wishlist header hard-stops the run
 *                         (DeckTextExtractor.isStopSectionHeader), unlike a
 *                         Sideboard header which the run is meant to bridge.
 *     Rémi Dutil (2026) - testLongCategoryTransitionGapIsNowTolerated(): two
 *                         more real captures (Moxfield's "Lands\n(38)\n+ 1
 *                         other", Archidekt's "Ramp\n(CTRL to add
 *                         secondary)\nRemoval\nQty: 4\nPrice: $34.66")
 *                         exceeded the old MAX_GAP=2, silently splitting a
 *                         100-card deck into two separate runs and keeping
 *                         only the larger one. MAX_GAP is now 10 - this
 *                         locks in that a longer multi-line category-
 *                         transition header no longer breaks a run, using
 *                         same-line qty+name matches (DB-independent) on
 *                         both sides. Archidekt's own fix - a QtyPolicy.
 *                         TRAILING convention for its "Name\n[rules text]\n
 *                         Type line\n[qty]" per-card shape, tried alongside
 *                         the existing PRECEDING one, keeping whichever finds
 *                         the larger run - is NOT separately testable here:
 *                         it only ever engages for bare (DB-confirmed)
 *                         matches, which this sandboxed suite (no database)
 *                         can't produce at all. It was instead verified with
 *                         a standalone, algorithmically-identical copy of
 *                         this class (DataManager swapped for a directly-
 *                         settable field) run against the real captured text
 *                         from all four sites fixed this session, each
 *                         checked against the deck's own stated card count:
 *                         TCGplayer 75 total/25 unique (PRECEDING correctly
 *                         still wins there, 25 vs 24 - confirming the known
 *                         stray "Dragon's Rage Channeler" Product Details
 *                         mention does NOT hijack the whole deck's qty
 *                         convention), Archidekt 103 total/91 unique (100
 *                         maindeck + 3 sideboard, Maybeboard's 9 correctly
 *                         excluded, TRAILING correctly wins 91 vs 32),
 *                         Moxfield 100 total/92 unique (previously 62,
 *                         entirely missing its 38-card Lands category), and
 *                         deckstats.net unchanged at 88 total/33 unique
 *                         (confirming the MAX_GAP raise didn't regress the
 *                         Maybeboard hard-stop fix above).
 *     Rémi Dutil (2026) - testMaybeboardSectionNeverOutranksTheRealDeck():
 *                         a real TappedOut.net capture showed
 *                         isStopSectionHeader alone wasn't enough - it only
 *                         stopped a run from bleeding INTO a Maybeboard
 *                         section, but its 34-row Maybeboard section (more
 *                         rows than the real 21-row main+sideboard deck
 *                         above it) simply became the longest run on the
 *                         page in its own right and got picked as "the
 *                         decklist" instead. scan() now breaks out entirely
 *                         - not just closes the current run - the moment it
 *                         reaches a stop-section header. TappedOut's rows
 *                         are same-line "3x Name" (trusted by shape alone,
 *                         same as testEntireTextIsDeckLines), so unlike the
 *                         Archidekt/TCGplayer real-capture checks this one
 *                         IS fully reproducible here without a database -
 *                         verified against the real page text too (76
 *                         total/19 unique, matching the standalone-harness
 *                         run against the actual capture).
 *     Rémi Dutil (2026) - testRealTcgplayerCapture()/testRealArchidektCapture()/
 *                         testRealMoxfieldCapture()/testRealDeckstatsCapture():
 *                         requested - real, permanent regression tests for
 *                         the URLs actually in the user's "Browse Website..."
 *                         favorites list, not just the standalone-harness
 *                         verification used to design the fix. useDatabase()/
 *                         clearDatabase() (reflection into DeckTextExtractor's
 *                         private NORM_NAMES) turned out to make even the
 *                         DB-dependent code paths (bare matches, QtyPolicy.
 *                         TRAILING) fully reproducible in this sandboxed
 *                         suite after all - each test below runs the FULL
 *                         real page text (exactly as BrowseWebsiteDialog
 *                         captured it) through the actual production class,
 *                         asserting the exact total/unique already verified
 *                         against each site's own stated card count. Only 4
 *                         of the user's 7 current favorites are covered here
 *                         - mtgdecks.net (Eldrazi Bloodchief Combo),
 *                         TappedOut (Red Goblin deck) and deckbox.org
 *                         (Power Hungry) are still awaiting a real capture of
 *                         those EXACT pages (earlier real captures used for
 *                         other TappedOut/deckbox decks don't match the
 *                         favorites list's current URLs).
 *     Rémi Dutil (2026) - testTypeWordAloneIsNeverTrustedAsACardName()/
 *                         testRealMtgtop8Capture(): a real mtgtop8.com
 *                         capture over-imported "18 LANDS" as a bogus extra
 *                         card - its category headers render as "&lt;count&gt;
 *                         &lt;TYPE&gt;" with no separating punctuation, and
 *                         "LANDS" apparently collided with something in the
 *                         real database. Both tests deliberately put "Lands"
 *                         (and friends) IN the fake database, reproducing
 *                         that exact collision, to prove DeckTextExtractor's
 *                         new TYPE_WORDS denylist wins regardless.
 *     Rémi Dutil (2026) - testRealDeckboxCapture(): a real deckbox.org
 *                         capture found ZERO cards on a page whose 83 rows
 *                         all individually matched fine - the site's own
 *                         sidebar nav ("MTG COLLECTION: Inventory /
 *                         Tradelist / Wishlist") hit scan()'s
 *                         isStopSectionHeader hard-break before the scan
 *                         ever reached the real decklist further down the
 *                         page. Locks in the fix: that hard-break now only
 *                         engages once a real run has actually been found at
 *                         least once.
 *     Rémi Dutil (2026) - testRealDeckboxCommanderGoesToSideboard(): same
 *                         real capture as testRealDeckboxCapture() - locks
 *                         in that the deck's own "Commander" section (a
 *                         separate bug from the Wishlist one above) wraps
 *                         Prossh, Skyraider of Kher in a one-shot Sideboard/
 *                         Deck toggle in extractDeckSection()'s output
 *                         (which FreeformImportDelegate reads to actually
 *                         place the card - see DeckTextExtractor's own
 *                         header), by MTG convention, without sweeping the
 *                         real 99-card maindeck that follows into the
 *                         sideboard too.
 *     Rémi Dutil (2026) - testRealArchidektCommanderGoesToSideboard()/
 *                         testRealMoxfieldCommanderGoesToSideboard(): the
 *                         deckbox.org-only Commander fix above turned out
 *                         too narrow - Archidekt and Moxfield each put a
 *                         real site's own per-card metadata lines between
 *                         their own "Commander" header and the actual
 *                         match, which the single-line check missed
 *                         entirely (see DeckTextExtractor's own header for
 *                         the fix, startsWithCommanderHeader()). Locks in
 *                         that all three independently-designed real site
 *                         layouts now correctly wrap their own commander.
 *                         ARCHIDEKT_DB/MOXFIELD_DB factored out of
 *                         testRealArchidektCapture()/testRealMoxfieldCapture()
 *                         so these new tests (which need the same full real
 *                         DB to reach MIN_MATCHES) don't triple the
 *                         duplication.
 *******************************************************************************/
package com.reflexit.magiccards.core.exports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import org.junit.After;
import org.junit.Test;

public class DeckTextExtractorTest {
	/** Injects a fake card database directly into DeckTextExtractor's private
	 *  NORM_NAMES cache (via reflection, using the class' own private norm()
	 *  method for normalization) - this sandboxed suite has no live
	 *  DataManager/OSGi database, which previously meant every bare-match/
	 *  QtyPolicy.TRAILING code path (Archidekt's shape, and any real page
	 *  whose rows aren't same-line "qty name") was documented as "verified
	 *  against the real site instead, not reproducible here". This closes
	 *  that gap - {@link #testRealTcgplayerCapture}/{@link
	 *  #testRealArchidektCapture}/{@link #testRealMoxfieldCapture}/{@link
	 *  #testRealDeckstatsCapture} below run the FULL real page text (as
	 *  actually captured by BrowseWebsiteDialog from the user's own current
	 *  "Browse Website..." favorites) through the real production class, not
	 *  a synthetic approximation. {@link #clearDatabase} resets this after
	 *  every test so the fake DB never leaks into an unrelated test that
	 *  expects the "no database" fallback (e.g. {@link
	 *  #testBareLinesWithoutQuantityAreRejectedWhenDbUnavailable}). */
	private static void useDatabase(String... realCardNames) throws Exception {
		Method normMethod = DeckTextExtractor.class.getDeclaredMethod("norm", String.class);
		normMethod.setAccessible(true);
		Set<String> db = new HashSet<>();
		for (String name : realCardNames)
			db.add((String) normMethod.invoke(null, name));
		Field normNamesField = DeckTextExtractor.class.getDeclaredField("NORM_NAMES");
		normNamesField.setAccessible(true);
		normNamesField.set(null, db);
	}

	@After
	public void clearDatabase() throws Exception {
		Field normNamesField = DeckTextExtractor.class.getDeclaredField("NORM_NAMES");
		normNamesField.setAccessible(true);
		normNamesField.set(null, null);
	}

	// modeled on a typical deck-site page's document.body.innerText: nav/ads
	// before, the decklist itself as "<qty> <name>" lines (with a "Sideboard"
	// header and a blank line in between), then comments/related-decks noise
	// after - none of which is itself a "<qty> <name>" line.
	private static final String PAGE = "MTG Decks\n" //
			+ "Standard  Pioneer  Modern  Legacy  Commander\n" //
			+ "Sign in   Register\n" //
			+ "Home > Modern > Eldrazi Bloodchief Combo\n" //
			+ "Eldrazi Bloodchief Combo\n" //
			+ "by stefansson30952\n" //
			+ "Format: Modern    Last updated: 3 days ago\n" //
			+ "Buy this deck on TCGPlayer for $342.19\n" //
			+ "Creatures (8)\n" //
			+ "4 Simian Spirit Guide\n" //
			+ "4 Elvish Reclaimer\n" //
			+ "Spells (28)\n" //
			+ "4 Ancient Stirrings\n" //
			+ "4 Chromatic Star\n" //
			+ "4 Expedition Map\n" //
			+ "1 Nissa, Who Shakes the World\n" //
			+ "\n" //
			+ "Sideboard (15)\n" //
			+ "2 Damping Sphere\n" //
			+ "3 Grafdigger's Cage\n" //
			+ "\n" //
			+ "Comments (12)\n" //
			+ "Great list, been running something similar for months.\n" //
			+ "Related decks\n" //
			+ "Eldrazi Tron by anotheruser - Modern\n" //
			+ "Copyright 2026 mtgdecks.net - Privacy Policy - Contact\n";

	@Test
	public void testExtractsOnlyTheDecklistBlock() {
		String result = DeckTextExtractor.extractDeckSection(PAGE);
		assertTrue("expected a result", result != null);
		assertTrue("should not include the site nav", !result.contains("Sign in"));
		assertTrue("should not include the footer", !result.contains("Copyright"));
		assertTrue("should not include the comments", !result.contains("Great list"));
		assertTrue("should include the main deck", result.contains("4 Simian Spirit Guide"));
		assertTrue("the sideboard header should be normalized to the bare word",
				result.contains("Sideboard") && !result.contains("Sideboard (15)"));
		assertTrue("should include the sideboard", result.contains("2 Damping Sphere"));
	}

	@Test
	public void testPlainProseReturnsNull() {
		String prose = "This is just a regular paragraph of text.\n"
				+ "It has several sentences, none of which look like a decklist.\n"
				+ "4 out of 5 stars is a common rating, but this line alone should not be enough.\n";
		assertNull(DeckTextExtractor.extractDeckSection(prose));
	}

	@Test
	public void testEmptyOrNullInput() {
		assertNull(DeckTextExtractor.extractDeckSection(null));
		assertNull(DeckTextExtractor.extractDeckSection(""));
	}

	@Test
	public void testBelowMinimumMatchesFallsBackToNull() {
		// only 3 deck-shaped lines, buried in noise - too few to trust
		String text = "Some header\n" //
				+ "1 Plains\n" //
				+ "1 Island\n" //
				+ "1 Swamp\n" //
				+ "Some footer\n";
		assertNull(DeckTextExtractor.extractDeckSection(text));
	}

	@Test
	public void testLooksLikeDeckLineShapes() {
		assertTrue(DeckTextExtractor.looksLikeDeckLine("4 Lightning Bolt"));
		assertTrue(DeckTextExtractor.looksLikeDeckLine("4x Lightning Bolt"));
		assertTrue(DeckTextExtractor.looksLikeDeckLine("x4 Lightning Bolt"));
		assertTrue(DeckTextExtractor.looksLikeDeckLine("x 4 Lightning Bolt"));
		assertTrue(!DeckTextExtractor.looksLikeDeckLine("Lightning Bolt"));
		assertTrue(!DeckTextExtractor.looksLikeDeckLine("Sideboard"));
		assertTrue(!DeckTextExtractor.looksLikeDeckLine(""));
	}

	@Test
	public void testEntireTextIsDeckLines() {
		String text = "4 Lightning Bolt\n3 Counterspell\n2 Brainstorm\n1 Ponder\n4 Island\n4 Mountain\n";
		String result = DeckTextExtractor.extractDeckSection(text);
		assertEquals(text.trim(), result);
	}

	@Test
	public void testBareLinesWithoutQuantityAreRejectedWhenDbUnavailable() {
		// no leading count on any line - without a live database to confirm
		// these are real card names, bare lines must not be trusted on shape
		// alone (unlike qty-prefixed lines, which testEntireTextIsDeckLines
		// already covers)
		String text = "Lightning Bolt\nCounterspell\nBrainstorm\nPonder\nIsland\nMountain\n";
		assertNull(DeckTextExtractor.extractDeckSection(text));
	}

	@Test
	public void testQuantityLinesWithNonBreakingSpacesStillMatch() {
		// verbatim shape of a real mtgdecks.net capture: non-breaking spaces
		// between the qty and the name
		String nbsp = String.valueOf((char) 0x00A0);
		String text = "4" + nbsp + nbsp + "Basking Broodscale\n" //
				+ "2" + nbsp + nbsp + "Devourer of Destiny\n" //
				+ "4" + nbsp + nbsp + "Emrakul, the Promised End\n" //
				+ "1" + nbsp + nbsp + "Haywire Mite\n" //
				+ "1" + nbsp + nbsp + "Sire of Seven Deaths\n" //
				+ "4" + nbsp + nbsp + "Sowing Mycospawn\n" //
				+ "1" + nbsp + nbsp + "Thought-Knot Seer\n";
		for (String line : text.split("\n"))
			assertTrue("expected a match (shape-only, DB unavailable here): '" + line + "'",
					DeckTextExtractor.looksLikeDeckLine(line));
		// the non-breaking spaces are consumed by the qty/name separator and
		// never appear in the output, regardless of how many there were
		String expected = "4 Basking Broodscale\n2 Devourer of Destiny\n4 Emrakul, the Promised End\n"
				+ "1 Haywire Mite\n1 Sire of Seven Deaths\n4 Sowing Mycospawn\n1 Thought-Knot Seer";
		assertEquals(expected, DeckTextExtractor.extractDeckSection(text));
	}

	@Test
	public void testCleansTrailingJunkAndDropsCategoryHeaders() {
		// without a real database this test can't verify actual price
		// stripping (that only engages once DeckTextExtractor's DB-backed
		// resolveName() can confirm a trimmed name is real - verified live
		// against mtgdecks.net instead), but it locks in that category
		// headers ("Artifact [7]") are dropped from the output rather than
		// passed through as bogus 1x cards, while a "Sideboard"-style header
		// is kept, normalized to the bare word.
		String text = "Creature [17]\n" //
				+ "4 Basking Broodscale\n" //
				+ "2 Devourer of Destiny\n" //
				+ "4 Emrakul, the Promised End\n" //
				+ "1 Haywire Mite\n" //
				+ "Artifact [7]\n" //
				+ "1 Springleaf Drum\n" //
				+ "1 Soul-Guide Lantern\n" //
				+ "Sideboard [15]\n" //
				+ "2 Trinisphere\n" //
				+ "2 Disruptor Flute\n";
		String result = DeckTextExtractor.extractDeckSection(text);
		assertTrue("expected a result", result != null);
		assertTrue("category headers should be dropped", !result.contains("Creature [17]"));
		assertTrue("category headers should be dropped", !result.contains("Artifact [7]"));
		assertTrue("sideboard header should be normalized to the bare word",
				result.contains("Sideboard") && !result.contains("Sideboard [15]"));
		assertTrue(result.contains("4 Basking Broodscale"));
		assertTrue(result.contains("2 Trinisphere"));
	}

	@Test
	public void testNeutralGapLinesDoNotBreakARun() {
		// modeled on TCGplayer's page shape: each card row is followed by a
		// blank line and a standalone price line, with a category header
		// between sections - none of that should break the run.
		String text = "4 Lightning Bolt\n" //
				+ "\n" //
				+ "$0.77\n" //
				+ "\n" //
				+ "4 Preordain\n" //
				+ "\n" //
				+ "$0.71\n" //
				+ "\n" //
				+ "Instant (14)\n" //
				+ "\n" //
				+ "4 Mutagenic Growth\n" //
				+ "\n" //
				+ "$2.32\n" //
				+ "\n" //
				+ "2 Violent Urge\n" //
				+ "\n" //
				+ "$0.35\n" //
				+ "\n" //
				+ "4 Mishra's Bauble\n" //
				+ "\n" //
				+ "$3.05\n" //
				+ "\n" //
				+ "4 Cori-Steel Cutter\n";
		String result = DeckTextExtractor.extractDeckSection(text);
		assertTrue("expected a result", result != null);
		assertTrue("category header should be dropped", !result.contains("Instant (14)"));
		assertTrue("standalone prices should be dropped", !result.contains("$"));
		assertTrue(result.contains("4 Lightning Bolt"));
		assertTrue(result.contains("4 Cori-Steel Cutter"));
	}

	@Test
	public void testCountRecognizedCardsReturnsTotalAndUnique() {
		String text = "4 Lightning Bolt\n3 Counterspell\n2 Brainstorm\n1 Ponder\n4 Island\n4 Mountain\n";
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals(6, count.unique);
		assertEquals(4 + 3 + 2 + 1 + 4 + 4, count.total);
	}

	@Test
	public void testCountRecognizedCardsCountsDistinctNamesNotRows() {
		// "Lightning Bolt" appears as two separate rows (maindeck + sideboard)
		// - unique counts distinct NAMES (5), not rows (6); total still sums both
		String text = "4 Lightning Bolt\n3 Counterspell\n2 Brainstorm\n1 Ponder\n4 Island\nSideboard\n2 Lightning Bolt\n";
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals(5, count.unique);
		assertEquals(4 + 3 + 2 + 1 + 4 + 2, count.total);
	}

	@Test
	public void testCountRecognizedCardsBelowThresholdStillReportsWhatItFound() {
		// countRecognizedCards() does not gate on MIN_MATCHES itself - the
		// caller (BrowseWebsiteDialog) decides how to phrase "not enough yet"
		String text = "Some header\n1 Plains\n1 Island\n1 Swamp\nSome footer\n";
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals(3, count.unique);
		assertEquals(3, count.total);
	}

	@Test
	public void testCountRecognizedCardsEmptyInput() {
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards("");
		assertEquals(0, count.unique);
		assertEquals(0, count.total);
	}

	@Test
	public void testMaybeboardSectionIsExcludedFromTheRun() {
		// modeled on deckstats.net's real shape: Maybeboard immediately
		// follows Sideboard, same "qty name" rows, same kind of header in
		// between - only Maybeboard's cards must NOT be counted as part of
		// the deck.
		String text = "4 Lightning Bolt\n" //
				+ "3 Counterspell\n" //
				+ "2 Brainstorm\n" //
				+ "1 Ponder\n" //
				+ "4 Island\n" //
				+ "4 Mountain\n" //
				+ "Sideboard (2)\n" //
				+ "2 Damping Sphere\n" //
				+ "3 Grafdigger's Cage\n" //
				+ "Maybeboard (2)\n" //
				+ "4 Chalice of the Void\n" //
				+ "1 Karn, the Great Creator\n";
		String result = DeckTextExtractor.extractDeckSection(text);
		assertTrue("expected a result", result != null);
		assertTrue("maindeck should be included", result.contains("4 Lightning Bolt"));
		assertTrue("sideboard should be included", result.contains("2 Damping Sphere"));
		assertTrue("maybeboard cards must be excluded", !result.contains("Chalice of the Void"));
		assertTrue("maybeboard cards must be excluded", !result.contains("Karn, the Great Creator"));
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals(8, count.unique);
		assertEquals(4 + 3 + 2 + 1 + 4 + 4 + 2 + 3, count.total);
	}

	@Test
	public void testLongCategoryTransitionGapIsNowTolerated() {
		// modeled on Moxfield's real shape: a category transition can carry
		// several non-neutral lines ("Lands", "(38)", "+ 1 other") - the old
		// MAX_GAP=2 broke the run right there, silently discarding everything
		// before it since only the longer remaining piece survives.
		String text = "4 Lightning Bolt\n" //
				+ "3 Counterspell\n" //
				+ "2 Brainstorm\n" //
				+ "1 Ponder\n" //
				+ "4 Mountain\n" //
				+ "Lands\n" //
				+ "(38)\n" //
				+ "+ 1 other\n" //
				+ "4 Island\n" //
				+ "4 Forest\n";
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals("the whole deck should be one run, not split at the category transition", 7, count.unique);
		assertEquals(4 + 3 + 2 + 1 + 4 + 4 + 4, count.total);
	}

	@Test
	public void testMaybeboardSectionNeverOutranksTheRealDeck() {
		// modeled on TappedOut's real shape: a real 6-row deck above an even
		// BIGGER 8-row Maybeboard - before this fix, the longer Maybeboard
		// run would simply be the longest run on the page and get returned
		// as "the decklist" instead of the real, shorter one above it.
		String text = "4 Lightning Bolt\n" //
				+ "3 Counterspell\n" //
				+ "2 Brainstorm\n" //
				+ "1 Ponder\n" //
				+ "4 Island\n" //
				+ "4 Mountain\n" //
				+ "Maybeboard\n" //
				+ "1 Card One\n" //
				+ "1 Card Two\n" //
				+ "1 Card Three\n" //
				+ "1 Card Four\n" //
				+ "1 Card Five\n" //
				+ "1 Card Six\n" //
				+ "1 Card Seven\n" //
				+ "1 Card Eight\n";
		String result = DeckTextExtractor.extractDeckSection(text);
		assertTrue("expected a result", result != null);
		assertTrue("the real (shorter) deck should be picked", result.contains("4 Lightning Bolt"));
		assertTrue("the real (shorter) deck should be picked", result.contains("4 Mountain"));
		assertTrue("maybeboard cards must never win over the real deck", !result.contains("Card One"));
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals(6, count.unique);
		assertEquals(4 + 3 + 2 + 1 + 4 + 4, count.total);
	}

	@Test
	public void testTypeWordAloneIsNeverTrustedAsACardName() throws Exception {
		// modeled on mtgtop8.com's real shape: a category header rendered as
		// "<count> <TYPE>" with no separating punctuation, right next to
		// genuine "<qty> <name>" rows - "Lands" is deliberately IN the
		// database here (reproducing the real collision) to prove the
		// denylist wins regardless of what the database says.
		useDatabase("Lightning Bolt", "Counterspell", "Brainstorm", "Ponder", "Island", "Mountain", "Lands");
		String text = "18 LANDS\n" //
				+ "4 Lightning Bolt\n" //
				+ "3 Counterspell\n" //
				+ "2 Brainstorm\n" //
				+ "1 Ponder\n" //
				+ "4 Island\n" //
				+ "4 Mountain\n";
		String result = DeckTextExtractor.extractDeckSection(text);
		assertTrue("expected a result", result != null);
		assertTrue("the type word alone must never be treated as a card", !result.contains("LANDS"));
		assertTrue(result.contains("4 Lightning Bolt"));
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals(6, count.unique);
		assertEquals(4 + 3 + 2 + 1 + 4 + 4, count.total);
	}

	/** Reads a real captured-page fixture from testdata/ (verbatim
	 *  BrowseWebsiteDialog.captureText - see each test method's own list of
	 *  real card names for the matching useDatabase() call). */
	private static String readCapture(String fileName) throws Exception {
		java.io.InputStream in = DeckTextExtractorTest.class.getResourceAsStream("testdata/" + fileName);
		assertTrue("missing test fixture: testdata/" + fileName, in != null);
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		byte[] buf = new byte[4096];
		int n;
		while ((n = in.read(buf)) >= 0)
			out.write(buf, 0, n);
		in.close();
		return new String(out.toByteArray(), "UTF-8");
	}

	/** Shared with {@link #testRealArchidektCommanderGoesToSideboard} - both
	 *  need the full real DB to reach MIN_MATCHES via extractDeckSection()
	 *  (unlike countRecognizedCards(), which only needs the run's own match
	 *  count, extractDeckSection() also requires it to clear MIN_MATCHES or
	 *  it returns null outright). */
	private static final String[] ARCHIDEKT_DB = { "Thranduil, Sindarin Liege", "Necklace of Girion",
			"Elrond, Moon-Reader", "Thranduil the Strategist", "Thranduil's Company", "Boseiju, Who Endures",
			"Breeding Pool", "Command Tower", "Dreamroot Cascade", "Eclipsed Realms", "Elven Passage",
			"Elvenking's Halls", "Fabled Passage", "Forest", "Hedge Maze", "Hinterland Harbor", "Island",
			"Misty Rainforest", "Path of Ancestry", "Reliquary Tower", "Rivendell", "Simic Growth Chamber",
			"Sodden Verdure", "Temple of Mystery", "Turbulent Wilderness", "Vineglimmer Snarl", "Willowrush Verge",
			"Galadhrim Ambush", "Lightning Greaves", "Selfless Safewright", "Swiftfoot Boots", "Arcane Signet",
			"Azusa, Lost but Seeking", "Crop Rotation", "Cultivate", "Elven Chorus", "Elvish Archdruid",
			"Elvish Mystic", "Entish Restoration", "Farhaven Elf", "Farseek", "Fyndhorn Elves", "Growth Spiral",
			"Harrow", "Icetill Explorer", "Kodama's Reach", "Llanowar Elves", "Marwyn, the Nurturer",
			"Nature's Lore", "Oracle of Mul Daya", "Paradise Druid", "Priest of Titania", "Rampant Growth",
			"Skyshroud Claim", "Sol Ring", "Spelunking", "Springbloom Druid", "Thought Vessel", "Three Visits",
			"Tireless Provisioner", "Wood Elves", "An Offer You Can't Refuse", "Counterspell", "Reclamation Sage",
			"Swan Song", "Elvish Warmaster", "Imperious Perfect", "Galadhrim Brigade", "Arwen, Weaver of Hope",
			"Elrond, Master of Healing", "Scythecat Cub", "Aesi, Tyrant of Gyre Strait", "Beast Whisperer",
			"Chronicle of Victory", "Elrond, Lord of Rivendell", "Kindred Discovery", "Opt", "Preordain",
			"Shamanic Revelation", "Tatyova, Benthic Druid", "Retreat to Coralhelm", "Retreat to Kazandu",
			"Scute Swarm", "Crown of Skemfar", "Glacierwood Siege", "Life from the Loam", "Planar Engineering",
			"Through the Forest Gate", "Elfsworn Giant", "Silvan Reveler", "Virulent Emissary", "Cyclonic Rift",
			"Doubling Season", "Exploration", "Lavaspur Boots", "Lumra, Bellow of the Woods", "Parallel Lives",
			"Rhystic Study", "Sylvan Library", "Zuran Orb" };

	/** Shared with {@link #testRealMoxfieldCommanderGoesToSideboard} - see
	 *  {@link #ARCHIDEKT_DB}'s own header for why. */
	private static final String[] MOXFIELD_DB = { "Jace, Multiverse Architect", "Aminatou, the Fateshifter",
			"Elspeth, Storm Slayer", "Elspeth, Sun's Champion", "Nicol Bolas, Dragon-God", "Nicol Bolas, God-Pharaoh",
			"Archfiend of Despair", "Archon of Cruelty", "Ashen Rider", "Aurelia, the Warleader",
			"Avacyn, Angel of Horror", "Consecrated Sphinx", "Elesh Norn, Grand Cenobite", "Etali, Primal Storm",
			"Exalted Sunborn", "Illustrious Wanderglyph", "Jhoira, Weatherlight Corsair",
			"Jin-Gitaxias, Progress Tyrant", "Memnarch, the Warden", "Nissa, Leyline Tamer",
			"Ojer Taq, Deepest Foundation", "Overlord of the Mistmoors", "Reya Dawnbringer", "Sanctum Lurker",
			"Serra's Emissary", "Spark Double", "Starwinder", "Martial Coup", "Mass Polymorph", "Sunfall",
			"White Sun's Twilight", "Brainstorm", "Brainsurge", "Despark", "Flawless Maneuver", "Grand Crescendo",
			"Path to Exile", "Secure the Wastes", "Stroke of Midnight", "Swords to Plowshares", "Synthetic Destiny",
			"Teferi's Reproach", "Arcane Signet", "Azorius Signet", "Chromatic Lantern", "Cursed Mirror",
			"Dimir Signet", "Fellwar Stone", "Izzet Signet", "Proteus Staff", "Rakdos Signet", "Sol Ring",
			"Staff of the Storyteller", "Talisman of Creativity", "Talisman of Dominance", "Talisman of Indulgence",
			"Talisman of Progress", "Oath of Teferi", "Skrelv's Hive", "Sphere of Safety", "Way of the Mind Sculptor",
			"Way of the Necromancer", "Battlefield Forge", "Bloodstained Mire", "Caves of Koilos", "Clifftop Retreat",
			"Command Tower", "Drowned Catacomb", "Exotic Orchard", "Fabled Passage", "Fetid Heath", "Flooded Strand",
			"Glacial Fortress", "Godless Shrine", "Hallowed Fountain", "Island", "Isolated Chapel", "Kher Keep",
			"Mountain", "Mystic Gate", "Plains", "Reflecting Pool", "Sacred Foundry", "Shivan Reef", "Sulfur Falls",
			"Sulfurous Springs", "Swamp", "Theorist's Sanctum", "Turbulent Crater", "Turbulent Shore",
			"Turbulent Wetlands", "Underground River" };

	@Test
	public void testRealTcgplayerCapture() throws Exception {
		// favorite: https://www.tcgplayer.com/content/magic-the-gathering/deck/Izzet-Prowess/550442/
		useDatabase("Dragon's Rage Channeler", "Monastery Swiftspear", "Slickshot Show-Off", "Expressive Iteration",
				"Preordain", "Mutagenic Growth", "Lightning Bolt", "Lava Dart", "Violent Urge", "Mishra's Bauble",
				"Cori-Steel Cutter", "Bloodstained Mire", "Fiery Islet", "Scalding Tarn", "Wooded Foothills",
				"Mountain", "Arid Mesa", "Thundering Falls", "Steam Vents", "Consign to Memory", "Meltdown",
				"Spell Snare", "Unholy Heat", "Spell Pierce", "Surgical Extraction");
		String text = readCapture("tcgplayer-izzet-prowess.txt");
		// the page's own "Maindeck, 60 cards" + "Sideboard (15)" - and PRECEDING
		// must still win over TRAILING here (not the stray "Dragon's Rage
		// Channeler" Product Details mention getting rescued instead)
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals(25, count.unique);
		assertEquals(75, count.total);
	}

	@Test
	public void testRealArchidektCapture() throws Exception {
		// favorite: https://archidekt.com/decks/26740402/copy_of_thranduil
		useDatabase(ARCHIDEKT_DB);
		String text = readCapture("archidekt-thranduil.txt");
		// the page's own "Deck size: 100" + 3-card Sideboard - Maybeboard's 9
		// cards must be excluded, and QtyPolicy.TRAILING must win here (this
		// site's own PRECEDING-only reading only ever found 32)
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals(91, count.unique);
		assertEquals(103, count.total);
	}

	@Test
	public void testRealMoxfieldCapture() throws Exception {
		// favorite: https://moxfield.com/decks/6I46dLgrQkWnGlyDvpeXWg
		useDatabase(MOXFIELD_DB);
		String text = readCapture("moxfield-jace-polymorph.txt");
		// the page's own "100 main deck/0 sideboard" - previously only 62
		// (the entire 38-card Lands category was silently discarded)
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals(92, count.unique);
		assertEquals(100, count.total);
	}

	@Test
	public void testRealDeckstatsCapture() throws Exception {
		// favorite: https://deckstats.net/decks/4392637-MB-oldschool-rev-2
		useDatabase("Graveborn Muse", "Greater Harvester", "Hypnotic Specter", "Nantuko Shade", "Undead Gladiator",
				"Contagion", "Dark Ritual", "Consume Spirit", "Duress", "Haunting Echoes", "Hymn to Tourach",
				"Mutilate", "Extraplanar Lens", "Phyrexian Arena", "Snow-Covered Swamp", "Avatar of Woe",
				"Decree of Pain", "Diabolic Edict", "Extirpate", "Funeral Charm", "Gatekeeper of Malakir",
				"Ghastly Demise", "Ghost Quarter", "Grafted Wargear", "Infest", "Mind Sludge", "Oversold Cemetery",
				"Plague Spitter", "Promise of Power", "Smother", "Toshiro Umezawa", "Umezawa's Jitte",
				"Withered Wretch");
		String text = readCapture("deckstats-mb-oldschool.txt");
		// the page's own "60 main / 28 side" - Maybeboard's 39 cards must be
		// excluded even though they're DB-confirmed real cards too
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals(33, count.unique);
		assertEquals(88, count.total);
	}

	@Test
	public void testRealMtgtop8Capture() throws Exception {
		// https://mtgtop8.com/event?e=90872&d=890135&f=MO - deliberately
		// includes "Lands"/"Creatures"/"Instants"/"Sorceries"/"Spells" in the
		// database too, reproducing the exact collision reported against the
		// real database ("18 LANDS" - this site's category headers render as
		// "<count> <TYPE>" with no separating punctuation - resolved to a
		// same-named database entry and got imported as a bogus extra card)
		useDatabase("Blood Crypt", "Bloodstained Mire", "Mountain", "Polluted Delta", "Raucous Theater", "Swamp",
				"Wooded Foothills", "Bloodghast", "Dragon's Rage Channeler", "Nethergoyf", "Orcish Bowmasters",
				"Stitcher's Supplier", "Corrupted Conviction", "Faithless Looting", "Fatal Push", "Flare of Malice",
				"Lightning Bolt", "Rakdos Charm", "Village Rites", "Mishra's Bauble", "Brotherhood's End",
				"Engineered Explosives", "Magus of the Moon", "Obsidian Charmaw", "Vexing Bauble", "Lands",
				"Creatures", "Instants", "Sorceries", "Spells");
		String text = readCapture("mtgtop8-rakdos-aggro.txt");
		String result = DeckTextExtractor.extractDeckSection(text);
		assertTrue("expected a result", result != null);
		assertTrue("category-header type words must never appear as cards", !result.contains("LANDS"));
		assertTrue("category-header type words must never appear as cards", !result.contains("CREATURES"));
		// the page's own "MD 61 SB 15" - Rakdos Charm appears in both main (2)
		// and sideboard (2), so unique names is one less than the 26 rows
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals(25, count.unique);
		assertEquals(76, count.total);
	}

	@Test
	public void testRealDeckboxCapture() throws Exception {
		// https://deckbox.org/sets/3560392 ("wrensleigh's Power Hungry") -
		// its own sidebar nav includes a genuine "Wishlist" link (a
		// collection-management page, nothing to do with any deck), which
		// used to hard-stop the scan before it ever reached the real
		// decklist much further down the page
		useDatabase("Prossh, Skyraider of Kher", "Brooding Saurian", "Capricious Efreet", "Charnelhoard Wurm",
				"Deathbringer Thoctar", "Deepfire Elemental", "Elvish Skysweeper", "Endless Cockroaches",
				"Endrek Sahr, Master Breeder", "Fell Shepherd", "Goblin Sharpshooter", "Golgari Guildmage",
				"Hooded Horror", "Hua Tuo, Honored Physician", "Hunted Troll", "Inferno Titan", "Jade Mage",
				"Ophiomancer", "Quagmire Druid", "Sakura-Tribe Elder", "Scarland Thrinax", "Sek'Kuar, Deathkeeper",
				"Shattergang Brothers", "Silklash Spider", "Sprouting Thrinax", "Stalking Vengeance",
				"Stronghold Assassin", "Terra Ravager", "Viscera Seer", "Walker of the Grove",
				"Wight of Precinct Six", "Jund Charm", "Reincarnation", "Dirge of Dread", "Mass Mutiny", "Restore",
				"Rough // Tumble", "Spoils of Victory", "Sudden Demise", "Tempt with Vengeance", "Armillary Sphere",
				"Carnage Altar", "Jar of Eyeballs", "Obelisk of Jund", "Plague Boiler", "Sol Ring",
				"Spine of Ish Sah", "Swiftfoot Boots", "Blood Rites", "Curse of Chaos", "Curse of Predation",
				"Curse of Shallow Graves", "Fecundity", "Foster", "Furnace Celebration", "Goblin Bombardment",
				"Night Soil", "Primal Vigor", "Tooth and Claw", "Vile Requiem", "Widespread Panic", "Akoum Refuge",
				"Command Tower", "Evolving Wilds", "Forest", "Golgari Guildgate", "Golgari Rot Farm",
				"Grim Backwoods", "Gruul Guildgate", "Jund Panorama", "Kazandu Refuge", "Khalni Garden", "Kher Keep",
				"Llanowar Reborn", "Mountain", "Opal Palace", "Rakdos Guildgate", "Rupture Spire", "Savage Lands",
				"Swamp", "Temple of the False God", "Terramorphic Expanse", "Vivid Grove");
		String text = readCapture("deckbox-power-hungry.txt");
		// the page's own "Main Deck - 100 cards, 83 distinct"
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals(83, count.unique);
		assertEquals(100, count.total);
	}

	@Test
	public void testRealDeckboxCommanderGoesToSideboard() throws Exception {
		// same capture as testRealDeckboxCapture() - this locks in the
		// separate Commander fix: the deck's own "Commander" section header
		// (a bare line ahead of the entire maindeck, with just the
		// commander's own card row under it) must wrap Prossh in a one-shot
		// Sideboard/Deck toggle (FreeformImportDelegate's own section-toggle
		// convention) so it lands in the sideboard on import, by MTG
		// convention - not as an ordinary maindeck row, and without
		// sweeping the real 99-card maindeck that follows into the
		// sideboard too.
		// same full DB as testRealDeckboxCapture() - a contiguous run needs
		// enough of the real 83 unique names seeded to actually reach
		// MIN_MATCHES via extractDeckSection() (unlike countRecognizedCards(),
		// which just needs the run's own count, extractDeckSection() also
		// requires it to clear MIN_MATCHES or it returns null outright)
		useDatabase("Prossh, Skyraider of Kher", "Brooding Saurian", "Capricious Efreet", "Charnelhoard Wurm",
				"Deathbringer Thoctar", "Deepfire Elemental", "Elvish Skysweeper", "Endless Cockroaches",
				"Endrek Sahr, Master Breeder", "Fell Shepherd", "Goblin Sharpshooter", "Golgari Guildmage",
				"Hooded Horror", "Hua Tuo, Honored Physician", "Hunted Troll", "Inferno Titan", "Jade Mage",
				"Ophiomancer", "Quagmire Druid", "Sakura-Tribe Elder", "Scarland Thrinax", "Sek'Kuar, Deathkeeper",
				"Shattergang Brothers", "Silklash Spider", "Sprouting Thrinax", "Stalking Vengeance",
				"Stronghold Assassin", "Terra Ravager", "Viscera Seer", "Walker of the Grove",
				"Wight of Precinct Six", "Jund Charm", "Reincarnation", "Dirge of Dread", "Mass Mutiny", "Restore",
				"Rough // Tumble", "Spoils of Victory", "Sudden Demise", "Tempt with Vengeance", "Armillary Sphere",
				"Carnage Altar", "Jar of Eyeballs", "Obelisk of Jund", "Plague Boiler", "Sol Ring",
				"Spine of Ish Sah", "Swiftfoot Boots", "Blood Rites", "Curse of Chaos", "Curse of Predation",
				"Curse of Shallow Graves", "Fecundity", "Foster", "Furnace Celebration", "Goblin Bombardment",
				"Night Soil", "Primal Vigor", "Tooth and Claw", "Vile Requiem", "Widespread Panic", "Akoum Refuge",
				"Command Tower", "Evolving Wilds", "Forest", "Golgari Guildgate", "Golgari Rot Farm",
				"Grim Backwoods", "Gruul Guildgate", "Jund Panorama", "Kazandu Refuge", "Khalni Garden", "Kher Keep",
				"Llanowar Reborn", "Mountain", "Opal Palace", "Rakdos Guildgate", "Rupture Spire", "Savage Lands",
				"Swamp", "Temple of the False God", "Terramorphic Expanse", "Vivid Grove");
		String text = readCapture("deckbox-power-hungry.txt");
		String result = DeckTextExtractor.extractDeckSection(text);
		assertTrue("expected a result", result != null);
		String[] lines = result.split("\n");
		assertEquals("Sideboard", lines[0]);
		assertEquals("1 Prossh, Skyraider of Kher", lines[1]);
		assertEquals("Deck", lines[2]);
		long sideboardMarkers = java.util.Arrays.stream(lines).filter(l -> l.equals("Sideboard")).count();
		long deckMarkers = java.util.Arrays.stream(lines).filter(l -> l.equals("Deck")).count();
		assertEquals("only Prossh should be wrapped - the real maindeck must not also be swept into the sideboard", 1,
				sideboardMarkers);
		assertEquals(1, deckMarkers);
		assertTrue("the real maindeck must still be present", result.contains("1 Brooding Saurian"));
	}

	@Test
	public void testRealArchidektCommanderGoesToSideboard() throws Exception {
		// same capture as testRealArchidektCapture() - the deckbox.org fix
		// above only checked the single line right before the run's first
		// match, which missed Archidekt's own shape entirely: its
		// "Commander" header sits 2 lines ahead of Thranduil's own name
		// ("Qty: 1", "Price: $0.49" in between, part of this site's per-
		// card metadata block - see startsWithCommanderHeader()'s own
		// header). Archidekt ALSO has a real, separate 3-card Sideboard
		// section later in the run (already covered by
		// testRealArchidektCapture()'s own total/unique assertions), so
		// unlike the deckbox.org/Moxfield tests this doesn't assert
		// exactly one Sideboard marker - just that the FIRST one correctly
		// wraps the commander.
		useDatabase(ARCHIDEKT_DB);
		String text = readCapture("archidekt-thranduil.txt");
		String result = DeckTextExtractor.extractDeckSection(text);
		assertTrue("expected a result", result != null);
		String[] lines = result.split("\n");
		assertEquals("Sideboard", lines[0]);
		assertEquals("1 Thranduil, Sindarin Liege", lines[1]);
		assertEquals("Deck", lines[2]);
		assertTrue("the real maindeck must still be present", result.contains("Sol Ring"));
	}

	@Test
	public void testRealMoxfieldCommanderGoesToSideboard() throws Exception {
		// same capture as testRealMoxfieldCapture() - Moxfield's own shape
		// is different again from both deckbox.org and Archidekt: its
		// "Commander" header sits 2 lines ahead of the match too, but via a
		// placeholder "(1)" line and a standalone qty "1" in between (a
		// PRECEDING-policy bare match), not per-card "Qty:"/"Price:" labels.
		useDatabase(MOXFIELD_DB);
		String text = readCapture("moxfield-jace-polymorph.txt");
		String result = DeckTextExtractor.extractDeckSection(text);
		assertTrue("expected a result", result != null);
		String[] lines = result.split("\n");
		assertEquals("Sideboard", lines[0]);
		assertEquals("1 Jace, Multiverse Architect", lines[1]);
		assertEquals("Deck", lines[2]);
		long sideboardMarkers = java.util.Arrays.stream(lines).filter(l -> l.equals("Sideboard")).count();
		long deckMarkers = java.util.Arrays.stream(lines).filter(l -> l.equals("Deck")).count();
		assertEquals("only Jace should be wrapped - the real maindeck must not also be swept into the sideboard", 1,
				sideboardMarkers);
		assertEquals(1, deckMarkers);
		assertTrue("the real maindeck must still be present", result.contains("Aminatou, the Fateshifter"));
	}
}
