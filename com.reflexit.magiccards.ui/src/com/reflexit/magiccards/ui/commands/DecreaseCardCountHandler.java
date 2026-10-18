package com.reflexit.magiccards.ui.commands;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkbenchWindow;

import com.reflexit.magiccards.core.MagicException;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.OwnershipRules;
import com.reflexit.magiccards.core.model.storage.ICardStore;

/**
 * Decrease card number, if number is 1 remove the card
 */
public class DecreaseCardCountHandler extends AbstractCardCommandHandler {
	@Override
	public void run(IWorkbenchWindow window, IStructuredSelection iss) {
		ICardStore<IMagicCard> activeDeckHandler = getActiveDeckHandler(window);
		if (activeDeckHandler != null) {
			// an owned deck's cards only leave it by being moved out (nothing is changed yet)
			String veto = OwnershipRules.countChangeVeto(activeDeckHandler, 1, 0);
			if (veto != null)
				throw new MagicException(veto);
			List list = iss.toList();
			ArrayList<IMagicCard> toRemove = new ArrayList<IMagicCard>();
			for (Iterator iterator = list.iterator(); iterator.hasNext();) {
				IMagicCard magicCard = (IMagicCard) iterator.next();
				if (magicCard instanceof MagicCardPhysical) {
					MagicCardPhysical mc = (MagicCardPhysical) magicCard;
					int count = mc.getCount();
					if (count <= 1) {
						toRemove.add(new MagicCardPhysical(mc, mc.getLocation()));
					} else {
						getDataManager().setField(activeDeckHandler, mc, MagicCardField.COUNT, count - 1);
					}
				} else {
					MagicCardPhysical magicCardCopy = new MagicCardPhysical(magicCard, null);
					magicCardCopy.setCount(1);
					toRemove.add(magicCardCopy);
				}
			}
			getDataManager().remove(toRemove, activeDeckHandler);
		} else {
			MessageDialog.openError(window.getShell(), "Error", "No active deck");
		}
	}
}
