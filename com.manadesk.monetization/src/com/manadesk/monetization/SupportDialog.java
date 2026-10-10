/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil
 * All rights reserved.
 *
 * This file is NOT open-source.
 * Permission is granted to use this file ONLY as part of the ManaDesk application.
 * Modification, redistribution, or reuse of this file or its contents is prohibited.
 * You may NOT replace affiliate identifiers, ad URLs, or donation links.
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk: the "Support ManaDesk" dialog
 *                  (donation / sponsorship links).
 *     Rémi Dutil (2026) - links opened through BrowserLauncher.
 *******************************************************************************/
package com.manadesk.monetization;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;

import com.manadesk.monetization.MonetizationConfig.SupportLink;

/**
 * Lists the support destinations of the current {@link MonetizationConfig}.
 * Links open in the user's own web browser (never an embedded one).
 */
public class SupportDialog extends Dialog {
	private final MonetizationConfig config;

	public SupportDialog(Shell parentShell) {
		super(parentShell);
		this.config = MonetizationManager.getInstance().getConfig();
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText(config.getSupportTitle());
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite area = (Composite) super.createDialogArea(parent);
		area.setLayout(GridLayoutFactory.swtDefaults().margins(12, 12).spacing(8, 10).create());
		if (!config.getSupportMessage().isEmpty()) {
			Label msg = new Label(area, SWT.WRAP);
			msg.setText(config.getSupportMessage());
			GridDataFactory.fillDefaults().grab(true, false).hint(420, SWT.DEFAULT).applyTo(msg);
		}
		if (config.getSupportLinks().isEmpty()) {
			Label none = new Label(area, SWT.WRAP);
			none.setText("Support links are not available right now. Thank you for thinking of it!");
			GridDataFactory.fillDefaults().grab(true, false).hint(420, SWT.DEFAULT).applyTo(none);
		}
		for (final SupportLink l : config.getSupportLinks()) {
			Link link = new Link(area, SWT.NONE);
			String text = "<a>" + escape(l.getLabel()) + "</a>";
			if (!l.getDescription().isEmpty())
				text += " - " + escape(l.getDescription());
			link.setText(text);
			link.setToolTipText(l.getUrl());
			link.addListener(SWT.Selection, e -> open(l.getUrl()));
			GridDataFactory.fillDefaults().grab(true, false).applyTo(link);
		}
		Label hint = new Label(area, SWT.WRAP);
		hint.setText("Links open in your web browser.");
		hint.setForeground(hint.getDisplay().getSystemColor(SWT.COLOR_WIDGET_DARK_SHADOW));
		return area;
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.CLOSE_ID, IDialogConstants.CLOSE_LABEL, true);
	}

	@Override
	protected void buttonPressed(int buttonId) {
		if (buttonId == IDialogConstants.CLOSE_ID)
			close();
		else
			super.buttonPressed(buttonId);
	}

	static void open(String url) {
		if (url == null || !MonetizationConfig.isWebUrl(url))
			return;
		if (!BrowserLauncher.open(url))
			MonetizationPlugin.warn("Could not open " + url + " in the web browser", null);
	}

	/** SWT Link treats '&' as a mnemonic marker and '<' as markup. */
	private static String escape(String s) {
		return s.replace("&", "&&").replace("<", "&lt;");
	}
}
