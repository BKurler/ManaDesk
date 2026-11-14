/*******************************************************************************
 * Contributors:
 *     Rémi Dutil (2026) - read-only: it shows the pile's count when the pile has the "fortrade" Special tag
 *******************************************************************************/
package com.reflexit.magiccards.ui.views.columns;

import org.eclipse.jface.viewers.ColumnViewer;
import org.eclipse.jface.viewers.EditingSupport;

import com.reflexit.magiccards.core.model.MagicCardField;

/**
 * The number of copies offered for trade. It cannot be edited: a pile is for trade (all of it) when its Special
 * field has the "fortrade" tag - see {@link com.reflexit.magiccards.core.model.ForTradeRules}. To trade only some
 * copies, split the pile and tag one of the two.
 */
public class ForTradeCountColumn extends CountColumn {
	public ForTradeCountColumn() {
		super(MagicCardField.FORTRADECOUNT, "For Trade");
	}

	@Override
	public EditingSupport getEditingSupport(final ColumnViewer viewer) {
		return null; // read only
	}

	@Override
	protected boolean canEditElement(Object element) {
		return false;
	}
}
