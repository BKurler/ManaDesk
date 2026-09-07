
/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - full deck/collection column set + "Properties..." header menu
 */

package com.reflexit.magiccards.ui.views.instances;

import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.part.PluginTransfer;
import org.eclipse.ui.services.IDisposable;

import com.reflexit.magiccards.ui.dnd.MagicCardDragListener;
import com.reflexit.magiccards.ui.dnd.MagicCardTransfer;
import com.reflexit.magiccards.ui.views.ExtendedTreeViewer;

public class InstancesViewer extends ExtendedTreeViewer implements IDisposable {

	protected InstancesViewer(String id, Composite parent) {
		super(parent, id);
		setComparator(null);
	}

	@Override
	public void hookDragAndDrop(StructuredViewer viewer) {
		this.getViewer().getControl().setDragDetect(true);
		int ops = DND.DROP_COPY | DND.DROP_MOVE;
		viewer.addDragSupport(ops, new Transfer[] { MagicCardTransfer.getInstance(), TextTransfer.getInstance(),
				PluginTransfer.getInstance() }, new MagicCardDragListener(viewer));
	}

	@Override
	public void refresh() {
		super.refresh();
	}

	@Override
	protected Menu createColumnHeaderContextMenu(int index) {
		if (index < 0)
			return null;
		Menu menu = new Menu(getControl());
		MenuItem props = new MenuItem(menu, SWT.PUSH);
		props.setText("Properties...");
		props.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				openColumnDialog();
			}
		});
		return menu;
	}

	private void openColumnDialog() {
		String id = getColumnsCollection().getId();
		PreferenceDialog dialog = PreferencesUtil.createPreferenceDialogOn(getControl().getShell(), id,
				new String[] { id }, null);
		dialog.open();
	}

	@Override
	public void updateColumns(String preferenceValue) {
		super.updateColumns(preferenceValue);
	}

	@Override
	public String getColumnLayoutProperty() {
		return super.getColumnLayoutProperty();
	}

}
