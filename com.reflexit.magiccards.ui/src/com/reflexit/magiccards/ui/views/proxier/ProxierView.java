/*
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: Proxier view, grid-layout step
 *                         (left pane: checkbox+reorderable list of unboxed
 *                         decks; right pane: Card / Needed / one column per
 *                         checked deck, current real/proxy/needed state per
 *                         cell - no cross-deck scarcity allocation yet, that
 *                         is a separate follow-up step)
 *     Rémi Dutil (2026) - left pane is now the same tree as the Decks
 *                         navigator (folders + decks, same icons/labels/sort:
 *                         folders first), not a flat list; an explicit
 *                         Refresh action reloads it (a boxed-status change
 *                         elsewhere in the app doesn't auto-update this view
 *                         otherwise)
 *     Rémi Dutil (2026) - columns are now Card / Owned / Boxed / Needed, one
 *                         per checked deck: Owned = genuine + proxy owned,
 *                         whole app, every printing; Boxed = copies of it
 *                         already tied up in OTHER already-boxed decks
 *                         (unavailable to this batch); Needed = the actual
 *                         proxy shortfall - max(0, wanted across the checked
 *                         decks - max(0, Owned - Boxed))
 *     Rémi Dutil (2026) - selecting a row now always shows the card in proxy
 *                         style (grey + diagonal "Proxy" watermark, via the
 *                         existing CardDescComposite proxy rendering) - the
 *                         whole point of this view is planning proxies, so
 *                         the preview should always look like the printed
 *                         proxy, not whatever a sample physical copy happens
 *                         to be flagged
 *     Rémi Dutil (2026) - checking a folder now cascades to every deck below
 *                         it; cardsOf() now resolves Sideboard/Extra directly
 *                         by Location (DataManager.getCardStore) instead of
 *                         via CardElement.getRelatedElements()'s sibling
 *                         lookup, which depends on the sideboard/extra
 *                         already being loaded as children in the in-memory
 *                         navigator tree
 *     Rémi Dutil (2026) - cardsOf(): dropped the loc.getFile().exists() guard
 *                         in front of getCardStore() - it was under-reporting
 *                         existing sideboards/extras; now matches the proven
 *                         AccessoriesPage/DeckAccessoriesPopulator pattern of
 *                         calling getCardStore() directly and checking the
 *                         returned store for null (plus the same defensive
 *                         try/catch SideboardHelpHtmlExportDelegate uses)
 *     Rémi Dutil (2026) - Owned/Boxed/Available (new) now each show
 *                         "total (genuine/proxy)"; Boxed now counts every
 *                         slot in a boxed deck, not just materialized ones -
 *                         it used to read zero for an all-virtual deck (the
 *                         common case, decks default to virtual), since a
 *                         virtual non-proxy slot still claims real supply
 *                         the same as a materialized one
 *     Rémi Dutil (2026) - split what used to be one "Needed" column in two:
 *                         Needed is now the raw wanted total (undoing the
 *                         earlier netted definition, which read confusingly
 *                         low next to Owned/Available); the new "Proxy to
 *                         print" column carries the actual netted result -
 *                         Needed minus Available, floored at 0
 *     Rémi Dutil (2026) - dropped the per-checked-deck grid columns entirely
 *                         (a deck name rarely fits a table column header - a
 *                         first attempt at word-wrapping the header text
 *                         didn't render as multiple lines on this platform's
 *                         native Table widget) - replaced with a 3rd zone,
 *                         bottom-right: a plain text listing of the selected
 *                         row's per-deck breakdown, where a full deck name
 *                         has room to render without a column-width
 *                         constraint; the grid (top-right) is now pooled
 *                         sums only, no per-deck data
 *     Rémi Dutil (2026) - "Proxy to print" renamed "To Print"; the
 *                         bottom-right zone is now a real table (Needed /
 *                         Deck / Set / Coll. #) instead of plain text lines;
 *                         new "Perfect Match" toolbar toggle (left of
 *                         Refresh) controls whether Set/Coll. # (both
 *                         tables) show the row's actual printing or just
 *                         "Any" - display-only for now, doesn't change the
 *                         name-based row grouping or the numeric columns
 *     Rémi Dutil (2026) - "Perfect Match" renamed "Exact Match" and given an
 *                         icon (the same check16.png CollectorView's toggle
 *                         actions already use); "Coll. #" columns renamed
 *                         "CollNum" (matching the other views' naming); the
 *                         toggle is no longer display-only - rows now
 *                         actually re-group by exact printing (name + set +
 *                         collector number) when it's on, using the
 *                         per-printing getOwnCount()/getGenuineOwnCount() for
 *                         Owned/Boxed instead of the pooled-across-every-
 *                         printing getOwnTotalAll()/getGenuineOwnTotalAll() -
 *                         a pooled row genuinely can't report one truthful
 *                         Set/CollNum for cards owned across several
 *                         printings, which is what "not working" turned out
 *                         to mean
 *     Rémi Dutil (2026) - added a header above the grid showing the total
 *                         "Proxies to print"; removed the left pane's Up/Down
 *                         buttons (their only remaining effect - per-deck
 *                         breakdown row order - wasn't worth the confusion
 *                         of two unlabeled buttons); new "Only To Print"
 *                         toolbar filter (grid rows where To Print is 0);
 *                         new "Create Collection" action builds a new
 *                         "ProxiesToPrint" collection (de-duplicated with a
 *                         trailing number) holding one virtual, proxy-flagged
 *                         card per non-zero "To Print" row, at that quantity
 *                         - confirmed first. (Originally, off Exact Match, it
 *                         picked a printing DB-wide instead of the deck's own
 *                         - see the later bullet fixing that.)
 *     Rémi Dutil (2026) - Boxed cell background turns red when Boxed exceeds
 *                         Owned (total, or either genuine/proxy bucket) - a
 *                         claim can't legitimately exceed the stock it's
 *                         claiming against, so this flags a data
 *                         inconsistency rather than a normal planning state
 *     Rémi Dutil (2026) - rowKeyFor()'s separator was two literal NUL bytes
 *                         (an editing accident - a Java char literal can hold
 *                         any value including NUL, so it compiled fine, but
 *                         git/GitHub's binary-file heuristic flagged the
 *                         whole file as binary because of it) - replaced with
 *                         plain spaces
 *     Rémi Dutil (2026) - the bottom-right per-deck breakdown now tells a
 *                         deck's main list, Sideboard and Extra apart -
 *                         DeckLocation replaces CardCollection as the
 *                         breakdown's key, so a card needed in both a deck's
 *                         main list and its Sideboard is two lines ("Deck",
 *                         "Deck (Sideboard)"), not one merged count
 *     Rémi Dutil (2026) - "Create Collection" now always uses row.sample's
 *                         printing (a card genuinely pulled from one of the
 *                         checked decks during aggregation), regardless of
 *                         Exact Match. Off Exact Match it used to fall back
 *                         to pickAnyPrintingCard() - a DB-wide "most recently
 *                         released set" pick with no guarantee of matching
 *                         anything the decks actually reference - removed as
 *                         dead code along with releaseDateOf()/collNumValue()
 */
package com.reflexit.magiccards.ui.views.proxier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.ui.part.ViewPart;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.nav.CardCollection;
import com.reflexit.magiccards.core.model.nav.CollectionsContainer;
import com.reflexit.magiccards.core.model.storage.ICardStore;
import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.views.nav.CardsNavigatorContentProvider;
import com.reflexit.magiccards.ui.views.nav.CardsNavigatorLabelProvider;
import com.reflexit.magiccards.ui.views.nav.DeckFamilyViewerComparator;

/**
 * Helps plan which cards a batch of decks being "boxed up" need proxies for.
 * This is the grid-layout step only. Three zones:
 * <ul>
 * <li><b>Left</b> (full height): the same Decks tree as the Cards Navigator -
 * checkbox to include a deck; boxed decks excluded entirely rather than shown
 * disabled.</li>
 * <li><b>Top-right</b>: a header showing the total "Proxies to print" across
 * every row, then the pivot grid - Card / Owned / Boxed / Available / Needed
 * / To Print / Set / CollNum, one row per card across every checked deck. No
 * per-deck columns here (a deck name rarely fits a table column header; see
 * the per-deck pane below instead) - these are the pooled sums for the whole
 * checked batch. <b>To Print</b> is the actual result - Needed (raw demand)
 * minus Available (Owned minus Boxed), floored at 0.</li>
 * <li><b>Bottom-right</b>: a table listing the selected grid row's breakdown
 * by deck <i>list</i> - Needed / Deck / Set / CollNum, one row per deck list
 * (main, Sideboard or Extra) that needs the card, so a card needed in both a
 * deck's main list and its Sideboard shows as two lines, not one merged
 * count - this is where a deck's full name actually gets to render (ordinary
 * cell content, not a column header).</li>
 * </ul>
 * By default rows are grouped by card name alone - every printing pooled
 * together - and Set/CollNum (both tables) just show "Any", since a pooled
 * row has no single printing to report there truthfully. The <b>Exact
 * Match</b> toolbar toggle switches to one row per exact printing (name +
 * set + collector number): Set/CollNum then show that row's actual values,
 * and Owned/Boxed/Available use that specific printing's own count instead
 * of the pooled-across-every-printing total. <b>Only To Print</b> filters the
 * grid to rows that actually need a proxy. <b>Create Collection</b> builds a
 * real collection from those rows - see {@link #createProxiesCollection()}.
 * <p>
 * The remaining open piece - which specific selected deck gets priority on a
 * contested real/existing-proxy copy - is still a separate follow-up step;
 * this view's To-Print number is a pooled total, not a per-deck resolution.
 */
public class ProxierView extends ViewPart {
	public static final String ID = ProxierView.class.getName();

	private CheckboxTreeViewer deckTreeViewer;
	private TableViewer gridViewer;
	private Table gridTable;
	private Label toPrintHeader;
	private TableViewer deckNeedsViewer;
	/** Whether the grid is filtered to rows where "To Print" is non-zero. */
	private boolean onlyToPrint;
	/** The full (unfiltered) row set from the last {@link #rebuildGrid()} - what "Create Collection" reads from. */
	private final List<ProxyRow> currentRows = new ArrayList<>();
	/**
	 * Whether rows are grouped by exact printing (name + set + collector
	 * number) instead of by name alone. On: Owned/Boxed/Available use the
	 * per-printing aggregate (that exact card's own count, not pooled across
	 * every printing of the name) and CollNum/Set show the row's actual
	 * printing. Off (default): rows pool every printing of the name together
	 * (as before), and CollNum/Set show "Any" - a pooled row has no single
	 * printing to report there truthfully.
	 */
	private boolean exactMatch;
	/**
	 * Every eligible (unboxed, main) deck, in navigator order - which of these
	 * are actually included in the grid is read from the tree's checked state.
	 * Rebuilt from scratch every time Refresh runs.
	 */
	private final List<CardCollection> deckOrder = new ArrayList<>();
	private final CopyOnWriteArrayList<ISelectionChangedListener> cardSelectionListeners = new CopyOnWriteArrayList<>();

	/** This deck list's (main/Sideboard/Extra) current state for one card in one row - no cross-list math. */
	private static final class DeckCardInfo {
		int real;
		int proxy;
		int needed;
		/** A representative copy from this specific deck list - Set/CollNum in Exact Match mode. */
		MagicCardPhysical sample;

		int total() {
			return real + proxy + needed;
		}
	}

	/**
	 * One line of the bottom-right breakdown: a deck, further qualified by
	 * which of its lists (main / Sideboard / Extra) the card is needed in - a
	 * card needed in both the main deck and its Sideboard is two separate
	 * lines, not one merged count.
	 */
	private static final class DeckLocation {
		final CardCollection deck;
		final String suffix; // "", " (Sideboard)" or " (Extra)"

		DeckLocation(CardCollection deck, String suffix) {
			this.deck = deck;
			this.suffix = suffix;
		}

		String label() {
			return deck.getName() + suffix;
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof DeckLocation))
				return false;
			DeckLocation d = (DeckLocation) o;
			return deck == d.deck && suffix.equals(d.suffix);
		}

		@Override
		public int hashCode() {
			return System.identityHashCode(deck) * 31 + suffix.hashCode();
		}
	}

	/** A Genuine/Proxy split - Owned, Boxed and Available all share this shape and its display format. */
	private static final class GenuineProxySplit {
		int genuine;
		int proxy;

		int total() {
			return genuine + proxy;
		}
	}

	/** One pivot row: a card name and its breakdown by deck list among the checked decks. */
	private static final class ProxyRow {
		final String cardName;
		/** A synthetic proxy-flagged copy - feeds the card info zone with the proxy-style preview and Set/Coll.#. */
		MagicCardPhysical sample;
		/** Genuine + proxy copies owned, whole app, every printing of the name. */
		final GenuineProxySplit owned = new GenuineProxySplit();
		/**
		 * Copies of this card claimed by OTHER already-boxed decks - unavailable to
		 * this batch. Every slot in a boxed deck counts (materialized or still
		 * virtual): a boxed deck's virtual, non-proxy slot still represents an
		 * intent to use a real copy there, so it claims real supply the same as an
		 * already-materialized one would (this is also why this used to read zero
		 * for an all-virtual deck - counting only materialized copies missed that
		 * claim entirely).
		 */
		final GenuineProxySplit boxed = new GenuineProxySplit();
		/** Raw total demand across the checked decks - the "Needed" column, not yet netted against stock. */
		int wanted;
		/** The actual result - "Proxy to print": max(0, wanted - available.total()). */
		int needed;
		final Map<DeckLocation, DeckCardInfo> perDeck = new LinkedHashMap<>();

		ProxyRow(String cardName) {
			this.cardName = cardName;
		}

		/** Owned stock not already claimed by another boxed deck - each bucket floored at 0 independently. */
		GenuineProxySplit available() {
			GenuineProxySplit a = new GenuineProxySplit();
			a.genuine = Math.max(0, owned.genuine - boxed.genuine);
			a.proxy = Math.max(0, owned.proxy - boxed.proxy);
			return a;
		}
	}

	/**
	 * Publishes the grid's row selection as the underlying card(s), not the
	 * pivot row object itself - this is what lets the workbench's existing card
	 * info zone ({@code CardDescView}) render it, exactly as it does for any
	 * other card list in the app.
	 */
	private final ISelectionProvider cardSelectionProvider = new ISelectionProvider() {
		@Override
		public void addSelectionChangedListener(ISelectionChangedListener listener) {
			cardSelectionListeners.add(listener);
		}

		@Override
		public ISelection getSelection() {
			return toCardSelection(gridViewer.getStructuredSelection());
		}

		@Override
		public void removeSelectionChangedListener(ISelectionChangedListener listener) {
			cardSelectionListeners.remove(listener);
		}

		@Override
		public void setSelection(ISelection selection) {
			// read-mostly view - not wired to accept external selections
		}
	};

	@Override
	public void createPartControl(Composite parent) {
		SashForm sash = new SashForm(parent, SWT.HORIZONTAL);
		createLeftPane(sash);
		createRightPane(sash);
		sash.setWeights(new int[] { 30, 70 });
		contributeToolbar();
		loadDecks();
		rebuildGrid();
	}

	@Override
	public void setFocus() {
		gridTable.setFocus();
	}

	private Shell getShell() {
		return getViewSite().getShell();
	}

	private void contributeToolbar() {
		IToolBarManager mgr = getViewSite().getActionBars().getToolBarManager();
		Action exactMatchAction = new Action("Exact Match", IAction.AS_CHECK_BOX) {
			@Override
			public void run() {
				exactMatch = isChecked();
				rebuildGrid(); // rows re-group by exact printing, not just a relabel
			}
		};
		exactMatchAction.setImageDescriptor(MagicUIActivator.getImageDescriptor("icons/obj16/check16.png"));
		exactMatchAction.setToolTipText(
				"Group rows by exact printing (set + collector number) instead of by name alone - which specific printing a proxy should match");
		mgr.add(exactMatchAction);

		Action onlyToPrintAction = new Action("Only To Print", IAction.AS_CHECK_BOX) {
			@Override
			public void run() {
				onlyToPrint = isChecked();
				gridViewer.refresh(); // same rows, just re-filtered - no recompute needed
			}
		};
		onlyToPrintAction.setImageDescriptor(MagicUIActivator.getImageDescriptor("icons/clcl16/filter_ps.png"));
		onlyToPrintAction.setToolTipText("Show only rows where \"To Print\" is not 0");
		mgr.add(onlyToPrintAction);

		Action createCollection = new Action("Create Collection") {
			@Override
			public void run() {
				createProxiesCollection();
			}
		};
		createCollection.setImageDescriptor(MagicUIActivator.getImageDescriptor("icons/obj16/lib16.png"));
		createCollection.setToolTipText(
				"Create a new collection with all the proxies currently needed (virtual, flagged Proxy - mark them owned once printed)");
		mgr.add(createCollection);

		Action refresh = new Action("Refresh") {
			@Override
			public void run() {
				refreshDecks();
			}
		};
		refresh.setImageDescriptor(MagicUIActivator.getImageDescriptor("icons/clcl16/refresh.gif"));
		refresh.setToolTipText(
				"Reload the deck list - picks up boxed-status changes made elsewhere in the app");
		mgr.add(refresh);
		getViewSite().getActionBars().updateActionBars();
	}

	// --- left pane: the Decks tree, checkbox to include a deck ---

	private void createLeftPane(Composite parent) {
		Tree tree = new Tree(parent, SWT.CHECK | SWT.BORDER);
		deckTreeViewer = new CheckboxTreeViewer(tree);
		deckTreeViewer.setContentProvider(new CardsNavigatorContentProvider());
		deckTreeViewer.setLabelProvider(new CardsNavigatorLabelProvider());
		deckTreeViewer.setComparator(new DeckFamilyViewerComparator()); // folders first, same as the Decks navigator
		deckTreeViewer.addFilter(CardsNavigatorContentProvider.getFilter(CardsNavigatorContentProvider.FILTER_SIDEBOARDS));
		deckTreeViewer.addFilter(new ViewerFilter() {
			@Override
			public boolean select(Viewer viewer, Object parentElement, Object element) {
				// boxed decks are excluded entirely, not shown disabled
				return !(element instanceof CardCollection && ((CardCollection) element).isBoxed());
			}
		});
		deckTreeViewer.addCheckStateListener(e -> {
			Object el = e.getElement();
			if (!(el instanceof CardCollection)) {
				// a folder: cascade the check state to every deck below it
				deckTreeViewer.setSubtreeChecked(el, e.getChecked());
			}
			rebuildGrid();
		});
	}

	private void loadDecks() {
		deckTreeViewer.setInput(DataManager.getInstance().getModelRoot().getDeckContainer());
		rebuildDeckOrder();
	}

	/** Reloads the tree and drops now-boxed decks - the explicit alternative to auto-refreshing on every change. */
	private void refreshDecks() {
		for (Object o : deckTreeViewer.getCheckedElements()) {
			if (o instanceof CardCollection && ((CardCollection) o).isBoxed())
				deckTreeViewer.setChecked(o, false);
		}
		deckTreeViewer.refresh();
		rebuildDeckOrder();
		rebuildGrid();
	}

	/** Rebuilt fresh (in navigator order) every refresh. */
	private void rebuildDeckOrder() {
		deckOrder.clear();
		for (CardCollection cc : DataManager.getInstance().getModelRoot().getDeckContainer().getAllElements()) {
			if (isEligible(cc))
				deckOrder.add(cc);
		}
	}

	private static boolean isEligible(CardCollection cc) {
		if (!cc.isDeck())
			return false;
		Location loc = cc.getLocation();
		if (loc != null && (loc.isSideboard() || loc.isExtra()))
			return false;
		return !cc.isBoxed();
	}

	// --- right pane: pivot grid on top, per-deck breakdown of the selected row below ---

	private void createRightPane(Composite parent) {
		SashForm rightSash = new SashForm(parent, SWT.VERTICAL);

		Composite gridArea = new Composite(rightSash, SWT.NONE);
		GridLayout gridAreaLayout = new GridLayout(1, false);
		gridAreaLayout.marginWidth = 0;
		gridAreaLayout.marginHeight = 0;
		gridArea.setLayout(gridAreaLayout);

		toPrintHeader = new Label(gridArea, SWT.NONE);
		toPrintHeader.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		toPrintHeader.setText("Proxies to print: 0");

		gridTable = new Table(gridArea, SWT.BORDER | SWT.FULL_SELECTION);
		gridTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		gridTable.setHeaderVisible(true);
		gridTable.setLinesVisible(true);
		gridViewer = new TableViewer(gridTable);
		gridViewer.setContentProvider(ArrayContentProvider.getInstance());
		gridViewer.addFilter(new ViewerFilter() {
			@Override
			public boolean select(Viewer viewer, Object parentElement, Object element) {
				return !onlyToPrint || ((ProxyRow) element).needed != 0;
			}
		});

		TableViewerColumn cardCol = new TableViewerColumn(gridViewer, SWT.NONE);
		cardCol.getColumn().setText("Card");
		cardCol.getColumn().setWidth(160);
		cardCol.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((ProxyRow) element).cardName;
			}

			@Override
			public String getToolTipText(Object element) {
				return ((ProxyRow) element).cardName;
			}
		});

		TableViewerColumn ownedCol = new TableViewerColumn(gridViewer, SWT.NONE);
		ownedCol.getColumn().setText("Owned");
		ownedCol.getColumn().setWidth(90);
		ownedCol.getColumn().setToolTipText(
				"Copies you own of this card, every printing, whole app - total (genuine/proxy)");
		ownedCol.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return formatSplit(((ProxyRow) element).owned);
			}
		});

		TableViewerColumn boxedCol = new TableViewerColumn(gridViewer, SWT.NONE);
		boxedCol.getColumn().setText("Boxed");
		boxedCol.getColumn().setWidth(90);
		boxedCol.getColumn().setToolTipText(
				"Copies of this card claimed by other already-boxed decks, unavailable to this batch - total (genuine/proxy)");
		boxedCol.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return formatSplit(((ProxyRow) element).boxed);
			}

			@Override
			public Color getBackground(Object element) {
				// Boxed can never legitimately exceed Owned (a claim needs stock to claim
				// against) - a red background flags the data inconsistency for follow-up
				ProxyRow row = (ProxyRow) element;
				boolean overBoxed = row.boxed.total() > row.owned.total() || row.boxed.genuine > row.owned.genuine
						|| row.boxed.proxy > row.owned.proxy;
				return overBoxed ? Display.getDefault().getSystemColor(SWT.COLOR_RED) : null;
			}
		});

		TableViewerColumn availableCol = new TableViewerColumn(gridViewer, SWT.NONE);
		availableCol.getColumn().setText("Available");
		availableCol.getColumn().setWidth(90);
		availableCol.getColumn().setToolTipText("Owned minus Boxed - total (genuine/proxy)");
		availableCol.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return formatSplit(((ProxyRow) element).available());
			}
		});

		TableViewerColumn neededCol = new TableViewerColumn(gridViewer, SWT.NONE);
		neededCol.getColumn().setText("Needed");
		neededCol.getColumn().setWidth(70);
		neededCol.getColumn().setToolTipText("Total copies wanted across the checked decks (raw demand, before netting against Available)");
		neededCol.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return String.valueOf(((ProxyRow) element).wanted);
			}
		});

		TableViewerColumn proxyToPrintCol = new TableViewerColumn(gridViewer, SWT.NONE);
		proxyToPrintCol.getColumn().setText("To Print");
		proxyToPrintCol.getColumn().setWidth(70);
		proxyToPrintCol.getColumn()
				.setToolTipText("The actual result: Needed minus Available, floored at 0 - how many new proxies to print");
		proxyToPrintCol.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return String.valueOf(((ProxyRow) element).needed);
			}
		});

		TableViewerColumn setCol = new TableViewerColumn(gridViewer, SWT.NONE);
		setCol.getColumn().setText("Set");
		setCol.getColumn().setWidth(80);
		setCol.getColumn().setToolTipText(
				"\"Exact Match\" on: this row's exact set (rows are grouped per printing); off: any printing is acceptable");
		setCol.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (!exactMatch)
					return "Any";
				MagicCardPhysical sample = ((ProxyRow) element).sample;
				return sample == null ? "" : String.valueOf(sample.getSet());
			}
		});

		TableViewerColumn collNumCol = new TableViewerColumn(gridViewer, SWT.NONE);
		collNumCol.getColumn().setText("CollNum");
		collNumCol.getColumn().setWidth(60);
		collNumCol.getColumn().setToolTipText(
				"\"Exact Match\" on: this row's exact collector number (rows are grouped per printing); off: any printing is acceptable");
		collNumCol.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (!exactMatch)
					return "Any";
				MagicCardPhysical sample = ((ProxyRow) element).sample;
				return sample == null ? "" : String.valueOf(sample.getCard().getCollNumber());
			}
		});

		createDeckNeedsPane(rightSash);
		rightSash.setWeights(new int[] { 70, 30 });

		getSite().setSelectionProvider(cardSelectionProvider);
		gridViewer.addSelectionChangedListener(e -> {
			IStructuredSelection sel = e.getStructuredSelection();
			ISelection cardSel = toCardSelection(sel);
			SelectionChangedEvent evt = new SelectionChangedEvent(cardSelectionProvider, cardSel);
			for (ISelectionChangedListener l : cardSelectionListeners)
				l.selectionChanged(evt);
			updateDeckNeedsTable(sel);
		});
	}

	/**
	 * The bottom-right per-deck breakdown for the selected grid row - Needed /
	 * Deck / Set / Coll.#, one row per checked deck that needs the card. A
	 * deck name has room to render in full here (it's ordinary cell content in
	 * a "Deck" column, not a column header).
	 */
	private void createDeckNeedsPane(Composite parent) {
		Table table = new Table(parent, SWT.BORDER | SWT.FULL_SELECTION);
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		deckNeedsViewer = new TableViewer(table);
		deckNeedsViewer.setContentProvider(ArrayContentProvider.getInstance());

		TableViewerColumn neededCol = new TableViewerColumn(deckNeedsViewer, SWT.NONE);
		neededCol.getColumn().setText("Needed");
		neededCol.getColumn().setWidth(120);
		neededCol.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return formatCell(entryInfo(element));
			}
		});

		TableViewerColumn deckCol = new TableViewerColumn(deckNeedsViewer, SWT.NONE);
		deckCol.getColumn().setText("Deck");
		deckCol.getColumn().setWidth(180);
		deckCol.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return entryLocation(element).label();
			}

			@Override
			public String getToolTipText(Object element) {
				return entryLocation(element).label();
			}
		});

		TableViewerColumn setCol = new TableViewerColumn(deckNeedsViewer, SWT.NONE);
		setCol.getColumn().setText("Set");
		setCol.getColumn().setWidth(80);
		setCol.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (!exactMatch)
					return "Any";
				MagicCardPhysical sample = entryInfo(element).sample;
				return sample == null ? "" : String.valueOf(sample.getSet());
			}
		});

		TableViewerColumn collNumCol = new TableViewerColumn(deckNeedsViewer, SWT.NONE);
		collNumCol.getColumn().setText("CollNum");
		collNumCol.getColumn().setWidth(60);
		collNumCol.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (!exactMatch)
					return "Any";
				MagicCardPhysical sample = entryInfo(element).sample;
				return sample == null ? "" : String.valueOf(sample.getCard().getCollNumber());
			}
		});
	}

	@SuppressWarnings("unchecked")
	private static DeckCardInfo entryInfo(Object element) {
		return ((Map.Entry<DeckLocation, DeckCardInfo>) element).getValue();
	}

	@SuppressWarnings("unchecked")
	private static DeckLocation entryLocation(Object element) {
		return ((Map.Entry<DeckLocation, DeckCardInfo>) element).getKey();
	}

	private void updateDeckNeedsTable(IStructuredSelection sel) {
		if (sel.size() != 1 || !(sel.getFirstElement() instanceof ProxyRow)) {
			deckNeedsViewer.setInput(Collections.emptyList());
			return;
		}
		ProxyRow row = (ProxyRow) sel.getFirstElement();
		List<Map.Entry<DeckLocation, DeckCardInfo>> entries = new ArrayList<>();
		for (Map.Entry<DeckLocation, DeckCardInfo> e : row.perDeck.entrySet()) {
			if (e.getValue().total() > 0)
				entries.add(e);
		}
		deckNeedsViewer.setInput(entries);
	}

	private static ISelection toCardSelection(IStructuredSelection raw) {
		List<Object> cards = new ArrayList<>();
		for (Object o : raw.toList()) {
			if (o instanceof ProxyRow && ((ProxyRow) o).sample != null)
				cards.add(((ProxyRow) o).sample);
		}
		return new StructuredSelection(cards);
	}

	/** Recomputes the pooled-sums rows for the currently-checked decks, in the grid's priority order. */
	private void rebuildGrid() {
		List<CardCollection> checked = new ArrayList<>();
		for (CardCollection cc : deckOrder) {
			if (deckTreeViewer.getChecked(cc))
				checked.add(cc);
		}
		deckNeedsViewer.setInput(Collections.emptyList()); // the selection (and so the per-deck detail) doesn't survive a rebuild

		Map<String, ProxyRow> rows = new LinkedHashMap<>();
		for (CardCollection cc : checked) {
			for (SuffixedCard sc : cardsOfWithSuffix(cc)) {
				if (!(sc.card instanceof MagicCardPhysical))
					continue;
				MagicCardPhysical mcp = (MagicCardPhysical) sc.card;
				int count = mcp.getCount();
				if (count <= 0)
					continue;
				String key = rowKeyFor(mcp, exactMatch);
				ProxyRow row = rows.computeIfAbsent(key, k -> new ProxyRow(mcp.getName()));
				if (row.sample == null) {
					row.sample = proxyPreviewOf(mcp);
					if (exactMatch) {
						// this exact printing's own aggregate - not pooled across other printings
						row.owned.genuine = mcp.getCard().getGenuineOwnCount();
						row.owned.proxy = mcp.getCard().getOwnCount() - row.owned.genuine;
					} else {
						row.owned.genuine = mcp.getGenuineOwnTotalAll();
						row.owned.proxy = mcp.getOwnTotalAll() - row.owned.genuine;
					}
				}
				DeckLocation dl = new DeckLocation(cc, sc.suffix);
				DeckCardInfo info = row.perDeck.computeIfAbsent(dl, k -> new DeckCardInfo());
				if (info.sample == null)
					info.sample = mcp;
				if (mcp.isProxy())
					info.proxy += count;
				else if (mcp.isOwn())
					info.real += count;
				else
					info.needed += count;
				row.wanted += count;
			}
		}

		Map<String, GenuineProxySplit> boxedCounts = computeBoxedCounts(exactMatch);
		for (Map.Entry<String, ProxyRow> e : rows.entrySet()) {
			ProxyRow row = e.getValue();
			GenuineProxySplit b = boxedCounts.get(e.getKey());
			if (b != null) {
				row.boxed.genuine = b.genuine;
				row.boxed.proxy = b.proxy;
			}
			row.needed = Math.max(0, row.wanted - row.available().total());
		}

		List<ProxyRow> sorted = new ArrayList<>(rows.values());
		sorted.sort(Comparator.<ProxyRow, String>comparing(r -> r.cardName, String.CASE_INSENSITIVE_ORDER)
				.thenComparing(r -> r.sample == null ? "" : String.valueOf(r.sample.getSet())));
		currentRows.clear();
		currentRows.addAll(sorted);
		int totalToPrint = 0;
		for (ProxyRow row : sorted)
			totalToPrint += row.needed;
		toPrintHeader.setText("Proxies to print: " + totalToPrint);
		gridViewer.setInput(sorted);
		gridTable.getParent().layout(true, true);
	}

	/**
	 * The row-grouping key: card name alone when {@code exact} is off (every
	 * printing pooled into one row - matches Owned/Boxed using
	 * getOwnTotalAll()/getGenuineOwnTotalAll()); name + set + collector number
	 * when on (one row per exact printing - matches Owned/Boxed using the
	 * per-printing getOwnCount()/getGenuineOwnCount()). Boxed counts
	 * ({@link #computeBoxedCounts}) must use the same key so a boxed deck's
	 * claim lands on the right row.
	 */
	private static String rowKeyFor(MagicCardPhysical mcp, boolean exact) {
		if (!exact)
			return mcp.getName();
		return mcp.getName() + ' ' + mcp.getSet() + ' ' + mcp.getCard().getCollNumber();
	}

	/**
	 * How many copies of each row (keyed the same way as {@link #rowKeyFor} -
	 * by name, or by exact printing when {@code exact}) are claimed by decks
	 * that are already boxed - genuine (non-proxy-flagged) vs proxy, split the
	 * same way the Owned column is. Every slot in a boxed deck counts,
	 * materialized or still virtual: a boxed deck's virtual, non-proxy slot
	 * still represents an intent to use a real copy there, so it claims real
	 * supply the same as an already-materialized one would - counting only
	 * materialized copies (isOwn()) used to read zero for an all-virtual deck,
	 * which is the common case, since decks default to virtual. One pass over
	 * every boxed deck app-wide, not just the checked batch - a boxed deck's
	 * cards are spoken for regardless of what's being planned right now.
	 */
	private static Map<String, GenuineProxySplit> computeBoxedCounts(boolean exact) {
		Map<String, GenuineProxySplit> boxed = new HashMap<>();
		for (CardCollection cc : DataManager.getInstance().getModelRoot().getDeckContainer().getAllElements()) {
			if (!cc.isDeck() || !cc.isBoxed())
				continue;
			for (IMagicCard card : cardsOf(cc)) {
				if (!(card instanceof MagicCardPhysical))
					continue;
				MagicCardPhysical mcp = (MagicCardPhysical) card;
				int count = mcp.getCount();
				if (count <= 0)
					continue;
				GenuineProxySplit split = boxed.computeIfAbsent(rowKeyFor(mcp, exact), k -> new GenuineProxySplit());
				if (mcp.isProxy())
					split.proxy += count;
				else
					split.genuine += count;
			}
		}
		return boxed;
	}

	/**
	 * A throwaway, unpersisted proxy-flagged copy of {@code mcp}'s underlying
	 * card - selecting a row always shows the proxy-style preview (grey +
	 * diagonal "Proxy" watermark, via {@code CardDescComposite}'s existing
	 * rendering), regardless of whether this particular sample happens to be a
	 * real owned copy. This view is for planning proxies, so that's what the
	 * preview should always look like.
	 */
	private static MagicCardPhysical proxyPreviewOf(MagicCardPhysical mcp) {
		MagicCardPhysical preview = new MagicCardPhysical(mcp.getCard(), null);
		preview.setProxy(true);
		return preview;
	}

	/**
	 * Builds a new collection ("ProxiesToPrint", de-duplicated with a trailing
	 * number if that name is taken) holding one virtual, proxy-flagged card
	 * per row whose "To Print" is non-zero, at that quantity - always using
	 * {@code row.sample}'s actual printing (a card genuinely pulled from one
	 * of the checked decks), whether or not Exact Match is on, so the
	 * collection never contains a printing the decks don't actually
	 * reference. Asks for confirmation first; does nothing if there's
	 * nothing to print.
	 */
	private void createProxiesCollection() {
		List<ProxyRow> toPrint = new ArrayList<>();
		int totalCopies = 0;
		for (ProxyRow row : currentRows) {
			if (row.needed > 0) {
				toPrint.add(row);
				totalCopies += row.needed;
			}
		}
		if (toPrint.isEmpty()) {
			MessageDialog.openInformation(getShell(), "Create Proxies Collection",
					"Nothing to print - every row's \"To Print\" is 0.");
			return;
		}

		CollectionsContainer collectionsRoot = DataManager.getInstance().getModelRoot().getCollectionsContainer();
		String name = uniqueCollectionName(collectionsRoot, "ProxiesToPrint");

		boolean ok = MessageDialog.openConfirm(getShell(), "Create Proxies Collection",
				"Create a new collection \"" + name + "\" with " + toPrint.size() + " card"
						+ (toPrint.size() == 1 ? "" : "s") + ", " + totalCopies + " proxy cop"
						+ (totalCopies == 1 ? "y" : "ies") + " total?\n\n"
						+ "Cards are added as virtual proxies - mark them owned once you've actually printed them.");
		if (!ok)
			return;

		CardCollection coll = collectionsRoot.addDeck(name + ".xml", false, true);
		ICardStore<IMagicCard> store = coll.getStore();
		Location loc = coll.getLocation();

		List<MagicCardPhysical> toAdd = new ArrayList<>();
		for (ProxyRow row : toPrint) {
			if (row.sample == null)
				continue; // shouldn't happen - a "to print" row always has a contributing sample
			MagicCardPhysical phi = new MagicCardPhysical(row.sample.getCard(), loc, true);
			phi.setCount(row.needed);
			phi.setProxy(true);
			toAdd.add(phi);
		}
		store.setMergeOnAdd(false);
		DataManager.getInstance().add(toAdd, store);
		store.setMergeOnAdd(!store.isUnsorted());
	}

	/** {@code base}, or {@code "<base> 2"}, {@code "<base> 3"}, ... - whichever isn't already a child of {@code parent}. */
	private static String uniqueCollectionName(CollectionsContainer parent, String base) {
		String candidate = base;
		for (int n = 2; parent.findChieldByName(candidate + ".xml") != null; n++)
			candidate = base + " " + n;
		return candidate;
	}

	/** One card paired with which of a deck's lists it came from - see {@link #cardsOfWithSuffix}. */
	private static final class SuffixedCard {
		final String suffix;
		final IMagicCard card;

		SuffixedCard(String suffix, IMagicCard card) {
			this.suffix = suffix;
			this.card = card;
		}
	}

	/** A deck's own cards plus its Sideboard and Extra piles, if it has them - suffix dropped. */
	private static List<IMagicCard> cardsOf(CardCollection deck) {
		List<IMagicCard> all = new ArrayList<>();
		for (SuffixedCard sc : cardsOfWithSuffix(deck))
			all.add(sc.card);
		return all;
	}

	/**
	 * A deck's own cards plus its Sideboard and Extra piles, if it has them,
	 * each tagged with which list it came from ("", " (Sideboard)" or
	 * " (Extra)") - so a card needed in more than one of a deck's lists can be
	 * reported as separate lines instead of one merged count. Resolved
	 * directly by {@link Location} via {@code DataManager.getCardStore} - the
	 * same call {@code AccessoriesPage}/{@code DeckAccessoriesPopulator}
	 * already use for exactly this - checking the *returned* store for null,
	 * not a {@code loc.getFile().exists()} pre-check (which was the actual bug
	 * in an earlier version of this method: it under-reported existing
	 * sideboards). Not routed through {@code CardElement.getRelatedElements()}
	 * either - that depends on the sideboard/extra already being loaded as
	 * children in the in-memory navigator tree, which this view's own tree may
	 * not have triggered for every deck.
	 */
	private static List<SuffixedCard> cardsOfWithSuffix(CardCollection deck) {
		List<SuffixedCard> all = new ArrayList<>();
		for (IMagicCard c : deck.getStore().getCards())
			all.add(new SuffixedCard("", c));
		Location main = deck.getLocation().toMainDeck();
		addSuffixed(all, main.toSideboard(), " (Sideboard)");
		addSuffixed(all, main.toExtra(), " (Extra)");
		return all;
	}

	private static void addSuffixed(List<SuffixedCard> all, Location loc, String suffix) {
		try {
			ICardStore<IMagicCard> store = DataManager.getInstance().getCardStore(loc);
			if (store != null)
				for (IMagicCard c : store.getCards())
					all.add(new SuffixedCard(suffix, c));
		} catch (RuntimeException e) {
			// no sideboard/extra for this deck - same defensive catch
			// SideboardHelpHtmlExportDelegate uses around this same call
		}
	}

	/** Shared "total (genuine/proxy)" display format for the Owned/Boxed/Available columns. */
	private static String formatSplit(GenuineProxySplit split) {
		return split.total() + " (" + split.genuine + "/" + split.proxy + ")";
	}

	private static String formatCell(DeckCardInfo info) {
		int total = info.total();
		if (info.proxy == 0 && info.needed == 0)
			return String.valueOf(total); // all real
		if (info.real == 0 && info.needed == 0)
			return total + " (proxy)"; // all proxy
		if (info.real == 0 && info.proxy == 0)
			return total + " (needed)"; // all needed
		List<String> parts = new ArrayList<>();
		if (info.real > 0)
			parts.add(info.real + " real");
		if (info.proxy > 0)
			parts.add(info.proxy + " proxy");
		if (info.needed > 0)
			parts.add(info.needed + " needed");
		return total + " (" + String.join(", ", parts) + ")";
	}
}
