/*
 * Contributors:
 *     Rémi Dutil (2026) - setLocationFilter(): fixed a background-thread SWT
 *                         access crash (now via WaitUtils.asyncExec), wrote to
 *                         the wrong preference store (filter store, not the
 *                         columns store), and did not match the "-extra" suffix
 */
package com.reflexit.magiccards.ui.views.lib;

import java.util.Collection;
import java.util.Iterator;

import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.swt.widgets.Composite;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.Locations;
import com.reflexit.magiccards.core.model.storage.IFilteredCardStore;
import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.preferences.LibViewPreferencePage;
import com.reflexit.magiccards.ui.utils.WaitUtils;
import com.reflexit.magiccards.ui.views.IViewPage;
import com.reflexit.magiccards.ui.views.ViewPageContribution;

public class MyCardsView extends AbstractMyCardsView {
	public static final String ID = "com.reflexit.magiccards.ui.views.lib.MyCardsView";
	private IFilteredCardStore mystore;
	private MyCardsListControl page;

	public MyCardsView() {
	}

	class MyCardPresentation extends MyCardsListControl {
		@Override
		public IFilteredCardStore doGetFilteredStore() {
			return mystore;
		}

		@Override
		protected void hookDoubleClickAction() {
			viewer.addDoubleClickListener(new IDoubleClickListener() {
				@Override
				public void doubleClick(DoubleClickEvent event) {
					MyCardsView.this.runDoubleClick();
				}
			});
		}

		@Override
		protected void makeActions() {
			super.makeActions();
		}
	}

	public MyCardsView getView() {
		return this;
	}

	@Override
	protected void createPages() {
		page = new MyCardPresentation();
		getPageGroup().add(new ViewPageContribution("", "Main", null, page));
	}

	@Override
	protected IViewPage getActivePage() {
		return page;
	}

	@Override
	protected void createMainControl(Composite parent) {
		super.createMainControl(parent);
	}

	@Override
	protected void makeActions() {
		super.makeActions();
	}

	@Override
	protected void fillLocalToolBar(IToolBarManager manager) {
		super.fillLocalToolBar(manager);
	}

	@Override
	public String getHelpId() {
		return MagicUIActivator.helpId("viewcol");
	}

	@Override
	protected void loadInitialInBackground() {
		super.loadInitialInBackground();
		mystore = DataManager.getCardHandler().getLibraryFilteredStore();
		reloadData();
	}

	@Override
	protected String getPreferencePageId() {
		return LibViewPreferencePage.class.getName();
	}

	public void setLocationFilter(Location loc) {
		// getMagicControl().setStatus("Loading " + loc + "...");
		WaitUtils.scheduleJob("Updating location", () -> {
			// the Location advanced-filter checkboxes live in the FILTER store
			// (read by AbstractMagicCardsListControl.syncFilter()), not the columns
			// store - writing there silently did nothing
			IPreferenceStore preferenceStore = getFilterPreferenceStore();
			Collection ids = Locations.getInstance().getIds();
			String locId = Locations.getInstance().getPrefConstant(loc);
			for (Iterator iterator = ids.iterator(); iterator.hasNext();) {
				String id = (String) iterator.next();
				if (id.startsWith(locId + ".") || id.startsWith(locId + "/") || id.equals(locId)
						|| id.equals(locId + Location.SIDEBOARD_SUFFIX) || id.equals(locId + Location.EXTRA_SUFFIX)) {
					preferenceStore.setValue(id, true);
				} else {
					preferenceStore.setValue(id, false);
				}
			}
			// reloadData() touches SWT (e.g. Table.getTopIndex()) - this runs on a
			// background Job, so it must hop back to the UI thread
			WaitUtils.asyncExec(this::reloadData);
		});
	}

	@Override
	public String getId() {
		return ID;
	}
}