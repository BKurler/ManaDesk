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
package com.reflexit.magiccards.core.sync;

import java.util.LinkedHashSet;
import java.util.Set;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Assert;
import org.junit.Test;

/** Offline tests for {@link AccessoryExtractor} - token/emblem + counter/dice/marker detection. */
public class AccessoryExtractorTest {

	private static Set<String> text(String oracle, String typeLine) {
		Set<String> acc = new LinkedHashSet<>();
		AccessoryExtractor.fromText(acc, oracle.toLowerCase(), typeLine.toLowerCase());
		return acc;
	}

	@Test
	public void plusOnePlusOneCounter() {
		Assert.assertTrue(text("When this enters, put a +1/+1 counter on target creature.", "Creature")
				.contains("c+1/+1"));
	}

	@Test
	public void minusOneMinusOneCounter() {
		Assert.assertTrue(text("Put two -1/-1 counters on each creature.", "Sorcery").contains("c-1/-1"));
	}

	@Test
	public void namedCounters() {
		Set<String> a = text("Tap target creature. It gains a stun counter. Put an oil counter on it.", "Instant");
		Assert.assertTrue(a.contains("cstun"));
		Assert.assertTrue(a.contains("coil"));
	}

	@Test
	public void counterAsVerbIsNotACounter() {
		// "counter target spell" must not be read as an "X counter"
		Set<String> a = text("Counter target spell.", "Instant");
		Assert.assertTrue(a.isEmpty());
	}

	@Test
	public void stopwordsFiltered() {
		Set<String> a = text("Remove a counter from that permanent. Move the counter to another permanent.", "Instant");
		Assert.assertFalse(a.contains("ca"));
		Assert.assertFalse(a.contains("cthe"));
		Assert.assertFalse(a.contains("cthat"));
		Assert.assertFalse(a.contains("canother"));
	}

	@Test
	public void realCountersKeptProseDropped() {
		// real named counters survive - including ones from recent sets
		Assert.assertTrue(text("Put a charge counter on it.", "Artifact").contains("ccharge"));
		Assert.assertTrue(text("It enters with a shield counter.", "Creature").contains("cshield"));
		Assert.assertTrue(text("Put a shadow counter on target creature.", "Instant").contains("cshadow"));
		Assert.assertTrue(text("Put a plan counter on this enchantment.", "Enchantment").contains("cplan"));
		// English function words before "counter(s)" are prose, not counters
		Set<String> a = text("Remove another counter. For each counter, draw a card. "
				+ "If it had counters on it, move that counter. Counter target spell instead.", "Instant");
		Assert.assertFalse(a.contains("canother"));
		Assert.assertFalse(a.contains("ceach"));
		Assert.assertFalse(a.contains("chad"));
		Assert.assertFalse(a.contains("cthat"));
		Assert.assertFalse(a.contains("cinstead"));
	}

	@Test
	public void multiWordKeywordCounter() {
		Assert.assertTrue(text("Put a first strike counter on target creature.", "Instant").contains("cfirst strike"));
		Assert.assertTrue(text("It enters with a double strike counter.", "Creature").contains("cdouble strike"));
	}

	@Test
	public void prosePhrasesBeforeCounterAreDropped() {
		// Felisa, Fang of Silverquill: "...if it had counters on it... the number of counters it had on it."
		Set<String> a = text("Whenever a nontoken creature you control dies, if it had counters on it, "
				+ "create X tokens, where X is the number of counters it had on it.", "Legendary Creature");
		Assert.assertFalse(a.contains("chad"));
		Assert.assertFalse(a.contains("cof"));
		Assert.assertFalse(a.contains("cnontoken"));
		// Bane's Contingency: "...instead counter that spell, scry 2..."
		Set<String> b = text("Counter target spell. If that spell targets a commander you control, "
				+ "instead counter that spell, scry 2, then draw a card.", "Instant");
		Assert.assertTrue(b.isEmpty());
	}

	@Test
	public void planeswalkerNeedsLoyalty() {
		Assert.assertTrue(text("+1: Draw a card.", "Legendary Planeswalker - Jace").contains("cloyalty"));
	}

	@Test
	public void dieRolls() {
		Assert.assertTrue(text("Roll a d20.", "Sorcery").contains("dd20"));
		Assert.assertTrue(text("Roll two d6 and note the results.", "Instant").contains("dd6"));
	}

	@Test
	public void coinFlip() {
		Assert.assertTrue(text("Flip a coin. If you win the flip, draw a card.", "Instant").contains("dcoin"));
	}

	@Test
	public void energySymbol() {
		Assert.assertTrue(text("When this enters, you get {E}{E}.", "Artifact Creature").contains("cenergy"));
	}

	@Test
	public void monarchAndInitiative() {
		Assert.assertTrue(text("When this enters, you become the monarch.", "Creature").contains("mmonarch"));
		Assert.assertTrue(text("Whenever this attacks, you take the initiative.", "Creature").contains("minitiative"));
	}

	@Test
	public void theRing() {
		Assert.assertTrue(text("When this enters, the Ring tempts you.", "Legendary Creature").contains("mring"));
	}

	@Test
	public void daybound() {
		Assert.assertTrue(text("Daybound (If a player casts no spells during their own turn, it becomes night next turn.)",
				"Creature").contains("mdaynight"));
	}

	@Test
	public void extractTokensFromAllParts() throws Exception {
		String json = "{\"id\":\"root-1\",\"type_line\":\"Creature\",\"oracle_text\":\"Create a 1/1 Soldier token.\","
				+ "\"all_parts\":["
				+ "{\"object\":\"related_card\",\"id\":\"root-1\",\"component\":\"combo_piece\",\"type_line\":\"Creature\"},"
				+ "{\"object\":\"related_card\",\"id\":\"tok-soldier\",\"component\":\"token\",\"type_line\":\"Token Creature\"},"
				+ "{\"object\":\"related_card\",\"id\":\"combo-x\",\"component\":\"combo_piece\",\"type_line\":\"Sorcery\"},"
				+ "{\"object\":\"related_card\",\"id\":\"emb-1\",\"component\":\"token\",\"type_line\":\"Emblem\"},"
				+ "{\"object\":\"related_card\",\"id\":\"emb-2\",\"component\":\"combo_piece\",\"type_line\":\"Emblem\"}]}";
		JSONObject elem = (JSONObject) new JSONParser().parse(json);
		String enc = AccessoryExtractor.extract(elem);
		Assert.assertNotNull(enc);
		Set<String> parts = new LinkedHashSet<>(java.util.Arrays.asList(enc.split(";")));
		Assert.assertTrue("keeps token component", parts.contains("ttok-soldier"));
		Assert.assertTrue("keeps emblem (token component)", parts.contains("temb-1"));
		Assert.assertTrue("keeps emblem tagged combo_piece", parts.contains("temb-2"));
		Assert.assertFalse("drops plain combo_piece", parts.contains("tcombo-x"));
		Assert.assertFalse("drops self-reference", parts.contains("troot-1"));
	}

	@Test
	public void twoFacedCardScansEveryFace() throws Exception {
		// modal DFC: no top-level oracle_text/type_line, planeswalker + token only on a face
		String json = "{\"id\":\"mdfc-1\",\"layout\":\"modal_dfc\",\"card_faces\":["
				+ "{\"name\":\"A\",\"type_line\":\"Creature - Elf\",\"oracle_text\":\"Vigilance.\"},"
				+ "{\"name\":\"B\",\"type_line\":\"Legendary Planeswalker - X\","
				+ "\"oracle_text\":\"+1: Create a 1/1 Bird token with flying.\"}]}";
		JSONObject elem = (JSONObject) new JSONParser().parse(json);
		String enc = AccessoryExtractor.extract(elem);
		Assert.assertNotNull(enc);
		Assert.assertTrue(enc.contains("cloyalty"));
	}

	@Test
	public void adventureAdventureSideCounted() {
		// counter appears only on the adventure half - both faces get scanned
		Assert.assertTrue(text("Bear Cub 2/2. // Adventure - Put a +1/+1 counter on target creature.",
				"Creature - Bear // Sorcery - Adventure").contains("c+1/+1"));
	}

	@Test
	public void wordsDoNotFuseAcrossFaceBoundary() throws Exception {
		// Emerald Dragon "Flying, trample" // Dissonant Wave "Counter target ability" -
		// must NOT read as a "trample counter"
		String json = "{\"id\":\"ed\",\"layout\":\"adventure\",\"card_faces\":["
				+ "{\"name\":\"Emerald Dragon\",\"type_line\":\"Creature - Dragon\",\"oracle_text\":\"Flying, trample\"},"
				+ "{\"name\":\"Dissonant Wave\",\"type_line\":\"Instant - Adventure\","
				+ "\"oracle_text\":\"Counter target activated or triggered ability from a noncreature source.\"}]}";
		JSONObject elem = (JSONObject) new JSONParser().parse(json);
		Assert.assertNull(AccessoryExtractor.extract(elem));
	}

	@Test
	public void bareStrikeIsAKeywordCounterForOlderData() {
		// pre-fix data captured "first/double strike counter" as just "strike"
		Assert.assertTrue(com.reflexit.magiccards.core.model.CounterTypes.isKeyword("strike"));
		Assert.assertTrue(com.reflexit.magiccards.core.model.CounterTypes.isKeyword("double strike"));
	}

	@Test
	public void nothingNeededIsNull() throws Exception {
		JSONObject elem = (JSONObject) new JSONParser()
				.parse("{\"id\":\"x\",\"type_line\":\"Land\",\"oracle_text\":\"{T}: Add {G}.\"}");
		Assert.assertNull(AccessoryExtractor.extract(elem));
	}
}
