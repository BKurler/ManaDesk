/*
 * Contributors:
 *     Rémi Dutil (2026) - Printings view column set: card-database facts only
 */
package com.reflexit.magiccards.ui.views.columns;

import java.util.List;

import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.ui.MagicUIActivator;

/**
 * Columns offered by the Printings view. Only fields that belong to the shared
 * card database are selectable - nothing tied to a collection or a deck (count,
 * location, ownership, comment, user price, special, for-trade, sideboard,
 * extra, ...). The collector number is mandatory and cannot be hidden.
 */
public class PrintingsColumnCollection extends MagicColumnCollection {
	public PrintingsColumnCollection(String prefPageId) {
		super(prefPageId);
	}

	@Override
	protected void createColumns(List<AbstractColumn> columns) {
		// Only fields that actually differ between printings of the same card.
		// Cost / Type / Power / Toughness / Oracle text / Color* / Legality are
		// the same on every printing, so they carry no information here and are
		// left out.
		columns.add(createGroupColumn());              // "Name" - structural, always visible
		columns.add(createCollectorsNumberColumn());   // "Collector's Number" - mandatory
		columns.add(createSetColumn());                // "Set"
		columns.add(new GenColumn(MagicCardField.RARITY, "Rarity"));
		columns.add(createGathererIdColumn());         // "Multiverse ID"
		columns.add(new GenColumn(MagicCardField.ARTIST, "Artist"));
		columns.add(new LanguageColumn());            // "Language"
		columns.add(new ReleaseDateColumn());         // "Release Date"
		columns.add(createIdColumn());                 // "Card Id"
		columns.add(new SellerPriceColumn());        // "Online Price"
		columns.add(new GenColumn(MagicCardField.TCGID, "TCGplayer ID"));
		if (MagicUIActivator.TRACE_EXPORT) {
			columns.add(new GenColumn(MagicCardField.HASHCODE, "HashCode"));
		}
	}

	@Override
	public void updateColumnsFromPropery(String value) {
		super.updateColumnsFromPropery(value);
		// The collector number identifies which printing a row is - keep it on
		// no matter what the stored preference says.
		AbstractColumn collNum = getColumn(MagicCardField.COLLNUM);
		if (collNum != null)
			collNum.setVisible(true);
	}
}
