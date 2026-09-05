package com.reflexit.magiccards.ui.views.analyzers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
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
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.StructuredSelection;
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
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.dialogs.PreferencesUtil;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.MagicLogger;
import com.reflexit.magiccards.core.exports.AbstractExportDelegate;
import com.reflexit.magiccards.core.exports.CompactHtmlExportDelegate;
import com.reflexit.magiccards.core.exports.IExportDelegate;
import com.reflexit.magiccards.core.exports.ImportExportFactory;
import com.reflexit.magiccards.core.exports.ReportType;
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
import com.reflexit.magiccards.ui.preferences.DeckViewPreferencePage;
import com.reflexit.magiccards.ui.preferences.PreferenceConstants;
import com.reflexit.magiccards.ui.utils.StoredSelectionProvider;
import com.reflexit.magiccards.ui.views.columns.MagicColumnCollection;

public class ExportDeckPage extends AbstractDeckPage {
	private Browser textBrowser;
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
		Composite exportArea = new Composite(area, SWT.NONE);
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
		stackLayout.topControl = markup ? textBrowser : textArea;
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
		this.actionSort = new SortByAction(new MagicColumnCollection(null), filter, null, this::refresh);
		this.actionRefresh = new RefreshAction(this::activate);
		initReportTypes();
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
			actionSort.setFilter(filter);
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
		// within each section. fstore keeps no sort of its own so this grouping
		// survives (unless the user picks an explicit column sort).
		SortOrder nameSort = new SortOrder();
		for (Location l : new Location[] { main, sb, ex }) {
			if (!want.contains(l))
				continue;
			ICardStore<IMagicCard> s = getCardStore(l);
			if (s == null)
				continue;
			List<IMagicCard> group = new ArrayList<>(s.getCards());
			group.sort(nameSort);
			fstore.getCardStore().addAll(group);
		}
		fstore.setLocation(main);
		updateSecondaryActionLabels();
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
	private StackLayout stackLayout;

	public String queryOverwrite(String pathString) {
		Path path = new Path(pathString);
		String messageString;
		// Break the message up if there is a file name and a directory
		// and there are at least 2 segments.
		if (path.getFileExtension() == null || path.segmentCount() < 2) {
			messageString = NLS.bind("File {0} already exists, overwrite?", pathString);
		} else {
			messageString = NLS.bind("File {0} already exists in directory {1}, owerwrite?", path.lastSegment(),
					path.removeLastSegments(1).toOSString());
		}
		final MessageDialog dialog = new MessageDialog(getArea().getShell(), "Question", null, messageString,
				MessageDialog.QUESTION,
				new String[] { IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL, IDialogConstants.CANCEL_LABEL },
				0);
		String[] response = new String[] { YES, NO, CANCEL };
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
					if (!file.exists() || queryOverwrite(fileStr) == YES) {
						FileUtils.saveString(textResult, file);
						succ = true;
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
