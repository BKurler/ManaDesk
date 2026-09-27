/*
 * Contributors:
 *     Rémi Dutil (2026) - removed the "Community Rating" numeric filter
 *                         (community rating is not a concept this app tracks
 *                         anymore)
 *     Rémi Dutil (2026) - switched every field here to a Min/Max range editor
 *                         instead of a single =/&lt;=/&gt;= comparison
 *     Rémi Dutil (2026) - optional userFilter constructor flag: when true,
 *                         User Price and Count (the former, separate
 *                         UserPriceCountPreferenceGroup) are added right
 *                         after Collector's Number, in this SAME field grid -
 *                         being two more rows of the one shared
 *                         FieldEditorPreferencePage grid is what actually
 *                         aligns their operator/bound columns with Power/
 *                         Toughness/etc. above; two separate pages (the old
 *                         design) each compute their own column widths from
 *                         their own labels, so "User Price"/"Count" - shorter
 *                         than "Collector's Number" - never lined up
 */
package com.reflexit.magiccards.ui.preferences.feditors;

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;

import com.reflexit.magiccards.core.model.FilterField;

public class NumbericalPreferenceGroup extends MFieldEditorPreferencePage {
	private Collection<String> ids = new ArrayList<String>(7);
	private final boolean userFilter;

	public NumbericalPreferenceGroup() {
		this(false);
	}

	/** @param userFilter whether to also add User Price and Count - only
	 *            meaningful for owned copies, so off when filtering the
	 *            Scryfall database itself */
	public NumbericalPreferenceGroup(boolean userFilter) {
		this.userFilter = userFilter;
	}

	@Override
	public Collection<String> getIds() {
		return ids;
	}

	// private Group group;
	@Override
	protected void createFieldEditors() {
		// this.group = new Group(getFieldEditorParent(), SWT.NONE);
		// this.group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		// this.group.setText("Subtype");
		// Composite parent = this.group;
		// addCheckBox("Any", parent);
		String id = FilterField.POWER.getPrefConstant();
		getPreferenceStore().setDefault(id, "");
		addField(new RangeComparisonFieldEditor(id, "Power", getFieldEditorParent()));
		ids.add(id);
		id = FilterField.TOUGHNESS.getPrefConstant();
		getPreferenceStore().setDefault(id, "");
		addField(new RangeComparisonFieldEditor(id, "Toughness", getFieldEditorParent()));
		ids.add(id);
		id = FilterField.CCC.getPrefConstant();
		getPreferenceStore().setDefault(id, "");
		addField(new RangeComparisonFieldEditor(id, "Converted CC", getFieldEditorParent()));
		ids.add(id);
		id = FilterField.DBPRICE.getPrefConstant();
		getPreferenceStore().setDefault(id, "");
		addField(new RangeComparisonFieldEditor(id, "Online Price", getFieldEditorParent()));
		ids.add(id);
		id = FilterField.COLLNUM.getPrefConstant();
		getPreferenceStore().setDefault(id, "");
		addField(new RangeComparisonFieldEditor(id, "Collector's Number", getFieldEditorParent()));
		ids.add(id);
		if (this.userFilter) {
			id = FilterField.PRICE.getPrefConstant();
			getPreferenceStore().setDefault(id, "");
			addField(new RangeComparisonFieldEditor(id, "User Price", getFieldEditorParent()));
			ids.add(id);
			id = FilterField.COUNT.getPrefConstant();
			getPreferenceStore().setDefault(id, "");
			addField(new RangeComparisonFieldEditor(id, "Count", getFieldEditorParent()));
			ids.add(id);
		}
	}

	@Override
	protected void adjustGridLayout() {
		GridLayout layout = (GridLayout) ((Composite) this.getControl()).getLayout();
		layout.marginHeight = 5;
		layout.marginWidth = 5;
		super.adjustGridLayout();
	}
}
