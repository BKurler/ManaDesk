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
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.reflexit.magiccards.core.model.DeckAccessories.Kind;
import com.reflexit.magiccards.core.model.DeckAccessories.Need;
import com.reflexit.magiccards.core.model.DeckAccessories.Ref;
import com.reflexit.magiccards.core.model.DeckAccessories.Result;

/** Offline tests for {@link DeckAccessories} - decode + per-deck aggregation (db-free path). */
public class DeckAccessoriesTest {

	@Test
	public void decodeSplitsAndTypesEveryChunk() {
		List<Ref> refs = DeckAccessories.decode("ta1b2;c+1/+1;dd20;mmonarch;cenergy");
		Assert.assertEquals(5, refs.size());
		Assert.assertEquals(Kind.TOKEN, refs.get(0).kind);
		Assert.assertEquals("a1b2", refs.get(0).payload);
		Assert.assertEquals(Kind.COUNTER, refs.get(1).kind);
		Assert.assertEquals("+1/+1", refs.get(1).payload);
		Assert.assertEquals(Kind.DIE, refs.get(2).kind);
		Assert.assertEquals(Kind.PLAYER_MARKER, refs.get(3).kind);
	}

	@Test
	public void decodeDropsStaleCounterCodes() {
		// "chad" (Felisa: "if it had counters on it") and "cinstead" (Bane's
		// Contingency: "instead counter that spell") were written by an older
		// extractor with no allow-list; they must not survive a re-read.
		List<Ref> refs = DeckAccessories.decode("ta1b2;chad;c+1/+1;cinstead;cloyalty;cstun");
		List<String> raw = new ArrayList<>();
		for (Ref r : refs)
			raw.add(r.raw);
		Assert.assertTrue(raw.contains("ta1b2"));
		Assert.assertTrue(raw.contains("c+1/+1"));
		Assert.assertTrue(raw.contains("cloyalty"));
		Assert.assertTrue(raw.contains("cstun"));
		Assert.assertFalse(raw.contains("chad"));
		Assert.assertFalse(raw.contains("cinstead"));
	}

	@Test
	public void proseInOracleNeverReachesAResult() {
		Result r = DeckAccessories.compute(java.util.Arrays.asList(phys("Felisa, Fang of Silverquill", 1,
				"Whenever a nontoken creature you control dies, if it had counters on it, create X tokens, "
						+ "where X is the number of counters it had on it.")),
				null);
		Assert.assertTrue(r.isEmpty());
	}

	@Test
	public void accumulatingVsKeywordCountersGoToDifferentSections() {
		List<IMagicCard> deck = new ArrayList<>();
		deck.add(phys("Hardened Scales", 1, "put a +1/+1 counter on target creature"));
		deck.add(phys("Chandra, Spark Hunter", 1, "+1: Draw a card.", "Legendary Planeswalker - Chandra"));
		deck.add(phys("The Wandering Emperor", 1, "put a stun counter on target creature"));
		deck.add(phys("Skrelv, Defector Mite", 1, "put a first strike counter and a shield counter on it"));
		Result r = DeckAccessories.compute(deck, null);
		List<String> counters = new ArrayList<>();
		for (Need n : r.counters)
			counters.add(n.label);
		List<String> keywords = new ArrayList<>();
		for (Need n : r.keywords)
			keywords.add(n.label);
		Assert.assertTrue(counters.contains("+1/+1 counter"));
		Assert.assertTrue(counters.contains("Loyalty counter"));
		// markers are on/off, not accumulating counters - no "counter" suffix on the name
		Assert.assertTrue(keywords.contains("Stun"));
		Assert.assertTrue(keywords.contains("First strike"));
		Assert.assertTrue(keywords.contains("Shield"));
		Assert.assertTrue(r.counters.stream().noneMatch(n -> n.label.contains("Stun")));
	}

	@Test
	public void incompleteFlaggedWhenOracleMakesTokenButNoAllParts() {
		Result r = DeckAccessories.compute(java.util.Arrays.asList(
				phys("Chatterfang, Squirrel General", 1, "create a 1/1 green Squirrel creature token")), null);
		Assert.assertTrue(r.incomplete.contains("Chatterfang, Squirrel General"));
	}

	@Test
	public void noIncompleteWhenOracleDoesNotMakeTokens() {
		Result r = DeckAccessories.compute(java.util.Arrays.asList(phys("Grizzly Bears", 1, "")), null);
		Assert.assertTrue(r.incomplete.isEmpty());
	}

	@Test
	public void noIncompleteForDoublingEffectsThatDoNotMakeTheirOwnToken() {
		Result r = DeckAccessories.compute(java.util.Arrays.asList(phys("Doubling Season", 1,
				"If an effect would create one or more tokens under your control, it creates twice that many of "
						+ "those tokens instead.")),
				null);
		Assert.assertTrue(r.incomplete.isEmpty());
	}

	@Test
	public void noIncompleteForPayoffsThatOnlyCountTokensMadeElsewhere() {
		Result r = DeckAccessories.compute(java.util.Arrays.asList(phys("Ellyn Harbreeze, Busybody", 1,
				"Look at the top X cards of your library, where X is the number of tokens you created this turn.")),
				null);
		Assert.assertTrue(r.incomplete.isEmpty());
	}

	@Test
	public void decodeIgnoresJunkAndNull() {
		Assert.assertTrue(DeckAccessories.decode(null).isEmpty());
		Assert.assertTrue(DeckAccessories.decode("").isEmpty());
		Assert.assertTrue(DeckAccessories.decode(";;x;?;t").isEmpty());
	}

	/** counters / dice / markers are re-derived from the card's oracle text now. */
	private static MagicCardPhysical phys(String name, int count, String oracle) {
		return phys(name, count, oracle, "Creature");
	}

	private static MagicCardPhysical phys(String name, int count, String oracle, String type) {
		MagicCard base = new MagicCard();
		base.setName(name);
		base.setOracleText(oracle);
		base.setType(type);
		MagicCardPhysical phi = new MagicCardPhysical(base, null);
		phi.setCount(count);
		return phi;
	}

	/** a card whose stored ACCESSORIES string carries a token id (needs all_parts). */
	private static MagicCardPhysical physTok(String name, int count, String tokenCodes, String oracle) {
		MagicCardPhysical phi = phys(name, count, oracle);
		phi.getCard().set(MagicCardField.ACCESSORIES, tokenCodes);
		return phi;
	}

	private static List<String> sourceNames(Need n) {
		List<String> names = new ArrayList<>();
		for (IMagicCard c : n.sources)
			names.add(c.getName());
		return names;
	}

	@Test
	public void aggregatesAcrossDeckWithCopiesAndSources() {
		List<IMagicCard> deck = new ArrayList<>();
		deck.add(physTok("Draconautics Engineer", 3, "tDRAGON", "Create a Dragon. Put a +1/+1 counter on this creature."));
		deck.add(phys("Hardened Scales", 4, "If one or more +1/+1 counters would be put on a creature you control..."));

		Result r = DeckAccessories.compute(deck, null); // db-free: tokens fall back to a badge

		Assert.assertEquals(1, r.tokens.size());
		Assert.assertEquals(1, r.counters.size());
		Need plus = r.counters.get(0);
		Assert.assertEquals("+1/+1 counter", plus.label);
		Assert.assertEquals(2, plus.getDeckCards());
		Assert.assertEquals(7, plus.copies);
		Assert.assertTrue(sourceNames(plus).contains("Draconautics Engineer"));
		Assert.assertTrue(sourceNames(plus).contains("Hardened Scales"));
	}

	@Test
	public void samePrintingCardCountedSeparately() {
		// two printings of the same card (e.g. one from ORI, one from DFT) each get
		// their own source/tile - they are never merged, matching the detail panel
		List<IMagicCard> deck = new ArrayList<>();
		deck.add(phys("Hardened Scales", 3, "put a +1/+1 counter"));
		deck.add(phys("Hardened Scales", 2, "put a +1/+1 counter"));
		Result r = DeckAccessories.compute(deck, null);
		Assert.assertEquals(1, r.counters.size());
		Assert.assertEquals(2, r.counters.get(0).getDeckCards());
		Assert.assertEquals(5, r.counters.get(0).copies);
	}

	@Test
	public void tokenIdentityIgnoresSetArtAndReminderText() {
		MagicCard a = new MagicCard();
		a.setName("Beast");
		a.setType("Token Creature - Beast");
		a.setPower("4");
		a.setToughness("4");
		a.setOracleText("");
		MagicCard b = new MagicCard();
		b.setName("Beast");
		b.setType("Token Creature - Beast");
		b.setPower("4");
		b.setToughness("4");
		b.setOracleText("Trample (This creature can deal excess combat damage...)");
		MagicCard c = new MagicCard();
		c.setName("Beast");
		c.setType("Token Creature - Beast");
		c.setPower("4");
		c.setToughness("4");
		c.setOracleText(""); // another set, same token
		Assert.assertEquals(DeckAccessories.identity(a), DeckAccessories.identity(c));
		Assert.assertNotEquals(DeckAccessories.identity(a), DeckAccessories.identity(b)); // trample is a real difference
	}

	@Test
	public void tokenIdentityIgnoresRulingsLinkJunkInOracle() {
		// some DB token records carry a stray per-printing "Rulings" html link
		MagicCard a = new MagicCard();
		a.setName("Beast");
		a.setPower("3");
		a.setToughness("3");
		a.setOracleText("<BR><a href=\"https://api.scryfall.com/cards/01e095bd-091c-442f/rulings\">Rulings</a>");
		MagicCard b = new MagicCard();
		b.setName("Beast");
		b.setPower("3");
		b.setToughness("3");
		b.setOracleText("<BR><a href=\"https://api.scryfall.com/cards/d93d0098-2147-4e84/rulings\">Rulings</a>");
		MagicCard c = new MagicCard();
		c.setName("Beast");
		c.setPower("3");
		c.setToughness("3");
		c.setOracleText("");
		Assert.assertEquals(DeckAccessories.identity(a), DeckAccessories.identity(b));
		Assert.assertEquals(DeckAccessories.identity(a), DeckAccessories.identity(c));
	}

	@Test
	public void loyaltyIsACounterButPlayerCountersGoToPlayerMarkers() {
		List<IMagicCard> deck = new ArrayList<>();
		deck.add(phys("Chandra, Spark Hunter", 1, "+1: Draw a card.", "Legendary Planeswalker - Chandra"));
		deck.add(phys("Aether Hub", 4, "When this enters, you get {E}{E}."));
		deck.add(phys("Mizzix of the Izmagnus", 1, "put an experience counter on it"));
		deck.add(phys("Blightbeetle", 1, "put a poison counter on that player"));
		Result r = DeckAccessories.compute(deck, null);
		Assert.assertTrue(labels(r.counters).contains("Loyalty counter"));
		Assert.assertTrue(labels(r.playerMarkers).contains("Energy"));
		Assert.assertTrue(labels(r.playerMarkers).contains("Experience"));
		Assert.assertTrue(labels(r.playerMarkers).contains("Poison"));
		Assert.assertFalse(labels(r.counters).contains("Energy"));
	}

	private static List<String> labels(List<Need> needs) {
		List<String> l = new ArrayList<>();
		for (Need n : needs)
			l.add(n.label);
		return l;
	}

	@Test
	public void aHelperCardInTheDeckDoesNotReferToItself() {
		MagicCard exp = new MagicCard();
		exp.setName("Experience");
		exp.setType("Card");
		exp.set(MagicCardField.ACCESSORIES, "cexperience");
		MagicCard monarch = new MagicCard();
		monarch.setName("The Monarch");
		monarch.setType("Card");
		monarch.set(MagicCardField.ACCESSORIES, "mmonarch");
		Result r = DeckAccessories.compute(java.util.Arrays.asList(exp, monarch), null);
		Assert.assertTrue(r.isEmpty());
	}
}
