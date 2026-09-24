/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk: Collector's "Count Finishes
 *                  Separately" completion mode, Progress4 variant - the
 *                  numerator, capping each (printing, finish) slot at 4
 *                  owned copies instead of the whole printing (so an
 *                  etched-only printing tops out at 4, while a nonfoil+foil
 *                  printing can contribute up to 8 - matching
 *                  FieldUniqueByFinishAggregator's denominator, which counts
 *                  that same printing as 1 slot vs 2)
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
 * Like {@link FieldCount4Aggregator} but capped per finish instead of per
 * printing: a printing supporting nonfoil+foil can contribute up to 4 of
 * each (8 total), not just 4 overall. Proxy-inclusive, matching
 * {@link FieldCount4Aggregator}'s own always-proxy-inclusive behavior -
 * Progress4 was never proxy-aware and this doesn't change that.
 */
public class FieldCount4ByFinishAggregator extends AbstractIntAggregator {
	public FieldCount4ByFinishAggregator(MagicCardField field) {
		super(field);
	}

	@Override
	protected Object visitAbstractMagicCard(AbstractMagicCard card, Object data) {
		if (card instanceof MagicCardPhysical) {
			MagicCardPhysical p = (MagicCardPhysical) card;
			return p.isOwn() ? Math.min(p.getCount(), 4) : 0;
		}
		MagicCard base = card.getBase();
		EnumMap<CardFinish, Integer> counts = new EnumMap<>(CardFinish.class);
		for (MagicCardPhysical p : base.getPhysicalCards()) {
			if (p.isOwn())
				counts.merge(p.getFinish(), p.getCount(), Integer::sum);
		}
		int sum = 0;
		for (int n : counts.values()) {
			sum += Math.min(n, 4);
		}
		return sum;
	}
}
