
/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - startup: pre-download the Scryfall bulk file; splash tail
 *                         "Restoring decks and collections" progress
 *     Rémi Dutil (2026) - startup: checkInitialDatabase() (first-run download prompt)
 *     Rémi Dutil (2026) - startup: drainInitialCardLoads() now shows a live
 *                         "(N left: <deck>)" count instead of a frozen caption
 *     Rémi Dutil (2026) - startup: restoreDeckFamilyIcons() only force-materializes
 *                         non-active deck/collection tabs for their icon - their
 *                         card-list load runs at background priority
 *                         (backgroundLoadHint) and no longer holds up the splash
 *     Rémi Dutil (2026) - startup: drainInitialCardLoads()'s idle counter is now
 *                         driven purely by "is there still real work pending",
 *                         not by whether the SWT event queue happened to be
 *                         empty on a given tick - a live workbench almost always
 *                         has something to dispatch, so the old check never
 *                         advanced and the loop ran to the full 6s deadline even
 *                         once the real jobs had long finished
 *     Rémi Dutil (2026) - startup: restoreDeckFamilyIcons()'s splash caption
 *                         resolves the deck/collection's real name from the
 *                         model (elementOf()/nameOf()), not ref.getPartName() -
 *                         that is only the tab's static pre-materialization
 *                         title, so every tab read "Restoring deck "Deck""
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

		trace("postWindowOpen: about to call restoreDeckFamilyIcons()");
		MASplashHandler.reportStartupTail("Restoring views…", 0.10);
		restoreDeckFamilyIcons();
		trace("postWindowOpen: restoreDeckFamilyIcons() returned, about to call drainInitialCardLoads()");
		MASplashHandler.reportStartupTail("Loading card lists…", 0.35);
		drainInitialCardLoads();
		trace("postWindowOpen: drainInitialCardLoads() returned - done");
		MASplashHandler.reportStartupTail("Finishing…", 0.95);
	}

	/** Flip to {@code false} to silence - temporary, for diagnosing "the splash
	 *  text never seems to update" reports (visible with {@code -consoleLog}). */
	private static final boolean TRACE = false;

	private static void trace(String msg) {
		if (TRACE) {
			System.err.println("[Startup] " + msg);
		}
	}

	private static String stateName(int jobState) {
		switch (jobState) {
		case Job.RUNNING:
			return "RUNNING";
		case Job.WAITING:
			return "WAITING";
		case Job.SLEEPING:
			return "SLEEPING";
		case Job.NONE:
			return "NONE";
		default:
			return String.valueOf(jobState);
		}
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
			trace("drainInitialCardLoads: no display (window=" + (window == null ? "null" : "ok") + ", shell="
					+ (shell == null ? "null" : (shell.isDisposed() ? "disposed" : "ok")) + ") - returning immediately");
			return;
		}
		if (TRACE) {
			trace("drainInitialCardLoads: starting. ALL jobs known to the JobManager right now:");
			for (Job j : Job.getJobManager().find(null)) {
				trace("  - \"" + j.getName() + "\"  state=" + stateName(j.getState()) + "  priority=" + j.getPriority());
			}
		}
		long start = System.currentTimeMillis();
		long deadline = start + 6000L;
		int idle = 0;
		int lastShownCount = -1;
		long lastTextUpdate = 0L;
		int poll = 0;
		while (System.currentTimeMillis() < deadline) {
			long now = System.currentTimeMillis();
			// keep the splash bar drifting through the tail slice while we pump
			double frac = 0.35 + 0.55 * (now - start) / 6000.0;
			List<String> pending = activeCardLoadJobNames();
			if (TRACE && (++poll % 10) == 1) { // every ~400ms of polling, not every 40ms tick
				trace("drainInitialCardLoads: t=" + (now - start) + "ms pending=" + pending + " idle=" + idle);
			}
			// refresh the caption whenever the remaining count changes, and at
			// least twice a second regardless - a static "Loading card lists..."
			// for 6 straight seconds reads as a hang even while it is working
			if (pending.size() != lastShownCount || now - lastTextUpdate >= 500L) {
				String msg = pending.isEmpty() ? null
						: "Loading card lists… (" + pending.size() + " left: " + pending.get(0) + ")";
				MASplashHandler.reportStartupTail(msg, frac);
				lastShownCount = pending.size();
				lastTextUpdate = now;
			} else {
				MASplashHandler.reportStartupTail(null, frac);
			}
			// idle-tracking is driven purely by "is there still real work
			// pending", NOT by whether the SWT event queue happened to be
			// empty on this tick. A live workbench window almost always has
			// SOMETHING to dispatch (caret blink, tooltips, the splash bar's
			// own repaint), so display.readAndDispatch() returning true on
			// basically every tick used to starve this counter entirely and
			// ran the loop all the way to the 6s deadline even when the real
			// jobs had finished in well under a second - a debug trace showed
			// pending already [] at t=114ms while idle was still 0 past
			// t=900ms.
			if (pending.isEmpty()) {
				if (++idle >= 3) {
					trace("drainInitialCardLoads: nothing pending for " + idle + " consecutive polls at t="
							+ (now - start) + "ms - exiting");
					break;
				}
			} else {
				idle = 0;
			}
			// Pump the UI queue for a short, bounded burst so the splash
			// keeps painting, then always take a small fixed pause - this
			// also gives every poll a real, roughly-consistent time budget
			// instead of spinning as fast as the CPU allows whenever the
			// queue is chatty (which is what made the old readAndDispatch()
			// check above never fall through to the idle logic).
			long pumpUntil = now + 20L;
			while (System.currentTimeMillis() < pumpUntil && display.readAndDispatch()) {
				// draining
			}
			try {
				Thread.sleep(15L);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
		if (System.currentTimeMillis() >= deadline) {
			trace("drainInitialCardLoads: hit the 6s deadline with pending=" + activeCardLoadJobNames());
		} else {
			trace("drainInitialCardLoads: finished after " + (System.currentTimeMillis() - start) + "ms total");
		}
	}

	/** The tab-startup jobs a restored deck/collection tab actually runs, in
	 *  order: {@code "Initializing <location>"} ({@code LibraryEventListener} -
	 *  waits for the library, resolves the CardCollection, then triggers the
	 *  refresh below) THEN {@code "Loading cards for <name>"}
	 *  ({@code AbstractMagicCardsListControl} - reads/resolves the actual card
	 *  list). Both prefixes must be tracked - the first one is what is usually
	 *  still running during "Loading card lists...": watching only the second
	 *  name left {@code pending} permanently empty and the caption frozen.
	 *  Background-priority loads (deck/collection tabs the platform did not
	 *  already have active - see {@code backgroundLoadHint}) are deliberately
	 *  excluded: they keep loading, just not as something we hold the splash
	 *  for. */
	private static final String[] CARD_LOAD_JOB_PREFIXES = { "Initializing ", "Loading cards for " };

	private static List<String> activeCardLoadJobNames() {
		List<String> names = new ArrayList<>();
		for (Job j : Job.getJobManager().find(null)) {
			int state = j.getState();
			if (state != Job.RUNNING && state != Job.WAITING) {
				continue;
			}
			if (j.getPriority() == Job.DECORATE) {
				continue;
			}
			String name = j.getName();
			if (name == null) {
				continue;
			}
			for (String prefix : CARD_LOAD_JOB_PREFIXES) {
				if (name.startsWith(prefix)) {
					names.add(name.substring(prefix.length()));
					break;
				}
			}
		}
		return names;
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
			trace("restoreDeckFamilyIcons: no active workbench window - returning immediately");
			return;
		}
		IWorkbenchPage page = window.getActivePage();
		if (page == null) {
			trace("restoreDeckFamilyIcons: no active page - returning immediately");
			return;
		}
		Display display = window.getShell() != null ? window.getShell().getDisplay() : Display.getCurrent();
		java.util.List<IViewReference> decks = new ArrayList<>();
		for (IViewReference ref : page.getViewReferences())
			if (com.reflexit.magiccards.ui.views.lib.DeckView.ID.equals(ref.getId()))
				decks.add(ref);
		trace("restoreDeckFamilyIcons: found " + decks.size() + " DeckView tab(s) among "
				+ page.getViewReferences().length + " total view reference(s); display="
				+ (display == null ? "NULL" : "ok"));
		if (decks.isEmpty()) {
			// nothing to restore - say so explicitly rather than leaving the
			// generic caption from before this method ran up on the splash
			MASplashHandler.reportStartupTail("Restoring views… (no deck tabs to restore)", 0.12);
			return;
		}
		long methodStart = System.currentTimeMillis();
		int done = 0;
		for (IViewReference ref : decks) {
			done++;
			boolean alreadyActive = ref.getView(false) != null;
			trace("restoreDeckFamilyIcons: [" + done + "/" + decks.size() + "] id=" + ref.getId() + " secId="
					+ ref.getSecondaryId() + " partName=" + ref.getPartName() + " alreadyActive=" + alreadyActive);
			// Label the deck/collection we are about to materialize, not the last
			// one we finished: getView(true) below can block for a moment on that
			// tab's first data load, and a counter that already reads "(5/12)"
			// looks like work in progress instead of a freeze at "(4/12)". Name
			// and TYPE it too, so consecutive updates visibly differ even when a
			// whole batch of small tabs flies by between two splash repaints, and
			// collections (same DeckView.ID, same tab kind at the API level) are
			// visibly covered - not just decks.
			//
			// ref.getPartName() is NOT the real name here - it is the tab's
			// static/persisted title from BEFORE this materialization (the
			// plugin.xml default "Deck" the very first time, or whatever was
			// last saved), which is exactly why every tab used to read
			// "Restoring deck "Deck"": the real name only gets set by
			// DeckView.updatePartName(), which runs AS PART OF getView(true)
			// below - too late to read here. Resolve it from the model instead,
			// the same way kindOf() already does from the secondary id.
			com.reflexit.magiccards.core.model.nav.CardElement el = elementOf(ref);
			String deckName = nameOf(ref, el);
			MASplashHandler.reportStartupTail(
					"(" + done + "/" + decks.size() + ")  Restoring " + kindOf(el) + " "
							+ (deckName == null || deckName.isEmpty() ? "…" : "“" + deckName + "”"),
					0.10 + 0.20 * done / Math.max(1, decks.size()));
			if (display != null) {
				// give the splash a moment to actually paint this name - an
				// already-warm getView(true) below can take under a millisecond,
				// which would let several updates race by uncoalesced (never
				// actually shown) without this floor
				long dwell = System.currentTimeMillis() + 35L;
				while (System.currentTimeMillis() < dwell) {
					if (!display.readAndDispatch()) {
						try {
							Thread.sleep(5L);
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
							break;
						}
					}
				}
			}
			// This tab is being force-materialized purely to fix its icon/title
			// (see the class comment); getView(false) tells us whether the
			// platform had ALREADY made it active on its own (the tab the user
			// will actually see first) versus one we are only touching for its
			// icon. Only the former needs its card list ready before the window
			// is shown - hint the latter's upcoming loadData() to run at the
			// lowest job priority so it does not hold up drainInitialCardLoads()
			// (or compete with) the one tab that is actually about to be visible.
			if (!alreadyActive) {
				com.reflexit.magiccards.ui.views.AbstractMagicCardsListControl.backgroundLoadHint = true;
			}
			// This is the real unknown cost of this method: getView(true)
			// synchronously runs createPartControl() on the UI thread (table
			// build, column model from the preference store, listener
			// wiring...) for a tab the platform had NOT already materialized.
			// The 35ms dwell above is a fixed, known cost (~350ms total for
			// 10 tabs) - THIS number is what tells us whether "Restoring
			// views..." being slow is actually this call, or something else.
			long viewStart = System.currentTimeMillis();
			ref.getView(true);
			long viewMs = System.currentTimeMillis() - viewStart;
			trace("restoreDeckFamilyIcons: [" + done + "/" + decks.size() + "] getView(true) took " + viewMs
					+ "ms (alreadyActive=" + alreadyActive + ")");
			if (display != null) {
				for (int i = 0; i < 10 && display.readAndDispatch(); i++) {
					// flush pending paints / async work before the next view
				}
			}
		}
		trace("restoreDeckFamilyIcons: finished " + decks.size() + " tab(s) in "
				+ (System.currentTimeMillis() - methodStart) + "ms total");
	}

	/** Resolves the tab's secondary id (the element's Location path) to its
	 *  in-memory model element - a tree walk, not a card-list load - or
	 *  {@code null} if there is no secondary id or it cannot be resolved (the
	 *  model may not be fully populated yet). Shared by {@link #kindOf} and
	 *  {@link #nameOf} so a tab is only looked up once. */
	private static com.reflexit.magiccards.core.model.nav.CardElement elementOf(IViewReference ref) {
		String secId = ref.getSecondaryId();
		if (secId == null || secId.isEmpty())
			return null;
		try {
			return com.reflexit.magiccards.core.DataManager.getInstance().getModelRoot().findElement(secId);
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** "deck" or "collection". Both kinds are DeckView.ID tabs, so without this
	 *  every restored tab reads as a "deck" in the splash text. */
	private static String kindOf(com.reflexit.magiccards.core.model.nav.CardElement el) {
		if (el == null)
			return "deck";
		return com.reflexit.magiccards.core.DataManager.getInstance().getModelRoot()
				.sideOf(el) == com.reflexit.magiccards.core.model.nav.ModelRoot.Side.COLLECTION ? "collection"
						: "deck";
	}

	/** The deck/collection's real name. {@code ref.getPartName()} is NOT this -
	 *  before the tab is materialized it is only the static/persisted title
	 *  (the plugin.xml default "Deck" on a first restore), so every tab used to
	 *  read "Restoring deck "Deck"". Falls back to the last path segment of the
	 *  secondary id, then to {@code getPartName()}, if the model element itself
	 *  is not resolvable yet. */
	private static String nameOf(IViewReference ref, com.reflexit.magiccards.core.model.nav.CardElement el) {
		if (el != null && el.getName() != null && !el.getName().isEmpty())
			return el.getName();
		String secId = ref.getSecondaryId();
		if (secId != null && !secId.isEmpty()) {
			int slash = secId.lastIndexOf('/');
			String last = slash >= 0 ? secId.substring(slash + 1) : secId;
			if (!last.isEmpty())
				return last;
		}
		return ref.getPartName();
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
