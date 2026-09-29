/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: Name + URL editor for a
 *                         single "Browse Website..." favorite, used by
 *                         WebFavoritesListEditor's New.../Edit... buttons.
 *                         Modeled on EditTextDialog's shape.
 *******************************************************************************/
package com.reflexit.magiccards.ui.dialogs;

import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.reflexit.magiccards.ui.web.WebFavorite;

public class EditWebFavoriteDialog extends TitleAreaDialog {
	private Text nameText;
	private Text urlText;
	private WebFavorite favorite;

	public EditWebFavoriteDialog(Shell parentShell, WebFavorite initial) {
		super(parentShell);
		setShellStyle(getShellStyle() | SWT.RESIZE);
		this.favorite = initial;
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		getShell().setText(favorite == null ? "Add Favorite" : "Edit Favorite");
		setMessage("Give this website a name and its address.");
		Composite area = (Composite) super.createDialogArea(parent);
		Composite fields = new Composite(area, SWT.NONE);
		fields.setLayout(GridLayoutFactory.swtDefaults().numColumns(2).create());
		fields.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).create());

		new Label(fields, SWT.NONE).setText("Name:");
		nameText = new Text(fields, SWT.BORDER);
		nameText.setLayoutData(GridDataFactory.fillDefaults().hint(350, SWT.DEFAULT).grab(true, false).create());

		new Label(fields, SWT.NONE).setText("URL:");
		urlText = new Text(fields, SWT.BORDER);
		urlText.setLayoutData(GridDataFactory.fillDefaults().hint(350, SWT.DEFAULT).grab(true, false).create());

		if (favorite != null) {
			nameText.setText(favorite.getName());
			urlText.setText(favorite.getUrl());
		}
		return area;
	}

	@Override
	protected void okPressed() {
		favorite = new WebFavorite(nameText.getText().trim(), urlText.getText().trim());
		super.okPressed();
	}

	public WebFavorite getFavorite() {
		return favorite;
	}
}
