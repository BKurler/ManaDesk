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
 *     Rémi Dutil - created for ManaDesk: plug-in activator of the monetization module.
 *******************************************************************************/
package com.manadesk.monetization;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * Activator of the ManaDesk monetization module (support links, and later
 * affiliate store links). Other plug-ins never depend on this one: they only
 * use the command id {@link #SUPPORT_COMMAND_ID}, so ManaDesk builds and runs
 * without it.
 */
public class MonetizationPlugin extends AbstractUIPlugin {
	public static final String PLUGIN_ID = "com.manadesk.monetization";
	/** "Support ManaDesk..." command (see plugin.xml). */
	public static final String SUPPORT_COMMAND_ID = "com.manadesk.monetization.support";

	private static MonetizationPlugin plugin;

	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	public static MonetizationPlugin getDefault() {
		return plugin;
	}

	static void log(int severity, String message, Throwable e) {
		MonetizationPlugin p = plugin;
		if (p != null)
			p.getLog().log(new Status(severity, PLUGIN_ID, message, e));
		else
			System.err.println("[" + PLUGIN_ID + "] " + message + (e == null ? "" : ": " + e));
	}

	static void warn(String message, Throwable e) {
		log(IStatus.WARNING, message, e);
	}
}
