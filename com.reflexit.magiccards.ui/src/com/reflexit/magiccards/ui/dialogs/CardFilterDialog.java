/*
 * Contributors:
 *     Rémi Dutil (2026) - allowsMultipleFinishesPerRow(): whether the Finish
 *                         filter's "And" (exact match) mode is meaningful for
 *                         this dialog's rows - true by default (this base
 *                         class backs the DB-browsing views, whose rows can
 *                         legitimately offer several finishes at once);
 *                         DeckFilterDialog turns it back off
 */
package com.reflexit.magiccards.ui.dialogs;

import org.eclipse.jface.preference.IPreferenceNode;
import org.eclipse.jface.preference.IPreferencePageContainer;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.preference.PreferenceManager;
import org.eclipse.jface.preference.PreferenceNode;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.widgets.Shell;

import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.preferences.AbilitiesFilterPreferencePage;
import com.reflexit.magiccards.ui.preferences.AbstractFilterPreferencePage;
import com.reflexit.magiccards.ui.preferences.BasicFilterPreferencePage;
import com.reflexit.magiccards.ui.preferences.EditionsFilterPreferencePage;
import com.reflexit.magiccards.ui.preferences.SaveFilterPreferencePage;

public class CardFilterDialog extends PreferenceDialog implements IPreferencePageContainer {
	private IPreferenceStore store;
	// Default true: the base dialog backs the DB-browsing views (Scryfall
	// database, Printings, Instances), where a single row can legitimately
	// offer several finishes at once, so the Finish filter's "And" mode is
	// meaningful there. Subclasses for single-valued, owned-copy-only views
	// (Deck/Collection, My Cards) turn this back off.
	private boolean multiFinish = true;

	public CardFilterDialog(Shell parentShell, IPreferenceStore store) {
		super(parentShell, new PreferenceManager());
		if (store == null)
			this.store = MagicUIActivator.getDefault().getPreferenceStore();
		else
			this.store = store;
		//
		addNode(new PreferenceNode("basic", new BasicFilterPreferencePage(this)));
		addNode(new PreferenceNode("editions", new EditionsFilterPreferencePage(this)));
		addNode(new PreferenceNode("abilities", new AbilitiesFilterPreferencePage(this)));
		addSavePage();
	}

	protected void addSavePage() {
		addNode(new PreferenceNode("save", new SaveFilterPreferencePage(this)));
	}

	public boolean allowsMultipleFinishesPerRow() {
		return this.multiFinish;
	}

	public void setAllowsMultipleFinishesPerRow(boolean multiFinish) {
		this.multiFinish = multiFinish;
	}

	public void addNode(PreferenceNode node) {
		getPreferenceManager().addToRoot(node);
		PreferencePage page = (PreferencePage) node.getPage();
		page.setPreferenceStore(this.store);
	}

	public void performOk() {
		IPreferenceNode[] rootSubNodes = getPreferenceManager().getRootSubNodes();
		for (IPreferenceNode node : rootSubNodes) {
			node.getPage().performOk();
		}
	}

	@Override
	public boolean close() {
		return super.close();
	}

	@Override
	protected boolean isResizable() {
		return true;
	}

	@Override
	public IPreferenceStore getPreferenceStore() {
		return this.store;
	}

	public void refresh() {
		IPreferenceNode[] rootSubNodes = getPreferenceManager().getRootSubNodes();
		for (IPreferenceNode node : rootSubNodes) {
			PreferencePage page = (PreferencePage) node.getPage();
			page.setPreferenceStore(this.store);
		}
	}

	public void performDefaults() {
		IPreferenceNode[] rootSubNodes = getPreferenceManager().getRootSubNodes();
		for (IPreferenceNode node : rootSubNodes) {
			PreferencePage page = (PreferencePage) node.getPage();
			if (page instanceof AbstractFilterPreferencePage) {
				((AbstractFilterPreferencePage) page).performDefaults();
			}
		}
	}
}
