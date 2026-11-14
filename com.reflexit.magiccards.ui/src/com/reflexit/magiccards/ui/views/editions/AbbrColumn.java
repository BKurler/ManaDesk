/*
 * Contributors:
 *     Rémi Dutil (2026) - read-only: abbreviations (and the old two-letter codes) only come from the database update
 */

package com.reflexit.magiccards.ui.views.editions;

import org.eclipse.jface.viewers.ColumnViewer;
import org.eclipse.jface.viewers.EditingSupport;

import com.reflexit.magiccards.core.model.Edition;

public class AbbrColumn extends AbstractEditionColumn {
	public AbbrColumn() {
		super("Abbr", EditionField.NAME);
	}

	@Override
	public String getText(Object element) {
		Edition ed = (Edition) element;
		String abbrs = ed.getMainAbbreviation();
		if (ed.getExtraAbbreviations().length() > 0)
			abbrs += " (" + ed.getExtraAbbreviations() + ")";
		return abbrs;
	}

	/** Not editable: the abbreviations are set by the database update. */
	@Override
	public EditingSupport getEditingSupport(ColumnViewer viewer) {
		return null;
	}

	@Override
	public int getColumnWidth() {
		return 100;
	}
}
