/*
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: "Import cards into an existing
 *                         collection" - launched from the Cards Navigator
 *                         right-click.
 */
package com.reflexit.magiccards.ui.exportWizards;

import org.eclipse.jface.viewers.IStructuredSelection;

import com.reflexit.magiccards.core.model.nav.ModelRoot;

public class ImportIntoCollectionPage extends AbstractImportIntoPage {

	public ImportIntoCollectionPage(String pageName, IStructuredSelection selection) {
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
}
