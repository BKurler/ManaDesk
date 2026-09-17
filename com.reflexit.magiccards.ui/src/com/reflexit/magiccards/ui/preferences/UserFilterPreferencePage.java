/*
 * Contributors:
 *     Rémi Dutil (2026) - moved the Condition and Proxy filter groups here
 *                         from Basic Filter: they describe the user's own
 *                         physical copies, not generic card-database facts
 *     Rémi Dutil (2026) - Finish filter group, alongside Condition and Proxy
 *     Rémi Dutil (2026) - moved Finish back to Basic Filter: unlike
 *                         Condition/Proxy it's also meaningful for a
 *                         printing with no owned copy at all (which finishes
 *                         does it come in?), so it needs to work when
 *                         filtering the Scryfall database itself, not just
 *                         the user's own decks/collections
 */
package com.reflexit.magiccards.ui.preferences;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import com.reflexit.magiccards.ui.dialogs.CardFilterDialog;
import com.reflexit.magiccards.ui.preferences.feditors.CardConditionPreferenceGroup;
import com.reflexit.magiccards.ui.preferences.feditors.ProxyPreferenceGroup;
import com.reflexit.magiccards.ui.preferences.feditors.UserFieldsPreferenceGroup;

public class UserFilterPreferencePage extends AbstractFilterPreferencePage {
	private Composite panel;

	public UserFilterPreferencePage(CardFilterDialog dialog) {
		super(dialog);
		setTitle("User Filter");
		// setDescription("A demonstration of a preference page
		// implementation");
	}

	@Override
	protected Control createContents(Composite parent) {
		setTitle("User Filter");
		this.panel = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		this.panel.setLayout(layout);
		this.panel.setFont(parent.getFont());
		createAndAdd(new UserFieldsPreferenceGroup(), panel);
		Composite physicalRow = createColumnComposite(this.panel, 2);
		createAndAdd(new CardConditionPreferenceGroup(), physicalRow);
		createAndAdd(new ProxyPreferenceGroup(), physicalRow);
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
