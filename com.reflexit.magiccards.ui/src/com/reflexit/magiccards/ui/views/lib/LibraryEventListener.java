/*
 * Contributors:
 *     Rémi Dutil (2026) - name this job after the tab's secondary id (its
 *                         deck/collection Location), not the generic
 *                         site.getRegisteredName() ("Deck" for every single
 *                         DeckView instance) - the startup splash surfaces
 *                         this job's name and a name shared by every tab was
 *                         useless for telling them apart
 */
package com.reflexit.magiccards.ui.views.lib;

import org.eclipse.ui.IViewSite;
import org.eclipse.ui.services.IDisposable;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.MagicLogger;
import com.reflexit.magiccards.core.model.events.CardEvent;
import com.reflexit.magiccards.core.model.events.ICardEventListener;
import com.reflexit.magiccards.ui.utils.WaitUtils;

public class LibraryEventListener implements ICardEventListener, IDisposable {
	private final DataManager DM = DataManager.getInstance();
	private ICardEventListener eventHandler;

	public void init(IViewSite site, Runnable postLoad) {
		String secondaryId = site.getSecondaryId();
		String label = secondaryId != null && !secondaryId.isEmpty() ? secondaryId : site.getRegisteredName();
		WaitUtils.scheduleJob("Initializing " + label, () -> {
			if (WaitUtils.waitForLibrary()) {
				DM.getLibraryCardStore().addListener(LibraryEventListener.this);
				DM.getModelRoot().addListener(LibraryEventListener.this);
			} else {
				MagicLogger.log("Timeout on waiting for db init. Listeners are not installed.");
			}
			if (postLoad != null)
				postLoad.run();
		});
	}

	public void setEventHandler(ICardEventListener eventHandler) {
		this.eventHandler = eventHandler;
	}

	@Override
	public void dispose() {
		DM.getLibraryCardStore().removeListener(this);
		DM.getModelRoot().removeListener(this);
	}

	@Override
	public void handleEvent(CardEvent event) {
		eventHandler.handleEvent(event);
	}
}
