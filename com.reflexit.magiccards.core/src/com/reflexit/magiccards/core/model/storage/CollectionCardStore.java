/*******************************************************************************
 * Copyright (c) 2008 Alena Laskavaia. All rights reserved. This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Alena Laskavaia - initial API and implementation
 *******************************************************************************/

/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.core.model.storage;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICardCountable;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.model.events.CardEvent;


public class CollectionCardStore extends AbstractCardStoreWithStorage<IMagicCard>
		implements ICardStore<IMagicCard>, ICardCollection<IMagicCard>, IStorageContainer<IMagicCard> {
	protected HashCollectionPart hashpart;

	public CollectionCardStore(IStorage<IMagicCard> storage) {
		this(storage, true);
	}

	public CollectionCardStore(IStorage<IMagicCard> storage, boolean wrapped) {
		super(storage, wrapped);
		this.hashpart = new HashCollectionPart();
	}

	@Override
	protected boolean doUpdate(IMagicCard card, Set<? extends ICardField> mask) {
		if (hashpart.getCard(card.getCardId()) == null) {
			// hash if wrong now, fixing
			reindex();
		}
		return super.doUpdate(card, mask);
	}

	@Override
	public IMagicCard doAddCard(IMagicCard card) {
		Location loc = getLocation();
		if (isUnsorted()) {
			// RD Never merge, except with the last card in the list

			// !!! RD Check for last card, implementation not optimal
			MagicCardPhysical lastCard = (MagicCardPhysical) this.getLast();
			MagicCardPhysical cloneCard = null;
			if (lastCard != null) {
				cloneCard = (MagicCardPhysical) lastCard.cloneCard();
				cloneCard.setCount(1); // Force to 1 to allow the comparaison to work				
			}

			MagicCardPhysical phi = (MagicCardPhysical) this.hashpart.getCard(card);
			if (phi == null || // !!! RD not sure what to do... phi.isMigrated()
					(cloneCard != null && !card.equals(cloneCard))) {
				// New card, we must add it
				if (phi == null || loc != null && !loc.equals(phi.getLocation())) {
					phi = (MagicCardPhysical) createNewCard(card, loc);
					this.hashpart.storeCard(phi);
					if (this.storage.add(phi))
						return phi;
					else {
						this.hashpart.removeCard(phi);
						return null;
					}
				}

			} else {
				// Same card, just increment the count
				lastCard.setCount(lastCard.getCount() + 1);
				return lastCard;
			}

		} else if (getMergeOnAdd()) {
			MagicCardPhysical phi = (MagicCardPhysical) this.hashpart.getCard(card);
			if (phi == null || phi.isMigrated()) {
				if (phi == null || loc != null && !loc.equals(phi.getLocation())) {
					phi = (MagicCardPhysical) createNewCard(card, loc);
					this.hashpart.storeCard(phi);
					if (this.storage.add(phi))
						return phi;
					else {
						this.hashpart.removeCard(phi);
						return null;
					}
				} else {
					int count = 1;
					if (card instanceof MagicCardPhysical) {
						count = ((ICardCountable) card).getCount();
					}
					MagicCardPhysical add = new MagicCardPhysical(card, loc);
					MagicCardPhysical old = phi;
					add.setCount(old.getCount() + count);
					doRemoveCard(old);
					this.hashpart.storeCard(add);
					if (storageAdd(add))
						return add;
					else {
						return null;
					}
				}
			}
		}
		IMagicCard phi = createNewCard(card, loc);
		this.hashpart.storeCard(phi);
		if (storageAdd(phi))
			return phi;
		else {
			return null;
		}
	}

	private boolean storageAdd(IMagicCard card) {
		return this.storage.add(card);
	}

	protected IMagicCard createNewCard(IMagicCard card, Location loc) {
		IMagicCard phi;
		int count = 1;
		if (card instanceof MagicCardPhysical) {
			phi = new MagicCardPhysical(card, loc);
			count = ((MagicCardPhysical) card).getCount();
			((MagicCardPhysical) phi).setCount(count);
		} else {
			phi = new MagicCardPhysical(card, loc);
		}
		return phi;
	}

	@Override
	public boolean doRemoveCard(IMagicCard card) {
		if (!(card instanceof MagicCardPhysical))
			return false;
		MagicCardPhysical phi = (MagicCardPhysical) card;
		Collection cards = this.hashpart.getCards(card.getCardId());
		MagicCardPhysical found = null;
		MagicCardPhysical max = null;
		if (cards != null) {
			for (Object card2 : cards) {
				MagicCardPhysical candy = (MagicCardPhysical) card2;
				// exact instance wins - avoids picking a value-equal sibling
				if (candy == phi) {
					found = candy;
					break;
				}
				if (phi.matching(candy)) {
					if (found == null && phi.getCount() == candy.getCount()) {
						found = candy;
					}
					if (max == null || max.getCount() < candy.getCount())
						max = candy;
				}
			}
		} else {
			storageRemove(phi);
			return true;
		}
		if (found != null) {
			storageRemove(found);
			this.hashpart.removeCard(found);
		} else {
			if (max == null)
				return false;
			if (max.getCount() < phi.getCount())
				return false;
			MagicCardPhysical add = new MagicCardPhysical(max, max.getLocation());
			add.setCount(max.getCount() - phi.getCount());
			storageRemove(max);
			this.hashpart.removeCard(max);
			doAddCard(add);
		}
		return true;
	}

	private void storageRemove(IMagicCard card) {
		this.storage.remove(card);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.reflexit.magiccards.core.xml.SingleFileCardStore#doInitialize()
	 */
	@Override
	protected synchronized void doInitialize() {
		this.storage.load();
		reindex();
	}

	@Override
	public synchronized void reindex() {
		this.hashpart = new HashCollectionPart();
		// load in hash
		for (Object element : this) {
			IMagicCard card = (IMagicCard) element;
			this.hashpart.storeCard(card);
		}
	}

	@Override
	public synchronized IMagicCard getCard(String id) {
		return this.hashpart.getCard(id);
	}

	@Override
	public synchronized Collection getCards(String id) {
		return this.hashpart.getCards(id);
	}

	@Override
	public int getCount() {
		// return this.cardCount;
		return getRealCount();
	}

	public int getRealCount() {
		int count = 0;
		for (Object element : getStorage()) {
			if (element instanceof ICardCountable) {
				ICardCountable card = (ICardCountable) element;
				count += card.getCount();
			} else {
				count++;
			}
		}
		return count;
	}

	public void clear() {
		this.hashpart = new HashCollectionPart();
	}

	/**
	 * In-place split helper: insert a brand new sibling pile of {@code count}
	 * cards <em>directly behind</em> {@code anchor} in this collection / deck.
	 * <p>
	 * The anchor keeps its exact list position and identity - its own count is
	 * <b>not</b> touched here, the caller lowers it. Nothing is merged and
	 * nothing is re-sorted, so the rest of the collection is left untouched and
	 * the new pile shows up right next to the one it came from rather than at
	 * the end of the list.
	 *
	 * @return the newly inserted pile
	 */
	public synchronized MagicCardPhysical addRightAfter(MagicCardPhysical anchor, int count) {
		MagicCardPhysical newPile = new MagicCardPhysical(anchor, anchor.getLocation());
		newPile.setCount(count);
		this.hashpart.storeCard(newPile);
		boolean placed = false;
		if (this.storage instanceof MemoryCardStorage) {
			List<IMagicCard> list = ((MemoryCardStorage<IMagicCard>) this.storage).getList();
			synchronized (list) {
				for (int i = 0; i < list.size(); i++) {
					if (list.get(i) == anchor) {
						list.add(i + 1, newPile);
						placed = true;
						break;
					}
				}
			}
		}
		if (!placed)
			this.storage.add(newPile);
		this.storage.autoSave();
		if (isListenerAttached())
			fireEvent(new CardEvent(this, CardEvent.ADD, newPile));
		return newPile;
	}
}
