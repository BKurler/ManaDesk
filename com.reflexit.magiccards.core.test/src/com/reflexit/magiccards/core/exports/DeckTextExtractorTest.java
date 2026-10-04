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
 *     Rémi Dutil (2026) - testRealTappedOutCommanderGoesToSideboard(): a
 *                         real TappedOut capture needed three MORE fixes on
 *                         top of the three sites above - its "Commander"
 *                         section isn't always first (exercises the mid-loop
 *                         isCommanderHeader() check, not the pre-loop
 *                         startsWithCommanderHeader() the other three tests
 *                         exercise), and its commander's only recoverable
 *                         text ("Commander: Gishath, Sun's Avatar", via
 *                         CAPTURE_TEXT_WITH_IMAGE_ALT_SCRIPT's image-alt
 *                         substitution - the fixture here IS that capture,
 *                         not plain innerText) needed a colon-exclusion in
 *                         isCommanderHeader(), a new COMMANDER_PREFIX strip
 *                         in matchLine(), and a new unconditional bare-match
 *                         trust in matchLines() for a line immediately
 *                         preceded by a Commander header (see
 *                         DeckTextExtractor's own header for all three).
 *     Rémi Dutil (2026) - testGluedTrailingBadgeIsStripped(): the same real
 *                         TappedOut capture's "Aura ShardsGC" gap, deferred
 *                         above as its own separately-considered fix -
 *                         GLUED_TRAILING_BADGE strips a short glued-on
 *                         uppercase run (a "Game Changer" Commander-bracket
 *                         badge here) with no separating space at all, which
 *                         the existing trim-by-WORD loop could never reach.
 *                         Also includes the false-positive guard: a name
 *                         that only "resolves" after the strip but isn't a
 *                         real card even then is still correctly rejected.
 *                         testRealTappedOutCommanderGoesToSideboard() above
 *                         updated to assert on it too, using the same real
 *                         capture.
 *     Rémi Dutil (2026) - testRealTipsymagicFlatSortedViewTableRow(): a real
 *                         tipsymagic.com capture ("Flat Sorted View" - one
 *                         of 4 toggleable ways this site can show a deck)
 *                         showed a genuine multi-column HTML table, tab-
 *                         separated, name in the FIRST column and qty in
 *                         the LAST, six real columns apart - far beyond
 *                         what the existing trim-by-WORD loop could ever
 *                         trim through, and trimming from the wrong end
 *                         entirely. Locks in matchTableRow() (see
 *                         DeckTextExtractor's own header), which reads the
 *                         raw line's own tab characters directly instead of
 *                         guessing from flattened text.
 *     Rémi Dutil (2026) - testDeckTitleCoincidingWithARealCardIsNotImported():
 *                         a real capture of the SAME tipsymagic.com "Hydra"
 *                         deck (slightly less page chrome than the fixture
 *                         above) reproduced a genuine bug: the deck's own
 *                         title happens to ALSO be a real card name, and
 *                         picked up its page's own view-count as a bogus
 *                         quantity (a real observed total: 101 instead of
 *                         60). Locks in the fix - see findTrailingQty()'s
 *                         own header.
 *     Rémi Dutil (2026) - testDetectDeckMetaMoxfieldIgnoresProfilePictureAvatarLine():
 *                         a later real capture of the same Moxfield deck used
 *                         by testRealMoxfieldCapture() showed the site now
 *                         renders an avatar alt-text line ("&lt;Username&gt;'s
 *                         Profile Picture") right above the real title -
 *                         wrongly picked up as the title itself. Locks in the
 *                         fix - see TITLE_CHROME_WORDS' own header in
 *                         DeckTextExtractor.
 *     Rémi Dutil (2026) - testDetectDeckMetaDeckboxMultiWordUsername(): the
 *                         same real deckbox.org capture reused by
 *                         testRealDeckboxScratchpadExcluded() above has a
 *                         two-word display name ("Devon Krynicki") - nothing
 *                         was detected at all until TITLE_POSSESSIVE was
 *                         widened/re-tightened (see its own header in
 *                         DeckTextExtractor) to allow a multi-word username
 *                         while still rejecting quote-glued ad copy.
 *     Rémi Dutil (2026) - testDetectDeckMetaTappedOutFallsBackToCommanderName():
 *                         a real TappedOut capture with no title text
 *                         anywhere at all (this site's own "Commander"
 *                         category sorts deep inside the matched card
 *                         block, not before it) - locks in the absolute
 *                         last-resort commander-name fallback, see its own
 *                         header at the end of detectDeckMeta() in
 *                         DeckTextExtractor.
 *     Rémi Dutil (2026) - testDetectDeckMetaTappedOutStripsQuotedTitle(): a
 *                         real TappedOut capture whose own title line is
 *                         wrapped in a literal pair of quote characters -
 *                         locks in stripSurroundingQuotes(), detectDeckMeta()'s
 *                         own final cleanup step in DeckTextExtractor.
 *     Rémi Dutil (2026) - testRealArchidektAnjeMaidOfDishonor()/
 *                         testRealArchidektAnjeReorderedCategoriesWithTokensExtras()/
 *                         testRealArchidektAnjeGridViewWithOracleText(): a
 *                         real Archidekt deck reported as showing "100 or
 *                         101 cards" and "Detected name: not detected"
 *                         depending on which of the site's 4 "View as"
 *                         layouts was selected. The third ("grid view")
 *                         capture - every card's oracle text interleaved
 *                         between its name and its qty line - initially DID
 *                         reproduce a real miscount under test, tracked down
 *                         to useDatabase() not mirroring normNames()'s own
 *                         faces() splitting for modal double-faced cards
 *                         (see useDatabase()'s own header for the fix). A
 *                         fourth capture, testRealArchidektAnjeStackedView()
 *                         ("stacked view" - a FOURTH distinct category order
 *                         from this one real deck, confirming Archidekt's
 *                         own order is never stable between page loads),
 *                         rounds out real coverage of all 4 of this site's
 *                         "View as" layouts. With a database that actually
 *                         matches what a real user has, all four come back
 *                         correct; the live "101"/"not detected" discrepancy
 *                         itself was never reproduced against real captured
 *                         text. See ANJE_DB's own header.
 *     Rémi Dutil (2026) - testRealMtgdecksSisayWeatherlightCaptainExcludes-
 *                         ArenaExportButton(): a real mtgdecks.net Commander
 *                         capture's own "Export to MTG Arena" button renders
 *                         as two lines that match the site's card-row shape
 *                         - a real run counted it as a phantom 101st card.
 *                         Locks in the fix (the button text is filtered
 *                         before matching, see DeckTextExtractor's own
 *                         header).
 *     Rémi Dutil (2026) - testRealDeckstatsBruno(): added while double-
 *                         checking full real-site coverage against the
 *                         user's own list of 28 known-good URLs - a clean
 *                         addition, no DeckTextExtractor bug found. Unlike
 *                         every other real Commander capture in this file,
 *                         deckstats.net's own Commander section wraps its
 *                         card as "COMMANDER (1)\n1\nThe Balrog, Durin's
 *                         Bane" (qty and name on their own separate lines,
 *                         no shared row) - already handled by the existing
 *                         separate-line shape, so this is a coverage-only
 *                         addition.
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
		for (String name : realCardNames) {
			db.add((String) normMethod.invoke(null, name));
			// mirrors normNames()'s own faces() helper - the real production
			// database indexes a modal double-faced card's FRONT FACE name
			// alone too ("Voldaren Bloodcaster"), not just the combined "A
			// // B" form, since a real Archidekt "grid view" capture shows
			// that bare front-face name as the card's own oracle-text
			// header line, with the combined name only appearing inside a
			// reprint-stamp line that's correctly rejected elsewhere. A
			// fixture seeded with ONLY the combined name (not also its
			// split faces) tests a weaker, unrealistic database than real
			// users actually have - see ANJE_DB's own header.
			for (String face : name.split("//"))
				if (!face.trim().isEmpty())
					db.add((String) normMethod.invoke(null, face));
		}
		Field normNamesField = DeckTextExtractor.class.getDeclaredField("NORM_NAMES");
		normNamesField.setAccessible(true);
		normNamesField.set(null, db);
	}

	/** For every test below that deliberately exercises the "no database"
	 *  shape-only-trust path (resolveName()'s own trustShapeIfDbUnavailable
	 *  fallback) by simply never calling {@link #useDatabase}: leaving
	 *  NORM_NAMES at its {@code @After}-reset {@code null} is NOT the same
	 *  thing as an empty database - {@link DeckTextExtractor}'s own
	 *  normNames() treats {@code null} as "not yet cached", and lazily
	 *  queries the REAL DataManager.getInstance().getMagicDBStore() to
	 *  (re)populate it. That real store is a process-wide singleton, not
	 *  reset between test CLASSES - as long as DeckTextExtractorTest ran
	 *  alone (every verification this whole project's history), nothing
	 *  else in the same JVM ever populated it, so the lazy query always
	 *  legitimately found it empty and these tests passed for the right
	 *  reason. Once DeckTextExtractorTest started running inside the same
	 *  suite as other tests that DO seed a real (if small, testdb/*.txt-only)
	 *  database - DataManagerTest, and MagicXmlHandlerTest once it also
	 *  started calling TestFileUtils.resetDb() - that database stays loaded
	 *  in the SAME JVM for the rest of the suite run, so the very next
	 *  "no useDatabase()" test's own lazy query now finds real (if
	 *  incomplete) data instead, silently flipping it from shape-only trust
	 *  to DB-confirmation mode. A real observed case: a deckbox.org capture
	 *  (testRealDeckboxScratchpadExcluded) read 10 cards instead of 100 -
	 *  most of its real card names simply aren't in that small 20-set test
	 *  fixture. Explicitly forcing an EMPTY (not null) Set here closes the
	 *  gap at its root - normNames() never even reaches the real DataManager
	 *  when NORM_NAMES is already non-null, empty or not. */
	private static void useEmptyDatabase() throws Exception {
		Field normNamesField = DeckTextExtractor.class.getDeclaredField("NORM_NAMES");
		normNamesField.setAccessible(true);
		normNamesField.set(null, new HashSet<String>());
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
	public void testExtractsOnlyTheDecklistBlock() throws Exception {
		useEmptyDatabase();
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
	public void testPlainProseReturnsNull() throws Exception {
		useEmptyDatabase();
		String prose = "This is just a regular paragraph of text.\n"
				+ "It has several sentences, none of which look like a decklist.\n"
				+ "4 out of 5 stars is a common rating, but this line alone should not be enough.\n";
		assertNull(DeckTextExtractor.extractDeckSection(prose));
	}

	@Test
	public void testEmptyOrNullInput() throws Exception {
		useEmptyDatabase();
		assertNull(DeckTextExtractor.extractDeckSection(null));
		assertNull(DeckTextExtractor.extractDeckSection(""));
	}

	@Test
	public void testBelowMinimumMatchesFallsBackToNull() throws Exception {
		useEmptyDatabase();
		// only 3 deck-shaped lines, buried in noise - too few to trust
		String text = "Some header\n" //
				+ "1 Plains\n" //
				+ "1 Island\n" //
				+ "1 Swamp\n" //
				+ "Some footer\n";
		assertNull(DeckTextExtractor.extractDeckSection(text));
	}

	@Test
	public void testLooksLikeDeckLineShapes() throws Exception {
		useEmptyDatabase();
		assertTrue(DeckTextExtractor.looksLikeDeckLine("4 Lightning Bolt"));
		assertTrue(DeckTextExtractor.looksLikeDeckLine("4x Lightning Bolt"));
		assertTrue(DeckTextExtractor.looksLikeDeckLine("x4 Lightning Bolt"));
		assertTrue(DeckTextExtractor.looksLikeDeckLine("x 4 Lightning Bolt"));
		assertTrue(!DeckTextExtractor.looksLikeDeckLine("Lightning Bolt"));
		assertTrue(!DeckTextExtractor.looksLikeDeckLine("Sideboard"));
		assertTrue(!DeckTextExtractor.looksLikeDeckLine(""));
	}

	@Test
	public void testEntireTextIsDeckLines() throws Exception {
		useEmptyDatabase();
		String text = "4 Lightning Bolt\n3 Counterspell\n2 Brainstorm\n1 Ponder\n4 Island\n4 Mountain\n";
		String result = DeckTextExtractor.extractDeckSection(text);
		assertEquals(text.trim(), result);
	}

	@Test
	public void testBareLinesWithoutQuantityAreRejectedWhenDbUnavailable() throws Exception {
		useEmptyDatabase();
		// no leading count on any line - without a live database to confirm
		// these are real card names, bare lines must not be trusted on shape
		// alone (unlike qty-prefixed lines, which testEntireTextIsDeckLines
		// already covers)
		String text = "Lightning Bolt\nCounterspell\nBrainstorm\nPonder\nIsland\nMountain\n";
		assertNull(DeckTextExtractor.extractDeckSection(text));
	}

	@Test
	public void testQuantityLinesWithNonBreakingSpacesStillMatch() throws Exception {
		useEmptyDatabase();
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
	public void testCleansTrailingJunkAndDropsCategoryHeaders() throws Exception {
		useEmptyDatabase();
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
	public void testNeutralGapLinesDoNotBreakARun() throws Exception {
		useEmptyDatabase();
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
	public void testCountRecognizedCardsReturnsTotalAndUnique() throws Exception {
		useEmptyDatabase();
		String text = "4 Lightning Bolt\n3 Counterspell\n2 Brainstorm\n1 Ponder\n4 Island\n4 Mountain\n";
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals(6, count.unique);
		assertEquals(4 + 3 + 2 + 1 + 4 + 4, count.total);
	}

	@Test
	public void testCountRecognizedCardsCountsDistinctNamesNotRows() throws Exception {
		useEmptyDatabase();
		// "Lightning Bolt" appears as two separate rows (maindeck + sideboard)
		// - unique counts distinct NAMES (5), not rows (6); total still sums both
		String text = "4 Lightning Bolt\n3 Counterspell\n2 Brainstorm\n1 Ponder\n4 Island\nSideboard\n2 Lightning Bolt\n";
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals(5, count.unique);
		assertEquals(4 + 3 + 2 + 1 + 4 + 2, count.total);
	}

	@Test
	public void testCountRecognizedCardsBelowThresholdStillReportsWhatItFound() throws Exception {
		useEmptyDatabase();
		// countRecognizedCards() does not gate on MIN_MATCHES itself - the
		// caller (BrowseWebsiteDialog) decides how to phrase "not enough yet"
		String text = "Some header\n1 Plains\n1 Island\n1 Swamp\nSome footer\n";
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals(3, count.unique);
		assertEquals(3, count.total);
	}

	@Test
	public void testCountRecognizedCardsEmptyInput() throws Exception {
		useEmptyDatabase();
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards("");
		assertEquals(0, count.unique);
		assertEquals(0, count.total);
	}

	@Test
	public void testMaybeboardSectionIsExcludedFromTheRun() throws Exception {
		useEmptyDatabase();
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
	public void testLongCategoryTransitionGapIsNowTolerated() throws Exception {
		useEmptyDatabase();
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
	public void testMaybeboardSectionNeverOutranksTheRealDeck() throws Exception {
		useEmptyDatabase();
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

	@Test
	public void testGluedTrailingBadgeIsStripped() throws Exception {
		// modeled on a real TappedOut capture ("Aura ShardsGC" - "GC" is a
		// "Game Changer" Commander-bracket badge rendered with no space at
		// all between it and the card's own name, so the existing trim-by-
		// WORD loop can never help - there's no space to trim on). Also
		// locks in the false-positive guard: a name that only "resolves"
		// after stripping a glued suffix but ISN'T a real card even then
		// must still be rejected (the DB cross-check still applies to the
		// stripped candidate, not bypassed).
		useDatabase("Aura Shards", "Sol Ring");
		assertTrue("a real card name with a glued badge should still resolve",
				DeckTextExtractor.looksLikeDeckLine("1x Aura ShardsGC"));
		assertTrue("an ordinary name with no glued suffix must be unaffected",
				DeckTextExtractor.looksLikeDeckLine("1x Sol Ring"));
		assertTrue("a fake name must still be rejected even with a glued-looking suffix",
				!DeckTextExtractor.looksLikeDeckLine("1x FakeCardXYZ"));
		String text = "4 Lightning Bolt\n" //
				+ "1x Aura ShardsGC\n" //
				+ "1x Sol Ring\n" //
				+ "3 Counterspell\n" //
				+ "2 Brainstorm\n" //
				+ "1 Ponder\n";
		useDatabase("Lightning Bolt", "Aura Shards", "Sol Ring", "Counterspell", "Brainstorm", "Ponder");
		String result = DeckTextExtractor.extractDeckSection(text);
		assertTrue("expected a result", result != null);
		assertTrue("the badge itself must not leak into the resolved name", !result.contains("ShardsGC"));
		assertTrue(result.contains("1 Aura Shards"));
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
		// the page's own "Deck size: 100" - Maybeboard's 9 cards must be
		// excluded, and QtyPolicy.TRAILING must win here (this site's own
		// PRECEDING-only reading only ever found 32). This deck's own
		// "Commander" section makes it Commander-format, and Commander has
		// no official sideboard at all - Archidekt's own 3-card "Sideboard"
		// section (real MTG rules: never a legal sideboard on a Commander
		// deck, whatever a site calls it) is now excluded too, same as any
		// other site's "Sideboard" once a Commander header is recognized
		// (see DeckTextExtractor's own header) - previously wrongly counted
		// as 103 total/91 unique.
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals(88, count.unique);
		assertEquals(100, count.total);
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
		// header). Archidekt's own real, separate 3-card "Sideboard"
		// section later in the run is now excluded entirely (Commander has
		// no official sideboard - see DeckTextExtractor's own header;
		// already covered by testRealArchidektCapture()'s own total/unique
		// assertions), so - unlike when this test was written - there is
		// now exactly one Sideboard marker, the commander's own.
		useDatabase(ARCHIDEKT_DB);
		String text = readCapture("archidekt-thranduil.txt");
		String result = DeckTextExtractor.extractDeckSection(text);
		assertTrue("expected a result", result != null);
		String[] lines = result.split("\n");
		assertEquals("Sideboard", lines[0]);
		assertEquals("1 Thranduil, Sindarin Liege", lines[1]);
		assertEquals("Deck", lines[2]);
		assertTrue("the real maindeck must still be present", result.contains("Sol Ring"));
		long sideboardMarkers = java.util.Arrays.stream(lines).filter(l -> l.equals("Sideboard")).count();
		assertEquals("the real Sideboard section is excluded now (Commander has no official sideboard) - only "
				+ "the commander's own wrap remains", 1, sideboardMarkers);
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

	@Test
	public void testRealTappedOutCommanderGoesToSideboard() throws Exception {
		// https://tappedout.net/mtg-decks/fast-gishath-dino-vomit/ - the
		// hardest of the four real Commander shapes fixed this session: (1)
		// "Commander" is NOT the first section here (it sits between Land
		// and Enchantment), so this exercises the mid-loop isCommanderHeader
		// check in extractDeckSection(), not the pre-loop
		// startsWithCommanderHeader() the other three tests exercise; (2)
		// the commander's only text is "Commander: Gishath, Sun's Avatar"
		// (recovered via CAPTURE_TEXT_WITH_IMAGE_ALT_SCRIPT's image-alt
		// substitution - this fixture is that capture, not plain innerText,
		// since that's what a real "Import this page" click actually sees),
		// which needed three separate fixes to resolve correctly: the colon
		// exclusion in isCommanderHeader(), the COMMANDER_PREFIX strip in
		// matchLine(), and trusting a bare match preceded by a Commander
		// header even with no adjacent qty evidence in matchLines(). This
		// fixture ALSO exercises the separate GLUED_TRAILING_BADGE fix
		// ("Aura ShardsGC" - a "Game Changer" bracket badge glued directly
		// onto the card's own name with no separating space at all), AND
		// the universal "Commander has no official sideboard" rule - this
		// page's own real, 11-card "Sideboard" section (Creature/
		// Enchantment/Land/Artifact) is now excluded entirely, matching the
		// page's own stated "Cards 100" total exactly.
		useDatabase("Apex Altisaur", "Birds of Paradise", "Bloom Tender", "Bonehoard Dracosaur",
				"Bronzebeak Foragers", "Curious Altisaur", "Delighted Halfling", "Earthshaker Dreadmaw",
				"Etali, Primal Conqueror", "Etali, Primal Storm", "Faeburrow Elder", "Fanatic of Rhonas",
				"Ghalta and Mavren", "Ghalta, Primal Hunger", "Ghalta, Stampede Tyrant", "Goring Ceratops",
				"Hunting Velociraptor", "Ilysian Caryatid", "Kinjalli's Caller", "Kinjalli's Sunwing",
				"Kogla and Yidaro", "Marauding Raptor", "Pantlaza, Sun-Favored", "Polyraptor",
				"Quartzwood Crasher", "Regal Behemoth", "Regisaur Alpha", "Roaming Throne", "Scion of Calamity",
				"Selvala, Heart of the Wilds", "Shaman of Forgotten Ways", "Silverclad Ferocidons",
				"Somberwald Sage", "Temple Altisaur", "The Tarrasque", "Topiary Stomper", "Trapjaw Tyrant",
				"Trumpeting Carnosaur", "Vaultborn Tyrant", "Verdant Sun's Avatar", "Wakening Sun's Avatar",
				"Wayward Swordtooth", "Whisperer of the Wilds", "Zacama, Primal Calamity", "Arid Mesa",
				"Battlefield Forge", "Boseiju, Who Endures", "Bountiful Promenade", "Brushland", "Castle Garenbrig",
				"Cavern of Souls", "Clifftop Retreat", "Command Tower", "Commercial District", "Elegant Parlor",
				"Evendo, Waking Haven", "Exotic Orchard", "Forest", "Hushwood Verge", "Jetmir's Garden",
				"Karplusan Forest", "Krosan Verge", "Lush Portico", "Mountain", "Overgrown Farmland", "Plateau",
				"Rockfall Vale", "Rootbound Crag", "Sacred Foundry", "Savannah", "Spectator Seating", "Spire Garden",
				"Stomping Ground", "Sunbillow Verge", "Sunhome, Fortress of the Legion", "Sunpetal Grove", "Taiga",
				"Temple Garden", "Thornspire Verge", "Windswept Heath", "Wooded Foothills", "Gishath, Sun's Avatar",
				"Aura Shards", "Elemental Bond", "Garruk's Uprising",
				"Guardian Project", "Overgrowth", "Pandemonium", "Rhythm of the Wild", "Shadow in the Warp",
				"Sylvan Library", "Utopia Sprawl", "Warstorm Surge", "Wild Growth", "Herald's Horn",
				"Herd Heirloom", "Sol Ring", "The Great Henge", "Urza's Incubator", "Primal Surge", "Arbor Elf",
				"Atzocan Seer", "Knight of the Stampede", "Otepec Huntmaster", "Ripjaw Raptor", "Wrathful Raptors",
				"Descendants' Path", "Lurking Predators", "Mirari's Wake", "Sundown Pass", "Monster Manual");
		String text = readCapture("tappedout-gishath-dino-vomit.txt");
		String result = DeckTextExtractor.extractDeckSection(text);
		assertTrue("expected a result", result != null);
		String[] lines = result.split("\n");
		int commanderIndex = -1;
		for (int i = 0; i < lines.length; i++)
			// no leading "1 " - unlike the other three sites, nothing on
			// this page attaches an explicit qty to the commander's own
			// line at all (no same-line "1x", no adjacent standalone "1"),
			// so it comes out as a bare name - FreeformImportDelegate's own
			// default (a line with no leading count means qty 1) already
			// handles that correctly
			if (lines[i].equals("Gishath, Sun's Avatar"))
				commanderIndex = i;
		assertTrue("expected the commander to be found, wrapped in a Sideboard/Deck pair", commanderIndex > 0);
		assertEquals("Sideboard", lines[commanderIndex - 1]);
		assertEquals("Deck", lines[commanderIndex + 1]);
		assertTrue("the maindeck BEFORE the (non-first) Commander section must still be present",
				result.contains("1 Apex Altisaur") && result.contains("1 Wooded Foothills"));
		assertTrue("the maindeck AFTER the Commander section must still be present",
				result.contains("1 Elemental Bond") && result.contains("1 Primal Surge"));
		assertTrue("Commander has no official sideboard - this real, 11-card 'Sideboard' section must now be "
				+ "excluded entirely", !result.contains("Monster Manual"));
		assertTrue("the glued 'GC' badge must not prevent Aura Shards from resolving",
				result.contains("1 Aura Shards"));
		assertTrue("the badge itself must not leak into the resolved name", !result.contains("ShardsGC"));
		// the page's own "Cards 100" - the real Sideboard's 11 cards
		// excluded, the commander recovered, and Aura Shards' glued badge
		// no longer dropping it
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals(100, count.total);
	}

	@Test
	public void testRealTipsymagicFlatSortedViewTableRow() throws Exception {
		// https://tipsymagic.com/view_deck/7657-hydra - one of 4 toggleable
		// view modes this site offers; "Flat Sorted View" renders a real
		// HTML table, one row per card, tab-separated: "Card\tSection\t
		// Type\tRarity\tMV\tColor\tPrice\tQty" - name in the FIRST column,
		// quantity in the LAST, six real columns apart. Locks in
		// matchTableRow(): the existing trim-by-WORD loop (bounded by
		// MAX_NAME_TRIMS=4) could never trim through that much column data,
		// and trims from the wrong end entirely - the quantity here isn't
		// adjacent to the name at all. One other view mode on this same real
		// deck ("Stacked Pictures", a plain qty/name shape) already worked
		// via existing PRECEDING logic, verified by hand against a live
		// capture - not separately covered here since it needs no new code.
		// The site's "List" view looks superficially similar (name/-/qty/+)
		// but is NOT the same shape - see
		// testRealTipsymagicListViewQuantityPrecedesNameWithGap() below,
		// which needed a real fix, not just hand-verification.
		useDatabase("Stomping Ground", "Kalonian Hydra", "Birds of Paradise", "Eldritch Evolution",
				"Summoner's Pact", "Ignoble Hierarch", "Copperline Gorge", "Elvish Mystic", "Karplusan Forest",
				"Llanowar Elves", "Generator Servant", "Arboreal Grazer", "Forest", "Mountain");
		String text = readCapture("tipsymagic-hydra-flat-sorted.txt");
		// the page's own "14 entries" + real deck total (60 cards - four 6s
		// are basic lands, the rest are singles at 4 copies each in this
		// non-singleton Modern-style deck)
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals(14, count.unique);
		assertEquals(60, count.total);
		String result = DeckTextExtractor.extractDeckSection(text);
		assertTrue("expected a result", result != null);
		assertTrue("the table's own junk columns (Section/Type/Rarity/MV/Color/Price) must never leak into the "
				+ "resolved name", !result.contains("Main Deck") && !result.contains("Rare") && !result.contains("$"));
		assertTrue(result.contains("4 Stomping Ground"));
		assertTrue(result.contains("6 Forest"));
	}

	@Test
	public void testDeckTitleCoincidingWithARealCardIsNotImported() throws Exception {
		// same tipsymagic.com "Hydra" deck as above, but a real capture with
		// SLIGHTLY less page chrome above the Flat Sorted View table (no
		// "News/Videos/Test This Deck/..." nav row) reproduced a genuine
		// bug: the deck's own TITLE, "Hydra", happens to ALSO be a real (if
		// obscure) card name - deliberately seeded into the DB here to
		// reproduce that exact coincidence - and under QtyPolicy.TRAILING,
		// findTrailingQty() used to skip past "by"/the username and pick up
		// the page's own view-count ("41") as a bogus trailing quantity,
		// then bridge (well within MAX_GAP) into the real 14-card table
		// further down, adding a phantom 41-copy "Hydra" to the total (109
		// -> 101, a real observed count). Locks in the fix: a candidate
		// trailing quantity immediately followed by ANOTHER bare standalone
		// number (the unmistakable shape of a page's own engagement stats,
		// never a real per-card quantity) is no longer trusted.
		useDatabase("Stomping Ground", "Kalonian Hydra", "Birds of Paradise", "Eldritch Evolution",
				"Summoner's Pact", "Ignoble Hierarch", "Copperline Gorge", "Elvish Mystic", "Karplusan Forest",
				"Llanowar Elves", "Generator Servant", "Arboreal Grazer", "Forest", "Mountain",
				"Hydra" /* the real, obscure Marvel Super Heroes Jumpstart card */);
		String text = readCapture("tipsymagic-hydra-title-collision.txt");
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals("the deck's own title must never be imported as a phantom 41-copy card", 14, count.unique);
		assertEquals(60, count.total);
		String result = DeckTextExtractor.extractDeckSection(text);
		assertTrue("expected a result", result != null);
		for (String line : result.split("\n"))
			assertTrue("the bare 'Hydra' deck title must not appear as its own row (only as part of the real "
					+ "'Kalonian Hydra')", !line.equals("Hydra") && !line.equals("41 Hydra"));
	}

	@Test
	public void testRealTipsymagicListViewQuantityPrecedesNameWithGap() throws Exception {
		// same tipsymagic.com "Hydra" deck as the two tests above, but its
		// "List" view (a different toggle than "Flat Sorted View"): each
		// card is "qty\n[blank/whitespace-only filler]\nName" - the quantity
		// comes BEFORE the name (like QtyPolicy.PRECEDING), but with 1-2
		// filler lines in between rather than on the immediately preceding
		// line, so strict PRECEDING never matches. TRAILING (searching
		// forward from the name) used to "work" only by accident - it kept
		// finding the NEXT card's own quantity instead, which happened to
		// sum correctly card-by-card since every quantity but Forest/
		// Mountain's is the same value (4) - except for the very LAST card
		// on the page (Eldritch Evolution), which has nothing after it but
		// footer text: no trailing quantity was ever found for it, and it
		// was silently dropped - a real observed count, 60 real cards read
		// as 56 (exactly Arboreal Grazer's own quantity short, since the
		// whole page acts as one forward-shifted chain that loses one unit
		// off the front and one whole card off the tail). Locks in
		// QtyPolicy.PRECEDING_GAP/findPrecedingQty(): a genuine bounded
		// backward search so each card claims its own quantity instead of a
		// neighbor's, with no dropped card at the boundary.
		useDatabase("Stomping Ground", "Kalonian Hydra", "Birds of Paradise", "Eldritch Evolution",
				"Summoner's Pact", "Ignoble Hierarch", "Copperline Gorge", "Elvish Mystic", "Karplusan Forest",
				"Llanowar Elves", "Generator Servant", "Arboreal Grazer", "Forest", "Mountain");
		String text = readCapture("tipsymagic-hydra-list-view.txt");
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals(14, count.unique);
		assertEquals("the last card on the page must not be silently dropped", 60, count.total);
		String result = DeckTextExtractor.extractDeckSection(text);
		assertTrue("expected a result", result != null);
		assertTrue("the last card on the page must be present with its own real quantity",
				result.contains("4 Eldritch Evolution"));
		assertTrue(result.contains("4 Arboreal Grazer"));
		assertTrue(result.contains("6 Forest"));
		assertTrue(result.contains("6 Mountain"));
	}

	// ---- detectDeckMeta(): pre-filling the New Deck wizard's Name field and
	// Default Format combo when browsing a page to import. Same real
	// fixtures/databases as the sibling countRecognizedCards()/
	// extractDeckSection() tests above - detectDeckMeta() reuses the exact
	// same scan, just looks a bit further back from where it starts.

	@Test
	public void testDetectDeckMetaTcgplayer() throws Exception {
		// "Modern\nIzzet Prowess\nModern\nIzzet Prowess\n\nBy\nEthanBird" - the
		// breadcrumb's format precedes the title (the reverse of every other
		// real site checked), and the closest "Commander Bestiary" nav
		// mention (an unrelated article link) must lose to the real, closer
		// "Modern" pairing.
		useDatabase("Dragon's Rage Channeler", "Monastery Swiftspear", "Slickshot Show-Off", "Expressive Iteration",
				"Preordain", "Mutagenic Growth", "Lightning Bolt", "Lava Dart", "Violent Urge", "Mishra's Bauble",
				"Cori-Steel Cutter", "Bloodstained Mire", "Fiery Islet", "Scalding Tarn", "Wooded Foothills",
				"Mountain", "Arid Mesa", "Thundering Falls", "Steam Vents", "Consign to Memory", "Meltdown",
				"Spell Snare", "Unholy Heat", "Spell Pierce", "Surgical Extraction");
		DeckTextExtractor.DeckMeta meta = DeckTextExtractor.detectDeckMeta(readCapture("tcgplayer-izzet-prowess.txt"));
		assertEquals("Izzet Prowess", meta.title);
		assertEquals("Modern", meta.format);
	}

	@Test
	public void testDetectDeckMetaArchidekt() throws Exception {
		// this deck's own card list has a real Commander-zone section header
		// ("Commander\nQty: 1\nPrice: $0.49\nThranduil...") much closer to
		// the decklist than the page's own real format indicator further up
		// ("Thranduil, Sindarin Liege // Silvan Rally\n62 views\n16 hrs
		// ago\n0\nCommander\nLegal\n...") - both resolve to the same format
		// value ("Commander"), but only the farther one sits next to the
		// real title; the closer, chrome-heavy one must be skipped for the
		// title lookup to land correctly.
		useDatabase(ARCHIDEKT_DB);
		DeckTextExtractor.DeckMeta meta = DeckTextExtractor.detectDeckMeta(readCapture("archidekt-thranduil.txt"));
		assertEquals("Thranduil, Sindarin Liege // Silvan Rally", meta.title);
		assertEquals("Commander", meta.format);
	}

	@Test
	public void testDetectDeckMetaMoxfield() throws Exception {
		useDatabase(MOXFIELD_DB);
		DeckTextExtractor.DeckMeta meta = DeckTextExtractor.detectDeckMeta(readCapture("moxfield-jace-polymorph.txt"));
		assertEquals("Jace polymorph WIP", meta.title);
		assertEquals("Commander", meta.format);
	}

	@Test
	public void testDetectDeckMetaMoxfieldIgnoresProfilePictureAvatarLine() throws Exception {
		// favorite: https://moxfield.com/decks/6I46dLgrQkWnGlyDvpeXWg - a
		// later real capture of the SAME deck now renders an extra avatar
		// alt-text line, "DoomNoodle's Profile Picture", directly above the
		// username/title block - CLOSER to scan.start than the real title,
		// so titleEmbeddedWithPossessive() matched it first and returned
		// "Profile Picture" (the title was being reported as truncated to
		// "Jace polymorph WP" in the UI before this was tracked down - the
		// real bug was an entirely different wrong title being picked, not
		// a truncation). Fixed by blocklisting "profile picture"/"avatar"
		// in TITLE_CHROME_WORDS so this candidate is rejected and the
		// search correctly falls through to findAdjacentTitle's
		// format-anchored search instead.
		useDatabase(MOXFIELD_DB);
		DeckTextExtractor.DeckMeta meta = DeckTextExtractor
				.detectDeckMeta(readCapture("moxfield-jace-polymorph-avatar.txt"));
		assertEquals("Jace polymorph WIP", meta.title);
		assertEquals("Commander", meta.format);
	}

	@Test
	public void testDetectDeckMetaDeckstatsFindsTitleViaAllCapsTag() throws Exception {
		// this real capture's own format-shaped line reads "CASUAL" - not a
		// real Format this app tracks legality for (Format.getFormats() has
		// no "Casual") - detectDeckMeta must not guess/invent a format
		// value for it, so format stays null. But the title ("MB oldschool
		// rev 2") sits directly above "CASUAL" with no gap - the same real
		// shape as a recognized format tag (Moxfield's own "COMMANDER"),
		// just for a word that isn't a real tracked format - the short
		// ALL-CAPS-tag fallback still finds it even though matchFormatAlias
		// never does.
		useDatabase("Graveborn Muse", "Greater Harvester", "Hypnotic Specter", "Nantuko Shade", "Undead Gladiator",
				"Contagion", "Dark Ritual", "Consume Spirit", "Duress", "Haunting Echoes", "Hymn to Tourach",
				"Mutilate", "Extraplanar Lens", "Phyrexian Arena", "Snow-Covered Swamp", "Avatar of Woe",
				"Decree of Pain", "Diabolic Edict", "Extirpate", "Funeral Charm", "Gatekeeper of Malakir",
				"Ghastly Demise", "Ghost Quarter", "Grafted Wargear", "Infest", "Mind Sludge", "Oversold Cemetery",
				"Plague Spitter", "Promise of Power", "Smother", "Toshiro Umezawa", "Umezawa's Jitte",
				"Withered Wretch");
		DeckTextExtractor.DeckMeta meta = DeckTextExtractor.detectDeckMeta(readCapture("deckstats-mb-oldschool.txt"));
		assertEquals("MB oldschool rev 2", meta.title);
		assertEquals(null, meta.format);
	}

	@Test
	public void testDetectDeckMetaMtgtop8IgnoresOwnFormatFilterNavBar() throws Exception {
		// this real page's own site-wide nav is literally a format-filter
		// menu ("STANDARD\nPIONEER\nMODERN\nLEGACY\nVINTAGE\n...\nPAUPER\n
		// cEDH\nDUEL COMMANDER\nPREMODERN\n...") at the very top of the
		// page - far closer to the top than to this particular deck's own
		// real header ("#16 Rakdos Aggro - DragonFodder\nModern\n63
		// players..."), but well within a naive unbounded search. Locks in
		// that the closest-to-the-decklist match always wins over a nearer-
		// the-top one, so the nav bar's own "STANDARD" is never mistaken for
		// this Modern deck's format.
		useDatabase("Blood Crypt", "Bloodstained Mire", "Mountain", "Polluted Delta", "Raucous Theater", "Swamp",
				"Wooded Foothills", "Bloodghast", "Dragon's Rage Channeler", "Nethergoyf", "Orcish Bowmasters",
				"Stitcher's Supplier", "Corrupted Conviction", "Faithless Looting", "Fatal Push", "Flare of Malice",
				"Lightning Bolt", "Rakdos Charm", "Village Rites", "Mishra's Bauble", "Brotherhood's End",
				"Engineered Explosives", "Magus of the Moon", "Obsidian Charmaw", "Vexing Bauble", "Lands",
				"Creatures", "Instants", "Sorceries", "Spells");
		DeckTextExtractor.DeckMeta meta = DeckTextExtractor.detectDeckMeta(readCapture("mtgtop8-rakdos-aggro.txt"));
		assertEquals("#16 Rakdos Aggro - DragonFodder", meta.title);
		assertEquals("Modern", meta.format);
	}

	@Test
	public void testDetectDeckMetaTappedOut() throws Exception {
		// "Fast Gishath Dino-Vomit\n\nCommander / EDH Bracket 3 Dinosaurs RGW
		// (Naya)\n\ncrowfather1307" - the format line has real text beyond
		// just the format name itself (a prefix match, not a whole-line
		// one), and this real capture's own deck-internal Commander header
		// ("Commander (1)\nCommander: Gishath...") sits well AFTER the
		// decklist's own start, so it never competes with the real, earlier
		// page-header one the way Archidekt's/Moxfield's do.
		useDatabase("Apex Altisaur", "Birds of Paradise", "Bloom Tender", "Bonehoard Dracosaur", "Bronzebeak Foragers",
				"Curious Altisaur", "Delighted Halfling", "Earthshaker Dreadmaw", "Etali, Primal Conqueror",
				"Etali, Primal Storm", "Faeburrow Elder", "Fanatic of Rhonas", "Ghalta and Mavren",
				"Ghalta, Primal Hunger", "Ghalta, Stampede Tyrant", "Goring Ceratops", "Hunting Velociraptor",
				"Ilysian Caryatid", "Kinjalli's Caller", "Kinjalli's Sunwing", "Kogla and Yidaro",
				"Marauding Raptor", "Pantlaza, Sun-Favored", "Polyraptor", "Quartzwood Crasher", "Regal Behemoth",
				"Regisaur Alpha", "Roaming Throne", "Scion of Calamity", "Selvala, Heart of the Wilds",
				"Shaman of Forgotten Ways", "Silverclad Ferocidons", "Somberwald Sage", "Temple Altisaur",
				"The Tarrasque", "Topiary Stomper", "Trapjaw Tyrant", "Trumpeting Carnosaur", "Vaultborn Tyrant",
				"Verdant Sun's Avatar", "Wakening Sun's Avatar", "Wayward Swordtooth", "Whisperer of the Wilds",
				"Zacama, Primal Calamity", "Arid Mesa", "Battlefield Forge", "Boseiju, Who Endures",
				"Bountiful Promenade", "Brushland", "Castle Garenbrig", "Cavern of Souls", "Clifftop Retreat",
				"Command Tower", "Commercial District", "Elegant Parlor", "Evendo, Waking Haven", "Exotic Orchard",
				"Forest", "Hushwood Verge", "Jetmir's Garden", "Karplusan Forest", "Krosan Verge", "Lush Portico",
				"Mountain", "Overgrown Farmland", "Plateau", "Rockfall Vale", "Rootbound Crag", "Sacred Foundry",
				"Savannah", "Spectator Seating", "Spire Garden", "Stomping Ground", "Sunbillow Verge",
				"Sunhome, Fortress of the Legion", "Sunpetal Grove", "Taiga", "Temple Garden", "Thornspire Verge",
				"Windswept Heath", "Wooded Foothills", "Gishath, Sun's Avatar", "Aura Shards", "Elemental Bond",
				"Garruk's Uprising", "Guardian Project", "Overgrowth", "Pandemonium", "Rhythm of the Wild",
				"Shadow in the Warp", "Sylvan Library", "Utopia Sprawl", "Warstorm Surge", "Wild Growth",
				"Herald's Horn", "Herd Heirloom", "Sol Ring", "The Great Henge", "Urza's Incubator", "Primal Surge",
				"Arbor Elf", "Atzocan Seer", "Knight of the Stampede", "Otepec Huntmaster", "Ripjaw Raptor",
				"Wrathful Raptors", "Descendants' Path", "Lurking Predators", "Mirari's Wake", "Sundown Pass",
				"Monster Manual");
		DeckTextExtractor.DeckMeta meta = DeckTextExtractor
				.detectDeckMeta(readCapture("tappedout-gishath-dino-vomit.txt"));
		assertEquals("Fast Gishath Dino-Vomit", meta.title);
		assertEquals("Commander", meta.format);
	}

	@Test
	public void testDetectDeckMetaTipsymagicTitleOnlyNoFormatStated() throws Exception {
		// this real capture never states a format anywhere on the page - a
		// deck title alone (no adjacent format line, no "by" line either)
		// still needs its own extraction path, independent of the format
		// heuristic entirely.
		useDatabase("Stomping Ground", "Kalonian Hydra", "Birds of Paradise", "Eldritch Evolution",
				"Summoner's Pact", "Ignoble Hierarch", "Copperline Gorge", "Elvish Mystic", "Karplusan Forest",
				"Llanowar Elves", "Generator Servant", "Arboreal Grazer", "Forest", "Mountain");
		DeckTextExtractor.DeckMeta meta = DeckTextExtractor
				.detectDeckMeta(readCapture("tipsymagic-hydra-flat-sorted.txt"));
		assertEquals("Hydra", meta.title);
		assertEquals(null, meta.format);
	}

	/** Shared by {@link #testRealTipsymagicLordHighArtificerListView} and
	 *  {@link #testRealTipsymagicLordHighArtificerFlatSortedView} - the real
	 *  81-card decklist (80 unique names + Island at qty 21) behind
	 *  https://tipsymagic.com/view_deck/7897-lord-high-artificer-copy . */
	private static final String[] LORD_HIGH_ARTIFICER_DB = { "Urza, Lord High Artificer", "Tezzeret the Seeker",
			"Tezzeret, Betrayer of Flesh", "Arcbound Crusher", "Broodstar", "Chrome Host Seedshark",
			"Cyberdrive Awakener", "Deadeye Navigator", "Etherium Sculptor", "Foundry Inspector", "Kappa Cannoneer",
			"Metalwork Colossus", "Omnath, Locus of the Void", "Ornithopter", "Padeem, Consul of Innovation",
			"Phantasmal Image", "Phyrexian Metamorph", "Research Thief", "Sai, Master Thopterist", "Spire Golem",
			"Steel Hellkite", "Thought Monitor", "Wurmcoil Engine", "Arcane Signet", "Conjurer's Closet",
			"Everflowing Chalice", "Manifold Key", "Mechtitan Core", "Mishra's Bauble", "Retrofitter Foundry",
			"Simulacrum Synthesizer", "Sol Ring", "Soul-Guide Lantern", "Spellbook", "Tormod's Crypt",
			"Unwinding Clock", "Welding Jar", "Whispersilk Cloak", "Academy Ruins", "Buried Ruin",
			"Darksteel Citadel", "Island", "Mishra's Workshop", "Otawara, Soaring City", "Seat of the Synod",
			"Secluded Starforge", "Tomb of the Spirit Dragon", "Urza's Saga", "Arcum Dagsson",
			"An Offer You Can't Refuse", "Arcane Denial", "Blustersquall", "Boomerang", "Counterspell",
			"Desynchronization", "Dramatic Reversal", "Echoing Truth", "Fierce Guardianship", "Force of Negation",
			"Ghostly Flicker", "Hide on the Ceiling", "Mental Misstep", "Muddle the Mixture", "Mystic Reflection",
			"Mystical Tutor", "Narset's Reversal", "Pongify", "Snap", "Swan Song", "Unsummon", "Vapor Snag",
			"Voyage's End", "Fabricate", "Preordain", "Ravenform", "Thoughtcast", "Void Snare",
			"Imprisoned in the Moon", "Mirrormade", "Rhystic Study", "Thopter Spy Network" };

	@Test
	public void testRealTipsymagicLordHighArtificerListView() throws Exception {
		// https://tipsymagic.com/view_deck/7897-lord-high-artificer-copy -
		// unlike the "Hydra" deck used for every other tipsymagic test
		// above, this real deck has an actual Sideboard section (Arcum
		// Dagsson) AND is genuinely Commander-format. Two real gaps this
		// exposed: (1) tipsymagic.com's own category header reads
		// "Commanders" (PLURAL) - isCommanderHeader() only ever recognized
		// the singular "Commander", so the universal Commander-no-sideboard
		// rule never even recognized this as a Commander deck; (2) once (1)
		// is fixed, this site puts Sideboard in the MIDDLE of its own fixed
		// category order (Commanders, Planeswalkers, ..., Lands, Sideboard,
		// Other, Instants, Sorceries, Enchantments) - a hard stop at
		// Sideboard (correct for every other real site checked, where it's
		// always the LAST section) would have also dropped the real,
		// legitimate Instants/Sorceries/Enchantments sections that come
		// after it here. The page's own "Cards 101" (summing every category
		// header's own count, Sideboard included) minus Arcum Dagsson (not
		// a legal Commander sideboard card) is the real total, 100.
		useDatabase(LORD_HIGH_ARTIFICER_DB);
		String text = readCapture("tipsymagic-lord-high-artificer-list-view.txt");
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals(80, count.unique);
		assertEquals("Arcum Dagsson (Sideboard) must be excluded - Commander decks have no legal sideboard", 100,
				count.total);
		String result = DeckTextExtractor.extractDeckSection(text);
		assertTrue("expected a result", result != null);
		assertTrue("Arcum Dagsson must not appear anywhere in the output", !result.contains("Arcum Dagsson"));
		assertTrue("the real Instants section (after Sideboard in this site's own category order) must survive",
				result.contains("Counterspell"));
		assertTrue("the real Sorceries section (after Sideboard) must survive", result.contains("Preordain"));
		assertTrue("the real Enchantments section (after Sideboard) must survive", result.contains("Rhystic Study"));
	}

	@Test
	public void testRealTipsymagicLordHighArtificerFlatSortedView() throws Exception {
		// same real deck as above, but its "Flat Sorted View" table shape
		// (see matchTableRow()) - there is no separate "Sideboard" HEADER
		// line to toggle a skip zone on here at all: each row tags its own
		// section via a table COLUMN instead ("Arcum Dagsson\tSideboard\t
		// ...", "Urza, Lord High Artificer\tCommander\t..."). This capture
		// also proves the ordering hazard that ruled out a simpler,
		// progressive isCommanderDeck check: rows are sorted by PRICE, and
		// Arcum Dagsson ($16.78) sorts ABOVE Urza ($15.63) - the Sideboard
		// row appears in document order BEFORE the row that reveals this is
		// even a Commander deck.
		useDatabase(LORD_HIGH_ARTIFICER_DB);
		String text = readCapture("tipsymagic-lord-high-artificer-flat-sorted.txt");
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals(80, count.unique);
		assertEquals(100, count.total);
		String result = DeckTextExtractor.extractDeckSection(text);
		assertTrue("expected a result", result != null);
		assertTrue("Arcum Dagsson must not appear anywhere in the output", !result.contains("Arcum Dagsson"));
		assertTrue("the table's own junk columns (Section/Type/Rarity/MV/Color/Price) must never leak into the "
				+ "resolved name", !result.contains("Main Deck") && !result.contains("Mythic") && !result.contains("$"));
		assertTrue(result.contains("Urza, Lord High Artificer"));
		assertTrue(result.contains("21 Island"));
	}

	@Test
	public void testRealTipsymagicLordHighArtificerCardView() throws Exception {
		// same real deck, "Card view": every card except the Commander has a
		// "Name\n-\n1\n+" stepper (TRAILING policy finds each one's own "1"
		// fine), but the Commander's own row is "Commanders\n- 1\nAll decks
		// with this commander\nUrza, Lord High Artificer\nPlaneswalkers\n..."
		// - NO stepper at all under Urza, going straight to the next
		// category header. TRAILING's forward search hits Tezzeret the
		// Seeker's own real match first and gives up with no quantity
		// found, and the old precededByCommanderHeader check only looked at
		// the SINGLE immediately preceding line ("All decks with this
		// commander" - not a header) - two lines short of "Commanders"
		// itself. Urza was silently dropped entirely (not even counted as
		// qty-less bare text) - a real observed count, 100 cards read as 99.
		// Locks in reusing startsWithCommanderHeader's own bounded backward
		// scan (already proven against deckbox.org/Archidekt/Moxfield) for
		// this check too, instead of a separate, stricter single-line one.
		useDatabase(LORD_HIGH_ARTIFICER_DB);
		String text = readCapture("tipsymagic-lord-high-artificer-card-view.txt");
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals(80, count.unique);
		assertEquals("the commander (Urza) must not be silently dropped", 100, count.total);
		String result = DeckTextExtractor.extractDeckSection(text);
		assertTrue("expected a result", result != null);
		assertTrue("the commander must be present with its own implicit quantity of 1",
				result.contains("Urza, Lord High Artificer"));
		assertTrue("Arcum Dagsson must not appear anywhere in the output", !result.contains("Arcum Dagsson"));
	}

	@Test
	public void testRealArchidektPreconCaptureWithReprintStamps() throws Exception {
		// https://archidekt.com/decks/26549829/calling_all_angels_foundations_commander
		// - a real Archidekt precon deck, a different per-card shape than
		// ARCHIDEKT_DB's own Thranduil capture: every card also has its own
		// "<Name> (fdc) <collector#>" reprint/printing stamp directly above
		// its bare qty ("Austere Command (fdc) 18\n1"). resolveName()'s
		// trim-by-WORD loop happily strips "(fdc)"/the number and resolves
		// this as a SECOND, independent match for the same card - which by
		// itself would only double-count an already-found card, but is also
		// directly responsible for a much worse bug: findTrailingQty() stops
		// at the FIRST other raw match it reaches, so the card's own CLEAN
		// name line (several lines above, rules text and a type line in
		// between) always hits its own reprint stamp first and gives up
		// with NO quantity found - only the stamp line, much closer to the
		// "1", ever actually got a quantity. The clean name going unmatched
		// added one more non-neutral gap line at every single card, and
		// this real deck's own commander (missing all 3 price sources,
		// "$----" x3 - NOT recognized as a neutral price, since it has no
		// digit) plus Archidekt's own "(CTRL to add secondary)" category-
		// transition widget was enough to tip the Commander->Enchantment
		// transition alone over MAX_GAP: a real observed count, 100 cards
		// read as 55 (and worse on some other real view/zoom states).
		// Locks in three fixes together: REPRINT_STAMP rejects the stamp
		// line outright (so the clean name's own forward search reaches the
		// real "1" instead of giving up), STANDALONE_PRICE also accepts
		// Archidekt's own "$----" no-data placeholder, and scan()'s own
		// run-continuity gap counting treats a category/type-header word as
		// neutral (expected structural content, not "wandered off the
		// decklist" noise) - without the gap fix alone this still failed
		// (removing the phantom stamp match made the FIRST transition
		// worse, not better, since it had been the only thing keeping that
		// specific gap under budget).
		useDatabase("Giada, Font of Hope", "Always Watching", "Angelic Destiny", "Court of Grace", "Grasp of Fate",
				"Search the Premises", "Austere Command", "Cleansing Nova", "Cut a Deal", "Day of Judgment",
				"Defy Death", "Exorcise", "Secret Rendezvous", "Arcane Signet", "Commander's Sphere",
				"Endless Atlas", "Heraldic Banner", "Marble Diamond", "Mind Stone", "Patchwork Banner", "Sol Ring",
				"Swiftfoot Boots", "Tome of Legends", "Vanquisher's Banner", "Destroy Evil", "Fateful Absence",
				"Invoke the Divine", "Swords to Plowshares", "Valorous Stance", "Angel of Finality",
				"Angel of the Ruins", "Angel of Vitality", "Angelic Field Marshal", "Angelic Sleuth",
				"Archangel of Tithes", "Bishop of Wings", "Dazzling Angel", "Emeria Shepherd", "Exemplar of Light",
				"Firemane Commando", "Herald of Eternal Dawn", "Herald of War", "Inspiring Overseer",
				"Linvala, the Preserver", "Lyra Dawnbringer", "Merchant of Truth", "Metallic Mimic",
				"Metropolis Reformer", "Norn's Choirmaster", "Reya Dawnbringer", "Righteous Valkyrie",
				"Segovian Angel", "Sephara, Sky's Blade", "Seraph of the Sword", "Serra Avenger",
				"Speaker of the Heavens", "Starnheim Aspirant", "Sunblast Angel", "Thraben Watcher",
				"Vanguard Seraph", "Wojek Investigator", "Youthful Valkyrie", "Bonders' Enclave", "Plains",
				"Radiant Fountain", "Secluded Steppe", "Seraph Sanctuary", "Temple of the False God", "War Room");
		String text = readCapture("archidekt-calling-all-angels.txt");
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals(69, count.unique);
		assertEquals("the page's own stated \"Size: 100\"/per-category counts summing to 100", 100, count.total);
		String result = DeckTextExtractor.extractDeckSection(text);
		assertTrue("expected a result", result != null);
		assertTrue("the commander (Giada) must be wrapped in its own Sideboard/Deck pair",
				result.contains("Sideboard") && result.contains("1 Giada, Font of Hope"));
		assertTrue("no card's own reprint-stamp line must leak into the output as a bogus duplicate",
				!result.contains("(fdc)"));
		// no duplicate lines - each real card appears exactly once, never
		// once via its clean name AND again via its own reprint stamp
		java.util.List<String> lines = java.util.Arrays.asList(result.split("\n"));
		assertEquals("no duplicate lines", new java.util.HashSet<>(lines).size(), lines.size());
	}

	@Test
	public void testDetectDeckMetaTappedOutStripsQuotedTitle() throws Exception {
		// favorite: https://tappedout.net/mtg-decks/grumgully-gultch/ - a
		// real capture whose own title renders wrapped in a literal pair of
		// quote characters on its own line, "\"Take it and be grateful\"" -
		// every title heuristic returns the display line (or a substring of
		// it) verbatim, with no quote-stripping of its own, so the quotes
		// rode along into the detected title unchanged (a real observed
		// bug: the New Deck wizard's Name field filled in with the quote
		// marks still attached, reported by the user as the title "not
		// found"). Locks in stripSurroundingQuotes(), the final cleanup
		// step in detectDeckMeta().
		useDatabase("Duskshell Crawler", "Frog Butler", "Goblin Anarchomancer", "Goblin Medics", "Guttersnipe",
				"Ironshell Beetle", "Kazandu Nectarpot", "Mogg War Marshal", "Nightshade Dryad", "Noxious Newt",
				"Poison Dart Frog", "Pollenbright Druid", "Riot Piker", "Sarpadian Simulacrum", "Scarecrow Guide",
				"Scarwood Goblins", "Scuzzback Marauders", "Scuzzback Scrapper", "Shopkeeper's Bane",
				"Springbloom Druid", "Tackle Artist", "Temperamental Oozewagg", "Thornscape Familiar",
				"Witty Roastmaster", "Careful Cultivation", "Gift of Paradise", "Nature's Embrace",
				"Treefolk Umbra", "Arachnoid Adaptation", "Band Together", "Bull's Strength", "Charge Through",
				"Duel for Dominance", "Gift of the Viper", "Lifecrafter's Gift", "Manhole Missile",
				"Master's Rebuke", "Mine Collapse", "Pedal to the Metal", "Pit Fight", "Prizefight",
				"Ram Through", "Raze the Effigy", "Repopulate", "Shock", "Steady Aim", "Tamiyo's Safekeeping",
				"Tenderize", "Brokers Hideout", "Cabaretti Courtyard", "Command Tower", "Evolving Wilds",
				"Forest", "Maestros Theater", "Mountain", "Opal Palace", "Riveteers Overlook",
				"Grumgully, the Generous", "Abundant Harvest", "Crack Open", "Destructive Tampering",
				"Fade into Antiquity", "Go for Blood", "Longstalk Brawl", "Mage Duel", "Mutiny",
				"Over the Edge", "Rampant Growth", "Renegade Tactics", "Rumbling Rockslide",
				"Scale the Heights", "Wild Instincts");
		DeckTextExtractor.DeckMeta meta = DeckTextExtractor
				.detectDeckMeta(readCapture("tappedout-grumgully-quoted-title.txt"));
		assertEquals("Take it and be grateful", meta.title);
	}

	@Test
	public void testRealTappedOutGrumgullyQuotedTitleCount() throws Exception {
		// same real capture as testDetectDeckMetaTappedOutStripsQuotedTitle() -
		// this deck ("Take it and be grateful", Grumgully, the Generous) has
		// a real, 8-card Sideboard section AND a real, 25-card Maybeboard
		// section after it, several of whose own card names repeat ones
		// already in the real deck (Frog Butler, Guttersnipe, Noxious Newt,
		// Manhole Missile, Tenderize). The page's own category headers sum
		// to 100 (24 Creature + 4 Enchantment + 20 Instant + 37 Land + 1
		// Commander + 14 Sorcery), matching the standard 100-card Commander
		// deck convention once the real Sideboard/Maybeboard are excluded
		// (Commander has no official sideboard - see DeckTextExtractor's own
		// header). 72 unique names once Land's own 9 rows (two of which,
		// Forest at 20x and Mountain at 10x, carry most of the Land
		// category's 37-card total) are counted once each.
		useDatabase("Duskshell Crawler", "Frog Butler", "Goblin Anarchomancer", "Goblin Medics", "Guttersnipe",
				"Ironshell Beetle", "Kazandu Nectarpot", "Mogg War Marshal", "Nightshade Dryad", "Noxious Newt",
				"Poison Dart Frog", "Pollenbright Druid", "Riot Piker", "Sarpadian Simulacrum", "Scarecrow Guide",
				"Scarwood Goblins", "Scuzzback Marauders", "Scuzzback Scrapper", "Shopkeeper's Bane",
				"Springbloom Druid", "Tackle Artist", "Temperamental Oozewagg", "Thornscape Familiar",
				"Witty Roastmaster", "Careful Cultivation", "Gift of Paradise", "Nature's Embrace",
				"Treefolk Umbra", "Arachnoid Adaptation", "Band Together", "Bull's Strength", "Charge Through",
				"Duel for Dominance", "Gift of the Viper", "Lifecrafter's Gift", "Manhole Missile",
				"Master's Rebuke", "Mine Collapse", "Pedal to the Metal", "Pit Fight", "Prizefight",
				"Ram Through", "Raze the Effigy", "Repopulate", "Shock", "Steady Aim", "Tamiyo's Safekeeping",
				"Tenderize", "Brokers Hideout", "Cabaretti Courtyard", "Command Tower", "Evolving Wilds",
				"Forest", "Maestros Theater", "Mountain", "Opal Palace", "Riveteers Overlook",
				"Grumgully, the Generous", "Abundant Harvest", "Crack Open", "Destructive Tampering",
				"Fade into Antiquity", "Go for Blood", "Longstalk Brawl", "Mage Duel", "Mutiny",
				"Over the Edge", "Rampant Growth", "Renegade Tactics", "Rumbling Rockslide",
				"Scale the Heights", "Wild Instincts");
		String text = readCapture("tappedout-grumgully-quoted-title.txt");
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals("real Land(9 names)+Creature(24)+Enchantment(4)+Instant(20)+Commander(1)+Sorcery(14) - the "
				+ "real Sideboard/Maybeboard sections (sharing several of the same card names) must not inflate this",
				72, count.unique);
		assertEquals("a standard 100-card Commander deck once the real Sideboard/Maybeboard are excluded", 100,
				count.total);
	}

	/** Shared by all four "View as" layout tests below - a real Archidekt
	 *  capture ("Anje, Maid of Dishonor") reported as showing "100 or 101
	 *  cards detected" depending on which of the site's 4 "View as" layouts
	 *  was selected. The actual root cause (found from a real screenshot the
	 *  user sent of the site's own UI, not from this file's own earlier
	 *  guesses): this deck defines its own custom category, "Blood", to
	 *  group its blood-tribal subtheme (Arterial Alchemy, Bloodcrazed
	 *  Socialite, Ceremonial Knife, ...) - rendered exactly like a real
	 *  "Commander\nQty: N\nPrice: $X" section header, just with a
	 *  user-chosen name instead of a fixed keyword. "Blood" is ALSO a real
	 *  Scryfall Token Artifact name (confirmed directly against the bulk
	 *  card data), and this category header happens to sit right after the
	 *  Commander section's own category-transition widget - within
	 *  startsWithCommanderHeader()'s COMMANDER_LOOKBACK_LIMIT of the real
	 *  "Commander" header above it. Without recognizing the widget as a
	 *  section boundary (see isCategoryTransitionWidget()'s own header),
	 *  that backward scan walked straight past it and wrongly trusted
	 *  "Blood" as the commander's own bare card line, adding it as a
	 *  phantom 1-copy card. Every earlier investigation of "100 or 101"
	 *  THIS FILE RAN never seeded "Blood" itself into the test database
	 *  (only "Blood Petal Celebrant"/"Blood Artist"/etc. - real card names
	 *  that happen to start with the word, never the bare token itself),
	 *  so none of them ever actually exercised this bug - see the "Blood"
	 *  entry seeded below, deliberately matching the real production
	 *  database this time. */
	private static final String[] ANJE_DB = { "Anje, Maid of Dishonor", "Blood", "Arterial Alchemy", "Belligerent Guest",
			"Blood Petal Celebrant", "Bloodcrazed Socialite", "Bloodtithe Harvester", "Ceremonial Knife",
			"Falkenrath Celebrants", "Glass-Cast Heart", "Gluttonous Guest", "Kamber, the Plunderer",
			"Olivia's Attendants", "Sanguine Statuette", "Vampire's Kiss", "Voldaren Epicure", "Molten Echoes",
			"Blade of the Bloodchief", "Cordial Vampire", "Necropolis Regent", "Rakish Heir", "Arterial Flow",
			"Blood Artist", "Creeping Bloodsucker", "Falkenrath Noble", "Kalastria Highborn",
			"Malakir Bloodwitch", "Sanctum Seeker", "Sanguine Bond", "Sorin, Imperious Bloodlord",
			"Vampire Sovereign", "Vindictive Vampire", "Vito, Thorn of the Dusk Rose", "Ancient Craving",
			"Pointed Discussion", "Read the Bones", "Sign in Blood", "Blood Hypnotist",
			"Strefan, Maurer Progenitor", "Voldaren Bloodcaster // Bloodbat Summoner", "Akoum Refuge",
			"Bloodfell Caves", "Bonders' Enclave", "Command Tower", "Evolving Wilds", "Foreboding Ruins",
			"Geier Reach Sanitarium", "Mountain", "Myriad Landscape", "Path of Ancestry", "Rakdos Carnarium",
			"Reliquary Tower", "Rogue's Passage", "Scavenger Grounds", "Shadowblood Ridge",
			"Smoldering Marsh", "Swamp", "Temple of Malice", "Temple of the False God",
			"Terramorphic Expanse", "Unclaimed Territory", "Voldaren Estate", "Witch's Cottage",
			"Blood Tribute", "Sangromancer", "Vampire Nighthawk", "Lightning Greaves", "Arcane Signet",
			"Charcoal Diamond", "Commander's Sphere", "Fire Diamond", "Nirkana Revenant", "Rakdos Signet",
			"Sol Ring", "Blood Fountain", "Anowon, the Ruin Sage", "Butcher of Malakir", "Feast of Blood",
			"Lacerate Flesh", "Markov Enforcer", "Olivia's Wrath", "Patron of the Vein",
			"Vampires' Vengeance", "Vengeant Vampire", "Bloodline Keeper // Lord of Lineage",
			"Diabolic Tutor", "Captivating Vampire", "Blood Feud", "Falkenrath Forebear",
			"Ivora, Insatiable Heir", "Restless Bloodseeker // Bloodsoaked Reveler", "Stromkirk Captain" };

	@Test
	public void testRealArchidektAnjeMaidOfDishonor() throws Exception {
		// favorite: https://archidekt.com/decks/13837536/anje_maid_of_dishonor
		// - see ANJE_DB's own header.
		useDatabase(ANJE_DB);
		String text = readCapture("archidekt-anje-maid-of-dishonor.txt");
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals("the page's own \"Size: 100\"", 86, count.unique);
		assertEquals(100, count.total);
		String result = DeckTextExtractor.extractDeckSection(text);
		assertTrue("expected a result", result != null);
		assertTrue("the Maybeboard's own cards must not appear anywhere in the output",
				!result.contains("Blood Feud") && !result.contains("Stromkirk Captain"));
		DeckTextExtractor.DeckMeta meta = DeckTextExtractor.detectDeckMeta(text);
		assertEquals("Anje, Maid of Dishonor", meta.title);
		assertEquals("Commander", meta.format);
	}

	@Test
	public void testRealArchidektAnjeReorderedCategoriesWithTokensExtras() throws Exception {
		// same real deck as testRealArchidektAnjeMaidOfDishonor() above, a
		// later capture where Archidekt's own category order had reshuffled
		// (a real observed behavior: this site doesn't keep a stable
		// category order between page loads) and the page's own "Deck
		// Tokens & Extras" panel was expanded, adding 4 more token-shaped
		// lines AFTER the Maybeboard stop-section - see ANJE_DB's own
		// header.
		useDatabase(ANJE_DB);
		String text = readCapture("archidekt-anje-reordered-with-tokens-extras.txt");
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals("the page's own \"Size: 100\" - unaffected by category reordering or the trailing Tokens panel",
				86, count.unique);
		assertEquals(100, count.total);
		String result = DeckTextExtractor.extractDeckSection(text);
		assertTrue("expected a result", result != null);
		assertTrue("the Maybeboard's own cards must not appear anywhere in the output",
				!result.contains("Blood Feud") && !result.contains("Stromkirk Captain"));
		assertTrue("the trailing Deck Tokens & Extras panel's own token definitions must not leak in either",
				!result.contains("Artifact Token") && !result.contains("Creature Token"));
		DeckTextExtractor.DeckMeta meta = DeckTextExtractor.detectDeckMeta(text);
		assertEquals("Anje, Maid of Dishonor", meta.title);
		assertEquals("Commander", meta.format);
	}

	@Test
	public void testRealArchidektAnjeGridViewWithOracleText() throws Exception {
		// same real deck again, this time Archidekt's "grid view" (the
		// site's own default) - every card's full oracle text sits between
		// its name and its qty/price block, and a modal double-faced card
		// (Voldaren Bloodcaster // Bloodbat Summoner) shows its BARE FRONT
		// FACE name as the oracle-text header line, with the combined "A //
		// B" form only appearing inside a reprint-stamp line (correctly
		// rejected elsewhere). Without useDatabase()'s own faces()-mirroring
		// fix (see its header), this card contributed zero matches and a
		// 24-line gap wide enough to break the matched run right there,
		// making a later, shorter, gap-free run (the Land section) win as
		// "best" instead - silently dropping everything before it (a real
		// observed symptom: "100 or 101 cards detected" and "Detected name:
		// not detected", though the exact live mechanism was never
		// reproduced against real captured text with the REAL production
		// database, which already splits DFC faces the same way).
		useDatabase(ANJE_DB);
		String text = readCapture("archidekt-anje-grid-view-with-oracle-text.txt");
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals(86, count.unique);
		assertEquals("the page's own \"Size: 100\" - the DFC card's bare front-face oracle-text "
				+ "header must not create a run-breaking gap", 100, count.total);
		String result = DeckTextExtractor.extractDeckSection(text);
		assertTrue("expected a result", result != null);
		assertTrue("the commander must be present, not cut off by a later, gap-free run winning instead",
				result.contains("Anje, Maid of Dishonor"));
		assertTrue("the Maybeboard's own cards must not appear anywhere in the output",
				!result.contains("Blood Feud") && !result.contains("Stromkirk Captain"));
		DeckTextExtractor.DeckMeta meta = DeckTextExtractor.detectDeckMeta(text);
		assertEquals("Anje, Maid of Dishonor", meta.title);
		assertEquals("Commander", meta.format);
	}

	@Test
	public void testRealArchidektAnjeStackedView() throws Exception {
		// same real deck again, this time Archidekt's "stacked view" - a
		// FOURTH distinct category order from this one real deck alone
		// (Commander, Counters, Draw, Land, Ramp, Tokens, Blood, Discard,
		// Evasion, Lifegain, Recursion, Tutor, Copy, Drain, Flying/Reach,
		// Protection, Removal, Typal, Maybeboard - confirming Archidekt's
		// own category order is NOT stable between page loads/views, so
		// nothing here may ever assume a fixed order), and oracle text
		// interleaved like grid view, but with PRICE lines appearing only
		// ONCE per category (after the LAST card), not after every card -
		// a real observed report said this view (among others) showed "101
		// cards" and no detected name, though that was never reproduced
		// against this exact real captured text with a database that
		// matches what a real user has (see ANJE_DB's own header for the
		// wider investigation across all 4 of this site's layouts).
		useDatabase(ANJE_DB);
		String text = readCapture("archidekt-anje-stacked-view.txt");
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals(86, count.unique);
		assertEquals("the page's own \"Size: 100\"", 100, count.total);
		String result = DeckTextExtractor.extractDeckSection(text);
		assertTrue("expected a result", result != null);
		assertTrue("the commander must be present",
				result.contains("Sideboard") && result.contains("1 Anje, Maid of Dishonor"));
		assertTrue("the Maybeboard's own cards must not appear anywhere in the output",
				!result.contains("Blood Feud") && !result.contains("Stromkirk Captain"));
		DeckTextExtractor.DeckMeta meta = DeckTextExtractor.detectDeckMeta(text);
		assertEquals("Anje, Maid of Dishonor", meta.title);
		assertEquals("Commander", meta.format);
	}

	@Test
	public void testDetectDeckMetaAetherhubTitleAndFormatOnSameLine() throws Exception {
		// https://aetherhub.com/Deck/... (a real Modern tournament-result
		// page) - unlike every other real site checked, AetherHub renders
		// its own title and format TOGETHER on one line: "Modern - Boros
		// Aggro". findAdjacentTitle()'s separate-line search used to land on
		// whatever sits immediately above/below that one line instead - a
		// real capture's own site-wide nav has "Apps" (its Apps/tools menu
		// item) directly above it, and that got wrongly picked as the
		// title (a real observed bug: the New Deck wizard's Name field
		// filled in with "Apps"). titleEmbeddedWithFormat() now checks the
		// format line itself first, confirming the split is really right
		// after the format (not just any hyphen) by requiring the prefix
		// alone to also resolve as a format on its own.
		useDatabase("Ajani, Nacatl Pariah", "Guide of Souls", "Ragavan, Nimble Pilferer", "Seasoned Pyromancer",
				"Voice of Victory", "Ocelot Pride", "Galvanic Discharge", "Goblin Bombardment", "Lightning Bolt",
				"Blood Moon", "Arena of Glory", "Mountain", "Marsh Flats", "Elegant Parlor", "Flooded Strand",
				"Arid Mesa", "Sacred Foundry", "Plains", "Containment Priest", "Obsidian Charmaw", "Meltdown",
				"High Noon", "The Legend of Roku");
		DeckTextExtractor.DeckMeta meta = DeckTextExtractor.detectDeckMeta(readCapture("aetherhub-boros-aggro.txt"));
		assertEquals("Boros Aggro", meta.title);
		assertEquals("Modern", meta.format);
	}

	@Test
	public void testRealAetherhubSideHeaderRecognizedAsSideboard() throws Exception {
		// same real capture as testDetectDeckMetaAetherhubTitleAndFormatOnSameLine
		// - AetherHub's own sideboard header reads "Side 13 cards (5
		// distinct)", not "Sideboard" - isSideboardHeader() only ever
		// recognized the latter, so this site's sideboard cards still got
		// imported (nothing stopped the scan), just without the "Sideboard"
		// tag in extractDeckSection()'s output - hasSideboardMarker() (in
		// AbstractCardListImportPage, checking for a literal "Sideboard"
		// line) never found one, so the New Deck wizard's "Also create a
		// Sideboard" checkbox never auto-checked itself even though the
		// deck genuinely has one.
		useDatabase("Ajani, Nacatl Pariah", "Guide of Souls", "Ragavan, Nimble Pilferer", "Seasoned Pyromancer",
				"Voice of Victory", "Ocelot Pride", "Galvanic Discharge", "Goblin Bombardment", "Lightning Bolt",
				"Blood Moon", "Arena of Glory", "Mountain", "Marsh Flats", "Elegant Parlor", "Flooded Strand",
				"Arid Mesa", "Sacred Foundry", "Plains", "Containment Priest", "Obsidian Charmaw", "Meltdown",
				"High Noon", "The Legend of Roku");
		String text = readCapture("aetherhub-boros-aggro.txt");
		String result = DeckTextExtractor.extractDeckSection(text);
		assertTrue("expected a result", result != null);
		assertTrue("the page's own \"Side 13 cards (5 distinct)\" header must be recognized as a Sideboard marker",
				result.contains("Sideboard"));
		assertTrue("the sideboard's own cards must still be present", result.contains("The Legend of Roku"));
	}

	@Test
	public void testRealDeckboxScratchpadExcluded() throws Exception {
		// https://deckbox.org/sets/3431193 - a real Commander deck
		// ("Devon Krynicki's WUBRG - Legends Matter"). deckbox.org has a
		// "Scratchpad" section ("Scratchpad - 41 cards, 40 distinct" - the
		// page's own description: "cards that you are considering for this
		// deck, but are not actually in the built deck... do not count
		// towards the in built decks count") that wasn't excluded -
		// STOP_SECTION_WORDS only ever had maybeboard/considering/wishlist.
		// The real Sideboard (15 cards) was already correctly excluded
		// (this deck is Commander-format), so the deck's own real "Main
		// Deck - 100 cards" plus the Scratchpad's 41 read as 141 cards - a
		// real observed count. Every row here has an explicit leading
		// quantity ("1\tRamos, Dragon Engine\t...") - decent shape evidence
		// on its own, trusted even without a seeded database, so this test
		// (unlike every other real-capture test in this file) needs no
		// useDatabase() call at all - but DOES need useEmptyDatabase() (see
		// its own header): leaving NORM_NAMES at its default null, instead
		// of an explicit empty Set, is NOT the same thing once this test
		// runs inside a suite alongside others that load a real (if small)
		// database into the same JVM - a real observed count, 100 cards
		// read as 10.
		useEmptyDatabase();
		String text = readCapture("deckbox-wubrg-legends-matter.txt");
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals("the page's own \"Main Deck - 100 cards, 100 distinct\"", 100, count.unique);
		assertEquals(100, count.total);
		String result = DeckTextExtractor.extractDeckSection(text);
		assertTrue("expected a result", result != null);
		assertTrue("the commander must be present", result.contains("Ramos, Dragon Engine"));
		assertTrue("the real sideboard must still be excluded (this is a Commander deck)",
				!result.contains("Jegantha"));
		assertTrue("the Scratchpad's own cards must not appear anywhere in the output",
				!result.contains("Atraxa, Grand Unifier") && !result.contains("Cultivate"));
	}

	@Test
	public void testDetectDeckMetaDeckboxMultiWordUsername() throws Exception {
		useEmptyDatabase();
		// same real capture as testRealDeckboxScratchpadExcluded() - this
		// deckbox.org user's display name is two words ("Devon Krynicki"),
		// unlike the single-word "wrensleigh" that titleEmbeddedWithPossessive()
		// was built against (testDetectDeckMetaDeckboxPossessiveTitle below).
		// Its own title line, "Devon Krynicki's WUBRG - Legends Matter", never
		// matched TITLE_POSSESSIVE's original "^\S+'s" (a bare single token
		// immediately followed by "'s") - "Devon" alone doesn't end in "'s" -
		// so NOTHING was detected at all (a real observed bug: the New Deck
		// wizard's Name field stayed blank). Locks in the fix - see
		// TITLE_POSSESSIVE's own header.
		String text = readCapture("deckbox-wubrg-legends-matter.txt");
		DeckTextExtractor.DeckMeta meta = DeckTextExtractor.detectDeckMeta(text);
		assertEquals("WUBRG - Legends Matter", meta.title);
	}

	@Test
	public void testDetectDeckMetaTappedOutFallsBackToCommanderName() throws Exception {
		// favorite: https://tappedout.net/mtg-decks/lets-build-zaxara-the-exemplary/
		// a real capture with NO title text anywhere at all - this site's
		// own "Commander" category sorts AFTER Land/Creature/Enchantment/
		// Instant, so scan.start lands right at the very top of the whole
		// captured text (nothing but price-table column headers above it)
		// and every backward-window heuristic above has nothing to look
		// at. The only deck-identifying text present anywhere is the
		// commander's own card line, "Commander: Zaxara, the Exemplary" -
		// locks in the last-resort fallback that searches the WHOLE matched
		// block (not just the header window) for it.
		useDatabase("Alchemist's Refuge", "Barkchannel Pathway", "Breeding Pool", "Castle Garenbrig",
				"Clearwater Pathway", "Command Tower", "Darkbore Pathway", "Deathcap Glade", "Dreamroot Cascade",
				"Drowned Catacomb", "Exotic Orchard", "Foreboding Landscape", "Forest", "Gaea's Cradle",
				"Hinterland Harbor", "Island", "Karn's Bastion", "Mana Confluence", "Misty Rainforest",
				"Morphic Pool", "Overgrown Tomb", "Polluted Delta", "Reflecting Pool", "Rejuvenating Springs",
				"Reliquary Tower", "Shipwreck Marsh", "Snow-Covered Forest", "Snow-Covered Island",
				"Snow-Covered Swamp", "Swamp", "Undergrowth Stadium", "Verdant Catacombs", "Watery Grave",
				"Woodland Cemetery", "Zagoth Triome", "Altered Ego", "Bloom Tender", "Bristly Bill, Spine Sower",
				"Corpsejack Menace", "Gadwick, the Wizened", "Gwenna, Eyes of Gaea", "Hooded Hydra",
				"Hydroid Krasis", "Kalonian Hydra", "Lifeblood Hydra", "Neverwinter Hydra", "Nyxborn Hydra",
				"Selvala, Heart of the Wilds", "Steelbane Hydra", "The Goose Mother", "Troyan, Gutsy Explorer",
				"Vastwood Hydra", "Voracious Hydra", "Branching Evolution", "Doubling Season",
				"Garruk's Uprising", "Growing Rites of Itlimoc", "Innkeeper's Talent", "Pemmin's Aura",
				"Primeval Bounty", "Retribution of the Ancients", "Simic Ascendancy", "Stocking the Pantry",
				"Unbound Flourishing", "Unnatural Growth", "Assassin's Trophy", "Beast Within",
				"Blue Sun's Zenith", "Cyclonic Rift", "Erebos's Intervention", "Pull from Tomorrow",
				"Repulsive Mutation", "Stroke of Genius", "Zaxara, the Exemplary", "Black Sun's Zenith",
				"Curse of the Swine", "Demonic Tutor", "Diabolic Intent", "Doomsday Confluence", "Exsanguinate",
				"Fangs of Kalonia", "Farseek", "Finale of Revelation", "Nature's Lore", "Open Into Wonder",
				"Stargaze", "Three Visits", "Torment of Hailfire", "Villainous Wealth", "Wildest Dreams",
				"Arcane Signet", "Astral Cornucopia", "Elementalist's Palette", "Fractal Harness",
				"Lightning Greaves", "Sequence Engine", "Sol Ring", "Strionic Resonator", "The Ozolith",
				"Thought Vessel");
		DeckTextExtractor.DeckMeta meta = DeckTextExtractor
				.detectDeckMeta(readCapture("tappedout-zaxara-commander-only-title.txt"));
		assertEquals("Zaxara, the Exemplary", meta.title);
	}

	@Test
	public void testRealTappedOutZaxaraCommanderOnlyTitleCount() throws Exception {
		// same real capture as testDetectDeckMetaTappedOutFallsBackToCommanderName()
		// - unlike the Gishath/Grumgully TappedOut captures elsewhere in this
		// file, this page has no separate Sideboard/Maybeboard section at
		// all: every category header's own count (Land 35, Creature 18,
		// Enchantment 12, Instant 8, Commander 1, Sorcery 16, Artifact 10)
		// sums to exactly 100, and every single row is a distinct card name
		// (including the commander, "Commander: Zaxara, the Exemplary",
		// recovered via COMMANDER_PREFIX), so unique equals total here.
		useDatabase("Alchemist's Refuge", "Barkchannel Pathway", "Breeding Pool", "Castle Garenbrig",
				"Clearwater Pathway", "Command Tower", "Darkbore Pathway", "Deathcap Glade", "Dreamroot Cascade",
				"Drowned Catacomb", "Exotic Orchard", "Foreboding Landscape", "Forest", "Gaea's Cradle",
				"Hinterland Harbor", "Island", "Karn's Bastion", "Mana Confluence", "Misty Rainforest",
				"Morphic Pool", "Overgrown Tomb", "Polluted Delta", "Reflecting Pool", "Rejuvenating Springs",
				"Reliquary Tower", "Shipwreck Marsh", "Snow-Covered Forest", "Snow-Covered Island",
				"Snow-Covered Swamp", "Swamp", "Undergrowth Stadium", "Verdant Catacombs", "Watery Grave",
				"Woodland Cemetery", "Zagoth Triome", "Altered Ego", "Bloom Tender", "Bristly Bill, Spine Sower",
				"Corpsejack Menace", "Gadwick, the Wizened", "Gwenna, Eyes of Gaea", "Hooded Hydra",
				"Hydroid Krasis", "Kalonian Hydra", "Lifeblood Hydra", "Neverwinter Hydra", "Nyxborn Hydra",
				"Selvala, Heart of the Wilds", "Steelbane Hydra", "The Goose Mother", "Troyan, Gutsy Explorer",
				"Vastwood Hydra", "Voracious Hydra", "Branching Evolution", "Doubling Season",
				"Garruk's Uprising", "Growing Rites of Itlimoc", "Innkeeper's Talent", "Pemmin's Aura",
				"Primeval Bounty", "Retribution of the Ancients", "Simic Ascendancy", "Stocking the Pantry",
				"Unbound Flourishing", "Unnatural Growth", "Assassin's Trophy", "Beast Within",
				"Blue Sun's Zenith", "Cyclonic Rift", "Erebos's Intervention", "Pull from Tomorrow",
				"Repulsive Mutation", "Stroke of Genius", "Zaxara, the Exemplary", "Black Sun's Zenith",
				"Curse of the Swine", "Demonic Tutor", "Diabolic Intent", "Doomsday Confluence", "Exsanguinate",
				"Fangs of Kalonia", "Farseek", "Finale of Revelation", "Nature's Lore", "Open Into Wonder",
				"Stargaze", "Three Visits", "Torment of Hailfire", "Villainous Wealth", "Wildest Dreams",
				"Arcane Signet", "Astral Cornucopia", "Elementalist's Palette", "Fractal Harness",
				"Lightning Greaves", "Sequence Engine", "Sol Ring", "Strionic Resonator", "The Ozolith",
				"Thought Vessel");
		String text = readCapture("tappedout-zaxara-commander-only-title.txt");
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals("the page's own category headers (Land 35 + Creature 18 + Enchantment 12 + Instant 8 + "
				+ "Commander 1 + Sorcery 16 + Artifact 10) summing to 100 distinct cards", 100, count.unique);
		assertEquals(100, count.total);
	}

	@Test
	public void testCountBySectionSplitsMainAndSideboard() throws Exception {
		// same real AetherHub capture as testRealAetherhubSideHeaderRecognizedAsSideboard
		// - "Main 60 cards (18 distinct)" / "Side 13 cards (5 distinct)" -
		// for BrowseWebsiteDialog's own live "Main: N, Sideboard: M"
		// indicator, parsed from extractDeckSection()'s own "Sideboard"/
		// "Deck" toggle markers rather than re-running the whole match
		// pipeline a third time.
		useDatabase("Ajani, Nacatl Pariah", "Guide of Souls", "Ragavan, Nimble Pilferer", "Seasoned Pyromancer",
				"Voice of Victory", "Ocelot Pride", "Galvanic Discharge", "Goblin Bombardment", "Lightning Bolt",
				"Blood Moon", "Arena of Glory", "Mountain", "Marsh Flats", "Elegant Parlor", "Flooded Strand",
				"Arid Mesa", "Sacred Foundry", "Plains", "Containment Priest", "Obsidian Charmaw", "Meltdown",
				"High Noon", "The Legend of Roku");
		String extracted = DeckTextExtractor.extractDeckSection(readCapture("aetherhub-boros-aggro.txt"));
		DeckTextExtractor.SectionCounts bySection = DeckTextExtractor.countBySection(extracted);
		assertEquals(60, bySection.mainTotal);
		assertEquals(18, bySection.mainUnique);
		assertEquals(13, bySection.sideboardTotal);
		assertEquals(5, bySection.sideboardUnique);
	}

	@Test
	public void testRealTappedOutGluedFoilSuffix() throws Exception {
		// https://tappedout.net/mtg-decks/red-goblin-deck-16-11-12-1/ - a
		// real capture renders a foil-print indicator glued directly onto
		// the card's own name with no separating space at all: "2x Warren
		// Instigatorfoil" - exactly GLUED_TRAILING_BADGE's own "Aura
		// ShardsGC" shape, but lowercase, so that pattern's all-uppercase
		// requirement never caught it. The card was silently dropped
		// entirely - a real observed count, 60 cards read as 58 (21 unique
		// read as 20).
		useDatabase("Goblin Arsonist", "Goblin Bushwhacker", "Goblin Chieftain", "Goblin Guide", "Goblin Wardriver",
				"Hellrider", "Krenko, Mob Boss", "Mogg Fanatic", "Siege-Gang Commander", "Warren Instigator",
				"Inkmoth Nexus", "Mountain", "Smoldering Spires", "Boggart Shenanigans", "Quest for the Goblin Lord",
				"Lightning Bolt", "Shard Volley", "Arc Trail", "Dragon Fodder", "Goblin Grenade",
				"Krenko's Command");
		String text = readCapture("tappedout-red-goblin-deck.txt");
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals("the page's own \"Cards 60\"", 60, count.total);
		assertEquals(21, count.unique);
		String result = DeckTextExtractor.extractDeckSection(text);
		assertTrue("expected a result", result != null);
		assertTrue("Warren Instigator must resolve with its real quantity",
				result.contains("2 Warren Instigator"));
		assertTrue("the glued 'foil' suffix must not leak into the resolved name",
				!result.toLowerCase(java.util.Locale.ROOT).contains("foil"));
	}

	@Test
	public void testDetectDeckMetaMtggoldfishTitleByAuthor() throws Exception {
		// https://www.mtggoldfish.com/archetype/standard-mono-green-landfall-woe
		// - a real capture renders its own title and author together on
		// ONE line, "Mono-Green Landfall by Nicolas D'Ambrose" - unlike
		// every other real site checked, with no format mentioned anywhere
		// near it at all (this site's own "Format: Standard" is a separate,
		// unrelated line - a label:value shape detectDeckMeta doesn't
		// resolve, so format stays null here; the title is still found
		// independently via titleEmbeddedWithBy()). Every row in this
		// capture has an explicit leading quantity - decent shape evidence
		// on its own, so no useDatabase() call is needed here either - but
		// DOES need useEmptyDatabase() (see its own header), or this test
		// is fragile to running inside a suite alongside others that load
		// a real database into the same JVM - a real observed failure,
		// title read as null instead of "Mono-Green Landfall".
		useEmptyDatabase();
		DeckTextExtractor.DeckMeta meta = DeckTextExtractor
				.detectDeckMeta(readCapture("mtggoldfish-mono-green-landfall.txt"));
		assertEquals("Mono-Green Landfall", meta.title);
	}

	@Test
	public void testRealMtggoldfishMonoGreenLandfallCount() throws Exception {
		// same real capture as testDetectDeckMetaMtggoldfishTitleByAuthor() -
		// unlike that sibling title test, this one DOES need a seeded
		// database: the page's own footer line, "75 Cards Total", shares
		// the exact same "<qty> <name>" shape every other row on this page
		// uses ("4 Llanowar Elves", "14 Forest", ...), and resolveName()'s
		// own trustShapeIfDbUnavailable fallback (meant for the real app's
		// own early-startup window, before the card database has finished
		// loading - see its header) blindly trusts ANY such shape when the
		// database is empty, with no real names to check it against. That
		// turned this summary line into a phantom "75 Cards Total" card,
		// exactly doubling the real total (150 instead of 75) - caught only
		// by actually running this real capture through the compiled class
		// instead of reasoning about the count by hand. A seeded database
		// (as every other real-capture count test in this file already
		// uses) correctly rejects it, since "Cards Total" matches no real
		// card name. The page's own "Maindeck (60)" + "Sideboard (15)" =
		// its own stated "75 Cards Total" footer, taken as data (a real
		// quantity+name row) rather than at face value as a total. 24
		// unique names - Keen-Eyed Curator, Surrak (Elusive Hunter), and
		// Sapling Nursery each appear in both the maindeck and the
		// sideboard, so unique is 5 less than the 29 real rows (28 rows
		// once the phantom footer is excluded).
		useDatabase("Llanowar Elves", "Sazh's Chocobo", "Keen-Eyed Curator", "Surrak, Elusive Hunter",
				"Icetill Explorer", "Mightform Harmonizer", "Bushwhack", "Esper Origins", "Glimpse the Core",
				"Lumbering Worldwagon", "Meltstrider's Resolve", "Earthbender Ascension", "Sapling Nursery",
				"Ba Sing Se", "Elven Passage", "Escape Tunnel", "Fabled Passage", "Forest", "Promising Vein",
				"Meltstrider's Gear", "Torpor Orb", "Mossborn Hydra", "Sandman, Shifting Scoundrel",
				"Leatherhead, Swamp Stalker");
		String text = readCapture("mtggoldfish-mono-green-landfall.txt");
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals(24, count.unique);
		assertEquals("the page's own \"75 Cards Total\" (Maindeck 60 + Sideboard 15) - its own footer line must "
				+ "never be double-counted as a phantom 75-copy card itself", 75, count.total);
	}

	@Test
	public void testDetectDeckMetaDeckboxPossessiveTitle() throws Exception {
		// https://deckbox.org/sets/3560392 ("wrensleigh's Power Hungry") -
		// a real capture renders its own title as "<username>'s <Deck
		// Name>" on one line. Also locks in two false-positive fixes on
		// this same real, unusually cluttered page: (1) its own sidebar
		// nav reuses real format names as personal folder names ("MTG
		// DECKS (342)\n...\nSecret Lair Decks\nStandard\n2021 Starters...")
		// - an exact "Standard" match there used to win and pick the nav
		// item right before it ("Secret Lair Decks") as the title; (2) its
		// own "Commander Bracket" UI field label (2 words) used to be
		// trusted as a format anchor via the same alias-prefix match as a
		// real TappedOut subtitle (8 words), landing on the garbled
		// "Format (legal) comCommander" line next to it as the title - a
		// real observed bug. The backward scan (closest match wins) now
		// reaches the real "<username>'s <title>" line before either of
		// those, since it sits much closer to the decklist than the
		// sidebar nav or the page's own format/legality labels.
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
				"Grim Backwoods", "Gruul Guildgate", "Jund Panorama", "Kazandu Refuge", "Khalni Garden",
				"Kher Keep", "Llanowar Reborn", "Mountain", "Opal Palace", "Rakdos Guildgate", "Rupture Spire",
				"Savage Lands", "Swamp", "Temple of the False God", "Terramorphic Expanse", "Vivid Grove");
		DeckTextExtractor.DeckMeta meta = DeckTextExtractor.detectDeckMeta(readCapture("deckbox-power-hungry.txt"));
		assertEquals("Power Hungry", meta.title);
	}

	@Test
	public void testTitleEmbeddedWithByRejectsOrdinaryProseContainingBy() throws Exception {
		// a real Archidekt capture's own UI instruction text ("Choose your
		// preferred layout. You can mix and match options using the View
		// as & Group by drop-downs above.") also contains " by " as an
		// ordinary connector word - and used to get wrongly picked as the
		// title entirely (via the SAME code path "Mono-Green Landfall by
		// Nicolas D'Ambrose" needs) once Archidekt's own format-anchored
		// title lookup came up empty. Locks in the length cap/period
		// rejection that tells a real "Title by Author" line apart from an
		// ordinary sentence that merely uses "by" as a word.
		useDatabase(ARCHIDEKT_DB);
		DeckTextExtractor.DeckMeta meta = DeckTextExtractor.detectDeckMeta(readCapture("archidekt-thranduil.txt"));
		assertEquals("Thranduil, Sindarin Liege // Silvan Rally", meta.title);
	}

	// ---- WebFetch-approximate fixtures - NOT real browser captures. Every
	// other fixture in this file is byte-for-byte what BrowseWebsiteDialog's
	// embedded SWT Browser actually captures (document.body.innerText) from
	// a real page - the whole point of this suite is testing DeckTextExtractor
	// against that real mess (oracle text, price columns, category-transition
	// widgets, reprint stamps, ...). These two fixtures instead come from an
	// AI-summarization web-fetch tool, which returns already-cleaned "<qty>
	// <name>" lines with none of that real chrome - closer to what
	// extractDeckSection()'s own OUTPUT looks like than to a real capture's
	// INPUT. They only exercise the shape-only "<qty> <name>" matching path
	// (same as testRealDeckboxScratchpadExcluded()/
	// testDetectDeckMetaMtggoldfishTitleByAuthor(), which also need no
	// useDatabase() call), not the real-world noise the rest of this file
	// guards against - a regression in, say, REPRINT_STAMP handling would
	// never be caught here. Kept deliberately separate (own fixture-name
	// prefix, own section) so they are never mistaken for the same level of
	// evidence as a real capture. The same web-fetch tool, asked twice for
	// mtggoldfish.com's own "Blue Artifacts" sideboard, returned two
	// different, both wrong, quantity breakdowns (summing to 16, then to a
	// non-matching total) despite the page itself stating 15 - confirming it
	// cannot be trusted for exact per-card figures and is NOT used here;
	// only the two fixtures below, whose numbers were independently checked
	// to sum to their own page's stated category/section totals, are used.
	@Test
	public void testWebfetchApproxArchidektAvengersAssemble() throws Exception {
		useEmptyDatabase();
		// https://archidekt.com/decks/23342365/avengers_assemble_marvel_super_heroes
		// - every category total in the fetched summary (Artifacts 14,
		// Creatures 29, Enchantments 5, Instants 7, Lands 38, Sorceries 6,
		// plus the Commander) sums to exactly 100, a standard Commander deck
		// size - the one cross-check available without a real capture.
		String text = readCapture("webfetch-approx-archidekt-avengers-assemble.txt");
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals(87, count.unique);
		assertEquals(100, count.total);
	}

	@Test
	public void testWebfetchApproxMtgtop8PinnacleAffinity() throws Exception {
		useEmptyDatabase();
		// https://mtgtop8.com/event?e=91036&f=MO ("Pinnacle Affinity",
		// Modern) - the fetched summary's own stated "75 (60 maindeck + 15
		// sideboard)" matches the maindeck/sideboard row quantities summed
		// by hand exactly, unlike the mtggoldfish sideboard attempt (see
		// this section's own header).
		String text = readCapture("webfetch-approx-mtgtop8-pinnacle-affinity.txt");
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals(28, count.unique);
		assertEquals(75, count.total);
	}

	// ---- real BrowseWebsiteDialog captures, pasted directly from the app's
	// own diagnostic log (not WebFetch) - same level of evidence as every
	// other real-capture test above.

	@Test
	public void testRealAetherhubMonoRed() throws Exception {
		// https://aetherhub.com/Metagame/Historic-BO1/Deck/mono-red-1426582
		// ("Historic - Mono Red") - "Krenko, Mob Boss" renders as TWO
		// separate rows (2 copies + 1 copy, likely two different
		// printings/versions tracked separately by this site's own deck
		// builder) - the page's own "Main 60 cards (14 distinct)" counts
		// those as 2 distinct ROWS, but countRecognizedCards() correctly
		// collapses them to one distinct NAME (13 main-unique), same
		// principle as "Rakdos Charm" in testRealMtgtop8Capture() and
		// several other real captures in this file - not a bug, a
		// deliberate divergence from the site's own row-counting label.
		useDatabase("General Kreat, the Boltbringer", "Rundvelt Hordemaster", "Muxus, Goblin Grandee",
				"Goblin Chieftain", "Goblin Warchief", "Skirk Prospector", "Krenko, Mob Boss", "Goblin Instigator",
				"Goblin Matron", "Goblin Chainwhirler", "Hobgoblin Bandit Lord", "Impact Tremors", "Mountain",
				"Krenko, Tin Street Kingpin", "Legion Warboss",
				// this page's own "Meta deck card breakdown" section (a
				// percentage-of-the-format list, not this deck's own
				// content) repeats several more real card names with no
				// qty prefix at all - seeded here too so the test proves
				// that section is correctly excluded even when every name
				// in it is DB-confirmable, not just coincidentally absent
				// from a sparse test database
				"Conspicuous Snoop", "Searslicer Goblin", "Battle Cry Goblin", "Squee, Dubious Monarch",
				"Wily Goblin", "Lightning Strike", "Raid Bombardment", "Castle Embereth", "Den of the Bugbear",
				"Eclipsed Realms", "Cavern of Souls", "Claim the Firstborn", "Dragon Fodder",
				"You See a Pair of Goblins", "Bolt Bend", "Ember Hauler");
		String text = readCapture("aetherhub-mono-red.txt");
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals(15, count.unique);
		assertEquals("Main 60 + Side 2", 62, count.total);
	}

	@Test
	public void testRealAetherhubDimirControl() throws Exception {
		// https://aetherhub.com/Deck/dimir-control-sep-2026-mythic}
		// ("Standard - Dimir Control (Sep 2026 Mythic)") - a real deck
		// whose own sideboard overlaps the maindeck on 3 names (Duress,
		// Intimidation Tactics, Strategic Betrayal), same collapsing
		// principle as the Mono Red capture above.
		useDatabase("Bitter Triumph", "Consult the Star Charts", "Deadly Cover-Up", "Duress",
				"Intimidation Campaign", "Intimidation Tactics", "Requiting Hex", "Spell Snare", "Stock Up",
				"Strategic Betrayal", "The End", "Three Steps Ahead", "Outrageous Robbery", "Spell Pierce",
				"Deceit", "Oildeep Gearhulk", "Liliana, Dreadhorde General", "Demolition Field", "Fountainport",
				"Gloomlake Verge", "Island", "Petrified Hamlet", "Restless Reef", "Swamp", "Undercity Sewers",
				"Watery Grave", "M.O.D.O.K.", "Ancient Vendetta", "Annul", "Day of Black Sun", "Negate");
		String text = readCapture("aetherhub-dimir-control.txt");
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals("the page's own \"Main 60 cards (26 distinct)\" + \"Side 15 cards (8 distinct)\", minus 3 "
				+ "names shared between main and side", 31, count.unique);
		assertEquals(75, count.total);
	}

	@Test
	public void testRealTipsymagicEdgarMarkov() throws Exception {
		// https://tipsymagic.com/view_deck/6063-edgar-markov - a real
		// Commander deck (the site's own "Card view" layout). Unlike the
		// Lord High Artificer deck this site's other tests use, this
		// capture has no separate Sideboard section at all - every
		// category total (Commanders 1, Planeswalkers 2, Creatures 30,
		// Artifacts 8, Lands 39, Instants 11, Sorceries 4, Enchantments 5)
		// sums to exactly 100, and every single row is a distinct name, so
		// unique equals total minus nothing here.
		useDatabase("Edgar Markov", "Sorin, Imperious Bloodlord", "Sorin, Solemn Visitor", "Adanto Vanguard",
				"Bishop of Binding", "Blood Artist", "Bloodcrazed Paladin", "Bloodline Keeper // Lord of Lineage",
				"Bloodlord of Vaasgoth", "Captivating Vampire", "Dark Impostor", "Dusk Legion Zealot",
				"Duskborne Skymarcher", "Elenda, the Dusk Rose", "Forerunner of the Legion", "Gifted Aetherborn",
				"Kheru Mind-Eater", "Legion Lieutenant", "Malakir Bloodwitch", "Mavren Fein, Dusk Apostle",
				"Metallic Mimic", "Mirri the Cursed", "Mirror Entity", "Patron of the Vein", "Rakish Heir",
				"Sadistic Skymarcher", "Sanctum Seeker", "Sangromancer", "Stromkirk Captain", "Twilight Prophet",
				"Vampire Nighthawk", "Vampire Nocturnus", "Vicious Conquistador", "Blade of the Bloodchief",
				"Boros Signet", "Chromatic Lantern", "Herald's Horn", "Orzhov Signet", "Perilous Snare",
				"Rakdos Signet", "Throne of the God-Pharaoh", "Battlefield Forge", "Blood Crypt",
				"Bloodstained Mire", "Caves of Koilos", "Command Tower", "Evolving Wilds", "Exotic Orchard",
				"Godless Shrine", "Graven Cairns", "Mountain", "Mutavault", "Nomad Outpost", "Opal Palace",
				"Path of Ancestry", "Plains", "Reliquary Tower", "Sacred Foundry", "Scavenger Grounds",
				"Spectator Seating", "Sulfurous Springs", "Swamp", "Terramorphic Expanse",
				"Vault of the Archangel", "Wooded Foothills", "Akroma's Will", "Crackling Doom", "Dark Ritual",
				"Dismember", "Erode", "Feed the Cycle", "Mortify", "Murderous Cut", "Return to Dust",
				"Take Up the Shield", "Vona's Hunger", "Arterial Flow", "Diabolic Tutor", "New Blood",
				"Unburial Rites", "Anointed Procession", "Blind Obedience", "Ghostly Prison", "Intangible Virtue",
				"Legion's Landing // Adanto, the First Fort");
		String text = readCapture("tipsymagic-edgar-markov.txt");
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals(85, count.unique);
		assertEquals(100, count.total);
	}

	@Test
	public void testRealTipsymagicAggro() throws Exception {
		// https://tipsymagic.com/view_deck/7931-aggro - a real, non-Commander
		// 60-card deck on this same site's "Card view" layout (the stepper
		// shape, "Name\n-\n1\n+", same as testRealTipsymagicLordHighArtificerCardView()).
		useDatabase("Aggressive Mammoth", "Bear Cub", "Blooming Stinger", "Drix Fatemaker",
				"Flopsie, Bumi's Buddy", "Frontline War-Rager", "Fungal Colossus", "Galactic Wayfarer",
				"Hemosymbic Mite", "Kavaron Harrier", "Kavaron Skywarden", "Kavaron Turbodrone",
				"Meltstrider Eulogist", "Memorial Team Leader", "Molecular Modifier", "Nebula Dragon",
				"Oreplate Pangolin", "Roving Actuator", "Seedship Agrarian", "Tannuk, Memorial Ensign",
				"Turtle-Duck", "Meltstrider's Gear", "Nutrient Block", "Pinnacle Kill-Ship", "Warmaker Gunship",
				"Forest", "Mountain", "Needle Spires", "Thriving Bluff", "Uncharted Haven", "Biosynthic Burst",
				"Drill Too Deep", "Invasive Maneuvers", "Rig for War", "Orbital Plunge", "Pull Through the Weft",
				"Shattered Wings");
		String text = readCapture("tipsymagic-aggro.txt");
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals(37, count.unique);
		assertEquals(60, count.total);
	}

	@Test
	public void testRealTcgplayerMonoBlueTerror() throws Exception {
		// https://www.tcgplayer.com/content/magic-the-gathering/deck/Mono-Blue-Terror/550006/
		// - a different real TCGplayer capture than testRealTcgplayerCapture()'s
		// own Izzet Prowess deck. "Dragon Wings" appears once in the
		// maindeck's own Enchantment (1) category and again as a single
		// copy in the Sideboard (15) - 25 total rows, 24 unique names.
		useDatabase("Cryptic Serpent", "Murmuring Mystic", "Tolarian Terror", "Ponder", "Sleep of the Dead",
				"Deep Analysis", "Lórien Revealed", "Deem Inferior", "Boomerang", "Force Spike", "Mental Note",
				"Vapor Snag", "Thought Scour", "Brainstorm", "Dispel", "Counterspell", "Spell Pierce",
				"Dragon Wings", "Island", "Gut Shot", "Steel Sabotage", "Annul", "Hydroblast",
				"Blue Elemental Blast");
		String text = readCapture("tcgplayer-mono-blue-terror.txt");
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals("\"Dragon Wings\" appears in both Maindeck and Sideboard - 25 rows collapse to 24 names",
				24, count.unique);
		assertEquals("Maindeck 60 + Sideboard 15", 75, count.total);
	}

	@Test
	public void testRealMtgdecksEldraziBloodchiefCombo() throws Exception {
		// https://mtgdecks.net/Modern/eldrazi-bloodchief-combo-decklist-by-stefansson30952-3101819
		// - a real Modern tournament-result page. 4 names (Sire of Seven
		// Deaths, Thought-Knot Seer, Dismember, Vexing Bauble) appear in
		// both the Maindeck and the Sideboard, so unique is 4 less than
		// the 35 real rows.
		useDatabase("Basking Broodscale", "Devourer of Destiny", "Emrakul, the Promised End", "Haywire Mite",
				"Sire of Seven Deaths", "Sowing Mycospawn", "Thought-Knot Seer", "Springleaf Drum",
				"Soul-Guide Lantern", "Blade of the Bloodchief", "Vexing Bauble", "Kozilek's Command",
				"Dismember", "Malevolent Rumble", "Ancient Stirrings", "Urza's Saga",
				"Yavimaya, Cradle of Growth", "Boseiju, Who Endures", "Shifting Woodland", "Gemstone Caverns",
				"Forest", "Eldrazi Temple", "Cavern of Souls", "Ugin's Labyrinth", "Trinisphere",
				"Disruptor Flute", "Nature's Claim", "Grafdigger's Cage", "Pithing Needle", "Six",
				"Thief of Existence");
		String text = readCapture("mtgdecks-eldrazi-bloodchief-combo.txt");
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals(31, count.unique);
		assertEquals("Maindeck 60 + Sideboard 15", 75, count.total);
	}

	@Test
	public void testRealMtggoldfishLegacyBlueArtifacts() throws Exception {
		// https://www.mtggoldfish.com/archetype/legacy-blue-artifacts#paper
		// ("Mono-Blue" by ADK7) - a real capture of a DIFFERENT, later
		// snapshot of this same archetype page than an earlier WebFetch
		// attempt at this URL returned (that attempt's own maindeck/
		// sideboard numbers don't match this real deck at all - the
		// archetype page's featured decklist had simply changed between
		// the two visits, and WebFetch's own sideboard figures were
		// independently shown to be unreliable regardless - see
		// testWebfetchApproxArchidektAvengersAssemble()'s own section
		// header). This real capture's own footer, "77 Cards Total",
		// shares the exact same "<qty> <name>" shape as the maindeck/
		// sideboard rows around it (see resolveName()'s own
		// trustShapeIfDbUnavailable header for the real, narrow window
		// this is a genuine risk in: an empty/not-yet-loaded database) -
		// with a real seeded database here, "Cards Total" correctly
		// resolves to no card. Also seeds this page's own "Card
		// Breakdown" aggregate section's additional real card names
		// (Tamiyo, Mox Amber, Flusterstorm, ...) to prove that section is
		// excluded even when every name in it is DB-confirmable, same
		// principle as testRealAetherhubMonoRed()'s own "Meta deck card
		// breakdown" seeding.
		useDatabase("Bilbo, Thief in the Night", "Loki, God of Mischief", "Thassa's Oracle",
				"Emry, Lurker of the Loch", "Harbinger of the Seas", "Metallic Rebuke", "Sink into Stupor",
				"Force of Will", "Lotus Petal", "Mishra's Bauble", "Mox Opal", "Conjurer's Bauble",
				"Giant's Boulder", "Key to the Side-Door", "Pithing Needle", "Relic of Progenitus",
				"Sewer-veillance Cam", "Grinding Station", "The Filigree Sylex", "Academy Ruins", "Island",
				"Minamo, School at Water's Edge", "Otawara, Soaring City", "Seat of the Synod", "Urza's Saga",
				"Tormod's Crypt", "Consign to Memory", "Into the Flood Maw", "Soul-Guide Lantern",
				"Disruptor Flute", "Dismember", "Kappa Cannoneer",
				"Tamiyo, Inquisitive Student", "Tamiyo, Seasoned Scholar", "Mox Amber", "Urza's Bauble",
				"Aether Spellbomb", "Shadowspear", "Skateboard", "Unable to Scream", "Soporific Springs",
				"Flusterstorm", "Gut Shot", "Hydroblast", "Surgical Extraction", "Force of Negation");
		String text = readCapture("mtggoldfish-legacy-blue-artifacts.txt");
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals("\"Conjurer's Bauble\" appears in both Maindeck and Sideboard - 33 rows collapse to 32 names",
				32, count.unique);
		assertEquals("the page's own \"77 Cards Total\" footer (Maindeck 62 + Sideboard 15) - must never be "
				+ "double-counted as a phantom card itself", 77, count.total);
	}

	@Test
	public void testRealMtgdecksSisayWeatherlightCaptainExcludesArenaExportButton() throws Exception {
		// https://mtgdecks.net/Commander/sisay-weatherlight-captain-decklist-by-...-3098562
		// - this site's own "Export to MTG Arena" button renders as two
		// separate lines, " ARENA" then " EXPORT" (and elsewhere, intact,
		// " ARENA EXPORT") - "Arena" is itself a real, if obscure, Commander-
		// legal enchantment (Legends/Commander 2014 - confirmed directly
		// against the bulk card data, not guessed), so resolveName()'s own
		// trim-by-word loop happily resolves "ARENA EXPORT" down to "ARENA"
		// once "EXPORT" fails to match anything. That bare match then needed
		// its own qty evidence, which it only found via
		// startsWithCommanderHeader()'s backward scan wrongly trusting this
		// site's own page breadcrumb ("COMMANDER DECKSSISAY, WEATHERLIGHT
		// CAPTAIN ... COMMANDER RUMBLE...") as a genuine Commander section
		// header purely because its first word is "Commander" - the same
		// class of false positive as this site's own nav menu ("COMMANDER
		// META"/"COMMANDER TOURNAMENTS"/...), locked in by
		// isGenuineCommanderHeaderLine()'s own header. A real observed
		// count: 100 cards (99 maindeck + the commander, Sisay, wrapped as a
		// 1-card "sideboard") read as 101.
		useDatabase("Arena", "Birds of Paradise", "Noble Hierarch", "Bloom Tender", "Avacyn's Pilgrim",
				"Deathrite Shaman", "Sakashima the Impostor", "Derevi, Empyrial Tactician",
				"Selvala, Heart of the Wilds", "Lavinia, Azorius Renegade", "Faeburrow Elder",
				"Kinnan, Bonder Prodigy", "Emiel the Blessed", "Esika, God of the Tree",
				"Ragavan, Nimble Pilferer", "Ignoble Hierarch", "Gluntch, the Bestower", "Ertai Resurrected",
				"Gwenna, Eyes of Gaea", "Jirina, Dauntless General", "Ioreth of the Healing House",
				"Orcish Bowmasters", "Lotho, Corrupt Shirriff", "Ruby, Daring Tracker",
				"Kutzil, Malamet Exemplar", "Basim Ibn Ishaq", "Mockingbird", "Enduring Vitality",
				"Marvin, Murderous Mimic", "Deadpool, Trading Card", "Tataru Taru", "Wan Shi Tong, Librarian",
				"The Duke, Rebel Sentry", "The Cabbage Merchant", "Tam, Mindful First-Year",
				"The Wondrous Wasp", "Shang-Chi, Master of Kung Fu", "King T'Challa", "Mox Amber", "Sol Ring",
				"Chrome Mox", "Relic of Legends", "Agatha's Soul Cauldron", "Loki's Scepter",
				"Force of Will", "Dispel", "Vampiric Tutor", "Worldly Tutor", "Swords to Plowshares",
				"Silence", "Mindbreak Trap", "Mental Misstep", "Orim's Chant", "Swan Song", "Flusterstorm",
				"Fierce Guardianship", "Deadly Rollick", "Deflecting Swat", "An Offer You Can't Refuse",
				"Into the Flood Maw", "Redirect Lightning", "Demonic Tutor", "Mystic Remora", "Rhystic Study",
				"Touch the Spirit Realm", "Flowering of the White Tree", "Gleaming Splendor",
				"Teferi, Time Raveler", "Tyvar, Jubilant Brawler", "City of Brass", "Forbidden Orchard",
				"Exotic Orchard", "Mana Confluence", "Bloodstained Mire", "Flooded Strand", "Polluted Delta",
				"Windswept Heath", "Wooded Foothills", "Bayou", "Plateau", "Savannah", "Scrubland", "Taiga",
				"Tropical Island", "Tundra", "Underground Sea", "Volcanic Island", "Arid Mesa", "Marsh Flats",
				"Misty Rainforest", "Scalding Tarn", "Verdant Catacombs", "Gaea's Cradle", "Gemstone Caverns",
				"Command Tower", "Boseiju, Who Endures", "Otawara, Soaring City", "Mount Doom",
				"Talon Gates of Madara", "Starting Town", "Sisay, Weatherlight Captain");
		String text = readCapture("mtgdecks-sisay-weatherlight-captain.txt");
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals("the real decklist's own 100 distinct cards - \"Arena\" (the site's own button label, not "
				+ "part of this deck) must not be counted as a phantom 101st", 100, count.unique);
		assertEquals("Maindeck 99 + the commander (Sisay) wrapped as a 1-card sideboard", 100, count.total);
	}

	@Test
	public void testRealDeckstatsBruno() throws Exception {
		// https://deckstats.net/decks/4319588-bruno - a real Commander deck
		// ("bruno", a goblin/Lord-of-the-Rings tribal deck). Unlike every
		// other real Commander capture in this file, this site's own
		// Commander section ("COMMANDER (1)\n1\nThe Balrog, Durin's Bane")
		// is simply the first category in its own page, counted as part of
		// the same 99-card total the page itself states ("99 main / 0
		// side") - no separate Sideboard wrap needed here, this deck has no
		// real sideboard at all.
		useDatabase("The Balrog, Durin's Bane", "Goblin Arsonist", "Skirk Prospector", "War-Torch Goblin",
				"Frogtosser Banneret", "Goblin Fireleaper", "Goblin Instigator", "Gorbag of Minas Morgul",
				"Mauhúr, Uruk-hai Captain", "Goblin Matron", "Goblin Warchief", "Grishnákh, Brash Instigator",
				"Guttersnipe", "Legion Warboss", "Moria Scavenger", "Orcish Siegemaster",
				"Gothmog, Morgul Lieutenant", "Krenko, Mob Boss", "Mer-Ek Nightblade", "Mirkwood Bats",
				"Olog-hai Crusher", "Sling-Gang Lieutenant", "Uglúk of the White Hand", "Airdrop Condor",
				"Cirith Ungol Patrol", "Siege-Gang Commander", "Swarming Goblins", "The Balrog, Flame of Udûn",
				"Muxus, Goblin Grandee", "The Balrog of Moria", "Brightstone Ritual", "Rush the Room",
				"Cast into the Fire", "Nasty End", "Smite the Deathless", "Goblin Surprise",
				"You See a Pair of Goblins", "Kill! Maim! Burn!", "Faithless Looting", "Fear, Fire, Foes!",
				"Lash of the Balrog", "Dragon Fodder", "Krenko's Command", "Mordor Muster",
				"Assault on Osgiliath", "Swarming of Moria", "Empty the Warrens", "Foray of Orcs",
				"The Torment of Gollum", "Goblin Rally", "Pyrite Spellbomb", "Stone of Erech",
				"Arcane Signet", "Mind Stone", "Grond, the Gatebreaker", "March from the Black Gate",
				"Book of Mazarbul", "Fall of Cair Andros", "Goblin War Drums", "Raid Bombardment",
				"Rising of the Day", "Barad-dûr", "Bloodfell Caves", "Command Tower", "Mountain",
				"Path of Ancestry", "Rakdos Carnarium", "Smoldering Marsh", "Swamp");
		String text = readCapture("deckstats-bruno.txt");
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		assertEquals("Commander(1) + Creatures(29) + Instants(8) + Sorceries(12) + Artifacts(5) + "
				+ "Enchantments(6) + 8 distinct Land names (Mountain/Swamp at 16 copies each)", 69, count.unique);
		assertEquals("the page's own \"99 main / 0 side\"", 99, count.total);
		DeckTextExtractor.DeckMeta meta = DeckTextExtractor.detectDeckMeta(text);
		assertEquals("bruno", meta.title);
		assertEquals("Commander", meta.format);
	}
}
