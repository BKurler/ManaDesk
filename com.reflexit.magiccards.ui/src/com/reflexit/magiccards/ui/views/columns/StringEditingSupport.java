package com.reflexit.magiccards.ui.views.columns;

import java.util.Collections;
import java.util.Set;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnViewer;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.ForTradeRules;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.model.abs.ICardModifiable;
import com.reflexit.magiccards.core.xml.StringCache;

/*
 * Contributors:
 *     Rémi Dutil (2026) - the Special text of a card in a For Trade collection keeps the "fortrade" tag
 */
public class StringEditingSupport extends EditingSupport {
	private AbstractColumn column;

	public StringEditingSupport(ColumnViewer viewer, AbstractColumn col) {
		super(viewer);
		this.column = col;
	}

	@Override
	protected boolean canEdit(Object element) {
		return element instanceof ICardModifiable;
	}

	@Override
	protected CellEditor getCellEditor(final Object element) {
		return new TextCellEditor((Composite) getViewer().getControl(), SWT.NONE);
	}

	@Override
	protected Object getValue(Object element) {
		return column.getText(element);
	}

	@Override
	protected void setValue(Object element, Object value) {
		ICardModifiable card = (ICardModifiable) element;
		if (card instanceof MagicCardPhysical) {
			// a For Trade collection keeps the "fortrade" tag on every card
			MagicCardPhysical mcp = (MagicCardPhysical) card;
			String veto = ForTradeRules.fieldVeto(DataManager.getInstance().getCardStore(mcp.getLocation()),
					column.getDataField(), value);
			if (veto != null) {
				MessageDialog.openError(getViewer().getControl().getShell(), column.getColumnName(), veto);
				return;
			}
		}
		card.set(column.getDataField(), StringCache.intern((String) value));
		Set<ICardField> of = Collections.singleton(column.getDataField());
		DataManager.getInstance().update((IMagicCard) card, of);
		// getViewer().refresh(true);
	}
}