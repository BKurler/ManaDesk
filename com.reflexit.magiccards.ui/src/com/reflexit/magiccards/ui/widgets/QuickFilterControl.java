/*
 * Contributors:
 *     Rémi Dutil 2026 - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - Own/Virtual and Collections/Decks quick-filter
 *                         toggle buttons - Collections/Decks (the
 *                         navigator's own deck/collection icons, Collections
 *                         first) listed before Own/Virtual (drawn as bold
 *                         colour "O"/"V" letter icons - crisper than the
 *                         native ToolItem text rendering); both pairs start
 *                         unselected (selecting both has the same effect as
 *                         selecting neither, so there is no need to
 *                         pre-check them); removed the "clear filter"
 *                         toolbar button (redundant with the advanced filter
 *                         dialog's own reset); shrank the Name and Type
 *                         fields to make room
 *     Rémi Dutil (2026) - Set now widens/narrows into whatever horizontal
 *                         space its siblings in this bar aren't using
 *                         (150px floor, capped at the longest edition name)
 *                         instead of a fixed width - installSetWidthAdjuster()
 *                         (the matching AbstractMagicCardsListControl fix
 *                         that lets this control itself receive that space
 *                         in the first place - it was being silently
 *                         overridden by a bare "new GridData()" there - is
 *                         in that class' own header). The cap is recomputed
 *                         on every resize rather than once at construction:
 *                         Editions loads from Scryfall asynchronously, so a
 *                         value captured up front would almost always see an
 *                         empty list and cap the field at its floor forever.
 *     Rémi Dutil (2026) - new showOwnershipKindFilters constructor flag: the
 *                         Own/Virtual and Collections/Decks toggles only make
 *                         sense in a view spanning every deck/collection at
 *                         once (My Cards) - everywhere else every row already
 *                         has one fixed answer to both, so they're skipped
 */
package com.reflexit.magiccards.ui.widgets;

import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.CardTypes;
import com.reflexit.magiccards.core.model.Colors;
import com.reflexit.magiccards.core.model.Editions;
import com.reflexit.magiccards.core.model.FilterField;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.Locations;
import com.reflexit.magiccards.core.model.nav.CardCollection;
import com.reflexit.magiccards.core.model.nav.CardElement;
import com.reflexit.magiccards.core.model.nav.ModelRoot;
import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.utils.StatusDots;
import com.reflexit.magiccards.ui.utils.SymbolRenderer;
import com.reflexit.magiccards.ui.utils.WaitUtils;

public class QuickFilterControl extends Composite {
	private static final String ALL_TYPES = "";
	private static final String ADVANCED = "<advanced filter>";
	private static final String ALL_NAMES = "";
	private static final int SET_MIN_WIDTH = 150;
	private Text searchText;
	private IPreferenceStore store;
	private Runnable runnable;
	private Combo typeCombo;
	private ToolBar toolbar;
	private EditionTextControl setCombo;
	private ToolItem ownButton;
	private ToolItem virtualButton;
	private ToolItem deckButton;
	private ToolItem collectionButton;
	private boolean showOwnershipKindFilters;
	private long lastMod = 0;
	private boolean pendingUpdate = false;
	private Object updateLock = new Object();
	private UpdateThread uthread;
	private int updateDelay = 300;
	private boolean suppressUpdates = false;

	class UpdateThread extends Thread {
		public UpdateThread() {
			super("Quick Filter Update Thread");
		}

		@Override
		public void run() {
			try {
				while (true) {
					synchronized (updateLock) {
						if (pendingUpdate == false) {
							updateLock.wait();
							// MagicLogger.trace("QUPDATE", "got update on
							// wait");
							// we got notification
							if (pendingUpdate == false)
								continue; // hmm misfire?
							while (pendingUpdate && System.currentTimeMillis() - lastMod < updateDelay) {
								updateLock.wait(updateDelay);
								// MagicLogger.trace("QUPDATE", "got update on
								// wait " +
								// (System.currentTimeMillis() - lastMod));
							}
							if (pendingUpdate == false)
								continue;
							// we got more than 500 ms timeout
						}
						// pendingUpdate is true now
					}
					// System.err.println(System.currentTimeMillis() + " running
					// now");
					doUpdate();
				}
			} catch (InterruptedException e) {
				return;
			}
		}
	}

	private void doUpdate() {
		synchronized (updateLock) {
			pendingUpdate = false;
			updateLock.notifyAll();
		}
		updateStore();
		runnable.run();
	}

	private void updateStore() {
		WaitUtils.syncExec(() -> {
			filterSet(setCombo.getText());
			filterText(searchText.getText());
			filterType(typeCombo.getText());
		});
	}

	public QuickFilterControl(Composite composite, Runnable run, boolean visible) {
		this(composite, run, visible, false);
	}

	/**
	 * @param showOwnershipKindFilters whether to show the Own/Virtual and
	 *        Collections/Decks toggle buttons - only meaningful in a view
	 *        that spans every deck/collection at once (My Cards); elsewhere
	 *        every row already has a fixed ownership and container kind, so
	 *        the toggles would have nothing to do
	 */
	public QuickFilterControl(Composite composite, Runnable run, boolean visible, boolean showOwnershipKindFilters) {
		super(composite, SWT.NONE);
		this.showOwnershipKindFilters = showOwnershipKindFilters;
		setLayout(GridLayoutFactory.fillDefaults().create());
		setLayoutData(GridDataFactory.fillDefaults().grab(true, false).create());
		createBar(this);
		this.runnable = run;
		setVisible(visible);
	}

	public void setUpdateDelay(int updateDelay) {
		this.updateDelay = updateDelay;
	}

	@Override
	public void setVisible(boolean vis) {
		super.setVisible(vis);
		GridData gd = (GridData) getLayoutData();
		gd.exclude = !vis;
		gd.widthHint = vis ? SWT.DEFAULT : 0;
		getParent().getParent().layout(true);
		if (!vis) {
			if (uthread != null) {
				uthread.interrupt();
				uthread = null;
			}
		} else {
			setFocus();
			if (uthread != null)
				uthread.interrupt();
			uthread = new UpdateThread();
			uthread.start();
		}
	}

	@Override
	public boolean setFocus() {
		boolean x = searchText.setFocus();
		this.searchText.setSelection(0, this.searchText.getText().length());
		return x;
	}

	void createBar(Composite comp) {
		setLayout(GridLayoutFactory.fillDefaults().numColumns(4).create());
		// search field
		createSearchField(comp);
		// toolbar
		toolbar = new ToolBar(comp, SWT.FLAT);
		toolbar.setLayoutData(GridDataFactory.fillDefaults().create());
		createColorButton(toolbar, "White");
		createColorButton(toolbar, "Blue");
		createColorButton(toolbar, "Black");
		createColorButton(toolbar, "Red");
		createColorButton(toolbar, "Green");
		if (showOwnershipKindFilters) {
			new ToolItem(toolbar, SWT.SEPARATOR);
			createKindButtons(toolbar);
			new ToolItem(toolbar, SWT.SEPARATOR);
			createOwnershipButtons(toolbar);
		}
		// type
		createTypeField(comp);
		// set
		createEditionField(comp);
		// hide
		// createHideButton(comp);
	}

	private void createSearchField(Composite toolbar) {
		this.searchText = new Text(toolbar, SWT.SEARCH | SWT.ICON_CANCEL);
		this.searchText.setText(ALL_NAMES);
		searchText.setLayoutData(GridDataFactory.fillDefaults().hint(120, 16).create());
		this.searchText.addFocusListener(new FocusListener() {
			@Override
			public void focusLost(FocusEvent e) {
				// nothing
			}

			@Override
			public void focusGained(FocusEvent e) {
				searchText.setSelection(0, searchText.getText().length());
			}
		});
		this.searchText.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				kickUpdate();
			}
		});
		this.searchText.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				doUpdate();
			}
		});
		searchText.setToolTipText("Name filter");
		searchText.addFocusListener(new SearchContextFocusListener());
		if (toolbar instanceof ToolBar) {
			ToolItem text = new ToolItem((ToolBar) toolbar, SWT.SEPARATOR);
			text.setControl(this.searchText);
			text.setWidth(200);
		}
	}

	private void createTypeField(Composite toolbar) {
		typeCombo = new Combo(toolbar, SWT.BORDER);
		typeCombo.add(ALL_TYPES);
		typeCombo.setText(ALL_TYPES);
		Collection<String> names = CardTypes.getInstance().getLocalizedNames();
		for (String type : names) {
			typeCombo.add(type);
		}
		typeCombo.setLayoutData(GridDataFactory.fillDefaults().hint(90, 16).create());
		typeCombo.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				kickUpdate();
			}
		});
		typeCombo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				doUpdate();
			}
		});
		typeCombo.setToolTipText("Type filter");
		typeCombo.addFocusListener(new SearchContextFocusListener());
		if (toolbar instanceof ToolBar) {
			ToolItem item = new ToolItem((ToolBar) toolbar, SWT.SEPARATOR);
			item.setControl(typeCombo);
			item.setWidth(150);
		}
	}

	private void createEditionField(Composite toolbar) {
		EditionTextControl setCombo = new EditionTextControl(toolbar, SWT.BORDER);
		setCombo.setToolTipText("Set filter");
		setCombo.setLayoutData(GridDataFactory.fillDefaults().hint(SET_MIN_WIDTH, 16).create());
		installSetWidthAdjuster(toolbar, setCombo);
		setCombo.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				kickUpdate();
			}
		});
		setCombo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				doUpdate();
			}
		});
		this.setCombo = setCombo;
		if (toolbar instanceof ToolBar) {
			ToolItem item = new ToolItem((ToolBar) toolbar, SWT.SEPARATOR);
			item.setControl(setCombo);
			item.setWidth(180);
		}
	}

	/** Grows the Set field into whatever horizontal space its siblings
	 *  (search field, colour/toggle toolbar, Type combo) aren't using, capped
	 *  at the width of the longest edition name - there's no point growing
	 *  past a size no set name could ever fill. GridData has no "max width",
	 *  so this recomputes a widthHint from the actual available space on
	 *  every resize instead of just grabbing indefinitely. The cap itself is
	 *  recomputed on every resize too, not just once at install time:
	 *  {@link Editions} loads its data from Scryfall asynchronously, well
	 *  after this control is built, so a value captured up front would
	 *  almost always see an empty edition list and permanently cap the field
	 *  at its floor width - recomputing here means it's simply ignored
	 *  (uncapped) until real names are available, then applied from the next
	 *  resize on. */
	private void installSetWidthAdjuster(Composite comp, EditionTextControl setCombo) {
		Listener resize = e -> {
			if (comp.isDisposed() || setCombo.isDisposed())
				return;
			int used = 0;
			for (Control c : comp.getChildren()) {
				if (c == setCombo)
					continue;
				used += c.getBounds().width;
			}
			GridLayout gl = (GridLayout) comp.getLayout();
			int gaps = Math.max(0, comp.getChildren().length - 1);
			used += gl.horizontalSpacing * gaps + gl.marginWidth * 2;
			int available = comp.getClientArea().width - used;
			int rawMax = computeMaxSetNameWidth(comp);
			int cap = rawMax > 0 ? rawMax + 24 : Integer.MAX_VALUE;
			int desired = Math.max(SET_MIN_WIDTH, Math.min(available, cap));
			GridData gd = (GridData) setCombo.getLayoutData();
			if (gd.widthHint != desired) {
				gd.widthHint = desired;
				comp.layout(new Control[] { setCombo });
			}
		};
		comp.addListener(SWT.Resize, resize);
		// the very first layout pass may not fire a Resize event - force one
		// computation once the control actually has real bounds
		comp.getDisplay().asyncExec(() -> {
			if (!comp.isDisposed())
				resize.handleEvent(null);
		});
	}

	private int computeMaxSetNameWidth(Control control) {
		GC gc = new GC(control);
		try {
			int max = 0;
			for (String name : Editions.getInstance().getNames()) {
				int w = gc.textExtent(name).x;
				if (w > max)
					max = w;
			}
			return max;
		} finally {
			gc.dispose();
		}
	}

	/** Two small "O" / "V" letter toggles - green for Own, blue for Virtual
	 *  (the same blue {@link StatusDots#VIRTUAL} already uses elsewhere for
	 *  "virtual") - unselected by default (= show everything; selecting both
	 *  has the same effect as selecting neither, so there is no need to
	 *  pre-check them). Drives {@link FilterField#OWNERSHIP} the same way the
	 *  existing Own/Virtual radio group in the advanced filter dialog does -
	 *  {@code ""} (both, or neither, checked), {@code "true"} (Own only) or
	 *  {@code "false"} (Virtual only). */
	private void createOwnershipButtons(ToolBar toolbar) {
		ownButton = new ToolItem(toolbar, SWT.CHECK);
		ownButton.setImage(buildLetterImage("O", new RGB(45, 140, 65)));
		ownButton.setToolTipText("Own");
		ownButton.setSelection(false);
		ownButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				updateOwnershipFilter();
				kickUpdate();
			}
		});
		virtualButton = new ToolItem(toolbar, SWT.CHECK);
		virtualButton.setImage(buildLetterImage("V", StatusDots.VIRTUAL));
		virtualButton.setToolTipText("Virtual");
		virtualButton.setSelection(false);
		virtualButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				updateOwnershipFilter();
				kickUpdate();
			}
		});
	}

	/** A crisp bold letter on a transparent background, for the Own/Virtual
	 *  toggle buttons - the native ToolItem text rendering reads as cramped
	 *  next to the mana-symbol colour buttons, so the letter is drawn once
	 *  into a properly centred, bold, anti-aliased icon instead. */
	private Image buildLetterImage(String letter, RGB rgb) {
		RGB sentinel = new RGB(255, 0, 255);
		Image img = new Image(getDisplay(), 16, 16);
		GC gc = new GC(img);
		Font bold = null;
		try {
			Color bg = new Color(getDisplay(), sentinel);
			gc.setBackground(bg);
			gc.fillRectangle(0, 0, 16, 16);
			bg.dispose();
			gc.setAntialias(SWT.ON);
			gc.setTextAntialias(SWT.ON);
			FontData[] fds = getFont().getFontData();
			for (FontData fd : fds) {
				fd.setStyle(SWT.BOLD);
				fd.setHeight(10);
			}
			bold = new Font(getDisplay(), fds);
			gc.setFont(bold);
			Color fg = new Color(getDisplay(), rgb);
			gc.setForeground(fg);
			Point extent = gc.textExtent(letter);
			gc.drawText(letter, (16 - extent.x) / 2, (16 - extent.y) / 2, true);
			fg.dispose();
		} finally {
			gc.dispose();
			if (bold != null)
				bold.dispose();
		}
		ImageData data = img.getImageData();
		img.dispose();
		data.transparentPixel = data.palette.getPixel(sentinel);
		Image result = new Image(getDisplay(), data);
		addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				result.dispose();
			}
		});
		return result;
	}

	private void updateOwnershipFilter() {
		String id = FilterField.OWNERSHIP.getPrefConstant();
		boolean own = ownButton.getSelection();
		boolean virtual = virtualButton.getSelection();
		if (own == virtual) {
			store.setValue(id, "");
		} else if (own) {
			store.setValue(id, "true");
		} else {
			store.setValue(id, "false");
		}
	}

	/** Two small icon toggles (the same deck/collection icons the navigator
	 *  tree uses), unselected by default (= show everything; selecting both
	 *  has the same effect as selecting neither, so there is no need to
	 *  pre-check them). There is no single card field for this - it bulk-sets
	 *  the existing per-location {@link Locations} checkbox group (already
	 *  wired into {@code MagicCardFilter#update()}) to match every location
	 *  whose {@link CardCollection#isDeck()} agrees, which is the same effect
	 *  as checking every deck (or every collection) individually in the
	 *  advanced Locations filter. */
	private void createKindButtons(ToolBar toolbar) {
		collectionButton = new ToolItem(toolbar, SWT.CHECK);
		collectionButton.setImage(MagicUIActivator.getDefault().getImage("icons/obj16/lib16.png"));
		collectionButton.setToolTipText("Collections");
		collectionButton.setSelection(false);
		collectionButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				updateKindFilter();
				kickUpdate();
			}
		});
		deckButton = new ToolItem(toolbar, SWT.CHECK);
		deckButton.setImage(MagicUIActivator.getDefault().getImage("icons/obj16/ideck16.png"));
		deckButton.setToolTipText("Decks");
		deckButton.setSelection(false);
		deckButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				updateKindFilter();
				kickUpdate();
			}
		});
	}

	private void updateKindFilter() {
		boolean deck = deckButton.getSelection();
		boolean collection = collectionButton.getSelection();
		Boolean wantDeck = deck == collection ? null : Boolean.valueOf(deck);
		ModelRoot modelRoot = DataManager.getInstance().getModelRoot();
		Map<Location, CardElement> locations = modelRoot.getLocationsMap();
		for (Map.Entry<Location, CardElement> entry : locations.entrySet()) {
			CardElement el = entry.getValue();
			boolean isDeck = el instanceof CardCollection && ((CardCollection) el).isDeck();
			String id = Locations.getInstance().getPrefConstant(entry.getKey());
			store.setValue(id, wantDeck != null && wantDeck.booleanValue() == isDeck);
		}
	}

	// private void createToolBarLabel(ToolBar toolbar, String string) {
	// Label label = new Label(toolbar, SWT.NONE);
	// label.setText(string);
	// ToolItem text = new ToolItem(toolbar, SWT.SEPARATOR);
	// text.setControl(label);
	// text.setWidth(50);
	// }
	private void createColorButton(ToolBar toolbar, String name) {
		Colors colors = Colors.getInstance();
		final String id = colors.getPrefConstant(name);
		String abbr = Colors.getInstance().getEncodeByName(name);
		//
		final ToolItem button = new ToolItem(toolbar, SWT.CHECK);
		button.setImage(SymbolRenderer.buildCostImage("{" + abbr + "}"));
		button.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				store.setValue(id, button.getSelection());
				kickUpdate();
			}
		});
		button.setSelection(false);
		button.setToolTipText(name);
		button.setData("id", id);
	}

	private void setButtonToStoreValue(ToolBar toolbar, String id) {
		ToolItem[] children = toolbar.getItems();
		for (int i = 0; i < children.length; i++) {
			ToolItem control = children[i];
			Object data = control.getData("id");
			if (data != null && id.equals(data)) {
				control.setSelection(store.getBoolean(id));
			}
		}
	}

	// private void createHideButton(Composite comp) {
	// ToolBar toolbar = new ToolBar(comp, SWT.FLAT);
	// GridData gd = new GridData();
	// gd.horizontalAlignment = GridData.END;
	// //
	// ToolItem hideButton = new ToolItem(toolbar, SWT.PUSH);
	// hideButton.setImage(MagicUIActivator.getDefault().getImage("icons/clcl16/delete_obj.gif"));
	// hideButton.addSelectionListener(new SelectionAdapter() {
	// @Override
	// public void widgetSelected(SelectionEvent e) {
	// setVisible(false);
	// }
	// });
	// }
	public void setPreferenceStore(IPreferenceStore store) {
		this.store = store;
	}

	public void refresh() {
		if (searchText != null && store != null) {
			// text
			String textId = FilterField.NAME_LINE.getPrefConstant();
			String text = store.getString(textId);
			if (text == null || text.trim().length() == 0) {
				searchText.setText(ALL_NAMES);
			} else if (text.startsWith("\"") && text.endsWith("\"")) {
				text = text.replaceAll("\"(.*)\"", "$1");
				searchText.setText(text);
			} else {
				searchText.setText(ADVANCED);
			}
			// type
			String type = ALL_TYPES;
			String typeId = FilterField.TYPE_LINE.getPrefConstant();
			String type1 = store.getString(typeId);
			int typehit = 0;
			CardTypes coreTypes = CardTypes.getInstance();
			for (Iterator<String> iterator = coreTypes.getIds().iterator(); iterator.hasNext();) {
				String id = iterator.next();
				boolean isSet = store.getBoolean(id);
				// System.err.println(id + " " + isSet);
				if (isSet) {
					type = coreTypes.getLocalizedNameById(id);
					typehit++;
				}
			}
			if (typehit > 1 || typehit == 1 && type1.length() > 0) {
				typeCombo.setText(ADVANCED);
			} else if (typehit == 0) {
				typeCombo.setText(type1);
			} else {
				typeCombo.setText(type);
			}
			// set
			Collection<String> ids = Colors.getInstance().getIds();
			for (Iterator<String> iterator = ids.iterator(); iterator.hasNext();) {
				String id = iterator.next();
				setButtonToStoreValue(toolbar, id);
			}
			// type
			String set = ALL_TYPES;
			int sethit = 0;
			Editions editions = Editions.getInstance();
			for (Iterator<String> iterator = editions.getIds().iterator(); iterator.hasNext();) {
				String id = iterator.next();
				boolean isSet = store.getBoolean(id);
				// System.err.println(id + " " + isSet);
				if (isSet) {
					set = editions.getNameById(id);
					sethit++;
				}
			}
			if (sethit > 1) {
				setCombo.setText(ADVANCED);
			} else if (sethit == 0) {
				setCombo.setText(ALL_TYPES);
			} else {
				setCombo.setText(set);
			}
			if (showOwnershipKindFilters) {
				// ownership
				String ownId = FilterField.OWNERSHIP.getPrefConstant();
				String ownValue = store.getString(ownId);
				ownButton.setSelection("true".equals(ownValue));
				virtualButton.setSelection("false".equals(ownValue));
				// collections vs decks
				ModelRoot modelRoot = DataManager.getInstance().getModelRoot();
				Map<Location, CardElement> locations = modelRoot.getLocationsMap();
				int deckTotal = 0, deckHit = 0, colTotal = 0, colHit = 0;
				for (Map.Entry<Location, CardElement> entry : locations.entrySet()) {
					CardElement el = entry.getValue();
					boolean isDeck = el instanceof CardCollection && ((CardCollection) el).isDeck();
					String id = Locations.getInstance().getPrefConstant(entry.getKey());
					boolean checked = store.getBoolean(id);
					if (isDeck) {
						deckTotal++;
						if (checked)
							deckHit++;
					} else {
						colTotal++;
						if (checked)
							colHit++;
					}
				}
				if (deckTotal > 0 && deckHit == deckTotal && colHit == 0) {
					deckButton.setSelection(true);
					collectionButton.setSelection(false);
				} else if (colTotal > 0 && colHit == colTotal && deckHit == 0) {
					deckButton.setSelection(false);
					collectionButton.setSelection(true);
				} else {
					deckButton.setSelection(false);
					collectionButton.setSelection(false);
				}
			}
		}
	}

	public void setUpdateHook(Runnable run) {
		this.runnable = run;
	}

	private void kickUpdate() {
		if (suppressUpdates)
			return;
		synchronized (updateLock) {
			lastMod = System.currentTimeMillis();
			pendingUpdate = true;
			// MagicLogger.trace("QUPDATE", "Sending notification for '" + text
			// + "'");
			updateLock.notifyAll();
		}
	}

	protected void filterText(String text) {
		if (ADVANCED.equals(text))
			return;
		if (ALL_NAMES.equals(text))
			text = "";
		String textId = FilterField.NAME_LINE.getPrefConstant();
		if (text.trim().length() == 0) {
			this.store.setValue(textId, "");
		} else {
			this.store.setValue(textId, "\"" + text + "\"");
		}
	}

	protected void filterType(String text) {
		if (ADVANCED.equals(text))
			return;
		if (ALL_TYPES.equals(text))
			text = "";
		CardTypes coreTypes = CardTypes.getInstance();
		String selId = null;
		String textId = FilterField.TYPE_LINE.getPrefConstant();
		for (Iterator<String> iterator = coreTypes.getIds().iterator(); iterator.hasNext();) {
			String id = iterator.next();
			store.setValue(id, "false");
			if (coreTypes.getLocalizedNameById(id).equals(text)) {
				selId = id;
			}
		}
		if (selId == null) {
			store.setValue(textId, text.trim());
		} else {
			store.setValue(textId, "");
			store.setValue(selId, "true");
		}
	}

	protected void filterSet(String set) {
		if (ADVANCED.equals(set))
			return;
		if (ALL_TYPES.equals(set))
			set = "";
		Editions editions = Editions.getInstance();
		String lset = set.toLowerCase(Locale.ENGLISH);
		boolean exactMatch = false;
		Collection<String> ids = editions.getIds();
		for (Iterator<String> iterator = ids.iterator(); iterator.hasNext();) {
			String id = iterator.next();
			store.setValue(id, "false");
			if (editions.getNameById(id).toLowerCase().equals(lset)) {
				store.setValue(id, "true");
				exactMatch = true;
			}
		}
		if (!exactMatch && lset.length() > 0) {
			for (Iterator<String> iterator = ids.iterator(); iterator.hasNext();) {
				String id = iterator.next();
				if (editions.getNameById(id).toLowerCase().contains(lset)) {
					store.setValue(id, "true");
				}
			}
		}
	}

	public boolean isSuppressUpdates() {
		return suppressUpdates;
	}

	public void setSuppressUpdates(boolean suppressUpdates) {
		this.suppressUpdates = suppressUpdates;
	}
}
