/*
 * Contributors:
 *     Rémi Dutil (2026) - REMOVE_CONTAINER: the deleted list's store is emptied
 *                         without saving it, and its file removed if still there
 *     Rémi Dutil (2026) - RENAME_CONTAINER: nothing when the list was deleted
 *                         meanwhile (isInTree()); the event threads never let an
 *                         exception escape - logged, or traced for a deleted list
 *     Rémi Dutil (2026) - doInitialize() repairs the main collection: always a
 *                         sorted, writable Standard collection
 */
package com.reflexit.magiccards.core.model.xml;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.MagicLogger;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.OwnershipRules;
import com.reflexit.magiccards.core.model.abs.CardList;
import com.reflexit.magiccards.core.model.abs.ICard;
import com.reflexit.magiccards.core.model.events.CardEvent;
import com.reflexit.magiccards.core.model.events.CardEventUpdate;
import com.reflexit.magiccards.core.model.events.ICardEventListener;
import com.reflexit.magiccards.core.model.nav.CardCollection;
import com.reflexit.magiccards.core.model.nav.CardElement;
import com.reflexit.magiccards.core.model.nav.CardOrganizer;
import com.reflexit.magiccards.core.model.nav.ModelRoot;
import com.reflexit.magiccards.core.model.storage.AbstractCardStoreWithStorage;
import com.reflexit.magiccards.core.model.storage.CollectionCardStore;
import com.reflexit.magiccards.core.model.storage.AbstractCardStore;
import com.reflexit.magiccards.core.model.storage.ICardStore;
import com.reflexit.magiccards.core.model.storage.IStorageInfo;

public class LibraryCardStore extends CollectionMultiFileCardStore {
	private static LibraryCardStore instance;

	public synchronized static LibraryCardStore getInstance() {
		if (instance == null)
			instance = new LibraryCardStore();
		return instance;
	}

	@Override
	public synchronized void doInitialize() {
		final ModelRoot container = DataManager.getInstance().getModelRoot();
		CollectionMultiFileCardStore table = this;
		Collection<CardCollection> colls = container.getAllElements();
		// init super
		CardCollection def = container.getDefaultLib();
		for (CardElement elem : colls) {
			table.addFile(elem.getFile(), elem.getLocation(), false);
		}
		table.setLocation(def.getLocation());
		super.doInitialize();
		// the main collection is always a sorted, writable Standard collection
		AbstractCardStoreWithStorage main = getMultiStore().getStorage(def.getLocation());
		if (main != null && main.getStorage() instanceof IStorageInfo) {
			try {
				OwnershipRules.enforceMainCollection((IStorageInfo) main.getStorage());
			} catch (RuntimeException e) {
				MagicLogger.log(e);
			}
		}
		initialized = true;
		EventListener lis = new EventListener();
		DataManager.getInstance().getModelRoot().addListener(lis);
	}

	@Override
	public void handleEvent(CardEvent event) {
		switch (event.getType()) {
		case CardEvent.UPDATE:
			// need to save xml. Add/Remove save automatically since there is add/remove all,
			// but there nothing triggers save on update except this
			CardEventUpdate evupdate = (CardEventUpdate) event;
			CardList<? extends ICard> cardList = evupdate.getCardList();
			Set<Location> locations = cardList.getUnique(MagicCardField.LOCATION);
			for (Location location : locations) {
				AbstractCardStoreWithStorage storage = getMultiStore().getStorage(location);
				if (storage != null) {
					storage.getStorage().autoSave();
				}
			}
			break;
		}
		super.handleEvent(event);
	}

	class EventListener implements ICardEventListener {
		@Override
		public void handleEvent(final CardEvent event) {
			switch (event.getType()) {
			case CardEvent.ADD:
			case CardEvent.REMOVE:
			case CardEvent.UPDATE:
				return;
			case CardEvent.ADD_CONTAINER: {
				CardElement elem = (CardElement) event.getData();
				if (elem instanceof CardCollection) {
					synchronized (elem) {
						CardCollection cardCollection = (CardCollection) elem;
						CollectionCardStore store = getMultiStore().addFile(elem.getFile(), elem.getLocation());
						cardCollection.associate(store);
					}
				}
				break;
			}
			default:
				break;
			}
			new Thread("Lib event processing") {
				@Override
				public void run() {
					try {
						handle();
					} catch (Throwable t) {
						// never escape the thread ("Exception in thread ..."): log it -
						// unless the list was deleted while this was still working on
						// it (its file is gone, e.g. "Access is denied"): nothing lost
						Object o = event.getType() == CardEvent.RENAME_CONTAINER ? event.getSource() : event.getData();
						if (o instanceof CardElement && !isInTree((CardElement) o))
							MagicLogger.trace("lib", "List deleted while being processed: " + t);
						else
							MagicLogger.log(t);
					}
				}

				private void handle() {
					switch (event.getType()) {
					case CardEvent.ADD_CONTAINER: {
						CardElement elem = (CardElement) event.getData();
						if (elem instanceof CardCollection) {
							ICardStore<IMagicCard> store = getStore(elem.getLocation());
							DataManager.getInstance().reconcile(store);
						}
						break;
					}
					case CardEvent.REMOVE_CONTAINER: {
						CardElement elem = (CardElement) event.getData();
						if (elem instanceof CardCollection) {
							ICardStore<IMagicCard> store = getStore(elem.getLocation());
							if (store != null) {
								// the list is being deleted: emptying its store must not
								// save it - that raced the file's deletion ("Cannot
								// delete", "Access is denied") and could even write the
								// deleted deck's file back
								if (store instanceof AbstractCardStore)
									((AbstractCardStore<?>) store).setAutoCommit(false);
								DataManager.getInstance().remove(null, store);
								getMultiStore().removeLocation(elem.getLocation());
								DataManager.getInstance().reconcile(store);
							}
							java.io.File file = elem.getFile();
							if (file != null && file.isFile() && !file.delete())
								file.deleteOnExit(); // still open elsewhere: gone at exit
						}
						break;
					}
					case CardEvent.RENAME_CONTAINER: {
						CardElement elem = (CardElement) event.getSource();
						// System.err.println("renamed " + event.getData() + " to " +
						// elem.getLocation());
						if (elem instanceof CardOrganizer) {
							// ignore, individual rename per element would be sent for
							// path and name changes
						} else if (isInTree(elem)) { // not when the list was deleted meanwhile
							getMultiStore().renameLocation((Location) event.getData(), elem.getLocation());
							ICardStore<IMagicCard> store = getStore(elem.getLocation());
							if (store != null) // null when the list was deleted meanwhile
								DataManager.getInstance().update(store, Collections.singleton(MagicCardField.LOCATION));
						}
						break;
					}
					case CardEvent.UPDATE_CONTAINER: {
						break;
					}
					default:
						break;
					}
					return;
				}
			}.start();
		}
	}

	/**
	 * Whether {@code el} is still in the deck / collection tree - a deleted
	 * element keeps its parent link, but its parent no longer lists it.
	 */
	static boolean isInTree(CardElement el) {
		CardElement cur = el;
		for (CardOrganizer p = cur.getParent(); p != null; cur = p, p = p.getParent())
			if (!p.getChildren().contains(cur))
				return false;
		return true;
	}

	public CollectionMultiFileCardStore getMultiStore() {
		return this;
	}
}
