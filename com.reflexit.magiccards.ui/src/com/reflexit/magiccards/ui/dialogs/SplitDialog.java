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
 *     Rémi Dutil (2026) - "Split & move" mode: make the moved / kept counts explicit
 */
package com.reflexit.magiccards.ui.dialogs;

import org.eclipse.jface.dialogs.TrayDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.swt.widgets.Shell;

/**
 * @author Alena
 * 
 */
public class SplitDialog extends TrayDialog {
	private int split;
	private int max;
	/** When {@code true} the dialog talks in "moved / kept" terms and
	 * {@link #okPressed()} always resolves to an absolute count of cards to move. */
	private boolean moveMode;
	private Button oneToNButton;
	private Button evenButton;
	private Button customButton;
	private Scale scale;

	/**
	 * @param parentShell
	 * @param max
	 */
	protected SplitDialog(Shell parentShell, int max) {
		this(parentShell, max, false);
	}

	protected SplitDialog(Shell parentShell, int max, boolean moveMode) {
		super(parentShell);
		this.max = max;
		this.moveMode = moveMode;
	}

	private String ratio(int selection) {
		if (this.moveMode)
			return "Move " + selection + " card(s), keep " + (this.max - selection) + " here";
		return "Keep " + selection + " card(s) here, new pile of " + (this.max - selection);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		getShell().setText(this.moveMode ? "Split & Move" : "Split");
		Composite area = (Composite) super.createDialogArea(parent);
		Composite buttons = new Composite(area, SWT.NONE);
		buttons.setLayout(new GridLayout(2, false));
		GridDataFactory buttonGridData = GridDataFactory.fillDefaults().span(2, 1);
		Label title = new Label(buttons, SWT.WRAP);
		title.setText(this.moveMode
				? "Pile of " + this.max + " cards - choose how many to move; the rest stays here."
				: "Pile of " + this.max + " cards - choose how many stay; the rest goes to a new pile.");
		buttonGridData.applyTo(title);
		this.oneToNButton = new Button(buttons, SWT.RADIO);
		this.oneToNButton.setText(this.moveMode ? "Move 1" : "Keep 1");
		buttonGridData.applyTo(this.oneToNButton);
		this.evenButton = new Button(buttons, SWT.RADIO);
		this.evenButton.setText(this.moveMode ? "Move half" : "Even split");
		buttonGridData.applyTo(this.evenButton);
		this.customButton = new Button(buttons, SWT.RADIO);
		this.customButton.setText("Custom");
		final Label from = new Label(buttons, SWT.NONE);
		this.scale = new Scale(buttons, SWT.HORIZONTAL);
		buttonGridData.applyTo(this.scale);
		this.scale.setMinimum(1);
		this.scale.setMaximum(this.max - 1);
		// scale.add
		this.scale.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				int selection = SplitDialog.this.scale.getSelection();
				from.setText(ratio(selection));
				SplitDialog.this.customButton.setSelection(true);
				SplitDialog.this.evenButton.setSelection(false);
				SplitDialog.this.oneToNButton.setSelection(false);
			}
		});
		this.oneToNButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (SplitDialog.this.oneToNButton.getSelection()) {
					int selection = 1;
					SplitDialog.this.scale.setSelection(selection);
					from.setText(ratio(selection));
				}
			}
		});
		this.evenButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (SplitDialog.this.evenButton.getSelection()) {
					int selection = SplitDialog.this.max / 2;
					SplitDialog.this.scale.setSelection(selection);
					from.setText(ratio(selection));
				}
			}
		});
		// Start on a sensible, visible default so both counts are obvious.
		int selection = this.max / 2;
		this.evenButton.setSelection(true);
		this.scale.setSelection(selection);
		from.setText(ratio(selection));
		return area;
	}

	@Override
	protected void okPressed() {
		if (this.moveMode) {
			if (this.oneToNButton.getSelection())
				this.split = 1;
			else if (this.evenButton.getSelection())
				this.split = this.max / 2;
			else
				this.split = this.scale.getSelection();
		} else if (this.customButton.getSelection()) {
			this.split = this.scale.getSelection();
		} else if (this.oneToNButton.getSelection()) {
			this.split = 1;
		} else if (this.evenButton.getSelection()) {
			this.split = -2;
		}
		super.okPressed();
	}

	/**
	 * @param shell
	 * @return
	 */
	public static int askSplitType(Shell shell, int max) {
		SplitDialog dialog = new SplitDialog(shell, max);
		if (dialog.open() == dialog.OK) {
			return dialog.getSplit();
		}
		return 0;
	}

	/**
	 * Ask how many cards of a pile of {@code max} to move elsewhere.
	 *
	 * @return the number of cards to move (1..max-1), or 0 if cancelled
	 */
	public static int askMoveCount(Shell shell, int max) {
		SplitDialog dialog = new SplitDialog(shell, max, true);
		if (dialog.open() == dialog.OK) {
			int move = dialog.getSplit();
			if (move > 0 && move < max)
				return move;
		}
		return 0;
	}

	/**
	 * @return
	 */
	private int getSplit() {
		return this.split;
	}
}
