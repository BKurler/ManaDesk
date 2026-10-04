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
 *     Rémi Dutil (2026) - a real TappedOut capture (fast-gishath-dino-vomit)
 *                         showed the Commander fix chain above still missed
 *                         its commander, for two compounding reasons unique
 *                         to this site: (1) its "Commander" category isn't
 *                         always first (it sat between Land and Enchantment
 *                         here) - already handled by the mid-loop
 *                         isCommanderHeader() check in extractDeckSection(),
 *                         so this alone wasn't the gap; (2) the commander's
 *                         only text (recovered via
 *                         CAPTURE_TEXT_WITH_IMAGE_ALT_SCRIPT's alt-text
 *                         substitution) is "Commander: Gishath, Sun's
 *                         Avatar" - norm() stripping the colon made
 *                         isCommanderHeader() treat this CARD line as a
 *                         second, redundant HEADER instead (now excluded via
 *                         a literal colon check), and even once excluded,
 *                         resolveName()'s trim-from-the-end loop could never
 *                         strip the leading "Commander: " junk (added
 *                         COMMANDER_PREFIX to matchLine() for this specific
 *                         shape) - and even once resolved to a real bare
 *                         match, matchLines()'s "needs adjacent qty
 *                         evidence" anti-false-positive rule (from the
 *                         earlier TCGplayer "Product Details" stray-mention
 *                         fix) rejected it anyway, since nothing marks a
 *                         commander's own line with an explicit qty on this
 *                         site. A bare match immediately preceded by a
 *                         Commander header is now trusted unconditionally,
 *                         same as a qty-prefixed line - a commander is
 *                         unambiguously always exactly 1 copy, and the
 *                         header itself is the evidence. Verified
 *                         empirically (standalone harness) against the real
 *                         capture: the commander is now correctly wrapped
 *                         in its own Sideboard/Deck pair. A separate,
 *                         unrelated real gap on the same page - "Aura
 *                         ShardsGC" (extra text glued directly onto a card
 *                         name with NO separating space, so even the
 *                         existing trailing-trim-by-WORD loop can't recover
 *                         it) - was deliberately left alone; a character-
 *                         level trim-back would risk real false positives
 *                         elsewhere and needs its own, separately-considered
 *                         fix.
 *     Rémi Dutil (2026) - added GLUED_TRAILING_BADGE to resolveName() for
 *                         the "Aura ShardsGC" gap above: a "GC" (Game
 *                         Changer, this deck's own Commander Bracket 3)
 *                         badge rendered with no space at all between it
 *                         and the card's own name - the existing trim-by-
 *                         WORD loop can never help here (there is no space
 *                         to trim on), so this is a deliberately narrow,
 *                         separate check: strip a short (1-4 letter) run of
 *                         UPPERCASE glued directly onto the end, only when
 *                         genuinely unspaced (the character right before it
 *                         is lowercase/apostrophe/digit/closing-paren, never
 *                         itself part of an all-caps word). Real MTG card
 *                         names are Title Case and never end in a bare,
 *                         unspaced uppercase run, so this is safe to try
 *                         unconditionally rather than a general character-
 *                         level backoff (which was rejected above for real
 *                         false-positive risk) - this only strips a shape
 *                         that could never legitimately be part of a real
 *                         name to begin with.
 *     Rémi Dutil (2026) - implemented the universal "Commander has no
 *                         official sideboard" rule per explicit request: a
 *                         "Sideboard" header following a genuine Commander
 *                         header now gets the same hard-stop scan() already
 *                         gives Maybeboard/Considering/Wishlist - not just
 *                         tagged and imported alongside the deck - since no
 *                         site's "Sideboard" label is ever a legal one on a
 *                         Commander deck, whatever it's called. Needed
 *                         isCommanderHeader() to stop being trusted
 *                         unconditionally: TCGplayer's own nav chrome has a
 *                         "Commander Bestiary" content section (nowhere near
 *                         its actual Modern deck, which has a real,
 *                         legitimate 15-card sideboard) - added
 *                         isFollowedByCommanderMatch() (the forward
 *                         counterpart of startsWithCommanderHeader) so a
 *                         bare "first word is Commander" line only counts
 *                         once it is genuinely followed by a real card.
 *                         Even that wasn't quite enough for extractDeckSection()'s
 *                         own OUTPUT loop: a real Archidekt capture's per-
 *                         card category-transition widget repeats
 *                         "Commander" as the outgoing category name right
 *                         after Thranduil's own block closes, and that
 *                         stray mention IS followed by the next (unrelated)
 *                         real card within the lookback window too - so
 *                         adjacency alone can't tell a second, stray mention
 *                         apart from a genuine one. A deck has at most ONE
 *                         real commander section, so the output loop now
 *                         only ever honors the first one it finds (pre-loop
 *                         or mid-loop) and ignores any further "Commander"
 *                         sighting entirely.
 *     Rémi Dutil (2026) - added QtyPolicy.PRECEDING_GAP/findPrecedingQty():
 *                         a real tipsymagic.com capture ("List" view) put
 *                         each card's own quantity BEFORE its name but with a
 *                         couple of blank/whitespace-only lines in between,
 *                         a shape neither existing policy actually handles -
 *                         strict PRECEDING only checks the immediately
 *                         preceding line, and TRAILING (searching forward
 *                         from the name) kept borrowing the NEXT card's own
 *                         quantity instead, which happened to still sum
 *                         right card-by-card purely by coincidence (this
 *                         deck's quantities repeat the same couple of
 *                         values) right up until the very last card on the
 *                         page, which has nothing after it but footer text
 *                         and was silently dropped entirely (a real count:
 *                         60 cards read as 56). See the new policy/method's
 *                         own doc comments for the full reasoning.
 *     Rémi Dutil (2026) - a real tipsymagic.com capture (Lord High
 *                         Artificer, a genuine Commander deck with a real
 *                         Sideboard section) read 101 cards instead of 100 on
 *                         3 of its 4 view modes. Two compounding gaps: (1)
 *                         this site's own category header is "Commanders"
 *                         (PLURAL) - isCommanderHeader() only ever recognized
 *                         the singular, so the universal Commander-no-
 *                         sideboard rule never even recognized these decks
 *                         as Commander-format; (2) once fixed, the existing
 *                         hard-stop-at-Sideboard behavior turned out to only
 *                         ever have been correct by coincidence - every real
 *                         site checked so far happened to put Sideboard
 *                         LAST, but tipsymagic.com puts it in the MIDDLE of
 *                         its own fixed category order, with real Instants/
 *                         Sorceries/Enchantments sections after it that a
 *                         hard stop would have wrongly dropped too. Replaced
 *                         with a proper skip-zone (see typeHeaderCategory())
 *                         that resumes at the next category header NOT
 *                         already used earlier in the run - a real
 *                         TappedOut/Archidekt Sideboard's own internal per-
 *                         type sub-headers (mirroring its maindeck's own
 *                         structure) needed the "already seen" qualifier
 *                         specifically so they don't each wrongly end the
 *                         skip zone on their own. The "Flat Sorted View"
 *                         table shape (see matchTableRow()) has no separate
 *                         Sideboard header LINE at all - its own Section
 *                         COLUMN needed threading through as Match#section
 *                         instead, checked upfront over the whole page
 *                         (rows are sorted by PRICE, not section, so the
 *                         Sideboard row can appear before the Commander row
 *                         reveals this is even a Commander deck). All three
 *                         of scan()/extractDeckSection()/countRecognized-
 *                         Cards() needed the identical fix - they'd drifted
 *                         out of sync with each other before (the live
 *                         count and the real import must always agree).
 *     Rémi Dutil (2026) - the same real tipsymagic.com deck's "Card view"
 *                         (the one mode with no per-card −/+ stepper under
 *                         the Commander specifically - every OTHER card has
 *                         one) still read 99 instead of 100: matchLines()'s
 *                         own "trust a bare match preceded by a Commander
 *                         header even with no adjacent qty evidence" rule
 *                         only ever checked the SINGLE immediately preceding
 *                         line, but this capture puts 2 lines of its own
 *                         chrome ("— 1", "All decks with this commander")
 *                         between "Commanders" and the commander's own bare
 *                         name - short exactly the same way a real
 *                         Archidekt/Moxfield capture already needed
 *                         startsWithCommanderHeader()'s bounded backward
 *                         scan for a DIFFERENT purpose (see that method's
 *                         own header). Reused it here instead of maintaining
 *                         a second, stricter check that happened to miss
 *                         this real site's own structure.
 *     Rémi Dutil (2026) - a real Archidekt precon capture ("Calling All
 *                         Angels - Foundations Commander") read 55 cards
 *                         instead of 100 - the worst undercount seen this
 *                         whole session, traced to three compounding causes.
 *                         (1) this deck's own per-card shape also has a
 *                         printing/collector-number "stamp" line directly
 *                         above the bare qty ("Austere Command (fdc) 18\n1")
 *                         - resolveName()'s trim loop happily strips "(fdc)"/
 *                         the number and resolves it as a SECOND, phantom
 *                         match for a card already found at its own clean
 *                         name line. On its own that just double-counts;
 *                         the real damage is that findTrailingQty() stops
 *                         at the FIRST other raw match it reaches, so the
 *                         clean name's own forward search always hit this
 *                         stamp line first and gave up with NO quantity -
 *                         only the stamp line, much closer to the "1", ever
 *                         actually resolved. New REPRINT_STAMP pattern
 *                         rejects the stamp line outright in matchLine(), so
 *                         it's simply never a raw match to begin with. (2)
 *                         Archidekt's own "no price data for this source"
 *                         placeholder ("$----") isn't a real price
 *                         (STANDALONE_PRICE requires a digit) - widened to
 *                         accept it too. (3) even with (1) and (2), fixing
 *                         (1) alone made the FIRST category transition
 *                         WORSE, not better - the phantom stamp match had
 *                         been the only thing keeping that one gap under
 *                         budget. scan()'s own run-continuity gap counting
 *                         now also treats a category/type-header word as
 *                         neutral (expected structural content at a section
 *                         boundary, not "wandered off the decklist" noise) -
 *                         scoped to scan() alone, not the shared
 *                         isNeutralGapLine() extractDeckSection() also uses
 *                         to decide when a Commander's own block closes,
 *                         which genuinely needs the type line to count as
 *                         real content. All three together were needed -
 *                         verified against the real capture, which needs
 *                         all three before its own count matches its stated
 *                         "Size: 100" exactly.
 *     Rémi Dutil (2026) - a real AetherHub capture filled the New Deck
 *                         wizard's Name field with "Apps" - this site
 *                         renders its own title and format TOGETHER on one
 *                         line ("Modern - Boros Aggro"), unlike every other
 *                         real site checked, where they're always separate
 *                         lines. findAdjacentTitle()'s separate-line search
 *                         landed on whatever sits immediately above that one
 *                         line instead - AetherHub's own site-wide nav has
 *                         "Apps" (its Apps/tools menu item) directly above
 *                         it. New titleEmbeddedWithFormat() checks the
 *                         matched format line itself first, confirming the
 *                         split is really right after the format (not just
 *                         any hyphen) by requiring the prefix alone to also
 *                         resolve via matchFormatAlias() on its own.
 *     Rémi Dutil (2026) - the same real AetherHub capture reported a
 *                         correct card count but its real Sideboard section
 *                         (13 cards) wasn't auto-detected - isSideboardHeader()
 *                         recognized "Sideboard" but this site's own header
 *                         reads "Side 13 cards (5 distinct)". The cards
 *                         still imported fine (nothing stopped the scan),
 *                         just without the "Sideboard" tag
 *                         hasSideboardMarker() looks for, so the New Deck
 *                         wizard's "Also create a Sideboard" checkbox never
 *                         auto-checked itself. Added "side" alongside
 *                         "sideboard".
 *     Rémi Dutil (2026) - added "scratchpad" to STOP_SECTION_WORDS - a real
 *                         deckbox.org capture ("Devon Krynicki's WUBRG -
 *                         Legends Matter") has its own "Scratchpad - 41
 *                         cards, 40 distinct" section (the page's own
 *                         description: cards being considered but NOT
 *                         actually in the built deck), which bled straight
 *                         into the count - a 100-card Commander deck (its
 *                         real Sideboard already correctly excluded) read
 *                         as 141.
 *     Rémi Dutil (2026) - factored the Sideboard-section check out of
 *                         AbstractCardListImportPage's own previously-
 *                         private hasSideboardMarker() into a new public
 *                         hasSideboardSection() here, so BrowseWebsiteDialog
 *                         can share it too for a live "Sideboard detected"
 *                         indicator while browsing, rather than only
 *                         finding out after "Import this page".
 *     Rémi Dutil (2026) - added GLUED_FOIL_SUFFIX - a real TappedOut
 *                         capture ("2x Warren Instigatorfoil") glues a
 *                         foil-print indicator directly onto the card's own
 *                         name with no separating space, exactly
 *                         GLUED_TRAILING_BADGE's own "Aura ShardsGC" shape
 *                         but lowercase, so that pattern's all-uppercase
 *                         requirement never caught it. The card was
 *                         silently dropped entirely - a real observed
 *                         count, 60 cards read as 58 (21 unique read as
 *                         20).
 *     Rémi Dutil (2026) - a round of real-site title/format detection bugs,
 *                         all traced to the same root cause: a PARTIAL
 *                         alias-prefix match (the line has more content
 *                         than just the format name) was trusted exactly
 *                         like a real descriptive subtitle (TappedOut's own
 *                         "Commander / EDH Bracket 3 Dinosaurs RGW (Naya)",
 *                         8 words) even when the extra content was really
 *                         just a short UI label - a real deckstats.net
 *                         capture's own "Commanders" nav-menu link (1 word)
 *                         and a real deckbox.org capture's own "Commander
 *                         Bracket" field label (2 words) both won this way,
 *                         landing on neighboring chrome as the "title"
 *                         (deckstats: "Cards", its own nav link next to
 *                         it). Now requires a partial match's own extra
 *                         content to be at least 3 words; an EXACT match
 *                         (Moxfield's own bare "COMMANDER" tag, mtgtop8's
 *                         own bare "Modern") is unaffected. Added three new
 *                         title-only detectors to recover what the
 *                         tightened format matching alone would otherwise
 *                         leave unfound: titleEmbeddedWithBy() ("&lt;Title&gt;
 *                         by &lt;Author&gt;" on one line - MTGGoldfish's own
 *                         "Mono-Green Landfall by Nicolas D'Ambrose"),
 *                         titleEmbeddedWithPossessive() ("&lt;Username&gt;'s
 *                         &lt;Title&gt;" - deckbox.org's own "wrensleigh's
 *                         Power Hungry"), and a short-ALL-CAPS-tag fallback
 *                         (deckstats.net's own "MB oldschool rev 2\nCASUAL" -
 *                         "Casual" isn't a real tracked Format, so it was
 *                         never a candidate for the format value itself,
 *                         but it's still a reliable title anchor). Both new
 *                         same-line detectors are length-capped and reject
 *                         a candidate containing a period - a real
 *                         Archidekt capture's own UI instruction text
 *                         ("...using the View as &amp; Group by drop-downs
 *                         above.") also contains " by " as an ordinary
 *                         connector word in an unrelated sentence, and
 *                         without this safeguard it was wrongly picked up
 *                         as the title once Archidekt's own (format-
 *                         anchored) title lookup came up empty.
 *     Rémi Dutil (2026) - a later real Moxfield capture of the same deck
 *                         used above (testRealMoxfieldCapture) now renders
 *                         an extra avatar alt-text line, "&lt;Username&gt;'s
 *                         Profile Picture", directly above the username/
 *                         title block - closer to scan.start than the real
 *                         title, so titleEmbeddedWithPossessive() (added
 *                         above) matched it first and returned "Profile
 *                         Picture" instead of "Jace polymorph WIP".
 *                         isPlausibleTitle()'s TITLE_CHROME_WORDS blocklist
 *                         already had "profile" (added for a deckbox.org
 *                         "profile" link, see titleEmbeddedWithPossessive()'s
 *                         own header) but only as an exact whole-candidate
 *                         match, so the two-word "Profile Picture" slipped
 *                         through - added "profile picture" and "avatar"
 *                         (the same real shape on other sites) alongside it.
 *     Rémi Dutil (2026) - a real deckbox.org capture with a First/Last
 *                         display name ("Devon Krynicki's WUBRG - Legends
 *                         Matter") found NOTHING at all (a real observed
 *                         bug: the New Deck wizard's Name field stayed
 *                         blank) - TITLE_POSSESSIVE's own "^\S+'s" only
 *                         ever matched a single-token username with no
 *                         internal spaces (deckbox.org's own single-word
 *                         "wrensleigh" case), and "Devon" alone doesn't end
 *                         in "'s". Widened to allow up to 2 leading words
 *                         before the "'s" word - but that alone reintroduced
 *                         a regression caught by re-running the full suite:
 *                         a real TCGplayer capture's own ad copy, "Try
 *                         "collector's rare effect monsters"", now wrongly
 *                         matched too ("Try" as the 1 leading word, the
 *                         quote-glued "&quot;collector's" as the anchor),
 *                         returning "rare effect monsters" as a bogus
 *                         title. Fixed by requiring the "'s" word itself to
 *                         START WITH A LETTER - a real username never has a
 *                         stray leading quote character glued to it, only
 *                         ad-copy quotation does.
 *     Rémi Dutil (2026) - a real TappedOut capture ("Let's build: Zaxara,
 *                         the Exemplary") had no title text anywhere in the
 *                         page's own innerText at all - this site's own
 *                         "Commander" category happened to sort AFTER
 *                         Land/Creature/Enchantment/Instant, so scan.start
 *                         landed right at the very top of the captured
 *                         text (nothing but price-table column headers
 *                         above it) and every title heuristic above (all
 *                         bounded to the backward header window above
 *                         scan.start) had nothing to look at - a real
 *                         observed bug: the New Deck wizard's Name field
 *                         stayed blank. Added an absolute last-resort
 *                         fallback: when nothing else is found, search the
 *                         WHOLE matched card block (not just the header
 *                         window) for the commander's own card line
 *                         ("Commander: Zaxara, the Exemplary", already
 *                         recognized via COMMANDER_PREFIX for card-matching
 *                         purposes) and use its name - naming a Commander
 *                         deck after its own commander is an extremely
 *                         common real-world convention.
 *     Rémi Dutil (2026) - a real Archidekt capture of a genuinely tiny WIP
 *                         deck ("Copy of - The Spells Are Lava", 4 unique
 *                         cards/9 total copies) was rejected outright by
 *                         MIN_MATCHES (needs 6 matched lines minimum) no
 *                         matter how clean its own shape - a real observed
 *                         bug ("Archidekt not working anymore"): a deck
 *                         this small could never import at all. Added
 *                         isCorroboratedBySizeLabel(): Archidekt (and
 *                         likely other sites, going by the "100 main deck/0
 *                         sideboard" shape already handled elsewhere here)
 *                         states its own total card count directly on the
 *                         page ("Size: 9"/"Deck size: 9") - when a matched
 *                         run's own total copy count exactly matches one of
 *                         these labels anywhere on the page, that's strong
 *                         enough independent corroboration to trust the run
 *                         despite being below MIN_MATCHES. Wired into both
 *                         extractDeckSection()'s and detectDeckMeta()'s own
 *                         MIN_MATCHES bail checks. Needed two follow-up
 *                         hardening fixes once this started actually
 *                         reaching code paths that previously only ever saw
 *                         runs already known to be MIN_MATCHES-sized: (1)
 *                         computeCardCount()'s per-match quantity lookup
 *                         assumed a bare match always has a preceding
 *                         standalone-quantity line, which crashed on
 *                         (scan.start == 0, no quantity resolvable at all) -
 *                         a real "bare lines without quantity, DB
 *                         unavailable" test case hit this; and (2)
 *                         isCorroboratedBySizeLabel() itself needed to bail
 *                         immediately on an invalid scan (scan.start == -1,
 *                         the sentinel for "no run found at all") before
 *                         ever calling computeCardCount(). Also added
 *                         "secs"/"seconds" to RELATIVE_TIME_OR_COUNT's own
 *                         unit list (see its header) after this same
 *                         capture's "45 secs ago" slipped through as a
 *                         bogus title once detectDeckMeta() could reach
 *                         this deck's title line at all.
 *     Rémi Dutil (2026) - a real TappedOut capture rendered its own title
 *                         wrapped in a literal pair of quote characters on
 *                         its own line, "\"Take it and be grateful\"" -
 *                         every title heuristic above returns the display
 *                         line (or a substring of it) verbatim, with no
 *                         quote-stripping of its own, so the quotes rode
 *                         along into the detected title unchanged (a real
 *                         observed bug, reported as the title "not found").
 *                         Added stripSurroundingQuotes() as a single final
 *                         cleanup step right before returning, instead of
 *                         duplicating quote-handling in every individual
 *                         heuristic - only strips a MATCHING pair (both
 *                         ends), never a lone stray quote that might
 *                         genuinely be part of the title itself.
 *     Rémi Dutil (2026) - added listRecognizedCards(), a diagnostic
 *                         companion to countRecognizedCards()'s bare
 *                         totals - a real report ("101 cards" against a
 *                         known 100-card Archidekt decklist) could never be
 *                         reproduced against any real captured text tried
 *                         by hand, across every one of that site's 4 "View
 *                         as" layouts, with every real-card database built
 *                         up to try to mirror it - the one thing genuinely
 *                         impossible to fully reproduce outside the running
 *                         application is the real card database itself
 *                         (tens of thousands of entries). Returns the
 *                         actual matched "&lt;qty&gt; &lt;name&gt; (line N)"
 *                         rows so a mismatch between the live label and a
 *                         user's own count can be tracked down directly
 *                         from the log - see BrowseWebsiteDialog's own new
 *                         logging of it.
 *     Rémi Dutil (2026) - isFollowedByCommanderMatch()'s own qty-evidence
 *                         check (added above to stop trusting a bare match
 *                         as proof of a genuine section header) required
 *                         match.qty itself to be non-null - but that field
 *                         is ONLY populated for the same-line "4 Lightning
 *                         Bolt" shape; the far more common "1\nCard Name"
 *                         shape leaves it null by design (its qty is
 *                         resolved lazily elsewhere by checking the
 *                         preceding line against STANDALONE_QTY). This
 *                         silently broke ordinary real captures (Moxfield's
 *                         own "1\nJace, Multiverse Architect", Archidekt's
 *                         reordered-view equivalent), falling through to
 *                         page chrome ("View Options", "grid view") as the
 *                         detected title - caught by the full regression
 *                         suite (5 failures) after the fix above was first
 *                         verified against only the original bug report.
 *                         Added the same STANDALONE_QTY-on-preceding-line
 *                         check as an alternate form of real qty evidence.
 *     Rémi Dutil (2026) - the real root cause of the "100 or 101 cards"
 *                         Archidekt report, found from a real screenshot of
 *                         the site's own UI the user sent (every earlier
 *                         guess in this file - oracle-text keyword
 *                         coincidence, reprint-stamp gap fragmentation - was
 *                         a real, separate bug in its own right, but never
 *                         THE one the user was actually looking at): this
 *                         deck defines its own custom category, "Blood",
 *                         grouping its blood-tribal subtheme, rendered
 *                         exactly like a real "Commander\nQty: N\nPrice: $X"
 *                         section header - and "Blood" is ALSO a real
 *                         Scryfall Token Artifact name (confirmed directly
 *                         against the bulk card data, not guessed). This
 *                         category header sits right after the Commander
 *                         section's own category-transition widget
 *                         ("(CTRL to add secondary)"), within
 *                         startsWithCommanderHeader()'s own
 *                         COMMANDER_LOOKBACK_LIMIT of the real "Commander"
 *                         header above it - that backward scan previously
 *                         only stopped early at another real MATCH, never
 *                         at a category boundary, so it walked straight
 *                         past the widget and wrongly trusted "Blood" as
 *                         the commander's own bare card line, adding it as
 *                         a phantom 1-copy card (100 read as 101). Added
 *                         isCategoryTransitionWidget() and a stop check for
 *                         it in startsWithCommanderHeader() - see that
 *                         method's own new header for why the obvious
 *                         symmetric fix (the same stop check in
 *                         isFollowedByCommanderMatch(), the forward
 *                         counterpart) was deliberately NOT also made: it
 *                         broke a real commander's own legitimately-null
 *                         qty evidence elsewhere (Thranduil, Sindarin
 *                         Liege), caught only by the full regression suite.
 *                         Every earlier "100 or 101" investigation in this
 *                         file never seeded the bare word "Blood" itself
 *                         into its test database (only real card names that
 *                         happen to start with it), so none of them ever
 *                         actually exercised this - fixed in ANJE_DB itself
 *                         (shared by all four real "View as" layout tests)
 *                         to close that blind spot.
 *     Rémi Dutil (2026) - a real mtgdecks.net capture ("Sisay, Weatherlight
 *                         Captain") read 101 cards instead of 100 - the same
 *                         general shape as the "Blood" fix above (a bare
 *                         page-chrome line coincidentally resolving to a
 *                         real card name, then blindly trusted via
 *                         startsWithCommanderHeader()'s own backward scan),
 *                         but a DIFFERENT specific gap: this site's own
 *                         "Export to MTG Arena" button label resolves to
 *                         "Arena" (a real, if obscure, Commander-legal
 *                         enchantment - confirmed directly against the bulk
 *                         card data) via resolveName()'s trim-by-word loop,
 *                         and the backward scan trusted it via this site's
 *                         own page breadcrumb/nav chrome ("COMMANDER
 *                         DECKSSISAY, WEATHERLIGHT CAPTAIN .../COMMANDER
 *                         META"/"COMMANDER TOURNAMENTS"/...) - every one of
 *                         which satisfies isCommanderHeader()'s own
 *                         first-word-only check, but none of which is a
 *                         genuine per-card section header. A plain word-
 *                         count cutoff can't tell these apart from a real
 *                         one: deckbox.org's own real page has the exact
 *                         same "Commander" + one more word" shape on BOTH a
 *                         genuine header ("Commander (1)") and its own
 *                         unrelated sidebar nav ("Commander Archive"/
 *                         "Commander Bracket"). Added
 *                         isGenuineCommanderHeaderLine() - see its own
 *                         header for what actually does distinguish them
 *                         (the shape of whatever follows "Commander", not
 *                         how much of it there is) - as an extra
 *                         requirement in startsWithCommanderHeader()
 *                         specifically, not in the shared isCommanderHeader()
 *                         itself (several call sites there, not worth the
 *                         same cross-feature risk the "Blood" fix's
 *                         isFollowedByCommanderMatch() attempt ran into).
 *******************************************************************************/
package com.reflexit.magiccards.core.exports;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.MagicLogger;
import com.reflexit.magiccards.core.legality.Format;
import com.reflexit.magiccards.core.model.IMagicCard;

public class DeckTextExtractor {
	// "4 Lightning Bolt", "4x Lightning Bolt", "x4 Lightning Bolt", "x 4 Lightning Bolt"
	private static final Pattern QTY_LINE = Pattern.compile("^(?:[xX]\\s*)?(\\d{1,3})\\s*[xX]?\\s+(\\S.{0,90})$");
	// a quantity with nothing else on the line ("4"), or a price with nothing
	// else ("$1.56") - table "plumbing" around a qty-then-name-on-separate-
	// lines row (see TCGplayer's shape in this class' own header)
	private static final Pattern STANDALONE_QTY = Pattern.compile("^\\d{1,3}$");
	// a real price ("$1.56") or Archidekt's own "no data for this price
	// source" placeholder ("$----") - structurally the same table cell,
	// just empty; without this a real capture's own commander (missing
	// TCGPlayer/Card Kingdom/Cardhoarder data, "$----" x3) added 3 non-
	// neutral gap lines right after its own block, tipping several real
	// category transitions over MAX_GAP and losing whole categories' worth
	// of real cards from the final best-run window.
	private static final Pattern STANDALONE_PRICE = Pattern.compile("^[$£€¥]\\s?(?:\\d[\\d,.]*|-+)$");
	// a card's own name repeated with a printing/collector-number stamp - a
	// real Archidekt precon capture ("Calling All Angels - Foundations
	// Commander") renders this directly under EVERY card, right before its
	// own bare quantity: "Austere Command (fdc) 18\n1", "Giada, Font of
	// Hope (fdc) 4\n1", "Plains (fdn) 272\n32" - matchLine()'s own trim-by-
	// WORD loop happily strips "(fdc)"/"18" as trailing junk and resolves
	// this as a SECOND, independent match for the same card. That alone
	// would just double-count a card already found elsewhere, but it's
	// also directly responsible for a much worse real bug: findTrailingQty
	// stops the instant it reaches ANOTHER raw match, so the card's own
	// CLEAN name line (several lines above, its rules text and type line
	// in between) always hits this stamp line first and gives up with NO
	// quantity found at all - the stamp line ends up "stealing" the
	// quantity via its own, much closer, forward search instead. The
	// clean name going unmatched adds one more non-neutral gap line at
	// every single card in the deck, and a real capture showed this alone
	// was enough to push several category transitions (already gap-heavy:
	// the "(CTRL to add secondary)" widget, a next category's "Qty:
	// N"/"Price: $X" header, and Archidekt's own "$----" no-data price
	// placeholder, which doesn't match STANDALONE_PRICE since it has no
	// digit at all) over MAX_GAP - resetting the run and losing whole
	// categories' worth of real cards from the final best-run window (45
	// cards short on one real capture: 55 found instead of 100). Recognized
	// and rejected as noise up front, before the normal trim-based
	// resolution ever gets a chance to "successfully" mis-resolve it.
	// a real Archidekt "Stacked view" capture showed two more real collector
	// number shapes this pure-digit suffix missed entirely: a promo
	// collector number ("Vengeant Vampire (g18) GP3") and a star-suffixed
	// one ("Vito, Thorn of the Dusk Rose (pm21) 127★") - each one, left
	// unrecognized, is just MORE gap-filling noise on top of that same
	// card's own oracle text, and the two together (one card's own 4-6
	// line block, immediately followed by the next category's own 5-line
	// transition widget, with no real match in between to reset the
	// counter) was enough to tip MAX_GAP on its own - a real observed bug:
	// a 86-card run split into disconnected fragments (27/28/...) instead
	// of being found as one continuous 86-match run. Widened from bare
	// digits to any alphanumeric/dash/star run, still anchored to the same
	// "(short set code) <number>" shape so this stays specific to a real
	// reprint stamp, not a coincidental sentence ending in parentheses.
	private static final Pattern REPRINT_STAMP = Pattern
			.compile("^.+\\s\\([a-zA-Z0-9]{2,6}\\)\\s[a-zA-Z0-9][a-zA-Z0-9★-]{0,9}$");
	// a real TappedOut capture used this as an image's alt text - see
	// matchLine()'s own header for why this needs its own pattern
	private static final Pattern COMMANDER_PREFIX = Pattern.compile("^commander\\s*:\\s*(.+)$",
			Pattern.CASE_INSENSITIVE);

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
		/** The row's own Section-column tag ({@link #norm}-ed: "commander",
		 *  "sideboard", "maindeck", ...), for the "Flat Sorted View" table
		 *  shape only (see {@link #matchTableRow}) - every other shape
		 *  signals section membership via a separate header LINE instead
		 *  (see {@link #isCommanderHeader}/{@link #isSideboardHeader}), so
		 *  this is {@code null} there. */
		final String section;

		Match(String qty, String name) {
			this(qty, name, null);
		}

		Match(String qty, String name, String section) {
			this.qty = qty;
			this.name = name;
			this.section = section;
		}

		String toLine() {
			return (qty == null || qty.isEmpty()) ? name : qty + " " + name;
		}
	}

	/** A genuine multi-column table row - a real tipsymagic.com "Flat Sorted
	 *  View" capture showed "Card\tSection\tType\tRarity\tMV\tColor\tPrice\t
	 *  Qty" (tab-separated, one real HTML table row per card), the name in
	 *  the FIRST column and the quantity in the LAST, several real columns
	 *  apart. Unlike every other shape this class handles (the qty is
	 *  always adjacent to the name, at most a price or a mashed-in type
	 *  line's worth of trailing junk), {@link #resolveName}'s trim-by-WORD
	 *  loop (bounded by {@link #MAX_NAME_TRIMS}) could never trim through
	 *  six real columns of junk, and it trims from the wrong end entirely -
	 *  the quantity isn't attached to the name at all here, it's the very
	 *  last field. Read directly from the RAW line's own tab characters
	 *  (this must run before {@link #normalizeSpaces} converts them to
	 *  plain spaces and this structure is lost - matchLine() calls this
	 *  first, ahead of every other shape) rather than guessed at from
	 *  flattened text - so this doesn't compete with or risk regressing
	 *  anything else: a line with fewer than 3 real (non-blank) tab-
	 *  separated fields, or whose last field isn't a bare small integer, is
	 *  simply left for the normal paths below. The candidate name is still
	 *  run through the same DB cross-check every other bare match needs -
	 *  this only supplies a different (and, for a real table row, far more
	 *  reliable) way to locate the name and quantity, not a new way to
	 *  trust an unconfirmed one. */
	private static Match matchTableRow(String rawLine) {
		if (rawLine.indexOf('\t') < 0)
			return null;
		List<String> fields = new ArrayList<>();
		for (String f : rawLine.split("\t"))
			if (!f.trim().isEmpty())
				fields.add(f.trim());
		if (fields.size() < 3)
			return null;
		String last = fields.get(fields.size() - 1);
		if (!STANDALONE_QTY.matcher(last).matches())
			return null;
		String resolved = resolveName(fields.get(0), false);
		if (resolved == null)
			return null;
		// fields[1] is the row's own "Section" column ("Main Deck",
		// "Sideboard", "Commander", ...) - carried through as this Match's
		// own section tag so scan()/extractDeckSection() can apply the same
		// universal Commander-no-sideboard rule here too, even though this
		// table shape has no separate header LINE for it to key off of (see
		// Match#section's own header).
		String section = fields.size() > 1 ? norm(fields.get(1)) : null;
		return new Match(last, resolved, section);
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
		Match tableRow = matchTableRow(line);
		if (tableRow != null)
			return tableRow;
		String trimmed = normalizeSpaces(line).trim();
		if (trimmed.isEmpty())
			return null;
		if (REPRINT_STAMP.matcher(trimmed).matches())
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
		if (resolved == null) {
			// a real TappedOut capture rendered a Commander card as a pure
			// image, with a "Commander: <name>" accessibility label as its
			// only text (see CAPTURE_TEXT_WITH_IMAGE_ALT_SCRIPT in
			// BrowseWebsiteDialog, which recovers it from the image's own
			// alt text) - unlike every other trailing-junk case resolveName
			// already handles, this junk is a LEADING prefix, which its
			// trim-from-the-end loop can never strip on its own.
			Matcher cm = COMMANDER_PREFIX.matcher(trimmed);
			if (cm.matches())
				resolved = resolveName(cm.group(1).trim(), false);
		}
		return resolved == null ? null : new Match(null, resolved);
	}

	/** A section-header line naming the sideboard ("Sideboard", "Sideboard
	 *  [15]", "Sideboard (15)", ...) - recognized loosely by its first word,
	 *  so it can be normalized to the literal word FreeformImportDelegate's
	 *  own section-toggle detection looks for. Also accepts AetherHub's own
	 *  "Side 13 cards (5 distinct)" - a real capture showed this site's
	 *  sideboard header word is "Side", not "Sideboard", so it was never
	 *  recognized at all: the sideboard's own cards still got imported
	 *  (nothing stopped the scan), just without the "Sideboard" tag, so
	 *  hasSideboardMarker() never found one and the New Deck wizard's "Also
	 *  create a Sideboard" checkbox never auto-checked itself. */
	private static boolean isSideboardHeader(String trimmedLine) {
		String first = norm(trimmedLine.split("\\s", 2)[0]);
		return first.equals("sideboard") || first.equals("side");
	}

	/** A section-header line naming the Commander (deckbox.org: a bare
	 *  "Commander" line ahead of the maindeck, with exactly the commander's
	 *  own card row(s) under it) - see {@link #extractDeckSection}'s own
	 *  one-shot Sideboard/Deck toggle around it: a commander is, by MTG
	 *  convention, tracked in the sideboard pile (separate from the 99-card
	 *  maindeck), not as its own concept ManaDesk's importer would otherwise
	 *  need to understand. Excludes a colon-suffixed first token ("Commander:
	 *  Gishath, Sun's Avatar") - a real TappedOut capture showed this is the
	 *  commander's own CARD line (see COMMANDER_PREFIX/matchLine's own
	 *  header), not another header; without this exclusion, norm() stripping
	 *  the colon made the two indistinguishable, and the card's own line got
	 *  swallowed as a second, redundant header instead of ever being parsed
	 *  as content. Accepts the plural "Commanders" too - a real tipsymagic.com
	 *  capture uses that as its own fixed category-list template word
	 *  ("Commanders\nPlaneswalkers\nCreatures\n..."), and without this the
	 *  Commander-no-sideboard rule below never recognized this site's decks
	 *  as Commander-format at all. */
	private static boolean isCommanderHeader(String trimmedLine) {
		String first = trimmedLine.split("\\s", 2)[0];
		if (first.contains(":"))
			return false;
		String n = norm(first);
		return n.equals("commander") || n.equals("commanders");
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
			if (isCategoryTransitionWidget(norm[j]))
				return false;
			if (isCommanderHeader(norm[j]) && isGenuineCommanderHeaderLine(norm[j]))
				return true;
			if (matches[j] != null)
				return false;
		}
		return false;
	}

	/** Whether a line {@link #isCommanderHeader} already accepted (first word
	 *  "Commander"/"Commanders") actually LOOKS like a genuine per-card
	 *  section header, not just any page-chrome line that happens to start
	 *  with that word - {@link #isCommanderHeader} only ever checks the
	 *  first word, deliberately (several real per-card header shapes DO have
	 *  more after it, e.g. TappedOut's own "Commander (1)"), but that same
	 *  looseness is exactly what let a real mtgdecks.net capture's own page
	 *  breadcrumb ("COMMANDER DECKSSISAY, WEATHERLIGHT CAPTAIN サカイ ヒロエツ
	 *  COMMANDER RUMBLE2026/09/22 (NAGOYA, JAPAN)") and its own site-wide nav
	 *  menu ("COMMANDER META"/"COMMANDER TOURNAMENTS"/"COMMANDER DECKLISTS"/
	 *  "COMMANDER STAPLES") both satisfy {@link #startsWithCommanderHeader}
	 *  too - the breadcrumb sat within COMMANDER_LOOKBACK_LIMIT of a bare "
	 *  ARENA EXPORT" button label (this site's own "Export to MTG Arena"
	 *  link, split in two across lines; "Arena" is also a real, if obscure,
	 *  Commander-legal enchantment - Legends/Commander 2014), and got
	 *  blindly trusted as "this bare match's own Commander header", adding
	 *  a phantom "1 Arena" card to the actual 100-card deck (a real observed
	 *  count: 101 instead of 100). A simple word-count cutoff can't tell
	 *  these apart - deckbox.org's own real page has the exact same
	 *  "Commander" + one more word" shape on BOTH a genuine header
	 *  ("Commander (1)" - 2 tokens) and its own unrelated sidebar nav
	 *  ("Commander Archive"/"Commander Bracket" - also 2 tokens each). What
	 *  actually distinguishes them across every real capture checked so far
	 *  is the SHAPE of what follows "Commander"/"Commanders": either
	 *  nothing at all (the bare word alone, Archidekt/Moxfield/deckbox.org/
	 *  tipsymagic.com's own category header), or a parenthesized/bracketed
	 *  count and nothing else (TappedOut's own "Commander (1)") - never
	 *  ordinary prose, a year, or another proper noun. */
	private static boolean isGenuineCommanderHeaderLine(String trimmedLine) {
		String[] parts = trimmedLine.trim().split("\\s+", 2);
		if (parts.length == 1)
			return true;
		return parts[1].trim().matches("[(\\[]\\d+[)\\]]");
	}

	/** Archidekt's own per-category-transition UI widget - a real capture
	 *  ("Anje, Maid of Dishonor") renders it as "&lt;OutgoingCategory&gt;\n
	 *  (CTRL to add secondary)\n&lt;IncomingCategory&gt;\nQty: N\nPrice: $X"
	 *  between every pair of categories, including right after the
	 *  Commander section itself: "...\n1\nAnje, Maid of Dishonor\nCommander\n
	 *  (CTRL TO ADD SECONDARY)\nBlood\nQty: 13\n...". Without stopping at
	 *  this exact marker, {@link #startsWithCommanderHeader}'s backward scan
	 *  (which otherwise only stops early at another real match - see its own
	 *  header) walked straight past it and reached that SAME "Commander"
	 *  word again - except this second occurrence is the OUTGOING category
	 *  tag the widget itself names, not a real header introducing a new
	 *  commander card. "Blood" (this deck's own user-defined category name
	 *  for its blood-tribal subtheme, grouping real cards like Arterial
	 *  Alchemy/Bloodcrazed Socialite/Ceremonial Knife under it) sits right
	 *  after that widget and got wrongly trusted as "the commander's own
	 *  bare card line" purely because a "Commander"-shaped line happened to
	 *  sit within {@link #COMMANDER_LOOKBACK_LIMIT} lines behind it -
	 *  exactly the self-reinforcing shape {@link #isFollowedByCommanderMatch}
	 *  was already hardened against (see its own header), just approached
	 *  from the opposite direction. "Blood" also happens to be a real
	 *  Scryfall Token Artifact name (confirmed directly against the bulk
	 *  card data, not guessed), so with a real card database loaded this
	 *  wasn't a dead end that simply failed to resolve - it was trusted as
	 *  a genuine bare 1-copy card, adding a phantom "1 Blood" row (a real
	 *  observed count: 100 cards read as 101). The existing
	 *  isLikelyEmbeddedCoincidence() guard (see its own header) never
	 *  caught this because it only guards the TRAILING/PRECEDING_GAP
	 *  branches in matchLines() - the plain PRECEDING branch, which is what
	 *  actually resolves this site's own bare matches, trusts
	 *  precededByCommanderHeader unconditionally, with no such guard at all.
	 *  Any line matching this exact widget shape always marks a genuine
	 *  category boundary - crossing it backward means whatever "Commander"
	 *  is found beyond it belongs to a section that has already closed.
	 *  Deliberately NOT also applied to {@link #isFollowedByCommanderMatch}
	 *  (the forward counterpart) despite looking like the obvious symmetric
	 *  fix: this exact real capture's own TRUE commander match (Thranduil,
	 *  Sindarin Liege) has a null m.qty too (findTrailingQty() legitimately
	 *  finds nothing for it and it is itself only trusted via
	 *  precededByCommanderHeader - the same "no evidence of its own" shape
	 *  as the bug this guards against), so requiring real qty evidence
	 *  forward of the header started rejecting genuine commanders, not just
	 *  stray category tags. Worse, isFollowedByCommanderMatch() also feeds
	 *  scan()'s own isCommanderDeck determination, which influences which
	 *  QtyPolicy bestMatchesAndScan() picks as "best" - breaking it changed
	 *  Thranduil's own match from a real resolved qty to a null one via a
	 *  different, worse-resolved policy winning instead, corrupting
	 *  detectDeckMeta()'s title lookup too (landed on "Local filter", page
	 *  chrome nowhere near the title). Caught only by the full regression
	 *  suite (4 failures) - this file's own methodology (verify every fix
	 *  against the complete suite, not just the capture that motivated it)
	 *  working as intended. */
	private static boolean isCategoryTransitionWidget(String trimmedLine) {
		return norm(trimmedLine).equals("ctrltoaddsecondary");
	}

	/** Whether the Commander-shaped header line at {@code headerIndex} is
	 *  actually followed by a real card within {@link
	 *  #COMMANDER_LOOKBACK_LIMIT} lines (the forward counterpart of {@link
	 *  #startsWithCommanderHeader}, used by {@link #scan} to decide whether
	 *  this page is genuinely Commander-format before hard-stopping a later
	 *  "Sideboard"). A real TCGplayer capture showed a line whose first word
	 *  is "Commander" doesn't always mean a Commander header at all - its
	 *  own nav chrome has a "Commander Bestiary" content section, nowhere
	 *  near the actual (Modern, real-sideboard) deck - and without this
	 *  check, that alone was enough to wrongly hard-stop TCGplayer's real,
	 *  legitimate 15-card sideboard. Stops early at another header-shaped
	 *  line (a second "Commander"/"Sideboard"/stop-section header reached
	 *  before ever finding a real match means this one wasn't genuine
	 *  either). */
	private static boolean isFollowedByCommanderMatch(Match[] matches, String[] norm, int headerIndex) {
		int bound = Math.min(matches.length, headerIndex + 1 + COMMANDER_LOOKBACK_LIMIT);
		for (int j = headerIndex + 1; j < bound; j++) {
			// a match with no resolvable qty at all was ITSELF only trusted
			// via this same "blindly accept a bare match sitting within
			// COMMANDER_LOOKBACK_LIMIT of a Commander-shaped line" fallback
			// (see matchLines()'s own precededByCommanderHeader branch) -
			// accepting it here as confirming evidence creates exactly the
			// self-reinforcing cycle this is meant to prevent: a real
			// Archidekt capture's own "Blood" deck TAG (part of
			// "Vampires/Lifegain/Blood/Budget", displayed near the top of
			// every page) coincidentally matches a real Scryfall token name,
			// sits within 10 lines of the page's own top-level "Commander"
			// format label, and got blindly trusted as "this page's
			// commander" that way - which then, circularly, made THIS check
			// wrongly confirm that SAME top-level label as a genuine section
			// header, skipping it entirely and leaving both title and format
			// undetected. Only a match with real qty evidence of its own
			// counts here - a same-line qty (m.qty, set by the "4 Lightning
			// Bolt" one-liner shape), or a standalone qty on the immediately
			// preceding line (the common "1\nJace, Multiverse Architect"
			// shape - m.qty itself is null for this shape; the actual
			// resolution happens lazily wherever it's needed, e.g. this same
			// STANDALONE_QTY check at line ~1978). "Blood" in the deck-tag
			// list has neither (preceded by "Lifegain", not a number).
			if (matches[j] != null) {
				boolean hasQty = matches[j].qty != null
						|| (j > 0 && STANDALONE_QTY.matcher(norm[j - 1]).matches());
				if (hasQty)
					return true;
			}
			if (isCommanderHeader(norm[j]) || isSideboardHeader(norm[j]) || isStopSectionHeader(norm[j]))
				return false;
		}
		return false;
	}

	/** Section headers naming a list of cards that are NOT part of the actual
	 *  deck - seen on deckstats.net ("Maybeboard"), the same convention
	 *  elsewhere (Moxfield/Archidekt "Considering", a generic "Wishlist"),
	 *  and deckbox.org's own "Scratchpad" ("Scratchpad - 41 cards, 40
	 *  distinct" - the page's own description: "cards that you are
	 *  considering for this deck, but are not actually in the built deck...
	 *  do not count towards the in built decks count"). Unlike {@link
	 *  #isSideboardHeader}, encountering one of these is a hard stop for
	 *  {@link #scan} - closes the current run outright rather than just
	 *  costing one gap tick - since these sections otherwise share the
	 *  exact same "qty / name" row shape as a real Sideboard and would
	 *  silently bleed into it. A real capture: a 100-card Commander deck
	 *  (sideboard already correctly excluded) plus its own 41-card
	 *  Scratchpad read as 141 cards. */
	private static final Set<String> STOP_SECTION_WORDS = new HashSet<>(
			java.util.Arrays.asList("maybeboard", "considering", "wishlist", "scratchpad"));

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
			"spells", "other", "commander", "commanders"));

	/** A short (1-4 letter) run of UPPERCASE letters glued directly onto the
	 *  end of a candidate with no separating space at all - a real TappedOut
	 *  capture ("Aura ShardsGC") showed a "Game Changer" Commander-bracket
	 *  badge rendered immediately after the card's own name link, with
	 *  nothing between them in document.body.innerText. Unlike every other
	 *  trailing-junk case {@link #resolveName}'s trim-by-WORD loop already
	 *  handles (a price, a mashed-in type line - always separated by at
	 *  least a space), there is no space here to trim on at all. Requires
	 *  the character right before the run to be lowercase/apostrophe/digit/
	 *  closing-paren (i.e. genuinely glued, not itself part of an all-caps
	 *  word) - real MTG card names are Title Case and never end in a bare,
	 *  unspaced uppercase run, so this is safe to try unconditionally. */
	private static final Pattern GLUED_TRAILING_BADGE = Pattern.compile("^(.*[a-z'\")0-9])([A-Z]{1,4})$");

	/** The word "foil" glued directly onto a candidate's end with no
	 *  separating space - a real TappedOut capture ("2x Warren
	 *  Instigatorfoil") renders a foil-print indicator right after the
	 *  card's own name link, exactly like {@link #GLUED_TRAILING_BADGE}'s
	 *  own "Aura ShardsGC" case but lowercase, so that pattern's all-
	 *  uppercase requirement doesn't catch it. Same "genuinely glued, not
	 *  part of the name itself" character-class guard on what comes right
	 *  before it - no real MTG card name ends in "foil". */
	private static final Pattern GLUED_FOIL_SUFFIX = Pattern.compile("(?i)^(.*[a-z'\")0-9])foil$");

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
			Matcher gm = GLUED_TRAILING_BADGE.matcher(candidate);
			if (gm.matches()) {
				String stripped = gm.group(1);
				String normStripped = norm(stripped);
				if (!TYPE_WORDS.contains(normStripped) && names.contains(normStripped))
					return stripped;
			}
			Matcher fm = GLUED_FOIL_SUFFIX.matcher(candidate);
			if (fm.matches()) {
				String stripped = fm.group(1);
				String normStripped = norm(stripped);
				if (!TYPE_WORDS.contains(normStripped) && names.contains(normStripped))
					return stripped;
			}
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
	 *  #scan}: {@code bestCount == 0} when nothing matched at all.
	 *  {@code isCommanderDeck} is exposed so {@link #extractDeckSection}
	 *  doesn't need to recompute the same "did this page's decklist show a
	 *  genuine Commander header" determination a second time. */
	private static final class Scan {
		final int start, end, count;
		final boolean isCommanderDeck;

		Scan(int start, int end, int count, boolean isCommanderDeck) {
			this.start = start;
			this.end = end;
			this.count = count;
			this.isCommanderDeck = isCommanderDeck;
		}
	}

	/** {@code null}, or {@code trimmedLine}'s first word (normalized) if it's
	 *  a known card {@link #TYPE_WORDS} category header (Creatures/Lands/
	 *  Instants/...) - used to recognize where a skipped Commander-deck
	 *  Sideboard section (see {@link #scan}'s and {@link #extractDeckSection}
	 *  's own Commander-no-sideboard handling) ends: the next category
	 *  header NOT already seen earlier in the same run, not necessarily the
	 *  end of the page and not just any category word. Two real, opposite
	 *  shapes forced this: a real tipsymagic.com capture puts Sideboard in
	 *  the MIDDLE of its own fixed category order (Commanders,
	 *  Planeswalkers, ..., Lands, Sideboard, Other, Instants, Sorceries,
	 *  Enchantments) - a hard stop at Sideboard would also silently drop its
	 *  real Instants/Sorceries/Enchantments that come after it. But a real
	 *  TappedOut/Archidekt capture's own Sideboard section has its OWN
	 *  internal per-type sub-headers mirroring the maindeck's structure
	 *  ("Sideboard\nCreature (6)\n...\nEnchantment (3)\n...\nLand (1)\n...
	 *  \nArtifact (1)\n...") - ending the skip at the FIRST of those
	 *  (already-seen) category words let its own excluded cards straight
	 *  back in. Comparing against categories already seen earlier in the
	 *  SAME run tells these apart: a genuinely new section word (tipsymagic)
	 *  ends the skip, a repeat of one already used earlier in the run
	 *  (TappedOut/Archidekt) does not. */
	private static String typeHeaderCategory(String trimmedLine) {
		String first = trimmedLine.split("\\s", 2)[0];
		String n = norm(first);
		return TYPE_WORDS.contains(n) ? n : null;
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
		// Commander/EDH has no official sideboard at all - once a genuine
		// Commander header has been seen anywhere on the page (any site:
		// this isn't a TappedOut-specific carve-out), a later "Sideboard"
		// header is, by MTG rules, never a real one - just a site's own
		// "extra/maybe" bucket wearing that label. Gated through
		// isFollowedByCommanderMatch() - see its own header for why a bare
		// "first word is Commander" line alone isn't trustworthy
		// (TCGplayer's own "Commander Bestiary" nav link nearly hard-
		// stopped its real, legitimate sideboard on an ordinary Modern
		// deck).
		// Determined upfront, over the WHOLE array, rather than
		// progressively during the main loop below - a real tipsymagic.com
		// "Flat Sorted View" table capture (see matchTableRow()/Match#
		// section) sorts its rows by PRICE, not by section, so its own
		// Sideboard row can appear BEFORE its Commander row in document
		// order; progressive detection would miss the Sideboard exclusion
		// for a row that comes before the one that reveals this is even a
		// Commander deck.
		boolean isCommanderDeck = false;
		for (int i = 0; i < matches.length; i++) {
			if (isCommanderHeader(norm[i]) && isFollowedByCommanderMatch(matches, norm, i))
				isCommanderDeck = true;
			if (matches[i] != null && "commander".equals(matches[i].section))
				isCommanderDeck = true;
		}
		// Once inside a Commander-deck's fake "Sideboard" section, its own
		// matched lines are skipped (not counted, not treated as a gap -
		// see typeHeaderCategory's own header for why a hard stop here would
		// be wrong on a real tipsymagic.com capture) until the next category
		// header NOT already seen earlier in the run ends the section - see
		// typeHeaderCategory's own header for why a repeat doesn't count.
		boolean skippingCommanderSideboard = false;
		Set<String> seenCategories = new HashSet<>();
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
			String category = everMatched ? typeHeaderCategory(norm[i]) : null;
			if (skippingCommanderSideboard) {
				if (category != null && !seenCategories.contains(category))
					skippingCommanderSideboard = false; // falls through - a genuinely new section, processed below
				else
					continue; // this Sideboard-under-Commander line (or one of its own internal sub-headers,
							  // already seen earlier in the run) - never happened, not even as a gap
			} else if (everMatched && isCommanderDeck && isSideboardHeader(norm[i])) {
				skippingCommanderSideboard = true;
				continue;
			}
			if (category != null)
				seenCategories.add(category);
			// the "Flat Sorted View" table shape's own per-row equivalent -
			// no separate header line to toggle a skip zone on, the row
			// tags itself directly (see Match#section/matchTableRow())
			if (matches[i] != null && isCommanderDeck && "sideboard".equals(matches[i].section))
				continue;
			if (matches[i] != null) {
				everMatched = true;
				if (runStart < 0) {
					runStart = i;
					// the run's own first category header usually sits
					// directly above its first match (never inside
					// [runStart, ...] itself, so the loop's own category !=
					// null tracking above never reaches it) - a real
					// TappedOut capture's Sideboard reuses the exact same
					// word ("Creature") as the maindeck's own very first
					// header, so without this retroactive peek, that first
					// occurrence is invisible to seenCategories and the
					// sideboard's own repeat wrongly reads as "new".
					if (i > 0) {
						String precedingCategory = typeHeaderCategory(norm[i - 1]);
						if (precedingCategory != null)
							seenCategories.add(precedingCategory);
					}
				}
				lastMatch = i;
				count++;
				gap = 0;
			} else if (runStart >= 0) {
				// a category/type-header word (a card's own type line, or
				// the next section's header) is expected, structural
				// content once we're already inside a real run - not
				// "have we wandered off into unrelated page chrome" noise
				// the gap budget exists to guard against. Scoped to this
				// run-continuity check alone (not isNeutralGapLine() itself,
				// which extractDeckSection() also uses to decide when a
				// Commander's own block closes - that decision genuinely
				// needs the type line to count as real content). A real
				// Archidekt capture's own category-transition widget
				// ("<Category>\n(CTRL to add secondary)\n<NextCategory>\n
				// Qty: N\nPrice: $X.XX") repeats two TYPE_WORD-shaped lines
				// back to back around it - without this, that alone was
				// often enough to tip a transition over MAX_GAP on its own.
				if (isNeutralGapLine(norm[i]) || category != null)
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
					seenCategories.clear(); // a new run starts fresh - the abandoned run's categories don't carry over
				}
			}
		}
		if (runStart >= 0 && count > bestCount) {
			bestStart = runStart;
			bestEnd = lastMatch;
			bestCount = count;
		}
		return new Scan(bestStart, bestEnd, bestCount, isCommanderDeck);
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
	 *  #bestMatchesAndScan).
	 *  <li>{@code PRECEDING_GAP} - tipsymagic.com's "List" view: the qty
	 *  comes before the name like PRECEDING, but with 1-2 blank/whitespace-
	 *  only lines in between ("4\n[blank]\n[blank]\nArboreal Grazer") rather
	 *  than on the immediately preceding line, so strict PRECEDING never
	 *  matches at all. TRAILING was tried against this shape first since it
	 *  also has a bounded-lookahead search, but it search FORWARD from the
	 *  name and so keeps finding the NEXT card's qty instead of this one's
	 *  own - which happens to still sum correctly card-by-card (borrowing a
	 *  neighbor's identical-looking value), right up until the very LAST
	 *  card of the whole page, which has nothing after it but footer text -
	 *  no qty is ever found for it, and it's silently dropped (a real
	 *  capture: 60 real cards read as 56, exactly the first card's own qty
	 *  short, because the shift chain loses one unit off one end and one
	 *  whole card off the other). A genuine backward-bounded search fixes
	 *  this properly - see {@link #findPrecedingQty}. */
	private enum QtyPolicy {
		PRECEDING, TRAILING, PRECEDING_GAP
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
			// a real TappedOut Commander card's own bare line (see
			// matchLine()'s COMMANDER_PREFIX handling) sits directly under
			// its "Commander" header with no adjacent qty evidence at all
			// (a commander is unambiguously always 1 copy - the header
			// itself is the evidence) - trusted regardless of policy, same
			// as any other qty-prefixed line, rather than falling through
			// to either policy's normal "needs adjacent qty evidence" rule
			// below (which would otherwise reject it exactly like a stray,
			// unrelated repeat of a card name elsewhere on the page). Uses
			// the same bounded backward scan as startsWithCommanderHeader
			// (not just the single immediately-preceding line) - a real
			// tipsymagic.com "Card view" capture puts 2 lines of its own
			// junk ("— 1", "All decks with this commander") between the
			// header and the commander's own bare name, which a single-line
			// check would miss entirely, silently dropping the commander
			// from the count (a real observed bug: 100 cards read as 99).
			boolean precededByCommanderHeader = startsWithCommanderHeader(raw, norm, i);
			if (policy == QtyPolicy.PRECEDING) {
				// a bare match is only trusted when the line right before it
				// is a standalone quantity - a bare match with no such
				// preceding qty is most likely a stray repeat of an
				// already-listed card's name elsewhere on the page (see this
				// class' own header: a "product details" panel repeating the
				// deck's first card was misread as an extra 1x copy)
				boolean hasPrecedingQty = i > 0 && STANDALONE_QTY.matcher(norm[i - 1]).matches();
				if (hasPrecedingQty || precededByCommanderHeader)
					result[i] = m;
			} else if (policy == QtyPolicy.TRAILING) {
				// reject a candidate OUTRIGHT, before ever resolving its own
				// quantity, when its own nearest reachable reprint-stamp
				// contradicts it (names a different card) - otherwise a bare
				// oracle-text keyword line that coincidentally matches a
				// real card/token name (see findTrailingQty()'s own header:
				// "Lifelink"/"Blood") would still independently claim a
				// quantity of its own even after the REAL card it's
				// embedded in has correctly been taught to search past it.
				// Skipped when precededByCommanderHeader - that's already a
				// strong, independent legitimacy signal (a commander is
				// unambiguously 1 copy regardless of what a stamp search
				// finds or doesn't find).
				int bound = Math.min(norm.length, i + 1 + TRAILING_SEARCH_LIMIT);
				if (!precededByCommanderHeader && isLikelyEmbeddedCoincidence(norm, i, bound, m.name))
					continue;
				String trailingQty = findTrailingQty(raw, norm, i);
				if (trailingQty != null)
					result[i] = new Match(trailingQty, m.name);
				else if (precededByCommanderHeader)
					result[i] = m;
			} else {
				// same self-rejection as the TRAILING branch above - a
				// candidate whose own nearest reprint-stamp names a
				// different card must not independently claim a quantity
				// via PRECEDING_GAP either, or it just resurfaces there
				// instead (a real observed case: this exact capture's
				// "Blood" category-header line was rejected correctly
				// under TRAILING, then won anyway because PRECEDING_GAP,
				// left unguarded, still picked it up and produced a
				// longer - wrongly longer - run that beat TRAILING's
				// now-correct count).
				int bound = Math.min(norm.length, i + 1 + TRAILING_SEARCH_LIMIT);
				if (!precededByCommanderHeader && isLikelyEmbeddedCoincidence(norm, i, bound, m.name))
					continue;
				String precedingQty = findPrecedingQty(raw, norm, i);
				if (precedingQty != null)
					result[i] = new Match(precedingQty, m.name);
				else if (precededByCommanderHeader)
					result[i] = m;
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
	 *  #TRAILING_SEARCH_LIMIT} lines - or if the candidate quantity found is
	 *  ITSELF immediately followed by another bare standalone number: a real
	 *  tipsymagic.com capture showed a deck literally TITLED "Hydra" (a
	 *  real, if obscure, card name of its own) picking up its own page's
	 *  view-count ("41") as a bogus trailing quantity this way, bridging
	 *  (well within {@link #MAX_GAP}) into the real decklist further down
	 *  the page and adding a phantom 41-copy card to the count. No real
	 *  site's own per-card shape puts a bare quantity immediately before
	 *  ANOTHER bare number (a price is always $-prefixed, matching {@link
	 *  #STANDALONE_PRICE} instead) - two bare small numbers back to back is
	 *  the unmistakable shape of a page's own engagement stats (views+
	 *  likes, votes+comments), never a real per-card quantity. */
	private static String findTrailingQty(Match[] raw, String[] norm, int from) {
		int bound = Math.min(norm.length, from + 1 + TRAILING_SEARCH_LIMIT);
		for (int j = from + 1; j < bound; j++) {
			if (raw[j] != null) {
				if (isLikelyEmbeddedCoincidence(norm, j, bound, raw[j].name))
					continue;
				return null;
			}
			if (isSideboardHeader(norm[j]) || isStopSectionHeader(norm[j]))
				return null;
			if (STANDALONE_QTY.matcher(norm[j]).matches()) {
				if (j + 1 < norm.length && STANDALONE_QTY.matcher(norm[j + 1]).matches())
					return null;
				return norm[j];
			}
		}
		return null;
	}

	// a real Archidekt "grid view" capture (full oracle text between each
	// card's name and its own qty/price block) showed a bare oracle-text
	// KEYWORD line ("Lifelink", sitting alone on its own line the way
	// "Flying"/"Trample"/... commonly do in rules text) coincidentally
	// matching a REAL, legitimately-playable Magic card of the exact same
	// name (Lifelink, a white Aura from M12 - not a token, not an obscure
	// edge case; "Blood" similarly matches a real Token Artifact entry).
	// This broke TWO separate things at once, both fixed by the SAME real
	// tell: a GENUINE new card's own reprint-stamp line ("&lt;Name&gt;
	// (set) number") names ITSELF - a coincidental embedded match's
	// nearest reachable reprint-stamp instead names whatever card it's
	// actually embedded in (here, "Kamber, the Plunderer (voc) 19", not
	// "Lifelink (m12) 24"):
	//  1. findTrailingQty()'s own raw[j]!=null abort (meant to stop a
	//     search from crossing into a genuinely DIFFERENT card's own
	//     block) treated the embedded "Lifelink" line as a hard boundary,
	//     so "Kamber, the Plunderer" (several lines above it) silently
	//     lost its own quantity and dropped out of the count entirely -
	//     fixed by skipping PAST a blocking match that isn't confirmed by
	//     its own nearest reprint-stamp, instead of aborting on it.
	//  2. "Lifelink" was STILL independently resolved as its own bare
	//     match in matchLines()'s first pass, and successfully found a
	//     quantity of its own via the SAME forward search - a phantom
	//     entry, double-claiming a quantity that (1) alone didn't stop -
	//     fixed by matchLines() itself rejecting a candidate outright when
	//     its own nearest reprint-stamp contradicts it (see its own call
	//     site below).
	// A real observed bug: 100 cards read as 101/87 unique, reproduced for
	// the first time only once the actual matched-row list (not just the
	// bare count) was logged - see listRecognizedCards()'s own header. The
	// real card database has thousands of single-word names that double as
	// common keyword/ability words, so neither half could be fixed with a
	// blocklist (a genuine "Flash"/"Persist"/... decklist entry must still
	// work) - only this structural, reprint-stamp-based check tells a
	// coincidental embedded match apart from a real one.
	private static boolean isLikelyEmbeddedCoincidence(String[] norm, int candidateIndex, int bound,
			String candidateName) {
		String candidateNorm = norm(candidateName);
		for (int k = candidateIndex + 1; k < bound; k++) {
			if (!REPRINT_STAMP.matcher(norm[k]).matches())
				continue;
			// found SOME reprint-stamp within reach - if it doesn't name
			// this candidate, it belongs to whatever real card this
			// candidate is embedded in; if no stamp is found at all within
			// range, this candidate is neither confirmed nor contradicted
			// (many real sites never show a "(set) number" stamp at all),
			// so it's left alone rather than rejected on silence.
			return !norm(norm[k]).startsWith(candidateNorm);
		}
		return false;
	}

	/** The backward mirror of {@link #findTrailingQty}, for {@link
	 *  QtyPolicy#PRECEDING_GAP}: looks behind a bare match for its own
	 *  quantity, tolerating a few blank/non-matching lines in between (unlike
	 *  {@link QtyPolicy#PRECEDING}'s strict immediately-preceding-line check)
	 *  but stopping the instant it crosses into another card's own block
	 *  (another raw match) or a section boundary - so it can never cross into
	 *  a DIFFERENT card's quantity, only skip past genuinely empty filler
	 *  between a card's own qty and its name. Same engagement-stat-pair
	 *  guard as {@link #findTrailingQty} (rejects a candidate immediately
	 *  preceded by another bare number), for the same reason: two bare
	 *  numbers stacked together is never a real site's per-card shape. */
	private static String findPrecedingQty(Match[] raw, String[] norm, int from) {
		int bound = Math.max(-1, from - 1 - TRAILING_SEARCH_LIMIT);
		for (int j = from - 1; j > bound; j--) {
			if (raw[j] != null) {
				// same reasoning as findTrailingQty()'s own header - a
				// blocking match here is noise, not a genuine card
				// boundary, if its own nearest reprint-stamp (which, in
				// document order, always comes AFTER a card's bare name -
				// so still searched FORWARD from j, even though this outer
				// search itself runs backward) doesn't confirm it.
				int forwardBound = Math.min(norm.length, j + 1 + TRAILING_SEARCH_LIMIT);
				if (isLikelyEmbeddedCoincidence(norm, j, forwardBound, raw[j].name))
					continue;
				return null;
			}
			if (isSideboardHeader(norm[j]) || isStopSectionHeader(norm[j]))
				return null;
			if (STANDALONE_QTY.matcher(norm[j]).matches()) {
				if (j - 1 >= 0 && STANDALONE_QTY.matcher(norm[j - 1]).matches())
					return null;
				return norm[j];
			}
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

	/** Scans {@code lines} under all three {@link QtyPolicy} conventions and
	 *  keeps whichever finds the larger run - see {@link QtyPolicy}'s own
	 *  header for why this can't be decided per line. Ties (including the
	 *  common case of TRAILING/PRECEDING_GAP finding nothing at all, e.g.
	 *  every site seen so far except Archidekt and tipsymagic's List view)
	 *  keep PRECEDING, preserving the exact, already-verified behavior for
	 *  every other real site this was tested against. PRECEDING_GAP is
	 *  checked last and only replaces a strictly larger count, for the same
	 *  reason: a shifted/borrowed count from the wrong policy is never
	 *  preferred over a real one just because it happens to tie. */
	private static PolicyResult bestMatchesAndScan(String[] lines, String[] norm) {
		Match[] preceding = matchLines(lines, norm, QtyPolicy.PRECEDING);
		PolicyResult best = new PolicyResult(preceding, scan(norm, preceding));
		Match[] trailing = matchLines(lines, norm, QtyPolicy.TRAILING);
		Scan trailingScan = scan(norm, trailing);
		if (trailingScan.count > best.scan.count)
			best = new PolicyResult(trailing, trailingScan);
		Match[] precedingGap = matchLines(lines, norm, QtyPolicy.PRECEDING_GAP);
		Scan precedingGapScan = scan(norm, precedingGap);
		if (precedingGapScan.count > best.scan.count)
			best = new PolicyResult(precedingGap, precedingGapScan);
		return best;
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
		return computeCardCount(matches, norm, scan, null);
	}

	/** The actual matched rows ("&lt;qty&gt; &lt;name&gt;", one per line,
	 *  encounter order) a matched run would import right now - a diagnostic
	 *  companion to {@link #countRecognizedCards}'s bare totals, so a real
	 *  mismatch between the live label and a user's own manual count (a
	 *  real observed report: the live label read 101 cards against a known
	 *  100-card decklist, never reproduced against any captured text tried
	 *  by hand) can be tracked down directly from the log - which exact
	 *  card is the extra/wrong one - instead of needing to reconstruct the
	 *  real card database (tens of thousands of entries, impossible to
	 *  fully mirror by hand) outside the running application. Returns an
	 *  empty list rather than {@code null} if nothing matched. */
	public static java.util.List<String> listRecognizedCards(String fullText) {
		if (fullText == null || fullText.isEmpty())
			return java.util.Collections.emptyList();
		String[] lines = fullText.split("\r?\n");
		String[] norm = normalizeLines(lines);
		PolicyResult pr = bestMatchesAndScan(lines, norm);
		Match[] matches = pr.matches;
		Scan scan = pr.scan;
		if (scan.count == 0)
			return java.util.Collections.emptyList();
		java.util.List<String> out = new java.util.ArrayList<>();
		computeCardCount(matches, norm, scan, out);
		return out;
	}

	/** The actual total/unique count a matched run would import - shared by
	 *  {@link #countRecognizedCards} (the live "N card(s) found" label) and
	 *  {@link #extractDeckSection}'s own small-deck size-corroboration check
	 *  below, so both agree on exactly what a run contains. {@code
	 *  outMatchedLines}, if non-null, is appended with one "&lt;qty&gt;
	 *  &lt;name&gt;" entry per matched row (see {@link #listRecognizedCards}) -
	 *  kept as a side channel rather than a second, duplicated copy of this
	 *  loop, so the two can never silently drift apart on what counts as a
	 *  match. */
	private static CardCount computeCardCount(Match[] matches, String[] norm, Scan scan,
			java.util.List<String> outMatchedLines) {
		int total = 0;
		Set<String> uniqueNames = new HashSet<>();
		// mirrors scan()'s/extractDeckSection()'s own skip-zone handling of
		// a Commander deck's fake "Sideboard" section - this loop must agree
		// with what extractDeckSection() would actually import, or the live
		// "N card(s) found" label disagrees with the real import (exactly
		// the class of bug this label was already fixed for once - see this
		// class' own header).
		boolean skippingCommanderSideboard = false;
		Set<String> seenCategories = new HashSet<>();
		// see extractDeckSection()'s own matching peek for why
		if (scan.start > 0) {
			String precedingCategory = typeHeaderCategory(norm[scan.start - 1]);
			if (precedingCategory != null)
				seenCategories.add(precedingCategory);
		}
		for (int i = scan.start; i <= scan.end; i++) {
			String category = typeHeaderCategory(norm[i]);
			if (skippingCommanderSideboard) {
				if (category != null && !seenCategories.contains(category))
					skippingCommanderSideboard = false;
				else
					continue;
			} else if (scan.isCommanderDeck && isSideboardHeader(norm[i])) {
				skippingCommanderSideboard = true;
				continue;
			}
			if (category != null)
				seenCategories.add(category);
			Match m = matches[i];
			if (m == null)
				continue;
			if (scan.isCommanderDeck && "sideboard".equals(m.section))
				continue;
			uniqueNames.add(norm(m.name));
			// every surviving bare match is guaranteed a preceding standalone
			// quantity by matchLines() above, so this is always resolvable -
			// except when this method is reached via isCorroboratedBySizeLabel()
			// on a scan that was never actually going to be accepted (i == 0,
			// no preceding line can exist at all) - a real "bare lines without
			// quantity, DB unavailable" test case hit this and crashed before
			// the length guard below was added.
			String qtyStr = m.qty != null ? m.qty : (i > 0 ? norm[i - 1] : null);
			int qty;
			try {
				qty = Integer.parseInt(qtyStr);
			} catch (NumberFormatException e) {
				qty = 1;
			}
			total += qty;
			if (outMatchedLines != null)
				outMatchedLines.add(qty + " " + m.name + " (line " + i + ")");
		}
		return new CardCount(total, uniqueNames.size());
	}

	// a real Archidekt capture of a genuinely tiny WIP deck ("Copy of - The
	// Spells Are Lava", 4 unique cards/9 total copies) was rejected outright
	// by MIN_MATCHES (needs 6 matched lines, too easy for page chrome to
	// coincidentally produce a couple of matches) - a real observed bug: a
	// deck this small could never import at all, no matter how clean its
	// own shape. Archidekt (and, going by the "100 main deck/0 sideboard"
	// shape already handled elsewhere in this class, likely other sites too)
	// states its own total card count directly on the page ("Size: 9" near
	// the top, "Deck size: 9" again in its stats panel) - when a matched
	// run's own total copy count EXACTLY matches one of these explicit
	// labels anywhere on the page, that is strong independent corroboration
	// that the run really is the decklist even though it's short, so it's
	// trusted despite being below MIN_MATCHES.
	private static final Pattern DECK_SIZE_LABEL = Pattern.compile("(?i)^(?:deck\\s+)?size:\\s*(\\d{1,4})$");

	private static boolean isCorroboratedBySizeLabel(String[] norm, Match[] matches, Scan scan) {
		if (scan.start < 0 || scan.count <= 0)
			return false;
		int matchedTotal = computeCardCount(matches, norm, scan, null).total;
		if (matchedTotal <= 0)
			return false;
		for (String line : norm) {
			Matcher m = DECK_SIZE_LABEL.matcher(line);
			if (m.matches() && Integer.parseInt(m.group(1)) == matchedTotal)
				return true;
		}
		return false;
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
		if (scan.count < MIN_MATCHES && !isCorroboratedBySizeLabel(norm, matches, scan)) {
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
		// a deck has at most ONE legitimate commander section - once it's
		// been wrapped (here, or by the pre-loop check above), any FURTHER
		// "Commander"-shaped line is untrusted noise, not a second
		// commander. A real Archidekt capture showed this matters even with
		// isFollowedByCommanderMatch()'s own guard: right after Thranduil's
		// own block closes, its per-card category-transition widget repeats
		// "Commander" as the OUTGOING category name (the exact same shape
		// documented in this class' own header for "Ramp\n(CTRL to add
		// secondary)\nRemoval..."), and that stray mention IS followed by
		// the next real (unrelated) card within the lookback window too -
		// so distance/adjacency alone can't tell them apart; only "this
		// deck already has its commander" can.
		boolean commanderWrapUsed = inCommanderBlock;
		if (inCommanderBlock)
			sb.append("Sideboard").append('\n');
		// mirrors scan()'s own skippingCommanderSideboard - a real Sideboard
		// header under the universal Commander-no-sideboard rule is dropped
		// ENTIRELY here (no "Sideboard" tag emitted, its own card rows
		// skipped) rather than tagged and let through, until the next
		// category header NOT already seen earlier in the run ends the
		// section - see typeHeaderCategory's own header for why a repeat
		// (a real TappedOut/Archidekt Sideboard's own internal per-type
		// sub-headers, mirroring its maindeck's structure) doesn't count,
		// and a real tipsymagic.com capture needs this instead of a hard
		// stop (its own Instants/Sorceries/Enchantments sections come AFTER
		// Sideboard in its fixed category order).
		boolean skippingCommanderSideboard = false;
		Set<String> seenCategories = new HashSet<>();
		// the run's own first category header usually sits directly above
		// scan.start itself (never inside [scan.start, scan.end] for this
		// loop to see on its own) - see scan()'s own matching retroactive
		// peek for why this matters (a real TappedOut capture's Sideboard
		// reuses the maindeck's very first header word as one of its own
		// sub-headers).
		if (scan.start > 0) {
			String precedingCategory = typeHeaderCategory(norm[scan.start - 1]);
			if (precedingCategory != null)
				seenCategories.add(precedingCategory);
		}
		for (int i = scan.start; i <= scan.end; i++) {
			String category = typeHeaderCategory(norm[i]);
			if (skippingCommanderSideboard) {
				if (category != null && !seenCategories.contains(category))
					skippingCommanderSideboard = false; // falls through - a genuinely new section
				else
					continue;
			} else if (scan.isCommanderDeck && isSideboardHeader(norm[i])) {
				skippingCommanderSideboard = true;
				continue;
			}
			if (category != null)
				seenCategories.add(category);
			// the "Flat Sorted View" table shape's own per-row equivalent -
			// see scan()'s matching check for why (no separate header line
			// to toggle a skip zone on; the row tags itself directly)
			if (matches[i] != null && scan.isCommanderDeck && "sideboard".equals(matches[i].section))
				continue;
			Match m = matches[i];
			if (m != null) {
				String qty = m.qty;
				if (qty == null && i > 0 && STANDALONE_QTY.matcher(norm[i - 1]).matches())
					qty = norm[i - 1];
				sb.append(new Match(qty, m.name).toLine()).append('\n');
			} else if (isSideboardHeader(norm[i])) {
				// only reached when !scan.isCommanderDeck - a real, legal
				// sideboard, tagged and kept exactly as before
				sb.append("Sideboard").append('\n');
				inCommanderBlock = false; // a real Sideboard header always wins
			} else if (!commanderWrapUsed && isCommanderHeader(norm[i]) && isFollowedByCommanderMatch(matches, norm, i)) {
				sb.append("Sideboard").append('\n');
				inCommanderBlock = true;
				commanderWrapUsed = true;
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

	/** {@link #detectDeckMeta}'s result - either field may be {@code null} if
	 *  nothing plausible was found (the caller should fall back to its own
	 *  default, e.g. "Standard" for format and a blank Name field for
	 *  title). */
	public static final class DeckMeta {
		public final String title;
		public final String format;

		private DeckMeta(String title, String format) {
			this.title = title;
			this.format = format;
		}
	}

	private static final DeckMeta NO_META = new DeckMeta(null, null);

	/** How far back from the real decklist's own start (see {@link
	 *  #bestMatchesAndScan}) {@link #detectDeckMeta} looks for the page's own
	 *  title/format - real captures put these within the deck's own header
	 *  block, well short of a whole page's worth of earlier site-wide nav. A
	 *  real mtgtop8.com capture's own nav bar lists every format name as a
	 *  filter menu right at the very top of the page - an unbounded (or even
	 *  a too-generous) backward search would misread the nav bar's own
	 *  "STANDARD" entry as this Modern deck's format - safe in practice
	 *  since the search always prefers the CLOSEST match to the decklist
	 *  (the real per-deck header sits far closer than site-wide nav chrome
	 *  on every real capture checked, mtgtop8.com included: its own real
	 *  format line is ~62 lines above the decklist, its nav bar over 70),
	 *  but a window this generous is still worth bounding rather than
	 *  scanning the whole page. */
	private static final int HEADER_LOOKBACK_LIMIT = 80;

	/** Case-insensitive, punctuation/space-stripped (see {@link #norm})
	 *  format name/alias -&gt; the real {@link Format#name()} to select.
	 *  Built from the live {@link Format} registry (every real Scryfall
	 *  format, plus Commander's own family), with a couple of extra common
	 *  abbreviations real sites use in place of the real name. */
	private static final Map<String, String> FORMAT_ALIASES = buildFormatAliases();

	private static Map<String, String> buildFormatAliases() {
		Map<String, String> m = new LinkedHashMap<>();
		for (Format f : Format.getFormats())
			m.put(norm(f.name()), f.name());
		m.put("edh", "Commander");
		m.put("cedh", "Commander");
		return m;
	}

	/** A handful of common page-chrome words/shapes that must never be
	 *  mistaken for a deck's own title, even when they happen to sit right
	 *  next to the matched format line (a real Archidekt capture puts "62
	 *  views"/"16 hrs ago"/"0" between the title and the format). "secs" was
	 *  added after a real Archidekt capture of a very small, newly-created
	 *  WIP deck (where the title line sits just a few lines above its
	 *  format, unlike a more established deck's own "hrs/days ago") showed
	 *  "45 secs ago" slipping through as a bogus title - this unit list
	 *  previously jumped straight from "mins" to "hrs" with no "secs" entry
	 *  at all. */
	private static final Pattern RELATIVE_TIME_OR_COUNT = Pattern.compile(
			"^\\d+\\s*(views?|hrs?|hours?|days?|mins?|minutes?|secs?|seconds?|likes?|votes?|comments?)(\\s+ago)?$",
			Pattern.CASE_INSENSITIVE);
	private static final Set<String> TITLE_CHROME_WORDS = new HashSet<>(java.util.Arrays.asList("login", "log in",
			"register", "sign in", "menu", "search", "legal", "not legal", "playtest", "buy", "download", "home",
			"forum", "help", "premium", "copy link", "clone deck", "more", "add card", "view as", "group by",
			"sort by", "save", "results", "info", "tags", "supporter", "view only", "profile", "profile picture",
			"avatar"));

	/** Whether {@code candidate} is plausible as a deck title - non-blank,
	 *  a reasonable length, contains at least one letter (rules out a bare
	 *  count/id), and isn't one of the small set of known chrome shapes
	 *  above. Deliberately loose otherwise - a real deck title can contain
	 *  almost anything (punctuation, numbers, card names). */
	private static boolean isPlausibleTitle(String candidate) {
		if (candidate == null)
			return false;
		String t = candidate.trim();
		if (t.isEmpty() || t.length() > 100)
			return false;
		if (!t.chars().anyMatch(Character::isLetter))
			return false;
		if (RELATIVE_TIME_OR_COUNT.matcher(t).matches())
			return false;
		return !TITLE_CHROME_WORDS.contains(t.toLowerCase(java.util.Locale.ROOT));
	}

	/** Looks outward from {@code anchor} (a matched format line's index) for
	 *  the nearest plausible title candidate - checked backward first (the
	 *  dominant real shape: Moxfield/deckstats.net/Archidekt/mtgtop8 all put
	 *  the title directly above their own format line, sometimes with a
	 *  couple of lines of view-count/timestamp chrome in between), then
	 *  forward (TCGplayer's breadcrumb puts the format first: "Modern\nIzzet
	 *  Prowess"). Bounded to a handful of lines either way - by the time a
	 *  real site's title/format pairing needs to look further than this,
	 *  it's no longer this pairing at all. */
	private static final int TITLE_ADJACENCY_LIMIT = 6;

	private static String findAdjacentTitle(String[] norm, int anchor) {
		for (int j = anchor - 1; j >= 0 && j > anchor - 1 - TITLE_ADJACENCY_LIMIT; j--) {
			if (norm[j].isEmpty())
				continue;
			if (isPlausibleTitle(norm[j]) && !matchesAnyFormatAlias(norm[j]))
				return norm[j];
		}
		for (int j = anchor + 1; j < norm.length && j < anchor + 1 + TITLE_ADJACENCY_LIMIT; j++) {
			if (norm[j].isEmpty())
				continue;
			if (isPlausibleTitle(norm[j]) && !matchesAnyFormatAlias(norm[j]))
				return norm[j];
		}
		return null;
	}

	private static boolean matchesAnyFormatAlias(String normLine) {
		return matchFormatAlias(normLine) != null;
	}

	/** {@code null}, or the real {@link Format#name()} {@code normLine}
	 *  (already {@link #norm}-ed) starts with - a PREFIX match, not a whole-
	 *  line or substring-anywhere one: a real TappedOut capture's own format
	 *  line is "Commander / EDH Bracket 3 Dinosaurs RGW (Naya)", more text
	 *  than just the format name, but a substring-anywhere match would be
	 *  far too easy for unrelated prose to satisfy (see this class' own
	 *  header for the general shape of that problem). The longest matching
	 *  alias wins, so a specific family name (e.g. "Duel Commander") is
	 *  never shadowed by the generic "Commander" - moot for a pure prefix
	 *  check in practice (no two aliases here share a common prefix), kept
	 *  for safety if the {@link Format} registry ever grows one.
	 *  @param displayLine a line from {@link #normalizeLines}'s output -
	 *  whitespace-cleaned and trimmed, but NOT the aggressive lowercase/
	 *  alphanumeric-only {@link #norm} used for the actual alias comparison
	 *  (that normalization happens internally here) - callers never need to
	 *  normalize it themselves. */
	private static String matchFormatAlias(String displayLine) {
		String key = norm(displayLine);
		String best = null;
		for (Map.Entry<String, String> e : FORMAT_ALIASES.entrySet()) {
			String alias = e.getKey();
			if (key.startsWith(alias) && (best == null || alias.length() > best.length()))
				best = alias;
		}
		return best == null ? null : FORMAT_ALIASES.get(best);
	}

	// "<Title> by <Author>" on ONE line - a real MTGGoldfish capture:
	// "Mono-Green Landfall by Nicolas D'Ambrose" - unlike TCGplayer's own
	// bare, standalone "By" line (see the "by"/"By" fallback below), the
	// author is glued onto the SAME line as the title, with no format
	// mentioned anywhere near it at all (MTGGoldfish's own format line,
	// "Format: Standard", is a separate, unrelated line).
	private static final Pattern TITLE_BY_AUTHOR = Pattern.compile("(?i)^(.+?)\\s+by\\s+(\\S.*)$");

	/** If {@code displayLine} is shaped "&lt;Title&gt; by &lt;Author&gt;" -
	 *  returns the leading title portion; {@code null} otherwise. Checked
	 *  across the whole header window, independent of whether a format line
	 *  was found at all, since this shape carries no format information of
	 *  its own. A bare standalone "By"/"by" line (TCGplayer) never matches
	 *  this pattern - it has nothing before "by" to capture as group 1.
	 *  Length-capped and rejects a title candidate containing a period - a
	 *  real Archidekt capture's own UI instruction text ("...using the View
	 *  as &amp; Group by drop-downs above.") also contains " by " as an
	 *  ordinary connector word in an otherwise unrelated sentence; a real
	 *  "Title by Author" line (MTGGoldfish: "Mono-Green Landfall by Nicolas
	 *  D'Ambrose") is always short and never a sentence in its own right. */
	private static String titleEmbeddedWithBy(String displayLine) {
		if (displayLine.length() > 60)
			return null;
		Matcher m = TITLE_BY_AUTHOR.matcher(displayLine);
		if (!m.matches())
			return null;
		String candidate = m.group(1).trim();
		if (candidate.contains("."))
			return null;
		return isPlausibleTitle(candidate) && !matchesAnyFormatAlias(candidate) ? candidate : null;
	}

	// "<Username>'s <Title>" on ONE line - a real deckbox.org capture:
	// "wrensleigh's Power Hungry". The SAME page also has a "<USERNAME>'s
	// profile" link near the very top (unrelated), but it's much farther
	// from the decklist than the real title - the backward scan (closest
	// match wins) reaches the real one first, so the "profile" link is
	// never reached; "profile" is also blocklisted in TITLE_CHROME_WORDS
	// as a second layer of safety. A real Moxfield capture showed this
	// second layer actually matters: its own avatar image renders as
	// "<Username>'s Profile Picture" right above the username/title block,
	// CLOSER to scan.start than the real title ("Jace polymorph WIP", which
	// has no "'s"/"by" shape at all and is only found afterwards via
	// findAdjacentTitle's format-anchored search) - "profile picture" (and
	// "avatar", the same shape on other sites) is blocklisted too so this
	// candidate is rejected and the sweep correctly continues past it
	// instead of ending the search here.
	//
	// The username itself can be MORE than one word - a real deckbox.org
	// capture: "Devon Krynicki's WUBRG - Legends Matter" (a First/Last
	// display name, unlike the single-word "wrensleigh" above). The
	// original "^\S+'s" only matched a username with no internal spaces -
	// "Devon" (the line's first token) doesn't itself end in "'s", so the
	// whole line silently failed to match and no title was found at all.
	// Bounded to at most 2 leading words before the "'s" word so this
	// doesn't start matching ordinary prose ("...see today's top decks").
	// The "'s" word itself must START WITH A LETTER - a real TCGplayer
	// capture's own ad copy, "Try "collector's rare effect monsters"",
	// wrongly matched the wider (?:\S+\s+){0,2}\S+'s version (1 leading
	// word, "Try", then the quote-glued token "\"collector's" as the
	// anchor) and returned "rare effect monsters" as a bogus title before
	// this guard - the stray leading quote character on that anchor token
	// is exactly what a real username/display-name never has.
	private static final Pattern TITLE_POSSESSIVE = Pattern
			.compile("^(?:\\S+\\s+){0,2}[A-Za-z]\\S*'s\\s+(\\S.*)$");

	private static String titleEmbeddedWithPossessive(String displayLine) {
		if (displayLine.length() > 60)
			return null;
		Matcher m = TITLE_POSSESSIVE.matcher(displayLine);
		if (!m.matches())
			return null;
		String candidate = m.group(1).trim();
		if (candidate.contains("."))
			return null;
		return isPlausibleTitle(candidate) && !matchesAnyFormatAlias(candidate) ? candidate : null;
	}

	// "<Format> - <Title>" or "<Format>: <Title>" - a real AetherHub
	// capture renders its OWN title and format together on one line
	// ("Modern - Boros Aggro"), unlike every other real site checked, where
	// they're always separate lines.
	private static final Pattern FORMAT_TITLE_SPLIT = Pattern.compile("^(.+?)\\s*[-:–—]\\s+(\\S.*)$");

	/** If {@code displayLine} is the SAME line {@code matchFormatAlias}
	 *  already matched, shaped "&lt;Format&gt; - &lt;Title&gt;" (a real
	 *  AetherHub capture: "Modern - Boros Aggro") - returns the trailing
	 *  title portion; {@code null} otherwise. Checked before falling back to
	 *  {@link #findAdjacentTitle}'s separate-line search, which would
	 *  otherwise land on whatever chrome happens to sit immediately above/
	 *  below this one line - a real capture's own "Apps" nav-menu item sits
	 *  directly above it, and was wrongly picked as the title before this
	 *  check existed. Confirms the split point is really right after the
	 *  format (not just any hyphen) by requiring the PREFIX alone to also
	 *  resolve via {@link #matchFormatAlias}. */
	private static String titleEmbeddedWithFormat(String displayLine) {
		Matcher m = FORMAT_TITLE_SPLIT.matcher(displayLine);
		if (!m.matches())
			return null;
		if (matchFormatAlias(m.group(1)) == null)
			return null;
		String candidate = m.group(2).trim();
		return isPlausibleTitle(candidate) && !matchesAnyFormatAlias(candidate) ? candidate : null;
	}

	// a short, bare ALL-CAPS "tag" word directly after the title, with no
	// gap - the same real shape as a recognized format tag (Moxfield's own
	// "COMMANDER", mtgtop8's own "Modern") but for a word that ISN'T a real
	// tracked Format, so matchFormatAlias never anchors on it - a real
	// deckstats.net capture: "MB oldschool rev 2\nCASUAL" ("Casual" isn't a
	// real constructed format this app tracks legality for). Length-capped
	// and blocklisted against generic page chrome that's ALSO short and
	// all-caps ("ADVERTISEMENT" sits a few lines below the real title on
	// the same real capture, closer to the decklist, and would otherwise
	// be found first).
	private static final Pattern SHORT_ALL_CAPS_TAG = Pattern.compile("^[A-Z][A-Z'-]{0,9}$");
	private static final Set<String> ALL_CAPS_TAG_CHROME = new HashSet<>(
			java.util.Arrays.asList("advertisement", "home", "menu", "login", "register", "search", "help"));

	private static boolean isShortAllCapsTag(String displayLine) {
		return SHORT_ALL_CAPS_TAG.matcher(displayLine).matches() && !ALL_CAPS_TAG_CHROME.contains(norm(displayLine));
	}

	/** Best-effort deck title/format extraction from {@code fullText}, for
	 *  pre-filling the New Deck wizard's Name field and Default Format combo
	 *  when browsing a page to import - see this class' own header and
	 *  {@link #HEADER_LOOKBACK_LIMIT}/{@link #findAdjacentTitle} for the
	 *  reasoning. Returns {@link #NO_META} (both fields {@code null}) if the
	 *  page doesn't look like a decklist at all ({@link #bestMatchesAndScan}
	 *  found nothing), or if nothing plausible was found within the header
	 *  window - callers are expected to fall back to their own default
	 *  rather than treat a miss as an error. */
	public static DeckMeta detectDeckMeta(String fullText) {
		if (fullText == null || fullText.isEmpty())
			return NO_META;
		String[] lines = fullText.split("\r?\n");
		String[] norm = normalizeLines(lines);
		PolicyResult pr = bestMatchesAndScan(lines, norm);
		Match[] matches = pr.matches;
		Scan scan = pr.scan;
		if (scan.start <= 0 || (scan.count < MIN_MATCHES && !isCorroboratedBySizeLabel(norm, matches, scan)))
			return NO_META;
		int bound = Math.max(0, scan.start - HEADER_LOOKBACK_LIMIT);
		String format = null;
		int formatIndex = -1;
		for (int j = scan.start - 1; j >= bound; j--) {
			// a genuine deck-internal Commander SECTION header (the card
			// list's own commander-zone, e.g. Archidekt/Moxfield's
			// "Commander\nQty: 1\n...") reads exactly like the page's own
			// top-of-page format indicator to matchFormatAlias() - a real
			// Archidekt/Moxfield capture showed this picking the section
			// header (closer to scan.start, chrome-heavy neighbors) over
			// the real, more informative one further up next to the actual
			// title. The format VALUE is the same either way ("Commander"),
			// but the title lookup right beside it is not - skip past it
			// entirely so a real page-level mention further back (if any)
			// gets the chance instead.
			if (isCommanderHeader(norm[j]) && isFollowedByCommanderMatch(matches, norm, j))
				continue;
			String alias = matchFormatAlias(norm[j]);
			if (alias != null) {
				// a PARTIAL match (the line has more content than just the
				// format name itself) needs that extra content to be
				// substantial - at least 3 words - to be trusted without a
				// real card following. A real deckstats.net capture's own
				// "Commanders" nav-menu link (1 word) and a real deckbox.org
				// capture's own "Commander Bracket" UI field label (2 words)
				// both matched via the same alias prefix as TappedOut's own
				// genuine "Commander / EDH Bracket 3 Dinosaurs RGW (Naya)"
				// subtitle (8 words) - without this, the nav link's/label's
				// own neighboring chrome got wrongly picked as the title
				// (deckstats: "Cards", its OWN nav link right above it). An
				// EXACT match (the line IS just the format name, Moxfield's
				// own bare "COMMANDER" tag, mtgtop8's own bare "Modern") is
				// unaffected - it has no extra content to judge.
				boolean exactMatch = norm(norm[j]).equals(norm(alias));
				if (!exactMatch && norm[j].trim().split("\\s+").length < 3)
					continue;
				format = alias;
				formatIndex = j;
				break;
			}
		}
		String title = formatIndex >= 0 ? titleEmbeddedWithFormat(norm[formatIndex]) : null;
		if (title == null) {
			// "<Title> by <Author>" on one line (a real MTGGoldfish capture:
			// "Mono-Green Landfall by Nicolas D'Ambrose") - checked across
			// the whole window regardless of whether a format line was
			// found at all, since this site's own format line ("Format:
			// Standard") is a separate, unrelated line with no title
			// embedded in it.
			for (int j = scan.start - 1; j >= bound; j--) {
				String candidate = titleEmbeddedWithBy(norm[j]);
				if (candidate == null)
					candidate = titleEmbeddedWithPossessive(norm[j]);
				if (candidate != null) {
					title = candidate;
					break;
				}
			}
		}
		if (title == null && formatIndex >= 0)
			title = findAdjacentTitle(norm, formatIndex);
		if (title == null) {
			// no format line found (or nothing plausible beside it) - fall
			// back to a "by"/"By" author-line anchor on its own (a real
			// TCGplayer capture: "Izzet Prowess\n\nBy\nEthanBird"), still
			// bounded to the same header window.
			for (int j = scan.start - 1; j >= bound; j--) {
				if (!norm[j].equalsIgnoreCase("by") && !norm[j].toLowerCase(java.util.Locale.ROOT).startsWith("by "))
					continue;
				for (int k = j - 1; k >= 0 && k > j - 1 - TITLE_ADJACENCY_LIMIT; k--) {
					if (norm[k].isEmpty())
						continue;
					if (isPlausibleTitle(norm[k]) && !matchesAnyFormatAlias(norm[k])) {
						title = norm[k];
						break;
					}
				}
				break;
			}
		}
		if (title == null) {
			// last resort: a short, bare ALL-CAPS "tag" word directly after
			// the title, with no gap (deckstats.net's own "MB oldschool rev
			// 2\nCASUAL" - "Casual" isn't a real tracked format, so it was
			// never a candidate for formatIndex above, but it's still a
			// reliable title anchor on its own).
			for (int j = scan.start - 1; j >= bound; j--) {
				if (!isShortAllCapsTag(norm[j]) || j == 0)
					continue;
				String candidate = norm[j - 1];
				if (candidate.isEmpty() || !isPlausibleTitle(candidate) || matchesAnyFormatAlias(candidate)
						|| isShortAllCapsTag(candidate))
					continue;
				title = candidate;
				break;
			}
		}
		if (title == null) {
			// absolute last resort: a real TappedOut capture ("Let's build:
			// Zaxara, the Exemplary") had NO title text anywhere above the
			// decklist at all - scan.start sat right at the very top of the
			// whole captured text (this site's own "Commander" category
			// happened to sort AFTER Land/Creature/Enchantment/Instant, deep
			// INSIDE the matched card block rather than before it), so every
			// check above (all bounded to the backward header window above
			// scan.start) had nothing to look at. The only deck-identifying
			// text actually present anywhere is the commander's own card
			// line ("Commander: Zaxara, the Exemplary"), already recognized
			// elsewhere via COMMANDER_PREFIX for card-matching purposes -
			// searched here across the WHOLE matched block (scan.start to
			// scan.end), not just the header window, since this line can
			// sit anywhere within it depending on the site's own section
			// order. Naming a Commander deck after its own commander is an
			// extremely common real-world convention, so when every other
			// heuristic above has come up empty, fall back to that name
			// rather than leaving the New Deck wizard's Name field blank.
			for (int i = scan.start; i <= scan.end; i++) {
				Matcher cm = COMMANDER_PREFIX.matcher(norm[i]);
				if (!cm.matches())
					continue;
				String candidate = cm.group(1).trim();
				if (isPlausibleTitle(candidate) && !matchesAnyFormatAlias(candidate))
					title = candidate;
				break;
			}
		}
		return new DeckMeta(stripSurroundingQuotes(title), format);
	}

	// a real TappedOut capture rendered its own deck title wrapped in a
	// literal pair of quote characters on its own line, "\"Take it and be
	// grateful\"" - every title heuristic above returns the display line
	// (or a substring of it) verbatim, with no quote-stripping of its own,
	// so the quotes rode along into the detected title unchanged (a real
	// observed bug: the New Deck wizard's Name field filled in with the
	// quote marks still attached). Stripped as a single final cleanup step
	// here rather than in each individual heuristic, so every detector
	// benefits without needing its own copy of this logic. Only strips a
	// MATCHING pair (both ends), never a single stray quote that might be
	// part of the real title itself (e.g. a card name's own apostrophe).
	private static final char[][] QUOTE_PAIRS = { { '"', '"' }, { '\'', '\'' }, { '“', '”' },
			{ '‘', '’' } };

	private static String stripSurroundingQuotes(String title) {
		if (title == null || title.length() < 2)
			return title;
		for (char[] pair : QUOTE_PAIRS) {
			if (title.charAt(0) == pair[0] && title.charAt(title.length() - 1) == pair[1])
				return title.substring(1, title.length() - 1).trim();
		}
		return title;
	}

	/** Whether {@code extractedText} ({@link #extractDeckSection}'s own
	 *  output, or equivalently-shaped text) includes a Sideboard section - a
	 *  real one, normalized to the literal line "Sideboard", or (since the
	 *  Commander handling above reuses the exact same marker) a Commander
	 *  deck's own commander card. Shared by the New Deck wizard (to auto-
	 *  check "Also create a Sideboard") and BrowseWebsiteDialog's own live
	 *  "Sideboard detected" indicator while browsing, rather than each
	 *  keeping its own copy of the same one-line check. */
	public static boolean hasSideboardSection(String extractedText) {
		if (extractedText == null)
			return false;
		for (String line : extractedText.split("\r?\n"))
			if (line.trim().equals("Sideboard"))
				return true;
		return false;
	}

	/** A line parsed from {@link #extractDeckSection}'s own "&lt;qty&gt;
	 *  &lt;name&gt;" / bare "&lt;name&gt;" output lines (see {@link
	 *  Match#toLine}) - the inverse of that formatting, for {@link
	 *  #countBySection}. */
	private static final Pattern EXTRACTED_LINE_QTY = Pattern.compile("^(\\d{1,3})\\s+(.+)$");

	/** {@link #extractDeckSection}'s own total physical cards and distinct
	 *  names, split between the main deck and the Sideboard section (parsed
	 *  from its "Sideboard"/"Deck" toggle markers - the same ones
	 *  FreeformImportDelegate's own section parsing looks for) - for a live
	 *  "Main: N, Sideboard: M" breakdown while browsing, shown once {@link
	 *  #hasSideboardSection} confirms there is one. */
	public static final class SectionCounts {
		public final int mainTotal;
		public final int mainUnique;
		public final int sideboardTotal;
		public final int sideboardUnique;

		private SectionCounts(int mainTotal, int mainUnique, int sideboardTotal, int sideboardUnique) {
			this.mainTotal = mainTotal;
			this.mainUnique = mainUnique;
			this.sideboardTotal = sideboardTotal;
			this.sideboardUnique = sideboardUnique;
		}
	}

	public static SectionCounts countBySection(String extractedText) {
		int mainTotal = 0, sideboardTotal = 0;
		Set<String> mainNames = new HashSet<>();
		Set<String> sideboardNames = new HashSet<>();
		if (extractedText != null) {
			boolean inSideboard = false;
			for (String line : extractedText.split("\r?\n")) {
				String trimmed = line.trim();
				if (trimmed.equals("Sideboard")) {
					inSideboard = true;
					continue;
				}
				if (trimmed.equals("Deck")) {
					inSideboard = false;
					continue;
				}
				if (trimmed.isEmpty())
					continue;
				int qty = 1;
				String name = trimmed;
				Matcher m = EXTRACTED_LINE_QTY.matcher(trimmed);
				if (m.matches()) {
					qty = Integer.parseInt(m.group(1));
					name = m.group(2);
				}
				if (inSideboard) {
					sideboardTotal += qty;
					sideboardNames.add(norm(name));
				} else {
					mainTotal += qty;
					mainNames.add(norm(name));
				}
			}
		}
		return new SectionCounts(mainTotal, mainNames.size(), sideboardTotal, sideboardNames.size());
	}
}
