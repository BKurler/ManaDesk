/*******************************************************************************
 * Copyright (c) 2008 Alena Laskavaia.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Alena Laskavaia - initial API and implementation
 *******************************************************************************/

/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk; dropped the per-set "Update cards
 *                         of selected set(s)" action (one full "Update Card
 *                         Database" now covers it)
 *     Rémi Dutil (2026) - "Count proxies" toggle (completion % / value totals)
 *     Rémi Dutil (2026) - allowsMultipleFinishesPerRow() overridden to true:
 *                         Collector browses whole printings from the Scryfall
 *                         database, which can legitimately offer several
 *                         finishes at once, so the Finish filter's "And"
 *                         (exact match) mode is meaningful here
 *     Rémi Dutil (2026) - "Count Finishes Separately" toggle: normally owning
 *                         any one finish of a printing (foil or nonfoil)
 *                         already marks it complete; this toggle instead
 *                         treats each finish as its own slot, mirroring
 *                         "Count Proxies"' toggle-Action pattern exactly
 *     Rémi Dutil (2026) - renderLetterIcon(): "Count Proxies" and "Count
 *                         Finishes Separately" both used the same generic
 *                         icons/obj16/check16.png, indistinguishable at a
 *                         glance in the toolbar - now bold "P"/"F" letter
 *                         icons instead, same technique QuickFilterControl
 *                         already uses for its own O/V toggle buttons
 *     Rémi Dutil (2026) - P and F toolbar toggles sit just left of the gear
 *     Rémi Dutil (2026) - follows P / F changed from the preference page
 *                         (Restore Defaults included): toggles and counts update
 *     Rémi Dutil (2026) - "D" toggle (digital cards, while enabled): paper or digital, never both - the
 *                         Card Kind filter and the lists whose copies count as owned (DataManager.OwnedKind)
 */
package com.reflexit.magiccards.ui.views.collector;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.PartInitException;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.DigitalSupport;
import com.reflexit.magiccards.core.model.FilterField;
import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.actions.ShowPreferencesAction;
import com.reflexit.magiccards.ui.preferences.CollectorViewPreferencePage;
import com.reflexit.magiccards.ui.preferences.PreferenceConstants;
import com.reflexit.magiccards.ui.views.IViewPage;
import com.reflexit.magiccards.ui.views.ViewPageContribution;
import com.reflexit.magiccards.ui.views.lib.AbstractMyCardsView;

/**
 * Shows sets and how many cards collected per set
 *
 */
public class CollectorView extends AbstractMyCardsView {
	public static final String ID = CollectorView.class.getName();
	private Action refresh;
	private Action onlyOwn;
	private Action countProxies;
	private Action countFinishesSeparately;
	private Action digitalMode;
	private Image digitalIcon;
	private boolean onlyOwnFiltred;
	private CollectorListControl page;
	private Image proxyIcon;
	private Image finishIcon;

	/**
	 * Whether the Collector view's completion % and value totals should include
	 * proxy copies. Off by default - proxies are cards you physically have but not
	 * the real thing.
	 */
	public static boolean isCountProxies() {
		return MagicUIActivator.getDefault().getPreferenceStore()
				.getBoolean(PreferenceConstants.COLLECTOR_COUNT_PROXIES);
	}

	/**
	 * Whether Collector's completion % / Own Unique should treat each finish
	 * (nonfoil/foil/etched) of a printing as its own slot to complete. Off by
	 * default - owning any one finish already completes that printing.
	 */
	public static boolean isCountFinishesSeparately() {
		return MagicUIActivator.getDefault().getPreferenceStore()
				.getBoolean(PreferenceConstants.COLLECTOR_COUNT_FINISHES_SEPARATELY);
	}

	@Override
	protected void createPages() {
		page = new CollectorListControl();
		getPageGroup().add(new ViewPageContribution("", "Main", null, page));
	}

	@Override
	protected IViewPage getActivePage() {
		return page;
	}

	@Override
	protected boolean allowsCardKindFilter() {
		return false; // paper or digital, chosen with the D button
	}

	@Override
	protected boolean allowsMultipleFinishesPerRow() {
		// Collector browses whole printings from the Scryfall database, not
		// just owned copies - a printing can legitimately offer several
		// finishes at once (e.g. regular + foil), unlike a My Cards row.
		return true;
	}

	@Override
	public String getHelpId() {
		return MagicUIActivator.helpId("viewcollector");
	}

	@Override
	protected void fillLocalPullDown(IMenuManager manager) {
		manager.add(refresh);
		manager.add(countProxies);
		manager.add(countFinishesSeparately);
	}

	@Override
	protected void fillLocalToolBar(IToolBarManager manager) {
		// manager.add(onlyOwn);
		// onlyOwn.setChecked(isOnlyOwn());
		countProxies.setChecked(isCountProxies());
		if (digitalMode != null)
			digitalMode.setChecked(isDigitalMode());
		countFinishesSeparately.setChecked(isCountFinishesSeparately());
		// P and F go just left of the list's Preferences (gear) button, which
		// the list control has usually contributed already
		if (manager.find(ShowPreferencesAction.ID) != null) {
			manager.insertBefore(ShowPreferencesAction.ID, countProxies);
			manager.insertBefore(ShowPreferencesAction.ID, countFinishesSeparately);
			if (digitalMode != null)
				manager.insertBefore(ShowPreferencesAction.ID, digitalMode);
		} else {
			manager.add(countProxies);
			manager.add(countFinishesSeparately);
			if (digitalMode != null)
				manager.add(digitalMode);
		}
		super.fillLocalToolBar(manager);
	}

	@Override
	protected void fillEditingContextActions(IMenuManager manager) {
		// The Collector view is a read-only window over the whole database: copy /
		// move / split / edit have nothing to act on here.
	}

	@Override
	protected void makeActions() {
		super.makeActions();
		this.refresh = new Action("Refresh", SWT.NONE) {
			{
				setImageDescriptor(MagicUIActivator.getImageDescriptor("icons/clcl16/refresh.gif"));
			}

			@Override
			public void run() {
				DataManager.getInstance().reconcile();
				reloadData();
			}
		};
		this.proxyIcon = renderLetterIcon("P", new RGB(190, 110, 30));
		this.finishIcon = renderLetterIcon("F", new RGB(45, 130, 160));
		this.countProxies = new Action("Count Proxies", IAction.AS_CHECK_BOX) {
			{
				setImageDescriptor(ImageDescriptor.createFromImage(proxyIcon));
				setToolTipText("Include proxy copies in the completion % and value totals");
			}

			@Override
			public void run() {
				MagicUIActivator.getDefault().getPreferenceStore()
						.setValue(PreferenceConstants.COLLECTOR_COUNT_PROXIES, isChecked());
				reloadData();
			}
		};
		this.countFinishesSeparately = new Action("Count Finishes Separately", IAction.AS_CHECK_BOX) {
			{
				setImageDescriptor(ImageDescriptor.createFromImage(finishIcon));
				setToolTipText("Treat each finish (nonfoil/foil/etched) of a printing as its own"
						+ " slot to complete, instead of any one finish completing the whole printing");
			}

			@Override
			public void run() {
				MagicUIActivator.getDefault().getPreferenceStore()
						.setValue(PreferenceConstants.COLLECTOR_COUNT_FINISHES_SEPARATELY, isChecked());
				reloadData();
			}
		};
		if (DigitalSupport.isEnabled()) {
			this.digitalIcon = renderLetterIcon("D", new RGB(110, 70, 170));
			this.digitalMode = new Action("Digital cards", IAction.AS_CHECK_BOX) {
				{
					setImageDescriptor(ImageDescriptor.createFromImage(digitalIcon));
					setToolTipText("Collect the digital cards (MTGO / Arena) instead of the paper ones: the sets"
							+ " and the totals follow, and only your digital decks and collections count");
				}

				@Override
				public void run() {
					MagicUIActivator.getDefault().getPreferenceStore().setValue(PreferenceConstants.COLLECTOR_DIGITAL,
							isChecked());
					applyKind();
					reloadData();
				}
			};
		}
		this.onlyOwn = new Action("Show Only Own", IAction.AS_CHECK_BOX) {
			{
				setImageDescriptor(MagicUIActivator.getImageDescriptor("icons/obj16/check16.png"));
			}

			@Override
			public void run() {
				triggerOnlyOwn(!isOnlyOwn());
			}
		};
	}

	/** A crisp bold letter on a transparent 16x16 background - avoids two
	 *  toolbar toggles ("Count Proxies" / "Count Finishes Separately") both
	 *  showing the same generic checkmark icon and being indistinguishable
	 *  at a glance. Built once per view instance (not shared/cached via the
	 *  plugin's image registry - {@link ImageDescriptor#createFromImage}
	 *  hands the image itself to the toolbar's own resource management, so
	 *  ownership is simplest kept per-view, disposed in {@link #dispose()},
	 *  the same lifecycle QuickFilterControl's own O/V icons already use). */
	private Image renderLetterIcon(String letter, RGB rgb) {
		Display display = Display.getDefault();
		RGB sentinel = new RGB(255, 0, 255);
		Image img = new Image(display, 16, 16);
		GC gc = new GC(img);
		Font bold = null;
		try {
			Color bg = new Color(display, sentinel);
			gc.setBackground(bg);
			gc.fillRectangle(0, 0, 16, 16);
			bg.dispose();
			gc.setAntialias(SWT.ON);
			gc.setTextAntialias(SWT.ON);
			FontData[] fds = display.getSystemFont().getFontData();
			for (FontData fd : fds) {
				fd.setStyle(SWT.BOLD);
				fd.setHeight(10);
			}
			bold = new Font(display, fds);
			gc.setFont(bold);
			Color fg = new Color(display, rgb);
			gc.setForeground(fg);
			Point extent = gc.textExtent(letter);
			gc.drawText(letter, (16 - extent.x) / 2, (16 - extent.y) / 2, true);
			fg.dispose();
		} finally {
			gc.dispose();
			if (bold != null)
				bold.dispose();
		}
		ImageData data = img.getImageData();
		img.dispose();
		data.transparentPixel = data.palette.getPixel(sentinel);
		return new Image(display, data);
	}

	protected boolean isOnlyOwn() {
		return onlyOwnFiltred;
	}

	public void triggerOnlyOwn(boolean mode) {
		onlyOwnFiltred = mode;
		onlyOwn.setChecked(mode);
		if (!mode)
			onlyOwn.setToolTipText("Check to show only own cards");
		else
			onlyOwn.setToolTipText("Uncheck to show cards in database which you don't own");
		String id = FilterField.OWNERSHIP.getPrefConstant();
		IPreferenceStore store = getLocalPreferenceStore();
		store.putValue(id, onlyOwnFiltred ? "true" : "");
		reloadData();
	}

	@Override
	protected void removeSelected() {
		// System.err.println("Remove attempt");
	}

	/** P / F changed elsewhere (the View: Collector page) - sync the buttons and recount. */
	private final IPropertyChangeListener countOptionsListener = event -> {
		String p = event.getProperty();
		if (!PreferenceConstants.COLLECTOR_COUNT_PROXIES.equals(p)
				&& !PreferenceConstants.COLLECTOR_COUNT_FINISHES_SEPARATELY.equals(p))
			return;
		Display.getDefault().asyncExec(() -> {
			if (countProxies == null || getPartControl() == null || getPartControl().isDisposed())
				return;
			boolean changed = countProxies.isChecked() != isCountProxies()
					|| countFinishesSeparately.isChecked() != isCountFinishesSeparately();
			countProxies.setChecked(isCountProxies());
			countFinishesSeparately.setChecked(isCountFinishesSeparately());
			if (changed) // the buttons themselves already reloaded
				reloadData();
		});
	};

	/** True when Collector shows and counts the digital cards (only possible while they are enabled). */
	public static boolean isDigitalMode() {
		return DigitalSupport.isEnabled()
				&& MagicUIActivator.getDefault().getPreferenceStore().getBoolean(PreferenceConstants.COLLECTOR_DIGITAL);
	}

	/**
	 * Paper or digital, never both: the list (CollectorListControl#adjustFilterMap) picks the cards - and so the
	 * sets - and only the copies in decks / collections of that kind count as owned. Nothing changes while the
	 * digital cards are not enabled.
	 */
	private void applyKind() {
		DataManager dm = DataManager.getInstance();
		if (!DigitalSupport.isEnabled()) {
			dm.setOwnedKind(DataManager.OwnedKind.ALL);
			return;
		}
		boolean digital = isDigitalMode();
		dm.setOwnedKind(digital ? DataManager.OwnedKind.DIGITAL : DataManager.OwnedKind.PAPER);
	}

	@Override
	public void createPartControl(Composite parent) {
		applyKind();
		super.createPartControl(parent);
		MagicUIActivator.getDefault().getPreferenceStore().addPropertyChangeListener(countOptionsListener);
	}

	@Override
	public void init(IViewSite site) throws PartInitException {
		super.init(site);
	}

	@Override
	public void dispose() {
		MagicUIActivator.getDefault().getPreferenceStore().removePropertyChangeListener(countOptionsListener);
		if (proxyIcon != null)
			proxyIcon.dispose();
		if (finishIcon != null)
			finishIcon.dispose();
		if (digitalIcon != null)
			digitalIcon.dispose();
		// the owned counts of the other views go back to every list
		DataManager.getInstance().setOwnedKind(DataManager.OwnedKind.ALL);
		super.dispose();
	}

	@Override
	protected void loadInitialInBackground() {
		reloadData();
	}

	@Override
	protected String getPreferencePageId() {
		return CollectorViewPreferencePage.class.getName();
	}

	@Override
	public String getId() {
		return ID;
	}
}