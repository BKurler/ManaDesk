package com.reflexit.magiccards.ui.gallery;

import java.util.Collections;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Widget;

import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.ui.views.IColumnSortAction;
import com.reflexit.magiccards.ui.views.IMagicViewer;

public class BrowserGalleryViewer extends StructuredViewer implements IMagicViewer {

	private final Browser browser;
	private Object lastSelection;

	private final Action dummyAction = new Action() {
	};

	public BrowserGalleryViewer(Composite parent, int style) {
		browser = new Browser(parent, style);

		setContentProvider(new IStructuredContentProvider() {
			@Override
			public Object[] getElements(Object inputElement) {
				if (inputElement instanceof List<?>) {
					return ((List<?>) inputElement).toArray();
				}
				return new Object[] { inputElement };
			}

			@Override
			public void dispose() {
			}

			@Override
			public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			}
		});

		hookSelectionBridge();
	}

	@Override
	public Control getControl() {
		return browser;
	}

	@Override
	protected void inputChanged(Object input, Object oldInput) {
		super.inputChanged(input, oldInput);
		render();
	}

	@Override
	protected void internalRefresh(Object element) {
		render();
	}

	@Override
	public void reveal(Object element) {
		// no-op
	}

	private void render() {
		Object input = getInput();
		String html = GalleryHtmlBuilder.buildHtml(input);
		browser.setText(html);
	}

	// ============================================================
	// SELECTION BRIDGE (JS → Java → JFace → Workbench)
	// ============================================================

	private void hookSelectionBridge() {
		new BrowserFunction(browser, "javaSelectCard") {
			@Override
			public Object function(Object[] args) {
				if (args.length == 0)
					return null;

				Object id = args[0];
				Object element = resolveElementFromId(id);

				if (element != null) {
					lastSelection = element;
					fireSelectionChangedFromBrowser(element);
				}

				return null;
			}
		};
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

	private void fireSelectionChangedFromBrowser(Object element) {
		ISelection sel = new StructuredSelection(element);

		// Update StructuredViewer’s internal selection
		super.setSelection(sel, true);

		// Notify JFace listeners
		fireSelectionChanged(new SelectionChangedEvent(this, sel));
	}

	// ============================================================
	// StructuredViewer required methods
	// ============================================================

	@Override
	protected Widget doFindItem(Object element) {
		return null;
	}

	@Override
	protected Widget doFindInputItem(Object element) {
		return null;
	}

	@Override
	protected void doUpdateItem(Widget item, Object element, boolean fullMap) {
		// no per-item widgets
	}

	@Override
	@SuppressWarnings("rawtypes")
	protected List getSelectionFromWidget() {
		if (lastSelection == null)
			return Collections.emptyList();
		return Collections.singletonList(lastSelection);
	}

	@Override
	@SuppressWarnings("rawtypes")
	protected void setSelectionToWidget(List list, boolean reveal) {
		// TODO: highlight in DOM later
	}

	@Override
	public void refresh() {
		super.refresh();
	}

	// ============================================================
	// IMagicViewer
	// ============================================================

	@Override
	public ISelectionProvider getSelectionProvider() {
		return this; // StructuredViewer implements ISelectionProvider
	}

	@Override
	public Viewer getViewer() {
		return this;
	}

	@Override
	public void hookSortAction(IColumnSortAction sortAction) {
	}

	@Override
	public void setLinesVisible(boolean grid) {
	}

	@Override
	public void hookDragAndDrop() {
	}

	@Override
	public void hookContext(String id) {
	}

	@Override
	public boolean hookContextMenu(MenuManager mgr) {
		return false;
	}

	@Override
	public void dispose() {
	}

	public Action getZoomInAction() {
		return dummyAction;
	}

	public Action getZoomOutAction() {
		return dummyAction;
	}

	public Action getToggleGroupAction() {
		return dummyAction;
	}

	public Action getSortAction() {
		return dummyAction;
	}
}
