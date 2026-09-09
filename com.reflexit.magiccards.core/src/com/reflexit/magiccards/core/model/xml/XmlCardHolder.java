/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - downloadUpdates() rewritten as one full update from the
 *                         Scryfall Default Cards bulk file (no per-set path);
 *                         cancellable, reports progress per phase
 *     Rémi Dutil (2026) - dropped the bundled flat-file seed (loadFromFlat*)
 */

package com.reflexit.magiccards.core.model.xml;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.MagicException;
import com.reflexit.magiccards.core.MagicLogger;
import com.reflexit.magiccards.core.model.Edition;
import com.reflexit.magiccards.core.model.Editions;
import com.reflexit.magiccards.core.model.ICardHandler;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.storage.ICardStore;
import com.reflexit.magiccards.core.model.storage.IDbCardStore;
import com.reflexit.magiccards.core.model.storage.IDbPriceStore;
import com.reflexit.magiccards.core.model.storage.IFilteredCardStore;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;
import com.reflexit.magiccards.core.monitor.SubCoreProgressMonitor;
import com.reflexit.magiccards.core.sync.ParseScryFallChecklist;
import com.reflexit.magiccards.core.sync.ParseScryFallSets;
import com.reflexit.magiccards.core.sync.ScryfallBulkCache;

public class XmlCardHolder implements ICardHandler {
	private String activeDeck;

	@Override
	public IFilteredCardStore getMagicDBFilteredStore() {
		return MagicDBFilteredCardFileStore.getInstance();
	}

	@Override
	public IDbCardStore getMagicDBStore() {
		return DbMultiFileCardStore.getInstance();
	}

	@Override
	public IFilteredCardStore getMagicDBFilteredStoreWorkingCopy() {
		return new BasicMagicDBFilteredCardFileStore((DbMultiFileCardStore) getMagicDBStore());
	}

	@Override
	public IFilteredCardStore getLibraryFilteredStore() {
		return LibraryFilteredCardFileStore.getInstance();
	}

	@Override
	public ICardStore getCardStore(Location to) {
		return LibraryFilteredCardFileStore.getInstance().getStore(to);
	}

	@Override
	public IFilteredCardStore getLibraryFilteredStoreWorkingCopy() {
		return new BasicLibraryFilteredCardFileStore((CollectionMultiFileCardStore) getLibraryCardStore());
	}

	@Override
	public ICardStore getLibraryCardStore() {
		return LibraryCardStore.getInstance();
	}

	@Override
	public IFilteredCardStore getCardCollectionFilteredStore(String filename) {
		return new DeckFilteredCardFileStore(filename);
	}

	@Override
	public ICardStore loadFromXml(String filename) {
		File file = new File(filename);
		CollectionSingleFileCardStore store = new CollectionSingleFileCardStore(file, Location.createLocation(file),
				true);
		return store;
	}

	public static File getDbFolder() {
		File dir = DataManager.getInstance().getModelRoot().getMagicDBContainer().getFile();
		return dir;
	}

	/** Serializes card-database updates so two "Update" runs don't race on the DB / editions.txt. */
	private static final Object UPDATE_LOCK = new Object();

	/**
	 * Full card-database update from the Scryfall <em>Default Cards</em> bulk file:
	 * download a fresher bulk if Scryfall published one, parse it once, write every
	 * {@code <DB>/<set>.xml}, then refresh {@code editions.txt} (and prices, done
	 * inside the parse). {@code set} is ignored - there is no per-set update any
	 * more. Honours {@code pm.isCanceled()} during the download and between sets.
	 *
	 * @return the number of card records loaded
	 */
	@Override
	public int downloadUpdates(final String set, final Properties options, ICoreProgressMonitor pm)
			throws MagicException, InterruptedException {
		final int[] rec = { 0 };
		synchronized (UPDATE_LOCK) {
			final DbMultiFileCardStore db = (DbMultiFileCardStore) DataManager.getInstance().getMagicDBStore();
			db.updateOperation(pm1 -> {
				try {
					pm1.beginTask("Updating card database", 1000);
					pm1.subTask("Checking Scryfall for a newer card file…");
					if (pm1.isCanceled())
						throw new InterruptedException();
					File bulk = ScryfallBulkCache.getDefaultCardsFile(new SubCoreProgressMonitor(pm1, 100));

					pm1.subTask("Reading card data…");
					java.util.Map<String, java.util.List<MagicCard>> all = new ParseScryFallChecklist()
							.parseBulkGrouped(bulk, null, new SubCoreProgressMonitor(pm1, 350));

					// 1. merge every parsed card into the in-memory DB (per set)
					SubCoreProgressMonitor mm = new SubCoreProgressMonitor(pm1, 250);
					mm.beginTask("Merging cards", Math.max(1, all.size()));
					int done = 0;
					for (java.util.Map.Entry<String, java.util.List<MagicCard>> e : all.entrySet()) {
						if (pm1.isCanceled())
							throw new InterruptedException();
						if (!e.getValue().isEmpty()) {
							db.addAll(e.getValue());
							rec[0] += e.getValue().size();
						}
						mm.setTaskName("(" + (++done) + "/" + all.size() + ")  Reading " + e.getKey());
						mm.worked(1);
					}
					mm.done();

					// 2. write the changed <DB>/<set>.xml files (per set, reported)
					pm1.subTask("Writing sets…");
					db.saveDirtySets(new SubCoreProgressMonitor(pm1, 250));

					pm1.subTask("Updating set list…");
					try {
						ParseScryFallSets sets = new ParseScryFallSets();
						sets.loadSets(false);
						for (Edition ed : sets.getAll())
							Editions.getInstance().addEdition(ed);
					} catch (Exception e) {
						MagicLogger.log(e); // set-list refresh is best effort
					}
					Editions.getInstance().save();
					pm1.worked(50);
				} catch (IOException e) {
					throw new MagicException(e);
				}
			}, pm);
		}
		return rec[0];
	}

	@Override
	public ICardStore getActiveStore() {
		LibraryFilteredCardFileStore lib = (LibraryFilteredCardFileStore) DataManager.getCardHandler()
				.getLibraryFilteredStore();
		Location location = Location.createLocation(activeDeck);
		ICardStore<IMagicCard> store = lib.getStore(location);
		return store;
	}

	@Override
	public String getActiveDeckId() {
		return this.activeDeck;
	}

	@Override
	public void setActiveDeckId(String key) {
		this.activeDeck = key;
	}

	@Override
	public IDbPriceStore getDBPriceStore() {
		return DbPricesMultiFileStore.getInstance();
	}
}
