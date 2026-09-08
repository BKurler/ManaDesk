/*
 * Contributors:
 *     Rémi Dutil (2026) - startup bulk pre-download + "new sets available" prompt (runs the full update)
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

import com.reflexit.magiccards.core.model.Edition;
import com.reflexit.magiccards.core.model.Editions;
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
