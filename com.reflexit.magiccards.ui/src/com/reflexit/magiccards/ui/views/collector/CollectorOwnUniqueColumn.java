/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *     Rémi Dutil (2026) - Collector "Own Unique": a card owned only as a proxy
 *                         does not count unless "Count Proxies" is on
 *******************************************************************************/
package com.reflexit.magiccards.ui.views.collector;

import com.reflexit.magiccards.core.model.AbstractMagicCard;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.abs.ICardGroup;
import com.reflexit.magiccards.ui.views.columns.OwnUniqueColumn;

/**
 * "Own Unique" in the Collector view: the different cards you own - counting
 * genuine copies only while "Count Proxies" is off.
 */
public class CollectorOwnUniqueColumn extends OwnUniqueColumn {
	@Override
	public String getText(Object element) {
		if (CollectorView.isCountProxies())
			return super.getText(element);
		if (element instanceof ICardGroup && element instanceof AbstractMagicCard)
			return String.valueOf(((AbstractMagicCard) element).getGenuineOwnUnique());
		if (element instanceof MagicCard)
			return CollectorOwnCountColumn.genuineOwnCount((MagicCard) element) > 0 ? "1" : "0";
		return super.getText(element);
	}
}
