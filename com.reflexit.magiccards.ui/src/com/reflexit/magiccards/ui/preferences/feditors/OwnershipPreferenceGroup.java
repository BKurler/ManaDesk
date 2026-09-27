/*******************************************************************************
 * Copyright (c) 2008 Alena Laskavaia.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Alena Laskavaia - initial API and implementation
 *
 *     Rémi Dutil (2026) - created for ManaDesk: Ownership filter group,
 *                         replacing the radio-button "Ownership" field
 *                         formerly in the now-retired UserFieldsPreference-
 *                         Group - same compact titled-Group-with-checkboxes
 *                         look as ProxyPreferenceGroup, sitting between it
 *                         and Finish in BasicFilterPreferencePage's second
 *                         row (see that class' own header)
 *******************************************************************************/
package com.reflexit.magiccards.ui.preferences.feditors;

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Group;

import com.reflexit.magiccards.core.model.FilterField;

public class OwnershipPreferenceGroup extends MFieldEditorPreferencePage {
	private Group group;
	private final Collection<String> ids = new ArrayList<String>(1);

	@Override
	protected void createFieldEditors() {
		this.group = new Group(getFieldEditorParent(), SWT.NONE);
		this.group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		this.group.setText("Ownership");
		this.group.setFont(getFieldEditorParent().getFont());
		String id = FilterField.OWNERSHIP.getPrefConstant();
		getPreferenceStore().setDefault(id, "");
		addField(new OwnershipFieldEditor(id, this.group));
		this.ids.add(id);
	}

	@Override
	public Collection<String> getIds() {
		return this.ids;
	}

	@Override
	protected void adjustGridLayout() {
		GridLayout layout = (GridLayout) this.group.getLayout();
		layout.marginHeight = 5;
		layout.marginWidth = 5;
		super.adjustGridLayout();
	}
}
