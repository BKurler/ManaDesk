/*
 * Contributors:
 *     Rémi Dutil (2026) - shared helper for the Set / Num column editors
 *     Rémi Dutil (2026) - a copy in a digital / paper list only offers the printings fit for it
 */
package com.reflexit.magiccards.ui.views.columns;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.CollectorNumber;
import com.reflexit.magiccards.core.model.DigitalRules;
import com.reflexit.magiccards.core.model.IMagicCard;

/** Every database printing of a card name that lives in a given set, ordered by collector number. */
final class Printings {

	static final Comparator<IMagicCard> BY_NUMBER = (a, b) -> CollectorNumber.compare(a.getCollectorId(),
			b.getCollectorId());

	private Printings() {
	}

	static List<IMagicCard> inSet(String name, String set) {
		return inSet(name, set, null);
	}

	/** Same, only the printings that fit the kind (digital / paper) of the deck or collection at {@code list}. */
	static List<IMagicCard> inSet(String name, String set, com.reflexit.magiccards.core.model.Location list) {
		List<IMagicCard> out = new ArrayList<>();
		if (name == null || set == null || set.isEmpty())
			return out;
		for (IMagicCard base : DataManager.getInstance().getMagicDBStore().getCandidates(name)) {
			if (set.equals(base.getSet()))
				out.add(base);
		}
		out.sort(BY_NUMBER);
		return out;
	}
}
