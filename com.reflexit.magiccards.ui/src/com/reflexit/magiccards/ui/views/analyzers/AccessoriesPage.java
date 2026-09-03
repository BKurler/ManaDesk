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

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationAdapter;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
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
import com.reflexit.magiccards.ui.actions.ImageAction;
import com.reflexit.magiccards.ui.actions.RefreshAction;
import com.reflexit.magiccards.ui.utils.StoredSelectionProvider;
import com.reflexit.magiccards.ui.utils.SymbolRenderer;

/**
 * Deck tab that shows, as a picture gallery, every physical accessory the deck
 * needs at the table: token and emblem cards, counters, dice and status markers.
 * Data comes from the {@code ACCESSORIES} field stored on each card during the
 * Scryfall bulk parse; {@link DeckAccessories} turns it into the per-deck list.
 *
 * <p>
 * The gallery is on the left; every tile is selectable (one at a time). Selecting
 * a tile fills the right-hand panel with the deck cards that require it.
 */
public class AccessoriesPage extends AbstractDeckPage {

	// Real-looking http URLs (not custom schemes): the SWT Browser resolves these
	// predictably against its <base href> and always delivers them to the
	// LocationListener, where we cancel the navigation and act on the path.
	private static final String SELECT_URL = "http://acc.local/select/";
	private static final String CARD_URL = "http://acc.local/card/";

	private Browser gallery;
	private Browser detail;
	private Text fallback;
	private final ISelectionProvider selProvider = new StoredSelectionProvider();
	private boolean includeSideboard = true;
	private RefreshAction refreshAction;
	private ImageAction sideboardAction;

	private Result model;
	/** the selected gallery tile (accessory), by url-encoded key. */
	private String selectedKey;
	/** the selected card in the detail panel, by scryfall id (raw). */
	private String selectedCardId;

	@Override
	public void createPageContents(Composite area) {
		area.setLayout(new FillLayout());
		try {
			SashForm sash = new SashForm(area, SWT.HORIZONTAL);
			gallery = new Browser(sash, SWT.NONE);
			gallery.addLocationListener(linkHandler(true));
			detail = new Browser(sash, SWT.NONE);
			detail.addLocationListener(linkHandler(false));
			sash.setWeights(new int[] { 70, 30 });
		} catch (SWTError e) {
			MagicUIActivator.log(e);
			gallery = null;
			detail = null;
			fallback = new Text(area, SWT.READ_ONLY | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
		}
	}

	private LocationAdapter linkHandler(final boolean isGallery) {
		return new LocationAdapter() {
			@Override
			public void changing(LocationEvent event) {
				String loc = event.location;
				if (loc == null || loc.equals("about:blank"))
					return;
				int s = loc.indexOf("/select/");
				int c = loc.indexOf("/card/");
				if (loc.startsWith("http://acc.local") && s >= 0) {
					event.doit = false;
					select(decodeUrl(loc.substring(s + "/select/".length())));
				} else if (loc.startsWith("http://acc.local") && c >= 0) {
					event.doit = false;
					selectCard(decodeUrl(loc.substring(c + "/card/".length())));
				} else if (loc.startsWith("http")) {
					event.doit = false; // never navigate the panels away
				}
			}
		};
	}

	@Override
	protected void makeActions() {
		refreshAction = new RefreshAction(this::refresh);
		sideboardAction = new ImageAction("Include Sideboard", "icons/obj16/sideboard16.png", IAction.AS_CHECK_BOX) {
			@Override
			public void run() {
				includeSideboard = !includeSideboard;
				setChecked(includeSideboard);
				refresh();
			}
		};
		sideboardAction.setChecked(includeSideboard);
	}

	@Override
	public void fillLocalToolBar(IToolBarManager manager) {
		manager.add(sideboardAction);
		manager.add(new Separator());
		manager.add(refreshAction);
	}

	@Override
	public void activate() {
		super.activate();
		refresh();
	}

	@Override
	public void refresh() {
		if (gallery == null && fallback == null)
			return;
		try {
			model = compute();
		} catch (Exception e) {
			MagicUIActivator.log(e);
			model = null;
			renderGallery("<p>Could not work out the deck's accessories.</p>");
			renderDetail("");
			return;
		}
		if (model == null || model.isEmpty()) {
			selectedKey = null;
			renderGallery("<p><i>This deck needs no tokens, counters, dice or markers.</i></p>");
			renderDetail("");
			return;
		}
		if (selectedKey != null && model.find(selectedKey) == null)
			selectedKey = null;
		renderGallery(buildGallery(model));
		renderDetail(buildDetail(model.find(selectedKey)));
	}

	private void select(String key) {
		selectedKey = key;
		selectedCardId = null;
		if (model == null)
			return;
		// move the highlight in-place via script - re-setting the whole gallery
		// HTML makes every badge flash while its data: URI is re-decoded.
		boolean moved = false;
		if (gallery != null && !gallery.isDisposed()) {
			try {
				moved = gallery.execute("accSelect('" + encodeUrl(key).replace("'", "%27") + "')");
			} catch (Exception e) {
				moved = false;
			}
		}
		if (!moved)
			renderGallery(buildGallery(model));
		Need n = model.find(key);
		renderDetail(buildDetail(n));
		// selecting a real card (token / emblem / player marker) also drives the
		// Card Info / Printing / Instances views
		if (n != null && n.card != null)
			pushCardSelection(n.card.getCardId());
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
		if (includeSideboard) {
			ICardStore<IMagicCard> side = DataManager.getInstance().getCardStore(loc.toSideboard());
			if (side != null)
				cards.addAll(side.getCards());
		}
		IDbCardStore<IMagicCard> db = DataManager.getCardHandler().getMagicDBStore();
		return DeckAccessories.compute(cards, db);
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

	// --- rendering ----------------------------------------------------------

	/** Moves the {@code .sel} highlight to the tile with the given (url-encoded) key - no scrolling. */
	private static final String SELECT_SCRIPT = "<script>function accSelect(k){"
			+ "var o=document.querySelector('.acc-tile.sel');"
			+ "if(o){o.classList.remove('sel');o.removeAttribute('id');}"
			+ "var t=document.querySelector('.acc-tile[data-key=\"'+k+'\"]');"
			+ "if(t){t.classList.add('sel');t.id='sel-tile';}"
			+ "return true;}</script>";

	private void renderGallery(String body) {
		if (gallery != null && !gallery.isDisposed()) {
			gallery.setText(SymbolRenderer.wrapHtml(galleryStyle() + body + SELECT_SCRIPT, gallery));
		} else if (fallback != null && !fallback.isDisposed()) {
			fallback.setText(body.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim());
		}
	}

	/** Moves the {@code .sel} highlight to the detail-panel card with the given (encoded) id. */
	private static final String DETAIL_SCRIPT = "<script>function accSelCard(k){"
			+ "var o=document.querySelector('.acc-tile.sel');if(o)o.classList.remove('sel');"
			+ "var t=document.querySelector('.acc-tile[data-card=\"'+k+'\"]');"
			+ "if(t)t.classList.add('sel');return true;}</script>";

	private void renderDetail(String body) {
		if (detail != null && !detail.isDisposed())
			detail.setText(SymbolRenderer.wrapHtml(galleryStyle() + body + DETAIL_SCRIPT, detail));
	}

	private static String galleryStyle() {
		return "<style>"
				+ ".acc-group{margin:0 0 18px 0;}"
				+ ".acc-group h4{margin:0 0 6px 0;font-size:1.05em;border-bottom:1px solid rgba(128,128,128,.35);}"
				+ ".acc-tiles{display:flex;flex-wrap:wrap;align-items:flex-start;gap:12px;}"
				+ ".acc-tile{width:200px;text-align:center;font-size:.88em;text-decoration:none;color:inherit;"
				+ "border:3px solid transparent;border-radius:12px;padding:4px;}"
				+ ".acc-tile.sel{border-color:#3d7eff;background:rgba(61,126,255,.12);}"
				+ ".acc-tile img{width:188px;height:262px;border-radius:9px;display:block;margin:0 auto;"
				+ "box-shadow:0 1px 5px rgba(0,0,0,.4);object-fit:cover;background:#3336;}"
				// counter / keyword / dice badges are ~1/3 height for density; token /
				// emblem / player-marker placeholders keep the full card size
				+ ".acc-tile.badge img{height:88px;object-fit:fill;}"
				+ ".acc-tile .cap{margin-top:4px;line-height:1.22;}"
				+ ".acc-tile .sub{opacity:.6;font-size:.9em;}"
				// the detail panel shows one thing at a time - give its cards more room
				+ ".acc-detail h3{margin:0 0 2px 0;} .acc-detail .lead{opacity:.7;margin:0 0 12px 0;}"
				+ ".acc-detail .acc-tile{width:250px;} .acc-detail .acc-tile img{width:238px;height:332px;}"
				+ "</style>";
	}

	private String buildGallery(Result r) {
		StringBuilder sb = new StringBuilder();
		group(sb, "Tokens", r.tokens);
		group(sb, "Emblems", r.emblems);
		group(sb, "Player Markers", r.playerMarkers);
		group(sb, "Counters", r.counters);
		group(sb, "Markers", r.keywords);
		group(sb, "Dice & Coins", r.dice);
		return sb.toString();
	}

	private void group(StringBuilder sb, String title, List<Need> needs) {
		if (needs == null || needs.isEmpty())
			return;
		sb.append("<div class='acc-group'><h4>").append(esc(title)).append("</h4><div class='acc-tiles'>");
		for (Need n : needs)
			tile(sb, n);
		sb.append("</div></div>");
	}

	private void tile(StringBuilder sb, Need n) {
		String img = imageUrl(n.card);
		boolean noPic = img == null;
		// compact = a small pill; only for the die-tracked / marker counters, never
		// for a token / emblem / player-marker whose picture just failed to load
		boolean compact = noPic && (n.kind == Kind.COUNTER || n.kind == Kind.KEYWORD || n.kind == Kind.DIE);
		if (noPic)
			img = badge(n, compact);
		boolean sel = n.key.equals(selectedKey);
		String enc = encodeUrl(n.key);
		sb.append("<a class='acc-tile").append(sel ? " sel" : "").append(compact ? " badge" : "").append("'")
				.append(sel ? " id='sel-tile'" : "").append(" data-key='").append(enc).append("'")
				.append(" href='").append(SELECT_URL).append(enc).append("'>");
		sb.append("<img src=\"").append(img).append("\" alt=\"").append(esc(n.label)).append("\"/>");
		sb.append("<div class='cap'>").append(esc(n.label));
		sb.append("<div class='sub'>").append(n.getDeckCards()).append(n.getDeckCards() == 1 ? " card" : " cards");
		sb.append("</div></div></a>");
	}

	private String buildDetail(Need n) {
		if (n == null)
			return "<p class='lead'><i>Select an accessory to see which cards need it.</i></p>";
		StringBuilder sb = new StringBuilder("<div class='acc-detail'>");
		sb.append("<h3>").append(esc(n.label)).append("</h3>");
		sb.append("<p class='lead'>").append(n.getDeckCards()).append(n.getDeckCards() == 1 ? " card in this deck" : " cards in this deck").append("</p>");
		sb.append("<div class='acc-tiles'>");
		for (IMagicCard src : n.sources) {
			String img = imageUrl(src);
			String id = src.getCardId() == null ? "" : src.getCardId();
			String enc = encodeUrl(id);
			boolean sel = id.equals(selectedCardId);
			sb.append("<a class='acc-tile").append(sel ? " sel" : "").append("' data-card='").append(enc)
					.append("' href='").append(CARD_URL).append(enc).append("'>");
			if (img != null)
				sb.append("<img src=\"").append(img).append("\" alt=\"").append(esc(src.getName())).append("\"/>");
			sb.append("<div class='cap'>").append(esc(src.getName())).append("</div></a>");
		}
		sb.append("</div></div>");
		return sb.toString();
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

	/**
	 * A dark SVG pill used when there is no real card picture. The word is written
	 * out in full (no acronyms) - the font shrinks to fit the width.
	 *
	 * @param compact a short pill (counter / keyword / die); otherwise a full
	 *                card-sized placeholder (token / emblem / player marker)
	 */
	private static String badge(Need n, boolean compact) {
		String big;
		if (n.kind == Kind.DIE)
			big = n.label.toLowerCase().startsWith("d") && n.label.length() <= 4 ? n.label : "coin";
		else if (n.kind == Kind.COUNTER || n.kind == Kind.KEYWORD)
			big = n.key.length() > 1 ? n.key.substring(1) : n.label;
		else
			big = n.label;
		big = clip(big, 24);

		int h = compact ? 54 : 168;
		int base = compact ? 22 : 26;
		int cy = compact ? 30 : 88;
		int fs = Math.max(9, Math.min(base, 190 / Math.max(1, big.length())));
		String svg = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 120 " + h + "' width='120' height='" + h
				+ "'>" + "<rect x='2' y='2' width='116' height='" + (h - 4) + "' rx='8' ry='8'"
				+ " fill='#2b2f38' stroke='#8a8f99' stroke-width='2'/>"
				+ "<text x='60' y='" + cy + "' font-family='sans-serif' font-size='" + fs + "' font-weight='bold'"
				+ " fill='#f2f4f8' text-anchor='middle' dominant-baseline='middle'>" + esc(big) + "</text>"
				+ "</svg>";
		return "data:image/svg+xml;charset=utf-8," + encodeUrl(svg);
	}

	private static String clip(String s, int max) {
		return s.length() <= max ? s : s.substring(0, Math.max(1, max - 1)) + "…";
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
