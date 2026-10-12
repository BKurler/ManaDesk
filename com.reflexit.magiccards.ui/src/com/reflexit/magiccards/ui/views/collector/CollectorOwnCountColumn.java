/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *     Rémi Dutil (2026) - Collector "Own Count": proxy copies are excluded
 *                         unless "Count Proxies" is on, like the Progress and
 *                         price columns
 *******************************************************************************/
package com.reflexit.magiccards.ui.views.collector;

import com.reflexit.magiccards.core.model.AbstractMagicCard;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICardGroup;
import com.reflexit.magiccards.ui.views.columns.OwnCountColumn;

/**
 * "Own Count" in the Collector view: the copies you own - genuine ones only
 * while "Count Proxies" is off.
 */
public class CollectorOwnCountColumn extends OwnCountColumn {
	@Override
	public String getText(Object element) {
		if (CollectorView.isCountProxies())
			return super.getText(element);
		if (element instanceof ICardGroup && element instanceof AbstractMagicCard)
			return String.valueOf(((AbstractMagicCard) element).getGenuineOwnCount());
		if (element instanceof MagicCard)
			return String.valueOf(genuineOwnCount((MagicCard) element));
		return super.getText(element);
	}

	@Override
	public String getToolTipText(Object element) {
		if (CollectorView.isCountProxies())
			return super.getToolTipText(element);
		return "Copies you own, proxies excluded (see \"Count Proxies\")";
	}

	static int genuineOwnCount(MagicCard card) {
		int count = 0;
		for (MagicCardPhysical p : card.getPhysicalCards()) {
			if (p.isOwn() && !p.isProxy())
				count += p.getCount();
		}
		return count;
	}
}
