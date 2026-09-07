/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */
package com.reflexit.magiccards.ui.exportWizards;

import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.IImportWizard;
import org.eclipse.ui.IWorkbench;

import com.reflexit.magiccards.core.model.nav.CardCollection;
import com.reflexit.magiccards.core.model.nav.CardElement;
import com.reflexit.magiccards.ui.views.lib.DeckView;

public class DeckImportWizard extends Wizard implements IImportWizard {
	protected DeckImportPage mainPage;
	protected DeckImportPreviewPage previewPage;

	public DeckImportWizard() {
		setNeedsProgressMonitor(true);
	}

	@Override
	public void addPages() {
		addPage(mainPage);
		addPage(previewPage);
	}

	@Override
	public boolean canFinish() {
		// Finish is only reachable from the Preview page: the user must press
		// Next on the first page and look at the preview before importing.
		if (getContainer() == null || previewPage == null)
			return false;
		if (getContainer().getCurrentPage() != previewPage)
			return false;
		return previewPage.isPageComplete();
	}

	@Override
	public boolean performFinish() {
		mainPage.saveWidgetValues();
		mainPage.setIgnoreErrors(previewPage.isIgnoreErrors());
		mainPage.performImport(false);
		if (mainPage.getImportData().isOk()) {
			// open the deck / collection the cards landed in - a brand new one is
			// already opened by the navigator's ADD_CONTAINER handler, but an
			// existing target needs this
			CardElement target = mainPage.getElement();
			if (target instanceof CardCollection)
				DeckView.openCollection((CardCollection) target, null);
			return true;
		}
		return false;
	}

	@Override
	public void init(IWorkbench workbench, IStructuredSelection selection) {
		setWindowTitle("Import"); // NON-NLS-1
		setNeedsProgressMonitor(true);
		setForcePreviousAndNextButtons(true);
		mainPage = createMainPage(selection);
		previewPage = new DeckImportPreviewPage("Preview");
	}

	public DeckImportPage createMainPage(IStructuredSelection selection) {
		return new DeckImportPage("Import", selection);
	}
}
