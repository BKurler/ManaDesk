/*******************************************************************************
 * Copyright (c) 2008 Alena Laskavaia.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Alena Laskavaia - initial API and implementation
 *******************************************************************************/

/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - saveDirtySets() writes the changed set files in parallel
 *                         with per-set progress; updateOperation() withholds card
 *                         events during a bulk update and replays one at the end
 *     Rémi Dutil (2026) - no bundled flat-file seed: doInitialize() just loads
 *                         <DB>/*.xml; isEmpty()/loadedSetCount() count set files
 *                         on disk (not the map, which reconcile() inflates)
 */

package com.reflexit.magiccards.core.model.xml;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.MagicException;
import com.reflexit.magiccards.core.MagicLogger;
import com.reflexit.magiccards.core.model.Edition;
import com.reflexit.magiccards.core.model.Editions;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.model.events.CardEvent;
import com.reflexit.magiccards.core.model.nav.MagicDbContainter;
import com.reflexit.magiccards.core.model.storage.AbstractCardStoreWithStorage;
import com.reflexit.magiccards.core.model.storage.AbstractMultiStore;
import com.reflexit.magiccards.core.model.storage.ICardCollection;
import com.reflexit.magiccards.core.model.storage.IDbCardStore;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;
import com.reflexit.magiccards.core.monitor.ICoreRunnableWithProgress;

/**
 * Card Store for Magic DB
 *
 */
public class DbMultiFileCardStore extends AbstractMultiStore<IMagicCard> implements
		ICardCollection<IMagicCard>, IDbCardStore<IMagicCard> {
	private static DbMultiFileCardStore instance;
	private boolean loadDefault;
	private GlobalDbHandler handler;

	@Override
	public void reload() {
		init();
		super.reload();
	}

	private void init() {
		loadDefault = true;
		handler = new GlobalDbHandler();
	}

	public class GlobalDbHandler {
		private HashMap<String, IMagicCard> hash = new HashMap<>();
		// map from name to latest card
		private HashMap<String, Object> primeMap = new HashMap<>();
		private Comparator<IMagicCard> comp = (arg0, arg1) -> {
			String id1 = arg0.getCardId();
			String id2 = arg1.getCardId();
			if (id1 == null && id2 == null)
				return 0;
			if (id1 == null && id2 != null)
				return 1;
			if (id1 != null && id2 == null)
				return -1;
			if (id1.equals(id2))
				return 0;
			boolean en1 = arg0.getEnglishCardId() == null;
			boolean en2 = arg1.getEnglishCardId() == null;
			if (id2.compareTo(id2) > 0 && en1 == en2 || (en2 && !en1)) {
				return 1;
			}
			return -1;
		};

		public boolean hashAndResolve(IMagicCard card) {
			boolean conflict = false;
			String id = card.getCardId();
			IMagicCard prev = hash.get(id);
			if (prev != null) {
				boolean delcur = conflictMerge(prev, card);
				hash.put(prev.getCardId(), prev); // rehash prev it could have
													// changed
				if (delcur) {
					conflict = true;
				} else {
					hash.put(card.getCardId(), card); // id could have changed
				}
			} else
				hash.put(id, card);
			// map for name
			Object sibCard = primeMap.get(card.getName());
			if (sibCard == null)
				primeMap.put(card.getName(), card);
			else {
				if (sibCard instanceof Collection) {
					((Collection) sibCard).add(card);
				} else {
					TreeSet<MagicCard> list = new TreeSet<>(comp);
					list.add((MagicCard) sibCard);
					list.add((MagicCard) card);
					primeMap.put(card.getName(), list);
				}
			}
			return conflict;
		}

		public boolean conflictMerge(IMagicCard prev, IMagicCard card) {
			if (prev.equals(card)) {
				// merge
				((MagicCard) prev).setNonEmptyFromCard(card.getBase());
				// ACCESSORIES is fully re-derived from Scryfall on every split, so
				// an incoming empty value must be allowed to clear a stale one
				// (setNonEmptyFromCard would keep the old value). A null means the
				// incoming record simply has no accessories column - leave prev be.
				String acc = card.getAccessories();
				if (acc != null)
					((MagicCard) prev).set(MagicCardField.ACCESSORIES, acc);
				return true;
			}
			String id = card.getCardId();
			// redo
			Integer oldI = (Integer) prev.get(MagicCardField.SIDE);
			Integer curI = (Integer) card.get(MagicCardField.SIDE);
			Object prevPart = prev.get(MagicCardField.PART);
			Object curPart = card.get(MagicCardField.PART);
			int old = oldI != null ? oldI.intValue() : 0;
			int cur = curI != null ? curI.intValue() : 0;
			if (old == 0 && cur == 0) {
				if (prevPart != null)
					old = 1;
				if (curPart != null)
					cur = 1;
			}
			if (old == cur) {
				MagicLogger.log("STORE DOUBLE: " + prev + " " + old + "[" + prevPart + "] -> new " + card
						+ "[" + curPart + "] " + cur);
				return true;
			} else {
				if (old == 1) {
					((MagicCard) prev).setCardId("-" + id);
					return false;
				} else if (cur == 1) {
					((MagicCard) card).setCardId("-" + id);
					return false;
				}
			}
			return false;
		}

		public IMagicCard get(String id) {
			return hash.get(id);
		}

		public void remove(IMagicCard card) {
			hash.remove(card.getCardId());
		}

		public MagicCard getPrime(String name) {
			Object object = primeMap.get(name);
			if (object == null)
				return null;
			if (object instanceof MagicCard)
				return (MagicCard) object;
			return ((Collection<MagicCard>) object).iterator().next();
		}

		public Collection<IMagicCard> getCandidates(String name) {
			Object object = primeMap.get(name);
			if (object instanceof MagicCard) {
				ArrayList<IMagicCard> arr = new ArrayList<>(1);
				arr.add((IMagicCard) object);
				return arr;
			} else {
				return (Collection<IMagicCard>) object;
			}
		}
	}

	protected DbMultiFileCardStore(boolean load) {
		init();
		this.loadDefault = load;
	}

	public synchronized static DbMultiFileCardStore getInstance() {
		if (instance == null)
			instance = new DbMultiFileCardStore(true);
		return instance;
	}

	public synchronized DbFileCardStore addFile(final File file, final Location location, boolean initialize) {
		if (location != null && map.containsKey(location)) {
			return (DbFileCardStore) map.get(location);
		}
		DbFileCardStore store = new DbFileCardStore(file, location, handler, initialize);
		if (initialize) {
			store.initialize();
		}
		addCardStore(store);
		return store;
	}

	@Override
	public int getCount() {
		return getStorage().size();
	}

	@Override
	protected AbstractCardStoreWithStorage<IMagicCard> newStorage(IMagicCard card) {
		DbFileCardStore store = new DbFileCardStore(getFile(card), getLocation(card), handler, false);
		store.getStorage().setAutoCommit(getStorage().isAutoCommit());
		return store;
	}

	@Override
	public void update(IMagicCard card, Set<? extends ICardField> mask) {
		if (card instanceof MagicCardPhysical)
			super.update(((MagicCardPhysical) card).getCard(), mask);
		else
			super.update(card, mask);
	}

	@Override
	public IMagicCard getCard(String id) {
		return handler.get(id);
	}

	@Override
	public Collection<IMagicCard> getCards(String id) {
		IMagicCard card = getCard(id);
		if (card == null)
			return Collections.EMPTY_LIST;
		ArrayList<IMagicCard> arr = new ArrayList<>(1);
		arr.add(card);
		return arr;
	}

	@Override
	protected boolean doUpdate(IMagicCard card, Set<? extends ICardField> mask) {
		boolean needUpdate = true;
		if (mask != null && !mask.isEmpty()) {
			needUpdate = false;
			for (ICardField f : mask) {
				if (!f.isTransient()) {
					needUpdate = true;
					break;
				}
			}
		}
		if (card.getSet() != null) {
			AbstractCardStoreWithStorage storage = getStorage(getLocation(card));
			if (storage == null) {
				storage = newStorage(card);
				addCardStore(storage);
			}
			if (needUpdate)
				storage.getStorage().autoSave();
		}
		return super.doUpdate(card, mask);
	}

	@Override
	protected Location getLocation(IMagicCard card) {
		return Location.fromCard(card);
	}

	@Override
	public Location getLocation() {
		return null;
	}

	@Override
	public String getComment() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getName() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isVirtual() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isUnsorted() {
		throw new UnsupportedOperationException();
	}

	public File getFile(final IMagicCard card) {
		if (card instanceof MagicCard) {
			return new File(XmlCardHolder.getDbFolder(), Location.fromCard(card)
					.getBaseFileName());
		}
		throw new MagicException("Unknown card type " + card);
	}

	/** How many {@code <DB>/*.xml} set files are on disk. This is the real "has the
	 *  card database been downloaded" signal - {@code map} is unreliable because
	 *  {@code reconcile()} creates empty per-set stores for the user's own
	 *  deck/collection cards before any set file exists. */
	private static int setFileCount() {
		File dir = XmlCardHolder.getDbFolder();
		String[] xml = dir == null ? null : dir.list((d, n) -> n.endsWith(".xml"));
		return xml == null ? 0 : xml.length;
	}

	/**
	 * {@code true} when the card database has never been downloaded (no
	 * {@code <DB>/*.xml} on disk). The workbench prompts for a first-run download
	 * (see {@code CheckForUpdateDbHandler.promptDownloadIfEmpty}).
	 */
	public boolean isEmpty() {
		return setFileCount() == 0;
	}

	/** Number of {@code <DB>/*.xml} set files on disk (0 = never downloaded). */
	public int loadedSetCount() {
		return setFileCount();
	}

	/** Non-hidden editions that have no non-empty {@code <DB>/<abbr>.xml} on disk. */
	public java.util.List<String> missingSetFiles() {
		java.util.List<String> missing = new ArrayList<>();
		Set<Location> hidden = getHiddenSets();
		for (String set : Editions.getInstance().getNames()) {
			Location loc = Location.createLocationFromSet(set);
			if (hidden.contains(loc))
				continue;
			File f = new File(XmlCardHolder.getDbFolder(), loc.getBaseFileName());
			if (!f.exists() || f.length() == 0)
				missing.add(set);
		}
		return missing;
	}

	@Override
	public synchronized void doInitialize() throws MagicException {
		if (isInitialized())
			return;
		MagicLogger.traceStart("db init");
		try {
			if (!loadDefault) {
				super.doInitialize();
				return;
			}
			this.loadDefault = false;
			// load the per-set <DB>/*.xml files. There is no bundled seed any more -
			// an empty <DB> dir means the database has never been downloaded; the
			// workbench detects that and offers a first-run Scryfall update.
			ArrayList<File> files = new ArrayList<>();

			try {
				MagicDbContainter con = DataManager.getInstance().getModelRoot().getMagicDBContainer();
				File[] members = con.getFile().listFiles();
				if (members != null) {
					for (File file : members) {
						if (file.getName().endsWith(".xml"))
							files.add(file);
					}
				}
			} catch (MagicException e) {
				MagicLogger.log(e);
				return;
			}
			Set<Location> hiddenSets = getHiddenSets();
			setInitialized(false);
			try {
				int total = files.size();
				int done = 0;
				for (File file : files) {
					Location setLocation = Location.createLocation(file, Location.NO_WHERE);
					if (!hiddenSets.contains(setLocation))
						addFile(file, setLocation, true);
					else
						MagicLogger.log("Not loading set - hidden - " + setLocation);
					DataManager.reportDbLoad(++done, total, setLocation.getName());
				}
			} finally {
				setInitialized(true);
			}
		} finally {
			MagicLogger.traceEnd("db init");
		}
	}

	private Set<Location> getHiddenSets() {
		HashSet<Location> sets = new HashSet<>();
		for (Edition edition : Editions.getInstance().getEditions()) {
			if (edition.isHidden()) {
				sets.add(Location.createLocationFromSet(edition.getName()));
			}
		}
		return sets;
	}

	@Override
	public MagicCard getPrime(String name) {
		return handler.getPrime(name);
	}

	@Override
	public Collection<IMagicCard> getCandidates(String name) {
		if (name == null)
			return Collections.emptyList();
		Collection<IMagicCard> xcards = handler.getCandidates(name);
		if (xcards == null)
			return Collections.emptyList();
		return xcards;
	}

	@Override
	public synchronized boolean isInitialized() {
		return super.isInitialized();
	}

	/**
	 * While true (only during a bulk {@link #updateOperation}), the per-set
	 * {@code ADD} events that {@code addAll} fires are withheld and one coalesced
	 * event is sent when the flag clears. Without this a full update fires ~1000
	 * events, each making every card list reschedule its "Loading cards for ..."
	 * job - which buries the update's own progress in the status bar and pulls
	 * card images from the web for lists nobody is looking at yet.
	 */
	private volatile boolean deferEvents;
	private volatile CardEvent deferredEvent;

	@Override
	protected void fireEvent(CardEvent event) {
		if (deferEvents) {
			if (event != null && event.getFirstDataElement() instanceof IMagicCard)
				deferredEvent = event; // keep a representative one to replay
			return;
		}
		super.fireEvent(event);
	}

	@Override
	public void updateOperation(ICoreRunnableWithProgress run, ICoreProgressMonitor monitor)
			throws InterruptedException {
		boolean ac = isAutoCommit();
		setAutoCommit(false);
		deferEvents = true;
		deferredEvent = null;
		try {
			run.run(monitor);
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException)
				throw (RuntimeException) cause;
			throw new MagicException(cause);
		} finally {
			setAutoCommit(ac);
			deferEvents = false;
			CardEvent replay = deferredEvent;
			deferredEvent = null;
			if (replay != null)
				fireEvent(replay); // single reload instead of one per set
		}
	}

	/**
	 * Write the per-set {@code <DB>/*.xml} files that have unsaved changes,
	 * reporting one work unit per set. Used by "Update Card Database" so the write
	 * phase shows real progress instead of stalling inside the auto-commit flush.
	 * <p>
	 * The files are independent, so the writes run on a small thread pool. Each
	 * {@code storage.save()} is synchronized on its own storage and builds a fresh
	 * XML writer (see {@code MagicXmlStreamHandler}); the shared progress monitor
	 * is the only cross-thread contact and every call to it is synchronized here.
	 */
	public void saveDirtySets(ICoreProgressMonitor pm) {
		ArrayList<AbstractCardStoreWithStorage<IMagicCard>> dirty = new ArrayList<>();
		for (AbstractCardStoreWithStorage<IMagicCard> t : map.values())
			if (t.getStorage().isNeedToBeSaved())
				dirty.add(t);
		final int total = dirty.size();
		pm.beginTask("Writing sets", Math.max(1, total));
		int threads = Math.max(1, Math.min(6, Runtime.getRuntime().availableProcessors()));
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads,
				r -> {
					Thread th = new Thread(r, "db-save");
					th.setDaemon(true);
					return th;
				});
		java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();
		java.util.List<java.util.concurrent.Future<?>> futures = new ArrayList<>(total);
		for (AbstractCardStoreWithStorage<IMagicCard> t : dirty) {
			futures.add(pool.submit(() -> {
				try {
					t.getStorage().save();
				} catch (RuntimeException e) {
					MagicLogger.log(e);
				}
				synchronized (pm) {
					pm.setTaskName("(" + done.incrementAndGet() + "/" + total + ")  Writing " + t.getLocation());
					pm.worked(1);
				}
			}));
		}
		pool.shutdown();
		try {
			for (java.util.concurrent.Future<?> f : futures)
				f.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			pool.shutdownNow();
		} catch (java.util.concurrent.ExecutionException e) {
			MagicLogger.log(e);
		}
		pm.done();
	}
}
