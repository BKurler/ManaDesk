/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk: Collector's "Count Finishes
 *                  Separately" completion mode - the numerator, counting one
 *                  owned slot per (printing, finish) instead of one owned
 *                  slot per printing
 *     Rémi Dutil (2026) - visitAbstractMagicCard(): now branches on whether
 *                         the visited node is already a MagicCardPhysical
 *                         (check it directly - its own finish/ownership)
 *                         instead of unconditionally assuming it's a
 *                         MagicCard printing and scanning
 *                         base.getPhysicalCards(), matching how
 *                         FieldGenuineCount4Aggregator already handles both
 *                         shapes. Collector's own rows are always MagicCard
 *                         printings so this never mattered there, but the
 *                         old code silently returned 0 for a group whose
 *                         child is a bare MagicCardPhysical never registered
 *                         in its base's own physical-copies list -
 *                         CardGroupTest#testContractOne caught this as a
 *                         leaf-vs-1-member-group mismatch
 *******************************************************************************/
package com.reflexit.magiccards.core.model.aggr;

import java.util.HashSet;

import com.reflexit.magiccards.core.model.AbstractMagicCard;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;

/**
 * Numerator for "count finishes separately" completion tracking: one slot per
 * (printing, finish) combination actually owned - any owned copy counts,
 * proxies included. See {@link FieldGenuineOwnUniqueByFinishAggregator} for
 * the proxy-excluding variant, matching the plain/genuine split
 * {@link FieldOwnUniqueAggregator}/{@link FieldGenuineOwnUniqueAggregator}
 * already have.
 */
public class FieldOwnUniqueByFinishAggregator extends FieldUniqueByFinishAggregator {
	public FieldOwnUniqueByFinishAggregator(MagicCardField field) {
		super(field);
	}

	@Override
	protected Object visitAbstractMagicCard(AbstractMagicCard card, Object data) {
		if (data == null)
			return 1;
		@SuppressWarnings("unchecked")
		HashSet<String> uniq = (HashSet<String>) data;
		if (card instanceof MagicCardPhysical) {
			MagicCardPhysical p = (MagicCardPhysical) card;
			if (qualifies(p)) {
				MagicCard base = p.getBase();
				String cardId = uniqueCardId(base);
				if (cardId != null)
					uniq.add(cardId + "|" + p.getFinish());
			}
			return 0;
		}
		MagicCard base = card.getBase();
		String cardId = uniqueCardId(base);
		if (cardId == null)
			return 0;
		for (MagicCardPhysical p : base.getPhysicalCards()) {
			if (qualifies(p)) {
				uniq.add(cardId + "|" + p.getFinish());
			}
		}
		return 0;
	}

	/** Which owned copies fill a finish slot - overridden to exclude proxies. */
	protected boolean qualifies(MagicCardPhysical p) {
		return p.isOwn();
	}
}
