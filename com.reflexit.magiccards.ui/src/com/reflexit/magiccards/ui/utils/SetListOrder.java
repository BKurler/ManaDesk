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
package com.reflexit.magiccards.ui.utils;

import java.util.Collection;
import java.util.List;

import com.reflexit.magiccards.core.model.Editions;
import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.preferences.PreferenceConstants;

/**
 * The order of every set drop list (Set column, import preview, quick filter,
 * Correct Set): alphabetical by default, or by release date - oldest first -
 * when "List sets by release date" is checked in the ManaDesk preferences.
 */
public final class SetListOrder {
	private SetListOrder() {
	}

	/** Whether set lists follow the release date instead of the alphabet. */
	public static boolean byReleaseDate() {
		try {
			return MagicUIActivator.getDefault().getPreferenceStore()
					.getBoolean(PreferenceConstants.SETS_BY_RELEASE_DATE);
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** {@code names} in the user's chosen set order. */
	public static List<String> sort(Collection<String> names) {
		return Editions.getInstance().sortSetNames(names, byReleaseDate());
	}

	/** {@link #sort} as an array, for a combo / cell editor. */
	public static String[] sortToArray(Collection<String> names) {
		List<String> sorted = sort(names);
		return sorted.toArray(new String[sorted.size()]);
	}
}
