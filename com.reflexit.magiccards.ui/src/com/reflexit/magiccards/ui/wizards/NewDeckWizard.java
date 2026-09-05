/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.ui.wizards;

import org.eclipse.ui.INewWizard;

import com.reflexit.magiccards.core.model.DeckAccessoriesPopulator;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.nav.CardCollection;
import com.reflexit.magiccards.core.model.nav.CardElement;
import com.reflexit.magiccards.core.model.nav.CollectionsContainer;
import com.reflexit.magiccards.ui.utils.WaitUtils;

/**
 * This is a sample new wizard. Its role is to create a new file resource in the
 * provided container. If the container resource (a folder or a project) is
 * selected in the workspace when the wizard is opened, it will accept it as the
 * target container. The wizard creates one file with the extension "element".
 * If a sample multi-page editor (also available as a template) is registered
 * for the same extension, it will be able to open it.
 */
public class NewDeckWizard extends NewCardElementWizard implements INewWizard {
	public static final String ID = "com.reflexit.magiccards.ui.wizards.NewDeckWizard";
	private boolean createSideboard;
	private boolean createExtra;

	/**
	 * Constructor for NewDeckWizard.
	 */
	public NewDeckWizard() {
		super();
	}

	/**
	 * Adding the page to the wizard.
	 */
	@Override
	public void addPages() {
		this.page = new NewDeckWizardPage(this.selection);
		addPage(this.page);
	}

	@Override
	protected void beforeFinish() {
		super.beforeFinish();
		// doCreateCardElement() runs on a background Job (see doFinish()), and
		// Button.getSelection() is an SWT call that must happen on the UI
		// thread - read the page's checkboxes here, while still on the UI
		// thread, and cache them for the background Job to use
		NewDeckWizardPage deckPage = (NewDeckWizardPage) this.page;
		this.createSideboard = deckPage.isCreateSideboard();
		this.createExtra = deckPage.isCreateExtra();
	}

	@Override
	protected CardElement doCreateCardElement(CollectionsContainer parent, String name, boolean virtual,
			boolean unsorted) {
		CardCollection d = new CardCollection(name + ".xml", parent, true, virtual, unsorted);
		d.persistInitialSettings(true, virtual, unsorted);

		if (this.createSideboard) {
			createFamilyMember(parent, d.getLocation().toSideboard(), virtual);
		}
		if (this.createExtra) {
			Location extraLoc = d.getLocation().toExtra();
			if (createFamilyMember(parent, extraLoc, virtual) != null) {
				// already off the UI thread here (background Job, see doFinish()) -
				// populate() fires CardEvents that touch SWT widgets, so it must not
				// run directly on this thread
				WaitUtils.asyncExec(() -> DeckAccessoriesPopulator.populate(extraLoc));
			}
		}
		return d;
	}

	/** Creates {@code loc} (the new deck's sideboard/extra sibling) if it
	 * doesn't already exist, mirroring the deck's own virtual flag. Returns
	 * null (and creates nothing) if something with that name is already there. */
	private CardCollection createFamilyMember(CollectionsContainer parent, Location loc, boolean virtual) {
		if (parent.contains(loc))
			return null;
		return parent.addDeck(loc.getBaseFileName(), true, virtual);
	}
}