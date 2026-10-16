/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - Filter / Reset Filter / Unsort like the Cards tab; the
 *                         sort and filter ARE the Cards tab's (shared both ways),
 *                         so Save As exports the list as the Cards tab shows it
 *     Rémi Dutil (2026) - Save As on an existing file: Overwrite or Append (same
 *                         rules as the Export wizard's "Append to the existing
 *                         file" - ExportAppender)
 */

package com.reflexit.magiccards.ui.views.analyzers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.Path;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.action.StatusLineContributionItem;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.preference.IPersistentPreferenceStore;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationAdapter;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.dialogs.PreferencesUtil;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.MagicLogger;
import com.reflexit.magiccards.core.exports.AbstractExportDelegate;
import com.reflexit.magiccards.core.exports.CompactHtmlExportDelegate;
import com.reflexit.magiccards.core.exports.ExportAppender;
import com.reflexit.magiccards.core.exports.IExportDelegate;
import com.reflexit.magiccards.core.exports.ImportExportFactory;
import com.reflexit.magiccards.core.exports.ReportType;
import com.reflexit.magiccards.core.model.FilterField;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardFilter;
import com.reflexit.magiccards.core.model.SortOrder;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.model.storage.ICardStore;
import com.reflexit.magiccards.core.model.storage.IDbCardStore;
import com.reflexit.magiccards.core.model.storage.IFilteredCardStore;
import com.reflexit.magiccards.core.model.storage.MemoryFilteredCardStore;
import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.actions.ImageAction;
import com.reflexit.magiccards.ui.actions.RefreshAction;
import com.reflexit.magiccards.ui.actions.SortByAction;
import com.reflexit.magiccards.ui.dialogs.CardFilterDialog;
import com.reflexit.magiccards.ui.dialogs.DeckFilterDialog;
import com.reflexit.magiccards.ui.preferences.DeckViewPreferencePage;
import com.reflexit.magiccards.ui.preferences.PreferenceConstants;
import com.reflexit.magiccards.ui.preferences.PreferenceInitializer;
import com.reflexit.magiccards.ui.utils.StoredSelectionProvider;
import com.reflexit.magiccards.ui.views.columns.MagicColumnCollection;

public class ExportDeckPage extends AbstractDeckPage {
	private Browser textBrowser;
	private Composite exportArea;
	private ISelectionProvider selProvider = new StoredSelectionProvider();
	private IFilteredCardStore<IMagicCard> fstore;
	private IAction save;
	private String textResult;
	protected ReportType reportType;
	private Text textArea;
	StatusLineContributionItem a;
	private ImageAction sideboard;
	private ImageAction extra;
	private ImageAction header;
	private boolean includeSideboard = true;
	private boolean includeExtra = false;
	private boolean secondaryDefaultsApplied = false;
	private boolean includeHeader = true;
	private Action actionShowPrefs;
	private MagicCardFilter filter;
	private SortByAction actionSort;
	/** Holds the Cards tab's sort only - the Sort By menu edits it in place.
	 *  The store's filter gets a copy plus the section keepers (setFStore). */
	private final MagicCardFilter sortHolder = new MagicCardFilter();
	private ImageAction actionUnsort;
	private ImageAction actionFilter;
	private ImageAction actionResetFilter;
	private ImageAction actionRefresh;
	private Combo typeCombo;

	private void addComboType(ReportType rt) {
		typeCombo.add(rt.getLabel());
		typeCombo.setData(rt.getLabel(), rt);
	}

	@Override
	public void createPageContents(Composite area) {
		typeCombo = new Combo(area, SWT.READ_ONLY | SWT.DROP_DOWN);
		typeCombo.setLayoutData(GridDataFactory.fillDefaults().align(SWT.BEGINNING, SWT.CENTER).create());
		Collection<ReportType> types = ImportExportFactory.getExportTypes();
		for (ReportType rt : types) {
			addComboType(rt);
		}
		typeCombo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				setReportType(ImportExportFactory.getByLabel(typeCombo.getText()));
			}
		});
		exportArea = new Composite(area, SWT.NONE);
		exportArea.setLayoutData(GridDataFactory.fillDefaults().grab(true, true).create());
		exportArea.setFont(area.getFont());
		stackLayout = new StackLayout();
		exportArea.setLayout(stackLayout);
		try {
			// if (true)
			// throw new SWTError();
			this.textBrowser = new Browser(exportArea, SWT.WRAP | SWT.INHERIT_FORCE);
			this.textBrowser.setFont(exportArea.getFont());
			textBrowser.addLocationListener(new LocationAdapter() {
				@Override
				public void changing(LocationEvent event) {
					String location = event.location;
					if (location.startsWith(CompactHtmlExportDelegate.CARD_URI)) {
						location = location.substring(CompactHtmlExportDelegate.CARD_URI.length());
						if (location.endsWith("/")) {
							location = location.substring(0, location.length() - 1);
						}
						if (location.indexOf('?') > 0) {
							location = location.substring(location.indexOf('?') + 1);
						}
						String params[] = location.split("&");
						for (int i = 0; i < params.length; i++) {
							String string = params[i];
							if (string.startsWith(CompactHtmlExportDelegate.CARDID)) {
								event.doit = false;
								String cardId = string.substring(CompactHtmlExportDelegate.CARDID.length());
								IDbCardStore magicDBStore = DataManager.getCardHandler().getMagicDBStore();
								IMagicCard card = (IMagicCard) magicDBStore.getCard(cardId);
								if (card != null)
									selProvider.setSelection(new StructuredSelection(card));
							}
						}
						event.doit = false;
					}
				}
			});
		} catch (Throwable e) {
			MagicLogger.log(e);
			textBrowser = null;
		}
		textArea = new Text(exportArea, SWT.READ_ONLY | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
		setTopControl();
	}

	protected void initReportTypes() {
		Collection<ReportType> types = ImportExportFactory.getExportTypes();
		for (final ReportType rt : types) {
			if (rt.getLabel().contains("HTML")) {
				reportType = rt; // last
			}
		}
		setReportType(reportType);
		typeCombo.setText(reportType.getLabel());
	}

	protected void setTopControl() {
		// render in the embedded browser for any markup export (html) - the old
		// check only looked for "HTML" in the label, so "Print Sideboard List"
		// (and any other html export not named "... HTML ...") fell through to
		// the raw-text area
		boolean markup = reportType != null && textBrowser != null
				&& ("html".equalsIgnoreCase(reportType.getExtension()) || reportType.getLabel().contains("HTML"));
		Control top = markup ? textBrowser : textArea;
		if (stackLayout.topControl != top) {
			stackLayout.topControl = top;
			// a StackLayout only swaps on its own composite's layout() - without
			// this the previous control (e.g. the browser) stays visible
			if (exportArea != null && !exportArea.isDisposed())
				exportArea.layout();
		}
	}

	private boolean isSecondaryView() {
		return store != null && store.getLocation() != null
				&& (store.getLocation().isSideboard() || store.getLocation().isExtra());
	}

	@Override
	public void fillLocalPullDown(IMenuManager manager) {
		super.fillLocalPullDown(manager);
		// manager.add(actionRefresh);
	}

	@Override
	public void fillLocalToolBar(IToolBarManager manager) {
		manager.add(this.save);
		manager.add(actionSort);
		manager.add(actionUnsort);
		manager.add(actionFilter);
		manager.add(actionResetFilter);
		manager.add(this.actionShowPrefs);
		manager.add(this.sideboard);
		manager.add(this.extra);
		// manager.add(this.header);
		manager.add(new Separator());
		manager.add(actionRefresh);
		super.fillLocalToolBar(manager);
	}

	@Override
	protected void makeActions() {
		this.save = new ImageAction("Save As...", "icons/clcl16/save.png", IAction.AS_PUSH_BUTTON) {
			@Override
			public void run() {
				saveAs();
			}
		};
		this.sideboard = new ImageAction("Include Sideboard", "icons/obj16/include_sideboard16.png",
				IAction.AS_CHECK_BOX) {
			@Override
			public void run() {
				triggerSideboard(!isInludeSideboard());
			}
		};
		this.sideboard.setChecked(isInludeSideboard());
		this.extra = new ImageAction("Include Extra", "icons/obj16/include_extra16.png", IAction.AS_CHECK_BOX) {
			@Override
			public void run() {
				triggerExtra(!isInludeExtra());
			}
		};
		this.extra.setChecked(isInludeExtra());
		this.header = new ImageAction("Include Header", "icons/obj16/header16.png", IAction.AS_CHECK_BOX) {
			@Override
			public void run() {
				triggerHeader(!isInludeHeader());
			}
		};
		this.header.setChecked(isInludeHeader());
		this.actionShowPrefs = new ImageAction("Preferences...", "icons/clcl16/gear.png", IAction.AS_PUSH_BUTTON) {
			@Override
			public void run() {
				String id = DeckViewPreferencePage.class.getName();
				if (id != null) {
					PreferenceDialog dialog = PreferencesUtil.createPreferenceDialogOn(getArea().getShell(), id,
							new String[] { id }, null);
					dialog.open();
					refresh();
				}
			}
		};
		// no store given: the menu edits sortHolder in place, saveSort() persists it
		this.actionSort = new SortByAction(new MagicColumnCollection(null), sortHolder, null, this::saveSort);
		this.actionUnsort = new ImageAction("Unsort", "icons/clcl16/unsort.png",
				"Clear the sort (main deck, sideboard, extra - each by name)", () -> {
					sortHolder.getSortOrder().clear();
					saveSort();
				});
		this.actionFilter = new ImageAction("Filter...", "icons/clcl16/filter_ps.png",
				"Choose which cards to export", this::runFilter);
		this.actionResetFilter = new ImageAction("Reset Filter", "icons/clcl16/reset_filter.gif",
				"Resets the filter to default values", () -> {
					IPersistentPreferenceStore st = getCardsFilterStore();
					if (st != null)
						PreferenceInitializer.setToDefault(st);
					refresh();
				});
		this.actionRefresh = new RefreshAction(this::activate);
		initReportTypes();
	}

	/**
	 * The Cards tab's filter: the export shows (and saves) the list as the Cards
	 * tab shows it, and a filter or sort changed here is the Cards tab's too.
	 * Each tab re-reads them when it is activated.
	 */
	private IPersistentPreferenceStore getCardsFilterStore() {
		return getDeckView() != null ? getDeckView().getMainFilterPreferenceStore() : null;
	}

	/** The Cards tab's own sort (this deck's / collection's, not shared). */
	private IPersistentPreferenceStore getCardsSortStore() {
		return getDeckView() != null ? getDeckView().getMainPresentationPreferenceStore() : null;
	}

	/** Saves the sort (edited in place by Sort By / Unsort) as the Cards tab's, and refreshes. */
	private void saveSort() {
		IPersistentPreferenceStore st = getCardsSortStore();
		if (st != null) {
			st.setValue(PreferenceConstants.SORT_ORDER, sortHolder.getSortOrder().getStringValue());
			try {
				st.save();
			} catch (IOException e) {
				MagicUIActivator.log(e);
			}
		}
		refresh();
	}

	private void runFilter() {
		IPersistentPreferenceStore st = getCardsFilterStore();
		if (st == null)
			return;
		CardFilterDialog dialog = new DeckFilterDialog(getArea().getShell(), st);
		dialog.setOnApply(this::refresh);
		if (dialog.open() == Window.OK)
			refresh();
	}

	private static HashMap<String, String> storeToMap(IPreferenceStore st) {
		HashMap<String, String> map = new HashMap<>();
		for (String id : FilterField.getAllIds()) {
			String value = st.getString(id);
			if (value != null && value.length() > 0)
				map.put(id, value);
		}
		return map;
	}

	/** Grey out Unsort / Reset Filter when there is nothing to undo. */
	private void syncActionState() {
		if (actionUnsort != null)
			actionUnsort.setEnabled(!sortHolder.getSortOrder().isEmpty());
		if (actionResetFilter != null) {
			IPersistentPreferenceStore st = getCardsFilterStore();
			String[] names = st == null ? null : PreferenceInitializer.preferenceNames(st);
			actionResetFilter.setEnabled(names != null && names.length > 0);
		}
	}

	protected boolean isInludeSideboard() {
		return includeSideboard;
	}

	protected boolean isInludeExtra() {
		return includeExtra;
	}

	public void triggerSideboard(boolean mode) {
		includeSideboard = mode;
		sideboard.setChecked(mode);
		updateSecondaryActionLabels();
		refresh();
	}

	public void triggerExtra(boolean mode) {
		includeExtra = mode;
		extra.setChecked(mode);
		updateSecondaryActionLabels();
		refresh();
	}

	private boolean isSideboardOnlyExport() {
		IExportDelegate<?> d = reportType != null ? reportType.getExportDelegate() : null;
		return d != null && d.isSideboardOnly();
	}

	private boolean isMultiLocationExport() {
		IExportDelegate<?> d = reportType != null ? reportType.getExportDelegate() : null;
		return d == null || d.isMultipleLocationSupported();
	}

	/**
	 * Sets the enabled / checked / tooltip state of the two include toggles.
	 * <p>
	 * Normal exports: each toggle acts on the list that is <b>not</b> the one
	 * currently shown - from the main deck the sideboard button adds the
	 * sideboard and the extra button adds the extra; from the sideboard view
	 * the sideboard button adds the <i>main deck</i> instead; from the extra
	 * view the extra button adds the main deck. So both stay useful in every
	 * view.
	 * <p>
	 * Sideboard-only exports (the printable list): the sideboard is always in
	 * and "add main deck" is meaningless, so the sideboard button is checked +
	 * disabled and only the extra button stays live.
	 */
	private void updateSecondaryActionLabels() {
		Location loc = store != null ? store.getLocation() : null;
		boolean onSideboard = loc != null && loc.isSideboard();
		boolean onExtra = loc != null && loc.isExtra();

		if (isSideboardOnlyExport()) {
			if (sideboard != null) {
				sideboard.setChecked(true);
				sideboard.setEnabled(false);
				sideboard.setToolTipText("Sideboard (always included in this export)");
			}
			if (extra != null) {
				extra.setEnabled(true);
				extra.setChecked(includeExtra);
				extra.setToolTipText(includeExtra ? "Do not include extra" : "Include extra");
			}
			return;
		}

		boolean multi = isMultiLocationExport();
		if (sideboard != null) {
			sideboard.setEnabled(multi);
			sideboard.setChecked(includeSideboard);
			if (onSideboard)
				sideboard.setToolTipText(includeSideboard ? "Exclude the main deck" : "Also include the main deck");
			else
				sideboard.setToolTipText(includeSideboard ? "Do not include sideboard" : "Include sideboard");
		}
		if (extra != null) {
			extra.setEnabled(multi);
			extra.setChecked(includeExtra);
			if (onExtra)
				extra.setToolTipText(includeExtra ? "Exclude the main deck" : "Also include the main deck");
			else
				extra.setToolTipText(includeExtra ? "Do not include extra" : "Include extra");
		}
	}

	protected boolean isInludeHeader() {
		return includeHeader;
	}

	public void triggerHeader(boolean mode) {
		includeHeader = mode;
		header.setChecked(mode);
		refresh();
	}

	@Override
	public ISelectionProvider getSelectionProvider() {
		return selProvider;
	}

	protected void setText(String textResult) {
		this.textArea.setText(textResult);
		if (textBrowser != null)
			this.textBrowser.setText(textResult);
	}

	public void setFStore() {
		if (getCardStore() == null)
			return;
		if (fstore == null) {
			fstore = new MemoryFilteredCardStore<>();
			filter = fstore.getFilter();
		}
		Location loc = store.getLocation();
		Location main = loc.toMainDeck();
		Location sb = main.toSideboard();
		Location ex = main.toExtra();
		fstore.clear();

		// From a sideboard/extra view that list is the base content and both
		// toggles start off - so opening a sideboard's Export tab shows just
		// the sideboard, not the whole deck.
		if ((loc.isSideboard() || loc.isExtra()) && !secondaryDefaultsApplied) {
			includeSideboard = false;
			includeExtra = false;
			secondaryDefaultsApplied = true;
			if (sideboard != null)
				sideboard.setChecked(false);
			if (extra != null)
				extra.setChecked(false);
		}

		// Which lists to include: the current view (base) always, plus - per
		// toggle - the "other" list. On a secondary view the toggle for THAT
		// list means "also the main deck".
		Set<Location> want = new LinkedHashSet<>();
		if (isSideboardOnlyExport()) {
			// the printable sideboard list: sideboard always, never the main
			// deck, extra only when its toggle is on
			want.add(sb);
			if (includeExtra)
				want.add(ex);
		} else {
			want.add(loc);
			if (isMultiLocationExport()) {
				if (includeSideboard)
					want.add(loc.isSideboard() ? main : sb);
				if (includeExtra)
					want.add(loc.isExtra() ? main : ex);
			}
		}

		// Always emit main deck first, then sideboard, then extra - name-sorted
		// within each section. With no sort, fstore keeps this order.
		SortOrder nameSort = new SortOrder();
		int sections = 0;
		for (Location l : new Location[] { main, sb, ex }) {
			if (!want.contains(l))
				continue;
			ICardStore<IMagicCard> s = getCardStore(l);
			if (s == null)
				continue;
			List<IMagicCard> group = new ArrayList<>(s.getCards());
			group.sort(nameSort);
			fstore.getCardStore().addAll(group);
			sections++;
		}
		fstore.setLocation(main);

		// the Cards tab's filter + sort: export what that list shows
		IPersistentPreferenceStore filterStore = getCardsFilterStore();
		filter.update(filterStore != null ? storeToMap(filterStore) : new HashMap<String, String>());
		IPersistentPreferenceStore sortStore = getCardsSortStore();
		String sortStr = sortStore != null ? sortStore.getString(PreferenceConstants.SORT_ORDER) : "";
		sortHolder.setSortOrder(SortOrder.valueOf(sortStr));
		SortOrder effective = SortOrder.valueOf(sortStr);
		if (!effective.isEmpty() && sections > 1) {
			// keep main deck -> sideboard -> extra; the user sort applies inside
			// each section. Last setSortField() = primary
			effective.setSortField(MagicCardField.SIDEBOARD, true);
			effective.setSortField(MagicCardField.EXTRA, true);
		}
		filter.setSortOrder(effective);
		updateSecondaryActionLabels();
		syncActionState();
		fstore.update();
	}

	private ICardStore<IMagicCard> getCardStore(Location loc) {
		return DataManager.getInstance().getCardStore(loc);
	}

	private String getText() throws InvocationTargetException, InterruptedException {
		// setFStore();
		if (fstore == null)
			return "";
		ByteArrayOutputStream byteSt = new ByteArrayOutputStream();
		IExportDelegate ex = reportType.getExportDelegate();
		String selcolumns = getDeckView().getLocalPreferenceStore().getString(PreferenceConstants.LOCAL_COLUMNS);
		MagicColumnCollection magicColumnCollection = new MagicColumnCollection(null);
		magicColumnCollection.updateColumnsFromPropery(selcolumns);
		Location loc = store != null ? store.getLocation() : null;
		boolean sbShown = includeSideboard || (loc != null && loc.isSideboard()) || isSideboardOnlyExport();
		boolean exShown = includeExtra || (loc != null && loc.isExtra());
		if (sbShown && magicColumnCollection.getColumn(MagicCardField.SIDEBOARD) != null)
			magicColumnCollection.getColumn(MagicCardField.SIDEBOARD).setVisible(true);
		if (exShown && magicColumnCollection.getColumn(MagicCardField.EXTRA) != null)
			magicColumnCollection.getColumn(MagicCardField.EXTRA).setVisible(true);
		ICardField[] columns = magicColumnCollection.getColumnFields();
		ex.setColumns(columns);
		ex.init(byteSt, includeHeader, fstore);
		ex.run(null);
		return byteSt.toString();
	}

	/**
	 * Return code indicating the operation should be canceled.
	 */
	public static final String CANCEL = "CANCEL"; //$NON-NLS-1$
	/**
	 * Return code indicating the entity should not be overwritten, but operation
	 * should not be canceled.
	 */
	public static final String NO = "NO"; //$NON-NLS-1$
	/**
	 * Return code indicating the entity should be overwritten.
	 */
	public static final String YES = "YES"; //$NON-NLS-1$
	/**
	 * Return code indicating the export should be added at the end of the entity.
	 */
	public static final String APPEND = "APPEND"; //$NON-NLS-1$
	private StackLayout stackLayout;

	/**
	 * Asks what to do with an existing file: {@link #YES} (overwrite),
	 * {@link #APPEND} (only offered when {@code canAppend}) or {@link #CANCEL}.
	 */
	public String queryOverwrite(String pathString, boolean canAppend) {
		Path path = new Path(pathString);
		String messageString;
		// Break the message up if there is a file name and a directory
		// and there are at least 2 segments.
		if (path.getFileExtension() == null || path.segmentCount() < 2) {
			messageString = NLS.bind("File {0} already exists.", pathString);
		} else {
			messageString = NLS.bind("File {0} already exists in directory {1}.", path.lastSegment(),
					path.removeLastSegments(1).toOSString());
		}
		messageString += canAppend ? "\n\nOverwrite it, or append this export at the end of it?"
				: "\n\nOverwrite it?";
		String[] labels = canAppend ? new String[] { "Overwrite", "Append", IDialogConstants.CANCEL_LABEL }
				: new String[] { "Overwrite", IDialogConstants.CANCEL_LABEL };
		final MessageDialog dialog = new MessageDialog(getArea().getShell(), "File Exists", null, messageString,
				MessageDialog.QUESTION, labels, 0);
		String[] response = canAppend ? new String[] { YES, APPEND, CANCEL } : new String[] { YES, CANCEL };
		// run in syncExec because callback is from an operation,
		// which is probably not running in the UI thread.
		getControl().getDisplay().syncExec(new Runnable() {
			@Override
			public void run() {
				dialog.open();
			}
		});
		return dialog.getReturnCode() < 0 ? CANCEL : response[dialog.getReturnCode()];
	}

	/**
	 * Suggested save name for the current export: {@code <deck>[-<content>].<ext>}
	 * - e.g. "deck1-sideboard-list.html" - so each export type proposes its own
	 * name.
	 */
	private String suggestedFileName() {
		String deckName = "deck";
		if (fstore != null && fstore.getLocation() != null) {
			Location loc = fstore.getLocation().toMainDeck();
			if (loc != null && loc.getName() != null && !loc.getName().isEmpty())
				deckName = new File(loc.getName()).getName();
		}
		String slug = reportType != null ? reportType.getFileNameSlug() : "";
		String base = (slug == null || slug.isEmpty()) ? deckName : deckName + "-" + slug;
		String ext = reportType != null ? reportType.getExtension() : null;
		return (ext != null && !ext.isEmpty()) ? base + "." + ext : base;
	}

	public void saveAs() {
		if (textResult != null) {
			FileDialog fileDialog = new FileDialog(getArea().getShell(), SWT.SAVE | SWT.SHEET);
			fileDialog.setFileName(suggestedFileName());
			String ext = reportType != null ? reportType.getExtension() : null;
			if (ext != null && !ext.isEmpty())
				fileDialog.setFilterExtensions(new String[] { "*." + ext, "*.*" });
			String fileStr = fileDialog.open();
			if (fileStr != null) {
				boolean succ = false;
				try {
					File file = new File(fileStr);
					String answer = file.exists() ? queryOverwrite(fileStr, canAppend()) : YES;
					if (answer == YES) {
						FileUtils.saveString(textResult, file);
						succ = true;
					} else if (answer == APPEND) {
						String existing = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
						try {
							String merged = ExportAppender.append(existing, textResult, appendFormat(),
									file.getName());
							Files.write(file.toPath(), merged.getBytes(StandardCharsets.UTF_8));
							succ = true;
						} catch (ExportAppender.AppendException e) {
							// not the same kind of file: left untouched
							MessageDialog.openError(getArea().getShell(), "Cannot Append", e.getMessage());
						}
					}
					if (succ) {
						try {
							Program.launch(file.getPath());
						} catch (Throwable e) {
							MagicUIActivator.log(e);
						}
					}
				} catch (IOException e) {
					MessageDialog.openError(getArea().getShell(), "Error", "Cannot save file: " + fileStr);
				}
			}
		}
	}

	/** XML formats can't be appended to; printable sheets (html) can. */
	private boolean canAppend() {
		if (reportType == null)
			return false;
		IExportDelegate<?> d = reportType.getExportDelegate();
		return !reportType.isXmlFormat() || (d != null && d.isPrintSheet());
	}

	/** Same as the Export wizard's (DeckExportPage.appendFormat). */
	private ExportAppender.Format appendFormat() {
		IExportDelegate<?> d = reportType.getExportDelegate();
		return new ExportAppender.Format(reportType.getLabel(), reportType.getExtension(),
				d != null && d.isPrintSheet(), reportType.isXmlFormat(), includeHeader && !reportType.isXmlFormat());
	}

	public void setReportType(final ReportType rt) {
		reportType = rt;
		actionShowPrefs.setEnabled(true);
		if (reportType.getExportDelegate() instanceof AbstractExportDelegate) {
			AbstractExportDelegate<IMagicCard> delegate = (AbstractExportDelegate<IMagicCard>) reportType
					.getExportDelegate();
			actionShowPrefs.setEnabled(delegate.isColumnChoiceSupported());
		}
		updateSecondaryActionLabels();
		refresh();
	}

	@Override
	public boolean hookContextMenu(MenuManager menuMgr) {
		return false;
	}

	// @Override
	// public void runCopy() {
	// CopySupport.runCopy(getArea().getDisplay().getFocusControl());
	// }
	//
	// @Override
	// public void runPaste() {
	// CopySupport.runPaste(getArea().getDisplay().getFocusControl());
	// }
	public void refreshViewer() {
		textResult = null;
		setTopControl();
		try {
			textResult = getText();
			setText(textResult);
		} catch (InvocationTargetException e) {
			setText("Error: " + e.getCause());
		} catch (InterruptedException e) {
			setText("Cancelled");
		}
		if (textBrowser != null) {
			textBrowser.setBackground(getArea().getBackground());
			textBrowser.setForeground(getArea().getForeground());
		}
		getArea().layout();
	}

	@Override
	public void activate() {
		setFStore();
		setTopControl();
		super.activate();
		refreshViewer();
	}

	@Override
	public void refresh() {
		setFStore();
		refreshViewer();
	}

	@Override
	public void setGlobalHandlers(IActionBars bars) {
		// TODO Auto-generated method stub
	}
}
