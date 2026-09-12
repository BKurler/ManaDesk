/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - abstract base of every New / Import wizard: page 1 +
 *                         the shared card-list preview page + Finish handling.
 *                         "Empty" (create only) short-circuits the preview.
 */
package com.reflexit.magiccards.ui.exportWizards;

import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWizard;

import com.reflexit.magiccards.core.model.nav.CardCollection;
import com.reflexit.magiccards.core.model.nav.CardElement;
import com.reflexit.magiccards.ui.views.lib.DeckView;

public abstract class AbstractCardListImportWizard extends Wizard implements IWorkbenchWizard {
	protected AbstractCardListImportPage mainPage;
	protected DeckImportPreviewPage previewPage;
	private boolean finished;

	public AbstractCardListImportWizard() {
		setNeedsProgressMonitor(true);
	}

	/** Build the concrete first page. */
	protected abstract AbstractCardListImportPage createMainPage(IStructuredSelection selection);

	/** The wizard-dialog window title. */
	protected abstract String windowTitle();

	@Override
	public void init(IWorkbench workbench, IStructuredSelection selection) {
		setWindowTitle(windowTitle());
		setNeedsProgressMonitor(true);
		setForcePreviousAndNextButtons(true);
		mainPage = createMainPage(selection);
		previewPage = new DeckImportPreviewPage("Preview");
	}

	@Override
	public void addPages() {
		addPage(mainPage);
		addPage(previewPage);
	}

	/** The element created / targeted, or {@code null} if the wizard was cancelled
	 *  (available once the dialog has closed). */
	public CardElement getElement() {
		return finished ? mainPage.getElement() : null;
	}

	@Override
	public boolean canFinish() {
		if (mainPage.isEmptyMode())
			return mainPage.isPageComplete();
		// an import must go through the preview page first
		if (getContainer() == null || previewPage == null)
			return false;
		if (getContainer().getCurrentPage() != previewPage)
			return false;
		return previewPage.isPageComplete();
	}

	@Override
	public boolean performFinish() {
		if (mainPage.isEmptyMode()) {
			mainPage.createEmptyElement();
			openResult();
			finished = true;
			return true;
		}
		mainPage.saveWidgetValues();
		mainPage.setIgnoreErrors(previewPage.isIgnoreErrors());
		mainPage.performImport(false);
		if (mainPage.getImportData().isOk()) {
			openResult();
			finished = true;
			return true;
		}
		return false;
	}

	private void openResult() {
		CardElement target = mainPage.getElement();
		if (target instanceof CardCollection)
			DeckView.openCollection((CardCollection) target, null);
	}
}
