/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *     Rémi Dutil (2026) - "What's New": the list of features added since 0.9.7,
 *                         grouped by topic. Help > What's New... shows it all,
 *                         any time. After an upgrade that brings new entries
 *                         (FEATURES_VERSION bumped), the first start adds the
 *                         new views (Proxier, Buyer) to the restored layout and
 *                         pops up the unseen entries once. A clean install
 *                         never pops it up.
 *     Rémi Dutil (2026) - major vs minor: each release lists its "Highlights"
 *                         (bold, more room) before "Also new" (by topic).
 *     Rémi Dutil (2026) - new views are placed by ViewZones (in their zone)
 *     Rémi Dutil (2026) - entries for collection types, ownership following
 *                         the list, and the "Main" collection
 *     Rémi Dutil (2026) - "Set lists" entry (alphabetical / release date)
 *     Rémi Dutil (2026) - "Report a bug or request a feature" entry (+ by email,
 *                         webmail, log file)
 *     Rémi Dutil (2026) - Export entry: printable sheets, append, page count
 *     Rémi Dutil (2026) - "Symbols and keywords stay current" entry
 *     Rémi Dutil (2026) - "More card facts" entry
 *     Rémi Dutil (2026) - "ManaBox" entry (import / export)
 *     Rémi Dutil (2026) - "New help" and "Deck statistics" entries, Export entry
 *                         mentions the printable preview
 *     Rémi Dutil (2026) - "Extra list" and "Collector" entries; Proxies, Filters
 *                         and sorting, Printings and Instances entries updated
 *     Rémi Dutil (2026) - Export entry: the Export tab follows the Cards tab's
 *                         sort and filter
 *     Rémi Dutil (2026) - Filters and sorting (several levels, each deck and
 *                         collection keeps its own), Collector (faster), new
 *                         Gallery and Linux entries
 *******************************************************************************/
package com.reflexit.magiccards_rcp;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.preference.IPersistentPreferenceStore;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.views.IViewDescriptor;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.utils.WaitUtils;

/**
 * The "What's New" list. Each {@link Feature} carries the
 * {@link #FEATURES_VERSION} it arrived with; the last version the user has seen
 * is kept in the preferences.
 * <p>
 * To announce new features in a release: add them to {@link #FEATURES} with
 * {@code since = FEATURES_VERSION + 1}, bump {@link #FEATURES_VERSION} and add
 * the release's heading to {@link #RELEASES}.
 */
final class WhatsNew {
	/** Bump whenever {@link #FEATURES} gets new entries. */
	static final int FEATURES_VERSION = 1;
	/** The heading of each features version, index = version - 1. */
	private static final String[] RELEASES = { "New since ManaDesk 0.9.7" };
	private static final String SEEN_KEY = "whatsNew.seenVersion";

	private static final String PROXIER_VIEW = "com.reflexit.magiccards.ui.views.proxier.ProxierView";
	private static final String BUYER_VIEW = "com.manadesk.monetization.BuyerView";

	private static final boolean MAJOR = true;
	private static final boolean MINOR = false;

	private static final String TABS = "New tabs";
	private static final String CARDS = "Your cards";
	private static final String DECKS = "Decks and collections";
	private static final String IMPORT = "Import and export";
	private static final String DATABASE = "Card database and prices";
	private static final String BROWSING = "Searching and browsing";
	private static final String GENERAL = "General";

	/** One feature: a short title and one or two lines of text. */
	private static final class Feature {
		final int since;
		/** A major addition (listed under "Highlights") or a smaller improvement ("Also new"). */
		final boolean major;
		final String group;
		final String title;
		final String text;
		/** A new view, added to the layout on upgrade (shown with its icon); else {@code null}. */
		final String viewId;

		Feature(int since, boolean major, String group, String title, String text, String viewId) {
			this.since = since;
			this.major = major;
			this.group = group;
			this.title = title;
			this.text = text;
			this.viewId = viewId;
		}
	}

	private static final Feature[] FEATURES = {
			// --- since 0.9.7
			new Feature(1, MAJOR, TABS, "Proxier", "Check several decks at once: what you own, what boxed decks already"
					+ " use, and which proxies to print.", PROXIER_VIEW),
			new Feature(1, MAJOR, TABS, "Buyer", "What your decks still need, with prices, ready to paste into TCGplayer,"
					+ " Cardmarket and other stores.", BUYER_VIEW),
			new Feature(1, MAJOR, TABS, "Accessories", "In every deck and collection: the tokens, emblems, counters,"
					+ " markers and dice your cards need.", null),
			new Feature(1, MAJOR, CARDS, "Finish", "Non-foil, foil and etched copies are tracked separately, each with its"
					+ " own price.", null),
			new Feature(1, MINOR, CARDS, "Condition", "Grade your copies, from Near Mint to Damaged.", null),
			new Feature(1, MINOR, CARDS, "Proxies", "Mark copies as proxies, shown with a red Proxy badge - collection"
					+ " value, completion and owned counts leave them out (unless you turn on Count Proxies in the"
					+ " Collector), a deck's info shows how many it has and what replacing them would cost, and the"
					+ " Legality tab warns when a legal deck uses proxies.",
					null),
			new Feature(1, MINOR, CARDS, "Quick edits", "Change the set or collector number right in the list, and"
					+ " \"Split & move to\" moves part of a pile to another deck or collection.", null),
			new Feature(1, MINOR, CARDS, "Unsorted boxes", "Card positions in unsorted collections: find a card without"
					+ " sorting the box first.", null),
			new Feature(1, MINOR, CARDS, "Safer deleting", "Deleting cards asks for a confirmation first.", null),
			new Feature(1, MINOR, DECKS, "Owned decks", "Decks stay virtual by default. Uncheck Virtual when you create"
					+ " a deck to make an owned deck, for cards you really own: it starts empty (no import) and you move"
					+ " owned cards into it from your Standard or For Trade collections. Adding from the database, copying,"
					+ " pasting, importing, changing a count and deleting cards are refused in it - cards only leave by"
					+ " being moved out - so the number of cards you own never changes by mistake. Its sideboard and extra"
					+ " list are owned too, and the Proxier and the Buyer leave it out and count its cards as in use."
					+ " The choice is made at creation and cannot be changed.", null),
			new Feature(1, MINOR, CARDS, "More card facts", "A new Traits column (Reserved List, Game Changer,"
					+ " Reprint, and how a printing looks: Showcase, Borderless, Full Art, an old frame, a stamp, Oversized,"
					+ " a watermark, Prerelease, Playtest, Surge Foil...; the card text lists them too), an EDHREC Rank column to sort by popularity, a Produced Mana column (also a \"Produced\""
					+ " choice next to Identity and Extended Identity in the Colors filter) and, in the card text under"
					+ " the finishes, those traits plus the mana the card produces; search the traits"
					+ " with brackets ([reserved], [showcase], [full art]...; type \"[\" in the Text field for suggestions, which no longer pop up while you type plain words). The Extended Color Identity is now"
					+ " Scryfall's identity plus the colors a card can fetch (fetch lands, Farseek...) or shows in its"
					+ " reminder text (Extort...), no longer a guess from the text. A deck's Info tab lists its Game Changers and its mana sources. The Printings view"
					+ " now finds a card's printings by Scryfall's own id, not by its name. A card's Color is now"
					+ " Scryfall's own (a color indicator counts, a devoid card or a land is colorless), and your decks"
					+ " and collections follow cards Scryfall merged into others. Update the card database once to"
					+ " fill it all in.", null),
			new Feature(1, MINOR, CARDS, "Color identity", "Now taken straight from Scryfall, so it is always"
					+ " right (it used to be guessed from the card text, which got some cards wrong), and groups"
					+ " show all their colors. New: an Extended Color Identity column and filter that also catch"
					+ " cards that only touch a color, like a fetch land that finds a Forest.", null),
			new Feature(1, MAJOR, DECKS, "Decks and collections apart", "Each with its own New Deck / New Collection"
					+ " wizard; add a deck's sideboard and extra lists from its properties.", null),
			new Feature(1, MAJOR, DECKS, "Collection types", "Each collection is Standard, For Trade or Wishlist/To"
					+ " Print. Wishlist/To Print holds the cards to buy or the proxies to print; moving one to a"
					+ " Standard collection marks it as owned.", null),
			new Feature(1, MAJOR, DECKS, "Formats and legality", "Pick a deck's format: legality now covers every"
					+ " format (Brawl, Oathbreaker, Pauper Commander, Old School...), and commander decks are"
					+ " checked against the commander's color identity. The Legality tab shows its verdict in"
					+ " green (legal), yellow (legal, with proxies) or red (not legal).", null),
			new Feature(1, MINOR, DECKS, "Status at a glance", "Small colored markers on deck tabs, in the"
					+ " navigator and in the dialogs: virtual (blue), read-only (red), unsorted (green) and boxed"
					+ " (black). Moving or copying cards where they can't go is blocked.", null),
			new Feature(1, MINOR, DECKS, "Boxed decks", "Mark a built deck as boxed: its cards count as in use,"
					+ " so the Proxier and Buyer don't count on them for other decks. ManaDesk asks you to confirm,"
					+ " because boxed means every card of the deck is really in the box.", null),
			new Feature(1, MINOR, CARDS, "Columns stay in place", "A deck, a collection or My Cards now shows the same"
					+ " columns in every View As (table, tree, split tree): the split tree used the card database's"
					+ " columns, without Count, Ownership or Condition, and reshuffled the ones you chose. Count stays"
					+ " first, except in a tree, whose first column is always Name (Restore Defaults once on the"
					+ " column page if yours were already shuffled).", null),
			new Feature(1, MINOR, DECKS, "Ownership follows the list", "A card is owned in a Standard or For Trade"
					+ " collection and virtual in a deck or a Wishlist/To Print collection - no need to set it card"
					+ " by card. Imports follow the destination too, and virtual cards imported into a collection"
					+ " are marked as proxies.", null),
			new Feature(1, MINOR, DECKS, "Deck statistics", "The average mana cost counts the main deck only,"
					+ " like the Mana and Types tabs; cleaner percentages; and the Export tab shows text formats"
					+ " line by line.", null),
			new Feature(1, MINOR, DECKS, "Extra list", "A deck's extra list is created with the tokens, emblems and"
					+ " markers the deck needs, at count 0. After you change the deck, Refresh (F5) in the extra list"
					+ " adds what is now needed and removes what is not - the counts you set are kept.", null),
			new Feature(1, MINOR, DECKS, "Main collection", "Renamed \"Main\"; it is always there, sorted and"
					+ " writable.", null),
			new Feature(1, MAJOR, IMPORT, "Import from a website", "Moxfield, Archidekt, TappedOut, MTGGoldfish, TCGplayer"
					+ " and more - or any card list from the clipboard or a file, with a preview to fix unknown"
					+ " cards.", null),
			new Feature(1, MAJOR, IMPORT, "ManaBox", "Already using the ManaBox app? Import its CSV exports into a"
					+ " collection: each card keeps its exact printing, finish, condition,"
					+ " purchase price, proxy and misprint / altered / signed marks, and its language when"
					+ " ManaDesk knows that printing. Decks and collections can also be exported for ManaBox.",
					null),
			new Feature(1, MINOR, IMPORT, "Export", "Several decks at once, in one file or one per deck, a printable"
					+ " sideboard list, and export formats you can edit with a live preview. Proxies and"
					+ " sideboard lists print at 100%, 9 per page, on the same sheets; \"Append to the existing"
					+ " file\" fills a sheet you already exported (or a CSV with the same columns), and the"
					+ " preview counts the pages. A deck's or collection's Export tab now follows the Cards"
					+ " tab's sort (several columns) and filter, so you save the list as you see it; saving over a"
					+ " file offers Overwrite or Append.", null),
			new Feature(1, MAJOR, DATABASE, "Card database from Scryfall", "Downloaded on first start (or imported from a"
					+ " file when offline), a full update in under a minute, and a backup before each update.",
					null),
			new Feature(1, MINOR, DATABASE, "Symbols and keywords stay current", "Updating the card database also"
					+ " refreshes the mana symbols, keyword abilities, ability words and card types from Scryfall, so"
					+ " new ones appear without a new ManaDesk release. The Abilities tab and the [ability] search now use"
					+ " the abilities Scryfall lists for each card - keyword abilities, ability words and keyword"
					+ " actions (Scry, Surveil...) - so a card that only mentions the word no longer counts. Update"
					+ " the card database once to fill them in.", null),
			new Feature(1, MAJOR, DATABASE, "Prices", "From TCGplayer (US$) or Cardmarket (€), non-foil and foil; the"
					+ " card info links to both stores.", null),
			new Feature(1, MINOR, BROWSING, "Filters and sorting", "A filter dialog with tabs and ranges, quick filters in"
					+ " My Cards, sort by several columns, and column sizes are remembered. Restore Defaults on a"
					+ " view's preference page also resets its grouping and sorting. Each deck and collection"
					+ " remembers its own View As, grouping, sort and filter, even after a restart; columns stay"
					+ " the same for all decks, and for all collections.", null),
			new Feature(1, MINOR, BROWSING, "Collector", "Opens much faster, grouped by set by default, sets in"
					+ " alphabetical order; Count Proxies and Count Finishes Separately are also on its preference"
					+ " page.", null),
			new Feature(1, MINOR, BROWSING, "Gallery", "Scrolls on to the end of long lists without getting stuck,"
					+ " and keeps the selected card in view.", null),
			new Feature(1, MINOR, BROWSING, "Printings and Instances", "Sortable, with their own choice of columns;"
					+ " Instances shows Name, Count, Set, Num and Location first.", null),
			new Feature(1, MINOR, BROWSING, "Rulings", "Shown formatted, in their own view.", null),
			new Feature(1, MINOR, BROWSING, "Find", "Next / previous, and * as a wildcard in Find and in the name"
					+ " filter.", null),
			new Feature(1, MINOR, BROWSING, "Set lists", "Lists of sets are alphabetical, or by release date"
					+ " (oldest first) if you prefer - see the ManaDesk preferences.", null),
			new Feature(1, MAJOR, GENERAL, "New help", "Rewritten from scratch, with screenshots: getting started,"
					+ " collections and decks, wishlists, proxies and buying, import and export, backups and cloud"
					+ " sync. Press F1 in a view to open its page.", null),
			new Feature(1, MINOR, GENERAL, "Faster startup", "The splash screen shows the real progress, and the"
					+ " window opens ready to use.", null),
			new Feature(1, MINOR, GENERAL, "Linux", "The Linux version starts right from its archive, with a more"
					+ " compact look, and its toolbars draw correctly under Wayland.", null),
			new Feature(1, MINOR, GENERAL, "No more Work Offline", "Without internet, ManaDesk keeps working and"
					+ " quietly skips what needs the web.", null),
			new Feature(1, MINOR, GENERAL, "Report a bug or request a feature", "From the Help menu: describe it,"
					+ " and ManaDesk opens the report on GitHub, ready to submit - or, without a GitHub account, as"
					+ " an email to the developer, in your mail app or in Gmail / Outlook.com. For a bug, ManaDesk"
					+ " prepares its log as a file to attach.", null), };

	private WhatsNew() {
	}

	// ------------------------------------------------------------ triggers

	/**
	 * Called once the workbench is up. Pops up only after an upgrade that brings
	 * entries the user has not seen ({@link #FEATURES_VERSION} above the saved
	 * one) and only for someone who already used ManaDesk (cards in their decks /
	 * collections). A clean install just records the current version. While the
	 * card database still waits for its first download (an upgrade from 0.9.7,
	 * which had it built in), it waits for a later start - never on top of the
	 * download prompt.
	 */
	static void checkOnStartup() {
		if (MagicUIActivator.TRACE_TESTING || MagicUIActivator.isJunitRunning())
			return;
		final IPreferenceStore prefs = Activator.getDefault().getPreferenceStore();
		final int seen = prefs.getInt(SEEN_KEY);
		if (seen >= FEATURES_VERSION)
			return;
		new Job("Checking what's new") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					DataManager dm = DataManager.getInstance();
					// decks / collections loaded first - else a user's cards look like none
					if (!WaitUtils.waitForLibrary())
						return Status.OK_STATUS; // can't tell: next start
					if (seen == 0 && dm.getLibraryCardStore().size() == 0) {
						markSeen(prefs); // clean install (or never used): nothing to announce
						return Status.OK_STATUS;
					}
					if (dm.getMagicDBStore().size() == 0)
						return Status.OK_STATUS; // download prompt first; next start
				} catch (RuntimeException e) {
					return Status.OK_STATUS;
				}
				Display.getDefault().asyncExec(() -> showUpgradeNotice(prefs, seen));
				return Status.OK_STATUS;
			}
		}.schedule(3000);
	}

	private static void markSeen(IPreferenceStore prefs) {
		prefs.setValue(SEEN_KEY, FEATURES_VERSION);
		if (prefs instanceof IPersistentPreferenceStore) {
			try {
				((IPersistentPreferenceStore) prefs).save();
			} catch (Exception e) {
				Activator.log(e);
			}
		}
	}

	private static void showUpgradeNotice(IPreferenceStore prefs, int seen) {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		if (window == null || window.getActivePage() == null)
			return; // try again next start
		List<Feature> news = features(seen);
		markSeen(prefs);
		if (news.isEmpty())
			return;
		for (Feature f : news)
			if (f.viewId != null)
				addView(window, f.viewId);
		new WhatsNewDialog(window.getShell(), news, false).open();
	}

	/** Help > What's New...: every feature, newest release first. */
	static void open(Shell shell) {
		new WhatsNewDialog(shell, features(0), true).open();
	}

	/** The features newer than {@code seen}, newest release first; a view whose plug-in isn't installed is left out. */
	private static List<Feature> features(int seen) {
		List<Feature> list = new ArrayList<>();
		for (int v = FEATURES_VERSION; v > seen; v--)
			for (Feature f : FEATURES)
				if (f.since == v && (f.viewId == null || viewDescriptor(f.viewId) != null))
					list.add(f);
		return list;
	}

	private static IViewDescriptor viewDescriptor(String id) {
		return PlatformUI.getWorkbench().getViewRegistry().find(id);
	}

	/**
	 * Adds the view as a tab in its zone (see {@link ViewZones}), without
	 * activating it.
	 */
	private static void addView(IWorkbenchWindow window, String viewId) {
		try {
			ViewZones.apply(window); // gives the view its place in its zone
			window.getActivePage().showView(viewId, null, IWorkbenchPage.VIEW_CREATE);
		} catch (Exception e) {
			Activator.log(e);
		}
	}

	// ------------------------------------------------------------ dialog

	/** Release heading (menu only), topic headings, then a bold title + a line or two per feature. */
	private static final class WhatsNewDialog extends Dialog {
		private final List<Feature> features;
		private final boolean all;
		private final List<Image> images = new ArrayList<>();

		WhatsNewDialog(Shell parent, List<Feature> features, boolean all) {
			super(parent);
			this.features = features;
			this.all = all;
		}

		@Override
		protected boolean isResizable() {
			return true;
		}

		@Override
		protected void configureShell(Shell shell) {
			super.configureShell(shell);
			shell.setText(all ? "What's New in ManaDesk" : "New in ManaDesk");
			shell.addDisposeListener(e -> {
				for (Image i : images)
					i.dispose();
			});
		}

		@Override
		protected Control createDialogArea(Composite parent) {
			Composite area = (Composite) super.createDialogArea(parent);
			ScrolledComposite scroll = new ScrolledComposite(area, SWT.V_SCROLL);
			scroll.setExpandHorizontal(true);
			scroll.setExpandVertical(true);
			GridDataFactory.fillDefaults().grab(true, true).hint(540, 460).applyTo(scroll);

			Composite body = new Composite(scroll, SWT.NONE);
			GridLayoutFactory.swtDefaults().numColumns(2).spacing(10, 2).margins(12, 8).applyTo(body);

			if (!all)
				heading(body, "This version brings:", 0);
			// per release: "Highlights" (the major additions, bold, with room
			// around them), then "Also new" (smaller improvements, by topic)
			for (int release = FEATURES_VERSION; release >= 1; release--) {
				List<Feature> majors = new ArrayList<>();
				List<Feature> minors = new ArrayList<>();
				for (Feature f : features)
					if (f.since == release)
						(f.major ? majors : minors).add(f);
				if (majors.isEmpty() && minors.isEmpty())
					continue;
				if (all)
					heading(body, RELEASES[release - 1], 2);
				if (!majors.isEmpty()) {
					heading(body, "Highlights", 1);
					for (Feature f : majors)
						entry(body, f, true);
				}
				if (!minors.isEmpty()) {
					heading(body, majors.isEmpty() ? "New" : "Also new", 1);
					String group = null;
					for (Feature f : minors) {
						if (!f.group.equals(group)) {
							group = f.group;
							Label g = heading(body, group, 0);
							g.setFont(JFaceResources.getFontRegistry().getItalic(JFaceResources.DIALOG_FONT));
						}
						entry(body, f, false);
					}
				}
			}
			scroll.setContent(body);
			Point size = body.computeSize(520, SWT.DEFAULT);
			scroll.setMinSize(size);
			scroll.addListener(SWT.Resize, e -> {
				int w = scroll.getClientArea().width;
				scroll.setMinSize(body.computeSize(w, SWT.DEFAULT));
			});
			return area;
		}

		/**
		 * One feature: icon column (new views only), then a wrapped, read-only
		 * paragraph. A major one: bold title, a little more room; a minor one:
		 * plain title, text in the dimmer color.
		 */
		private void entry(Composite body, Feature f, boolean major) {
			Label icon = new Label(body, SWT.NONE);
			Image img = imageOf(f);
			if (img != null)
				icon.setImage(img);
			GridDataFactory.swtDefaults().align(SWT.CENTER, SWT.BEGINNING).hint(24, SWT.DEFAULT)
					.indent(8, major ? 6 : 1).applyTo(icon);
			StyledText text = new StyledText(body, SWT.WRAP | SWT.READ_ONLY);
			text.setText(f.title + " - " + f.text);
			text.setEditable(false);
			text.setCaret(null); // reads as text, not as an input field
			text.setBackground(body.getBackground());
			text.setForeground(body.getForeground());
			StyleRange title = new StyleRange();
			title.start = 0;
			title.length = f.title.length();
			title.fontStyle = SWT.BOLD;
			if (major) {
				text.setStyleRange(title);
			} else {
				StyleRange rest = new StyleRange();
				rest.start = f.title.length();
				rest.length = text.getCharCount() - rest.start;
				rest.foreground = body.getDisplay().getSystemColor(SWT.COLOR_WIDGET_DARK_SHADOW);
				text.setStyleRange(rest);
			}
			GridDataFactory.fillDefaults().grab(true, false).hint(460, SWT.DEFAULT).indent(0, major ? 6 : 1)
					.applyTo(text);
		}

		/** level 2: release heading; 1: "Highlights" / "Also new"; 0: topic or intro line. */
		private Label heading(Composite body, String text, int level) {
			Label l = new Label(body, SWT.WRAP);
			l.setText(text);
			if (level == 2)
				l.setFont(JFaceResources.getFontRegistry().getBold(JFaceResources.HEADER_FONT));
			else if (level == 1)
				l.setFont(JFaceResources.getFontRegistry().getBold(JFaceResources.BANNER_FONT));
			GridDataFactory.fillDefaults().span(2, 1).indent(0, level == 2 ? 4 : level == 1 ? 14 : 8).applyTo(l);
			return l;
		}

		/** A new view gets its own icon; the other features none. */
		private Image imageOf(Feature f) {
			if (f.viewId == null)
				return null;
			try {
				IViewDescriptor v = viewDescriptor(f.viewId);
				ImageDescriptor d = v == null ? null : v.getImageDescriptor();
				Image img = d == null ? null : d.createImage(false);
				if (img != null)
					images.add(img);
				return img;
			} catch (RuntimeException e) {
				return null;
			}
		}

		@Override
		protected void createButtonsForButtonBar(Composite parent) {
			createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
		}
	}
}
