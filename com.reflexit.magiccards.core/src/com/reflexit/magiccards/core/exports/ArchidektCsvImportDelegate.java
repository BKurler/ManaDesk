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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.reflexit.magiccards.core.MagicException;
import com.reflexit.magiccards.core.model.CardCondition;
import com.reflexit.magiccards.core.model.CardFinish;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICardField;

/**
 * Imports an Archidekt collection CSV:
 *
 * <pre>
 * "Quantity","Name","Finish","Condition","Language","Edition Name","Edition Code","Collector Number"
 * </pre>
 *
 * The columns are matched by name, in any order; the other ones Archidekt writes (dates, prices, tags, colors...)
 * are not read. Mapping: Quantity -&gt; Count, Name -&gt; Name, Finish (Normal / Foil / Etched) -&gt; Finish,
 * Condition (NM, LP, MP, HP, D or the full words) -&gt; Condition, Language -&gt; Language, Scryfall ID -&gt; the exact card, Edition Code and
 * Collector Number -&gt; the printing (Edition Name when there is no code). A deck exported with only
 * {@code Quantity,Name} is read too (the printing is then the default one). A file without the columns Quantity
 * and Name, or with a column of another app (Set code, Binder Name, Count...), is not an Archidekt file and is
 * rejected so another importer gets a go.
 */
public class ArchidektCsvImportDelegate extends CsvImportDelegate {
	/** Lower-cased Archidekt header -&gt; field. */
	private static final Map<String, ICardField> HEADERS = new LinkedHashMap<>();
	static {
		HEADERS.put("quantity", MagicCardField.COUNT);
		HEADERS.put("name", MagicCardField.NAME);
		HEADERS.put("finish", MagicCardField.FINISH);
		HEADERS.put("condition", MagicCardField.CONDITION);
		HEADERS.put("language", MagicCardField.LANG);
		HEADERS.put("edition name", MagicCardField.SET);
		HEADERS.put("edition code", MagicCardField.EDITION_ABBR);
		HEADERS.put("collector number", MagicCardField.COLLNUM);
		HEADERS.put("scryfall id", MagicCardField.ID);
	}

	/** Archidekt's bug: it writes the two headings "Edition date" and "Category" as one, "Edition dateCategory". */
	static final String MERGED_HEADING = "edition datecategory";

	@Override
	protected void setHeaderFields(List<String> list) {
		boolean qty = false, name = false, other = false;
		List<ICardField> fields = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (String heading : list) {
			String key = heading == null ? "" : heading.trim().toLowerCase(Locale.ENGLISH);
			qty |= key.equals("quantity");
			name |= key.equals("name");
			// columns of the other apps that also have Quantity and Name (ManaBox, Deckbox, Moxfield...)
			other |= key.equals("set code") || key.equals("binder name") || key.equals("count") || key.equals("edition");
			if (key.equals(MERGED_HEADING)) {
				// the rows have both values, so the columns after it are one further than the header says
				fields.add(null);
				fields.add(null);
				continue;
			}
			// a heading may appear twice (Archidekt writes "Collector number" twice): the first one counts
			fields.add(seen.add(key) ? HEADERS.get(key) : null);
		}
		if (!(qty && name) || other)
			throw new MagicException("Not an Archidekt CSV: expecting the columns Quantity and Name, and none of"
					+ " another app's (Set code, Binder Name, Count...) (" + list + ")");
		setFields(fields.toArray(new ICardField[0]));
	}

	@Override
	protected ICardField[] getExampleFields() {
		return null; // getExample() is the export's
	}

	@Override
	public String getExample() {
		return new ArchidektCsvExportDelegate().getExample();
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

	/** Archidekt's grades: NM, LP, MP, HP, D, the full words, or its menu's "Near Mint (NM)"; null: not graded. */
	static CardCondition condition(String value) {
		if (value == null)
			return null;
		int open = value.indexOf('(');
		int close = value.indexOf(')', open + 1);
		if (open >= 0 && close > open) // "Near Mint (NM)": the abbreviation in parentheses
			value = value.substring(open + 1, close);
		switch (value.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z]", "")) {
		case "d":
		case "dmg":
		case "damaged":
			return CardCondition.DAMAGED;
		default:
			return DeckBoxImportDelegate.condition(value);
		}
	}

	@Override
	public void setFieldValue(MagicCardPhysical card, ICardField field, int i, String value) {
		if (field == null || value == null || value.trim().isEmpty())
			return;
		value = value.trim();
		if (field == MagicCardField.FINISH) {
			CardFinish f = CardFinish.resolve(value);
			if (f != null)
				card.setFinish(f);
			return;
		}
		if (field == MagicCardField.CONDITION) {
			setConditionOrUnknown(card, value, condition(value));
			return;
		}
		if (field == MagicCardField.LANG) {
			importData.declareLanguage(card, value);
			return;
		}
		if (field == MagicCardField.NAME)
			value = ArchidektTextImportDelegate.name(value);
		super.setFieldValue(card, field, i, value);
	}
}
