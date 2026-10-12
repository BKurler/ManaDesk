/*
 * Contributors:
 *     Rémi Dutil (2026) - "Visible Columns and Order" now built from
 *                         DeckColumnCollection instead of the generic
 *                         MagicColumnCollection, so Sideboard/Extra are no
 *                         longer offered as pickable columns here
 *     Rémi Dutil (2026) - split back to Deck-only: Collection views now have
 *                         their own CollectionViewPreferencePage/store, so
 *                         column visibility/width/order no longer bleeds
 *                         between the two (DeckView#getPreferencePageId()
 *                         picks whichever page/store applies)
 *     Rémi Dutil (2026) - extends CardViewPreferencePage: Restore Defaults also resets Group By / sort
 */
package com.reflexit.magiccards.ui.preferences;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.ui.IWorkbench;

import com.reflexit.magiccards.ui.preferences.feditors.ColumnFieldEditor;
import com.reflexit.magiccards.ui.views.columns.ColumnCollection;
import com.reflexit.magiccards.ui.views.lib.DeckColumnCollection;

public class DeckViewPreferencePage extends CardViewPreferencePage {
	public static String PPID = DeckViewPreferencePage.class.getName();
	public DeckViewPreferencePage() {
		super(GRID);
		setPreferenceStore(PreferenceInitializer.getDeckStore());
		setDescription("Deck View Preferences");
	}

	@Override
	protected void createFieldEditors() {
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
