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
 *     Rémi Dutil - created for ManaDesk: handler of the "Support ManaDesk..."
 *                  command.
 *******************************************************************************/
package com.manadesk.monetization;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.handlers.HandlerUtil;

/** Opens the {@link SupportDialog} (Help menu, update-finished message, ...). */
public class SupportHandler extends AbstractHandler {
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		MonetizationManager.getInstance().refreshInBackground(false); // for next time; never blocks
		new SupportDialog(HandlerUtil.getActiveShellChecked(event)).open();
		return null;
	}
}
