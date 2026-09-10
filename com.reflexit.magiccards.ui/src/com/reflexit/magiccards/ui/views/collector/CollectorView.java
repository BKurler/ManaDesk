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
 */
package com.reflexit.magiccards.ui.views.collector;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.PartInitException;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.FilterField;
import com.reflexit.magiccards.ui.MagicUIActivator;
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
	private boolean onlyOwnFiltred;
	private CollectorListControl page;

	/**
	 * Whether the Collector view's completion % and value totals should include
	 * proxy copies. Off by default - proxies are cards you physically have but not
	 * the real thing.
	 */
	public static boolean isCountProxies() {
		return MagicUIActivator.getDefault().getPreferenceStore()
				.getBoolean(PreferenceConstants.COLLECTOR_COUNT_PROXIES);
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
	public String getHelpId() {
		return MagicUIActivator.helpId("viewcollector");
	}

	@Override
	protected void fillLocalPullDown(IMenuManager manager) {
		manager.add(refresh);
		manager.add(countProxies);
	}

	@Override
	protected void fillLocalToolBar(IToolBarManager manager) {
		// manager.add(onlyOwn);
		// onlyOwn.setChecked(isOnlyOwn());
		countProxies.setChecked(isCountProxies());
		manager.add(countProxies);
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
		this.countProxies = new Action("Count Proxies", IAction.AS_CHECK_BOX) {
			{
				setImageDescriptor(MagicUIActivator.getImageDescriptor("icons/obj16/check16.png"));
				setToolTipText("Include proxy copies in the completion % and value totals");
			}

			@Override
			public void run() {
				MagicUIActivator.getDefault().getPreferenceStore()
						.setValue(PreferenceConstants.COLLECTOR_COUNT_PROXIES, isChecked());
				reloadData();
			}
		};
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

	@Override
	public void createPartControl(Composite parent) {
		super.createPartControl(parent);
	}

	@Override
	public void init(IViewSite site) throws PartInitException {
		super.init(site);
	}

	@Override
	public void dispose() {
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