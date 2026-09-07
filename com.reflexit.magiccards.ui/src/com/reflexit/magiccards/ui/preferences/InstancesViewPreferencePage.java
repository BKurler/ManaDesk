/*
 * Contributors:
 *     Rémi Dutil (2026) - Instances view column preferences
 */
package com.reflexit.magiccards.ui.preferences;

import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.reflexit.magiccards.ui.preferences.feditors.ColumnFieldEditor;
import com.reflexit.magiccards.ui.views.columns.MagicColumnCollection;
import com.reflexit.magiccards.ui.views.instances.InstancesView;

public class InstancesViewPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {
	public static final String PPID = InstancesView.ID;

	public InstancesViewPreferencePage() {
		super(GRID);
		setPreferenceStore(PreferenceInitializer.getLocalStore(InstancesView.ID));
		setDescription("Instances View Preferences");
	}

	@Override
	protected void createFieldEditors() {
		addField(new ColumnFieldEditor(PreferenceConstants.LOCAL_COLUMNS, "Visible Columns and Order",
				getFieldEditorParent(), new MagicColumnCollection(InstancesView.ID)));
	}

	@Override
	public void init(IWorkbench workbench) {
	}
}
