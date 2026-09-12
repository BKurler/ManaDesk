/*
 * Contributors:
 *     Rémi Dutil (2026) - dispose(): capture the active page's CACHED
 *                         selection (this view's own SelectionProviderIntermediate)
 *                         and hand it to that page via
 *                         persistSelectionBeforeShutdown() before pageGroup.dispose()
 *                         runs - the page's own LIVE widget selection is already
 *                         unreliable (reset with no SelectionChangedEvent) by the
 *                         time its own dispose() runs
 */
package com.reflexit.magiccards.ui.views;

import java.util.ArrayList;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.preference.IPersistentPreferenceStore;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.PartInitException;

import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.storage.IFilteredCardStore;
import com.reflexit.magiccards.ui.utils.SelectionProviderIntermediate;

public abstract class AbstractGroupPageCardsView extends AbstractCardsView {
	private Composite main;
	private ViewPageGroup pageGroup;
	private SelectionProviderIntermediate selectionProviderBridge = new SelectionProviderIntermediate();
	private MenuManager sharedContextMenuManager;

	public AbstractGroupPageCardsView() {
		pageGroup = createPageGroup();
		createPages();
	}

	protected ViewPageGroup createPageGroup() {
		return new FolderPageGroup(this::preActivate, this::postActivate);
	}

	protected abstract void createPages();

	public ViewPageGroup getPageGroup() {
		return pageGroup;
	}

	@Override
	public void init(IViewSite site) throws PartInitException {
		super.init(site);
		pageGroup.init(this);
	}

	@Override
	public void dispose() {
		// A debug trace proved the active page's OWN live widget selection is
		// already unreliable (empty, with no SelectionChangedEvent ever
		// announcing it) by the time that page's own dispose() runs - whatever
		// disposes the underlying SWT Table races ahead of the Java
		// IWorkbenchPart.dispose() cascade. This view's CACHED
		// SelectionProviderIntermediate bridge (getSelectionProvider(), NOT the
		// active page's own live one) is one frame earlier in that same
		// cascade and still reliably holds the real selection - hand it to the
		// active page explicitly, before pageGroup.dispose() gets a chance to
		// ask that page to read its own (by-then-stale) live selection.
		IViewPage activePage = pageGroup == null ? null : pageGroup.getActivePage();
		if (activePage instanceof AbstractMagicCardsListControl) {
			((AbstractMagicCardsListControl) activePage).persistSelectionBeforeShutdown(getSelectionProvider().getSelection());
		}
		pageGroup.dispose();
		super.dispose();
	}

	@Override
	protected ISelectionProvider getSelectionProvider() {
		return selectionProviderBridge;
	}

	@Override
	protected void createMainControl(Composite parent) {
		main = new Composite(parent, SWT.NONE);
		main.setLayoutData(new GridData(GridData.FILL_BOTH));
		main.setLayout(GridLayoutFactory.fillDefaults().create());
		// Pages
		pageGroup.createContent(main);
	}

	@Override
	protected void activate() {
		pageGroup.activate();
	}

	protected synchronized IViewPage getActivePage() {
		return pageGroup.getActivePage();
	}

	@Override
	public IFilteredCardStore getFilteredStore() {
		return ((IMagicCardListControl) getMainPage()).getFilteredStore();
	}

	@Override
	protected void hookContextMenu() {
		// register view menu
		registerContextMenu(getContextMenuManager());
	}

	public IAction getGroupAction() {
		return ((AbstractMagicCardsListControl) getActivePage()).getGroupAction();
	}

	public void setSelection(IStructuredSelection structuredSelection) {
		ArrayList<Object> l = new ArrayList<>();
		for (Object o : structuredSelection.toList()) {
			if (o instanceof MagicCard) {
				l.addAll(((MagicCard) o).getRealCards().getChildrenList());
				continue;
			} else if (o instanceof IMagicCard) {
				l.add(o);
			}
		}
		getSelectionProvider().setSelection(new StructuredSelection(l));
	}

	protected void preActivate(IViewPage activePage) {
		// clear menus and actions
		clearActionBars();
	}

	@Override
	protected void clearActionBars() {
		super.clearActionBars();
		// reset context menu
		sharedContextMenuManager = createContextMenuManager();
		pageGroup.getActivePage().setContextMenuManager(sharedContextMenuManager);
		hookContextMenu(); // register
	}

	protected void postActivate(IViewPage activePage) {
		// contribute this view extra actions
		contributeToActionBars();
		registerSelectionProvider();
	}

	@Override
	protected void registerSelectionProvider() {
		selectionProviderBridge.setSelectionProviderDelegate(pageGroup.getActivePage().getSelectionProvider());
		super.registerSelectionProvider();
	}

	private MenuManager getContextMenuManager() {
		return sharedContextMenuManager;
	}

	@Override
	public void reloadData() {
		if (getActivePage() != null)
			getActivePage().refresh();
	}

	public IPersistentPreferenceStore getLocalPreferenceStore() {
		IViewPage magicControl = getActivePage();
		if (magicControl instanceof IMagicCardListControl)
			return ((IMagicCardListControl) magicControl).getColumnsPreferenceStore();
		magicControl = getMainPage();
		if (magicControl instanceof IMagicCardListControl)
			return ((IMagicCardListControl) magicControl).getColumnsPreferenceStore();
		return null;
	}

	protected IViewPage getMainPage() {
		return pageGroup.getPage(0);
	}

	@Override
	public IPersistentPreferenceStore getFilterPreferenceStore() {
		if (getActivePage() instanceof IMagicCardListControl)
			return ((IMagicCardListControl) getActivePage()).getElementPreferenceStore();
		return null;
	}
}