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
import org.eclipse.swt.SWT;
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
import com.reflexit.magiccards.ui.views.model.ExpandContentProvider;

public class BrowserGalleryViewer extends Viewer implements IMagicViewer, ISelectionProvider {

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

	public BrowserGalleryViewer(Composite parent, int style) {

		this.browser = new Browser(parent, style);
		browser.setLayoutData(new GridData(GridData.FILL_BOTH));

		System.out.println("BROWSER INSTANCE: " + browser.hashCode());

		browser.addListener(SWT.MouseDown, e -> {
			System.out.println("BROWSER MOUSEDOWN at " + e.x + "," + e.y);
		});

		hookSelectionBridge();
		hookDoubleClickBridge();

		this.getSelectionProvider().addSelectionChangedListener(event -> {
			System.out.println("SELECTION FIRED: " + event.getSelection());
		});

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
		inputChanged(input, old);
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
			currentSelectionElement = element;
			fireSelectionChanged();
		}
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
		System.out.println("===  HTML LOADED INTO BROWSER ===");
		if (browser.isDisposed())
			return;

		Object input = getInput();

		if (input == null) {
			System.out.println("GALLERY: input is null, skipping render");
			return;
		}

		System.out.println("GALLERY INPUT CLASS = " + input.getClass());
		if (input instanceof java.util.List) {
			System.out.println("GALLERY INPUT SIZE = " + ((java.util.List) input).size());
		}

		// Case 1: Expand grouped store into physical cards (root)
		if (input instanceof IFilteredCardStore) {
			IFilteredCardStore store = (IFilteredCardStore) input;

			ExpandContentProvider provider = new ExpandContentProvider(true);
			provider.inputChanged(null, null, store.getCardGroupRoot());
			Object[] expanded = provider.getElements(store.getCardGroupRoot());

			input = java.util.Arrays.asList(expanded);

			System.out.println("GALLERY EXPANDED SIZE = " + expanded.length);
		}

		// Case 2: Expand a list of CardGroup into physical cards (SplitGalleryViewer)
		if (input instanceof java.util.List) {
			java.util.List<?> list = (java.util.List<?>) input;
			if (!list.isEmpty() && list.get(0) instanceof ICardGroup) {
				java.util.List<Object> expandedAll = new java.util.ArrayList<>();
				ExpandContentProvider provider = new ExpandContentProvider(true);

				for (Object o : list) {
					ICardGroup group = (ICardGroup) o;
					provider.inputChanged(null, null, group);
					Object[] expanded = provider.getElements(group);
					java.util.Collections.addAll(expandedAll, expanded);
				}

				input = expandedAll;
				System.out.println("GALLERY EXPANDED FROM GROUP LIST, SIZE = " + expandedAll.size());
			}
		}

		String html = GalleryHtmlBuilder.buildHtml(input);

		browser.setUrl("about:blank");
		browser.setText(html, true);

		System.out.println(html);
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

		Object input = getInput();
		System.out.println("resolveElementFromId: input class = " + (input == null ? "null" : input.getClass()));

		// Case 1: input is a plain List<IMagicCard>
		if (input instanceof java.util.List<?>) {
			for (Object o : (java.util.List<?>) input) {
				if (o instanceof IMagicCard) {
					IMagicCard card = (IMagicCard) o;
					if (sid.equals(String.valueOf(card.getCardId()))) {
						System.out.println("resolveElementFromId: matched card in List = " + card);
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
						System.out.println("resolveElementFromId: matched card in store = " + card);
						return card;
					}
				}
			}
		}

		System.out.println("resolveElementFromId: no match for id = " + sid);
		return null;
	}

	// ============================================================
	// JS → Java selection bridge
	// ============================================================
	private void hookSelectionBridge() {
		System.out.println("REGISTERING javaSelectCard");
		new BrowserFunction(browser, "javaSelectCard") {
			@Override
			public Object function(Object[] args) {
				System.out.println(">>> javaSelectCard CALLED, args = " + java.util.Arrays.toString(args));
				if (args != null && args.length > 0) {
					Object id = args[0];
					Object element = resolveElementFromId(id);
					System.out.println(">>> javaSelectCard RESOLVED ELEMENT = " + element);

					if (element != null) {
						System.out.println(">>> javaSelectCard BEFORE setSelection, currentSelectionElement = "
								+ currentSelectionElement);
						setSelection(new StructuredSelection(element), true);
						System.out.println(">>> javaSelectCard AFTER setSelection, currentSelectionElement = "
								+ currentSelectionElement);
					}
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
		System.out.println("fireSelectionChanged() with selection = " + sel);
		SelectionChangedEvent event = new SelectionChangedEvent(this, sel);
		for (ISelectionChangedListener l : new ArrayList<>(selectionChangedListeners)) {
			l.selectionChanged(event);
		}
	}

	// ============================================================
	// JS → Java double-click bridge
	// ============================================================
	private void hookDoubleClickBridge() {
		System.out.println("REGISTERING javaDoubleClickCard");
		new BrowserFunction(browser, "javaDoubleClickCard") {
			@Override
			public Object function(Object[] args) {
				System.out.println("javaDoubleClickCard CALLED, args=" + java.util.Arrays.toString(args));
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
}
