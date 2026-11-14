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
package com.reflexit.magiccards.ui.exportWizards;

import java.util.ArrayList;
import java.util.List;

import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardPhysical;

import junit.framework.TestCase;

/**
 * The import preview shows the Comments column when a card has a comment - in particular the "$$French$$" mark of
 * a card imported in English although the file gave another language. Pure logic, plain JUnit.
 */
public class PreviewCommentColumnTest extends TestCase {
	private static MagicCardPhysical card(String comment) {
		MagicCard base = new MagicCard();
		base.setName("Plains");
		MagicCardPhysical c = new MagicCardPhysical(base, null);
		if (comment != null)
			c.setComment(comment);
		return c;
	}

	public void testNoComment() {
		List<Object> cards = new ArrayList<>();
		cards.add(card(null));
		cards.add(card(""));
		cards.add(card("   "));
		assertFalse(DeckImportPreviewPage.anyComment(cards));
		assertFalse(DeckImportPreviewPage.anyComment(new ArrayList<Object>()));
		assertFalse(DeckImportPreviewPage.anyComment(null));
	}

	public void testOneCommentIsEnough() {
		List<Object> cards = new ArrayList<>();
		cards.add(card(null));
		cards.add(card("$$French$$"));
		cards.add(card(null));
		assertTrue(DeckImportPreviewPage.anyComment(cards));
		// a comment of the file counts too
		cards.clear();
		cards.add(card("for my cousin"));
		assertTrue(DeckImportPreviewPage.anyComment(cards));
	}

	/** Rows that are not cards (a group, an error line) are ignored. */
	public void testOtherRowsAreIgnored() {
		List<Object> cards = new ArrayList<>();
		cards.add("not a card");
		cards.add(null);
		assertFalse(DeckImportPreviewPage.anyComment(cards));
	}
}
