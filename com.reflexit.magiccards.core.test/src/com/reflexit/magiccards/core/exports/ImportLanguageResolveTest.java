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
import java.util.List;
import java.util.function.Function;

import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardPhysical;

import junit.framework.TestCase;

/**
 * A card the file gives in another language is looked up in that language first (a French card of a set that
 * has French printings only was an error: "Language is not loaded in DB"), then as the file gave it. Pure logic
 * (plain JUnit): the database lookup is replaced by a fake resolver.
 */
public class ImportLanguageResolveTest extends TestCase {
	/** The language the base card had at each call of the resolver ("-" when none). */
	private final List<String> calls = new ArrayList<>();

	private static MagicCardPhysical card(String id) {
		MagicCard base = new MagicCard();
		base.setName("Plains");
		base.setSet("Foreign Black Border");
		if (id != null)
			base.setCardId(id);
		return new MagicCardPhysical(base, null);
	}

	/** The database has the set in French only: found in French, "Language is not loaded in DB" otherwise. */
	private Function<MagicCardPhysical, MagicCard> frenchOnly() {
		return c -> {
			calls.add(c.getBase().getLanguage() == null ? "-" : c.getBase().getLanguage());
			if ("French".equals(c.getBase().getLanguage()))
				return null; // found
			c.getBase().setCardId("french-printing-id"); // what a failed attempt leaves in the base
			c.setError(ImportError.NO_LANG);
			return c.getBase();
		};
	}

	/** The database has the set in English only. */
	private Function<MagicCardPhysical, MagicCard> englishOnly() {
		return c -> {
			calls.add(c.getBase().getLanguage() == null ? "-" : c.getBase().getLanguage());
			if (c.getBase().getLanguage() == null)
				return null; // found
			c.getBase().setCardId("english-printing-id");
			c.setError(ImportError.NO_LANG);
			return c.getBase();
		};
	}

	/** French in the file, French in the database: no error. */
	public void testFoundInTheLanguageOfTheFile() {
		MagicCardPhysical card = card(null);
		ImportUtils.resolveInDeclaredLanguage(card, "French", frenchOnly());
		assertNull(card.getError());
		assertEquals("French", card.getLanguage());
		assertEquals("looked up once, in French", java.util.Arrays.asList("French"), calls);
	}

	/** French in the file, English only in the database: the card as the file gave it, nothing left of the attempt. */
	public void testNotInThatLanguageGoesBackToTheCardAsTheFileGaveIt() {
		MagicCardPhysical card = card(null);
		ImportUtils.resolveInDeclaredLanguage(card, "French", englishOnly());
		assertNull(card.getError());
		assertNull("English", card.getLanguage());
		assertNull("no id of the failed attempt", card.getCardId());
		assertEquals("Foreign Black Border", card.getSet());
		assertEquals(java.util.Arrays.asList("French", "-"), calls);
	}

	/** Not found either way: the error of the lookup as the file gave it. */
	public void testNotFoundAtAllKeepsTheError() {
		MagicCardPhysical card = card(null);
		ImportUtils.resolveInDeclaredLanguage(card, "German", frenchOnly());
		assertEquals(ImportError.NO_LANG, card.getError()); // the set has French only: as before for such a card
		assertNull(card.getLanguage());
		assertEquals(java.util.Arrays.asList("German", "-"), calls);
	}

	/** No language in the file: one lookup, as before. */
	public void testNoLanguageInTheFile() {
		MagicCardPhysical card = card(null);
		ImportUtils.resolveInDeclaredLanguage(card, null, englishOnly());
		assertNull(card.getError());
		assertEquals(java.util.Arrays.asList("-"), calls);
		calls.clear();
		ImportUtils.resolveInDeclaredLanguage(card, "  ", englishOnly());
		assertEquals(java.util.Arrays.asList("-"), calls);
	}

	/** A card with an id: the printing the id names is exact, its language is not changed. */
	public void testCardWithAnIdIsLookedUpOnce() {
		MagicCardPhysical card = card("some-id");
		ImportUtils.resolveInDeclaredLanguage(card, "French", englishOnly());
		assertEquals(java.util.Arrays.asList("-"), calls);
		assertNull(card.getBase().getLanguage());
	}
}
