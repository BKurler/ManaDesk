/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.ui.commands;

import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;

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
import com.reflexit.magiccards.core.MagicException;
import com.reflexit.magiccards.core.model.Edition;
import com.reflexit.magiccards.core.model.Editions;
import com.reflexit.magiccards.core.model.ICardHandler;
import com.reflexit.magiccards.core.monitor.SubCoreProgressMonitor;
import com.reflexit.magiccards.core.sync.CurrencyConvertor;
import com.reflexit.magiccards.core.sync.ParseScryFallSets;
import com.reflexit.magiccards.core.sync.ScryfallBulkCache;
import com.reflexit.magiccards.core.sync.WebUtils;
import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.utils.CoreMonitorAdapter;

public class CheckForUpdateDbHandler extends AbstractHandler {
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.core.commands.IHandler#execute(org.eclipse.core.commands.
	 * ExecutionEvent)
	 */
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		if (WebUtils.isWorkOffline()) {
			Display.getCurrent().asyncExec(new Runnable() {
				@Override
				public void run() {
					// TODO Auto-generated method stub
					MessageDialog.openInformation(MagicUIActivator.getShell(), "Disabled",
							"Online updates are disabled");
				}
			});
			return null;
		}
		doCheckForCardUpdates();
		return null;
	}

	/**
	 * Independent startup task: bring the local Scryfall bulk split up to date -
	 * download the "Default Cards" bulk file if Scryfall published a newer one,
	 * then split it into one file per set - so later set updates are just a file
	 * read. Runs in its own background job; no-op when working offline.
	 */
	public static void primeCardDatabaseSplit() {
		new Job("Preparing card database") {
			@Override
			protected IStatus run(IProgressMonitor imonitor) {
				if (WebUtils.isWorkOffline()) {
					System.err.println("[ScryfallBulk] startup: offline, card-data split not checked");
					return Status.OK_STATUS;
				}
				System.err.println("[ScryfallBulk] startup: checking card-data split...");
				try {
					ScryfallBulkCache.ensureSplitAll(new CoreMonitorAdapter(imonitor));
					System.err.println("[ScryfallBulk] startup: card-data split ready");
				} catch (Exception e) {
					MagicUIActivator.log(e);
				}
				return Status.OK_STATUS;
			}
		}.schedule(5000);
	}

	public static void doCheckForCardUpdates() {
		new Job("Checking for cards updates...") {
			@Override
			public IStatus run(IProgressMonitor imonitor) {
				final CoreMonitorAdapter monitor = new CoreMonitorAdapter(imonitor);
				monitor.beginTask("Checking for cards updates...", 110);
				try {
					final ICardHandler handler = DataManager.getCardHandler();

					ParseScryFallSets sets = new ParseScryFallSets();
					sets.loadSets(false);

					final Collection<Edition> newSets = sets.getNew();

					if (newSets.size() > 0) {
						Editions.getInstance().save();
						final boolean result[] = new boolean[1];
						Display.getDefault().syncExec(new Runnable() {
							@Override
							public void run() {
								if (MessageDialog.openQuestion(null, "New Cards", "New sets are available: " + newSets
										+ ". Would you like to download them now?")) {
									result[0] = true;
								}
							}
						});
						if (result[0]) {
							int k = newSets.size();
							for (Iterator iterator = newSets.iterator(); iterator.hasNext();) {
								Edition edition = (Edition) iterator.next();

								// Add / update the edition in the official list
								Editions.getInstance().addEdition(edition);
								try {
									// Download the cards
									handler.downloadUpdates(edition.getName(), new Properties(),
											new SubCoreProgressMonitor(monitor, 60 / k));
								} catch (MagicException e) {
									MagicUIActivator.log(e);
								} catch (InterruptedException e) {
									monitor.setCanceled(true);
								}
								if (monitor.isCanceled())
									break;
							}
						}
					} else {
						Display.getDefault().syncExec(new Runnable() {
							@Override
							public void run() {
								MessageDialog.openInformation(null, "New Cards", "No New sets found");
							}
						});

					}
					if (monitor.isCanceled())
						return Status.CANCEL_STATUS;

					// Force and full update of the edition list 
					final Collection<Edition> allSets = sets.getAll();
					for (Iterator iterator = allSets.iterator(); iterator.hasNext();) {
						Edition edition = (Edition) iterator.next();

						// Update the edition in the official list
						Editions.getInstance().addEdition(edition);
					}

					Editions.getInstance().save();

					CurrencyConvertor.update();
				} catch (Exception e) {
					MagicUIActivator.log(e); // move on if exception via set
												// loading
				}
				if (monitor.isCanceled())
					return Status.CANCEL_STATUS;
				monitor.done();
				return Status.OK_STATUS;
			}
		}.schedule();
	}

	@Override
	public void setEnabled(Object evaluationContext) {
		setBaseEnabled(true);
	}
}
