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
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;

import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.ui.views.IColumnSortAction;
import com.reflexit.magiccards.ui.views.IMagicViewer;

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

		// Disable native browser menu
		browser.addListener(SWT.MenuDetect, e -> e.doit = false);

		// Default content provider
		this.contentProvider = new IStructuredContentProvider() {
			@Override
			public Object[] getElements(Object inputElement) {
				if (inputElement instanceof List<?>) {
					return ((List<?>) inputElement).toArray();
				}
				if (inputElement == null)
					return new Object[0];
				return new Object[] { inputElement };
			}

			@Override
			public void dispose() {
			}

			@Override
			public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			}
		};

		hookSelectionBridge();
		hookDoubleClickBridge();
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
		if (browser.isDisposed())
			return;

		Object input = getInput(); // use the viewer input directly
		String html = GalleryHtmlBuilder.buildHtml(input); // old, working contract
		browser.setText(html);
	}

	@Override
	public void refresh() {
		render();
	}

	private Object resolveElementFromId(Object id) {
		Object input = getInput();
		if (input instanceof List<?>) {
			for (Object o : (List<?>) input) {
				if (o instanceof IMagicCard) {
					IMagicCard card = (IMagicCard) o;
					if (String.valueOf(card.getCardId()).equals(String.valueOf(id))) {
						return card;
					}
				}
			}
		}
		return null;
	}

	// ============================================================
	// JS → Java selection bridge
	// ============================================================

	private void hookSelectionBridge() {
		new BrowserFunction(browser, "javaSelectCard") {
			@Override
			public Object function(Object[] args) {
				if (args != null && args.length > 0) {
					Object id = args[0];
					Object element = resolveElementFromId(id);

					if (element != null) {
						currentSelectionElement = element;
						fireSelectionChanged();
					}
				}
				return null;
			}
		};
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
