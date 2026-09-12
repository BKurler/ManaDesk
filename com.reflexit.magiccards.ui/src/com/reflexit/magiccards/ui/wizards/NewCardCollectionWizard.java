/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - now "New Collection" only (create empty / from a card
 *                         list); importing into an existing collection is
 *                         ImportIntoCollectionWizard
 */
package com.reflexit.magiccards.ui.wizards;

import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.INewWizard;

import com.reflexit.magiccards.ui.exportWizards.AbstractCardListImportPage;
import com.reflexit.magiccards.ui.exportWizards.AbstractCardListImportWizard;
import com.reflexit.magiccards.ui.exportWizards.NewCollectionPage;

public class NewCardCollectionWizard extends AbstractCardListImportWizard implements INewWizard {
	public static final String ID = "com.reflexit.magiccards.ui.wizards.NewCardCollectionWizard";

	@Override
	protected AbstractCardListImportPage createMainPage(IStructuredSelection selection) {
		return new NewCollectionPage("Main", selection);
	}

	@Override
	protected String windowTitle() {
		return "New Collection";
	}
}
