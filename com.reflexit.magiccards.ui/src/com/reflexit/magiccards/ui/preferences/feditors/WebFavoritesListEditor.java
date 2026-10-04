/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: the "Browse Website..."
 *                         favorites list editor (ManaDesk Preferences > Web
 *                         Favorites). Keeps Up/Down (unlike sibling
 *                         StringListFieldEditor, which hides them) because
 *                         order matters here - the first entry is
 *                         BrowseWebsiteDialog's starting page. Bypasses the
 *                         preference store entirely in doLoad()/doStore():
 *                         real-world deck-site URLs routinely contain
 *                         commas in query strings, which would silently
 *                         corrupt entries under ListEditor2's default
 *                         comma-joined single-string storage - so this talks
 *                         directly to WebFavoritesStore instead.
 *     Rémi Dutil (2026) - Remove/Edit... are now disabled whenever the
 *                         selected row is one of WebFavoritesStore.
 *                         BUILT_IN_FAVORITES - re-checked from the row's own
 *                         URL (WebFavoritesStore.isBuiltIn()) rather than
 *                         tracked in a parallel array, so it can never drift
 *                         out of sync with whatever the base ListEditor2's
 *                         own New.../Up/Down button handlers do to the list
 *                         widget directly. Up/Down are deliberately left
 *                         alone - built-ins are never persisted (doStore()/
 *                         WebFavoritesStore.save() always filters them back
 *                         out, and doLoad() always re-inserts them first, in
 *                         their fixed order), so any reordering done to a
 *                         built-in row here only lasts for the current
 *                         session and reverts the next time this page opens.
 *******************************************************************************/
package com.reflexit.magiccards.ui.preferences.feditors;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.window.Window;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;

import com.reflexit.magiccards.ui.dialogs.EditWebFavoriteDialog;
import com.reflexit.magiccards.ui.web.WebFavorite;
import com.reflexit.magiccards.ui.web.WebFavoritesStore;

public class WebFavoritesListEditor extends ListEditor2 {
	private Button editButton;

	public WebFavoritesListEditor(Composite parent) {
		super("webFavorites", "Favorites:", parent); // name/label unused - doLoad/doStore bypass the store
	}

	@Override
	protected void createButtons(Composite box) {
		super.createButtons(box); // New... / Remove / Up / Down
		editButton = createPushButton(box, "Edit...");
		editButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				editPressed();
			}
		});
	}

	private void editPressed() {
		int index = list.getSelectionIndex();
		if (index < 0)
			return;
		WebFavorite current = WebFavorite.fromDisplayString(list.getItem(index));
		if (WebFavoritesStore.isBuiltIn(current.getUrl()))
			return; // defense-in-depth - the button is already disabled for this row
		EditWebFavoriteDialog dialog = new EditWebFavoriteDialog(getShell(), current);
		if (dialog.open() == Window.OK) {
			list.setItem(index, dialog.getFavorite().toDisplayString());
			selectionChanged();
		}
	}

	@Override
	protected void removePressed() {
		int index = list.getSelectionIndex();
		if (index >= 0 && WebFavoritesStore.isBuiltIn(WebFavorite.fromDisplayString(list.getItem(index)).getUrl()))
			return; // defense-in-depth - the button is already disabled for this row
		super.removePressed();
	}

	@Override
	protected void selectionChanged() {
		super.selectionChanged();
		int index = list.getSelectionIndex();
		if (index < 0)
			return;
		boolean builtIn = WebFavoritesStore.isBuiltIn(WebFavorite.fromDisplayString(list.getItem(index)).getUrl());
		if (builtIn)
			getRemoveButton().setEnabled(false);
		if (editButton != null && !editButton.isDisposed())
			editButton.setEnabled(!builtIn);
	}

	@Override
	protected String getNewInputObject() {
		EditWebFavoriteDialog dialog = new EditWebFavoriteDialog(getShell(), null);
		if (dialog.open() == Window.OK) {
			WebFavorite f = dialog.getFavorite();
			if (!f.getUrl().isEmpty())
				return f.toDisplayString();
		}
		return null;
	}

	@Override
	protected void doLoad() {
		if (list == null)
			return;
		list.removeAll();
		for (WebFavorite f : WebFavoritesStore.load())
			list.add(f.toDisplayString());
	}

	@Override
	protected void doLoadDefault() {
		doLoad();
	}

	@Override
	protected void doStore() {
		if (list == null)
			return;
		List<WebFavorite> favorites = new ArrayList<>();
		for (String item : list.getItems())
			favorites.add(WebFavorite.fromDisplayString(item));
		WebFavoritesStore.save(favorites);
	}

	@Override
	protected String createList(String[] items) {
		return null; // unused - doStore() bypasses the preference store
	}

	@Override
	protected String[] parseString(String stringList) {
		return new String[0]; // unused - doLoad() bypasses the preference store
	}
}
