package com.reflexit.magiccards.core.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.regex.Pattern;

import com.reflexit.magiccards.core.locale.CardTextLocal;
import com.reflexit.magiccards.core.locale.LocalizedText;
import com.reflexit.magiccards.core.sync.ScryfallReference;

public class CardTypes implements ISearchableProperty {
	public static CardTextLocal TYPES = CardTextLocal.getCardText(LocalizedText.ENGLISH);

	private CardTypes() {
		this.names = new LinkedHashMap<String, String>();
		add(TYPES.Type_Land);
		add(TYPES.Type_Creature);
		add(TYPES.Type_Instant);
		add(TYPES.Type_Sorcery);
		add(TYPES.Type_Enchantment);
		add(TYPES.Type_Artifact);
		add(TYPES.Type_Planeswalker);
	}

	static CardTypes instance = new CardTypes();
	private LinkedHashMap<String, String> names;

	private void add(String string) {
		String id = getPrefConstant(string);
		this.names.put(id, string);
	}

	public boolean hasType(IMagicCard card, String type) {
		String typeText = card.getType();
		if (containsType(typeText, type))
			return true;
		String language = card.getLanguage();
		if (language != null && language.length() > 0) {
			CardTextLocal localized = CardTextLocal.getCardText(language);
			String localizedType = TYPES.translate(type, localized);
			if (localizedType != null && containsType(typeText, localizedType))
				return true;
		}
		if (type == TYPES.Type_Creature) {
			return hasType(card, TYPES.Type_Summon);
		}
		if (type == TYPES.Type_Instant) {
			return hasType(card, TYPES.Type_Interrupt);
		}
		return false;
	}

	private boolean containsType(String text, String type) {
		if (text == null)
			return false;
		return Pattern.compile(type, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text).find();
	}

	@Override
	public String getIdPrefix() {
		return getFilterField().toString();
	}

	@Override
	public FilterField getFilterField() {
		return FilterField.CARD_TYPE;
	}

	public static CardTypes getInstance() {
		return instance;
	}

	@Override
	public Collection<String> getIds() {
		return new ArrayList<String>(this.names.keySet());
	}

	public String getPrefConstant(String name) {
		return FilterField.getPrefConstant(getIdPrefix(), name);
	}

	@Override
	public String getNameById(String id) {
		return this.names.get(id);
	}

	public String getLocalizedNameById(String id) {
		String enName = getNameById(id);
		return TYPES.translate(enName, Locale.getDefault());
	}

	private static final String[] PROPOSAL_CATALOGS = { ScryfallReference.SUPERTYPES, ScryfallReference.CARD_TYPES,
			ScryfallReference.BATTLE_TYPES, ScryfallReference.ARTIFACT_TYPES, ScryfallReference.ENCHANTMENT_TYPES,
			ScryfallReference.LAND_TYPES, ScryfallReference.SPELL_TYPES, ScryfallReference.CREATURE_TYPES,
			ScryfallReference.PLANESWALKER_TYPES };
	private static String[] proposals = new String[0];
	private static long proposalsVersion = -1;

	/**
	 * Every supertype, card type and subtype Scryfall knows (read from
	 * {@link ScryfallReference}, so it grows with the game), for auto-completion.
	 * Each group is sorted; a word that is in several groups appears once.
	 */
	public static synchronized String[] getProposals() {
		long v = ScryfallReference.version();
		if (proposalsVersion != v) {
			proposals = buildProposals();
			proposalsVersion = v;
		}
		return proposals;
	}

	static String[] buildProposals() {
		LinkedHashMap<String, String> res = new LinkedHashMap<String, String>();
		for (String catalog : PROPOSAL_CATALOGS) {
			ArrayList<String> group = new ArrayList<String>(ScryfallReference.catalog(catalog));
			Collections.sort(group, String.CASE_INSENSITIVE_ORDER);
			for (String s : group) {
				String key = s.toLowerCase(Locale.ROOT);
				if (!res.containsKey(key))
					res.put(key, s);
			}
		}
		return res.values().toArray(new String[res.size()]);
	}

	public Collection<String> getLocalizedNames() {
		ArrayList<String> names2 = new ArrayList<String>();
		for (Iterator<String> iterator = names.values().iterator(); iterator.hasNext();) {
			String string = iterator.next();
			names2.add(TYPES.translate(string, Locale.getDefault()));
		}
		return names2;
	}
}
