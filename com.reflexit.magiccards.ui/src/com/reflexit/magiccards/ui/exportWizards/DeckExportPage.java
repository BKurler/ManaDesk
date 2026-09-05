/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.ui.exportWizards;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.preference.FileFieldEditor;
import org.eclipse.jface.preference.PreferenceStore;
import org.eclipse.jface.preference.StringButtonFieldEditor;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.IOverwriteQuery;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.exports.CustomExportDelegate;
import com.reflexit.magiccards.core.exports.IExportDelegate;
import com.reflexit.magiccards.core.exports.ImportExportFactory;
import com.reflexit.magiccards.core.exports.ReportType;
import com.reflexit.magiccards.core.exports.SideboardHelpHtmlExportDelegate;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.Locations;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardFilter;
import com.reflexit.magiccards.core.model.SortOrder;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.model.nav.CardElement;
import com.reflexit.magiccards.core.model.nav.CardOrganizer;
import com.reflexit.magiccards.core.model.nav.ModelRoot;
import com.reflexit.magiccards.core.model.storage.ICardStore;
import com.reflexit.magiccards.core.model.storage.IFilteredCardStore;
import com.reflexit.magiccards.core.model.storage.ILocatable;
import com.reflexit.magiccards.core.model.storage.MemoryFilteredCardStore;
import com.reflexit.magiccards.core.exports.PrintProxyHtmlExportDelegate;
import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.dialogs.LocationPickerDialog;
import com.reflexit.magiccards.ui.dialogs.MagicFieldSelectorDialog;
import com.reflexit.magiccards.ui.jobs.ExportDeckJob;
import com.reflexit.magiccards.ui.preferences.feditors.FileSaveFieldEditor;

/**
 * First and only page of Deck Export Wizard
 */
public class DeckExportPage extends WizardDataTransferPage {
	private static final String EXPORTED_RESOURCES_SETTING = "exportedResources"; //$NON-NLS-1$
	private static final String OUTPUT_FILE_SETTING = "outputFile"; //$NON-NLS-1$
	private static final String REPORT_TYPE_SETTING = "reportType"; //$NON-NLS-1$
	private static final String INCLUDE_HEADER_SETTING = "includeHeader"; //$NON-NLS-1$
	private static final String INCLUDE_SIDEBOARD = "includeSideBoard"; //$NON-NLS-1$
	private static final String INCLUDE_EXTRA = "includeExtra"; //$NON-NLS-1$
	private static final String INCLUDE_COMBINE = "includeCombine"; //$NON-NLS-1$
	private static final String OPEN_AFTER = "openAfterExport"; //$NON-NLS-1$
	FileFieldEditor editor;
	private String fileName = "";
	private IStructuredSelection resourceSelection;
	private Button includeHeader;
	private static final String ID = DeckExportPage.class.getName();
	private ReportType reportType;
	private Combo typeCombo;
	private Button includeSideBoard;
	private Button includeExtra;
	private Button includeCombine;
	private Button openAfter;
	private boolean combineTouched;
	private StringButtonFieldEditor collection;
	private Text previewText;
	private Job previewJob;
	/** Bumped on every preview request; a job whose generation is stale drops its
	 * result instead of overwriting the preview with an out-of-date export. */
	private volatile int previewGen;
	/** Serialises preview jobs - their nested export delegates are cached
	 * singletons and must not run concurrently (shared output stream). */
	private final ISchedulingRule previewRule = new ISchedulingRule() {
		@Override
		public boolean contains(ISchedulingRule rule) {
			return rule == this;
		}

		@Override
		public boolean isConflicting(ISchedulingRule rule) {
			return rule == this;
		}
	};
	private StringButtonFieldEditor columnsChoice;

	/** The selected main-deck elements (never sideboard/extra), or empty. */
	List<CardElement> selectedDecks() {
		List<CardElement> res = new ArrayList<>();
		if (resourceSelection != null)
			for (Object o : resourceSelection.toList())
				if (o instanceof CardElement)
					res.add((CardElement) o);
		return res;
	}

	private boolean isCombineSelected() {
		return includeCombine != null && includeCombine.getSelection();
	}

	/**
	 * File-name stem for a deck / collection: the full location path (including
	 * the "Decks" / "Collections" level) with each level joined by '-', so two
	 * decks that share a name in different folders produce different files.
	 * "Decks/EDH/Aggro/deck1" -&gt; "Decks-EDH-Aggro-deck1";
	 * "Collections/deck1" -&gt; "Collections-deck1".
	 */
	static String deckFileStem(Location mainDeck) {
		String path = mainDeck == null ? null : mainDeck.getPath();
		if (path == null || path.isEmpty())
			return "deck";
		StringBuilder sb = new StringBuilder();
		for (String seg : path.split("/")) {
			String s = sanitizeSegment(seg);
			if (s.isEmpty())
				continue;
			if (sb.length() > 0)
				sb.append('-');
			sb.append(s);
		}
		return sb.length() == 0 ? "deck" : sb.toString();
	}

	private static String sanitizeSegment(String s) {
		return s.replaceAll("[^\\w.-]+", "_").replaceAll("^_+|_+$", "");
	}

	protected DeckExportPage(final String pageName, final IStructuredSelection selection) {
		super(pageName);
		resourceSelection = selection == null ? null : new StructuredSelection(selection.toList());
	}

	HashMap<String, String> storeToMap(Collection<CardElement> decks, boolean sideboard, boolean extra,
			boolean sideboardSupported) {
		HashMap<String, String> map = new HashMap<String, String>();
		Locations locs = Locations.getInstance();
		for (CardElement d : decks) {
			if (!sideboardSupported) {
				map.put(locs.getPrefConstant(d.getLocation()), "true");
			} else {
				Location main = d.getLocation().toMainDeck();
				map.put(locs.getPrefConstant(main), "true");
				if (sideboard)
					map.put(locs.getPrefConstant(main.toSideboard()), "true");
				if (extra)
					map.put(locs.getPrefConstant(main.toExtra()), "true");
			}
		}
		return map;
	}

	/**
	 * Hand-built, explicitly-ordered store for a "combine in one file" export:
	 * every deck in selection order, and within each deck main -> sideboard ->
	 * extra, name-sorted inside each block. No self-sort on the store so this
	 * grouping survives. (Same technique as the deck view's Export tab.)
	 */
	private IFilteredCardStore<IMagicCard> buildCombinedStore(Collection<CardElement> decks, boolean sideboard,
			boolean extra, boolean sideboardSupported) {
		MemoryFilteredCardStore<IMagicCard> mem = new MemoryFilteredCardStore<>();
		@SuppressWarnings("unchecked")
		java.util.Comparator<IMagicCard> nameSort = new SortOrder();
		Location firstMain = null;
		for (CardElement d : decks) {
			Location main = d.getLocation().toMainDeck();
			if (firstMain == null)
				firstMain = main;
			Location[] blocks = sideboardSupported
					? new Location[] { main, sideboard ? main.toSideboard() : null, extra ? main.toExtra() : null }
					: new Location[] { d.getLocation() };
			for (Location l : blocks) {
				if (l == null)
					continue;
				ICardStore<IMagicCard> s = DataManager.getInstance().getCardStore(l);
				if (s == null)
					continue;
				List<IMagicCard> g = new ArrayList<>(s.getCards());
				g.sort(nameSort);
				mem.getCardStore().addAll(g);
			}
		}
		if (firstMain != null)
			mem.setLocation(firstMain);
		mem.update();
		return mem;
	}

	protected IRunnableContext getRunnableContext() {
		return getContainer();
	}

	protected void createDestinationGroup(final Composite parent) {
		Composite fileSelectionArea = new Composite(parent, SWT.NONE);
		GridData fileSelectionData = new GridData(GridData.GRAB_HORIZONTAL | GridData.FILL_HORIZONTAL);
		fileSelectionArea.setLayoutData(fileSelectionData);
		GridLayout fileSelectionLayout = new GridLayout();
		fileSelectionLayout.numColumns = 3;
		fileSelectionLayout.makeColumnsEqualWidth = false;
		fileSelectionLayout.marginWidth = 0;
		fileSelectionLayout.marginHeight = 0;
		fileSelectionArea.setLayout(fileSelectionLayout);
		editor = new FileSaveFieldEditor("fileSelect", "Select output file", fileSelectionArea); // NON-NLS-1
		// //NON-NLS-2
		// //$NON-NLS-1$
		editor.getTextControl(fileSelectionArea).addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(final ModifyEvent e) {
				setFileName(editor.getStringValue());
				updatePageCompletion();
			}
		});
		// fileSelectionArea.moveAbove(null);
	}

	protected void setFileName(final String string) {
		fileName = string;
	}

	@Override
	protected boolean allowNewContainerName() {
		return true;
	}

	// public void handleEvent(final Event event) {
	// if (event.type == SWT.Selection && event.widget instanceof Combo) {
	// Object data = event.widget.getData(((Combo) event.widget).getText());
	// if (data instanceof ReportType) {
	// reportType = (ReportType) data;
	// }
	// }
	// updateWidgetEnablements();
	// updatePageCompletion();
	// }
	protected String getFileExtension() {
		String ext = "." + reportType.getExtension();
		return ext;
	}

	/**
	 * (non-Javadoc) Method declared on IDialogPage.
	 */
	@Override
	public void createControl(final Composite parent) {
		initializeDialogUnits(parent);
		Composite composite = new Composite(parent, SWT.NULL);
		composite.setLayout(new GridLayout());
		composite.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL));
		composite.setFont(parent.getFont());
		createResourcesGroup(composite);
		createDestinationGroup(composite);
		createOptionsGroup(composite);
		createPreviewGroup(composite);
		openAfter = new Button(composite, SWT.CHECK | SWT.LEFT);
		openAfter.setText("Automatically open the generated file(s)");
		openAfter.setSelection(true);
		openAfter.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING));
		restoreWidgetValues(); // ie.- subclass hook
		setTextFromSelection();
		updateWidgetEnablements();
		setTitle("Export");
		defaultPrompt();
		setPageComplete(determinePageCompletion());
		setErrorMessage(null); // should not initially have error message
		setControl(composite);
		MagicUIActivator.getDefault();
		PlatformUI.getWorkbench().getHelpSystem().setHelp(composite, MagicUIActivator.PLUGIN_ID + ".export"); //$NON-NLS-1$
		generatePreview();
	}

	protected void createPreviewGroup(Composite parent) {
		Group previewGroup = new Group(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		previewGroup.setLayout(layout);
		previewGroup.setLayoutData(new GridData(GridData.FILL_BOTH));
		previewGroup.setText("Preview");
		previewGroup.setFont(parent.getFont());
		previewText = new Text(previewGroup, SWT.READ_ONLY | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
		previewText.setText("preview...");
		GridData layoutData = new GridData(GridData.FILL_BOTH);
		layoutData.heightHint = 100;
		previewText.setLayoutData(layoutData);
	}

	private void defaultPrompt() {
		setMessage("Export to " + reportType.getLabel());
	}

	@Override
	protected void restoreWidgetValues() {
		super.restoreWidgetValues();
		IDialogSettings dialogSettings = MagicUIActivator.getDefault().getDialogSettings(ID);
		// restore selection
		String ids = dialogSettings.get(EXPORTED_RESOURCES_SETTING);
		if (ids != null) {
			loadFromMemento(ids);
		}
		// restore options
		String stype = dialogSettings.get(REPORT_TYPE_SETTING);
		ReportType type = ImportExportFactory.getByLabel(stype);
		if (type != null && type.getExportDelegate() != null) {
			selectReportType(type);
		} else
			selectReportType(ImportExportFactory.CSV);
		// restore file
		String file = dialogSettings.get(OUTPUT_FILE_SETTING);
		if (file != null) {
			setFileName(file);
			editor.setStringValue(file);
		}
		if (dialogSettings.get(INCLUDE_HEADER_SETTING) != null) {
			includeHeader.setSelection(dialogSettings.getBoolean(INCLUDE_HEADER_SETTING));
		}
		if (dialogSettings.get(INCLUDE_SIDEBOARD) != null) {
			includeSideBoard.setSelection(dialogSettings.getBoolean(INCLUDE_SIDEBOARD));
		}
		if (dialogSettings.get(INCLUDE_EXTRA) != null) {
			includeExtra.setSelection(dialogSettings.getBoolean(INCLUDE_EXTRA));
		}
		if (dialogSettings.get(INCLUDE_COMBINE) != null) {
			includeCombine.setSelection(dialogSettings.getBoolean(INCLUDE_COMBINE));
			combineTouched = true;
		}
		if (dialogSettings.get(OPEN_AFTER) != null) {
			openAfter.setSelection(dialogSettings.getBoolean(OPEN_AFTER));
		}
	}

	private void loadFromMemento(String ids) {
		if (ids != null) {
			collection.setStringValue(ids);
		} else {
			collection.setStringValue("");
		}
	}

	@Override
	protected void saveWidgetValues() {
		try {
			// save pref page
			IDialogSettings dialogSettings = MagicUIActivator.getDefault().getDialogSettings(ID);
			// save file name
			dialogSettings.put(OUTPUT_FILE_SETTING, fileName);
			// save selection
			dialogSettings.put(EXPORTED_RESOURCES_SETTING, collection.getStringValue());
			// save options
			dialogSettings.put(REPORT_TYPE_SETTING, reportType.getLabel());
			dialogSettings.put(INCLUDE_HEADER_SETTING, includeHeader.getSelection());
			dialogSettings.put(INCLUDE_SIDEBOARD, includeSideBoard.getSelection());
			dialogSettings.put(INCLUDE_EXTRA, includeExtra.getSelection());
			dialogSettings.put(INCLUDE_COMBINE, includeCombine.getSelection());
			dialogSettings.put(OPEN_AFTER, openAfter.getSelection());
			// save into file
			MagicUIActivator.getDefault().saveDialogSetting(dialogSettings);
		} catch (IOException e) {
			MagicUIActivator.log(e);
		}
	}

	public void setDeckSelection() {
		try {
			ModelRoot root = DataManager.getInstance().getModelRoot();
			LinkedHashSet<CardElement> picked = new LinkedHashSet<>();
			for (String path : collection.getStringValue().split(",")) {
				path = path.trim();
				if (path.isEmpty())
					continue;
				CardElement el = root.findElement(path);
				if (el == null)
					continue;
				if (el instanceof CardOrganizer)
					picked.addAll(((CardOrganizer) el).getAllElements()); // recursive leaf collections
				else
					picked.add(el);
			}
			// getAllElements() also returns the hidden -sideboard / -extra leaves
			picked.removeIf(e -> e.getLocation().isSideboard() || e.getLocation().isExtra());
			// one entry per deck family, first pick wins
			LinkedHashMap<String, CardElement> byMain = new LinkedHashMap<>();
			for (CardElement e : picked)
				byMain.putIfAbsent(e.getLocation().toMainDeck().getPath(), e);
			resourceSelection = byMain.isEmpty() ? null
					: new StructuredSelection(new ArrayList<>(byMain.values()));
		} catch (Exception e) {
			MagicUIActivator.log(e);
			resourceSelection = null;
		}
	}

	protected void createResourcesGroup(final Composite parent2) {
		Composite parent = new Composite(parent2, SWT.NONE);
		parent.setLayout(new GridLayout());
		parent.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));
		collection = new StringButtonFieldEditor("deckSelect", "Decks / collections:", parent) {
			@Override
			protected String changePressed() {
				LocationPickerDialog dialog = new LocationPickerDialog(getShell(), SWT.MULTI | SWT.READ_ONLY);
				dialog.setHideSideboards(true);
				dialog.setSelection(resourceSelection);
				if (dialog.open() == Window.OK) {
					if (dialog.getSelection() != null) {
						return dialog.getStringValue();
					}
				}
				return null;
			}
		};
		collection.getTextControl(parent).addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				setDeckSelection();
				updatePageCompletion();
				updateWidgetEnablements();
			}
		});
	}

	/**
	 * Set the initial selections in the resource group.
	 */
	protected void setTextFromSelection() {
		if (resourceSelection != null && !resourceSelection.isEmpty()) {
			StringBuilder sb = new StringBuilder();
			for (Object el : resourceSelection.toList()) {
				if (!(el instanceof ILocatable))
					continue;
				if (sb.length() > 0)
					sb.append(",");
				sb.append(((ILocatable) el).getLocation().toString());
			}
			collection.setStringValue(sb.toString());
		}
	}

	@Override
	protected void createOptionsGroupButtons(final Group optionsPanel) {
		// top level group
		Composite buttonComposite = new Composite(optionsPanel, SWT.NONE);
		buttonComposite.setFont(optionsPanel.getFont());
		GridLayout layout1 = new GridLayout();
		layout1.numColumns = 2;
		layout1.makeColumnsEqualWidth = true;
		buttonComposite.setLayout(layout1);
		buttonComposite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		// create report type
		Label label = new Label(buttonComposite, SWT.NONE);
		label.setText("Export Type:");
		typeCombo = new Combo(buttonComposite, SWT.READ_ONLY | SWT.DROP_DOWN);
		Collection<ReportType> types = ImportExportFactory.getExportTypes();
		for (ReportType rt : types) {
			addComboType(rt);
		}
		selectReportType(ImportExportFactory.CSV);
		GridData gd1 = new GridData(GridData.FILL_HORIZONTAL);
		gd1.horizontalSpan = 1;
		typeCombo.setLayoutData(gd1);
		typeCombo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				Object data = e.widget.getData(((Combo) e.widget).getText());
				if (data instanceof ReportType) {
					reportType = (ReportType) data;
					updateWidgetEnablements();
					updatePageCompletion();
				}
			}
		});
		// "Generate header row" + "Combine in one file", stacked in col 1
		Composite genChecks = new Composite(buttonComposite, SWT.NONE);
		GridLayout genLayout = new GridLayout(1, false);
		genLayout.marginWidth = 0;
		genLayout.marginHeight = 0;
		genLayout.verticalSpacing = 2;
		genChecks.setLayout(genLayout);
		genChecks.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		includeHeader = new Button(genChecks, SWT.CHECK | SWT.LEFT);
		includeHeader.setText("Generate header row");
		includeHeader.setSelection(true);
		includeHeader.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				updatePageCompletion();
			}
		});
		includeCombine = new Button(genChecks, SWT.CHECK | SWT.LEFT);
		includeCombine.setText("Combine in one file");
		includeCombine.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				combineTouched = true;
				updateWidgetEnablements();
				updatePageCompletion();
			}
		});
		// sideboard + extra options, kept together (stacked)
		Composite locChecks = new Composite(buttonComposite, SWT.NONE);
		GridLayout locLayout = new GridLayout(1, false);
		locLayout.marginWidth = 0;
		locLayout.marginHeight = 0;
		locLayout.verticalSpacing = 2;
		locChecks.setLayout(locLayout);
		locChecks.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		SelectionAdapter recompute = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				updatePageCompletion();
			}
		};
		includeSideBoard = new Button(locChecks, SWT.CHECK | SWT.LEFT);
		includeSideBoard.setText("Include sideboard");
		includeSideBoard.setSelection(true);
		includeSideBoard.addSelectionListener(recompute);
		includeExtra = new Button(locChecks, SWT.CHECK | SWT.LEFT);
		includeExtra.setText("Include extra");
		includeExtra.setSelection(false);
		includeExtra.addSelectionListener(recompute);
		createFieldsControl(buttonComposite);
	}

	public void createFieldsControl(Composite area) {
		final PreferenceStore store = new PreferenceStore();
		columnsChoiceParent = new Composite(area, SWT.NONE);
		GridData layoutData = new GridData(GridData.FILL_HORIZONTAL);
		layoutData.horizontalSpan = ((GridLayout) area.getLayout()).numColumns;
		columnsChoiceParent.setLayoutData(layoutData);
		columnsChoice = new StringButtonFieldEditor(CustomExportDelegate.ROW_FIELDS, "Columns:",
				columnsChoiceParent) {
			@Override
			protected String changePressed() {
				new MagicFieldSelectorDialog(getShell(), store).open();
				// validate();
				String fields = store.getString(CustomExportDelegate.ROW_FIELDS);
				columns = MagicCardField.toFields(fields, ",");
				generatePreview();
				return fields;
			}
		};
		columnsChoice.setTextLimit(60);
		columnsChoice.setPreferenceStore(store);
		if (columns != null) {
			columnsChoice.getTextControl(columnsChoiceParent).setEditable(false);
			String value = "";
			for (int i = 0; i < columns.length; i++) {
				ICardField field = columns[i];
				if (i != 0)
					value += ",";
				value += field.name();
			}
			columnsChoice.getPreferenceStore().setValue(CustomExportDelegate.ROW_FIELDS, value);
		}
		columnsChoice.load();
	}

	private void addComboType(ReportType rt) {
		typeCombo.add(rt.getLabel());
		typeCombo.setData(rt.getLabel(), rt);
	}

	private void selectReportType(final ReportType type) {
		if (type == null)
			return;
		reportType = type;
		typeCombo.setText(type.getLabel());
	}

	@Override
	protected String getErrorDialogTitle() {
		return "Error";
	}

	@Override
	protected boolean validateSourceGroup() {
		if (collection.getStringValue().equals("")) {
			setErrorMessage("Select an element to export");
			return false;
		}
		if (resourceSelection == null) {
			setErrorMessage("Invalid deck/collection selected");
			return false;
		}
		return true;
	}

	@Override
	protected boolean validateDestinationGroup() {
		if ((fileName == null) || (fileName.length() == 0) || (editor.getStringValue().length() == 0)) {
			setMessage("File is not selected");
			return false;
		}
		String ext = getFileExtension();
		if (!(fileName.endsWith(ext))) {
			setMessage("File should have " + ext + " extension");
			return true;
		}
		return true;
	}

	@Override
	protected void updatePageCompletion() {
		super.updatePageCompletion();
		if (isPageComplete()) {
			defaultPrompt(); // set default prompt, otherwise it empty ugly
		}
		generatePreview();
	}

	/**
	 * Creates a new button with the given id.
	 * <p>
	 * The <code>Dialog</code> implementation of this framework method creates a standard push button,
	 * registers for selection events including button presses and registers default buttons with its shell.
	 * The button id is stored as the buttons client data. Note that the parent's layout is assumed to be a
	 * GridLayout and the number of columns in this layout is incremented. Subclasses may override.
	 * </p>
	 *
	 * @param parent
	 *            the parent composite
	 * @param id
	 *            the id of the button (see <code>IDialogConstants.*_ID</code> constants for standard dialog
	 *            button ids)
	 * @param label
	 *            the label from the button
	 * @param defaultButton
	 *            <code>true</code> if the button is to be the default button, and <code>false</code>
	 *            otherwise
	 */
	protected Button createButton(final Composite parent, final int id, final String label,
			final boolean defaultButton) {
		// increment the number of columns in the button bar
		((GridLayout) parent.getLayout()).numColumns++;
		Button button = new Button(parent, SWT.PUSH);
		GridData buttonData = new GridData(GridData.FILL_HORIZONTAL);
		button.setLayoutData(buttonData);
		button.setData(new Integer(id));
		button.setText(label);
		button.setFont(parent.getFont());
		if (defaultButton) {
			Shell shell = parent.getShell();
			if (shell != null) {
				shell.setDefaultButton(button);
			}
			button.setFocus();
		}
		button.setFont(parent.getFont());
		setButtonLayoutData(button);
		return button;
	}

	@Override
	protected void updateWidgetEnablements() {
		// type
		includeHeader.setEnabled(!reportType.isXmlFormat());
		String ext = getFileExtension();
		editor.setFileExtensions(new String[] { "*" + ext });
		IExportDelegate delegate = reportType.getExportDelegate();
		List<CardElement> decks = selectedDecks();

		// "Combine in one file" - only relevant with >1 deck; seed its default
		// from the export type until the user touches it
		includeCombine.setEnabled(decks.size() > 1);
		if (!combineTouched)
			includeCombine.setSelection(delegate != null && delegate.isCombineByDefault());
		boolean combine = isCombineSelected() && decks.size() > 1;

		// propose a file name: <stem>[-<export content>].<format>
		//   stem = deck name (1 deck) | "combined" (combine) | "*" (one file per deck)
		String stem = null;
		if (decks.size() == 1)
			stem = deckFileStem(decks.get(0).getLocation().toMainDeck());
		else if (decks.size() > 1)
			stem = combine ? "combined" : "*";
		if (stem != null) {
			String slug = reportType != null ? reportType.getFileNameSlug() : "";
			String base = (slug == null || slug.isEmpty()) ? stem : stem + "-" + slug;
			String dir = (fileName.length() > 0 && new File(fileName).getParent() != null)
					? new File(fileName).getParent()
					: System.getProperty("user.home");
			String proposed = dir + File.separator + base + ext;
			if (!proposed.equals(fileName)) {
				fileName = proposed;
				editor.setStringValue(fileName);
			}
		}
		if (delegate != null) {
			if (delegate.isSideboardOnly()) {
				// the sideboard is always in this export; extra stays optional
				includeSideBoard.setSelection(true);
				includeSideBoard.setEnabled(false);
				includeExtra.setEnabled(true);
			} else {
				includeSideBoard.setEnabled(delegate.isMultipleLocationSupported());
				includeExtra.setEnabled(delegate.isMultipleLocationSupported());
			}
			// column editing is only meaningful for user-defined custom exporters -
			// the built-in ("defaults") exporters have a fixed column set
			columnsChoice.setEnabled(delegate.isColumnChoiceSupported() && reportType.isCustom(),
					columnsChoiceParent);
		} else {
			includeSideBoard.setEnabled(false);
			includeExtra.setEnabled(false);
			columnsChoice.setEnabled(false, columnsChoiceParent);
		}
	}

	public ReportType getReportType() {
		return reportType;
	}

	public String getFileName() {
		return fileName;
	}

	public boolean getIncludeHeader() {
		return includeHeader.getSelection();
	}

	public boolean getIncludeSideBoard() {
		return includeSideBoard.getSelection();
	}

	public boolean getIncludeExtra() {
		return includeExtra.getSelection();
	}

	public boolean getIncludeCombine() {
		return includeCombine.getSelection();
	}

	public CardElement getFirstCardElement() {
		for (Object object : resourceSelection.toList()) {
			if (!(object instanceof CardOrganizer) && object instanceof CardElement)
				return (CardElement) object;
		}
		return null;
	}

	public void generatePreview() {
		if (resourceSelection == null) {
			updatePreview("");
			return;
		}
		final OutputStream outStream = new ByteArrayOutputStream(1024 * 4);
		saveWidgetValues();
		final boolean header = getIncludeHeader();
		final boolean sideboard = getIncludeSideBoard();
		final boolean extra = getIncludeExtra();
		final ReportType type = getReportType();
		final List<CardElement> decks = selectedDecks();
		final boolean combine = isCombineSelected() && decks.size() > 1;
		final int gen = ++previewGen;
		if (previewJob != null) {
			previewJob.cancel();
		}
		previewJob = new Job("Generating preview") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				if (monitor.isCanceled() || gen != previewGen)
					return Status.CANCEL_STATUS;
				try {
					if (combine || decks.size() <= 1) {
						exportDeck(outStream, monitor, type, header, decks, sideboard, extra, combine);
						if (!monitor.isCanceled() && gen == previewGen)
							updatePreview(outStream.toString());
					} else {
						CardElement first = decks.get(0);
						exportDeck(outStream, monitor, type, header, java.util.Collections.singletonList(first),
								sideboard, extra, false);
						if (!monitor.isCanceled() && gen == previewGen)
							updatePreview("# Preview: \"" + first.getLocation().toMainDeck().getName()
									+ "\" only — " + decks.size() + " files will be written.\n\n"
									+ outStream.toString());
					}
				} catch (InvocationTargetException e) {
					if (e.getTargetException() instanceof InterruptedException) {
						//
					} else if (!monitor.isCanceled() && gen == previewGen)
						updatePreview(e.getCause().getMessage());
				} catch (InterruptedException e) {
					//
				} catch (Exception e) {
					if (!monitor.isCanceled() && gen == previewGen)
						updatePreview(e.getMessage());
				}
				return Status.OK_STATUS;
			}
		};
		previewJob.setRule(previewRule);
		previewJob.schedule();
	}

	protected void updatePreview(final String string) {
		if (getControl() == null)
			return;
		getControl().getDisplay().asyncExec(new Runnable() {
			@Override
			public void run() {
				previewText.setText(string);
				previewText.getParent().layout(true, true);
			}
		});
	}

	public boolean saveFile() {
		// don't let a still-running preview share the export delegate with us
		previewGen++;
		if (previewJob != null) {
			previewJob.cancel();
			try {
				previewJob.join();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		final DeckExportPage mainPage = this;
		final boolean header = getIncludeHeader();
		final boolean sideboard = getIncludeSideBoard();
		final boolean extra = getIncludeExtra();
		final List<CardElement> decks = selectedDecks();
		final boolean combine = isCombineSelected() && decks.size() > 1;

		// resolve the target file(s): "*" in the path is replaced by the deck's
		// container path + name (so same-named decks in different folders differ)
		final LinkedHashMap<String, List<CardElement>> targets = new LinkedHashMap<>();
		if (combine || decks.size() <= 1) {
			String stem = combine && decks.size() > 1 ? "combined"
					: decks.isEmpty() ? "deck" : deckFileStem(decks.get(0).getLocation().toMainDeck());
			targets.put(getFileName().replace("*", stem), decks);
		} else {
			for (CardElement d : decks) {
				String f = getFileName().replace("*", deckFileStem(d.getLocation().toMainDeck()));
				targets.put(f, java.util.Collections.singletonList(d));
			}
		}

		// one overwrite prompt for the whole batch
		final Set<String> skip = new LinkedHashSet<>();
		java.util.List<String> existing = new ArrayList<>();
		for (String f : targets.keySet())
			if (new File(f).exists())
				existing.add(f);
		if (!existing.isEmpty()) {
			String res = mainPage.queryOverwrite(existing.size() == 1 ? existing.get(0)
					: existing.size() + " files already exist");
			if (res == IOverwriteQuery.CANCEL)
				return false;
			if (res == IOverwriteQuery.NO)
				skip.addAll(existing);
		}

		boolean res = false;
		try {
			IRunnableWithProgress work = new IRunnableWithProgress() {
				@Override
				public void run(IProgressMonitor monitor) throws InvocationTargetException {
					monitor.beginTask("Exporting", targets.size());
					try {
						for (java.util.Map.Entry<String, List<CardElement>> t : targets.entrySet()) {
							if (skip.contains(t.getKey())) {
								monitor.worked(1);
								continue;
							}
							try (OutputStream os = new FileOutputStream(t.getKey())) {
								exportDeck(os, monitor, reportType, header, t.getValue(), sideboard, extra, combine);
							}
							monitor.worked(1);
						}
					} catch (InterruptedException e) {
						throw new InvocationTargetException(e);
					} catch (Exception e) {
						throw new InvocationTargetException(e);
					} finally {
						monitor.done();
					}
				}
			};
			getRunnableContext().run(true, true, work);
			if (openAfter.getSelection()) {
				for (String f : targets.keySet()) {
					if (skip.contains(f))
						continue;
					try {
						java.awt.Desktop.getDesktop().open(new File(f));
					} catch (Throwable ex) {
						MagicUIActivator.log(ex);
					}
				}
			}
			return true;
		} catch (InvocationTargetException e) {
			if (e.getTargetException() instanceof InterruptedException) {
				mainPage.displayErrorDialog("Export cancelled");
			} else
				mainPage.displayErrorDialog(e.getCause());
		} catch (InterruptedException e) {
			mainPage.displayErrorDialog("Export cancelled");
		} catch (Exception e) {
			mainPage.displayErrorDialog(e);
		}
		return res;
	}

	public void exportDeck(final OutputStream outStream, IProgressMonitor monitor, ReportType reportType,
			boolean header, Collection<CardElement> decks, boolean sideboard, boolean extra, boolean combine)
			throws InvocationTargetException, InterruptedException {
		IExportDelegate<?> exportDelegate = reportType.getExportDelegate();
		boolean sbSupported = exportDelegate.isSideboardSupported();
		IFilteredCardStore filteredLibrary;
		ICardField[] cols = columns;

		if (combine && decks.size() > 1) {
			filteredLibrary = buildCombinedStore(decks, sideboard, extra, sbSupported);
		} else {
			final HashMap<String, String> map = storeToMap(decks, sideboard, extra, sbSupported);
			filteredLibrary = DataManager.getCardHandler().getLibraryFilteredStoreWorkingCopy();
			MagicCardFilter locFilter = filteredLibrary.getFilter();
			locFilter.update(map);
			// group main deck -> sideboard -> extra. Last setSortField() = primary
			// key, so EXTRA splits the extra off last, then SIDEBOARD splits the
			// sideboard from the main deck, then NAME/ID inside each section.
			if (sideboard || extra)
				locFilter.getSortOrder().setSortField(MagicCardField.SIDEBOARD, true);
			if (extra)
				locFilter.getSortOrder().setSortField(MagicCardField.EXTRA, true);
			filteredLibrary.update();
		}
		// when several decks land in one text/CSV file, every row gets a LOCATION
		// column (added by the delegate itself, if not already present) so it is
		// still clear which deck each card came from. The two per-deck-section
		// HTML delegates render their own deck headings and opt out.
		boolean multiDeck = combine && decks.size() > 1
				&& !(exportDelegate instanceof PrintProxyHtmlExportDelegate)
				&& !(exportDelegate instanceof SideboardHelpHtmlExportDelegate);
		new ExportDeckJob(outStream, reportType, header, filteredLibrary, cols)
				.setMultiDeck(multiDeck).syncRun();
	}

	private ICardField[] columns;
	private Composite columnsChoiceParent;

	public void setColumns(ICardField[] columns2) {
		this.columns = columns2;
	}
}
