/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - Proxy (Genuine / Proxy) field editor
 *     Rémi Dutil (2026) - Finish (Nonfoil / Foil / Etched) field editor
 *     Rémi Dutil (2026) - Finish choices constrained to what the printing
 *                         (FINISHES) actually supports - Etched can no longer
 *                         be picked on a printing that was never etched
 *     Rémi Dutil (2026) - dropped "Auto" from the Finish combo - just
 *                         Nonfoil/Foil/Etched now; unknown-printing fallback
 *                         changed to {Nonfoil, Foil} (never offer Etched
 *                         without positive confirmation)
 *     Rémi Dutil (2026) - allowedFinishes() split into a package-visible pure
 *                         function of the raw FINISHES store value, so the
 *                         multi-card-selection case is actually unit-tested
 *                         (EditCardsPropertiesDialogFinishTest), not just
 *                         reasoned about
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
import com.reflexit.magiccards.core.model.CardFinish;
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
	public static final String FINISH_FIELD = MagicCardField.FINISH.name();
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
		// finish
		createFinishFieldEditor(area);
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

	public void createFinishFieldEditor(Composite area) {
		createTextLabel(area, "Finish");
		final Combo finish = new Combo(area, SWT.READ_ONLY);
		finish.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		String stored = store.getDefaultString(FINISH_FIELD);
		String shown;
		if (UNCHANGED.equals(stored)) {
			shown = UNCHANGED;
		} else {
			// card.get(FINISH) always resolves to a real value (MagicCardPhysical#
			// getFinish() never returns null), so this is only a defensive fallback
			CardFinish f = CardFinish.resolve(stored);
			shown = f == null ? CardFinish.NONFOIL.getLabel() : f.getLabel();
		}
		// only offer what this printing actually supports (FINISHES, populated
		// from Scryfall - already loaded into the store next to every other
		// field). Unknown / multiple different printings selected -> Etched
		// must be positively confirmed, never offered "just in case".
		CardFinish[] all = allowedFinishes();
		String[] choices = new String[all.length + 1];
		for (int i = 0; i < all.length; i++)
			choices[i] = all[i].getLabel();
		choices[choices.length - 1] = UNCHANGED;
		// the stored value may no longer be one of the offered choices (e.g. a
		// stale value from before the printing's finishes were known) - keep it
		// visible & selected instead of silently dropping it
		boolean shownIsChoice = false;
		for (String c : choices)
			if (c.equals(shown))
				shownIsChoice = true;
		if (!shownIsChoice) {
			String[] withStale = java.util.Arrays.copyOf(choices, choices.length + 1);
			withStale[choices.length] = shown + " (not offered by this printing)";
			choices = withStale;
			shown = withStale[choices.length - 1];
		}
		setComboChoices(finish, choices, shown);
		finish.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				String t = finish.getText();
				int paren = t.indexOf(" (not offered");
				if (paren > 0)
					t = t.substring(0, paren);
				store.setValue(FINISH_FIELD, UNCHANGED.equals(t) ? UNCHANGED : t);
			}
		});
	}

	/** {@link CardFinish#values()}, filtered to {@link MagicCardField#FINISHES}
	 *  (already loaded in the store, same as every other field) when a single
	 *  printing is being edited and its finishes are known. Unknown / multiple
	 *  different printings selected -&gt; {@code {Nonfoil, Foil}} (Etched is
	 *  never offered without positive confirmation), same fallback
	 *  {@link com.reflexit.magiccards.core.model.MagicCard#getSupportedFinishes()}
	 *  uses. */
	private CardFinish[] allowedFinishes() {
		return allowedFinishes(store.getDefaultString(MagicCardField.FINISHES.name()));
	}

	/**
	 * The guts of {@link #allowedFinishes()}, pulled out as a pure function of
	 * the raw {@code MagicCardField.FINISHES} store value so it's unit-testable
	 * without a live dialog/Shell - see {@code EditCardsPropertiesDialogFinishTest}.
	 * {@code csv} is either one printing's {@code nonfoil,foil,etched} subset, an
	 * empty/missing value (unknown), or {@link #UNCHANGED} (several different
	 * printings selected together, each with a different value for this field -
	 * see {@code EditMagicCardPhysicalDialog}'s constructor, which is what
	 * actually decides UNCHANGED vs. a shared value across the selection).
	 */
	static CardFinish[] allowedFinishes(String csv) {
		java.util.EnumSet<CardFinish> set = java.util.EnumSet.noneOf(CardFinish.class);
		if (csv != null && !csv.isEmpty() && !UNCHANGED.equals(csv))
			for (String part : csv.split(","))
				if (CardFinish.resolve(part.trim()) != null)
					set.add(CardFinish.resolve(part.trim()));
		if (set.isEmpty())
			set = java.util.EnumSet.of(CardFinish.NONFOIL, CardFinish.FOIL);
		java.util.List<CardFinish> ordered = new java.util.ArrayList<>();
		for (CardFinish f : CardFinish.values())
			if (set.contains(f))
				ordered.add(f);
		return ordered.toArray(new CardFinish[ordered.size()]);
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
