/*
 * Contributors:
 *     Rémi Dutil (2026) - the header row follows "Generate header row"
 *     Rémi Dutil (2026) - rewritten for ManaDesk: the inventory columns without the Tradelist Count
 *     Rémi Dutil (2026) - same columns as the inventory (Edition Code, Tags, Scryfall ID), without the Tradelist Count
 */
package com.reflexit.magiccards.core.exports;

import com.reflexit.magiccards.core.model.abs.ICardField;

/** A Deckbox Wishlist CSV: the Inventory columns without the Tradelist Count. */
public class DeckBoxWishlistExportDelegate extends DeckBoxExportDelegate {
	public static final String HEADER_W = header(wishlistColumns());

	/** All the inventory columns but the Tradelist Count, in the order of the inventory. */
	private static ICardField[] wishlistColumns() {
		ICardField[] all = Column.values();
		ICardField[] fields = new ICardField[all.length - 1];
		int k = 0;
		for (ICardField f : all)
			if (f != Column.TRADELIST_COUNT)
				fields[k++] = f;
		return fields;
	}

	@Override
	protected ICardField[] doGetFields() {
		return wishlistColumns();
	}

	@Override
	public String getContentSlug() {
		return "deckbox-wishlist";
	}

	@Override
	public String getExample() {
		return HEADER_W + "\n" //
				+ "3,Platinum Angel,Magic 2010,m10,218,Near Mint,English,,,,,,,$0.00,\n" //
				+ "4,Reya Dawnbringer,Duel Decks: Divine vs. Demonic,ddc,13,Good (Lightly Played),English,foil,,,,,,$2.50,\n";
	}
}
