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
package com.reflexit.magiccards.ui.exportWizards;

import java.util.function.Supplier;

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import com.reflexit.magiccards.core.exports.ImportData;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.ui.views.SimpleTableViewer;
import com.reflexit.magiccards.ui.views.columns.AbstractColumn;
import com.reflexit.magiccards.ui.views.columns.ColumnCollection;

/**
 * The table of the import preview. Two columns cannot use the shared sort order:
 * <ul>
 * <li><b>Error</b>: a card's error is not a comparable value, so the rows are sorted by the short text the
 * column shows ({@link ErrorTextOrder});</li>
 * <li><b>Language</b>: the cards imported as English although the file gave another language (the yellow
 * cells) are grouped ({@link DeclaredLanguageOrder}).</li>
 * </ul>
 * The viewer keeps ONE sort direction for the whole table, so which direction a click on a column gets depends
 * on what was clicked before. For these two sorts the rows to look at must be on top at the first click on
 * their column, not hidden at the bottom of a long list, and a second click reverses: the direction is kept
 * per column here ({@link #rowsOnTop}).
 */
class PreviewTableViewer extends SimpleTableViewer {
	private final Supplier<ImportData> importData;

	PreviewTableViewer(Composite parent, ColumnCollection collection, Supplier<ImportData> importData) {
		super(parent, collection);
		this.importData = importData;
	}

	/** The column of the last click on Error / Language, or -1 when the last click was on another column. */
	private int lastSpecialColumn = -1;

	/** Whether the rows to look at are on top for that column now. */
	private boolean rowsOnTop = true;

	/**
	 * A click on the Error / Language header: the first click on that column (after any other column, or after
	 * the first load) puts the rows to look at on top, the next click on the same column reverses.
	 */
	private boolean clickSpecial(int index) {
		rowsOnTop = lastSpecialColumn == index ? !rowsOnTop : true;
		lastSpecialColumn = index;
		// the arrow of the header follows (the viewer's own toggle was decided by the previous column)
		setControlSortColumn(index, rowsOnTop ? SWT.DOWN : SWT.UP);
		return rowsOnTop;
	}

	/**
	 * The comparator is built here, not set after the shared one: setting a comparator refreshes the table at
	 * once, and the shared sort order throws a ClassCastException on the Error column (an error is an
	 * {@code ImportError} for some rows and a text for others), which made the click do nothing.
	 */
	@Override
	protected ViewerComparator createSortComparator(int index, AbstractColumn man, boolean ascending) {
		ImportData data = importData.get();
		if (man.getSortField() == MagicCardField.ERROR) {
			final ErrorTextOrder order = new ErrorTextOrder(
					c -> c instanceof MagicCardPhysical ? DeckImportPreviewPage.errorShort(((MagicCardPhysical) c).getError())
							: "",
					clickSpecial(index));
			return new ViewerComparator() {
				@Override
				public int compare(Viewer v, Object e1, Object e2) {
					return order.compare(e1, e2);
				}
			};
		}
		if (man.getSortField() == MagicCardField.LANG && data != null && !data.getLanguageOverridden().isEmpty()) {
			final ViewerComparator base = super.createSortComparator(index, man, ascending);
			final DeclaredLanguageOrder order = new DeclaredLanguageOrder(c -> {
				ImportData d = importData.get();
				return d == null ? null : d.getDeclaredLanguage(c);
			}, clickSpecial(index));
			return new ViewerComparator() {
				@Override
				public int compare(Viewer v, Object e1, Object e2) {
					int d = order.compare(e1, e2);
					return d != 0 ? d : base.compare(v, e1, e2);
				}
			};
		}
		lastSpecialColumn = -1; // another column: the next Error / Language click is a first click
		return super.createSortComparator(index, man, ascending);
	}
}
