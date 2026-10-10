/*
 * Contributors:
 *     Rémi Dutil (2026) - ownership follows the card's deck / collection
 *                         (OwnershipRules): only a card whose stored value
 *                         does not match its list is editable, and only to
 *                         the matching value; the tooltip says why
 */
package com.reflexit.magiccards.ui.views.columns;

import java.util.Collections;
import java.util.Set;

import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnViewer;
import org.eclipse.jface.viewers.ComboBoxCellEditor;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.IMagicCardPhysical;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.OwnershipRules;

/**
 * @author Alena
 * 
 */
public class OwnershipColumn extends GenColumn {
	/**
	 * @param columnName
	 */
	public OwnershipColumn() {
		super(MagicCardField.OWNERSHIP, "O");
	}

	protected boolean isOwn(Object element) {
		boolean own = false;
		if (element instanceof IMagicCardPhysical) {
			IMagicCardPhysical m = (IMagicCardPhysical) element;
			own = m.isOwn();
		}
		return own;
	}

	@Override
	public int getColumnWidth() {
		return 25; // +3px
	}

	@Override
	public String getText(Object element) {
		if (isOwn(element))
			return "O";
		else
			return "V";
	}

	@Override
	public String getToolTipText(Object element) {
		String text = isOwn(element) ? "Own (Physical or Online)" : "Virtual";
		if (element instanceof IMagicCardPhysical) {
			IMagicCardPhysical card = (IMagicCardPhysical) element;
			if (!OwnershipRules.canSetOwn(card, !card.isOwn()))
				text += " - " + OwnershipRules.NO_SET_OWNERSHIP;
		}
		return text;
	}

	// @Override
	// public Color getBackground(Object element) {
	// if (isOwn(element))
	// return oColor;
	// else
	// return vColor;
	// }
	@Override
	public String getColumnFullName() {
		return "Ownership";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.reflexit.magiccards.ui.views.columns.ColumnManager#getEditingSupport
	 * (org.eclipse.jface .viewers.ColumnViewer)
	 */
	@Override
	public EditingSupport getEditingSupport(final ColumnViewer viewer) {
		return new EditingSupport(viewer) {
			@Override
			protected boolean canEdit(Object element) {
				// only a card that does not match its list can change - towards it
				if (!(element instanceof MagicCardPhysical))
					return false;
				MagicCardPhysical card = (MagicCardPhysical) element;
				return OwnershipRules.canSetOwn(card, !card.isOwn());
			}

			@Override
			protected CellEditor getCellEditor(final Object element) {
				final Integer pos = (Integer) getValue(element);
				CellEditor editor = new ComboBoxCellEditor((Composite) viewer.getControl(), new String[] {
						"own", "virtual" },
						SWT.READ_ONLY) {
				};
				editor.setValue(pos);
				return editor;
			}

			@Override
			protected Object getValue(Object element) {
				if (element instanceof MagicCardPhysical) {
					IMagicCardPhysical card = (IMagicCardPhysical) element;
					Boolean ow = card.isOwn();
					return ow ? 0 : 1;
				}
				return null;
			}

			@Override
			protected void setValue(Object element, Object value) {
				if (element instanceof MagicCardPhysical) {
					MagicCardPhysical card = (MagicCardPhysical) element;
					// set
					boolean own = value.toString().equals("0");
					if (own == card.isOwn() || !OwnershipRules.canSetOwn(card, own))
						return;
					card.setOwn(own);
					// update
					Set<MagicCardField> of = Collections.singleton(MagicCardField.OWNERSHIP);
					DataManager.getInstance().update(card, of);
					// viewer.update(element, null);
				}
			}
		};
	}
}