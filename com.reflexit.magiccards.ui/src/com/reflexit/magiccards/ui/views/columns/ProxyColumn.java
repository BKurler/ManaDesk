/*
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: editable per-copy "Proxy" column
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
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICard;

/**
 * Whether a card copy is a home-printed proxy rather than the real card. Cell
 * text is "Proxy" for a proxy copy, blank otherwise. Editable via a drop-down
 * (Genuine / Proxy). A colliding group shows "*".
 */
public class ProxyColumn extends GenColumn {
	static final String[] CHOICES = { "Genuine", "Proxy" };

	public ProxyColumn() {
		super(MagicCardField.PROXY, "Proxy");
	}

	@Override
	public int getColumnWidth() {
		return 60;
	}

	@Override
	public String getColumnFullName() {
		return "Proxy";
	}

	@Override
	public String getText(Object element) {
		if (element instanceof ICard) {
			Object v = ((ICard) element).get(MagicCardField.PROXY);
			if ("*".equals(v))
				return "*";
			if (Boolean.TRUE.equals(v) || "true".equals(v))
				return "Proxy";
		}
		return "";
	}

	static boolean isProxy(Object element) {
		return element instanceof MagicCardPhysical && ((MagicCardPhysical) element).isProxy();
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
				return isProxy(element) ? 1 : 0;
			}

			@Override
			protected void setValue(Object element, Object value) {
				if (!(element instanceof MagicCardPhysical))
					return;
				int idx = value instanceof Integer ? ((Integer) value).intValue()
						: Integer.parseInt(String.valueOf(value));
				boolean next = idx == 1;
				MagicCardPhysical card = (MagicCardPhysical) element;
				if (card.isProxy() == next)
					return;
				card.setProxy(next);
				Set<MagicCardField> of = Collections.singleton(MagicCardField.PROXY);
				DataManager.getInstance().update(card, of);
			}
		};
	}
}
