/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - "When card is selected" dropped its own Group box -
 *                         it only ever held the one checkbox, so the group
 *                         title got folded into that checkbox's own label
 *                         instead ("...when a card is selected") - a single
 *                         sentence checkbox needs no surrounding box
 *     Rémi Dutil (2026) - new "Show workspace selection dialog at startup"
 *                         checkbox: the ONLY way to reverse the Workspace
 *                         Launcher's own "Use this as the default and do not
 *                         ask again" checkbox was to manually edit/delete
 *                         config files - no in-app UI for it. Both read/write
 *                         the exact same persisted flag
 *                         (ChooseWorkspaceData/MAWorkbenchPreferences#
 *                         SHOW_WORKSPACE_SELECTION_DIALOG, at
 *                         ConfigurationScope under com.reflexit.magiccards_rcp
 *                         - NOT this plugin's own InstanceScope store, since
 *                         MAApplication#checkInstanceLocation() has to read
 *                         it before any workspace, and therefore any
 *                         InstanceScope preference, even exists yet). Doesn't
 *                         reference ChooseWorkspaceData/MAWorkbenchPreferences
 *                         directly - com.reflexit.magiccards_rcp already
 *                         depends on this plugin, so importing the other way
 *                         would be circular - the plugin id and preference
 *                         key are duplicated here as plain string literals
 *                         instead (see the constants below). Not a
 *                         BooleanFieldEditor/addField() like every other
 *                         field on this page - those all get set to this
 *                         page's own single IPreferenceStore
 *                         (MagicUIActivator's), which would silently
 *                         overwrite this field's own, different store if it
 *                         went through the same mechanism - so it's a plain
 *                         SWT Button, loaded/stored/defaulted by hand in
 *                         createFieldEditors()/performOk()/
 *                         performDefaults().
 *     Rémi Dutil (2026) - "Work Offline" removed as a user feature (the app
 *                         now copes quietly with no web); the same switch
 *                         lives on as "Simulate web not working", shown in
 *                         debug launches only, for testing that behaviour.
 *     Rémi Dutil (2026) - "Card prices from:" TCGplayer (USD) / Cardmarket
 *                         (EUR) - the price provider combo is back, limited
 *                         to the two Scryfall-fed sources.
 *     Rémi Dutil (2026) - "Allow to copy non-virtual cards" removed: copying
 *                         is always allowed, the copy follows the
 *                         destination's default ownership
 */

package com.reflexit.magiccards.ui.preferences;


import org.eclipse.core.runtime.preferences.ConfigurationScope;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

import com.reflexit.magiccards.core.sync.WebUtils;
import com.reflexit.magiccards.ui.MagicUIActivator;

/**
 * This class represents a preference page that is contributed to the
 * Preferences dialog. By subclassing <samp>FieldEditorPreferencePage</samp>, we
 * can use the field support built into JFace that allows us to create a page
 * that is small and knows how to save, restore and apply itself.
 * <p>
 * This page is used to modify preferences only. They are stored in the
 * preference store that belongs to the main plug-in class. That way,
 * preferences can be accessed directly via the preference store.
 */
public class MagicPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {
	/** com.reflexit.magiccards_rcp's own plugin id and MAWorkbenchPreferences#
	 *  SHOW_WORKSPACE_SELECTION_DIALOG, duplicated as literals - see this
	 *  class' own header for why this isn't just an import. */
	private static final String WORKSPACE_CHOOSER_PLUGIN_ID = "com.reflexit.magiccards_rcp";
	private static final String SHOW_WORKSPACE_SELECTION_DIALOG = "SHOW_WORKSPACE_SELECTION_DIALOG";
	private Button showWorkspaceDialog;

	public MagicPreferencePage() {
		super(GRID);
		setPreferenceStore(MagicUIActivator.getDefault().getPreferenceStore());
		setDescription("To set more preferences please pick subcategory");
	}

	/**
	 * Creates the field editors. CardFieldExpr editors are abstractions of the
	 * common GUI blocks needed to manipulate various types of preferences. Each
	 * field editor knows how to save and restore itself.
	 */
	@Override
	public void createFieldEditors() {
		// internet
		createInternetOptionsGroup();
		// presentation
		BooleanFieldEditor grid = new BooleanFieldEditor(PreferenceConstants.SHOW_GRID,
				"Show grid lines in card tables", getFieldEditorParent());
		addField(grid);
		// workspace - not a BooleanFieldEditor: it targets a different plugin's
		// ConfigurationScope preference, not this page's own store - see this
		// class' own header
		this.showWorkspaceDialog = new Button(getFieldEditorParent(), SWT.CHECK);
		this.showWorkspaceDialog.setText("Show workspace selection dialog at startup");
		GridData wgd = new GridData();
		wgd.horizontalSpan = 2;
		this.showWorkspaceDialog.setLayoutData(wgd);
		this.showWorkspaceDialog.setSelection(getWorkspaceSelectionDialogPreference().getBoolean(
				SHOW_WORKSPACE_SELECTION_DIALOG, true));
		/* !!! RD
		StringFieldEditor cur = new StringFieldEditor(PreferenceConstants.CURRENCY, //
				"Default currency (code)", getFieldEditorParent()) {
			@Override
			protected void fireValueChanged(String property, Object oldValue, Object newValue) {
				super.fireValueChanged(property, oldValue, newValue);
				String val = (String) newValue;
				if (val.length() == 3) {
					try {
						CurrencyConvertor.setCurrency(val);
						CurrencyConvertor.loadRate("USD", val);
					} catch (IllegalArgumentException e) {
						MagicLogger.log("Invalid currency " + val);
						MagicLogger.log(e);
					}
				}
			}
		};
		addField(cur);
		*/
	}

	protected void createInternetOptionsGroup() {
		Group inetOptions = new Group(getFieldEditorParent(), SWT.NONE);
		inetOptions.setText("Internet");
		GridData ld = new GridData(GridData.FILL_HORIZONTAL | GridData.GRAB_HORIZONTAL);
		ld.horizontalSpan = 2;
		inetOptions.setLayoutData(ld);
		if (MagicUIActivator.isDebugLaunch()) {
			// testing aid, debug launches only: the app must cope quietly with no web
			addField(new BooleanFieldEditor(PreferenceConstants.SIMULATE_WEB_DOWN,
					"Simulate web not working (testing)", inetOptions) {
				@Override
				protected void fireStateChanged(String property, boolean oldValue, boolean newValue) {
					super.fireStateChanged(property, oldValue, newValue);
					WebUtils.setSimulateWebDown(newValue);
				}
			});
		}
		addField(new BooleanFieldEditor(PreferenceConstants.CHECK_FOR_CARDS, "Check for new cards on startup",
				inetOptions));
		/*
		 * !!! RD Disable for now addField(new
		 * BooleanFieldEditor(PreferenceConstants.CHECK_FOR_UPDATES,
		 * "Check for software updates on startup", inetOptions));
		 */
		// card prices: TCGplayer (USD) or Cardmarket (EUR), both from the card
		// database update, shown in the currency setting's currency
		addField(new ComboFieldEditor(PreferenceConstants.PRICE_PROVIDER, "Card prices from:", getPriceProviders(),
				inetOptions));
		createButtons(inetOptions);
		// selection - a single sentence checkbox, no separate group box: it
		// only ever held this one field (see this class' own header)
		BooleanFieldEditor load = new BooleanFieldEditor(PreferenceConstants.LOAD_IMAGES,
				"Load card graphics from the web when a card is selected", inetOptions);
		addField(load);
		/*
		 * !!! RD Deprecated, all extras loaded all the time now BooleanFieldEditor
		 * rulings = new BooleanFieldEditor(PreferenceConstants.LOAD_RULINGS,
		 * "Load rulings from the web", inetOptions); addField(rulings);
		 * BooleanFieldEditor other = new
		 * BooleanFieldEditor(PreferenceConstants.LOAD_EXTRAS,
		 * "Load extra fields and update oracle text from the web", inetOptions);
		 * addField(other); BooleanFieldEditor printings = new
		 * BooleanFieldEditor(PreferenceConstants.LOAD_PRINTINGS,
		 * "Load all card's printings (all sets and artworks) from the web",
		 * inetOptions); addField(printings);
		 */
	}

	private void createButtons(Composite fieldEditorParent) {

		/*
		 * !!!! RD No software update for now Button pressMe = new
		 * Button(fieldEditorParent, SWT.PUSH);
		 * pressMe.setText("Add Software Update Site...");
		 * pressMe.addSelectionListener(new SelectionAdapter() {
		 * 
		 * @Override public void widgetSelected(SelectionEvent e) { new
		 * UpdateHandlerP2().openManipulateRepositories(); } }); GridData ld = new
		 * GridData(); ld.horizontalSpan = 2; pressMe.setLayoutData(ld);
		 */
	}

	@Override
	public void propertyChange(PropertyChangeEvent event) {
		super.propertyChange(event);
	}

	private Preferences getWorkspaceSelectionDialogPreference() {
		return ConfigurationScope.INSTANCE.getNode(WORKSPACE_CHOOSER_PLUGIN_ID);
	}

	@Override
	public boolean performOk() {
		boolean ok = super.performOk();
		if (this.showWorkspaceDialog != null) {
			Preferences node = getWorkspaceSelectionDialogPreference();
			node.putBoolean(SHOW_WORKSPACE_SELECTION_DIALOG, this.showWorkspaceDialog.getSelection());
			try {
				node.flush();
			} catch (BackingStoreException e) {
				// ignore - same as ChooseWorkspaceData#writePersistedData()
			}
		}
		return ok;
	}

	@Override
	protected void performDefaults() {
		super.performDefaults();
		if (this.showWorkspaceDialog != null) {
			// documented default for SHOW_WORKSPACE_SELECTION_DIALOG is true
			// (MAWorkbenchPreferences' own javadoc)
			this.showWorkspaceDialog.setSelection(true);
		}
	}

	/** {label, provider name} for the two price sources. */
	private String[][] getPriceProviders() {
		String[] names = com.reflexit.magiccards.core.seller.PriceSources.names();
		String[][] res = new String[names.length][2];
		for (int i = 0; i < names.length; i++) {
			res[i][0] = com.reflexit.magiccards.core.seller.PriceSources.label(names[i]);
			res[i][1] = names[i];
		}
		return res;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.ui.IWorkbenchPreferencePage#init(org.eclipse.ui.IWorkbench)
	 */
	@Override
	public void init(IWorkbench workbench) {
	}
}