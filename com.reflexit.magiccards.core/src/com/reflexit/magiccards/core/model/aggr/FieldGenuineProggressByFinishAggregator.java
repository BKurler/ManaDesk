/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk: completion % for Collector's "Count
 *                  Finishes Separately" mode, genuine copies only (proxies
 *                  don't fill a finish slot)
 *******************************************************************************/
package com.reflexit.magiccards.core.model.aggr;

import com.reflexit.magiccards.core.model.CardGroup;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.abs.ICardField;

/**
 * Like {@link FieldProggressByFinishAggregator} but the numerator is
 * {@link MagicCardField#GENUINE_OWN_UNIQUE_BY_FINISH} - a proxy copy of a
 * finish does not count toward completing that finish's slot.
 */
public class FieldGenuineProggressByFinishAggregator extends FieldProggressByFinishAggregator {
	public FieldGenuineProggressByFinishAggregator(ICardField field) {
		super(field);
	}

	@Override
	public int getProgressSize(CardGroup cardGroup) {
		return cardGroup.getInt(MagicCardField.GENUINE_OWN_UNIQUE_BY_FINISH);
	}
}
