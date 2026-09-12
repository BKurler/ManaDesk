/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - restored the "Clipboard" import source (radio + preview +
 *                         Edit...); empty-clipboard guard; clamp a stale "URL" choice
 *     Rémi Dutil (2026) - split from the old DeckImportPage into this abstract
 *                         engine: source group (clipboard / file), format combo,
 *                         auto-detect, preview hand-off and the write only. Every
 *                         destination flow is its own concrete page:
 *                         NewDeckPage / NewCollectionPage (create) and
 *                         ImportIntoDeckPage / ImportIntoCollectionPage (existing).
 */
package com.reflexit.magiccards.ui.exportWizards;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceStore;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.SameShellProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.forms.events.HyperlinkAdapter;
import org.eclipse.ui.forms.events.HyperlinkEvent;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.MagicException;
import com.reflexit.magiccards.core.MagicLogger;
import com.reflexit.magiccards.core.exports.IImportDelegate;
import com.reflexit.magiccards.core.exports.ImportData;
import com.reflexit.magiccards.core.exports.ImportError;
import com.reflexit.magiccards.core.exports.ImportExportFactory;
import com.reflexit.magiccards.core.exports.ImportSource;
import com.reflexit.magiccards.core.exports.ImportUtils;
import com.reflexit.magiccards.core.exports.ReportType;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.model.nav.CardCollection;
import com.reflexit.magiccards.core.model.nav.CardElement;
import com.reflexit.magiccards.core.model.nav.CardOrganizer;
import com.reflexit.magiccards.core.model.nav.CollectionsContainer;
import com.reflexit.magiccards.core.model.storage.ICardStore;
import com.reflexit.magiccards.core.model.storage.IStorageInfo;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;
import com.reflexit.magiccards.core.sync.ParseGathererOracle;
import com.reflexit.magiccards.core.sync.WebUtils;
import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.dialogs.EditTextDialog;
import com.reflexit.magiccards.ui.dnd.CopySupport;
import com.reflexit.magiccards.ui.utils.CoreMonitorAdapter;
import com.reflexit.magiccards.ui.utils.WaitUtils;
import com.reflexit.magiccards.ui.widgets.MagicToolkit;

/**
 * Abstract card-list <b>import engine</b> shared by every New / Import page: the
 * source group (clipboard / file), the format combo, the auto-detect, the preview
 * hand-off and the write. A concrete page adds its own destination UI in
 * {@link #createDestinationGroup(Composite)} and, if it creates a brand-new
 * element, overrides {@link #isEmptyMode()} / {@link #getNewElementName()} /
 * {@link #wantVirtual()} etc.
 */
public abstract class AbstractCardListImportPage extends WizardDataTransferPage {
	private static final String NOERROR = "noerror";
	private static final String IMPUT_FILE_SETTING = "outputFile"; //$NON-NLS-1$
	private static final String REPORT_TYPE_SETTING = "reportType"; //$NON-NLS-1$
	private static final String FROM_CHOICE = "from"; //$NON-NLS-1$
	protected static final String AUTO_NAME = "<auto-generated-name>";
	private final String ID = getClass().getName();
	// ui elements
	protected Button fileRadio;
	protected Text fileText;
	protected Button clipboardRadio;
	private Button editButton;
	private Button browseButton;
	private Combo typeCombo;
	private boolean newVirtualChoice;
	private boolean newReadOnlyChoice;
	private boolean newUnsortedChoice;
	private String newNameChoice;
	/** set from the preview page: skip errored cards instead of blocking Finish */
	private boolean ignoreErrors;
	private MagicToolkit toolkit;
	// data elements
	private PreferenceStore store;
	protected ImportSource inputChoice;
	protected CardElement element;
	private Collection<ReportType> types;
	private ImportData importData;
	private String fileName = "";
	private ReportType reportType;
	private Button urlRadio;
	private Text urlText;
	private String urlName = "";
	private Text clipboardPreviewText;

	protected AbstractCardListImportPage(final String pageName, final IStructuredSelection selection) {
		super(pageName);
		store = new PreferenceStore();
		types = ImportExportFactory.getImportTypes();
		// the raw selected element, unmodified - AbstractImportIntoPage needs the
		// selected deck/collection itself; AbstractCreateElementPage is the one
		// that wants a container, and snaps a leaf selection to its parent itself
		if (selection != null && selection.getFirstElement() instanceof CardElement) {
			element = (CardElement) selection.getFirstElement();
		} else {
			element = getDeckContainer();
		}
		importData = new ImportData();
	}

	// ---- hooks a concrete page may answer ---------------------------------

	/** Build this page's destination UI. Called from {@link #createControl}. */
	protected abstract void createDestinationGroup(Composite parent);

	/** Title shown at the top of the page. */
	protected abstract String getTitleText();

	/** True when the user chose to create the element empty (no card-list import).
	 *  Only the "New ..." pages offer this; imports are never empty. */
	protected boolean isEmptyMode() {
		return false;
	}

	/** DECK_TYPE for a new deck, false for a new collection. Only consulted when
	 *  {@link #element} is a container (i.e. a new element is being created). */
	protected boolean isDeckTarget() {
		return true;
	}

	/** New-element option state - read on the UI thread before the background job. */
	protected boolean wantVirtual() {
		return false;
	}

	protected boolean wantReadOnly() {
		return false;
	}

	protected boolean wantUnsorted() {
		return false;
	}

	/** Name for a new element: the concrete "New ..." page overrides to prefer its
	 *  Name field; this fallback is the source file's base name (or "imported"). */
	protected String getNewElementName() {
		return getSourceBasedName();
	}

	/** Re-sync the destination widgets after the engine changed {@link #element}.
	 *  Default: nothing (concrete pages override). */
	protected void syncDestination() {
		// concrete pages override
	}

	/** Contents/Source layout - the plain source group by default; the "New ..."
	 *  pages override to fold Empty/Clipboard/File into one flat radio list. */
	protected void createResourcesGroup(final Composite parent) {
		createSourceGroup(parent);
	}

	/** Label for the Clipboard radio - "New ..." pages read better as "Import
	 *  from Clipboard" once Empty/Clipboard/File are one flat list. */
	protected String clipboardRadioLabel() {
		return "Clipboard";
	}

	/** Label for the File radio - see {@link #clipboardRadioLabel()}. */
	protected String fileRadioLabel() {
		return "File";
	}

	/** Where {@link #createSourceGroup} builds the Clipboard/File radios and
	 *  their controls. Default: a dedicated "Import Source" group. The "New ..."
	 *  pages override this to return their own "Contents" group instead, so
	 *  Empty/Clipboard/File render as one flat, mutually-exclusive radio list
	 *  (SWT groups radios by immediate parent) rather than two separate choices
	 *  (Empty-vs-Import, then Clipboard-vs-File within "Import"). */
	protected Composite createSourceContainer(Composite parent) {
		Composite fileSelectionArea = toolkit.createGroup(parent, "Import Source");
		fileSelectionArea.setLayoutData(GridDataFactory.fillDefaults().create());
		fileSelectionArea.setLayout(GridLayoutFactory.swtDefaults().numColumns(3).create());
		return fileSelectionArea;
	}

	/** Where the cards are going, for the preview page's subtitle
	 *  ("Importing into &lt;this&gt;."). Concrete pages refine it. */
	public String getImportTargetDescription() {
		return element == null ? "the target" : "“" + element.getName() + "”";
	}

	@Override
	protected boolean validateDestinationGroup() {
		if (isEmptyMode())
			return true;
		if (!importData.isOk() && importData.getError() != null) {
			setErrorMessage(importData.getError().getMessage());
			return false;
		}
		return true;
	}

	/** Create the element with no cards. Only the "New ..." pages support this
	 *  (they alone can be in {@link #isEmptyMode()}). */
	public void createEmptyElement() {
		throw new UnsupportedOperationException("no empty mode on " + getClass().getSimpleName());
	}

	public void setIgnoreErrors(boolean ignoreErrors) {
		this.ignoreErrors = ignoreErrors;
	}

	public void performImport(final boolean preview) {
		Display.getDefault().syncExec(() -> {
			// a card's "virtual" nature follows its deck / collection: an existing
			// target's current flag, or the flag chosen for the new one
			boolean targetVirtual = (element instanceof CardCollection)
					? ((CardCollection) element).isVirtual()
					: wantVirtual();
			importData.setVirtual(targetVirtual);
			// cache the widget state now, on the UI thread - importRunnable() runs
			// on a background job and can't touch SWT widgets
			newVirtualChoice = wantVirtual();
			newReadOnlyChoice = wantReadOnly();
			newUnsortedChoice = wantUnsorted();
			newNameChoice = getNewElementName();
			final boolean dbImport = false;
			try {
				IRunnableWithProgress work = new IRunnableWithProgress() {
					@Override
					public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
						importRunnable(preview, dbImport, monitor);
					}
				};
				getRunnableContext().run(true, true, work);
			} catch (InvocationTargetException ite) {
				Throwable e = ite.getCause();
				importData.setError(e);
				if (e instanceof RuntimeException && !(e instanceof MagicException))
					MagicUIActivator.log(e);
			} catch (InterruptedException e) {
				importData.setError(e);
			}
		});
	}

	public boolean askQuestion(String message) {
		AtomicBoolean result = new AtomicBoolean(false);
		AtomicBoolean cancelled = new AtomicBoolean(false);
		getControl().getDisplay().syncExec(new Runnable() {
			@Override
			public void run() {
				int kind = MessageDialog.QUESTION_WITH_CANCEL;
				String[] dialogButtonLabels = new String[] { IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL,
						IDialogConstants.CANCEL_LABEL };
				MessageDialog dialog = new MessageDialog(getShell(), "Question", null, message, kind, 0,
						dialogButtonLabels) {
					{
						setShellStyle(getShellStyle() | SWT.SHEET);
					}
				};
				int res = dialog.open();
				result.set(res == 0);
				cancelled.set(res < 0 || res == 2);
			}
		});
		if (cancelled.get()) {
			throw new OperationCanceledException();
		}
		return result.get();
	}

	public boolean openDialog(Window dialog) {
		AtomicBoolean result = new AtomicBoolean(false);
		AtomicBoolean cancelled = new AtomicBoolean(false);
		getControl().getDisplay().syncExec(new Runnable() {
			@Override
			public void run() {
				int res = dialog.open();
				result.set((res == Window.OK));
				cancelled.set(res < 0 || res == 2);
			}
		});
		if (cancelled.get())
			throw new OperationCanceledException();
		return result.get();
	}

	boolean fixErrors(final Collection<IMagicCard> result, final boolean dbImport, IProgressMonitor monitor) {
		int total = result.size();
		SubMonitor submon = SubMonitor.convert(monitor, "Fixing Errors...", total);
		ICardStore magicDb = DataManager.getCardHandler().getMagicDBStore();
		Map<Object, Integer> categorizeErrors = categorizeErrors(result);
		int noerrors = categorizeErrors.get(NOERROR);
		int totalerrors = total - noerrors;
		submon.worked(noerrors);
		submon.setWorkRemaining(totalerrors);
		/*
		 * !!! RD Integer varErrors = categorizeErrors.get(ImportError.NO_VARIANT); if
		 * (varErrors != null) { boolean yes = askQuestion(
		 * "Local database does not contain cards variants you trying to import (i.e. alternate land graphics) ("
		 * + varErrors + " errors), do you want to load them?"); if (yes) {
		 * fixVariantErrors(magicDb, result, submon.split(varErrors)); } else {
		 * monitor.worked(varErrors); } } if (submon.isCanceled()) throw new
		 * OperationCanceledException(); Integer langErrors =
		 * categorizeErrors.get(ImportError.NO_LANG); if (langErrors != null) { boolean
		 * yes =
		 * askQuestion("Local database does not contain cards in the language you trying to import ("
		 * + langErrors + " errors), do you want to load them? It will take A WHILE");
		 * if (yes) { fixLangErrors(magicDb, result, submon.split(langErrors)); } else {
		 * monitor.worked(langErrors); } } if (submon.isCanceled()) throw new
		 * OperationCanceledException();
		 */
		int size = total;
		/*
		 * !!! RD Map<String, String> badSets = ImportUtils.getSetCandidates(result); if
		 * (badSets.size() > 0) { boolean yes = askQuestion( "Cannot resolve " +
		 * badSets.size() + " set(s). The following sets are not found or ambigues: " +
		 * badSets.keySet() + ".\n Do you want to fix these?"); if (yes) { // ask user
		 * to fix sets for (Iterator<String> iterator = badSets.keySet().iterator();
		 * iterator.hasNext();) { String set = iterator.next(); CorrectSetDialog dialog
		 * = new CorrectSetDialog(getShell(), set, badSets.get(set)); if
		 * (openDialog(dialog)) { String newSet = dialog.getSet(); badSets.put(set,
		 * newSet); } else { break; } } // fix cards for these sets
		 * ImportUtils.fixSets(result, badSets); } }
		 */
		ArrayList<IMagicCard> newdbrecords = new ArrayList<>();
		ImportUtils.performPreImportWithDb(result, newdbrecords,
				reportType.getImportDelegate().getResult().getFields());
		ArrayList<String> lerrors = new ArrayList<>();
		ImportUtils.validateDbRecords(newdbrecords, lerrors);
		if (false && newdbrecords.size() > 0 && lerrors.size() == 0) { // !!! RD Disable for now
			boolean yes2 = dbImport;
			if (yes2 == false) {
				yes2 = askQuestion(newdbrecords.size()
						+ " cards are not found in the database. Do you want to add new cards into database?");
			}
			if (yes2)
				ImportUtils.importIntoDb(newdbrecords);
		} else if (lerrors.size() > 0 && dbImport) {
			String message = newdbrecords.size() + " cards are not found in the database " + lerrors.size()
					+ "\nThe following errors preventing import all of them into database:\n";
			for (Iterator iterator = lerrors.iterator(); iterator.hasNext();) {
				String str = (String) iterator.next();
				message += str + "\n";
			}
			message += "Do you want to proceed with importing cards partually? No would abort the import into DB";
			boolean yes2 = askQuestion(message);
			if (yes2)
				ImportUtils.importIntoDb(newdbrecords);
		}
		ArrayList<String> cerrors = new ArrayList<>();
		for (Iterator iterator = result.iterator(); iterator.hasNext();) {
			IMagicCard card = (IMagicCard) iterator.next();
			if (card.getCardId() == null || magicDb.getCard(card.getCardId()) == null) {
				iterator.remove();
				cerrors.add(card.getName() + " (" + card.getSet() + ")");
			}
		}
		if (cerrors.size() != 0) {
			if (ignoreErrors) {
				// the user opted in on the preview page: import the good cards,
				// silently drop the rest (already removed from result above)
				MagicUIActivator.log("Import: skipping " + cerrors.size() + " unresolved card(s) of " + size
						+ " ('Ignore cards with errors' is on)");
				return cerrors.size() < size;
			}
			String message = "After all this effort I cannot resolve " + cerrors.size() + " cards of " + size + ":\n";
			int i = 0;
			for (Iterator iterator = cerrors.iterator(); iterator.hasNext() && i < 10; i++) {
				String str = (String) iterator.next();
				message += str + "\n";
			}
			if (i < cerrors.size()) {
				message += "... + " + (cerrors.size() - i) + " more\n";
				message += "Full error log can be found at <workspace>/.metadata/.log\n";
			}
			if (cerrors.size() >= size) {
				message += "No good cards to import :( Proceed?";
				return askQuestion(message);
			} else {
				message += "Do you want to proceed with importing cards partually? No would abort the import";
				return askQuestion(message);
			}
		}
		return true;
	}

	private void fixLangErrors(ICardStore magicDb, Collection<IMagicCard> result, SubMonitor monitor) {
		HashMap<String, List> categorizedErrors = new HashMap<>();
		for (IMagicCard card : result) {
			if (card instanceof MagicCardPhysical) {
				Object error = ((MagicCardPhysical) card).getError();
				if (error == ImportError.NO_LANG) {
					String language = card.getBase().getLanguage();
					List records = categorizedErrors.get(language);
					if (records == null) {
						categorizedErrors.put(language, records = new ArrayList<>());
					}
					records.add(card);
				}
			}
		}
		// IDbCardStore<IMagicCard> db = DataManager.getInstance().getMagicDBStore();
		for (String lang : categorizedErrors.keySet()) {
			List list = categorizedErrors.get(lang);
			// System.err.println("Loading " + lang + " for " + list);
			ImportUtils.loadLanguageForCard(lang, list, magicDb, new CoreMonitorAdapter(monitor.split(list.size())));
		}
	}

	private void fixVariantErrors(ICardStore magicDb, Collection<IMagicCard> result, SubMonitor monitor) {
		Set<ICardField> fieldMap = Collections.singleton((ICardField) MagicCardField.COLLNUM);
		for (IMagicCard card : result) {
			if (card instanceof MagicCardPhysical) {
				Object error = ((MagicCardPhysical) card).getError();
				if (error == ImportError.NO_VARIANT) {
					ParseGathererOracle parser = new ParseGathererOracle();
					try {
						parser.updateCard(card.getBase(), fieldMap, ICoreProgressMonitor.NONE);
						ICardStore<MagicCard> variations = parser.getVariations();
						monitor.subTask("Loading " + card.getBase().getName());
						for (MagicCard landVariation : variations) {
							ParseGathererOracle parser2 = new ParseGathererOracle();
							parser2.updateCard(landVariation, fieldMap, ICoreProgressMonitor.NONE);
							if (magicDb.getCard(landVariation.getCardId()) == null) {
								magicDb.add(landVariation);
								// System.err.println("Added " + landVariation.getName());
							}
						}
						monitor.worked(1);
					} catch (IOException e) {
						MagicLogger.log(e);
					}
				}
			}
		}
	}

	private Map<Object, Integer> categorizeErrors(final Collection<IMagicCard> result) {
		HashMap<Object, Integer> categorizedErrors = new HashMap<>();
		categorizedErrors.put(NOERROR, 0);
		for (IMagicCard card : result) {
			if (card instanceof MagicCardPhysical) {
				Object error = ((MagicCardPhysical) card).getError();
				if (error == null) {
					error = NOERROR;
				}
				Integer integer = categorizedErrors.get(error);
				if (integer == null) {
					categorizedErrors.put(error, 1);
				} else {
					categorizedErrors.put(error, integer + 1);
				}
			}
		}
		return categorizedErrors;
	}

	protected void createNewDeck(final String base, boolean isDeck, boolean virtual, boolean unsorted, boolean readOnly,
			CollectionsContainer resource) {
		int attempts = 1000;
		Location newloc = Location.createLocation(base);
		while (resource.contains(newloc) && attempts-- > 0) {
			newloc = Location.createLocation(base + new Random().nextInt(1000));
		}
		if (attempts <= 0)
			throw new IllegalArgumentException("Cannot generate deck name");
		CardCollection created = new CardCollection(newloc.getBaseFileName(), resource, isDeck, virtual, unsorted);
		created.persistInitialSettings(isDeck, virtual, unsorted);
		if (readOnly) {
			try {
				IStorageInfo si = created.getStorageInfo();
				if (si != null)
					si.setReadOnly(true);
			} catch (RuntimeException ignore) {
				// non-fatal - fixable via Edit Properties
			}
		}
		this.element = created;
	}

	/** Name to fall back to when the user left the Name field blank: the source
	 *  file's base name, or "imported". */
	private String getSourceBasedName() {
		if (inputChoice != ImportSource.FILE) {
			return "imported";
		} else {
			String basename = new File(fileName).getName();
			int k = basename.lastIndexOf('.');
			if (k >= 0)
				basename = basename.substring(0, k);
			return basename;
		}
	}

	String readSource() throws IOException {
		String text = "";
		importData.setImportSource(inputChoice);
		switch (inputChoice) {
		case FILE:
			if (!fileName.isEmpty()) {
				text = FileUtils.readFileAsString(new File(fileName));
				importData.setText(text);
			}
			importData.setProperty(inputChoice.name(), fileName);
			break;
		case TEXT:
			text = getClipboardText();
			importData.setText(text);
			importData.setProperty(inputChoice.name(), "clipboard");
			break;
		case URL:
			importData.setProperty(inputChoice.name(), urlName);
			if (!urlName.isEmpty()) {
				text = WebUtils.openUrlText(new URL(urlName));
				importData.setText(text);
			}
			break;
		default:
			break;
		}
		return text;
	}

	public String getClipboardText() {
		String text[] = new String[] { "" };
		WaitUtils.syncExec(() -> {
			final Clipboard cb = new Clipboard(PlatformUI.getWorkbench().getDisplay());
			Object clipboardText = cb.getContents(TextTransfer.getInstance());
			if (clipboardText == null)
				clipboardText = "";
			text[0] = clipboardText.toString();
		});
		return text[0];
	}

	protected CollectionsContainer getDeckContainer() {
		return DataManager.getInstance().getModelRoot().getDeckContainer();
	}

	protected CollectionsContainer getCollectionContainer() {
		return DataManager.getInstance().getModelRoot().getCollectionsContainer();
	}

	private Location getSelectedLocation() {
		return getElement().getLocation();
	}

	protected IPreferenceStore getPreferenceStore() {
		return store;
	}

	protected IRunnableContext getRunnableContext() {
		return getContainer();
	}

	@Override
	protected boolean allowNewContainerName() {
		return true;
	}

	/**
	 * (non-Javadoc) Method declared on IDialogPage.
	 */
	@Override
	public void createControl(final Composite parent) {
		toolkit = MagicToolkit.getInstance();
		setTitle(getTitleText());
		initializeDialogUnits(parent);
		Composite composite = new Composite(parent, SWT.NULL);
		composite.setLayout(new GridLayout());
		composite.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL));
		composite.setFont(parent.getFont());
		setControl(composite);
		createDestinationGroup(composite);
		createResourcesGroup(composite);
		createOptionsGroup(composite);
		restoreWidgetValues();
		updateWidgetEnablements();
		defaultPrompt();
		setPageComplete(determinePageCompletion());
		// setErrorMessage(null); // should not initially have error message
		PlatformUI.getWorkbench().getHelpSystem().setHelp(composite, MagicUIActivator.PLUGIN_ID + ".export");
	}

	private void defaultPrompt() {
		if (reportType == null)
			reportType = ImportExportFactory.TEXT_DECK_CLASSIC;
		String mess = "You have selected '" + reportType.getLabel() + "' format.\n";
		if (importData.getError() != null) {
			mess += "Warning: cannot parse data (" + importData.getError().getMessage() + "). ";
		} else {
			int errcount = importData.getErrorCount();
			mess += "Found " + importData.size() + " record(s) and " + errcount + " error(s).";
		}
		mess += " Press 'Example...' to see the expected layout, or Next to preview.";
		setMessage(mess);
	}

	@Override
	protected void restoreWidgetValues() {
		super.restoreWidgetValues();
		IDialogSettings dialogSettings = MagicUIActivator.getDefault().getDialogSettings(ID);
		// restore file
		String file = dialogSettings.get(IMPUT_FILE_SETTING);
		if (file != null) {
			final String string = file;
			fileName = string;
			fileText.setText(file);
			fileText.setSelection(file.length(), file.length());
		}
		String sfrom = dialogSettings.get(FROM_CHOICE);
		inputChoice = ImportSource.TEXT;
		if (sfrom != null) {
			try {
				inputChoice = ImportSource.valueOf(sfrom);
			} catch (Exception e) {
				// ignore
			}
		}
		// only File and Clipboard have UI - an old "URL" setting would NPE later
		if (inputChoice != ImportSource.FILE && inputChoice != ImportSource.TEXT)
			inputChoice = ImportSource.TEXT;
		setInputChoice(inputChoice);
		// restore options
		String stype = dialogSettings.get(REPORT_TYPE_SETTING);
		ReportType type = ImportExportFactory.getByLabel(stype);
		if (type != null && type.getImportDelegate() != null) {
			selectReportType(type);
		} else {
			selectReportType(ImportExportFactory.TEXT_DECK_CLASSIC);
		}
		syncDestination();
		updateWidgetEnablements();
	}

	public void setInputChoice(ImportSource inputChoice) {
		this.inputChoice = inputChoice;
		if (fileRadio != null)
			fileRadio.setSelection(inputChoice == ImportSource.FILE);
		if (clipboardRadio != null)
			clipboardRadio.setSelection(inputChoice == ImportSource.TEXT);
	}

	private void selectReportType(final ReportType type) {
		if (type == null)
			return;
		reportType = type;
		typeCombo.setText(type.getLabel());
		typeCombo.setToolTipText(type.getExample());
	}

	@Override
	protected void saveWidgetValues() {
		try {
			IDialogSettings dialogSettings = MagicUIActivator.getDefault().getDialogSettings(ID);
			// save file name
			dialogSettings.put(IMPUT_FILE_SETTING, fileName);
			dialogSettings.put(FROM_CHOICE, inputChoice.name());
			// save options
			dialogSettings.put(REPORT_TYPE_SETTING, reportType.getLabel());
			// save into file
			MagicUIActivator.getDefault().saveDialogSetting(dialogSettings);
		} catch (IOException e) {
			MagicUIActivator.log(e);
		}
	}

	/** Human-readable description of where the import data came from, for error
	 * messages and the log. */
	public String getSourceDescription() {
		switch (inputChoice) {
		case FILE:
			return (fileName == null || fileName.isEmpty()) ? "file (none selected)" : "file \"" + fileName + "\"";
		case URL:
			return (urlName == null || urlName.isEmpty()) ? "URL (none)" : "URL \"" + urlName + "\"";
		case TEXT:
			return "clipboard";
		default:
			return "input";
		}
	}

	/** The Clipboard (radio + preview + Edit...) and File (radio + path +
	 *  Browse...) controls - in their own "Import Source" group by default, or
	 *  folded into the caller's own group by {@link #createSourceContainer}. */
	protected void createSourceGroup(final Composite parent) {
		Composite fileSelectionArea = createSourceContainer(parent);
		GridDataFactory textBoxFc = GridDataFactory.fillDefaults().hint(200, SWT.DEFAULT).grab(true, false);
		GridDataFactory buttFc = GridDataFactory.swtDefaults().hint(100, SWT.DEFAULT).grab(true, false);

		// clipboard control: radio + read-only preview of the current clipboard +
		// an Edit... button (opens the text in a dialog and copies it back)
		clipboardRadio = toolkit.createButton(fileSelectionArea, clipboardRadioLabel(), SWT.RADIO,
				(e) -> onInputChoice(e, ImportSource.TEXT));
		clipboardRadio.setLayoutData(GridDataFactory.fillDefaults().create());
		clipboardPreviewText = toolkit.createText(fileSelectionArea, "", SWT.BORDER);
		clipboardPreviewText.setEditable(false);
		clipboardPreviewText.setToolTipText("The text currently on the clipboard - click or press Edit... to change it");
		clipboardPreviewText.setText(getClipboardClipped());
		clipboardPreviewText.setLayoutData(textBoxFc.create());
		clipboardPreviewText.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseUp(MouseEvent e) {
				openEditDialog();
			}
		});
		editButton = toolkit.createButton(fileSelectionArea, "Edit...", SWT.PUSH, (e) -> openEditDialog());
		editButton.setLayoutData(buttFc.create());

		// file selector
		fileRadio = toolkit.createButton(fileSelectionArea, fileRadioLabel(), SWT.RADIO,
				(e) -> onInputChoice(e, ImportSource.FILE));
		fileText = toolkit.createText(fileSelectionArea, "", SWT.BORDER);
		fileText.addModifyListener((e) -> {
			fileName = fileText.getText();
			if (inputChoice != ImportSource.FILE) {
				setInputChoice(ImportSource.FILE);
			}
			onInputChoice(null, ImportSource.FILE);
		});
		fileText.setLayoutData(textBoxFc.create());
		browseButton = toolkit.createButton(fileSelectionArea, "Browse...", SWT.PUSH, (e) -> {
			FileDialog fileDialog = new FileDialog(parent.getShell());
			fileDialog.setFileName(fileText.getText());
			String file = fileDialog.open();
			if (file != null) {
				setInputChoice(ImportSource.FILE);
				fileText.setText(file);
				fileText.setSelection(file.length(), file.length());
			}
		});
		browseButton.setLayoutData(buttFc.create());
		// editor controls
		// inputRadio = toolkit.createButton(fileSelectionArea, "Editor",
		// SWT.RADIO,
		// (e) -> onInputChoice(e, ImportSource.INPUT));
		// inputRadio.setToolTipText("Text editor will appear on the next page
		// where you enter the text (or paste");
		// inputRadio.setLayoutData(GridDataFactory.fillDefaults().create());
		// url selector
		/*
		 * !!! RD urlRadio = toolkit.createButton(fileSelectionArea, "URL", SWT.RADIO,
		 * (e) -> onInputChoice(e, ImportSource.URL));
		 * 
		 * urlText = toolkit.createText(fileSelectionArea, "", SWT.BORDER);
		 * urlText.setToolTipText(
		 * "You can select an url by copying it from browser, but only if there is a parser that understans its format it can be parsed"
		 * ); urlText.addModifyListener((e) -> { urlName = urlText.getText(); if
		 * (inputChoice != ImportSource.URL) { setInputChoice(ImportSource.URL); }
		 * onInputChoice(null, ImportSource.URL); });
		 * urlText.setLayoutData(textBoxFc.create());
		 */
	}

	private void openEditDialog() {
		EditTextDialog dialog = new EditTextDialog(new SameShellProvider(getShell()));
		dialog.setContents(getClipboardText());
		if (dialog.open() == Window.OK) {
			String text = dialog.getText();
			CopySupport.runCopy(text);
			clipboardPreviewText.setText(getClipboardClipped());
			setInputChoice(ImportSource.TEXT);
			onInputChoice(null, inputChoice);
		}
	}

	private String getClipboardClipped() {
		String cl = getClipboardText();
		String clipped = cl.length() > 80 ? cl.subSequence(0, 80) + "..." : cl;
		return clipped;
	}

	public void onInputChoice(SelectionEvent event, ImportSource choice) {
		if (event == null || ((Button) event.widget).getSelection()) {
			inputChoice = choice;
			scheduleAutoDetect();
			updateWidgetEnablements();
			updatePageCompletion();
		}
	}

	private Runnable detectPending;

	/** Auto-detect after a short quiet period, so typing a path char by char (or
	 * a burst of setText calls) triggers a single detection pass, not one per
	 * keystroke - each pass reads the file and runs every importer. */
	private void scheduleAutoDetect() {
		if (fileText == null || fileText.isDisposed())
			return;
		if (detectPending == null)
			detectPending = () -> {
				if (fileText != null && !fileText.isDisposed())
					autoDetectFormat();
			};
		fileText.getDisplay().timerExec(-1, detectPending);
		fileText.getDisplay().timerExec(400, detectPending);
	}

	protected void autoDetectFormat() {
		new Job("Detecting format") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				ReportType type = null;
				switch (inputChoice) {
				case FILE:
					if (!fileName.isEmpty())
						type = ReportType.autoDetectType(new File(fileName), types);
					break;
				case TEXT:
					type = ReportType.autoDetectType(getClipboardText(), types);
					break;
				case URL:
					try {
						if (!urlName.isEmpty())
							type = ReportType.autoDetectType(new URL(urlName), types);
					} catch (MalformedURLException e) {
						break;
					}
					break;
				default:
					break;
				}
				if (type == null || type == reportType)
					return Status.OK_STATUS;
				ReportType type2 = type;
				Display.getDefault().syncExec(() -> {
					selectReportType(type2);
					updatePageCompletion();
				});
				// re-parse with the detected format so the preview matches
				performImport(true);
				return Status.OK_STATUS;
			}
		}.schedule();
	}

	@Override
	protected void createOptionsGroupButtons(final Group optionsPanel) {
		// top level group
		Composite buttonComposite = new Composite(optionsPanel, SWT.NONE);
		buttonComposite.setLayout(GridLayoutFactory.swtDefaults().numColumns(3).create());
		// create report type
		toolkit.createLabel(buttonComposite, "Import Format:");
		typeCombo = new Combo(buttonComposite, SWT.READ_ONLY | SWT.DROP_DOWN);
		for (ReportType reportType : types) {
			typeCombo.add(reportType.getLabel());
		}
		typeCombo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				reportType = ImportExportFactory.getByLabel(typeCombo.getText());
				typeCombo.setToolTipText(reportType.getExample());
				performImport(true);
				updateWidgetEnablements();
				updatePageCompletion();
			}
		});
		typeCombo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		toolkit.createHyperlink(buttonComposite, "Example...", SWT.NONE).addHyperlinkListener(new HyperlinkAdapter() {
			@Override
			public void linkActivated(HyperlinkEvent e) {
				if (reportType == null)
					return;
				String example = reportType.getExample();
				if (example == null || example.isEmpty())
					example = "No example available for this format.";
				InputDialog inputDialog = new InputDialog(getShell(), "Example", reportType.getLabel(),
						example, null) {
					@Override
					protected int getShellStyle() {
						return super.getShellStyle() | SWT.RESIZE;
					}

					@Override
					protected int getInputTextStyle() {
						return SWT.BORDER | SWT.MULTI | SWT.READ_ONLY | SWT.WRAP;
					}

					@Override
					protected Control createDialogArea(Composite parent) {
						Control x = super.createDialogArea(parent);
						getText().setLayoutData(GridDataFactory.fillDefaults().hint(600, 400).create());
						return x;
					}
				};
				inputDialog.setBlockOnOpen(false);
				inputDialog.open();
			}
		});
	}

	@Override
	protected String getErrorDialogTitle() {
		return "Error";
	}

	@Override
	public boolean canFlipToNextPage() {
		// restores the standard WizardPage rule (a page with unresolved errors -
		// e.g. no valid import target - must not let the user click past it)
		return getNextPage() != null && isPageComplete();
	}

	@Override
	public org.eclipse.jface.wizard.IWizardPage getNextPage() {
		// "Empty" creates the element straight from Finish - no card-list preview
		return isEmptyMode() ? null : super.getNextPage();
	}

	@Override
	protected boolean validateOptionsGroup() {
		if (isEmptyMode())
			return true;
		if (reportType == null) {
			setErrorMessage("Reporter is not defined");
			return false;
		}
		IImportDelegate worker = reportType.getImportDelegate();
		if (worker == null) {
			setErrorMessage("Importer is not defined for " + reportType.getLabel());
			return false;
		}
		return true;
	}

	@Override
	protected boolean validateSourceGroup() {
		if (isEmptyMode())
			return true;
		if (inputChoice == ImportSource.FILE) {
			if (fileText.getText().isEmpty()) {
				setErrorMessage("Input file is not selected");
				return false;
			}
			File file = new File(fileText.getText().trim());
			if (!file.exists()) {
				setErrorMessage("File does not exists");
				return false;
			}
			if (!file.isFile()) {
				setErrorMessage("Not a file");
				return false;
			}
			return true;
		}
		if (inputChoice == ImportSource.TEXT) {
			if (getClipboardText().trim().isEmpty()) {
				setErrorMessage("The clipboard is empty - copy the deck list first, or use Edit...");
				return false;
			}
			return true;
		}
		if (inputChoice == ImportSource.URL) {
			if (urlText.getText().isEmpty()) {
				//				setErrorMessage("URL is selected but empty");
				return true;
			}
			try {
				new URL(urlText.getText());
			} catch (MalformedURLException e) {
				setErrorMessage("Malformed URL: " + e.getMessage());
				return false;
			}
		}
		return true;
	}

	@Override
	protected void updatePageCompletion() {
		super.updatePageCompletion();
		if (isPageComplete() || getErrorMessage() == null) {
			defaultPrompt(); // set default prompt, otherwise it empty ugly
		}
	}

	@Override
	protected void updateWidgetEnablements() {
		// The radios themselves (Empty/Clipboard/File, in the "New ..." pages)
		// stay enabled always - only the controls that belong to the NOT
		// currently-selected source are greyed, whether that is because a
		// different source radio is selected or because Empty is selected.
		boolean notEmpty = !isEmptyMode();
		fileText.setEnabled(notEmpty && inputChoice == ImportSource.FILE);
		if (browseButton != null && !browseButton.isDisposed())
			browseButton.setEnabled(notEmpty && inputChoice == ImportSource.FILE);
		if (clipboardPreviewText != null && !clipboardPreviewText.isDisposed())
			clipboardPreviewText.setEnabled(notEmpty && inputChoice == ImportSource.TEXT);
		if (editButton != null && !editButton.isDisposed())
			editButton.setEnabled(notEmpty && inputChoice == ImportSource.TEXT);
	}

	public ReportType getReportType() {
		return reportType;
	}

	public CardElement getElement() {
		return element;
	}

	public ImportData getImportData() {
		return importData;
	}

	public ImportSource getInputChoice() {
		return inputChoice;
	}

	public void importRunnable(final boolean preview, final boolean dbImport, IProgressMonitor monitor)
			throws InvocationTargetException {
		SubMonitor submon = SubMonitor.convert(monitor, "Importing", 200);
		try {
			readSource();
			submon.worked(10);
			IImportDelegate worker = reportType.getImportDelegate();
			if (worker == null)
				throw new IllegalArgumentException("Importer is not defined for " + reportType.getLabel());
			boolean resolve = !dbImport;
			if (preview) {
				submon.worked(10);
				// if error occurs importResult.error would be set
				// to exception
				ImportUtils.performPreImport(worker, importData, new CoreMonitorAdapter(submon.split(50)));
				if (!dbImport) {
					ImportUtils.resolve(importData.getList());
				} else {
					ImportUtils.performPreImportWithDb((Collection<IMagicCard>) importData.getList(), new ArrayList<>(),
							importData.getFields());
				}
				submon.worked(10);
			} else {
				if (!importData.isOk()) {
					ImportUtils.performPreImport(worker, importData, new CoreMonitorAdapter(submon.split(50)));
				}
				if (importData.isOk()) {
					Collection<IMagicCard> result = (Collection<IMagicCard>) importData.getList();
					if (resolve) {
						ImportUtils.resolve(importData.getList());
						if (element instanceof CollectionsContainer) {
							// newNameChoice was captured on the UI thread by performImport()
							createNewDeck(newNameChoice != null ? newNameChoice : getSourceBasedName(), isDeckTarget(),
									newVirtualChoice, newUnsortedChoice, newReadOnlyChoice, (CollectionsContainer) element);
						}
						if (!(element instanceof CardCollection)) {
							throw new IllegalArgumentException("Cannot import into " + element);
						}
						Location location = getSelectedLocation();
						importData.setLocation(location);
						ImportUtils.updateLocation(result, location);
						submon.worked(10);
					}
					if (fixErrors(result, dbImport, submon.split(20))) {
						submon.worked(10);
						if (resolve)
							ImportUtils.performImport(result, DataManager.getCardHandler().getLibraryCardStore());
					}
				}
			}
		} catch (InvocationTargetException e) {
			throw e;
		} catch (Exception e) {
			throw new InvocationTargetException(e);
		} finally {
			monitor.done();
		}
		if (importData.isOk())
			return;
		if (importData.getError() != null)
			throw new InvocationTargetException(importData.getError());
		else
			throw new InvocationTargetException(new IllegalArgumentException("Cannot import"));
	}

	@Override
	public void setVisible(boolean visible) {
		if (visible) {
			refreshClipboardPreview();
			if (inputChoice == ImportSource.TEXT)
				scheduleAutoDetect();
			updatePageCompletion();
		}
		super.setVisible(visible);
	}

	private void refreshClipboardPreview() {
		if (clipboardPreviewText != null && !clipboardPreviewText.isDisposed())
			clipboardPreviewText.setText(getClipboardClipped());
	}
}
