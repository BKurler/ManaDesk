/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *     Rémi Dutil (2026) - pages as 3 x 3 tables instead of a CSS grid, and
 *                         IE=edge: the export wizard's preview (SWT's embedded
 *                         Internet Explorer on Windows) knows no CSS grid and
 *                         showed one card per line
 *******************************************************************************/
package com.reflexit.magiccards.core.exports;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The printable sheet shared by Print Proxies and Print Sideboard List: pages of
 * {@link #PER_PAGE} card-sized slots (3 x 3), each {@link #SLOT_WIDTH_MM} x
 * {@link #SLOT_HEIGHT_MM} mm - a Magic card's proportions, a little smaller so a
 * printed proxy slides into a sleeve - printed at 100%.
 * <p>
 * Every slot is kept between {@code <!--slot-->} / {@code <!--/slot-->} marks
 * and the document carries a generator meta tag, so an existing file can be
 * recognized ({@link #isPrintSheet(String)}), its slots read back
 * ({@link #slots(String)}) and the whole document written again with new slots
 * added ({@link #write(PrintStream, String, List)}) - appending fills the empty
 * slots of the last page first.
 */
public final class PrintSheet {
	/** Cards per printed page. */
	public static final int PER_PAGE = 9;
	/** Cards per row. */
	static final int COLUMNS = 3;
	/** Slot width: at most 60 mm, smaller than a card (63 mm) to fit a sleeve. */
	public static final double SLOT_WIDTH_MM = 60;
	/** Slot height: a card's 63:88 ratio at {@link #SLOT_WIDTH_MM}. */
	public static final double SLOT_HEIGHT_MM = 83.8;
	/** Identifies a ManaDesk printable file. */
	static final String GENERATOR = "ManaDesk printable sheet";
	private static final String SLOT_START = "<!--slot-->";
	private static final String SLOT_END = "<!--/slot-->";
	private static final Pattern SLOT = Pattern.compile(Pattern.quote(SLOT_START) + "(.*?)" + Pattern.quote(SLOT_END),
			Pattern.DOTALL);

	private PrintSheet() {
	}

	/** Whether {@code html} is a printable sheet written by ManaDesk. */
	public static boolean isPrintSheet(String html) {
		return html != null && html.contains("<meta name=\"generator\" content=\"" + GENERATOR + "\"");
	}

	/** The slots' contents, in order, of a printable sheet. */
	public static List<String> slots(String html) {
		List<String> res = new ArrayList<>();
		if (html == null)
			return res;
		Matcher m = SLOT.matcher(html);
		while (m.find())
			res.add(m.group(1).trim());
		return res;
	}

	/** Pages needed for {@code cards} slots. */
	public static int pages(int cards) {
		return (cards + PER_PAGE - 1) / PER_PAGE;
	}

	/** The whole document: pages of {@link #PER_PAGE} slots. */
	public static void write(PrintStream out, String title, List<String> slots) {
		out.println("<!DOCTYPE html>");
		out.println("<html><head><meta charset=\"UTF-8\">");
		// the wizard's preview is SWT's embedded Internet Explorer on Windows:
		// without this it renders as IE7 (no transform, no mm-exact layout)
		out.println("<meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">");
		out.println("<meta name=\"generator\" content=\"" + GENERATOR + "\">");
		out.println("<title>" + esc(title) + "</title>");
		out.println("<style>");
		out.println(css());
		out.println("</style></head><body>");
		// a table per page (3 x 3): it lays out the same in every browser engine
		// and prints with clean page breaks
		for (int p = 0; p < pages(slots.size()); p++) {
			out.println("<table class=\"page\">");
			int end = Math.min(slots.size(), (p + 1) * PER_PAGE);
			for (int row = p * PER_PAGE; row < end; row += COLUMNS) {
				out.print("<tr>");
				for (int i = row; i < Math.min(end, row + COLUMNS); i++)
					out.print("<td><div class=\"slot\">" + SLOT_START + slots.get(i) + SLOT_END + "</div></td>");
				out.println("</tr>");
			}
			out.println("</table>");
		}
		out.println("</body></html>");
	}

	/** The document as a string (see {@link #write(PrintStream, String, List)}). */
	public static String toHtml(String title, List<String> slots) {
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		PrintStream ps = ExportAppender.utf8(out);
		write(ps, title, slots);
		ps.flush();
		return ExportAppender.toUtf8(out);
	}

	/** One stylesheet for every kind of slot, so they can share a page. */
	static String css() {
		String w = mm(SLOT_WIDTH_MM);
		String h = mm(SLOT_HEIGHT_MM);
		return "  @page { margin: 8mm; }\n" //
				+ "  * { box-sizing: border-box; }\n" //
				+ "  html, body { margin: 0; padding: 0; }\n" //
				+ "  body { -webkit-print-color-adjust: exact; print-color-adjust: exact; }\n" //
				+ "  table.page { border-collapse: collapse; border-spacing: 0; page-break-after: always;"
				+ " break-after: page; page-break-inside: avoid; }\n" //
				+ "  table.page:last-child { page-break-after: auto; break-after: auto; }\n" //
				+ "  table.page td { padding: 0; vertical-align: top; width: " + w + "; height: " + h + "; }\n" //
				+ "  .slot { width: " + w + "; height: " + h + "; overflow: hidden; position: relative; }\n" //
				// a proxy: the card image fills its slot
				+ "  .slot img.proxy { display: block; width: " + w + "; height: " + h + "; }\n" //
				// a sideboard list: a card turned on its side, rotated upright into the slot
				+ "  .sb { position: absolute; left: 0; top: " + h + "; width: " + h + "; height: " + w + ";"
				+ " -ms-transform: rotate(-90deg); -ms-transform-origin: top left;"
				+ " transform: rotate(-90deg); transform-origin: top left; padding: 1.5mm;"
				+ " border: 0.2mm solid #999; overflow: hidden;"
				+ " font: 3.9pt/1.15 'Arial Narrow', 'Segoe UI', Arial, sans-serif; color: #000; }\n" //
				+ "  .sb .title { font-size: 4.6pt; font-weight: 700; margin: 0 0 0.8mm; padding-bottom: 0.4mm;"
				+ " border-bottom: 0.2mm solid #000; }\n" //
				+ "  .sb .title small { font-weight: 400; }\n" //
				+ "  .sb table { width: 100%; border-collapse: collapse; table-layout: fixed; }\n" //
				+ "  .sb th, .sb td { text-align: left; vertical-align: top; padding: 0.15mm 0.6mm;"
				+ " overflow-wrap: break-word; }\n" //
				+ "  .sb th { font-weight: 700; text-transform: uppercase; letter-spacing: 0.1pt;"
				+ " border-bottom: 0.15mm solid #000; white-space: nowrap; }\n" //
				+ "  .sb tbody tr:nth-child(even) { background: #eee; }\n" //
				+ "  .sb .qty { width: 3.5mm; text-align: right; font-weight: 700; }\n" //
				+ "  .sb .set { width: 8mm; }\n" //
				+ "  .sb .special { width: 8mm; }\n" //
				+ "  .sb .comment { width: 10mm; }\n" //
				+ "  .sb .empty { color: #666; font-style: italic; padding: 2mm 0; }";
	}

	private static String mm(double v) {
		return (v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v)) + "mm";
	}

	static String esc(String s) {
		if (s == null)
			return "";
		StringBuilder sb = new StringBuilder(s.length() + 8);
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
			case '&':
				sb.append("&amp;");
				break;
			case '<':
				sb.append("&lt;");
				break;
			case '>':
				sb.append("&gt;");
				break;
			case '"':
				sb.append("&quot;");
				break;
			default:
				sb.append(c);
			}
		}
		return sb.toString();
	}
}
