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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.reflexit.magiccards.core.MagicException;
import com.reflexit.magiccards.core.model.CardFinish;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICardField;

/**
 * Imports a Moxfield collection CSV (the export of a Moxfield collection, or the file its own import accepts):
 *
 * <pre>
 * Count,Tradelist Count,Name,Edition,Condition,Language,Foil,Tags,Last Modified,Collector Number,Alter,Proxy,
 * Purchase Price
 * </pre>
 *
 * The columns are matched by name, in any order, and the ones ManaDesk has no use for (Last Modified, Playtest
 * Card...) are ignored. Mapping:
 * <ul>
 * <li>Count -&gt; Count; Tradelist Count -&gt; how many of them are for trade;</li>
 * <li>Edition (Moxfield's set code, any case) and Collector Number -&gt; the printing;</li>
 * <li>Condition (Mint, Near Mint, Good (Lightly Played), Played, Heavily Played, Damaged) -&gt; Condition, as the
 * Deckbox ones;</li>
 * <li>Language: the card stays English when the database has no printing in that language, the file's language is
 * reported in the preview;</li>
 * <li>Foil ("foil" or "etched", blank: not foil) -&gt; Finish;</li>
 * <li>Alter -&gt; the "altered" special tag; Proxy -&gt; Proxy; Tags (free text) -&gt; Comment;</li>
 * <li>Purchase Price -&gt; User Price.</li>
 * </ul>
 * A file without the columns Count, Name, Edition and Collector Number is not a Moxfield file, and is rejected so
 * another importer gets a go (Deckbox's header has "Card Number", not "Collector Number").
 */
public class MoxfieldCsvImportDelegate extends CsvImportDelegate {
	/** The "Alter" column: a non-blank cell sets the "altered" special tag. */
	private enum Alter implements ICardField {
		ALTER;

		@Override
		public boolean isTransient() {
			return true;
		}

		@Override
		public String getLabel() {
			return "altered";
		}

		@Override
		public Object aggregateValueOf(com.reflexit.magiccards.core.model.abs.ICard card) {
			return null;
		}
	}

	/** The "Proxy" column. */
	private enum ProxyFlag implements ICardField {
		PROXY;

		@Override
		public boolean isTransient() {
			return true;
		}

		@Override
		public String getLabel() {
			return "proxy";
		}

		@Override
		public Object aggregateValueOf(com.reflexit.magiccards.core.model.abs.ICard card) {
			return null;
		}
	}

	/** Lower-cased Moxfield header -&gt; field. */
	private static final Map<String, ICardField> HEADERS = new LinkedHashMap<>();
	static {
		HEADERS.put("count", MagicCardField.COUNT);
		HEADERS.put("tradelist count", MagicCardField.FORTRADECOUNT);
		HEADERS.put("name", MagicCardField.NAME);
		HEADERS.put("edition", MagicCardField.EDITION_ABBR);
		HEADERS.put("condition", MagicCardField.CONDITION);
		HEADERS.put("language", MagicCardField.LANG);
		HEADERS.put("foil", MagicCardField.FINISH);
		HEADERS.put("tags", MagicCardField.COMMENT);
		HEADERS.put("collector number", MagicCardField.COLLNUM);
		HEADERS.put("alter", Alter.ALTER);
		HEADERS.put("proxy", ProxyFlag.PROXY);
		HEADERS.put("purchase price", MagicCardField.PRICE);
	}

	private boolean tradelistColumn;

	/** True when the file read has a Tradelist Count column. */
	public boolean hasTradelistColumn() {
		return tradelistColumn;
	}

	@Override
	protected void setHeaderFields(List<String> list) {
		boolean count = false, name = false, edition = false, number = false;
		ICardField[] fields = new ICardField[list.size()];
		tradelistColumn = false;
		for (int i = 0; i < fields.length; i++) {
			String key = norm(list.get(i));
			count |= key.equals("count");
			name |= key.equals("name");
			edition |= key.equals("edition");
			number |= key.equals("collector number");
			tradelistColumn |= key.equals("tradelist count");
			fields[i] = HEADERS.get(key);
		}
		if (!(count && name && edition && number))
			throw new MagicException(
					"Not a Moxfield CSV: expecting the columns Count, Name, Edition and Collector Number (" + list + ")");
		setFields(fields);
	}

	@Override
	protected ICardField[] getExampleFields() {
		return new ICardField[] { MagicCardField.COUNT, MagicCardField.FORTRADECOUNT, MagicCardField.NAME,
				MagicCardField.EDITION_ABBR, MagicCardField.CONDITION, MagicCardField.LANG, MagicCardField.FINISH,
				MagicCardField.COLLNUM, MagicCardField.PRICE };
	}

	@Override
	public String getExample() {
		return new MoxfieldCsvExportDelegate().getExample();
	}

	@Override
	protected MagicCardPhysical createCard(List<String> list) {
		// tolerate blank lines
		boolean empty = true;
		for (String v : list)
			empty &= v == null || v.trim().isEmpty();
		if (empty)
			return null;
		return super.createCard(list);
	}

	@Override
	public void setFieldValue(MagicCardPhysical card, ICardField field, int i, String value) {
		if (field == null)
			return;
		if (field == Alter.ALTER) {
			if (DeckBoxImportDelegate.isSet(value))
				card.setSpecialTag("altered");
			return;
		}
		if (field == ProxyFlag.PROXY) {
			if (DeckBoxImportDelegate.isSet(value))
				card.setProxy(true);
			return;
		}
		if (field == MagicCardField.FINISH) {
			CardFinish f = CardFinish.resolve(value);
			if (f != null)
				card.setFinish(f);
			return;
		}
		if (field == MagicCardField.CONDITION) {
			if (DeckBoxImportDelegate.isProxy(value))
				card.setProxy(true);
			else
				setConditionOrUnknown(card, value, DeckBoxImportDelegate.condition(value));
			return;
		}
		if (field == MagicCardField.LANG) {
			importData.declareLanguage(card, value == null ? null : value.trim());
			return;
		}
		if (field == MagicCardField.PRICE) {
			float price = DeckBoxImportDelegate.price(value);
			if (price > 0)
				card.setPrice(price);
			return;
		}
		if (field == MagicCardField.FORTRADECOUNT || field == MagicCardField.COUNT
				|| field == MagicCardField.COLLNUM) {
			if (value == null || value.trim().isEmpty())
				return;
			value = value.trim();
		}
		super.setFieldValue(card, field, i, value);
	}

	/** Tradelist Count only says whether the pile is for trade: it never raises Count. */
	@Override
	protected void importCard(MagicCardPhysical card) {
		if (card != null) {
			int forTrade = card.getForTrade();
			if (forTrade > card.getCount())
				card.set(MagicCardField.FORTRADECOUNT, Integer.valueOf(card.getCount()));
		}
		super.importCard(card);
	}

	private static String norm(String header) {
		return header == null ? "" : header.trim().toLowerCase(Locale.ENGLISH);
	}
}
