/*
 * Contributors:
 *     Rémi Dutil (2026) - "Count Proxies" / "Count Finishes Separately" on this
 *                         page too (they live in the global store), so Restore
 *                         Defaults resets them along with the columns
 *     Rémi Dutil (2026) - extends CardViewPreferencePage: Restore Defaults also resets Group By / sort
 */
package com.reflexit.magiccards.ui.preferences;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.ui.IWorkbench;

import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.preferences.feditors.ColumnFieldEditor;
import com.reflexit.magiccards.ui.views.collector.CollectorColumnCollection;
import com.reflexit.magiccards.ui.views.columns.ColumnCollection;

public class CollectorViewPreferencePage extends CardViewPreferencePage {
	public static final String PPID = CollectorViewPreferencePage.class.getName();
	private BooleanFieldEditor countProxies;
	private BooleanFieldEditor countFinishes;

	public CollectorViewPreferencePage() {
		super(GRID);
		setPreferenceStore(PreferenceInitializer.getCollectorStore());
		setDescription("Collector View Preferences");
	}

	@Override
	protected void createFieldEditors() {
		ColumnCollection columnCollection = new CollectorColumnCollection();
		addField(new BooleanFieldEditor(PreferenceConstants.LOCAL_SHOW_QUICKFILTER, "Show quick filter",
				getFieldEditorParent()));
		countProxies = new BooleanFieldEditor(PreferenceConstants.COLLECTOR_COUNT_PROXIES,
				"Count proxies (P) in completion, counts and value", getFieldEditorParent());
		addField(countProxies);
		countFinishes = new BooleanFieldEditor(PreferenceConstants.COLLECTOR_COUNT_FINISHES_SEPARATELY,
				"Count finishes separately (F)", getFieldEditorParent());
		addField(countFinishes);
		addField(new ColumnFieldEditor(PreferenceConstants.LOCAL_COLUMNS, "Visible Columns and Order",
				getFieldEditorParent(),
				columnCollection));
	}

	@Override
	protected void initialize() {
		super.initialize(); // gives every editor this page's (Collector view) store
		// ...but these two are global preferences, shared with the view's P / F buttons
		for (BooleanFieldEditor e : new BooleanFieldEditor[] { countProxies, countFinishes }) {
			e.setPreferenceStore(MagicUIActivator.getDefault().getPreferenceStore());
			e.load();
		}
	}

	@Override
	public void init(IWorkbench workbench) {
		// nothing
	}
}
