/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: "Browse Website..." dialog
 *                         for the New Deck/Collection wizard's "Website"
 *                         import source. A real, navigable embedded browser
 *                         (constructed with SWT.EDGE - the WebView2/Chromium
 *                         backend, not this project's older-Eclipse-target
 *                         default of the legacy IE/Trident engine - so
 *                         modern JS-heavy deck sites render correctly), with
 *                         NO LocationAdapter blocking navigation (unlike
 *                         every other Browser usage in this codebase, which
 *                         is a locked-down HTML renderer) - login pages,
 *                         redirects and JS all just work because this is a
 *                         real, unrestricted web view. A configurable
 *                         favorites list (WebFavoritesStore) is both this
 *                         dialog's starting page (first entry) and
 *                         manageable from here (quick "Add current page"
 *                         and a "Manage Favorites..." shortcut into the
 *                         same preference page used for full add/edit/
 *                         remove/reorder). "Import this page" reads the
 *                         live, post-login, post-JS-render DOM text
 *                         (document.body.innerText), runs it through
 *                         DeckTextExtractor to cut the surrounding page
 *                         chrome down to just the decklist-shaped block,
 *                         and hands that back to the caller, which feeds it
 *                         into the exact same text-import pipeline
 *                         Clipboard/File already use.
 *     Rémi Dutil (2026) - takes an initialUrl (the wizard page's own address
 *                         field) and navigates there automatically on open,
 *                         ahead of the favorites' starting page; Back/
 *                         Forward/Reload get tooltips and Back/Forward now
 *                         track browser.isBackEnabled()/isForwardEnabled();
 *                         "Import this page" runs the captured text through
 *                         DeckTextExtractor instead of importing the whole
 *                         page verbatim.
 *     Rémi Dutil (2026) - debug trace: logs the raw captured text length/URL
 *                         and whether a decklist block was found or the full
 *                         page fell back to as-is - needed after a report
 *                         that a real site's page was still "importing
 *                         everything" (see DeckTextExtractor's own trace for
 *                         the actual scan result/extracted preview).
 *     Rémi Dutil (2026) - added a live card-count label, updated whenever
 *                         the browser finishes loading a page
 *                         (ProgressListener#completed) - runs the exact same
 *                         DeckTextExtractor.countRecognizedCards() the
 *                         eventual "Import this page" would, so the user can
 *                         see right away whether the current page is
 *                         parsing correctly, without needing to click
 *                         Import or read the debug log.
 *     Rémi Dutil (2026) - the label showed a bare distinct-row count ("30
 *                         card(s)") that didn't match what actually got
 *                         imported (60 maindeck + 15 sideboard) - it now
 *                         shows "Total N (unique M)", the same convention
 *                         the deck/collection views already use elsewhere in
 *                         ManaDesk, using DeckTextExtractor's new CardCount
 *                         (total physical cards, quantities summed / unique
 *                         distinct names) instead of a plain row count.
 *     Rémi Dutil (2026) - the count label could get stuck at "No decklist
 *                         recognized" (e.g. tcgplayer.com) even though
 *                         Import worked fine moments later: ProgressListener
 *                         #completed only fires once, when the initial page
 *                         load finishes, but some sites keep populating the
 *                         decklist via XHR/fetch afterwards (SPA behavior) -
 *                         document.body.innerText read at that single moment
 *                         was simply too early. Now also polls updateCardCount()
 *                         on a timer every 1500ms while the dialog is open, so
 *                         the label catches up once the page settles, instead
 *                         of relying on a single load-completed snapshot.
 *     Rémi Dutil (2026) - three usability fixes from real use: (1) the
 *                         dialog always started on the first favorite unless
 *                         a URL was already typed into the wizard's address
 *                         field - it now falls back to the last page
 *                         actually imported from (WebFavoritesStore.
 *                         loadLastUrl(), saved on a successful "Import this
 *                         page") before the first favorite, so re-browsing
 *                         resumes where the user left off; (2) the favorites
 *                         combo never reflected which favorite (if any) was
 *                         currently open, only updating on an explicit combo
 *                         selection - it now syncs on every navigation
 *                         (syncFavoritesSelection, called from the same
 *                         LocationListener that already syncs the address
 *                         bar); (3) the combo's width hint was too small to
 *                         read a typical favorite's name - widened from 200
 *                         to 400.
 *     Rémi Dutil (2026) - dropped okPressed()'s MagicLogger.log dump of the
 *                         full raw captured page text - useful while
 *                         designing DeckTextExtractor's parsing against real
 *                         sites, but every real "Import this page" click
 *                         from here on would otherwise write an entire
 *                         page's worth of text to the Eclipse log. The
 *                         short "no decklist block found" fallback notice
 *                         right below stays - real signal, not noise.
 *     Rémi Dutil (2026) - a real TappedOut capture showed a Commander card
 *                         rendered as a pure raster image (the name baked
 *                         into the artwork), nowhere present as real text at
 *                         all - document.body.innerText, and so
 *                         DeckTextExtractor, had genuinely nothing to find
 *                         for it. okPressed()'s capture now runs
 *                         CAPTURE_TEXT_WITH_IMAGE_ALT_SCRIPT instead of a
 *                         plain innerText read - see that constant's own
 *                         header for how it substitutes every image with
 *                         its own alt/title text (standard accessibility
 *                         practice, genuinely site-agnostic, not a
 *                         TappedOut-specific scrape) without touching the
 *                         real, visible page at all.
 *     Rémi Dutil (2026) - updateCardCount()'s live label now also uses
 *                         CAPTURE_TEXT_WITH_IMAGE_ALT_SCRIPT (see that
 *                         constant's own header) - it previously stayed on
 *                         a cheaper plain innerText read, which meant the
 *                         live count could disagree with what "Import this
 *                         page" actually produced (a real capture: the
 *                         label read 109/109 while browsing a deck that
 *                         correctly imported 111 cards, since the
 *                         commander was only recoverable via the image-alt
 *                         substitution the live label wasn't using).
 *     Rémi Dutil (2026) - okPressed() also runs DeckTextExtractor.
 *                         detectDeckMeta() and exposes the result via
 *                         getCapturedTitle()/getCapturedFormat() - the New
 *                         Deck wizard uses these to pre-fill the Name field
 *                         and Default Format combo when browsing a page to
 *                         import, best-effort (both are {@code null} when
 *                         nothing plausible was found).
 *     Rémi Dutil (2026) - added a second live status line (deckInfoLabel) -
 *                         "Detected name: ... | Sideboard: Yes/No" - updated
 *                         on the same 1.5s poll as the card count, so the
 *                         user sees the title/format pre-fill and the
 *                         "Also create a Sideboard" auto-check BEFORE
 *                         clicking "Import this page", not only after. New
 *                         DeckTextExtractor#hasSideboardSection() factors
 *                         out AbstractCardListImportPage's own previously-
 *                         private hasSideboardMarker() check so both this
 *                         live indicator and the wizard's own post-import
 *                         detection share one implementation.
 *     Rémi Dutil (2026) - once a Sideboard is detected, deckInfoLabel also
 *                         shows the main/sideboard split ("Yes (Main: 100,
 *                         Sideboard: 15)") instead of just "Yes" - new
 *                         DeckTextExtractor#countBySection() parses
 *                         extractDeckSection()'s own "Sideboard"/"Deck"
 *                         toggle markers to split the total, per an
 *                         explicit request.
 *     Rémi Dutil (2026) - "Import this page" is no longer the shell's
 *                         default button - JFace's own convention would
 *                         normally make OK the default, but a shell default
 *                         button fires on Enter from ANY control in the
 *                         dialog that doesn't consume it itself, including
 *                         the address bar's own Text - pressing Enter to
 *                         navigate was ALSO immediately closing/importing
 *                         the page. "Go" is the default button instead now,
 *                         and the address bar gets initial keyboard focus,
 *                         so the dialog opens ready to paste a URL and
 *                         press Enter to navigate.
 *     Rémi Dutil (2026) - updateCardCount() also logs DeckTextExtractor#
 *                         listRecognizedCards() (the actual matched "&lt;qty&gt;
 *                         &lt;name&gt; (line N)" rows), on the same
 *                         change-gated log entry as the captured text - a
 *                         real report ("101 cards" against a known 100-card
 *                         decklist) could never be reproduced against any
 *                         real captured text tried by hand, with every
 *                         real-card database built up to try to mirror it;
 *                         the only thing genuinely impossible to reproduce
 *                         outside the running application is the real card
 *                         database itself. This log now shows exactly which
 *                         row is the extra/wrong one directly, next time.
 *     Rémi Dutil (2026) - CAPTURE_TEXT_WITH_IMAGE_ALT_SCRIPT also substitutes
 *                         Moxfield's own card-row links now - a real "Dimir
 *                         Faery" capture (confirmed with a screenshot of the
 *                         actual card and its own HTML) showed a land
 *                         displayed as "Barrow-Downs", this printing's own
 *                         flavor/alternate-art name for the real card Bojuka
 *                         Bog - not a real Scryfall card name at all (checked
 *                         directly against the bulk data), so the DB cross-
 *                         check correctly, silently excluded it, 75 cards
 *                         read as 74. See that constant's own header for the
 *                         fix (reads the real name from the row's own link
 *                         href, e.g. "/cards/xRQDJ-bojuka-bog") and why it's
 *                         scoped to Moxfield's own hostname/CSS class only.
 *******************************************************************************/
package com.reflexit.magiccards.ui.dialogs;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationAdapter;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.ProgressAdapter;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.PreferencesUtil;

import com.reflexit.magiccards.core.MagicLogger;
import com.reflexit.magiccards.core.exports.DeckTextExtractor;
import com.reflexit.magiccards.ui.preferences.WebFavoritesPreferencePage;
import com.reflexit.magiccards.ui.web.WebFavorite;
import com.reflexit.magiccards.ui.web.WebFavoritesStore;

public class BrowseWebsiteDialog extends TitleAreaDialog {
	/** document.body.innerText, but with every &lt;img&gt; substituted (in
	 *  place, preserving its exact position in the text flow) by its own
	 *  alt/title text - some real sites (TappedOut: a Commander card, seen
	 *  in a real capture) render a card as a raster image with the name
	 *  baked into the artwork, nowhere present as real text at all, so no
	 *  amount of DeckTextExtractor parsing logic can recover it from plain
	 *  innerText; a card image's alt text is standard accessibility
	 *  practice and, unlike anything else here, is genuinely site-agnostic -
	 *  no per-site scraping, matching this whole feature's own design
	 *  principle (see this class' own header). Works by cloning body's
	 *  children into a detached wrapper (never the live page - nothing the
	 *  user sees is touched), replacing each &lt;img&gt; with a text node,
	 *  then reading THAT wrapper's own innerText - innerText needs real
	 *  layout to compute correctly, so the wrapper is briefly appended
	 *  off-screen (fixed position, far off the left edge) rather than left
	 *  detached, then removed immediately after. Also used by the live
	 *  card-count polling below (every 1.5s while the dialog is open) -
	 *  originally kept on a cheaper plain innerText read there instead, to
	 *  avoid a clone+reflow on every poll, but that meant the live label
	 *  could quietly disagree with what "Import this page" actually
	 *  produces (a real TappedOut Commander card was invisible to the
	 *  cheaper read, undercounting by exactly one) - a label that doesn't
	 *  match the real import is worse than a slightly heavier poll. An
	 *  image with no alt/title text at all is left untouched (nothing to
	 *  substitute); an
	 *  unrelated image's alt text (a logo, an icon) becomes an ordinary bare
	 *  line, no different from any other stray page-chrome text already
	 *  handled by DeckTextExtractor's own DB cross-check. Public: reused
	 *  as-is by BrowseWebsiteLiveTest (a different bundle) rather
	 *  than risking the two copies drifting apart.
	 *
	 *  <p>Also substitutes Moxfield's own card-row links, for a real,
	 *  different reason than the image-alt case above: a real capture (a
	 *  Pauper "Dimir Faery" deck) showed a land displayed as "Barrow-Downs"
	 *  - not a real Magic card name at all, but this printing's own
	 *  flavor/alternate-art name (Scryfall's own {@code flavor_name} field)
	 *  for the real card, Bojuka Bog; the DB cross-check correctly refused
	 *  to count it (confirmed directly against the real Scryfall bulk data:
	 *  no card is named "Barrow-Downs"), undercounting the deck by exactly
	 *  one (75 read as 74). The real name is recoverable, just not from the
	 *  displayed text: a card row's own link target encodes it, e.g.
	 *  {@code href="/cards/xRQDJ-bojuka-bog"} (confirmed from a real
	 *  capture's own HTML, inspected in a browser) - the hash Moxfield
	 *  assigns this printing, then a dash, then the real card's name in
	 *  kebab-case. Scoped to Moxfield's own hostname and its own
	 *  {@code table-deck-row-link} class (unlike the image-alt case, this
	 *  "/cards/&lt;hash&gt;-&lt;slug&gt;" URL shape is Moxfield's own router
	 *  convention, not a universal web standard - a different site's
	 *  unrelated link just happening to look similar must never have its
	 *  text silently rewritten) - see BrowseWebsiteLiveTest's own
	 *  dedicated test for this. Only substitutes when the derived name
	 *  actually differs from what's displayed (a cheap, approximate
	 *  lowercase/alphanumeric-only compare - good enough to decide "is this
	 *  worth substituting", not required to be exact, since
	 *  DeckTextExtractor's own norm() does the real, precise comparison
	 *  afterward either way), so the overwhelming majority of ordinary,
	 *  non-flavor-named card rows are left completely untouched. */
	public static final String CAPTURE_TEXT_WITH_IMAGE_ALT_SCRIPT = "return (function() {" //
			+ "if (!document.body) return '';" //
			+ "var wrapper = document.createElement('div');" //
			+ "var clone = document.body.cloneNode(true);" //
			+ "while (clone.firstChild) wrapper.appendChild(clone.firstChild);" //
			+ "if (location.hostname.indexOf('moxfield.com') !== -1) {" //
			+ "  var cardLinks = wrapper.querySelectorAll('a.table-deck-row-link[href^=\"/cards/\"]');" //
			+ "  for (var k = 0; k < cardLinks.length; k++) {" //
			+ "    var link = cardLinks[k];" //
			+ "    var slugMatch = (link.getAttribute('href') || '').match(/^\\/cards\\/[^\\/-]+-(.+)$/);" //
			+ "    if (!slugMatch) continue;" //
			+ "    var derived = slugMatch[1].split('-').map(function(w) {" //
			+ "      return w.charAt(0).toUpperCase() + w.slice(1);" //
			+ "    }).join(' ');" //
			+ "    var displayed = (link.textContent || '').trim();" //
			+ "    var normDisplayed = displayed.toLowerCase().replace(/[^a-z0-9]/g, '');" //
			+ "    var normDerived = derived.toLowerCase().replace(/[^a-z0-9]/g, '');" //
			+ "    if (normDisplayed && normDerived && normDisplayed !== normDerived) link.textContent = derived;" //
			+ "  }" //
			+ "}" //
			+ "var imgs = wrapper.getElementsByTagName('img');" //
			+ "for (var i = imgs.length - 1; i >= 0; i--) {" //
			+ "  var img = imgs[i];" //
			+ "  var alt = (img.getAttribute('alt') || img.getAttribute('title') || '').trim();" //
			+ "  if (alt && img.parentNode) img.parentNode.replaceChild(document.createTextNode(alt), img);" //
			+ "}" //
			+ "wrapper.style.position = 'fixed';" //
			+ "wrapper.style.top = '0';" //
			+ "wrapper.style.left = '-99999px';" //
			+ "wrapper.style.pointerEvents = 'none';" //
			+ "document.body.appendChild(wrapper);" //
			+ "var out = wrapper.innerText;" //
			+ "document.body.removeChild(wrapper);" //
			+ "return out;" //
			+ "})();";

	private final String initialUrl;
	private Browser browser;
	private Text addressText;
	private Combo favoritesCombo;
	private org.eclipse.swt.widgets.Button goButton;
	private org.eclipse.swt.widgets.Button backButton;
	private org.eclipse.swt.widgets.Button forwardButton;
	private org.eclipse.swt.widgets.Button addFavoriteButton;
	private org.eclipse.swt.widgets.Button manageFavoritesButton;
	private Label cardCountLabel;
	private Label deckInfoLabel;
	private List<WebFavorite> favorites = new ArrayList<>();
	private String capturedText;
	private String capturedUrl;
	private String capturedTitle;
	private String capturedFormat;
	private String lastLoggedCardCountText;

	/** @param initialUrl the address to navigate to as soon as the dialog opens
	 *  (the wizard page's own address field) - takes priority over the
	 *  favorites list's starting page; may be null/empty. */
	public BrowseWebsiteDialog(Shell parentShell, String initialUrl) {
		super(parentShell);
		this.initialUrl = initialUrl;
		setShellStyle(getShellStyle() | SWT.RESIZE | SWT.MAX);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		getShell().setText("Browse Website");
		setTitle("Browse to the deck's page");
		setMessage("Navigate (and log in, if the site needs it) until the deck list is visible, then click \"Import this page\".");
		Composite area = (Composite) super.createDialogArea(parent);
		area.setLayout(GridLayoutFactory.fillDefaults().margins(5, 5).create());
		area.setLayoutData(GridDataFactory.fillDefaults().grab(true, true).hint(900, 650).create());

		createToolbarRow(area);
		createBrowser(area);
		createFavoritesRow(area);
		createStatusRow(area);
		startCardCountPolling();

		refreshFavorites();
		if (browser != null) {
			String startUrl = null;
			if (initialUrl != null && !initialUrl.isEmpty())
				startUrl = initialUrl;
			else {
				String lastUrl = WebFavoritesStore.loadLastUrl();
				if (lastUrl != null && !lastUrl.isEmpty())
					startUrl = lastUrl;
				else if (!favorites.isEmpty())
					startUrl = favorites.get(0).getUrl();
			}
			if (startUrl != null) {
				addressText.setText(startUrl);
				navigateTo(startUrl);
			}
		}
		addressText.setFocus();

		return area;
	}

	private void createToolbarRow(Composite parent) {
		Composite row = new Composite(parent, SWT.NONE);
		row.setLayout(GridLayoutFactory.fillDefaults().numColumns(5).create());
		row.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).create());

		backButton = new org.eclipse.swt.widgets.Button(row, SWT.PUSH);
		backButton.setText("<");
		backButton.setToolTipText("Back");
		backButton.setEnabled(false);
		backButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (browser != null)
					browser.back();
			}
		});

		forwardButton = new org.eclipse.swt.widgets.Button(row, SWT.PUSH);
		forwardButton.setText(">");
		forwardButton.setToolTipText("Forward");
		forwardButton.setEnabled(false);
		forwardButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (browser != null)
					browser.forward();
			}
		});

		org.eclipse.swt.widgets.Button reload = new org.eclipse.swt.widgets.Button(row, SWT.PUSH);
		reload.setText("Reload");
		reload.setToolTipText("Reload this page");
		reload.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (browser != null)
					browser.refresh();
			}
		});

		addressText = new Text(row, SWT.BORDER);
		addressText.setToolTipText("Paste or type the deck's page address here, or navigate using the browser below");
		addressText.setMessage("Paste or type a URL, or pick a Favorite");
		addressText.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).create());
		addressText.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				navigateTo(addressText.getText());
			}
		});

		goButton = new org.eclipse.swt.widgets.Button(row, SWT.PUSH);
		goButton.setText("Go");
		goButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				navigateTo(addressText.getText());
			}
		});
	}

	private void createBrowser(Composite parent) {
		Composite browserArea = new Composite(parent, SWT.NONE);
		browserArea.setLayout(GridLayoutFactory.fillDefaults().create());
		browserArea.setLayoutData(GridDataFactory.fillDefaults().grab(true, true).create());
		try {
			browser = new Browser(browserArea, SWT.EDGE);
		} catch (SWTError e) {
			browser = null;
		}
		if (browser != null) {
			browser.setLayoutData(GridDataFactory.fillDefaults().grab(true, true).create());
			browser.addLocationListener(new LocationAdapter() {
				@Override
				public void changed(LocationEvent event) {
					if (event.top && addressText != null && !addressText.isDisposed())
						addressText.setText(event.location);
					if (backButton != null && !backButton.isDisposed())
						backButton.setEnabled(browser.isBackEnabled());
					if (forwardButton != null && !forwardButton.isDisposed())
						forwardButton.setEnabled(browser.isForwardEnabled());
					if (event.top)
						syncFavoritesSelection(event.location);
				}
			});
			browser.addProgressListener(new ProgressAdapter() {
				@Override
				public void completed(ProgressEvent event) {
					updateCardCount();
				}
			});
		} else {
			Label msg = new Label(browserArea, SWT.WRAP);
			msg.setText("The embedded web browser (Microsoft Edge WebView2) is not available on this "
					+ "machine, so websites cannot be browsed here. Install the WebView2 Runtime, or use "
					+ "Import from Clipboard/File instead.");
			msg.setLayoutData(GridDataFactory.fillDefaults().grab(true, true).create());
		}
	}

	private void createFavoritesRow(Composite parent) {
		Composite row = new Composite(parent, SWT.NONE);
		row.setLayout(GridLayoutFactory.fillDefaults().numColumns(3).create());
		row.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).create());

		favoritesCombo = new Combo(row, SWT.READ_ONLY | SWT.DROP_DOWN);
		favoritesCombo.setLayoutData(GridDataFactory.fillDefaults().hint(400, SWT.DEFAULT).create());
		favoritesCombo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				int index = favoritesCombo.getSelectionIndex();
				if (index >= 0 && index < favorites.size())
					navigateTo(favorites.get(index).getUrl());
			}
		});

		addFavoriteButton = new org.eclipse.swt.widgets.Button(row, SWT.PUSH);
		addFavoriteButton.setText("Add current page to Favorites");
		addFavoriteButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				addCurrentPageToFavorites();
			}
		});

		manageFavoritesButton = new org.eclipse.swt.widgets.Button(row, SWT.PUSH);
		manageFavoritesButton.setText("Manage Favorites...");
		manageFavoritesButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				manageFavorites();
			}
		});

		if (browser == null) {
			addFavoriteButton.setEnabled(false);
			manageFavoritesButton.setEnabled(false);
			favoritesCombo.setEnabled(false);
		}
	}

	private void createStatusRow(Composite parent) {
		cardCountLabel = new Label(parent, SWT.NONE);
		cardCountLabel.setText(browser == null ? "" : "Waiting for the page to load...");
		cardCountLabel.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).create());
		deckInfoLabel = new Label(parent, SWT.NONE);
		deckInfoLabel.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).create());
	}

	private static final int CARD_COUNT_POLL_MS = 1500;

	/** Keeps re-checking the card count while the dialog is open, since
	 *  ProgressListener#completed alone only catches the initial page load -
	 *  SPA sites (e.g. tcgplayer.com) keep filling in the decklist via
	 *  XHR/fetch afterwards, and the label needs to catch up with that. */
	private void startCardCountPolling() {
		if (browser == null || cardCountLabel == null || cardCountLabel.isDisposed())
			return;
		updateCardCount();
		cardCountLabel.getDisplay().timerExec(CARD_COUNT_POLL_MS, new Runnable() {
			@Override
			public void run() {
				if (cardCountLabel == null || cardCountLabel.isDisposed())
					return;
				updateCardCount();
				cardCountLabel.getDisplay().timerExec(CARD_COUNT_POLL_MS, this);
			}
		});
	}

	/** Runs the same DeckTextExtractor scan "Import this page" would, and
	 *  shows the result - a live sanity-check the user can read while
	 *  browsing, without needing to click Import or read the debug log. Uses
	 *  {@link #CAPTURE_TEXT_WITH_IMAGE_ALT_SCRIPT} - the same capture
	 *  okPressed() uses - so this count actually matches what "Import this
	 *  page" would produce (a real TappedOut Commander deck's own card was
	 *  otherwise invisible to a plain innerText read here, so this label
	 *  under-counted by exactly one and never agreed with the real import).
	 *  The clone+reflow this costs on every 1.5s poll was a deliberate
	 *  simplicity/accuracy tradeoff at first - a live count that quietly
	 *  disagrees with the actual import is worse than a slightly heavier
	 *  poll. */
	private void updateCardCount() {
		if (browser == null || cardCountLabel == null || cardCountLabel.isDisposed())
			return;
		String text;
		try {
			Object result = browser.evaluate(CAPTURE_TEXT_WITH_IMAGE_ALT_SCRIPT);
			text = (result instanceof String) ? (String) result : "";
		} catch (Exception e) {
			text = "";
		}
		if (!text.equals(lastLoggedCardCountText)) {
			lastLoggedCardCountText = text;
			MagicLogger.log("BrowseWebsiteDialog: [DIAGNOSTIC] captured text (" + text.length() + " chars):\n" + text);
			// the matched-card LIST, not just the bare count - a real report
			// ("101 cards" against a known 100-card decklist) could never be
			// reproduced against any captured text tried by hand outside the
			// running application (the real card database has tens of
			// thousands of entries, impossible to fully mirror in a test);
			// logging exactly which rows matched lets the extra/wrong one be
			// spotted directly from this log instead.
			List<String> matchedCards = DeckTextExtractor.listRecognizedCards(text);
			MagicLogger.log("BrowseWebsiteDialog: [DIAGNOSTIC] " + matchedCards.size() + " matched row(s):\n"
					+ String.join("\n", matchedCards));
		}
		DeckTextExtractor.CardCount count = DeckTextExtractor.countRecognizedCards(text);
		if (count.unique >= DeckTextExtractor.MIN_MATCHES)
			cardCountLabel.setText("Total " + count.total + " (unique " + count.unique + ") card(s) recognized on this page");
		else if (count.unique > 0)
			cardCountLabel.setText("Total " + count.total + " (unique " + count.unique + ") card(s) recognized - not "
					+ "enough yet (need " + DeckTextExtractor.MIN_MATCHES + " unique), the whole page would be "
					+ "imported instead");
		else
			cardCountLabel.setText("No decklist recognized on this page yet");
		if (deckInfoLabel != null && !deckInfoLabel.isDisposed()) {
			DeckTextExtractor.DeckMeta meta = DeckTextExtractor.detectDeckMeta(text);
			String extracted = DeckTextExtractor.extractDeckSection(text);
			boolean hasSideboard = DeckTextExtractor.hasSideboardSection(extracted);
			String sideboardPart;
			if (hasSideboard) {
				DeckTextExtractor.SectionCounts bySection = DeckTextExtractor.countBySection(extracted);
				sideboardPart = "Yes (Main: " + bySection.mainTotal + ", Sideboard: " + bySection.sideboardTotal + ")";
			} else {
				sideboardPart = "No";
			}
			deckInfoLabel.setText("Detected name: " + (meta.title != null ? meta.title : "(not detected)")
					+ "   |   Sideboard: " + sideboardPart);
		}
	}

	private void navigateTo(String url) {
		if (browser == null || url == null)
			return;
		String trimmed = url.trim();
		if (trimmed.isEmpty())
			return;
		if (!trimmed.contains("://"))
			trimmed = "https://" + trimmed;
		browser.setUrl(trimmed);
	}

	private void refreshFavorites() {
		favorites = WebFavoritesStore.load();
		if (favoritesCombo == null || favoritesCombo.isDisposed())
			return;
		favoritesCombo.removeAll();
		for (WebFavorite f : favorites)
			favoritesCombo.add(f.getName());
	}

	/** Selects the matching entry in the favorites combo when the page now
	 *  showing is one of the user's favorites (exact URL match), so it is
	 *  clear at a glance which favorite (if any) is currently open - called
	 *  from the same LocationListener that already syncs the address bar, so
	 *  this stays correct through every kind of navigation (initial load,
	 *  the combo itself, typing a URL, Back/Forward, links on the page).
	 *  Clears the selection when the current page doesn't match any. */
	private void syncFavoritesSelection(String url) {
		if (favoritesCombo == null || favoritesCombo.isDisposed())
			return;
		for (int i = 0; i < favorites.size(); i++) {
			if (favorites.get(i).getUrl().equals(url)) {
				favoritesCombo.select(i);
				return;
			}
		}
		favoritesCombo.deselectAll();
	}

	private void addCurrentPageToFavorites() {
		if (browser == null)
			return;
		String currentUrl = browser.getUrl();
		if (currentUrl == null || currentUrl.isEmpty() || "about:blank".equals(currentUrl))
			return;
		String suggested = currentUrl;
		try {
			Object title = browser.evaluate("return document.title;");
			if (title instanceof String && !((String) title).isEmpty())
				suggested = (String) title;
		} catch (Exception e) {
			// ignore - fall back to the URL as the suggested name
		}
		InputDialog dialog = new InputDialog(getShell(), "Add to Favorites", "Name for this favorite:", suggested,
				null);
		if (dialog.open() == Window.OK) {
			List<WebFavorite> current = WebFavoritesStore.load();
			current.add(new WebFavorite(dialog.getValue().trim(), currentUrl));
			WebFavoritesStore.save(current);
			refreshFavorites();
		}
	}

	private void manageFavorites() {
		String id = WebFavoritesPreferencePage.ID;
		PreferenceDialog dialog = PreferencesUtil.createPreferenceDialogOn(getShell(), id, new String[] { id }, null);
		if (dialog != null)
			dialog.open();
		refreshFavorites();
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		// "Import this page" is NOT the shell's default button (the final
		// false, where JFace's own convention would normally pass true for
		// OK) - this dialog's whole point is browsing/navigating first, and
		// a shell default button fires on Enter from ANY control in the
		// dialog that doesn't itself consume it, including the address
		// bar's own Text - with OK as default, pressing Enter to navigate
		// was ALSO immediately closing/importing the page instead. "Go" is
		// set as the default button below instead, once it exists.
		createButton(parent, IDialogConstants.OK_ID, "Import this page", false);
		createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
		if (browser == null)
			getButton(IDialogConstants.OK_ID).setEnabled(false);
		if (goButton != null && !goButton.isDisposed())
			parent.getShell().setDefaultButton(goButton);
	}

	@Override
	protected void okPressed() {
		Object result;
		try {
			result = browser.evaluate(CAPTURE_TEXT_WITH_IMAGE_ALT_SCRIPT);
		} catch (Exception e) {
			setErrorMessage("Could not read the page's text: " + e.getMessage());
			return;
		}
		String text = (result instanceof String) ? (String) result : "";
		if (text.trim().isEmpty()) {
			setErrorMessage("The current page has no visible text yet - wait for it to finish loading, "
					+ "or navigate to the deck's page first.");
			return;
		}
		capturedUrl = browser.getUrl();
		WebFavoritesStore.saveLastUrl(capturedUrl);
		// cut the surrounding page chrome (nav, ads, comments, related decks)
		// down to just the decklist-shaped block - fall back to the whole
		// page if nothing looks sufficiently deck-shaped, rather than lose it
		String deckSection = DeckTextExtractor.extractDeckSection(text);
		capturedText = deckSection != null ? deckSection : text;
		if (deckSection == null)
			MagicLogger.log("BrowseWebsiteDialog: no decklist block found - importing the full page text ("
					+ text.length() + " char(s)) as-is");
		DeckTextExtractor.DeckMeta meta = DeckTextExtractor.detectDeckMeta(text);
		capturedTitle = meta.title;
		capturedFormat = meta.format;
		super.okPressed();
	}

	public String getCapturedText() {
		return capturedText;
	}

	public String getCapturedUrl() {
		return capturedUrl;
	}

	/** The page's own deck title, best-effort extracted from the captured
	 *  text (see {@link DeckTextExtractor#detectDeckMeta}) - {@code null} if
	 *  nothing plausible was found. Used to pre-fill the wizard's Name field
	 *  when it's still empty. */
	public String getCapturedTitle() {
		return capturedTitle;
	}

	/** The page's own stated constructed format ("Modern", "Commander", ...),
	 *  best-effort extracted from the captured text - {@code null} if
	 *  nothing plausible was found, in which case the wizard's Default
	 *  Format combo keeps its own "Standard" default rather than being
	 *  overwritten with a guess. */
	public String getCapturedFormat() {
		return capturedFormat;
	}
}
