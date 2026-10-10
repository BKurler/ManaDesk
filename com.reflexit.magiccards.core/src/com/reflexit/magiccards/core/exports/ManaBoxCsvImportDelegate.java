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
package com.reflexit.magiccards.core.exports;

import java.time.Instant;
import java.util.Currency;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.reflexit.magiccards.core.MagicException;
import com.reflexit.magiccards.core.model.CardCondition;
import com.reflexit.magiccards.core.model.CardFinish;
import com.reflexit.magiccards.core.model.Languages;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.sync.CurrencyConvertor;

/**
 * Imports a ManaBox CSV export (a whole collection, or one binder / list /
 * deck). Header, as ManaBox writes it (older exports lack Signed and Proxy;
 * a collection export starts with Binder Name, Binder Type):
 *
 * <pre>
 * Name,Set code,Set name,Collector number,Foil,Rarity,Quantity,ManaBox ID,Scryfall ID,Purchase price,
 * Misprint,Altered,Signed,Condition,Language,Proxy,Purchase price currency,Added
 * </pre>
 *
 * The Scryfall ID is the ManaDesk card id, so the import pipeline resolves each
 * row to its exact printing first, and falls back to Name / Set / Collector
 * number only when there is no id. Mapping:
 * <ul>
 * <li>Foil (normal / foil / etched) -&gt; Finish;</li>
 * <li>Condition (near_mint, excellent, good, light_played, played, poor)
 * -&gt; Condition (see {@link #condition});</li>
 * <li>Language (en, fr...): the card stays English - ManaBox gives the
 * English printing's Scryfall ID, and the card database only has English
 * printings; a non-English language is reported in the preview
 * ({@link ImportData#declareLanguage});</li>
 * <li>Added (when the card was added to ManaBox) -&gt; the copy's Date;</li>
 * <li>Purchase price -&gt; User Price, converted from the Purchase price
 * currency into ManaDesk's currency when they differ and a rate is known;
 * Proxy -&gt; Proxy;</li>
 * <li>Misprint / Altered / Signed = true -&gt; a "misprint" / "altered" /
 * "signed" Special tag;</li>
 * <li>Binder Name / Type, Rarity, ManaBox ID are not imported.</li>
 * </ul>
 * Headers are matched by name, in any order; a column ManaBox adds later is
 * ignored. A file without a "Scryfall ID" or "ManaBox ID" column is not a
 * ManaBox export, and is rejected so another importer gets a go.
 */
public class ManaBoxCsvImportDelegate extends CsvImportDelegate {
	/** Special tags for ManaBox's true / false columns. */
	enum FlagTag implements ICardField {
		MISPRINT("misprint"), ALTERED("altered"), SIGNED("signed");

		final String tag;

		FlagTag(String tag) {
			this.tag = tag;
		}

		@Override
		public boolean isTransient() {
			return true;
		}

		@Override
		public String getLabel() {
			return tag;
		}

		@Override
		public Object aggregateValueOf(com.reflexit.magiccards.core.model.abs.ICard card) {
			return null;
		}
	}

	/** Lower-cased ManaBox header -&gt; field ({@code null}: known, not imported). */
	private static final Map<String, ICardField> HEADERS = new LinkedHashMap<>();
	static {
		HEADERS.put("name", MagicCardField.NAME);
		HEADERS.put("set code", MagicCardField.EDITION_ABBR);
		HEADERS.put("set name", MagicCardField.SET);
		HEADERS.put("collector number", MagicCardField.COLLNUM);
		HEADERS.put("foil", MagicCardField.FINISH);
		HEADERS.put("quantity", MagicCardField.COUNT);
		HEADERS.put("scryfall id", MagicCardField.ID);
		HEADERS.put("purchase price", MagicCardField.PRICE);
		HEADERS.put("misprint", FlagTag.MISPRINT);
		HEADERS.put("altered", FlagTag.ALTERED);
		HEADERS.put("signed", FlagTag.SIGNED);
		HEADERS.put("condition", MagicCardField.CONDITION);
		HEADERS.put("language", MagicCardField.LANG);
		HEADERS.put("proxy", MagicCardField.PROXY);
		HEADERS.put("added", MagicCardField.DATE);
	}

	/** Column of the Purchase price currency, or -1. */
	private int currencyColumn = -1;

	@Override
	protected void setHeaderFields(List<String> list) {
		currencyColumn = -1;
		boolean manaBox = false;
		boolean hasSetCode = false;
		for (String raw : list) {
			String key = norm(raw);
			manaBox |= key.equals("scryfall id") || key.equals("manabox id");
			hasSetCode |= key.equals("set code");
		}
		if (!manaBox)
			throw new MagicException("Not a ManaBox CSV: no 'Scryfall ID' or 'ManaBox ID' column (" + list + ")");
		ICardField[] fields = new ICardField[list.size()];
		for (int i = 0; i < fields.length; i++) {
			ICardField f = HEADERS.get(norm(list.get(i)));
			// the set code is the exact one; the set name only when there is no code
			if (f == MagicCardField.SET && hasSetCode)
				f = null;
			fields[i] = f;
			if (norm(list.get(i)).equals("purchase price currency"))
				currencyColumn = i;
		}
		setFields(fields);
	}

	@Override
	protected ICardField[] getExampleFields() {
		return new ICardField[] { MagicCardField.NAME, MagicCardField.EDITION_ABBR, MagicCardField.COLLNUM,
				MagicCardField.FINISH, MagicCardField.COUNT, MagicCardField.ID, MagicCardField.CONDITION,
				MagicCardField.LANG };
	}

	@Override
	public String getExample() {
		return "Name,Set code,Set name,Collector number,Foil,Rarity,Quantity,ManaBox ID,Scryfall ID,Purchase price,"
				+ "Misprint,Altered,Signed,Condition,Language,Proxy,Purchase price currency,Added\n"
				+ "Kaervek the Merciless,DSC,Duskmourn: House of Horror Commander,222,normal,rare,1,99260,"
				+ "95c5bd95-b483-4dfb-9010-cfee47d22468,0.36,false,false,false,near_mint,en,false,USD,"
				+ "2026-10-10T19:29:49.920Z\n"
				+ "\"Atraxa, Praetors' Voice\",MUL,Multiverse Legends,98,etched,mythic,6,82156,"
				+ "cf7e8917-8bd1-4899-bc78-7a5815c08057,40.39,false,false,false,near_mint,en,false,USD,"
				+ "2026-10-10T19:29:49.911Z\n";
	}

	@Override
	protected MagicCardPhysical createCard(List<String> list) {
		// tolerate blank lines
		boolean empty = true;
		for (String v : list)
			empty &= v == null || v.trim().isEmpty();
		if (empty)
			return null;
		MagicCardPhysical card = super.createCard(list);
		if (card != null && currencyColumn >= 0 && currencyColumn < list.size())
			convertPrice(card, list.get(currencyColumn));
		return card;
	}

	/** The purchase price in ManaDesk's currency (unchanged when no rate is known). */
	private static void convertPrice(MagicCardPhysical card, String currencyCode) {
		float price = card.getPrice();
		if (price <= 0 || currencyCode == null || currencyCode.trim().isEmpty())
			return;
		try {
			Currency from = Currency.getInstance(currencyCode.trim().toUpperCase(Locale.ENGLISH));
			Currency to = CurrencyConvertor.getCurrency();
			if (to != null && !from.equals(to))
				card.setPrice(CurrencyConvertor.convertFromInto(price, from, to));
		} catch (RuntimeException e) {
			// unknown currency or no rate (offline): keep the price as written
		}
	}

	@Override
	public void setFieldValue(MagicCardPhysical card, ICardField field, int i, String value) {
		if (field == null)
			return;
		if (field instanceof FlagTag) {
			if (Boolean.parseBoolean(value.trim()))
				card.setSpecialTag(((FlagTag) field).tag);
			return;
		}
		if (field == MagicCardField.FINISH) {
			CardFinish f = CardFinish.resolve(value);
			if (f != null)
				card.setFinish(f);
			return;
		}
		if (field == MagicCardField.CONDITION) {
			card.setCondition(condition(value));
			return;
		}
		if (field == MagicCardField.LANG) {
			// ManaBox gives the English printing's Scryfall ID even for a foreign
			// copy, and the card database only has English printings: the card
			// stays English, the file's language is reported in the preview
			String name = Languages.fromScryfallCode(value);
			importData.declareLanguage(card, name != null ? name : value);
			return;
		}
		if (field == MagicCardField.DATE) {
			try {
				card.setDate(Date.from(Instant.parse(value.trim())));
			} catch (RuntimeException e) {
				// not an ISO timestamp - keep the copy's own date
			}
			return;
		}
		if (field == MagicCardField.PROXY) {
			card.setProxy(Boolean.parseBoolean(value.trim()));
			return;
		}
		super.setFieldValue(card, field, i, value);
	}

	/**
	 * ManaBox grades like Cardmarket - mint, near_mint, excellent, good,
	 * light_played, played, poor - mapped to the closest ManaDesk grade:
	 * mint / near_mint -&gt; NM, excellent -&gt; LP, good / light_played -&gt;
	 * MP, played -&gt; HP, poor -&gt; DMG. ManaDesk's own names (lightly_played,
	 * ...) are accepted too. Blank / unknown: not graded.
	 */
	static CardCondition condition(String value) {
		CardCondition c = CardCondition.resolve(value);
		if (c != null)
			return c;
		switch (value == null ? "" : value.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z]", "")) {
		case "excellent":
			return CardCondition.LIGHTLY_PLAYED;
		case "good":
		case "lightplayed":
			return CardCondition.MODERATELY_PLAYED;
		case "played":
			return CardCondition.HEAVILY_PLAYED;
		case "poor":
			return CardCondition.DAMAGED;
		default:
			return null;
		}
	}

	private static String norm(String header) {
		return header == null ? "" : header.trim().toLowerCase(Locale.ENGLISH);
	}
}
