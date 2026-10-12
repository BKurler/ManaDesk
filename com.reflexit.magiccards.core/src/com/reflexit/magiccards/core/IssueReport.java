/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *     Rémi Dutil (2026) - the report as an email to the developer: toMailto(),
 *                         toMailLink() (Gmail / Outlook.com picked from the
 *                         user's address, mailServiceFor()), the system line on
 *                         top, mailRoomLeft() for the dialog's limit, and a
 *                         note when the log is attached (setMailAttachment())
 *     Rémi Dutil (2026) - logForAttachment(): the end of the whole log, for the
 *                         file attached to an email or a GitHub issue
 *******************************************************************************/
package com.reflexit.magiccards.core;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * A bug report or feature request for the ManaDesk GitHub repository, opened
 * as a pre-filled "new issue" page: the user reviews it there and submits it
 * with their own GitHub account. Field names match the issue forms in
 * {@code .github/ISSUE_TEMPLATE}.
 */
public class IssueReport {
	public static final String NEW_ISSUE_URL = "https://github.com/BKurler/ManaDesk/issues/new";
	/** Where "Report via Email" sends the report - the developer files the issue. */
	public static final String REPORT_EMAIL = "BKurler-Manadesk@outlook.com";
	/**
	 * Mail apps are started with the whole mailto link on a command line, which
	 * Windows cuts at about 2 KB - a longer report is shortened in the email and
	 * given in full through the clipboard instead.
	 */
	static final int MAX_MAILTO_LENGTH = 1900;
	/**
	 * The description's length limit, in characters: typical text then fits the
	 * email link with room to spare (the link limit is still checked exactly).
	 */
	public static final int MAX_DESCRIPTION_CHARS = 1000;
	/** Browsers and GitHub handle URLs up to about 8 KB; stay below. */
	static final int MAX_URL_LENGTH = 7500;
	/** How many of the most recent log errors to include. */
	static final int MAX_LOG_ERRORS = 3;
	/** Stack lines kept per log error. */
	static final int MAX_LINES_PER_ERROR = 12;

	public enum Kind {
		BUG("bug_report.yml", "Bug report", "bug"), FEATURE("feature_request.yml", "Feature request", "enhancement");

		final String template;
		final String label;
		/** The GitHub label - also set by the issue form itself. */
		final String githubLabel;

		Kind(String template, String label, String githubLabel) {
			this.template = template;
			this.label = label;
			this.githubLabel = githubLabel;
		}

		public String getLabel() {
			return label;
		}
	}

	private Kind kind = Kind.BUG;
	private String title = "";
	private String description = "";
	private String system = "";
	private String logs = "";
	/** The error log file sent along with an email report, or null. */
	private String mailAttachment;

	public IssueReport setKind(Kind kind) {
		this.kind = kind;
		return this;
	}

	public Kind getKind() {
		return kind;
	}

	public IssueReport setTitle(String title) {
		this.title = nz(title).trim();
		return this;
	}

	public IssueReport setDescription(String description) {
		this.description = nz(description).trim();
		return this;
	}

	public IssueReport setSystem(String system) {
		this.system = nz(system).trim();
		return this;
	}

	/** Log excerpt - only used for a bug report. */
	public IssueReport setLogs(String logs) {
		this.logs = nz(logs).trim();
		return this;
	}

	/**
	 * The pre-filled new-issue URL. When it would be too long, the log excerpt
	 * is shortened first (keeping its newest end), then the description.
	 */
	public String toUrl() {
		String l = kind == Kind.BUG ? logs : "";
		String d = description;
		String url = buildUrl(d, l);
		while (url.length() > MAX_URL_LENGTH && !l.isEmpty()) {
			// drop the oldest third, keep the newest end
			l = l.length() > 300 ? "...\n" + l.substring(l.length() / 3) : "";
			url = buildUrl(d, l);
		}
		while (url.length() > MAX_URL_LENGTH && d.length() > 100) {
			d = d.substring(0, d.length() * 3 / 4) + "\n[... shortened - the full text is in the clipboard copy]";
			url = buildUrl(d, l);
		}
		return url;
	}

	private String buildUrl(String desc, String logText) {
		StringBuilder sb = new StringBuilder(NEW_ISSUE_URL);
		sb.append("?template=").append(encode(kind.template));
		// honoured by GitHub only for people with triage access; for everyone
		// else the issue form's own "labels" sets it
		sb.append("&labels=").append(encode(kind.githubLabel));
		if (!title.isEmpty())
			sb.append("&title=").append(encode(title));
		if (!desc.isEmpty())
			sb.append("&description=").append(encode(desc));
		if (!system.isEmpty())
			sb.append("&system=").append(encode(system));
		if (!logText.isEmpty())
			sb.append("&logs=").append(encode(logText));
		// a plain issue's body - used by GitHub only when the issue form is
		// missing (not on the default branch yet, renamed...); ignored otherwise
		sb.append("&body=").append(encode(markdownBody(desc, logText)));
		return sb.toString();
	}

	/** "[ManaDesk bug] title" / "[ManaDesk feature] title" - the email's subject. */
	public String mailSubject() {
		return "[ManaDesk " + (kind == Kind.BUG ? "bug" : "feature") + "] " + (title.isEmpty() ? "(no title)" : title);
	}

	/**
	 * The report as a {@code mailto:} link to {@link #REPORT_EMAIL}, subject and
	 * body filled in, never longer than {@link #MAX_MAILTO_LENGTH}: the compact
	 * system line on top, the user's description (whole - the dialog keeps it
	 * within {@link #mailRoomLeft()}), and a note when the error log is attached
	 * (see {@link #setMailAttachment(String)}). Only a description that is still
	 * too long (should not happen) loses its end.
	 */
	public String toMailto() {
		return mailtoUrl(fittedMailBody());
	}

	/** Where "Report via Email" opens the email. */
	public enum MailService {
		/** The mail program set up on the computer ({@code mailto:} link). */
		APP("your mail app"),
		/** Gmail's compose page, in the browser. */
		GMAIL("Gmail"),
		/** Outlook.com's compose page, in the browser (also Hotmail / Live). */
		OUTLOOK("Outlook.com");

		private final String label;

		MailService(String label) {
			this.label = label;
		}

		public String getLabel() {
			return label;
		}
	}

	/**
	 * The webmail of an address, from its domain: Gmail, Outlook.com (Hotmail,
	 * Live, MSN...), otherwise the mail app.
	 */
	public static MailService mailServiceFor(String email) {
		String e = nz(email).trim().toLowerCase(java.util.Locale.ROOT);
		int at = e.lastIndexOf('@');
		if (at < 0)
			return MailService.APP;
		String domain = e.substring(at + 1);
		if (domain.equals("gmail.com") || domain.equals("googlemail.com"))
			return MailService.GMAIL;
		if (domain.startsWith("outlook.") || domain.startsWith("hotmail.") || domain.startsWith("live.")
				|| domain.equals("msn.com") || domain.equals("windowslive.com"))
			return MailService.OUTLOOK;
		return MailService.APP;
	}

	/**
	 * The report as an email to {@link #REPORT_EMAIL} in {@code service}: a
	 * {@code mailto:} link for the mail app, or the webmail's "new email" page
	 * filled in. Same content and size for all of them (see {@link #toMailto()}).
	 */
	public String toMailLink(MailService service) {
		String body = fittedMailBody();
		switch (service) {
		case GMAIL:
			return "https://mail.google.com/mail/?view=cm&fs=1&to=" + encode(REPORT_EMAIL) + "&su="
					+ encode(mailSubject()) + "&body=" + encode(body);
		case OUTLOOK:
			return "https://outlook.live.com/mail/0/deeplink/compose?to=" + encode(REPORT_EMAIL) + "&subject="
					+ encode(mailSubject()) + "&body=" + encode(body);
		default:
			return mailtoUrl(body);
		}
	}

	/**
	 * The error log goes along with the email as an attached file (an email link
	 * can't carry one): the body only says so. {@code null} for none.
	 */
	public IssueReport setMailAttachment(String fileName) {
		this.mailAttachment = fileName == null || fileName.trim().isEmpty() ? null : fileName.trim();
		return this;
	}

	/** The email body, within {@link #MAX_MAILTO_LENGTH} once in a mailto link. */
	private String fittedMailBody() {
		String body = mailText(description);
		String d = description;
		while (mailtoUrl(body).length() > MAX_MAILTO_LENGTH && !d.isEmpty()) {
			// last resort - the dialog keeps the description within mailRoomLeft()
			d = d.substring(0, d.length() * 3 / 4);
			body = mailText(d + "\n[...]");
		}
		return body;
	}

	/**
	 * How much room the email link has left for more description, in encoded
	 * characters (a letter takes 1, a space 3, an accented letter 6, a line
	 * break 6): negative when the description is too long. The system line and
	 * the "error log attached" line are counted.
	 */
	public int mailRoomLeft() {
		return MAX_MAILTO_LENGTH - mailtoUrl(mailText(description)).length();
	}

	/** What {@code text} takes in the email link (see {@link #mailRoomLeft()}). */
	public static int mailLength(String text) {
		return encode(nz(text).replace("\r\n", "\n").replace("\n", "\r\n")).length();
	}

	/** The email body: the system line, the description, and the attachment note. */
	private String mailText(String desc) {
		StringBuilder sb = new StringBuilder();
		if (!system.isEmpty())
			sb.append(system).append("\n\n");
		sb.append(desc.isEmpty() ? "(no description)" : desc).append("\n");
		if (kind == Kind.BUG && mailAttachment != null)
			sb.append("\nLog: attached (").append(mailAttachment).append(")\n");
		return sb.toString();
	}

	private String mailtoUrl(String body) {
		// CRLF line breaks: what mail clients expect in a mailto body
		return "mailto:" + REPORT_EMAIL + "?subject=" + encode(mailSubject()) + "&body="
				+ encode(body.replace("\r\n", "\n").replace("\n", "\r\n"));
	}

	/** The report as a plain issue body (Markdown), for when there is no form. */
	String markdownBody(String desc, String logText) {
		StringBuilder sb = new StringBuilder();
		sb.append(desc.isEmpty() ? "(no description)" : desc).append("\n");
		if (!system.isEmpty())
			sb.append("\n### System information\n```\n").append(system).append("\n```\n");
		if (!logText.isEmpty())
			sb.append("\n### Recent errors from the log\n```\n").append(logText).append("\n```\n");
		return sb.toString();
	}

	/** The whole report as plain text, for "Copy Report" (email, Ko-fi...). */
	public String toText() {
		StringBuilder sb = new StringBuilder();
		sb.append(kind.label).append(": ").append(title.isEmpty() ? "(no title)" : title).append("\n\n");
		sb.append(description).append("\n");
		if (!system.isEmpty())
			sb.append("\n--- System information ---\n").append(system).append("\n");
		if (kind == Kind.BUG && !logs.isEmpty())
			sb.append("\n--- Recent errors from the log ---\n").append(logs).append("\n");
		sb.append("\nReport it at: ").append(NEW_ISSUE_URL).append("\n");
		return sb.toString();
	}

	static String encode(String s) {
		try {
			// URLEncoder writes spaces as '+'; %20 is unambiguous in a query
			return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
		} catch (UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	// ---------------------------------------------------------------- log

	/**
	 * The last few errors of an Eclipse {@code .log} file: each "!ENTRY" of
	 * severity ERROR (4) with its message and the first lines of its stack
	 * trace. The user's home folder is replaced by "~". Empty when the file is
	 * missing or has no error.
	 */
	public static String recentLogErrors(File log) {
		return recentLogErrors(log, MAX_LOG_ERRORS, MAX_LINES_PER_ERROR);
	}

	/**
	 * The end of the whole log, for the file attached to a report: its last
	 * {@code maxBytes} (starting at a line), the user's home folder replaced by
	 * "~". Empty when the file is missing or empty.
	 */
	public static String logForAttachment(File log, int maxBytes) {
		if (log == null || !log.isFile())
			return "";
		try {
			return logForAttachment(new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8),
					System.getProperty("user.home"), maxBytes);
		} catch (IOException | RuntimeException e) {
			return "";
		}
	}

	static String logForAttachment(String logText, String home, int maxBytes) {
		String out = logText;
		if (out.length() > maxBytes) {
			out = out.substring(out.length() - maxBytes);
			int nl = out.indexOf('\n');
			out = "[older entries cut]\n" + (nl >= 0 ? out.substring(nl + 1) : out);
		}
		if (home != null && home.length() > 3)
			out = out.replace(home, "~");
		return out.trim();
	}

	/** Same, with more room. */
	public static String recentLogErrors(File log, int maxErrors, int maxLinesPerError) {
		if (log == null || !log.isFile())
			return "";
		try {
			return recentLogErrors(new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8),
					System.getProperty("user.home"), maxErrors, maxLinesPerError);
		} catch (IOException | RuntimeException e) {
			return "";
		}
	}

	static String recentLogErrors(String logText, String home) {
		return recentLogErrors(logText, home, MAX_LOG_ERRORS, MAX_LINES_PER_ERROR);
	}

	static String recentLogErrors(String logText, String home, int maxErrors, int maxLinesPerError) {
		List<String> errors = new ArrayList<>();
		String[] lines = logText.split("\r?\n");
		StringBuilder cur = null;
		int kept = 0;
		for (String line : lines) {
			if (line.startsWith("!ENTRY ")) {
				if (cur != null)
					errors.add(cur.toString().trim());
				cur = isError(line) ? new StringBuilder() : null;
				kept = 0;
			} else if (line.startsWith("!SESSION")) {
				if (cur != null)
					errors.add(cur.toString().trim());
				cur = null;
			}
			if (cur != null && kept < maxLinesPerError) {
				cur.append(line).append('\n');
				kept++;
			}
		}
		if (cur != null)
			errors.add(cur.toString().trim());
		int from = Math.max(0, errors.size() - maxErrors);
		StringBuilder sb = new StringBuilder();
		for (int i = from; i < errors.size(); i++) {
			if (sb.length() > 0)
				sb.append("\n\n");
			sb.append(errors.get(i));
		}
		String out = sb.toString();
		if (home != null && home.length() > 3)
			out = out.replace(home, "~");
		return out;
	}

	/** "!ENTRY plugin 4 code date" - severity 4 is ERROR. */
	private static boolean isError(String entryLine) {
		String[] parts = entryLine.split(" ");
		return parts.length > 2 && "4".equals(parts[2]);
	}

	private static String nz(String s) {
		return s == null ? "" : s;
	}
}
