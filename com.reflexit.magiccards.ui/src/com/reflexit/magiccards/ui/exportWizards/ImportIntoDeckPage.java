/*
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: "Import cards into an existing
 *                         deck" - launched from the Cards Navigator right-click.
 */
package com.reflexit.magiccards.ui.exportWizards;

import org.eclipse.jface.viewers.IStructuredSelection;

import com.reflexit.magiccards.core.model.nav.ModelRoot;

public class ImportIntoDeckPage extends AbstractImportIntoPage {

	public ImportIntoDeckPage(String pageName, IStructuredSelection selection) {
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
}
