/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *******************************************************************************/
package com.reflexit.magiccards.ui.views.columns;

import com.reflexit.magiccards.core.model.DigitalSupport;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.abs.ICard;

/**
 * Whether a printing is digital: "Digital" for a digital-only printing (MTGO /
 * Arena only), "Also online" for a paper printing that is playable online too,
 * blank for a plain paper one. Read-only. Blank while the digital cards are
 * not enabled.
 */
public class DigitalColumn extends GenColumn {
	public DigitalColumn() {
		super(MagicCardField.DIGITAL, "Digital");
	}

	@Override
	public int getColumnWidth() {
		return 75;
	}

	@Override
	public String getColumnFullName() {
		return "Digital (MTGO / Arena)";
	}

	@Override
	public String getText(Object element) {
		return textOf(element);
	}

	/** The cell text for {@code element}; split out so it can be tested. */
	static String textOf(Object element) {
		if (!DigitalSupport.isEnabled() || !(element instanceof ICard))
			return "";
		if (DigitalSupport.isDigitalOnly(element))
			return "Digital";
		if (DigitalSupport.isOnline(element))
			return "Also online";
		return "";
	}
}
