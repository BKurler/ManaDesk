/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk: Progress4's completion % with both
 *                  "Count Finishes Separately" and proxy exclusion active at
 *                  once - the numerator is GENUINE_COUNT4_BY_FINISH, the
 *                  denominator (via the inherited
 *                  FieldProggressByFinishAggregator#getSetSize) is still
 *                  UNIQUE_COUNT_BY_FINISH x4, same as the proxy-inclusive
 *                  variant - proxies only ever affect the numerator, never
 *                  how many slots exist
 *******************************************************************************/
package com.reflexit.magiccards.core.model.aggr;

import com.reflexit.magiccards.core.model.CardGroup;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.abs.ICardField;

/**
 * Like {@link FieldProggress4ByFinishAggregator} but the numerator is
 * {@link MagicCardField#GENUINE_COUNT4_BY_FINISH} - a proxy copy doesn't
 * fill its finish slot.
 */
public class FieldGenuineProggress4ByFinishAggregator extends FieldProggress4ByFinishAggregator {
	public FieldGenuineProggress4ByFinishAggregator(ICardField field) {
		super(field);
	}

	@Override
	public int getProgressSize(CardGroup cardGroup) {
		return cardGroup.getInt(MagicCardField.GENUINE_COUNT4_BY_FINISH);
	}
}
