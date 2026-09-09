
/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - startup: pre-download the Scryfall bulk file; splash tail
 *                         "Restoring decks and collections" progress
 *     Rémi Dutil (2026) - startup: checkInitialDatabase() (first-run download prompt)
 */

package com.reflexit.magiccards_rcp;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.equinox.internal.p2.core.helpers.ServiceHelper;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.operations.UpdateOperation;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.application.ActionBarAdvisor;
import org.eclipse.ui.application.IActionBarConfigurer;
import org.eclipse.ui.application.IWorkbenchWindowConfigurer;
import org.eclipse.ui.application.WorkbenchWindowAdvisor;

import com.reflexit.magicassistant.p2.P2Util;
import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.commands.CheckForUpdateDbHandler;
import com.reflexit.magiccards.ui.commands.UpdateHandler;
import com.reflexit.magiccards.ui.preferences.PreferenceConstants;

public class ApplicationWorkbenchWindowAdvisor extends WorkbenchWindowAdvisor {
	private static final String JUSTUPDATED = "justUpdated";

	public ApplicationWorkbenchWindowAdvisor(IWorkbenchWindowConfigurer configurer) {
		super(configurer);
	}

	@Override
	public ActionBarAdvisor createActionBarAdvisor(IActionBarConfigurer configurer) {
		return new ApplicationActionBarAdvisor(configurer);
	}

	@Override
	public void preWindowOpen() {
		IWorkbenchWindowConfigurer configurer = getWindowConfigurer();
		configurer.setInitialSize(new Point(1600, 900));
		configurer.setShowCoolBar(false);
		configurer.setShowStatusLine(true);
		configurer.setShowProgressIndicator(true);
	}

	@Override
	public void postWindowOpen() {
		try {
			installSoftwareUpdate();
			checkForCardUpdates();
			// Freshness-check + pre-download the Scryfall bulk file in the
			// background so a later "Update Card Database" skips the download.
			CheckForUpdateDbHandler.predownloadBulk();
			// The first-run "download the card database" prompt is fired from
			// ApplicationWorkbenchAdvisor.postStartup() - after the splash closes.
		} catch (Throwable e) {
			Activator.log(e);
		}

		hookWorkbenchFolderPatching();

		// existing logic: hide selection view
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		if (window != null) {
			IWorkbenchPage page = window.getActivePage();
			if (page != null) {
				IViewReference ref = page.findViewReference("com.reflexit.magiccards.ui.gallery.GallerySelectionView");
				if (ref != null) {
					page.hideView(ref);
				}
			}
		}

		MASplashHandler.reportStartupTail("Restoring views…", 0.10);
		restoreDeckFamilyIcons();
		MASplashHandler.reportStartupTail("Loading card lists…", 0.35);
		drainInitialCardLoads();
		MASplashHandler.reportStartupTail("Finishing…", 0.95);
	}

	/**
	 * Each restored card view finishes its first data load on a background job and
	 * then rebuilds its (large) viewer on the UI thread. Left alone, that burst of
	 * refreshes lands the instant the splash closes and briefly freezes the fresh
	 * window. Pump it here - we are still under the splash screen (see
	 * {@code MASplashHandler}) - so the window is settled when it appears. Bounded
	 * so a stuck load can never hold startup.
	 */
	private void drainInitialCardLoads() {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		Shell shell = window == null ? null : window.getShell();
		Display display = (shell == null || shell.isDisposed()) ? null : shell.getDisplay();
		if (display == null) {
			return;
		}
		long start = System.currentTimeMillis();
		long deadline = start + 6000L;
		int idle = 0;
		while (System.currentTimeMillis() < deadline) {
			// keep the splash bar drifting through the tail slice while we pump
			double frac = 0.35 + 0.55 * (System.currentTimeMillis() - start) / 6000.0;
			MASplashHandler.reportStartupTail(null, frac);
			if (display.readAndDispatch()) {
				idle = 0;
				continue;
			}
			if (!cardLoadJobsActive() && ++idle >= 3) {
				break;
			}
			try {
				Thread.sleep(40L);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	private static boolean cardLoadJobsActive() {
		for (Job j : Job.getJobManager().find(null)) {
			int state = j.getState();
			if (state != Job.RUNNING && state != Job.WAITING) {
				continue;
			}
			String name = j.getName();
			if (name != null && name.startsWith("Loading cards for")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * On startup Eclipse restores every deck/sideboard/extra tab lazily: only the
	 * tab that ends up on top of its stack actually gets createPartControl()/
	 * activate() called, so only that tab recomputes its icon/name. Every other
	 * restored tab keeps showing whatever icon was cached in the previous
	 * session's workbench layout until the user clicks into it. Force all of
	 * them to materialize once, right after the window opens, so every tab's
	 * icon is correct without requiring a click.
	 * <p>
	 * This runs while the splash screen is still up (see {@code MASplashHandler}).
	 * Each {@code getView(true)} synchronously builds a deck view and kicks its
	 * data-load job; pump the event queue between them so the splash keeps
	 * painting and the burst of restored views does not land on the UI thread all
	 * at once.
	 */
	private void restoreDeckFamilyIcons() {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		if (window == null) {
			return;
		}
		IWorkbenchPage page = window.getActivePage();
		if (page == null) {
			return;
		}
		Display display = window.getShell() != null ? window.getShell().getDisplay() : Display.getCurrent();
		java.util.List<IViewReference> decks = new ArrayList<>();
		for (IViewReference ref : page.getViewReferences())
			if (com.reflexit.magiccards.ui.views.lib.DeckView.ID.equals(ref.getId()))
				decks.add(ref);
		int done = 0;
		for (IViewReference ref : decks) {
			done++;
			// Label the deck we are about to materialize, not the last one we
			// finished: getView(true) below can block for a moment on that
			// deck's first data load, and a counter that already reads "(5/12)"
			// looks like work in progress instead of a freeze at "(4/12)".
			MASplashHandler.reportStartupTail(
					"(" + done + "/" + decks.size() + ")  Restoring decks and collections",
					0.10 + 0.20 * done / Math.max(1, decks.size()));
			if (display != null) {
				display.readAndDispatch();
			}
			ref.getView(true);
			if (display != null) {
				for (int i = 0; i < 10 && display.readAndDispatch(); i++) {
					// flush pending paints / async work before the next view
				}
			}
		}
	}

	private void hookWorkbenchFolderPatching() {
	    // Just run one async scan after the window is open
	    scheduleScanAndPatch();
	}
	

	private void scheduleScanAndPatch() {
		Display display = Display.getDefault();
		if (display == null || display.isDisposed()) {
			return;
		}

		display.asyncExec(() -> {
			IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
			if (window == null) {
				return;
			}
			Shell shell = window.getShell();
			if (shell == null || shell.isDisposed()) {
				return;
			}

			List<CTabFolder> folders = new ArrayList<>();
			findCTabFolders(shell, folders);

			for (CTabFolder folder : folders) {
				if (isWorkbenchFolder(folder)) {
					patchFolder(folder);
				}
			}
		});
	}

	// ------------------------------------------------------------------------
	// Detection: is this CTabFolder a Workbench stack?
	// ------------------------------------------------------------------------

	/**
	 * A CTabFolder is considered a Workbench stack if somewhere in its subtree
	 * there is a ContributedPartRenderer$1 composite (E4 compatibility renderer).
	 */
	private boolean isWorkbenchFolder(CTabFolder folder) {
		return containsContributedPartRenderer(folder);
	}

	private boolean containsContributedPartRenderer(Composite root) {
		for (Control child : root.getChildren()) {
			String name = child.getClass().getName();
			if (name.contains("ContributedPartRenderer")) {
				return true;
			}
			if (child instanceof Composite) {
				if (containsContributedPartRenderer((Composite) child)) {
					return true;
				}
			}
		}
		return false;
	}

	// ------------------------------------------------------------------------
	// Patch: enforce DPI-scaled tab height on this folder instance
	// ------------------------------------------------------------------------

	private void patchFolder(CTabFolder folder) {
		if (folder == null || folder.isDisposed())
			return;
		if (folder.getData("md_patchedHeight") != null)
			return;

		folder.setData("md_patchedHeight", Boolean.TRUE);

		Display display = folder.getDisplay();
		int dpiY = display.getDPI().y;
		int minHeight = Math.max(28, dpiY / 5);  

		folder.setTabHeight(minHeight);
		folder.setSimple(false);

		// Re-apply on every paint of this folder so new tabs also get the height
		folder.addListener(SWT.Paint, e -> {
			if (!folder.isDisposed()) {
				if (folder.getTabHeight() != minHeight) {
					folder.setTabHeight(minHeight);
				}
			}
		});
	}

	// ------------------------------------------------------------------------
	// Utility: find all CTabFolders under the Workbench shell
	// ------------------------------------------------------------------------

	private void findCTabFolders(Control control, List<CTabFolder> result) {
		if (control instanceof CTabFolder) {
			result.add((CTabFolder) control);
		}
		if (control instanceof Composite) {
			for (Control child : ((Composite) control).getChildren()) {
				findCTabFolders(child, result);
			}
		}
	}

	private void checkForCardUpdates() {
		final boolean updates = MagicUIActivator.getDefault().getPreferenceStore()
				.getBoolean(PreferenceConstants.CHECK_FOR_CARDS);
		if (updates == false || MagicUIActivator.TRACE_TESTING || MagicUIActivator.isJunitRunning())
			return;
		new Job("Checking for Card Update") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				if (updates == false || MagicUIActivator.TRACE_TESTING || MagicUIActivator.isJunitRunning())
					return Status.CANCEL_STATUS;
				CheckForUpdateDbHandler.doCheckForCardUpdates();
				return Status.OK_STATUS;
			}
		}.schedule(1000 * 15 * 1);
	}

	protected void installSoftwareUpdate() {
		final boolean updates = MagicUIActivator.getDefault().getPreferenceStore()
				.getBoolean(PreferenceConstants.CHECK_FOR_UPDATES);
		if (updates == false || MagicUIActivator.TRACE_TESTING || MagicUIActivator.isJunitRunning())
			return;
		final IProvisioningAgent agent = (IProvisioningAgent) ServiceHelper
				.getService(Activator.getDefault().getBundle().getBundleContext(), IProvisioningAgent.SERVICE_NAME);
		if (agent == null) {
			Activator.log(new Status(IStatus.ERROR, Activator.PLUGIN_ID,
					"No provisioning agent found.  This application is not set up for updates."));
		}
		// XXX if we're restarting after updating, don't check again.
		final IPreferenceStore prefStore = Activator.getDefault().getPreferenceStore();
		if (prefStore.getBoolean(JUSTUPDATED)) {
			prefStore.setValue(JUSTUPDATED, false);
			return;
		}
		new Job("Checking for Software Update") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				if (updates == false || MagicUIActivator.TRACE_TESTING || MagicUIActivator.isJunitRunning())
					return Status.CANCEL_STATUS;
				monitor.beginTask("Checking for application updates...", 100);
				IStatus updateStatus = P2Util.checkForUpdates(agent, new SubProgressMonitor(monitor, 50), false);
				if (updateStatus.getCode() != UpdateOperation.STATUS_NOTHING_TO_UPDATE) {
					if (updateStatus.getSeverity() != IStatus.ERROR) {
						// update is available
						PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
							@Override
							public void run() {
								Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
								if (MessageDialog.openQuestion(shell, "Updates",
										"New software update is available, would you like to install it?")) {
									new UpdateHandler().execute(null);
								}
							}
						});
					}
				}
				monitor.done();
				return Status.OK_STATUS;
			}
		}.schedule(1000 * 60 * 5);
	}
}
