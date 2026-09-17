/*
 * Contributors:
 *     Rémi Dutil (2026) - added the Condition filter group
 *     Rémi Dutil (2026) - added the Proxy filter group
 *     Rémi Dutil (2026) - moved Condition and Proxy to User Filter: they are
 *                         specific to the user's own collection, not generic
 *                         card-database facts like the other Basic Filter groups
 *     Rémi Dutil (2026) - Finish filter group, alongside Rarity: unlike
 *                         Condition/Proxy it's a real fact about a printing
 *                         (which finishes does it come in?) even with no
 *                         owned copy at all, so it belongs here - it needs to
 *                         work when filtering the Scryfall database itself
 *     Rémi Dutil (2026) - passes the owning CardFilterDialog's
 *                         allowsMultipleFinishesPerRow() into
 *                         CardFinishPreferenceGroup, so its "And" checkbox is
 *                         disabled where it can never apply
 */
package com.reflexit.magiccards.ui.preferences;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import com.reflexit.magiccards.ui.dialogs.CardFilterDialog;
import com.reflexit.magiccards.ui.preferences.feditors.ColorsPreferenceGroup;
import com.reflexit.magiccards.ui.preferences.feditors.CardFinishPreferenceGroup;
import com.reflexit.magiccards.ui.preferences.feditors.NumbericalPreferenceGroup;
import com.reflexit.magiccards.ui.preferences.feditors.RarityPreferenceGroup;
import com.reflexit.magiccards.ui.preferences.feditors.TextSearchPreferenceGroup;
import com.reflexit.magiccards.ui.preferences.feditors.TypesPreferenceGroup;

public class BasicFilterPreferencePage extends AbstractFilterPreferencePage {
	private Composite panel;

	public BasicFilterPreferencePage(CardFilterDialog cardFilterDialog) {
		super(cardFilterDialog);
		setTitle("Basic Filter");
		// setDescription("A demonstration of a preference page
		// implementation");
	}

	@Override
	protected Control createContents(Composite parent) {
		setTitle("Basic Filter");
		this.panel = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		this.panel.setLayout(layout);
		this.panel.setFont(parent.getFont());
		// card-database facts
		Composite firstRow = createColumnComposite(this.panel, 2);
		Composite secondRow = createColumnComposite(this.panel, 3);
		Composite thirdRow = createColumnComposite(this.panel, 1);
		createAndAdd(new TypesPreferenceGroup(), firstRow);
		createAndAdd(new ColorsPreferenceGroup(), firstRow);
		createAndAdd(new RarityPreferenceGroup(), secondRow);
		createAndAdd(new CardFinishPreferenceGroup(this.dialog.allowsMultipleFinishesPerRow()), secondRow);
		createAndAdd(new NumbericalPreferenceGroup(), secondRow);
		createAndAdd(new TextSearchPreferenceGroup(), thirdRow);
		return this.panel;
	}

	private Composite createColumnComposite(Composite parent, int cols) {
		Composite sec = new Composite(parent, SWT.NONE);
		GridLayout layout2row = new GridLayout(cols, false);
		layout2row.marginHeight = 0;
		layout2row.marginWidth = 0;
		sec.setLayout(layout2row);
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.verticalAlignment = SWT.BEGINNING;
		sec.setLayoutData(gd);
		return sec;
	}
}
