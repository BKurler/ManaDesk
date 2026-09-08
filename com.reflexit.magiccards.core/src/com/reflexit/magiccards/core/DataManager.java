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
 */

package com.reflexit.magiccards.core;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.reflexit.magiccards.core.exports.ImportUtils;
import com.reflexit.magiccards.core.model.CardGroup;
import com.reflexit.magiccards.core.model.ICardHandler;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.IMagicCardPhysical;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardList;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.Predicate;
import com.reflexit.magiccards.core.model.abs.ICard;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.model.nav.ModelRoot;
import com.reflexit.magiccards.core.model.storage.AbstractFilteredCardStore;
import com.reflexit.magiccards.core.model.storage.CollectionCardStore;
import com.reflexit.magiccards.core.model.storage.ICardStore;
import com.reflexit.magiccards.core.model.storage.IDbCardStore;
import com.reflexit.magiccards.core.model.storage.IDbPriceStore;
import com.reflexit.magiccards.core.model.storage.ILocatable;
import com.reflexit.magiccards.core.model.xml.DbMultiFileCardStore;
import com.reflexit.magiccards.core.model.xml.XmlCardHolder;

public class DataManager {
	public static final String ID = "com.reflexit.magiccards.core";
	public static final Set<? extends ICardField> COUNT_FIELDSET = Collections.singleton(MagicCardField.COUNT);
	private static DataManager instance = new DataManager();
	private ICardHandler handler;
	private ModelRoot root;
	private HashMap<String, IMagicCard> links = new HashMap<>();
	private boolean owncopy;
	private Thread initThread;
	private Object initThreadLock = new Object();

	/**
	 * Lightweight startup-progress channel for the database load. The core plugin
	 * is kept Eclipse-free, so this exposes plain values rather than an
	 * {@code IProgressMonitor}; the RCP splash handler polls them while it holds
	 * the splash screen open during {@link #syncInitDb()}.
	 */
	private static volatile int initWorked;
	private static volatile int initTotal;
	private static volatile String initTask = "";
	private static volatile boolean dbFullyLoaded;

	/** Called by the database loaders to publish progress during {@link #syncInitDb()}. */
	public static void reportInit(int worked, int total, String task) {
		initWorked = worked;
		initTotal = total;
		if (task != null)
			initTask = task;
	}

	public static int getInitWorked() {
		return initWorked;
	}

	public static int getInitTotal() {
		return initTotal;
	}

	public static String getInitTask() {
		return initTask;
	}

	/** {@code true} once {@link #syncInitDb()} has finished (or failed). */
	public static boolean isDbFullyLoaded() {
		return dbFullyLoaded;
	}

	// Overall startup progress is reported on a fixed 0..INIT_SCALE scale so the
	// splash bar advances smoothly across the phases of syncInitDb() instead of
	// jumping 0->100 per phase.
	public static final int INIT_SCALE = 1000;
	private static final int BAND_SEED_START = 20;
	private static final int BAND_SEED_END = 90;
	private static final int BAND_DB_START = 90;
	private static final int BAND_DB_END = 700;
	private static final int BAND_PRICE_START = 700;
	private static final int BAND_PRICE_END = 800;
	private static final int BAND_RECONCILE_START = 850;
	private static final int BAND_RECONCILE_END = INIT_SCALE;

	private static int band(int start, int end, long done, long total) {
		return total > 0 ? start + (int) ((long) (end - start) * Math.min(done, total) / total) : start;
	}

	/** {@code "(3/500)  "} count prefix, or {@code ""} when the total is unknown. */
	private static String count(int done, int total) {
		return total > 0 ? "(" + done + "/" + total + ")  " : "";
	}

	/** Per-edition progress while seeding set files (before the main load). */
	public static void reportDbSeed(int done, int total, String setName) {
		reportInit(band(BAND_SEED_START, BAND_SEED_END, done, total), INIT_SCALE,
				count(done, total) + (setName == null ? "Preparing sets…" : "Preparing " + setName));
	}

	/** Per-set progress from the card-database loader, mapped into the card-DB band. */
	public static void reportDbLoad(int setsDone, int setsTotal, String setName) {
		reportInit(band(BAND_DB_START, BAND_DB_END, setsDone, setsTotal), INIT_SCALE,
				count(setsDone, setsTotal) + (setName == null ? "Loading card database…" : "Loading " + setName));
	}

	/** Per-file progress while loading cached prices. */
	public static void reportPriceLoad(int done, int total) {
		reportInit(band(BAND_PRICE_START, BAND_PRICE_END, done, total), INIT_SCALE,
				count(done, total) + "Loading prices…");
	}

	/** Per-card progress from reconcile(), mapped into the reconcile band. */
	private static void reportReconcile(int done, int total) {
		reportInit(band(BAND_RECONCILE_START, BAND_RECONCILE_END, done, total), INIT_SCALE,
				"Linking collections to cards…");
	}

	private DataManager() {
		MagicLogger.debug("Data Manager instance " + this.hashCode());
		handler = new XmlCardHolder();
	}

	public static final DataManager getInstance() {
		return instance;
	}

	public static final ICardHandler getCardHandler() {
		return instance.handler;
	}

	public ICardStore getLibraryCardStore() {
		return handler.getLibraryCardStore();
	}

	public IDbCardStore<IMagicCard> getMagicDBStore() {
		return handler.getMagicDBStore();
	}

	public static IDbPriceStore getDBPriceStore() {
		return instance.handler.getDBPriceStore();
	}

	public boolean isInitialized() {
		return root != null;
	}

	public ModelRoot getModelRoot() {
		if (root != null)
			return root;
		synchronized (this) {
			if (root == null)
				root = ModelRoot.getInstance(FileUtils.getMagicCardsDir());
			return root;
		}
	}

	public ICardStore<IMagicCard> getCardStore(Location to) {
		return getCardHandler().getCardStore(to);
	}

	public void reset(File dir) {
		// File locDir = new File(FileUtils.getWorkspaceFile(), "magiccards");
		synchronized (this) {
			System.setProperty("ma.magiccards.area", dir.getAbsolutePath());
			FileUtils.deleteTree(dir);
			if (root == null) {
				root = ModelRoot.getInstance(dir);
				return;
			}
			root.resetRoot(dir);
		}
		((DbMultiFileCardStore) (getCardHandler().getMagicDBStore())).reload();
		((AbstractFilteredCardStore) (getCardHandler().getLibraryFilteredStore())).reload();
		reconcile();
	}

	public void reset() {
		reset(getRootDir());
	}

	public File getRootDir() {
		File rootDir = getModelRoot().getRootDir();
		if (rootDir == null)
			throw new NullPointerException();
		return rootDir;
	}

	boolean copyCards(Collection<IMagicCard> cards, ICardStore<IMagicCard> store, Location to) {
		if (store == null)
			throw new NullPointerException();
		boolean virtual = store.isVirtual();
		boolean unsorted = store.isUnsorted();
		ArrayList<IMagicCard> list = new ArrayList<>(cards.size());
		boolean ownCopyAllowed = owncopy;
		for (IMagicCard card : cards) {
			if (ownCopyAllowed == false && card instanceof MagicCardPhysical && virtual == false
					&& ((MagicCardPhysical) card).isOwn()) {
				throw new MagicException(
						"Cannot copy own cards into non-virtual deck, use move instead - or override this protection in preferences");
			}
			// copied cards will have target collection ownership
			MagicCardPhysical phi = new MagicCardPhysical(card, to, virtual);
			list.add(phi);
		}
		return add(list, store);
	}

	public boolean copyCards(Collection cards1, ICardStore<IMagicCard> sto) {
		return copyCards(cards1, sto, sto.getLocation());
	}

	/**
	 * Using card representation create proper link to base or find actuall base
	 * card to replace fake one
	 *
	 * @param input
	 * @return
	 */
	public Collection<IMagicCard> resolve(Collection<IMagicCard> input) {
		return resolve(input, new ArrayList<>(), getMagicDBStore());
	}

	private Collection<IMagicCard> resolve(Collection<IMagicCard> input, Collection<IMagicCard> output, ICardStore db) {
		for (Object element : input) {
			IMagicCard card = (IMagicCard) element;
			if (card instanceof CardGroup) {
				resolve(((CardGroup) card).getChildren(), output, db);
			} else {
				// Need to repair references to MagicCard instances
				IMagicCard cardRes = resolve(card, db);
				if (cardRes != null)
					output.add(cardRes);
			}
		}
		return output;
	}

	private Collection<IMagicCard> resolve(ICard[] input, Collection<IMagicCard> output, ICardStore db) {
		for (ICard card : input) {
			if (card instanceof CardGroup) {
				resolve(((CardGroup) card).getChildren(), output, db);
			} else {
				// Need to repair references to MagicCard instances
				IMagicCard cardRes = resolve((IMagicCard) card, db);
				if (cardRes != null)
					output.add(cardRes);
			}
		}
		return output;
	}

	public IMagicCard resolve(IMagicCard card, ICardStore db) {
		if (card instanceof MagicCard) {
			card = (IMagicCard) db.getCard(card.getCardId());
			return card;
		} else if (card instanceof MagicCardPhysical) {
			IMagicCard base = (IMagicCard) db.getCard(card.getCardId());
			if (base != null) {
				((MagicCardPhysical) card).setMagicCard((MagicCard) base);
			} else {
				return null;
			}
		}
		return card;
	}

	public boolean moveCards(Collection<IMagicCard> cards1, ICardStore<IMagicCard> sto) {
		return moveCards(cards1, sto, sto.getLocation());
	}

	boolean moveCards(Collection cards, ICardStore<IMagicCard> store, Location to) {
		if (store == null)
			throw new NullPointerException();
		boolean virtual = store.isVirtual();
		boolean unsorted = store.isUnsorted();
		ArrayList<IMagicCard> list = new ArrayList<>(cards.size());
		for (Object card2 : cards) {
			IMagicCard card = (IMagicCard) card2;
			MagicCardPhysical phi = new MagicCardPhysical(card, to, virtual);
			if (card instanceof MagicCardPhysical) {
				if (((IMagicCardPhysical) card).isOwn()) {
					if (virtual)
						throw new MagicException("Cannot move own cards to virtual collection. Use copy instead.");
					phi.setOwn(true);
				} else {
					phi.setOwn(false);
				}
			}
			list.add(phi);
		}
		boolean res = add(list, store);
		if (res) {
			Location from = getLocation(cards);
			if (from != null) { // optimization
				ICardStore<IMagicCard> sfrom2 = getCardStore(from);
				if (sfrom2 != null) {
					remove(cards, sfrom2);
				}
			} else {
				for (Object card2 : cards) {
					IMagicCard card = (IMagicCard) card2;
					if (!(card instanceof MagicCardPhysical))
						continue;
					MagicCardPhysical mcp = (MagicCardPhysical) card;
					remove(mcp);
				}
			}
		}
		return res;
	}

	public Location getLocation(Collection<IMagicCard> cards) {
		Location from = null;
		for (Object card2 : cards) {
			IMagicCard card = (IMagicCard) card2;
			if (!(card instanceof MagicCardPhysical))
				break;
			Location from2 = ((MagicCardPhysical) card).getLocation();
			if (from2 != null) {
				if (from == null)
					from = from2;
				else if (!from.equals(from2)) {
					from = null;
					break;
				}
			}
		}
		return from;
	}

	public List<IMagicCard> splitCards(Collection<IMagicCard> cards1, int count) {
		Collection<IMagicCard> cards = expandGroups(cards1);
		List<IMagicCard> x = new ArrayList<>();
		for (IMagicCard o : cards) {
			if (o instanceof MagicCardPhysical) {
				MagicCardPhysical mcp = (MagicCardPhysical) o;
				MagicCardPhysical toMove = split(mcp, count);
				if (toMove != null)
					x.add(toMove);
			} else if (o instanceof MagicCard) {
				for (int i = 0; i < count; i++) {
					x.add(o);
				}
			}
		}
		return x;
	}

	public List<IMagicCard> splitCards(Map<IMagicCard, Integer> countMap) {
		List<IMagicCard> x = new ArrayList<>();
		for (IMagicCard o : countMap.keySet()) {
			int count = countMap.get(o);
			if (o instanceof MagicCardPhysical) {
				MagicCardPhysical mcp = (MagicCardPhysical) o;
				MagicCardPhysical toMove = split(mcp, count);
				if (toMove != null)
					x.add(toMove);
			} else if (o instanceof MagicCard) {
				for (int i = 0; i < count; i++) {
					x.add(o);
				}
			}
		}
		return x;
	}

	public boolean add(Collection list, ICardStore store) {
		boolean res = store.addAll(list);
		reconcile(list);
		return res;
	}

	public void remove(Collection list, ICardStore store) {
		if (list == null) {
			store.removeAll();
			reconcile();/// XXX
		} else {
			store.removeAll(list);
			reconcile(list);
		}
	}

	public void remove(MagicCardPhysical mcp) {
		Location from = mcp.getLocation();
		ICardStore<IMagicCard> store = getCardStore(from);
		if (store == null)
			throw new IllegalArgumentException("Cannot find store for " + from);
		store.remove(mcp);
		reconcile(mcp);
	}

	public boolean add(MagicCardPhysical mcp) {
		Location loc = mcp.getLocation();
		ICardStore<IMagicCard> store = getCardStore(loc);
		if (store == null)
			throw new IllegalArgumentException("Cannot find store for " + store);
		boolean res = store.add(mcp);
		reconcile(mcp);
		return res;
	}

	public void move(MagicCardPhysical card, Location toLoc) {
		remove(card);
		card.setLocation(toLoc);
		add(card); // XXX if fails we need to undo the remove
	}

	public MagicCardPhysical split(MagicCardPhysical card, int right) {
		if (!card.isMigrated())
			throw new IllegalArgumentException("Has to be migrated first");
		if (right <= 0)
			return null;
		if (right >= card.getCount())
			return null;
		Location loc = card.getLocation();
		ICardStore<IMagicCard> cardStore = getCardStore(loc);
		if (cardStore == null)
			throw new IllegalArgumentException("Cannot find store for " + cardStore);
		int left = card.getCount() - right;
		Set<MagicCardField> fieldSet = Collections.singleton(MagicCardField.COUNT);
		MagicCardPhysical card2;
		if (cardStore instanceof CollectionCardStore) {
			// Never move or re-sort the source pile: lower its count where it
			// sits and drop the new pile directly behind it.
			card.setCount(left);
			cardStore.update(card, fieldSet);
			card2 = ((CollectionCardStore) cardStore).addRightAfter(card, right);
		} else {
			card2 = new MagicCardPhysical(card, card.getLocation());
			card.setCount(left);
			card2.setCount(right);
			cardStore.update(card, fieldSet);
			cardStore.setMergeOnAdd(false);
			cardStore.add(card2);
			cardStore.setMergeOnAdd(!cardStore.isUnsorted());
		}
		updateList(cardStore.getCards(card.getCardId()), fieldSet);
		return card2;
	}

	public void updateMCP(MagicCardPhysical mcp, Set<? extends ICardField> fieldSet) {
		Location loc = mcp.getLocation();
		ICardStore<IMagicCard> store = getCardStore(loc);
		if (store == null)
			throw new IllegalArgumentException("Cannot find store for " + store);
		store.update(mcp, fieldSet);
		reconcile(mcp);
	}

	public void updateMC(MagicCard mc, Set<? extends ICardField> fieldSet) {
		getMagicDBStore().update(mc, fieldSet);
	}

	public void setField(MagicCardPhysical mc, ICardField field, Object newValue) {
		setField(null, mc, field, newValue);
	}

	public void setField(ICardStore cardStore, MagicCardPhysical mc, ICardField field, Object newValue) {
		Object oldValue = mc.get(field);
		if ((newValue != null && newValue.equals(oldValue)) || (newValue == null && oldValue == null))
			return;
		mc.set(field, newValue);
		if (cardStore == null) {
			Location loc = mc.getLocation();
			cardStore = getCardStore(loc);
			if (cardStore == null)
				throw new IllegalArgumentException("Cannot find store for " + cardStore);
		}
		cardStore.update(mc, Collections.singleton(field));
		reconcile(mc);
	}

	public void update(ICardStore<IMagicCard> cardStore, Set<? extends ICardField> fieldSet) {
		cardStore.updateList(null, fieldSet);
		reconcile(cardStore);
	}

	public void update(IMagicCard card, Set<? extends ICardField> fieldSet) {
		if (card instanceof MagicCard) {
			updateMC((MagicCard) card, fieldSet);
		} else if (card instanceof MagicCardPhysical) {
			updateMCP((MagicCardPhysical) card, fieldSet);
		} else {
			// ignore
		}
	}

	public void updateList(Collection<IMagicCard> list, Set<? extends ICardField> fieldSet) {
		if (list == null || list.isEmpty()) {
			getMagicDBStore().updateList(null, fieldSet);
			reconcile();
		} else {
			IMagicCard card = list.iterator().next();
			if (card instanceof ILocatable) {
				Location loc = ((ILocatable) card).getLocation();
				ICardStore<IMagicCard> store = getCardStore(loc);
				if (store == null)
					throw new IllegalArgumentException("Cannot find store for " + loc);
				store.updateList(list, fieldSet);
			} else {
				getMagicDBStore().updateList(list, fieldSet);
			}
			reconcile(list);
		}
	}

	public Collection<MagicCardPhysical> materialize(Collection<? extends IMagicCard> cards,
			ICardStore<IMagicCard> from) {
		return materialize(cards, Collections.singleton(from));
	}

	public Collection<MagicCardPhysical> materialize(Collection<? extends IMagicCard> cards,
			Collection<ICardStore<IMagicCard>> stores) {
		ArrayList<MagicCardPhysical> in = new ArrayList<>();
		DataManager.expandGroups(in, cards, (Predicate<Object>) card -> {
			if (card instanceof MagicCardPhysical)
				return true;
			return false;
		});
		ArrayList<MagicCardPhysical> res = new ArrayList<>();
		for (MagicCardPhysical card : in) {
			materialize(card, stores, res);
		}
		return res;
	}

	public ArrayList<MagicCardPhysical> materialize(MagicCardPhysical card, Collection<ICardStore<IMagicCard>> stores,
			ArrayList<MagicCardPhysical> res) {
		if (card.isOwn()) {
			res.add(new MagicCardPhysical(card, null));
			return res;
		}
		int rem = card.getCount();
		for (ICardStore<IMagicCard> from : stores) {
			Collection<IMagicCard> piles = from.getCards(card.getCardId());
			if (piles == null || piles.size() == 0) {
				continue;
			}
			for (IMagicCard cand : piles) {
				if (rem <= 0)
					break;
				if (cand instanceof MagicCardPhysical) {
					MagicCardPhysical mcp = (MagicCardPhysical) cand;
					int mc = mcp.getCount();
					if (mc == 0 || mcp.isOwn() == false)
						continue;
					if (mc > rem)
						mc = rem;
					MagicCardPhysical grab = new MagicCardPhysical(mcp, mcp.getLocation());
					grab.setCount(mc);
					grab.setOwn(true);
					res.add(grab);
					rem = rem - mc;
				}
			}
		}
		if (rem > 0) {
			MagicCardPhysical x = new MagicCardPhysical(card, null);
			x.setOwn(false);
			x.setCount(rem);
			res.add(x);
		}
		return res;
	}

	/**
	 * Repairs back link between base cards and physical cards, expensive since it
	 * reads whole database
	 */
	public void reconcile() {
		links.clear();
		ICardStore lib = getLibraryCardStore();
		ICardStore db = getMagicDBStore();
		db.initialize();
		reconcile(lib);
	}

	public void reconcile(Iterable cards) {
		if (cards == null)
			return;
		ICardStore db = getMagicDBStore();
		ICardStore library = getLibraryCardStore();
		List<IMagicCard> list = new MagicCardList(cards).getList();
		int total = list.size();
		int done = 0;
		for (Object card : list) {
			// Need to repair references to MagicCard instances
			if (card instanceof MagicCardPhysical) {
				MagicCardPhysical mcp = (MagicCardPhysical) card;
				if (mcp.getName() != null) {
					reconcile(mcp, db, library, false);
				}
			}
			if ((++done & 0x3ff) == 0)
				reportReconcile(done, total);
		}
		Collection<IMagicCard> list2 = new MagicCardList(list).getMagicBaseList();
		getMagicDBStore().updateList(list2, Collections.singleton(MagicCardField.OWN_COUNT));
	}

	private void reconcile(MagicCardPhysical mcp) {
		reconcile(mcp, getMagicDBStore(), getLibraryCardStore(), true);
	}

	private void reconcile(MagicCardPhysical mcp, ICardStore db, ICardStore library, boolean update) {
		// System.err.println("reconcile " + mcp + " " +
		// System.identityHashCode(mcp));
		String id = mcp.getCardId();
		if (id == null)
			return;
		MagicCard base = (MagicCard) db.getCard(id);
		if (base != null) {
			mcp.setMagicCard(base);
		} else {

			// RD To allow automatic "link" for collections and decks using different ids
			// This is not "updating" the source file, just linking them
			ImportUtils.updateCardReference((MagicCardPhysical) mcp);

			id = mcp.getCardId();
			if (id == null)
				return;
			base = (MagicCard) db.getCard(id);
			if (base != null) {
				mcp.setMagicCard(base);
			} else {
				MagicLogger.log("Cannot reconsile after retry " + mcp);
			}

		}
		CardGroup realcards = new CardGroup(MagicCardField.ID, mcp.getName());
		realcards.addAll(library.getCards(id));
		links.put(id, realcards);
		if (update)
			update(mcp.getBase(), Collections.singleton(MagicCardField.OWN_COUNT));
	}

	public CardGroup getRealCards(MagicCard mc) {
		String id = mc.getCardId();
		if (links.containsKey(id))
			return (CardGroup) links.get(id);
		return null;
	}

	public void setOwnCopyEnabled(boolean newValue) {
		owncopy = newValue;
	}

	public boolean waitForInit(int sec) {
		IDbCardStore<IMagicCard> magicDBStore = getMagicDBStore();
		synchronized (magicDBStore) {
			if (!magicDBStore.isInitialized())
				asyncInitDb();
			while (!magicDBStore.isInitialized() && sec-- > 0) {
				try {
					magicDBStore.wait(1000);
				} catch (InterruptedException e) {
					break;
				}
			}
			return magicDBStore.isInitialized();
		}
	}

	public void syncInitDb() {
		MagicLogger.traceStart("syncDb");
		try {
			reportInit(0, INIT_SCALE, "Scanning card folders…");
			getModelRoot();
			reportInit(BAND_SEED_START, INIT_SCALE, "Preparing sets…");
			// getMagicDBStore().initialize() runs loadFromSoftware() (per-edition
			// seed - reportDbSeed) then the per-set load loop (reportDbLoad).
			getMagicDBStore().initialize();
			reportInit(BAND_PRICE_START, INIT_SCALE, "Loading prices…");
			getDBPriceStore().initialize();
			getDBPriceStore().reloadPrices(); // XXX
			reportInit(BAND_PRICE_END, INIT_SCALE, "Loading your collections…");
			getCardHandler().getLibraryCardStore().initialize();
			reportInit(BAND_RECONCILE_START, INIT_SCALE, "Linking collections to cards…");
			reconcile();
		} finally {
			reportInit(INIT_SCALE, INIT_SCALE, "Ready");
			dbFullyLoaded = true;
			MagicLogger.traceEnd("syncDb");
		}
	}

	public void asyncInitDb() {
		synchronized (initThreadLock) {
			if (initThread == null) {
				MagicLogger.trace("sync db thread");
				initThread = new Thread("Init DB") {
					@Override
					public void run() {
						syncInitDb();
						initThread = null;
					}
				};
				initThread.start();
			} else {
				MagicLogger.trace("sync db bailed");
				return;
			}
		}
	}

	public File getPricesDir() {
		File dir = getModelRoot().getMagicDBContainer().getFile();
		File pricesDir = new File(dir, "prices");
		if (!pricesDir.exists())
			pricesDir.mkdirs();
		return pricesDir;
	}

	public File getTablesDir() {
		File dir = getModelRoot().getMagicDBContainer().getFile();
		File pricesDir = new File(dir, "tables");
		if (!pricesDir.exists())
			pricesDir.mkdirs();
		return pricesDir;
	}

	public static Collection expandGroups(Collection cards) {
		return expandGroups(new ArrayList(cards.size()), cards, new CardGroup.NonGroupPredicate());
	}

	public static Collection expandGroups(Collection result, Collection cards) {
		return expandGroups(result, cards, new CardGroup.NonGroupPredicate());
	}

	public static Collection expandGroups(Collection result, Collection cards, Predicate<Object> filter) {
		for (Object o : cards) {
			if (filter.test(o))
				result.add(o);
			if (o instanceof CardGroup)
				expandGroups(result, ((CardGroup) o).getChildren(), filter);
		}
		return result;
	}

	public static Collection expandGroups(Collection result, ICard[] cards, Predicate<Object> filter) {
		for (ICard o : cards) {
			if (filter.test(o))
				result.add(o);
			if (o instanceof CardGroup)
				expandGroups(result, ((CardGroup) o).getChildren(), filter);
		}
		return result;
	}
}
