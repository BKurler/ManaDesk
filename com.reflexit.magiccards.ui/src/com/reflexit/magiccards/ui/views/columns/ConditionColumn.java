/*
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: editable card-condition column
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
import com.reflexit.magiccards.core.model.CardCondition;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;

/**
 * The physical grade of a card copy. Cell text is the grade label ("Near Mint"),
 * blank for an ungraded copy - rendered by {@link AbstractColumn#getActualText}
 * straight from {@link CardCondition#toString()}. Editable via a drop-down whose
 * first entry clears the grade.
 */
public class ConditionColumn extends GenColumn {
	/** index 0 = "not graded", then one entry per {@link CardCondition} in order. */
	static final String[] CHOICES;
	static {
		CardCondition[] v = CardCondition.values();
		CHOICES = new String[v.length + 1];
		CHOICES[0] = "—"; // em dash
		for (int i = 0; i < v.length; i++)
			CHOICES[i + 1] = v[i].getLabel();
	}

	public ConditionColumn() {
		super(MagicCardField.CONDITION, "Condition");
	}

	@Override
	public int getColumnWidth() {
		return 110;
	}

	static CardCondition fromIndex(int idx) {
		return idx <= 0 ? null : CardCondition.values()[idx - 1];
	}

	static int toIndex(CardCondition c) {
		return c == null ? 0 : c.ordinal() + 1;
	}

	@Override
	public EditingSupport getEditingSupport(final ColumnViewer viewer) {
		return new EditingSupport(viewer) {
			@Override
			protected boolean canEdit(Object element) {
				return element instanceof MagicCardPhysical;
			}

			@Override
			protected CellEditor getCellEditor(Object element) {
				ComboBoxCellEditor editor = new ComboBoxCellEditor((Composite) viewer.getControl(), CHOICES,
						SWT.READ_ONLY);
				editor.setValue(getValue(element));
				return editor;
			}

			@Override
			protected Object getValue(Object element) {
				if (element instanceof MagicCardPhysical)
					return toIndex(((MagicCardPhysical) element).getCondition());
				return 0;
			}

			@Override
			protected void setValue(Object element, Object value) {
				if (!(element instanceof MagicCardPhysical))
					return;
				int idx = value instanceof Integer ? ((Integer) value).intValue()
						: Integer.parseInt(String.valueOf(value));
				CardCondition next = fromIndex(idx);
				MagicCardPhysical card = (MagicCardPhysical) element;
				if (card.getCondition() == next)
					return;
				card.setCondition(next);
				Set<MagicCardField> of = Collections.singleton(MagicCardField.CONDITION);
				DataManager.getInstance().update(card, of);
			}
		};
	}
}
