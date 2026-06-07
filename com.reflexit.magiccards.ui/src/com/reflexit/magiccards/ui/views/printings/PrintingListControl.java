package com.reflexit.magiccards.ui.views.printings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.widgets.Composite;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.CardGroup;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.SortOrder;
import com.reflexit.magiccards.core.model.events.CardEvent;
import com.reflexit.magiccards.core.model.storage.ICardStore;
import com.reflexit.magiccards.core.model.storage.IDbCardStore;
import com.reflexit.magiccards.core.model.storage.IFilteredCardStore;
import com.reflexit.magiccards.core.model.storage.MemoryFilteredCardStore;
import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.views.AbstractMagicCardsListControl;
import com.reflexit.magiccards.ui.views.IMagicColumnViewer;

public class PrintingListControl extends AbstractMagicCardsListControl {
	private IMagicCard card;

	public PrintingListControl() {
		super(true);
	}

	@Override
	public void handleEvent(CardEvent event) {
		mcEventHandler(event);
	}

	@Override
	protected String getPreferencePageId() {
		return getViewPreferencePageId();
	}

	@Override
	public IMagicColumnViewer createViewer(Composite parent) {
		return new PrintingsViewer(getPreferencePageId(), parent);
	}

	@Override
	public void fillLocalToolBar(IToolBarManager manager) {
		// TODO Auto-generated method stub
		// super.fillLocalToolBar(manager);
	}

	@Override
	public String getStatusMessage() {
		if (card == MagicCard.DEFAULT || card == null) {
			return "No card";
		}
		return card.getName() + ": " + getStatusMessage1();
	}

	@Override
	protected void makeActions() {
		super.makeActions();

		// Disable default sorting
		getFilter().setSortOrder(new SortOrder()); // empty

		// Now apply your comparator
		applyPrintingSort();
	}

	@Override
	protected void sort(int index, int dir) {
		updateSortColumn(index);
		refreshViewer();
	}

	public String getStatusMessage1() {
		IFilteredCardStore filteredStore = getFilteredStore();
		if (filteredStore == null)
			return "";
		ICardStore cardStore = filteredStore.getCardStore();
		int totalSize = cardStore.size();
		if (totalSize == 1)
			return "Only one version found";
		return "Total " + totalSize + " diffrent versions";
	}

	public void setCard(IMagicCard card) {
		this.card = card;
	}

	@Override
	protected void populateStore(IProgressMonitor monitor) {
		if (card == IMagicCard.DEFAULT || card == null)
			return;
		monitor.beginTask("Loading card printings for " + card.getName(), 100);
		if (fstore == null) {
			fstore = doGetFilteredStore();
		}
		MemoryFilteredCardStore mstore = (MemoryFilteredCardStore) fstore;
		mstore.clear();
		mstore.addAll(searchInStore(DataManager.getCardHandler().getMagicDBStore()));
		monitor.done();
	}

	public Collection<IMagicCard> searchInStore(IDbCardStore<IMagicCard> store) {
		if (card == null || card == MagicCard.DEFAULT || card.getName() == null)
			return Collections.emptyList();
		String englishName;
		String enId = card.getEnglishCardId();
		if (enId != null) {
			IMagicCard card2 = store.getCard(enId);
			englishName = card2 != null ? card2.getName() : card.getName();
		} else {
			englishName = card.getName();
		}
		Collection<IMagicCard> candidates = store.getCandidates(englishName);
		LinkedHashSet<IMagicCard> res = new LinkedHashSet<>();
		res.addAll(candidates);
		ArrayList<IMagicCard> res2 = new ArrayList<>();
		for (Iterator<IMagicCard> iterator = store.iterator(); iterator.hasNext();) {
			IMagicCard next = iterator.next();
			try {
				String parentId = next.getEnglishCardId();
				if (parentId != null) {
					for (Iterator<IMagicCard> iterator2 = res.iterator(); iterator2.hasNext();) {
						IMagicCard mc = iterator2.next();
						if (mc.getCardId() == parentId) {
							res2.add(next);
						}
					}
				}
			} catch (Exception e) {
				MagicUIActivator.log("Bad card: " + next);
				MagicUIActivator.log(e);
			}
		}
		res.addAll(res2);
		return res;
	}

	@Override
	public IFilteredCardStore doGetFilteredStore() {
		return new MemoryFilteredCardStore();
	}

	private void applyPrintingSort() {
		if (viewer == null)
			return;

		Viewer jfaceViewer = viewer.getViewer();
		if (!(jfaceViewer instanceof StructuredViewer))
			return;

		StructuredViewer sv = (StructuredViewer) jfaceViewer;

		sv.setComparator(new ViewerComparator() {
			@Override
			public int compare(Viewer v, Object a, Object b) {

				// Handle CardGroup BEFORE casting
				boolean aGroup = a instanceof CardGroup;
				boolean bGroup = b instanceof CardGroup;

				if (aGroup && bGroup)
					return 0; // keep group order
				if (aGroup)
					return -1; // group above cards
				if (bGroup)
					return 1;

				// Now safe to cast
				MagicCard c1 = (MagicCard) a;
				MagicCard c2 = (MagicCard) b;

				// Release date - oldest first
				int d = c1.getEdition().getReleaseDate().compareTo(c2.getEdition().getReleaseDate());
				if (d != 0)
					return d;

				// Collector number - smallest first
				try {
					int n1 = Integer.parseInt(c1.getCollNumber());
					int n2 = Integer.parseInt(c2.getCollNumber());
					d = Integer.compare(n1, n2);
					if (d != 0)
						return d;
				} catch (NumberFormatException ignore) {
					d = c1.getCollectorId().compareTo(c2.getCollectorId());
					if (d != 0)
						return d;
				}

				// Fallback: set code
				return c1.getSet().compareTo(c2.getSet());
			}
		});
	}

}
