/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *******************************************************************************/
package com.reflexit.magiccards.ui.views.analyzers;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationAdapter;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.DeckAccessories;
import com.reflexit.magiccards.core.model.DeckAccessories.Kind;
import com.reflexit.magiccards.core.model.DeckAccessories.Need;
import com.reflexit.magiccards.core.model.DeckAccessories.Result;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.storage.ICardStore;
import com.reflexit.magiccards.core.model.storage.IDbCardStore;
import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.actions.RefreshAction;
import com.reflexit.magiccards.ui.utils.StoredSelectionProvider;
import com.reflexit.magiccards.ui.utils.SymbolRenderer;

/**
 * Deck tab that shows every physical accessory the deck needs at the table:
 * token and emblem cards, player markers, counters, keyword markers and dice.
 * Data comes from the {@code ACCESSORIES} field stored on each card during the
 * Scryfall bulk parse, re-derived live for counters/dice/markers; the deck's own
 * {@code -extra} list (see {@link DeckAccessories}) supplies the printing
 * the user actually owns.
 *
 * <p>
 * The left side is a plain card list, like the sideboard's - one row per
 * accessory with a "Type" column telling tokens from counters from markers apart
 * - so counters and markers sit in the same list instead of a separate picture
 * gallery. Selecting a row fills the right-hand panel with the deck cards that
 * require it.
 */
public class AccessoriesPage extends AbstractDeckPage {

	// Real-looking http URL (not a custom scheme): the SWT Browser resolves this
	// predictably against its <base href> and always delivers it to the
	// LocationListener, where we cancel the navigation and act on the path.
	private static final String CARD_URL = "http://acc.local/card/";

	private Table table;
	private TableViewer tableViewer;
	private org.eclipse.swt.graphics.Color uselessRowBg;
	private Label banner;
	private Browser detail;
	private Text fallback;
	private final ISelectionProvider selProvider = new StoredSelectionProvider();
	private RefreshAction refreshAction;

	private Result model;
	/** the selected accessory, by its {@link Need#key}. */
	private String selectedKey;
	/** the selected card in the detail panel, by scryfall id (raw). */
	private String selectedCardId;

	@Override
	public void createPageContents(Composite area) {
		area.setLayout(new org.eclipse.swt.layout.FillLayout());
		SashForm sash = new SashForm(area, SWT.HORIZONTAL);
		createList(sash);
		try {
			detail = new Browser(sash, SWT.NONE);
			detail.addLocationListener(linkHandler());
		} catch (SWTError e) {
			MagicUIActivator.log(e);
			detail = null;
			fallback = new Text(sash, SWT.READ_ONLY | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
		}
		sash.setWeights(new int[] { 55, 45 });
	}

	private void createList(Composite parent) {
		Composite left = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		layout.verticalSpacing = 4;
		left.setLayout(layout);

		banner = new Label(left, SWT.WRAP);
		banner.setBackground(left.getDisplay().getSystemColor(SWT.COLOR_INFO_BACKGROUND));
		banner.setForeground(left.getDisplay().getSystemColor(SWT.COLOR_INFO_FOREGROUND));
		GridData bannerData = new GridData(GridData.FILL_HORIZONTAL);
		bannerData.exclude = true;
		banner.setLayoutData(bannerData);
		banner.setVisible(false);

		tableViewer = new TableViewer(left, SWT.FULL_SELECTION | SWT.SINGLE | SWT.BORDER);
		table = tableViewer.getTable();
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		table.setLayoutData(new GridData(GridData.FILL_BOTH));
		tableViewer.setContentProvider(ArrayContentProvider.getInstance());

		uselessRowBg = new org.eclipse.swt.graphics.Color(left.getDisplay(), 248, 215, 218);
		table.addDisposeListener(e -> uselessRowBg.dispose());

		// leftmost: how many matching cards are already stocked in the deck's Extra
		// list - "-" when there's no possible card to stock (a bare counter/die/marker)
		addColumn("Have", 55, n -> n.card == null ? "-" : String.valueOf(n.haveCount));
		addColumn("Name", 220, n -> n.label);
		addColumn("Type", 110, n -> typeLabel(n.kind));
		// each printing that needs this counted separately - matches the tiles shown
		// on the right when this row is selected
		addColumn("Deck Cards", 80, n -> String.valueOf(n.getDeckCards()));

		tableViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				IStructuredSelection sel = (IStructuredSelection) event.getSelection();
				Need n = (Need) sel.getFirstElement();
				selectedKey = n == null ? null : n.key;
				selectedCardId = null;
				renderDetail(buildDetail(n));
				if (n != null && n.card != null)
					pushCardSelection(n.card.getCardId());
			}
		});
	}

	private interface CellText {
		String get(Need n);
	}

	private void addColumn(String title, int width, CellText text) {
		TableViewerColumn col = new TableViewerColumn(tableViewer, SWT.NONE);
		TableColumn tc = col.getColumn();
		tc.setText(title);
		tc.setWidth(width);
		col.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return text.get((Need) element);
			}

			@Override
			public org.eclipse.swt.graphics.Color getBackground(Object element) {
				return ((Need) element).kind == Kind.USELESS ? uselessRowBg : null;
			}
		});
		tc.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				sortBy(tc, text);
			}
		});
	}

	private TableColumn sortColumn;
	private boolean sortAscending = true;

	private void sortBy(TableColumn column, CellText text) {
		sortAscending = column == sortColumn ? !sortAscending : true;
		sortColumn = column;
		table.setSortColumn(column);
		table.setSortDirection(sortAscending ? SWT.UP : SWT.DOWN);
		final boolean asc = sortAscending;
		tableViewer.setComparator(new ViewerComparator() {
			@Override
			public int compare(Viewer viewer, Object e1, Object e2) {
				int c = String.valueOf(text.get((Need) e1)).compareToIgnoreCase(String.valueOf(text.get((Need) e2)));
				return asc ? c : -c;
			}
		});
	}

	private static String typeLabel(Kind k) {
		switch (k) {
		case TOKEN:
			return "Token";
		case EMBLEM:
			return "Emblem";
		case PLAYER_MARKER:
			return "Player Marker";
		case COUNTER:
			return "Counter";
		case KEYWORD:
			return "Marker";
		case DIE:
			return "Die/Coin";
		case USELESS:
			return "Useless";
		default:
			return "";
		}
	}

	private LocationAdapter linkHandler() {
		return new LocationAdapter() {
			@Override
			public void changing(LocationEvent event) {
				String loc = event.location;
				if (loc == null || loc.equals("about:blank"))
					return;
				int c = loc.indexOf("/card/");
				if (loc.startsWith("http://acc.local") && c >= 0) {
					event.doit = false;
					selectCard(decodeUrl(loc.substring(c + "/card/".length())));
				} else if (loc.startsWith("http")) {
					event.doit = false; // never navigate the panel away
				}
			}
		};
	}

	@Override
	protected void makeActions() {
		refreshAction = new RefreshAction(this::refresh);
	}

	@Override
	public void fillLocalToolBar(IToolBarManager manager) {
		manager.add(refreshAction);
	}

	@Override
	public void activate() {
		super.activate();
		refresh();
	}

	@Override
	public void refresh() {
		if (tableViewer == null || table.isDisposed())
			return;
		try {
			model = compute();
		} catch (Exception e) {
			MagicUIActivator.log(e);
			model = null;
			tableViewer.setInput(java.util.Collections.emptyList());
			updateBanner(null);
			renderDetail("<p>Could not work out the deck's accessories.</p>");
			return;
		}
		if (model == null) {
			selectedKey = null;
			tableViewer.setInput(java.util.Collections.emptyList());
			updateBanner(null);
			renderDetail("<p><i>This deck needs no tokens, counters, dice or markers.</i></p>");
			return;
		}
		updateBanner(model.incomplete);
		if (model.isEmpty()) {
			selectedKey = null;
			tableViewer.setInput(java.util.Collections.emptyList());
			renderDetail("<p><i>This deck needs no tokens, counters, dice or markers.</i></p>");
			return;
		}
		tableViewer.setInput(model.all());
		Need sel = selectedKey == null ? null : model.find(selectedKey);
		if (sel != null) {
			tableViewer.setSelection(new StructuredSelection(sel), true);
		} else {
			selectedKey = null;
			renderDetail(buildDetail(null));
		}
	}

	private void updateBanner(List<String> incomplete) {
		if (banner == null || banner.isDisposed())
			return;
		boolean show = incomplete != null && !incomplete.isEmpty();
		if (show) {
			int n = incomplete.size();
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < n && i < 6; i++) {
				if (i > 0)
					sb.append(", ");
				sb.append(incomplete.get(i));
			}
			if (n > 6)
				sb.append(", …");
			banner.setText("⚠ " + n + (n == 1 ? " deck card makes" : " deck cards make")
					+ " a token or emblem we could not identify - the list may be incomplete: " + sb
					+ ". Add the missing card by hand from the database.");
		}
		((GridData) banner.getLayoutData()).exclude = !show;
		banner.setVisible(show);
		banner.getParent().layout(true, true);
	}

	/** Push a scryfall id to the workbench selection so the card views update. */
	private void pushCardSelection(String scryfallId) {
		try {
			IDbCardStore<IMagicCard> db = DataManager.getCardHandler().getMagicDBStore();
			IMagicCard card = db == null ? null : db.getCard(scryfallId);
			if (card != null)
				selProvider.setSelection(new StructuredSelection(card));
		} catch (Exception e) {
			MagicUIActivator.log(e);
		}
	}

	private Result compute() {
		ICardStore<IMagicCard> deckStore = getCardStore();
		if (deckStore == null)
			return null;
		Location loc = deckStore.getLocation();
		List<IMagicCard> cards = new ArrayList<>();
		ICardStore<IMagicCard> main = DataManager.getInstance().getCardStore(loc.toMainDeck());
		if (main != null)
			cards.addAll(main.getCards());
		else
			cards.addAll(deckStore.getCards());
		// the sideboard is always part of what the deck needs - not optional
		ICardStore<IMagicCard> side = DataManager.getInstance().getCardStore(loc.toSideboard());
		if (side != null)
			cards.addAll(side.getCards());
		// the deck's extra list - the printings the user has stocked; kept separate
		// from `cards` so a token card sitting in the main deck/sideboard is never
		// mistaken for something the user has stocked in extra (they're not the same)
		ICardStore<IMagicCard> acc = DataManager.getInstance().getCardStore(loc.toExtra());
		List<IMagicCard> extraCards = acc == null ? null : new ArrayList<>(acc.getCards());
		IDbCardStore<IMagicCard> db = DataManager.getCardHandler().getMagicDBStore();
		return DeckAccessories.compute(cards, extraCards, db);
	}

	/** A card was clicked in the detail panel: mark it there and drive the card views. */
	private void selectCard(String scryfallId) {
		selectedCardId = scryfallId;
		pushCardSelection(scryfallId);
		if (detail != null && !detail.isDisposed()) {
			try {
				detail.execute("accSelCard('" + encodeUrl(scryfallId).replace("'", "%27") + "')");
			} catch (Exception e) {
				renderDetail(buildDetail(model == null ? null : model.find(selectedKey)));
			}
		}
	}

	// --- rendering (detail panel only) --------------------------------------

	/** Moves the {@code .sel} highlight to the detail-panel card with the given (encoded) id. */
	private static final String DETAIL_SCRIPT = "<script>function accSelCard(k){"
			+ "var o=document.querySelector('.acc-tile.sel');if(o)o.classList.remove('sel');"
			+ "var t=document.querySelector('.acc-tile[data-card=\"'+k+'\"]');"
			+ "if(t)t.classList.add('sel');return true;}</script>";

	private void renderDetail(String body) {
		if (detail != null && !detail.isDisposed())
			detail.setText(SymbolRenderer.wrapHtml(detailStyle() + body + DETAIL_SCRIPT, detail));
		else if (fallback != null && !fallback.isDisposed())
			fallback.setText(body.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim());
	}

	private static String detailStyle() {
		return "<style>"
				+ ".acc-detail h3{margin:0 0 2px 0;} .acc-detail .lead{opacity:.7;margin:0 0 12px 0;}"
				+ ".acc-tiles{display:flex;flex-wrap:wrap;align-items:flex-start;gap:12px;}"
				+ ".acc-tile{width:220px;text-align:center;font-size:.88em;text-decoration:none;color:inherit;"
				+ "border:3px solid transparent;border-radius:12px;padding:4px;}"
				+ ".acc-tile.sel{border-color:#3d7eff;background:rgba(61,126,255,.12);}"
				+ ".acc-tile img{width:208px;height:290px;border-radius:9px;display:block;margin:0 auto;"
				+ "box-shadow:0 1px 5px rgba(0,0,0,.4);object-fit:cover;background:#3336;}"
				+ ".acc-tile .cap{margin-top:4px;line-height:1.22;}"
				+ "</style>";
	}

	private String buildDetail(Need n) {
		if (n == null)
			return "<p class='lead'><i>Select an accessory to see which cards need it.</i></p>";
		StringBuilder sb = new StringBuilder("<div class='acc-detail'>");
		sb.append("<h3>").append(esc(n.label)).append(" &middot; ").append(esc(typeLabel(n.kind))).append("</h3>");
		if (n.kind == Kind.USELESS) {
			sb.append("<p class='lead'>Stocked in the deck's Extra list, but not needed by anything in this deck.</p>");
			sb.append("<div class='acc-tiles'>");
			if (n.card != null)
				tile(sb, n.card, n.card.getCardId());
			sb.append("</div></div>");
			return sb.toString();
		}
		sb.append("<p class='lead'>").append(n.getDeckCards())
				.append(n.getDeckCards() == 1 ? " card in this deck" : " cards in this deck").append("</p>");
		sb.append("<div class='acc-tiles'>");
		for (IMagicCard src : n.sources)
			tile(sb, src, src.getCardId());
		sb.append("</div></div>");
		return sb.toString();
	}

	private void tile(StringBuilder sb, IMagicCard card, String rawId) {
		String img = imageUrl(card);
		String id = rawId == null ? "" : rawId;
		String enc = encodeUrl(id);
		boolean sel = id.equals(selectedCardId);
		sb.append("<a class='acc-tile").append(sel ? " sel" : "").append("' data-card='").append(enc)
				.append("' href='").append(CARD_URL).append(enc).append("'>");
		if (img != null)
			sb.append("<img src=\"").append(img).append("\" alt=\"").append(esc(card.getName())).append("\"/>");
		sb.append("<div class='cap'>").append(esc(card.getName())).append("</div></a>");
	}

	private static String imageUrl(IMagicCard card) {
		MagicCard mc = null;
		if (card instanceof MagicCard)
			mc = (MagicCard) card;
		else if (card instanceof MagicCardPhysical)
			mc = ((MagicCardPhysical) card).getCard();
		if (mc == null)
			return null;
		String url = mc.getImageUrl();
		return (url == null || url.isEmpty()) ? null : url;
	}

	private static String encodeUrl(String s) {
		try {
			return URLEncoder.encode(s, StandardCharsets.UTF_8.name()).replace("+", "%20");
		} catch (UnsupportedEncodingException e) {
			return s;
		}
	}

	private static String decodeUrl(String s) {
		try {
			return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8.name());
		} catch (UnsupportedEncodingException e) {
			return s;
		}
	}

	private static String esc(String s) {
		if (s == null)
			return "";
		// strip {mana}/{symbol} tokens - SymbolRenderer.wrapHtml turns them into
		// <img> tags across the whole doc, which corrupts them inside attributes
		s = s.replaceAll("\\{[^}]{0,6}\\}", "");
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'",
				"&#39;");
	}

	@Override
	public ISelectionProvider getSelectionProvider() {
		return selProvider;
	}

	@Override
	public boolean hookContextMenu(MenuManager menuMgr) {
		return false;
	}
}
