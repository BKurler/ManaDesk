/*
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: completion % counting genuine copies only
 */
package com.reflexit.magiccards.core.model.aggr;

import com.reflexit.magiccards.core.model.CardGroup;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.abs.ICardField;

/**
 * Like {@link FieldProggressAggregator} but the numerator is the number of
 * unique cards you own a <b>genuine</b> copy of - proxies do not count toward set
 * / format completion.
 */
public class FieldGenuineProggressAggregator extends FieldProggressAggregator {
	public FieldGenuineProggressAggregator(ICardField field) {
		super(field);
	}

	@Override
	public int getProgressSize(CardGroup cardGroup) {
		return cardGroup.getInt(MagicCardField.GENUINE_OWN_UNIQUE);
	}
}
