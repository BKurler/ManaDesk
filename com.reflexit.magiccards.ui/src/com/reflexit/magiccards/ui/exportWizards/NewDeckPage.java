/*
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: the "New Deck" page. Creates a
 *                         deck under "Decks" - empty or from a card list - and can
 *                         also create its Sideboard / Extra lists. Importing into
 *                         an existing deck is ImportIntoDeckPage.
 */
package com.reflexit.magiccards.ui.exportWizards;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Group;

import com.reflexit.magiccards.core.model.DeckAccessoriesPopulator;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.nav.CardCollection;
import com.reflexit.magiccards.core.model.nav.CollectionsContainer;
import com.reflexit.magiccards.core.model.nav.ModelRoot;
import com.reflexit.magiccards.ui.utils.WaitUtils;

public class NewDeckPage extends AbstractCreateElementPage {
	private Button createSideboard;
	private Button createExtra;

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

	@Override
	protected boolean defaultVirtual() {
		return true;
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
	}

	@Override
	protected void createEmptyExtras(CollectionsContainer parent) {
		CardCollection deck = createdElement();
		if (deck == null)
			return;
		Location base = deck.getLocation();
		if (createSideboard != null && createSideboard.getSelection())
			addFamily(parent, base.toSideboard(), wantVirtual(), false);
		if (createExtra != null && createExtra.getSelection())
			addFamily(parent, base.toExtra(), wantVirtual(), true);
	}

	private static void addFamily(CollectionsContainer parent, Location loc, boolean virtual, boolean populate) {
		if (parent.contains(loc))
			return;
		parent.addDeck(loc.getBaseFileName(), true, virtual);
		if (populate)
			WaitUtils.asyncExec(() -> DeckAccessoriesPopulator.populate(loc));
	}
}
