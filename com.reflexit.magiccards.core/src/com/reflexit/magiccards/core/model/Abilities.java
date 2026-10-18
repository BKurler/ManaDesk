/*******************************************************************************
 * Copyright (c) 2008 Alena Laskavaia.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Alena Laskavaia - initial API and implementation
 *    Rémi Dutil (2026) - abilities are what Scryfall says each card has (its keywords array,
 *                        MagicCardField.KEYWORDS), named after Scryfall's keyword abilities,
 *                        ability words and keyword actions (ScryfallReference)
 *******************************************************************************/
package com.reflexit.magiccards.core.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.reflexit.magiccards.core.sync.ScryfallReference;

/**
 * The abilities of a card: its keyword abilities (Flying), ability words (Landfall)
 * and keyword actions (Scry), exactly as Scryfall lists them for the card
 * ({@link MagicCardField#KEYWORDS}) - not guessed from the rules text, so a card that
 * merely mentions "flying" or "scry" does not count.
 * <p>
 * {@link #getNames()} is the full official list (for suggestions); {@link #of} is what
 * one card has. A card's keyword that is not (yet) in the official list is still
 * reported, as Scryfall spells it.
 */
public class Abilities {

	/** Separates the keywords in {@link MagicCardField#KEYWORDS}. */
	public static final char SEPARATOR = '|';

	/** Display name by lower-case name, rebuilt when {@link ScryfallReference} is refreshed. */
	private static Map<String, String> official = Collections.emptyMap();
	private static String[] names = new String[0];
	private static long officialVersion = -1;

	private static synchronized void refresh() {
		long v = ScryfallReference.version();
		if (officialVersion == v)
			return;
		Map<String, String> res = new LinkedHashMap<String, String>();
		String[] catalogs = { ScryfallReference.KEYWORD_ABILITIES, ScryfallReference.ABILITY_WORDS,
				ScryfallReference.KEYWORD_ACTIONS };
		for (String catalog : catalogs) {
			for (String name : ScryfallReference.catalog(catalog)) {
				String key = name.toLowerCase(Locale.ROOT);
				if (!res.containsKey(key))
					res.put(key, name);
			}
		}
		String[] arr = res.values().toArray(new String[res.size()]);
		Arrays.sort(arr, String.CASE_INSENSITIVE_ORDER);
		official = res;
		names = arr;
		officialVersion = v;
	}

	/** @return every official keyword ability, ability word and keyword action, sorted */
	public static synchronized String[] getNames() {
		refresh();
		return names.clone();
	}

	/** @return the official spelling of an ability (any case), or {@code null} if Scryfall does not list it */
	public static synchronized String getAbility(String name) {
		if (name == null)
			return null;
		refresh();
		return official.get(name.trim().toLowerCase(Locale.ROOT));
	}

	/**
	 * @return the abilities of one card, in the order Scryfall lists them, spelled as in
	 *         the official list; empty if it has none (or was not synced since keywords
	 *         were stored)
	 */
	public static Set<String> of(IMagicCard card) {
		if (card == null)
			return Collections.emptySet();
		return parse(card.get(MagicCardField.KEYWORDS));
	}

	/** @param keywords a stored {@link MagicCardField#KEYWORDS} value */
	static Set<String> parse(Object keywords) {
		if (keywords == null)
			return Collections.emptySet();
		String csv = keywords.toString();
		if (csv.isEmpty())
			return Collections.emptySet();
		Set<String> res = new LinkedHashSet<String>();
		for (String k : csv.split(Pattern.quote(String.valueOf(SEPARATOR)))) {
			k = k.trim();
			if (k.isEmpty())
				continue;
			String spelled = getAbility(k);
			res.add(spelled == null ? k : spelled);
		}
		return res;
	}

	/** @return {@code true} if the card has the ability (case-insensitive) */
	public static boolean has(IMagicCard card, String ability) {
		if (ability == null)
			return false;
		for (String a : of(card)) {
			if (a.equalsIgnoreCase(ability.trim()))
				return true;
		}
		return false;
	}

	/**
	 * A pattern that matches a {@link MagicCardField#KEYWORDS} value containing the
	 * ability, for filtering.
	 */
	public static Pattern keywordPattern(String ability) {
		String sep = Pattern.quote(String.valueOf(SEPARATOR));
		return Pattern.compile("(^|" + sep + ")" + Pattern.quote(ability.trim()) + "($|" + sep + ")",
				Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	}

	static class TextSeach implements ISearchableProperty {
		@Override
		public String getIdPrefix() {
			return getFilterField().toString();
		}

		@Override
		public FilterField getFilterField() {
			return FilterField.TEXT_LINE;
		}

		@Override
		public Collection getIds() {
			Collection list = new ArrayList<String>();
			list.add(FilterField.TEXT_LINE);
			list.add(FilterField.TEXT_LINE_2);
			list.add(FilterField.TEXT_LINE_3);
			return list;
		}

		@Override
		public String getNameById(String id) {
			// TODO Auto-generated method stub
			return null;
		}
	}

	static class TextSeachNot implements ISearchableProperty {
		@Override
		public String getIdPrefix() {
			return getFilterField().toString();
		}

		@Override
		public FilterField getFilterField() {
			return FilterField.TEXT_LINE;
		}

		@Override
		public Collection getIds() {
			Collection list = new ArrayList<String>();
			list.add(FilterField.TEXT_NOT_1);
			list.add(FilterField.TEXT_NOT_2);
			list.add(FilterField.TEXT_NOT_3);
			return list;
		}

		@Override
		public String getNameById(String id) {
			// TODO Auto-generated method stub
			return null;
		}
	}

	static ISearchableProperty getTextFields() {
		return new TextSeach();
	}

	static ISearchableProperty getTextNotFields() {
		return new TextSeachNot();
	}
}
