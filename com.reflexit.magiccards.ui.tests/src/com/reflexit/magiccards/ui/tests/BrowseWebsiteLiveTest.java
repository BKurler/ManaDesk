/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: unlike DeckTextExtractorTest's
 *                         real-capture tests (a frozen snapshot of a page,
 *                         checked at the moment it was pasted), this connects
 *                         to every URL currently in the user's "Browse
 *                         Website..." favorites list (WebFavoritesStore) with
 *                         a REAL embedded browser (same SWT.EDGE mechanism as
 *                         BrowseWebsiteDialog itself) and re-runs
 *                         DeckTextExtractor against today's actual page - the
 *                         whole point being to notice if a site's layout
 *                         changes and silently breaks parsing, which a
 *                         static text fixture can never catch. Needs a live
 *                         network connection, a real display, and can take a
 *                         while (one browser navigation + settle-time poll
 *                         per favorite) - also runnable directly (right-
 *                         click > Run As > JUnit Plug-in Test) whenever you
 *                         just want to sanity-check the favorites list
 *                         itself, not the whole suite.
 *                         Deliberately asserts only "still finds a plausible
 *                         decklist" (unique &gt;= MIN_MATCHES), not exact
 *                         counts - unlike a frozen capture, a live page's
 *                         real deck can legitimately change over time (the
 *                         user editing their own list), so pinning an exact
 *                         total/unique here would fail for the wrong reason.
 *     Rémi Dutil (2026) - relying solely on WebFavoritesStore.load() meant
 *                         this test silently covered NOTHING on a fresh
 *                         workspace (the favorites file lives under the
 *                         per-workspace magiccards data dir - a new
 *                         workspace starts with none) - Assume.assumeTrue
 *                         would just skip the whole test rather than fail
 *                         loudly, so a regression could go completely
 *                         unnoticed. Added DEFAULT_FAVORITES - one known-good
 *                         real deck URL per site this project has actually
 *                         had to fix parsing for (TCGplayer, Archidekt,
 *                         Moxfield, deckstats.net, TappedOut, deckbox.org,
 *                         mtgtop8) - always tested regardless of workspace
 *                         state, with the user's own current favorites
 *                         merged in on top (deduplicated by URL) for
 *                         whatever extra coverage they've configured.
 *     Rémi Dutil (2026) - the test ran so fast it wasn't believable any URL
 *                         was really being fetched (no output to prove
 *                         otherwise) - added permanent trace output for
 *                         every navigation and poll, to System.out (visible
 *                         immediately in the JUnit run's Console view) -
 *                         each line is timestamped (elapsed ms since the
 *                         test started) specifically so real wall-clock time
 *                         passing is visible, not just a final pass/fail.
 *                         (Originally also logged via MagicLogger, which
 *                         turned out to duplicate every line - that already
 *                         echoes to the same console in this environment, so
 *                         it was dropped in favor of a single System.out.)
 *     Rémi Dutil (2026) - the trace output above showed a real run genuinely
 *                         fetching every page (captured char counts growing
 *                         over several polls, real elapsed time), yet 5 of 7
 *                         favorites still failed - but the 2 that passed
 *                         (TappedOut, mtgtop8) are exactly the 2 sites whose
 *                         card rows are same-line "qty name" shape, trusted
 *                         by pattern alone with no database needed; every
 *                         other site's shape (bare/trailing-qty matches -
 *                         Archidekt, deckstats.net, TCGplayer, deckbox.org)
 *                         REQUIRES DeckTextExtractor's database cross-check
 *                         to resolve at all. This JUnit Plug-in Test runs in
 *                         its own separate runtime workspace, which (unlike
 *                         the user's regular dev/test workspace) has likely
 *                         never had a card database downloaded into it -
 *                         previously nothing in this test ever loaded one.
 *                         Added a DataManager.waitForInit() call up front
 *                         (reads whatever is already on disk - no network
 *                         call, no "download the database?" prompt, unlike
 *                         the product-bootstrap path this test deliberately
 *                         avoids) plus a loud trace of how many cards
 *                         loaded, and an Assume-based skip (not a failure)
 *                         when the database is empty, so a bare/uninitialized
 *                         test workspace reports clearly as "can't check
 *                         this" instead of silently misreporting every DB-
 *                         dependent site as broken.
 *     Rémi Dutil (2026) - loadAndCount() kept polling a site all the way to
 *                         MAX_POLLS even once its captured content had
 *                         completely stopped changing - a real run showed
 *                         TCGplayer plateau at a fixed capture by poll 3 and
 *                         then poll identically 37 more times for nothing.
 *                         Added a plateau-detection early exit (see
 *                         PLATEAU_POLLS): once the captured text length is
 *                         unchanged for a few consecutive polls, the page
 *                         has settled (successfully or not) and further
 *                         polling can only repeat the same result.
 *     Rémi Dutil (2026) - the "107,116 cards loaded" trace was misleading -
 *                         it turned out to be reading a months-stale
 *                         database under &lt;user.home&gt;/ManaDesk, not the
 *                         user's actual dev database, because a PDE "JUnit
 *                         Plug-in Test" launch doesn't always set
 *                         osgi.instance.area the way a full RCP application
 *                         launch does, and FileUtils.getWorkspace() silently
 *                         falls back to that default when it's unset. Pinned
 *                         DEFAULT_MAGICCARDS_DIR (only when -Dma.magiccards.
 *                         area isn't already set on the launch) to the same
 *                         directory every other Eclipse launch in this
 *                         project already uses by convention, and traces the
 *                         resolved directory up front so this is never a
 *                         silent surprise again.
 *     Rémi Dutil (2026) - a real run flagged tipsymagic.com as an unfixable
 *                         false failure (confirmed via DeckTextExtractor's
 *                         own investigation: it renders card names as
 *                         images, never as text) - added
 *                         KNOWN_UNSUPPORTED_DOMAINS so a favorite pointing
 *                         there is traced and skipped instead of counted as
 *                         a failure, and the pass/fail summary now reports
 *                         against the checked count (excluding skips), not
 *                         the raw favorites count.
 *     Rémi Dutil (2026) - TCGplayer/Moxfield/deckbox.org kept plateauing at
 *                         a small, unchanging capture even after raising
 *                         MAX_POLLS to 40 - real evidence this is a genuine
 *                         stall (most likely a first-visit cookie-consent
 *                         wall), not a timing issue, since this test always
 *                         started a brand-new, anonymous WebView2 profile.
 *                         Pointed org.eclipse.swt.browser.Edge at a
 *                         PERSISTENT profile directory instead (confirmed by
 *                         inspecting the actual bundled SWT jar's Edge.class/
 *                         Edge.java, not assumed) - the SAME mechanism that
 *                         already makes interactive BrowseWebsiteDialog
 *                         sessions immune to this (a shared profile that has
 *                         accumulated real cookies over months of manual
 *                         use). The first run against a fresh profile will
 *                         still need any consent prompt cleared by hand once;
 *                         every run after that reuses the saved state. Also
 *                         passes --disable-save-password-bubble as an
 *                         AdditionalBrowserArgument (defense in depth - SWT's
 *                         bundled WebView2 settings interface here predates
 *                         IsPasswordAutosaveEnabled, so there is no verified
 *                         code-level way to disable it outright); this
 *                         profile exists only for cookie/consent persistence
 *                         and must never be used to log into anything.
 *     Rémi Dutil (2026) - two follow-ups after running with the persistent
 *                         profile above: (1) a second run showed frequent
 *                         ~1-minute stalls, traced to a few still-corrupted
 *                         favorite URLs (see the WebFavorite.
 *                         fromDisplayString() fix - it prevents future
 *                         corruption but doesn't repair what's already on
 *                         disk) reaching browser.setUrl() unvalidated and
 *                         hanging on DNS resolution of a nonsense,
 *                         punycode-encoded hostname; now fails each one
 *                         immediately with a clear reason instead. (2) the
 *                         profile directory was test-exclusive, defeating
 *                         the point - a fresh test profile starts logged out
 *                         of everything the real app is already signed into.
 *                         Repointed BROWSER_PROFILE_DIR to the exact same
 *                         path MAApplication.start() now pins for the real,
 *                         running application, so both genuinely share one
 *                         WebView2 profile (cookies, logins) rather than
 *                         each keeping their own.
 *     Rémi Dutil (2026) - with the shared profile above, a real run against
 *                         all 12 of the user's actual favorites (up from the
 *                         original 7 - user has since added more) showed
 *                         deckbox.org now passing (see DeckTextExtractor's
 *                         own header for that fix) and only TCGplayer/
 *                         Moxfield still failing, both plateauing at a
 *                         suspiciously SMALL capture (3190 chars/1 match,
 *                         44 chars/0 matches - nowhere near a real decklist
 *                         page's size) rather than the "found real content,
 *                         parsed 0 cards from it" shape a DeckTextExtractor
 *                         bug would produce. With no visibility into what
 *                         actually rendered there (a bot-check interstitial?
 *                         a loading shell that never finished? something
 *                         else?), loadAndCount() now dumps the actual
 *                         captured text (capped) whenever it gives up
 *                         without reaching MIN_MATCHES, so the next real run
 *                         turns this into evidence instead of another guess.
 *     Rémi Dutil (2026) - the dump above gave two concrete, unrelated
 *                         answers on the very next real run. Moxfield's
 *                         capture was its own literal, unchanging loading
 *                         placeholder ("Loading Moxfield. This may take a
 *                         minute...", 44 chars) - PLATEAU_POLLS was writing
 *                         this off as "settled" well before that promised
 *                         minute was up; a stable capture below
 *                         MIN_PLATEAU_TEXT_LEN is now treated as still-
 *                         loading instead, worth the wait up to MAX_POLLS.
 *                         TCGplayer's capture, by contrast, was a fully real,
 *                         legitimate page (nav, mana curve, card-type/rarity
 *                         breakdowns, "Maindeck / Market Price") with the
 *                         actual card rows missing entirely - traced to the
 *                         test's Shell being created and sized but never
 *                         actually opened/realized (shell.open() was simply
 *                         never called), a real gap independent of anything
 *                         about TCGplayer specifically: a never-shown window
 *                         still renders plain DOM content fine (every other
 *                         site's static content already proved that), but
 *                         viewport-visibility-gated behavior (a virtualized/
 *                         windowed list, an IntersectionObserver-based lazy
 *                         mount - exactly what a long decklist table is a
 *                         plausible candidate for) can depend on the window
 *                         actually being realized to fire at all. Opened it
 *                         off-screen (setLocation far outside any real
 *                         monitor before open()) so this doesn't pop a
 *                         visible window onto the user's desktop.
 *     Rémi Dutil (2026) - WebFavoritesStore now always includes 10 built-in,
 *                         non-editable/removable SITE (not deck) favorites
 *                         (see its own header) - real users are meant to
 *                         favorite sites, browse to their actual deck, then
 *                         Import, not favorite individual decks one at a
 *                         time. That broke this test's old design of
 *                         iterating WebFavoritesStore.load() (a base site
 *                         URL has no decklist on it at all) and made it
 *                         couple two unrelated concerns - "is this site's
 *                         parser still correct" and "what does the user
 *                         happen to have favorited right now". Split into
 *                         one independent @Test per supported site, each
 *                         with its own hardcoded, already-proven real deck
 *                         URL (the exact 10 sites this project has had to
 *                         design/fix DeckTextExtractor's parsing for - the
 *                         same set WebFavoritesStore.BUILT_IN_FAVORITES
 *                         lists), fully decoupled from WebFavoritesStore -
 *                         a layout regression on one site now fails just
 *                         that one JUnit test instead of one bundled "N of M
 *                         failed" result, and the built-in site URLs (no
 *                         decklist to find) are never a test subject. The
 *                         shared Display/Shell/Browser moved to
 *                         @BeforeClass/@AfterClass - real cost (WebView2
 *                         startup + a persistent profile) that's worth
 *                         paying once for the whole class, not once per
 *                         site.
 *     Rémi Dutil (2026) - added testTappedOutCommanderRenderedAsImage(): a
 *                         real capture showed TappedOut renders a Commander
 *                         card as a pure image, invisible to plain
 *                         innerText - see BrowseWebsiteDialog's own
 *                         CAPTURE_TEXT_WITH_IMAGE_ALT_SCRIPT header for the
 *                         fix. Deliberately its own separate test, not
 *                         folded into checkSite()/loadAndCount() (which
 *                         only ever exercise plain innerText) - proves the
 *                         image-alt capture specifically, without risking
 *                         the other 10 sites' shared polling logic.
 *     Rémi Dutil (2026) - added testMoxfieldFlavorNamedCardLink(): same
 *                         shape as testTappedOutCommanderRenderedAsImage()
 *                         above, but for a different real gap - a Pauper
 *                         "Dimir Faery" deck's own land, Bojuka Bog, is
 *                         displayed on this printing as "Barrow-Downs" (its
 *                         own flavor/alternate-art name, not a real Scryfall
 *                         card name), undercounting the deck by one. See
 *                         CAPTURE_TEXT_WITH_IMAGE_ALT_SCRIPT's own header for
 *                         the fix (reads the real name from the row's own
 *                         link href instead of its displayed text).
 *     Rémi Dutil (2026) - renamed from BrowseWebsiteFavoritesLiveTest - by
 *                         now this class is really a live regression check
 *                         for DeckTextExtractor's own real-site parsing
 *                         (same role as DeckTextExtractorTest's frozen
 *                         captures, just against today's actual page), not
 *                         a test of the favorites feature itself; it still
 *                         uses WebFavoritesStore/DEFAULT_FAVORITES purely as
 *                         its own convenient source of real URLs to check.
 *     Rémi Dutil (2026) - added to AllTests.java's @SuiteClasses, by explicit
 *                         request - a normal run of that suite now needs a
 *                         live network connection and a real display, and
 *                         takes noticeably longer (one browser navigation +
 *                         settle-time poll per real site checked).
 *     Rémi Dutil (2026) - removed testMoxfieldFlavorNamedCardLink: its Moxfield
 *                         deck was deleted and no replacement deck is known yet.
 *******************************************************************************/
package com.reflexit.magiccards.ui.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.exports.DeckTextExtractor;
import com.reflexit.magiccards.ui.dialogs.BrowseWebsiteDialog;

public class BrowseWebsiteLiveTest {
	/** FileUtils.getMagicCardsDir() falls back to {@code <user.home>/ManaDesk}
	 *  whenever the running OSGi instance has no osgi.instance.area set - a
	 *  PDE "JUnit Plug-in Test" launch doesn't always set that the way a full
	 *  RCP application launch does, so this test was silently reading (and,
	 *  since syncInitDb() -&gt; reconcile() updates each DB card's OWN_COUNT
	 *  from whatever Collections/Decks sit alongside it, potentially writing
	 *  into) a months-stale, unrelated default folder instead of the user's
	 *  actual dev database - the same one this whole feature's earlier manual
	 *  BrowseWebsiteDialog testing, and every other Eclipse launch in this
	 *  project, already uses by convention. Pinned explicitly here (only if
	 *  not already set - an explicit -Dma.magiccards.area on the launch
	 *  itself still wins) so this test's data source is never an accident. */
	private static final String DEFAULT_MAGICCARDS_DIR = "C:/Dev/runtime-magic.product/magiccards";

	/** A real, PERSISTENT WebView2 profile (cookies, local storage - so a
	 *  first-visit consent wall only ever has to be cleared once, not on
	 *  every run) instead of an anonymous ephemeral one. Deliberately the
	 *  SAME path MAApplication.start() pins for the real, running ManaDesk
	 *  application (".metadata/.plugins/" + DataManager.ID + "/browser-
	 *  profile", under the workspace root) rather than a test-exclusive
	 *  folder, hardcoded here rather than resolved via
	 *  FileUtils.getWorkspaceFile(...) for the same reason as
	 *  DEFAULT_MAGICCARDS_DIR above: this launch type's own workspace/state-
	 *  area resolution isn't reliable, so both must be pinned to the one,
	 *  real dev workspace explicitly. This means the test genuinely reuses
	 *  whatever the real app already has signed into a site - which is the
	 *  point (real decks are commonly behind a login) - so this folder can
	 *  accumulate real browser data over time; the WebView2 SDK surface
	 *  bound by this SWT version doesn't expose a way to programmatically
	 *  disable password autosave (confirmed by inspecting the actual SWT jar
	 *  bundled with this project), so --disable-save-password-bubble below
	 *  is defense-in-depth only, not a guarantee - never save a password
	 *  through this profile regardless. Wired to SWT's real, documented
	 *  property for this - org.eclipse.swt.browser.Edge.DATA_DIR_PROP. */
	private static final String BROWSER_PROFILE_DIR = "C:/Dev/runtime-magic.product/.metadata/.plugins/com.reflexit.magiccards.core/browser-profile";

	/** How long to wait between innerText re-checks after navigating - matches
	 *  BrowseWebsiteDialog's own live card-count polling interval, since some
	 *  sites (TCGplayer, a real capture showed) keep filling in the decklist
	 *  via XHR/fetch well after the initial page load event fires. */
	private static final int POLL_MS = 1500;
	/** How many times to re-check before giving up on a site - roughly 60s of
	 *  settle time per favorite. Raised from 15 (22.5s): a real run showed
	 *  TCGplayer/Moxfield/deckbox.org plateauing at a small, unchanging
	 *  capture for the full 22.5s window - this JUnit test always starts a
	 *  brand-new WebView2 profile (no saved cookies/local storage, unlike an
	 *  interactive BrowseWebsiteDialog session reused across many manual
	 *  tests), which a first-time cookie-consent wall or slower cold-start
	 *  bootstrap could plausibly need more time to clear. If a site is STILL
	 *  flat after this longer budget, that instead points at a real stall
	 *  (e.g. a consent overlay genuinely waiting on a click no automated
	 *  session can give it) rather than simply needing more patience. */
	private static final int MAX_POLLS = 40;
	/** Stop polling a site early once its captured innerText length hasn't
	 *  changed for this many consecutive polls (~6s) - a real run showed
	 *  TCGplayer plateau at a fixed, unchanging capture by poll 3 and then
	 *  poll uselessly all the way to MAX_POLLS, burning most of a minute
	 *  checking a page that had already settled (successfully or not). */
	private static final int PLATEAU_POLLS = 4;
	/** Below this many captured characters, a stable/unchanging capture is
	 *  treated as still-loading rather than settled, so {@link
	 *  #PLATEAU_POLLS} does not apply - a real Moxfield run plateaued for the
	 *  full PLATEAU_POLLS budget on a literal, unchanging loading placeholder
	 *  ("Loading Moxfield. This may take a minute...", 44 chars - Moxfield's
	 *  own text says so) well before that "minute" was up, and got wrongly
	 *  written off as settled. Every currently-passing favorite's real,
	 *  final capture is 1612-21362 chars, so a stable capture below this
	 *  threshold is presumptively still a loading shell, not a genuinely
	 *  tiny real page - worth the extra wait up to {@link #MAX_POLLS} rather
	 *  than giving up early on a false plateau. */
	private static final int MIN_PLATEAU_TEXT_LEN = 500;

	/** When the test class started - every trace() line reports elapsed time
	 *  since here, so real wall-clock time passing (network + page render,
	 *  not an instant no-op) is visible at a glance across every site's own
	 *  test method. */
	private static long startNanos;

	/** Prints to System.out - visible immediately in the JUnit run's Console
	 *  view, which is all this needs. (Not also MagicLogger: that duplicated
	 *  every line, since MagicLogger.log() itself already echoes to the
	 *  Eclipse log/console in this environment - not needed on top of a
	 *  direct System.out.println.) */
	private static void trace(String message) {
		System.out.println(
				"[" + (System.nanoTime() - startNanos) / 1_000_000L + "ms] BrowseWebsiteLiveTest: " + message);
	}

	/** Shared across every per-site @Test method below - one Display/Shell/
	 *  Browser for the whole class (created once in {@link #setUpOnce}, torn
	 *  down once in {@link #tearDownOnce}), not per test: a real, JS-heavy
	 *  page + a persistent WebView2 profile is expensive enough to load that
	 *  recreating the browser 10 times over would make the class' real
	 *  runtime dominated by browser startup rather than site checks. */
	private static Display display;
	private static Shell shell;
	private static Browser browser;

	/** Pins the shared card-database directory and browser profile (only if
	 *  not already set by the launch itself), waits for the local card
	 *  database to load (most sites' parsing depends on DeckTextExtractor's
	 *  database cross-check), and creates the one Display/Shell/Browser every
	 *  per-site @Test method below shares. An Assume failure here (database
	 *  empty, or WebView2 unavailable) skips the WHOLE class, same as it
	 *  previously skipped the one bundled test method - there is nothing
	 *  meaningful any individual site check could still verify without
	 *  either of these. */
	@BeforeClass
	public static void setUpOnce() throws Exception {
		startNanos = System.nanoTime();

		// Just setting the system property here is not enough when this class
		// runs as part of a larger suite (AllTests, where it's last in the
		// @SuiteClasses list): DataManager.getModelRoot() is a lazily-
		// initialized, process-wide singleton, locked in by whichever caller -
		// an earlier test class in the same suite, or the Activator's own
		// eager "Loading database" startup thread - reaches it FIRST in this
		// JVM, using whatever ma.magiccards.area resolved to at THAT moment;
		// setting the property afterward here has no effect on an
		// already-resolved root. DataManager.relocateTo() forces root back
		// onto the right directory regardless of who won that race, without
		// deleting anything under it (unlike DataManager.reset(), which is
		// built for a disposable test-owned temp dir and would wipe out this
		// real, populated database).
		String explicit = System.getProperty("ma.magiccards.area");
		File magicCardsDir = explicit != null ? new File(explicit) : new File(DEFAULT_MAGICCARDS_DIR);
		DataManager.getInstance().relocateTo(magicCardsDir);
		trace("card database directory: " + FileUtils.getMagicCardsDir());

		// org.eclipse.swt.browser.Edge's own DATA_DIR_PROP/BROWSER_ARGS_PROP
		// constants (confirmed by inspecting the actual SWT jar this project
		// bundles - they're package-private, so the literal property names are
		// used here instead of a compile-time reference). --disable-save-
		// password-bubble is a real, long-standing Chromium switch (used by
		// browser-automation frameworks for exactly this) - defense in depth,
		// not a substitute for never actually logging into anything here.
		if (System.getProperty("org.eclipse.swt.browser.EdgeDataDir") == null)
			System.setProperty("org.eclipse.swt.browser.EdgeDataDir", BROWSER_PROFILE_DIR);
		if (System.getProperty("org.eclipse.swt.browser.EdgeArgs") == null)
			System.setProperty("org.eclipse.swt.browser.EdgeArgs", "--disable-save-password-bubble");
		trace("browser profile directory: " + System.getProperty("org.eclipse.swt.browser.EdgeDataDir"));

		// DeckTextExtractor's database cross-check (needed by most sites' real
		// shape - Archidekt/deckstats.net/TCGplayer/deckbox.org all rely on it
		// for their bare/trailing-qty matches) is silently a no-op if the local
		// card database was never loaded. waitForInit() only reads whatever is
		// ALREADY on disk under this run's magiccards dir (no network call, no
		// "download the database?" prompt) - if that dir has never had a
		// database downloaded into it (a fresh/separate JUnit test workspace
		// most likely does), this reports 0 cards and skips rather than
		// reporting confusing, misleading site failures.
		trace("waiting for the local card database to finish loading...");
		boolean dbReady = DataManager.getInstance().waitForInit(30);
		int dbSize = DataManager.getInstance().getMagicDBStore().getCards().size();
		trace("card database: " + (dbReady ? "initialized" : "NOT fully initialized after 30s") + ", " + dbSize
				+ " card(s) loaded");
		Assume.assumeTrue(
				"the local card database is empty in this test run's workspace - most sites' parsing depends on "
						+ "DeckTextExtractor's database cross-check, so this run can't meaningfully check them. "
						+ "Point this launch's -data (or -Dma.magiccards.area=<dir>) at a workspace/directory that "
						+ "already has a downloaded card database, or run File ▸ Update Card Database there first.",
				dbSize > 0);

		display = Display.getDefault();
		AtomicReference<Shell> shellRef = new AtomicReference<>();
		AtomicReference<Browser> browserRef = new AtomicReference<>();
		display.syncExec(() -> {
			Shell s = new Shell(display);
			s.setLayout(new FillLayout());
			s.setSize(1024, 768);
			Browser b;
			try {
				b = new Browser(s, SWT.EDGE);
			} catch (SWTError e) {
				b = null;
			}
			shellRef.set(s);
			browserRef.set(b);
			// a real TCGplayer capture showed a fully-loaded, legitimate page
			// (nav, mana curve, card-type/rarity breakdowns, "Maindeck /
			// Market Price") with the actual card rows missing entirely -
			// this shell was created and sized but never actually opened/
			// realized. A never-shown window still renders plain DOM content
			// fine (every other site's static content proves that), but
			// viewport-visibility-gated behavior (a virtualized/windowed
			// list, an IntersectionObserver-based lazy mount - exactly what
			// a long decklist table is a plausible candidate for) can depend
			// on the window actually being realized to fire at all. Opened
			// off-screen (far outside any real monitor) rather than at its
			// default position so this doesn't pop a visible window onto the
			// user's desktop during the run.
			if (b != null) {
				s.setLocation(-10000, -10000);
				s.open();
			}
		});
		shell = shellRef.get();
		browser = browserRef.get();
		Assume.assumeTrue("embedded browser (Microsoft Edge WebView2) is not available on this machine",
				browser != null);
	}

	@AfterClass
	public static void tearDownOnce() {
		if (display != null && shell != null)
			display.syncExec(() -> shell.dispose());
	}

	/** Navigates to {@code url} and asserts DeckTextExtractor recognizes a
	 *  plausible decklist on it - shared by every per-site @Test method
	 *  below, each hardcoding its own known-good real deck URL (see this
	 *  class' own header: these replaced a single test iterating
	 *  WebFavoritesStore.load(), since the favorites list itself now always
	 *  contains WebFavoritesStore.BUILT_IN_FAVORITES - base SITE urls with no
	 *  decklist on them at all, not something this kind of check could ever
	 *  usefully assert against - and decoupling from the user's own,
	 *  personal favorites keeps this a check of "is this site's parser still
	 *  working", not "does the user happen to have a favorite for it right
	 *  now"). */
	private void checkSite(String siteName, String url) throws InterruptedException {
		trace("=== " + siteName + " ===");
		// a malformed URL (a real cause seen once: WebFavorite.
		// fromDisplayString()'s now-fixed separator bug had corrupted a few
		// real favorites) previously reached browser.setUrl() as-is - the
		// browser tried to resolve the garbage as a hostname, and a DNS
		// lookup timing out for a domain that doesn't exist took up to a
		// full minute. These URLs are hardcoded constants now, so this is
		// only a defensive sanity check against a future typo, not a real
		// expected failure mode - but cheap enough to keep.
		try {
			new java.net.URL(url);
		} catch (java.net.MalformedURLException e) {
			throw new AssertionError(siteName + " (" + url + "): malformed URL (" + e.getMessage() + ")", e);
		}
		DeckTextExtractor.CardCount count = loadAndCount(display, browser, url);
		boolean passed = count.unique >= DeckTextExtractor.MIN_MATCHES;
		trace("  RESULT: total=" + count.total + " unique=" + count.unique + " (need >= " + DeckTextExtractor.MIN_MATCHES
				+ ") - " + (passed ? "PASS" : "FAIL"));
		assertTrue(siteName + " (" + url + "): only " + count.unique + " unique card(s) recognized (need "
				+ DeckTextExtractor.MIN_MATCHES + ") - the site's layout may have changed", passed);
	}

	@Test
	public void testTcgplayer() throws InterruptedException {
		checkSite("TCGplayer", "https://www.tcgplayer.com/content/magic-the-gathering/deck/Izzet-Prowess/550442/");
	}

	@Test
	public void testArchidekt() throws InterruptedException {
		checkSite("Archidekt", "https://archidekt.com/decks/26740402/copy_of_thranduil");
	}

	@Test
	public void testMoxfield() throws InterruptedException {
		checkSite("Moxfield", "https://moxfield.com/decks/6I46dLgrQkWnGlyDvpeXWg");
	}

	@Test
	public void testDeckstats() throws InterruptedException {
		checkSite("deckstats.net", "https://deckstats.net/decks/4392637-MB-oldschool-rev-2");
	}

	@Test
	public void testTappedOut() throws InterruptedException {
		checkSite("TappedOut", "https://tappedout.net/mtg-decks/midnight-znack/");
	}

	@Test
	public void testDeckbox() throws InterruptedException {
		checkSite("deckbox.org", "https://deckbox.org/sets/3561925");
	}

	@Test
	public void testMtgtop8() throws InterruptedException {
		checkSite("mtgtop8", "https://mtgtop8.com/event?e=90872&d=890135&f=MO");
	}

	@Test
	public void testAetherhub() throws InterruptedException {
		checkSite("Aetherhub", "https://aetherhub.com/Metagame/Modern/Deck/boros-aggro-1430155");
	}

	@Test
	public void testMtgdecks() throws InterruptedException {
		checkSite("MTG Decks",
				"https://mtgdecks.net/Modern/eldrazi-bloodchief-combo-decklist-by-stefansson30952-3101819");
	}

	@Test
	public void testMtggoldfish() throws InterruptedException {
		checkSite("MTGGoldfish", "https://www.mtggoldfish.com/archetype/standard-mono-green-landfall-woe#paper");
	}

	/** Deliberately separate from {@link #checkSite}/{@link #loadAndCount} -
	 *  a real TappedOut capture (godsmack) showed a Commander card ("Xenagos,
	 *  God of Revels") rendered as a pure raster image, nowhere present as
	 *  real text at all - document.body.innerText (what every other test
	 *  here checks) genuinely has nothing to find for it, so a normal
	 *  checkSite() run legitimately still passes (the other ~20 real cards
	 *  in this deck are ordinary text and clear MIN_MATCHES on their own),
	 *  without ever proving the commander itself is recoverable. This
	 *  instead does one settle-wait via the normal (cheap) polling, then a
	 *  SEPARATE, one-shot read using BrowseWebsiteDialog's own
	 *  CAPTURE_TEXT_WITH_IMAGE_ALT_SCRIPT (a heavier DOM-clone + image-alt-
	 *  substitution capture, deliberately not used for polling - see that
	 *  constant's own header), asserting the commander's name is now
	 *  present. */
	@Test
	public void testTappedOutCommanderRenderedAsImage() throws InterruptedException {
		String url = "https://tappedout.net/mtg-decks/17-02-26-godsmack/";
		trace("=== TappedOut - godsmack (Commander rendered as an image) ===");
		DeckTextExtractor.CardCount plain = loadAndCount(display, browser, url);
		trace("  plain innerText RESULT: total=" + plain.total + " unique=" + plain.unique);
		assertTrue("expected the rest of the deck (ordinary text, not image-rendered) to still be recognized via "
				+ "plain innerText (need >= " + DeckTextExtractor.MIN_MATCHES + ")",
				plain.unique >= DeckTextExtractor.MIN_MATCHES);

		AtomicReference<String> textRef = new AtomicReference<>();
		display.syncExec(() -> {
			try {
				Object result = browser.evaluate(BrowseWebsiteDialog.CAPTURE_TEXT_WITH_IMAGE_ALT_SCRIPT);
				textRef.set((result instanceof String) ? (String) result : "");
			} catch (Exception e) {
				textRef.set("");
			}
		});
		String text = textRef.get();
		DeckTextExtractor.CardCount withAlt = DeckTextExtractor.countRecognizedCards(text);
		boolean commanderRecovered = text.toLowerCase(java.util.Locale.ROOT).contains("xenagos");
		trace("  image-alt-substituted RESULT: total=" + withAlt.total + " unique=" + withAlt.unique
				+ ", 'Xenagos' present: " + commanderRecovered);
		assertTrue("expected the commander (Xenagos, God of Revels - rendered as a pure image on this page) to be "
				+ "recoverable via its alt/title text", commanderRecovered);
	}

	/** Navigates {@code browser} to {@code url} and polls its rendered
	 *  {@code document.body.innerText} through DeckTextExtractor until either
	 *  a plausible decklist is found or {@link #MAX_POLLS} is exhausted -
	 *  returns the best (highest unique-count) result seen along the way,
	 *  since a page can render partial content before settling. All Browser/
	 *  SWT access happens via {@code display.syncExec} - this method itself
	 *  runs on the JUnit test thread, sleeping between polls (harmless there,
	 *  unlike on the UI thread, which must stay free to pump the event loop
	 *  that actually drives the page's own load/JS). */
	private DeckTextExtractor.CardCount loadAndCount(Display display, Browser browser, String url)
			throws InterruptedException {
		trace("  navigating to " + url);
		display.syncExec(() -> browser.setUrl(url));
		DeckTextExtractor.CardCount best = DeckTextExtractor.countRecognizedCards("");
		String lastText = "";
		int lastTextLen = -1;
		int unchangedPolls = 0;
		for (int i = 0; i < MAX_POLLS; i++) {
			Thread.sleep(POLL_MS);
			AtomicReference<String> currentUrlRef = new AtomicReference<>();
			AtomicReference<DeckTextExtractor.CardCount> countRef = new AtomicReference<>();
			AtomicReference<String> textRef = new AtomicReference<>();
			display.syncExec(() -> {
				String text;
				try {
					Object result = browser.evaluate("return document.body ? document.body.innerText : '';");
					text = (result instanceof String) ? (String) result : "";
				} catch (Exception e) {
					text = "";
				}
				currentUrlRef.set(browser.getUrl());
				textRef.set(text);
				countRef.set(DeckTextExtractor.countRecognizedCards(text));
			});
			DeckTextExtractor.CardCount count = countRef.get();
			lastText = textRef.get();
			int textLen = lastText.length();
			trace("  poll " + (i + 1) + "/" + MAX_POLLS + ": browser is at " + currentUrlRef.get() + ", captured "
					+ textLen + " char(s), total=" + count.total + " unique=" + count.unique);
			if (count.unique > best.unique)
				best = count;
			if (best.unique >= DeckTextExtractor.MIN_MATCHES) {
				trace("  reached MIN_MATCHES, stopping early");
				return best;
			}
			// the captured page hasn't changed at all in a while - it has
			// settled (successfully or not), not "still loading"; polling
			// further at that point can only ever repeat the same result, so
			// stop instead of burning through the rest of MAX_POLLS for
			// nothing (a real run showed this exact waste: TCGplayer
			// plateaued by poll 3 and stayed byte-for-byte identical through
			// poll 10+, yet kept polling all the way to 40) - UNLESS the
			// stable capture is suspiciously small (see MIN_PLATEAU_TEXT_LEN):
			// a real Moxfield run plateaued the entire PLATEAU_POLLS budget on
			// its own literal "Loading Moxfield. This may take a minute..."
			// placeholder and got wrongly written off as settled well before
			// that promised minute was up - a small stable capture is worth
			// the extra wait, a large one is not.
			if (textLen == lastTextLen) {
				if (textLen >= MIN_PLATEAU_TEXT_LEN && ++unchangedPolls >= PLATEAU_POLLS) {
					trace("  captured text unchanged for " + PLATEAU_POLLS + " consecutive polls - the page has "
							+ "settled (or is stuck), not still loading - giving up early");
					break;
				}
			} else {
				unchangedPolls = 0;
				lastTextLen = textLen;
			}
		}
		// a plateau/exhausted MAX_POLLS with too few matches is otherwise a
		// dead end to diagnose from this trace alone - real runs against
		// TCGplayer (plateaued at 3190 chars, 1 match) and Moxfield
		// (plateaued at 44 chars, 0 matches) gave no way to tell a bot-check/
		// consent-wall interstitial apart from a genuinely broken parse
		// without seeing what actually rendered. Dumping it (capped - some
		// sites' loading-shell text alone can run long) turns the next real
		// run's log into hard evidence instead of another guess.
		if (best.unique < DeckTextExtractor.MIN_MATCHES) {
			String snippet = lastText.length() > 1500
					? lastText.substring(0, 1500) + "\n... [truncated, " + lastText.length() + " char(s) total]"
					: lastText;
			trace("  gave up without finding a decklist - captured text at the last poll, to see what actually "
					+ "rendered:\n" + snippet);
		}
		return best;
	}
}
