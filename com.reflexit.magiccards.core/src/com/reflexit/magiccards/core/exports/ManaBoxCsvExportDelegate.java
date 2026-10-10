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
import java.util.Currency;
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
import com.reflexit.magiccards.core.sync.CurrencyConvertor;

/**
 * Exports a deck / collection in ManaBox's own CSV format (the columns of a
 * ManaBox binder export, without its ManaBox ID; Added is the copy's Date), so ManaBox imports
 * it as is: the Scryfall ID gives it the exact printing.
 *
 * <pre>
 * Name,Set code,Set name,Collector number,Foil,Rarity,Quantity,Scryfall ID,Purchase price,Misprint,Altered,
 * Signed,Condition,Language,Proxy,Purchase price currency,Added
 * </pre>
 */
public class ManaBoxCsvExportDelegate extends CsvExportDelegate {
	public static final String HEADER = "Name,Set code,Set name,Collector number,Foil,Rarity,Quantity,Scryfall ID,"
			+ "Purchase price,Misprint,Altered,Signed,Condition,Language,Proxy,Purchase price currency,Added";

	/** One ManaBox column each. */
	enum Column implements ICardField {
		NAME, SET_CODE, SET_NAME, COLLECTOR_NUMBER, FOIL, RARITY, QUANTITY, SCRYFALL_ID, PURCHASE_PRICE, MISPRINT,
		ALTERED, SIGNED, CONDITION, LANGUAGE, PROXY, PURCHASE_PRICE_CURRENCY, ADDED;

		@Override
		public boolean isTransient() {
			return true;
		}

		@Override
		public String getLabel() {
			return name();
		}

		@Override
		public Object aggregateValueOf(ICard card) {
			return null;
		}
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
		return "manabox";
	}

	@Override
	public Object getObjectByField(IMagicCard card, ICardField field) {
		if (!(field instanceof Column))
			return super.getObjectByField(card, field);
		MagicCardPhysical phi = card instanceof MagicCardPhysical ? (MagicCardPhysical) card : null;
		switch ((Column) field) {
		case NAME:
			return name(card.getName());
		case SET_CODE:
			String abbr = Editions.getInstance().getAbbrByName(card.getSet());
			return abbr == null ? "" : abbr.toUpperCase(Locale.ENGLISH);
		case SET_NAME:
			return card.getSet();
		case COLLECTOR_NUMBER:
			return card.getCollectorId();
		case FOIL:
			return finish(phi == null ? CardFinish.NONFOIL : phi.getFinish());
		case RARITY:
			return card.getRarity() == null ? "" : card.getRarity().toLowerCase(Locale.ENGLISH);
		case QUANTITY:
			return phi == null ? 1 : phi.getCount();
		case SCRYFALL_ID:
			return scryfallId(card.getCardId());
		case PURCHASE_PRICE:
			float price = phi == null ? 0 : phi.getPrice();
			return price > 0 ? String.format(Locale.ROOT, "%.2f", price) : "";
		case MISPRINT:
			return phi != null && phi.isSpecialTag("misprint");
		case ALTERED:
			return phi != null && phi.isSpecialTag("altered");
		case SIGNED:
			return phi != null && phi.isSpecialTag("signed");
		case CONDITION:
			return condition(phi == null ? null : phi.getCondition());
		case LANGUAGE:
			String code = Languages.toScryfallCode(card.getLanguage());
			return code == null ? "en" : code;
		case PROXY:
			return phi != null && phi.isProxy();
		case PURCHASE_PRICE_CURRENCY:
			Currency cur = CurrencyConvertor.getCurrency();
			return cur == null ? "" : cur.getCurrencyCode();
		case ADDED:
			// ISO-8601 UTC, as ManaBox writes it
			return phi == null || phi.getDate() == null ? "" : phi.getDate().toInstant().toString();
		default:
			return "";
		}
	}

	/** A two-faced card's name without ManaDesk's layout note ("A // B (transform)" -&gt; "A // B"). */
	static String name(String name) {
		if (name == null)
			return "";
		if (name.contains(" // "))
			return name.replaceFirst("\\s*\\([^()]*\\)\\s*$", "");
		return name;
	}

	/** ManaBox's Foil value. */
	static String finish(CardFinish f) {
		if (f == CardFinish.FOIL)
			return "foil";
		if (f == CardFinish.ETCHED)
			return "etched";
		return "normal";
	}

	/**
	 * ManaBox's Condition value. ManaBox grades like Cardmarket (near_mint,
	 * excellent, good, light_played, played, poor); each ManaDesk grade goes to
	 * the ManaBox grade it is read back from. A card that is not graded is
	 * exported as near_mint.
	 */
	static String condition(CardCondition c) {
		if (c == null)
			return "near_mint";
		switch (c) {
		case LIGHTLY_PLAYED:
			return "excellent";
		case MODERATELY_PLAYED:
			return "light_played";
		case HEAVILY_PLAYED:
			return "played";
		case DAMAGED:
			return "poor";
		default:
			return "near_mint";
		}
	}

	/** A back face's id is "-" + the card's Scryfall id. */
	static String scryfallId(String id) {
		if (id == null)
			return "";
		return id.startsWith("-") ? id.substring(1) : id;
	}

	@Override
	public String getExample() {
		return HEADER + "\n" + "Kaervek the Merciless,DSC,Duskmourn: House of Horror Commander,222,normal,rare,1,"
				+ "95c5bd95-b483-4dfb-9010-cfee47d22468,0.36,false,false,false,near_mint,en,false,USD,"
				+ "2026-10-10T19:29:49.920Z\n"
				+ "\"Atraxa, Praetors' Voice\",MUL,Multiverse Legends,98,etched,mythic,6,"
				+ "cf7e8917-8bd1-4899-bc78-7a5815c08057,40.39,false,false,false,near_mint,en,false,USD,"
				+ "2026-10-10T19:29:49.911Z\n";
	}
}
