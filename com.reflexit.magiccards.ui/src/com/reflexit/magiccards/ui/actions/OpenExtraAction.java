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
package com.reflexit.magiccards.ui.actions;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.StructuredSelection;

import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.nav.CardCollection;
import com.reflexit.magiccards.core.model.nav.CollectionsContainer;
import com.reflexit.magiccards.ui.views.lib.DeckView;

/**
 * Opens the deck's "Extra" list - a real card list, exactly like the
 * sideboard, holding the token / emblem / marker cards the deck needs at the
 * table. The button is only enabled once that list already exists (see
 * {@link DeckView#updatePartName()}); it never creates it.
 */
public class OpenExtraAction extends ImageAction {
	private CardCollection deck;

	public OpenExtraAction() {
		super("Open Extra", "icons/obj16/open_extra16.png", IAction.AS_PUSH_BUTTON);
		setToolTipText("Open the deck's extra list (tokens, emblems, markers)");
	}

	public OpenExtraAction(CardCollection deck) {
		this();
		this.deck = deck;
	}

	@Override
	public void run() {
		if (deck == null)
			return;
		Location main = deck.getLocation().toMainDeck();
		final Location extra = main.toExtra();
		if (main.equals(extra))
			return;
		CollectionsContainer parent = (CollectionsContainer) deck.getParent();
		if (!parent.contains(extra))
			return; // doesn't exist yet - the button is disabled for this case, never create it here
		CardCollection a = (CardCollection) parent.findChield(extra);
		DeckView.openCollection(a, new StructuredSelection());
	}

	public void setDeck(CardCollection deck) {
		this.deck = deck;
	}
}
