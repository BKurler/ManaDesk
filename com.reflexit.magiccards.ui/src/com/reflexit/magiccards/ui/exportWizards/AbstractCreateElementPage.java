/*
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: shared scaffold of the two
 *                         "New ..." pages (NewDeckPage / NewCollectionPage) - the
 *                         Name field, the "Contents" (Empty / Import) choice and
 *                         the Virtual / Read only options. A new element always
 *                         lands under this page's side root; importing into an
 *                         existing element is a separate wizard.
 */
package com.reflexit.magiccards.ui.exportWizards;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.nav.CardCollection;
import com.reflexit.magiccards.core.model.nav.CardElement;
import com.reflexit.magiccards.core.model.nav.CollectionsContainer;
import com.reflexit.magiccards.core.model.nav.ModelRoot;
import com.reflexit.magiccards.ui.dialogs.LocationPickerDialog;
import com.reflexit.magiccards.ui.utils.StatusDots;

/**
 * "New Deck" / "New Collection" first page. Create an empty element, or fill it
 * from the clipboard / a file. The element is always created under the side root
 * ("Decks" / "Collections").
 */
public abstract class AbstractCreateElementPage extends AbstractCardListImportPage {
	private Text nameText;
	private Button emptyRadio;
	private Text whereText;
	protected Button newVirtual;
	protected Button newReadOnly;

	protected AbstractCreateElementPage(String pageName, IStructuredSelection selection) {
		super(pageName, selection);
		// a leaf (an existing deck/collection) selection means "create alongside
		// this one" - use its parent folder; the base ctor leaves such a
		// selection as-is (AbstractImportIntoPage needs the leaf itself)
		CardElement candidate = element;
		if (candidate != null && !(candidate instanceof CollectionsContainer) && candidate.getParent() != null)
			candidate = candidate.getParent();
		// create inside the selected container when it is on this page's side
		// (a sub-folder counts); otherwise fall back to the side root
		ModelRoot root = DataManager.getInstance().getModelRoot();
		CollectionsContainer parent = null;
		if (candidate instanceof CollectionsContainer && root.sideOf(candidate) == side())
			parent = (CollectionsContainer) candidate;
		this.parentContainer = parent != null ? parent : getSideRoot();
		this.element = this.parentContainer;
	}

	/** The container the new element will be created in. */
	protected CollectionsContainer parentContainer;

	// ---- hooks for NewDeckPage / NewCollectionPage -------------------------

	/** "deck" or "collection" - lower case, for labels. */
	protected abstract String typeName();

	/** DECK / COLLECTION. */
	protected abstract ModelRoot.Side side();

	/** Whether a brand-new element of this type is Virtual by default. */
	protected abstract boolean defaultVirtual();

	/** Add the type-specific options below Virtual / Read only
	 *  (deck: Sideboard / Extra; collection: Unsorted). */
	protected abstract void createTypeSpecificOptions(Group group);

	/** Whether {@link #createTypeSpecificOptions} adds a distinct action (a
	 *  deck's Sideboard/Extra checkboxes create additional elements) that
	 *  deserves visual separation from Virtual/Read only, rather than reading
	 *  as one more checkbox in that same list (a collection's Unsorted).
	 *  Deck overrides this true; collection leaves the default false. */
	protected boolean separateTypeSpecificOptions() {
		return false;
	}

	// ---- wiring ----------------------------------------------------------

	protected final CollectionsContainer getSideRoot() {
		return DataManager.getInstance().getModelRoot().containerFor(side());
	}

	@Override
	protected final boolean isDeckTarget() {
		return side() == ModelRoot.Side.DECK;
	}

	@Override
	protected String getTitleText() {
		String t = typeName();
		return "New " + Character.toUpperCase(t.charAt(0)) + t.substring(1);
	}

	@Override
	protected boolean isEmptyMode() {
		return emptyRadio != null && emptyRadio.getSelection();
	}

	@Override
	protected boolean wantVirtual() {
		return newVirtual == null ? defaultVirtual() : newVirtual.getSelection();
	}

	@Override
	protected boolean wantReadOnly() {
		return newReadOnly != null && newReadOnly.getSelection();
	}

	@Override
	protected String getNewElementName() {
		String n = nameText != null && !nameText.isDisposed() ? nameText.getText().trim() : "";
		return n.isEmpty() ? super.getNewElementName() : n;
	}

	@Override
	public IWizardPage getNextPage() {
		// "Empty" creates the element straight from Finish - no card-list preview
		return isEmptyMode() ? null : super.getNextPage();
	}

	// ---- destination group: just a Name + the new-element options ---------

	@Override
	protected void createDestinationGroup(Composite parent) {
		String type = typeName();
		Group group = new Group(parent, SWT.NONE);
		group.setText(getTitleText().substring(4)); // "Deck" / "Collection"
		group.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).create());
		group.setLayout(new GridLayout(3, false));

		Label nl = new Label(group, SWT.NONE);
		nl.setText("Name:");
		nameText = new Text(group, SWT.BORDER);
		nameText.setMessage("name of the new " + type);
		nameText.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).span(2, 1).create());
		nameText.addModifyListener(e -> updatePageCompletion());

		new Label(group, SWT.NONE).setText("In:");
		whereText = new Text(group, SWT.BORDER);
		whereText.setEditable(false);
		whereText.setText(parentContainer.getLocation().getPath());
		whereText.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).create());
		whereText.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDown(MouseEvent e) {
				pickParentContainer();
			}
		});
		Button change = new Button(group, SWT.PUSH);
		change.setText("Choose...");
		change.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				pickParentContainer();
			}
		});

		newVirtual = StatusDots.check(group, StatusDots.VIRTUAL,
				"Virtual - tracks cards you do not own (affects move / copy / count)");
		newVirtual.setSelection(defaultVirtual());
		newReadOnly = StatusDots.check(group, StatusDots.READ_ONLY,
				"Read only - lock the new " + type + " after it is created");
		// Breathing room AFTER Read only, before the type-specific options -
		// but only when those options are a genuinely separate action (a deck's
		// "Also create a Sideboard/Extra" creates additional elements), not
		// when they read as one more checkbox in the same Virtual/Read
		// only/... list (a collection's "Unsorted"). GridData.verticalIndent
		// adds space ABOVE its own control, so it belongs on the type-specific
		// options' own first row (set generically here, once, rather than in
		// every createTypeSpecificOptions override) - not on newReadOnly's.
		int before = group.getChildren().length;
		createTypeSpecificOptions(group);
		if (separateTypeSpecificOptions()) {
			Control[] added = group.getChildren();
			if (added.length > before) {
				Object gd = added[before].getLayoutData();
				if (gd instanceof GridData)
					((GridData) gd).verticalIndent = 8;
			}
		}
	}

	/** Opens a folder picker (containers only, this side only) for the "In:"
	 *  field, so a new deck/collection can be created anywhere under
	 *  Decks/Collections, not just directly at the side root. */
	private void pickParentContainer() {
		LocationPickerDialog dialog = new LocationPickerDialog(getShell(), SWT.SINGLE) {
			@Override
			protected org.eclipse.swt.widgets.Control createDialogArea(Composite parent) {
				org.eclipse.swt.widgets.Control x = super.createDialogArea(parent);
				setMessage("Choose the folder the new " + typeName() + " is created in.");
				return x;
			}
		};
		dialog.setContainersOnly(true);
		dialog.setSideFilter(side());
		// this picker is already inside a "New deck/collection" wizard - it is
		// only choosing a destination FOLDER for the element being created,
		// so offering to launch another New Deck/Collection wizard from here
		// is out of place.
		dialog.setShowCreateButtons(false);
		dialog.setSelection(new StructuredSelection(parentContainer));
		if (dialog.open() == Window.OK && dialog.getSelection() != null && !dialog.getSelection().isEmpty()) {
			Object picked = dialog.getSelection().getFirstElement();
			if (picked instanceof CollectionsContainer
					&& DataManager.getInstance().getModelRoot().sideOf((CardElement) picked) == side()) {
				parentContainer = (CollectionsContainer) picked;
				whereText.setText(parentContainer.getLocation().getPath());
				updatePageCompletion();
			}
		}
	}

	// ---- "Contents": Empty / Import from Clipboard / Import from File -----
	// one flat, mutually-exclusive radio list (see
	// AbstractCardListImportPage#createSourceContainer).

	@Override
	protected void createResourcesGroup(final Composite parent) {
		Group contents = new Group(parent, SWT.NONE);
		contents.setText("Contents");
		contents.setLayout(GridLayoutFactory.swtDefaults().numColumns(3).create());
		contents.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).create());

		emptyRadio = new Button(contents, SWT.RADIO);
		emptyRadio.setText("Empty " + typeName());
		emptyRadio.setSelection(true);
		emptyRadio.setLayoutData(GridDataFactory.swtDefaults().span(3, 1).create());
		emptyRadio.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				syncEmptyImport();
			}
		});

		// Clipboard / File radios + their controls render straight into this
		// same "Contents" group (not a separate "Import Source" box), and a
		// widgetSelected listener isn't needed on them individually - they
		// already call onInputChoice(), which calls updateWidgetEnablements()
		// and updatePageCompletion(); syncEmptyImport() below only needs to
		// react to emptyRadio specifically.
		createSourceGroup(contents);
		syncEmptyImport();
	}

	@Override
	protected String clipboardRadioLabel() {
		return "Import from Clipboard";
	}

	@Override
	protected String fileRadioLabel() {
		return "Import from File";
	}

	/** Fold the Clipboard/File radios and their controls directly into the
	 *  caller's own group (here, "Contents") instead of the base class's
	 *  default separate "Import Source" box - Empty/Clipboard/File must read
	 *  as one flat list of choices, not "Empty" next to a whole other labeled
	 *  area. */
	@Override
	protected Composite createSourceContainer(Composite parent) {
		return parent;
	}

	/**
	 * The base class restores the File/Clipboard preference persisted from the
	 * last time this wizard ran and calls {@code setSelection(true)} on that
	 * radio - but {@code Button.setSelection(true)} does NOT enforce radio-group
	 * exclusivity when called programmatically (only a real click does, via the
	 * native widget), so it leaves that radio visually checked ALONGSIDE
	 * "Empty", which is always meant to be this page's default. Re-assert Empty
	 * once everything has settled.
	 */
	@Override
	protected void restoreWidgetValues() {
		super.restoreWidgetValues();
		if (emptyRadio != null && !emptyRadio.isDisposed()) {
			emptyRadio.setSelection(true);
			if (clipboardRadio != null && !clipboardRadio.isDisposed())
				clipboardRadio.setSelection(false);
			if (fileRadio != null && !fileRadio.isDisposed())
				fileRadio.setSelection(false);
			syncEmptyImport();
		}
	}

	private void syncEmptyImport() {
		updateWidgetEnablements();
		if (getContainer() != null)
			getContainer().updateButtons();
		updatePageCompletion();
	}

	// ---- validation / creation ------------------------------------------

	@Override
	protected boolean validateDestinationGroup() {
		String n = nameText == null ? "" : nameText.getText().trim();
		if (n.isEmpty()) {
			if (isEmptyMode()) {
				setErrorMessage("Enter a name for the new " + typeName());
				return false;
			}
			return super.validateDestinationGroup(); // import: fall back to the file name
		}
		if (n.contains("/") || n.contains("\\") || n.contains(".")) {
			setErrorMessage("A name cannot contain '.', '/' or '\\'");
			return false;
		}
		if (parentContainer.findChieldByName(n + ".xml") != null) {
			setErrorMessage("A " + typeName() + " named “" + n + "” already exists in “"
					+ parentContainer.getName() + "”");
			return false;
		}
		return super.validateDestinationGroup();
	}

	/** Create the empty deck / collection. Runs on the UI thread from Finish. */
	@Override
	public void createEmptyElement() {
		createNewDeck(getNewElementName(), isDeckTarget(), wantVirtual(), wantUnsorted(), wantReadOnly(),
				parentContainer);
		createEmptyExtras(parentContainer);
	}

	/** Hook for NewDeckPage to also create the Sideboard / Extra siblings. */
	protected void createEmptyExtras(CollectionsContainer parent) {
		// NewDeckPage overrides
	}

	protected CardCollection createdElement() {
		return getElement() instanceof CardCollection ? (CardCollection) getElement() : null;
	}

	@Override
	public String getImportTargetDescription() {
		String n = getNewElementName();
		return "the new " + typeName() + (n == null || n.isEmpty() ? "" : " “" + n + "”");
	}
}
