/*******************************************************************************
 * Copyright (c) 2008 Alena Laskavaia.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Alena Laskavaia - initial API and implementation
 *******************************************************************************/

/*
 * Contributors:
 *     Rémi Dutil (2026) - setAllowsMultipleFinishesPerRow(false): every row
 *                         here is a single owned copy with exactly one
 *                         Finish, so the Finish filter's "And" (exact match)
 *                         mode would never do anything
 *     Rémi Dutil (2026) - addNode() now takes the page directly, not a
 *                         PreferenceNode - see CardFilterDialog's own header
 *     Rémi Dutil (2026) - User Filter is no longer its own tab - now
 *                         setAllowsUserFilter(true), folding its fields into
 *                         Main Filter instead. No more need to override
 *                         addSavePage() either - it used to exist purely to
 *                         insert the "user" node between the base tabs and
 *                         "save", which no longer applies.
 */
package com.reflexit.magiccards.ui.dialogs;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.widgets.Shell;

/**
 * Filter dialog for Decks and Collections
 */
public class DeckFilterDialog extends CardFilterDialog {
	public DeckFilterDialog(Shell parentShell, IPreferenceStore store) {
		super(parentShell, store);
		setAllowsMultipleFinishesPerRow(false);
		setAllowsUserFilter(true);
	}
}
