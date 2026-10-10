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
		if (log == null || !log.isFile())
			return "";
		try {
			return recentLogErrors(new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8),
					System.getProperty("user.home"));
		} catch (IOException | RuntimeException e) {
			return "";
		}
	}

	static String recentLogErrors(String logText, String home) {
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
			if (cur != null && kept < MAX_LINES_PER_ERROR) {
				cur.append(line).append('\n');
				kept++;
			}
		}
		if (cur != null)
			errors.add(cur.toString().trim());
		int from = Math.max(0, errors.size() - MAX_LOG_ERRORS);
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
