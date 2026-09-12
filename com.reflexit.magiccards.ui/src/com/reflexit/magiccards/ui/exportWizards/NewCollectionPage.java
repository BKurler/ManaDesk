/*
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: the "New Collection" page.
 *                         Creates a collection under "Collections" - empty or from
 *                         a card list - with the "Unsorted" option. No Sideboard /
 *                         Extra. Importing into an existing collection is
 *                         ImportIntoCollectionPage.
 */
package com.reflexit.magiccards.ui.exportWizards;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Group;

import com.reflexit.magiccards.core.model.nav.ModelRoot;
import com.reflexit.magiccards.ui.utils.StatusDots;

public class NewCollectionPage extends AbstractCreateElementPage {
	private Button unsorted;

	public NewCollectionPage(String pageName, IStructuredSelection selection) {
		super(pageName, selection);
	}

	@Override
	protected String typeName() {
		return "collection";
	}

	@Override
	protected ModelRoot.Side side() {
		return ModelRoot.Side.COLLECTION;
	}

	@Override
	protected boolean defaultVirtual() {
		return false;
	}

	@Override
	protected boolean wantUnsorted() {
		return unsorted != null && unsorted.getSelection();
	}

	@Override
	protected void createTypeSpecificOptions(Group group) {
		unsorted = StatusDots.check(group, StatusDots.UNSORTED,
				"Unsorted - keep the manual card order and do not merge identical cards");
		StatusDots.exclusive(newVirtual, unsorted);
	}
}
