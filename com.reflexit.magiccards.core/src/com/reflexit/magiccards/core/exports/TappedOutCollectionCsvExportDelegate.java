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

import com.reflexit.magiccards.core.model.CardCondition;
import com.reflexit.magiccards.core.model.CardFinish;
import com.reflexit.magiccards.core.model.Editions;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.Languages;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICard;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;

/**
 * Exports a collection as a TappedOut.net collection CSV, the columns of its own export:
 *
 * <pre>
 * Qty,Name,Set,Set Number,Foil,Alter,Signed,Condition,Languange,Proxy
 * 1,Lightning Bolt,MSC,806,f,-,-,NM,EN,-
 * </pre>
 *
 * An empty cell is "-", Set the set's code in upper case, Foil {@code f} ({@code f-etch} for etched), Condition NM,
 * SL, MP, HP or DMG (a card that is not graded is NM) and Language a code (EN, FR...). The heading "Languange" is
 * TappedOut's own spelling. Alter and Proxy are {@code TRUE} when set, "-" otherwise (assumed: TappedOut's own
 * export of them could not be checked); Signed is always "-". The columns are fixed. See {@link TappedOutCsvImportDelegate}.
 */
public class TappedOutCollectionCsvExportDelegate extends CsvExportDelegate {
	/** One TappedOut column each, with its heading in the file. */
	enum Column implements ICardField {
		QTY("Qty"), NAME("Name"), SET("Set"), SET_NUMBER("Set Number"), FOIL("Foil"), ALTER("Alter"),
		SIGNED("Signed"), CONDITION("Condition"), LANGUAGE("Languange"), PROXY("Proxy");

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

	public static final String HEADER = header();

	private static String header() {
		StringBuilder sb = new StringBuilder();
		for (Column c : Column.values()) {
			if (sb.length() > 0)
				sb.append(',');
			sb.append(c.heading);
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
		return "tappedout-collection";
	}

	/** TappedOut's grade: NM, SL, MP, HP, DMG; a card that is not graded is NM. */
	static String condition(CardCondition c) {
		if (c == null)
			return "NM";
		switch (c) {
		case LIGHTLY_PLAYED:
			return "SL";
		case MODERATELY_PLAYED:
			return "MP";
		case HEAVILY_PLAYED:
			return "HP";
		case DAMAGED:
			return "DMG";
		default:
			return "NM";
		}
	}

	/** TappedOut's language code (EN, FR...) for a language name. */
	static String languageCode(String name) {
		String code = Languages.toScryfallCode(name);
		return code == null ? "EN" : code.toUpperCase(Locale.ENGLISH);
	}

	private static String dash(String s) {
		return s == null || s.isEmpty() ? "-" : s;
	}

	@Override
	public Object getObjectByField(IMagicCard card, ICardField field) {
		if (!(field instanceof Column))
			return super.getObjectByField(card, field);
		MagicCardPhysical phi = card instanceof MagicCardPhysical ? (MagicCardPhysical) card : null;
		switch ((Column) field) {
		case QTY:
			return phi == null ? 1 : phi.getCount();
		case NAME:
			return TappedOutCsvExportDelegate.name(card.getName());
		case SET: {
			String abbr = card.getSet() == null ? null : Editions.getInstance().getAbbrByName(card.getSet());
			return dash(abbr == null ? "" : abbr.toUpperCase(Locale.ENGLISH));
		}
		case SET_NUMBER:
			return dash(card.getCollectorId());
		case FOIL:
			if (phi == null || phi.getFinish() == CardFinish.NONFOIL)
				return "-";
			return phi.getFinish() == CardFinish.ETCHED ? "f-etch" : "f";
		case ALTER:
			return phi != null && phi.isSpecialTag("altered") ? "TRUE" : "-";
		case CONDITION:
			return condition(phi == null ? null : phi.getCondition());
		case LANGUAGE:
			return languageCode(DeckBoxExportDelegate.language(card, phi));
		case PROXY:
			return phi != null && phi.isProxy() ? "TRUE" : "-";
		default:
			return "-"; // Signed
		}
	}

	@Override
	public String getExample() {
		return HEADER + "\n" //
				+ "1,Lightning Bolt,MSC,806,f,-,-,NM,EN,-\n" //
				+ "1,The Legend of Yangchen,TLA,27,-,-,-,SL,FR,-\n";
	}
}
