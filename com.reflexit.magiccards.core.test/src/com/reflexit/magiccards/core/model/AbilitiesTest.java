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
package com.reflexit.magiccards.core.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import com.reflexit.magiccards.core.model.abs.ICardGroup;
import com.reflexit.magiccards.core.model.utils.CardStoreUtils;

/**
 * A card's abilities are the keywords Scryfall lists for it (KEYWORDS), named after
 * Scryfall's official keyword abilities, ability words and keyword actions
 * (see {@code ScryfallReference}). Pure logic - runs as a plain JUnit test.
 */
public class AbilitiesTest extends TestCase {

	private static MagicCard card(String name, String keywords, String oracle) {
		MagicCard c = new MagicCard();
		c.setName(name);
		c.setOracleText(oracle);
		c.set(MagicCardField.KEYWORDS, keywords);
		return c;
	}

	public void testOfficialList() {
		List<String> all = Arrays.asList(Abilities.getNames());
		assertTrue("keyword abilities + ability words + keyword actions: " + all.size(), all.size() > 300);
		assertTrue(all.contains("Flying"));
		assertTrue(all.contains("Cascade"));
		assertTrue(all.contains("Landfall")); // an ability word
		assertTrue(all.contains("Scry")); // a keyword action
		assertFalse(all.contains("Tap/Untap")); // the old "mined" ones are gone
		assertFalse(all.contains("Power/Toughness"));
	}

	public void testGetAbilityIsCaseInsensitiveAndOfficiallySpelled() {
		assertEquals("Flying", Abilities.getAbility("flying"));
		assertEquals("Flying", Abilities.getAbility(" FLYING "));
		assertEquals("Level Up", Abilities.getAbility("level up"));
		assertNull(Abilities.getAbility("no such ability"));
		assertNull(Abilities.getAbility(null));
	}

	public void testOfIsWhatTheCardHas() {
		MagicCard c = card("Rampaging Baloths", "Trample|Landfall", "Trample\nLandfall — Whenever a land enters...");
		Set<String> a = Abilities.of(c);
		assertEquals(2, a.size());
		assertTrue(a.contains("Trample"));
		assertTrue(a.contains("Landfall"));
	}

	public void testMentioningTheWordIsNotHavingTheAbility() {
		// Reach's reminder text says "flying"; a spell says "scry" in its prose
		MagicCard reach = card("Giant Spider", "Reach", "Reach (This creature can block creatures with flying.)");
		assertTrue(Abilities.has(reach, "Reach"));
		assertFalse(Abilities.has(reach, "Flying"));
		MagicCard text = card("Some Spell", null, "Target creature gains flying. Scry 2 would be nice.");
		assertTrue(Abilities.of(text).isEmpty());
	}

	public void testNoKeywordsMeansNoAbilities() {
		assertTrue(Abilities.of(card("Plain", null, "")).isEmpty());
		assertTrue(Abilities.of(card("Plain", "", "")).isEmpty());
		assertTrue(Abilities.of(null).isEmpty());
	}

	public void testSpellingIsNormalisedAndUnknownKeywordsKept() {
		Set<String> a = Abilities.of(card("X", "flying|Brand New Keyword", ""));
		assertTrue(a.contains("Flying"));
		assertTrue("a keyword Scryfall added after our list still shows", a.contains("Brand New Keyword"));
	}

	public void testHasIsCaseInsensitive() {
		assertTrue(Abilities.has(card("X", "First strike", ""), "first strike"));
		assertFalse(Abilities.has(card("X", "First strike", ""), "strike"));
		assertFalse(Abilities.has(card("X", "First strike", ""), null));
	}

	public void testKeywordPattern() {
		assertTrue(Abilities.keywordPattern("Flying").matcher("Flying").find());
		assertTrue(Abilities.keywordPattern("flying").matcher("Trample|Flying|Haste").find());
		assertFalse(Abilities.keywordPattern("Strike").matcher("First strike").find());
		assertFalse(Abilities.keywordPattern("First strike").matcher("Double strike").find());
	}

	public void testStatsCountEveryCopy() {
		MagicCard a = card("A", "Flying|Scry", "");
		MagicCard b = card("B", "Flying", "");
		MagicCardPhysical pa = new MagicCardPhysical(a, null);
		pa.setCount(3);
		MagicCardPhysical pb = new MagicCardPhysical(b, null);
		pb.setCount(2);
		com.reflexit.magiccards.core.model.storage.MemoryCardStore<IMagicCard> store = new com.reflexit.magiccards.core.model.storage.MemoryCardStore<IMagicCard>();
		store.add(pa);
		store.add(pb);
		HashMap<String, Integer> stats = CardStoreUtils.buildAbilityStats(store);
		assertEquals(Integer.valueOf(5), stats.get("Flying"));
		assertEquals(Integer.valueOf(3), stats.get("Scry"));
		assertNull(stats.get("Trample"));
	}

	public void testGroupsAreSortedAndFromKeywords() {
		List<IMagicCard> cards = new ArrayList<IMagicCard>();
		cards.add(card("A", "Trample|Flying", "Flying"));
		cards.add(card("B", "Flying", "Reach (flying)"));
		cards.add(card("C", null, "Flying"));
		ICardGroup root = CardStoreUtils.buildAbilityGroups(cards);
		ICardGroup keywordNode = (ICardGroup) ((ICardGroup) root.getChildAtIndex(0)).getChildAtIndex(0);
		assertEquals(2, keywordNode.size());
		assertEquals("Flying", keywordNode.getChildAtIndex(0).getName());
		assertEquals(2, ((ICardGroup) keywordNode.getChildAtIndex(0)).size());
		assertEquals("Trample", keywordNode.getChildAtIndex(1).getName());
	}
}
