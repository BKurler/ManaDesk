/*
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: the "New Deck" page. Creates a
 *                         deck under "Decks" - empty or from a card list - and can
 *                         also create its Sideboard / Extra lists. Importing into
 *                         an existing deck is ImportIntoDeckPage.
 *     Rémi Dutil (2026) - "Also create a Sideboard/Extra" now also takes
 *                         effect for an imported deck, not just an empty one
 *                         (see AbstractCardListImportPage's own header) -
 *                         createEmptyExtras() (Empty-mode, UI thread, reads
 *                         the live checkboxes) and createImportExtras()
 *                         (import, background thread, takes the already-
 *                         cached choices) now share the same createExtras()
 *                         logic instead of duplicating it.
 *     Rémi Dutil (2026) - onSideboardDetected()/gateSideboardOnImport(): a
 *                         Commander deck's own commander card needs to land
 *                         in the sideboard (MTG convention, and how
 *                         DeckTextExtractor now tags it), and a real
 *                         Sideboard section is easy to miss remembering to
 *                         check for - "Also create a Sideboard" now auto-
 *                         checks itself once browsing a page detects either
 *                         one (see AbstractCardListImportPage's own header),
 *                         and this is the one page where unchecking it
 *                         afterward now actually EXCLUDES those cards from
 *                         the import (not just skips pre-creating an empty
 *                         sideboard sibling), so a user who genuinely
 *                         doesn't want the sideboard/commander can still opt
 *                         out.
 *     Rémi Dutil (2026) - added a "Default Format:" combo (defaults to
 *                         Standard, like EditDeckPropertiesDialog's own) -
 *                         wantFormat() feeds it into the created deck's
 *                         IStorageInfo (see AbstractCardListImportPage's own
 *                         header). onDeckMetaDetected() also updates it from
 *                         a browsed page's own stated format when one was
 *                         found (DeckTextExtractor#detectDeckMeta()) -
 *                         "in doubt, leave Standard" per an explicit
 *                         request, so a miss never touches the combo.
 *     Rémi Dutil (2026) - a new deck is always virtual (no more Virtual
 *                         checkbox); a hint above Name says so.
 */
package com.reflexit.magiccards.ui.exportWizards;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;

import com.reflexit.magiccards.core.legality.Format;
import com.reflexit.magiccards.core.model.DeckAccessoriesPopulator;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.nav.CardCollection;
import com.reflexit.magiccards.core.model.nav.CollectionsContainer;
import com.reflexit.magiccards.core.model.nav.ModelRoot;
import com.reflexit.magiccards.ui.utils.WaitUtils;

public class NewDeckPage extends AbstractCreateElementPage {
	private Button createSideboard;
	private Button createExtra;
	private Combo formatCombo;

	public NewDeckPage(String pageName, IStructuredSelection selection) {
		super(pageName, selection);
	}

	@Override
	protected String typeName() {
		return "deck";
	}

	@Override
	protected ModelRoot.Side side() {
		return ModelRoot.Side.DECK;
	}

	/** A deck is always virtual - it lists cards, it does not own them. */
	@Override
	protected boolean wantVirtual() {
		return true;
	}

	@Override
	protected void createLeadingOptions(Group group) {
		Label hint = new Label(group, SWT.WRAP);
		hint.setText("A deck is virtual: it lists the cards to play. The cards you own stay in your collections.");
		hint.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).span(3, 1).hint(300, SWT.DEFAULT).create());
	}

	@Override
	protected boolean separateTypeSpecificOptions() {
		return true; // creates additional elements - not just another flag on this one
	}

	@Override
	protected void createTypeSpecificOptions(Group group) {
		createSideboard = new Button(group, SWT.CHECK);
		createSideboard.setText("Also create a Sideboard");
		createSideboard.setLayoutData(GridDataFactory.fillDefaults().span(3, 1).create());
		createExtra = new Button(group, SWT.CHECK);
		createExtra.setText("Also create an Extra list (tokens, emblems, markers)");
		createExtra.setLayoutData(GridDataFactory.fillDefaults().span(3, 1).create());

		Label formatLabel = new Label(group, SWT.NONE);
		formatLabel.setText("Default Format:");
		formatCombo = new Combo(group, SWT.READ_ONLY);
		for (Format f : Format.getFormats())
			formatCombo.add(f.name());
		formatCombo.setText(Format.STANDARD.name());
		formatCombo.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).span(2, 1).create());
	}

	/** The format the Legality tab will validate the new deck against by
	 *  default - "Standard" unless the user changed it, or
	 *  {@link #onDeckMetaDetected} updated it from a browsed page's own
	 *  stated format. */
	@Override
	protected String wantFormat() {
		return formatCombo != null && !formatCombo.isDisposed() ? formatCombo.getText() : null;
	}

	/** Also updates the Default Format combo when a browsed page states one
	 *  (see DeckTextExtractor#detectDeckMeta()) - unlike the Name field
	 *  (only filled when empty), this overwrites the combo's current
	 *  selection every time a format IS detected: the combo always shows
	 *  SOME value (defaults to "Standard"), so "only if empty" doesn't
	 *  apply the way it does for Name - the detected format is a more
	 *  informed guess than a default nobody chose. A miss (format == null)
	 *  leaves it exactly as-is, never resets it back to "Standard". */
	@Override
	protected void onDeckMetaDetected(String title, String format) {
		super.onDeckMetaDetected(title, format);
		if (format != null && formatCombo != null && !formatCombo.isDisposed() && formatCombo.indexOf(format) >= 0)
			formatCombo.setText(format);
	}

	@Override
	protected boolean wantSideboard() {
		return createSideboard != null && createSideboard.getSelection();
	}

	@Override
	protected boolean gateSideboardOnImport() {
		return true; // this is the one page where the checkbox is a real, live user choice
	}

	@Override
	protected void onSideboardDetected(boolean hasSideboard) {
		if (hasSideboard && createSideboard != null && !createSideboard.isDisposed())
			createSideboard.setSelection(true);
	}

	@Override
	protected boolean wantExtra() {
		return createExtra != null && createExtra.getSelection();
	}

	@Override
	protected void createEmptyExtras(CollectionsContainer parent) {
		createExtras(parent, wantSideboard(), wantExtra(), wantVirtual());
	}

	@Override
	protected void createImportExtras(CollectionsContainer parent, boolean wantSideboard, boolean wantExtra,
			boolean virtual) {
		createExtras(parent, wantSideboard, wantExtra, virtual);
	}

	private void createExtras(CollectionsContainer parent, boolean sideboard, boolean extra, boolean virtual) {
		CardCollection deck = createdElement();
		if (deck == null)
			return;
		Location base = deck.getLocation();
		if (sideboard)
			addFamily(parent, base.toSideboard(), virtual, false);
		if (extra)
			addFamily(parent, base.toExtra(), virtual, true);
	}

	private static void addFamily(CollectionsContainer parent, Location loc, boolean virtual, boolean populate) {
		if (parent.contains(loc))
			return;
		parent.addDeck(loc.getBaseFileName(), true, virtual);
		if (populate)
			WaitUtils.asyncExec(() -> DeckAccessoriesPopulator.populate(loc));
	}
}
