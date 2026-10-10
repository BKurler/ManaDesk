

/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - confirm before a copy turns virtual cards into
 *                         owned ones (OwnershipConfirmation)
 */

package com.reflexit.magiccards.ui.actions;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Collection;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.storage.ICardStore;
import com.reflexit.magiccards.ui.dialogs.OwnershipConfirmation;
import com.reflexit.magiccards.ui.dnd.CopySupport;
import com.reflexit.magiccards.ui.dnd.MagicCardTransfer;
import com.reflexit.magiccards.ui.utils.MagicAdapterFactory;

public class MagicPasteAction extends AbstractMagicAction {

// RD New paste implementation
	public Object readClipboard() {
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		try {
			String result = (String) clipboard.getData(DataFlavor.stringFlavor);
			return result;
		} catch (UnsupportedFlavorException | IOException e) {
			System.err.println("Error: " + e.getMessage());
			return "";
		}
	}

	public MagicPasteAction(ISelectionProvider provider) {
		super(provider, "Paste");
		setActionDefinitionId(ActionFactory.PASTE.getCommandId());
	}

	@Override
	public void run() {
		try {
			runPaste();
		} catch (Exception e) {
			MessageDialog.openError(getShell(), "Error", e.getMessage());
		}
	}

	public void runPaste() {
		MagicCardTransfer mt = MagicCardTransfer.getInstance();
		Object contents = mt.fromClipboard();
		if (contents instanceof Collection) {
			DataManager DM = DataManager.getInstance();
			ICardStore<IMagicCard> cardStore = getCardStore();
			if (cardStore == null)
				MessageDialog.openError(getShell(), "Error", "Cannot figure out where to copy");
			else {
				Collection resolved = DM.resolve((Collection) contents);
				if (OwnershipConfirmation.confirmCopy(getShell(), resolved, cardStore))
					DM.copyCards(resolved, cardStore);
			}
		} else if (contents == null) {
			Object retry = readClipboard();
			if (retry == null) {
				MessageDialog.openError(getShell(), "Error", "Nothing is in the clipboard");
			} else {
				Control fc = getDisplay().getFocusControl();
				CopySupport.runPaste(fc);
			}

		} else {
			Control fc = getDisplay().getFocusControl();
			CopySupport.runPaste(fc);
		}
	}

	private ICardStore<IMagicCard> getCardStore() {
		ISelectionService s = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getSelectionService();
		IStructuredSelection sel = (IStructuredSelection) s.getSelection();
		if (!sel.isEmpty()) {
			ICardStore cardStore = MagicAdapterFactory.adaptToICardStore(sel);
			if (cardStore != null)
				return cardStore;
		}
		IWorkbenchPart activePart = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getPartService()
				.getActivePart();
		sel = new StructuredSelection(activePart);
		if (!sel.isEmpty()) {
			ICardStore cardStore = MagicAdapterFactory.adaptToICardStore(sel);
			if (cardStore != null)
				return cardStore;
		}
		return null;
	}
}
