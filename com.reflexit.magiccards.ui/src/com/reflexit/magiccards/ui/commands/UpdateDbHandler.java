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
 *     Rémi Dutil (2026) - record LAST_PARSER_VERSION after a successful update
 *                         (see ParseScryFallChecklist#PARSER_VERSION) - lets
 *                         CheckForUpdateDbHandler notice "this data was
 *                         derived by an older version of our own parsing
 *                         logic" and prompt a refresh, instead of relying on
 *                         the user to remember to click Update
 *     Rémi Dutil (2026) - runs BackupHandler#createBackup() (the same
 *                         mechanism the manual "Backup" command uses - a zip
 *                         of the whole workspace) before the update, and
 *                         aborts rather than proceeding unprotected if it
 *                         fails - a major update overwrites every
 *                         <DB>/*.xml set file in place
 *                         (DbMultiFileCardStore#saveDirtySets, called from
 *                         downloadUpdates below) with no way back if the
 *                         parse goes wrong or the Scryfall data itself is
 *                         bad, and it's the same folder tree (decks,
 *                         collections, AND the card database all live under
 *                         the one workspace) this zip already covers whole -
 *                         no need for a second, DB-only backup mechanism of
 *                         its own. Runs synchronously, since this Job
 *                         already IS the background thread. Every update
 *                         path funnels through this one performUpdate(), so
 *                         this covers the manual "Update Card Database"
 *                         command, the startup checks, and the "new sets
 *                         available" prompt alike.
 *     Rémi Dutil (2026) - "Work Offline" removed: with no local card file and
 *                         no web, a friendly "web is not accessible" message
 *                         up front (before the backup); a no-web failure
 *                         mid-update shows the same message instead of the
 *                         raw exception. The manual "Update Card Database"
 *                         (performUpdate(true)) requires the web - it no
 *                         longer silently re-parses the same local card file;
 *                         repair / parser refresh / file import still rebuild
 *                         from the local file with no web.
 *     Rémi Dutil (2026) - the update-finished message offers "Support
 *                         ManaDesk..." when the optional monetization plug-in
 *                         is installed (SupportManaDesk); OK stays the default.
 *     Rémi Dutil (2026) - the manual update says "already up to date" (and
 *                         offers Rebuild anyway) when Scryfall has nothing new,
 *                         instead of re-processing the same card file; the
 *                         update-finished message's buttons are centred;
 *                         the "already up to date" question offers Support
 *                         ManaDesk... too (it is the most frequent message);
 *                         its default button is No.
 */

package com.reflexit.magiccards.ui.commands;

import java.io.IOException;
import java.util.Properties;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.ICardHandler;
import com.reflexit.magiccards.core.model.xml.DbMultiFileCardStore;
import com.reflexit.magiccards.core.model.xml.DbPricesMultiFileStore;
import com.reflexit.magiccards.core.sync.ParseScryFallChecklist;
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

	static final String WEB_NOT_ACCESSIBLE = "The web is not accessible right now, so the card database "
			+ "cannot be downloaded from Scryfall.\n\nCheck your internet connection and try again later, "
			+ "or use File ▸ Import Card Database from File…";

	/** How many {@code <DB>/*.xml} files a healthy update produced, so a later
	 *  startup can tell "database never downloaded" / "half the sets vanished"
	 *  from a normal load. 0 = unknown. */
	public static final String LAST_GOOD_SET_COUNT = "cardDb.lastGoodSetCount";

	public static int lastGoodSetCount() {
		return MagicUIActivator.getDefault().getPreferenceStore().getInt(LAST_GOOD_SET_COUNT);
	}

	/** The {@code ParseScryFallChecklist#PARSER_VERSION} the local card
	 *  database was last fully rebuilt with. 0 = never (a DB from before this
	 *  existed, or one that's never actually finished an update). */
	public static final String LAST_PARSER_VERSION = "cardDb.lastParserVersion";

	public static int lastParserVersion() {
		return MagicUIActivator.getDefault().getPreferenceStore().getInt(LAST_PARSER_VERSION);
	}

	/** True while an update job is scheduled or running. */
	public static boolean isRunning() {
		return running;
	}

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		// asked for explicitly: with no web there is nothing new to get - re-parsing
		// the same local card file would only pretend to update
		performUpdate(true);
		return null;
	}

	/** Schedule the update job (no-op if one is already running). With no web,
	 *  rebuilds from the already downloaded / imported card file - what a repair,
	 *  a parser-version refresh, an empty DB or a file import needs. */
	public static void performUpdate() {
		performUpdate(false);
	}

	/**
	 * @param requireWeb {@code true}: when Scryfall cannot be reached, say the web
	 *        is not accessible instead of rebuilding from the local card file
	 *        (unless a user-imported file is waiting to be parsed)
	 */
	public static void performUpdate(final boolean requireWeb) {
		synchronized (LOCK) {
			if (running)
				return;
			running = true;
		}
		Job job = new Job("Updating card database") {
			@Override
			public IStatus run(IProgressMonitor pm) {
				try {
					// no web is fine if a bulk file was already downloaded / imported -
					// the DB is then rebuilt from it; otherwise say so up front,
					// before backing anything up
					boolean canUseLocal = requireWeb ? ScryfallBulkCache.isLocalImportPending()
							: ScryfallBulkCache.hasLocalBulk();
					if (!canUseLocal && !ScryfallBulkCache.isReachable()) {
						asyncInfo(WEB_NOT_ACCESSIBLE);
						return Status.OK_STATUS;
					}
					// asked for explicitly but nothing new: say so instead of silently
					// re-processing the same card file (offer to rebuild anyway)
					if (requireWeb && isAlreadyUpToDate() && !askRebuildAnyway())
						return Status.OK_STATUS;
					pm.beginTask("Updating card database", 100);
					pm.subTask("Backing up your decks and collections…");
					try {
						BackupHandler.createBackup();
					} catch (IOException e) {
						MagicUIActivator.log(e);
						asyncInfo("Could not back up your decks and collections - update aborted.\n"
								+ "Check the Error Log for details.");
						return Status.OK_STATUS;
					}
					pm.worked(10);
					if (pm.isCanceled())
						return Status.CANCEL_STATUS;

					ICardHandler ch = DataManager.getCardHandler();
					final int rec = ch.downloadUpdates(null, new Properties(),
							new CoreMonitorAdapter(new SubProgressMonitor(pm, 75)));
					if (pm.isCanceled())
						return Status.CANCEL_STATUS;

					pm.subTask("Refreshing prices…");
					DbPricesMultiFileStore.getInstance().reloadPrices();
					pm.worked(10);
					pm.subTask("Relinking your collections…");
					DataManager.getInstance().reconcile();
					pm.worked(5);
					int sets = ((DbMultiFileCardStore) DataManager.getInstance().getMagicDBStore()).loadedSetCount();
					if (sets > 0) {
						MagicUIActivator.getDefault().getPreferenceStore().setValue(LAST_GOOD_SET_COUNT, sets);
						MagicUIActivator.getDefault().getPreferenceStore().setValue(LAST_PARSER_VERSION,
								ParseScryFallChecklist.PARSER_VERSION);
					}
					asyncExec(() -> {
						reloadMagicDbView();
						showUpdatedMessage("Card database updated (" + rec + " card records).");
					});
					return Status.OK_STATUS;
				} catch (InterruptedException e) {
					return Status.CANCEL_STATUS;
				} catch (Exception e) {
					if (WebUtils.isWebUnavailable(e)) {
						asyncInfo(WEB_NOT_ACCESSIBLE);
						return Status.OK_STATUS;
					}
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

	/**
	 * The update-finished message - with a "Support ManaDesk..." button when the
	 * (optional) support command is installed; OK stays the default button.
	 */
	private static void showUpdatedMessage(String msg) {
		if (!SupportManaDesk.isAvailable()) {
			centered(MessageDialog.INFORMATION, 0, msg, IDialogConstants.OK_LABEL).open();
			return;
		}
		MessageDialog dialog = centered(MessageDialog.INFORMATION, 0, msg + SUPPORT_LINE, IDialogConstants.OK_LABEL,
				SupportManaDesk.LABEL);
		if (dialog.open() == 1)
			SupportManaDesk.open();
	}

	private static final String SUPPORT_LINE = "\n\nManaDesk is free. If it is useful to you, you can support its development.";

	/** A message dialog whose buttons are centred; {@code defaultIndex} = the default button. */
	private static MessageDialog centered(int kind, int defaultIndex, String msg, String... buttons) {
		return new MessageDialog(MagicUIActivator.getShell(), "Update Card Database", null, msg, kind,
				defaultIndex, buttons) {
			@Override
			protected Control createButtonBar(Composite parent) {
				Control bar = super.createButtonBar(parent);
				if (bar.getLayoutData() instanceof GridData)
					((GridData) bar.getLayoutData()).horizontalAlignment = SWT.CENTER;
				return bar;
			}
		};
	}

	/**
	 * Nothing to do: the local card file is Scryfall's current one, the database
	 * was built from it with this parser version, and no set file is missing.
	 * (Scryfall republishes its file about once a day.)
	 */
	private static boolean isAlreadyUpToDate() {
		if (ScryfallBulkCache.isLocalImportPending() || !ScryfallBulkCache.hasLocalBulk()
				|| ScryfallBulkCache.isRemoteBulkNewer())
			return false;
		if (lastParserVersion() < ParseScryFallChecklist.PARSER_VERSION)
			return false;
		DbMultiFileCardStore db = (DbMultiFileCardStore) DataManager.getInstance().getMagicDBStore();
		int good = lastGoodSetCount();
		return !db.isEmpty() && (good == 0 || db.loadedSetCount() >= good - 10);
	}

	/**
	 * Yes / No (+ Support ManaDesk..., which opens the support dialog and does
	 * not rebuild). No is the default: nothing changed, so not rebuilding is
	 * the safe choice. The most frequent update message, so the support offer is
	 * shown here too.
	 *
	 * @return true to rebuild the (already current) database anyway
	 */
	private static boolean askRebuildAnyway() {
		final boolean[] yes = new boolean[1];
		Display d = PlatformUI.isWorkbenchRunning() ? PlatformUI.getWorkbench().getDisplay() : Display.getDefault();
		if (d == null || d.isDisposed())
			return false;
		d.syncExec(() -> {
			String msg = "Your card database is already up to date: Scryfall has not published new card data since "
					+ "your last update (it does about once a day).\n\nRebuild it anyway?";
			boolean support = SupportManaDesk.isAvailable();
			int choice = support
					? centered(MessageDialog.QUESTION, 1, msg + SUPPORT_LINE, IDialogConstants.YES_LABEL,
							IDialogConstants.NO_LABEL, SupportManaDesk.LABEL).open()
					: centered(MessageDialog.QUESTION, 1, msg, IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL)
							.open();
			yes[0] = choice == 0;
			if (support && choice == 2)
				SupportManaDesk.open();
		});
		return yes[0];
	}

	private static void asyncInfo(String msg) {
		asyncExec(() -> MessageDialog.openInformation(MagicUIActivator.getShell(), "Update Card Database", msg));
	}

	@Override
	public void setEnabled(Object evaluationContext) {
		setBaseEnabled(true);
	}
}
