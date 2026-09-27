/*
 * Contributors:
 *     Rémi Dutil (2026) - added the Condition filter group
 *     Rémi Dutil (2026) - added the Proxy filter group
 *     Rémi Dutil (2026) - moved Condition and Proxy to User Filter: they are
 *                         specific to the user's own collection, not generic
 *                         card-database facts like the other Basic Filter groups
 *     Rémi Dutil (2026) - Finish filter group, alongside Rarity: unlike
 *                         Condition/Proxy it's a real fact about a printing
 *                         (which finishes does it come in?) even with no
 *                         owned copy at all, so it belongs here - it needs to
 *                         work when filtering the Scryfall database itself
 *     Rémi Dutil (2026) - passes the owning CardFilterDialog's
 *                         allowsMultipleFinishesPerRow() into
 *                         CardFinishPreferenceGroup, so its "And" checkbox is
 *                         disabled where it can never apply
 *     Rémi Dutil (2026) - createContents(): the page never grabbed vertical
 *                         space of its own, so it sat with a lot of empty
 *                         room below the third row - the dialog's overall
 *                         height is actually set by the Set Filter page's
 *                         big set list (EditionsFilterPreferencePage), not
 *                         by this page's own content. Filled that dead space
 *                         with an inline search-syntax cheat sheet (via the
 *                         new FilterSyntaxHelp helper, shared with
 *                         AbilitiesFilterPreferencePage) instead of leaving
 *                         it blank - covers word AND/quoting/exclusion/regex/
 *                         mana-symbol/ability-bracket syntax and the Name
 *                         field's own wildcards (star and question mark), so
 *                         most of what's in the Help is visible without
 *                         leaving the dialog.
 *     Rémi Dutil (2026) - createContents(): first tried merging Types/Colors/
 *                         Rarity/Finish/Numerical into one 5-across row once
 *                         CardFilterDialog switched to tabs (more width to
 *                         work with, no tree eating a column) - too wide in
 *                         practice. Settled on Types/Colors/Rarity across one
 *                         row and Finish/Numerical on the row below instead -
 *                         still one fewer row than the original 3, still
 *                         leaves FilterSyntaxHelp a natural amount of room at
 *                         the bottom, without stretching the dialog sideways.
 *     Rémi Dutil (2026) - addCompact()/createContents(): createAndAdd() (the
 *                         shared AbstractFilterPreferencePage helper) always
 *                         sets GridData.FILL_HORIZONTAL on the group it adds
 *                         - grabExcessHorizontalSpace=true - so every one of
 *                         these small checkbox groups was being stretched to
 *                         an even share of the row's full width instead of
 *                         sitting at its own natural size, leaving each one
 *                         looking sparse. addCompact() re-clears that flag
 *                         afterward (left-aligned, natural width) for these
 *                         5 groups specifically - the text fields below
 *                         still want to fill horizontally, so
 *                         TextSearchPreferenceGroup keeps using plain
 *                         createAndAdd(). Also swapped Finish/Numerical to
 *                         put Finish to the right of Power/Toughness.
 *     Rémi Dutil (2026) - renamed "Basic Filter" -> "Main Filter" and folded
 *                         in the former Abilities Filter tab's
 *                         AdvancedTextSeachFieldsPreferenceGroup (the "Text/
 *                         Or/Or/Excluding" fields), plus the former User
 *                         Filter tab's groups when
 *                         this.dialog.allowsUserFilter() (Condition/Proxy/
 *                         Ownership - meaningless when browsing the Scryfall
 *                         database itself, so gated the same way
 *                         allowsMultipleFinishesPerRow() already is). See
 *                         CardFilterDialog's own header for why. Both
 *                         AbilitiesFilterPreferencePage and
 *                         UserFilterPreferencePage are now gone.
 *     Rémi Dutil (2026) - dropped AdvancedTextSeachFieldsPreferenceGroup (the
 *                         former Abilities Filter tab's Text/Or/Or/Excluding
 *                         fields) again: redundant now that the single Text
 *                         field plus FilterSyntaxHelp's explanation already
 *                         cover the same ground, and a filter saved with
 *                         those fields can still be reproduced through Text.
 *     Rémi Dutil (2026) - createContents(): folded Condition into firstRow
 *                         (right of Colors) and Proxy into secondRow (right
 *                         of Finish) instead of their own separate row below
 *                         - both rows grow one column wider when
 *                         allowsUserFilter() (4 and 3 instead of 3 and 2).
 *                         UserPriceCountPreferenceGroup (User Price, Count)
 *                         is added to secondRow AFTER Proxy, so - secondRow
 *                         now being 3 columns wide - it wraps onto its own
 *                         row directly under Numerical, i.e. right below
 *                         Collector's Number, same as before. Also reordered
 *                         firstRow to Types/Rarity/Colors (was Types/Colors/
 *                         Rarity).
 *     Rémi Dutil (2026) - createContents(): TextSearchPreferenceGroup and
 *                         UserFieldsPreferenceGroup (Comment/Special/
 *                         Ownership) now use addCompact() instead of plain
 *                         createAndAdd() - createAndAdd's FILL_HORIZONTAL
 *                         was stretching these groups' own outer control to
 *                         the full dialog width, which (since GridLayout
 *                         gives every row in a column the SAME width) forced
 *                         even the narrow fields in them - Legality,
 *                         Language, Comment, Special - to sit in a
 *                         needlessly wide column. addCompact() packs the
 *                         group to its own natural/preferred width instead
 *                         (the internal fields' own widthHint-bounded sizes
 *                         still hold - see TextSearchPreferenceGroup's own
 *                         header - grabExcessHorizontalSpace only matters
 *                         when there's leftover space to distribute, and
 *                         packing to natural size leaves none). Comment and
 *                         Special also picked up the same width-in-characters
 *                         StringFieldEditor constructor Name/Type/Text/Artist
 *                         already use - see UserFieldsPreferenceGroup's own
 *                         header.
 *     Rémi Dutil (2026) - createContents(): retired UserFieldsPreferenceGroup
 *                         and UserPriceCountPreferenceGroup entirely - each
 *                         field they held was folded into whichever existing
 *                         group it needs to visually line up with, since two
 *                         separate FieldEditorPreferencePages never share one
 *                         grid (each computes its own column widths from its
 *                         own labels, so short labels like "User Price"/
 *                         "Count" never lined up with "Collector's Number"
 *                         above them, no matter how they were positioned):
 *                         User Price/Count moved into NumbericalPreference-
 *                         Group (right after Collector's Number, its own
 *                         header), Comment/Special moved into TextSearch-
 *                         PreferenceGroup (bottom of its left column, below
 *                         Text, its own header). Ownership dropped its old
 *                         3-way RadioGroupFieldEditor for a new, standalone
 *                         OwnershipPreferenceGroup (Own/Virtual checkboxes,
 *                         same concept as Proxy - see OwnershipFieldEditor's
 *                         own header for why it's not a real
 *                         ISearchableProperty checkbox group like Proxy is),
 *                         placed in secondRow between Finish and Proxy.
 *                         TextSearchPreferenceGroup also went two-column
 *                         internally (Name/Type/Text left, Legality/Artist/
 *                         Language right - its own header) to save vertical
 *                         space, so thirdRow no longer grows as tall.
 *     Rémi Dutil (2026) - createContents(): keeps a reference to the
 *                         TextSearchPreferenceGroup it builds (textGroup) so
 *                         it can call wireSearchTips() with the Panel handle
 *                         FilterSyntaxHelp.create() now returns - see both
 *                         classes' own headers. Order here still matters for
 *                         the LAYOUT (textGroup before FilterSyntaxHelp, so
 *                         Search Tips renders last/at the bottom), but not
 *                         for the wiring itself - a FocusListener can be
 *                         added to an already-built control at any time.
 */
package com.reflexit.magiccards.ui.preferences;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import com.reflexit.magiccards.ui.dialogs.CardFilterDialog;
import com.reflexit.magiccards.ui.preferences.feditors.CardConditionPreferenceGroup;
import com.reflexit.magiccards.ui.preferences.feditors.ColorsPreferenceGroup;
import com.reflexit.magiccards.ui.preferences.feditors.CardFinishPreferenceGroup;
import com.reflexit.magiccards.ui.preferences.feditors.MFieldEditorPreferencePage;
import com.reflexit.magiccards.ui.preferences.feditors.NumbericalPreferenceGroup;
import com.reflexit.magiccards.ui.preferences.feditors.FilterSyntaxHelp;
import com.reflexit.magiccards.ui.preferences.feditors.OwnershipPreferenceGroup;
import com.reflexit.magiccards.ui.preferences.feditors.ProxyPreferenceGroup;
import com.reflexit.magiccards.ui.preferences.feditors.RarityPreferenceGroup;
import com.reflexit.magiccards.ui.preferences.feditors.TextSearchPreferenceGroup;
import com.reflexit.magiccards.ui.preferences.feditors.TypesPreferenceGroup;

public class BasicFilterPreferencePage extends AbstractFilterPreferencePage {
	private Composite panel;

	public BasicFilterPreferencePage(CardFilterDialog cardFilterDialog) {
		super(cardFilterDialog);
		setTitle("Main Filter");
		// setDescription("A demonstration of a preference page
		// implementation");
	}

	@Override
	protected Control createContents(Composite parent) {
		setTitle("Main Filter");
		this.panel = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		this.panel.setLayout(layout);
		this.panel.setFont(parent.getFont());
		boolean userFilter = this.dialog.allowsUserFilter();
		// card-database facts - see this class' own header for the row layout
		Composite firstRow = createColumnComposite(this.panel, userFilter ? 4 : 3);
		Composite secondRow = createColumnComposite(this.panel, userFilter ? 4 : 2);
		Composite thirdRow = createColumnComposite(this.panel, 1);
		addCompact(new TypesPreferenceGroup(), firstRow);
		addCompact(new RarityPreferenceGroup(), firstRow);
		addCompact(new ColorsPreferenceGroup(), firstRow);
		addCompact(new NumbericalPreferenceGroup(userFilter), secondRow);
		addCompact(new CardFinishPreferenceGroup(this.dialog.allowsMultipleFinishesPerRow()), secondRow);
		// user-copy-specific groups - only where they apply (meaningless when
		// browsing the Scryfall database itself). Condition lands right of
		// Colors (firstRow grows a column wider above); Ownership/Proxy land
		// right of Finish, in that order (secondRow grows two columns wider)
		if (userFilter) {
			addCompact(new CardConditionPreferenceGroup(), firstRow);
			addCompact(new OwnershipPreferenceGroup(), secondRow);
			addCompact(new ProxyPreferenceGroup(), secondRow);
		}
		TextSearchPreferenceGroup textGroup = new TextSearchPreferenceGroup(userFilter);
		addCompact(textGroup, thirdRow);
		FilterSyntaxHelp.Panel tips = FilterSyntaxHelp.create(this.panel);
		textGroup.wireSearchTips(tips);
		return this.panel;
	}

	/** Like createAndAdd(), but sized to the group's own natural width
	 *  instead of stretched to an even share of the row - see this class'
	 *  own header. */
	private void addCompact(MFieldEditorPreferencePage subPage, Composite parent) {
		createAndAdd(subPage, parent);
		GridData gd = (GridData) subPage.getControl().getLayoutData();
		gd.grabExcessHorizontalSpace = false;
		gd.horizontalAlignment = SWT.LEFT;
	}

	private Composite createColumnComposite(Composite parent, int cols) {
		Composite sec = new Composite(parent, SWT.NONE);
		GridLayout layout2row = new GridLayout(cols, false);
		layout2row.marginHeight = 0;
		layout2row.marginWidth = 0;
		sec.setLayout(layout2row);
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.verticalAlignment = SWT.BEGINNING;
		sec.setLayoutData(gd);
		return sec;
	}
}
