/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk: own-unique-by-finish that ignores
 *                  proxy copies, matching FieldGenuineOwnUniqueAggregator's
 *                  relationship to FieldOwnUniqueAggregator
 *******************************************************************************/
package com.reflexit.magiccards.core.model.aggr;

import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;

/**
 * Like {@link FieldOwnUniqueByFinishAggregator} but a proxy copy does not
 * fill its finish slot - only a genuine owned copy of that finish counts.
 */
public class FieldGenuineOwnUniqueByFinishAggregator extends FieldOwnUniqueByFinishAggregator {
	public FieldGenuineOwnUniqueByFinishAggregator(MagicCardField field) {
		super(field);
	}

	@Override
	protected boolean qualifies(MagicCardPhysical p) {
		return p.isOwn() && !p.isProxy();
	}
}
