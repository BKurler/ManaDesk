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
package com.reflexit.magiccards.core.model.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.reflexit.magiccards.core.model.CardGroup;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICardGroup;
import com.reflexit.magiccards.core.model.storage.MemoryCardStore;

/**
 * {@link CardStoreUtils}: the deck statistics (mana curve, type split, counts,
 * repeats, mana sources, game changers, printings) that feed the deck info page and
 * the charts. Pure logic, no DB: works as plain JUnit.
 */
public class CardStoreUtilsStatsTest {
	private static MagicCardPhysical card(String name, String type, String cost, int count) {
		MagicCard c = new MagicCard();
		c.setName(name);
		c.setType(type);
		c.setCost(cost);
		MagicCardPhysical p = new MagicCardPhysical(c, Location.NO_WHERE);
		p.setCount(count);
		return p;
	}

	private static MemoryCardStore<IMagicCard> store(IMagicCard... cards) {
		MemoryCardStore<IMagicCard> s = new MemoryCardStore<IMagicCard>();
		for (IMagicCard c : cards)
			s.add(c);
		return s;
	}

	private static List<IMagicCard> list(IMagicCard... cards) {
		return new ArrayList<IMagicCard>(Arrays.asList(cards));
	}

	// ---- counts

	@Test
	public void testCountCardsUsesCountsAndPlainCards() {
		List<IMagicCard> l = list(card("A", "Instant", "{R}", 3), card("B", "Instant", "{R}", 2), new MagicCard());
		assertEquals(3 + 2 + 1, CardStoreUtils.countCards(l));
	}

	@Test
	public void testGetCount() {
		assertEquals(0, CardStoreUtils.getCount(null));
		assertEquals(0, CardStoreUtils.getCount(list()));
		assertEquals(7, CardStoreUtils.getCount(list(card("A", "Instant", "{R}", 4), card("B", "Land", "", 3))));
		assertEquals(1, CardStoreUtils.getCount(list(new MagicCard())));
	}

	@Test
	public void testGetManaCostIsCmcTimesCount() {
		assertEquals(0, CardStoreUtils.getManaCost(null));
		// {1}{R} = 2, x3 ; {3} = 3, x2 ; land = 0
		List<IMagicCard> l = list(card("A", "Instant", "{1}{R}", 3), card("B", "Artifact", "{3}", 2),
				card("L", "Land", "", 9));
		assertEquals(2 * 3 + 3 * 2, CardStoreUtils.getManaCost(l));
	}

	// ---- mana curve

	@Test
	public void testManaCurveStats() {
		MemoryCardStore<IMagicCard> s = store(card("Bolt", "Instant", "{R}", 4), card("Bear", "Creature - Bear", "{1}{G}", 3),
				card("Big", "Creature - Dragon", "{5}{R}{R}", 2), card("Huge", "Creature - Eldrazi", "{9}", 1),
				card("Forest", "Basic Land - Forest", "", 20));
		int[] bars = CardStoreUtils.buildManaCurveStats(s);
		assertEquals(8, bars.length);
		assertArrayEquals(new int[] { 0, 4, 3, 0, 0, 0, 0, 2 + 1 }, bars);
	}

	@Test
	public void testManaCurveGroup() {
		List<IMagicCard> l = list(card("Bolt", "Instant", "{R}", 1), card("Bear", "Creature - Bear", "{1}{G}", 1),
				card("Huge", "Creature - Eldrazi", "{9}", 1), card("Forest", "Basic Land - Forest", "", 1));
		ICardGroup root = CardStoreUtils.buildManaCurveGroup(l);
		CardGroup curve = (CardGroup) root.getChildAtIndex(0);
		assertEquals(8, curve.size());
		assertEquals(0, ((CardGroup) curve.getChildAtIndex(0)).getCount());
		assertEquals(1, ((CardGroup) curve.getChildAtIndex(1)).getCount());
		assertEquals(1, ((CardGroup) curve.getChildAtIndex(2)).getCount());
		assertEquals(1, ((CardGroup) curve.getChildAtIndex(7)).getCount()); // 7+
	}

	// ---- types

	@Test
	public void testTypeStats() {
		MemoryCardStore<IMagicCard> s = store(card("Forest", "Basic Land - Forest", "", 10), card("Bear", "Creature - Bear", "{1}{G}", 4),
				card("Bolt", "Instant", "{R}", 3), card("Ring", "Artifact", "{1}", 2), new MagicCardPhysical(new MagicCard(), Location.NO_WHERE));
		// land, creature, other; the card without a type is skipped
		assertArrayEquals(new int[] { 10, 4, 5 }, CardStoreUtils.buildTypeStats(s));
	}

	@Test
	public void testIsLand() {
		assertTrue(CardStoreUtils.isLand(card("F", "Basic Land - Forest", "", 1)));
		assertTrue(CardStoreUtils.isLand(card("U", "Land", "", 1)));
		assertTrue("an untyped card is treated as a land", CardStoreUtils.isLand(new MagicCard()));
		assertFalse(CardStoreUtils.isLand(card("B", "Creature - Bear", "{1}{G}", 1)));
	}

	@Test
	public void testTypeGroups() {
		List<IMagicCard> l = list(card("Forest", "Basic Land - Forest", "", 1), card("Tower", "Land", "", 1),
				card("Bear", "Creature - Bear Cub", "{1}{G}", 1), card("Bolt", "Instant", "{R}", 1),
				card("Wrath", "Sorcery", "{2}{W}{W}", 1), card("Ring", "Artifact", "{1}", 1),
				card("Pact", "Enchantment", "{1}{G}", 1), card("Jace", "Legendary Planeswalker - Jace", "{2}{U}{U}", 1),
				new MagicCard());
		ICardGroup root = CardStoreUtils.buildTypeGroups(l);
		CardGroup type = (CardGroup) root.getChildAtIndex(0);
		assertEquals(l.size(), type.getCount());
		CardGroup land = (CardGroup) type.getChildAtIndex(0);
		assertEquals(2, land.getCount());
		assertEquals(1, ((CardGroup) land.getChildAtIndex(0)).getCount()); // basic
		assertEquals(1, ((CardGroup) land.getChildAtIndex(1)).getCount()); // non basic
	}

	@Test
	public void testCreatureStatsAndTop() {
		MemoryCardStore<IMagicCard> s = store(card("Elf", "Creature - Elf Druid", "{G}", 2), card("Elf2", "Creature - Elf Warrior", "{1}{G}", 3),
				card("Golem", "Artifact Creature - Golem", "{4}", 1), card("Plain", "Creature", "{2}", 4),
				card("Bolt", "Instant", "{R}", 9));
		HashMap<String, Integer> stats = CardStoreUtils.buildCreatureStats(s);
		assertEquals(Integer.valueOf(5), stats.get("Elf"));
		assertEquals(Integer.valueOf(2), stats.get("Druid"));
		assertEquals(Integer.valueOf(3), stats.get("Warrior"));
		assertEquals(Integer.valueOf(1), stats.get("Golem"));
		assertEquals("a creature without subtype goes to Others", Integer.valueOf(4), stats.get("Others"));
		assertFalse("non creatures are ignored", stats.containsKey("Instant"));
		Map<String, Integer> top2 = CardStoreUtils.top(2, stats);
		List<String> keys = new ArrayList<String>(top2.keySet());
		// biggest two by value, the rest (Druid 2 + Golem 1 + Others 4) folded into Others
		assertEquals(Arrays.asList("Elf", "Warrior", "Others"), keys);
		assertEquals(Integer.valueOf(5), top2.get("Elf"));
		assertEquals(Integer.valueOf(4 + 2 + 1), top2.get("Others"));
	}

	@Test
	public void testTopWithoutOthersNeeded() {
		HashMap<String, Integer> stats = new HashMap<String, Integer>();
		stats.put("a", 3);
		stats.put("b", 1);
		Map<String, Integer> top = CardStoreUtils.top(5, stats);
		assertEquals(Arrays.asList("a", "b"), new ArrayList<String>(top.keySet()));
		assertFalse(top.containsKey("Others"));
	}

	// ---- repeats

	@Test
	public void testRepeatsSkipBasicLands() {
		List<IMagicCard> l = list(card("Bolt", "Instant", "{R}", 4), card("Bolt", "Instant", "{R}", 0),
				card("Forest", "Basic Land - Forest", "", 24), card("Bear", "Creature - Bear", "{1}{G}", 2));
		CountersMap m = CardStoreUtils.buildRepeats(l, null);
		assertEquals(Integer.valueOf(4), m.get("Bolt"));
		assertEquals(Integer.valueOf(2), m.get("Bear"));
		assertFalse(m.containsKey("Forest"));
		assertEquals(4, CardStoreUtils.getMaxRepeats(l));
	}

	@Test
	public void testRepeatsAccumulateIntoGivenMap() {
		CountersMap m = new CountersMap();
		m.inc("Bolt", 2);
		CardStoreUtils.buildRepeats(list(card("Bolt", "Instant", "{R}", 2)), m);
		assertEquals(Integer.valueOf(4), m.get("Bolt"));
		assertNotNull(CardStoreUtils.buildRepeats(null, null));
		assertEquals(0, CardStoreUtils.getMaxRepeats(null));
	}

	// ---- Scryfall-derived facts

	private static MagicCardPhysical withFacts(String name, int count, String gameChanger, String produced, String oracleId) {
		MagicCardPhysical p = card(name, "Artifact", "{2}", count);
		MagicCard base = p.getCard();
		if (gameChanger != null)
			base.setProperty(MagicCardField.GAME_CHANGER, gameChanger);
		if (produced != null)
			base.setProperty(MagicCardField.PRODUCED_MANA, produced);
		if (oracleId != null)
			base.setProperty(MagicCardField.ORACLE_ID, oracleId);
		return p;
	}

	@Test
	public void testGameChangers() {
		List<IMagicCard> l = list(withFacts("Sol Ring", 1, "true", null, null), withFacts("sol ring", 1, "true", null, null),
				withFacts("Plain", 3, null, null, null), withFacts("Rhystic Study", 2, "false", null, null),
				withFacts("Cyclonic Rift", 1, "true", null, null));
		Map<String, Integer> gc = CardStoreUtils.buildGameChangers(l);
		assertEquals("names are merged ignoring case, 'false' and absent do not count", 2, gc.size());
		assertEquals(Integer.valueOf(2), gc.get("Sol Ring"));
		assertEquals(Integer.valueOf(1), gc.get("Cyclonic Rift"));
	}

	@Test
	public void testManaSources() {
		List<IMagicCard> l = list(withFacts("Forest", 10, null, "{G}", null), withFacts("Wastes", 2, null, "{C}", null),
				withFacts("Command Tower", 1, null, "{W}{U}{B}{R}{G}", null), withFacts("Bolt", 4, null, null, null));
		Map<String, Integer> src = CardStoreUtils.buildManaSources(l);
		assertEquals(Arrays.asList("W", "U", "B", "R", "G", "C"), new ArrayList<String>(src.keySet()));
		assertEquals(Integer.valueOf(1), src.get("W"));
		assertEquals(Integer.valueOf(1), src.get("U"));
		assertEquals(Integer.valueOf(1), src.get("B"));
		assertEquals(Integer.valueOf(1), src.get("R"));
		assertEquals(Integer.valueOf(11), src.get("G"));
		assertEquals(Integer.valueOf(2), src.get("C"));
	}

	@Test
	public void testManaSourcesEmpty() {
		Map<String, Integer> src = CardStoreUtils.buildManaSources(new ArrayList<IMagicCard>());
		assertEquals(6, src.size());
		for (Integer v : src.values())
			assertEquals(Integer.valueOf(0), v);
	}

	@Test
	public void testPrintingsByOracleId() {
		IMagicCard a = withFacts("Bolt", 1, null, null, "oracle-1").getCard();
		IMagicCard b = withFacts("Lightning Bolt", 1, null, null, "oracle-1").getCard();
		IMagicCard c = withFacts("Other", 1, null, null, "oracle-2").getCard();
		IMagicCard d = withFacts("Unknown", 1, null, null, null).getCard();
		List<IMagicCard> all = list(a, c, b, d);
		assertEquals(Arrays.asList(a, b), CardStoreUtils.printingsByOracleId(all, "oracle-1"));
		assertTrue(CardStoreUtils.printingsByOracleId(all, null).isEmpty());
		assertTrue(CardStoreUtils.printingsByOracleId(all, "").isEmpty());
		assertTrue(CardStoreUtils.printingsByOracleId(all, "nope").isEmpty());
	}
}
