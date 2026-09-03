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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decides whether a word that appears before "counter(s)" in a card's rules text
 * names a real counter.
 *
 * <p>
 * There are ~200 named counter types and the list grows with every set, so an
 * allow-list is a maintenance trap and always lags new sets. Instead we
 * <em>reject</em> a small closed list of English function words ("remove
 * <b>another</b> counter", "for <b>each</b> counter", "if it <b>had</b>
 * counters"...) and accept everything else. An audit over the whole card pool
 * showed every non-function word before "counter" is in fact a real counter type
 * (including the joke counters on Un-set cards, which those decks really do need).
 *
 * <p>
 * Counters split into two families that players track differently:
 * <ul>
 * <li>{@link #isKeyword keyword counters} - deathtouch, flying, shield, stun...
 * are on/off; tracked with a marker, never a die.
 * <li>everything else - {@code +1/+1}, lore, charge, loyalty... accumulate and
 * are usually tracked with a die.
 * </ul>
 */
public final class CounterTypes {

	/** {@code +1/+1}, {@code -1/-1}, {@code +0/+2}... - always a real counter. */
	public static final Pattern POWER_TOUGHNESS = Pattern.compile("[+\\-]?\\d+/[+\\-]?\\d+");

	/**
	 * On/off counters - a keyword ability, or a binary replacement effect. Tracked
	 * with a marker, not a die. The bare {@code "strike"} is here for older data
	 * where the two-word "first strike" / "double strike" was captured as one word.
	 */
	public static final Set<String> KEYWORD = unmod("deathtouch", "double strike", "first strike", "strike", "flying",
			"haste", "hexproof", "indestructible", "lifelink", "menace", "reach", "trample", "vigilance", "shield",
			"stun", "finality");

	/**
	 * Function words that turn up before "counter(s)" in rules prose but never
	 * name a counter. Derived from an audit of every card's oracle text.
	 */
	private static final Set<String> PROSE = unmod("a", "an", "the", "this", "that", "these", "those", "another",
			"other", "each", "every", "all", "any", "some", "no", "none", "its", "it's", "their", "his", "her", "our",
			"your", "my", "of", "with", "without", "and", "or", "but", "as", "more", "most", "less", "fewer", "least",
			"many", "much", "additional", "extra", "one", "two", "three", "four", "five", "six", "seven", "eight",
			"nine", "ten", "x", "n", "first", "second", "third", "last", "next", "then", "than", "control", "controls",
			"get", "gets", "have", "has", "had", "having", "number", "amount", "same", "different", "such", "sort",
			"kind", "type", "only", "both", "either", "neither", "target", "targeted", "would", "will", "may", "might",
			"must", "can", "from", "into", "onto", "for", "instead", "day", "when", "where", "which", "whose", "what",
			"how", "if", "is", "are", "was", "were", "be", "been", "put", "puts", "placed", "moved", "removed");

	private CounterTypes() {
	}

	/**
	 * @param name the word(s) captured before "counter(s)", already lower-cased
	 * @return {@code true} if that names a real counter (a P/T counter, or any
	 *         word that is not plain English prose)
	 */
	public static boolean isCounter(String name) {
		if (name == null || name.isEmpty())
			return false;
		return POWER_TOUGHNESS.matcher(name).matches() || !PROSE.contains(name);
	}

	/** @return {@code true} for an on/off counter (keyword ability / binary), tracked with a marker not a die. */
	public static boolean isKeyword(String name) {
		return name != null && KEYWORD.contains(name);
	}

	private static Set<String> unmod(String... items) {
		return java.util.Collections.unmodifiableSet(new HashSet<>(Arrays.asList(items)));
	}
}
