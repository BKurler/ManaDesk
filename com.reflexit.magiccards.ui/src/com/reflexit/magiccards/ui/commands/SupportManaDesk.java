/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *     Rémi Dutil (2026) - bridge to the optional "Support ManaDesk..." command
 *                         (com.manadesk.monetization plug-in): knows only its
 *                         command id, so ManaDesk builds and runs without that
 *                         plug-in, and no link / identifier lives in EPL code.
 *******************************************************************************/
package com.reflexit.magiccards.ui.commands;

import org.eclipse.core.commands.Command;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.handlers.IHandlerService;

import com.reflexit.magiccards.ui.MagicUIActivator;

/**
 * Optional "Support ManaDesk..." entry points (e.g. the update-finished
 * message). The command is contributed by the monetization plug-in; when that
 * plug-in is absent {@link #isAvailable()} is {@code false} and callers simply
 * don't offer it.
 */
public final class SupportManaDesk {
	public static final String COMMAND_ID = "com.manadesk.monetization.support";
	public static final String LABEL = "Support ManaDesk...";

	private SupportManaDesk() {
	}

	/** {@code true} when the "Support ManaDesk..." command is installed. */
	public static boolean isAvailable() {
		try {
			if (!PlatformUI.isWorkbenchRunning())
				return false;
			ICommandService cs = PlatformUI.getWorkbench().getService(ICommandService.class);
			Command c = cs == null ? null : cs.getCommand(COMMAND_ID);
			return c != null && c.isDefined();
		} catch (Exception e) {
			return false;
		}
	}

	/** Opens the support dialog (UI thread). No-op when not available. */
	public static void open() {
		if (!isAvailable())
			return;
		try {
			IHandlerService hs = PlatformUI.getWorkbench().getService(IHandlerService.class);
			hs.executeCommand(COMMAND_ID, null);
		} catch (Exception e) {
			MagicUIActivator.log(e);
		}
	}
}
