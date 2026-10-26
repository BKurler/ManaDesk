/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *******************************************************************************/
package com.reflexit.magiccards.core.model;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.model.storage.ICardStore;
import com.reflexit.magiccards.core.model.storage.IStorage;
import com.reflexit.magiccards.core.model.storage.IStorageContainer;
import com.reflexit.magiccards.core.model.storage.IStorageInfo;

/**
 * Digital decks and collections (the list's own "digital" flag), next to
 * {@link OwnershipRules}. A digital list holds digital cards only - digital-only
 * printings and paper printings also playable online (see {@link DigitalSupport});
 * a paper list never holds a digital-only printing. Cards never cross from a paper
 * list to a digital one (or back): a paper copy would end up in an Arena deck.
 * A digital list has no use for the paper-only details: no Etched finish, no
 * Proxy, no Condition.
 * <p>
 * Every check returns the reason it refuses, or {@code null} when all is fine.
 */
public final class DigitalRules {
	public static final String NO_PAPER_IN_DIGITAL = "This is a digital deck or collection: it only holds cards"
			+ " that exist on MTGO or Arena. This card has no digital printing.";
	public static final String NO_DIGITAL_IN_PAPER = "This card only exists digitally (MTGO / Arena):"
			+ " it can only go in a digital deck or collection.";
	public static final String NO_CROSS_KIND = "Cards cannot be copied or moved between a digital and a paper deck"
			+ " or collection.";
	public static final String NO_PAPER_DETAILS = "A digital deck or collection has no Etched finish, Proxy or Condition.";

	/** The kind of list an import is being resolved for: TRUE digital, FALSE paper, null unknown / no import. */
	private static final ThreadLocal<Boolean> IMPORT_TARGET = new ThreadLocal<Boolean>();

	/** Debug traces of the digital checks and of imports into digital lists (INFO lines in the log); off when committed. */
	public static boolean TRACE_DIGITAL = false;

	/** A {@link #TRACE_DIGITAL} line. */
	public static void trace(String message) {
		if (TRACE_DIGITAL)
			com.reflexit.magiccards.core.MagicLogger.info("[digital] " + message);
	}

	private DigitalRules() {
	}

	/** Name lookups of the running import (on this thread) only match printings fit for a list of this kind; null clears it. */
	public static void setImportTarget(Boolean digitalList) {
		if (digitalList == null)
			IMPORT_TARGET.remove();
		else
			IMPORT_TARGET.set(digitalList);
	}

	/** True when {@code candidate} is not fit for the list kind an import (on this thread) is resolved for. */
	public static boolean isOutOfImportTarget(Object candidate) {
		Boolean target = IMPORT_TARGET.get();
		return target != null && kindVeto(candidate, target.booleanValue()) != null;
	}

	/**
	 * True when {@code element} is a copy in a digital deck or collection: it has no paper price, so
	 * the value columns stay empty for it. Always false while the digital cards are not enabled.
	 */
	public static boolean hasNoValue(Object element) {
		return isInDigitalList(element);
	}

	/** True when {@code element} is a copy in a digital deck or collection (never while the digital cards are not enabled). */
	public static boolean isInDigitalList(Object element) {
		return DigitalSupport.isEnabled() && element instanceof MagicCardPhysical
				&& isDigital(((MagicCardPhysical) element).getLocation());
	}

	/** The names of every digital deck and collection (empty while there is none). Opens the lists. */
	public static java.util.List<String> digitalListNames() {
		java.util.List<String> names = new java.util.ArrayList<String>();
		try {
			com.reflexit.magiccards.core.model.nav.ModelRoot root = DataManager.getInstance().getModelRoot();
			for (com.reflexit.magiccards.core.model.nav.CardOrganizer container : new com.reflexit.magiccards.core.model.nav.CardOrganizer[] {
					root.getDeckContainer(), root.getCollectionsContainer() }) {
				for (com.reflexit.magiccards.core.model.nav.CardCollection list : container.getAllElements())
					if (list.isDigital())
						names.add(list.getName());
			}
		} catch (RuntimeException e) {
			// an unreadable list is not counted
		}
		return names;
	}

	/** True when {@code info} is a digital deck / collection. */
	public static boolean isDigital(IStorageInfo info) {
		return info != null && info.isDigital();
	}

	/** True when {@code store} is a digital deck / collection. */
	public static boolean isDigital(ICardStore<?> store) {
		return isDigital(getStorageInfo(store));
	}

	/** True when the deck / collection at {@code loc} is digital; false for a paper or unknown one. */
	public static boolean isDigital(Location loc) {
		if (loc == null)
			return false;
		try {
			return isDigital(DataManager.getInstance().getCardStore(loc));
		} catch (RuntimeException e) {
			return false;
		}
	}

	private static IStorageInfo getStorageInfo(ICardStore<?> store) {
		if (store instanceof IStorageContainer) {
			IStorage<?> storage = ((IStorageContainer<?>) store).getStorage();
			if (storage instanceof IStorageInfo)
				return (IStorageInfo) storage;
		}
		return null;
	}

	/** True when printing {@code card} fits the deck / collection at {@code list} (an unknown or null list takes any printing). */
	public static boolean fitsList(Object card, Location list) {
		return list == null || !isKnownList(list) || kindVeto(card, isDigital(list)) == null;
	}

	/** Drops what a digital list does not have from {@code copy}: an Etched finish (back to automatic), the Proxy flag and the Condition. */
	public static void dropPaperDetails(MagicCardPhysical copy) {
		if (copy.getRawFinish() == CardFinish.ETCHED)
			copy.setFinish(null);
		if (copy.isProxy())
			copy.setProxy(false);
		if (copy.getCondition() != null)
			copy.setCondition(null);
	}

	/** Why a card cannot be in a list of the given kind, or {@code null}. */
	public static String kindVeto(Object card, boolean digitalList) {
		if (card == null)
			return null;
		if (digitalList) {
			return DigitalSupport.isUsableDigitally(card) ? null : NO_PAPER_IN_DIGITAL;
		}
		return DigitalSupport.isDigitalOnly(card) ? NO_DIGITAL_IN_PAPER : null;
	}

	/**
	 * Why {@code card} cannot be added to (copied, moved, pasted, imported into)
	 * {@code dest}, or {@code null}: its printing must fit the kind of the list, a copy
	 * must not come from a list of the other kind, and a digital list takes no paper-only detail.
	 */
	public static String addVeto(Object card, ICardStore<?> dest) {
		if (card == null || dest == null)
			return null;
		// a single deck / collection knows its kind; the library store holds every list (an import goes through
		// it), so there the kind is the one of the list the copy belongs to
		IStorageInfo info = getStorageInfo(dest);
		boolean digital = info != null ? info.isDigital()
				: card instanceof MagicCardPhysical && isDigital(((MagicCardPhysical) card).getLocation());
		String veto = kindVeto(card, digital);
		if (veto != null)
		{
			trace("addVeto REFUSED " + card + " into " + dest.getLocation() + " (digital list=" + digital + "): " + veto);
			return veto;
		}
		if (card instanceof MagicCardPhysical) {
			MagicCardPhysical copy = (MagicCardPhysical) card;
			Location from = copy.getLocation();
			if (info != null && from != null && !from.equals(dest.getLocation()) && isKnownList(from)
					&& isDigital(from) != digital)
			{
				trace("addVeto REFUSED " + card + " from " + from + " into " + dest.getLocation() + ": cross kind");
				return NO_CROSS_KIND;
			}
			if (digital && hasPaperDetails(copy))
			{
				trace("addVeto REFUSED " + card + " into " + dest.getLocation() + ": paper-only details");
				return NO_PAPER_DETAILS;
			}
		}
		return null;
	}

	/** Why {@code field} cannot be set to {@code value} on a copy in {@code store}, or {@code null}. */
	public static String fieldVeto(ICardStore<?> store, ICardField field, Object value) {
		if (value == null || store == null || !isDigital(store))
			return null;
		if (field == MagicCardField.FINISH && value == CardFinish.ETCHED)
			return NO_PAPER_DETAILS;
		if (field == MagicCardField.PROXY && isTrue(value))
			return NO_PAPER_DETAILS;
		if (field == MagicCardField.CONDITION)
			return NO_PAPER_DETAILS;
		return null;
	}

	/**
	 * Why the list holding {@code cards} cannot become digital ({@code toDigital}) or paper,
	 * or {@code null} when every card fits, so a non-empty list only changes kind when it can.
	 */
	public static String kindChangeVeto(Iterable<?> cards, boolean toDigital) {
		if (cards == null)
			return null;
		int bad = 0;
		for (Object o : cards) {
			if (o instanceof MagicCardPhysical) {
				if (kindVeto(o, toDigital) != null || (toDigital && hasPaperDetails((MagicCardPhysical) o)))
					bad++;
			}
		}
		if (bad == 0)
			return null;
		return "contains " + bad + " card entr" + (bad == 1 ? "y" : "ies")
				+ (toDigital ? " without a digital printing (or with an Etched finish, Proxy or Condition)"
						: " that only exist digitally");
	}

	private static boolean hasPaperDetails(MagicCardPhysical copy) {
		return copy.getRawFinish() == CardFinish.ETCHED || copy.isProxy() || copy.getCondition() != null;
	}

	private static boolean isTrue(Object value) {
		return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
	}

	private static boolean isKnownList(Location loc) {
		try {
			return DataManager.getInstance().getCardStore(loc) != null;
		} catch (RuntimeException e) {
			return false;
		}
	}
}
