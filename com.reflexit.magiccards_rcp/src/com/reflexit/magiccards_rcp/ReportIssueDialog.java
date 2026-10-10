/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *******************************************************************************/
package com.reflexit.magiccards_rcp;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;

import org.eclipse.core.runtime.IProduct;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.osgi.framework.Bundle;

import com.reflexit.magiccards.core.IssueReport;

/**
 * Help &gt; Report a Bug or Request a Feature...: the user describes the
 * problem or idea, then "Open on GitHub" opens the ManaDesk repository's
 * new-issue page with everything filled in - reviewed and submitted there with
 * the user's own (free) GitHub account. "Copy Report" puts the same text on
 * the clipboard, for someone without a GitHub account.
 */
public class ReportIssueDialog extends TitleAreaDialog {
	private static final int COPY_ID = IDialogConstants.CLIENT_ID + 1;
	private Button bugRadio;
	private Button featureRadio;
	private Text titleText;
	private Text descText;
	private Button includeSystem;
	private Button includeLog;

	public ReportIssueDialog(Shell parent) {
		super(parent);
		setShellStyle(getShellStyle() | SWT.RESIZE);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		getShell().setText("Report a Bug or Request a Feature");
		setTitle("Report a Bug or Request a Feature");
		setMessage("Describe it below. \"Open on GitHub\" opens the report in your browser, filled in - you review it"
				+ " and submit it there (a free GitHub account is needed).");
		Composite area = (Composite) super.createDialogArea(parent);
		Composite comp = new Composite(area, SWT.NONE);
		comp.setLayoutData(new GridData(GridData.FILL_BOTH));
		comp.setLayout(new GridLayout(2, false));

		new Label(comp, SWT.NONE).setText("Type:");
		Composite kinds = new Composite(comp, SWT.NONE);
		GridLayout kl = new GridLayout(2, false);
		kl.marginWidth = 0;
		kl.marginHeight = 0;
		kinds.setLayout(kl);
		bugRadio = new Button(kinds, SWT.RADIO);
		bugRadio.setText("Bug report");
		bugRadio.setSelection(true);
		featureRadio = new Button(kinds, SWT.RADIO);
		featureRadio.setText("Feature request");
		SelectionAdapter sync = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				syncKind();
			}
		};
		bugRadio.addSelectionListener(sync);
		featureRadio.addSelectionListener(sync);

		new Label(comp, SWT.NONE).setText("Title:");
		titleText = new Text(comp, SWT.BORDER | SWT.SINGLE);
		titleText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		Label dl = new Label(comp, SWT.NONE);
		dl.setText("Description:");
		dl.setLayoutData(new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false));
		descText = new Text(comp, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
		GridData dgd = new GridData(GridData.FILL_BOTH);
		dgd.widthHint = 520;
		dgd.heightHint = 200;
		descText.setLayoutData(dgd);

		includeSystem = new Button(comp, SWT.CHECK);
		includeSystem.setText("Include system information (ManaDesk, Java and operating system versions)");
		includeSystem.setSelection(true);
		includeSystem.setLayoutData(span2());
		includeLog = new Button(comp, SWT.CHECK);
		includeLog.setText("Include the most recent errors from the log");
		includeLog.setSelection(true);
		includeLog.setLayoutData(span2());

		Label note = new Label(comp, SWT.WRAP);
		note.setText("No GitHub account? Use \"Copy Report\" and send the text to the developer.");
		GridData ngd = span2();
		ngd.widthHint = 520;
		note.setLayoutData(ngd);
		syncKind();
		return area;
	}

	private static GridData span2() {
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan = 2;
		return gd;
	}

	private void syncKind() {
		boolean bug = bugRadio.getSelection();
		descText.setMessage(bug ? "What did you do, what did you expect, and what happened instead?"
				: "What would you like ManaDesk to do, and what would it help you with?");
		includeLog.setEnabled(bug); // log errors only matter for a bug
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.OK_ID, "Open on GitHub", true);
		createButton(parent, COPY_ID, "Copy Report", false);
		createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
	}

	@Override
	protected void buttonPressed(int buttonId) {
		if (buttonId == COPY_ID) {
			copyToClipboard(buildReport().toText());
			setMessage("The report is in the clipboard - paste it in an email or a message to the developer.");
			return;
		}
		super.buttonPressed(buttonId);
	}

	@Override
	protected void okPressed() {
		if (titleText.getText().trim().isEmpty()) {
			setErrorMessage("Enter a short title.");
			return;
		}
		IssueReport report = buildReport();
		if (!openInBrowser(report.toUrl())) {
			copyToClipboard(report.toText());
			MessageDialog.openInformation(getShell(), "Report a Bug or Request a Feature",
					"The browser could not be opened. The report is in the clipboard - you can paste it at "
							+ IssueReport.NEW_ISSUE_URL);
		}
		super.okPressed();
	}

	private IssueReport buildReport() {
		IssueReport r = new IssueReport();
		r.setKind(bugRadio.getSelection() ? IssueReport.Kind.BUG : IssueReport.Kind.FEATURE);
		r.setTitle(titleText.getText());
		r.setDescription(descText.getText());
		if (includeSystem.getSelection())
			r.setSystem(systemInfo());
		if (bugRadio.getSelection() && includeLog.getSelection())
			r.setLogs(IssueReport.recentLogErrors(logFile()));
		return r;
	}

	/** ManaDesk, Java and OS versions - nothing personal. */
	static String systemInfo() {
		StringBuilder sb = new StringBuilder();
		sb.append("ManaDesk: ").append(manaDeskVersion()).append('\n');
		sb.append("Java: ").append(System.getProperty("java.version")).append(" (")
				.append(System.getProperty("java.vendor")).append(")\n");
		sb.append("OS: ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version"))
				.append(" (").append(System.getProperty("os.arch")).append(")");
		return sb.toString();
	}

	private static String manaDeskVersion() {
		try {
			IProduct product = Platform.getProduct();
			Bundle b = product != null ? product.getDefiningBundle() : null;
			if (b == null)
				b = Platform.getBundle("com.reflexit.magiccards.core");
			return b == null ? "unknown" : b.getVersion().toString();
		} catch (RuntimeException e) {
			return "unknown";
		}
	}

	private static File logFile() {
		try {
			return Platform.getLogFileLocation().toFile();
		} catch (RuntimeException e) {
			return null;
		}
	}

	/**
	 * Opens {@code url} in the user's browser with its query string intact
	 * ({@link Program#launch} can drop everything after the first '&amp;' on
	 * Windows, so it is only a fallback).
	 */
	private static boolean openInBrowser(String url) {
		try {
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
				Desktop.getDesktop().browse(new URI(url));
				return true;
			}
		} catch (Exception e) {
			Activator.log(e);
		}
		return url.indexOf('&') < 0 && Program.launch(url);
	}

	private void copyToClipboard(String text) {
		Clipboard cb = new Clipboard(getShell().getDisplay());
		try {
			cb.setContents(new Object[] { text }, new Transfer[] { TextTransfer.getInstance() });
		} finally {
			cb.dispose();
		}
	}
}
