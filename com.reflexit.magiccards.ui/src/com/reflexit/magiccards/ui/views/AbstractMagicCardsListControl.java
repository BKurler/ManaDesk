/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */
package com.reflexit.magiccards.ui.views;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.preference.IPersistentPreferenceStore;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.UIJob;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.MagicException;
import com.reflexit.magiccards.core.MagicLogger;
import com.reflexit.magiccards.core.model.CardGroup;
import com.reflexit.magiccards.core.model.FilterField;
import com.reflexit.magiccards.core.model.GroupOrder;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardComparator;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardFilter;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.SortOrder;
import com.reflexit.magiccards.core.model.abs.ICard;
import com.reflexit.magiccards.core.model.abs.ICardCountable;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.model.abs.ICardGroup;
import com.reflexit.magiccards.core.model.events.CardEvent;
import com.reflexit.magiccards.core.model.events.ICardEventListener;
import com.reflexit.magiccards.core.model.storage.ICardStore;
import com.reflexit.magiccards.core.model.storage.IFilteredCardStore;
import com.reflexit.magiccards.core.model.utils.CardStoreUtils;
import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.PerspectiveFactoryMagic;
import com.reflexit.magiccards.ui.actions.GroupByAction;
import com.reflexit.magiccards.ui.actions.ImageAction;
import com.reflexit.magiccards.ui.actions.SearchCardAction;
import com.reflexit.magiccards.ui.actions.ShowPreferencesAction;
import com.reflexit.magiccards.ui.actions.SortAction;
import com.reflexit.magiccards.ui.actions.SortByAction;
import com.reflexit.magiccards.ui.actions.UnsortAction;
import com.reflexit.magiccards.ui.actions.ViewAsAction;
import com.reflexit.magiccards.ui.commands.ShowFilterHandler;
import com.reflexit.magiccards.ui.dnd.CopySupport;
import com.reflexit.magiccards.ui.dnd.MagicCardTransfer;
import com.reflexit.magiccards.ui.preferences.CustomGroupsPreferencePage;
import com.reflexit.magiccards.ui.preferences.PreferenceConstants;
import com.reflexit.magiccards.ui.preferences.PreferenceInitializer;
import com.reflexit.magiccards.ui.utils.WaitUtils;
import com.reflexit.magiccards.ui.views.columns.AbstractColumn;
import com.reflexit.magiccards.ui.views.columns.ColumnCollection;
import com.reflexit.magiccards.ui.views.columns.GroupColumn;
import com.reflexit.magiccards.ui.views.columns.MagicColumnCollection;
import com.reflexit.magiccards.ui.views.search.ISearchRunnable;
import com.reflexit.magiccards.ui.views.search.SearchContext;
import com.reflexit.magiccards.ui.views.search.SearchControl;
import com.reflexit.magiccards.ui.views.search.TableSearch;
import com.reflexit.magiccards.ui.widgets.QuickFilterControl;

/**
 * Magic card list control - MagicControl that represents list of cards (tree or
 * table), and comes with actions and preferences to manipulate this list
 *
 */
public abstract class AbstractMagicCardsListControl extends AbstractViewPage
		implements IMagicCardListControl, ICardEventListener {
	protected static final DataManager DM = DataManager.getInstance();
	private QuickFilterControl quickFilter;
	private SearchControl searchControl;
	private Label statusLine;
	private Composite topToolBar;
	protected GroupByAction actionGroupBy;
	protected ViewAsAction actionViewAs;
	protected Action actionDoubleClick;
	protected Action actionShowFilter;
	protected Action actionResetFilter;
	protected Action actionShowFind;
	protected Action actionShowPrefs;
	protected SortByAction actionSortBy;
	protected IMagicViewer viewer;
	protected IFilteredCardStore<ICard> fstore;
	private Presentation presentation = Presentation.TABLE;
	private final boolean fixedPresentation;
	private boolean suppressBridgeForwarding = false;

	/**
	 * Stable identities of the elements that should become the selection (the
	 * first of them scrolled into view) after the next data reload finishes.
	 * Because a single user action - a move, a remove, an add - can trigger
	 * several cascading refresh passes, this request survives until it is either
	 * satisfied or {@link #pendingRevealDeadline} passes, rather than being
	 * consumed by the first pass. Empty means "just keep the current selection".
	 */
	private final java.util.List<Object> pendingRevealKeys = new ArrayList<>();
	private long pendingRevealDeadline = 0;
	/**
	 * When {@code true} an ordinary {@link #setNextSelection(ISelection)} (for
	 * instance from the ADD event handler) will not replace the pending reveal
	 * request. Used by move / remove so the "select the next card" request is
	 * not clobbered by the ADD that the same operation fires on another view
	 * sharing this store.
	 */
	private boolean pendingRevealSticky = false;
	private static final long PENDING_REVEAL_TIMEOUT_MS = 5000;
	/**
	 * Table scroll position (first visible row) captured at the moment of the
	 * user action, before the burst of store events can scroll the list. Restored
	 * once, when the pending reveal is applied. -1 = none.
	 */
	private int pendingRevealTopIndex = -1;
	/** Leaf list snapshot taken at the top of a {@link #refreshViewer()} pass. */
	private java.util.List<Object> refreshLeafSnapshot;

	private Object lastInput;

	/**
	 * Flip to {@code true} to print a trace of the selection / reveal / refresh
	 * handling to the Eclipse console. Keep {@code false} for releases.
	 */
	static final boolean DEBUG = false;

	protected void trace(String msg) {
		if (DEBUG) {
			Location loc = fstore == null ? null : fstore.getLocation();
			System.out.println("[sel " + (loc == null ? getClass().getSimpleName() : loc.getName()) + "] " + msg);
		}
	}

	private static String eventTypeName(int type) {
		switch (type) {
		case CardEvent.ADD:
			return "ADD";
		case CardEvent.REMOVE:
			return "REMOVE";
		case CardEvent.UPDATE:
			return "UPDATE";
		default:
			return "type#" + type;
		}
	}

	private static String safeStr(java.util.concurrent.Callable<?> c) {
		try {
			return String.valueOf(c.call());
		} catch (Exception e) {
			return "<err:" + e + ">";
		}
	}

	/** A short window of {@code list} around {@code idx}, for diagnosing reorders. */
	private static String around(java.util.List<?> list, int idx, int radius) {
		if (idx < 0)
			return "<idx -1>";
		int from = Math.max(0, idx - radius);
		int to = Math.min(list.size(), idx + radius + 1);
		StringBuilder sb = new StringBuilder();
		for (int i = from; i < to; i++)
			sb.append("\n    [").append(i).append(i == idx ? "*] " : "]  ").append(list.get(i));
		return sb.toString();
	}

	// !!! RD 	private final java.util.List<ISelectionChangedListener> selectionListeners = new java.util.ArrayList<>();

	private ISelectionChangedListener statusSelectionListener = new ISelectionChangedListener() {
		@Override
		public void selectionChanged(SelectionChangedEvent event) {
			// selection changes on own view
			updateStatus();
		}
	};
	protected IPropertyChangeListener preferenceListener = new IPropertyChangeListener() {
		@Override
		public void propertyChange(PropertyChangeEvent event) {
			AbstractMagicCardsListControl.this.propertyChange(event);
		}
	};
	private Label warning;
	private String statusMessage = "";
	private boolean isFiltered = false;
	private boolean isGroupped = false;
	private Composite mainControl;
	// ⭐ Stable selection provider bridge
	private final java.util.List<ISelectionChangedListener> selectionListeners = new java.util.ArrayList<>();

	private final ISelectionProvider selectionProviderBridge = new ISelectionProvider() {
		@Override
		public ISelection getSelection() {
			return viewer == null ? StructuredSelection.EMPTY : viewer.getSelectionProvider().getSelection();
		}

		@Override
		public void setSelection(ISelection selection) {
			if (suppressBridgeForwarding)
				return; // highlightCard is calling us; do not forward back to viewer

			if (viewer != null) {
				viewer.getSelectionProvider().setSelection(selection);
			}
		}

		@Override
		public void addSelectionChangedListener(ISelectionChangedListener listener) {
			selectionListeners.add(listener);
			if (viewer != null) {
				viewer.getSelectionProvider().addSelectionChangedListener(listener);
			}
		}

		@Override
		public void removeSelectionChangedListener(ISelectionChangedListener listener) {
			selectionListeners.remove(listener);
			if (viewer != null) {
				viewer.getSelectionProvider().removeSelectionChangedListener(listener);
			}
		}
	};

	public AbstractMagicCardsListControl(Presentation pres) {
		this.presentation = pres;
		this.fixedPresentation = true;
	}

	public AbstractMagicCardsListControl(boolean fixed) {
		this.presentation = Presentation.TABLE;
		this.fixedPresentation = fixed;
	}

	public void setPresentation(Presentation presentation) {
		if (fixedPresentation) {
			MagicUIActivator.log(new Exception("Cannot call set presentation on static presentation view"));
			return;
		}
		this.presentation = presentation;
	}

	public Presentation getPresentation() {
		return presentation;
	}

	protected void switchPresentation(Presentation selected) {
		if (fixedPresentation) {
			MagicUIActivator.log(new Exception("Cannot call switch presentation on static presentation view"));
			return;
		}
		trace("switchPresentation -> " + selected);
		setPresentation(selected);
		if (actionViewAs != null)
			actionViewAs.syncIcon();
		createTableControl(mainControl);
		if (getGroupAction() != null) {
			boolean cangroup = getPresentation() != Presentation.TABLE;
			getGroupAction().setEnabled(cangroup);
		}
		mainControl.layout(true, true);
		// Re-read the stored grouping / sort and FORCE a real re-group. reGroup()
		// alone goes through update(), which skips when the filter looks
		// unchanged - so a group tree that was first built before the collection
		// cards were reconciled with the DB (every card CMC 0 / type Unknown)
		// would just be re-shown as-is. setRefreshRequired(true) makes update()
		// rebuild it against the now-resolved cards.
		setNextSelection(getSelection());
		syncFilter();
		forceStoreRegroup();
		loadData(null);
		updateGroupingVisibility();
		// RD Fix context menu not working after changing presentation
		// (tree,listview,gallery etc) (from Speedprog)
		hookContextMenu();
	}

	public void createMainControl(Composite area) {
		if (!fixedPresentation) {
			String cur = getColumnsPreferenceStore().getString(PreferenceConstants.PRESENTATION_VIEW);
			if (cur != null) {
				try {
					Presentation pres = Presentation.valueOf(cur);
					setPresentation(pres);
				} catch (RuntimeException e) {
					// ignore
				}
			}
		}
		mainControl = new Composite(area, SWT.NONE);
		mainControl.setLayout(GridLayoutFactory.fillDefaults().spacing(0, 0).create());
		mainControl.setLayoutData(GridDataFactory.fillDefaults().grab(true, true).create());

		createTopBar(mainControl);

		// ⭐ FIX: search bar must be created BEFORE the viewer
		createSearchControl(mainControl);

		// ⭐ Viewer created last so it fills remaining space
		createTableControl(mainControl);

		getSelectionProvider().addSelectionChangedListener(statusSelectionListener);
	}

	public IMagicViewer createViewer(Composite parent) {
		MagicColumnCollection columns = new MagicColumnCollection(getPreferencePageId());
		if (presentation == Presentation.TABLE) {
			LazyTableViewer v = new LazyTableViewer(parent, columns);
			return v;
		}
		if (presentation == Presentation.TREE) {
			ExtendedTreeViewer v = new ExtendedTreeViewer(parent, columns);
			// v.setContentProvider(new RootTreeViewerContentProvider());
			return v;
		}
		if (presentation == Presentation.SPLITTREE)
			return new SplitViewer(parent, getPreferencePageId());

		if (presentation == Presentation.GALLERY)
			return new com.reflexit.magiccards.ui.gallery.SplitGalleryViewer(parent, getPreferencePageId());

		throw new IllegalArgumentException(presentation.name());
	}

	@Override
	public MagicCardFilter getFilter() {
		if (getFilteredStore() == null)
			return null;
		return getFilteredStore().getFilter();
	}

	@Override
	public synchronized IFilteredCardStore getFilteredStore() {
		if (fstore == null) {
			fstore = doGetFilteredStore();
			if (fstore != null) {
				if (actionSortBy != null)
					actionSortBy.setFilter(fstore.getFilter());
				if (actionGroupBy != null)
					actionGroupBy.setFilter(fstore.getFilter());
			}
		}
		return fstore;
	}

	public Action getGroupAction() {
		return actionGroupBy;
	}

	@Override
	public IPersistentPreferenceStore getColumnsPreferenceStore() {
		return PreferenceInitializer.getLocalStore(getPreferencePageId());
	}

	@Override
	public IPersistentPreferenceStore getElementPreferenceStore() {
		return PreferenceInitializer.getFilterStore(getPreferencePageId());
	}

	public IPersistentPreferenceStore getPresentaionPreferenceStore() {
		return getColumnsPreferenceStore();
	}

	public IMagicViewer getManager() {
		return this.viewer;
	}

	@Override
	public ISelection getSelection() {
		if (viewer == null)
			return StructuredSelection.EMPTY;

		final ISelection[] result = { StructuredSelection.EMPTY };

		Display display = PlatformUI.getWorkbench().getDisplay();
		if (display == null || display.isDisposed())
			return StructuredSelection.EMPTY;

		display.syncExec(() -> {
			try {
				if (viewer.getSelectionProvider() != null) {
					result[0] = viewer.getSelectionProvider().getSelection();
				}
			} catch (Exception e) {
				MagicUIActivator.log(e);
			}
		});

		return result[0];
	}

	public Action getShowFilterAction() {
		return actionShowFilter;
	}

	public String getStatusMessage() {
		IFilteredCardStore filteredStore = getFilteredStore();
		if (filteredStore == null)
			return "";
		ICardStore cardStore = filteredStore.getCardStore();
		int shownSize = filteredStore.getFlatSize();
		int storeSize = cardStore.size();
		if (storeSize == 0)
			return "";
		int storeCount = storeSize;
		if (cardStore instanceof ICardCountable) {
			storeCount = ((ICardCountable) cardStore).getCount();
		}
		int shownCount = shownSize;
		if (storeCount != storeSize) // collection not db
			shownCount = filteredStore.getCount();
		String mainMessage = "Total " + cardsUnique(shownCount, storeCount, shownSize, storeSize) + " cards";
		// if (shownSize != storeSize) { // filter is active
		// mainMessage += ". Filtered " + (storeCount - shownCount);
		// }
		IStructuredSelection sel = (IStructuredSelection) getSelection();
		if (sel != null && !sel.isEmpty()) { // selection
			int selCount = CardStoreUtils.countCards(sel.toList());
			int selSize = sel.size();
			mainMessage += ". Selected " + cardsUnique(selCount, selCount, selSize, selSize);
			// For an unsorted collection the cards physically sit in the box in
			// entry order - show where the first selected card is so it can be
			// found quickly. Counted in individual copies, not unique rows.
			int pos = firstSelectedUnsortedPosition(sel, cardStore);
			if (pos > 0)
				mainMessage += ". Position " + pos + " of " + storeCount;
		}
		return mainMessage;
	}

	/**
	 * 1-based position of the first selected card in the <em>physical box</em> of
	 * an <em>unsorted</em> collection - i.e. the on-disk / entry order, counted in
	 * individual card copies (a stack of 3 advances the count by 3). This is
	 * deliberately independent of how the current view is grouped or sorted, so
	 * the same card always reports the same position whatever view you look at.
	 * Returns -1 when the collection is not marked unsorted, the selection is not
	 * a single card, or the card cannot be located.
	 */
	private int firstSelectedUnsortedPosition(IStructuredSelection sel, ICardStore cardStore) {
		if (cardStore == null || sel == null || sel.isEmpty())
			return -1;
		try {
			if (!cardStore.isUnsorted())
				return -1;
		} catch (UnsupportedOperationException e) {
			return -1; // e.g. the card DB store - not a collection
		}
		Object first = sel.getFirstElement();
		if (first instanceof TreePath)
			first = ((TreePath) first).getLastSegment();
		if (first instanceof ICardGroup || !(first instanceof IMagicCard))
			return -1;
		IMagicCard target = (IMagicCard) first;
		// The raw store list IS the box order (MemoryCardStorage = an ArrayList
		// filled in file order). Use it directly - NOT the view's flat list,
		// which reorders under grouping / sort and made the number differ
		// between views.
		java.util.List<?> box;
		try {
			box = cardStore.getCards();
		} catch (Exception e) {
			return -1;
		}
		int pos = unsortedCopyPosition(box, target);
		if (DEBUG)
			trace("unsortedPosition: '" + target.getName() + "' -> " + pos + " (box size " + box.size() + ")");
		return pos;
	}

	/**
	 * 1-based position of {@code target} within {@code box}, counted in
	 * individual card copies (a stack of 3 advances the count by 3), or -1 if it
	 * is not there. An identity match is tried first: duplicate cards (e.g. four
	 * count-1 "Clue" rows) are value-equal, so {@code equals()} alone would keep
	 * matching the first copy and the position would jump as the user clicks
	 * different rows; the group tree holds the same instances the store does, so
	 * identity pins the right one. Public for unit testing.
	 */
	public static int unsortedCopyPosition(java.util.List<?> box, IMagicCard target) {
		if (box == null || target == null)
			return -1;
		int pos = 1;
		for (Object o : box) {
			if (o == target)
				return pos;
			if (o instanceof IMagicCard)
				pos += copiesOf((IMagicCard) o);
		}
		pos = 1;
		for (Object o : box) {
			if (o instanceof IMagicCard) {
				if (o.equals(target))
					return pos;
				pos += copiesOf((IMagicCard) o);
			}
		}
		return -1;
	}

	private static int copiesOf(IMagicCard c) {
		if (c instanceof MagicCardPhysical) {
			int n = ((MagicCardPhysical) c).getCount();
			return n > 0 ? n : 1;
		}
		return 1;
	}

	private String cardsUnique(int a, int ta, int b, int tb) {
		if (a != b)
			return countOf(a, ta) + " (unique " + countOf(b, tb) + ")";
		else
			return countOf(a, ta) + "";
	}

	private String countOf(int a, int ta) {
		if (a != ta)
			return a + " of " + ta;
		else
			return a + "";
	}

	public Composite getTopBar() {
		return topToolBar;
	}

	protected Viewer getViewer() {
		return this.viewer.getViewer();
	}

	@Override
	public boolean hookContextMenu(MenuManager menuMgr) {
		return viewer.hookContextMenu(menuMgr);
	}

	protected void addListeners() {
		MagicUIActivator.getDefault().getPreferenceStore().addPropertyChangeListener(preferenceListener);
		PlatformUI.getWorkbench().getThemeManager().addPropertyChangeListener(preferenceListener);
		addStoreChangeListener();
		getColumnsPreferenceStore().addPropertyChangeListener(preferenceListener);
	}

	protected void removeListeners() {
		removeStoreChangeListener();
		getColumnsPreferenceStore().removePropertyChangeListener(preferenceListener);
		MagicUIActivator.getDefault().getPreferenceStore().removePropertyChangeListener(preferenceListener);
		PlatformUI.getWorkbench().getThemeManager().removePropertyChangeListener(preferenceListener);
	}

	@Override
	public void dispose() {
		if (viewer != null) {
			getSelectionProvider().removeSelectionChangedListener(statusSelectionListener);
			this.viewer.dispose();
		}
		try {
			getColumnsPreferenceStore().save();
			getElementPreferenceStore().save();
			getPresentaionPreferenceStore().save();
		} catch (IOException e) {
			MagicUIActivator.log(e);
		}
		deactivate();
		super.dispose();
	}

	public void addStoreChangeListener() {
		DM.getLibraryCardStore().addListener(AbstractMagicCardsListControl.this);
		DM.getMagicDBStore().addListener(AbstractMagicCardsListControl.this);
	}

	protected void removeStoreChangeListener() {
		DM.getLibraryCardStore().removeListener(this);
		DM.getMagicDBStore().removeListener(AbstractMagicCardsListControl.this);
	}

	public void reGroup() {
		refresh();
		updateGroupingVisibility();

	}

	@Override
	public void refresh() {
		MagicLogger.trace("reload data " + getClass());
		setNextSelection(getSelection());
		syncFilter();
		// syncFilter() just recomputed isGroupped from the stored GROUP_FIELD.
		// The split viewer maximises its card pane (hiding the tree) while
		// isGroupped is false, and at startup updateGroupingVisibility() runs
		// from createTableControl() *before* the first syncFilter() - so without
		// this the tree pane stays hidden even though grouping is on.
		updateGroupingVisibility();
		loadData(null);
	}

	protected void syncSortColumnIndicator() {
		if (viewer instanceof IMagicColumnViewer) {
			IMagicColumnViewer cviewer = (IMagicColumnViewer) viewer;
			SortOrder o = getFilter().getSortOrder();
			if (o.isEmpty()) {
				cviewer.setSortColumn(-1, 0);
			} else {
				MagicCardComparator top = o.peek();
				ICardField field = top.getField();
				AbstractColumn column = cviewer.getColumnsCollection().getColumn(field);
				if (column == null && field == MagicCardField.CMC) {
					column = cviewer.getColumnsCollection().getColumn(MagicCardField.COST);
				}
				if (column != null) {
					int index = column.getColumnIndex();
					cviewer.setSortColumn(index, o.isAccending(field) ? -1 : 1);
				} else {
					cviewer.setSortColumn(-1, 0);
				}
			}
		}
	}

	public void refilterData() {
		MagicLogger.trace("refilter data " + getClass());
		setNextSelection(null);
		syncFilter();
		loadData(new Runnable() {
			@Override
			public void run() {
				refreshViewer();
				selectFirstVisible();
			}
		});
	}

	public void runFind() {
		searchControl.setVisible(true);
		searchControl.getControl().setFocus();
	}

	@Override
	public void setNextSelection(ISelection structuredSelection) {
		if (!(structuredSelection instanceof IStructuredSelection) || structuredSelection.isEmpty()) {
			// explicit clear (e.g. refilterData)
			clearPendingReveal();
			return;
		}
		if (pendingRevealSticky && !isPendingRevealExpired()) {
			// a move / remove already asked for a specific "next" element - keep it
			trace("setNextSelection ignored (sticky reveal " + pendingRevealKeys + " pending)");
			return;
		}
		setPendingReveal(((IStructuredSelection) structuredSelection).toList(), false);
	}

	/**
	 * Ask that {@code element} becomes the selection and is revealed once the
	 * pending data reload settles. Unlike {@link #setNextSelection(ISelection)}
	 * this request is "sticky": it will not be overwritten by the ADD/REMOVE
	 * event handlers while it is still pending. Used by move / remove to select
	 * the following card.
	 */
	public void revealElementAfterRefresh(Object element) {
		setPendingReveal(java.util.Collections.singletonList(element), true);
		// Gallery: seed the target now, before the move's refreshViewer rebuilds
		// the browser page, so the selection + scroll are baked into that ONE
		// render instead of appearing a beat later (select -> flash empty ->
		// reselect).
		if (viewer instanceof com.reflexit.magiccards.ui.gallery.SplitGalleryViewer
				&& element instanceof com.reflexit.magiccards.core.model.IMagicCard) {
			ISelectionProvider sp = viewer.getSelectionProvider();
			if (sp instanceof com.reflexit.magiccards.ui.gallery.BrowserGalleryViewer)
				((com.reflexit.magiccards.ui.gallery.BrowserGalleryViewer) sp).setPendingSelectId(
						String.valueOf(((com.reflexit.magiccards.core.model.IMagicCard) element).getCardId()));
		}
	}

	private void setPendingReveal(java.util.List<?> elements, boolean sticky) {
		pendingRevealKeys.clear();
		for (Object e : elements) {
			Object key = stableKey(e);
			if (key != null)
				pendingRevealKeys.add(key);
		}
		pendingRevealSticky = sticky && !pendingRevealKeys.isEmpty();
		pendingRevealDeadline = System.currentTimeMillis() + PENDING_REVEAL_TIMEOUT_MS;
		// Capture the scroll position now, while the list is still where the user
		// left it (the store events that follow will scroll it around).
		pendingRevealTopIndex = pendingRevealKeys.isEmpty() ? -1 : savedTopIndex();
		trace("setPendingReveal " + pendingRevealKeys + (sticky ? " (sticky)" : "") + " topIndex=" + pendingRevealTopIndex);
	}

	private void clearPendingReveal() {
		pendingRevealKeys.clear();
		pendingRevealSticky = false;
		pendingRevealDeadline = 0;
		pendingRevealTopIndex = -1;
	}

	private boolean isPendingRevealExpired() {
		return System.currentTimeMillis() > pendingRevealDeadline;
	}

	/**
	 * A value that identifies a list element across a data reload (which rebuilds
	 * the model objects). {@link MagicCardPhysical#hashCode()} is identity based,
	 * so we cannot rely on the element instance surviving a reload.
	 */
	private static Object stableKey(Object element) {
		if (element == null)
			return null;
		if (element instanceof ICardGroup)
			return "G:" + ((ICardGroup) element).getName();
		if (element instanceof MagicCardPhysical) {
			String id = ((MagicCardPhysical) element).getCardId();
			return id != null ? "P:" + id : element;
		}
		if (element instanceof MagicCard) {
			String id = ((MagicCard) element).getCardId();
			return id != null ? "C:" + id : element;
		}
		return element;
	}

	/**
	 * All card elements of the filtered store in display order, with any groups
	 * flattened. {@code IFilteredCardStore.getElement(int)} is indexed by
	 * top-level child, not by flat leaf, so it cannot be used to walk a grouped
	 * list.
	 */
	private java.util.List<Object> flatLeaves() {
		java.util.List<Object> model = flatElements(false);
		// When the user has clicked a column header the widget re-sorts the flat
		// element list with a ViewerComparator that is independent of the model
		// tree order. "Next card after a move" must follow what the user sees, so
		// apply that same comparator here.
		try {
			org.eclipse.jface.viewers.Viewer v = viewer == null ? null : viewer.getViewer();
			if (v instanceof org.eclipse.jface.viewers.StructuredViewer) {
				org.eclipse.jface.viewers.StructuredViewer sv = (org.eclipse.jface.viewers.StructuredViewer) v;
				org.eclipse.jface.viewers.ViewerComparator vc = sv.getComparator();
				if (vc != null && !model.isEmpty()) {
					Object[] arr = model.toArray();
					vc.sort(sv, arr);
					trace("flatLeaves: applied column comparator " + vc.getClass().getSimpleName());
					return new ArrayList<>(java.util.Arrays.asList(arr));
				}
			}
		} catch (Exception e) {
			trace("flatLeaves: comparator sort failed " + e);
		}
		return model;
	}

	private java.util.List<Object> flatElements(boolean includeGroups) {
		java.util.List<Object> out = new ArrayList<>();
		IFilteredCardStore store = getFilteredStore();
		if (store != null) {
			ICardGroup root = store.getCardGroupRoot();
			if (root != null)
				collectElements(root, out, includeGroups);
		}
		return out;
	}

	private static void collectElements(ICardGroup group, java.util.List<Object> out, boolean includeGroups) {
		Object[] children = group.getChildren();
		if (children == null)
			return;
		for (Object child : children) {
			if (child instanceof ICardGroup) {
				if (includeGroups)
					out.add(child);
				collectElements((ICardGroup) child, out, includeGroups);
			} else if (child != null) {
				out.add(child);
			}
		}
	}

	@Override
	public void setStatus(String text) {
		if (statusLine == null)
			return;
		if (statusLine.isDisposed())
			return;
		if (statusLine.getText().equals(text))
			return;
		this.statusLine.setText(text);
		this.statusLine.setToolTipText(text);
		if (text.isEmpty()) {
			statusLine.setVisible(false);
		} else {
			statusLine.setVisible(true);
		}
		statusLine.getParent().getParent().layout(true, true);
	}

	public void setWarning(boolean war) {
		if (warning == null)
			return;
		if (warning.isDisposed())
			return;
		warning.setVisible(war);
		warning.setToolTipText("There are " + getFiltered() + " hidden cards!\nChange filter to see more");
		warning.getParent().layout(true, true);
	}

	private Label createStatusLine(Composite composite) {
		Label statusLine = new Label(composite, SWT.NONE);
		statusLine.setText("Status");
		return statusLine;
	}

	private HashMap<String, String> storeToMap(IPreferenceStore store) {
		HashMap<String, String> map = new HashMap<>();
		Collection col = FilterField.getAllIds();
		for (Iterator iterator = col.iterator(); iterator.hasNext();) {
			String id = (String) iterator.next();
			String value = store.getString(id);
			if (value != null && value.length() > 0) {
				map.put(id, value);
				// System.err.println(id + "=" + value);
			}
		}
		return map;
	}

	/**
	 * @param composite
	 * @return
	 */
	protected QuickFilterControl createQuickFilterControl(Composite composite) {
		QuickFilterControl quickFilter = new QuickFilterControl(composite, new Runnable() {
			@Override
			public void run() {
				refilterData();
			}
		}, false);
		return quickFilter;
	}

	/**
	 * @param composite
	 */
	protected void createSearchControl(Composite composite) {
		this.searchControl = new SearchControl(new ISearchRunnable() {
			@Override
			public void run(SearchContext context) {
				runSearch(context);
			}
		}, this // pass the view
		);

		this.searchControl.createFindBar(composite);
		this.searchControl.setVisible(false);
		this.searchControl.setSearchAsYouType(true);
		// searchControl.getControl().setBackground(Display.getDefault().getSystemColor(SWT.COLOR_CYAN));
	}

	protected Control createTableControl(Composite parent) {
		if (viewer != null && !viewer.getControl().isDisposed()) {
			viewer.getControl().dispose();
		}

		viewer = null; // absolutely required
		this.viewer = createViewer(parent);

		// Reattach all selection listeners to the new viewer
		for (ISelectionChangedListener l : selectionListeners) {
			viewer.getSelectionProvider().addSelectionChangedListener(l);
		}

		// update search anchor when user selects a card
		viewer.getSelectionProvider().addSelectionChangedListener(event -> {
			if (suppressBridgeForwarding)
				return; // ignore programmatic selection (highlight)

			ISelection sel = event.getSelection();
			if (!(sel instanceof IStructuredSelection))
				return;

			Object first = ((IStructuredSelection) sel).getFirstElement();
			if (first == null)
				return;

			// Convert to TreePath if possible
			TreePath path = null;
			if (first instanceof ICard) {
				path = findPathForCard((ICard) first);
			} else if (first instanceof TreePath) {
				path = (TreePath) first;
			}

			if (path != null) {
				searchControl.getContext().setLast(path);
			}
		});

		// Reapply input to the new viewer
		if (lastInput != null) {
			viewer.setInput(lastInput);
		}

		Control control = viewer.getControl();
		control.setLayoutData(new GridData(GridData.FILL_BOTH));
		this.viewer.hookContext(PerspectiveFactoryMagic.TABLES_CONTEXT);
		this.viewer.hookSortAction(this::sort);

		attachRefreshFlushOnClick();
		updateGroupingVisibility();

		return control;
	}

	/** On mouse-down in any viewer pane, flush a queued refresh first (see {@link #flushPendingRefresh()}). */
	private void attachRefreshFlushOnClick() {
		java.util.List<Control> controls = new ArrayList<>();
		if (viewer instanceof SplitViewer) {
			SplitViewer sv = (SplitViewer) viewer;
			if (sv.getStructuredViewer() != null)
				controls.add(sv.getStructuredViewer().getControl());
			if (sv.getViewer() != null)
				controls.add(sv.getViewer().getControl());
		} else if (viewer instanceof com.reflexit.magiccards.ui.gallery.SplitGalleryViewer) {
			com.reflexit.magiccards.ui.gallery.SplitGalleryViewer gv = (com.reflexit.magiccards.ui.gallery.SplitGalleryViewer) viewer;
			if (gv.getStructuredViewer() != null)
				controls.add(gv.getStructuredViewer().getControl());
			controls.add(gv.getRightControl());
		} else if (viewer.getViewer() != null) {
			controls.add(viewer.getViewer().getControl());
		}
		if (viewer.getControl() != null)
			controls.add(viewer.getControl());
		org.eclipse.swt.widgets.Listener flush = e -> flushPendingRefresh();
		for (Control c : controls) {
			if (c == null || c.isDisposed())
				continue;
			c.addListener(SWT.MouseDown, flush);
		}
	}

	protected Composite createTopBar(Composite composite) {
		topToolBar = new Composite(composite, SWT.NONE);
		topToolBar.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).create());
		topToolBar.setLayout(GridLayoutFactory.fillDefaults().numColumns(3).create());
		quickFilter = createQuickFilterControl(topToolBar);
		quickFilter.setLayoutData(new GridData());
		statusLine = createStatusLine(topToolBar);
		statusLine.setLayoutData(GridDataFactory.fillDefaults().align(SWT.FILL, SWT.CENTER).grab(true, false).create());
		warning = new Label(topToolBar, SWT.NONE);
		warning.setImage(MagicUIActivator.getImage("icons/clcl16/exclamation.gif"));
		warning.setToolTipText("There are filtered cards!");
		warning.addMouseListener(new MouseListener() {
			@Override
			public void mouseUp(MouseEvent e) {
				actionShowFilter.run();
			}

			@Override
			public void mouseDown(MouseEvent e) {
				// nothing
			}

			@Override
			public void mouseDoubleClick(MouseEvent e) {
				actionShowFilter.run();
			}
		});
		return topToolBar;
	}

	@Override
	public void fillContextMenu(IMenuManager manager) {
		// manager.add(this.actionShowFind);
		// manager.add(this.actionShowFilter);
		// manager.add(this.actionResetFilter);
		manager.add(this.actionShowPrefs);
	}

	@Override
	public void fillLocalPullDown(IMenuManager manager) {
		if (actionSortBy != null)
			manager.add(this.actionSortBy.createMenuManager());
		if (actionGroupBy != null)
			manager.add(this.actionGroupBy.createMenuManager());
		manager.add(this.actionShowFilter);
		manager.add(this.actionResetFilter);
		manager.add(this.actionShowPrefs);
	}

	@Override
	public void fillLocalToolBar(IToolBarManager manager) {
		if (actionViewAs != null)
			manager.add(this.actionViewAs);
		if (actionGroupBy != null)
			manager.add(this.actionGroupBy);
		if (actionSortBy != null)
			manager.add(this.actionSortBy);
		if (actionShowFind != null)
			manager.add(this.actionShowFind);
		manager.add(this.actionShowFilter);
		manager.add(this.actionResetFilter);
		manager.add(this.actionShowPrefs);
	};

	protected String getViewPreferencePageId() {
		if (getMagicCardsView() != null)
			return getMagicCardsView().getPreferencePageId();
		return null;
	};

	protected abstract String getPreferencePageId();

	@Override
	public ISelectionProvider getSelectionProvider() {
		return selectionProviderBridge;
	}

	/**
	 * @param last
	 */

	// Cleaned highlightCard(...)
	protected void highlightCard(Object last) {
		StructuredSelection selection = (last instanceof TreePath) ? new TreeSelection((TreePath) last)
				: new StructuredSelection(last);

		if (DEBUG) {
			System.out.println(
					"[HIGHLIGHT] last=" + last + " class=" + (last == null ? "null" : last.getClass().getName()));
			System.out.println("[HIGHLIGHT] selection=" + selection);
		}

		// Expand parent groups before selecting
		if (last instanceof TreePath) {
			TreePath path = (TreePath) last;
			Viewer v = viewer.getViewer();

			if (v instanceof TreeViewer) {
				TreeViewer tv = (TreeViewer) v;

				// Expand all parent segments (except the last one, which is the card)
				for (int i = 0; i < path.getSegmentCount() - 1; i++) {
					Object segment = path.getSegment(i);
					tv.expandToLevel(segment, 1);
				}
			}
		}

		// Update the viewer (reveal = true)
		viewer.getViewer().setSelection(selection, true);

		// Update the global selection provider (bridge)
		suppressBridgeForwarding = true;
		try {
			if (DEBUG) {
				System.out.println("[HIGHLIGHT] pushing to bridge: " + selection);
			}
			selectionProviderBridge.setSelection(selection);
		} finally {
			suppressBridgeForwarding = false;
		}
	}

	public Object getCurrentSelectionElement() {
		ISelection sel = getSelection();
		if (sel instanceof IStructuredSelection)
			return ((IStructuredSelection) sel).getFirstElement();
		return null;
	}

	protected void hookDoubleClickAction() {
		// override to hook
		if (actionDoubleClick != null)
			viewer.addDoubleClickListener(new IDoubleClickListener() {
				@Override
				public void doubleClick(DoubleClickEvent event) {
					actionDoubleClick.run();
				}
			});
	}

	protected String getName() {
		if (fstore == null)
			return "";
		Location loc = fstore.getLocation();
		if (loc == null)
			return "";
		return loc.getName();
	}

	@Override
	protected void makeActions() {
		super.makeActions();
		this.actionShowFilter = new ImageAction("Filter...", "icons/clcl16/filter_ps.png", "Opens a Card Filter Dialog",
				this::runShowFilter);
		this.actionResetFilter = new ImageAction("Reset Filter", "icons/clcl16/reset_filter.gif",
				"Resets the filter to default values", this::runResetFilter);
		this.actionSortBy = new SortByAction(getSortColumnCollection(), null, getPresentaionPreferenceStore(),
				this::refresh);
		this.actionGroupBy = new GroupByAction(getGroups(), null, getPresentaionPreferenceStore(), this::reGroup);
		this.actionShowPrefs = new ShowPreferencesAction(getPreferencePageId()) {
			@Override
			public void before() {
				saveColumnLayout();
			}
		};
		this.actionShowPrefs.setImageDescriptor(MagicUIActivator.getImageDescriptor("icons/clcl16/gear.png"));
		this.actionShowFind = new SearchCardAction(this::runFind);
		if (!fixedPresentation)
			this.actionViewAs = new ViewAsAction(Arrays.asList(Presentation.values()), getColumnsPreferenceStore(),
					this::switchPresentation);
		// double click
		hookDoubleClickAction();
		// disable group by
		if (getPresentation() == Presentation.TABLE)
			getGroupAction().setEnabled(false);
	}

	public ColumnCollection getSortColumnCollection() {
		if (viewer instanceof IMagicColumnViewer) {
			return ((IMagicColumnViewer) viewer).getColumnsCollection();
		}
		return new MagicColumnCollection("");
	}

	protected Collection<GroupOrder> getGroups() {
		ArrayList<GroupOrder> res = new ArrayList<>();
		res.add(new GroupOrder());
		res.add(new GroupOrder("Color", MagicCardField.COST));
		res.add(new GroupOrder("Cost", MagicCardField.CMC));
		res.add(new GroupOrder(MagicCardField.TYPE));
		res.add(new GroupOrder("Core/Block/Set/Rarity", //
				MagicCardField.SET_CORE, MagicCardField.SET_BLOCK, MagicCardField.SET, MagicCardField.RARITY));
		res.add(new GroupOrder(MagicCardField.SET));
		res.add(new GroupOrder(MagicCardField.SET, MagicCardField.RARITY));
		res.add(new GroupOrder(MagicCardField.RARITY));
		res.add(new GroupOrder(MagicCardField.NAME));
		res.add(new GroupOrder(MagicCardField.OWNERSHIP, MagicCardField.NAME));
		res.addAll(new CustomGroupsPreferencePage().getCurrentValue());
		return res;
	}

	protected void propertyChange(PropertyChangeEvent event) {
		if (viewer.getViewer() == null || viewer.getViewer().getControl() == null)
			return;
		String property = event.getProperty();
		Object newValue = event.getNewValue();
		if (property.equals(PreferenceConstants.LOCAL_COLUMNS)) {
			if (viewer instanceof IMagicColumnViewer) {
				WaitUtils.syncExec(() -> {
					synchronized (AbstractMagicCardsListControl.this) {
						((IMagicColumnViewer) viewer).updateColumns((String) newValue);
					}
				});
				scheduleRefreshViewer();
			}
		} else if (property.equals(PreferenceConstants.SHOW_GRID)) {
			scheduleRefreshViewer();
		} else if (property.equals(PreferenceConstants.LOCAL_SHOW_QUICKFILTER)) {
			boolean qf = Boolean.valueOf(newValue.toString());
			WaitUtils.asyncExec(() -> setQuickFilterVisible(qf));
		} else if (newValue instanceof FontData[] || newValue instanceof RGB) {
			scheduleRefreshViewer();
		}
	}

	protected void scheduleRefreshViewer() {
		UIJob uiJob = new UIJob("Refresh") {
			@Override
			public boolean shouldSchedule() {
				Job[] jobs = getJobManager().find(getControl());
				for (Job job : jobs) {
					if (job.getState() == Job.WAITING)
						return false;
				}
				if (jobs.length >= 2)
					return false;
				return super.shouldSchedule();
			}

			@Override
			public boolean shouldRun() {
				Job[] jobs = getJobManager().find(getControl());
				for (Job job : jobs) {
					if (job.getState() == Job.WAITING)
						return false;
				}
				return true;
			}

			@Override
			public boolean belongsTo(Object family) {
				return family == getControl();
			}

			@Override
			public IStatus runInUIThread(IProgressMonitor monitor) {
				refreshViewer();
				return Status.OK_STATUS;
			}
		};
		uiJob.schedule(10);
	}

	public void runCopy() {
		Control fc = getControl().getDisplay().getFocusControl();
		CopySupport.runCopy(fc);
	}

	/**
	 * @param context
	 */
	/**
	 * The group Find should search inside. For the split viewer that is the node
	 * currently selected in the tree, so Find is limited to what the right pane
	 * shows; for every other viewer it is the whole filtered store root (the
	 * historical behaviour).
	 */
	private ICardGroup searchRootGroup() {
		IFilteredCardStore fs = getFilteredStore();
		ICardGroup storeRoot = fs == null ? null : fs.getCardGroupRoot();
		if (!(viewer instanceof SplitViewer))
			return storeRoot;
		// runSearch() runs on a worker thread; the tree selection is an SWT
		// widget, so read it on the UI thread.
		final ICardGroup[] holder = new ICardGroup[1];
		try {
			WaitUtils.syncExec(() -> {
				try {
					if (!(viewer instanceof SplitViewer))
						return;
					ISelection sel = ((SplitViewer) viewer).getStructuredViewer().getSelection();
					if (sel instanceof IStructuredSelection && !sel.isEmpty()) {
						Object first = ((IStructuredSelection) sel).getFirstElement();
						if (first instanceof TreePath)
							first = ((TreePath) first).getLastSegment();
						if (first instanceof ICardGroup)
							holder[0] = (ICardGroup) first;
					}
				} catch (Exception inner) {
					trace("searchRootGroup (ui) failed " + inner);
				}
			});
		} catch (Exception e) {
			trace("searchRootGroup failed " + e);
		}
		return holder[0] != null ? holder[0] : storeRoot;
	}

	protected void runSearch(final SearchContext context) {
		TableSearch.search(context, getFilteredStore(), searchRootGroup());

		if (!context.isFound())
			return;

		Object last = context.getLast();
		TreePath lastPath = null;

		// Case 1: already a TreePath
		if (last instanceof TreePath) {
			lastPath = (TreePath) last;
		}

		// Case 2: selection was an ICard (Split Table View)
		else if (last instanceof ICard) {
			lastPath = findPathForCard((ICard) last);
		}

		// Store normalized anchor back into context
		context.setLast(lastPath);

		// Highlight on UI thread
		final Object finalLast = lastPath;
		WaitUtils.syncExec(() -> highlightCard(finalLast));
	}

	protected void runShowFilter() {
		if (ShowFilterHandler.execute()) {
			syncQuickFilter();
			refilterData();
		}
	}

	protected void runResetFilter() {
		getSelectionProvider().setSelection(new StructuredSelection()); // remove
																		// selection
		PreferenceInitializer.setToDefault(getElementPreferenceStore());
		syncQuickFilter();
		refilterData();
	}

	public void syncQuickFilter() {
		boolean sup = quickFilter.isSuppressUpdates();
		quickFilter.setSuppressUpdates(true);
		try {
			quickFilter.refresh();
		} finally {
			quickFilter.setSuppressUpdates(sup);
		}
	}

	protected AbstractCardsView getMagicCardsView() {
		if (getViewPart() instanceof AbstractCardsView)
			return (AbstractCardsView) getViewPart();
		return null;
	}

	/**
	 * @param bars
	 */
	@Override
	public void setGlobalHandlers(IActionBars bars) {
		if (getMagicCardsView() != null) {
			getMagicCardsView().activateActionHandler(actionShowFind, actionShowFind.getActionDefinitionId());
		}
	}

	protected void setQuickFilterVisible(boolean qf) {
		if (quickFilter != null)
			quickFilter.setVisible(qf);
	}

	protected void sort(int index, int dir) {
		updateSortColumn(index);
		loadData(null);
	}

	public void unsort() {
		updateSortColumn(-1);
	}

	public void syncFilter() {
		MagicCardFilter filter = getFilter();
		if (filter == null)
			return;
		IPreferenceStore store = getElementPreferenceStore();
		if (quickFilter != null)
			quickFilter.setPreferenceStore(store);
		HashMap<String, String> map = storeToMap(store);
		filter.update(map);
		// !!! RD
		// filter.setOnlyLastSet(store.getBoolean(EditionsFilterPreferencePage.LAST_SET));
		IPersistentPreferenceStore viewSettings = getPresentaionPreferenceStore();
		String fields = viewSettings.getString(PreferenceConstants.GROUP_FIELD);
		String sortStr = viewSettings.getString(PreferenceConstants.SORT_ORDER);
		// The plain TABLE view is a flat list and never groups. The grouping /
		// sort prefs are shared with the tree / split / gallery views, so a
		// grouping chosen there bleeds in here and reorders the table (an
		// unsorted collection must stay in its on-disk / entry order). Ignore
		// the grouping for TABLE, and the multi-field sort CHAIN that grouping
		// leaves behind in SORT_ORDER even after the grouping itself is cleared
		// (e.g. "TYPEv/TOUGHNESS^/CMCv/SETv/COSTv/SET_COREv/SET_BLOCKv/"). A
		// genuine 1-2 column table sort by the user is still honoured.
		if (getPresentation() == Presentation.TABLE) {
			fields = "";
			if (countSortFields(sortStr) > 2)
				sortStr = "";
		}
		GroupOrder groupOrder = new GroupOrder(fields);
		filter.setGroupOrder(groupOrder);
		filter.setSortOrder(SortOrder.valueOf(sortStr));
		isGroupped = filter.isGroupped();
		if (DEBUG)
			trace("syncFilter: presentation=" + getPresentation() + " fixed=" + fixedPresentation
					+ " pref[GROUP_FIELD]='" + viewSettings.getString(PreferenceConstants.GROUP_FIELD)
					+ "' pref[SORT_ORDER]='" + viewSettings.getString(PreferenceConstants.SORT_ORDER)
					+ "' -> filter.groupField=" + safeStr(() -> filter.getGroupField()) + " filter.sort="
					+ safeStr(() -> filter.getSortOrder()) + " isGroupped=" + isGroupped);
	}

	private static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

	/** Number of real sort fields in a persisted SORT_ORDER string ("A/B/C/" -> 3). */
	private static int countSortFields(String sortStr) {
		if (isBlank(sortStr))
			return 0;
		int n = 0;
		for (String part : sortStr.split("/"))
			if (!part.trim().isEmpty())
				n++;
		return n;
	}

	protected void loadInitial() {
		IPreferenceStore ps = getColumnsPreferenceStore();
		if (viewer instanceof IMagicColumnViewer) {
			IMagicColumnViewer cviewer = (IMagicColumnViewer) viewer;
			// update manager columns
			String value = ps.getString(PreferenceConstants.LOCAL_COLUMNS);
			cviewer.updateColumns(value);
		}
		boolean qf = ps.getBoolean(PreferenceConstants.LOCAL_SHOW_QUICKFILTER);
		setQuickFilterVisible(qf);
	}

	public boolean isGroupped() {
		return isGroupped;
	}

	protected void updateStatus() {
		new Job("Status update") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				statusMessage = getStatusMessage();
				isFiltered = (getFiltered() != 0);
				WaitUtils.asyncExec(() -> {
					setStatus(statusMessage);
					setWarning(isFiltered);
				});
				return Status.OK_STATUS;
			}
		}.schedule();
	}

	protected int getFiltered() {
		IFilteredCardStore filteredStore = getFilteredStore();
		if (filteredStore != null) {
			ICardStore cardStore = filteredStore.getCardStore();
			int shownSize = filteredStore.getFlatSize();
			int storeSize = cardStore.size();
			return storeSize - shownSize;
		}
		return 0;
	}

	/**
	 * A single user action (move, remove, add) fires several store events, each
	 * of which - after its background load job finishes - asks for a viewer
	 * refresh. Those jobs can finish a few milliseconds apart, so a plain "one
	 * refresh already queued" guard is not enough. Debouncing with a short timer
	 * collapses the whole burst into a single {@link #refreshViewer()}, which is
	 * what stops the selection glitching through several intermediate states.
	 */
	private static final int REFRESH_DEBOUNCE_MS = 120;

	private volatile boolean refreshPending = false;

	private final Runnable debouncedRefresh = new Runnable() {
		@Override
		public void run() {
			// If a data-load job is still in flight the group tree it is
			// rebuilding is not stable yet; skip now and let that job re-arm the
			// timer from loadDataInJob() when it finishes.
			Job j = loadingJob;
			if (j != null && j.getState() != Job.NONE) {
				trace("debounced refresh deferred (load job " + j.getState() + ")");
				Display.getDefault().timerExec(REFRESH_DEBOUNCE_MS, this);
				return;
			}
			refreshPending = false;
			trace("debounced refreshViewer firing");
			refreshViewer();
		}
	};

	protected void coalesceRefreshViewer() {
		final Display display = Display.getDefault();
		display.asyncExec(() -> {
			if (viewer == null || viewer.getControl() == null || viewer.getControl().isDisposed())
				return;
			refreshPending = true;
			display.timerExec(-1, debouncedRefresh); // cancel a pending one
			display.timerExec(REFRESH_DEBOUNCE_MS, debouncedRefresh);
		});
	}

	/**
	 * Run a debounced refresh right now if one is queued. Called on mouse-down in
	 * the viewer so a click always lands on a settled list - otherwise a refresh
	 * firing mid-click rebuilds the rows and the selection ends up on a
	 * different card than the one the user aimed at.
	 */
	private void flushPendingRefresh() {
		if (!refreshPending)
			return;
		Display d = Display.getDefault();
		d.timerExec(-1, debouncedRefresh);
		debouncedRefresh.run();
	}

	/**
	 * Update view in UI thread after data load is finished.
	 */
	public void refreshViewer() {
		IFilteredCardStore filteredStore = getFilteredStore();
		Location location = filteredStore.getLocation();
		Object object = location == null ? getClass() : location;
		final String key = "updateViewer " + object;
		try {
			MagicLogger.traceStart(key);
			if (viewer == null || viewer.getControl() == null || viewer.getControl().isDisposed())
				return;

			ISelection previousSelection = getSelection();
			// The scroll position captured at the moment of the user action is the
			// reliable one during the event burst; once that has been consumed the
			// live position (set by the earlier pass) is fine to carry forward.
			int wantTop = pendingRevealTopIndex >= 0 ? pendingRevealTopIndex : savedTopIndex();
			trace("refreshViewer prev=" + shortSel(previousSelection) + " pendingReveal=" + pendingRevealKeys
					+ " storeFlatSize=" + filteredStore.getFlatSize() + " topSize=" + filteredStore.getSize()
					+ " groupField=" + safeStr(() -> getFilter().getGroupField())
					+ " nameGroup=" + safeStr(() -> getFilter().isNameGroupping())
					+ " sort=" + safeStr(() -> getFilter().getSortOrder())
					+ " wantTop=" + wantTop + " liveTop=" + savedTopIndex());

			boolean hadPendingReveal = !pendingRevealKeys.isEmpty();

			viewer.setInput(filteredStore);
			lastInput = filteredStore;
			// Snapshot the element list *now*, right after setInput populated the
			// viewer's content from it. A background update() can rebuild the
			// group tree with fresh instances a moment later; selecting against a
			// fresh instance that the widget's cache does not know about is what
			// makes "reveal the next card" silently fail for duplicate cards.
			refreshLeafSnapshot = flatLeaves();

			// setInput drops the scroll position - put it back before selecting so
			// a "select the next row" whose target is still on screen does not
			// scroll ("jump to top").
			restoreTopIndex(wantTop);
			restoreSelection(previousSelection);
			// When a reveal ran, applyPendingReveal() has already positioned the
			// list on its target; only re-assert the captured scroll otherwise.
			if (!hadPendingReveal)
				restoreTopIndex(wantTop);

			// Nudge the widget to repaint the visible rows after JFace's refresh.
			redrawViewer();

			updateStatus();
			syncSortColumnIndicator();
			trace("refreshViewer done, selection=" + shortSel(getSelection()) + " topIndex=" + savedTopIndex()
					+ " tableItemCount=" + tableItemCount());
		} catch (Exception e) {
			MagicLogger.log(e);
		} finally {
			refreshLeafSnapshot = null;
			MagicLogger.traceEnd(key);
		}
	}

	/** Leaf elements as they were when the current refreshViewer() started; falls
	 *  back to a live read outside a refresh. */
	private java.util.List<Object> currentLeaves() {
		return refreshLeafSnapshot != null ? refreshLeafSnapshot : flatLeaves();
	}

	private int tableItemCount() {
		Control c = viewer == null ? null : viewer.getControl();
		return (c instanceof Table && !c.isDisposed()) ? ((Table) c).getItemCount() : -1;
	}

	/** Ask the widget to repaint. JFace has already invalidated the item data. */
	private void redrawViewer() {
		Control c = viewer == null ? null : viewer.getControl();
		if (c != null && !c.isDisposed())
			c.redraw();
	}

	/** Current first visible row of the underlying table, or -1 if not a table. */
	private int savedTopIndex() {
		Control c = viewer == null ? null : viewer.getControl();
		if (c instanceof Table && !c.isDisposed())
			return ((Table) c).getTopIndex();
		return -1;
	}

	void restoreTopIndex(int top) {
		if (top <= 0)
			return;
		Control c = viewer == null ? null : viewer.getControl();
		if (c instanceof Table && !c.isDisposed()) {
			Table t = (Table) c;
			int max = Math.max(0, t.getItemCount() - 1);
			int was = t.getTopIndex();
			t.setTopIndex(Math.min(top, max));
			trace("restoreTopIndex " + was + " -> " + t.getTopIndex() + " (wanted " + top + ")");
		}
	}

	/**
	 * Decide what should be selected after {@link #refreshViewer()} updated the
	 * viewer.
	 * <ol>
	 * <li>An explicit "reveal this element" request (move / remove next card,
	 * search hit, single add) - resolved against the <em>current</em> store so a
	 * reload that replaced the model objects does not defeat it. The request
	 * survives refresh passes that run before the element is available, and
	 * expires after {@link #PENDING_REVEAL_TIMEOUT_MS} so it can never leak.</li>
	 * <li>Otherwise keep whatever was selected before the refresh, again resolved
	 * against the current store.</li>
	 * </ol>
	 * In both cases nothing is touched when the wanted rows are already selected.
	 */
	protected void restoreSelection(ISelection previousSelection) {
		if (applyPendingReveal())
			return;
		// A pending request that is not resolvable yet falls through here; keep
		// the previous selection until a later refresh pass can satisfy it.
		if (!pendingRevealKeys.isEmpty())
			return;

		if (previousSelection instanceof IStructuredSelection && !previousSelection.isEmpty()) {
			java.util.List<Object> keys = new ArrayList<>();
			for (Object o : ((IStructuredSelection) previousSelection).toList())
				keys.add(stableKey(o));
			// This is a plain re-apply of the same selection (nothing was moved
			// or removed here) - do it without revealing, so the list does not
			// scroll on the extra refresh passes an operation triggers.
			selectIfChanged(resolveInStore(keys), false);
		}
	}

	/**
	 * If a reveal request is pending and its targets can be found in the current
	 * filtered store, select and reveal them and return {@code true}. Returns
	 * {@code false} when nothing is pending, when the request has expired, or
	 * when the targets are not in the store yet (in which case the request is
	 * kept for a later refresh pass).
	 */
	protected boolean applyPendingReveal() {
		if (pendingRevealKeys.isEmpty())
			return false;
		if (isPendingRevealExpired()) {
			trace("pending reveal expired, dropping " + pendingRevealKeys);
			clearPendingReveal();
			return false;
		}
		java.util.List<Object> allLeaves = currentLeaves();
		java.util.List<Object> live = resolveInStore(pendingRevealKeys);
		if (live.isEmpty()) {
			trace("pending reveal " + pendingRevealKeys + " not in store yet");
			return false;
		}
		int idx = allLeaves.indexOf(live.get(0));
		trace("pending reveal resolved -> " + shortList(live) + " flatIndex=" + idx + " context:" + around(allLeaves, idx, 3));
		int top = pendingRevealTopIndex;
		clearPendingReveal();
		// Select without letting JFace reveal: its virtual-table scroll-to logic
		// fails to locate a row when the element instance differs from the cached
		// one (which happens for value-equal duplicate cards). Scroll by index
		// instead - that always works.
		selectIfChanged(live, false);
		scrollRowIntoView(idx, top);
		traceWidgetRowsAround(live.get(0));
		return true;
	}

	/**
	 * Dump the rows the viewer widget is <em>actually showing</em> around
	 * {@code target}, so a divergence between the model order ({@link #flatLeaves()})
	 * and what the user sees (esp. in the split viewer, whose right pane is fed
	 * from the selected tree node) shows up in the trace.
	 */
	private void traceWidgetRowsAround(Object target) {
		if (!DEBUG)
			return;
		try {
			org.eclipse.jface.viewers.Viewer v = viewer == null ? null : viewer.getViewer();
			if (!(v instanceof org.eclipse.jface.viewers.StructuredViewer)) {
				trace("widget rows: viewer is " + (v == null ? "null" : v.getClass().getSimpleName()) + " (no dump)");
				return;
			}
			org.eclipse.jface.viewers.StructuredViewer sv = (org.eclipse.jface.viewers.StructuredViewer) v;
			Object input = sv.getInput();
			Object cp = sv.getContentProvider();
			Object[] rows = null;
			if (cp instanceof org.eclipse.jface.viewers.IStructuredContentProvider)
				rows = ((org.eclipse.jface.viewers.IStructuredContentProvider) cp).getElements(input);
			if (rows == null) {
				trace("widget rows: content provider " + (cp == null ? "null" : cp.getClass().getSimpleName())
						+ " gave nothing (input=" + shortId(input) + ")");
				return;
			}
			org.eclipse.jface.viewers.ViewerComparator vc = sv.getComparator();
			if (vc != null) {
				Object[] sorted = rows.clone();
				vc.sort(sv, sorted);
				rows = sorted;
			}
			java.util.List<Object> rowList = java.util.Arrays.asList(rows);
			Object tkey = stableKey(target);
			int at = -1;
			for (int i = 0; i < rowList.size(); i++) {
				Object r = rowList.get(i);
				if (r == target || tkey != null && tkey.equals(stableKey(r))) {
					at = i;
					break;
				}
			}
			String ctx = at >= 0 ? around(rowList, at, 5)
					: around(rowList, Math.min(5, rowList.size() - 1), 5) + "  <target not among rows>";
			trace("widget rows: " + rows.length + " row(s), input=" + shortId(input) + ", target at " + at + ctx);
		} catch (Exception e) {
			trace("widget rows: dump failed " + e);
		}
	}

	private static String shortId(Object o) {
		if (o == null)
			return "null";
		String s = o.getClass().getSimpleName();
		if (o instanceof java.util.Collection)
			return s + "[" + ((java.util.Collection<?>) o).size() + "]";
		if (o instanceof ICardGroup)
			return s + "(" + ((ICardGroup) o).getName() + ")";
		return s;
	}

	/**
	 * Bring flat row {@code idx} into view. If it is already visible at the
	 * caller's preferred first-row {@code preferredTop}, keep that; otherwise
	 * scroll so the row sits a third of the way down the visible area.
	 */
	private void scrollRowIntoView(int idx, int preferredTop) {
		if (idx < 0)
			return;
		Control c = viewer == null ? null : viewer.getControl();
		if (!(c instanceof Table) || c.isDisposed())
			return;
		Table t = (Table) c;
		int count = t.getItemCount();
		if (count == 0)
			return;
		int itemH = Math.max(1, t.getItemHeight());
		int visible = Math.max(1, t.getClientArea().height / itemH);
		int newTop;
		if (preferredTop >= 0 && idx >= preferredTop && idx < preferredTop + visible) {
			newTop = preferredTop;
		} else {
			newTop = Math.max(0, idx - visible / 3);
		}
		newTop = Math.max(0, Math.min(newTop, count - 1));
		t.setTopIndex(newTop);
		trace("scrollRowIntoView idx=" + idx + " -> topIndex=" + t.getTopIndex());
	}

	private java.util.List<Object> resolveInStore(java.util.List<Object> keys) {
		java.util.List<Object> found = new ArrayList<>();
		if (keys.isEmpty())
			return found;
		java.util.List<Object> elements = currentLeaves();
		for (Object k : keys) {
			if (k == null)
				continue;
			for (Object el : elements) {
				if (el == k || k.equals(el) || k.equals(stableKey(el))) {
					found.add(el);
					break;
				}
			}
		}
		return found;
	}

	/** Select {@code targets} unless they are already exactly what is selected. */
	private void selectIfChanged(java.util.List<Object> targets, boolean reveal) {
		if (targets == null || targets.isEmpty())
			return;
		if (currentSelectionMatches(targets)) {
			trace("selection already " + shortList(targets) + ", nothing to do");
			return;
		}
		selectAndReveal(targets, reveal);
	}

	private boolean currentSelectionMatches(java.util.List<Object> targets) {
		ISelection cur = getSelection();
		if (!(cur instanceof IStructuredSelection))
			return false;
		java.util.List<?> curList = ((IStructuredSelection) cur).toList();
		if (curList.size() != targets.size())
			return false;
		for (int i = 0; i < targets.size(); i++) {
			Object a = curList.get(i);
			Object b = targets.get(i);
			if (a != b && (a == null || !stableKey(a).equals(stableKey(b))))
				return false;
		}
		return true;
	}

	/** Select the given live elements in the viewer, optionally scrolling the first into view. */
	private void selectAndReveal(java.util.List<Object> targets, boolean reveal) {
		if (targets == null || targets.isEmpty())
			return;
		trace("selectAndReveal " + shortList(targets) + (reveal ? " (reveal)" : ""));
		ISelection sel = new StructuredSelection(targets);
		// Split table AND split gallery: the right pane only shows the
		// tree-selected node's cards. If the target isn't in whatever the tree
		// currently has selected, the built-in fall-backs fail silently. Point
		// the tree at the target's enclosing group first (outermost real group -
		// selecting a NAME sub-group collapses the pane to 1-3 cards), then
		// select the card in the pane.
		if (viewer instanceof SplitViewer) {
			SplitViewer sv = (SplitViewer) viewer;
			revealInSplitPane(sv.getStructuredViewer(),
					sv.getViewer() instanceof org.eclipse.jface.viewers.StructuredViewer
							? (org.eclipse.jface.viewers.StructuredViewer) sv.getViewer()
							: null,
					sv.getSelectionProvider(), targets, sel);
			return;
		}
		if (viewer instanceof com.reflexit.magiccards.ui.gallery.SplitGalleryViewer) {
			com.reflexit.magiccards.ui.gallery.SplitGalleryViewer gv = (com.reflexit.magiccards.ui.gallery.SplitGalleryViewer) viewer;
			revealInSplitPane(gv.getStructuredViewer(), null, gv.getSelectionProvider(), targets, sel);
			return;
		}
		Viewer jface = viewer.getViewer();
		if (jface instanceof org.eclipse.jface.viewers.AbstractTreeViewer) {
			// In a tree, setSelection(sel, false) silently does nothing when the
			// target sits under a collapsed group node (e.g. the next card after
			// moving the last card of a Type group lives in another, collapsed
			// group). Expand its ancestors first, then let JFace reveal it.
			org.eclipse.jface.viewers.AbstractTreeViewer tv = (org.eclipse.jface.viewers.AbstractTreeViewer) jface;
			try {
				for (Object t : targets) {
					tv.expandToLevel(t, 0);
					tv.reveal(t);
				}
				tv.setSelection(sel, true);
				return;
			} catch (Exception e) {
				MagicUIActivator.log(e);
			}
		}
		if (jface != null) {
			try {
				jface.setSelection(sel, reveal);
				return;
			} catch (Exception e) {
				MagicUIActivator.log(e);
			}
		}
		getSelectionProvider().setSelection(sel);
	}

	/**
	 * Shared reveal for the split table / split gallery: aim the tree at the
	 * target's enclosing group (so the right pane shows it) then select the card
	 * in the pane.
	 *
	 * @param tree      the left tree viewer
	 * @param paneView  the right pane as a StructuredViewer, or null (gallery)
	 * @param paneSel   the right pane's selection provider (used when paneView is null)
	 */
	private void revealInSplitPane(org.eclipse.jface.viewers.StructuredViewer tree,
			org.eclipse.jface.viewers.StructuredViewer paneView, ISelectionProvider paneSel,
			java.util.List<Object> targets, ISelection sel) {
		try {
			Object first = targets.get(0);
			if (first instanceof ICard && !(first instanceof ICardGroup)
					&& !(paneView != null && rightPaneContains(paneView, first))) {
				if (paneView == null) {
					// Gallery. Seed the target id so it is baked into whatever
					// render happens next.
					String cid = first instanceof com.reflexit.magiccards.core.model.IMagicCard
							? String.valueOf(((com.reflexit.magiccards.core.model.IMagicCard) first).getCardId())
							: null;
					com.reflexit.magiccards.ui.gallery.BrowserGalleryViewer bgv = paneSel instanceof com.reflexit.magiccards.ui.gallery.BrowserGalleryViewer
							? (com.reflexit.magiccards.ui.gallery.BrowserGalleryViewer) paneSel
							: null;
					if (bgv != null && cid != null)
						bgv.setPendingSelectId(cid);
					// Keep the user's scope. If they have the whole collection
					// (root "All") selected in the tree, NEVER swap it for a
					// sub-group - just scroll within the full list. Drilling the
					// tree here is only for when they've already drilled into one
					// specific group. (Inferring this from gallery contents was
					// fragile: one missed lookup left "All" for good.)
					ICardGroup root = fstore != null ? fstore.getCardGroupRoot() : null;
					boolean atRoot = tree == null || root == null || tree.getSelection().isEmpty()
							|| isTreeNodeSelected(tree, root);
					boolean showing = bgv != null && cid != null && bgv.isShowingCardId(cid);
					if (!atRoot && !showing) {
						Object grp = displayGroupOf(findPathForCard((ICard) first));
						if (grp != null && tree != null) {
							tree.setSelection(org.eclipse.jface.viewers.StructuredSelection.EMPTY);
							tree.setSelection(new StructuredSelection(grp), true);
						}
					}
				} else {
					Object grp = displayGroupOf(findPathForCard((ICard) first));
					if (grp != null && tree != null && !isTreeNodeSelected(tree, grp))
						tree.setSelection(new StructuredSelection(grp), true);
				}
			}
			if (paneView != null)
				paneView.setSelection(sel, true);
			else if (paneSel != null)
				paneSel.setSelection(sel);
		} catch (Exception e) {
			MagicUIActivator.log(e);
			if (paneSel != null)
				paneSel.setSelection(sel);
		}
	}

	/** Innermost enclosing group of {@code path}, skipping NAME sub-groups. */
	private static Object displayGroupOf(TreePath path) {
		if (path == null || path.getSegmentCount() < 2)
			return null;
		int gi = path.getSegmentCount() - 2;
		while (gi > 0 && path.getSegment(gi) instanceof ICardGroup
				&& ((ICardGroup) path.getSegment(gi)).getFieldIndex() == MagicCardField.NAME)
			gi--;
		Object g = path.getSegment(gi);
		return g instanceof ICardGroup ? g : null;
	}

	private static boolean isTreeNodeSelected(org.eclipse.jface.viewers.StructuredViewer tree, Object grp) {
		ISelection s = tree.getSelection();
		if (!(s instanceof IStructuredSelection))
			return false;
		Object f = ((IStructuredSelection) s).getFirstElement();
		if (f instanceof TreePath)
			f = ((TreePath) f).getLastSegment();
		return grp.equals(f);
	}

	private boolean rightPaneContains(org.eclipse.jface.viewers.StructuredViewer rp, Object target) {
		try {
			if (rp == null)
				return false;
			Object cp = rp.getContentProvider();
			if (!(cp instanceof org.eclipse.jface.viewers.IStructuredContentProvider))
				return false;
			Object tkey = stableKey(target);
			for (Object e : ((org.eclipse.jface.viewers.IStructuredContentProvider) cp).getElements(rp.getInput()))
				if (e == target || tkey != null && tkey.equals(stableKey(e)))
					return true;
		} catch (Exception e) {
			// fall through
		}
		return false;
	}

	private static String shortSel(ISelection sel) {
		if (!(sel instanceof IStructuredSelection) || sel.isEmpty())
			return "<empty>";
		return shortList(((IStructuredSelection) sel).toList());
	}

	private static String shortList(java.util.List<?> list) {
		if (list.isEmpty())
			return "<empty>";
		String first = String.valueOf(list.get(0));
		return list.size() == 1 ? first : first + " (+" + (list.size() - 1) + ")";
	}

	protected void updateSortColumn(final int index) {
		if (viewer instanceof IMagicColumnViewer) {
			IMagicColumnViewer cviewer = (IMagicColumnViewer) viewer;
			GroupOrder groupOrder = null; // do not sort by group order
											// automatically
			if (index >= 0) {
				AbstractColumn man = (AbstractColumn) cviewer.getColumnViewer().getLabelProvider(index);
				ICardField sortField = man != null ? man.getSortField() : null;
				if (sortField == null && man instanceof GroupColumn)
					sortField = getFilter().getGroupField();
				if (sortField == null)
					return;
				final ICardField so = sortField;
				new SortAction(sortField.getLabel(), sortField, getFilter().getSortOrder(), groupOrder, (o) -> {
					cviewer.setSortColumn(index, o.isAccending(so) ? -1 : 1);
				}).force();
			} else {
				new UnsortAction(getFilter().getSortOrder(), groupOrder, (o) -> {
					cviewer.setSortColumn(-1, 0);
				}).force();
			}
		}
	}

	public void runPaste() {
		MagicCardTransfer mt = MagicCardTransfer.getInstance();
		Object contents = mt.fromClipboard();
		if (contents instanceof Collection) {
			DM.copyCards(DM.resolve((Collection) contents), getFilteredStore().getCardStore());
		} else {
			Control fc = getControl().getDisplay().getFocusControl();
			CopySupport.runPaste(fc);
		}
	}

	/** All card elements carried by an event - a move/remove fires ONE event with the whole list. */
	private static java.util.List<Object> eventElements(CardEvent event) {
		java.util.List<Object> out = new ArrayList<>();
		Object d = event.getData();
		if (d instanceof Iterable) {
			for (Object o : (Iterable<?>) d) {
				if (o instanceof Iterable)
					for (Object o2 : (Iterable<?>) o)
						out.add(o2);
				else if (o != null)
					out.add(o);
			}
		} else if (d != null) {
			out.add(d);
		}
		return out;
	}

	public void mcpEventHandler(final CardEvent event) {
		handleCardEvent(event, false);
	}

	private void handleCardEvent(final CardEvent event, boolean magicCardChannel) {
		int type = event.getType();
		java.util.List<Object> elems = eventElements(event);
		Object first = elems.isEmpty() ? event.getFirstDataElement() : elems.get(0);
		trace((magicCardChannel ? "mcEventHandler " : "mcpEventHandler ") + eventTypeName(type) + " " + first
				+ (elems.size() > 1 ? " (+" + (elems.size() - 1) + ")" : "") + " forThisStore=" + isForThisStore(first));

		boolean isMagicCardData = first instanceof MagicCard;
		if (isMagicCardData) {
			// DB card label changes (price, owned count...). Refresh via setInput
			// so ExpandContentProvider rebuilds its element list; do NOT re-group.
			if (type == CardEvent.UPDATE)
				coalesceRefreshViewer();
			return;
		}

		switch (type) {
		case CardEvent.UPDATE:
			coalesceRefreshViewer();
			break;
		case CardEvent.ADD: {
			setNextSelection(new StructuredSelection(elems.isEmpty() ? first : elems));
			boolean any = false;
			for (Object e : elems.isEmpty() ? java.util.Collections.singletonList(first) : elems)
				any |= isForThisStore(e);
			if (any) {
				forceStoreRegroup();
				loadData(null);
			}
			break;
		}
		case CardEvent.REMOVE: {
			boolean anyForThis = false;
			boolean allSurgical = true;
			for (Object e : elems.isEmpty() ? java.util.Collections.singletonList(first) : elems) {
				if (!isForThisStore(e))
					continue;
				anyForThis = true;
				// Drop each card from the group tree in place - a full re-group
				// would collapse / re-anchor name sub-groups and shuffle siblings.
				if (!removeCardFromFilteredTree(e))
					allSurgical = false;
			}
			if (anyForThis) {
				if (allSurgical) {
					coalesceRefreshViewer();
				} else {
					forceStoreRegroup();
					loadData(null);
				}
			}
			break;
		}
		default:
			break;
		}
	}

	/**
	 * True when {@code data} concerns the collection this control is showing -
	 * either its location matches, or the card is currently in the displayed
	 * tree. A propagated event for a card that moved between two <em>other</em>
	 * collections returns false, so this view is left untouched.
	 */
	private boolean isForThisStore(Object data) {
		if (!(data instanceof MagicCardPhysical))
			return true; // db card / list - be permissive
		IFilteredCardStore fs = getFilteredStore();
		if (fs == null)
			return false;
		Location dl = ((MagicCardPhysical) data).getLocation();
		if (dl != null && dl.equals(fs.getLocation()))
			return true;
		for (Object leaf : flatLeaves())
			if (leaf == data)
				return true;
		return false;
	}

	/**
	 * Force the filtered store to rebuild its groups on the next {@code update()}
	 * instead of taking its cheaper "re-filter in place" path.
	 */
	private void forceStoreRegroup() {
		IFilteredCardStore fs = getFilteredStore();
		if (fs instanceof com.reflexit.magiccards.core.model.storage.AbstractFilteredCardStore)
			((com.reflexit.magiccards.core.model.storage.AbstractFilteredCardStore<?>) fs).setRefreshRequired(true);
	}

	/**
	 * Remove a single card from the filtered store's group tree by identity,
	 * without a full re-group. Recaches the affected groups up to the root so
	 * {@code getFlatSize()} / the content provider see the change. Returns
	 * {@code false} if the card was not found (caller should fall back to a full
	 * reload).
	 */
	private boolean removeCardFromFilteredTree(Object card) {
		IFilteredCardStore fs = getFilteredStore();
		if (card == null || fs == null)
			return false;
		Object root = fs.getCardGroupRoot();
		if (!(root instanceof CardGroup))
			return false;
		boolean removed = removeCardFromGroup((CardGroup) root, card);
		trace("removeCardFromFilteredTree " + card + " -> " + removed);
		if (removed && fs instanceof com.reflexit.magiccards.core.model.storage.AbstractFilteredCardStore)
			// The tree already reflects the removal; keep the next update() on the
			// cheap "re-filter" path so it does not re-group and reshuffle groups.
			((com.reflexit.magiccards.core.model.storage.AbstractFilteredCardStore<?>) fs).setRefreshRequired(false);
		return removed;
	}

	private boolean removeCardFromGroup(CardGroup group, Object card) {
		for (Object child : group.getChildren()) { // getChildren() returns a cached snapshot
			if (child == card) {
				group.remove((ICard) card); // identity removal + recache of this group
				recacheAncestors(group);
				pruneEmpty(group);
				return true;
			}
			if (child instanceof CardGroup && removeCardFromGroup((CardGroup) child, card)) {
				group.recache();
				recacheAncestors(group);
				return true;
			}
		}
		return false;
	}

	/** If {@code group} is now empty, detach it from its parent (recursively). */
	private void pruneEmpty(CardGroup group) {
		CardGroup parent = group.getParent() instanceof CardGroup ? (CardGroup) group.getParent() : null;
		if (parent != null && group.size() == 0) {
			parent.remove(group);
			recacheAncestors(parent);
			pruneEmpty(parent);
		}
	}

	private void recacheAncestors(CardGroup group) {
		for (CardGroup p = group; p != null;) {
			p.recache();
			p = p.getParent() instanceof CardGroup ? (CardGroup) p.getParent() : null;
		}
	}

	public void mcEventHandler(final CardEvent event) {
		Object data = event.getFirstDataElement();
		int type = event.getType();
		trace("mcEventHandler " + eventTypeName(type) + " " + data);
		if (data instanceof MagicCard) {
			switch (type) {
			case CardEvent.UPDATE:
				coalesceRefreshViewer();
				break;
			case CardEvent.ADD:
				setNextSelection(new StructuredSelection(data));
				if (isForThisStore(data)) {
					forceStoreRegroup();
					loadData(null);
				}
				break;

			case CardEvent.REMOVE:
				if (isForThisStore(data)) {
					if (removeCardFromFilteredTree(data)) {
						coalesceRefreshViewer();
					} else {
						forceStoreRegroup();
						loadData(null);
					}
				}
				break;
			default:
				break;
			}
		}
	}

	@Override
	public void saveState(IMemento memento) {
		saveColumnLayout();
	}

	public void saveColumnLayout() {
		if (!(viewer instanceof IMagicColumnViewer))
			return;
		IMagicColumnViewer cviewer = (IMagicColumnViewer) viewer;
		final String value = cviewer.getColumnLayoutProperty();
		if (value == null || value.isEmpty())
			return;
		IPersistentPreferenceStore store = getColumnsPreferenceStore();
		if (value.equals(store.getString(PreferenceConstants.LOCAL_COLUMNS)))
			return;
		synchronized (this) {
			store.removePropertyChangeListener(this.preferenceListener);
			// System.err.println("saving layout " + this.getClass() + " " +
			// getName() + " " + value);
			try {
				store.setValue(PreferenceConstants.LOCAL_COLUMNS, value);
			} finally {
				store.addPropertyChangeListener(this.preferenceListener);
			}
			try {
				store.save();
			} catch (IOException e) {
				// ignore
			}
		}
	}

	private Object jobFamility = new Object();
	private Job loadingJob;

	public void loadData(final Runnable postLoad) {
		synchronized (jobFamility) {
			if (loadingJob != null) {
				loadingJob.cancel();
			}
			loadingJob = WaitUtils.scheduleJob("Loading cards for " + AbstractMagicCardsListControl.this,
					(monitor) -> loadDataInJob(postLoad, monitor));
		}
	}

	private void checkInit() {
		try {
			WaitUtils.waitForDb();
			WaitUtils.waitForLibrary();
			// DataManager.syncInitDb() reconciles the collection cards with the
			// DB *after* the DB/library report initialized. If we group before
			// that, every card is CMC 0 / type null and the whole view collapses
			// into one "Cost 0" / "Unknown" group. Wait for a sample card to
			// actually carry its DB data. Cheap after the first load (returns at
			// once once cards are resolved).
			waitForCardsResolved();
		} catch (MagicException e) {
			MagicUIActivator.log(e);
		}
	}

	private void waitForCardsResolved() {
		IFilteredCardStore fs = getFilteredStore();
		if (fs == null)
			return;
		MagicCardFilter f = fs.getFilter();
		if (f == null || f.getGroupField() == null)
			return; // ungrouped: grouping can't be wrong
		ICardStore cs = fs.getCardStore();
		if (cs == null)
			return;
		int prevUnresolved = Integer.MAX_VALUE;
		int stableCount = 0;
		for (int i = 0; i < 60; i++) {
			// Reconcile links the cards one by one; scan the WHOLE store, not
			// just the first card - sorting a partly-reconciled list (some cards
			// null type/rarity, some not) is what makes the comparator flip
			// mid-sort -> "Comparison method violates its general contract".
			int total = 0;
			int unresolved = 0;
			String sampleName = null;
			try {
				java.util.Iterator<?> it = cs.iterator();
				while (it.hasNext()) {
					Object o = it.next();
					if (!(o instanceof MagicCardPhysical))
						continue;
					total++;
					String type = ((MagicCardPhysical) o).getType();
					if (type == null || type.isEmpty()) {
						unresolved++;
						if (sampleName == null)
							sampleName = ((MagicCardPhysical) o).getName();
					}
				}
			} catch (Exception e) {
				return;
			}
			if (total == 0 || unresolved == 0)
				return; // empty, or everything resolved
			// Some tokens legitimately have no type and never resolve. Stop once
			// the unresolved count stops dropping (reconcile has finished; the
			// rest just don't have a type).
			if (unresolved >= prevUnresolved) {
				if (++stableCount >= 3) {
					trace("waitForCardsResolved: " + unresolved + "/" + total
							+ " still typeless but count is stable - proceeding");
					return;
				}
			} else {
				stableCount = 0;
			}
			prevUnresolved = unresolved;
			if (i == 0 || i % 10 == 0)
				trace("waitForCardsResolved: " + unresolved + "/" + total + " unresolved, e.g. " + sampleName + " (poll "
						+ i + ")");
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
		trace("waitForCardsResolved: gave up waiting for card resolution");
	}

	protected void populateStore(IProgressMonitor monitor) {
		getFilteredStore();
	}

	protected abstract IFilteredCardStore<ICard> doGetFilteredStore();

	public IStatus loadDataInJob(final Runnable postLoad, IProgressMonitor monitor) {
		final Display display = Display.getDefault();
		try {
			monitor.beginTask("Loading for " + getName(), 100);
			checkInit();
			if (monitor.isCanceled())
				return Status.CANCEL_STATUS;
			synchronized (AbstractMagicCardsListControl.this) {
				if (monitor.isCanceled())
					return Status.CANCEL_STATUS;
				populateStore(monitor);
				if (monitor.isCanceled())
					return Status.CANCEL_STATUS;
				if (getFilteredStore() == null)
					return Status.OK_STATUS;
				monitor.worked(10);
				Location location = getFilteredStore().getLocation();
				monitor.setTaskName("Loading cards for " + location);
				getFilteredStore().update();
			}
			// refresh ui
			if (postLoad != null)
				display.asyncExec(postLoad);
			else
				coalesceRefreshViewer();
		} catch (final Exception e) {
			// display.asyncExec(() ->
			// MessageDialog.openError(display.getActiveShell(), "Error",
			// e.getMessage()));
			MagicUIActivator.log(e);
			return Status.CANCEL_STATUS;
		} finally {
			monitor.done();
		}
		return Status.OK_STATUS;
	}

	protected int getCount(Object element) {
		if (element == null)
			return 0;
		int count = ((element instanceof ICardCountable) ? ((ICardCountable) element).getCount() : 1);
		return count;
	}

	@Override
	public void createPageContents(Composite parent) {
		createMainControl(parent);
		loadInitial(); // XXX reloadData()?
	}

	@Override
	public void activate() {
		contributeToActionBars();
		addListeners();
		// getViewSite().setSelectionProvider(getSelectionProvider());// XXX
		refresh();
	}

	@Override
	public void deactivate() {
		removeListeners();
		super.deactivate();
	}

	public Shell getShell() {
		return getControl().getShell();
	}

	protected Object getFirstVisible() {
		if (fstore.getSize() == 0)
			return null;
		Object element = fstore.getElement(0);

		if (element instanceof CardGroup) {
			CardGroup a = (CardGroup) element;
			if (a.size() > 0) {
				return a.getChildAtIndex(0);

			}
		}
		return element;
	}

	protected void selectFirstVisible() {
		// select first visible element

		Object element = getFirstVisible();
		if (element == null)
			return;

		getSelectionProvider().setSelection(new StructuredSelection(element));
	}

	private void updateGroupingVisibility() {
		if (viewer == null)
			return;
		Control c = viewer.getControl();
		if (c == null || c.isDisposed())
			return;
		// refresh() can be driven from a background job (DeckView.loadInitialInBackground);
		// SashForm manipulation must run on the UI thread.
		if (c.getDisplay().getThread() != Thread.currentThread()) {
			c.getDisplay().asyncExec(this::updateGroupingVisibility);
			return;
		}
		if (viewer instanceof com.reflexit.magiccards.ui.views.SplitViewer) {
			com.reflexit.magiccards.ui.views.SplitViewer sv = (com.reflexit.magiccards.ui.views.SplitViewer) viewer;

			if (!isGroupped) {
				sv.getSashForm().setMaximizedControl(sv.getRightControl());
			} else {
				sv.getSashForm().setMaximizedControl(null);
				sv.getSashForm().setWeights(new int[] { 22, 78 });
			}
		}

		if (viewer instanceof com.reflexit.magiccards.ui.gallery.SplitGalleryViewer) {
			com.reflexit.magiccards.ui.gallery.SplitGalleryViewer gv = (com.reflexit.magiccards.ui.gallery.SplitGalleryViewer) viewer;

			if (!isGroupped) {
				gv.getSashForm().setMaximizedControl(gv.getRightControl());
			} else {
				gv.getSashForm().setMaximizedControl(null);
				gv.getSashForm().setWeights(new int[] { 22, 78 });
			}
		}
	}

	/**
	 * Reconstruct a TreePath for a given card by walking the grouped store.
	 * Works for Split Table View where selection is an ICard, not a TreePath.
	 */
	private TreePath findPathForCard(ICard target) {
		if (target == null || fstore == null)
			return null;

		ICardGroup root = fstore.getCardGroupRoot();
		if (root == null)
			return null;

		return findPathRecursive(root, target, TreePath.EMPTY);
	}

	private TreePath findPathRecursive(ICardGroup group, ICard target, TreePath base) {
		Object[] children = group.getChildren();

		// Extract target ID using MagicCardField
		String targetId = null;
		Object tid = target.get(MagicCardField.ID);
		if (tid instanceof String) {
			targetId = (String) tid;
		}

		for (Object child : children) {

			// Subgroup
			if (child instanceof ICardGroup) {
				ICardGroup g = (ICardGroup) child;
				TreePath p = findPathRecursive(g, target, base.createChildPath(g));
				if (p != null)
					return p;
				continue;
			}

			// Card
			if (child instanceof ICard) {
				ICard c = (ICard) child;

				// Match by object identity
				if (c == target)
					return base.createChildPath(c);

				// Match by CARD_ID
				if (targetId != null) {
					Object cid = c.get(MagicCardField.ID);
					if (cid instanceof String && targetId.equals(cid))
						return base.createChildPath(c);
				}
			}
		}

		return null;
	}

	/**
	 * Given the elements that are about to be removed from this list, compute the
	 * element that should be selected in their place (the first row after the
	 * removed block, or the row before it when the block reaches the end) and
	 * register it as a sticky reveal request. Must be called <em>before</em> the
	 * removal so the neighbours can still be located.
	 */
	public void selectNeighbourAfterRemoval(java.util.Collection<?> removedElements) {
		if (removedElements == null || removedElements.isEmpty())
			return;
		java.util.Set<Object> removedKeys = new java.util.HashSet<>();
		addRemovedKeys(removedElements, removedKeys);

		// Tree / split view: keep the selection inside the removed card's own
		// group for as long as that group survives - next sibling, or the
		// previous one when the removed card was the group's last. Only cross a
		// group boundary (falling through to the flat logic below) when the
		// group is emptied.
		if (getPresentation() != Presentation.TABLE && selectNeighbourInGroup(removedElements, removedKeys))
			return;

		java.util.List<Object> leaves = flatLeaves();
		trace("selectNeighbourAfterRemoval: removing " + removedKeys.size() + " of " + leaves.size() + " row(s)");
		int firstRemoved = -1;
		int lastRemoved = -1;
		for (int i = 0; i < leaves.size(); i++) {
			if (removedKeys.contains(stableKey(leaves.get(i)))) {
				if (firstRemoved < 0)
					firstRemoved = i;
				lastRemoved = i;
			}
		}
		if (firstRemoved < 0) {
			trace("selectNeighbourAfterRemoval: removed rows not found in list");
			return;
		}
		Object next = null;
		int nextIdx = -1;
		for (int i = lastRemoved + 1; i < leaves.size(); i++) {
			if (!removedKeys.contains(stableKey(leaves.get(i)))) {
				next = leaves.get(i);
				nextIdx = i;
				break;
			}
		}
		if (next == null) {
			for (int i = firstRemoved - 1; i >= 0; i--) {
				if (!removedKeys.contains(stableKey(leaves.get(i)))) {
					next = leaves.get(i);
					nextIdx = i;
					break;
				}
			}
		}
		trace("selectNeighbourAfterRemoval: removedIdx=" + firstRemoved + ".." + lastRemoved + " nextIdx=" + nextIdx
				+ " next=" + next + " context BEFORE move:" + around(leaves, firstRemoved, 4));
		if (next != null)
			revealElementAfterRefresh(next);
	}

	/**
	 * Spec (tree / split): if the removed card's enclosing group still has a
	 * card left, select a sibling within that group (the next one, or the
	 * previous one when the removed card was the last) and return true. Return
	 * false when the group is emptied or the layout isn't grouped - the caller
	 * then falls back to the flat next/previous logic.
	 */
	private boolean selectNeighbourInGroup(java.util.Collection<?> removedElements, java.util.Set<Object> removedKeys) {
		Object firstRemoved = null;
		for (Object o : removedElements) {
			if (o instanceof ICardGroup) {
				java.util.List<Object> ls = new ArrayList<>();
				collectElements((ICardGroup) o, ls, false);
				if (!ls.isEmpty()) {
					firstRemoved = ls.get(0);
					break;
				}
			} else if (o instanceof ICard) {
				firstRemoved = o;
				break;
			}
		}
		if (!(firstRemoved instanceof ICard) || firstRemoved instanceof ICardGroup)
			return false;
		TreePath p = findPathForCard((ICard) firstRemoved);
		if (p == null || p.getSegmentCount() < 2 || !(p.getSegment(p.getSegmentCount() - 2) instanceof ICardGroup))
			return false;
		ICardGroup grp = (ICardGroup) p.getSegment(p.getSegmentCount() - 2);
		// All leaf cards under this group, in display order - RECURSE, so a
		// survivor still sitting inside a 1-member NAME sub-group (surgical
		// removal never collapses those) is not missed and the group wrongly
		// treated as emptied.
		java.util.List<Object> sibs = new ArrayList<>();
		collectElements(grp, sibs, false);
		sibs = applyColumnComparator(sibs); // match the order shown in the pane
		int firstRemSib = -1, lastRemSib = -1;
		for (int i = 0; i < sibs.size(); i++) {
			if (removedKeys.contains(stableKey(sibs.get(i)))) {
				if (firstRemSib < 0)
					firstRemSib = i;
				lastRemSib = i;
			}
		}
		if (firstRemSib < 0)
			return false; // removed card not under this group
		Object pick = null;
		for (int i = lastRemSib + 1; i < sibs.size() && pick == null; i++)
			if (!removedKeys.contains(stableKey(sibs.get(i))))
				pick = sibs.get(i);
		for (int i = firstRemSib - 1; i >= 0 && pick == null; i--)
			if (!removedKeys.contains(stableKey(sibs.get(i))))
				pick = sibs.get(i);
		if (pick != null) {
			trace("selectNeighbourInGroup: staying in group '" + grp.getName() + "' -> " + pick);
			revealElementAfterRefresh(pick);
			return true;
		}
		// group emptied -> first displayed card of the NEXT sibling group in tree
		// order (or last card of the previous group).
		Object cross = crossGroupCard(grp, removedKeys, true);
		boolean fwd = cross != null;
		if (cross == null)
			cross = crossGroupCard(grp, removedKeys, false);
		if (cross == null)
			return false;
		trace("selectNeighbourInGroup: group '" + grp.getName() + "' emptied -> " + (fwd ? "next" : "prev")
				+ " group's card " + cross);
		revealElementAfterRefresh(cross);
		return true;
	}

	/**
	 * Walk up the tree from {@code grp}; among its siblings (then its parent's
	 * siblings, ...) find the first (forward) / last (backward) group that still
	 * has a surviving card and return that card - in the order the right pane
	 * would show it.
	 */
	private Object crossGroupCard(ICardGroup grp, java.util.Set<Object> removedKeys, boolean forward) {
		ICardGroup g = grp;
		while (g != null && g.getParent() instanceof ICardGroup) {
			ICardGroup parent = (ICardGroup) g.getParent();
			Object[] kids = parent.getChildren();
			int idx = -1;
			for (int i = 0; i < kids.length; i++)
				if (kids[i] == g) {
					idx = i;
					break;
				}
			if (idx < 0)
				return null;
			for (int i = forward ? idx + 1 : idx - 1; i >= 0 && i < kids.length; i += forward ? 1 : -1) {
				Object sib = kids[i];
				java.util.List<Object> leaves = new ArrayList<>();
				if (sib instanceof ICardGroup)
					collectElements((ICardGroup) sib, leaves, false);
				else if (sib instanceof ICard)
					leaves.add(sib);
				leaves = applyColumnComparator(leaves);
				if (!forward)
					java.util.Collections.reverse(leaves);
				for (Object leaf : leaves)
					if (!removedKeys.contains(stableKey(leaf)))
						return leaf;
			}
			g = parent; // nothing among these siblings - try the parent's siblings
		}
		return null;
	}

	/** Re-sort {@code in} with the active viewer column comparator, if any (matches the pane). */
	private java.util.List<Object> applyColumnComparator(java.util.List<Object> in) {
		try {
			org.eclipse.jface.viewers.Viewer v = viewer == null ? null : viewer.getViewer();
			if (v instanceof org.eclipse.jface.viewers.StructuredViewer) {
				org.eclipse.jface.viewers.ViewerComparator vc = ((org.eclipse.jface.viewers.StructuredViewer) v)
						.getComparator();
				if (vc != null && in.size() > 1) {
					Object[] arr = in.toArray();
					vc.sort((org.eclipse.jface.viewers.StructuredViewer) v, arr);
					return new ArrayList<>(java.util.Arrays.asList(arr));
				}
			}
		} catch (Exception e) {
			// keep input order
		}
		return in;
	}

	/** Collect stable keys for {@code elements}, expanding any groups to their leaves. */
	private void addRemovedKeys(java.util.Collection<?> elements, java.util.Set<Object> out) {
		for (Object o : elements) {
			if (o instanceof ICardGroup) {
				java.util.List<Object> leaves = new ArrayList<>();
				collectElements((ICardGroup) o, leaves, false);
				for (Object leaf : leaves) {
					Object k = stableKey(leaf);
					if (k != null)
						out.add(k);
				}
			} else {
				Object k = stableKey(o);
				if (k != null)
					out.add(k);
			}
		}
	}

}
