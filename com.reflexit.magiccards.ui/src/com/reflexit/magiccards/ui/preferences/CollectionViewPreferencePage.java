/*
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: split off DeckViewPreferencePage
 *                         so Collection views persist their own column
 *                         visibility/width/order instead of sharing the
 *                         Deck view's - they don't want to display the same
 *                         things and a change to one was silently bleeding
 *                         into the other
 */
package com.reflexit.magiccards.ui.preferences;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.reflexit.magiccards.ui.preferences.feditors.ColumnFieldEditor;
import com.reflexit.magiccards.ui.views.columns.ColumnCollection;
import com.reflexit.magiccards.ui.views.lib.DeckColumnCollection;

public class CollectionViewPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {
	public static String PPID = CollectionViewPreferencePage.class.getName();
	public CollectionViewPreferencePage() {
		super(GRID);
		setPreferenceStore(PreferenceInitializer.getCollectionStore());
		setDescription("Collection View Preferences");
	}

	@Override
	protected void createFieldEditors() {
		// same column set as Deck (Sideboard/Extra dropped - a single
		// deck/collection's own tab always shows one pile at a time), just a
		// separately persisted store
		ColumnCollection columnCollection = new DeckColumnCollection(getClass().getName());
		addField(new BooleanFieldEditor(PreferenceConstants.LOCAL_SHOW_QUICKFILTER, "Show quick filter",
				getFieldEditorParent()));
		addField(new ColumnFieldEditor(PreferenceConstants.LOCAL_COLUMNS, "Visible Columns and Order",
				getFieldEditorParent(),
				columnCollection));
	}

	@Override
	public void init(IWorkbench workbench) {
		// nothing
	}
}
