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
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICard;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;

/**
 * Exports a deck / collection as an Archidekt collection CSV, which its collection import accepts:
 *
 * <pre>
 * Quantity,Name,Finish,Condition,Language,Scryfall ID,Edition Name,Edition Code,Collector Number
 * </pre>
 *
 * Finish is Normal, Foil or Etched; Condition NM, LP, MP, HP or D (NM when the copy is not graded); the Scryfall ID (the
 * exact card, leading the printing columns) and the set code in lower case. The columns are fixed, the user does not choose them. See {@link ArchidektCsvImportDelegate}.
 */
public class ArchidektCsvExportDelegate extends CsvExportDelegate {
	/** One Archidekt column each, with its heading in the file. */
	enum Column implements ICardField {
		QUANTITY("Quantity"), NAME("Name"), FINISH("Finish"), CONDITION("Condition"), LANGUAGE("Language"),
		SCRYFALL_ID("Scryfall ID"), EDITION_NAME("Edition Name"), EDITION_CODE("Edition Code"), COLLECTOR_NUMBER("Collector Number");

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
		return "archidekt-collection";
	}

	/** Archidekt's Finish value. */
	static String finish(CardFinish f) {
		if (f == CardFinish.FOIL)
			return "Foil";
		if (f == CardFinish.ETCHED)
			return "Etched";
		return "Normal";
	}

	/** Archidekt's Condition value: NM, LP, MP, HP, D; a copy that is not graded is NM. */
	static String condition(CardCondition c) {
		if (c == null)
			return "NM";
		return c == CardCondition.DAMAGED ? "D" : c.getAbbr();
	}

	@Override
	public Object getObjectByField(IMagicCard card, ICardField field) {
		if (!(field instanceof Column))
			return super.getObjectByField(card, field);
		MagicCardPhysical phi = card instanceof MagicCardPhysical ? (MagicCardPhysical) card : null;
		switch ((Column) field) {
		case QUANTITY:
			return phi == null ? 1 : phi.getCount();
		case NAME:
			return ManaBoxCsvExportDelegate.name(card.getName());
		case FINISH:
			return finish(phi == null ? null : phi.getFinish());
		case CONDITION:
			return condition(phi == null ? null : phi.getCondition());
		case LANGUAGE:
			return DeckBoxExportDelegate.language(card, phi);
		case SCRYFALL_ID:
			return ManaBoxCsvExportDelegate.scryfallId(card.getCardId());
		case EDITION_NAME:
			return card.getSet() == null ? "" : card.getSet();
		case EDITION_CODE: {
			String abbr = card.getSet() == null ? null : Editions.getInstance().getAbbrByName(card.getSet());
			return abbr == null ? "" : abbr.toLowerCase(Locale.ENGLISH);
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
				+ "3,Sol Ring,Normal,NM,English,Commander 2021,c21,263\n" //
				+ "1,Lightning Bolt,Foil,LP,English,Magic 2011,m11,149\n";
	}
}
