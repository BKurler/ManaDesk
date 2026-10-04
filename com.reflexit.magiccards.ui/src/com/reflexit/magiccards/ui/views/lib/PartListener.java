/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - activateDeck() no longer fires UPDATE_CONTAINER
 *                         (coll.update()): the deck's DeckView answered it with
 *                         a full reload on every tab activation - table back to
 *                         the top, visible selection lost (e.g. focus returning
 *                         from the Split & move dialog). Now a no-op when the
 *                         active deck did not change; otherwise only the
 *                         navigator's "(Active)" labels are repainted.
 */
package com.reflexit.magiccards.ui.views.lib;

import org.eclipse.jface.viewers.ColumnViewer;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.nav.CardCollection;
import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.views.nav.CardsNavigatorView;

public class PartListener implements IPartListener2 {
	private static PartListener instance;

	public synchronized static PartListener getInstance() {
		if (instance == null)
			instance = new PartListener();
		return instance;
	}

	private PartListener() {
		// singleton
	}

	@Override
	public void partActivated(IWorkbenchPartReference partRef) {
		IWorkbenchPart part = partRef.getPart(false);
		if (part instanceof DeckView) {
			DeckView deckView = (DeckView) part;
			activateDeck(deckView.getDeckId());
		}
	}

	public void activateDeck(String key) {
		if (key == null)
			return;
		String previous = DataManager.getCardHandler().getActiveDeckId();
		if (key.equals(previous))
			return; // same tab re-activated (e.g. focus back from a dialog) - nothing changed
		DataManager.getCardHandler().setActiveDeckId(key);
		CardCollection coll = DataManager.getInstance().getModelRoot().findCardCollectionById(key);
		if (coll == null) {
			MagicUIActivator.log("Cannot find collection by key " + key);
			return;
		}
		// Only the navigator's "(Active)" label changes. Do NOT coll.update():
		// that fires UPDATE_CONTAINER, which the deck's own DeckView answers with
		// a full reload (meant for property edits) - every tab activation then
		// re-input the table, scrolling it to the top and dropping the visible
		// selection (seen when focus came back from the Split & move dialog).
		CardCollection old = previous == null ? null
				: DataManager.getInstance().getModelRoot().findCardCollectionById(previous);
		repaintNavigatorLabels(old, coll);
	}

	private static void repaintNavigatorLabels(Object... elements) {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		IWorkbenchPage page = window == null ? null : window.getActivePage();
		IViewPart view = page == null ? null : page.findView(CardsNavigatorView.ID);
		if (!(view instanceof CardsNavigatorView))
			return;
		ColumnViewer viewer = ((CardsNavigatorView) view).getViewer();
		if (viewer == null || viewer.getControl() == null || viewer.getControl().isDisposed())
			return;
		java.util.List<Object> changed = new java.util.ArrayList<>();
		for (Object e : elements)
			if (e != null)
				changed.add(e);
		viewer.update(changed.toArray(), null);
	}

	@Override
	public void partBroughtToTop(IWorkbenchPartReference partRef) {
		// TODO Auto-generated method stub
	}

	@Override
	public void partClosed(IWorkbenchPartReference partRef) {
		// TODO Auto-generated method stub
	}

	@Override
	public void partDeactivated(IWorkbenchPartReference partRef) {
		// TODO Auto-generated method stub
	}

	@Override
	public void partHidden(IWorkbenchPartReference partRef) {
		// TODO Auto-generated method stub
	}

	@Override
	public void partInputChanged(IWorkbenchPartReference partRef) {
		// TODO Auto-generated method stub
	}

	@Override
	public void partOpened(IWorkbenchPartReference partRef) {
		//IWorkbenchPart part = partRef.getPart(false);
		//		if (part instanceof DeckView) {
		//			DeckView deckView = (DeckView) part;
		//			IFilteredCardStore store = deckView.getFilteredStore();
		//			activateDeck(store);
		//		}
	}

	@Override
	public void partVisible(IWorkbenchPartReference partRef) {
		// TODO Auto-generated method stub
	}
}
