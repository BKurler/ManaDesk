/*
 * Contributors:
 *     Rémi Dutil (2026) - drop the Sideboard/Extra columns for deck and
 *                         collection views: within a single deck/collection's
 *                         own tab every row already belongs to the one pile
 *                         you opened, so the columns are always the same
 *                         value and never useful there - unlike My Cards,
 *                         which searches across every pile of every deck at
 *                         once and needs them to tell rows apart
 *     Rémi Dutil (2026) - same reasoning drops Location too: every row in a
 *                         single deck/collection tab is in that one deck or
 *                         collection, so the column can never show anything
 *                         but the same value
 */
package com.reflexit.magiccards.ui.views.lib;

import java.util.List;

import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.ui.views.columns.AbstractColumn;
import com.reflexit.magiccards.ui.views.columns.MagicColumnCollection;

public class DeckColumnCollection extends MagicColumnCollection {
	public DeckColumnCollection(String prefPageId) {
		super(prefPageId);
	}

	@Override
	protected void createColumns(List<AbstractColumn> columns) {
		super.createColumns(columns);
		columns.removeIf(c -> c.getDataField() == MagicCardField.SIDEBOARD || c.getDataField() == MagicCardField.EXTRA
				|| c.getDataField() == MagicCardField.LOCATION);
	}
}
