/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk: Scryfall Database's own column set -
 *                  drop Sideboard/Extra/Date/User Price/Ownership/Count/
 *                  Location/Condition/Proxy/Comment/Special/For Trade, every
 *                  one of which is a single-copy attribute that can differ
 *                  across every deck/collection a card is owned in, so a
 *                  single value per row here would be misleading. Pulled out
 *                  of SplitViewer.doGetColumnCollection()'s anonymous class
 *                  into a real, named class: that anonymous class was
 *                  invisible outside SplitViewer, so MagicDbViewPreferencePage's
 *                  "Visible Columns and Order" list built its own separate,
 *                  unfiltered MagicColumnCollection instead and kept
 *                  offering every one of these columns regardless of this
 *                  exclusion - both now share this one class instead of two
 *                  copies of the same list silently drifting apart.
 *     Rémi Dutil (2026) - createFinishColumn(): +30px here only (+15, then
 *                         +15 more) - a printing browsed here has no single
 *                         owned copy, so the cell shows every finish the
 *                         printing supports joined together ("Nonfoil, Foil,
 *                         Etched"), which needs more room than a
 *                         single-copy view's one-word value
 *******************************************************************************/
package com.reflexit.magiccards.ui.views.columns;

import java.util.EnumSet;
import java.util.List;

import com.reflexit.magiccards.core.model.MagicCardField;

public class MagicDbColumnCollection extends MagicColumnCollection {
	private static final EnumSet<MagicCardField> NOT_APPLICABLE_TO_DB_BROWSING = EnumSet.of(MagicCardField.SIDEBOARD,
			MagicCardField.EXTRA, MagicCardField.DATE, MagicCardField.PRICE, MagicCardField.OWNERSHIP,
			MagicCardField.COUNT, MagicCardField.LOCATION, MagicCardField.CONDITION, MagicCardField.PROXY,
			MagicCardField.COMMENT, MagicCardField.SPECIAL, MagicCardField.FORTRADECOUNT);

	public MagicDbColumnCollection(String prefPageId) {
		super(prefPageId);
	}

	@Override
	protected GroupColumn createGroupColumn() {
		return new GroupColumn(false, true, false);
	}

	@Override
	protected void createColumns(List<AbstractColumn> columns) {
		super.createColumns(columns);
		columns.removeIf(c -> NOT_APPLICABLE_TO_DB_BROWSING.contains(c.getDataField()));
	}

	@Override
	protected AbstractColumn createFinishColumn() {
		return new FinishColumn() {
			@Override
			public int getColumnWidth() {
				return super.getColumnWidth() + 30;
			}
		};
	}
}
