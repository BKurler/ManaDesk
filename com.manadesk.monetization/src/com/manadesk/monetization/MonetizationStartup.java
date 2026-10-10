/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil
 * All rights reserved.
 *
 * This file is NOT open-source.
 * Permission is granted to use this file ONLY as part of the ManaDesk application.
 * Modification, redistribution, or reuse of this file or its contents is prohibited.
 * You may NOT replace affiliate identifiers, ad URLs, or donation links.
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk: refreshes the hosted configuration in
 *                  the background after startup.
 *******************************************************************************/
package com.manadesk.monetization;

import org.eclipse.ui.IStartup;

/** Refreshes the hosted monetization configuration once the workbench is up. */
public class MonetizationStartup implements IStartup {
	@Override
	public void earlyStartup() {
		MonetizationManager.getInstance().refreshInBackground(false);
	}
}
