/*******************************************************************************
 * Copyright (c) 2008 Alena Laskavaia.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Alena Laskavaia - initial API and implementation
 *
 *     Rémi Dutil (2026) - addNode() now takes the page directly, not a
 *                         PreferenceNode - see CardFilterDialog's own header
 *     Rémi Dutil (2026) - User Filter is no longer its own tab - now
 *                         setAllowsUserFilter(true), folding its fields into
 *                         Main Filter instead
 *******************************************************************************/
package com.reflexit.magiccards.ui.dialogs;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;

import com.reflexit.magiccards.ui.preferences.LocationFilterPreferencePage;

/**
 * Filter dialog for My Cards view
 */
public class MyCardsFilterDialog extends CardFilterDialog {
	public MyCardsFilterDialog(Shell parentShell, IPreferenceStore store) {
		super(parentShell, store);
		setAllowsUserFilter(true);
		addNode("locations", new LocationFilterPreferencePage(SWT.MULTI));
		super.addSavePage();
	}

	@Override
	protected void addSavePage() {
		// overload not add here
	}
}
