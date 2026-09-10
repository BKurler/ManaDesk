/*
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: own-count that ignores proxy copies
 */
package com.reflexit.magiccards.core.model.aggr;

import com.reflexit.magiccards.core.model.AbstractMagicCard;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;

/**
 * Like {@link FieldOwnCountAggregator} but a proxy copy contributes nothing - it
 * is a card you physically have that is not the real thing. Feeds the Collector
 * view's "genuine only" value / completion totals.
 */
public class FieldGenuineOwnCountAggregator extends FieldOwnCountAggregator {
	public FieldGenuineOwnCountAggregator(MagicCardField field) {
		super(field);
	}

	@Override
	protected Object visitAbstractMagicCard(AbstractMagicCard card, Object data) {
		// A base card is not itself a proxy - ask its physical copies (which are)
		// via getGenuineOwnCount(), which aggregates the same way over realCards.
		if (card instanceof MagicCard)
			return ((MagicCard) card).getGenuineOwnCount();
		if (card.isProxy())
			return 0;
		return card.getOwnCount();
	}
}
