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
 * Imports a Deckstats.net collection CSV:
 *
 * <pre>
 * "amount","card_name","is_foil","set_code","collector_number"
 * </pre>
 *
 * The columns are matched by name, in any order; the ones ManaDesk has no use for are ignored, and a few more
 * Deckstats may add are read when present: {@code language}, {@code condition}, {@code comment}. Mapping:
 * amount -&gt; Count, card_name -&gt; Name, is_foil ("1" / "true": foil) -&gt; Finish, set_code and
 * collector_number -&gt; the printing. A file without the columns amount and card_name is not a Deckstats file and
 * is rejected so another importer gets a go.
 */
public class DeckstatsCsvImportDelegate extends CsvImportDelegate {
	/** Lower-cased Deckstats header -&gt; field. */
	private static final Map<String, ICardField> HEADERS = new LinkedHashMap<>();
	static {
		HEADERS.put("amount", MagicCardField.COUNT);
		HEADERS.put("card_name", MagicCardField.NAME);
		HEADERS.put("is_foil", MagicCardField.FINISH);
		HEADERS.put("set_code", MagicCardField.EDITION_ABBR);
		HEADERS.put("collector_number", MagicCardField.COLLNUM);
		HEADERS.put("language", MagicCardField.LANG);
		HEADERS.put("condition", MagicCardField.CONDITION);
		HEADERS.put("comment", MagicCardField.COMMENT);
	}

	@Override
	protected void setHeaderFields(List<String> list) {
		boolean amount = false, name = false;
		ICardField[] fields = new ICardField[list.size()];
		for (int i = 0; i < fields.length; i++) {
			String key = list.get(i) == null ? "" : list.get(i).trim().toLowerCase(Locale.ENGLISH);
			amount |= key.equals("amount");
			name |= key.equals("card_name");
			fields[i] = HEADERS.get(key);
		}
		if (!(amount && name))
			throw new MagicException("Not a Deckstats CSV: expecting the columns amount and card_name (" + list + ")");
		setFields(fields);
	}

	@Override
	protected ICardField[] getExampleFields() {
		return new ICardField[] { MagicCardField.COUNT, MagicCardField.NAME, MagicCardField.FINISH,
				MagicCardField.EDITION_ABBR, MagicCardField.COLLNUM };
	}

	@Override
	public String getExample() {
		return new DeckstatsCsvExportDelegate().getExample();
	}

	@Override
	protected MagicCardPhysical createCard(List<String> list) {
		boolean empty = true; // tolerate blank lines
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
		if (field == MagicCardField.FINISH) {
			if (DeckBoxImportDelegate.isSet(value))
				card.setFinish(CardFinish.FOIL);
			return;
		}
		if (field == MagicCardField.CONDITION) {
			setConditionOrUnknown(card, value, DeckBoxImportDelegate.condition(value));
			return;
		}
		if (field == MagicCardField.LANG) {
			importData.declareLanguage(card, value == null ? null : value.trim());
			return;
		}
		if (field == MagicCardField.COUNT || field == MagicCardField.COLLNUM) {
			if (value == null || value.trim().isEmpty())
				return;
			value = value.trim();
		}
		if (field == MagicCardField.EDITION_ABBR)
			value = DeckstatsCsvExportDelegate.scryfallCode(value);
		super.setFieldValue(card, field, i, value);
	}
}
