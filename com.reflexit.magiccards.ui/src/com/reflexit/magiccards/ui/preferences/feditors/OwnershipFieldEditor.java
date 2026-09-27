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
 *     Rémi Dutil (2026) - created for ManaDesk: "Own"/"Virtual" checkbox
 *                         pair, replacing the old 3-way RadioGroupFieldEditor
 *                         (Show all/Show only own/Show only virtual) with the
 *                         same two-checkbox concept the Proxy filter group
 *                         already uses (Genuine/Proxy). Unlike Proxy - a real
 *                         ISearchableProperty checkbox group with one
 *                         preference key per checkbox - this stores both
 *                         checkboxes under the SAME single preference key
 *                         ("" = show all, "true" = own only, "false" =
 *                         virtual only), because QuickFilterControl's own
 *                         toolbar "O"/"V" toggle already reads and writes
 *                         that exact key and encoding - reusing it keeps the
 *                         toolbar toggle and this filter page in sync
 *                         instead of introducing a second, disconnected
 *                         on/off state for the same concept.
 *******************************************************************************/
package com.reflexit.magiccards.ui.preferences.feditors;

import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;

public class OwnershipFieldEditor extends FieldEditor {
	private Composite container;
	private Button ownButton;
	private Button virtualButton;

	public OwnershipFieldEditor(String name, Composite parent) {
		init(name, "");
		createControl(parent);
	}

	@Override
	protected void adjustForNumColumns(int numColumns) {
		((GridData) this.container.getLayoutData()).horizontalSpan = numColumns;
	}

	@Override
	protected void doFillIntoGrid(Composite parent, int numColumns) {
		this.container = new Composite(parent, SWT.NONE);
		// stacked (Own above Virtual), same as Proxy's own Genuine/Proxy
		// checkboxes - not side by side
		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		this.container.setLayout(layout);
		GridData gd = new GridData();
		gd.horizontalSpan = numColumns;
		this.container.setLayoutData(gd);
		this.container.setFont(parent.getFont());
		SelectionAdapter listener = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				valueChanged();
			}
		};
		this.ownButton = new Button(this.container, SWT.CHECK);
		this.ownButton.setText("Own");
		this.ownButton.setFont(this.container.getFont());
		this.ownButton.addSelectionListener(listener);
		this.virtualButton = new Button(this.container, SWT.CHECK);
		this.virtualButton.setText("Virtual");
		this.virtualButton.setFont(this.container.getFont());
		this.virtualButton.addSelectionListener(listener);
	}

	private void valueChanged() {
		setPresentsDefaultValue(false);
		fireValueChanged(VALUE, null, encode());
	}

	/** Same encoding QuickFilterControl#updateOwnershipFilter() uses: both or
	 *  neither checked reads as "show all", same as the earlier radio group's
	 *  own default entry. */
	private String encode() {
		boolean own = this.ownButton.getSelection();
		boolean virtual = this.virtualButton.getSelection();
		if (own == virtual)
			return "";
		return own ? "true" : "false";
	}

	private void applyValue(String value) {
		this.ownButton.setSelection("true".equals(value));
		this.virtualButton.setSelection("false".equals(value));
	}

	@Override
	protected void doLoad() {
		if (this.ownButton != null)
			applyValue(getPreferenceStore().getString(getPreferenceName()));
	}

	@Override
	protected void doLoadDefault() {
		if (this.ownButton != null)
			applyValue(getPreferenceStore().getDefaultString(getPreferenceName()));
	}

	@Override
	protected void doStore() {
		getPreferenceStore().setValue(getPreferenceName(), encode());
	}

	@Override
	public int getNumberOfControls() {
		return 1;
	}
}
