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

import java.util.Comparator;
import java.util.function.Function;

/**
 * Order of the import preview's Language column. A card whose language in the file was replaced by English
 * (the card database has no printing in that language: yellow cell) is not "English" for the user: sorting
 * by Language puts all these cards together - first when ascending, last when descending - and, inside the
 * block, by the language the file gave. Two cards that are both not in that block compare as equal, so the
 * normal Language order decides.
 */
class DeclaredLanguageOrder implements Comparator<Object> {
	private final Function<Object, String> declared;
	private final boolean ascending;

	/**
	 * @param declared
	 *            the language the file gave for a card that was imported as English instead, or {@code null}
	 * @param ascending
	 *            the direction of the sort
	 */
	DeclaredLanguageOrder(Function<Object, String> declared, boolean ascending) {
		this.declared = declared;
		this.ascending = ascending;
	}

	@Override
	public int compare(Object a, Object b) {
		String da = declared.apply(a);
		String db = declared.apply(b);
		if (da == null && db == null)
			return 0;
		int d;
		if (da == null)
			d = 1; // the block of "yellow" cards goes first
		else if (db == null)
			d = -1;
		else
			d = da.compareToIgnoreCase(db);
		return ascending ? d : -d;
	}
}
