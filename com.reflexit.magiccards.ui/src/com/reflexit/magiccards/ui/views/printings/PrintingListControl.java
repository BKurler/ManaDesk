/*
 * Contributors:
 *     Rémi Dutil (2026) - null-safe oldest-print-first sort applied at the model level
 */
package com.reflexit.magiccards.ui.views.printings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
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

	/**
	 * Oldest print first. Null-safe: an edition with no known release date (a
	 * fake / not-yet-downloaded set) sorts last instead of throwing and killing
	 * the whole sort - which is why the view stopped being ordered. Ties break
	 * on collector number then set name.
	 */
	public static final Comparator<IMagicCard> BY_PRINT_ORDER = (a, b) -> comparePrintOrder(releaseDate(a), collNum(a),
			safe(setOf(a)), releaseDate(b), collNum(b), safe(setOf(b)));

	/**
	 * Pure print-order comparison (extracted so it can be unit tested without a
	 * card DB): oldest {@code release} first (null = unknown = last), then lower
	 * {@code collNum}, then {@code set} name.
	 */
	public static int comparePrintOrder(Date releaseA, int collNumA, String setA, Date releaseB, int collNumB,
			String setB) {
		if (releaseA != null && releaseB != null) {
			int d = releaseA.compareTo(releaseB);
			if (d != 0)
				return d;
		} else if (releaseA != null) {
			return -1;
		} else if (releaseB != null) {
			return 1;
		}
		int d = Integer.compare(collNumA, collNumB);
		if (d != 0)
			return d;
		return safe(setA).compareTo(safe(setB));
	}

	private static Date releaseDate(IMagicCard c) {
		try {
			return c.getEdition() == null ? null : c.getEdition().getReleaseDate();
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static String setOf(IMagicCard c) {
		try {
			return c.getSet();
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static int collNum(IMagicCard c) {
		try {
			int n = c.getCollectorNumberId();
			return n > 0 ? n : Integer.MAX_VALUE;
		} catch (RuntimeException e) {
			return Integer.MAX_VALUE;
		}
	}

	private static String safe(String s) {
		return s == null ? "" : s;
	}

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
		// sort here, at the model level, so the order is right even for a
		// SWT.VIRTUAL tree (where a JFace ViewerComparator is not reliably
		// applied) and survives a plain refresh
		ArrayList<IMagicCard> printings = new ArrayList<>(searchInStore(DataManager.getCardHandler().getMagicDBStore()));
		printings.sort(BY_PRINT_ORDER);
		mstore.addAll(printings);
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
				boolean aGroup = a instanceof CardGroup;
				boolean bGroup = b instanceof CardGroup;
				if (aGroup && bGroup)
					return 0;
				if (aGroup)
					return -1;
				if (bGroup)
					return 1;
				return BY_PRINT_ORDER.compare((IMagicCard) a, (IMagicCard) b);
			}
		});
	}

}
