/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil
 * All rights reserved.
 *
 * This file is NOT open-source.
 * Permission is granted to use this file ONLY as part of the ManaDesk application.
 * Modification, redistribution, or reuse of this file or its contents is prohibited.
 * You may NOT replace affiliate identifiers, ad URLs, or donation links.
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk: Buyer view - what to buy for a batch
 *                  of decks / collections, with estimated prices and per-store
 *                  copy/paste lists.
 *     Rémi Dutil (2026) - TCGplayer lists use product ids (exact product,
 *                  no name guessing) and "Open in TCGplayer" opens Mass
 *                  Entry already filled through its c= link.
 *     Rémi Dutil (2026) - links opened through BrowserLauncher (the query
 *                  string was cut at the first '&' on Windows).
 *     Rémi Dutil (2026) - TCGplayer availability (Note column, "leave out
 *                  cards with no TCGplayer price", header count) only when
 *                  TCGplayer is the selected store.
 *     Rémi Dutil (2026) - with Exact Match, stores that cannot be asked for
 *                  a specific printing (Card Kingdom) are not offered.
 *     Rémi Dutil (2026) - Face to Face Games deck builder (names only).
 *     Rémi Dutil (2026) - The Mythic Store and Imaginaire store pages.
 *     Rémi Dutil (2026) - "Only the selected cards": the store lists can be
 *                  built from the grid's selected rows only.
 *     Rémi Dutil (2026) - prices from the selected store's own source
 *                  (Cardmarket EUR for Cardmarket, TCGplayer USD for
 *                  TCGplayer, TCGplayer as an estimate otherwise); the
 *                  availability check works for TCGplayer and Cardmarket.
 *     Rémi Dutil (2026) - the store list opens on the store matching the
 *                  "Card prices from" preference (Cardmarket / TCGplayer).
 *     Rémi Dutil (2026) - prices shown in the store's own currency (TCGplayer
 *                  USD, Cardmarket EUR), not the app-wide display currency.
 *     Rémi Dutil (2026) - with Exact Match, only stores taking the specific
 *                  printing are offered (TCGplayer + the Plain list).
 *     Rémi Dutil (2026) - foil / non-foil split is the standard for every store;
 *                  "Combine foil and non-foil in one list" checkbox.
 *     Rémi Dutil (2026) - finish like the set: any finish without Exact Match;
 *                  with it, rows per finish (DeckNeeds byFinish, stock counted
 *                  per finish) and two lists unless combined. "Replace
 *                  Proxies" on by default.
 *     Rémi Dutil (2026) - Copy tooltip = the first 10 lines (preview()).
 *     Rémi Dutil (2026) - the copy panel scrolls (ScrolledComposite) - in a
 *                  short bottom area the Copy / Open buttons were squeezed
 *                  out of sight.
 *******************************************************************************/
package com.manadesk.monetization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.ImageDescriptor;
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
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.plugin.AbstractUIPlugin;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.exports.BuyListFormat;
import com.reflexit.magiccards.core.exports.BuyListLine;
import com.reflexit.magiccards.core.model.CardFinish;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.nav.CardCollection;
import com.reflexit.magiccards.core.model.nav.CollectionsContainer;
import com.reflexit.magiccards.core.model.nav.DeckNeeds;
import com.reflexit.magiccards.core.model.nav.DeckNeeds.ListInfo;
import com.reflexit.magiccards.core.model.nav.DeckNeeds.ListRef;
import com.reflexit.magiccards.core.model.nav.DeckNeeds.Row;
import com.reflexit.magiccards.core.model.nav.DeckNeeds.Split;
import com.reflexit.magiccards.core.model.storage.ICardStore;
import com.reflexit.magiccards.core.model.xml.DbPricesMultiFileStore;
import com.reflexit.magiccards.core.seller.IPriceProvider;
import com.reflexit.magiccards.core.seller.PriceSources;
import com.reflexit.magiccards.ui.views.nav.CardsNavigatorContentProvider;
import com.reflexit.magiccards.ui.views.nav.CardsNavigatorLabelProvider;
import com.reflexit.magiccards.ui.views.nav.DeckFamilyViewerComparator;

/**
 * What to buy for a batch of decks and/or collections - the Proxier's layout
 * and calculation ({@link DeckNeeds}), turned towards buying:
 * <ul>
 * <li><b>Left</b>: decks and collections, checkbox to include (a folder
 * cascades); boxed decks are not shown.</li>
 * <li><b>Top-right</b>: one row per card (or exact printing): Owned / Boxed /
 * Available / Needed / <b>To Buy</b>, estimated price (TCGplayer market price
 * from the card database) and a warning when that printing + finish has no
 * TCGplayer price (probably not available there).</li>
 * <li><b>Bottom-right</b>: the selected row's per-list breakdown, and the
 * <b>copy panel</b>: the list as each store's bulk-entry box wants it
 * ({@link BuyListFormat}), split as the store needs it (TCGplayer: one list
 * per finish), with Copy and Open store page.</li>
 * </ul>
 * <b>To Buy</b> = slots with no copy at all; with <b>Replace Proxies</b>,
 * proxies count as missing too (only genuine copies cover a slot).
 * <b>Create Collection</b> makes a new "ToBuy" collection of virtual copies -
 * nothing existing is ever marked.
 */
public class BuyerView extends ViewPart {
	public static final String ID = "com.manadesk.monetization.BuyerView";

	/** Where to paste each store's list (bulk-entry pages). */
	private static String storePage(BuyListFormat f) {
		switch (f) {
		case TCGPLAYER:
			return "https://www.tcgplayer.com/massentry";
		case CARDMARKET:
			return "https://www.cardmarket.com/en/Magic/Wants";
		case CARD_KINGDOM:
			return "https://www.cardkingdom.com/builder";
		case FACE_TO_FACE:
			return "https://facetofacegames.com/pages/deck-builder";
		case MYTHIC_STORE:
			return "https://themythicstore.com/pages/multi-card-search-page";
		case IMAGINAIRE:
			return "https://imaginaire.com/en/magic/deck-builder.html";
		default:
			return null;
		}
	}

	private CheckboxTreeViewer sourceTree;
	private TableViewer gridViewer;
	private Table gridTable;
	private Label header;
	private TableViewer listViewer;
	private Combo storeCombo;
	private Button skipUnpriced;
	private Button onlySelected;
	private Button combineFinishes;
	private Label priceNote;
	private Composite groupsArea;
	private ScrolledComposite copyScroll;

	private boolean exactMatch;
	/** On by default: a proxy is a card still to buy. */
	private boolean replaceProxies = true;
	private boolean onlyToBuy;
	/** Every eligible source (unboxed decks + collections), in navigator order. */
	private final List<CardCollection> sources = new ArrayList<>();
	private final List<Row> currentRows = new ArrayList<>();
	private final CopyOnWriteArrayList<ISelectionChangedListener> cardSelectionListeners = new CopyOnWriteArrayList<>();

	/** Publishes the selected rows as their card printing, for the Card Info view. */
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
			// read-mostly view
		}
	};

	@Override
	public void createPartControl(Composite parent) {
		SashForm sash = new SashForm(parent, SWT.HORIZONTAL);
		createSourceTree(sash);
		createRightPane(sash);
		sash.setWeights(new int[] { 25, 75 });
		contributeToolbar();
		loadSources();
		rebuild();
	}

	@Override
	public void setFocus() {
		gridTable.setFocus();
	}

	private Shell getShell() {
		return getViewSite().getShell();
	}

	private static ImageDescriptor uiIcon(String path) {
		return AbstractUIPlugin.imageDescriptorFromPlugin("com.reflexit.magiccards.ui", path);
	}

	// ------------------------------------------------------------- toolbar

	private void contributeToolbar() {
		IToolBarManager mgr = getViewSite().getActionBars().getToolBarManager();
		Action exact = new Action("Exact Match", IAction.AS_CHECK_BOX) {
			@Override
			public void run() {
				exactMatch = isChecked();
				refreshStoreChoices(); // names-only stores can't take an exact printing
				combineFinishes.setEnabled(exactMatch); // the finish only counts with Exact Match
				updateStoreOptions();
				rebuild();
			}
		};
		exact.setImageDescriptor(uiIcon("icons/obj16/check16.png"));
		exact.setToolTipText("Buy the exact printing (set + collector number + finish) instead of any printing");
		mgr.add(exact);

		Action replace = new Action("Replace Proxies", IAction.AS_CHECK_BOX) {
			@Override
			public void run() {
				replaceProxies = isChecked();
				rebuild();
			}
		};
		replace.setChecked(replaceProxies);
		replace.setImageDescriptor(uiIcon("icons/proxier24.png"));
		replace.setToolTipText("Count proxies as missing: buy genuine copies to replace them too");
		mgr.add(replace);

		Action only = new Action("Only To Buy", IAction.AS_CHECK_BOX) {
			@Override
			public void run() {
				onlyToBuy = isChecked();
				gridViewer.refresh();
			}
		};
		only.setImageDescriptor(uiIcon("icons/clcl16/filter_ps.png"));
		only.setToolTipText("Show only rows where \"To Buy\" is not 0");
		mgr.add(only);

		Action create = new Action("Create Collection") {
			@Override
			public void run() {
				createToBuyCollection();
			}
		};
		create.setImageDescriptor(uiIcon("icons/obj16/lib16.png"));
		create.setToolTipText("Create a new collection with the cards to buy (virtual copies - nothing existing is changed)");
		mgr.add(create);

		Action refresh = new Action("Refresh") {
			@Override
			public void run() {
				refreshSources();
			}
		};
		refresh.setImageDescriptor(uiIcon("icons/clcl16/refresh.gif"));
		refresh.setToolTipText("Reload the decks and collections");
		mgr.add(refresh);
		getViewSite().getActionBars().updateActionBars();
	}

	// ----------------------------------------------------- left: sources

	private void createSourceTree(Composite parent) {
		Tree tree = new Tree(parent, SWT.CHECK | SWT.BORDER);
		sourceTree = new CheckboxTreeViewer(tree);
		sourceTree.setContentProvider(new CardsNavigatorContentProvider());
		sourceTree.setLabelProvider(new CardsNavigatorLabelProvider());
		sourceTree.setComparator(new DeckFamilyViewerComparator());
		sourceTree.addFilter(CardsNavigatorContentProvider.getFilter(CardsNavigatorContentProvider.FILTER_SIDEBOARDS));
		sourceTree.addFilter(new ViewerFilter() {
			@Override
			public boolean select(Viewer viewer, Object parentElement, Object element) {
				return !(element instanceof CardCollection && ((CardCollection) element).isBoxed());
			}
		});
		sourceTree.addCheckStateListener(e -> {
			if (!(e.getElement() instanceof CardCollection))
				sourceTree.setSubtreeChecked(e.getElement(), e.getChecked()); // a folder: cascade
			rebuild();
		});
	}

	private void loadSources() {
		sourceTree.setInput(DataManager.getInstance().getModelRoot());
		rebuildSourceList();
	}

	private void refreshSources() {
		for (Object o : sourceTree.getCheckedElements())
			if (o instanceof CardCollection && ((CardCollection) o).isBoxed())
				sourceTree.setChecked(o, false);
		sourceTree.refresh();
		rebuildSourceList();
		rebuild();
	}

	private void rebuildSourceList() {
		sources.clear();
		for (CardCollection cc : DataManager.getInstance().getModelRoot().getDeckContainer().getAllElements())
			if (isEligible(cc))
				sources.add(cc);
		for (CardCollection cc : DataManager.getInstance().getModelRoot().getCollectionsContainer().getAllElements())
			if (isEligible(cc))
				sources.add(cc);
	}

	private static boolean isEligible(CardCollection cc) {
		Location loc = cc.getLocation();
		if (loc != null && (loc.isSideboard() || loc.isExtra()))
			return false; // pulled in with their main deck
		return !cc.isBoxed();
	}

	// ------------------------------------------------------- right pane

	private void createRightPane(Composite parent) {
		SashForm right = new SashForm(parent, SWT.VERTICAL);
		Composite gridArea = new Composite(right, SWT.NONE);
		GridLayout gl = new GridLayout(1, false);
		gl.marginWidth = 0;
		gl.marginHeight = 0;
		gridArea.setLayout(gl);
		header = new Label(gridArea, SWT.NONE);
		header.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		createGrid(gridArea);

		SashForm bottom = new SashForm(right, SWT.HORIZONTAL);
		createListBreakdown(bottom);
		createCopyPanel(bottom);
		bottom.setWeights(new int[] { 45, 55 });
		right.setWeights(new int[] { 62, 38 });

		getSite().setSelectionProvider(cardSelectionProvider);
		gridViewer.addSelectionChangedListener(e -> {
			IStructuredSelection sel = e.getStructuredSelection();
			SelectionChangedEvent evt = new SelectionChangedEvent(cardSelectionProvider, toCardSelection(sel));
			for (ISelectionChangedListener l : cardSelectionListeners)
				l.selectionChanged(evt);
			updateListBreakdown(sel);
			updateOnlySelectedLabel();
			if (onlySelected.getSelection())
				rebuildGroups(); // the lists follow the selection
		});
	}

	private TableViewerColumn column(String title, int width, String tip, ColumnLabelProvider lp) {
		TableViewerColumn c = new TableViewerColumn(gridViewer, SWT.NONE);
		c.getColumn().setText(title);
		c.getColumn().setWidth(width);
		if (tip != null)
			c.getColumn().setToolTipText(tip);
		c.setLabelProvider(lp);
		return c;
	}

	private abstract static class RowLabel extends ColumnLabelProvider {
		abstract String text(Row r);

		@Override
		public String getText(Object element) {
			return text((Row) element);
		}
	}

	private void createGrid(Composite parent) {
		gridTable = new Table(parent, SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI);
		gridTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		gridTable.setHeaderVisible(true);
		gridTable.setLinesVisible(true);
		gridViewer = new TableViewer(gridTable);
		gridViewer.setContentProvider(ArrayContentProvider.getInstance());
		gridViewer.addFilter(new ViewerFilter() {
			@Override
			public boolean select(Viewer viewer, Object parentElement, Object element) {
				return !onlyToBuy || toBuy((Row) element) > 0;
			}
		});
		column("Card", 170, null, new RowLabel() {
			@Override
			String text(Row r) {
				return r.cardName;
			}
		});
		column("Owned", 80, "Copies owned, whole app - total (genuine/proxy)", new RowLabel() {
			@Override
			String text(Row r) {
				return formatSplit(r.owned);
			}
		});
		column("Boxed", 80, "Copies claimed by already-boxed decks - total (genuine/proxy)", new RowLabel() {
			@Override
			String text(Row r) {
				return formatSplit(r.boxed);
			}
		});
		column("Available", 80, "Owned minus Boxed - total (genuine/proxy)", new RowLabel() {
			@Override
			String text(Row r) {
				return formatSplit(r.available());
			}
		});
		column("Needed", 60, "Total copies wanted across the checked decks / collections", new RowLabel() {
			@Override
			String text(Row r) {
				return String.valueOf(r.wanted);
			}
		});
		column("To Buy", 60, "Needed minus Available (with \"Replace Proxies\": minus genuine copies only)",
				new RowLabel() {
					@Override
					String text(Row r) {
						return String.valueOf(toBuy(r));
					}
				});
		column("Set", 90, "\"Exact Match\" on: the printing to buy; off: any printing", new RowLabel() {
			@Override
			String text(Row r) {
				return !exactMatch ? "Any" : r.sample == null ? "" : String.valueOf(r.sample.getSet());
			}
		});
		column("CollNum", 60, null, new RowLabel() {
			@Override
			String text(Row r) {
				return !exactMatch ? "Any" : r.sample == null ? "" : String.valueOf(r.sample.getCard().getCollNumber());
			}
		});
		column("Finish", 70, null, new RowLabel() {
			@Override
			String text(Row r) {
				return !exactMatch ? "Any" : finishOf(r).toString();
			}
		});
		column("Price", 70, "Estimated unit price from the card database: the selected store's own prices"
				+ " (TCGplayer, Cardmarket), else TCGplayer's"
				+ " (\"Any\" printing: the price of the printing the decks use)", new RowLabel() {
					@Override
					String text(Row r) {
						float p = price(r);
						return p > 0 ? money(p) : "";
					}
				});
		column("Total", 75, "To Buy x Price", new RowLabel() {
			@Override
			String text(Row r) {
				float p = price(r);
				return p > 0 && toBuy(r) > 0 ? money(p * toBuy(r)) : "";
			}
		});
		column("Note", 220, null, new RowLabel() {
			@Override
			String text(Row r) {
				return hasOwnPrices() && toBuy(r) > 0 && price(r) <= 0
						? "No " + priceSourceLabel() + " price - probably not available there" : "";
			}

			@Override
			public Color getForeground(Object element) {
				return Display.getDefault().getSystemColor(SWT.COLOR_DARK_RED);
			}
		});
	}

	private void createListBreakdown(Composite parent) {
		Table table = new Table(parent, SWT.BORDER | SWT.FULL_SELECTION);
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		listViewer = new TableViewer(table);
		listViewer.setContentProvider(ArrayContentProvider.getInstance());
		TableViewerColumn needed = new TableViewerColumn(listViewer, SWT.NONE);
		needed.getColumn().setText("Needed");
		needed.getColumn().setWidth(110);
		needed.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return formatCell(entryInfo(element));
			}
		});
		TableViewerColumn source = new TableViewerColumn(listViewer, SWT.NONE);
		source.getColumn().setText("Deck / Collection");
		source.getColumn().setWidth(200);
		source.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return entryRef(element).label();
			}
		});
	}

	// ------------------------------------------------- copy panel (stores)

	private void createCopyPanel(Composite parent) {
		// scrolls when the bottom area is short - the Copy / Open buttons stay reachable
		copyScroll = new ScrolledComposite(parent, SWT.BORDER | SWT.V_SCROLL);
		copyScroll.setExpandHorizontal(true);
		copyScroll.setExpandVertical(true);
		Composite panel = new Composite(copyScroll, SWT.NONE);
		copyScroll.setContent(panel);
		copyScroll.addListener(SWT.Resize, e -> fitCopyPanel());
		panel.setLayout(new GridLayout(2, false));
		Label l = new Label(panel, SWT.NONE);
		l.setText("Copy the list for:");
		storeCombo = new Combo(panel, SWT.READ_ONLY | SWT.DROP_DOWN);
		storeCombo.addListener(SWT.Selection, e -> storeChanged());
		hiddenStoresNote = new Label(panel, SWT.WRAP);
		hiddenStoresNote.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		hiddenStoresNote.setForeground(hiddenStoresNote.getDisplay().getSystemColor(SWT.COLOR_WIDGET_DARK_SHADOW));
		refreshStoreChoices();
		skipUnpriced = new Button(panel, SWT.CHECK);
		skipUnpriced.setText("Leave out cards with no TCGplayer price");
		skipUnpriced.setToolTipText("No price for that printing + finish usually means no listing on that store"
				+ " (TCGplayer Mass Entry reports an error for them)");
		skipUnpriced.setSelection(true);
		skipUnpriced.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		skipUnpriced.addListener(SWT.Selection, e -> rebuildGroups());
		onlySelected = new Button(panel, SWT.CHECK);
		onlySelected.setText("Only the selected cards");
		onlySelected.setToolTipText("Build the lists from the rows selected in the table above (Ctrl/Shift-click"
				+ " to select several) instead of every card to buy");
		onlySelected.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		onlySelected.addListener(SWT.Selection, e -> rebuildGroups());
		combineFinishes = new Button(panel, SWT.CHECK);
		combineFinishes.setText("Combine foil and non-foil in one list");
		combineFinishes.setToolTipText("With Exact Match the foil and non-foil cards are two lists, since stores"
				+ " choose foil / non-foil once for the whole paste, not per card. Without Exact Match: any finish.");
		combineFinishes.setEnabled(exactMatch);
		combineFinishes.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		combineFinishes.addListener(SWT.Selection, e -> rebuildGroups());
		groupsArea = new Composite(panel, SWT.NONE);
		groupsArea.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
		groupsArea.setLayout(new GridLayout(3, false));
		priceNote = new Label(panel, SWT.WRAP);
		priceNote.setLayoutData(new GridData(SWT.FILL, SWT.END, true, false, 2, 1));
		priceNote.setForeground(priceNote.getDisplay().getSystemColor(SWT.COLOR_WIDGET_DARK_SHADOW));
		updateStoreOptions();
	}

	/** Lays the copy panel out again and gives it its real height, so it scrolls instead of squeezing. */
	private void relayoutCopyPanel() {
		if (copyScroll == null || copyScroll.isDisposed())
			return;
		copyScroll.getContent().getParent().layout(true, true);
		((Composite) copyScroll.getContent()).layout(true, true);
		fitCopyPanel();
	}

	private void fitCopyPanel() {
		Control content = copyScroll.getContent();
		if (content == null || content.isDisposed())
			return;
		int width = copyScroll.getClientArea().width;
		copyScroll.setMinSize(content.computeSize(width > 0 ? width : SWT.DEFAULT, SWT.DEFAULT));
	}

	/** The stores currently offered in {@link #storeCombo}, in its order. */
	private final List<BuyListFormat> shownStores = new ArrayList<>();
	private Label hiddenStoresNote;

	private BuyListFormat selectedStore() {
		int i = storeCombo.getSelectionIndex();
		return shownStores.isEmpty() ? BuyListFormat.TCGPLAYER : shownStores.get(i < 0 ? 0 : i);
	}

	/**
	 * With Exact Match, only stores that can be told which printing to sell are
	 * offered (a names-only store would quietly pick its own printing); the
	 * others are listed in a note. Keeps the current choice when still offered.
	 */
	/**
	 * The store matching the "Card prices from" preference: Cardmarket when
	 * Cardmarket prices are selected, TCGplayer otherwise - the view opens on it.
	 */
	private static BuyListFormat preferredStore() {
		try {
			IPriceProvider p = DataManager.getDBPriceStore().getProvider();
			if (p != null && PriceSources.CARDMARKET.equals(p.getName()))
				return BuyListFormat.CARDMARKET;
		} catch (RuntimeException e) {
			// no price store yet: TCGplayer
		}
		return BuyListFormat.TCGPLAYER;
	}

	private void refreshStoreChoices() {
		BuyListFormat current = shownStores.isEmpty() ? null : selectedStore();
		shownStores.clear();
		storeCombo.removeAll();
		List<String> hidden = new ArrayList<>();
		for (BuyListFormat f : BuyListFormat.values()) {
			// with Exact Match a store must take the specific printing (TCGplayer, for now)
			if (exactMatch && !f.supportsPrinting()) {
				hidden.add(f.getLabel());
				continue;
			}
			shownStores.add(f);
			storeCombo.add(f.getLabel());
		}
		int i = shownStores.indexOf(current != null ? current : preferredStore());
		storeCombo.select(i < 0 ? 0 : i);
		hiddenStoresNote.setText(hidden.isEmpty() ? ""
				: String.join(", ", hidden)
						+ ": not available with Exact Match (cannot ask for a specific printing).");
		((GridData) hiddenStoresNote.getLayoutData()).exclude = hidden.isEmpty();
		hiddenStoresNote.setVisible(!hidden.isEmpty());
		relayoutCopyPanel();
	}

	/** Every row, or only the rows selected in the grid ("Only the selected cards"). */
	private List<Row> rowsForLists() {
		if (!onlySelected.getSelection())
			return currentRows;
		List<Row> sel = new ArrayList<>();
		for (Object o : gridViewer.getStructuredSelection().toList())
			if (o instanceof Row)
				sel.add((Row) o);
		return sel;
	}

	private void updateOnlySelectedLabel() {
		int n = gridViewer.getStructuredSelection().size();
		onlySelected.setText("Only the selected cards" + (n > 0 ? " (" + n + ")" : ""));
		relayoutCopyPanel();
	}

	/** Rebuilds the copy panel's lists from the current rows / store / options. */
	private void rebuildGroups() {
		for (Control c : groupsArea.getChildren())
			c.dispose();
		BuyListFormat store = selectedStore();
		List<BuyListLine> lines = new ArrayList<>();
		int skipped = 0;
		for (Row r : rowsForLists()) {
			int n = toBuy(r);
			if (n <= 0 || r.sample == null)
				continue;
			if (hasOwnPrices() && skipUnpriced.getSelection() && price(r) <= 0) {
				skipped++;
				continue;
			}
			lines.add(exactMatch ? BuyListLine.of(r.sample, n)
					: new BuyListLine(r.cardName, n, null, null, null, null));
		}
		List<BuyListFormat.Group> groups = store.groups(lines, exactMatch, combineFinishes.getSelection());
		if (groups.isEmpty()) {
			Label none = new Label(groupsArea, SWT.WRAP);
			none.setText(onlySelected.getSelection() && gridViewer.getStructuredSelection().isEmpty()
					? "Select cards in the table above." : "Nothing to buy.");
			none.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 3, 1));
		}
		final String page = storePage(store);
		for (BuyListFormat.Group g : groups) {
			int cards = g.getText().split("\n").length;
			Label title = new Label(groupsArea, SWT.NONE);
			title.setText(g.getTitle() + " - " + cards + " line" + (cards == 1 ? "" : "s"));
			title.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			Button copy = new Button(groupsArea, SWT.PUSH);
			copy.setText("Copy");
			copy.setToolTipText(preview(g.getLines(), 10));
			final String text = g.getText();
			copy.addListener(SWT.Selection, e -> copyToClipboard(text));
			Button open = new Button(groupsArea, SWT.PUSH);
			if (store == BuyListFormat.TCGPLAYER) {
				// Mass Entry reads the list from its link (c=line||line) - opens already filled
				final String url = massEntryUrl(g.getLines());
				open.setText("Open in TCGplayer");
				if (url.length() <= MAX_LINK_LENGTH) {
					open.setToolTipText("Opens TCGplayer Mass Entry with this list already filled in");
					open.addListener(SWT.Selection, e -> BrowserLauncher.open(url));
				} else {
					open.setEnabled(false);
					open.setToolTipText("Too many cards for a link - use Copy and paste into Mass Entry");
				}
			} else {
				open.setText("Open store page");
				open.setEnabled(page != null);
				if (page != null) {
					open.setToolTipText(page);
					open.addListener(SWT.Selection, e -> BrowserLauncher.open(page));
				}
			}
			if (!g.getHint().isEmpty()) {
				Label hint = new Label(groupsArea, SWT.WRAP);
				hint.setText(g.getHint());
				hint.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 3, 1));
				hint.setForeground(hint.getDisplay().getSystemColor(SWT.COLOR_DARK_BLUE));
			}
		}
		if (skipped > 0) {
			Label s = new Label(groupsArea, SWT.WRAP);
			s.setText(skipped + " card" + (skipped == 1 ? "" : "s") + " left out (no " + priceSourceLabel()
					+ " price) - see the Note column.");
			s.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 3, 1));
			s.setForeground(s.getDisplay().getSystemColor(SWT.COLOR_DARK_RED));
		}
		relayoutCopyPanel();
	}

	/** Longest Mass Entry link offered (TCGplayer itself handles about 8000). */
	private static final int MAX_LINK_LENGTH = 7000;

	/** The first {@code max} lines, then "... and N more lines" - a tooltip stays readable for a long list. */
	static String preview(List<String> lines, int max) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < lines.size() && i < max; i++) {
			if (i > 0)
				sb.append('\n');
			sb.append(lines.get(i));
		}
		int more = lines.size() - max;
		if (more > 0)
			sb.append("\n... and ").append(more).append(" more line").append(more == 1 ? "" : "s");
		return sb.toString();
	}

	/**
	 * TCGplayer Mass Entry pre-filled through its {@code c} parameter: the
	 * lines ("1-563218" product ids, or "1 Name [SET] 84") joined by "||".
	 */
	static String massEntryUrl(List<String> lines) {
		StringBuilder sb = new StringBuilder("https://www.tcgplayer.com/massentry?productline=Magic&c=");
		for (int i = 0; i < lines.size(); i++) {
			if (i > 0)
				sb.append("||");
			try {
				sb.append(java.net.URLEncoder.encode(lines.get(i), "UTF-8"));
			} catch (java.io.UnsupportedEncodingException e) {
				throw new IllegalStateException(e); // UTF-8 is always supported
			}
		}
		return sb.toString();
	}

	private void copyToClipboard(String text) {
		Clipboard cb = new Clipboard(getShell().getDisplay());
		try {
			cb.setContents(new Object[] { text }, new Transfer[] { TextTransfer.getInstance() });
		} finally {
			cb.dispose();
		}
	}

	// ------------------------------------------------------- calculation

	private int toBuy(Row r) {
		return replaceProxies ? r.genuineShortfall() : r.shortfall();
	}

	/** The row's finish with Exact Match (rows are then per finish); otherwise any finish (non-foil price). */
	private CardFinish finishOf(Row r) {
		return exactMatch && r.finish != null ? r.finish : CardFinish.NONFOIL;
	}

	/**
	 * Price source used for the selected store: Cardmarket's own prices for
	 * Cardmarket, TCGplayer's for TCGplayer - and TCGplayer's as an estimate for
	 * the stores with no price data (Card Kingdom, the Canadian stores...).
	 */
	private String priceSource() {
		return selectedStore() == BuyListFormat.CARDMARKET ? PriceSources.CARDMARKET : PriceSources.TCGPLAYER;
	}

	/**
	 * Currency the Buyer shows prices in: the price source's own (TCGplayer USD,
	 * Cardmarket EUR) - not the app-wide display currency, so TCGplayer prices
	 * are never shown converted to euros (or Cardmarket's to dollars).
	 */
	private Currency storeCurrency() {
		return Currency.getInstance(PriceSources.currencyOf(priceSource()));
	}

	/** True when the price IS the selected store's own: a missing price then means "probably not sold there". */
	private boolean hasOwnPrices() {
		BuyListFormat s = selectedStore();
		return s == BuyListFormat.TCGPLAYER || s == BuyListFormat.CARDMARKET;
	}

	/** "TCGplayer" / "Cardmarket" - whose prices are shown. */
	private String priceSourceLabel() {
		return PriceSources.CARDMARKET.equals(priceSource()) ? "Cardmarket" : "TCGplayer";
	}

	/** Unit price of the row's printing in its finish, from the store's price source, in the user's currency; 0 when unknown. */
	private float price(Row r) {
		if (r.sample == null)
			return 0;
		IMagicCard card = r.sample.getCard();
		try {
			IPriceProvider src = ((DbPricesMultiFileStore) DataManager.getDBPriceStore()).getSource(priceSource());
			Currency cur = storeCurrency();
			switch (finishOf(r)) {
			case FOIL:
				return src.getDbPriceFoil(card, cur);
			case ETCHED:
				return src.getDbPriceEtched(card, cur);
			default:
				return src.getDbPrice(card, cur);
			}
		} catch (RuntimeException e) {
			return 0;
		}
	}

	private static String money(float v) {
		return String.format(Locale.ENGLISH, "%.2f", v);
	}

	private void rebuild() {
		List<CardCollection> checked = new ArrayList<>();
		for (CardCollection cc : sources)
			if (sourceTree.getChecked(cc))
				checked.add(cc);
		listViewer.setInput(Collections.emptyList());
		currentRows.clear();
		// like the set, the finish only counts with Exact Match (foil and non-foil apart)
		currentRows.addAll(DeckNeeds.compute(checked, exactMatch, exactMatch));
		this.anySourceChecked = !checked.isEmpty();
		gridViewer.setInput(new ArrayList<>(currentRows));
		gridTable.getParent().layout(true, true);
		updateHeader();
		rebuildGroups();
	}

	private boolean anySourceChecked;

	/** Availability (no price = probably no listing) only means something with the store's own prices. */
	private void storeChanged() {
		updateStoreOptions();
		gridViewer.refresh(); // Note column
		updateHeader();
		rebuildGroups();
	}

	/** Options that depend on the store: "leave out unpriced" only with the store's own prices. */
	private void updateStoreOptions() {
		boolean own = hasOwnPrices();
		skipUnpriced.setText("Leave out cards with no " + priceSourceLabel() + " price");
		skipUnpriced.setVisible(own);
		((GridData) skipUnpriced.getLayoutData()).exclude = !own;
		String code = storeCurrency().getCurrencyCode();
		priceNote.setText(hasOwnPrices()
				? "Prices: " + priceSourceLabel() + " prices from the card database (estimates), in " + code + "."
				: "Prices: TCGplayer prices as an estimate, in " + code + " - " + selectedStore().getLabel()
						+ "'s own prices are not known.");
		relayoutCopyPanel();
	}

	private void updateHeader() {
		int copies = 0;
		int cards = 0;
		int unpriced = 0;
		float total = 0;
		for (Row r : currentRows) {
			int n = toBuy(r);
			if (n <= 0)
				continue;
			copies += n;
			cards++;
			float p = price(r);
			if (p > 0)
				total += p * n;
			else
				unpriced++;
		}
		String cur = storeCurrency().getCurrencyCode();
		header.setText(!anySourceChecked ? "Check decks or collections on the left."
				: "To buy: " + copies + " cop" + (copies == 1 ? "y" : "ies") + " of " + cards + " card"
						+ (cards == 1 ? "" : "s") + " - estimated " + money(total) + " " + cur
						+ " (" + priceSourceLabel() + " prices" + (hasOwnPrices() ? "" : ", as an estimate") + ")"
						+ (hasOwnPrices() && unpriced > 0
								? " - " + unpriced + " without a " + priceSourceLabel() + " price" : ""));
		header.getParent().layout(true, true);
	}

	// ---------------------------------------------------------- helpers

	private ISelection toCardSelection(IStructuredSelection raw) {
		List<Object> cards = new ArrayList<>();
		for (Object o : raw.toList())
			if (o instanceof Row && ((Row) o).sample != null)
				cards.add(((Row) o).sample.getCard());
		return new StructuredSelection(cards);
	}

	private void updateListBreakdown(IStructuredSelection sel) {
		if (sel.size() != 1 || !(sel.getFirstElement() instanceof Row)) {
			listViewer.setInput(Collections.emptyList());
			return;
		}
		List<Map.Entry<ListRef, ListInfo>> entries = new ArrayList<>();
		for (Map.Entry<ListRef, ListInfo> e : ((Row) sel.getFirstElement()).perList.entrySet())
			if (e.getValue().total() > 0)
				entries.add(e);
		listViewer.setInput(entries);
	}

	@SuppressWarnings("unchecked")
	private static ListInfo entryInfo(Object element) {
		return ((Map.Entry<ListRef, ListInfo>) element).getValue();
	}

	@SuppressWarnings("unchecked")
	private static ListRef entryRef(Object element) {
		return ((Map.Entry<ListRef, ListInfo>) element).getKey();
	}

	private static String formatSplit(Split s) {
		return s.total() + " (" + s.genuine + "/" + s.proxy + ")";
	}

	/** Same wording as the Proxier's breakdown. */
	private static String formatCell(ListInfo info) {
		int total = info.total();
		if (info.proxy == 0 && info.needed == 0)
			return String.valueOf(total); // all real
		if (info.real == 0 && info.needed == 0)
			return total + " (proxy)";
		if (info.real == 0 && info.proxy == 0)
			return total + " (needed)";
		List<String> parts = new ArrayList<>();
		if (info.real > 0)
			parts.add(info.real + " real");
		if (info.proxy > 0)
			parts.add(info.proxy + " proxy");
		if (info.needed > 0)
			parts.add(info.needed + " needed");
		return total + " (" + String.join(", ", parts) + ")";
	}

	// ------------------------------------------------- create collection

	/**
	 * New collection "ToBuy" (de-duplicated with a trailing number) holding one
	 * virtual copy per row to buy, of the row's printing (and finish with Exact
	 * Match). Nothing existing is changed. Asks first.
	 */
	private void createToBuyCollection() {
		List<Row> rows = new ArrayList<>();
		int copies = 0;
		for (Row r : currentRows)
			if (toBuy(r) > 0 && r.sample != null) {
				rows.add(r);
				copies += toBuy(r);
			}
		if (rows.isEmpty()) {
			MessageDialog.openInformation(getShell(), "Create To-Buy Collection", "Nothing to buy.");
			return;
		}
		CollectionsContainer root = DataManager.getInstance().getModelRoot().getCollectionsContainer();
		String name = uniqueCollectionName(root, "ToBuy");
		if (!MessageDialog.openConfirm(getShell(), "Create To-Buy Collection",
				"Create a new collection \"" + name + "\" with " + rows.size() + " card" + (rows.size() == 1 ? "" : "s")
						+ ", " + copies + " cop" + (copies == 1 ? "y" : "ies") + " total?\n\n"
						+ "Cards are added as virtual copies - your decks and collections are not changed."))
			return;
		CardCollection coll = root.addDeck(name + ".xml", false, true);
		ICardStore<IMagicCard> store = coll.getStore();
		Location loc = coll.getLocation();
		List<MagicCardPhysical> toAdd = new ArrayList<>();
		for (Row r : rows) {
			MagicCardPhysical phi = new MagicCardPhysical(r.sample.getCard(), loc, true);
			phi.setCount(toBuy(r));
			if (exactMatch && finishOf(r) != CardFinish.NONFOIL)
				phi.setFinish(finishOf(r));
			toAdd.add(phi);
		}
		store.setMergeOnAdd(false);
		DataManager.getInstance().add(toAdd, store);
		store.setMergeOnAdd(!store.isUnsorted());
	}

	private static String uniqueCollectionName(CollectionsContainer parent, String base) {
		String candidate = base;
		for (int n = 2; parent.findChieldByName(candidate + ".xml") != null; n++)
			candidate = base + " " + n;
		return candidate;
	}
}
