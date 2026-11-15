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
import com.reflexit.magiccards.core.model.CardCondition;
import com.reflexit.magiccards.core.model.CardFinish;
import com.reflexit.magiccards.core.model.Languages;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICardField;

/**
 * Imports a TappedOut.net CSV. Two layouts exist and both are read:
 *
 * <pre>
 * Board,Qty,Name,Printing,Foil,Alter,Signed,Condition,Language                      (a deck)
 * Qty,Name,Set,Set Number,Foil,Alter,Signed,Condition,Languange,Proxy               (a collection)
 * </pre>
 *
 * (TappedOut spells the last heading of the second one "Languange"; "Language" is read too.) The columns are
 * matched by name, in any order, and a "-" cell is an empty one. Mapping:
 * <ul>
 * <li>Board ({@code main} the deck, {@code side} the sideboard; the maybeboard and the other boards are not
 * read);</li>
 * <li>Qty -&gt; Count; Name -&gt; Name ("Front / Back" is the card "Front // Back");</li>
 * <li>Printing / Set (the set code) and Set Number -&gt; the printing;</li>
 * <li>Foil ({@code f}; {@code f-etch} for etched) -&gt; Finish; Alter -&gt; the "altered" special tag; Proxy -&gt; Proxy;</li>
 * <li>Condition (NM, SL / SP, MP, HP, DMG or the full words) and Language (EN, FR... or the name);</li>
 * <li>Signed is not read.</li>
 * </ul>
 * A file without the columns Qty and Name, and one of Board / Set / Set Number / Printing, is not a TappedOut file
 * and is rejected so another importer gets a go.
 */
public class TappedOutCsvImportDelegate extends CsvImportDelegate {
	/** The "Board" column: which zone of the deck the card is in. */
	enum Board implements ICardField {
		BOARD;

		@Override
		public boolean isTransient() {
			return true;
		}

		@Override
		public String getLabel() {
			return "board";
		}

		@Override
		public Object aggregateValueOf(com.reflexit.magiccards.core.model.abs.ICard card) {
			return null;
		}
	}

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

	/** Lower-cased TappedOut header -&gt; field. */
	private static final Map<String, ICardField> HEADERS = new LinkedHashMap<>();
	static {
		HEADERS.put("board", Board.BOARD);
		HEADERS.put("qty", MagicCardField.COUNT);
		HEADERS.put("name", MagicCardField.NAME);
		HEADERS.put("printing", MagicCardField.EDITION_ABBR);
		HEADERS.put("set", MagicCardField.EDITION_ABBR);
		HEADERS.put("set number", MagicCardField.COLLNUM);
		HEADERS.put("foil", MagicCardField.FINISH);
		HEADERS.put("alter", Alter.ALTER);
		HEADERS.put("condition", MagicCardField.CONDITION);
		HEADERS.put("language", MagicCardField.LANG);
		HEADERS.put("languange", MagicCardField.LANG); // sic, TappedOut's heading
		HEADERS.put("proxy", ProxyFlag.PROXY);
	}

	/** What a board cell means. */
	enum Zone {
		MAIN, SIDE, SKIP
	}

	@Override
	protected void setHeaderFields(List<String> list) {
		boolean qty = false, name = false, other = false;
		ICardField[] fields = new ICardField[list.size()];
		for (int i = 0; i < fields.length; i++) {
			String key = list.get(i) == null ? "" : list.get(i).trim().toLowerCase(Locale.ENGLISH);
			qty |= key.equals("qty");
			name |= key.equals("name");
			other |= key.equals("board") || key.equals("set") || key.equals("set number") || key.equals("printing");
			fields[i] = HEADERS.get(key);
		}
		if (!(qty && name && other))
			throw new MagicException("Not a TappedOut CSV: expecting the columns Qty, Name and Board or Set ("
					+ list + ")");
		setFields(fields);
	}

	@Override
	protected ICardField[] getExampleFields() {
		return null; // getExample() is the export's
	}

	@Override
	public String getExample() {
		return new TappedOutCsvExportDelegate().getExample();
	}

	/** The zone a Board cell names: "side" / "sideboard" the sideboard, blank / "main" / "mainboard" the deck. */
	static Zone zone(String board) {
		String b = board == null ? "" : board.trim().toLowerCase(Locale.ENGLISH);
		if (b.isEmpty() || b.equals("-") || b.equals("main") || b.equals("mainboard") || b.equals("deck"))
			return Zone.MAIN;
		if (b.equals("side") || b.equals("sideboard"))
			return Zone.SIDE;
		return Zone.SKIP; // maybe, acquire...
	}

	@Override
	protected MagicCardPhysical createCard(List<String> list) {
		boolean empty = true; // tolerate blank lines
		for (String v : list)
			empty &= v == null || v.trim().isEmpty();
		if (empty)
			return null;
		ICardField[] fields = getFields();
		Zone zone = Zone.MAIN;
		for (int i = 0; i < fields.length && i < list.size(); i++)
			if (fields[i] == Board.BOARD)
				zone = zone(list.get(i));
		if (zone == Zone.SKIP)
			return null;
		MagicCardPhysical card = super.createCard(list);
		if (zone == Zone.SIDE)
			card.setLocation(getSideboardLocation());
		return card;
	}

	/** TappedOut writes a split / double-faced card as "Front / Back". */
	static String name(String tappedOut) {
		return MoxfieldTextImportDelegate.name(tappedOut);
	}

	/** TappedOut's grades (NM, SL / SP, MP, HP, DMG, or the full words) as a ManaDesk one; null: not graded. */
	static CardCondition condition(String value) {
		if (value == null)
			return null;
		switch (value.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z]", "")) {
		case "sl":
		case "sp":
		case "slightlyplayed":
			return CardCondition.LIGHTLY_PLAYED;
		case "dmg":
			return CardCondition.DAMAGED;
		default:
			return DeckBoxImportDelegate.condition(value);
		}
	}

	/** True for the empty cell of a TappedOut collection: blank or "-". */
	static boolean isBlank(String value) {
		return value == null || value.trim().isEmpty() || value.trim().equals("-");
	}

	/** The finish a Foil cell says: "f" / "foil" foil, "f-etch" (or "etched") etched, blank: null. */
	static CardFinish finish(String value) {
		if (isBlank(value))
			return null;
		String v = value.trim().toLowerCase(Locale.ENGLISH);
		if (v.equals("f"))
			return CardFinish.FOIL;
		if (v.equals("f-etch") || v.equals("e") || v.equals("etched"))
			return CardFinish.ETCHED;
		CardFinish f = CardFinish.resolve(value);
		if (f != null)
			return f;
		return DeckBoxImportDelegate.isSet(value) ? CardFinish.FOIL : null;
	}

	/** The language name for a TappedOut cell: a code (EN, FR, JP...) or the name itself. */
	static String language(String value) {
		if (isBlank(value))
			return null;
		String v = value.trim().toLowerCase(Locale.ENGLISH);
		switch (v) {
		case "jp":
			v = "ja";
			break;
		case "kr":
			v = "ko";
			break;
		case "cs":
		case "zh":
			v = "zhs";
			break;
		case "ct":
			v = "zht";
			break;
		default:
			break;
		}
		String name = Languages.fromScryfallCode(v);
		return name != null ? name : value.trim();
	}

	@Override
	public void setFieldValue(MagicCardPhysical card, ICardField field, int i, String value) {
		if (field == null || field == Board.BOARD || isBlank(value))
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
			CardFinish f = finish(value);
			if (f != null)
				card.setFinish(f);
			return;
		}
		if (field == MagicCardField.CONDITION) {
			setConditionOrUnknown(card, value, condition(value));
			return;
		}
		if (field == MagicCardField.LANG) {
			importData.declareLanguage(card, language(value));
			return;
		}
		if (field == MagicCardField.NAME) {
			super.setFieldValue(card, field, i, name(value));
			return;
		}
		super.setFieldValue(card, field, i, value.trim());
	}
}
