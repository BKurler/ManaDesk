/*
 * Contributors:
 *     Rémi Dutil (2026) - startup bulk pre-download + "new sets available" prompt (runs the full update)
 *     Rémi Dutil (2026) - checkInitialDatabase(): there is no bundled card DB any
 *                         more; on first run prompt to download it, and repair a
 *                         DB that has lost most of its set files
 */

package com.reflexit.magiccards.ui.commands;

import java.util.Collection;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.Edition;
import com.reflexit.magiccards.core.model.Editions;
import com.reflexit.magiccards.core.model.xml.DbMultiFileCardStore;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;
import com.reflexit.magiccards.core.sync.CurrencyConvertor;
import com.reflexit.magiccards.core.sync.ParseScryFallSets;
import com.reflexit.magiccards.core.sync.ScryfallBulkCache;
import com.reflexit.magiccards.core.sync.WebUtils;
import com.reflexit.magiccards.ui.MagicUIActivator;

/**
 * "Check for Card Updates": ask Scryfall for the set list, and if it lists sets
 * we don't have, offer to run the full {@link UpdateDbHandler} update. Also
 * refreshes the currency rates. Runs automatically ~15 s after startup.
 */
public class CheckForUpdateDbHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		if (WebUtils.isWorkOffline()) {
			Display.getDefault().asyncExec(() -> MessageDialog.openInformation(MagicUIActivator.getShell(),
					"Work Offline", "Online updates are disabled. Turn off 'Work Offline' first."));
			return null;
		}
		doCheckForCardUpdates(true);
		return null;
	}

	/**
	 * Startup task: freshness-check Scryfall and download the Default Cards bulk
	 * file (no parse) so a later "Update Card Database" skips the download.
	 */
	public static void predownloadBulk() {
		new Job("Preparing card database") {
			@Override
			protected IStatus run(IProgressMonitor imonitor) {
				if (WebUtils.isWorkOffline() || MagicUIActivator.TRACE_TESTING || MagicUIActivator.isJunitRunning())
					return Status.OK_STATUS;
				try {
					if (ScryfallBulkCache.isRemoteBulkNewer()) {
						System.err.println("[ScryfallBulk] startup: pre-downloading updated card file...");
						ScryfallBulkCache.getDefaultCardsFile(ICoreProgressMonitor.NONE);
						System.err.println("[ScryfallBulk] startup: card file ready");
					} else {
						System.err.println("[ScryfallBulk] startup: card file already current");
					}
				} catch (Exception e) {
					System.err.println("[ScryfallBulk] startup: pre-download failed (" + e.getMessage() + ")");
					MagicUIActivator.log(e);
				}
				return Status.OK_STATUS;
			}
		}.schedule(5000);
	}

	/**
	 * Startup integrity check (there is no bundled card database any more):
	 * <ul>
	 * <li>no card database at all → prompt the user to download it from Scryfall
	 * (or, offline, point them at <em>Import Card Database from File…</em>);</li>
	 * <li>a database that has lost most of its set files → repair it in the
	 * background.</li>
	 * </ul>
	 */
	/** Set from {@code ApplicationWorkbenchAdvisor.postStartup()} - the splash is
	 *  gone and the workbench is interactive. Gates {@link #promptDownloadIfEmpty}
	 *  so the first-run dialog never pops over the splash screen. */
	private static volatile boolean workbenchReady = false;

	public static void checkInitialDatabase() {
		workbenchReady = true;
		promptDownloadIfEmpty();
		new Job("Checking card database") {
			@Override
			protected IStatus run(IProgressMonitor imonitor) {
				if (MagicUIActivator.TRACE_TESTING || MagicUIActivator.isJunitRunning())
					return Status.OK_STATUS;
				DbMultiFileCardStore db = (DbMultiFileCardStore) DataManager.getInstance().getMagicDBStore();
				db.initialize();
				if (db.isEmpty())
					return Status.OK_STATUS; // handled by promptDownloadIfEmpty()
				// integrity: a healthy update recorded how many set files it wrote;
				// if a big chunk have gone missing since, quietly rebuild.
				int good = UpdateDbHandler.lastGoodSetCount();
				if (good > 20 && db.loadedSetCount() < good - 10 && !WebUtils.isWorkOffline()) {
					asyncInfo("Some card data is missing - updating the card database in the background.");
					UpdateDbHandler.performUpdate();
				}
				return Status.OK_STATUS;
			}
		}.schedule(4000);
	}

	private static volatile long lastEmptyPrompt = 0L;

	/**
	 * If there is no card database (no {@code <DB>/*.xml}), put a modal prompt in
	 * front of the user offering to download it - a status-bar hint alone is too
	 * easy to miss. Safe to call from any thread; no-op until the workbench is up
	 * (see {@link #workbenchReady}); a 30 s cooldown keeps it from nagging when the
	 * user dismisses it and clicks around.
	 */
	public static void promptDownloadIfEmpty() {
		if (!workbenchReady || MagicUIActivator.TRACE_TESTING || MagicUIActivator.isJunitRunning())
			return;
		new Job("Checking card database") {
			@Override
			protected IStatus run(IProgressMonitor m) {
				DbMultiFileCardStore db = (DbMultiFileCardStore) DataManager.getInstance().getMagicDBStore();
				if (!db.isEmpty() || UpdateDbHandler.isRunning())
					return Status.OK_STATUS;
				synchronized (CheckForUpdateDbHandler.class) {
					if (System.currentTimeMillis() - lastEmptyPrompt < 30_000L)
						return Status.OK_STATUS;
					lastEmptyPrompt = System.currentTimeMillis();
				}
				final boolean offline = WebUtils.isWorkOffline();
				final boolean canDownload = !offline || ScryfallBulkCache.hasLocalBulk();
				final long mb = (offline || !canDownload) ? -1 : ScryfallBulkCache.remoteBulkSizeMB();
				Display.getDefault().asyncExec(() -> {
					if (!canDownload) {
						MessageDialog.openInformation(MagicUIActivator.getShell(), "Card Database",
								"ManaDesk has no card database yet.\n\n"
										+ "Connect to the internet and it will offer to download it, "
										+ "or use File ▸ Import Card Database from File…");
						return;
					}
					String size = mb > 0 ? " (about " + mb + " MB)" : "";
					if (MessageDialog.openQuestion(MagicUIActivator.getShell(), "Card Database",
							"ManaDesk needs to download the card database from Scryfall" + size
									+ ".\n\nDownload it now?"))
						UpdateDbHandler.performUpdate();
				});
				return Status.OK_STATUS;
			}
		}.schedule();
	}

	private static void asyncInfo(String msg) {
		Display.getDefault().asyncExec(
				() -> MessageDialog.openInformation(MagicUIActivator.getShell(), "Card Database", msg));
	}

	/** Auto-check ~15 s after startup (silent when there is nothing new). */
	public static void doCheckForCardUpdates() {
		doCheckForCardUpdates(false);
	}

	private static void doCheckForCardUpdates(final boolean verbose) {
		new Job("Checking for card updates...") {
			@Override
			public IStatus run(IProgressMonitor imonitor) {
				if (WebUtils.isWorkOffline() || MagicUIActivator.TRACE_TESTING || MagicUIActivator.isJunitRunning())
					return Status.OK_STATUS;
				try {
					ParseScryFallSets sets = new ParseScryFallSets();
					sets.loadSets(false);

					// keep the official edition list current
					for (Edition edition : sets.getAll())
						Editions.getInstance().addEdition(edition);
					Editions.getInstance().save();
					CurrencyConvertor.update();

					final Collection<Edition> newSets = sets.getNew();
					if (newSets.isEmpty()) {
						if (verbose)
							Display.getDefault().asyncExec(() -> MessageDialog.openInformation(
									MagicUIActivator.getShell(), "Card Updates", "Your set list is up to date."));
						return Status.OK_STATUS;
					}
					final boolean[] yes = new boolean[1];
					Display.getDefault().syncExec(() -> yes[0] = MessageDialog.openQuestion(MagicUIActivator.getShell(),
							"New Cards", "New sets are available:\n\n" + newSets
									+ "\n\nUpdate the card database now?"));
					if (yes[0])
						UpdateDbHandler.performUpdate();
				} catch (Exception e) {
					MagicUIActivator.log(e); // move on if set-list loading fails
				}
				return Status.OK_STATUS;
			}
		}.schedule();
	}

	@Override
	public void setEnabled(Object evaluationContext) {
		setBaseEnabled(true);
	}
}
