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
import com.reflexit.magiccards.core.sync.ICardImagePathProvider;

/**
 * UI-side implementation of ICardImagePathProvider.
 *
 * Delegates to ImageCreator, which is the canonical source of truth
 * for card image folder structure, language, edition mapping, and
 * platform-specific quirks.
 */
public class UICardImagePathProvider implements ICardImagePathProvider {

	@Override
	public File getLocalImagePath(IMagicCard card) {
		String path = ImageCreator.getInstance().createCardPath(card, false, false);
		return (path == null) ? null : new File(path);
	}
}