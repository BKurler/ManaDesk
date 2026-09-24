

/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - createFieldEditors(): build the "Visible Columns
 *                         and Order" list from MagicDbColumnCollection, not a
 *                         plain MagicColumnCollection - the plain one offered
 *                         every per-copy column (Count, Location, Ownership,
 *                         Condition, Proxy, Comment, User Price, Special, For
 *                         Trade, Sideboard, Extra, Date) as checkable even
 *                         though the actual Scryfall Database view (via
 *                         SplitViewer) never shows them at all
 */

package com.reflexit.magiccards.ui.preferences;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.reflexit.magiccards.ui.preferences.feditors.ColumnFieldEditor;
import com.reflexit.magiccards.ui.views.columns.ColumnCollection;
import com.reflexit.magiccards.ui.views.columns.MagicDbColumnCollection;

public class MagicDbViewPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {
	public static final String PPID = MagicDbViewPreferencePage.class.getName();

	public MagicDbViewPreferencePage() {
		super(GRID);
		setPreferenceStore(PreferenceInitializer.getMdbStore());
		setDescription("Scryfall Database View Preferences");
	}

	@Override
	protected void createFieldEditors() {
		ColumnCollection columnCollection = new MagicDbColumnCollection(getClass().getName());
		addField(new BooleanFieldEditor(PreferenceConstants.LOCAL_SHOW_QUICKFILTER, "Show quick filter",
				getFieldEditorParent()));
		addField(new ColumnFieldEditor(PreferenceConstants.LOCAL_COLUMNS, "Visible Columns and Order",
				getFieldEditorParent(), columnCollection));
	}

	@Override
	public void init(IWorkbench workbench) {
		// TODO Auto-generated method stub
	}
}
