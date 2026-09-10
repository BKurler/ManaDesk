/*
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: own-unique that ignores proxy copies
 */
package com.reflexit.magiccards.core.model.aggr;

import com.reflexit.magiccards.core.model.AbstractMagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;

/**
 * Like {@link FieldOwnUniqueAggregator} but a card only counts as "one you have"
 * when at least one <b>genuine</b> owned copy exists - a proxy copy does not fill
 * the slot. A card owned as both genuine and proxy still counts once. The test is
 * count-independent, exactly like {@code isOwn()} in the plain aggregator, so a
 * count-0 owned pile never makes the two totals disagree on its own.
 */
public class FieldGenuineOwnUniqueAggregator extends FieldUniqueAggregator {
	public FieldGenuineOwnUniqueAggregator(MagicCardField field) {
		super(field);
	}

	@Override
	protected String getUniqueCardId(AbstractMagicCard card) {
		return card.hasGenuineOwnedCopy() ? super.getUniqueCardId(card) : null;
	}
}
