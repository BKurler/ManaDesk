/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk: Collector's "Count Finishes
 *                  Separately" completion mode - the denominator, counting
 *                  one slot per (printing, finish) instead of one slot per
 *                  printing
 *******************************************************************************/
package com.reflexit.magiccards.core.model.aggr;

import java.util.HashSet;

import com.reflexit.magiccards.core.model.AbstractMagicCard;
import com.reflexit.magiccards.core.model.CardFinish;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;

/**
 * Like {@link FieldUniqueAggregator} but a printing contributes one slot per
 * finish it supports (nonfoil/foil/etched) instead of a single slot - the
 * denominator for "count finishes separately" completion tracking.
 * Ownership-agnostic: every finish a printing supports counts, whether or not
 * any copy of it is owned.
 */
public class FieldUniqueByFinishAggregator extends AbstractIntTransAggregator {
	public FieldUniqueByFinishAggregator(MagicCardField field) {
		super(field);
	}

	@Override
	protected Object visitAbstractMagicCard(AbstractMagicCard card, Object data) {
		if (data == null)
			return 1;
		@SuppressWarnings("unchecked")
		HashSet<String> uniq = (HashSet<String>) data;
		MagicCard base = card.getBase();
		String cardId = uniqueCardId(base);
		if (cardId == null)
			return 0;
		for (CardFinish finish : base.getSupportedFinishes()) {
			uniq.add(cardId + "|" + finish);
		}
		return 0;
	}

	protected String uniqueCardId(MagicCard base) {
		String cardId = base.getEnglishCardId();
		if (cardId == null)
			cardId = base.getCardId();
		return cardId;
	}

	@Override
	protected Object pre(Object group) {
		return new HashSet<String>();
	}

	@Override
	protected Object post(Object data) {
		if (data == null)
			return null;
		@SuppressWarnings("unchecked")
		HashSet<String> uniq = (HashSet<String>) data;
		return uniq.size();
	}
}
