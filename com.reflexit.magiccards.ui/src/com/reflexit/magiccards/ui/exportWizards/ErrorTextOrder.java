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
 * Order of the import preview's Error column: by the short error text the column shows ("Name not found",
 * "Mismatched info"...). A card's error is not a comparable value (an {@code ImportError} object, or a text),
 * so the shared sort order cannot sort it. The rows with an error come first when ascending - they are the
 * ones to look at - and the rows without one last; descending is the exact reverse.
 */
class ErrorTextOrder implements Comparator<Object> {
	private final Function<Object, String> shortError;
	private final boolean ascending;

	/**
	 * @param shortError
	 *            the short error text of a row, empty or {@code null} when it has none
	 * @param ascending
	 *            the direction of the sort
	 */
	ErrorTextOrder(Function<Object, String> shortError, boolean ascending) {
		this.shortError = shortError;
		this.ascending = ascending;
	}

	@Override
	public int compare(Object a, Object b) {
		String ea = text(a);
		String eb = text(b);
		int d;
		if (ea.isEmpty() && eb.isEmpty())
			d = 0;
		else if (ea.isEmpty())
			d = 1; // no error: after the rows with an error
		else if (eb.isEmpty())
			d = -1;
		else
			d = ea.compareToIgnoreCase(eb);
		return ascending ? d : -d;
	}

	private String text(Object card) {
		String s = shortError.apply(card);
		return s == null ? "" : s.trim();
	}
}
