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
package com.reflexit.magiccards.ui.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.reflexit.magiccards.core.model.CardGroup;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCard;

/**
 * The small helpers of the UI plug-in: the text copied for a card, the waiting loops, the order of
 * the set lists and the temporary card of the image lookup. No window is opened. JUnit Plug-in Test (the set order reads the UI
 * plug-in preferences).
 */
public class UtilsTest {
	private static MagicCard card() {
		MagicCard c = new MagicCard();
		c.setCardId(7);
		c.setName("Lightning Bolt");
		c.setCost("{R}");
		c.setType("Instant");
		c.setSet("Alpha");
		c.setRarity("Common");
		c.setOracleText("Deal 3 damage.");
		return c;
	}

	// ---- the text of a card

	@Test
	public void testTheTextOfACard() {
		String nl = TextConvertor.LINE_SEPARATOR;
		String text = TextConvertor.toText(card());
		assertTrue(text, text.startsWith("Lightning Bolt {R}" + nl + "Instant [Alpha - Common]" + nl + "Deal 3 damage." + nl));
		assertTrue(text.endsWith("--------------------------" + nl));
	}

	@Test
	public void testTheTextOfAGroupIsItsName() {
		String nl = TextConvertor.LINE_SEPARATOR;
		assertEquals("Instants" + nl + "--------------------------" + nl,
				TextConvertor.toText(new CardGroup(null, "Instants")));
	}

	@Test
	public void testTheTextOfAnythingElse() {
		String nl = TextConvertor.LINE_SEPARATOR;
		assertEquals("plain" + nl + "--------------------------" + nl, TextConvertor.toText("plain"));
	}

	// ---- waiting

	@Test
	public void testAConditionAlreadyTrueDoesNotWait() {
		long start = System.currentTimeMillis();
		assertTrue(WaitUtils.waitForCondition(() -> true, 5000, 10));
		assertTrue(System.currentTimeMillis() - start < 2000);
	}

	@Test
	public void testAConditionThatBecomesTrueIsWaitedFor() {
		final AtomicInteger calls = new AtomicInteger();
		assertTrue(WaitUtils.waitForCondition(() -> calls.incrementAndGet() >= 3, 5000, 5));
		assertTrue(calls.get() >= 3);
	}

	@Test
	public void testAConditionThatStaysFalseTimesOut() {
		long start = System.currentTimeMillis();
		assertFalse(WaitUtils.waitForCondition(() -> false, 100, 10));
		assertTrue(System.currentTimeMillis() - start >= 100);
	}

	@Test
	public void testTheActionRunsOnlyOnceTheConditionIsTrue() {
		final AtomicInteger ran = new AtomicInteger();
		assertTrue(WaitUtils.runWhenCondition(() -> true, 1000, () -> ran.incrementAndGet()));
		assertEquals(1, ran.get());
		assertFalse(WaitUtils.runWhenCondition(() -> false, 100, () -> ran.incrementAndGet()));
		assertEquals("not run on a timeout", 1, ran.get());
	}

	// ---- the order of the set lists

	@Test
	public void testTheSetListKeepsEveryNameAndIsAlphabeticalByDefault() {
		List<String> names = Arrays.asList("zeta set", "Alpha Set", "beta set", "Gamma Set");
		List<String> sorted = SetListOrder.sort(names);
		assertEquals(4, sorted.size());
		assertTrue(sorted.containsAll(names));
		if (!SetListOrder.byReleaseDate())
			assertEquals(Arrays.asList("Alpha Set", "beta set", "Gamma Set", "zeta set"), sorted);
	}

	@Test
	public void testTheSetListAsAnArray() {
		String[] array = SetListOrder.sortToArray(Arrays.asList("b", "a"));
		assertEquals(2, array.length);
		assertTrue(Arrays.asList(array).contains("a"));
		assertTrue(Arrays.asList(array).contains("b"));
	}

	// ---- the temporary card of the image lookup

	@Test
	public void testTheTemporaryCardKeepsItsIdSetAndLanguage() {
		IMagicCard c = CardImageUI.buildTemporaryCard("42", "M21", "French");
		assertEquals("42", c.getCardId() + "");
		assertEquals("M21", c.getSet());
		assertEquals("French", c.getLanguage());
	}

	@Test
	public void testTheTemporaryCardWithoutALanguageIsEnglish() {
		// English is the default: a card stores no language for it
		assertEquals(null, CardImageUI.buildTemporaryCard("1", "M21", null).getLanguage());
		assertEquals(null, CardImageUI.buildTemporaryCard("1", "M21", "").getLanguage());
		assertEquals(null, CardImageUI.buildTemporaryCard("1", "M21", "English").getLanguage());
	}
}
