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

import junit.framework.TestCase;

public class IssueReportTest extends TestCase {
	public void testBugUrl() {
		String url = new IssueReport().setKind(IssueReport.Kind.BUG).setTitle("Set list & order")
				.setDescription("Line one\nline two").setSystem("ManaDesk: 1.0").setLogs("boom").toUrl();
		assertTrue(url, url.startsWith(IssueReport.NEW_ISSUE_URL + "?template=bug_report.yml"));
		assertTrue(url, url.contains("&labels=bug"));
		assertTrue(url, url.contains("&title=Set%20list%20%26%20order"));
		assertTrue(url, url.contains("&description=Line%20one%0Aline%20two"));
		assertTrue(url, url.contains("&system=ManaDesk%3A%201.0"));
		assertTrue(url, url.contains("&logs=boom"));
		assertFalse(url, url.contains("+"));
		// the same report as a plain issue body, for when the form is missing
		assertTrue(url, url.contains("&body=Line%20one%0Aline%20two"));
		assertTrue(url, url.contains("System%20information"));
	}

	public void testFeatureHasNoLogs() {
		String url = new IssueReport().setKind(IssueReport.Kind.FEATURE).setTitle("Idea").setLogs("boom").toUrl();
		assertTrue(url, url.contains("template=feature_request.yml"));
		assertTrue(url, url.contains("&labels=enhancement"));
		assertFalse(url, url.contains("logs="));
	}

	public void testLongReportStaysUnderTheLimit() {
		StringBuilder big = new StringBuilder();
		for (int i = 0; i < 2000; i++)
			big.append("at some.Class.method(Class.java:").append(i).append(")\n");
		IssueReport r = new IssueReport().setTitle("t").setDescription(big.toString()).setLogs(big.toString());
		String url = r.toUrl();
		assertTrue(String.valueOf(url.length()), url.length() <= IssueReport.MAX_URL_LENGTH);
		// the plain-text copy keeps everything
		assertTrue(r.toText().length() > big.length() * 2);
	}

	public void testRecentLogErrors() {
		String home = "C:\\Users\\Someone";
		String log = "!SESSION 2026-10-10\n" //
				+ "!ENTRY org.a 1 0 2026-10-10 info\n!MESSAGE fine\n" //
				+ "!ENTRY org.b 4 0 2026-10-10 first\n!MESSAGE bad 1\n!STACK 0\njava.lang.X\n" //
				+ "!ENTRY org.c 2 0 2026-10-10 warning\n!MESSAGE meh\n" //
				+ "!ENTRY org.d 4 0 2026-10-10 second\n!MESSAGE bad 2 in " + home + "\\file\n" //
				+ "!ENTRY org.e 4 0 2026-10-10 third\n!MESSAGE bad 3\n" //
				+ "!ENTRY org.f 4 0 2026-10-10 fourth\n!MESSAGE bad 4\n";
		String out = IssueReport.recentLogErrors(log, home);
		assertFalse(out, out.contains("bad 1")); // only the last 3 errors
		assertTrue(out, out.contains("bad 2") && out.contains("bad 3") && out.contains("bad 4"));
		assertFalse(out, out.contains("fine") || out.contains("meh")); // not info / warning
		assertTrue(out, out.contains("~\\file"));
		assertFalse(out, out.contains(home));
		assertEquals("", IssueReport.recentLogErrors("!ENTRY org.a 1 0 x\n!MESSAGE ok\n", home));
	}
}
