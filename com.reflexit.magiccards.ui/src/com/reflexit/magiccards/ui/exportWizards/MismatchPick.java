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
package com.reflexit.magiccards.ui.exportWizards;

import java.util.Collection;
import java.util.function.BiPredicate;

import com.reflexit.magiccards.core.model.CollectorNumber;
import com.reflexit.magiccards.core.model.IMagicCard;

/**
 * The printing "Auto complete using first match" binds a <b>Mismatched info</b> row to: the file's identifiers
 * (Scryfall id, Multiverse id, Num) contradict each other, so the Name and the Set of the file are trusted and
 * the Num is taken from the database - the file's Num stays only when that set has it, else the lowest number
 * of the set.
 */
final class MismatchPick {
	private MismatchPick() {
	}

	/**
	 * @param printings
	 *            every printing of the card's name
	 * @param set
	 *            the set the file gave (blank: none)
	 * @param num
	 *            the Num the file gave (blank: none)
	 * @param sameSet
	 *            tells if the set of the file is a printing's set
	 * @return the printing, or {@code null} when the name and the set of the file do not point at one (the
	 *         caller then falls back to the plain first match of the name)
	 */
	static IMagicCard choose(Collection<? extends IMagicCard> printings, String set, String num,
			BiPredicate<String, String> sameSet) {
		if (printings == null || printings.isEmpty())
			return null;
		boolean hasSet = set != null && !set.trim().isEmpty();
		boolean hasNum = num != null && !num.trim().isEmpty();
		IMagicCard exact = null;
		IMagicCard lowest = null;
		for (IMagicCard p : printings) {
			if (hasSet && !sameSet.test(set, p.getSet()))
				continue;
			if (hasNum && exact == null && CollectorNumber.compare(num.trim(), nz(p.getCollectorId())) == 0)
				exact = p;
			if (lowest == null || CollectorNumber.compare(nz(p.getCollectorId()), nz(lowest.getCollectorId())) < 0)
				lowest = p;
		}
		if (exact != null)
			return exact;
		// no set in the file: the Num alone could not tell, let the caller use the first match of the name
		return hasSet ? lowest : null;
	}

	private static String nz(String s) {
		return s == null ? "" : s;
	}
}
