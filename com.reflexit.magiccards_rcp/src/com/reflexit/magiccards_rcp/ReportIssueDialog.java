/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *     Rémi Dutil (2026) - "Report via Email" (without a GitHub account): the
 *                         report as an email to the developer, in the mail app
 *                         or - from the optional, remembered "Your email" - in
 *                         Gmail / Outlook.com; asks first when no mail app is
 *                         set up (Windows). "Copy Report" became "Copy".
 *     Rémi Dutil (2026) - description limited to 1000 characters ("N characters
 *                         left") so the report fits an email link; the system
 *                         line always included (no checkbox); the dialog stays
 *                         open after the email / GitHub page opens
 *     Rémi Dutil (2026) - the log as a file to attach (ManaDesk-log.txt, last
 *                         1 MB, home folder hidden), for GitHub and email: put
 *                         in the clipboard, its folder opened BEFORE the email
 *                         (so Windows' "choose an app" keeps the focus), and
 *                         shown in the dialog to drag, show in folder or copy
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
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceAdapter;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.osgi.framework.Bundle;

import com.reflexit.magiccards.core.IssueReport;

/**
 * Help &gt; Report a Bug or Request a Feature...: the user describes the
 * problem or idea, then "Open on GitHub" opens the ManaDesk repository's
 * new-issue page with everything filled in - reviewed and submitted there with
 * the user's own (free) GitHub account. Without a GitHub account, "Report via
 * Email" opens an email to the developer ({@link IssueReport#REPORT_EMAIL}) in
 * the mail app, or in Gmail / Outlook.com for such an address in "Your email".
 * For a bug, the log is prepared as a file to attach (see writeLogFile()).
 * "Copy" puts the report on the clipboard.
 */
public class ReportIssueDialog extends TitleAreaDialog {
	private static final int COPY_ID = IDialogConstants.CLIENT_ID + 1;
	private static final int EMAIL_ID = IDialogConstants.CLIENT_ID + 2;
	private Button bugRadio;
	private Button featureRadio;
	private Text titleText;
	private Text descText;
	private Button includeLog;
	/** "N characters left" - the description must fit in an email link. */
	private Label roomLabel;
	/** "Show ManaDesk-log.txt in its folder - copy it again" once the file is ready. */
	/** "Log file: [icon] ManaDesk-log.txt  show in folder - copy" - for a bug with the log. */
	private Composite logRow;
	private CLabel logFileLabel;
	private Image logFileIcon;
	private File preparedLog;
	/** The user's own address: picks the webmail, and lets the developer reply. */
	private Text emailText;
	private static final String EMAIL_SETTING = "ReportIssueDialog.email";

	public ReportIssueDialog(Shell parent) {
		super(parent);
		setShellStyle(getShellStyle() | SWT.RESIZE);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		getShell().setText("Report a Bug or Request a Feature");
		setTitle("Report a Bug or Request a Feature");
		setMessage("Describe it below, then send it: \"Open on GitHub\" (a free GitHub account is needed) or"
				+ " \"Report via Email\" (a new email opens, filled in).");
		Composite area = (Composite) super.createDialogArea(parent);
		Composite comp = new Composite(area, SWT.NONE);
		comp.setLayoutData(new GridData(GridData.FILL_BOTH));
		comp.setLayout(new GridLayout(2, false));

		new Label(comp, SWT.NONE).setText("Your email:");
		emailText = new Text(comp, SWT.BORDER | SWT.SINGLE);
		emailText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		emailText.setMessage("optional - for \"Report via Email\", so the developer can reply");
		emailText.setToolTipText("With a Gmail or Outlook.com / Hotmail address, \"Report via Email\" opens a new"
				+ " email in that webmail, in your browser. Otherwise it opens your mail app.");
		String saved = Activator.getDefault().getDialogSettings().get(EMAIL_SETTING);
		if (saved != null)
			emailText.setText(saved);
		emailText.addModifyListener(e -> updateEmailButton());

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
		new Label(comp, SWT.NONE); // under "Description:"
		roomLabel = new Label(comp, SWT.NONE);
		roomLabel.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));
		roomLabel.setToolTipText("The report must fit in an email link, with the system information line.");
		titleText.setTextLimit(100);
		descText.setTextLimit(IssueReport.MAX_DESCRIPTION_CHARS);
		titleText.addVerifyListener(mailLimit);
		descText.addVerifyListener(mailLimit);
		titleText.addModifyListener(e -> updateRoomLabel());
		descText.addModifyListener(e -> updateRoomLabel());

		includeLog = new Button(comp, SWT.CHECK);
		includeLog.setText("Include the ManaDesk log (prepared as a file for you to attach)");
		includeLog.setSelection(true);
		includeLog.setLayoutData(span2());
		includeLog.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				updateRoomLabel(); // the "log attached" line
				updateLogRow();
			}
		});
		createLogRow(comp);

		Label note = new Label(comp, SWT.WRAP);
		note.setText("ManaDesk, Java and system versions are always included (nothing personal). No GitHub"
				+ " account? \"Report via Email\" sends the report to the developer (" + IssueReport.REPORT_EMAIL
				+ "). \"Copy\" puts it in the clipboard, to paste it anywhere.");
		GridData ngd = span2();
		ngd.widthHint = 520;
		note.setLayoutData(ngd);
		syncKind();
		updateRoomLabel();
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
		updateRoomLabel();
		updateLogRow();
	}

	/**
	 * The log file, ready to attach: drag its icon into the email or the GitHub
	 * issue (like dragging it from Explorer), or show it in its folder, or copy
	 * it (then Ctrl+V). Part of the dialog from the start, so it is always laid
	 * out; shown only for a bug with the log checked.
	 */
	private void createLogRow(Composite comp) {
		logRow = new Composite(comp, SWT.NONE);
		GridLayout gl = new GridLayout(3, false);
		gl.marginHeight = 0;
		gl.marginWidth = 0;
		logRow.setLayout(gl);
		logRow.setLayoutData(span2());
		new Label(logRow, SWT.NONE).setText("Log file:");
		logFileLabel = new CLabel(logRow, SWT.NONE);
		logFileLabel.setText(LOG_FILE_NAME);
		logFileLabel.setToolTipText("Drag this file into your email or the GitHub issue to attach it");
		Program txt = Program.findProgram(".txt");
		ImageData icon = txt == null ? null : txt.getImageData();
		if (icon != null) {
			logFileIcon = new Image(comp.getDisplay(), icon);
			logFileLabel.setImage(logFileIcon);
		}
		DragSource drag = new DragSource(logFileLabel, DND.DROP_COPY);
		drag.setTransfer(new Transfer[] { FileTransfer.getInstance() });
		drag.addDragListener(new DragSourceAdapter() {
			@Override
			public void dragStart(DragSourceEvent event) {
				event.doit = preparedLogFile(true) != null;
			}

			@Override
			public void dragSetData(DragSourceEvent event) {
				File f = preparedLogFile(false);
				if (f != null && FileTransfer.getInstance().isSupportedType(event.dataType))
					event.data = new String[] { f.getAbsolutePath() };
			}
		});
		Link links = new Link(logRow, SWT.NONE);
		links.setText("(drag it into the email or the issue)   <a href=\"folder\">Show in folder</a>  -  "
				+ "<a href=\"copy\">Copy</a> (then Ctrl+V)");
		links.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				File f = preparedLogFile(true);
				if (f == null) {
					setMessage("There is no ManaDesk log to attach.");
					return;
				}
				if ("folder".equals(e.text))
					showInFolder(f);
				else
					copyFileToClipboard(f);
			}
		});
	}

	/** Shows the log file row only for a bug with the log checked. */
	private void updateLogRow() {
		if (logRow == null || logRow.isDisposed())
			return;
		boolean show = bugRadio.getSelection() && includeLog.getSelection();
		if (logRow.getVisible() == show)
			return;
		logRow.setVisible(show);
		((GridData) logRow.getLayoutData()).exclude = !show;
		logRow.getParent().layout(true, true);
	}

	/** The prepared log file - written now if it isn't yet, or {@code fresh}; null when there is no log. */
	private File preparedLogFile(boolean fresh) {
		if (preparedLog == null || fresh || !preparedLog.isFile())
			preparedLog = writeLogFile();
		return preparedLog;
	}

	@Override
	public boolean close() {
		if (logFileIcon != null)
			logFileIcon.dispose();
		return super.close();
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.OK_ID, "Open on GitHub", true);
		createButton(parent, EMAIL_ID, "Report via Email", false);
		updateEmailButton();
		createButton(parent, COPY_ID, "Copy", false);
		createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
	}

	@Override
	protected void buttonPressed(int buttonId) {
		if (buttonId == COPY_ID) {
			copyToClipboard(buildReport().toText());
			setMessage("The report is in the clipboard - paste it in an email or a message to the developer.");
			return;
		}
		if (buttonId == EMAIL_ID) {
			sendByEmail();
			return;
		}
		super.buttonPressed(buttonId);
	}

	/** Opens the mail app with the report addressed to the developer, then closes. */
	private void sendByEmail() {
		if (titleText.getText().trim().isEmpty()) {
			setErrorMessage("Enter a short title.");
			return;
		}
		IssueReport report = buildReport();
		setErrorMessage(null);
		File attachment = null;
		if (bugRadio.getSelection() && includeLog.getSelection()) {
			attachment = preparedLogFile(true);
			if (attachment != null)
				report.setMailAttachment(attachment.getName());
		}
		String email = emailText.getText().trim();
		Activator.getDefault().getDialogSettings().put(EMAIL_SETTING, email);
		IssueReport.MailService service = IssueReport.mailServiceFor(email);
		String link = report.toMailLink(service);
		if (service == IssueReport.MailService.APP && !confirmMailApp())
			return;
		if (attachment != null) {
			// the folder first, the email after: Windows' "choose an app" window
			// closes as soon as another window takes the focus
			File f = attachment;
			copyFileToClipboard(f);
			showInFolder(f);
			setMessage("Opening the email...");
			getShell().getDisplay().timerExec(FOLDER_DELAY_MS, () -> {
				if (getShell() != null && !getShell().isDisposed())
					openEmail(report, service, link, f);
			});
		} else {
			openEmail(report, service, link, null);
		}
	}

	/**
	 * When no usable mail app handles email links (Windows: a browser, or
	 * nothing / an app no longer installed), asks before opening one - a browser
	 * just comes to the front and does nothing. True to go on with the mail app.
	 */
	private boolean confirmMailApp() {
		MailHandler h = mailHandler();
		if (h == MailHandler.MAIL_APP || h == MailHandler.UNKNOWN)
			return true;
		String why = h == MailHandler.BROWSER ? "Email links open in a web browser on this computer, which can't write them."
				: "No mail app seems to be set up on this computer for email links.";
		MessageDialog d = new MessageDialog(getShell(), "Report via Email", null,
				why + "\n\nTo write the email in your webmail, type your Gmail or Outlook.com address in \"Your"
						+ " email\". Or use \"Copy\" and paste the report in an email to " + IssueReport.REPORT_EMAIL
						+ ".\n\nYou can also open the email link anyway, to pick a mail app.",
				MessageDialog.QUESTION, new String[] { "Type my email address", "Open anyway", "Cancel" }, 0);
		int choice = d.open();
		if (choice == 0) {
			emailText.setFocus();
			setMessage("Type your Gmail or Outlook.com address, then click \"Report via Email\" again.");
		}
		return choice == 1;
	}

	private enum MailHandler {
		MAIL_APP, BROWSER, NONE, UNKNOWN
	}

	/** Who handles email links - Windows only (registry), UNKNOWN elsewhere or when unsure. */
	private static MailHandler mailHandler() {
		if (!System.getProperty("os.name", "").toLowerCase().startsWith("windows"))
			return MailHandler.UNKNOWN;
		String progId = regValue(
				"HKCU\\Software\\Microsoft\\Windows\\Shell\\Associations\\UrlAssociations\\mailto\\UserChoice",
				"ProgId");
		if (progId == null || progId.isEmpty())
			return MailHandler.NONE;
		String id = progId.toLowerCase();
		for (String browser : new String[] { "msedge", "chrome", "firefox", "brave", "opera", "vivaldi", "ie.http" })
			if (id.contains(browser))
				return MailHandler.BROWSER;
		// a choice left behind by an app that is no longer installed
		if (!regKeyExists("HKCR\\" + progId))
			return MailHandler.NONE;
		return MailHandler.MAIL_APP;
	}

	/** A registry value through "reg query", or null. */
	private static String regValue(String key, String name) {
		String out = reg("query", key, "/v", name);
		if (out == null)
			return null;
		for (String line : out.split("\r?\n")) {
			String t = line.trim();
			if (t.startsWith(name + " ")) {
				int i = t.indexOf("REG_SZ");
				return i < 0 ? null : t.substring(i + "REG_SZ".length()).trim();
			}
		}
		return null;
	}

	private static boolean regKeyExists(String key) {
		return reg("query", key) != null;
	}

	/** Runs reg.exe; its output, or null when it fails (or takes more than 3 s). */
	private static String reg(String... args) {
		try {
			java.util.List<String> cmd = new java.util.ArrayList<>();
			cmd.add("reg");
			java.util.Collections.addAll(cmd, args);
			Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
			byte[] out = readAll(p.getInputStream());
			if (!p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
				p.destroy();
				return null;
			}
			return p.exitValue() == 0 ? new String(out, java.nio.charset.Charset.defaultCharset()) : null;
		} catch (Exception e) {
			return null;
		}
	}

	private static byte[] readAll(java.io.InputStream in) throws java.io.IOException {
		java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
		byte[] buf = new byte[4096];
		for (int n; (n = in.read(buf)) > 0;)
			bo.write(buf, 0, n);
		return bo.toByteArray();
	}

	/** Pause between opening the log file's folder and the email / GitHub page. */
	private static final int FOLDER_DELAY_MS = 1500;

	private void openEmail(IssueReport report, IssueReport.MailService service, String link, File attachment) {
		boolean opened = service == IssueReport.MailService.APP ? openMail(link) : openInBrowser(link);
		if (opened) {
			// stays open: if the email is cancelled, nothing typed here is lost
			String where = service == IssueReport.MailService.APP ? "Your mail app opened"
					: service.getLabel() + " opened in your browser";
			if (attachment != null) {
				setMessage(where + " with the report. Attach the log file: drag " + attachment.getName()
						+ " from the folder that opened (or from this window), or paste it in the email (Ctrl+V)."
						+ " Then send the email and close this window.");
			} else {
				setMessage(where + " with the report - send it from there, then close this window.");
			}
		} else {
			copyToClipboard(report.toText());
			MessageDialog.openInformation(getShell(), "Report via Email",
					"Your mail app could not be opened. The report is in the clipboard - paste it in an email to "
							+ IssueReport.REPORT_EMAIL + ".");
		}
	}

	/**
	 * Keeps the description (and the title, which is the email's subject) within
	 * what an email link can carry: a typed or pasted text that would not fit is
	 * cut to what still fits.
	 */
	private final VerifyListener mailLimit = new VerifyListener() {
		@Override
		public void verifyText(VerifyEvent e) {
			if (e.text.isEmpty())
				return; // deleting always fits
			Text t = (Text) e.widget;
			String before = t.getText().substring(0, e.start);
			String after = t.getText().substring(e.end);
			if (roomLeft(t, before + e.text + after) >= 0)
				return;
			// the longest start of the inserted text that still fits
			int lo = 0, hi = e.text.length();
			while (lo < hi) {
				int mid = (lo + hi + 1) / 2;
				if (roomLeft(t, before + e.text.substring(0, mid) + after) >= 0)
					lo = mid;
				else
					hi = mid - 1;
			}
			e.text = e.text.substring(0, lo);
			if (lo == 0)
				e.doit = false;
			getShell().getDisplay().beep();
		}
	};

	/** {@link IssueReport#mailRoomLeft()} with {@code text} in field {@code t}. */
	private int roomLeft(Text t, String text) {
		boolean bug = bugRadio.getSelection();
		IssueReport r = new IssueReport().setKind(bug ? IssueReport.Kind.BUG : IssueReport.Kind.FEATURE)
				.setTitle(t == titleText ? text : titleText.getText())
				.setDescription(t == descText ? text : descText.getText()).setSystem(systemInfo())
				.setMailAttachment(bug && includeLog != null && includeLog.getSelection() ? LOG_FILE_NAME : null);
		return r.mailRoomLeft();
	}

	/**
	 * "N characters left" under the description: a plain count against
	 * {@link IssueReport#MAX_DESCRIPTION_CHARS}. Only unusual text (many accents
	 * or line breaks, which take more room in an email link) can reach the
	 * email limit first - then the count shows that room instead.
	 */
	private void updateRoomLabel() {
		if (roomLabel == null || roomLabel.isDisposed())
			return;
		int room = Math.max(0, roomLeft(descText, descText.getText()));
		int chars = Math.max(0, Math.min(IssueReport.MAX_DESCRIPTION_CHARS - descText.getText().length(), room));
		roomLabel.setText(chars + (chars == 1 ? " character" : " characters") + " left");
		roomLabel.setForeground(chars < 50 ? getShell().getDisplay().getSystemColor(SWT.COLOR_RED) : null);
		roomLabel.getParent().layout();
	}

	@Override
	protected void okPressed() {
		if (titleText.getText().trim().isEmpty()) {
			setErrorMessage("Enter a short title.");
			return;
		}
		IssueReport report = buildReport();
		File attachment = null;
		if (bugRadio.getSelection() && includeLog.getSelection()) {
			attachment = preparedLogFile(true); // dropped into the form's "Log file" box
		}
		if (attachment == null) {
			if (!openInBrowser(report.toUrl()))
				browserFailed(report);
			super.okPressed();
			return;
		}
		// the folder first, the browser after (on top of the folder); the dialog
		// stays open with what to do with the file
		File f = attachment;
		copyFileToClipboard(f);
		showInFolder(f);
		setErrorMessage(null);
		setMessage("Opening the report on GitHub...");
		getShell().getDisplay().timerExec(FOLDER_DELAY_MS, () -> {
			if (getShell() == null || getShell().isDisposed())
				return;
			if (!openInBrowser(report.toUrl())) {
				browserFailed(report);
				return;
			}
			setMessage("The report opened on GitHub. Attach the log file in the \"Log file\" box: drag "
					+ f.getName() + " from the folder that opened (or from this window), or paste it (Ctrl+V)."
					+ " Then submit the issue and close this window.");
		});
	}

	private void browserFailed(IssueReport report) {
		copyToClipboard(report.toText());
		MessageDialog.openInformation(getShell(), "Report a Bug or Request a Feature",
				"The browser could not be opened. The report is in the clipboard - you can paste it at "
						+ IssueReport.NEW_ISSUE_URL);
	}

	private IssueReport buildReport() {
		IssueReport r = new IssueReport();
		r.setKind(bugRadio.getSelection() ? IssueReport.Kind.BUG : IssueReport.Kind.FEATURE);
		r.setTitle(titleText.getText());
		r.setDescription(descText.getText());
		r.setSystem(systemInfo());
		if (bugRadio.getSelection() && includeLog.getSelection())
			r.setLogs(IssueReport.recentLogErrors(logFile()));
		return r;
	}

	/** ManaDesk, Java and OS versions on one compact line - nothing personal. */
	static String systemInfo() {
		return "ManaDesk " + manaDeskVersion() + " | Java " + System.getProperty("java.version") + " | "
				+ System.getProperty("os.name") + " " + System.getProperty("os.version") + " "
				+ System.getProperty("os.arch");
	}

	/** The log file a report asks to attach. */
	private static final String LOG_FILE_NAME = "ManaDesk-log.txt";
	/** How much of the log goes in the file - the recent sessions. */
	private static final int LOG_FILE_MAX_BYTES = 1000000;

	/**
	 * Writes the end of the ManaDesk log (home folder hidden) to
	 * {@link #LOG_FILE_NAME} in a "ManaDesk" folder of the temp directory, the
	 * system line on top; {@code null} when there is no log or it can't be
	 * written.
	 */
	private static File writeLogFile() {
		String log = IssueReport.logForAttachment(logFile(), LOG_FILE_MAX_BYTES);
		if (log.isEmpty())
			return null;
		try {
			File dir = new File(System.getProperty("java.io.tmpdir"), "ManaDesk");
			dir.mkdirs();
			// only this report's file in the folder that opens - no older one to
			// attach by mistake (the folder is used by this dialog only)
			File[] old = dir.listFiles();
			if (old != null)
				for (File o : old)
					if (o.isFile() && !o.delete())
						o.deleteOnExit();
			File f = new File(dir, LOG_FILE_NAME);
			String text = systemInfo() + "\n\n" + log + "\n";
			java.nio.file.Files.write(f.toPath(), text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			return f;
		} catch (Exception e) {
			Activator.log(e);
			return null;
		}
	}

	/** Puts the file itself on the clipboard: Ctrl+V in an email attaches it. */
	private void copyFileToClipboard(File f) {
		Clipboard cb = new Clipboard(getShell().getDisplay());
		try {
			cb.setContents(new Object[] { new String[] { f.getAbsolutePath() } },
					new Transfer[] { FileTransfer.getInstance() });
		} catch (RuntimeException e) {
			Activator.log(e);
		} finally {
			cb.dispose();
		}
	}

	/** Opens the file's folder, with the file selected where the system allows it. */
	private static void showInFolder(File f) {
		try {
			if (System.getProperty("os.name", "").toLowerCase().startsWith("windows")) {
				new ProcessBuilder("explorer.exe", "/select," + f.getAbsolutePath()).start();
				return;
			}
		} catch (Exception e) {
			Activator.log(e);
		}
		Program.launch(f.getParentFile().getAbsolutePath());
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
		// Linux: xdg-open keeps the query string, and java.awt.Desktop would start
		// AWT's own GTK integration inside SWT's (Gdk-CRITICAL, possible hangs)
		if ("gtk".equals(SWT.getPlatform()) && Program.launch(url))
			return true;
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

	/** "Report via Email" says where it will open: the mail app, Gmail or Outlook.com. */
	private void updateEmailButton() {
		Button b = getButton(EMAIL_ID);
		if (b == null || b.isDisposed() || emailText == null)
			return;
		IssueReport.MailService service = IssueReport.mailServiceFor(emailText.getText());
		b.setToolTipText("Opens a new email to the developer, filled in, in " + service.getLabel());
	}

	/** Opens the user's mail app on a {@code mailto:} link. */
	private static boolean openMail(String mailto) {
		if ("gtk".equals(SWT.getPlatform()) && Program.launch(mailto)) // see openInBrowser
			return true;
		try {
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) {
				Desktop.getDesktop().mail(new URI(mailto));
				return true;
			}
		} catch (Exception e) {
			Activator.log(e);
		}
		return Program.launch(mailto);
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
