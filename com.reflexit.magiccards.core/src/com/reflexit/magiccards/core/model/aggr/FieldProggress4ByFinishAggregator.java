/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk: completion % for Collector's "Count
 *                  Finishes Separately" mode, Progress4 variant - target is 4
 *                  copies of EACH (printing, finish) slot, so an etched-only
 *                  printing needs 4 and a nonfoil+foil printing needs 8, not
 *                  a flat 4 per printing regardless of finish variety
 *******************************************************************************/
package com.reflexit.magiccards.core.model.aggr;

import com.reflexit.magiccards.core.model.CardGroup;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.abs.ICardField;

/**
 * Like {@link FieldProggress4Aggregator} but the denominator counts finish
 * slots ({@link MagicCardField#UNIQUE_COUNT_BY_FINISH}, via the inherited
 * {@link FieldProggressByFinishAggregator#getSetSize}) instead of printings,
 * and the numerator is {@link MagicCardField#COUNT4_BY_FINISH} - each finish
 * capped at 4 separately instead of the whole printing capped at 4.
 */
public class FieldProggress4ByFinishAggregator extends FieldProggressByFinishAggregator {
	public FieldProggress4ByFinishAggregator(ICardField field) {
		super(field);
	}

	@Override
	public int getProgressSize(CardGroup cardGroup) {
		return cardGroup.getInt(MagicCardField.COUNT4_BY_FINISH);
	}

	@Override
	public int getTotal(CardGroup element) {
		return getSetSize(element) * 4;
	}
}
