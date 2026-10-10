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
package com.reflexit.magiccards.ui.dialogs;

import java.util.Collection;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.OwnershipRules;
import com.reflexit.magiccards.core.model.nav.CardElement;
import com.reflexit.magiccards.core.model.storage.ICardStore;

/**
 * Moving or copying a virtual card (a wished card, a proxy to print) into a
 * non-virtual deck or collection gives an owned card - it has been bought or
 * printed. That is the normal way to turn a Wishlist/To Print card into an owned
 * one, so it is allowed, but only after the user confirms.
 */
public final class OwnershipConfirmation {
	private OwnershipConfirmation() {
	}

	/** {@link #confirm} for a move: the virtual cards themselves become Own. */
	public static boolean confirmMove(Shell shell, Collection<?> cards, ICardStore<?> dest) {
		return confirm(shell, cards, dest, "moved");
	}

	/** {@link #confirm} for a copy: the copies of the virtual cards are Own. */
	public static boolean confirmCopy(Shell shell, Collection<?> cards, ICardStore<?> dest) {
		return confirm(shell, cards, dest, "copied");
	}

	/**
	 * @return true when the operation may go ahead: no virtual card goes into a
	 *         non-virtual list, or the user confirmed
	 */
	private static boolean confirm(Shell shell, Collection<?> cards, ICardStore<?> dest, String verb) {
		if (cards == null || dest == null)
			return true;
		int copies = 0;
		for (Object o : DataManager.expandGroups(cards)) {
			if (o instanceof MagicCardPhysical && OwnershipRules.becomesOwnOnMove((MagicCardPhysical) o, dest))
				copies += ((MagicCardPhysical) o).getCount();
		}
		if (copies == 0)
			return true;
		return MessageDialog.openConfirm(shell, "Virtual Cards Become Own",
				copies + " virtual card" + (copies == 1 ? "" : "s") + " will become Own when " + verb + " to "
						+ displayName(dest) + " (bought / printed).\n\nContinue?");
	}

	/** The destination as the navigator shows it ("main", "My Deck"...) - the
	 *  name stored inside the file is often empty. */
	private static String displayName(ICardStore<?> dest) {
		try {
			CardElement el = DataManager.getInstance().getModelRoot().getCardElement(dest.getLocation());
			if (el != null && el.getName() != null && !el.getName().isEmpty())
				return "\"" + el.getName() + "\"";
		} catch (RuntimeException e) {
			// fall through to the stored name
		}
		String n = dest.getName();
		return n != null && !n.trim().isEmpty() ? "\"" + n + "\"" : "the destination";
	}
}
