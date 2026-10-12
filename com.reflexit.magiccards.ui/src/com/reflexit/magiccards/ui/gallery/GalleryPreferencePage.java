/*
 * Contributors:
 *     Rémi Dutil (2026) - extends CardViewPreferencePage: Restore Defaults also resets Group By / sort
 */
package com.reflexit.magiccards.ui.gallery;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.ui.IWorkbench;

import com.reflexit.magiccards.ui.preferences.CardViewPreferencePage;
import com.reflexit.magiccards.ui.preferences.PreferenceConstants;
import com.reflexit.magiccards.ui.preferences.PreferenceInitializer;

public class GalleryPreferencePage extends CardViewPreferencePage {
	public GalleryPreferencePage() {
		super(GRID);
		setPreferenceStore(PreferenceInitializer.getLocalStore(getId()));
		setDescription("Gallery View Preferences");
	}

	public static String getId() {
		return GalleryPreferencePage.class.getName();
	}

	@Override
	protected void createFieldEditors() {
		addField(new BooleanFieldEditor(PreferenceConstants.LOCAL_SHOW_QUICKFILTER, "Show quick filter",
				getFieldEditorParent()));
	}

	@Override
	public void init(IWorkbench workbench) {
		// nothing
	}
}
