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
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.reflexit.magiccards.core.model.CounterTypes;

/**
 * Works out the physical "accessories" a card needs to be played - tokens and
 * emblems (from Scryfall {@code all_parts}), and counters / dice / status
 * markers (from the rules text). The result is a compact {@code ;}-separated
 * string stored on the card:
 *
 * <pre>
 *   t&lt;scryfall-id&gt;   token or emblem card
 *   c&lt;name&gt;          counter type   e.g. c+1/+1, c-1/-1, cstun, cpoison, cenergy, cloyalty
 *   d&lt;die&gt;           die / coin     e.g. dd20, dd6, dcoin
 *   m&lt;name&gt;          status marker  e.g. mmonarch, minitiative, mring, mdaynight, mcitysblessing
 * </pre>
 */
public final class AccessoryExtractor {

	// A "+N/+N counter" or a single "<word> counter". Multi-word counter types
	// (only "first strike" / "double strike" exist - every named counter is one
	// word, every other keyword counter is one word) are matched separately, by
	// exact phrase, from CounterTypes.KEYWORD.
	private static final Pattern COUNTER = Pattern.compile("([+\\-]?\\d+/[+\\-]?\\d+|[a-z][a-z'-]{1,18}) counters?\\b");
	private static final Pattern DIE = Pattern.compile("roll(?:s)? (?:a |one |two |three |\\d+ )?(d\\d+)");

	private AccessoryExtractor() {
	}

	/** @return the encoded accessory string, or {@code null} when the card needs nothing. */
	public static String extract(JSONObject elem) {
		if (elem == null)
			return null;
		// The Monarch, City's Blessing, Experience, Poison Counter... are helper
		// "Card"-type objects - they ARE accessories and must not carry their own.
		if ("card".equals(String.valueOf(elem.get("type_line")).trim().toLowerCase(Locale.ROOT)))
			return null;
		Set<String> acc = new LinkedHashSet<>();

		// --- tokens & emblems: Scryfall all_parts ---
		// Tokens are component == "token". Emblems are inconsistent: sometimes
		// "token", sometimes "combo_piece" - so also keep anything whose
		// type_line says "Emblem". A bare "combo_piece" is noise (related combo
		// cards, the card's own other printings) and is ignored.
		String rootId = String.valueOf(elem.get("id"));
		Object apo = elem.get("all_parts");
		if (apo instanceof JSONArray) {
			for (Object o : (JSONArray) apo) {
				if (!(o instanceof JSONObject))
					continue;
				JSONObject part = (JSONObject) o;
				Object id = part.get("id");
				if (id == null || rootId.equals(String.valueOf(id)))
					continue;
				// The reliable signal is the related card's own type line: real
				// token objects are "Token Creature - X" etc, emblems are
				// "Emblem". A "combo_piece" pointing at a normal card (the card's
				// other face, a combo partner) has a normal type line and is
				// dropped.
				String tl = String.valueOf(part.get("type_line")).toLowerCase(Locale.ROOT);
				if (tl.contains("token") || tl.contains("emblem"))
					acc.add("t" + id);
			}
		}

		// --- everything else: from the rules text ---
		// Scan every face: transform / MDFC / adventure / split all carry the
		// relevant text (and sometimes the planeswalker type) only on a face,
		// and a modal DFC often has no top-level oracle_text at all.
		StringBuilder txt = new StringBuilder();
		StringBuilder typ = new StringBuilder();
		// Faces are separated by a sentence break ( . ) so the last word of one
		// face cannot fuse with the first word of the next - e.g. "Flying,
		// trample" // "Counter target ..." must not read as a "trample counter".
		appendSentence(txt, elem.get("oracle_text"));
		append(typ, elem.get("type_line"));
		Object faces = elem.get("card_faces");
		if (faces instanceof JSONArray) {
			for (Object f : (JSONArray) faces) {
				if (f instanceof JSONObject) {
					appendSentence(txt, ((JSONObject) f).get("oracle_text"));
					append(typ, ((JSONObject) f).get("type_line"));
				}
			}
		}
		String text = txt.toString().toLowerCase(Locale.ROOT);
		String typeLine = typ.toString().toLowerCase(Locale.ROOT);

		fromText(acc, text, typeLine);
		return acc.isEmpty() ? null : String.join(";", acc);
	}

	/**
	 * Run the counter / dice / marker rules on a card's rules text. Public so the
	 * accessories view can re-derive these live from a card instead of trusting a
	 * possibly-stale stored value; token / emblem ids still come from the split.
	 *
	 * @param text     the card's oracle text (all faces), lower-cased
	 * @param typeLine the card's type line, lower-cased
	 */
	public static void fromText(Set<String> acc, String text, String typeLine) {
		if (text == null)
			text = "";
		if (typeLine == null)
			typeLine = "";

		// multi-word keyword counters, by exact phrase ("first strike counter"...)
		for (String kw : CounterTypes.KEYWORD)
			if (kw.indexOf(' ') > 0 && text.contains(kw + " counter"))
				acc.add("c" + kw);
		// single-word / P/T counters - a real counter type, not prose ("remove
		// that counter", "for each counter"...). "strike" alone is always the
		// tail of "first/double strike" handled above.
		Matcher m = COUNTER.matcher(text);
		while (m.find()) {
			String name = m.group(1).trim();
			if (!name.equals("strike") && CounterTypes.isCounter(name))
				acc.add("c" + name);
		}
		// planeswalkers always need loyalty counters
		if (typeLine.contains("planeswalker"))
			acc.add("cloyalty");
		// energy (has its own helper card)
		if (text.contains("{e}"))
			acc.add("cenergy");

		// dice & coins
		m = DIE.matcher(text);
		while (m.find())
			acc.add("d" + m.group(1));
		if (text.contains("flip a coin") || text.contains("flip that many coins")
				|| text.contains("flip a number of coins"))
			acc.add("dcoin");

		// status markers
		if (text.contains("the monarch"))
			acc.add("mmonarch");
		if (text.contains("the initiative"))
			acc.add("minitiative");
		if (text.contains("the ring tempts you") || text.contains("ring-bearer")
				|| text.contains("your ring-bearer"))
			acc.add("mring");
		if (text.contains("daybound") || text.contains("nightbound") || text.contains("it becomes day")
				|| text.contains("it becomes night") || text.contains("if it's night") || text.contains("if it's day"))
			acc.add("mdaynight");
		if (text.contains("city's blessing"))
			acc.add("mcitysblessing");
	}

	private static void append(StringBuilder sb, Object s) {
		if (s != null) {
			sb.append(' ').append(s);
		}
	}

	/** Append with a leading sentence break so words don't fuse across the boundary. */
	private static void appendSentence(StringBuilder sb, Object s) {
		if (s != null) {
			sb.append(" . ").append(s);
		}
	}
}
