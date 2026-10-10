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
package com.reflexit.magiccards_rcp;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.e4.ui.model.application.ui.MElementContainer;
import org.eclipse.e4.ui.model.application.ui.MUIElement;
import org.eclipse.e4.ui.model.application.ui.advanced.MPerspective;
import org.eclipse.e4.ui.model.application.ui.advanced.MPlaceholder;
import org.eclipse.e4.ui.model.application.ui.basic.MPartStack;
import org.eclipse.e4.ui.model.application.ui.basic.MWindow;
import org.eclipse.e4.ui.workbench.modeling.EModelService;
import org.eclipse.e4.ui.workbench.modeling.EPartService;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.nav.CardCollection;

import com.reflexit.magiccards.ui.PerspectiveFactoryMagic;
import com.reflexit.magiccards.ui.gallery.GalleryView;
import com.reflexit.magiccards.ui.views.MagicDbView;
import com.reflexit.magiccards.ui.views.card.RulingsView;
import com.reflexit.magiccards.ui.views.collector.CollectorView;
import com.reflexit.magiccards.ui.views.instances.InstancesView;
import com.reflexit.magiccards.ui.views.lib.DeckView;
import com.reflexit.magiccards.ui.views.lib.MyCardsView;
import com.reflexit.magiccards.ui.views.printings.PrintingsView;

/**
 * Keeps every ManaDesk view in its zone of the ManaDesk perspective:
 * <ul>
 * <li>decks / collections together (the "up" stack);</li>
 * <li>Rulings, Printings, Instances together (the "right" stack);</li>
 * <li>Scryfall Database, My Cards, Collector, Gallery, Proxier, Buyer together
 * (the "main" stack).</li>
 * </ul>
 * Eclipse opens a view in the place reserved for it in the saved layout; a view
 * with no reserved place (added to ManaDesk after that layout was first saved)
 * goes to whatever stack was used last - which looks random. {@link #apply}
 * gives each view a (closed) place in its zone when it has none, and moves back
 * a view found outside its zone, so every way of opening it (Window menu,
 * links, code) lands in the right place. It also reopens a deck tab saved
 * under the main collection's old id ("Collections/main").
 */
public final class ViewZones {
	private static final String BUYER_VIEW = "com.manadesk.monetization.BuyerView";
	/** By literal id: com.reflexit.magiccards.ui.views.proxier is not an exported package. */
	private static final String PROXIER_VIEW = "com.reflexit.magiccards.ui.views.proxier.ProxierView";

	private static final class Zone {
		final String stackId;
		/** A view always present in this zone, to find the stack when it has no id. */
		final String anchorViewId;
		final String[] viewIds;
		/** Multi-instance views (decks / collections): every "id:secondary" placeholder. */
		final boolean multiInstance;

		Zone(String stackId, String anchorViewId, boolean multiInstance, String... viewIds) {
			this.stackId = stackId;
			this.anchorViewId = anchorViewId;
			this.multiInstance = multiInstance;
			this.viewIds = viewIds;
		}
	}

	private static final Zone[] ZONES = {
			new Zone("up", null, true, DeckView.ID),
			new Zone("right", PrintingsView.ID, false, PrintingsView.ID, InstancesView.ID, RulingsView.ID),
			new Zone("main", MagicDbView.ID, false, MagicDbView.ID, MyCardsView.ID, CollectorView.ID, GalleryView.ID,
					PROXIER_VIEW, BUYER_VIEW) };

	private ViewZones() {
	}

	/** Puts the ManaDesk views of {@code window}'s active perspective in their zones. */
	public static void apply(IWorkbenchWindow window) {
		try {
			if (window == null || window.getActivePage() == null || window.getActivePage().getPerspective() == null
					|| !PerspectiveFactoryMagic.PERSPECTIVE_ID.equals(window.getActivePage().getPerspective().getId()))
				return;
			reopenRenamedMainTab(window.getActivePage());
			EModelService ms = window.getService(EModelService.class);
			EPartService ps = window.getService(EPartService.class);
			MWindow mwin = window.getService(MWindow.class);
			if (ms == null || ps == null || mwin == null)
				return;
			MPerspective persp = ms.getActivePerspective(mwin);
			if (persp == null)
				return;
			for (Zone zone : ZONES) {
				MPartStack stack = findStack(ms, persp, zone);
				if (stack == null)
					continue; // layout customized beyond recognition - leave it to the platform
				if (zone.multiInstance)
					applyMulti(ms, persp, stack, zone.viewIds[0]);
				else
					for (String viewId : zone.viewIds)
						applySingle(ms, ps, persp, stack, viewId);
			}
		} catch (RuntimeException e) {
			Activator.log(e);
		}
	}

	/**
	 * The main collection was renamed main -&gt; Main: a deck tab saved with the
	 * old id ("Collections/main") would open empty under the old name. Close it
	 * and open the main collection under its new id instead (once - the saved
	 * layout then holds the new id).
	 */
	private static void reopenRenamedMainTab(IWorkbenchPage page) {
		CardCollection main = DataManager.getInstance().getModelRoot().getDefaultLib();
		if (main == null)
			return;
		String newId = main.getLocation().getPath();
		for (IViewReference ref : page.getViewReferences()) {
			String secId = ref.getSecondaryId();
			if (!DeckView.ID.equals(ref.getId()) || secId == null || secId.equals(newId)
					|| !secId.equalsIgnoreCase(newId))
				continue;
			boolean wasActive = page.getActivePartReference() == ref;
			page.hideView(ref);
			try {
				if (page.findViewReference(DeckView.ID, newId) == null)
					page.showView(DeckView.ID, newId,
							wasActive ? IWorkbenchPage.VIEW_ACTIVATE : IWorkbenchPage.VIEW_CREATE);
			} catch (PartInitException e) {
				Activator.log(e);
			}
		}
	}

	private static MPartStack findStack(EModelService ms, MPerspective persp, Zone zone) {
		List<MPartStack> byId = ms.findElements(persp, zone.stackId, MPartStack.class);
		if (!byId.isEmpty())
			return byId.get(0);
		if (zone.anchorViewId != null) {
			for (MPlaceholder ph : ms.findElements(persp, zone.anchorViewId, MPlaceholder.class)) {
				if (parentOf(ph) instanceof MPartStack)
					return (MPartStack) parentOf(ph);
			}
		}
		return null;
	}

	/** One place per view: create it (closed) when missing, move it back when elsewhere. */
	private static void applySingle(EModelService ms, EPartService ps, MPerspective persp, MPartStack stack,
			String viewId) {
		if (PlatformUI.getWorkbench().getViewRegistry().find(viewId) == null)
			return; // not installed (e.g. the monetization plug-in)
		List<MPlaceholder> found = ms.findElements(persp, viewId, MPlaceholder.class);
		if (found.isEmpty()) {
			MPlaceholder ph = ps.createSharedPart(viewId, false);
			if (ph != null) {
				ph.setToBeRendered(false); // a closed view: opening it shows it here
				stack.getChildren().add(ph);
			}
			return;
		}
		// keep a single place: the open one if any, else the first
		MPlaceholder keep = found.get(0);
		for (MPlaceholder ph : found)
			if (ph.isToBeRendered()) {
				keep = ph;
				break;
			}
		for (MPlaceholder ph : found)
			if (ph != keep && !ph.isToBeRendered() && ph.getParent() != null)
				ph.getParent().getChildren().remove(ph);
		moveInto(ms, keep, stack);
	}

	/** Decks / collections: the "id:*" place, plus each open deck tab, in the zone. */
	private static void applyMulti(EModelService ms, MPerspective persp, MPartStack stack, String viewId) {
		List<MPlaceholder> all = new ArrayList<>();
		boolean hasWildcard = false;
		for (MPlaceholder ph : ms.findElements(persp, null, MPlaceholder.class)) {
			String id = ph.getElementId();
			if (id != null && (id.equals(viewId) || id.startsWith(viewId + ":"))) {
				all.add(ph);
				if (id.equals(viewId + ":*") && parentOf(ph) == stack)
					hasWildcard = true;
			}
		}
		for (MPlaceholder ph : all)
			moveInto(ms, ph, stack);
		if (!hasWildcard) {
			boolean anyWildcard = false;
			for (MPlaceholder ph : all)
				anyWildcard |= (viewId + ":*").equals(ph.getElementId());
			if (!anyWildcard) {
				MPlaceholder wild = ms.createModelElement(MPlaceholder.class);
				wild.setElementId(viewId + ":*");
				wild.setToBeRendered(false);
				stack.getChildren().add(wild);
			}
		}
	}

	/** The placeholder's container, typed loosely (its generic type is not MPartStack's). */
	private static Object parentOf(MPlaceholder ph) {
		return ph.getParent();
	}

	private static void moveInto(EModelService ms, MPlaceholder ph, MPartStack stack) {
		MElementContainer<MUIElement> parent = ph.getParent();
		Object p = parentOf(ph);
		if (p == null || p == stack || !(p instanceof MPartStack))
			return; // already there (or minimized / in a sash - leave it)
		if (parent.getSelectedElement() == ph) {
			// hand the old stack's selection to another open tab
			MUIElement next = null;
			for (MUIElement c : parent.getChildren())
				if (c != ph && c.isToBeRendered()) {
					next = c;
					break;
				}
			parent.setSelectedElement(next);
		}
		ms.move(ph, stack);
	}
}
