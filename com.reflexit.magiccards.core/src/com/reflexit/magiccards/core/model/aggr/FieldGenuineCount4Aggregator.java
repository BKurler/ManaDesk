/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk: Progress4's "Count Proxies"
 *                  counterpart to FieldGenuineOwnCountAggregator - Progress4's
 *                  group-level number used to always include proxy copies
 *                  regardless of that toggle, while the per-card leaf text
 *                  (qualifyingOwnCount() in ProgressColumn) already excluded
 *                  them, so a card whose only copies were excluded proxies
 *                  showed "0/4" on its own row but still contributed a
 *                  nonzero amount to its group's total
 *******************************************************************************/
package com.reflexit.magiccards.core.model.aggr;

import com.reflexit.magiccards.core.model.AbstractMagicCard;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;

/**
 * Like {@link FieldCount4Aggregator} but a proxy copy contributes nothing,
 * matching {@link FieldGenuineOwnCountAggregator}'s own proxy exclusion.
 */
public class FieldGenuineCount4Aggregator extends AbstractIntAggregator {
	public FieldGenuineCount4Aggregator(MagicCardField field) {
		super(field);
	}

	@Override
	protected Object visitAbstractMagicCard(AbstractMagicCard card, Object data) {
		int c;
		if (card instanceof MagicCard)
			c = ((MagicCard) card).getGenuineOwnCount();
		else
			c = card.isProxy() ? 0 : card.getOwnCount();
		if (c > 4)
			return 4;
		return c;
	}
}
