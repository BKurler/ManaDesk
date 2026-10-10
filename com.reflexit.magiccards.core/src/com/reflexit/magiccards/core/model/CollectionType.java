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

/**
 * What a collection is for. A collection's virtual flag always follows its
 * type ({@link #isVirtual()}):
 * <ul>
 * <li>{@link #STANDARD} - cards you own (genuine or proxy); not virtual.</li>
 * <li>{@link #FOR_TRADE} - cards you own and offer for trade (genuine only);
 * not virtual. Same rules as Standard for now.</li>
 * <li>{@link #WISH} ("Wishlist/To Print") - cards to buy (Buyer view) or proxies
 * to print (Proxier view), not owned yet; always virtual.</li>
 * </ul>
 * A non-virtual collection never accepts virtual cards (an already-virtual
 * legacy card is left alone, but no card can be set back to virtual), and a
 * virtual collection never accepts owned cards - a wished card is marked Own
 * once bought, then moved to a Standard collection.
 */
public enum CollectionType {
	STANDARD("Standard", false, "Cards you own - genuine or proxy."),
	FOR_TRADE("For Trade", false, "Cards you own (genuine only) that you offer for trade."),
	WISH("Wishlist/To Print", true, "Cards to buy or proxies to print - not owned yet (always virtual).");

	private final String label;
	private final boolean virtual;
	private final String description;

	private CollectionType(String label, boolean virtual, String description) {
		this.label = label;
		this.virtual = virtual;
		this.description = description;
	}

	public String getLabel() {
		return label;
	}

	/** Whether a collection of this type is virtual (holds not-owned cards). */
	public boolean isVirtual() {
		return virtual;
	}

	public String getDescription() {
		return description;
	}

	@Override
	public String toString() {
		return label;
	}

	/** The type stored under {@code key} (an enum name), or {@code null}. */
	public static CollectionType fromKey(String key) {
		if (key == null)
			return null;
		for (CollectionType t : values()) {
			if (t.name().equalsIgnoreCase(key.trim()))
				return t;
		}
		return null;
	}

	/**
	 * The effective type of a collection: the stored one when it agrees with
	 * the collection's virtual flag, otherwise the one implied by that flag
	 * (a collection created before types existed, or whose flag was changed
	 * by older code) - Wishlist when virtual, Standard when not.
	 */
	public static CollectionType resolve(String storedKey, boolean virtual) {
		CollectionType t = fromKey(storedKey);
		if (t != null && t.isVirtual() == virtual)
			return t;
		return virtual ? WISH : STANDARD;
	}
}
