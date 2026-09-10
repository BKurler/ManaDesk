/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - Proxy (Genuine / Proxy) field editor
 */

package com.reflexit.magiccards.ui.dialogs;

import java.io.File;

import org.eclipse.jface.preference.PreferenceStore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.reflexit.magiccards.core.model.CardCondition;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.SpecialTags;
import com.reflexit.magiccards.ui.utils.CardImageUI;
import com.reflexit.magiccards.ui.utils.ImageCreator;
import com.reflexit.magiccards.ui.widgets.ContextAssist;

public class EditCardsPropertiesDialog extends MagicDialog {
	private static final String VIRTUAL_VALUE = "Virtual";
	private static final String OWN_VALUE = "Own";
	public static final String COMMENT_FIELD = MagicCardField.COMMENT.name();
	public static final String SPECIAL_FIELD = MagicCardField.SPECIAL.name();
	public static final String CONDITION_FIELD = MagicCardField.CONDITION.name();
	public static final String PROXY_FIELD = MagicCardField.PROXY.name();
	public static final String OWNERSHIP_FIELD = MagicCardField.OWNERSHIP.name();
	private static final String NOT_GRADED = "Not graded";
	private static final String GENUINE_VALUE = "Genuine";
	private static final String PROXY_VALUE = "Proxy";
	public static final String COUNT_FIELD = MagicCardField.COUNT.name();
	public static final String NAME_FIELD = MagicCardField.NAME.name();
	public static final String PRICE_FIELD = MagicCardField.PRICE.name();
	public static final String UNCHANGED = "<unchanged>";
	protected Composite area;

	public EditCardsPropertiesDialog(Shell parentShell, PreferenceStore store) {
		super(parentShell, store);
	}

	@Override
	protected void createBodyArea(Composite parent) {
		getShell().setText("Edit Card Properties");
		setTitle("Edit Magic Card Instance '" + store.getString(NAME_FIELD) + "'");
		Composite back = new Composite(parent, SWT.NONE);
		back.setLayout(new GridLayout(2, false));
		back.setLayoutData(new GridData(GridData.FILL_BOTH));
		createImageControl(back);
		area = new Composite(back, SWT.NONE);
		area.setLayout(new GridLayout(2, false));
		GridData gda = new GridData(GridData.FILL_BOTH);
		gda.widthHint = convertWidthInCharsToPixels(80);
		area.setLayoutData(gda);
		// Header
		createTextLabel(area, "Name");
		createTextLabel(area, store.getString(NAME_FIELD));
		// Count
		Text count = createTextFieldEditor(area, "Count", COUNT_FIELD);
		// Price
		createTextFieldEditor(area, "User Price", PRICE_FIELD);
		// ownership
		createOwnershipFieldEditor(area);
		// condition
		createConditionFieldEditor(area);
		// proxy
		createProxyFieldEditor(area);
		// comment
		createTextFieldEditor(area, "Comment", COMMENT_FIELD, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
		// special
		Text special = createTextFieldEditor(area, "Special Tags", SPECIAL_FIELD, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
		special.setToolTipText(
				"Set card tags, such as foil, mint, premium, forTrade, etc. Tags are separated by ','.\n To add tag use +, to remove tag use -. For example \"+foil,-online\".");
		ContextAssist.addContextAssist(special, SpecialTags.getTags(), true);
		// end
		count.setFocus();
	}

	private void createImageControl(Composite parent) {

		String id = store.getString(MagicCardField.ID.name());
		boolean multi = (id == null || id.isEmpty() || "<unchanged>".equals(id));

		// Always create the Browser so the layout stays aligned
		Browser browser = new Browser(parent, SWT.NONE);

		// Disable the native browser context menu
		browser.addListener(SWT.MenuDetect, e -> e.doit = false);

		GridData gda = new GridData(GridData.FILL_VERTICAL);
		gda.widthHint = ImageCreator.CARD_WIDTH;
		gda.heightHint = ImageCreator.CARD_HEIGHT;
		browser.setLayoutData(gda);

		// MULTI-EDIT MODE => blank area
		if (multi) {
			browser.setBackground(parent.getDisplay().getSystemColor(SWT.COLOR_WHITE));
			browser.setText("<html><body style='margin:0;padding:0;background:white;'></body></html>");
			return;
		}

		// SINGLE-CARD MODE => load actual image
		IMagicCard card = CardImageUI.buildTemporaryCard(store.getString(MagicCardField.ID.name()),
				store.getString(MagicCardField.EDITION_ABBR.name()), store.getString(MagicCardField.LANG.name()));

		File localFile = CardImageUI.getLocalImageFile(card);

		if (localFile == null || !localFile.exists()) {
			browser.setText("<html><body style='margin:0;padding:0;background:#222;color:white;"
					+ "display:flex;align-items:center;justify-content:center;"
					+ "font-family:sans-serif;font-size:14px;'>Image not found</body></html>");
			return;
		}

		String url = localFile.toURI().toString();

		browser.setText("<html><body style='margin:0;padding:0;background:#000;'>" + "<img src='" + url
				+ "' style='width:100%;height:100%;object-fit:contain;'/>" + "</body></html>");
	}

	public void createConditionFieldEditor(Composite area) {
		createTextLabel(area, "Condition");
		final Combo condition = new Combo(area, SWT.READ_ONLY);
		condition.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		String stored = store.getDefaultString(CONDITION_FIELD);
		String shown;
		if (UNCHANGED.equals(stored)) {
			shown = UNCHANGED;
		} else {
			CardCondition c = CardCondition.resolve(stored);
			shown = c == null ? NOT_GRADED : c.getLabel();
		}
		CardCondition[] all = CardCondition.values();
		String[] choices = new String[all.length + 2];
		choices[0] = NOT_GRADED;
		for (int i = 0; i < all.length; i++)
			choices[i + 1] = all[i].getLabel();
		choices[choices.length - 1] = UNCHANGED;
		setComboChoices(condition, choices, shown);
		condition.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				String t = condition.getText();
				if (UNCHANGED.equals(t))
					store.setValue(CONDITION_FIELD, UNCHANGED);
				else if (NOT_GRADED.equals(t))
					store.setValue(CONDITION_FIELD, "");
				else
					store.setValue(CONDITION_FIELD, t);
			}
		});
	}

	public void createProxyFieldEditor(Composite area) {
		createTextLabel(area, "Proxy");
		final Combo proxy = new Combo(area, SWT.READ_ONLY);
		proxy.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		String stored = store.getDefaultString(PROXY_FIELD);
		String shown = stored;
		if (!UNCHANGED.equals(stored))
			shown = Boolean.valueOf(stored) ? PROXY_VALUE : GENUINE_VALUE;
		setComboChoices(proxy, new String[] { GENUINE_VALUE, PROXY_VALUE, UNCHANGED }, shown);
		proxy.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				String t = proxy.getText();
				if (UNCHANGED.equals(t))
					store.setValue(PROXY_FIELD, UNCHANGED);
				else
					store.setValue(PROXY_FIELD, String.valueOf(PROXY_VALUE.equals(t)));
			}
		});
	}

	public void createOwnershipFieldEditor(Composite area) {
		createTextLabel(area, "Ownership");
		final Combo ownership = new Combo(area, SWT.READ_ONLY);
		ownership.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		String ovalue = store.getDefaultString(OWNERSHIP_FIELD);
		String defaultString = ovalue;
		if (!UNCHANGED.equals(ovalue))
			defaultString = Boolean.valueOf(ovalue) ? OWN_VALUE : VIRTUAL_VALUE;
		setComboChoices(ownership, new String[] { OWN_VALUE, VIRTUAL_VALUE, UNCHANGED }, defaultString);
		ownership.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				boolean own = ownership.getText().equals(OWN_VALUE);
				store.setValue(OWNERSHIP_FIELD, String.valueOf(own));
			}
		});
	}
}
