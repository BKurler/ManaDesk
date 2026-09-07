/*
 * Contributors:
 *     Rémi Dutil (2026) - reload the instance list on any add/remove/update of the shown card
 */
package com.reflexit.magiccards.ui.views.instances;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.swt.widgets.Composite;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.GroupOrder;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.Languages.Language;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICardCountable;
import com.reflexit.magiccards.core.model.events.CardEvent;
import com.reflexit.magiccards.core.model.storage.ICardStore;
import com.reflexit.magiccards.core.model.storage.IFilteredCardStore;
import com.reflexit.magiccards.core.model.storage.MemoryFilteredCardStore;
import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.preferences.CustomGroupsPreferencePage;
import com.reflexit.magiccards.ui.views.AbstractMagicCardsListControl;
import com.reflexit.magiccards.ui.views.IMagicColumnViewer;

public class InstancesListControl extends AbstractMagicCardsListControl {
	public InstancesListControl() {
		super(true);
	}

	private IMagicCard card;

	/**
	 * Default order of the instance list: grouped by the collection / deck the
	 * copy lives in. Null-safe - a copy with no location sorts last instead of
	 * throwing and killing the whole sort. Applied at the model level (in
	 * {@link #populateStore}) so it survives a plain refresh and a SWT.VIRTUAL
	 * tree; the user can still re-sort any column and Unsort returns here.
	 */
	public static final Comparator<IMagicCard> BY_LOCATION = (a, b) -> compareLocation(locationName(a), locationName(b));

	/**
	 * Pure location comparison (extracted so it can be unit tested without a card
	 * store): {@code null} location sorts last, otherwise alphabetical by
	 * location path.
	 */
	public static int compareLocation(String a, String b) {
		if (a == null && b == null)
			return 0;
		if (a == null)
			return 1;
		if (b == null)
			return -1;
		return a.compareTo(b);
	}

	private static String locationName(IMagicCard c) {
		try {
			if (!(c instanceof MagicCardPhysical))
				return null;
			Location l = ((MagicCardPhysical) c).getLocation();
			return l == null ? null : l.toString();
		} catch (RuntimeException e) {
			return null;
		}
	}

	@Override
	public IMagicColumnViewer createViewer(Composite parent) {
		return new InstancesViewer(getPreferencePageId(), parent);
	}

	@Override
	public void handleEvent(CardEvent event) {
		// "Instances of card X" spans every collection. A pile of X added,
		// removed or recounted anywhere - e.g. a Split & move that drops a new
		// pile into another deck - changes this list even though the affected
		// pile is not one of the rows shown here, so the generic
		// "is this event for my store?" test in mcpEventHandler misses it.
		if (touchesShownCard(event)) {
			loadData(null);
			return;
		}
		mcpEventHandler(event);
	}

	private boolean touchesShownCard(CardEvent event) {
		if (card == null || card == MagicCard.DEFAULT || card == IMagicCard.DEFAULT)
			return false;
		int t = event.getType();
		if (t != CardEvent.ADD && t != CardEvent.REMOVE && t != CardEvent.UPDATE)
			return false;
		String name = card.getName();
		return name != null && dataHasName(event.getData(), name);
	}

	private static boolean dataHasName(Object data, String name) {
		if (data instanceof MagicCardPhysical)
			return name.equals(((MagicCardPhysical) data).getName());
		if (data instanceof Iterable) {
			for (Object o : (Iterable<?>) data)
				if (dataHasName(o, name))
					return true;
		}
		return false;
	}

	@Override
	protected String getPreferencePageId() {
		return getViewPreferencePageId();
	}

	@Override
	protected Collection<GroupOrder> getGroups() {
		ArrayList<GroupOrder> res = new ArrayList<>();
		res.add(new GroupOrder());
		res.add(new GroupOrder(MagicCardField.SET));
		res.add(new GroupOrder(MagicCardField.LOCATION));
		res.add(new GroupOrder(MagicCardField.OWNERSHIP));
		res.addAll(new CustomGroupsPreferencePage().getCurrentValue());
		return res;
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
		// header right-click / "..." menu / gear all open the column dialog
		if (actionShowPrefs != null)
			actionShowPrefs.setText("Properties...");
	}

	@Override
	public void fillLocalToolBar(IToolBarManager manager) {
		if (actionSortBy != null)
			manager.add(actionSortBy);
		if (actionUnsort != null)
			manager.add(actionUnsort);
		manager.add(actionShowPrefs);
	}

	@Override
	public void fillLocalPullDown(IMenuManager manager) {
		if (actionSortBy != null)
			manager.add(actionSortBy.createMenuManager());
		if (actionUnsort != null)
			manager.add(actionUnsort);
		manager.add(actionShowPrefs);
	}

	public String getStatusMessage1() {
		IFilteredCardStore filteredStore = getFilteredStore();
		if (filteredStore == null)
			return "";
		ICardStore cardStore = filteredStore.getCardStore();
		int totalSize = cardStore.size();
		int count = totalSize;
		if (cardStore instanceof ICardCountable) {
			count = ((ICardCountable) cardStore).getCount();
		}
		String s = "";
		if (count != 1)
			s = "s";
		return "Total " + count + " card" + s + " in your collections";
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
		// sort here, at the model level, so the default order is by-location even
		// for a SWT.VIRTUAL tree and survives a plain refresh; a column-header
		// sort / Sort By still overrides it and Unsort falls back here
		ArrayList<IMagicCard> instances = new ArrayList<>(
				searchInStore(DataManager.getCardHandler().getLibraryCardStore()));
		instances.sort(BY_LOCATION);
		mstore.addAll(instances);
		monitor.done();
	}

	public Collection<IMagicCard> searchInStore(ICardStore<IMagicCard> store) {
		LinkedHashSet<IMagicCard> res = new LinkedHashSet<>();
		if (card == null || card == MagicCard.DEFAULT || card.getName() == null)
			return res;
		String englishName = card.getName();
		String language = card.getLanguage();
		if (language != null && !language.equals(Language.ENGLISH.getLang())) {
			String enId = card.getEnglishCardId();
			if (enId != null) {
				IMagicCard card2 = store.getCard(enId);
				englishName = card2 != null ? card2.getName() : card.getName();
			}
		}
		boolean multilang = false;
		for (Iterator<IMagicCard> iterator = store.iterator(); iterator.hasNext();) {
			IMagicCard next = iterator.next();
			try {
				if (englishName.equals(next.getName())) {
					res.add(next);
				}
				language = next.getLanguage();
				if (language != null && !language.equals(Language.ENGLISH.getLang())) {
					multilang = true;
				}
			} catch (Exception e) {
				MagicUIActivator.log("Bad card: " + next);
				MagicUIActivator.log(e);
			}
		}
		if (multilang) {
			ArrayList<IMagicCard> res2 = new ArrayList<>();
			for (Iterator<IMagicCard> iterator = store.iterator(); iterator.hasNext();) {
				IMagicCard next = iterator.next();
				try {
					String parentId = next.getEnglishCardId();
					if (parentId != null) {
						for (Iterator<IMagicCard> iterator2 = res.iterator(); iterator2.hasNext();) {
							IMagicCard mc = iterator2.next();
							if (mc.getCardId() == parentId) {
								if (!res.contains(next))
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
		}
		return res;
	}

	@Override
	public IFilteredCardStore doGetFilteredStore() {
		return new MemoryFilteredCardStore();
	}

	@Override
	public void saveColumnLayout() {
		super.saveColumnLayout();
	}

}
