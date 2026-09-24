/*
 * Contributors:
 *     Rémi Dutil 2026 - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - getActualText()/getActualImage(): bypass the
 *                         "transient group -> show first child" shortcut so
 *                         a Collector Name-group spanning printings of
 *                         different colors shows the combined color (via
 *                         the new ColorUnionAggregator) instead of just the
 *                         first printing's - same fix FinishColumn/
 *                         MagicColumnCollection's id columns needed
 */
package com.reflexit.magiccards.ui.views.columns;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Listener;

import com.reflexit.magiccards.core.model.Colors;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.abs.ICard;
import com.reflexit.magiccards.ui.utils.SymbolRenderer;

public class ColorColumn extends AbstractImageColumn implements Listener {
	public ColorColumn() {
		super(MagicCardField.COLOR, "Color");
	}

	@Override
	public int getColumnWidth() {
		return 55; // +15px from AbstractImageColumn's inherited 40
	}

	/** Bypasses AbstractColumn#getActualText()'s "transient group -> just show
	 *  the first child" shortcut, which always won for a Collector Name-group
	 *  (isTransient() is unconditionally true for those) and made a group
	 *  spanning printings with different colors silently show only the first
	 *  one's, instead of running COLOR's own aggregator (a "*" on mismatch) -
	 *  same fix FinishColumn/ProxyColumn already needed for their own fields. */
	@Override
	protected String getActualText(Object element) {
		if (element instanceof ICard) {
			Object value = ((ICard) element).get(getDataField());
			return value == null ? "" : value.toString();
		}
		return super.getActualText(element);
	}

	@Override
	public String getText(Object element) {
		String text = super.getText(element);
		if (text.length() == 0)
			return text;
		return Colors.getColorName(text);
	}

	/** Reads the icon straight off COLOR's own (now group-aware) value -
	 *  {@link Colors#getColorAsCost} re-derives colors from the Cost field
	 *  instead, which for a Collector Name-group never picked up the fix
	 *  above: Cost's own default aggregator still collides to "*" (or just
	 *  agrees, coincidentally, when every printing shares one cost), neither
	 *  of which is the group's actual combined color. */
	@Override
	public Image getActualImage(Object element) {
		if (element instanceof ICard) {
			Object value = ((ICard) element).get(getDataField());
			return SymbolRenderer.buildCostImage(value == null ? "" : value.toString());
		}
		return null;
	}
}
