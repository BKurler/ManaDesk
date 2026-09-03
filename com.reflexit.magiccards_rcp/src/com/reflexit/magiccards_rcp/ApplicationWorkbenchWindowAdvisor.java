
/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
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
			// Independent of the "check for card updates" flow: bring the local
			// Scryfall bulk split up to date in the background so set updates are
			// fast.
			CheckForUpdateDbHandler.primeCardDatabaseSplit();
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
