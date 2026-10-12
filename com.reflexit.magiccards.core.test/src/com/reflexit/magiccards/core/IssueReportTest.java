/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *     Rémi Dutil (2026) - email report tests: mailto / webmail links, room left,
 *                         attachment note, log file for the attachment
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

	public void testMailto() {
		IssueReport r = new IssueReport().setKind(IssueReport.Kind.BUG).setTitle("Set list & order")
				.setDescription("Line one\nline two").setSystem("ManaDesk: 1.0").setLogs("boom");
		String m = r.toMailto();
		assertTrue(m, m.startsWith("mailto:" + IssueReport.REPORT_EMAIL + "?subject="));
		assertTrue(m, m.contains("subject=%5BManaDesk%20bug%5D%20Set%20list%20%26%20order"));
		// the system line on top, then the description
		assertTrue(m, m.contains("&body=ManaDesk%3A%201.0%0D%0A%0D%0ALine%20one%0D%0Aline%20two"));
		assertFalse(m, m.contains("boom")); // the log is never in the email body
		assertFalse(m, m.contains("attached"));
		assertFalse(m, m.contains("+"));
		assertTrue(r.mailRoomLeft() > 1000);
		assertEquals("[ManaDesk feature] Idea", r.setKind(IssueReport.Kind.FEATURE).setTitle("Idea").mailSubject());
	}

	public void testMailAttachment() {
		IssueReport r = new IssueReport().setTitle("t").setDescription("d").setSystem("sys");
		int without = r.mailRoomLeft();
		r.setMailAttachment("ManaDesk-log.txt");
		assertTrue(r.toMailto(), r.toMailto().contains("Log%3A%20attached%20%28ManaDesk-log.txt%29"));
		assertTrue(r.mailRoomLeft() < without); // the note takes room too
		r.setKind(IssueReport.Kind.FEATURE); // a feature request has no error log
		assertFalse(r.toMailto().contains("attached"));
	}

	public void testMailService() {
		assertEquals(IssueReport.MailService.GMAIL, IssueReport.mailServiceFor("Someone@Gmail.com "));
		assertEquals(IssueReport.MailService.OUTLOOK, IssueReport.mailServiceFor("me@hotmail.com"));
		assertEquals(IssueReport.MailService.OUTLOOK, IssueReport.mailServiceFor("me@outlook.fr"));
		assertEquals(IssueReport.MailService.OUTLOOK, IssueReport.mailServiceFor("me@live.ca"));
		assertEquals(IssueReport.MailService.APP, IssueReport.mailServiceFor("me@videotron.ca"));
		assertEquals(IssueReport.MailService.APP, IssueReport.mailServiceFor(""));
		assertEquals(IssueReport.MailService.APP, IssueReport.mailServiceFor(null));

		IssueReport r = new IssueReport().setTitle("Hi there").setDescription("Line one\nline two");
		String g = r.toMailLink(IssueReport.MailService.GMAIL);
		assertTrue(g, g.startsWith("https://mail.google.com/mail/?view=cm&fs=1&to=BKurler-Manadesk%40outlook.com&su="));
		assertTrue(g, g.contains("&body=Line%20one%0Aline%20two"));
		String o = r.toMailLink(IssueReport.MailService.OUTLOOK);
		assertTrue(o, o.startsWith("https://outlook.live.com/mail/0/deeplink/compose?to=BKurler-Manadesk%40outlook.com"));
		assertTrue(o, o.contains("&subject=%5BManaDesk%20bug%5D%20Hi%20there"));
		assertEquals(r.toMailto(), r.toMailLink(IssueReport.MailService.APP));
	}

	public void testLogForAttachment() {
		String home = "C:\\Users\\Someone";
		String log = "!SESSION one\nline in " + home + "\\a\n!SESSION two\nlast line\n";
		assertEquals(log.trim().replace(home, "~"), IssueReport.logForAttachment(log, home, 1000000));
		String end = IssueReport.logForAttachment(log, home, 25);
		assertTrue(end, end.startsWith("[older entries cut]\n")); // cut at a line start
		assertTrue(end, end.endsWith("last line"));
		assertFalse(end, end.contains("one"));
		assertEquals("", IssueReport.logForAttachment((java.io.File) null, 100));
	}

	public void testFullDescriptionFits() {
		// the longest plain description the dialog allows fits, with a realistic
		// system line and the attachment note
		StringBuilder desc = new StringBuilder();
		while (desc.length() < IssueReport.MAX_DESCRIPTION_CHARS)
			desc.append("word ");
		desc.setLength(IssueReport.MAX_DESCRIPTION_CHARS);
		IssueReport r = new IssueReport().setTitle("A rather long title for a bug report, about sixty chars")
				.setSystem("ManaDesk 1.0.0.qualifier | Java 17.0.12 | Windows 11 10.0 amd64")
				.setMailAttachment("ManaDesk-log.txt").setDescription(desc.toString());
		assertTrue(String.valueOf(r.mailRoomLeft()), r.mailRoomLeft() >= 0);
		assertTrue(r.toMailto().contains("word%20word"));
	}

	public void testMailRoom() {
		IssueReport r = new IssueReport().setTitle("t").setDescription("");
		int empty = r.mailRoomLeft();
		r.setDescription("abc def");
		// "abc" + "%20" + "def" = 9, replacing "(no description)" (25 once encoded)
		assertEquals(empty + IssueReport.mailLength("(no description)") - 9, r.mailRoomLeft());
		assertEquals(3, IssueReport.mailLength(" "));
		assertEquals(6, IssueReport.mailLength("\n")); // CRLF
		StringBuilder tooLong = new StringBuilder();
		for (int i = 0; i < 1000; i++)
			tooLong.append("ab ");
		r.setDescription(tooLong.toString());
		assertTrue(r.mailRoomLeft() < 0);
		assertTrue(r.toMailto().length() <= IssueReport.MAX_MAILTO_LENGTH); // last resort: cut
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
