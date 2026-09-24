/*
 * Contributors:
 *     Rémi Dutil (2026) - created: the app's own oracle-text-based color
 *                         identity heuristic, next to (not replacing)
 *                         ColorIdentityColumn (now Scryfall's own
 *                         authoritative color_identity, the default "Color
 *                         Identity" - what Commander-family legality
 *                         actually validates against). This one is looser,
 *                         useful for search (e.g. finding a colorless fetch
 *                         land that still "touches" green)
 */
package com.reflexit.magiccards.ui.views.columns;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Listener;

import com.reflexit.magiccards.core.model.Colors;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.ui.utils.SymbolRenderer;

/**
 * The app's own oracle-text-based color identity heuristic - see
 * {@link ColorIdentityColumn} for Scryfall's own authoritative
 * {@code color_identity}, a different, stricter concept used for legality.
 */
public class ExtendedColorIdentityColumn extends AbstractImageColumn implements Listener {
	public ExtendedColorIdentityColumn() {
		super(MagicCardField.COLOR_IDENTITY_EXTENDED, "Extended Color Identity");
	}

	@Override
	public int getColumnWidth() {
		return 75; // +20px, then +15px more, from AbstractImageColumn's inherited 40
	}

	@Override
	public String getText(Object element) {
		if (element instanceof IMagicCard) {
			String icost = ((IMagicCard) element).getString(MagicCardField.COLOR_IDENTITY_EXTENDED);
			return Colors.getColorName(icost);
		}
		return "";
	}

	@Override
	public Image getActualImage(Object element) {
		if (element instanceof IMagicCard) {
			String icost = ((IMagicCard) element).getString(MagicCardField.COLOR_IDENTITY_EXTENDED);
			return SymbolRenderer.buildCostImage(icost);
		}
		return null;
	}
}
