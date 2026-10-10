/*
 * Contributors:
 *     Rémi Dutil (2026) - fromScryfallCode() / toScryfallCode(): the language
 *                         codes Scryfall (and ManaBox) use ("en", "zhs"...)
 */
package com.reflexit.magiccards.core.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;

import com.reflexit.magiccards.core.locale.LocalizedText;

public class Languages implements ISearchableProperty {
	public static enum Language {
		ENGLISH(LocalizedText.ENGLISH), RUSSIAN(LocalizedText.RUSSIAN), FRENCH(LocalizedText.FRENCH),
		SPANISH(LocalizedText.SPANISH), GERMAN(LocalizedText.GERMAN), ITALIAN(LocalizedText.ITALIAN),
		PORTUGUESE(LocalizedText.PORTUGUESE), JAPANESE(LocalizedText.JAPANESE),
		CHINESE_SIMPLIFIED("Chinese Simplified", LocalizedText.CHINESE_SIMPLIFIED),
		CHINESE_TRADITIONAL("Chinese Traditional", LocalizedText.CHINESE_TRADITIONAL), KOREAN(LocalizedText.KOREAN),
		HEBREW(LocalizedText.ENGLISH), // Partially supported
		LATIN(LocalizedText.ENGLISH), // Partially supported
		ANCIENT_GREEK("Ancient Greek", LocalizedText.ENGLISH), // Partially supported
		ARABIC(LocalizedText.ENGLISH), // Partially supported
		SANSKRIT(LocalizedText.ENGLISH), // Partially supported
		PHYREXIAN(LocalizedText.ENGLISH), // Partially supported
		QUENYA(LocalizedText.ENGLISH); // Partially supported

		private String lang;
		private Locale locale;

		Language(Locale locale) {
			this.lang = name().substring(0, 1).toUpperCase() + name().substring(1).toLowerCase();
			this.locale = locale;
		}

		Language(String name, Locale locale) {
			this.lang = name;
			this.locale = locale;
		}

		public String getLang() {
			return lang;
		}

		public Locale getLocale() {
			return locale;
		}

		public static Language fromName(String s) {
			Language[] values = Language.values();
			for (int i = 0; i < values.length; i++) {
				Language language = values[i];
				if (language.getLang().equals(s))
					return language;
			}
			if (s.equals("Chinese Standard"))
				return CHINESE_SIMPLIFIED;
			return null;
		}

		public static Language fromLocale(String s) {
			Language[] values = Language.values();
			for (int i = 0; i < values.length; i++) {
				Language language = values[i];
				if (language.getLocale().getLanguage().equals(s))
					return language;
			}
			return null;
		}
	}

	private LinkedHashMap names;

	/** Scryfall language codes, in Language order. */
	private static final String[][] SCRYFALL_CODES = { { "en", "English" }, { "es", "Spanish" },
			{ "fr", "French" }, { "de", "German" }, { "it", "Italian" }, { "pt", "Portuguese" },
			{ "ja", "Japanese" }, { "ko", "Korean" }, { "ru", "Russian" }, { "zhs", "Chinese Simplified" },
			{ "zht", "Chinese Traditional" }, { "he", "Hebrew" }, { "la", "Latin" }, { "grc", "Ancient Greek" },
			{ "ar", "Arabic" }, { "sa", "Sanskrit" }, { "ph", "Phyrexian" }, { "qya", "Quenya" } };

	/** The ManaDesk language name ("English") for a Scryfall code ("en"), or {@code null}. */
	public static String fromScryfallCode(String code) {
		if (code == null)
			return null;
		String c = code.trim().toLowerCase(Locale.ENGLISH);
		for (String[] pair : SCRYFALL_CODES)
			if (pair[0].equals(c))
				return pair[1];
		return null;
	}

	/** The Scryfall code ("en") for a ManaDesk language name ("English"), or {@code null}. */
	public static String toScryfallCode(String name) {
		if (name == null)
			return null;
		for (String[] pair : SCRYFALL_CODES)
			if (pair[1].equalsIgnoreCase(name.trim()))
				return pair[0];
		return null;
	}

	public static String[] getLangValues() {
		Language[] values = Language.values();
		String[] res = new String[values.length];
		for (int i = 0; i < values.length; i++) {
			Language language = values[i];
			res[i] = language.getLang();
		}
		return res;
	}

	private static Languages instance = new Languages();

	private Languages() {
		this.names = new LinkedHashMap();
		Language[] langs = Language.values();
		for (int i = 0; i < langs.length; i++) {
			Language lang = langs[i];
			add(lang.getLang());
		}
	}

	private void add(String string) {
		String id = getPrefConstant(string);
		this.names.put(id, string);
	}

	public static Languages getInstance() {
		return instance;
	}

	@Override
	public String getIdPrefix() {
		return getFilterField().toString();
	}

	@Override
	public FilterField getFilterField() {
		return FilterField.LANG;
	}

	public Collection getNames() {
		return new ArrayList(this.names.values());
	}

	@Override
	public Collection getIds() {
		return new ArrayList(this.names.keySet());
	}

	public String getPrefConstant(String name) {
		return FilterField.getPrefConstant(getIdPrefix(), name);
	}

	@Override
	public String getNameById(String id) {
		return (String) this.names.get(id);
	}

	public static Locale getLocale(String language) {
		Language[] array = Language.values();
		for (int i = 0; i < array.length; i++) {
			Language l = array[i];
			if (l.getLang().equals(language))
				return l.locale;
		}
		if (language.equals("*"))
			return Locale.ENGLISH;
		throw new IllegalArgumentException("Language Not Found: " + language);
	}
}
