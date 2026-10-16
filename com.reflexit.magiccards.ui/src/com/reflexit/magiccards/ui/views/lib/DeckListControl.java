/*
 * Contributors:
 *     Rémi Dutil (2026) - createColumnCollection() override: deck/collection
 *                         lists use DeckColumnCollection instead of the
 *                         generic MagicColumnCollection (drops Sideboard/
 *                         Extra, which are always the same value in a single
 *                         deck/collection's own tab)
 *     Rémi Dutil (2026) - getPresentaionPreferenceStore(): in a deck / collection
 *                         tab, presentation, grouping and sort are kept per
 *                         list (its own store); columns stay shared
 */
package com.reflexit.magiccards.ui.views.lib;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.IPersistentPreferenceStore;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

import com.reflexit.magiccards.core.model.storage.IFilteredCardStore;
import com.reflexit.magiccards.ui.preferences.PreferenceConstants;
import com.reflexit.magiccards.ui.preferences.PreferenceInitializer;
import com.reflexit.magiccards.ui.views.Presentation;
import com.reflexit.magiccards.ui.views.columns.MagicColumnCollection;

public class DeckListControl extends MyCardsListControl implements IDeckPage {
	public DeckListControl() {
	}

	public DeckListControl(Presentation pres) {
		super(pres);
	}

	@Override
	protected MagicColumnCollection createColumnCollection() {
		return new DeckColumnCollection(getPreferencePageId());
	}

	@Override
	public IFilteredCardStore doGetFilteredStore() {
		String secondaryId = getViewSite().getSecondaryId();
		return DM.getCardHandler().getCardCollectionFilteredStore(secondaryId);
	}

	private IPersistentPreferenceStore listViewStore;

	/** In a deck / collection tab: this list's own presentation, grouping and sort. */
	@Override
	public IPersistentPreferenceStore getPresentaionPreferenceStore() {
		if (!hasOwnPresentationStore())
			return super.getPresentaionPreferenceStore();
		if (listViewStore == null) {
			listViewStore = PreferenceInitializer.getListViewStore(getPreferencePageId(),
					getViewSite().getSecondaryId());
			vtrace("own presentation store created: " + storesForTrace() + " viewas='"
					+ listViewStore.getString(PreferenceConstants.PRESENTATION_VIEW) + "' group='"
					+ listViewStore.getString(PreferenceConstants.GROUP_FIELD) + "' sort='"
					+ listViewStore.getString(PreferenceConstants.SORT_ORDER) + "'");
		}
		return listViewStore;
	}

	@Override
	protected boolean hasOwnPresentationStore() {
		return getViewPart() instanceof DeckView && getViewSite() != null
				&& getViewSite().getSecondaryId() != null;
	}

	@Override
	public IPersistentPreferenceStore getElementPreferenceStore() {
		return new ScopedPreferenceStore(InstanceScope.INSTANCE, getPreferencePageId() + ".deck." + getName());
	}
}
