/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: manage the "Browse
 *                         Website..." favorites list (New Deck/Collection
 *                         wizard's Website import source) - full add/edit/
 *                         remove/reorder, in addition to the quick
 *                         "Add current page to Favorites" shortcut already
 *                         available inside BrowseWebsiteDialog itself. Both
 *                         read/write the same WebFavoritesStore file.
 *******************************************************************************/
package com.reflexit.magiccards.ui.preferences;

import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.preferences.feditors.WebFavoritesListEditor;

public class WebFavoritesPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {
	public static final String ID = "com.reflexit.magiccards.ui.preferences.WebFavoritesPreferencePage";

	public WebFavoritesPreferencePage() {
		super(GRID);
		setPreferenceStore(MagicUIActivator.getDefault().getPreferenceStore());
		setDescription("Favorite websites offered by \"Browse Website...\" in the New Deck/Collection wizards. "
				+ "The first entry is the starting page - reorder with Up/Down.");
	}

	@Override
	protected void createFieldEditors() {
		addField(new WebFavoritesListEditor(getFieldEditorParent()));
	}

	@Override
	public void init(IWorkbench workbench) {
	}
}
