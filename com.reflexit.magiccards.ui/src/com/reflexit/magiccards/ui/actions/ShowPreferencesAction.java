/*
 * Contributors:
 *     Rémi Dutil (2026) - id is now resolved lazily (Supplier<String>)
 *                         instead of captured once at construction time - a
 *                         Deck/Collection list control builds this action
 *                         before its underlying CardCollection has loaded,
 *                         so the old eager capture always froze on whichever
 *                         page getPreferencePageId() happened to resolve to
 *                         at that early point (in practice, always the same
 *                         one), making the "Preferences..." button open the
 *                         same page and edit the same store for every Deck
 *                         AND every Collection, however DeckView#
 *                         getPreferencePageId() was implemented
 */
package com.reflexit.magiccards.ui.actions;

import java.util.function.Supplier;

import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.ui.dialogs.PreferencesUtil;

public class ShowPreferencesAction extends ImageAction {
	private final Supplier<String> idSupplier;

	public ShowPreferencesAction(Supplier<String> idSupplier) {
		super("Preferences...", "icons/clcl16/gear.png", "Opens UI preferences");
		this.idSupplier = idSupplier;
		// setId(ActionFactory.DELETE.getId());
		// setActionDefinitionId("org.eclipse.ui.edit.findReplace");
	}

	@Override
	public void run() {
		String id = getPreferencePageId();
		if (id != null) {
			before();
			PreferenceDialog dialog = PreferencesUtil.createPreferenceDialogOn(getShell(), id, new String[] { id },
					null);
			if (dialog.open() == Window.OK) {
				after();
			}
		}
	}

	public String getPreferencePageId() {
		return idSupplier.get();
	}

	public void after() {
		// hook
	}

	public void before() {
		// hook
	}
}
