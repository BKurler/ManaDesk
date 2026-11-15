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
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICard;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;

/**
 * Exports a deck as a TappedOut.net deck CSV, which its import accepts:
 *
 * <pre>
 * Board,Qty,Name,Printing,Foil,Alter,Signed,Condition,Language
 * </pre>
 *
 * Board is {@code main} or {@code side}; the extra list (tokens, emblems...) is not written. Printing is the set's
 * code in upper case, Foil {@code f} / {@code f-etch} or blank, Condition NM, SL, MP, HP, DMG or blank, Language a code. The columns are fixed, the user does not choose
 * them. See {@link TappedOutCsvImportDelegate}.
 */
public class TappedOutCsvExportDelegate extends CsvExportDelegate {
	/** One TappedOut column each, with its heading in the file. */
	enum Column implements ICardField {
		BOARD("Board"), QTY("Qty"), NAME("Name"), PRINTING("Printing"), FOIL("Foil"), ALTER("Alter"),
		SIGNED("Signed"), CONDITION("Condition"), LANGUAGE("Language");

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
	public void printCard(IMagicCard card) {
		Location loc = card instanceof MagicCardPhysical ? ((MagicCardPhysical) card).getLocation() : null;
		if (loc != null && loc.isExtra())
			return; // tokens, emblems...: TappedOut has no such board
		super.printCard(card);
	}

	@Override
	public boolean isColumnChoiceSupported() {
		return false;
	}

	@Override
	public String getContentSlug() {
		return "tappedout-deck";
	}

	/** TappedOut spells a split / double-faced card "Front / Back". */
	static String name(String name) {
		return ManaBoxCsvExportDelegate.name(name).replace(" // ", " / ");
	}

	@Override
	public Object getObjectByField(IMagicCard card, ICardField field) {
		if (!(field instanceof Column))
			return super.getObjectByField(card, field);
		MagicCardPhysical phi = card instanceof MagicCardPhysical ? (MagicCardPhysical) card : null;
		switch ((Column) field) {
		case BOARD:
			return phi != null && phi.getLocation() != null && phi.getLocation().isSideboard() ? "side" : "main";
		case QTY:
			return phi == null ? 1 : phi.getCount();
		case NAME:
			return name(card.getName());
		case PRINTING: {
			String abbr = card.getSet() == null ? null : Editions.getInstance().getAbbrByName(card.getSet());
			return abbr == null ? "" : abbr.toUpperCase(Locale.ENGLISH);
		}
		case FOIL:
			if (phi == null || phi.getFinish() == CardFinish.NONFOIL)
				return "";
			return phi.getFinish() == CardFinish.ETCHED ? "f-etch" : "f";
		case ALTER:
			return phi != null && phi.isSpecialTag("altered") ? "TRUE" : "";
		case CONDITION: {
			CardCondition c = phi == null ? null : phi.getCondition();
			return c == null ? "" : TappedOutCollectionCsvExportDelegate.condition(c);
		}
		case LANGUAGE:
			return TappedOutCollectionCsvExportDelegate.languageCode(DeckBoxExportDelegate.language(card, phi));
		default:
			return "";
		}
	}

	@Override
	public String getExample() {
		return HEADER + "\n" //
				+ "main,1,\"Karn, the Great Creator\",,,,,,\n" //
				+ "main,1,Dread Linnorm / Scale Deflection,,,,,,\n" //
				+ "side,1,Plains,,,,,,\n";
	}
}
