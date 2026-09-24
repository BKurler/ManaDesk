/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk: COLOR/COLOR_IDENTITY/
 *                  COLOR_IDENTITY_EXTENDED no longer collide to "*" for a
 *                  Collector Name-group spanning several printings of the
 *                  same card
 *     Rémi Dutil (2026) - visitIterable(): parse each printing's own
 *                         cost-string SEPARATELY (Colors#getColorPresense(),
 *                         same call a single leaf card's own value goes
 *                         through) and union the resulting color-tag sets,
 *                         instead of concatenating the raw strings together
 *                         first - Colors#getColorPresense() sniffs the FIRST
 *                         character of whatever it's given to decide between
 *                         parsing a cost-string ("{W}{U}") and scanning
 *                         natural-language oracle text for color mentions;
 *                         concatenating fragments from several printings
 *                         made that decision (and everything downstream of
 *                         it) depend on which printing's value happened to
 *                         land first, silently dropping colors that came
 *                         after a mismatched fragment
 *******************************************************************************/
package com.reflexit.magiccards.core.model.aggr;

import java.util.Iterator;
import java.util.LinkedHashSet;

import com.reflexit.magiccards.core.model.Colors;
import com.reflexit.magiccards.core.model.abs.ICard;
import com.reflexit.magiccards.core.model.abs.ICardField;

public class ColorUnionAggregator extends AbstractGroupAggregator {
	public ColorUnionAggregator(ICardField field) {
		super(field);
	}

	@Override
	public Object visitIterable(Iterable group, Object data) {
		LinkedHashSet<String> union = new LinkedHashSet<>();
		for (Iterator<ICard> iterator = group.iterator(); iterator.hasNext();) {
			ICard object = iterator.next();
			Object value = object.get(field);
			if (value != null)
				Colors.getColorPresense(value.toString(), union);
		}
		StringBuilder combined = new StringBuilder();
		for (String tag : Colors.sortTags(union))
			combined.append('{').append(tag).append('}');
		return combined.toString();
	}
}
