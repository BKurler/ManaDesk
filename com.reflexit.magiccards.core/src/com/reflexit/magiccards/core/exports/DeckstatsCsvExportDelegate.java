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

import java.lang.reflect.InvocationTargetException;
import java.util.Locale;

import com.reflexit.magiccards.core.model.CardFinish;
import com.reflexit.magiccards.core.model.Editions;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICard;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;

/**
 * Exports a deck / collection as a Deckstats.net collection CSV, which its collection import accepts:
 *
 * <pre>
 * "amount","card_name","is_foil","set_code","collector_number"
 * </pre>
 *
 * set_code is the set's code in lower case, is_foil "1" for a foil or etched copy and "0" otherwise. The columns
 * are fixed, the user does not choose them. See {@link DeckstatsCsvImportDelegate}.
 */
public class DeckstatsCsvExportDelegate extends CsvExportDelegate {
	/** One Deckstats column each, with its heading in the file. */
	enum Column implements ICardField {
		AMOUNT("amount"), CARD_NAME("card_name"), IS_FOIL("is_foil"), SET_CODE("set_code"),
		COLLECTOR_NUMBER("collector_number");

		final String heading;

		private Column(String heading) {
			this.heading = heading;
		}

		@Override
		public boolean isTransient() {
			return true;
		}

		@Override
		public String getLabel() {
			return heading;
		}

		@Override
		public Object aggregateValueOf(ICard card) {
			return null;
		}
	}

	/** Set codes Deckstats spells differently from Scryfall (lower case): Scryfall code -> Deckstats code. */
	private static final String[][] CODES = { { "plst", "plist" } };

	/** The code Deckstats uses for a set whose (lower case) Scryfall code is given. */
	static String deckstatsCode(String scryfall) {
		for (String[] c : CODES)
			if (c[0].equalsIgnoreCase(scryfall))
				return c[1];
		return scryfall;
	}

	/** The (Scryfall) code of the set whose Deckstats code is given; any case, unchanged when the same. */
	static String scryfallCode(String deckstats) {
		if (deckstats == null)
			return null;
		for (String[] c : CODES)
			if (c[1].equalsIgnoreCase(deckstats.trim()))
				return c[0];
		return deckstats;
	}

	public static final String HEADER = header();

	private static String header() {
		StringBuilder sb = new StringBuilder();
		for (Column c : Column.values()) {
			if (sb.length() > 0)
				sb.append(',');
			sb.append('"').append(c.heading).append('"');
		}
		return sb.toString();
	}

	@Override
	public void printHeader() {
		if (header) // "Generate header row"
			stream.println(HEADER);
	}

	@Override
	public void run(ICoreProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
		setColumns(Column.values());
		super.run(monitor);
	}

	@Override
	public boolean isColumnChoiceSupported() {
		return false;
	}

	@Override
	public String getContentSlug() {
		return "deckstats-collection";
	}

	@Override
	public Object getObjectByField(IMagicCard card, ICardField field) {
		if (!(field instanceof Column))
			return super.getObjectByField(card, field);
		MagicCardPhysical phi = card instanceof MagicCardPhysical ? (MagicCardPhysical) card : null;
		switch ((Column) field) {
		case AMOUNT:
			return phi == null ? 1 : phi.getCount();
		case CARD_NAME:
			return ManaBoxCsvExportDelegate.name(card.getName());
		case IS_FOIL:
			return phi != null && phi.getFinish() != CardFinish.NONFOIL ? 1 : 0;
		case SET_CODE: {
			String abbr = card.getSet() == null ? null : Editions.getInstance().getAbbrByName(card.getSet());
			return abbr == null ? "" : deckstatsCode(abbr.toLowerCase(Locale.ENGLISH));
		}
		case COLLECTOR_NUMBER:
			return card.getCollectorId() == null ? "" : card.getCollectorId();
		default:
			return "";
		}
	}

	@Override
	public String getExample() {
		return HEADER + "\n" //
				+ "\"3\",\"Sol Ring\",\"0\",\"c21\",\"263\"\n" //
				+ "\"1\",\"Lightning Bolt\",\"1\",\"m11\",\"149\"\n";
	}
}
