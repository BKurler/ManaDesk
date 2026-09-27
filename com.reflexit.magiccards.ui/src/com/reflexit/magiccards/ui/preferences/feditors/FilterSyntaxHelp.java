/*
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: an inline search-syntax
 *                         cheat sheet for the Basic Filter page, filling the
 *                         vertical space it otherwise leaves empty (the
 *                         CardFilterDialog's overall size is set by the Set
 *                         Filter page's big set list, not by this page) -
 *                         covers most of what com.reflexit.magiccards.help's
 *                         own Filter Dialog help page documents, so a user
 *                         rarely needs to leave the dialog to look it up
 *     Rémi Dutil (2026) - the former separate Abilities Filter tab this was
 *                         also shown on got folded into Basic Filter (now
 *                         "Main Filter") - see BasicFilterPreferencePage's
 *                         own header - so this panel now only needs to exist
 *                         in the one place
 *     Rémi Dutil (2026) - dropped the "Text/Or/Excluding fields below"
 *                         mention: those fields (AdvancedTextSeachFields-
 *                         PreferenceGroup) are gone now, folded back into
 *                         plain Text - see BasicFilterPreferencePage's header
 *     Rémi Dutil (2026) - spelled out what m/regex/ actually matches and why
 *                         someone would reach for it instead of the plain
 *                         word/phrase syntax above - the terse one-liner
 *                         ("for a case-sensitive regular expression, e.g.
 *                         m/^Flying$/") didn't explain what the example
 *                         itself matched or when it's useful
 *     Rémi Dutil (2026) - dropped the m/regex/ line from this compact panel
 *                         again - it's an advanced, rarely-needed escape
 *                         hatch, not worth the space here now that its own
 *                         explanation is longer; still documented in
 *                         com.reflexit.magiccards.help's own Filter Dialog
 *                         help page, which this panel never fully replaces
 *     Rémi Dutil (2026) - fixed the [ability] line: it claimed to "work in
 *                         Name, Text, Type, and Artist", which is technically
 *                         true (the bracket syntax parses the same way
 *                         wherever it's typed - see MagicCardFilter#
 *                         tokenSearch()'s ABI case, which matches whatever
 *                         field the search came from) but misleading -
 *                         abilities are described in a card's oracle text,
 *                         not its name/type/artist credit, so [flying] typed
 *                         into Name/Type/Artist only ever finds a card by
 *                         coincidence (one whose actual name/type/artist
 *                         happens to contain the word), never because it has
 *                         the ability. Narrowed the claim to Text, the only
 *                         field where it's actually useful.
 *     Rémi Dutil (2026) - reworked into a per-field panel: one static block
 *                         shown to every field regardless of focus never had
 *                         room for field-specific detail (the {SYMBOL} table
 *                         especially - always abbreviated with "..."). Now
 *                         create() returns a Panel that starts empty and is
 *                         driven by focus - TextSearchPreferenceGroup#
 *                         wireSearchTips() calls show()/clear() as each of
 *                         Name/Type/Text/Artist/Special/Comment gains/loses
 *                         focus, so only one field's own tips ever occupy
 *                         the space at once, leaving room for Text's own tips
 *                         to list every real mana/tap symbol from
 *                         SymbolConverter's own Scryfall-sourced table
 *                         instead of a "..." abbreviation. The per-field
 *                         *_TIPS constants live here (not in
 *                         TextSearchPreferenceGroup) so the copy for a field
 *                         and its short, complementary tooltip - see that
 *                         class' own header - come from the same place.
 *     Rémi Dutil (2026) - that first cut went too far the other way: the new
 *                         per-field tooltips (a single short pointer line)
 *                         lost most of the actual syntax explanation the
 *                         previous shared tooltip carried. Restored a full,
 *                         real explanation to every field's tooltip - but
 *                         this attempt (per-field *_TOOLTIP constants, each
 *                         repeating the same base plus its own extra line)
 *                         was reworked again immediately after - see the
 *                         next entry.
 *     Rémi Dutil (2026) - split the two surfaces on a firm rule instead of
 *                         "roughly the same depth, minus what's wrong": the
 *                         tooltip is now ONE fixed, generic-only explanation
 *                         (TOOLTIP - words ANDed, "quoted text" is a literal
 *                         case-insensitive phrase, -word/-"quoted text"
 *                         excludes it) shared verbatim by every field with no
 *                         per-field variation at all - regex, wildcards,
 *                         [ability], {SYMBOL}, and every field-specific note
 *                         moved OUT of the tooltip entirely and into that
 *                         field's *_TIPS text instead, which now leads with
 *                         the regex explanation (shared, since it applies to
 *                         every field) before its own specific content.
 *                         Name's wildcard note also gained an explicit
 *                         incompatibility warning (wildcards bypass AND/
 *                         quoting/exclusion/regex entirely, not layered on
 *                         top of them) - that fact belongs here, not in a
 *                         tooltip meant to stay generic.
 *     Rémi Dutil (2026) - create()/Panel: the label had no minimum size of
 *                         its own, so it rendered however much - or little -
 *                         leftover vertical space Main Filter's other rows
 *                         happened to leave, which could be quite small.
 *                         Gave the Group a real heightHint and wrapped the
 *                         label in a ScrolledComposite (setExpandHorizontal,
 *                         setMinHeight recomputed on every show() and on
 *                         resize) so Text's own full symbol table - the
 *                         longest content here - gets a scrollbar instead of
 *                         being clipped or squeezed, and the panel has a
 *                         guaranteed floor instead of shrinking to nothing.
 *     Rémi Dutil (2026) - heightHint 220 -> 110 (too big at 220) and dropped
 *                         REGEX_TIP entirely - m/regex/ turned out to be
 *                         "pretty much useless" in practice for most users;
 *                         still documented in com.reflexit.magiccards.help's
 *                         own Filter Dialog help page, just not in this
 *                         panel anymore. Also dropped NAME_TIPS' "checks the
 *                         card's English name for foreign-language
 *                         printings" claim - verified false for real data:
 *                         MagicCardField.ENGLISH_NAME.getM() calls
 *                         MagicCard#getEnglishName(), which falls back to
 *                         plain getName() unless getEnglishCardId() (ENID)
 *                         is set, and ENID is only ever populated by the
 *                         legacy ParseGathererOracle/ParseMagicCardsInfo-
 *                         Spoiler importers - the current
 *                         ParseScryFallChecklist importer never sets it (and
 *                         Scryfall's own "name" field is already always the
 *                         English name, never a localized one) - so for
 *                         every card in a Scryfall-sourced database, the
 *                         ENGLISH_NAME OR-clause FilterField#NAME_LINE
 *                         builds is always comparing against the exact same
 *                         string NAME itself already searched. Left that
 *                         FilterField code alone (a real user could still
 *                         have legacy ENID data from before the Scryfall
 *                         migration) - only removed the now-disproven claim
 *                         from this copy.
 *     Rémi Dutil (2026) - replaced specialTips()'s live SpecialTags.getTags()
 *                         listing with a fixed SPECIAL_TIPS constant instead:
 *                         most of those tags (foil, mint/played/etc) are
 *                         superseded by their own dedicated column now
 *                         (Finish, Condition, Proxy) and listing them here
 *                         read as encouraging tag-based filtering the app no
 *                         longer wants to be the primary way to do this.
 */
package com.reflexit.magiccards.ui.preferences.feditors;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;

public class FilterSyntaxHelp {
	/** The ONLY thing every field's tooltip says - deliberately generic and
	 *  identical everywhere, so it never has to be field-accurate. Anything
	 *  more specific (regex, wildcards, [ability], {SYMBOL}, or a note about
	 *  what the field actually searches) belongs in that field's *_TIPS text
	 *  below instead - shown in Search Tips, not the tooltip. */
	public static final String TOOLTIP = "" //
			+ "Words are ANDed together (all must appear, in any order).\n" //
			+ "\"Quoted text\" is a literal, case-insensitive match for that exact phrase.\n" //
			+ "-word or -\"quoted text\" excludes it.";

	public static final String NAME_TIPS = "" //
			+ "Wildcards: * matches any run of characters, ? matches exactly one - e.g. \"Jace*\" finds every " //
			+ "Jace; \"Bo?k\" finds \"Book\" or \"Bork\"; \"*Blade\" finds any name ending in \"Blade\".\n" //
			+ "Wildcards are NOT compatible with the rest of this syntax - using * or ? switches to a pure " //
			+ "character-shape match and bypasses AND/quoting/exclusion entirely for that search.\n" //
			+ "Case-insensitive either way.";

	public static final String TYPE_TIPS = "" //
			+ "Checks the whole type line - supertype, card type, and subtypes together, e.g. \"Legendary " //
			+ "Creature - Human Wizard\".\n" //
			+ "Search just a subtype (\"Wizard\"), just a card type (\"Instant\"), or combine words " //
			+ "(\"Legendary Creature\") - AND applies the same as any other word search.\n" //
			+ "Double-faced cards are matched on both faces' type lines.";

	/**
	 * The full, real mana/tap symbol list is
	 * {@link com.reflexit.magiccards.core.sync.SymbolConverter}'s own
	 * SCRYFALL_MANA table (generated from Scryfall's own /symbology
	 * endpoint) - every entry there except the handful of Un-set/joke
	 * symbols (footnoted instead of listed one by one - {@code {CHAOS}},
	 * {@code {∞}}, {@code {100}}, {@code {1000000}}, {@code {½}},
	 * {@code {A}}, {@code {D}}, {@code {H}}, {@code {HR}}, {@code {HW}},
	 * {@code {L}}, {@code {P}}, {@code {PW}}, {@code {TK}}, {@code {Y}},
	 * {@code {Z}}).
	 */
	public static final String TEXT_TIPS = "" //
			+ "[ability] looks up a real keyword/ability by name, not a plain word - it also correctly ignores " //
			+ "a mention of that ability inside ANOTHER ability's own reminder text (e.g. Reach's reminder text " //
			+ "says \"creatures with flying\" - [flying] alone does not match a Reach-only creature).\n\n" //
			+ "{SYMBOL} matches a mana/tap symbol exactly as the rules text writes it:\n" //
			+ "  Colors {W} {U} {B} {R} {G}\n" //
			+ "  Colorless {C}\n" //
			+ "  Snow {S}\n" //
			+ "  Energy {E}\n" //
			+ "  Variable {X}\n" //
			+ "  Generic cost {0} {1} {2} ... {20} (higher on some cards)\n" //
			+ "  Tap / Untap {T} {Q}\n" //
			+ "  Hybrid (any two colors): {W/U} {U/B} {B/R} {R/G} {G/W} {W/B} {U/R} {B/G} {R/W} {G/U}\n" //
			+ "  2-generic hybrid: {2/W} {2/U} {2/B} {2/R} {2/G}\n" //
			+ "  Phyrexian: {W/P} {U/P} {B/P} {R/P} {G/P} {C/P}\n" //
			+ "  Phyrexian hybrid (rare): {W/U/P} {U/B/P} {B/R/P} {R/G/P} {G/W/P} {W/B/P} {U/R/P} {B/G/P} " //
			+ "{R/W/P} {G/U/P}\n" //
			+ "  A few Un-set/joke symbols also exist (e.g. {CHAOS}, {∞}) - rarely needed.";

	public static final String ARTIST_TIPS = "" //
			+ "Matches the PRINTING's own credited artist, not the card as a whole - different printings of the " //
			+ "same card can have different artists (reprints, alternate art, etc), so this filters which " //
			+ "printings match, not just which cards.\n" //
			+ "Combine with the Set Filter tab to find one artist's work across specific sets.";

	public static final String COMMENT_TIPS = "" //
			+ "Your own free-form notes about this card or copy - not part of the card's real data, and not " //
			+ "shared with anyone else.";

	private FilterSyntaxHelp() {
	}

	public static final String SPECIAL_TIPS = "" //
			+ "Most of the old attribute tags (foil, mint/played/etc, proxy) now have their own dedicated " //
			+ "column instead (Finish, Condition, Proxy) - use those, they're easier to filter/sort by.\n" //
			+ "This field is for whatever isn't covered by a dedicated column yet (e.g. wishlist, signed, " //
			+ "online) or a tag you invent yourself.\n" //
			+ "Comma-separated in the real data (e.g. \"wishlist,signed\") - search for just one tag as a plain " //
			+ "word, same as any other text field.";

	/** Adds an initially-empty, scrollable "Search Tips" panel to
	 *  {@code parent} that grabs all remaining vertical space, with a
	 *  guaranteed minimum height (not just whatever's left over) - {@code
	 *  parent}'s own layout must be a single-column GridLayout with earlier
	 *  siblings NOT grabbing vertical space themselves (the pattern
	 *  BasicFilterPreferencePage already uses), or this panel won't have any
	 *  dead space left to fill beyond its own minimum. Returns a
	 *  {@link Panel} handle - the caller wires it to whichever fields'
	 *  focus should populate it (see TextSearchPreferenceGroup#
	 *  wireSearchTips()). */
	public static Panel create(Composite parent) {
		Group group = new Group(parent, SWT.NONE);
		group.setText("Search Tips");
		group.setLayout(new GridLayout());
		GridData groupGd = new GridData(GridData.FILL_BOTH);
		groupGd.heightHint = 110;
		group.setLayoutData(groupGd);
		group.setFont(parent.getFont());

		ScrolledComposite scrolled = new ScrolledComposite(group, SWT.V_SCROLL);
		scrolled.setLayoutData(new GridData(GridData.FILL_BOTH));
		scrolled.setExpandHorizontal(true);
		scrolled.setExpandVertical(true);
		scrolled.setFont(parent.getFont());

		Label label = new Label(scrolled, SWT.WRAP);
		label.setText("");
		label.setFont(parent.getFont());
		scrolled.setContent(label);

		Panel panel = new Panel(scrolled, label);
		scrolled.addControlListener(new ControlAdapter() {
			@Override
			public void controlResized(ControlEvent e) {
				panel.updateSize();
			}
		});
		return panel;
	}

	/** A focus listener's handle onto the shared, scrollable Search Tips
	 *  label - show() on focus gained, clear() on focus lost, so the panel
	 *  only ever shows the currently-focused field's own tips (empty
	 *  otherwise). */
	public static final class Panel {
		private final ScrolledComposite scrolled;
		private final Label label;

		private Panel(ScrolledComposite scrolled, Label label) {
			this.scrolled = scrolled;
			this.label = label;
		}

		public void show(String text) {
			if (this.label.isDisposed())
				return;
			this.label.setText(text == null ? "" : text);
			updateSize();
		}

		public void clear() {
			show("");
		}

		/** Recomputes the label's wrapped height at the scrolled area's
		 *  current width, and tells the ScrolledComposite how tall its
		 *  scrollable content really is - called after every show()/clear()
		 *  and on resize (the wrap width changes when the dialog is
		 *  resized). */
		private void updateSize() {
			if (this.scrolled.isDisposed())
				return;
			Rectangle area = this.scrolled.getClientArea();
			int width = area.width > 0 ? area.width : SWT.DEFAULT;
			Point size = this.label.computeSize(width, SWT.DEFAULT);
			this.label.setSize(size);
			this.scrolled.setMinHeight(size.y);
		}
	}
}
