/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk: completion % for Collector's "Count
 *                  Finishes Separately" mode - one (printing, finish)
 *                  combination per slot instead of one printing per slot
 *******************************************************************************/
package com.reflexit.magiccards.core.model.aggr;

import com.reflexit.magiccards.core.model.CardGroup;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.abs.ICardField;

/**
 * Like {@link FieldProggressAggregator} but both the denominator and the
 * numerator count finish slots ({@link MagicCardField#UNIQUE_COUNT_BY_FINISH}
 * / {@link MagicCardField#OWN_UNIQUE_BY_FINISH}) instead of printings.
 */
public class FieldProggressByFinishAggregator extends FieldProggressAggregator {
	public FieldProggressByFinishAggregator(ICardField field) {
		super(field);
	}

	@Override
	protected int getSetSize(CardGroup cardGroup) {
		return cardGroup.getInt(MagicCardField.UNIQUE_COUNT_BY_FINISH);
	}

	@Override
	public int getProgressSize(CardGroup cardGroup) {
		return cardGroup.getInt(MagicCardField.OWN_UNIQUE_BY_FINISH);
	}
}
