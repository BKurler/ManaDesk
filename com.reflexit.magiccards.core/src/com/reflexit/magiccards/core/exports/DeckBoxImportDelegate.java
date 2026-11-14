/*
 * Contributors:
 *     Rémi Dutil (2026) - rewritten for ManaDesk: headers matched by name, first-class Condition / Finish /
 *                         price, Deckbox flag columns as special tags, Inventory and Wishlist in one importer
 *     Rémi Dutil (2026) - Tradelist Count only decides the "fortrade" tag: it never raises Count; hasTradelistColumn()
 */

package com.reflexit.magiccards.core.exports;

import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.reflexit.magiccards.core.MagicException;
import com.reflexit.magiccards.core.model.CardCondition;
import com.reflexit.magiccards.core.model.CardFinish;
import com.reflexit.magiccards.core.model.ForTradeRules;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICard;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.sync.CurrencyConvertor;

/**
 * Imports a Deckbox CSV: an Inventory export, a Tradelist / Wishlist export or the
 * "Count,...,Edition,..." file Deckbox itself accepts. The inventory export header is:
 *
 * <pre>
 * Count,Tradelist Count,Name,Edition,Edition Code,Card Number,Condition,Language,Foil,Signed,Artist Proof,
 * Altered Art,Misprint,Promo,Textless,Printing Id,Printing Note,Tags,My Price,Scryfall ID
 * </pre>
 *
 * A wishlist has no Tradelist Count, and Deckbox's import also takes a shorter, differently ordered header, so
 * the columns are matched by name, in any order; a column Deckbox adds later (Type, Cost, Rarity...) is
 * ignored (Printing Id, Printing Note, Promo, Textless). Current exports carry the Scryfall ID, which is the
 * ManaDesk card id: the card is then found by it, and Edition and Card Number are NOT read - Deckbox numbers
 * old sets differently (Abeyance is 117 in Weatherlight, 1 on Scryfall) and names some sets differently
 * ("Magic 2015 Core Set"), so they would only produce false "Mismatched info" rows. A file without the id
 * falls back to Name, Edition Code (Deckbox's own set code, read ignoring the case: it wins over the set's
 * name when ManaDesk knows it, else the name is used) and Card Number. Mapping:
 * <ul>
 * <li>Count -&gt; Count; Tradelist Count -&gt; how many of them are for trade (the pile is split, as for any
 * for-trade count);</li>
 * <li>Foil (blank or "foil") -&gt; Finish;</li>
 * <li>Condition (Mint, Near Mint, Good (Lightly Played), Played, Heavily Played, Poor) -&gt; Condition (see
 * {@link #condition}); <b>Proxy</b>, the seventh condition of Deckbox, is not a grade: the copy is imported as a
 * <b>Proxy</b> (and is not graded, and never for trade);</li>
 * <li>Language: the card stays English when the card database has no printing in that language, and the
 * file's language is reported in the preview ({@link ImportData#declareLanguage});</li>
 * <li>My Price ("$1.50", in USD) -&gt; User Price, converted into ManaDesk's currency when they differ and
 * a rate is known;</li>
 * <li>Signed, Artist Proof, Altered Art, Misprint (Deckbox writes "signed", "proof", "altered", "misprint";
 * any non-blank text counts) -&gt; the "signed", "proof", "altered" and "misprint" special tags. Promo and
 * Textless are deprecated on Deckbox and are not read; neither are Printing Id and Printing Note;</li>
 * <li>Tags (Deckbox's own free text tags) -&gt; the Comment, as written.</li>
 * </ul>
 * A file without Count, Name and Edition columns is not a Deckbox file, and is rejected so another importer
 * gets a go.
 */
public class DeckBoxImportDelegate extends CsvImportDelegate {
	/** Special tags for Deckbox's flag columns (a non-blank cell sets the tag). */
	enum Flag implements ICardField {
		SIGNED("signed"), PROOF("proof"), ALTERED("altered"), MISPRINT("misprint");

		final String tag;

		Flag(String tag) {
			this.tag = tag;
		}

		@Override
		public boolean isTransient() {
			return true;
		}

		@Override
		public String getLabel() {
			return tag;
		}

		@Override
		public Object aggregateValueOf(ICard card) {
			return null;
		}
	}

	/** Lower-cased Deckbox header -&gt; field ({@code null}: known, not imported). */
	private static final Map<String, ICardField> HEADERS = new LinkedHashMap<>();
	static {
		HEADERS.put("count", MagicCardField.COUNT);
		HEADERS.put("tradelist count", MagicCardField.FORTRADECOUNT);
		HEADERS.put("name", MagicCardField.NAME);
		HEADERS.put("edition", MagicCardField.SET);
		HEADERS.put("edition code", MagicCardField.EDITION_ABBR);
		HEADERS.put("card number", MagicCardField.COLLNUM);
		HEADERS.put("condition", MagicCardField.CONDITION);
		HEADERS.put("language", MagicCardField.LANG);
		HEADERS.put("foil", MagicCardField.FINISH);
		HEADERS.put("signed", Flag.SIGNED);
		HEADERS.put("artist proof", Flag.PROOF);
		HEADERS.put("altered art", Flag.ALTERED);
		HEADERS.put("misprint", Flag.MISPRINT);
		// Promo and Textless are deprecated on Deckbox (struck through in its own menu): not read
		HEADERS.put("my price", MagicCardField.PRICE);
		HEADERS.put("scryfall id", MagicCardField.ID);
		// Deckbox's own tags are free text (it may also group them in folders): read as the copy's comment as they
		// are written - Special tags have a syntax of their own (a comma, "=", a leading "+" or "-")
		HEADERS.put("tags", MagicCardField.COMMENT);
	}

	/** Whether the header of the last file read has the Tradelist Count column (a wishlist export has not). */
	private boolean tradelistColumn;
	/** Whether the header has a Scryfall ID column. */
	private boolean scryfallColumn;
	/** The set / number / code cells of the cards being read, kept until their Scryfall ID is known (see importCard). */
	private final java.util.Map<MagicCardPhysical, java.util.List<Object[]>> placeCells = new java.util.IdentityHashMap<>();

	@Override
	public void init(ImportData result) {
		super.init(result);
		placeCells.clear();
	}

	/** True when the file read has a Tradelist Count column - the only Deckbox data about trading. */
	public boolean hasTradelistColumn() {
		return tradelistColumn;
	}

	@Override
	protected void setHeaderFields(List<String> list) {
		boolean count = false, name = false, edition = false, scryfallId = false, tradelist = false;
		ICardField[] fields = new ICardField[list.size()];
		for (int i = 0; i < fields.length; i++) {
			String key = norm(list.get(i));
			tradelist |= key.equals("tradelist count");
			count |= key.equals("count");
			name |= key.equals("name");
			edition |= key.equals("edition");
			scryfallId |= key.equals("scryfall id");
			fields[i] = HEADERS.get(key);
		}
		// with a Scryfall ID in the row, the id is exact and Deckbox's set names and card numbers (not always
		// Scryfall's) are dropped; a row without an id (the column may be empty) keeps them - see importCard
		scryfallColumn = scryfallId;
		tradelistColumn = tradelist;
		if (!(count && name && edition))
			throw new MagicException("Not a Deckbox CSV: expecting the columns Count, Name and Edition (" + list + ")");
		setFields(fields);
	}

	@Override
	protected ICardField[] getExampleFields() {
		return new ICardField[] { MagicCardField.COUNT, MagicCardField.FORTRADECOUNT, MagicCardField.NAME,
				MagicCardField.SET, MagicCardField.COLLNUM, MagicCardField.CONDITION, MagicCardField.LANG,
				MagicCardField.FINISH, MagicCardField.PRICE, MagicCardField.ID };
	}

	@Override
	public String getExample() {
		return new DeckBoxExportDelegate().getExample(); // what Deckbox itself writes
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
		if (field instanceof Flag) {
			if (isSet(value))
				card.setSpecialTag(((Flag) field).tag);
			return;
		}
		if (field == MagicCardField.FINISH) {
			// "foil" (or blank: not foil)
			CardFinish f = CardFinish.resolve(value);
			if (f != null)
				card.setFinish(f);
			return;
		}
		if (field == MagicCardField.CONDITION) {
			if (isProxy(value))
				card.setProxy(true); // a proxy has no grade
			else
				setConditionOrUnknown(card, value, condition(value));
			return;
		}
		if (field == MagicCardField.LANG) {
			importData.declareLanguage(card, value == null ? null : value.trim());
			return;
		}
		if (scryfallColumn && (field == MagicCardField.SET || field == MagicCardField.COLLNUM
				|| field == MagicCardField.EDITION_ABBR)) {
			// the Scryfall ID may come later in the row: decided when the card is complete
			placeCells.computeIfAbsent(card, k -> new java.util.ArrayList<>()).add(new Object[] { field, i, value });
			return;
		}
		if (field == MagicCardField.PRICE) {
			float price = price(value);
			if (price > 0)
				card.setPrice(inManaDeskCurrency(price, value));
			return;
		}
		if (field == MagicCardField.FORTRADECOUNT || field == MagicCardField.COUNT) {
			if (value == null || value.trim().isEmpty())
				return;
			value = value.trim();
		}
		super.setFieldValue(card, field, i, value);
	}

	/**
	 * Tradelist Count only says whether the pile is for trade (the "fortrade" tag, see {@link ForTradeRules}): it
	 * never raises Count. A cell above Count is read as Count, so the whole pile is for trade.
	 */
	@Override
	protected void importCard(MagicCardPhysical card) {
		if (card != null) {
			java.util.List<Object[]> cells = placeCells.remove(card);
			boolean hasId = card.getCardId() != null && !card.getCardId().trim().isEmpty();
			if (cells != null && !hasId) {
				// no Scryfall ID in this row: the card is found by its set (name or code), number and name
				for (Object[] cell : cells)
					super.setFieldValue(card, (ICardField) cell[0], (Integer) cell[1], (String) cell[2]);
			}
			int forTrade = card.getForTrade();
			if (forTrade > card.getCount())
				card.set(MagicCardField.FORTRADECOUNT, Integer.valueOf(card.getCount()));
		}
		super.importCard(card);
	}

	/** The number in a price cell ("$1.50", "1.50", "$1,234.50"); 0 when there is none. */
	static float price(String value) {
		if (value == null)
			return 0;
		String digits = value.replaceAll("[^0-9.]", "");
		if (digits.isEmpty() || digits.equals("."))
			return 0;
		try {
			return Float.parseFloat(digits);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** Deckbox prices are US dollars ("$1.50"): converted when a rate is known, else kept as written. */
	private static float inManaDeskCurrency(float price, String cell) {
		if (cell == null || !cell.contains("$"))
			return price;
		try {
			Currency to = CurrencyConvertor.getCurrency();
			Currency usd = Currency.getInstance("USD");
			if (to != null && !to.equals(usd))
				return CurrencyConvertor.convertFromInto(price, usd, to);
		} catch (RuntimeException e) {
			// no rate (offline): keep the price as written
		}
		return price;
	}

	/** Deckbox's condition "Proxy" (any case): the copy is a proxy, not a card in some condition. */
	static boolean isProxy(String value) {
		return value != null && value.trim().equalsIgnoreCase("proxy");
	}

	/** A Deckbox flag cell: any text other than blank / false / 0 turns it on. */
	static boolean isSet(String value) {
		if (value == null)
			return false;
		String v = value.trim().toLowerCase(Locale.ENGLISH);
		return !(v.isEmpty() || v.equals("false") || v.equals("0") || v.equals("no"));
	}

	/**
	 * Deckbox's grades - Mint, Near Mint, Good (Lightly Played), Played, Heavily Played, Poor - mapped to the
	 * closest ManaDesk grade: Mint / Near Mint -&gt; NM, Good (Lightly Played) -&gt; LP, Played -&gt; MP,
	 * Heavily Played -&gt; HP, Poor -&gt; DMG. ManaDesk's own names are accepted too. Blank / unknown: not
	 * graded.
	 */
	static CardCondition condition(String value) {
		if (value == null)
			return null;
		switch (value.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z]", "")) {
		case "mint":
		case "nearmint":
			return CardCondition.NEAR_MINT;
		case "goodlightlyplayed":
		case "good":
			return CardCondition.LIGHTLY_PLAYED;
		case "played":
			return CardCondition.MODERATELY_PLAYED;
		case "heavilyplayed":
			return CardCondition.HEAVILY_PLAYED;
		case "poor":
			return CardCondition.DAMAGED;
		default:
			return CardCondition.resolve(value);
		}
	}

	private static String norm(String header) {
		return header == null ? "" : header.trim().toLowerCase(Locale.ENGLISH);
	}
}
