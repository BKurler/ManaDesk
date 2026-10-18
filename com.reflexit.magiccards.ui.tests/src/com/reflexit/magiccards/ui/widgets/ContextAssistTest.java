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
package com.reflexit.magiccards.ui.widgets;

import org.eclipse.jface.fieldassist.IContentProposal;

import junit.framework.TestCase;

/**
 * The Text field suggests only inside a bracket. Pure logic - runs as a plain JUnit test
 * (or inside the plug-in test run with the others).
 */
public class ContextAssistTest extends TestCase {
	private static final String[] NAMES = { "Flying", "Flash", "Level Up", "Showcase", "Scry" };

	private static String[] labels(IContentProposal[] p) {
		String[] res = new String[p.length];
		for (int i = 0; i < p.length; i++)
			res[i] = p[i].getLabel();
		return res;
	}

	private static IContentProposal[] at(String text) {
		return ContextAssist.bracketProposals(NAMES, text, text.length());
	}

	public void testNothingWhileTypingPlainWords() {
		assertEquals(0, at("fl").length);
		assertEquals(0, at("flying draw").length);
		assertEquals(0, at("").length);
	}

	public void testNothingOnceTheBracketIsClosed() {
		assertEquals(0, at("[flying] fl").length);
		assertEquals(0, at("[flying]").length);
	}

	public void testSuggestsAfterAnOpenBracket() {
		assertEquals(NAMES.length, at("[").length); // everything
		String[] fl = labels(at("[fl"));
		assertEquals(2, fl.length);
		assertEquals("Flying", fl[0]);
		assertEquals("Flash", fl[1]);
	}

	public void testCompletionFinishesTheNameAndClosesTheBracket() {
		IContentProposal[] p = at("[flyi");
		assertEquals(1, p.length);
		assertEquals("ng]", p[0].getContent()); // what is still to insert
		assertEquals("Flying", p[0].getLabel());
	}

	public void testCaseAndSpacesInTheTypedName() {
		assertEquals(1, at("[LEVEL u").length);
	}

	public void testSecondBracketInTheSameText() {
		assertEquals(1, at("draw [flying] [sho").length);
		assertEquals("Showcase", labels(at("draw [flying] [sho"))[0]);
	}

	public void testCursorInTheMiddle() {
		// text "[fl] and more", cursor right after "[fl"
		IContentProposal[] p = ContextAssist.bracketProposals(NAMES, "[fl] and more", 3);
		assertEquals(2, p.length);
	}

	public void testNullAndBadPosition() {
		assertEquals(0, ContextAssist.bracketProposals(NAMES, null, 0).length);
		assertEquals(0, ContextAssist.bracketProposals(NAMES, "[fl", 99).length);
	}
}
