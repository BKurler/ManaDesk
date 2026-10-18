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
package com.reflexit.magiccards.ui.views.columns;

import org.eclipse.swt.graphics.Image;

import com.reflexit.magiccards.core.model.Colors;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.ui.utils.SymbolRenderer;

/**
 * The mana a card can produce (Scryfall's {@code produced_mana}), as symbols - next to
 * the Color Identity and Extended Color Identity columns. Blank for a card that makes no
 * mana (a fetch land only finds a land: see the extended identity for that).
 */
public class ProducedManaColumn extends AbstractImageColumn {
	public ProducedManaColumn() {
		super(MagicCardField.PRODUCED_MANA, "Produced Mana");
	}

	@Override
	public int getColumnWidth() {
		return 75;
	}

	@Override
	public String getText(Object element) {
		if (element instanceof IMagicCard) {
			String cost = ((IMagicCard) element).getString(MagicCardField.PRODUCED_MANA);
			return cost == null || cost.isEmpty() ? "" : Colors.getColorName(cost);
		}
		return "";
	}

	@Override
	public Image getActualImage(Object element) {
		if (element instanceof IMagicCard) {
			String cost = ((IMagicCard) element).getString(MagicCardField.PRODUCED_MANA);
			if (cost != null && !cost.isEmpty())
				return SymbolRenderer.buildCostImage(cost);
		}
		return null;
	}
}
