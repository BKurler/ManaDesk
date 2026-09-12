/*
 * Contributors:
 *     Rémi Dutil (2026) - lazy page materialization: only the page shown right
 *                         away (index 0) builds its control eagerly; every
 *                         other deck-tab page (Info/Draw/Mana/.../Export) is
 *                         built on first selection - a startup trace measured
 *                         ~600ms/tab spent building analyzer pages nobody was
 *                         viewing
 */
package com.reflexit.magiccards.ui.views;

import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

public class FolderPageGroup extends ViewPageGroup {
	private CTabFolder folder;

	public FolderPageGroup(Consumer<IViewPage> beforeActivate, Consumer<IViewPage> afterActivate) {
		super(beforeActivate, afterActivate);
	}

	@Override
	public void createContent(Composite parent) {
		folder = new CTabFolder(parent, SWT.BOTTOM);
		folder.setLayoutData(new GridData(GridData.FILL_BOTH));
		// Pages
		super.createContent(folder);
		// Common
		folder.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				updateActivePage();
			}
		});
		folder.setSelection(0);
		folder.setSimple(false);
		Display display = folder.getDisplay();
		// folder.setBackground(display.getSystemColor(SWT.COLOR_TITLE_BACKGROUND));
		folder.setBackground(new Color[] { display.getSystemColor(SWT.COLOR_TITLE_BACKGROUND),
				display.getSystemColor(SWT.COLOR_WHITE) }, new int[] { 50 });
	}

	protected void updateActivePage() {
		CTabItem sel = folder.getSelection();
		if (sel.isDisposed())
			return;
		ensureMaterialized(folder.indexOf(sel), sel);
		IViewPage activePage = (IViewPage) sel.getData();
		activate(activePage);
	}

	@Override
	public boolean activate(int page) {
		ensureMaterialized(page, folder.getItem(page));
		folder.setSelection(page);
		return super.activate(page);
	}

	/**
	 * Every deck/collection page contribution (Cards, Info, Draw, Mana, Types,
	 * Creatures, Colors, Abilities, Legality, Accessories, Export...) used to get
	 * its full SWT control built the instant the tab opened, whether the user
	 * ever looked at it or not - a startup trace measured ~600ms per deck tab
	 * spent building analyzer pages nobody was viewing (worse yet, multiplied by
	 * every tab the app force-restores for its icon at startup). Only the page
	 * that is about to actually be shown (index 0, right away; any other page,
	 * the first time its CTabItem is selected or {@link #activate(int)} is
	 * called) pays that cost now - see {@link ViewPageContribution#isContentCreated()}.
	 */
	private void ensureMaterialized(int index, CTabItem item) {
		if (index < 0 || item == null || item.isDisposed())
			return;
		ViewPageContribution vc = getPages().get(index);
		if (vc.isContentCreated())
			return;
		IViewPage page = vc.getViewPage();
		page.createContents(folder);
		item.setControl(page.getControl());
		vc.markContentCreated();
	}

	@Override
	protected void createPageContent(ViewPageContribution vc, Composite parent) {
		final IViewPage page = vc.getViewPage();
		final CTabItem item = new CTabItem(folder, SWT.CLOSE);
		item.setText(vc.getName());
		item.setShowClose(false);
		page.init(getViewPart());
		item.setData(page);
		// Page 0 ("Cards") is activated immediately below in createContent()
		// (folder.setSelection(0)) and by ViewPageGroup.activate() defaulting
		// activePageIndex to 0 - it needs its control now. Every other page is
		// left un-built until ensureMaterialized() above is reached.
		if (getPages().indexOf(vc) == 0) {
			page.createContents(folder);
			item.setControl(page.getControl());
			vc.markContentCreated();
		}
	}
}
