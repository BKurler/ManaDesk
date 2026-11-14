/*
 * Contributors:
 *     Rémi Dutil (2026) - read-only: the aliases are not edited by hand any more
 */

package com.reflexit.magiccards.ui.views.editions;

import org.eclipse.jface.viewers.ColumnViewer;
import org.eclipse.jface.viewers.EditingSupport;

import com.reflexit.magiccards.core.model.Edition;

public class AliasesColumn extends AbstractEditionColumn {
	public AliasesColumn() {
		super("Aliases", EditionField.ALIASES);
	}

	@Override
	public String getText(Object element) {
		Edition ed = (Edition) element;
		return ed.getExtraAliases();
	}

	/** Not editable: the aliases are read from editions.txt. */
	@Override
	public EditingSupport getEditingSupport(ColumnViewer viewer) {
		return null;
	}

	@Override
	public int getColumnWidth() {
		return 100;
	}
}
