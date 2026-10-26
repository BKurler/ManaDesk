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

import com.reflexit.magiccards.core.model.abs.ICard;

/**
 * Digital (MTGO / Arena) cards. Scryfall tells two things about a printing:
 * <ul>
 * <li>{@link MagicCardField#DIGITAL}: it is <em>digital only</em> (no paper
 * card exists) - the Alchemy cards, the Arena-only cards of a paper set...;</li>
 * <li>{@link MagicCardField#ONLINE}: a paper printing that is <em>also</em>
 * playable online (its {@code games} list has {@code mtgo} or {@code arena}).</li>
 * </ul>
 * Digital-only cards are always parsed and stored, but they are hidden
 * everywhere (sets, card lists, name lookups) unless the user turned on
 * "Enable digital cards" - the core cannot read the preference store, so the UI
 * hands the value over with {@link #setEnabled(boolean)} at startup and when it
 * changes.
 */
public final class DigitalSupport {
	private static volatile boolean enabled = false;

	private DigitalSupport() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void setEnabled(boolean value) {
		enabled = value;
	}

	/** True when {@code card} is a digital-only printing. */
	public static boolean isDigitalOnly(Object card) {
		return card instanceof ICard && flag(((ICard) card).get(MagicCardField.DIGITAL));
	}

	/** True when a paper printing is also playable online (never true for a digital-only one). */
	public static boolean isOnline(Object card) {
		return card instanceof ICard && flag(((ICard) card).get(MagicCardField.ONLINE));
	}

	/** True when {@code card} can be used in a digital deck / collection: digital-only or playable online. */
	public static boolean isUsableDigitally(Object card) {
		return isDigitalOnly(card) || isOnline(card);
	}

	/** True when {@code card} must not show: it is digital-only and the support is off. */
	public static boolean isHidden(Object card) {
		return !enabled && isDigitalOnly(card);
	}

	private static boolean flag(Object value) {
		return "true".equals(value);
	}
}
