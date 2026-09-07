/*******************************************************************************
 * Copyright (c) 2008 Alena Laskavaia.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Alena Laskavaia - initial API and implementation
 *******************************************************************************/

/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.ui.dialogs;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.reflexit.magiccards.core.MagicException;
import com.reflexit.magiccards.core.model.storage.IStorageInfo;
import com.reflexit.magiccards.ui.utils.StatusDots;

/**
 * Dialog to edit properties of a deck/collection
 */
public class EditDeckPropertiesDialog extends TitleAreaDialog {
	private IStorageInfo info;
	private Combo type;
	private Button virtual;
	private Button unsorted;
	private Text text;
	private Button protection;

	public EditDeckPropertiesDialog(Shell shell, IStorageInfo info) {
		super(shell);
		if (info == null)
			throw new NullPointerException();
		this.info = info;
		setShellStyle(getShellStyle() | SWT.RESIZE);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		getShell().setText("Edit...");
		// setTitleImage(MagicUIActivator.getDefault().getImage("icons/Book-1-icon.gif"));
		setTitle("Edit Properties");
		setMessage("You can modify deck/collection properties here. Press OK to save.");
		Composite area = (Composite) super.createDialogArea(parent);
		Composite comp = new Composite(area, SWT.NONE);
		comp.setLayoutData(new GridData(GridData.FILL_BOTH));
		GridLayout layout = new GridLayout(4, false);
		comp.setLayout(layout);
		{
			Label label = new Label(comp, SWT.NONE);
			label.setText("Type:");
			type = new Combo(comp, SWT.READ_ONLY | SWT.DROP_DOWN);
			type.add(IStorageInfo.DECK_TYPE);
			type.add(IStorageInfo.COLLECTION_TYPE);
			type.setText(IStorageInfo.DECK_TYPE.equals(info.getType()) ? IStorageInfo.DECK_TYPE
					: IStorageInfo.COLLECTION_TYPE);
			GridData gd = new GridData(GridData.FILL_HORIZONTAL);
			// take the rest of the row so the checkboxes below aren't
			// crowded onto the same line - easier to read as its own row
			gd.horizontalSpan = ((GridLayout) comp.getLayout()).numColumns - 1;
			type.setLayoutData(gd);
		}
		virtual = StatusDots.check(comp, StatusDots.VIRTUAL, "Virtual");
		virtual.setSelection(info.isVirtual());
		protection = StatusDots.check(comp, StatusDots.READ_ONLY, "Read Only");
		protection.setSelection(info.isReadOnly());
		unsorted = StatusDots.check(comp, StatusDots.UNSORTED, "Unsorted (collections only)");
		unsorted.setSelection(info.isUnsorted());
		StatusDots.exclusive(virtual, unsorted);
		// Unsorted (manual card order) only makes sense for a collection
		type.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				syncUnsortedForType();
			}
		});
		syncUnsortedForType();
		createTextArea(comp);
		return comp;
	}

	private void syncUnsortedForType() {
		boolean deck = IStorageInfo.DECK_TYPE.equals(type.getText());
		if (deck)
			unsorted.setSelection(false);
		unsorted.setEnabled(!deck);
	}

	private void createTextArea(Composite area) {
		Group group = new Group(area, SWT.NONE);
		group.setText("Description");
		GridData gd = new GridData(GridData.FILL_BOTH);
		gd.horizontalSpan = ((GridLayout) area.getLayout()).numColumns;
		group.setLayoutData(gd);
		group.setLayout(new GridLayout());
		text = new Text(group, SWT.WRAP | SWT.BORDER);
		text.setLayoutData(GridDataFactory.fillDefaults().hint(600, 200).create());
		text.setText(info.getComment() == null ? "" : info.getComment());
	}

	@Override
	protected void okPressed() {
		try {
			save();
			super.okPressed();
		} catch (MagicException e) {
			MessageDialog.openError(getParentShell(), "Error", "Cannot save: " + e.getMessage());
		}
	}

	private void save() {
		boolean newRO = protection.getSelection();
		boolean oldRO = info.isReadOnly();

		// Case 1: disabling read-only → must disable first
		if (oldRO && !newRO) {
			info.setReadOnly(false);
		}

		// Apply all editable properties
		info.setComment(text.getText());
		info.setVirtual(virtual.getSelection());
		info.setUnsorted(unsorted.getSelection());
		info.setType(type.getText());

		// Case 2: enabling read-only → must enable last
		if (!oldRO && newRO) {
			info.setReadOnly(true);
		}
	}

}
