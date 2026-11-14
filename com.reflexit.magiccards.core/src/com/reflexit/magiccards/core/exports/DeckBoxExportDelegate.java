/*
 * Contributors:
 *     Rémi Dutil (2026) - fixed columns of Deckbox's own export (Edition Code, Tags, Scryfall ID; no Promo, Textless, Printing Id/Note), a
 *                         proxy is the condition "Proxy", the language of a "lang=" tag
 *     Rémi Dutil (2026) - the header row follows "Generate header row"
 *     Rémi Dutil (2026) - rewritten for ManaDesk: Deckbox's own inventory columns, first-class Condition /
 *                         Finish / price / for-trade count
 */
package com.reflexit.magiccards.core.exports;

import java.lang.reflect.InvocationTargetException;
import java.util.Currency;
import java.util.Locale;

import com.reflexit.magiccards.core.model.CardCondition;
import com.reflexit.magiccards.core.model.CardFinish;
import com.reflexit.magiccards.core.model.Editions;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.SpecialTags;
import com.reflexit.magiccards.core.model.abs.ICard;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;
import com.reflexit.magiccards.core.sync.CurrencyConvertor;

/**
 * Exports a deck / collection as a Deckbox Inventory CSV, the columns of Deckbox's own export, so Deckbox
 * imports it as is (Edition is the set's name, Card Number its collector number):
 *
 * <pre>
 * Count,Tradelist Count,Name,Edition,Edition Code,Card Number,Condition,Language,Foil,Signed,Artist Proof,
 * Altered Art,Misprint,Tags,My Price,Scryfall ID
 * </pre>
 *
 * The columns are fixed: every column listed is always written, the user does not choose them. Promo and Textless
 * (deprecated on Deckbox) and Printing Id and Printing Note (Deckbox's own numbers and notes) are not written: Deckbox
 * does not need them. A proxy is written as the condition "Proxy", the comment as the Tags, and the language a card
 * kept in its "lang=French" Special tag is the Language of a card exported in English.
 */
public class DeckBoxExportDelegate extends CsvExportDelegate {
	public static final String HEADER = header(Column.values());

	/** One Deckbox column each, with its heading in the file. */
	enum Column implements ICardField {
		COUNT("Count"), TRADELIST_COUNT("Tradelist Count"), NAME("Name"), EDITION("Edition"),
		EDITION_CODE("Edition Code"), CARD_NUMBER("Card Number"), CONDITION("Condition"), LANGUAGE("Language"),
		FOIL("Foil"), SIGNED("Signed"), ARTIST_PROOF("Artist Proof"), ALTERED_ART("Altered Art"),
		MISPRINT("Misprint"), TAGS("Tags"), MY_PRICE("My Price"), SCRYFALL_ID("Scryfall ID");

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

	/** The heading row of {@code columns}: their headings, comma separated. */
	static String header(ICardField[] columns) {
		StringBuilder sb = new StringBuilder();
		for (ICardField c : columns) {
			if (sb.length() > 0)
				sb.append(',');
			sb.append(((Column) c).heading);
		}
		return sb.toString();
	}

	@Override
	public void printHeader() {
		if (header) // "Generate header row"
			stream.println(header(doGetFields()));
	}

	/** The columns, in file order (a subclass may leave some out). */
	protected ICardField[] doGetFields() {
		return Column.values();
	}

	@Override
	public void run(ICoreProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
		setColumns(doGetFields());
		super.run(monitor);
	}

	@Override
	public boolean isColumnChoiceSupported() {
		return false;
	}

	@Override
	public String getContentSlug() {
		return "deckbox";
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
		case EDITION:
			return card.getSet() == null ? "" : card.getSet();
		case EDITION_CODE: {
			// Deckbox's codes are lower case ("m10", "4e")
			String abbr = card.getSet() == null ? null : Editions.getInstance().getAbbrByName(card.getSet());
			return abbr == null ? "" : abbr.toLowerCase(Locale.ENGLISH);
		}
		case CARD_NUMBER:
			return card.getCollectorId() == null ? "" : card.getCollectorId();
		case CONDITION:
			// a proxy has no grade: Deckbox has a condition of its own for it
			if (phi != null && phi.isProxy())
				return "Proxy";
			return condition(phi == null ? null : phi.getCondition());
		case LANGUAGE:
			return language(card, phi);
		case FOIL:
			// Deckbox has one foil flag: an etched foil is a foil
			return phi != null && phi.getFinish() != CardFinish.NONFOIL ? "foil" : "";
		case SIGNED:
			return flag(phi, "signed");
		case ARTIST_PROOF:
			return flag(phi, "proof");
		case ALTERED_ART:
			return flag(phi, "altered");
		case MISPRINT:
			return flag(phi, "misprint");
		case TAGS:
			return phi == null || phi.getComment() == null ? "" : phi.getComment();
		case MY_PRICE:
			return dollars(phi == null ? 0 : phi.getPrice());
		case SCRYFALL_ID:
			return ManaBoxCsvExportDelegate.scryfallId(card.getCardId());
		default:
			return "";
		}
	}

	/**
	 * The Language cell: the card's language, English when it has none. A card imported in English although the file
	 * gave another language keeps it in its "lang=French" Special tag (see ImportUtils): that is what is written.
	 */
	static String language(IMagicCard card, MagicCardPhysical phi) {
		String lang = card.getLanguage() == null ? "English" : card.getLanguage();
		if (phi != null && "English".equalsIgnoreCase(lang)) {
			String kept = phi.getSpecialTagValue(ImportUtils.LANGUAGE_TAG);
			if (kept != null && !kept.trim().isEmpty() && !"true".equalsIgnoreCase(kept))
				return kept.trim();
		}
		return lang;
	}

	/** Deckbox writes prices in US dollars, "$1.50" ("$0.00": no price). */
	static String dollars(float price) {
		if (price > 0) {
			try {
				Currency from = CurrencyConvertor.getCurrency();
				Currency usd = Currency.getInstance("USD");
				if (from != null && !from.equals(usd))
					price = CurrencyConvertor.convertFromInto(price, from, usd);
			} catch (RuntimeException e) {
				// no rate (offline): keep the price as it is
			}
		}
		return price > 0 ? String.format(Locale.ROOT, "$%.2f", price) : "$0.00";
	}

	/** Deckbox's flag cells hold the flag's own word ("signed", "misprint"...). */
	private static String flag(MagicCardPhysical phi, String tag) {
		return phi != null && phi.isSpecialTag(tag) ? tag : "";
	}

	/**
	 * Deckbox's Condition value: each ManaDesk grade goes to the Deckbox grade it is read back from (NM -&gt;
	 * Near Mint, LP -&gt; Good (Lightly Played), MP -&gt; Played, HP -&gt; Heavily Played, DMG -&gt; Poor). A
	 * card that is not graded is exported as Near Mint.
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
			return "Poor";
		default:
			return "Near Mint";
		}
	}

	@Override
	public String getExample() {
		return HEADER + "\n" //
				+ "3,0,Platinum Angel,Magic 2010,m10,218,Near Mint,English,,,,,,,$0.00,\n" //
				+ "4,1,Reya Dawnbringer,Duel Decks: Divine vs. Demonic,ddc,13,Good (Lightly Played),English,foil,,,,,,$2.50,\n";
	}

	/** Legacy per-column fields, still used by the Magic Assistant and ManaDesk CSV importers (not by the Deckbox format itself). */
	public enum ExtraFields implements ICardField {
		FOIL,
		TEXTLESS,
		PROMO,
		SIGNED,
		CONDITION {
			@Override
			public Object aggregateValueOf(ICard card) {
				if (card instanceof MagicCardPhysical) {
					String spe = ((MagicCardPhysical) card).getSpecialTagValue("c");
					if (spe == null)
						return null;
					if (spe.equals("nearmint"))
						return "Near Mint";
					if (spe.equals("good"))
						return "Good (Lightly Played)";
					if (spe.equals("mint"))
						return "Mint";
					if (spe.equals("heavily_played"))
						return "Heavily Played";
					if (spe.equals("poor"))
						return "Poor";
					return spe;
				}
				return "";
			}

			@Override
			public void importInto(MagicCardPhysical card, String value) {
				String tag = null;
				if (value.equals("Near Mint"))
					tag = "nearmint";
				else if (value.startsWith("Good"))
					tag = "good";
				else {
					tag = value.toLowerCase().replaceAll("\\W", "_");
				}
				if (tag != null)
					card.setSpecialTag("c=" + tag);
			}
		};
		@Override
		public boolean isTransient() {
			return false;
		}

		@Override
		public String getLabel() {
			String name = name();
			name = name.charAt(0) + name.substring(1).toLowerCase(Locale.ENGLISH);
			name = name.replace('_', ' ');
			return name;
		}

		public String getTag() {
			return name().toLowerCase(Locale.ENGLISH);
		}

		@Override
		public Object aggregateValueOf(ICard card) {
			if (SpecialTags.getInstance().toMap((IMagicCard) card).containsKey(getTag()))
				return getTag();
			return "";
		}

		public void importInto(MagicCardPhysical card, String value) {
			String tag = getTag();
			if (value.equals(tag)) {
				card.setSpecialTag(tag);
			} else {
				card.removeSpecialTag(tag);
			}
		}
	}
}
