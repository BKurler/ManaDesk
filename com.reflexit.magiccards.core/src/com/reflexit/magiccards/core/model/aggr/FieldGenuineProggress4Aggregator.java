/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk: Progress4's completion %, proxies
 *                  excluded - same relationship FieldGenuineProggressAggregator
 *                  has to FieldProggressAggregator, one level down at the
 *                  4-copies-per-playset target instead of 1
 *******************************************************************************/
package com.reflexit.magiccards.core.model.aggr;

import com.reflexit.magiccards.core.model.CardGroup;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.abs.ICardField;

/**
 * Like {@link FieldProggress4Aggregator} but the numerator is
 * {@link MagicCardField#GENUINE_COUNT4} - a proxy copy doesn't count toward
 * playset completion.
 */
public class FieldGenuineProggress4Aggregator extends FieldProggress4Aggregator {
	public FieldGenuineProggress4Aggregator(ICardField field) {
		super(field);
	}

	@Override
	public int getProgressSize(CardGroup cardGroup) {
		return cardGroup.getInt(MagicCardField.GENUINE_COUNT4);
	}
}
