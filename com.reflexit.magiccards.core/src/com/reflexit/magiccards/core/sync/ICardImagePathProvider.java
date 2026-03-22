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
package com.reflexit.magiccards.core.sync;

import java.io.File;

import com.reflexit.magiccards.core.model.IMagicCard;

/**
 * Core-side abstraction for resolving the local file path
 * where a card image should be stored.
 *
 * Implementations live in UI plugins, because only the UI
 * knows the correct folder structure, language rules,
 * edition mapping, and platform-specific quirks.
 *
 * Core code (CardCache, UpdateCardsFromWeb, etc.) must use
 * this interface instead of computing paths directly.
 */
public interface ICardImagePathProvider {

	/**
	 * Returns the full local file path where the image for the
	 * given card should be stored.
	 *
	 * @param card the card whose image path is requested
	 * @return a File pointing to the desired image location
	 */
	File getLocalImagePath(IMagicCard card);
}