/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk: Progress4 + "Count Finishes
 *                  Separately" + "Count Proxies" excluded - the finish-aware
 *                  counterpart to FieldGenuineCount4Aggregator, same
 *                  reasoning as that class' own header
 *     Rémi Dutil (2026) - visitAbstractMagicCard(): now branches on whether
 *                         the visited node is already a MagicCardPhysical
 *                         (check it directly) instead of unconditionally
 *                         assuming a MagicCard printing and scanning
 *                         base.getPhysicalCards() - see
 *                         FieldOwnUniqueByFinishAggregator's own header for
 *                         why (CardGroupTest#testContractOne caught the same
 *                         issue here)
 *******************************************************************************/
package com.reflexit.magiccards.core.model.aggr;

import java.util.EnumMap;

import com.reflexit.magiccards.core.model.AbstractMagicCard;
import com.reflexit.magiccards.core.model.CardFinish;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;

/**
 * Like {@link FieldCount4ByFinishAggregator} but a proxy copy doesn't fill
 * its finish slot, matching {@link FieldGenuineOwnUniqueByFinishAggregator}'s
 * own proxy exclusion.
 */
public class FieldGenuineCount4ByFinishAggregator extends AbstractIntAggregator {
	public FieldGenuineCount4ByFinishAggregator(MagicCardField field) {
		super(field);
	}

	@Override
	protected Object visitAbstractMagicCard(AbstractMagicCard card, Object data) {
		if (card instanceof MagicCardPhysical) {
			MagicCardPhysical p = (MagicCardPhysical) card;
			return p.isOwn() && !p.isProxy() ? Math.min(p.getCount(), 4) : 0;
		}
		MagicCard base = card.getBase();
		EnumMap<CardFinish, Integer> counts = new EnumMap<>(CardFinish.class);
		for (MagicCardPhysical p : base.getPhysicalCards()) {
			if (p.isOwn() && !p.isProxy())
				counts.merge(p.getFinish(), p.getCount(), Integer::sum);
		}
		int sum = 0;
		for (int n : counts.values()) {
			sum += Math.min(n, 4);
		}
		return sum;
	}
}
