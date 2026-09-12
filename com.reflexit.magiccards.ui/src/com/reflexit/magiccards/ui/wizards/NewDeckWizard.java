/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - now "New Deck" only (create empty / from a card list);
 *                         importing into an existing deck is ImportIntoDeckWizard
 */
package com.reflexit.magiccards.ui.wizards;

import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.INewWizard;

import com.reflexit.magiccards.ui.exportWizards.AbstractCardListImportPage;
import com.reflexit.magiccards.ui.exportWizards.AbstractCardListImportWizard;
import com.reflexit.magiccards.ui.exportWizards.NewDeckPage;

public class NewDeckWizard extends AbstractCardListImportWizard implements INewWizard {
	public static final String ID = "com.reflexit.magiccards.ui.wizards.NewDeckWizard";

	@Override
	protected AbstractCardListImportPage createMainPage(IStructuredSelection selection) {
		return new NewDeckPage("Main", selection);
	}

	@Override
	protected String windowTitle() {
		return "New Deck";
	}
}
