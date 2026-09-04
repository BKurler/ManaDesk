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

import java.util.Comparator;

/**
 * Ordering for collector numbers as printed on cards: {@code "2"} before
 * {@code "10"} before {@code "10a"} before {@code "★5"} - a numeric prefix drives
 * the order, purely alphabetic ones (★, T, GN...) sort after the numbered ones,
 * and the full string breaks ties.
 */
public final class CollectorNumber {

	public static final Comparator<String> ORDER = CollectorNumber::compare;

	private CollectorNumber() {
	}

	public static int compare(String a, String b) {
		if (a == null)
			a = "";
		if (b == null)
			b = "";
		long na = leadingNumber(a), nb = leadingNumber(b);
		if (na != nb)
			return Long.compare(na, nb);
		return a.compareToIgnoreCase(b);
	}

	/** @return the leading run of digits as a number, or {@link Long#MAX_VALUE} when there is none. */
	public static long leadingNumber(String s) {
		if (s == null)
			return Long.MAX_VALUE;
		int i = 0;
		while (i < s.length() && Character.isDigit(s.charAt(i)))
			i++;
		if (i == 0)
			return Long.MAX_VALUE;
		try {
			return Long.parseLong(s.substring(0, i));
		} catch (NumberFormatException e) {
			return Long.MAX_VALUE;
		}
	}
}
