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
package com.reflexit.magiccards.ui.views.nav;

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;

import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.nav.CardElement;
import com.reflexit.magiccards.core.model.nav.CardOrganizer;

/**
 * Sorts deck/collection trees alphabetically, but keeps a deck grouped with
 * its own sideboard and extra list right after it (main, then sideboard, then
 * extra) instead of pure alphabetical order - which would put "-extra" before
 * "-sideboard" for the same deck ("e" &lt; "s").
 */
public class DeckFamilyViewerComparator extends ViewerComparator {
	private final boolean foldersFirst;

	/** Folders sort before decks/collections (matches the Cards Navigator tree). */
	public DeckFamilyViewerComparator() {
		this(true);
	}

	public DeckFamilyViewerComparator(boolean foldersFirst) {
		this.foldersFirst = foldersFirst;
	}

	@Override
	public int category(Object element) {
		if (!foldersFirst)
			return super.category(element);
		return element instanceof CardOrganizer ? 0 : 1;
	}

	@Override
	public int compare(Viewer viewer, Object e1, Object e2) {
		int cat1 = category(e1);
		int cat2 = category(e2);
		if (cat1 != cat2)
			return cat1 - cat2;
		if (e1 instanceof CardElement && e2 instanceof CardElement) {
			Location la = ((CardElement) e1).getLocation();
			Location lb = ((CardElement) e2).getLocation();
			if (la != null && lb != null) {
				int c = la.toMainDeck().toString().compareToIgnoreCase(lb.toMainDeck().toString());
				if (c != 0)
					return c;
				return rank(la) - rank(lb);
			}
		}
		return super.compare(viewer, e1, e2);
	}

	private static int rank(Location loc) {
		if (loc.isSideboard())
			return 1;
		if (loc.isExtra())
			return 2;
		return 0;
	}
}
