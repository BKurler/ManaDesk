/*
 * Contributors:
 *     Rémi Dutil (2026) - Name field's own tooltip now also documents its
 *                         '*'/'?' wildcards (see FilterField's header) -
 *                         they don't apply to Type/Text/Artist, so they're
 *                         no longer lumped into the one tooltip shared by
 *                         all four
 *     Rémi Dutil (2026) - Name/Type/Text/Artist now use the width-in-
 *                         characters StringFieldEditor constructor instead
 *                         of the unbounded one - the 2-arg version grabs and
 *                         fills all available horizontal space, and once
 *                         this group had the whole (now wider, tab-based)
 *                         dialog to itself, that meant a single text field
 *                         stretching edge to edge
 *     Rémi Dutil (2026) - BasicFilterPreferencePage now adds this whole group
 *                         via addCompact() instead of createAndAdd() - see
 *                         that class' own header - so Legality/Language no
 *                         longer sit in a column stretched to the dialog's
 *                         full width just because Name/Type/Text/Artist are
 *                         in the same grid
 *     Rémi Dutil (2026) - two-column layout: Name/Type/Text on the left,
 *                         Legality/Artist/Language on the right - saves
 *                         vertical space now that all six no longer need to
 *                         stack in one column. Each column is its own
 *                         Composite with its own 2-column grid (label+
 *                         control), created directly under a shared "outer"
 *                         wrapper instead of getFieldEditorParent() - same
 *                         "sole child hosts its own sub-layout" shape
 *                         ColorsPreferenceGroup's left/right checkbox columns
 *                         already use. addTooltip()/addContextAssist() take
 *                         an explicit parent now (shadowing
 *                         MFieldEditorPreferencePage's own, which hardcodes
 *                         getFieldEditorParent()) because FieldEditor#
 *                         getTextControl(Composite) asserts the passed
 *                         Composite is the control's actual parent - passing
 *                         the wrong one throws.
 *     Rémi Dutil (2026) - optional userFilter constructor flag: when true,
 *                         Comment and Special (formerly UserFieldsPreference-
 *                         Group, now retired - see its own header) are added
 *                         to the bottom of the LEFT column - same grid as
 *                         Name/Type, so their label/field columns actually
 *                         line up with them, the same reason
 *                         NumbericalPreferenceGroup folded in User Price/
 *                         Count instead of keeping them as a separate page
 *     Rémi Dutil (2026) - briefly shrank every StringFieldEditor here from 40
 *                         to 22 characters wide (going two-column doubled the
 *                         number of these fields sitting side by side, so
 *                         keeping the old single-column width roughly
 *                         doubled the group's total width) - reverted back to
 *                         40 per explicit request: the field width itself
 *                         should stay as it was
 *     Rémi Dutil (2026) - pulled Text and Language out of the left/right
 *                         columns entirely: each now gets its own small
 *                         "row" Composite (textRow/langRow), a direct
 *                         child of "outer" with horizontalSpan=2 so the
 *                         row spans BOTH of outer's columns - NOT built
 *                         straight into outer itself, which was tried
 *                         first and looked broken: outer's own col1/col2
 *                         are sized to fit the WHOLE left/right panels
 *                         (label+box together), so a field built directly
 *                         under outer got a huge, mostly-empty label
 *                         column (sized to left's full width) and a
 *                         control column no wider than right alone -
 *                         exactly backwards from the goal. textRow/langRow
 *                         each get their own independent 2-column grid
 *                         (same shape left/right already use), so their
 *                         label stays compact and their control can
 *                         stretch across the row's full spanned width.
 *                         left/right now hold only Name/Type and Legality/
 *                         Artist (plus Comment/Special in left, when
 *                         userFilter). Text also grew from 40 to 60
 *                         characters wide - the point of moving it out was
 *                         to let it actually use the reclaimed width, not
 *                         just relocate it.
 *     Rémi Dutil (2026) - reordered top to bottom into: Text (its own full-
 *                         width row, using textRow - see above), then Name/
 *                         Legality, Type/Artist, Special/Language paired row
 *                         by row in left/right (moved Language back into
 *                         right, paired with Special, instead of its own
 *                         langRow - which is now gone), then Comment last, in
 *                         the LEFT column (same width as Special) rather than
 *                         a full-width row of its own.
 *                         left/right now rely on having the SAME row count
 *                         (Name/Type/Special/Comment vs Legality/Artist/
 *                         Language, when userFilter) so each pair lines up;
 *                         without userFilter, left only has Name/Type (2
 *                         rows) while right still has Legality/Artist/
 *                         Language (3) - Language just renders with nothing
 *                         to its left, same as any other field the base
 *                         DB-browsing dialog doesn't need paired.
 *     Rémi Dutil (2026) - two follow-up alignment fixes: Text's right edge
 *                         was overshooting past Legality/Artist/Language's
 *                         own right edge - its 60-character widthHint forced
 *                         textRow (and outer's columns under it) wider than
 *                         left+right's own natural combined width needed to
 *                         be. Dropped the widthHint entirely (unbounded
 *                         constructor) - Text's own minimum size is small, so
 *                         it just grabs whatever width textRow naturally
 *                         ends up with, landing exactly on left+right's
 *                         combined right edge instead of past it. Comment
 *                         moved out of its own full-width commentRow (which
 *                         is gone) back into the LEFT column, same width as
 *                         Special, so its right edge matches Special's
 *                         instead of overshooting the same way.
 *     Rémi Dutil (2026) - wireSearchTips(): the 6 Text controls built here
 *                         (Name/Type/Text/Artist[/Special/Comment]) are now
 *                         stashed as fields and given a FocusListener that
 *                         pushes that field's own Search Tips content into
 *                         the shared panel on focus, and clears it on focus
 *                         lost - see FilterSyntaxHelp's own header for why.
 *     Rémi Dutil (2026) - after two attempts at how much syntax detail the
 *                         tooltip vs. Search Tips should each carry, settled
 *                         on a firm split: every addTooltip() call here now
 *                         passes the exact same FilterSyntaxHelp.TOOLTIP
 *                         constant (generic-only: word AND, quoted phrase,
 *                         exclusion - nothing field-specific, so it can never
 *                         be wrong for any field). Everything else -
 *                         wildcards (with their AND/quoting/exclusion
 *                         incompatibility spelled out), [ability], {SYMBOL},
 *                         and each field's own note - lives only in that
 *                         field's FilterSyntaxHelp.*_TIPS constant, shown in
 *                         Search Tips on focus (regex dropped from both
 *                         surfaces entirely - see FilterSyntaxHelp's own
 *                         header for why).
 */
package com.reflexit.magiccards.ui.preferences.feditors;

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;

import com.reflexit.magiccards.core.legality.Format;
import com.reflexit.magiccards.core.model.CardTypes;
import com.reflexit.magiccards.core.model.FilterField;
import com.reflexit.magiccards.core.model.Languages;
import com.reflexit.magiccards.core.model.SpecialTags;
import com.reflexit.magiccards.ui.widgets.ContextAssist;

public class TextSearchPreferenceGroup extends MFieldEditorPreferencePage {
	private Collection<String> ids = new ArrayList<>(8);
	private final boolean userFilter;
	private Text nameControl;
	private Text typeControl;
	private Text textControl;
	private Text artistControl;
	private Text specialControl;
	private Text commentControl;

	public TextSearchPreferenceGroup() {
		this(false);
	}

	/** @param userFilter whether to also add Comment and Special below Text -
	 *            only meaningful for owned copies, so off when filtering the
	 *            Scryfall database itself */
	public TextSearchPreferenceGroup(boolean userFilter) {
		this.userFilter = userFilter;
	}

	@Override
	public Collection<String> getIds() {
		return ids;
	}

	// private Group group;
	@Override
	protected void createFieldEditors() {
		Composite outer = new Composite(getFieldEditorParent(), SWT.NONE);
		GridLayout outerLayout = new GridLayout(2, false);
		outerLayout.marginWidth = 0;
		outerLayout.marginHeight = 0;
		outer.setLayout(outerLayout);
		outer.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		outer.setFont(getFieldEditorParent().getFont());
		// text - its own full-width row, spanning BOTH of outer's columns
		// (horizontalSpan=2 on a dedicated mini-composite, not built
		// directly under outer - outer's own col1/col2 are sized to fit the
		// WHOLE left/right panels below, label-and-box together, so a field
		// built straight into outer would get a huge label column and a
		// control column no wider than "right" alone; this mini-composite
		// gets its own independent 2-column grid, same shape as left/right,
		// just spanning across both of outer's columns instead of sitting
		// in one). Placed first, above everything else.
		Composite textRow = new Composite(outer, SWT.NONE);
		GridData textRowGd = new GridData(GridData.FILL_HORIZONTAL);
		textRowGd.horizontalSpan = 2;
		textRow.setLayoutData(textRowGd);
		textRow.setFont(outer.getFont());
		String textId = FilterField.TEXT_LINE.getPrefConstant();
		getPreferenceStore().setDefault(textId, "");
		// no width-in-characters bound here (unlike every other field in
		// this page) - its own natural/minimum size is small, so it just
		// grabs whatever space textRow actually ends up with (left+right's
		// own combined natural width), landing its right edge exactly on
		// Legality/Artist/Language's own right edge instead of overshooting
		// past it the way an explicit widthHint wider than that combined
		// width would
		StringFieldEditor textSfe = new StringFieldEditor(textId, "Text", textRow);
		addContextAssist(textSfe, getTextProposals(), textRow);
		addField(textSfe);
		this.textControl = textSfe.getTextControl(textRow);
		addTooltip(textSfe, FilterSyntaxHelp.TOOLTIP, textRow);
		ids.add(textId);
		// left column: name, type[, special] - right column: legality,
		// artist[, language] - same row count on both sides (when
		// userFilter) so Name pairs with Legality, Type with Artist, and
		// Special with Language, row for row
		Composite left = new Composite(outer, SWT.NONE);
		Composite right = new Composite(outer, SWT.NONE);
		GridData leftGd = new GridData(GridData.FILL_HORIZONTAL);
		leftGd.verticalAlignment = SWT.TOP;
		left.setLayoutData(leftGd);
		GridData rightGd = new GridData(GridData.FILL_HORIZONTAL);
		// TOP, not the GridData default (CENTER) - matters if userFilter is
		// off (left has just Name/Type, 2 rows; right still has Legality/
		// Artist/Language, 3) - centering the shorter side would misalign it
		rightGd.verticalAlignment = SWT.TOP;
		right.setLayoutData(rightGd);
		left.setFont(outer.getFont());
		right.setFont(outer.getFont());
		// name
		String id = FilterField.NAME_LINE.getPrefConstant();
		getPreferenceStore().setDefault(id, "");
		ids.add(id);
		StringFieldEditor nameSfe = new StringFieldEditor(id, "Name", 40, left);
		addField(nameSfe);
		this.nameControl = nameSfe.getTextControl(left);
		addTooltip(nameSfe, FilterSyntaxHelp.TOOLTIP, left);
		// legality - built from the live Format registry (same one
		// ParseScryFallChecklist#BuildLegalities() populates real per-card
		// legality data for), not a hardcoded list - see this class' own
		// header for why the old hardcoded list went stale
		String legalityId = FilterField.FORMAT_TEXT.getPrefConstant();
		getPreferenceStore().setDefault(legalityId, "");
		Collection<Format> allFormats = Format.getFormats();
		String[][] legalities = new String[allFormats.size() + 1][2];
		legalities[0][0] = legalities[0][1] = ""; // no filter
		int fi = 1;
		for (Format f : allFormats) {
			legalities[fi][0] = legalities[fi][1] = f.name();
			fi++;
		}
		ComboFieldEditor legalitySfe = new ComboFieldEditor(legalityId, "Legality", legalities, right);
		addField(legalitySfe);
		ids.add(legalityId);
		// type
		String typeId = FilterField.TYPE_LINE.getPrefConstant();
		getPreferenceStore().setDefault(typeId, "");
		StringFieldEditor sfe = new StringFieldEditor(typeId, "Type", 40, left);
		addContextAssist(sfe, CardTypes.getProposals(), left);
		addField(sfe);
		this.typeControl = sfe.getTextControl(left);
		addTooltip(sfe, FilterSyntaxHelp.TOOLTIP, left);
		ids.add(typeId);
		// artist
		String artistId = FilterField.ARTIST.getPrefConstant();
		getPreferenceStore().setDefault(artistId, "");
		StringFieldEditor artistSfe = new StringFieldEditor(artistId, "Artist", 40, right);
		addField(artistSfe);
		this.artistControl = artistSfe.getTextControl(right);
		addTooltip(artistSfe, FilterSyntaxHelp.TOOLTIP, right);
		ids.add(artistId);
		if (this.userFilter) {
			// special
			String specialId = FilterField.SPECIAL.getPrefConstant();
			getPreferenceStore().setDefault(specialId, "");
			StringFieldEditor specialSfe = new StringFieldEditor(specialId, "Special", 40, left);
			addField(specialSfe);
			Text tags = specialSfe.getTextControl(left);
			this.specialControl = tags;
			addTooltip(specialSfe, FilterSyntaxHelp.TOOLTIP, left);
			ContextAssist.addContextAssist(tags, SpecialTags.getTags(), true);
			ids.add(specialId);
		}
		// language
		String langId = FilterField.LANG.getPrefConstant();
		getPreferenceStore().setDefault(langId, "");
		String[][] langs;
		String[] langValues = Languages.getInstance().getLangValues();
		langs = new String[langValues.length + 1][2];
		langs[0][0] = langs[0][1] = "";
		for (int i = 0; i < langs.length - 1; i++) {
			langs[i + 1][0] = langs[i + 1][1] = langValues[i];
		}
		ComboFieldEditor langSfe = new ComboFieldEditor(langId, "Language", langs, right);
		addField(langSfe);
		ids.add(langId);
		if (this.userFilter) {
			// comment - last row, in the LEFT column (same width as Special,
			// so its right edge lines up with Special's, not spanning the
			// full dialog width the way Text does)
			String commentId = FilterField.COMMENT.getPrefConstant();
			getPreferenceStore().setDefault(commentId, "");
			StringFieldEditor commentSfe = new StringFieldEditor(commentId, "Comment", 40, left);
			addField(commentSfe);
			this.commentControl = commentSfe.getTextControl(left);
			addTooltip(commentSfe, FilterSyntaxHelp.TOOLTIP, left);
			ids.add(commentId);
		}
	}

	/** Wires each of Name/Type/Text/Artist[/Special/Comment]'s own Search
	 *  Tips content into {@code tips} on focus gained, and clears it on
	 *  focus lost - see this class' own header. Called by
	 *  BasicFilterPreferencePage once both this page and the Search Tips
	 *  panel exist (this page is built first, for the layout's own visual
	 *  order, but wiring focus listeners doesn't care about that order). */
	public void wireSearchTips(FilterSyntaxHelp.Panel tips) {
		wireField(this.nameControl, tips, FilterSyntaxHelp.NAME_TIPS);
		wireField(this.typeControl, tips, FilterSyntaxHelp.TYPE_TIPS);
		wireField(this.textControl, tips, FilterSyntaxHelp.TEXT_TIPS);
		wireField(this.artistControl, tips, FilterSyntaxHelp.ARTIST_TIPS);
		wireField(this.specialControl, tips, FilterSyntaxHelp.SPECIAL_TIPS);
		wireField(this.commentControl, tips, FilterSyntaxHelp.COMMENT_TIPS);
	}

	private void wireField(Text control, FilterSyntaxHelp.Panel tips, String fieldTips) {
		if (control == null)
			return;
		control.addFocusListener(new FocusAdapter() {
			@Override
			public void focusGained(FocusEvent e) {
				tips.show(fieldTips);
			}

			@Override
			public void focusLost(FocusEvent e) {
				tips.clear();
			}
		});
	}

	static String[] textProposals = new String[] {

			"Living weapon", "Jump-start", "Commander ninjutsu", "Legendary landwalk", "Nonbasic landwalk", "Megamorph",
			"Haunt", "Forecast", "Graft", "Fortify", "Frenzy", "Gravestorm", "Hideaway", "Level Up", "Infect", "Reach",
			"Rampage", "Phasing", "Multikicker", "Morph", "Provoke", "Modular", "Ninjutsu", "Replicate", "Recover",
			"Poisonous", "Reinforce", "Persist", "Retrace", "Rebound", "Miracle", "Overload", "Outlast", "Prowess",
			"Renown", "Myriad", "Shroud", "Trample", "Vigilance", "Storm", "Soulshift", "Splice", "Transmute", "Ripple",
			"Suspend", "Vanishing", "Transfigure", "Wither", "Undying", "Soulbond", "Unleash", "Ascend", "Assist",
			"Afterlife", "Companion", "Fabricate", "Embalm", "Escape", "Fuse", "Menace", "Ingest", "Melee", "Improvise",
			"Mentor", "Partner", "Mutate", "Tribute", "Surge", "Skulk", "Riot", "Spectacle", "Forestwalk", "Islandwalk",
			"Mountainwalk", "Double strike", "Cumulative upkeep", "First strike", "Scavenge", "Encore", "Deathtouch",
			"Defender", "Amplify", "Affinity", "Bushido", "Convoke", "Bloodthirst", "Absorb", "Aura Swap", "Changeling",
			"Conspire", "Cascade", "Annihilator", "Battle Cry", "Cipher", "Bestow", "Dash", "Awaken", "Crew",
			"Aftermath", "Afflict", "Flanking", "Foretell", "Fading", "Eternalize", "Entwine", "Epic", "Dredge",
			"Delve", "Evoke", "Exalted", "Evolve", "Extort", "Dethrone", "Exploit", "Devoid", "Emerge", "Escalate",
			"Flying", "Haste", "Hexproof", "Indestructible", "Intimidate", "Lifelink", "Horsemanship", "Kicker",
			"Madness", "Swampwalk", "Desertwalk", "Craft", "Plainswalk", "Split second", "Augment", "Double agenda",
			"Reconfigure", "Ward", "Partner with", "Daybound", "Nightbound", "Decayed", "Disturb", "Squad", "Enlist",
			"Read Ahead", "Ravenous", "Blitz", "Offering", "Living metal", "Backup", "Banding", "Hidden agenda",
			"For Mirrodin!", "Friends forever", "Casualty", "Protection", "Compleated", "Enchant", "Flash", "Boast",
			"Demonstrate", "Sunburst", "Flashback", "Cycling", "Equip", "Buyback", "Hexproof from",
			"More Than Meets the Eye", "Cleave", "Champion", "Specialize", "Training", "Prototype", "Toxic", "Unearth",
			"Intensity", "Plainscycling", "Swampcycling", "Typecycling", "Wizardcycling", "Mountaincycling",
			"Basic landcycling", "Islandcycling", "Forestcycling", "Slivercycling", "Landcycling", "Bargain",
			"Choose a background", "Echo", "Disguise", "Doctor's companion", "Landwalk", "Umbra armor", "Freerunning",
			"Spree", "Saddle", "Shadow", "Warp", "Station", "Devour", "Undaunted", "Offspring", "Impending", "Gift",
			"Harmonize", "Exhaust", "Max speed", "Fear", "Tiered", "Mobilize", "Double team", "Job select", "Mayhem",
			"Web-slinging", "Prowl", "Solved", "Sneak", "Increment", "Paradigm", "Power-up", "Firebending",

			"Scry", "Seek", "Activate", "Attach", "Cast", "Counter", "Create", "Destroy", "Discard", "Exchange",
			"Exile", "Adapt", "Support", "Play", "Regenerate", "Reveal", "Sacrifice", "Shuffle", "Tap", "Untap", "Vote",
			"Time Travel", "Goad", "Transform", "Surveil", "Planeswalk", "Mill", "Learn", "Connive",
			"Venture into the dungeon", "Exert", "Open an Attraction", "Food", "Discover", "Abandon", "Explore",
			"Treasure", "Roll to Visit Your Attractions", "Set in motion", "Fateseal", "Manifest", "Populate", "Detain",
			"Investigate", "Monstrosity", "Clash", "Incubate", "Proliferate", "Meld", "Convert", "Fight", "Bolster",
			"Assemble", "Conjure", "Amass", "Cloak", "Suspect", "Collect evidence", "Role token", "Plot", "Harness",
			"Heist", "Forage", "Manifest dread", "Endure", "Prepared", "Incorporate", "Waterbend", "Airbend",
			"Earthbend", "Blight", "Behold", "Double", "Triple",

			"Eerie", "Battalion", "Bloodrush", "Channel", "Chroma", "Cohort", "Constellation", "Converge", "Delirium",
			"Domain", "Fateful hour", "Ferocious", "Formidable", "Grandeur", "Hellbent", "Heroic", "Imprint",
			"Inspired", "Join forces", "Kinship", "Landfall", "Lieutenant", "Metalcraft", "Morbid", "Parley",
			"Radiance", "Raid", "Rally", "Spell mastery", "Strive", "Sweep", "Tempting offer", "Threshold",
			"Will of the council", "Adamant", "Addendum", "Council's dilemma", "Eminence", "Enrage", "Hero's Reward",
			"Kinfall", "Landship", "Legacy", "Revolt", "Underdog", "Undergrowth", "Void", "Descend",
			"Fathomless descent", "Magecraft", "Teamwork", "Pack tactics", "Coven", "Alliance", "Corrupted",
			"Secret council", "Celebration", "Paradox", "Disappear", "Will of the Planeswalkers", "Survival", "Flurry",
			"Valiant", "Start your engines!", "Renew", "Repartee", "Opus", "Infusion", "Covercast", "Vivid",

	};

	/**
	 * TODO: refactor
	 *
	 * @return
	 */
	private String[] getTextProposals() {
		// TODO Auto-generated method stub
		return textProposals;
	}

	private void addContextAssist(StringFieldEditor sfe, String[] proposals, Composite parent) {
		Text t = sfe.getTextControl(parent);
		ContextAssist.addContextAssist(t, proposals, true);
	}

	private void addTooltip(StringFieldEditor sfe, String tip, Composite parent) {
		Text textControl = sfe.getTextControl(parent);
		textControl.setToolTipText(tip);
		sfe.getLabelControl(parent).setToolTipText(tip);
	}

	@Override
	protected void adjustGridLayout() {
		GridLayout layout = (GridLayout) ((Composite) this.getControl()).getLayout();
		layout.marginHeight = 5;
		layout.marginWidth = 5;
		super.adjustGridLayout();
	}
}
