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
package com.reflexit.magiccards.ui.gallery;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IContentProvider;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IWorkbenchPartSite;

import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.abs.ICardGroup;
import com.reflexit.magiccards.core.model.storage.IFilteredCardStore;
import com.reflexit.magiccards.ui.views.IColumnSortAction;
import com.reflexit.magiccards.ui.views.IMagicViewer;

public class BrowserGalleryViewer extends Viewer implements IMagicViewer, ISelectionProvider {

	/** Flip to {@code true} for a JS/Java console trace of gallery selection. */
	private static final boolean DEBUG = false;

	// --- core widget ---
	private final Browser browser;

	// --- content provider ---
	private IStructuredContentProvider contentProvider;

	// --- current input & selection ---
	private Object input;
	private Object currentSelectionElement;

	// --- listeners ---
	private final List<ISelectionChangedListener> selectionChangedListeners = new ArrayList<>();
	private final List<IDoubleClickListener> doubleClickListeners = new ArrayList<>();

	private java.util.List<IMagicCard> flatInput = java.util.Collections.emptyList();
	/** id of the card a programmatic setSelection() wants highlighted once the page is ready */
	private String pendingSelectId;

	public BrowserGalleryViewer(Composite parent, int style) {

		this.browser = new Browser(parent, style);
		browser.setLayoutData(new GridData(GridData.FILL_BOTH));
		// re-apply a queued programmatic selection after each (re)render
		browser.addProgressListener(new org.eclipse.swt.browser.ProgressListener() {
			@Override
			public void changed(org.eclipse.swt.browser.ProgressEvent event) {
				// nothing
			}

			@Override
			public void completed(org.eclipse.swt.browser.ProgressEvent event) {
				applyPendingSelect();
			}
		});

		// Removed debug mouse logging
		// browser.addListener(SWT.MouseDown, e -> {
		// System.out.println("BROWSER MOUSEDOWN at " + e.x + "," + e.y);
		// });

		hookSelectionBridge();
		hookDoubleClickBridge();

		// JS -> Java debug log (visible on -consoleLog). The JS side guards every
		// call with `if (window.javaLog)`, so when DEBUG is off we simply don't
		// register it and the trace calls become no-ops.
		if (DEBUG) {
			new BrowserFunction(browser, "javaLog") {
				@Override
				public Object function(Object[] args) {
					System.out.println("[gallery-js] " + (args != null && args.length > 0 ? args[0] : ""));
					return null;
				}
			};
		}

		// Virtual scrolling: JS → Java
		new BrowserFunction(browser, "loadCardRange") {
			@Override
			public Object function(Object[] args) {
				int start = ((Double) args[0]).intValue();
				int count = ((Double) args[1]).intValue();

				int end = Math.min(start + count, flatInput.size());
				List<IMagicCard> slice = flatInput.subList(start, end);

				return cardsToJson(slice);
			}
		};

		// Removed debug selection logging
		// this.getSelectionProvider().addSelectionChangedListener(event -> {
		// System.out.println("SELECTION FIRED: " + event.getSelection());
		// });
	}

	// ============================================================
	// Viewer implementation
	// ============================================================

	@Override
	public Control getControl() {
		return browser;
	}

	@Override
	protected void inputChanged(Object newInput, Object oldInput) {
		this.input = newInput;
		if (contentProvider != null) {
			contentProvider.inputChanged(this, oldInput, newInput);
		}
		render();
	}

	@Override
	public Object getInput() {
		return input;
	}

	@Override
	public void setInput(Object input) {
		Object old = this.input;
		this.input = input;

		// cache the flattened list used everywhere else
		this.flatInput = flatten(input);

		// notify with the flattened input
		inputChanged(flatInput, old);
	}

	/**
	 * Flattens any supported input into a List<ICard>. Works with: -
	 * IFilteredCardStore (root group) - List<ICardGroup> - List<ICard> - Single
	 * ICardGroup - Already-flat lists
	 */
	@SuppressWarnings("unchecked")
	private List<IMagicCard> flatten(Object input) {
		List<IMagicCard> result = new ArrayList<>();

		if (input == null) {
			return result;
		}

		// Case 1 — Store root
		if (input instanceof IFilteredCardStore) {
			IFilteredCardStore store = (IFilteredCardStore) input;
			return flatten(store.getCardGroupRoot());
		}

		// Case 2 — Single CardGroup
		if (input instanceof ICardGroup) {
			ICardGroup group = (ICardGroup) input;
			for (Object child : group.getChildrenList()) {
				result.addAll(flatten(child));
			}
			return result;
		}

		// Case 3 — Single card
		if (input instanceof IMagicCard) {
			result.add((IMagicCard) input);
			return result;
		}

		// Case 4 — List of mixed objects
		if (input instanceof List<?>) {
			for (Object o : (List<?>) input) {
				result.addAll(flatten(o));
			}
			return result;
		}

		// Case 5 — Unknown type → ignore
		return result;
	}

	@Override
	public ISelection getSelection() {
		if (currentSelectionElement == null)
			return StructuredSelection.EMPTY;
		return new StructuredSelection(currentSelectionElement);
	}

	@Override
	public void setSelection(ISelection selection, boolean reveal) {
		if (selection instanceof IStructuredSelection) {
			Object element = ((IStructuredSelection) selection).getFirstElement();
			String id = element instanceof IMagicCard ? String.valueOf(((IMagicCard) element).getCardId()) : null;
			// Ignore a request to select a card that is not in the current input.
			// refreshViewer() re-asserts the pre-move selection (the card that was
			// just moved away) right before the reveal runs; honouring it would
			// stomp the real pending reveal and make the selection flicker
			// (select next -> deselect -> reselect).
			if (id != null && indexOfCardId(id) < 0)
				return;
			currentSelectionElement = element;
			fireSelectionChanged();
			// push the highlight into the browser (JFace setSelection alone does
			// nothing visible here). Queue it - the page may still be rendering.
			pendingSelectId = id;
			applyPendingSelect();
		}
	}

	/**
	 * Tell the gallery which card to highlight <em>before</em> its next render,
	 * so the selection is baked into the generated HTML - no separate
	 * highlight-flash after the page loads. Call this before triggering the
	 * setInput/render (e.g. before selecting the enclosing group in the tree).
	 */
	public void setPendingSelectId(String cardId) {
		this.pendingSelectId = cardId;
	}

	private void applyPendingSelect() {
		if (pendingSelectId == null || browser == null || browser.isDisposed())
			return;
		String id = pendingSelectId.replace("\\", "\\\\").replace("'", "\\'");
		int idx = indexOfCardId(pendingSelectId);
		boolean ok;
		try {
			ok = browser.execute(
					"if(window.selectCardById){selectCardById('" + id + "'," + idx + ");true;}else{false;}");
		} catch (Exception e) {
			ok = false;
		}
		if (DEBUG)
			System.out.println(
					"[gallery-java] applyPendingSelect id=" + pendingSelectId + " idx=" + idx + " execute=" + ok);
	}

	/** @return the flat index of the card with this id in the current input, or -1. */
	public int indexOfCardId(String cardId) {
		if (cardId == null)
			return -1;
		for (int i = 0; i < flatInput.size(); i++) {
			if (cardId.equals(String.valueOf(flatInput.get(i).getCardId())))
				return i;
		}
		return -1;
	}

	/** @return true if a card with this id is in the gallery's current input. */
	public boolean isShowingCardId(String cardId) {
		return indexOfCardId(cardId) >= 0;
	}

	// ============================================================
	// Content provider
	// ============================================================

	public void setContentProvider(IContentProvider provider) {
		if (provider instanceof IStructuredContentProvider) {
			this.contentProvider = (IStructuredContentProvider) provider;
		} else {
			throw new IllegalArgumentException("BrowserGalleryViewer requires an IStructuredContentProvider");
		}
	}

	public IStructuredContentProvider getContentProvider() {
		return contentProvider;
	}

	// ============================================================
	// Rendering
	// ============================================================
	private void render() {
		if (browser.isDisposed())
			return;

		int total = flatInput.size();
		int selIdx = pendingSelectId != null ? indexOfCardId(pendingSelectId) : -1;
		// Never pre-seed a card that isn't in this input - a stale id left over
		// from the previous move makes the fresh page hunt for a card that was
		// moved away (20 empty retries) and show nothing selected in between.
		if (selIdx < 0)
			pendingSelectId = null;
		String html = GalleryHtmlBuilder.buildVirtualGalleryHtml(total, pendingSelectId, selIdx);

		browser.setText(html, true);
	}

	@Override
	public void refresh() {
		render();
	}

	private Object resolveElementFromId(Object id) {
		if (id == null) {
			System.out.println("resolveElementFromId: id is null, ignoring");
			return null;
		}

		String sid = String.valueOf(id);

		// Ignore aggregation / synthetic entries like "*"
		if ("*".equals(sid)) {
			System.out.println("resolveElementFromId: aggregate id '*', ignoring");
			return null;
		}

		// !!! RD java.util.List<ICard> list = flatInput;
		// System.out.println("resolveElementFromId: input class = " + list.getClass());

		// Case 1: input is a plain List<IMagicCard>
		if (input instanceof java.util.List<?>) {
			for (Object o : (java.util.List<?>) input) {
				if (o instanceof IMagicCard) {
					IMagicCard card = (IMagicCard) o;
					if (sid.equals(String.valueOf(card.getCardId()))) {
						// System.out.println("resolveElementFromId: matched card in List = " + card);
						return card;
					}
				}
			}
		}

		// Case 2: input is a filtered store (DeckFilteredCardFileStore, etc.)
		if (input instanceof Iterable<?>) {
			for (Object o : (Iterable<?>) input) {
				if (o instanceof IMagicCard) {
					IMagicCard card = (IMagicCard) o;
					if (sid.equals(String.valueOf(card.getCardId()))) {
						// System.out.println("resolveElementFromId: matched card in store = " + card);
						return card;
					}
				}
			}
		}

		// System.out.println("resolveElementFromId: no match for id = " + sid);
		return null;
	}

	// ============================================================
	// JS → Java selection bridge
	// ============================================================
	private void hookSelectionBridge() {
		// System.out.println("REGISTERING javaSelectCard");
		new BrowserFunction(browser, "javaSelectCard") {
			@Override
			public Object function(Object[] args) {
				// System.out.println(">>> javaSelectCard CALLED, raw args = " +
				// java.util.Arrays.toString(args));

				if (args == null || args.length == 0) {
					System.out.println(">>> javaSelectCard: NO ARGS");
					return null;
				}

				String id = (args[0] != null) ? args[0].toString() : null;
				// System.out.println(">>> javaSelectCard: converted id = " + id);

				if (id == null || id.isEmpty()) {
					System.out.println(">>> javaSelectCard: NULL ID, ignoring");
					return null;
				}

				Object element = resolveElementFromId(id);
				// System.out.println(">>> javaSelectCard RESOLVED ELEMENT = " + element);

				if (element != null) {
					/*
					 * System.out.
					 * println(">>> javaSelectCard BEFORE setSelection, currentSelectionElement = "
					 * + currentSelectionElement);
					 */
					setSelection(new StructuredSelection(element), true);
					/*
					 * System.out.
					 * println(">>> javaSelectCard AFTER setSelection, currentSelectionElement = " +
					 * currentSelectionElement);
					 */
				}
				return null;
			}
		};
	}

	public void registerContextMenu(MenuManager menuMgr, IWorkbenchPartSite site) {
		// Create the SWT menu and attach it to the browser
		Menu menu = menuMgr.createContextMenu(browser);
		browser.setMenu(menu);

		// Register with Eclipse so actions work
		site.registerContextMenu(menuMgr, this);

		// Ensure Eclipse uses this viewer's selection
		site.setSelectionProvider(this);
	}

	private void fireSelectionChanged() {
		ISelection sel = getSelection();

		SelectionChangedEvent event = new SelectionChangedEvent(this, sel);
		for (ISelectionChangedListener l : new ArrayList<>(selectionChangedListeners)) {
			l.selectionChanged(event);
		}
	}

	// ============================================================
	// JS → Java double-click bridge
	// ============================================================
	private void hookDoubleClickBridge() {

		new BrowserFunction(browser, "javaDoubleClickCard") {
			@Override
			public Object function(Object[] args) {
				// System.out.println("javaDoubleClickCard CALLED, args=" +
				// java.util.Arrays.toString(args));
				if (args != null && args.length > 0) {
					Object id = args[0];
					Object element = resolveElementFromId(id);
					if (element != null) {
						fireDoubleClick(element);
					}
				}
				return null;
			}
		};
	}

	private void fireDoubleClick(Object element) {
		ISelection sel = new StructuredSelection(element);
		DoubleClickEvent event = new DoubleClickEvent(this, sel);
		for (IDoubleClickListener l : new ArrayList<>(doubleClickListeners)) {
			l.doubleClick(event);
		}
	}

	// ============================================================
	// ISelectionProvider
	// ============================================================

	@Override
	public void addSelectionChangedListener(ISelectionChangedListener listener) {
		if (!selectionChangedListeners.contains(listener))
			selectionChangedListeners.add(listener);
	}

	@Override
	public void removeSelectionChangedListener(ISelectionChangedListener listener) {
		selectionChangedListeners.remove(listener);
	}

	@Override
	public void setSelection(ISelection selection) {
		setSelection(selection, true);
	}

	// ============================================================
	// IMagicViewer implementation
	// ============================================================

	@Override
	public ISelectionProvider getSelectionProvider() {
		return this;
	}

	@Override
	public Viewer getViewer() {
		return this;
	}

	@Override
	public boolean hookContextMenu(MenuManager mgr) {
		// The view will call attachContextMenu() with the actual Menu
		return true;
	}

	@Override
	public void hookSortAction(IColumnSortAction sortAction) {
		// Not implemented yet
	}

	@Override
	public void setLinesVisible(boolean grid) {
		// Not applicable for browser-based gallery
	}

	@Override
	public void hookDragAndDrop() {
		// Not implemented yet
	}

	@Override
	public void hookContext(String id) {
		// Not implemented yet
	}

	@Override
	public void dispose() {
		if (!browser.isDisposed())
			browser.dispose();
	}

	@Override
	public void addDoubleClickListener(IDoubleClickListener listener) {
		if (!doubleClickListeners.contains(listener))
			doubleClickListeners.add(listener);
	}

	// ============================================================
	// Context menu forwarding
	// ============================================================

	public void attachContextMenu(Menu menu) {
		if (!browser.isDisposed())
			browser.setMenu(menu);
	}

	private static String cardsToJson(List<IMagicCard> cards) {
		StringBuilder sb = new StringBuilder();
		sb.append('[');
		boolean first = true;

		for (IMagicCard c : cards) {
			if (!first)
				sb.append(',');
			first = false;

			// Resolve image URL
			String img = "";
			try {
				java.net.URL u = com.reflexit.magiccards.core.sync.CardCache.getImageURL(c);
				if (u != null)
					img = u.toString();
			} catch (Exception e) {
				// ignore
			}

			// Resolve count (0 for non-physical cards)
			int count = 0;
			if (c instanceof com.reflexit.magiccards.core.model.IMagicCardPhysical) {
				count = ((com.reflexit.magiccards.core.model.IMagicCardPhysical) c).getCount();
			}

			sb.append('{');
			sb.append("\"id\":\"").append(escapeJson(String.valueOf(c.getCardId()))).append("\",");
			sb.append("\"name\":\"").append(escapeJson(c.getName())).append("\",");
			sb.append("\"set\":\"").append(escapeJson(c.getSet())).append("\",");
			sb.append("\"image\":\"").append(escapeJson(img)).append("\",");
			sb.append("\"count\":").append(count);
			sb.append('}');
		}

		sb.append(']');
		return sb.toString();
	}

	private static String escapeJson(String s) {
		if (s == null)
			return "";
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			char ch = s.charAt(i);
			switch (ch) {
			case '\\':
				sb.append("\\\\");
				break;
			case '"':
				sb.append("\\\"");
				break;
			case '\b':
				sb.append("\\b");
				break;
			case '\f':
				sb.append("\\f");
				break;
			case '\n':
				sb.append("\\n");
				break;
			case '\r':
				sb.append("\\r");
				break;
			case '\t':
				sb.append("\\t");
				break;
			default:
				if (ch < 0x20) {
					sb.append(String.format("\\u%04x", (int) ch));
				} else {
					sb.append(ch);
				}
			}
		}
		return sb.toString();
	}

}
