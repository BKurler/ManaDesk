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
 
package com.reflexit.magiccards.ui.utils;

import java.io.File;

import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCard;

public final class CardImageUI {

	private CardImageUI() {
	}

	public static File getLocalImageFile(IMagicCard card) {
		String path = ImageCreator.getInstance().createCardPath(card, false, false);
		return (path == null) ? null : new File(path);
	}

	public static IMagicCard buildTemporaryCard(String id, String set, String lang) {
		MagicCard mc = new MagicCard();
		mc.setCardId(id);
		mc.setSet(set);

		// Just pass through whatever the store uses
		// If missing, fall back to English
		if (lang == null || lang.isEmpty()) {
			lang = "English";
		}
		mc.setLanguage(lang);

		return mc;
	}
}