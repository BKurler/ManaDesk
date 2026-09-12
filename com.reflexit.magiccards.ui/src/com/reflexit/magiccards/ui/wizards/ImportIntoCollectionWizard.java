/*
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: "Import cards into an existing
 *                         collection", launched from the Cards Navigator
 *                         right-click.
 */
package com.reflexit.magiccards.ui.wizards;

import org.eclipse.jface.viewers.IStructuredSelection;

import com.reflexit.magiccards.ui.exportWizards.AbstractCardListImportPage;
import com.reflexit.magiccards.ui.exportWizards.AbstractCardListImportWizard;
import com.reflexit.magiccards.ui.exportWizards.ImportIntoCollectionPage;

public class ImportIntoCollectionWizard extends AbstractCardListImportWizard {

	@Override
	protected AbstractCardListImportPage createMainPage(IStructuredSelection selection) {
		return new ImportIntoCollectionPage("Main", selection);
	}

	@Override
	protected String windowTitle() {
		return "Import into Collection";
	}
}
