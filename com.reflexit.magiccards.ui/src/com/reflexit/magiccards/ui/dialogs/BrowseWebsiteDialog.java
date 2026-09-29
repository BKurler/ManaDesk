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
	private final String initialUrl;
	private Browser browser;
	private Text addressText;
	private Combo favoritesCombo;
	private org.eclipse.swt.widgets.Button backButton;
	private org.eclipse.swt.widgets.Button forwardButton;
	private org.eclipse.swt.widgets.Button addFavoriteButton;
	private org.eclipse.swt.widgets.Button manageFavoritesButton;
	private Label cardCountLabel;
	private List<WebFavorite> favorites = new ArrayList<>();
	private String capturedText;
	private String capturedUrl;

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

		org.eclipse.swt.widgets.Button go = new org.eclipse.swt.widgets.Button(row, SWT.PUSH);
		go.setText("Go");
		go.addSelectionListener(new SelectionAdapter() {
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
	 *  browsing, without needing to click Import or read the debug log. */
	private void updateCardCount() {
		if (browser == null || cardCountLabel == null || cardCountLabel.isDisposed())
			return;
		String text;
		try {
			Object result = browser.evaluate("return document.body ? document.body.innerText : '';");
			text = (result instanceof String) ? (String) result : "";
		} catch (Exception e) {
			text = "";
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
		createButton(parent, IDialogConstants.OK_ID, "Import this page", true);
		createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
		if (browser == null)
			getButton(IDialogConstants.OK_ID).setEnabled(false);
	}

	@Override
	protected void okPressed() {
		Object result;
		try {
			result = browser.evaluate("return document.body ? document.body.innerText : '';");
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
		// (DeckTextExtractor itself logs the scan result/preview)
		String deckSection = DeckTextExtractor.extractDeckSection(text);
		capturedText = deckSection != null ? deckSection : text;
		if (deckSection == null)
			MagicLogger.log("BrowseWebsiteDialog: no decklist block found - importing the full page text ("
					+ text.length() + " char(s)) as-is");
		super.okPressed();
	}

	public String getCapturedText() {
		return capturedText;
	}

	public String getCapturedUrl() {
		return capturedUrl;
	}
}
