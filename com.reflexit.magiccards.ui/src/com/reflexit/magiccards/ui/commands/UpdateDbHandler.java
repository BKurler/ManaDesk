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
 *     Rémi Dutil (2026) - single "Update Card Database" (Scryfall bulk), background job + cancel
 *     Rémi Dutil (2026) - offline is OK when a bulk file was already downloaded /
 *                         imported; record the set-file count for the startup
 *                         integrity check
 */

package com.reflexit.magiccards.ui.commands;

import java.util.Properties;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.ICardHandler;
import com.reflexit.magiccards.core.model.xml.DbMultiFileCardStore;
import com.reflexit.magiccards.core.model.xml.DbPricesMultiFileStore;
import com.reflexit.magiccards.core.sync.ScryfallBulkCache;
import com.reflexit.magiccards.core.sync.WebUtils;
import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.utils.CoreMonitorAdapter;
import com.reflexit.magiccards.ui.views.MagicDbView;

/**
 * "Update Card Database": one background job that downloads the current Scryfall
 * <em>Default Cards</em> bulk file (only if a newer one was published) and rebuilds
 * the whole local card database + prices from it. Cancellable from the progress area.
 */
public class UpdateDbHandler extends AbstractHandler {

	private static final Object LOCK = new Object();
	private static volatile boolean running;

	/** How many {@code <DB>/*.xml} files a healthy update produced, so a later
	 *  startup can tell "database never downloaded" / "half the sets vanished"
	 *  from a normal load. 0 = unknown. */
	public static final String LAST_GOOD_SET_COUNT = "cardDb.lastGoodSetCount";

	public static int lastGoodSetCount() {
		return MagicUIActivator.getDefault().getPreferenceStore().getInt(LAST_GOOD_SET_COUNT);
	}

	/** True while an update job is scheduled or running. */
	public static boolean isRunning() {
		return running;
	}

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		performUpdate();
		return null;
	}

	/** Schedule the update job (no-op if one is already running). */
	public static void performUpdate() {
		synchronized (LOCK) {
			if (running)
				return;
			running = true;
		}
		Job job = new Job("Updating card database") {
			@Override
			public IStatus run(IProgressMonitor pm) {
				try {
					// offline is fine only if a bulk file was already downloaded /
					// imported - then we can still rebuild the DB from it.
					if (WebUtils.isWorkOffline() && !ScryfallBulkCache.hasLocalBulk()) {
						asyncInfo("You are working offline and no card file has been downloaded yet.\n"
								+ "Turn off 'Work Offline', or use File ▸ Import Card Database from File…");
						return Status.OK_STATUS;
					}
					pm.beginTask("Updating card database", 100);
					ICardHandler ch = DataManager.getCardHandler();
					final int rec = ch.downloadUpdates(null, new Properties(),
							new CoreMonitorAdapter(new SubProgressMonitor(pm, 85)));
					if (pm.isCanceled())
						return Status.CANCEL_STATUS;

					pm.subTask("Refreshing prices…");
					DbPricesMultiFileStore.getInstance().reloadPrices();
					pm.worked(10);
					pm.subTask("Relinking your collections…");
					DataManager.getInstance().reconcile();
					pm.worked(5);
					int sets = ((DbMultiFileCardStore) DataManager.getInstance().getMagicDBStore()).loadedSetCount();
					if (sets > 0)
						MagicUIActivator.getDefault().getPreferenceStore().setValue(LAST_GOOD_SET_COUNT, sets);
					asyncExec(() -> {
						reloadMagicDbView();
						MessageDialog.openInformation(MagicUIActivator.getShell(), "Update Card Database",
								"Card database updated (" + rec + " card records).");
					});
					return Status.OK_STATUS;
				} catch (InterruptedException e) {
					return Status.CANCEL_STATUS;
				} catch (Exception e) {
					MagicUIActivator.log(e);
					asyncInfo("Could not update the card database:\n" + e.getMessage());
					return Status.OK_STATUS; // error already shown
				} finally {
					synchronized (LOCK) {
						running = false;
					}
				}
			}
		};
		job.setPriority(Job.LONG);
		// Not setUser(true): that pops a modal progress dialog. We want it in the
		// progress area (status bar + Progress view) with a cancel button, and -
		// now that the bulk merge no longer fires a per-set reload storm - the
		// update's own progress is what stays shown there.
		job.setUser(false);
		job.schedule();
	}

	private static void reloadMagicDbView() {
		IWorkbenchWindow win = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		IWorkbenchPage page = win == null ? null : win.getActivePage();
		IViewPart view = page == null ? null : page.findView(MagicDbView.ID);
		if (view instanceof MagicDbView)
			((MagicDbView) view).reloadData();
	}

	private static void asyncExec(Runnable r) {
		Display d = PlatformUI.isWorkbenchRunning() ? PlatformUI.getWorkbench().getDisplay() : Display.getDefault();
		if (d != null && !d.isDisposed())
			d.asyncExec(r);
	}

	private static void asyncInfo(String msg) {
		asyncExec(() -> MessageDialog.openInformation(MagicUIActivator.getShell(), "Update Card Database", msg));
	}

	@Override
	public void setEnabled(Object evaluationContext) {
		setBaseEnabled(true);
	}
}
