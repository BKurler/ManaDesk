/*
 * Contributors:
 *     Rémi Dutil 2026 - updated for ManaDesk creation and Eclipse 2.0 migration
 */
package com.reflexit.magiccards.ui.views.columns;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Listener;

import com.reflexit.magiccards.core.model.Colors;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.ui.utils.SymbolRenderer;

public class ColorIdentityColumn extends AbstractImageColumn implements Listener {
	public ColorIdentityColumn() {
		super(MagicCardField.COLOR_IDENTITY, "Color Identity");
	}

	@Override
	public int getColumnWidth() {
		return 75; // +20px, then +15px more, from AbstractImageColumn's inherited 40
	}

	@Override
	public String getText(Object element) {
		if (element instanceof IMagicCard) {
			String icost = ((IMagicCard) element).getString(MagicCardField.COLOR_IDENTITY);
			return Colors.getColorName(icost);
		}
		return "";
	}

	@Override
	public Image getActualImage(Object element) {
		if (element instanceof IMagicCard) {
			String icost = ((IMagicCard) element).getString(MagicCardField.COLOR_IDENTITY);
			return SymbolRenderer.buildCostImage(icost);
		}
		return null;
	}
}
