/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.core.model.xml;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.MagicException;
import com.reflexit.magiccards.core.MagicLogger;
import com.reflexit.magiccards.core.model.Edition;
import com.reflexit.magiccards.core.model.Editions;
import com.reflexit.magiccards.core.model.ICardHandler;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.model.storage.ICardStore;
import com.reflexit.magiccards.core.model.storage.IDbCardStore;
import com.reflexit.magiccards.core.model.storage.IDbPriceStore;
import com.reflexit.magiccards.core.model.storage.IFilteredCardStore;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;
import com.reflexit.magiccards.core.monitor.SubCoreProgressMonitor;
import com.reflexit.magiccards.core.sync.ParseScryFallSets;
import com.reflexit.magiccards.core.sync.ScryfallBulkCache;
import com.reflexit.magiccards.core.sync.TextPrinter;
import com.reflexit.magiccards.core.sync.UpdateCardsFromWeb;

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

	@Override
	public void loadFromFlatResource(String set) throws IOException {
		InputStream is = FileUtils.loadDbResource(set);
		if (is != null) {
			BufferedReader st = new BufferedReader(new InputStreamReader(is, FileUtils.CHARSET_UTF_8));
			ArrayList<IMagicCard> list = new ArrayList<>();
			loadtFromFlatIntoDB(st, list);
			is.close();
		}
	}

	public static File getDbFolder() {
		File dir = DataManager.getInstance().getModelRoot().getMagicDBContainer().getFile();
		return dir;
	}

	private synchronized int loadtFromFlatIntoDB(BufferedReader st, ArrayList<IMagicCard> list)
			throws MagicException, IOException {
		ICardStore store = getMagicDBStore();
		int init = store.size();
		loadFromFlat(st, list);
		boolean hasAny = list.size() > 0;
		store.addAll(list);
		// ArrayList<IMagicCard> more = fixCards(list);
		// if (more.size() > 0)
		// store.addAll(more);
		int rec = store.size() - init;
		return rec > 0 ? rec : (hasAny ? 0 : -1);
	}

	private ArrayList<IMagicCard> loadFromFlat(BufferedReader st, ArrayList<IMagicCard> list) throws IOException {
		String line = st.readLine(); // header ignore for now
		if (line == null)
			throw new IOException("Empty set file");
		ICardField[] xfields = MagicCardField.toFields(line, "\\Q" + TextPrinter.SEPARATOR);
		String[] fields = new String[xfields.length];
		while ((line = st.readLine()) != null) {
			if (line.length() == 0)
				continue;
			try {
				linesplit(line, TextPrinter.SEPARATOR_CHAR, fields);
				MagicCard card = new MagicCard();
				int i = 0;
				for (ICardField field : xfields) {
					if (i < fields.length) {
						card.set(field, fields[i]);
					}
					i++;
				}
				// if (markCn && (card.getCollNumber() == null || card.getCollNumber().length()
				// ==
				// 0)) {
				// card.setCollNumber(cnum);
				// }
				String id = card.getCardId();
				if (id == null) {
					System.err.print("Skipped invalid: " + TextPrinter.getString(card));
					continue;
				}
				list.add(card);
			} catch (Exception e) {
				MagicLogger.log(e);
			}
		}
		return list;
	}

	/**
	 * Optimized split function
	 *
	 * @param line
	 * @param sep
	 * @return
	 */
	private String[] linesplit(String line, char sep, String res[]) {
		char[] charArray = line.toCharArray();
		int k = 0;
		int a = 0;
		int i = 0;
		for (char c : charArray) {
			if (c == sep) {
				res[k++] = line.substring(a, i).trim().intern();
				a = i + 1;
			}
			i++;
			if (k >= res.length)
				return res;
		}
		res[k++] = line.substring(a, i).trim().intern();
		return res;
	}

	/**
	 * Serializes card-database updates. Two update jobs (e.g. two "Update cards of
	 * selected set(s)" invocations) used to run fully in parallel -
	 * {@code updateOperation} does not lock - racing on the DB, editions.txt and
	 * the temp flat files.
	 */
	private static final Object UPDATE_LOCK = new Object();

	private static volatile long lastSetListRefresh = 0L;
	private static final long SET_LIST_TTL_MS = 10L * 60L * 1000L;

	/**
	 * Pull the Scryfall set list into {@link Editions}, at most once per
	 * {@link #SET_LIST_TTL_MS}. Does not save - the caller saves editions.txt once
	 * at the end of the update.
	 */
	private void refreshSetListOnce() {
		if (System.currentTimeMillis() - lastSetListRefresh < SET_LIST_TTL_MS)
			return;
		try {
			ParseScryFallSets allSets = new ParseScryFallSets();
			allSets.loadSets(false);
			for (Edition edition : allSets.getAll())
				Editions.getInstance().addEdition(edition);
			lastSetListRefresh = System.currentTimeMillis();
		} catch (Exception e) {
			MagicLogger.log(e); // move on if set loading fails
		}
	}

	@Override
	public int downloadUpdates(final String set, final Properties options, ICoreProgressMonitor pm)
			throws MagicException, InterruptedException {
		final int rec[] = new int[1];
		synchronized (UPDATE_LOCK) {
			DataManager.getInstance().getMagicDBStore().updateOperation(pm1 -> {
				try {
					String lang = (String) options.get(UpdateCardsFromWeb.UPDATE_LANGUAGE);
					if (lang != null && lang.length() == 0) {
						lang = null;
					}
					pm1.beginTask("Downloading", 110 + (lang == null ? 0 : 100));
					pm1.subTask("Initializing");
					if (pm1.isCanceled())
						throw new InterruptedException();
					pm1.subTask("Updating set list...");
					refreshSetListOnce();
					ArrayList<IMagicCard> list = new ArrayList<IMagicCard>();
					pm1.subTask("Downloading cards...");
					rec[0] = downloadAndStore(set, options, list, pm1);
					pm1.subTask("Updating editions...");
					Editions.getInstance().save();
					pm1.worked(10);
					if (lang != null && lang.length() > 0) {
						pm1.subTask("Updating languages...");
						Set<ICardField> fieldMaps = new HashSet<ICardField>();
						fieldMaps.add(MagicCardField.LANG);
						new UpdateCardsFromWeb().updateStore(list.iterator(), list.size(), fieldMaps, lang,
								getMagicDBStore(), new SubCoreProgressMonitor(pm1, 100));
					}
				} catch (IOException e) {
					throw new MagicException(e);
				}
			}, pm);
		}
		return rec[0];
	}

	/**
	 * Update a batch of named sets in a single operation: the Scryfall set list is
	 * refreshed once, the bulk card file is fetched and parsed once, and
	 * editions.txt is saved once - instead of repeating all of that per set as a
	 * per-set {@link #downloadUpdates(String, Properties, ICoreProgressMonitor)}
	 * loop would.
	 */
	public int downloadUpdates(final Collection<String> sets, final Properties options, ICoreProgressMonitor pm)
			throws MagicException, InterruptedException {
		final int rec[] = new int[1];
		synchronized (UPDATE_LOCK) {
			DataManager.getInstance().getMagicDBStore().updateOperation(pm1 -> {
				try {
					pm1.beginTask("Updating " + sets.size() + " sets", 120);
					pm1.subTask("Updating set list...");
					refreshSetListOnce();
					pm1.worked(10);
					if (pm1.isCanceled())
						throw new InterruptedException();

					Editions editions = Editions.getInstance();
					ArrayList<Edition> toUpdate = new ArrayList<>();
					for (String name : sets) {
						Edition ed = editions.getEditionByName(name);
						if (ed != null)
							toUpdate.add(ed);
						else
							MagicLogger.log("Update sets: unknown set '" + name + "'");
					}

					ArrayList<IMagicCard> list = new ArrayList<>();
					pm1.subTask("Downloading cards...");
					rec[0] = downloadAndStoreSets(toUpdate, options, list, new SubCoreProgressMonitor(pm1, 100));
					pm1.worked(10);
				} catch (IOException e) {
					throw new MagicException(e);
				}
			}, pm);
		}
		return rec[0];
	}

	// Scryfall download version
	public int downloadAndStore(String set, Properties options, ArrayList<IMagicCard> list, ICoreProgressMonitor pm)
			throws FileNotFoundException, MalformedURLException, IOException, InterruptedException {
		int rec = 0;
		if (set.equalsIgnoreCase("All")) {

			// Refresh sets list
			ParseScryFallSets allSets = new ParseScryFallSets();
			allSets.loadSets(false);

			rec = downloadAndStoreSets(allSets.getAll(), options, list, pm);
			return rec;
		} else if (set.equalsIgnoreCase("Recents")) {

			// Refresh recent sets (last 2 years)
			ParseScryFallSets allSets = new ParseScryFallSets();
			allSets.loadSets(false);

			LocalDate threshold = LocalDate.now().minusYears(2);
			ArrayList<Edition> recents = new ArrayList<>();
			for (Edition edition : allSets.getAll()) {
				LocalDate setDate = edition.getReleaseDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
				if (setDate.compareTo(threshold) > 0)
					recents.add(edition);
			}

			rec = downloadAndStoreSets(recents, options, list, pm);
			return rec;
		} else {

			Editions editions = Editions.getInstance();

			Edition ed = editions.getEditionByName(set);

			rec += downloadAndStoreSet(ed, options, list, new SubCoreProgressMonitor(pm, 1000));
			return rec;
		}
	}

	// Scryfall version
	public int downloadAndStoreSet(Edition edition, Properties options, ArrayList<IMagicCard> list,
			ICoreProgressMonitor pm)
			throws FileNotFoundException, MalformedURLException, IOException, InterruptedException {
		pm.beginTask("Downloading set", 100);
		long t0 = System.currentTimeMillis();
		Set<String> codes = setCodesOf(edition);
		try {
			// Add/refresh the set in the official list before loading its cards.
			Editions.getInstance().addEdition(edition);

			int rec = 0;
			boolean any = false;
			for (String code : codes) {
				File flat = ScryfallBulkCache.flatFileForSet(code, pm);
				pm.worked(60 / Math.max(1, codes.size()));
				if (flat == null)
					continue;
				any = true;
				if (pm.isCanceled())
					throw new InterruptedException();
				ArrayList<IMagicCard> one = new ArrayList<>();
				try (BufferedReader st = new BufferedReader(new InputStreamReader(
						new java.util.zip.GZIPInputStream(new FileInputStream(flat)), FileUtils.CHARSET_UTF_8))) {
					rec += loadtFromFlatIntoDB(st, one);
				}
				list.addAll(one);
			}
			pm.worked(30);
			System.err.println("[SetUpdate] done set '" + edition.getName() + "' " + codes + ": "
					+ (any ? rec + " cards" : "no Scryfall data") + " in " + (System.currentTimeMillis() - t0) + " ms");
			return rec;
		} finally {
			pm.done();
		}
	}

	/**
	 * Update several sets in one shot. Makes sure the local Scryfall split is
	 * current first (one download + split at most), then each set is a plain file
	 * read; editions.txt is saved once, not once per set.
	 */
	public int downloadAndStoreSets(Collection<Edition> editions, Properties options, ArrayList<IMagicCard> list,
			ICoreProgressMonitor pm) throws IOException, InterruptedException {
		int n = editions.size();
		pm.beginTask("Downloading " + n + " sets", Math.max(1, n) * 100 + 100);
		int rec = 0;
		try {
			try {
				ScryfallBulkCache.ensureSplitAll(pm);
			} catch (IOException e) {
				MagicLogger.log(e); // fall through: flatFileForSet does per-set fallback
			}
			pm.worked(100);

			int i = 0;
			for (Edition edition : editions) {
				if (pm.isCanceled())
					throw new InterruptedException();
				i++;
				pm.setTaskName("Updating " + edition.getName() + " (" + i + " of " + n + ")");
				try {
					rec += downloadAndStoreSet(edition, options, list, new SubCoreProgressMonitor(pm, 100));
				} catch (InterruptedException e) {
					throw e;
				} catch (Exception e) {
					MagicLogger.log(e);
				}
			}
			Editions.getInstance().save();
			return rec;
		} finally {
			pm.done();
		}
	}

	/**
	 * The Scryfall set codes for an edition. Only the real abbreviations - NOT
	 * {@code getIconAbbr()}, which is the set-symbol SVG token (e.g. "star" for
	 * sets that use the generic star icon) and is not a set code.
	 */
	private static Set<String> setCodesOf(Edition edition) {
		Set<String> codes = new HashSet<>();
		for (String a : edition.getAbbreviations())
			codes.add(a.toLowerCase());
		return codes;
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

	public static void main(String[] args) {
		String lines[] = new String[] {
				"386463|Abomination of Gudul|{3}{B}{G}{U}|Creature - Horror|3|4|Flying<br>Whenever Abomination of Gudul deals combat damage to a player, you may draw a card. If you do, discard a card.<br>Morph {2}{B}{G}{U} <i>(You may cast this card face down as a 2/2 creature for {3}. Turn it face up any time for its morph cost.)</i>|Khans of Tarkir|Common|0.0||0.0|Erica Yang|159||Flying<br>Whenever Abomination of Gudul deals combat damage to a player, you may draw a card. If you do, discard a card.<br>Morph {2}{B}{G}{U} <i>(You may cast this card face down as a 2/2 creature for {3}. Turn it face up any time for its morph cost.)</i>|0|\n",
				"386464|Abzan Ascendancy|{W}{B}{G}|Enchantment|||When Abzan Ascendancy enters the battlefield, put a +1/+1 counter on each creature you control.<br>Whenever a nontoken creature you control dies, put a 1/1 white Spirit creature token with flying onto the battlefield.|Khans of Tarkir|Rare|0.0||0.0|Mark Winters|160||When Abzan Ascendancy enters the battlefield, put a +1/+1 counter on each creature you control.<br>Whenever a nontoken creature you control dies, put a 1/1 white Spirit creature token with flying onto the battlefield.|0|\n" };
		XmlCardHolder holder = new XmlCardHolder();
		String buf[] = new String[20];
		for (int i = 0; i < 50000; i++) {
			for (String line : lines) {
				holder.linesplit(line, TextPrinter.SEPARATOR_CHAR, buf);
			}
		}
	}
}
