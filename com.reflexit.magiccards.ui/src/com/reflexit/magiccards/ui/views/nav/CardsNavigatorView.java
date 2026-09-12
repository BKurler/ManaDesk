/*******************************************************************************
 * Copyright (c) 2008 Alena Laskavaia.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Alena Laskavaia - initial API and implementation
 *******************************************************************************/

/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - selection-aware context menu (deck side vs collection
 *                         side); import is navigator-only ("Import into <X>…")
 *     Rémi Dutil (2026) - "Open (Activate)" now also works on a multi-selection
 *                         of decks/collections - opens each one, the last
 *                         selected ends up active (selectedCollections())
 */

package com.reflexit.magiccards.ui.views.nav;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.commands.ActionHandler;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.ColumnViewer;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchWizard;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.internal.IWorkbenchGraphicConstants;
import org.eclipse.ui.internal.WorkbenchImages;
import org.eclipse.ui.part.IShowInTarget;
import org.eclipse.ui.part.ShowInContext;
import org.eclipse.ui.part.ViewPart;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.MagicException;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.events.CardEvent;
import com.reflexit.magiccards.core.model.events.ICardEventListener;
import com.reflexit.magiccards.core.model.nav.CardCollection;
import com.reflexit.magiccards.core.model.nav.CardElement;
import com.reflexit.magiccards.core.model.nav.CardOrganizer;
import com.reflexit.magiccards.core.model.nav.MagicDbContainter;
import com.reflexit.magiccards.core.model.nav.ModelRoot;
import com.reflexit.magiccards.core.model.storage.ILocatable;
import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.commands.DeleteHandler;
import com.reflexit.magiccards.ui.dialogs.LocationPickerDialog;
import com.reflexit.magiccards.ui.dnd.MagicCardTransfer;
import com.reflexit.magiccards.ui.exportWizards.ExportAction;
import com.reflexit.magiccards.ui.utils.WaitUtils;
import com.reflexit.magiccards.ui.views.MagicDbView;
import com.reflexit.magiccards.ui.views.lib.DeckView;
import com.reflexit.magiccards.ui.views.lib.MyCardsView;
import com.reflexit.magiccards.ui.wizards.ImportIntoCollectionWizard;
import com.reflexit.magiccards.ui.wizards.ImportIntoDeckWizard;
import com.reflexit.magiccards.ui.wizards.NewCardCollectionWizard;
import com.reflexit.magiccards.ui.wizards.NewCollectionContainerWizard;
import com.reflexit.magiccards.ui.wizards.NewDeckWizard;

public class CardsNavigatorView extends ViewPart implements ICardEventListener, IPropertyChangeListener, IShowInTarget {
	public static final String ID = CardsNavigatorView.class.getName();
	private Action doubleClickAction;
	private CardsNavigatiorManager manager;
	private Action export;
	private Action importInto;
	private Action newCollectionWizard;
	private Action newDeckWizard;
	private Action newFolderWizard;
	private Action moveTo;
	private Action openInDeckView;
	private Action openInMyCardsView;
	private Action showDatabase;
	private Action showSideboards;
	private Action refresh;
	private Composite top;
	private ModelRoot modelRoot;
	private ICardEventListener modelListener = this;

	/**
	 * The constructor.
	 */
	public CardsNavigatorView() {
	}

	/**
	 * This is a callback that will allow us to create the viewer and initialize it.
	 */
	@Override
	public void createPartControl(Composite parent) {
		top = new Composite(parent, SWT.NONE);
		GridLayout gl = new GridLayout();
		gl.marginHeight = 0;
		gl.marginWidth = 0;
		top.setLayout(gl);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(top, MagicUIActivator.helpId("viewcardnav"));
		createTable(top);
		makeActions();
		hookContextMenu();
		hookDoubleClickAction();
		contributeToActionBars();
		addDragAndDrop();
	}

	private void addDragAndDrop() {
		int ops = DND.DROP_COPY | DND.DROP_MOVE;
		Transfer[] transfers = new Transfer[] { MagicCardTransfer.getInstance(), MagicDeckTransfer.getInstance() };
		Transfer[] transfers2 = new Transfer[] { MagicDeckTransfer.getInstance() };
		getViewer().addDropSupport(ops, transfers, new MagicNavDropAdapter(getViewer()));
		getViewer().addDragSupport(DND.DROP_MOVE, transfers2, new MagicNavDragListener(getViewer()));
	}

	private void createTable(Composite parent) {
		this.manager = new CardsNavigatiorManager();
		Control control = this.manager.createContents(parent, SWT.MULTI);
		((Composite) control).setLayoutData(new GridData(GridData.FILL_BOTH));
		// ADD the JFace Viewer as a Selection Provider to the View site.
		getSite().setSelectionProvider(this.manager.getViewer());
	}

	public ColumnViewer getViewer() {
		return this.manager.getViewer();
	}

	private void hookContextMenu() {
		MenuManager menuMgr = new MenuManager("#PopupMenu");
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			@Override
			public void menuAboutToShow(IMenuManager manager) {
				CardsNavigatorView.this.fillContextMenu(manager);
			}
		});
		Menu menu = menuMgr.createContextMenu(getViewer().getControl());
		getViewer().getControl().setMenu(menu);
		getSite().registerContextMenu(menuMgr, getViewer());
	}

	private void contributeToActionBars() {
		IActionBars bars = getViewSite().getActionBars();
		fillLocalPullDown(bars.getMenuManager());
		fillLocalToolBar(bars.getToolBarManager());
		setGlobalHandlers();
	}

	/** Move the selected deck(s) / collection(s) / folder(s) to another folder -
	 *  same side only (Decks stay under Decks, Collections under Collections).
	 *  Replaces the old Cut/Paste pair: no clipboard state, one dialog. */
	class MoveToAction extends Action {
		public MoveToAction() {
			super("Move to…");
		}

		@Override
		public void run() {
			IStructuredSelection sel = (IStructuredSelection) getViewer().getSelection();
			CardElement[] items = (CardElement[]) sel.toList().toArray(new CardElement[sel.size()]);
			if (items.length == 0)
				return;
			ModelRoot.Side side = getModelRoot().sideOf(items[0]);
			LocationPickerDialog dialog = new LocationPickerDialog(getShell(), SWT.SINGLE | SWT.READ_ONLY) {
				@Override
				protected Control createDialogArea(Composite parent) {
					Control x = super.createDialogArea(parent);
					setMessage("Select the " + (side == ModelRoot.Side.DECK ? "Decks" : "Collections")
							+ " folder to move " + (items.length == 1 ? "“" + items[0].getName() + "”" : "these")
							+ " into.");
					return x;
				}
			};
			// only folders on the same side are valid move targets - a deck can
			// never land under Collections and vice versa
			dialog.setContainersOnly(true);
			dialog.setSideFilter(side);
			if (dialog.open() != Window.OK || dialog.getSelection() == null || dialog.getSelection().isEmpty())
				return;
			CardElement target = (CardElement) dialog.getSelection().getFirstElement();
			if (!(target instanceof CardOrganizer) || getModelRoot().sideOf(target) != side) {
				MessageDialog.openError(getShell(), "Cannot Move",
						"Pick a folder under \"" + getModelRoot().containerFor(side).getName() + "\".");
				return;
			}
			for (CardElement item : items) {
				if (target == item || target.isAncestor(item)) {
					MessageDialog.openError(getShell(), "Cannot Move", "Cannot move an item into itself.");
					return;
				}
			}
			try {
				getModelRoot().move(items, (CardOrganizer) target);
			} catch (MagicException e) {
				MessageDialog.openError(getShell(), "Error", "Cannot perform this operation: " + e.getMessage());
			}
		}

		@Override
		public boolean isEnabled() {
			IStructuredSelection s = (IStructuredSelection) getViewer().getSelection();
			if (s == null || s.isEmpty())
				return false;
			ModelRoot.Side side = null;
			for (Object o : s.toList()) {
				if (!isMovable(o))
					return false;
				ModelRoot.Side os = getModelRoot().sideOf((CardElement) o);
				if (side == null)
					side = os;
				else if (side != os)
					return false; // mixed decks/collections selected - ambiguous target
			}
			return true;
		}
	}

	class DeleteAction extends Action {
		public DeleteAction() {
			super("Delete");
		}

		@Override
		public void run() {
			IStructuredSelection sel = (IStructuredSelection) getViewSite().getSelectionProvider().getSelection();
			DeleteHandler.remove(sel);
		}

		@Override
		public boolean isEnabled() {
			IStructuredSelection sel = (IStructuredSelection) getViewSite().getSelectionProvider().getSelection();
			if (sel.isEmpty())
				return false;
			for (Iterator iterator = sel.iterator(); iterator.hasNext();) {
				Object o = iterator.next();
				if (!(o instanceof CardElement) || isFixedNode((CardElement) o))
					return false;
			}
			return true;
		}
	}

	protected void setGlobalHandlers() {
		IHandlerService service = (getSite()).getService(IHandlerService.class);
		service.activateHandler("org.eclipse.ui.edit.delete", new ActionHandler(new DeleteAction()));
	}

	private void fillLocalPullDown(IMenuManager manager) {
		// the view menu keeps just the two top-level "New" commands
		manager.add(newDeckWizard);
		manager.add(newCollectionWizard);
		manager.add(new Separator());
		manager.add(export);
		manager.add(new Separator());
		manager.add(showSideboards);
		manager.add(refresh);
	}

	/** The one selected {@link CardElement}, or {@code null} for a multi / empty
	 *  selection or a non-element node. */
	private CardElement selectedElement() {
		ISelection s = getViewer().getSelection();
		if (s instanceof IStructuredSelection && ((IStructuredSelection) s).size() == 1) {
			Object o = ((IStructuredSelection) s).getFirstElement();
			if (o instanceof CardElement)
				return (CardElement) o;
		}
		return null;
	}

	/** Every selected {@link CardCollection} (deck or collection - never a
	 *  folder), in selection order, or an empty list if the selection is empty,
	 *  contains a non-collection (a folder, the Scryfall DB…), or isn't a
	 *  structured selection at all. Used by "Open (Activate)" so it also works
	 *  for a multi-selection - opening several decks/collections at once, each
	 *  in its own tab. */
	private java.util.List<CardCollection> selectedCollections() {
		ISelection s = getViewer().getSelection();
		if (!(s instanceof IStructuredSelection))
			return java.util.Collections.emptyList();
		java.util.List<?> all = ((IStructuredSelection) s).toList();
		if (all.isEmpty())
			return java.util.Collections.emptyList();
		java.util.List<CardCollection> result = new java.util.ArrayList<>(all.size());
		for (Object o : all) {
			if (!(o instanceof CardCollection))
				return java.util.Collections.emptyList();
			result.add((CardCollection) o);
		}
		return result;
	}

	/** A built-in node the user must not rename / move / delete: the roots
	 *  (My Cards, Decks, Collections), the Scryfall database, the default library. */
	private boolean isFixedNode(CardElement e) {
		ModelRoot r = getModelRoot();
		return e == null || e instanceof MagicDbContainter || e == r || e == r.getMyCardsContainer()
				|| e == r.getDeckContainer() || e == r.getCollectionsContainer() || e == r.getDefaultLib();
	}

	/** A user folder under Decks / Collections (not one of the fixed roots). */
	private boolean isUserFolder(CardElement e) {
		return e instanceof CardOrganizer && !(e instanceof CardCollection) && !isFixedNode(e)
				&& getModelRoot().sideOf(e) != null;
	}

	/** True for a node the user may cut / move: a deck, a collection or a user
	 *  folder - never a fixed root or the Scryfall database. */
	private boolean isMovable(Object o) {
		return o instanceof CardElement && !isFixedNode((CardElement) o)
				&& getModelRoot().sideOf((CardElement) o) != null;
	}

	private void fillContextMenu(IMenuManager manager) {
		CardElement sel = selectedElement();

		// the Scryfall database node: only "show it"
		if (sel instanceof MagicDbContainter) {
			manager.add(showDatabase);
			manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
			return;
		}

		ModelRoot root = getModelRoot();
		ModelRoot.Side side = root.sideOf(sel); // null: root / My Cards / nothing
		boolean sideRoot = sel == root.getDeckContainer() || sel == root.getCollectionsContainer();
		boolean folder = isUserFolder(sel);
		boolean pile = sel instanceof CardCollection;

		// "New …" - flat, no submenu; each option only where it can apply
		if (side != ModelRoot.Side.COLLECTION)
			manager.add(newDeckWizard);
		if (side != ModelRoot.Side.DECK)
			manager.add(newCollectionWizard);
		// a folder can only be made inside a side root or another user folder
		if (sideRoot || folder)
			manager.add(newFolderWizard);

		if (pile) {
			manager.add(new Separator());
			importInto.setText("Import into ‘" + sel.getName() + "’…");
			manager.add(importInto);
			manager.add(export);
		}

		if (pile || folder) {
			manager.add(new Separator());
			manager.add(moveTo);
		}

		manager.add(new Separator());
		// sideboards / extras are a deck-only notion - meaningless under Collections
		if (side != ModelRoot.Side.COLLECTION)
			manager.add(showSideboards);
		// single deck/collection (pile) OR a multi-selection made up entirely of
		// decks/collections - opens each one, the last selected ends up active
		if (pile || !selectedCollections().isEmpty())
			manager.add(openInDeckView);
		if (pile || folder) {
			manager.add(openInMyCardsView);
			openInMyCardsView.setEnabled(true);
		}
		// Other plug-ins can contribute their actions here
		manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
	}

	private void fillLocalToolBar(IToolBarManager manager) {
		manager.add(newDeckWizard);
		manager.add(newCollectionWizard);
		manager.add(export);
		manager.add(new Separator());
	}

	private void openWizard(IWorkbenchWizard wizard) {
		wizard.init(getSite().getWorkbenchWindow().getWorkbench(), (IStructuredSelection) getViewer().getSelection());
		WizardDialog dialog = new WizardDialog(getShell(), wizard);
		dialog.create();
		dialog.open();
	}

	private void makeActions() {
		// double cick
		this.doubleClickAction = new Action() {
			@Override
			public void run() {
				runDoubleClick();
			}
		};
		this.export = new ExportAction();
		this.newCollectionWizard = new Action("New Collection…") {
			{
				setImageDescriptor(MagicUIActivator.getImageDescriptor("icons/obj16/lib16.png"));
			}

			@Override
			public void run() {
				openWizard(new NewCardCollectionWizard());
			}
		};
		this.newDeckWizard = new Action("New Deck…") {
			{
				setImageDescriptor(MagicUIActivator.getImageDescriptor("icons/obj16/ideck16.png"));
			}

			@Override
			public void run() {
				openWizard(new NewDeckWizard());
			}
		};
		this.newFolderWizard = new Action("New Folder…") {
			{
				setImageDescriptor(MagicUIActivator.getImageDescriptor("icons/obj16/folder-lib.png"));
			}

			@Override
			public void run() {
				openWizard(new NewCollectionContainerWizard());
			}
		};
		this.moveTo = new MoveToAction();
		this.importInto = new Action("Import into…") {
			{
				setImageDescriptor(WorkbenchImages.getImageDescriptor(IWorkbenchGraphicConstants.IMG_ETOOL_IMPORT_WIZ));
			}

			@Override
			public void run() {
				// only offered on a selected deck / collection - open that side's
				// "Import into an existing ..." wizard, pre-targeted to the selection
				ModelRoot.Side side = getModelRoot().sideOf(selectedElement());
				openWizard(side == ModelRoot.Side.COLLECTION ? new ImportIntoCollectionWizard()
						: new ImportIntoDeckWizard());
			}
		};
		this.refresh = new Action("Refresh", SWT.NONE) {
			{
				setImageDescriptor(MagicUIActivator.getImageDescriptor("icons/clcl16/refresh.gif"));
			}

			@Override
			public void run() {
				// reset listeners just in case model root changed
				modelRoot.removeListener(modelListener);
				modelRoot = getModelRoot();
				modelRoot.addListener(modelListener);
				// refresh model and view
				modelRoot.refresh();
				getViewer().refresh(true);
			}
		};
		this.showSideboards = new Action("Show Sideboards and Extras", SWT.TOGGLE) {
			@Override
			public void run() {
				showSideboardFilter();
			}
		};
		showSideboardFilter(); // activate filter
		getViewer().addSelectionChangedListener((ISelectionChangedListener) this.export);
		openInDeckView = new Action("Open (Activate)") {
			@Override
			public void run() {
				// A single selection keeps going through runDoubleClick() (it also
				// handles the Scryfall DB / My Cards folder cases openInDeckView
				// itself is never shown for). A multi-selection opens every
				// selected deck/collection, each in its own tab - openCollection()
				// activates the tab it opens, so calling it in selection order
				// means the LAST one ends up the focused/active tab.
				java.util.List<CardCollection> all = selectedCollections();
				if (all.size() > 1) {
					for (CardCollection d : all)
						openDeckView(d);
				} else if (isEnabled()) {
					runDoubleClick();
				}
			}

			@Override
			public boolean isEnabled() {
				ISelection selection = getViewer().getSelection();
				if (selection.isEmpty())
					return false;
				if (((IStructuredSelection) selection).size() > 1)
					return !selectedCollections().isEmpty();
				Object obj = ((IStructuredSelection) selection).getFirstElement();
				return obj instanceof CardCollection;
			}
		};
		showDatabase = new Action("Show Scryfall Database") {
			@Override
			public void run() {
				try {
					getViewSite().getWorkbenchWindow().getActivePage().showView(MagicDbView.ID);
				} catch (PartInitException e) {
					MagicUIActivator.log(e);
				}
			}
		};
		// filters the (separate) My Cards view down to just this deck's / collection's
		// / folder's cards - a different, more powerful table (grouping, sorting,
		// extra columns) than this navigator or the deck tab itself
		openInMyCardsView = new Action("Show These Cards in My Cards View") {
			@Override
			public void run() {
				if (isEnabled()) {
					ISelection selection = getViewer().getSelection();
					Object obj = ((IStructuredSelection) selection).getFirstElement();
					MyCardsView view;
					try {
						view = (MyCardsView) getViewSite().getWorkbenchWindow().getActivePage()
								.showView(MyCardsView.ID);
						view.setLocationFilter(((CardElement) obj).getLocation());
					} catch (PartInitException e) {
						// error
					}
				}
			}

			@Override
			public boolean isEnabled() {
				ISelection selection = getViewer().getSelection();
				if (selection.isEmpty() || ((IStructuredSelection) selection).size() != 1)
					return false;
				Object obj = ((IStructuredSelection) selection).getFirstElement();
				return (obj instanceof CardElement && !(obj instanceof MagicDbContainter));
			}
		};
	}

	private void hookDoubleClickAction() {
		getViewer().addDoubleClickListener(new IDoubleClickListener() {
			@Override
			public void doubleClick(DoubleClickEvent event) {
				CardsNavigatorView.this.doubleClickAction.run();
			}
		});
	}

	private void showMessage(String message) {
		MessageDialog.openInformation(getViewSite().getShell(), "Scryfall Cards", message);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.ui.part.ViewPart#init(org.eclipse.ui.IViewSite)
	 */
	@Override
	public void init(IViewSite site) throws PartInitException {
		super.init(site);
		modelRoot = getModelRoot();
		modelRoot.addListener(modelListener);
		PlatformUI.getWorkbench().getThemeManager().addPropertyChangeListener(this);
	}

	public ModelRoot getModelRoot() {
		return DataManager.getInstance().getModelRoot();
	}

	@Override
	public void dispose() {
		modelRoot.removeListener(modelListener);
		this.manager.dispose();
		PlatformUI.getWorkbench().getThemeManager().removePropertyChangeListener(this);
		super.dispose();
	}

	/**
	 * Passing the focus request to the viewer's control.
	 */
	@Override
	public void setFocus() {
		top.setFocus();
	}

	protected void runDoubleClick() {
		ISelection selection = getViewer().getSelection();
		Object obj = ((IStructuredSelection) selection).getFirstElement();
		if (obj instanceof MagicDbContainter) {
			try {
				getViewSite().getWorkbenchWindow().getActivePage().showView(MagicDbView.ID);
			} catch (PartInitException e) {
				MagicUIActivator.log(e);
			}
		} else if (obj instanceof CardCollection) {
			// MyCardsView view = (MyCardsView)
			// getViewSite().getWorkbenchWindow().getActivePage().showView(
			// MyCardsView.ID);
			// view.setLocationFilter(((CardCollection) obj).getLocation());
			CardCollection d = (CardCollection) obj;
			openDeckView(d);
		} else if (obj instanceof CardOrganizer) {
			try {
				MyCardsView view = (MyCardsView) getViewSite().getWorkbenchWindow().getActivePage()
						.showView(MyCardsView.ID);
				view.setLocationFilter(((CardOrganizer) obj).getLocation());
			} catch (PartInitException e) {
				MagicUIActivator.log(e);
			}
		} else {
			showMessage("Cannot open this object " + obj.toString());
		}
	}

	private void openDeckView(CardCollection d) {
		DeckView.openCollection(d, null);
	}

	/** Sideboard/extra lists are opened deliberately via their own Open
	 * Sideboard/Open Extra buttons, never automatically - e.g. when the "New
	 * Deck" wizard creates one alongside the main deck, only the deck itself
	 * should end up open. */
	private static boolean isSideboardOrExtra(CardCollection coll) {
		Location loc = coll.getLocation();
		return loc.isSideboard() || loc.isExtra();
	}

	public Shell getShell() {
		return getViewSite().getShell();
	}

	@Override
	public void handleEvent(final CardEvent event) {
		int type = event.getType();
		switch (type) {
		case CardEvent.ADD_CONTAINER:
			Object obj = event.getData();
			if (obj instanceof CardCollection && !isSideboardOrExtra((CardCollection) obj)) {
				WaitUtils.scheduleJob("Opening deck", () -> {
					CardCollection coll = (CardCollection) obj;
					boolean gotit = WaitUtils.waitForCondition(() -> (coll.getStorageInfo() != null), 3000, 100);
					WaitUtils.asyncExec(() -> manager.getViewer().refresh(true));
					if (gotit)
						DeckView.openCollection(coll, null);
				});
			} else {
				WaitUtils.asyncExec(() -> manager.getViewer().refresh(true));
			}
			break;
		case CardEvent.REMOVE_CONTAINER:
		case CardEvent.RENAME_CONTAINER:
		case CardEvent.UPDATE_CONTAINER:
			WaitUtils.asyncExec(() -> manager.getViewer().refresh(true));
			break;
		default:
			break;
		}
	}

	protected void showSideboardFilter() {
		Map<String, Object> prop = new HashMap<String, Object>();
		boolean state = !showSideboards.isChecked();
		prop.put(CardsNavigatorContentProvider.FILTER_SIDEBOARDS, state);
		getViewer().setFilters(new ViewerFilter[] { CardsNavigatorContentProvider.getFilter(prop) });
	}

	@Override
	public void propertyChange(PropertyChangeEvent event) {
		manager.refresh();
	}

	@Override
	public boolean show(ShowInContext context) {
		ISelection selection = context.getSelection();
		if (selection.isEmpty() || !(selection instanceof IStructuredSelection)) {
			selection = new StructuredSelection(context.getInput());
		}
		IStructuredSelection iss = (IStructuredSelection) selection;
		Object element = iss.getFirstElement();
		if (element instanceof ILocatable) {
			Location loc = ((ILocatable) element).getLocation();
			if (loc == null || loc == Location.NO_WHERE) {
				return false;
			}
			final ModelRoot container = DataManager.getInstance().getModelRoot();
			CardCollection col = container.findCardCollectionById(loc.getName());
			if (col != null) {
				getViewer().setSelection(new StructuredSelection(col), true);
				DeckView.openCollection(col, iss);
				return true;
			}
		}
		return false;
	}
}