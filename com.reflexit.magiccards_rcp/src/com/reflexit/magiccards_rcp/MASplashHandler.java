/*
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: startup splash progress that tracks the card DB load
 */
package com.reflexit.magiccards_rcp;

import org.eclipse.core.runtime.IProduct;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.resource.StringConverter;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.splash.BasicSplashHandler;

import com.reflexit.magiccards.core.DataManager;

/**
 * Extended splash handler: the card database is parsed on a background thread
 * ({@code com.reflexit.magiccards.core.Activator} &rarr; {@link DataManager#asyncInitDb()}),
 * which is by far the longest part of startup. The stock splash progress bar only
 * tracks OSGi bundle activation, so it freezes for the whole DB load and the main
 * window opens half-populated.
 * <p>
 * This handler holds the splash screen up - running its own SWT event loop, the
 * documented "extended splash" pattern - and drives the progress bar / message
 * from {@link DataManager}'s startup-progress channel until the database is fully
 * loaded, then lets the workbench finish opening. A hard timeout guarantees a
 * broken or missing database can never block startup.
 * <p>
 * The database load occupies the first {@link #DB_PHASE_PCT}% of the bar; the
 * remaining slice is handed to the platform's own bundle/workbench-init progress
 * through {@link TailProgressMonitor}, so the whole thing reads as one 0&rarr;100
 * bar with no visible reset.
 */
public class MASplashHandler extends BasicSplashHandler {

	/** Absolute ceiling on how long we hold the splash for the DB load. */
	private static final long MAX_WAIT_MS = 120_000L;
	/** Share of the bar given to the card-DB load; the rest is the workbench start. */
	private static final int DB_PHASE_PCT = 85;
	private static final int TAIL_SPAN = 100 - DB_PHASE_PCT;

	private static final Rectangle DEFAULT_PROGRESS_RECT = new Rectangle(10, 10, 300, 15);
	private static final Rectangle DEFAULT_MESSAGE_RECT = new Rectangle(10, 22, 300, 15);
	private static final RGB DEFAULT_FOREGROUND = new RGB(0xD2, 0xD7, 0xDF);

	private IProgressMonitor realMonitor;
	private TailProgressMonitor tailMonitor;
	private boolean dbPhaseDone;

	// Shared tail state: both the platform's bundle progress (via TailProgressMonitor)
	// and the workbench advisor (via reportStartupTail) push the bar through the top
	// TAIL_SPAN%, whichever is further along - so a silent stretch of UI-thread view
	// restore does not leave the bar frozen at "Starting ...".
	private static volatile IProgressMonitor tailBar;
	private static int tailShown;

	private static synchronized void tailPushTo(int want) {
		want = Math.min(want, TAIL_SPAN - 1);
		IProgressMonitor bar = tailBar;
		if (bar != null && want > tailShown) {
			bar.internalWorked(want - tailShown);
			tailShown = want;
		}
	}

	/**
	 * Called by {@code ApplicationWorkbenchWindowAdvisor} while it does its
	 * (UI-thread, largely silent) post-window-open work under the splash, so the
	 * bar keeps moving instead of sitting at "Starting ManaDesk".
	 *
	 * @param message  status line, or {@code null} to leave it
	 * @param fraction 0..1 of the remaining tail slice
	 */
	/** Flip to {@code true} to trace every splash-text call on stderr (visible
	 *  with {@code -consoleLog}) - temporary, for diagnosing "the text never
	 *  seems to update" reports. */
	static final boolean TRACE = false;

	public static void reportStartupTail(String message, double fraction) {
		IProgressMonitor bar = tailBar;
		if (TRACE) {
			System.err.println("[Startup] reportStartupTail(" + (message == null ? "null" : "\"" + message + "\"")
					+ ", " + fraction + ")  bar=" + (bar == null ? "NULL - no-op" : bar.getClass().getName()));
		}
		if (bar == null)
			return;
		if (message != null && !message.isEmpty())
			bar.subTask(message);
		tailPushTo((int) Math.round(TAIL_SPAN * Math.max(0.0, Math.min(1.0, fraction))));
	}

	@Override
	public void init(Shell splash) {
		super.init(splash);
		applyProductSplashLayout();

		Display display = splash.getDisplay();
		IProgressMonitor pm = getBundleProgressMonitor(); // the real one (dbPhaseDone == false)

		// Touching DataManager activates the core bundle, which starts the load
		// thread; asyncInitDb() is a no-op if it is already running.
		DataManager dm = DataManager.getInstance();
		dm.asyncInitDb();

		pm.beginTask("Loading card database…", 100);
		int shown = 0;        // % already pushed to the bar
		int lastReal = 0;     // last % actually reported by DataManager
		long lastAdvance = System.currentTimeMillis();
		String lastTask = null;
		long deadline = System.currentTimeMillis() + MAX_WAIT_MS;

		while (!DataManager.isDbFullyLoaded() && System.currentTimeMillis() < deadline
				&& !splash.isDisposed()) {
			int total = DataManager.getInitTotal();
			int real = lastReal;
			if (total > 0) {
				real = (int) ((long) DataManager.getInitWorked() * DB_PHASE_PCT / total);
				real = Math.max(0, Math.min(real, DB_PHASE_PCT));
			}
			if (real > lastReal) {
				lastReal = real;
				lastAdvance = System.currentTimeMillis();
			}

			// Target: the real value, plus - when a phase has gone quiet (a big
			// opaque step like the full-DB price/own-count pass) - an asymptotic
			// creep so the bar keeps drifting instead of freezing. Capped a few %
			// ahead of the truth and never past the DB slice.
			int target = lastReal;
			if (System.currentTimeMillis() - lastAdvance > 300L) {
				int ceiling = Math.min(DB_PHASE_PCT - 1, lastReal + 6);
				if (shown < ceiling)
					target = shown + Math.max(1, (ceiling - shown) / 4);
			}
			if (target > shown) {
				pm.worked(target - shown);
				shown = target;
			}

			String task = DataManager.getInitTask();
			if (task != null && !task.equals(lastTask)) {
				pm.subTask(task);
				lastTask = task;
			}

			// Pump splash paint events, then poll again shortly. Do NOT use
			// Display.sleep(): a static splash produces no events, so it would
			// block forever and we would never re-check the completion flag or
			// the deadline.
			if (!display.readAndDispatch()) {
				try {
					Thread.sleep(40L);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}

		// Hand the rest of the bar to the platform and to the workbench advisor.
		// From here getBundleProgressMonitor() returns the tail wrapper, which
		// folds the platform's bundle/workbench-init progress into
		// DB_PHASE_PCT..100 instead of resetting to 0; reportStartupTail() drives
		// it through the advisor's own silent stretches.
		if (!splash.isDisposed())
			pm.subTask("Starting the workbench…");
		tailBar = realMonitor;
		dbPhaseDone = true;
		if (TRACE) {
			System.err.println("[Startup] DB phase done. splash.isDisposed()=" + splash.isDisposed()
					+ "  tailBar=" + (tailBar == null ? "NULL" : tailBar.getClass().getName()));
		}
	}

	@Override
	public IProgressMonitor getBundleProgressMonitor() {
		if (realMonitor == null)
			realMonitor = super.getBundleProgressMonitor();
		if (!dbPhaseDone)
			return realMonitor;
		if (tailBar == null)
			tailBar = realMonitor;
		if (tailMonitor == null)
			tailMonitor = new TailProgressMonitor();
		return tailMonitor;
	}

	/**
	 * Position the progress bar / message the same way the stock
	 * {@code EclipseSplashHandler} does, from the product's {@code startup*Rect}
	 * properties (see {@code com.reflexit.magiccards.product/magic.product}).
	 */
	private void applyProductSplashLayout() {
		String progress = null;
		String message = null;
		String foreground = null;
		IProduct product = Platform.getProduct();
		if (product != null) {
			progress = product.getProperty("startupProgressRect");
			message = product.getProperty("startupMessageRect");
			foreground = product.getProperty("startupForegroundColor");
		}
		setProgressRect(StringConverter.asRectangle(progress, DEFAULT_PROGRESS_RECT));
		setMessageRect(StringConverter.asRectangle(message, DEFAULT_MESSAGE_RECT));
		int rgb;
		try {
			rgb = Integer.parseInt(foreground, 16);
		} catch (RuntimeException e) {
			rgb = (DEFAULT_FOREGROUND.red << 16) | (DEFAULT_FOREGROUND.green << 8) | DEFAULT_FOREGROUND.blue;
		}
		setForeground(new RGB((rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff));
	}

	/**
	 * Folds the platform's post-splash bundle/workbench-init progress into the top
	 * {@link #TAIL_SPAN}% of a bar our DB phase already advanced to
	 * {@link #DB_PHASE_PCT}%. The platform runs its progress through
	 * {@code SubMonitor.convert()}, which calls {@code beginTask(null, MAX_INT)} -
	 * we swallow that (so the bar is not reset) and translate the streamed
	 * {@code internalWorked} amounts (fractions of {@code Integer.MAX_VALUE}) into
	 * forward motion, sharing the {@code tailShown} counter with
	 * {@link #reportStartupTail}.
	 */
	private static final class TailProgressMonitor implements IProgressMonitor {
		private double fraction;

		private void advance(double workOfMaxInt) {
			fraction = Math.min(1.0, fraction + workOfMaxInt / Integer.MAX_VALUE);
			tailPushTo((int) Math.round(TAIL_SPAN * fraction));
		}

		@Override
		public void beginTask(String name, int totalWork) {
			// swallow - do not reset the shared progress bar
		}

		@Override
		public void internalWorked(double work) {
			advance(work);
		}

		@Override
		public void worked(int work) {
			advance(work);
		}

		@Override
		public void done() {
			tailPushTo(TAIL_SPAN - 1);
		}

		@Override
		public void setTaskName(String name) {
			subTask(name);
		}

		@Override
		public void subTask(String name) {
			IProgressMonitor bar = tailBar;
			if (bar != null && name != null && !name.isEmpty())
				bar.subTask(name);
		}

		@Override
		public void setCanceled(boolean value) {
			IProgressMonitor bar = tailBar;
			if (bar != null)
				bar.setCanceled(value);
		}

		@Override
		public boolean isCanceled() {
			IProgressMonitor bar = tailBar;
			return bar != null && bar.isCanceled();
		}
	}
}
