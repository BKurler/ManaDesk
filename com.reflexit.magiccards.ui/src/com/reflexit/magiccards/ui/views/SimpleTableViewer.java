/*
 * Contributors:
 *     Rémi Dutil (2026) - createSortComparator(): a viewer can sort a column with its own comparator, without
 *                         the shared sort order being installed (and refreshing the table) first
 */
package com.reflexit.magiccards.ui.views;

import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import com.reflexit.magiccards.ui.views.columns.AbstractColumn;
import com.reflexit.magiccards.ui.views.columns.ColumnCollection;
import com.reflexit.magiccards.ui.views.model.SortOrderViewerComparator;

public class SimpleTableViewer extends ExtendedTableViewer {
	public SimpleTableViewer(Composite parent, ColumnCollection collection) {
		super(parent, collection);
	}

	@Override
	public void setSortColumn(int index, int direction) {
		super.setSortColumn(index, direction);
		if (index >= 0) {
			AbstractColumn man = (AbstractColumn) getColumnViewer().getLabelProvider(index);
			getColumnViewer().setComparator(createSortComparator(index, man, getSortDirection() == SWT.UP));
		} else {
			getColumnViewer().setComparator(null);
		}
	}

	/**
	 * The comparator for a click on the header of {@code man}. The shared sort order by default; a subclass
	 * may return its own for a column whose values the shared one cannot compare. Setting a comparator on the
	 * viewer refreshes the table at once, so the shared one must not be set first.
	 */
	protected ViewerComparator createSortComparator(int index, AbstractColumn man, boolean ascending) {
		SortOrderViewerComparator vcomp = manager.getViewerComparator();
		vcomp.setOrder(man.getSortField(), ascending);
		return vcomp;
	}
	// @Override
	// protected void doUpdateItem(Widget widget, Object element, boolean
	// fullMap) {
	// System.err.println("request to update item: " + element);
	// super.doUpdateItem(widget, element, fullMap);
	// }
}
