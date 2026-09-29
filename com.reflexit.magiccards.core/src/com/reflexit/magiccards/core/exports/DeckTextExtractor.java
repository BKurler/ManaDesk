/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: a generic "find the decklist"
 *                         heuristic for text captured from an arbitrary web
 *                         page (see BrowseWebsiteDialog). document.body.
 *                         innerText on a real deck site (mtgdecks.net,
 *                         Moxfield, Archidekt, MTGGoldfish, ...) drags along
 *                         a lot of surrounding page chrome - navigation,
 *                         ads, comments, related-deck lists - around the
 *                         actual decklist, and feeding that whole blob to
 *                         ReportType.autoDetectType()/FreeformImportDelegate
 *                         is unreliable. Every one of those sites still
 *                         renders the decklist itself as a run of plain
 *                         "<qty> <name>" lines, so instead of any
 *                         site-specific scraping, this looks for the
 *                         longest such run (tolerating a couple of
 *                         in-between lines for section headers like
 *                         "Sideboard"/blank lines) and returns just that
 *                         block, trimming the noise around it.
 *     Rémi Dutil (2026) - a purely textual "<qty> <name>" shape turned out to
 *                         be too easy for real page chrome to satisfy too -
 *                         numbered "related decks"/vote-count/comment-count
 *                         sidebars share the exact same shape (e.g. "12
 *                         Comments", "1. Best Related Deck"), and a run
 *                         bridging into one of those (allowed by MAX_GAP)
 *                         could drag the "decklist" block past the real
 *                         list. Added a real cross-check: a candidate line's
 *                         name portion must also resolve to an actual card
 *                         in the local database (DataManager's DB store),
 *                         normalized the same way DeckImportPreviewPage's
 *                         dbByNormName()/norm() already do (that class lives
 *                         in the .ui bundle and can't be called from here,
 *                         so the same lightweight pattern is duplicated in
 *                         core, not hoisted - it is small and self-
 *                         contained). Added debug trace (MagicLogger) so a
 *                         real run's log shows the scan result instead of
 *                         requiring a guess.
 *     Rémi Dutil (2026) - a real run against mtgdecks.net (debug trace: "187
 *                         line(s) scanned, best run 1 match(es)") showed the
 *                         leading-quantity assumption doesn't hold on every
 *                         site: that page's card rows have NO leading count
 *                         at all ("Basking Broodscale $0.39", not "4 Basking
 *                         Broodscale $0.39") - quantity there comes from
 *                         elsewhere (one row per physical copy, or a
 *                         separate column/line). Bare, count-less lines are
 *                         now accepted too, but ONLY when the database
 *                         confirms the name - never trusted on shape alone
 *                         the way a qty-prefixed line still is, since a bare
 *                         line is otherwise indistinguishable from ordinary
 *                         page prose/nav text (see resolveName's
 *                         trustShapeIfDbUnavailable param).
 *     Rémi Dutil (2026) - the bare-line fix above still scored "1 match" on
 *                         a second real mtgdecks.net run - full-text trace
 *                         logging (BrowseWebsiteDialog) revealed the actual
 *                         captured lines ("4  Basking Broodscale  $0.39")
 *                         use non-breaking spaces between the qty/name/price
 *                         columns, which Java's \s does not match by
 *                         default - QTY_LINE was matching NOTHING on that
 *                         page, not failing the DB check as first assumed.
 *                         Added normalizeSpaces() to convert non-breaking
 *                         and other Unicode space variants to plain ASCII
 *                         spaces before matching.
 *     Rémi Dutil (2026) - with the run now correctly found, a third real run
 *                         still showed "Name not found" for every card:
 *                         extractDeckSection() was returning each accepted
 *                         line VERBATIM, tab+price and all ("Basking
 *                         Broodscale  $0.39"), which FreeformImportDelegate
 *                         then treated as one literal (and unresolvable)
 *                         card name; category headers like "Artifact [7]"
 *                         were also passed through and misread as their own
 *                         bogus 1x cards. resolveName() now returns the
 *                         actual matched (already price/junk-trimmed) name
 *                         instead of a boolean, and the extracted output is
 *                         rebuilt from "<qty> <resolved name>" pairs -
 *                         non-matching lines are dropped entirely (except a
 *                         line starting with "sideboard", normalized to the
 *                         literal word so FreeformImportDelegate's own
 *                         section-toggle detection still fires).
 *     Rémi Dutil (2026) - a run against TCGplayer's deck page ("245 cards
 *                         have errors", i.e. no decklist block found at all)
 *                         showed a fourth real-world shape: qty and name on
 *                         SEPARATE consecutive lines ("4\nDragon's Rage
 *                         Channeler"), each row followed by a blank line and
 *                         a standalone price line ("$1.56"), with a category
 *                         header ("Sorcery (8)") between sections - up to 6
 *                         non-matching lines between two consecutive real
 *                         card names, far past the old MAX_GAP=2. Rather
 *                         than loosening MAX_GAP blindly (which would make
 *                         it that much easier for unrelated page content to
 *                         bridge a run), blank/standalone-price/standalone-
 *                         quantity lines are now recognized explicitly as
 *                         "neutral" table plumbing that never counts against
 *                         the gap budget - only a genuinely unrecognized
 *                         line (a category header) still does, still capped
 *                         at MAX_GAP. The output pass now also looks one
 *                         line back for a standalone quantity to attach to a
 *                         bare-name match, so "4\nDragon's Rage Channeler"
 *                         still becomes "4 Dragon's Rage Channeler" instead
 *                         of an uncounted bare name.
 *     Rémi Dutil (2026) - the run-finding loop is now a shared scan() helper
 *                         (Scan start/end/count), and a new public
 *                         countRecognizedCards(String) exposes just the
 *                         match count without needing to run the whole
 *                         extraction - used by BrowseWebsiteDialog to show a
 *                         live "N card(s) found" label as the user browses,
 *                         so a misparsed page is obvious without needing to
 *                         read the debug log.
 *     Rémi Dutil (2026) - a real TCGplayer import correctly found the whole
 *                         deck+sideboard, but "Dragon's Rage Channeler" (the
 *                         deck's first card) also showed up as a stray extra
 *                         1x copy IN THE SIDEBOARD - its own "Product
 *                         Details" panel, further down the page, repeats
 *                         that card's name in a bare line with no preceding
 *                         quantity, and nothing between it and the real
 *                         sideboard's last card broke the run. matchLines()
 *                         now rejects any bare (no same-line qty) match
 *                         whose immediately preceding line isn't a
 *                         standalone quantity - every confirmed real site's
 *                         genuine rows have one; a repeat mention elsewhere
 *                         on the page does not.
 *     Rémi Dutil (2026) - countRecognizedCards() returned a bare row count
 *                         ("30"), not what actually gets imported (a real
 *                         deck reporting "30" that turned out to import 60
 *                         maindeck + 15 sideboard cards) - it now returns a
 *                         CardCount (total physical cards / unique names),
 *                         the same "Total N (unique M)" convention already
 *                         used elsewhere in ManaDesk.
 *     Rémi Dutil (2026) - deckstats.net over-imported: a real capture (60
 *                         maindeck + 28 sideboard correctly found, but 39
 *                         extra "Maybeboard" cards tagged along too) showed
 *                         its Maybeboard section uses the EXACT same "qty \n
 *                         name" row shape as the real Sideboard, separated
 *                         from it by the same 2-line header pattern
 *                         ("▼"/"MAYBEBOARD (39)") that Sideboard's own header
 *                         uses - well within MAX_GAP, so the run just kept
 *                         going straight through it. Unlike a Sideboard
 *                         header, which the run is meant to bridge, a
 *                         Maybeboard/Considering/Wishlist header is now a
 *                         hard stop (isStopSectionHeader) - it closes the
 *                         current run outright in scan(), regardless of the
 *                         gap budget, rather than merely costing one gap
 *                         tick.
 *     Rémi Dutil (2026) - two more real captures showed MAX_GAP=2 was still
 *                         too tight, and (for Archidekt specifically) the
 *                         PRECEDING-only qty attachment rule was simply the
 *                         wrong convention: Moxfield's "Lands (38)\n+ 1
 *                         other" category transition needs 3 tolerated
 *                         lines (one over budget), which silently split its
 *                         100-card main deck into a 62-match run and a
 *                         38-match run, keeping only the larger. Archidekt
 *                         renders each card as "Name\n[1-4 lines of rules
 *                         text]\nType line\n[qty]" - the qty comes AFTER an
 *                         arbitrarily long text block (blowing past any
 *                         reasonable gap budget), and the PRECEDING rule
 *                         rejects every category's first card outright
 *                         (its name isn't preceded by a quantity - it's
 *                         preceded by the category's own header). MAX_GAP
 *                         raised to 10 (protection against unrelated page
 *                         chrome comes from the DB cross-check and
 *                         isStopSectionHeader, not a tight gap budget), and
 *                         a new QtyPolicy.TRAILING convention added
 *                         alongside the existing PRECEDING one specifically
 *                         for Archidekt's shape - the whole page is scanned
 *                         once under each policy (see QtyPolicy's own
 *                         header for why this can't be decided per line) and
 *                         whichever finds the larger run wins, so every
 *                         already-verified real site (which all win under
 *                         PRECEDING) is unaffected.
 *     Rémi Dutil (2026) - a real TappedOut capture showed isStopSectionHeader
 *                         alone wasn't enough: it only stopped a run from
 *                         BLEEDING into a Maybeboard section, but did nothing
 *                         to stop that section's own content from
 *                         separately becoming the longest run on the page in
 *                         its own right and getting picked as "the
 *                         decklist" instead - which is exactly what
 *                         happened (its 34-row Maybeboard outranked the
 *                         real 21-row main+sideboard deck above it). scan()
 *                         now breaks out entirely - not just closes the
 *                         current run - the moment it reaches a Maybeboard/
 *                         Considering/Wishlist header, so nothing from that
 *                         point to the end of the page can ever become a
 *                         candidate run, consistent with every real capture
 *                         seen so far (Maybeboard always trails the real
 *                         deck, never precedes it).
 *     Rémi Dutil (2026) - a real mtgtop8.com capture over-imported by one
 *                         card: "18 LANDS" (its category headers render as
 *                         "&lt;count&gt; &lt;TYPE&gt;" with no separating
 *                         punctuation, e.g. "19 CREATURES" right next to it)
 *                         matched the same-line qty+name shape and, unlike
 *                         its sibling headers, "LANDS" apparently coincided
 *                         with something in the database and got imported as
 *                         a bogus extra card. Added an explicit TYPE_WORDS
 *                         denylist to resolveName() - a card TYPE word alone
 *                         is never trusted as a real card name, regardless of
 *                         what the database says, closing this off for good
 *                         rather than relying on no card ever coincidentally
 *                         sharing a type word's name.
 *     Rémi Dutil (2026) - a real deckbox.org capture found ZERO cards on a
 *                         page whose 83 rows all individually matched fine
 *                         (verified with a standalone harness against the
 *                         actual captured text) - scan()'s isStopSectionHeader
 *                         hard-break fired on the site's own sidebar nav link
 *                         ("MTG COLLECTION: Inventory / Tradelist /
 *                         Wishlist"), line 12 of the page, nowhere near the
 *                         real decklist starting at line 71, killing the scan
 *                         before it ever reached a single card - the exact
 *                         same class of bug as the TYPE_WORDS/"18 LANDS" fix
 *                         above (unrelated page chrome coincidentally
 *                         matching a heuristic keyword). The hard-break is
 *                         now only honored once a real decklist run has
 *                         already been found at least once (see
 *                         {@code everMatched}) - a Maybeboard/Considering/
 *                         Wishlist header seen before any match is just page
 *                         chrome, never a genuine deck section (that only
 *                         makes sense once a decklist has actually started).
 *     Rémi Dutil (2026) - added Commander recognition to extractDeckSection():
 *                         a real deckbox.org Commander-deck capture has a
 *                         bare "Commander" header, with just the commander's
 *                         own card row(s) under it, ahead of the entire
 *                         maindeck - by MTG convention the commander belongs
 *                         in the sideboard pile (a separate pile from the
 *                         99-card maindeck), but nothing recognized this
 *                         header at all, so the commander card was just an
 *                         ordinary maindeck row on import. Unlike a real
 *                         Sideboard header (which stays "on" for the rest of
 *                         the run), Commander is a ONE-SHOT toggle - it
 *                         covers only the commander's own row(s), closing
 *                         itself back to the regular deck (a "Deck" toggle,
 *                         which FreeformImportDelegate's own MAINDECK regex
 *                         already recognizes) the moment a genuinely
 *                         unrecognized, non-neutral line is reached (the
 *                         commander's own type line does this immediately) -
 *                         otherwise the entire real maindeck that follows
 *                         would get swept into the sideboard too. Needed a
 *                         second check ahead of the output loop specifically
 *                         (verified empirically with a standalone harness
 *                         against the real capture - without it, this never
 *                         fired at all): Commander is always the very FIRST
 *                         section on this real site, so its header line
 *                         sits at scan.start - 1, outside the loop's own
 *                         [scan.start, scan.end] range (scan.start is, by
 *                         definition, the first MATCHED line - the
 *                         commander's own card row - never a header line
 *                         itself). "commander" also added to TYPE_WORDS, the
 *                         same collision-safety precedent as the "18 LANDS"
 *                         fix above.
 *     Rémi Dutil (2026) - the fix above only checked the SINGLE line right
 *                         before scan.start - correct for deckbox.org (its
 *                         header sits directly against the match) but a
 *                         real Archidekt capture puts 2 lines of per-card
 *                         metadata ("Qty: 1", "Price: $0.49") in between for
 *                         its own TRAILING-policy bare match, and a real
 *                         Moxfield capture puts 2 different lines ("(1)", a
 *                         standalone qty "1") in between for its own
 *                         PRECEDING-policy bare match - on both, the single-
 *                         line check missed the header entirely, so their
 *                         own commander still landed in the maindeck.
 *                         startsWithCommanderHeader() now walks backward up
 *                         to COMMANDER_LOOKBACK_LIMIT lines, skipping
 *                         whatever per-card junk a real site puts in
 *                         between, stopping early at another real match
 *                         (nothing further back than the previous card could
 *                         be this run's own header) - verified empirically
 *                         against all three real, independently-designed
 *                         site layouts (deckbox.org, Archidekt, Moxfield),
 *                         each correctly wrapping their own commander now.
 *     Rémi Dutil (2026) - dropped extractDeckSection()'s MagicLogger.log
 *                         dump of the entire extracted decklist text on
 *                         success - useful while designing this whole
 *                         Commander/scan()/TYPE_WORDS fix chain against real
 *                         captures, but every real "Import this page" click
 *                         from here on would otherwise write a full
 *                         decklist to the Eclipse log. The short "rejected,
 *                         falling back to the full page" notice above stays
 *                         - real signal, not noise.
 *******************************************************************************/
package com.reflexit.magiccards.core.exports;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.MagicLogger;
import com.reflexit.magiccards.core.model.IMagicCard;

public class DeckTextExtractor {
	// "4 Lightning Bolt", "4x Lightning Bolt", "x4 Lightning Bolt", "x 4 Lightning Bolt"
	private static final Pattern QTY_LINE = Pattern.compile("^(?:[xX]\\s*)?(\\d{1,3})\\s*[xX]?\\s+(\\S.{0,90})$");
	// a quantity with nothing else on the line ("4"), or a price with nothing
	// else ("$1.56") - table "plumbing" around a qty-then-name-on-separate-
	// lines row (see TCGplayer's shape in this class' own header)
	private static final Pattern STANDALONE_QTY = Pattern.compile("^\\d{1,3}$");
	private static final Pattern STANDALONE_PRICE = Pattern.compile("^[$£€¥]\\s?\\d[\\d,.]*$");

	/** Below this many matched lines, a run is not treated as a decklist - too
	 *  easy for page chrome to coincidentally produce a couple of matches.
	 *  Public so a caller showing a live "N card(s) found" count (see
	 *  {@link #countRecognizedCards}) can also say whether that count is
	 *  actually enough to be accepted. */
	public static final int MIN_MATCHES = 6;
	/** How many non-matching lines (section headers, blank lines) a run
	 *  tolerates in between matches before it is considered over. Raised from
	 *  the original 2 after two real captures needed more: Moxfield's
	 *  "Lands\n(38)\n+ 1 other" category-transition (3 non-neutral lines) and
	 *  Archidekt's per-card block ("Name\n[1-4 lines of rules text]\nType
	 *  line\n[qty]") which can need several more, plus its own multi-line
	 *  category-transition header ("Ramp\n(CTRL to add secondary)\nRemoval\n
	 *  Qty: 4\nPrice: $34.66"). The real protection against unrelated page
	 *  chrome bridging a run is the DB cross-check on every bare match plus
	 *  {@link #isStopSectionHeader}, not a tight gap budget. */
	private static final int MAX_GAP = 10;
	/** How many lines {@link #findTrailingQty} looks ahead of a bare match
	 *  for its own trailing quantity (see {@link QtyPolicy#TRAILING}) before
	 *  giving up - generous enough for the longest real card text block seen
	 *  so far (Archidekt), bounded so a genuinely qty-less page can't turn
	 *  this into a slow, essentially-unbounded scan. */
	private static final int TRAILING_SEARCH_LIMIT = 25;
	/** How many trailing words to try dropping off a candidate name before
	 *  giving up on it resolving to a real card (tolerates trailing junk
	 *  like a price or a mashed-in type line: "Lightning Bolt $0.25"). */
	private static final int MAX_NAME_TRIMS = 4;

	private DeckTextExtractor() {
	}

	public static boolean looksLikeDeckLine(String line) {
		return matchLine(line) != null;
	}

	/** A recognized decklist row: an optional explicit quantity (null for a
	 *  bare name, meaning an implicit 1) plus the already-resolved,
	 *  junk-trimmed card name. */
	private static final class Match {
		final String qty;
		final String name;

		Match(String qty, String name) {
			this.qty = qty;
			this.name = name;
		}

		String toLine() {
			return (qty == null || qty.isEmpty()) ? name : qty + " " + name;
		}
	}

	/** Recognizes a line as a decklist row. Two shapes are accepted: a
	 *  "&lt;qty&gt; &lt;name&gt;" line (e.g. "4 Lightning Bolt") - an
	 *  explicit leading count is decent evidence by itself, so this is
	 *  trusted even if the database can't confirm the name - and a bare
	 *  "&lt;name&gt;" line with no count at all (some sites render one row
	 *  per physical copy, or put the quantity in a separate column/line) -
	 *  which, having no shape evidence of its own, is ONLY accepted when the
	 *  database actually confirms it (otherwise nearly any nav label or
	 *  sentence fragment would qualify). Either way the returned name is the
	 *  already-resolved, trailing-junk-trimmed (price, mashed-in type line,
	 *  ...) card name, not the raw text - and {@code null} is returned when
	 *  nothing resolves, rejecting page noise like "12 Comments"/"45
	 *  Likes"/"1 Related Deck" that shares the qty+name shape but isn't a
	 *  card. */
	private static Match matchLine(String line) {
		String trimmed = normalizeSpaces(line).trim();
		if (trimmed.isEmpty())
			return null;
		Matcher m = QTY_LINE.matcher(trimmed);
		if (m.matches()) {
			String qty = m.group(1);
			String rest = m.group(2).trim();
			String resolved = resolveName(rest, true);
			return resolved == null ? null : new Match(qty, resolved);
		}
		// no explicit count - a bare name is far too easy to coincide with
		// ordinary page text (nav labels, prose) to trust on shape alone
		String resolved = resolveName(trimmed, false);
		return resolved == null ? null : new Match(null, resolved);
	}

	/** A section-header line naming the sideboard ("Sideboard", "Sideboard
	 *  [15]", "Sideboard (15)", ...) - recognized loosely by its first word,
	 *  so it can be normalized to the literal word FreeformImportDelegate's
	 *  own section-toggle detection looks for. */
	private static boolean isSideboardHeader(String trimmedLine) {
		return norm(trimmedLine.split("\\s", 2)[0]).equals("sideboard");
	}

	/** A section-header line naming the Commander (deckbox.org: a bare
	 *  "Commander" line ahead of the maindeck, with exactly the commander's
	 *  own card row(s) under it) - see {@link #extractDeckSection}'s own
	 *  one-shot Sideboard/Deck toggle around it: a commander is, by MTG
	 *  convention, tracked in the sideboard pile (separate from the 99-card
	 *  maindeck), not as its own concept ManaDesk's importer would otherwise
	 *  need to understand. */
	private static boolean isCommanderHeader(String trimmedLine) {
		return norm(trimmedLine.split("\\s", 2)[0]).equals("commander");
	}

	/** How many lines {@link #extractDeckSection} looks BACKWARD from the
	 *  run's very first match for a Commander header before giving up - real
	 *  captures showed the header is rarely directly adjacent to the match:
	 *  Archidekt puts 2 lines of per-card metadata ("Qty: 1", "Price:
	 *  $0.49") in between for its own TRAILING-policy bare match, Moxfield
	 *  puts 2 different lines ("(1)", a standalone qty "1") in between for
	 *  its own PRECEDING-policy bare match - only deckbox.org's same-line
	 *  qty+name match sits directly against the header. Generous but
	 *  bounded, matching MAX_GAP's own spirit. */
	private static final int COMMANDER_LOOKBACK_LIMIT = 10;

	/** Whether the run's first match (at {@code matches[scanStart]}) is
	 *  preceded by a Commander header within {@link #COMMANDER_LOOKBACK_LIMIT}
	 *  lines, skipping over whatever per-card metadata/placeholder junk a
	 *  real site puts in between (see that constant's own header) - stops
	 *  early the moment it reaches ANOTHER real match, since nothing further
	 *  back than the previous card could possibly be this run's own
	 *  Commander header. Verified empirically (standalone harness) against
	 *  three real, independently-designed site layouts: deckbox.org,
	 *  Archidekt and Moxfield all correctly wrap their own commander. */
	private static boolean startsWithCommanderHeader(Match[] matches, String[] norm, int scanStart) {
		int bound = Math.max(0, scanStart - COMMANDER_LOOKBACK_LIMIT);
		for (int j = scanStart - 1; j >= bound; j--) {
			if (isCommanderHeader(norm[j]))
				return true;
			if (matches[j] != null)
				return false;
		}
		return false;
	}

	/** Section headers naming a list of cards that are NOT part of the actual
	 *  deck - seen on deckstats.net ("Maybeboard"), and the same convention
	 *  elsewhere (Moxfield/Archidekt "Considering", a generic "Wishlist").
	 *  Unlike {@link #isSideboardHeader}, encountering one of these is a hard
	 *  stop for {@link #scan} - closes the current run outright rather than
	 *  just costing one gap tick - since these sections otherwise share the
	 *  exact same "qty / name" row shape as a real Sideboard and would
	 *  silently bleed into it. */
	private static final Set<String> STOP_SECTION_WORDS = new HashSet<>(
			java.util.Arrays.asList("maybeboard", "considering", "wishlist"));

	private static boolean isStopSectionHeader(String trimmedLine) {
		String first = trimmedLine.split("\\s", 2)[0];
		return STOP_SECTION_WORDS.contains(norm(first));
	}

	/** Card TYPE words (singular/plural) - never a valid resolution on their
	 *  own, even if the database happens to contain a same-named real card,
	 *  since a real capture (mtgtop8.com: "18 LANDS", "19 CREATURES", "20
	 *  INSTANTS and SORC.", "4 OTHER SPELLS" - a category header rendered as
	 *  "&lt;count&gt; &lt;TYPE&gt;" with no separating punctuation at all)
	 *  showed "LANDS" alone resolving to some database entry and getting
	 *  imported as a bogus extra "18 LANDS" card, while its sibling headers
	 *  happened not to collide with anything and were correctly rejected.
	 *  Category headers matching this exact "&lt;count&gt; &lt;TYPE&gt;"
	 *  shape are common (see this class' own header for TCGplayer/deckstats/
	 *  Archidekt's own bracketed/parenthesized variants) - the database
	 *  cross-check is meant to be the safety net against exactly this, so a
	 *  coincidental collision on one specific type word is closed off
	 *  explicitly rather than relying on the database never containing a
	 *  same-named card. */
	private static final Set<String> TYPE_WORDS = new HashSet<>(java.util.Arrays.asList("land", "lands", "creature",
			"creatures", "instant", "instants", "sorcery", "sorceries", "artifact", "artifacts", "enchantment",
			"enchantments", "planeswalker", "planeswalkers", "battle", "battles", "tribal", "kindred", "spell",
			"spells", "other", "commander"));

	/** @param trustShapeIfDbUnavailable whether to accept the name when the
	 *  database isn't available to confirm it - true for a qty-prefixed
	 *  line (the leading count is its own evidence), false for a bare line
	 *  (which would otherwise accept almost anything).
	 *  @return the matched, already-trimmed card name, or {@code null}. */
	private static String resolveName(String name, boolean trustShapeIfDbUnavailable) {
		Set<String> names = normNames();
		if (names.isEmpty())
			return trustShapeIfDbUnavailable && !TYPE_WORDS.contains(norm(name)) ? name : null;
		String candidate = name;
		for (int trims = 0; trims < MAX_NAME_TRIMS; trims++) {
			String normCandidate = norm(candidate);
			if (!TYPE_WORDS.contains(normCandidate) && names.contains(normCandidate))
				return candidate;
			int lastSpace = candidate.lastIndexOf(' ');
			if (lastSpace <= 0)
				break;
			candidate = candidate.substring(0, lastSpace).trim();
		}
		return null;
	}

	/** A real capture from mtgdecks.net (and likely other sites that visually
	 *  align a "qty / name / price" table) turned out to use non-breaking
	 *  spaces (and possibly other Unicode space variants) between columns
	 *  instead of plain ASCII spaces - Java's {@code \s} regex class only
	 *  matches plain ASCII whitespace by default, so QTY_LINE silently
	 *  matched nothing at all on that page (every single line was rejected).
	 *  Normalize these to a plain space before matching. Uses numeric casts
	 *  rather than {@code \\uXXXX} char-literal escapes, which are prone to
	 *  being silently decoded into the actual (invisible) character by
	 *  whatever wrote this source - unambiguous either way to javac, but
	 *  only one of them stays readable in an editor/diff.
	 *  <p>Also converts literal tab characters to a plain space: a real
	 *  deckbox.org capture separates a "qty / name / price" row's columns
	 *  with tabs ("1\tBrooding Saurian\t\t$0.38"), and resolveName()'s own
	 *  trim-loop looks for the LAST plain space character to find where
	 *  trailing junk (the price) starts - with no plain space there at all,
	 *  it instead found the name's own internal space ("Brooding
	 *  Saurian" -&gt; "Brooding") and failed to resolve. QTY_LINE's own
	 *  {@code \s} already matches a tab fine; this is only needed for that
	 *  trim-loop. */
	private static String normalizeSpaces(String s) {
		return s.replace((char) 0x00A0, ' ') // non-breaking space
				.replace((char) 0x2007, ' ') // figure space
				.replace((char) 0x2009, ' ') // thin space
				.replace((char) 0x202F, ' ') // narrow no-break space
				.replace((char) 0x200B, ' ') // zero-width space
				.replace('\t', ' ');
	}

	/** Normalized ({@link #norm}) card names, lazily built from the local card
	 *  database. Cached once a non-empty index is built - NOT cached on
	 *  failure/emptiness, since that would permanently disable the
	 *  cross-check for the rest of the session if this runs before the
	 *  background DB load finishes; an empty result is simply recomputed
	 *  next call. */
	private static volatile Set<String> NORM_NAMES;

	private static Set<String> normNames() {
		Set<String> s = NORM_NAMES;
		if (s != null)
			return s;
		Set<String> built = new HashSet<>();
		try {
			for (IMagicCard c : DataManager.getInstance().getMagicDBStore())
				built.addAll(faces(c.getName()));
		} catch (Throwable t) {
			// DB not available in this environment/at this time - resolveName()
			// falls back to shape-only matching for this call
		}
		if (!built.isEmpty())
			NORM_NAMES = built;
		return built;
	}

	private static Set<String> faces(String name) {
		Set<String> out = new HashSet<>();
		if (name == null)
			return out;
		out.add(norm(name));
		for (String p : name.split("//"))
			out.add(norm(p));
		out.remove("");
		return out;
	}

	/** Lower-case, accents and every non-alphanumeric char stripped - same
	 *  normalization DeckImportPreviewPage's own norm() uses. */
	private static String norm(String s) {
		if (s == null)
			return "";
		s = Normalizer.normalize(s, Normalizer.Form.NFD);
		StringBuilder b = new StringBuilder();
		for (char c : s.toLowerCase().toCharArray())
			if (Character.isLetterOrDigit(c) && Character.getType(c) != Character.NON_SPACING_MARK)
				b.append(c);
		return b.toString();
	}

	/** A line that carries no card-name evidence of its own, but is expected
	 *  "table plumbing" around a decklist row on sites that split qty/name/
	 *  price across separate lines - a blank line, a bare quantity with
	 *  nothing else on the line, or a bare price. Never breaks a run by
	 *  itself; only a genuinely unrecognized line (a category header like
	 *  "Sorcery (8)") still counts against {@link #MAX_GAP}. */
	private static boolean isNeutralGapLine(String normalizedTrimmed) {
		return normalizedTrimmed.isEmpty() || STANDALONE_QTY.matcher(normalizedTrimmed).matches()
				|| STANDALONE_PRICE.matcher(normalizedTrimmed).matches();
	}

	/** The run of consecutive (gap-tolerant) matched lines found by {@link
	 *  #scan}: {@code bestCount == 0} when nothing matched at all. */
	private static final class Scan {
		final int start, end, count;

		Scan(int start, int end, int count) {
			this.start = start;
			this.end = end;
			this.count = count;
		}
	}

	/** Finds the longest run of matched lines in {@code matches}, tolerating
	 *  {@link #isNeutralGapLine} lines freely and up to {@link #MAX_GAP}
	 *  anything-else lines in between. Shared by {@link #extractDeckSection}
	 *  and {@link #countRecognizedCards} - the count alone is also useful on
	 *  its own, to show the user a live "N card(s) found" sanity-check
	 *  before they commit to importing the current page. */
	private static Scan scan(String[] norm, Match[] matches) {
		int bestStart = -1, bestEnd = -1, bestCount = 0;
		int runStart = -1, lastMatch = -1, gap = 0, count = 0;
		boolean everMatched = false;
		for (int i = 0; i < matches.length; i++) {
			// a Maybeboard/Considering/Wishlist section starts here - but
			// only once a real decklist run has actually been found at
			// least once. A real deckbox.org capture showed this firing on
			// its own sidebar nav ("MTG COLLECTION: Inventory / Tradelist /
			// Wishlist" - just a collection-management link, line 12 of the
			// page, nowhere near the actual 100-card decklist starting at
			// line 71) and killing the scan before it ever reached a single
			// real card - the exact same class of bug as TYPE_WORDS below
			// (unrelated page chrome coincidentally matching a heuristic
			// keyword). Closing just the current run isn't enough once we
			// ARE past real deck content - a real TappedOut capture had a
			// 34-row Maybeboard section (more cards than the real 21-row
			// main+sideboard deck above it), so IT became the longest run
			// on the page and got picked as "the decklist" instead, exactly
			// the opposite of what this is meant to prevent - so nothing
			// from here to the end of the page is eligible once a run has
			// been seen.
			if (everMatched && isStopSectionHeader(norm[i]))
				break;
			if (matches[i] != null) {
				everMatched = true;
				if (runStart < 0)
					runStart = i;
				lastMatch = i;
				count++;
				gap = 0;
			} else if (runStart >= 0) {
				if (isNeutralGapLine(norm[i]))
					continue;
				gap++;
				if (gap > MAX_GAP) {
					if (count > bestCount) {
						bestStart = runStart;
						bestEnd = lastMatch;
						bestCount = count;
					}
					runStart = -1;
					lastMatch = -1;
					gap = 0;
					count = 0;
				}
			}
		}
		if (runStart >= 0 && count > bestCount) {
			bestStart = runStart;
			bestEnd = lastMatch;
			bestCount = count;
		}
		return new Scan(bestStart, bestEnd, bestCount);
	}

	private static String[] normalizeLines(String[] lines) {
		String[] norm = new String[lines.length];
		for (int i = 0; i < lines.length; i++)
			norm[i] = normalizeSpaces(lines[i]).trim();
		return norm;
	}

	/** Which line supplies a bare (no same-line qty) match's quantity. Two
	 *  real, mutually exclusive site conventions have been observed:
	 *  <ul>
	 *  <li>{@code PRECEDING} - TCGplayer/Moxfield: "4\nDragon's Rage
	 *  Channeler", the qty comes immediately before the name.</li>
	 *  <li>{@code TRAILING} - Archidekt: "Boseiju, Who Endures\n[1-4 lines of
	 *  rules text]\nLegendary Land\n1", the qty comes some distance AFTER the
	 *  name, following an arbitrarily long card-text block, and (unlike
	 *  PRECEDING) is reliably present for every card regardless of its
	 *  position within a category - PRECEDING fails for the first card of
	 *  every Archidekt category, since the line right before its name is
	 *  part of the category's own header, not a quantity.</li>
	 *  </ul>
	 *  Both conventions also present a plausible-looking (but wrong)
	 *  candidate for the OTHER direction - TCGplayer's preceding-qty line is
	 *  also reachable by a naive forward search, but it actually belongs to
	 *  the FOLLOWING card, and vice versa for Archidekt - so this can't be
	 *  decided per line. The whole page is scanned once under each policy
	 *  instead, and whichever finds the larger run wins (see
	 *  #bestMatchesAndScan). */
	private enum QtyPolicy {
		PRECEDING, TRAILING
	}

	/** @param norm the same lines, normalized/trimmed (see {@link
	 *  #normalizeLines}) - needed to check a bare match's neighboring
	 *  line(s). */
	private static Match[] matchLines(String[] lines, String[] norm, QtyPolicy policy) {
		Match[] raw = new Match[lines.length];
		for (int i = 0; i < lines.length; i++)
			raw[i] = matchLine(lines[i]);
		Match[] result = new Match[lines.length];
		for (int i = 0; i < raw.length; i++) {
			Match m = raw[i];
			if (m == null)
				continue;
			if (m.qty != null) {
				// same-line qty ("4 Lightning Bolt") - already unambiguous,
				// independent of policy
				result[i] = m;
				continue;
			}
			if (policy == QtyPolicy.PRECEDING) {
				// a bare match is only trusted when the line right before it
				// is a standalone quantity - a bare match with no such
				// preceding qty is most likely a stray repeat of an
				// already-listed card's name elsewhere on the page (see this
				// class' own header: a "product details" panel repeating the
				// deck's first card was misread as an extra 1x copy)
				boolean hasPrecedingQty = i > 0 && STANDALONE_QTY.matcher(norm[i - 1]).matches();
				if (hasPrecedingQty)
					result[i] = m;
			} else {
				String trailingQty = findTrailingQty(raw, norm, i);
				if (trailingQty != null)
					result[i] = new Match(trailingQty, m.name);
			}
		}
		return result;
	}

	/** Looks ahead of a bare match (index {@code from} in {@code raw}) for its
	 *  own trailing quantity (see {@link QtyPolicy#TRAILING}) - the first
	 *  standalone quantity line reached WITHOUT first hitting another raw
	 *  match (another card's name - this card's own block is over, with no
	 *  quantity found) or a section-boundary header (Sideboard, Maybeboard,
	 *  ... - a quantity beyond one of these belongs to a different section,
	 *  never this card, even though {@link #scan} itself is allowed to
	 *  bridge PAST a Sideboard header). Blank lines, prose and standalone
	 *  prices are skipped freely - that's the whole point, an Archidekt card
	 *  block can have several lines of rules text before its type line and
	 *  quantity. Returns {@code null} if nothing is found within {@link
	 *  #TRAILING_SEARCH_LIMIT} lines. */
	private static String findTrailingQty(Match[] raw, String[] norm, int from) {
		int bound = Math.min(norm.length, from + 1 + TRAILING_SEARCH_LIMIT);
		for (int j = from + 1; j < bound; j++) {
			if (raw[j] != null)
				return null;
			if (isSideboardHeader(norm[j]) || isStopSectionHeader(norm[j]))
				return null;
			if (STANDALONE_QTY.matcher(norm[j]).matches())
				return norm[j];
		}
		return null;
	}

	/** The result of scanning a page under one {@link QtyPolicy}. */
	private static final class PolicyResult {
		final Match[] matches;
		final Scan scan;

		PolicyResult(Match[] matches, Scan scan) {
			this.matches = matches;
			this.scan = scan;
		}
	}

	/** Scans {@code lines} under both {@link QtyPolicy} conventions and keeps
	 *  whichever finds the larger run - see {@link QtyPolicy}'s own header for
	 *  why this can't be decided per line. Ties (including the common case of
	 *  TRAILING finding nothing at all, e.g. every site seen so far except
	 *  Archidekt) keep PRECEDING, preserving the exact, already-verified
	 *  behavior for every other real site this was tested against. */
	private static PolicyResult bestMatchesAndScan(String[] lines, String[] norm) {
		Match[] preceding = matchLines(lines, norm, QtyPolicy.PRECEDING);
		Scan precedingScan = scan(norm, preceding);
		Match[] trailing = matchLines(lines, norm, QtyPolicy.TRAILING);
		Scan trailingScan = scan(norm, trailing);
		if (trailingScan.count > precedingScan.count)
			return new PolicyResult(trailing, trailingScan);
		return new PolicyResult(preceding, precedingScan);
	}

	/** A live sanity-check result: {@code total} physical cards (quantities
	 *  summed) and {@code unique} distinct card names - the same "Total N
	 *  (unique M)" convention the rest of ManaDesk already uses (e.g. the
	 *  deck/collection views), rather than a bare row count that doesn't
	 *  match what actually gets imported (a distinct-rows count reads as
	 *  "30" for a deck that really has 60 maindeck + 15 sideboard cards). */
	public static final class CardCount {
		public final int total;
		public final int unique;

		private CardCount(int total, int unique) {
			this.total = total;
			this.unique = unique;
		}
	}

	private static final CardCount NO_CARDS = new CardCount(0, 0);

	/** How many cards {@link #extractDeckSection} would find in {@code
	 *  fullText} right now - {@code unique} is the exact same row count it
	 *  compares against {@link #MIN_MATCHES} to decide whether to accept a
	 *  block, exposed so a caller (BrowseWebsiteDialog) can show it to the
	 *  user as a live sanity-check before they commit to importing the
	 *  current page, without needing to dig through the debug log. */
	public static CardCount countRecognizedCards(String fullText) {
		if (fullText == null || fullText.isEmpty())
			return NO_CARDS;
		String[] lines = fullText.split("\r?\n");
		String[] norm = normalizeLines(lines);
		PolicyResult pr = bestMatchesAndScan(lines, norm);
		Match[] matches = pr.matches;
		Scan scan = pr.scan;
		if (scan.count == 0)
			return NO_CARDS;
		int total = 0;
		Set<String> uniqueNames = new HashSet<>();
		for (int i = scan.start; i <= scan.end; i++) {
			Match m = matches[i];
			if (m == null)
				continue;
			uniqueNames.add(norm(m.name));
			// every surviving bare match is guaranteed a preceding standalone
			// quantity by matchLines() above, so this is always resolvable
			String qtyStr = m.qty != null ? m.qty : norm[i - 1];
			int qty;
			try {
				qty = Integer.parseInt(qtyStr);
			} catch (NumberFormatException e) {
				qty = 1;
			}
			total += qty;
		}
		return new CardCount(total, uniqueNames.size());
	}

	/** Finds the block of lines within {@code fullText} that most looks like a
	 *  decklist, and returns a cleaned "&lt;qty&gt; &lt;name&gt;" line per
	 *  recognized row (non-matching lines - blank lines, standalone prices/
	 *  quantities, category headers like "Artifact [7]" - are dropped,
	 *  except a "Sideboard" header, kept so FreeformImportDelegate's own
	 *  section-toggle detection still fires; a bare-name match whose
	 *  preceding line is a standalone quantity picks up that quantity, for
	 *  sites that put qty and name on separate lines). Returns {@code null}
	 *  if nothing looks sufficiently deck-shaped, so the caller can fall
	 *  back to the full text rather than lose it outright. */
	public static String extractDeckSection(String fullText) {
		if (fullText == null || fullText.isEmpty())
			return null;
		String[] lines = fullText.split("\r?\n");
		String[] norm = normalizeLines(lines);
		PolicyResult pr = bestMatchesAndScan(lines, norm);
		Match[] matches = pr.matches;
		Scan scan = pr.scan;
		boolean dbActive = !normNames().isEmpty();
		if (scan.count < MIN_MATCHES) {
			MagicLogger.log("DeckTextExtractor: " + lines.length + " line(s) scanned, best run " + scan.count
					+ " match(es) (need " + MIN_MATCHES + ") - rejected, falling back to the full page. DB cross-check "
					+ (dbActive ? "active." : "NOT active (database not loaded yet)."));
			return null;
		}
		StringBuilder sb = new StringBuilder();
		// a Commander section is a ONE-SHOT sideboard toggle, unlike a real
		// Sideboard header (which stays on for the rest of the run): it
		// covers only the commander's own card row(s) directly under it -
		// closed back into the regular deck (FreeformImportDelegate's own
		// "Deck" toggle) the moment a genuinely unrecognized, non-neutral
		// line is reached (the commander's own type line, e.g. "Legendary
		// Creature - Dragon", immediately does this) - so the real 99-card
		// maindeck that follows is never swept into the sideboard too. Three
		// real captures showed Commander is always the very FIRST section,
		// ahead of the entire maindeck - its header line is never directly
		// adjacent to scan.start (scan.start is, by definition, the first
		// MATCHED line - the commander's own card row - never a header line
		// itself; see startsWithCommanderHeader's own header for exactly how
		// far back each real site puts it), so it needs its own check ahead
		// of the loop.
		boolean inCommanderBlock = startsWithCommanderHeader(matches, norm, scan.start);
		if (inCommanderBlock)
			sb.append("Sideboard").append('\n');
		for (int i = scan.start; i <= scan.end; i++) {
			Match m = matches[i];
			if (m != null) {
				String qty = m.qty;
				if (qty == null && i > 0 && STANDALONE_QTY.matcher(norm[i - 1]).matches())
					qty = norm[i - 1];
				sb.append(new Match(qty, m.name).toLine()).append('\n');
			} else if (isSideboardHeader(norm[i])) {
				sb.append("Sideboard").append('\n');
				inCommanderBlock = false; // a real Sideboard header always wins
			} else if (isCommanderHeader(norm[i])) {
				sb.append("Sideboard").append('\n');
				inCommanderBlock = true;
			} else if (inCommanderBlock && !isNeutralGapLine(norm[i])) {
				sb.append("Deck").append('\n');
				inCommanderBlock = false;
			}
			// else: blank line / standalone price or quantity / category
			// header ("Artifact [7]") - dropped from the output rather than
			// fed to the importer as a bogus card
		}
		if (inCommanderBlock)
			sb.append("Deck").append('\n'); // the commander was the very last thing in the run
		return sb.toString().trim();
	}
}
