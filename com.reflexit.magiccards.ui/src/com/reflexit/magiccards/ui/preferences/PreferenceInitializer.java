/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - Proxy column defaults + collector "count proxies" default
 *     Rémi Dutil (2026) - dropped the stale "-Rating" token from every default
 *                         column-order string - the column itself no longer
 *                         exists (see MagicColumnCollection/CollectorColumnCollection)
 *     Rémi Dutil (2026) - getCollectionStore(): Collection views now persist
 *                         their own column visibility/width/order instead of
 *                         sharing the Deck store
 *     Rémi Dutil (2026) - default-column review across all 8 views: Set and
 *                         Collector's Number now default visible everywhere
 *                         they exist; Artist/Language/Release Date now
 *                         default hidden everywhere (Printings used to show
 *                         all three, Instances Language too); Comment now
 *                         hidden by default in Instances too; Scryfall
 *                         Database's Online Price is now visible by default
 *                         (see also MagicColumnCollection/DeckColumnCollection/
 *                         CollectorColumnCollection/SplitViewer for the
 *                         matching column-availability changes - Location
 *                         dropped from Deck/Collection, Sideboard/Extra/Date/
 *                         User Price dropped from Scryfall Database, Finish
 *                         added to Collector)
 *     Rémi Dutil (2026) - setToDefault() no longer relies on
 *                         preferenceNames() to discover which keys to reset -
 *                         see its own header for why that was unreliable
 *                         (Reset Filter could silently leave a checked
 *                         Finish/Color/etc. box persisted)
 *     Rémi Dutil (2026) - second default-column pass: Printings' Multiverse
 *                         ID and Rarity now hidden by default (identity
 *                         columns Name/Collector's Number/Set already cover
 *                         it); Finish now visible by default in Collection/
 *                         Collector/Instances/My Cards (not Deck - left
 *                         hidden there); Ownership now visible by default in
 *                         Collection/Deck/Instances/My Cards, and dropped
 *                         entirely from Scryfall Database (see SplitViewer);
 *                         Collector's User Price now hidden by default
 *                         (Online Price - market value - is the one meant to
 *                         be seen at a glance there); Online Price now
 *                         visible by default in Printings and My Cards (was
 *                         already visible in Scryfall Database); Release
 *                         Date now visible by default in Scryfall Database
 *     Rémi Dutil (2026) - third pass: Collector's Rarity now hidden by
 *                         default (Set/Collector's Number already identify
 *                         the printing); Count/Location/Condition/Proxy/
 *                         Comment/Special/For Trade dropped entirely from
 *                         Scryfall Database, not just hidden - see
 *                         SplitViewer's NOT_APPLICABLE_TO_DB_BROWSING
 *     Rémi Dutil (2026) - fourth pass: Proxy and Comment now visible by
 *                         default in Collection/Deck/Instances/My Cards
 *     Rémi Dutil (2026) - fifth pass: reordered every LOCAL_COLUMNS default
 *                         (every view except Collector, which keeps its own
 *                         progress-first order) to a single canonical column
 *                         order: Count, Name, Card Id, Set, Collector's
 *                         Number, Ownership, Finish, Proxy, Online Price,
 *                         User Price, Type, Cost, Power, Toughness, Rarity,
 *                         Special, Comment, Color, Color Type, Color
 *                         Identity, Extended Color Identity, Artist,
 *                         Language, Text, Oracle Text, For Trade, Release
 *                         Date - then whatever a view has that isn't on that
 *                         list (Multiverse ID, TCGplayer ID, Location,
 *                         Condition, Legality, Sideboard, Extra, Date)
 *                         appended after, in that same fixed order. Only the
 *                         order changed here - every column's visible/hidden
 *                         default is untouched from the passes above.
 *     Rémi Dutil (2026) - sixth pass: folded every column from the fifth
 *                         pass's leftover bucket into the canonical order
 *                         proper instead of tacking them on at the end -
 *                         Condition now sits right after Finish (before
 *                         Proxy); Multiverse ID/TCGplayer ID/Location/
 *                         Legality now sit right after Language (before
 *                         Text); Sideboard/Extra now sit right after Oracle
 *                         Text (before For Trade); Date now sits right
 *                         before Release Date. Collector's own order gets
 *                         the same treatment, plus Progress/Progress4/Own
 *                         Count/Own Unique/Own Total inserted right after
 *                         Name (before Card Id/Set) - still the one view
 *                         that doesn't follow the shared order as-is.
 */
package com.reflexit.magiccards.ui.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.IPersistentPreferenceStore;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceStore;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.osgi.service.prefs.BackingStoreException;

import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.model.FilterField;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.gallery.GalleryPreferencePage;
import com.reflexit.magiccards.ui.views.Presentation;
import com.reflexit.magiccards.ui.views.instances.InstancesView;
import com.reflexit.magiccards.ui.views.printings.PrintingsView;

/**
 * Class used to initialize default preference values.
 */
public class PreferenceInitializer extends AbstractPreferenceInitializer {
	private static IPreferenceStore deckStore;
	private static IPreferenceStore collectionStore;
	private static IPreferenceStore libStore;
	private static IPreferenceStore mdbStore;
	private static IPreferenceStore collectorStore;
	private static final String MY_CARRDS_PP_ID = LibViewPreferencePage.PPID;
	private static final String DECK_VIEW_PP_ID = DeckViewPreferencePage.PPID;
	private static final String COLLECTION_VIEW_PP_ID = CollectionViewPreferencePage.PPID;
	private static final String DB_PP_ID = MagicDbViewPreferencePage.PPID;
	private static final String COLLECTOR_PP_ID = CollectorViewPreferencePage.PPID;

	@Override
	public void initializeDefaultPreferences() {
		IPreferenceStore storeCore = MagicUIActivator.getDefault().getCorePreferenceStore();
		storeCore.setDefault(PreferenceConstants.DIR_MAGICCARDS, FileUtils.MAGICCARDS);
		storeCore.setDefault(PreferenceConstants.DIR_BACKUP, FileUtils.BACKUP);
		IPreferenceStore store = getGlobalStore();
		store.setDefault(PreferenceConstants.GATHERER_SITE, "http://ww2.wizards.com/gatherer");
		store.setDefault(PreferenceConstants.GATHERER_UPDATE,
				"http://ww2.wizards.com/gatherer/index.aspx?output=Spoiler&setfilter=Standard");
		store.setDefault(PreferenceConstants.CACHE_IMAGES, true);
		store.setDefault(PreferenceConstants.LOAD_IMAGES, true);
		store.setDefault(PreferenceConstants.LOAD_RULINGS, false);
		store.setDefault(PreferenceConstants.LOAD_EXTRAS, false);
		store.setDefault(PreferenceConstants.LOAD_PRINTINGS, false);
		store.setDefault(PreferenceConstants.SHOW_GRID, false);
		store.setDefault(PreferenceConstants.CHECK_FOR_UPDATES, false); // !!! RD Don't check for update per default
		store.setDefault(PreferenceConstants.CHECK_FOR_CARDS, false);
		store.setDefault(PreferenceConstants.OWNED_COPY, false);
		store.setDefault(PreferenceConstants.CURRENCY, "USD");
		store.setDefault(PreferenceConstants.WORK_OFFLINE, false);
		store.setDefault(PreferenceConstants.PRICE_PROVIDER,
				PriceProviderManager.getInstance().getDefaultProvider().getName());
		store.setDefault(PreferenceConstants.LAST_SELECTION, 205961);
		store.setDefault(PreferenceConstants.COLLECTOR_COUNT_PROXIES, false);
		store.setDefault(PreferenceConstants.COLLECTOR_COUNT_FINISHES_SEPARATELY, false);
		// magic store
		getMdbStore().setDefault(PreferenceConstants.LOCAL_COLUMNS,
				"Name,-Card Id,Set,Collector's Number,-Finish,Online Price,Type,Cost,Power,Toughness,-Rarity,-Color,"
						+ "-Color Type,-Color Identity,-Extended Color Identity,-Artist,-Language,-Multiverse ID,"
						+ "-TCGplayer ID,-Legality,-Text,-Oracle Text,Release Date");
		getMdbStore().setDefault(PreferenceConstants.LOCAL_SHOW_QUICKFILTER, true);
		// !!! RD getMdbStore().setDefault(PreferenceConstants.GROUP_FIELD,
		// GroupOrder.createGroupKey(MagicCardField.SET));
		getMdbStore().setDefault(PreferenceConstants.SORT_ORDER, MagicCardField.NAME.name());
		// !!! RD getMdbStore().setDefault(PreferenceConstants.PRESENTATION_VIEW,
		// Presentation.SPLITTREE.key());
		getMdbStore().setDefault(PreferenceConstants.PRESENTATION_VIEW, Presentation.TABLE.key());
		// library store
		getLibStore().setDefault(PreferenceConstants.LOCAL_COLUMNS,
				"Count,Name,-Card Id,Set,Collector's Number,Ownership,Finish,Condition,Proxy,Online Price,"
						+ "-User Price,Type,Cost,Power,Toughness,-Rarity,-Special,Comment,-Color,-Color Type,"
						+ "-Color Identity,-Extended Color Identity,-Artist,-Language,-Multiverse ID,-TCGplayer ID,"
						+ "Location,-Legality,-Text,-Oracle Text,-Sideboard,-Extra,-For Trade,-Date,-Release Date");
		getLibStore().setDefault(PreferenceConstants.LOCAL_SHOW_QUICKFILTER, true);
		// !!! RD getLibStore().setDefault(PreferenceConstants.GROUP_FIELD,
		// GroupOrder.createGroupKey(MagicCardField.LOCATION));
		// !!! RD getLibStore().setDefault(PreferenceConstants.PRESENTATION_VIEW,
		// Presentation.SPLITTREE.key());
		getLibStore().setDefault(PreferenceConstants.PRESENTATION_VIEW, Presentation.TABLE.key());
		// deck store
		getDeckStore().setDefault(PreferenceConstants.LOCAL_COLUMNS,
				"Count,Name,-Card Id,Set,Collector's Number,Ownership,-Finish,Condition,Proxy,-Online Price,"
						+ "-User Price,Type,Cost,Power,Toughness,-Rarity,-Special,Comment,-Color,-Color Type,"
						+ "-Color Identity,-Extended Color Identity,-Artist,-Language,-Multiverse ID,-TCGplayer ID,"
						+ "-Legality,-Text,-Oracle Text,-For Trade,-Date,-Release Date");
		getDeckStore().setDefault(PreferenceConstants.LOCAL_SHOW_QUICKFILTER, false);
		// !!! RD getDeckStore().setDefault(PreferenceConstants.GROUP_FIELD,
		// GroupOrder.createGroupKey(MagicCardField.CMC));
		// !!! RD getDeckStore().setDefault(PreferenceConstants.PRESENTATION_VIEW,
		// Presentation.GALLERY.key());
		getDeckStore().setDefault(PreferenceConstants.PRESENTATION_VIEW, Presentation.TABLE.key());

		// collection store - same starting point as the deck store, but persisted
		// separately so a change to one doesn't bleed into the other
		getCollectionStore().setDefault(PreferenceConstants.LOCAL_COLUMNS,
				"Count,Name,-Card Id,Set,Collector's Number,Ownership,Finish,Condition,Proxy,-Online Price,"
						+ "-User Price,Type,Cost,Power,Toughness,-Rarity,-Special,Comment,-Color,-Color Type,"
						+ "-Color Identity,-Extended Color Identity,-Artist,-Language,-Multiverse ID,-TCGplayer ID,"
						+ "-Legality,-Text,-Oracle Text,-For Trade,-Date,-Release Date");
		getCollectionStore().setDefault(PreferenceConstants.LOCAL_SHOW_QUICKFILTER, false);
		getCollectionStore().setDefault(PreferenceConstants.PRESENTATION_VIEW, Presentation.TABLE.key());

		// collector store - rows are whole-database printings; the default focuses
		// on the collection side (progress + all the quantities). User Price is
		// available but off by default - Online Price (market value) is the one
		// meant to be seen at a glance; User Price is there for whoever tracks
		// their own valuation instead (see CollectorColumnCollection - no
		// per-copy columns, Finish is the one exception since a printing can
		// offer more than one).
		getCollectorStore().setDefault(PreferenceConstants.LOCAL_COLUMNS,
				"Name,Progress,Progress4,Own Count,Own Unique,Own Total,-Card Id,Set,Collector's Number,Finish,"
						+ "-Online Price,-User Price,-Type,-Cost,-Power,-Toughness,-Rarity,-Color,-Color Type,"
						+ "-Color Identity,-Extended Color Identity,-Artist,-Language,-Multiverse ID,"
						+ "-TCGplayer ID,-Legality,-Text,-Oracle Text,-Release Date");
		getCollectorStore().setDefault(PreferenceConstants.LOCAL_SHOW_QUICKFILTER, true);
		// !!! RD getCollectorStore().setDefault(PreferenceConstants.GROUP_FIELD,
		// GroupOrder.createGroupKey(CollectorListControl.DEF_GROUP));
		// !!! RD getCollectorStore().setDefault(PreferenceConstants.PRESENTATION_VIEW,
		// Presentation.TREE.key());
		getCollectorStore().setDefault(PreferenceConstants.PRESENTATION_VIEW, Presentation.TABLE.key());

		// gallery
		IPersistentPreferenceStore gallerySettings = getLocalStore(GalleryPreferencePage.getId());
		gallerySettings.setDefault(PreferenceConstants.LOCAL_SHOW_QUICKFILTER, true);
		// !!! RD gallerySettings.setDefault(PreferenceConstants.GROUP_FIELD,
		// GroupOrder.createGroupKey(MagicCardField.SET));
		gallerySettings.setDefault(PreferenceConstants.SORT_ORDER, MagicCardField.NAME.name());

		// printings - card-database columns only; no SORT_ORDER default so the
		// list stays in oldest-print-first order until the user sorts a column
		IPersistentPreferenceStore printingsSettings = getLocalStore(PrintingsView.ID);
		printingsSettings.setDefault(PreferenceConstants.LOCAL_COLUMNS,
				"Name,-Card Id,Set,Collector's Number,-Finish,Online Price,-Rarity,-Artist,-Language,"
						+ "-Multiverse ID,-TCGplayer ID,-Release Date");
		printingsSettings.setDefault(PreferenceConstants.PRESENTATION_VIEW, Presentation.TABLE.key());

		// instances - full deck/collection column set; default order is by
		// location (handled at the model level in InstancesListControl)
		IPersistentPreferenceStore instancesSettings = getLocalStore(InstancesView.ID);
		instancesSettings.setDefault(PreferenceConstants.LOCAL_COLUMNS,
				"Count,Name,-Card Id,Set,Collector's Number,Ownership,Finish,Condition,Proxy,-Online Price,"
						+ "-User Price,-Type,-Cost,-Power,-Toughness,-Rarity,-Special,Comment,-Color,-Color Type,"
						+ "-Color Identity,-Extended Color Identity,-Artist,-Language,-Multiverse ID,"
						+ "-TCGplayer ID,Location,-Legality,-Text,-Oracle Text,-Sideboard,-Extra,-For Trade,"
						+ "-Date,-Release Date");
		instancesSettings.setDefault(PreferenceConstants.LOCAL_SHOW_QUICKFILTER, false);
		instancesSettings.setDefault(PreferenceConstants.PRESENTATION_VIEW, Presentation.TABLE.key());
	}

	public static IPreferenceStore getGlobalStore() {
		return MagicUIActivator.getDefault().getPreferenceStore();
	}

	public static IPersistentPreferenceStore getLocalStore(String id) {
		if (id == null)
			id = MagicUIActivator.PLUGIN_ID;
		ScopedPreferenceStore store = new ScopedPreferenceStore(InstanceScope.INSTANCE, id);
		return store;
	}

	public static IPersistentPreferenceStore getFilterStore(String id) {
		if (id == null)
			id = MagicUIActivator.PLUGIN_ID;
		id += ".filter";
		ScopedPreferenceStore store = new ScopedPreferenceStore(InstanceScope.INSTANCE, id);
		return store;
	}

	public static IEclipsePreferences getPreferences(String id) {
		if (id == null)
			id = MagicUIActivator.PLUGIN_ID;
		return InstanceScope.INSTANCE.getNode(id);
	}

	public static synchronized IPreferenceStore getDeckStore() {
		if (deckStore == null)
			deckStore = getLocalStore(DECK_VIEW_PP_ID);
		return deckStore;
	}

	public static synchronized IPreferenceStore getCollectionStore() {
		if (collectionStore == null)
			collectionStore = getLocalStore(COLLECTION_VIEW_PP_ID);
		return collectionStore;
	}

	public static synchronized IPreferenceStore getLibStore() {
		if (libStore == null) {
			libStore = getLocalStore(MY_CARRDS_PP_ID);
		}
		return libStore;
	}

	public static synchronized IPreferenceStore getMdbStore() {
		if (mdbStore == null)
			mdbStore = getLocalStore(DB_PP_ID);
		return mdbStore;
	}

	public static synchronized IPreferenceStore getCollectorStore() {
		if (collectorStore == null)
			collectorStore = getLocalStore(COLLECTOR_PP_ID);
		return collectorStore;
	}

	/**
	 * Resets every known filter preference on {@code store} back to its
	 * default. Every current caller passes a filter store (Reset Filter,
	 * Restore Defaults, Location filter's own reset), so
	 * {@link FilterField#getAllIds()} - the same authoritative id list
	 * {@code AbstractMagicCardsListControl#storeToMap()} already reads from
	 * to build the live filter - is the right, complete set to clear, unlike
	 * the old {@link #preferenceNames(IPreferenceStore)}-based approach: that
	 * only reset whatever keys a {@link ScopedPreferenceStore}'s own
	 * {@code getPreferenceNodes(false)[0].keys()} happened to enumerate for
	 * THIS particular store instance.
	 * <p>
	 * Explicitly {@code setValue()}s the default STRING rather than calling
	 * {@code store.setToDefault(id)}: confirmed by testing that opening the
	 * Filter dialog and pressing OK - which writes each field's value via
	 * {@code BooleanFieldEditor#doStore()}'s {@code setValue(name, boolean)} -
	 * persists to disk correctly, while the previous {@code setToDefault()}
	 * (a removal, not a value write) did not survive an app restart on this
	 * {@link ScopedPreferenceStore}. Writing the same way the working path
	 * does sidesteps whatever makes {@code setToDefault()} unreliable here.
	 * <p>
	 * Also flushes immediately instead of waiting for the eventual
	 * {@code dispose()}-time save - a Reset Filter that only "sticks" if the
	 * app later closes cleanly is not really reset yet, and every caller of
	 * this method already treats it as an immediate, final action.
	 */
	public static void setToDefault(IPreferenceStore store) {
		for (String id : FilterField.getAllIds()) {
			store.setValue(id, store.getDefaultString(id));
		}
		if (store instanceof IPersistentPreferenceStore) {
			try {
				((IPersistentPreferenceStore) store).save();
			} catch (java.io.IOException e) {
				MagicUIActivator.log(e);
			}
		}
	}

	public static String[] preferenceNames(IPreferenceStore store) {
		String res[] = null;
		if (store instanceof PreferenceStore) {
			res = ((PreferenceStore) store).preferenceNames();
		} else if (store instanceof ScopedPreferenceStore) {
			IEclipsePreferences[] preferenceNodes = ((ScopedPreferenceStore) store).getPreferenceNodes(false);
			try {
				if (preferenceNodes.length > 0)
					res = preferenceNodes[0].keys();
			} catch (BackingStoreException e) {
				// res = null;
			}
		}
		if (res == null)
			return null;
		return res;
	}
}
