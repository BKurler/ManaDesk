/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *     Rémi Dutil (2026) - base of the "View: ..." preference pages: Restore
 *                         Defaults also resets the view's Group By and sort
 *                         order, which live in the same store but have no
 *                         field editor on the page
 *******************************************************************************/
package com.reflexit.magiccards.ui.preferences;

import java.io.IOException;

import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IPersistentPreferenceStore;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.reflexit.magiccards.ui.MagicUIActivator;

/**
 * A card list view's preference page. Besides its field editors (columns, quick
 * filter...), "Restore Defaults" puts the view's Group By and sort order back to
 * their defaults when the page is applied - the open view follows.
 */
public abstract class CardViewPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {
	private boolean restoreLayout;

	protected CardViewPreferencePage(int style) {
		super(style);
	}

	@Override
	protected void performDefaults() {
		super.performDefaults();
		restoreLayout = true; // applied with the rest on Apply / OK
	}

	@Override
	public boolean performOk() {
		boolean ok = super.performOk();
		if (ok && restoreLayout) {
			restoreLayout = false;
			IPreferenceStore store = getPreferenceStore();
			store.setToDefault(PreferenceConstants.GROUP_FIELD);
			store.setToDefault(PreferenceConstants.SORT_ORDER);
			if (store instanceof IPersistentPreferenceStore && store.needsSaving()) {
				try {
					((IPersistentPreferenceStore) store).save();
				} catch (IOException e) {
					MagicUIActivator.log(e);
				}
			}
		}
		return ok;
	}
}
