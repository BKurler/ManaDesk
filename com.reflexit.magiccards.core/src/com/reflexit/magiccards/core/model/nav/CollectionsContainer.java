
/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.core.model.nav;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.storage.IStorageInfo;

public class CollectionsContainer extends CardOrganizer {
	public CollectionsContainer(String name, CardOrganizer parent) {
		super(name, parent);
	}

	public CollectionsContainer(LocationPath path, CardOrganizer parent) {
		super(path, parent);
	}

	/**
	 * Orders siblings alphabetically by deck name, but keeps a deck's own
	 * sideboard and extra list right after it (in that order) instead of wherever
	 * "-sideboard"/"-extra" would fall alphabetically on their own (which would
	 * put "-extra" before "-sideboard" for the same deck - "e" &lt; "s").
	 */
	private static final java.util.Comparator<File> DECK_FAMILY_ORDER = (a, b) -> {
		String an = stripXml(a.getName());
		String bn = stripXml(b.getName());
		int c = familyName(an).compareToIgnoreCase(familyName(bn));
		if (c != 0)
			return c;
		return familyRank(an) - familyRank(bn);
	};

	private static String stripXml(String name) {
		return name.endsWith(".xml") ? name.substring(0, name.length() - 4) : name;
	}

	private static String familyName(String name) {
		if (name.endsWith(Location.SIDEBOARD_SUFFIX))
			return name.substring(0, name.length() - Location.SIDEBOARD_SUFFIX.length());
		if (name.endsWith(Location.EXTRA_SUFFIX))
			return name.substring(0, name.length() - Location.EXTRA_SUFFIX.length());
		return name;
	}

	private static int familyRank(String name) {
		if (name.endsWith(Location.SIDEBOARD_SUFFIX))
			return 1;
		if (name.endsWith(Location.EXTRA_SUFFIX))
			return 2;
		return 0;
	}

	@SuppressWarnings("unused")
	public void loadChildren() {
		File dir = getFile();
		File[] listFiles = dir.listFiles();
		if (listFiles == null)
			return;
		java.util.Arrays.sort(listFiles, DECK_FAMILY_ORDER);
		for (File mem : listFiles) {
			if (!mem.exists())
				continue;
			String name = mem.getName();
			// System.err.println(this + "/" + name);
			if (name.equals("MagicDBScry"))
				continue; // skip this one, this is the main Db
			if (name.equals("MagicDB"))
				continue; // skip this one, this is the old Db
			if (name.startsWith("."))
				continue; // skip this one too
			CardElement el = findChieldByName(name);
			if (mem.isDirectory()) {
				if (el == null) {
					CollectionsContainer con = new CollectionsContainer(name, this);
					con.loadChildren();
				} else {
					if (el instanceof CollectionsContainer) {
						((CollectionsContainer) el).loadChildren();
					}
				}
			} else {
				if (name.endsWith(".xml")) {
					if (el == null) {
						boolean deck = checkType(mem);
						CardCollection cardCollection = new CardCollection(name, this, deck, null, null);
					}
				}
			}
		}
	}

	private boolean checkType(File mem) {
		try {
			byte[] headerBytes = new byte[1000];
			InputStream openStream = new FileInputStream(mem);
			try {
				int k = openStream.read(headerBytes);
				if (k == -1)
					return false;
				String header = new String(headerBytes, 0, k);
				if (header.contains("<type>deck</type"))
					return true;
			} finally {
				openStream.close();
			}
		} catch (Exception e) {
			// skip
		}
		return false;
	}

	public CollectionsContainer addCollectionsContainer(String name) {
		return (CollectionsContainer) newElement(name, this);
	}

	public CardCollection addDeck(String filename, boolean isDeck, boolean virtual) {
		CardCollection d = new CardCollection(filename, this, isDeck, virtual, false);

		// After creation, set storage properties BEFORE associate() overwrites transients
		IStorageInfo info = d.getStorageInfo();
		if (info != null) {
			info.setVirtual(virtual);
			info.setType(isDeck ? IStorageInfo.DECK_TYPE : IStorageInfo.COLLECTION_TYPE);
			info.setUnsorted(false); // or whatever default you want
		}

		return d;
	}

	/* !!! RD Never used
	public void removeDeck(CardCollection el) {
		System.out.println("Removing Deck: " + el.getName());
	
		IStorageInfo info = el.getStorageInfo();
	
		if (info instanceof SingleFileCardStorage) {
			SingleFileCardStorage storage = (SingleFileCardStorage) info;
			storage.setSuppressSave(true);
			try {
				el.remove(); // supprime le fichier + retire du parent
			} finally {
				storage.setSuppressSave(false);
			}
		} else {
			el.remove();
		}
	}
	*/

	@Override
	public CardElement newElement(String name, CardOrganizer parent) {
		return new CollectionsContainer(name, parent);
	}

	public CardElement findChield(Location loc) {
		return findChieldByName(loc.getBaseFileName());
	}
}
