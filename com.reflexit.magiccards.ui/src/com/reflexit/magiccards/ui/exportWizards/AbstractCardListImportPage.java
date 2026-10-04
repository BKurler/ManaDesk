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
 *     Rémi Dutil (2026) - added a 4th "Website" source: radio + read-only
 *                         preview + "Browse Website..." button, opening
 *                         BrowseWebsiteDialog (a real, navigable embedded
 *                         browser the user logs into/navigates themselves).
 *                         Its captured page text is played back exactly like
 *                         Clipboard text - readSource() does no re-fetch (the
 *                         dialog is already closed) and autoDetectFormat()
 *                         reuses the same content-sniffing
 *                         ReportType.autoDetectType(String, ...) overload, so
 *                         FreeformImportDelegate and friends need no changes.
 *     Rémi Dutil (2026) - the Website address field is now editable (not a
 *                         read-only preview) and does NOT open the browse
 *                         dialog on click - only the button does; the field
 *                         is meant to be typed/pasted into directly, and
 *                         Browse Website... now navigates straight to
 *                         whatever address is in it.
 *     Rémi Dutil (2026) - debug trace: autoDetectFormat()'s BROWSER case logs
 *                         the sniffed text length and the format it detected
 *                         (see also DeckTextExtractor/BrowseWebsiteDialog's
 *                         own traces) - needed after a report that a real
 *                         site's page was still "importing everything".
 *     Rémi Dutil (2026) - "Also create a Sideboard/Extra" (NewDeckPage) only
 *                         ever took effect via createEmptyExtras(), called
 *                         from createEmptyElement()'s Empty-mode Finish path
 *                         - completely ignored for an actual import
 *                         (Website/Clipboard/File), even though the imported
 *                         text's own "Sideboard" cards would still land in a
 *                         sideboard location automatically (see
 *                         ImportUtils.updateLocation/createDecks) - just
 *                         without the checkbox ever having created it
 *                         explicitly, or its "virtual" flag being consistent.
 *                         Added wantSideboard()/wantExtra() (read on the UI
 *                         thread by performImport(), same as wantVirtual())
 *                         and createImportExtras(), called right after
 *                         createNewDeck() in importRunnable() - so the
 *                         checkbox now has the same effect whether the deck
 *                         is created empty or from an import.
 *     Rémi Dutil (2026) - real use turned up two problems with the "Found N
 *                         record(s)..." status: (1) autoDetectFormat()
 *                         skipped its whole re-parse whenever the newly
 *                         detected format was UNCHANGED from before (the
 *                         common case re-importing from a Website, since the
 *                         same site keeps detecting as the same format every
 *                         time) - the wizard kept showing the PREVIOUS
 *                         parse's stale counts (often "Found 0 record(s)")
 *                         even though the import had actually worked; now
 *                         always re-parses once a format is detected, and
 *                         refreshes the displayed status AFTER the re-parse
 *                         instead of before it. (2) the status itself moved
 *                         out of the wizard's title-area banner (setMessage())
 *                         into a new statusLabel below the Options group -
 *                         reads more naturally right next to the format it
 *                         describes, and fills what had been unexplained
 *                         blank space reserved by the framework's Options
 *                         group layout. The banner now shows a static
 *                         description instead, reserved for validation
 *                         errors (setErrorMessage()).
 *     Rémi Dutil (2026) - added onSideboardDetected() (called from
 *                         openBrowseWebsiteDialog() once a page is browsed,
 *                         with whether the captured text includes a
 *                         Sideboard section - a real one or, per
 *                         DeckTextExtractor's own new Commander handling, a
 *                         Commander deck's commander card) and
 *                         gateSideboardOnImport(). Real sideboard-tagged
 *                         cards had always survived import regardless of
 *                         "Also create a Sideboard" - that checkbox only
 *                         ever controlled whether an EMPTY sideboard
 *                         sibling got pre-created - but per an explicit
 *                         request, NewDeckPage now auto-checks it on
 *                         detection AND actually gates the import on it
 *                         (unchecking it now excludes those cards, not just
 *                         the empty sibling) - gateSideboardOnImport()
 *                         defaults false so every OTHER page (import into
 *                         an existing deck, a collection - none of which
 *                         have a live "Also create a Sideboard" checkbox at
 *                         all) keeps importing sideboard cards exactly as
 *                         before; wantSideboard()'s own default (false) on
 *                         those pages reflects "no such concept here", not
 *                         a real choice to drop cards, and must never be
 *                         read as gating import on its own.
 *     Rémi Dutil (2026) - added onDeckMetaDetected() (called from
 *                         openBrowseWebsiteDialog(), mirroring
 *                         onSideboardDetected() above) with the page's own
 *                         best-effort extracted deck title/format (see
 *                         DeckTextExtractor#detectDeckMeta()). Only
 *                         NewDeckPage overrides it, same reasoning as
 *                         onSideboardDetected() - every other page has
 *                         neither a Name field to fill nor a Default Format
 *                         combo to update, so the default (nothing) is
 *                         correct there.
 *     Rémi Dutil (2026) - added wantFormat() (same shape as wantSideboard()/
 *                         wantExtra() - read on the UI thread by
 *                         performImport(), applied via createNewDeck()'s new
 *                         defaultFormat parameter for both the Empty-mode
 *                         and import-mode creation paths) so NewDeckPage's
 *                         new Default Format combo actually takes effect on
 *                         the created deck, not just sit there unused.
 *     Rémi Dutil (2026) - defaultPrompt()'s status now reads "Total N
 *                         (unique M) card(s) found" instead of "Found N
 *                         record(s)" - matches the "Total N (unique M)"
 *                         phrasing used everywhere else in ManaDesk; "record"
 *                         also undercounted what the user actually expects
 *                         to see, since each row already represents one
 *                         unique card with its own quantity, not one row per
 *                         physical copy.
 *     Rémi Dutil (2026) - dropped defaultPrompt()'s trailing "Press
 *                         'Example...' to see the expected layout, or Next
 *                         to preview." - redundant once the status line
 *                         itself already shows the real recognized count,
 *                         per an explicit request.
 *     Rémi Dutil (2026) - hasSideboardMarker() moved to DeckTextExtractor#
 *                         hasSideboardSection() (public) so
 *                         BrowseWebsiteDialog can share it for a live
 *                         "Sideboard detected" indicator too.
 *     Rémi Dutil (2026) - URL source: a no-web failure reports
 *                         BrowseWebsiteDialog.WEB_NOT_ACCESSIBLE instead of
 *                         the raw connection error.
 *     Rémi Dutil (2026) - the Website format-detection trace is disabled
 *                         (kept, behind DeckTextExtractor.TRACE_WEB_IMPORT).
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
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.forms.events.HyperlinkAdapter;
import org.eclipse.ui.forms.events.HyperlinkEvent;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.MagicException;
import com.reflexit.magiccards.core.MagicLogger;
import com.reflexit.magiccards.core.exports.DeckTextExtractor;
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
import com.reflexit.magiccards.core.model.abs.ICard;
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
import com.reflexit.magiccards.ui.dialogs.BrowseWebsiteDialog;
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
	private boolean newSideboardChoice;
	private boolean newExtraChoice;
	private String newNameChoice;
	private String newFormatChoice;
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
	protected Button websiteRadio;
	private Button browseWebsiteButton;
	/** editable: paste/type a URL, or filled in with the page last imported from */
	private Text websiteText;
	/** full document.body.innerText, captured when BrowseWebsiteDialog's OK fires */
	private String capturedWebsiteText;
	/** browser.getUrl() at the same moment - for the preview field and diagnostics */
	private String capturedWebsiteUrl;
	/** shows defaultPrompt()'s dynamic "Found N record(s)..." status - below the
	 *  Options group, not the wizard's title-area banner (see defaultPrompt()) */
	private Label statusLabel;

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

	/** Whether a brand-new deck should also get an empty Sideboard sibling -
	 *  read on the UI thread before the background import job runs (see
	 *  {@link #wantVirtual()}). Only NewDeckPage (which has that checkbox)
	 *  overrides this. */
	protected boolean wantSideboard() {
		return false;
	}

	/** Whether a brand-new deck should also get an empty Extra sibling - see
	 *  {@link #wantSideboard()}. */
	protected boolean wantExtra() {
		return false;
	}

	/** A brand-new deck's own Default Format ("Standard", "Modern", ...) - the
	 *  format the Legality tab validates it against by default - or {@code
	 *  null} to leave it unset. Read on the UI thread before the background
	 *  import job runs (see {@link #wantVirtual()}). Only NewDeckPage (which
	 *  has the combo) overrides this; every other page (a collection has no
	 *  notion of legality) leaves it null. */
	protected String wantFormat() {
		return null;
	}

	/** Whether {@link #wantSideboard()} being false should actually EXCLUDE
	 *  the imported text's own sideboard-tagged cards (see importRunnable())
	 *  instead of importing them regardless, as every page does by default.
	 *  Only NewDeckPage (the only page with a live, user-facing "Also create
	 *  a Sideboard" checkbox the user can deliberately uncheck) overrides
	 *  this true - {@link #wantSideboard()}'s own default (false) on every
	 *  OTHER page (importing into an existing deck, a collection) reflects
	 *  "this page has no such concept", not a real choice to drop cards, and
	 *  must never be read that way. */
	protected boolean gateSideboardOnImport() {
		return false;
	}

	/** Called after browsing a page (see openBrowseWebsiteDialog()) with
	 *  whether the captured/extracted text includes a Sideboard section - a
	 *  real one, or (see DeckTextExtractor's own Commander handling) a
	 *  Commander deck's own commander card, which by MTG convention belongs
	 *  in the sideboard pile. Only NewDeckPage (which has the checkbox)
	 *  overrides this, to auto-CHECK "Also create a Sideboard" - never auto-
	 *  unchecks it, so re-browsing to a page with no sideboard never
	 *  silently undoes a choice the user may have made deliberately; per the
	 *  user's own explicit request, unchecking it afterward is how they opt
	 *  out of importing sideboard cards at all (see gateSideboardOnImport()).
	 *  Default: nothing. */
	protected void onSideboardDetected(boolean hasSideboard) {
		// NewDeckPage overrides
	}

	/** Called after browsing a page (see openBrowseWebsiteDialog()) with the
	 *  page's own best-effort extracted deck title/format (see
	 *  BrowseWebsiteDialog#getCapturedTitle()/getCapturedFormat(), backed by
	 *  DeckTextExtractor#detectDeckMeta()) - either may be {@code null} if
	 *  nothing plausible was found. Only NewDeckPage (which has a Name field
	 *  and a Default Format combo) overrides this, to fill the Name field
	 *  when it's still empty and update the Default Format combo when a
	 *  format was actually found (never resets it back to "Standard" on a
	 *  miss - a miss means "couldn't tell", not "this deck is Standard").
	 *  Default: nothing. */
	protected void onDeckMetaDetected(String title, String format) {
		// NewDeckPage overrides
	}

	/** Called right after a brand-new element is created during an import (as
	 *  opposed to {@link #createEmptyElement()}'s Empty-mode path, which
	 *  creates its own Sideboard/Extra siblings directly) - lets "New ..."
	 *  pages also create them for an IMPORTED deck, so "Also create a
	 *  Sideboard/Extra" isn't silently ignored just because Contents was
	 *  Website/Clipboard/File instead of Empty. Runs on the background
	 *  import job's thread, like {@link #createNewDeck} itself - takes the
	 *  CACHED choices (wantSideboard()/wantExtra()/wantVirtual() were read
	 *  on the UI thread by {@link #performImport}), never the live
	 *  checkboxes directly. Default: nothing. */
	protected void createImportExtras(CollectionsContainer parent, boolean wantSideboard, boolean wantExtra,
			boolean virtual) {
		// concrete pages (NewDeckPage) override
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
			newSideboardChoice = wantSideboard();
			newExtraChoice = wantExtra();
			newNameChoice = getNewElementName();
			newFormatChoice = wantFormat();
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
			String defaultFormat, CollectionsContainer resource) {
		int attempts = 1000;
		Location newloc = Location.createLocation(base);
		while (resource.contains(newloc) && attempts-- > 0) {
			newloc = Location.createLocation(base + new Random().nextInt(1000));
		}
		if (attempts <= 0)
			throw new IllegalArgumentException("Cannot generate deck name");
		CardCollection created = new CardCollection(newloc.getBaseFileName(), resource, isDeck, virtual, unsorted);
		created.persistInitialSettings(isDeck, virtual, unsorted);
		if (readOnly || (defaultFormat != null && !defaultFormat.isEmpty())) {
			try {
				IStorageInfo si = created.getStorageInfo();
				if (si != null) {
					if (readOnly)
						si.setReadOnly(true);
					if (defaultFormat != null && !defaultFormat.isEmpty())
						si.setDefaultFormat(defaultFormat);
				}
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
				try {
					text = WebUtils.openUrlText(new URL(urlName));
				} catch (IOException e) {
					if (WebUtils.isWebUnavailable(e))
						throw new IOException(BrowseWebsiteDialog.WEB_NOT_ACCESSIBLE, e);
					throw e;
				}
				importData.setText(text);
			}
			break;
		case BROWSER:
			// no re-fetch: BrowseWebsiteDialog is already closed by the time this
			// runs, so there is no live page left to re-read - just play back the
			// text captured at the moment the user clicked "Import this page"
			importData.setProperty(inputChoice.name(), capturedWebsiteUrl);
			if (capturedWebsiteText != null && !capturedWebsiteText.isEmpty()) {
				text = capturedWebsiteText;
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
		setMessage("Select the source and format below, then Next to preview.");
		initializeDialogUnits(parent);
		Composite composite = new Composite(parent, SWT.NULL);
		composite.setLayout(new GridLayout());
		composite.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL));
		composite.setFont(parent.getFont());
		setControl(composite);
		createDestinationGroup(composite);
		createResourcesGroup(composite);
		createOptionsGroup(composite);
		// below Options, not the wizard's title-area banner above - reads more
		// naturally right next to the format it describes, and this is also
		// where a report that was previously blank space (reserved by the
		// framework's Options group layout for content this page never used)
		// turned out to be
		statusLabel = new Label(composite, SWT.WRAP);
		statusLabel.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).create());
		restoreWidgetValues();
		updateWidgetEnablements();
		defaultPrompt();
		setPageComplete(determinePageCompletion());
		// setErrorMessage(null); // should not initially have error message
		PlatformUI.getWorkbench().getHelpSystem().setHelp(composite, MagicUIActivator.PLUGIN_ID + ".export");
	}

	/** Shows the dynamic "You have selected '&lt;format&gt;'... Total N
	 *  (unique M) card(s) found..." status in {@link #statusLabel}, below the
	 *  Options group - moved out of the wizard's title-area banner
	 *  (setMessage()), which is now a static description set once in
	 *  createControl() and otherwise reserved for validation errors
	 *  (setErrorMessage()). The "Total N (unique M)" phrasing matches every
	 *  other card count shown elsewhere in ManaDesk (deck/collection views,
	 *  BrowseWebsiteDialog's own live count) - each {@code toImport} entry is
	 *  already one row PER UNIQUE CARD with its own {@code getCount()}
	 *  quantity, not one row per physical copy, so a bare "N record(s)" read
	 *  as a much smaller, unfamiliar number (e.g. a 100-card deck with a
	 *  21-copy Island showing "79 record(s)") instead of the total physical
	 *  card count the user actually expects to recognize. */
	private void defaultPrompt() {
		if (reportType == null)
			reportType = ImportExportFactory.TEXT_DECK_CLASSIC;
		String mess = "You have selected '" + reportType.getLabel() + "' format.\n";
		if (importData.getError() != null) {
			mess += "Warning: cannot parse data (" + importData.getError().getMessage() + "). ";
		} else {
			int errcount = importData.getErrorCount();
			int unique = importData.size();
			int total = 0;
			for (ICard card : importData.getList())
				if (card instanceof MagicCardPhysical)
					total += ((MagicCardPhysical) card).getCount();
			mess += "Total " + total + " (unique " + unique + ") card(s) found, " + errcount + " error(s).";
		}
		if (statusLabel != null && !statusLabel.isDisposed()) {
			statusLabel.setText(mess);
			statusLabel.getParent().layout();
		}
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
		// only File, Clipboard and Website have UI - an old "URL" setting would
		// NPE later. A restored BROWSER choice with nothing captured yet is
		// harmless: validateSourceGroup() blocks Next until the user browses again.
		if (inputChoice != ImportSource.FILE && inputChoice != ImportSource.TEXT
				&& inputChoice != ImportSource.BROWSER)
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
		if (websiteRadio != null)
			websiteRadio.setSelection(inputChoice == ImportSource.BROWSER);
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
		case BROWSER:
			return (capturedWebsiteUrl == null || capturedWebsiteUrl.isEmpty()) ? "website (none captured)"
					: "website \"" + capturedWebsiteUrl + "\"";
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

		// website control: radio + an editable address field (paste/type a URL
		// here, or it fills in with the page last imported from) + a Browse
		// Website... button that opens BrowseWebsiteDialog - a real, navigable
		// embedded browser - already navigated to whatever URL is in the field
		// above, if any. Clicking the field itself does NOT open the dialog
		// (unlike the Clipboard preview above) - it is meant to be typed/pasted
		// into directly, so a click-triggered popup would fight the user.
		websiteRadio = toolkit.createButton(fileSelectionArea, "Website", SWT.RADIO,
				(e) -> onInputChoice(e, ImportSource.BROWSER));
		websiteRadio.setLayoutData(GridDataFactory.fillDefaults().create());
		websiteText = toolkit.createText(fileSelectionArea, "", SWT.BORDER);
		websiteText.setToolTipText(
				"The deck's page address - paste or type it here, then press Browse Website... to load it automatically");
		websiteText.addModifyListener((e) -> {
			if (inputChoice != ImportSource.BROWSER) {
				setInputChoice(ImportSource.BROWSER);
			}
			onInputChoice(null, ImportSource.BROWSER);
		});
		websiteText.setLayoutData(textBoxFc.create());
		browseWebsiteButton = toolkit.createButton(fileSelectionArea, "Browse Website...", SWT.PUSH,
				(e) -> openBrowseWebsiteDialog());
		browseWebsiteButton.setToolTipText("Open the address above in an embedded browser, so you can navigate, "
				+ "log in if needed, and import the deck once you can see it");
		browseWebsiteButton.setLayoutData(buttFc.create());
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

	private void openBrowseWebsiteDialog() {
		BrowseWebsiteDialog dialog = new BrowseWebsiteDialog(getShell(), websiteText.getText().trim());
		if (dialog.open() == Window.OK) {
			capturedWebsiteText = dialog.getCapturedText();
			capturedWebsiteUrl = dialog.getCapturedUrl();
			websiteText.setText(capturedWebsiteUrl == null ? "" : capturedWebsiteUrl);
			setInputChoice(ImportSource.BROWSER);
			onInputChoice(null, inputChoice);
			onSideboardDetected(DeckTextExtractor.hasSideboardSection(capturedWebsiteText));
			onDeckMetaDetected(dialog.getCapturedTitle(), dialog.getCapturedFormat());
		}
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
				case BROWSER:
					// content-sniffing overload, same as TEXT above - NOT the URL
					// overload (regex-matched, only wired to ScryFallImportDelegate,
					// and does its own re-fetch): the page is already captured.
					if (capturedWebsiteText != null && !capturedWebsiteText.isEmpty()) {
						type = ReportType.autoDetectType(capturedWebsiteText, types);
						if (DeckTextExtractor.TRACE_WEB_IMPORT)
							MagicLogger.log("AbstractCardListImportPage: Website source, " + capturedWebsiteText.length()
								+ " char(s) sniffed -> detected format "
								+ (type == null ? "none" : type.getLabel()));
					}
					break;
				default:
					break;
				}
				if (type == null)
					return Status.OK_STATUS;
				if (type != reportType) {
					ReportType type2 = type;
					Display.getDefault().syncExec(() -> selectReportType(type2));
				}
				// re-parse so the preview reflects the newly detected/captured
				// content - even when the detected format is UNCHANGED from
				// before (the common case re-importing from a Website: the
				// same site usually keeps detecting as the same format every
				// time), the underlying text just changed and must still be
				// re-parsed, or the wizard is left showing the PREVIOUS
				// parse's stale "Found 0 record(s)" message - previously this
				// whole re-parse was skipped whenever the type didn't change
				performImport(true);
				// refresh the displayed message AFTER the re-parse actually
				// updated importData - previously this ran BEFORE performImport(true)
				// (inside the same syncExec as selectReportType above), so it
				// always showed the PREVIOUS parse's counts, never the new one
				Display.getDefault().syncExec(() -> updatePageCompletion());
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
		if (inputChoice == ImportSource.BROWSER) {
			if (capturedWebsiteText == null || capturedWebsiteText.trim().isEmpty()) {
				setErrorMessage("No page captured yet - click Browse Website... and press Import this page");
				return false;
			}
			return true;
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
		if (websiteText != null && !websiteText.isDisposed())
			websiteText.setEnabled(notEmpty && inputChoice == ImportSource.BROWSER);
		if (browseWebsiteButton != null && !browseWebsiteButton.isDisposed())
			browseWebsiteButton.setEnabled(notEmpty && inputChoice == ImportSource.BROWSER);
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
							CollectionsContainer newParent = (CollectionsContainer) element;
							createNewDeck(newNameChoice != null ? newNameChoice : getSourceBasedName(), isDeckTarget(),
									newVirtualChoice, newUnsortedChoice, newReadOnlyChoice, newFormatChoice, newParent);
							createImportExtras(newParent, newSideboardChoice, newExtraChoice, newVirtualChoice);
						}
						if (!(element instanceof CardCollection)) {
							throw new IllegalArgumentException("Cannot import into " + element);
						}
						Location location = getSelectedLocation();
						importData.setLocation(location);
						ImportUtils.updateLocation(result, location);
						// the imported text's own sideboard-tagged cards
						// (a real Sideboard section, or - see
						// DeckTextExtractor's own Commander handling - a
						// Commander deck's commander card) otherwise always
						// survive regardless of "Also create a Sideboard",
						// which by itself only controls whether an EMPTY
						// sideboard sibling gets pre-created (see
						// wantSideboard()'s own header). Per an explicit
						// request: on a page where that checkbox is a real,
						// user-facing choice (gateSideboardOnImport()),
						// unchecking it (after DeckTextExtractor auto-
						// checked it on detection - see onSideboardDetected)
						// is how the user opts out of the sideboard/
						// commander entirely, importing just the main deck.
						if (gateSideboardOnImport() && !newSideboardChoice)
							result.removeIf(
									c -> c instanceof MagicCardPhysical && ((MagicCardPhysical) c).isSideboard());
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
