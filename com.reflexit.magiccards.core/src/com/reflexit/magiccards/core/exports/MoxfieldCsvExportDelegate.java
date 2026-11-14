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
 * Exports a deck / collection as a Moxfield collection CSV, the columns of Moxfield's own export, which its
 * collection import accepts:
 *
 * <pre>
 * Count,Tradelist Count,Name,Edition,Condition,Language,Foil,Tags,Last Modified,Collector Number,Alter,Proxy,
 * Purchase Price
 * </pre>
 *
 * Edition is the set's code in lower case, Condition one of Mint, Near Mint, Good (Lightly Played), Played, Heavily
 * Played, Damaged (a card that is not graded is Near Mint), Foil "foil" / "etched" or blank. The columns are fixed,
 * the user does not choose them. See {@link MoxfieldCsvImportDelegate}.
 */
public class MoxfieldCsvExportDelegate extends CsvExportDelegate {
	/** One Moxfield column each, with its heading in the file. */
	enum Column implements ICardField {
		COUNT("Count"), TRADELIST_COUNT("Tradelist Count"), NAME("Name"), EDITION("Edition"),
		CONDITION("Condition"), LANGUAGE("Language"), FOIL("Foil"), TAGS("Tags"), LAST_MODIFIED("Last Modified"),
		COLLECTOR_NUMBER("Collector Number"), ALTER("Alter"), PROXY("Proxy"), PURCHASE_PRICE("Purchase Price");

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
		return "moxfield-collection";
	}

	@Override
	public Object getObjectByField(IMagicCard card, ICardField field) {
		if (!(field instanceof Column))
			return super.getObjectByField(card, field);
		MagicCardPhysical phi = card instanceof MagicCardPhysical ? (MagicCardPhysical) card : null;
		switch ((Column) field) {
		case COUNT:
			return phi == null ? 1 : phi.getCount();
		case TRADELIST_COUNT:
			return phi == null ? 0 : Math.min(phi.getForTrade(), phi.getCount());
		case NAME:
			return ManaBoxCsvExportDelegate.name(card.getName());
		case EDITION: {
			String abbr = card.getSet() == null ? null : Editions.getInstance().getAbbrByName(card.getSet());
			return abbr == null ? "" : abbr.toLowerCase(Locale.ENGLISH);
		}
		case CONDITION:
			return condition(phi == null ? null : phi.getCondition());
		case LANGUAGE:
			return DeckBoxExportDelegate.language(card, phi);
		case FOIL:
			if (phi == null || phi.getFinish() == CardFinish.NONFOIL)
				return "";
			return phi.getFinish() == CardFinish.ETCHED ? "etched" : "foil";
		case TAGS:
			return phi == null || phi.getComment() == null ? "" : phi.getComment();
		case LAST_MODIFIED:
			return "";
		case COLLECTOR_NUMBER:
			return card.getCollectorId() == null ? "" : card.getCollectorId();
		case ALTER:
			return phi != null && phi.isSpecialTag("altered") ? "true" : "";
		case PROXY:
			return phi != null && phi.isProxy() ? "true" : "";
		case PURCHASE_PRICE: {
			float price = phi == null ? 0 : phi.getPrice();
			return price > 0 ? String.format(Locale.ROOT, "%.2f", price) : "";
		}
		default:
			return "";
		}
	}

	/**
	 * Moxfield's Condition value: NM -&gt; Near Mint, LP -&gt; Good (Lightly Played), MP -&gt; Played, HP -&gt;
	 * Heavily Played, DMG -&gt; Damaged. A card that is not graded is exported as Near Mint.
	 */
	static String condition(CardCondition c) {
		if (c == null)
			return "Near Mint";
		switch (c) {
		case LIGHTLY_PLAYED:
			return "Good (Lightly Played)";
		case MODERATELY_PLAYED:
			return "Played";
		case HEAVILY_PLAYED:
			return "Heavily Played";
		case DAMAGED:
			return "Damaged";
		default:
			return "Near Mint";
		}
	}

	@Override
	public String getExample() {
		return HEADER + "\n" //
				+ "3,0,Sol Ring,c21,Near Mint,English,,,,263,,,\n" //
				+ "1,1,Arcane Denial,plst,Good (Lightly Played),English,foil,,,CMA-30,,,2.50\n";
	}
}
