/*
 * Contributors:
 *     Rémi Dutil (2026) - Printings view column preferences
 *     Rémi Dutil (2026) - extends CardViewPreferencePage: Restore Defaults also resets Group By / sort
 */
package com.reflexit.magiccards.ui.preferences;

import org.eclipse.ui.IWorkbench;

import com.reflexit.magiccards.ui.preferences.feditors.ColumnFieldEditor;
import com.reflexit.magiccards.ui.views.columns.PrintingsColumnCollection;
import com.reflexit.magiccards.ui.views.printings.PrintingsView;

public class PrintingsViewPreferencePage extends CardViewPreferencePage {
	public static final String PPID = PrintingsView.ID;

	public PrintingsViewPreferencePage() {
		super(GRID);
		setPreferenceStore(PreferenceInitializer.getLocalStore(PrintingsView.ID));
		setDescription("Printings View Preferences");
	}

	@Override
	protected void createFieldEditors() {
		addField(new ColumnFieldEditor(PreferenceConstants.LOCAL_COLUMNS, "Visible Columns and Order",
				getFieldEditorParent(), new PrintingsColumnCollection(PrintingsView.ID)));
	}

	@Override
	public void init(IWorkbench workbench) {
	}
}
