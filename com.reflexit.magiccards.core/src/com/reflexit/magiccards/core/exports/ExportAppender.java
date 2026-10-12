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
package com.reflexit.magiccards.core.exports;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "Append to the existing file" in the export wizard: adds a new export to a
 * file that already exists, only when both are the same kind of file.
 * <ul>
 * <li>Printable sheets (Print Proxies, Print Sideboard List): the existing file
 * must be a ManaDesk printable sheet; its cards and the new ones are laid out
 * again together, so the new ones fill the empty slots of its last page.</li>
 * <li>Text formats with a header row: the existing file must start with the
 * same header; the new rows are added without repeating it.</li>
 * <li>Text formats without a header row: the file must have the format's
 * extension.</li>
 * <li>Other (XML) formats can't be appended to.</li>
 * </ul>
 */
public final class ExportAppender {
	/** Why the new export can't be appended to the file. */
	public static class AppendException extends Exception {
		private static final long serialVersionUID = 1L;

		public AppendException(String message) {
			super(message);
		}
	}

	/** What is being appended. */
	public static class Format {
		final String label;
		final String extension;
		final boolean printable;
		final boolean xml;
		final boolean header;

		/**
		 * @param label
		 *            the format's name, for the messages
		 * @param extension
		 *            its file extension, without the dot
		 * @param printable
		 *            a printable sheet (see {@link PrintSheet})
		 * @param xml
		 *            an XML / HTML format
		 * @param header
		 *            the new export starts with a header row
		 */
		public Format(String label, String extension, boolean printable, boolean xml, boolean header) {
			this.label = label;
			this.extension = extension == null ? "" : extension;
			this.printable = printable;
			this.xml = xml;
			this.header = header;
		}
	}

	private ExportAppender() {
	}

	/**
	 * The file's new content: {@code existing} with {@code added} appended.
	 *
	 * @throws AppendException
	 *             when they are not the same kind of file
	 */
	public static String append(String existing, String added, Format format, String fileName) throws AppendException {
		if (existing == null || existing.trim().isEmpty())
			return added;
		boolean existingSheet = PrintSheet.isPrintSheet(existing);
		if (format.printable) {
			if (!existingSheet)
				throw new AppendException("The existing file is not a ManaDesk printable sheet (Print Proxies or Print"
						+ " Sideboard List), so " + format.label + " can't be appended to it.");
			List<String> slots = new ArrayList<>(PrintSheet.slots(existing));
			slots.addAll(PrintSheet.slots(added));
			return PrintSheet.toHtml("ManaDesk printable sheet", slots);
		}
		if (existingSheet)
			throw new AppendException("The existing file is a ManaDesk printable sheet (Print Proxies or Print Sideboard"
					+ " List), so " + format.label + " can't be appended to it.");
		if (format.xml)
			throw new AppendException(format.label + " can't be appended to an existing file.");
		if (format.header) {
			String existingHeader = firstLine(existing);
			String addedHeader = firstLine(added);
			if (!existingHeader.equals(addedHeader))
				throw new AppendException("The existing file doesn't start with the " + format.label + " header row:"
						+ " it was written in another format, or with other columns.");
			return withNewLine(existing) + afterFirstLine(added);
		}
		String ext = format.extension.startsWith(".") ? format.extension.substring(1) : format.extension;
		if (!ext.isEmpty() && fileName != null && !fileName.toLowerCase(Locale.ROOT).endsWith("." + ext.toLowerCase(Locale.ROOT)))
			throw new AppendException("The existing file is not a ." + ext + " file, so " + format.label
					+ " can't be appended to it.");
		return withNewLine(existing) + added;
	}

	/** Cards already in a printable sheet (0 for any other file) - for the page count. */
	public static int existingCards(String existing) {
		return PrintSheet.isPrintSheet(existing) ? PrintSheet.slots(existing).size() : 0;
	}

	private static String firstLine(String s) {
		int nl = s.indexOf('\n');
		return (nl < 0 ? s : s.substring(0, nl)).trim();
	}

	private static String afterFirstLine(String s) {
		int nl = s.indexOf('\n');
		return nl < 0 ? "" : s.substring(nl + 1);
	}

	private static String withNewLine(String s) {
		return s.endsWith("\n") ? s : s + System.lineSeparator();
	}

	static String toUtf8(ByteArrayOutputStream out) {
		try {
			return out.toString("UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	static PrintStream utf8(ByteArrayOutputStream out) {
		try {
			return new PrintStream(out, true, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}
}
