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
package com.reflexit.magiccards.ui.views.columns;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.reflexit.magiccards.core.model.DigitalSupport;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;

/**
 * {@link DigitalColumn}'s cell text: "Digital" for a digital-only printing, "Also online" for a paper
 * printing also on MTGO / Arena, blank otherwise and while the digital cards are not enabled. Plain
 * JUnit (no display needed).
 */
public class DigitalColumnTest {
	private boolean wasEnabled;

	@Before
	public void remember() {
		wasEnabled = DigitalSupport.isEnabled();
		DigitalSupport.setEnabled(true);
	}

	@After
	public void restore() {
		DigitalSupport.setEnabled(wasEnabled);
	}

	private static MagicCard card(String digital, String online) {
		MagicCard c = new MagicCard();
		c.set(MagicCardField.DIGITAL, digital);
		c.set(MagicCardField.ONLINE, online);
		return c;
	}

	@Test
	public void testTexts() {
		assertEquals("Digital", DigitalColumn.textOf(card("true", null)));
		assertEquals("Also online", DigitalColumn.textOf(card(null, "true")));
		assertEquals("", DigitalColumn.textOf(card(null, null)));
	}

	@Test
	public void testACopyShowsItsPrintingsKind() {
		assertEquals("Digital", DigitalColumn.textOf(new MagicCardPhysical(card("true", null), null)));
	}

	@Test
	public void testBlankWhileTheDigitalCardsAreNotEnabled() {
		DigitalSupport.setEnabled(false);
		assertEquals("", DigitalColumn.textOf(card("true", null)));
		assertEquals("", DigitalColumn.textOf(card(null, "true")));
	}

	@Test
	public void testBlankForNonCards() {
		assertEquals("", DigitalColumn.textOf(null));
		assertEquals("", DigitalColumn.textOf("text"));
	}
}
