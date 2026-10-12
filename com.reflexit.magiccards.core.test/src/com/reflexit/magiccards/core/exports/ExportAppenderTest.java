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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

public class ExportAppenderTest extends TestCase {
	private static final ExportAppender.Format PROXIES = new ExportAppender.Format("Print Proxies", "html", true, true,
			false);
	private static final ExportAppender.Format CSV = new ExportAppender.Format("ManaDesk CSV", "csv", false, false,
			true);
	private static final ExportAppender.Format CSV_NO_HEADER = new ExportAppender.Format("ManaDesk CSV", "csv",
			false, false, false);

	private static String sheet(String... slots) {
		return PrintSheet.toHtml("t", Arrays.asList(slots));
	}

	public void testSheetPagesOfNine() {
		List<String> slots = new ArrayList<>();
		for (int i = 0; i < 10; i++)
			slots.add("<img class=\"proxy\" src=\"c" + i + "\">");
		String html = PrintSheet.toHtml("t", slots);
		assertTrue(PrintSheet.isPrintSheet(html));
		assertEquals(slots, PrintSheet.slots(html));
		assertEquals(2, html.split("class=\"page\"", -1).length - 1);
		assertEquals(0, PrintSheet.pages(0));
		assertEquals(1, PrintSheet.pages(9));
		assertEquals(2, PrintSheet.pages(10));
	}

	public void testAppendSheetsFillTheLastPage() throws Exception {
		String merged = ExportAppender.append(sheet("a", "b"), sheet("c"), PROXIES, "x.html");
		assertEquals(Arrays.asList("a", "b", "c"), PrintSheet.slots(merged));
		assertEquals(1, merged.split("class=\"page\"", -1).length - 1); // still one page
		assertEquals(3, ExportAppender.existingCards(merged));
	}

	public void testRefusesMixedKinds() {
		try {
			ExportAppender.append("NAME,SET\nFog,4ED\n", sheet("c"), PROXIES, "x.csv");
			fail("a printable sheet appended to a CSV");
		} catch (ExportAppender.AppendException e) {
			assertTrue(e.getMessage(), e.getMessage().contains("not a ManaDesk printable sheet"));
		}
		try {
			ExportAppender.append(sheet("a"), "NAME,SET\nFog,4ED\n", CSV, "x.html");
			fail("a CSV appended to a printable sheet");
		} catch (ExportAppender.AppendException e) {
			assertTrue(e.getMessage(), e.getMessage().contains("is a ManaDesk printable sheet"));
		}
	}

	public void testAppendCsvSameHeader() throws Exception {
		String merged = ExportAppender.append("NAME,SET\nFog,4ED", "NAME,SET\nBolt,M10\n", CSV, "x.csv");
		assertEquals("NAME,SET\nFog,4ED" + System.lineSeparator() + "Bolt,M10\n", merged);
		try {
			ExportAppender.append("NAME,SET,COUNT\nFog,4ED,1\n", "NAME,SET\nBolt,M10\n", CSV, "x.csv");
			fail("other columns");
		} catch (ExportAppender.AppendException e) {
			assertTrue(e.getMessage(), e.getMessage().contains("header row"));
		}
	}

	public void testAppendWithoutHeaderChecksTheExtension() throws Exception {
		assertEquals("Fog,4ED\nBolt,M10\n", ExportAppender.append("Fog,4ED\n", "Bolt,M10\n", CSV_NO_HEADER, "x.csv"));
		try {
			ExportAppender.append("Fog,4ED\n", "Bolt,M10\n", CSV_NO_HEADER, "x.txt");
			fail("other extension");
		} catch (ExportAppender.AppendException e) {
			assertTrue(e.getMessage(), e.getMessage().contains(".csv"));
		}
	}

	public void testEmptyFileTakesTheExport() throws Exception {
		assertEquals("new", ExportAppender.append("", "new", CSV, "x.csv"));
	}
}
