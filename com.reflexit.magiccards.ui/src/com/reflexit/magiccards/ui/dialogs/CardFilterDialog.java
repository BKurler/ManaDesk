/*
 * Contributors:
 *     Rémi Dutil (2026) - allowsMultipleFinishesPerRow(): whether the Finish
 *                         filter's "And" (exact match) mode is meaningful for
 *                         this dialog's rows - true by default (this base
 *                         class backs the DB-browsing views, whose rows can
 *                         legitimately offer several finishes at once);
 *                         DeckFilterDialog turns it back off
 *     Rémi Dutil (2026) - replaced the JFace PreferenceDialog tree-of-pages
 *                         navigation with a plain TabFolder: for only 4-6
 *                         pages, the tree took a whole column of its own
 *                         width while sitting mostly empty below the last
 *                         entry - the dialog's actual height was already set
 *                         by the tallest single page (Set Filter's big set
 *                         list), not by the tree, so the tree bought nothing.
 *                         Each existing *FilterPreferencePage is unchanged -
 *                         PreferencePage only ever talks to its container
 *                         through the generic IPreferencePageContainer
 *                         interface (getPreferenceStore()/updateButtons()/
 *                         updateMessage()/updateTitle()), never assuming a
 *                         real PreferenceDialog is behind it, so this class
 *                         implements that interface directly instead of
 *                         inheriting it from PreferenceDialog. Each page's
 *                         own "Restore Page Defaults"/"Apply" row (added by
 *                         PreferencePage#createControl() itself, further
 *                         extended with "Restore All Defaults" by
 *                         AbstractFilterPreferencePage#contributeButtons())
 *                         comes along unchanged, embedded in the page's own
 *                         control - nothing to rebuild there. What
 *                         PreferenceDialog no longer provides for free: the
 *                         dialog's own OK/Cancel buttons (standard TrayDialog
 *                         default, no extra code needed) and calling
 *                         performOk() on every page when OK is pressed - see
 *                         okPressed() below, replacing whatever internal
 *                         mechanism PreferenceDialog used to call it through.
 *     Rémi Dutil (2026) - performApply()/setOnApply(): each page's own
 *                         "Apply" button (built into PreferencePage itself)
 *                         defaulted to calling performOk() on just THAT one
 *                         page - which writes the field values to the
 *                         preference store, but nothing then tells the
 *                         owning view to re-run its filter and redraw, so
 *                         Apply looked like it did nothing. Added a real
 *                         onApply callback the owning view wires to the same
 *                         refresh its toolbar "Refresh" action already uses,
 *                         and AbstractFilterPreferencePage now routes its
 *                         own performApply() through this dialog's, so Apply
 *                         commits every tab (matching what OK does) and
 *                         actually refreshes the view without closing the
 *                         dialog. okPressed() now goes through the same
 *                         method, so OK is guaranteed to refresh too.
 *     Rémi Dutil (2026) - folded the separate Abilities Filter tab into
 *                         Basic Filter (now titled "Main Filter") - once
 *                         tabs replaced the tree, having Basic/Abilities/User
 *                         as three separate single-purpose tabs (the last two
 *                         each just a handful of fields) cost more in
 *                         navigation than it saved in space. User Filter
 *                         folds in too, but only for dialogs where it's
 *                         actually meaningful (Condition/Proxy/Ownership are
 *                         per-copy facts - meaningless when browsing the
 *                         Scryfall database itself) - see
 *                         allowsUserFilter()/setAllowsUserFilter(), same
 *                         opt-in shape as allowsMultipleFinishesPerRow().
 *                         AbilitiesFilterPreferencePage and
 *                         UserFilterPreferencePage are gone - their content
 *                         moved into BasicFilterPreferencePage directly.
 */
package com.reflexit.magiccards.ui.dialogs;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.dialogs.TrayDialog;
import org.eclipse.jface.preference.IPreferencePageContainer;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;

import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.preferences.AbstractFilterPreferencePage;
import com.reflexit.magiccards.ui.preferences.BasicFilterPreferencePage;
import com.reflexit.magiccards.ui.preferences.EditionsFilterPreferencePage;
import com.reflexit.magiccards.ui.preferences.SaveFilterPreferencePage;

public class CardFilterDialog extends TrayDialog implements IPreferencePageContainer {
	private static final class Node {
		final String id;
		final PreferencePage page;

		Node(String id, PreferencePage page) {
			this.id = id;
			this.page = page;
		}
	}

	private IPreferenceStore store;
	// Default true: the base dialog backs the DB-browsing views (Scryfall
	// database, Printings, Instances), where a single row can legitimately
	// offer several finishes at once, so the Finish filter's "And" mode is
	// meaningful there. Subclasses for single-valued, owned-copy-only views
	// (Deck/Collection, My Cards) turn this back off.
	private boolean multiFinish = true;
	// Off by default: Condition/Proxy/Ownership describe the user's own
	// physical copies, meaningless when browsing the Scryfall database
	// itself. DeckFilterDialog/MyCardsFilterDialog turn this on.
	private boolean userFilter = false;
	private final List<Node> nodes = new ArrayList<>();
	private TabFolder tabFolder;
	private Runnable onApply;

	public CardFilterDialog(Shell parentShell, IPreferenceStore store) {
		super(parentShell);
		setShellStyle(getShellStyle() | SWT.RESIZE | SWT.MAX);
		if (store == null)
			this.store = MagicUIActivator.getDefault().getPreferenceStore();
		else
			this.store = store;
		//
		addNode("basic", new BasicFilterPreferencePage(this));
		addNode("editions", new EditionsFilterPreferencePage(this));
		addSavePage();
	}

	protected void addSavePage() {
		addNode("save", new SaveFilterPreferencePage(this));
	}

	public boolean allowsMultipleFinishesPerRow() {
		return this.multiFinish;
	}

	public void setAllowsMultipleFinishesPerRow(boolean multiFinish) {
		this.multiFinish = multiFinish;
	}

	public boolean allowsUserFilter() {
		return this.userFilter;
	}

	public void setAllowsUserFilter(boolean userFilter) {
		this.userFilter = userFilter;
	}

	public void addNode(String id, PreferencePage page) {
		page.setPreferenceStore(this.store);
		page.setContainer(this);
		this.nodes.add(new Node(id, page));
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		getShell().setText("Filter");
		Composite composite = (Composite) super.createDialogArea(parent);
		this.tabFolder = new TabFolder(composite, SWT.TOP);
		this.tabFolder.setLayoutData(new GridData(GridData.FILL_BOTH));
		for (Node node : this.nodes) {
			TabItem item = new TabItem(this.tabFolder, SWT.NONE);
			String title = node.page.getTitle();
			item.setText(title != null && !title.isEmpty() ? title : node.id);
			node.page.createControl(this.tabFolder);
			item.setControl(node.page.getControl());
		}
		return composite;
	}

	@Override
	protected void okPressed() {
		performApply();
		super.okPressed();
	}

	public void performOk() {
		for (Node node : this.nodes) {
			node.page.performOk();
		}
	}

	/** Called from the dialog's OK button (see okPressed()) and from any
	 *  individual page's own "Apply" button (see
	 *  AbstractFilterPreferencePage#performApply()) - commits every page
	 *  (not just the one currently showing an Apply button) and, unlike a
	 *  bare performOk(), actually tells the owning view to re-run its filter
	 *  and redraw via onApply. */
	public void performApply() {
		performOk();
		if (this.onApply != null)
			this.onApply.run();
	}

	/** The owning view wires this to whatever it needs to re-run its filter
	 *  and refresh - typically the same call its own "Refresh" toolbar
	 *  action uses. Writing the new values to the preference store (what
	 *  performOk() alone does) isn't observed by anything on its own. */
	public void setOnApply(Runnable onApply) {
		this.onApply = onApply;
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
		for (Node node : this.nodes) {
			node.page.setPreferenceStore(this.store);
		}
	}

	public void performDefaults() {
		for (Node node : this.nodes) {
			if (node.page instanceof AbstractFilterPreferencePage) {
				((AbstractFilterPreferencePage) node.page).performDefaults();
			}
		}
	}

	@Override
	public void updateButtons() {
		// no dialog-level Back/Next/Finish-style buttons to enable/disable here
	}

	@Override
	public void updateMessage() {
		// pages show their own message inline; nothing dialog-wide to update
	}

	@Override
	public void updateTitle() {
		// no dialog-wide title area bound to the active page's title
	}
}
