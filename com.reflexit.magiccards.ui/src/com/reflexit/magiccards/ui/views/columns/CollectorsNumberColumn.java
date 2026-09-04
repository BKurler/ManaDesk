/*
 * Contributors:
 *     Rémi Dutil (2026) - collector number made editable
 */
package com.reflexit.magiccards.ui.views.columns;

import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.jface.viewers.ColumnViewer;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.swt.SWT;

import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.widgets.ComboStringEditingSupport;

final class CollectorsNumberColumn extends GenColumn {
	public CollectorsNumberColumn() {
		super(MagicCardField.COLLNUM, "Num");
	}

	@Override
	public String getColumnFullName() {
		return "Collector's Number";
	}

	@Override
	public int getColumnWidth() {
		return 60; // room for the edit combo + its drop-down arrow
	}

	@Override
	public EditingSupport getEditingSupport(ColumnViewer viewer) {
		return new NumberEditingSupport(viewer);
	}

	/**
	 * Lets the user re-bind a deck / collection card to another printing in the
	 * <em>same set</em> - the drop-down lists every collector number that set has
	 * for that card name (normal, showcase, borderless, promo, ...), lowest first.
	 */
	public class NumberEditingSupport extends ComboStringEditingSupport {
		public NumberEditingSupport(ColumnViewer viewer) {
			super(viewer);
		}

		@Override
		protected boolean canEdit(Object element) {
			return element instanceof MagicCardPhysical;
		}

		@Override
		public int getStyle() {
			return SWT.NONE;
		}

		@Override
		public String[] getItems(Object element) {
			MagicCardPhysical card = (MagicCardPhysical) element;
			Set<String> nums = new LinkedHashSet<>();
			for (IMagicCard base : Printings.inSet(card.getName(), card.getSet())) {
				String n = base.getCollectorId();
				if (n != null && !n.isEmpty())
					nums.add(n);
			}
			if (card.getCollectorId() != null && !card.getCollectorId().isEmpty())
				nums.add(card.getCollectorId());
			return nums.toArray(new String[nums.size()]);
		}

		@Override
		protected Object getValue(Object element) {
			return ((MagicCardPhysical) element).getCollectorId();
		}

		@Override
		protected void setValue(Object element, Object value) {
			MagicCardPhysical card = (MagicCardPhysical) element;
			String num = (String) value;
			if (num == null || num.equals(card.getCollectorId()))
				return;
			for (IMagicCard base : Printings.inSet(card.getName(), card.getSet())) {
				if (num.equals(base.getCollectorId())) {
					card.setMagicCard((MagicCard) base);
					updateOnEdit(getViewer(), card);
					return;
				}
			}
			MagicUIActivator.log("Cannot set collector number " + num + " for " + card);
		}
	}
}
