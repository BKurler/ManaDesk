/*
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: card-condition filter checkbox group
 */
package com.reflexit.magiccards.ui.preferences.feditors;

import java.util.Collection;
import java.util.Iterator;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;

import com.reflexit.magiccards.core.model.CardConditions;
import com.reflexit.magiccards.core.model.ISearchableProperty;

public class CardConditionPreferenceGroup extends MFieldEditorPreferencePage {
	private Group group;

	@Override
	protected void createFieldEditors() {
		this.group = new Group(getFieldEditorParent(), SWT.NONE);
		this.group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		this.group.setText("Condition");
		this.group.setFont(getFieldEditorParent().getFont());
		Composite parent = this.group;
		CardConditions conditions = CardConditions.getInstance();
		for (Iterator iterator = conditions.getIds().iterator(); iterator.hasNext();) {
			String id = (String) iterator.next();
			addCheckBox(id, conditions.getNameById(id), parent);
		}
	}

	public ISearchableProperty getSearchablePropery() {
		return CardConditions.getInstance();
	}

	@Override
	public Collection<String> getIds() {
		return getSearchablePropery().getIds();
	}

	private FieldEditor addCheckBox(String id, String name, Composite parent) {
		BooleanFieldEditor editor = new BooleanFieldEditor(id, name, parent);
		addField(editor);
		return editor;
	}

	@Override
	protected void adjustGridLayout() {
		GridLayout layout = (GridLayout) this.group.getLayout();
		layout.marginHeight = 5;
		layout.marginWidth = 5;
		super.adjustGridLayout();
	}
}
