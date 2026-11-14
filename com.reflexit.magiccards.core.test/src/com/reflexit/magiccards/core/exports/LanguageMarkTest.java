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

import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardPhysical;

import junit.framework.TestCase;

/**
 * A card imported in English although the file gave another language keeps that language in its Special tags,
 * as "lang=French" (not in its comment). Pure logic (plain JUnit).
 */
public class LanguageMarkTest extends TestCase {
	private static MagicCardPhysical card() {
		MagicCard base = new MagicCard();
		base.setName("Plains");
		return new MagicCardPhysical(base, null);
	}

	public void testTag() {
		assertEquals("lang=French", ImportUtils.languageTag("French"));
		assertEquals("first letter upper case, the others lower case", "lang=French", ImportUtils.languageTag("fRENCH"));
		assertEquals("lang=German", ImportUtils.languageTag(" german "));
		assertEquals("lang=Chinese simplified", ImportUtils.languageTag("Chinese Simplified"));
		assertEquals("", ImportUtils.languageTag(""));
		assertEquals("", ImportUtils.languageTag(null));
	}

	/** The tag is added to the Special tags - next to the others - and the comment is not touched. */
	public void testAddedToTheSpecialTags() {
		MagicCardPhysical card = card();
		ImportUtils.addLanguageTag(card, "French");
		assertEquals("lang=French", card.getSpecial());
		assertEquals("French", card.getSpecialTagValue("lang"));
		assertNull("the comment is not touched", card.getComment());
		card.setSpecialTag("signed");
		assertEquals("lang=French,signed", card.getSpecial());
		assertTrue(card.isSpecialTag("signed"));
	}

	/** Importing again, or applying twice, does not repeat it. */
	public void testNeverAddedTwice() {
		MagicCardPhysical card = card();
		ImportUtils.addLanguageTag(card, "French");
		ImportUtils.addLanguageTag(card, "french");
		assertEquals("lang=French", card.getSpecial());
		// no language: nothing
		ImportUtils.addLanguageTag(card, null);
		ImportUtils.addLanguageTag(card, " ");
		assertEquals("lang=French", card.getSpecial());
	}

	/** The card has that language after all (found in the database): the tag goes away, the others stay. */
	public void testRemoved() {
		MagicCardPhysical card = card();
		card.setSpecialTag("signed");
		ImportUtils.addLanguageTag(card, "French");
		ImportUtils.removeLanguageTag(card, "French");
		assertEquals("signed", card.getSpecial());
		// only the tag of that language: a card tagged German stays tagged when French is removed
		ImportUtils.addLanguageTag(card, "German");
		ImportUtils.removeLanguageTag(card, "French");
		assertEquals("German", card.getSpecialTagValue("lang"));
		// nothing to remove, nothing to fail
		MagicCardPhysical none = card();
		ImportUtils.removeLanguageTag(none, "French");
		ImportUtils.removeLanguageTag(none, null);
		assertTrue(none.getSpecial() == null || none.getSpecial().isEmpty());
	}
}
