/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.core.model.nav;

import java.io.File;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.MagicException;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.storage.ICardStore;
import com.reflexit.magiccards.core.model.storage.IFilteredCardStore;
import com.reflexit.magiccards.core.model.storage.IStorage;
import com.reflexit.magiccards.core.model.storage.IStorageContainer;
import com.reflexit.magiccards.core.model.storage.IStorageInfo;

public class CardCollection extends CardElement {
	transient private ICardStore<IMagicCard> store;
	transient protected Boolean deck;
	transient protected Boolean virtual;
	transient protected Boolean unsorted;
	transient protected boolean opening = false;

	public CardCollection(String filename, CardOrganizer parent) {
		this(filename, parent, null, null, null);
	}

	public CardCollection(String filename, CardOrganizer parent, Boolean deck, Boolean virtual, Boolean unsorted) {
		super(filename, parent, false);
		this.deck = deck;
		this.virtual = virtual;
		this.unsorted = unsorted;
		setParentInit(parent);
		createFile();
		parent.fireCreationEvent(this);
	}

	private void createFile() {
		File file = getFile();
		try {
			if (!file.exists()) {
				file.createNewFile();
			}
		} catch (Exception e) {
			throw new MagicException("Cannot create: " + file, e);
		}
	}

	@Override
	public CardElement newElement(String name, CardOrganizer parent) {
		return new CardCollection(name + ".xml", parent, null, null, null);
	}

	public ICardStore<IMagicCard> getStore() {
		if (store == null) {
			open();
		}
		return this.store;
	}

	public IStorageInfo getStorageInfo() {
		try {
			if (getStore() == null)
				return null;
		} catch (MagicException e) {
			return null;
		}
		IStorage storage = ((IStorageContainer) getStore()).getStorage();
		if (storage instanceof IStorageInfo) {
			return (IStorageInfo) storage;
		}
		return null;
	}

	@Override
	public String getName() {
		return super.getName();
	}

	public synchronized void open() {
		if (opening)
			return;
		opening = true;
		try {
			IFilteredCardStore<IMagicCard> fi = DataManager.getInstance().getCardHandler()
					.getCardCollectionFilteredStore(getId());
			associate(fi.getCardStore());
		} finally {
			opening = false;
		}
	}

	/**
	 * Associate this collection with a card store.
	 * IMPORTANT: This method must NOT write transient values into storage.
	 * It must only synchronize storage => transient.
	 */
	public synchronized void associate(ICardStore<IMagicCard> store) {
		if (store == null)
			return;

		this.store = store;

		IStorageInfo info = getStorageInfo();
		if (info != null) {
			// Synchronize transient fields from storage (never the opposite)
			this.deck = IStorageInfo.DECK_TYPE.equals(info.getType());
			this.virtual = info.isVirtual();
			this.unsorted = info.isUnsorted();
		}
	}

	public boolean isDeck() {
		IStorageInfo info = getStorageInfo();
		if (info != null) {
			return IStorageInfo.DECK_TYPE.equals(info.getType());
		}
		return deck != null ? deck : false;
	}

	public void close() {
		this.store = null;
	}

	public String getId() {
		return getPath().getId();
	}

	public boolean isVirtual() {
		IStorageInfo info = getStorageInfo();
		if (info != null) {
			return info.isVirtual();
		}
		return virtual != null ? virtual : true;
	}

	public boolean isUnsorted() {
		IStorageInfo info = getStorageInfo();
		if (info != null) {
			return info.isUnsorted();
		}
		return unsorted != null ? unsorted : true;
	}

}
