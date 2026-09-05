package com.reflexit.magiccards.ui.views.lib;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.events.CardEvent;
import com.reflexit.magiccards.core.model.nav.CardCollection;
import com.reflexit.magiccards.core.model.nav.CardElement;
import com.reflexit.magiccards.core.model.storage.IFilteredCardStore;
import com.reflexit.magiccards.core.model.storage.IStorage;
import com.reflexit.magiccards.core.model.storage.IStorageInfo;
import com.reflexit.magiccards.core.model.xml.DeckFilteredCardFileStore;
import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.actions.MaterializeAction;
import com.reflexit.magiccards.ui.actions.OpenExtraAction;
import com.reflexit.magiccards.ui.actions.OpenSideboardAction;
import com.reflexit.magiccards.ui.dialogs.CardFilterDialog;
import com.reflexit.magiccards.ui.dialogs.DeckFilterDialog;
import com.reflexit.magiccards.ui.preferences.DeckViewPreferencePage;
import com.reflexit.magiccards.ui.utils.WaitUtils;
import com.reflexit.magiccards.ui.views.FolderPageGroup;
import com.reflexit.magiccards.ui.views.ViewPageGroup;
import com.reflexit.magiccards.ui.views.nav.CardsNavigatorView;

public class DeckView extends AbstractMyCardsView {
	public static final String ID = "com.reflexit.magiccards.ui.views.lib.DeckView";
	private CardCollection deck;
	private OpenSideboardAction sideboard;
	private OpenExtraAction extra;
	private org.eclipse.jface.action.Action fillExtra;
	private MaterializeAction materialize;

	/**
	 * The constructor.
	 */
	public DeckView() {
	}

	@Override
	protected ViewPageGroup createPageGroup() {
		return new FolderPageGroup(this::preActivate, this::postActivate);
	}

	@Override
	protected void createPages() {
		getPageGroup().loadExtensions(null);
	}

	@Override
	public String getHelpId() {
		return MagicUIActivator.helpId("viewdeck");
	}

	@Override
	protected void loadInitialInBackground() {
		String secondaryId = getDeckId();
		this.deck = DataManager.getInstance().getModelRoot().findCardCollectionById(secondaryId);
		if (deck != null) {
			// if (export!=null) export.selectionChanged(new
			// StructuredSelection(getCardCollection()));
			sideboard.setDeck(getCardCollection());
			extra.setDeck(getCardCollection());
		}
		refreshView();
	}

	@Override
	public void init(IViewSite site) throws PartInitException {
		super.init(site);
		site.getPage().addPartListener(PartListener.getInstance());
	}

	@Override
	public void createPartControl(org.eclipse.swt.widgets.Composite parent) {
		super.createPartControl(parent);
		// set the tab's icon/name-prefix synchronously, right away, instead of
		// waiting on the async deck-load job that only runs promptly for the
		// focused tab - see updatePartName()'s own comment for why
		updatePartName();
	}

	@Override
	public void dispose() {
		if (deck != null)
			this.deck.close();
		super.dispose();
	}

	@Override
	protected void makeActions() {
		super.makeActions();
		this.sideboard = new OpenSideboardAction(deck);
		this.extra = new OpenExtraAction(deck);
		this.fillExtra = new org.eclipse.jface.action.Action("Fill from Deck") {
			@Override
			public void run() {
				// already on the UI thread here (menu selection) - populate() fires
				// CardEvents that touch SWT widgets, so it must not run off a Job thread
				if (deck == null)
					return;
				final com.reflexit.magiccards.core.model.Location loc = deck.getLocation().toExtra();
				com.reflexit.magiccards.core.model.DeckAccessoriesPopulator.populate(loc);
			}
		};
		this.fillExtra.setToolTipText("Add every token / emblem / marker the deck needs, at count 0");
		this.materialize = new MaterializeAction(getFilteredStore().getCardStore());
	}

	// @Override
	// protected ExportAction createExportAction() {
	// CardCollection col = getCardCollection();
	// return new ExportAction(col == null ? new StructuredSelection() : new
	// StructuredSelection(col),
	// getPreferencePageId());
	// }
	protected IStorageInfo getStorageInfo() {
		IStorage<IMagicCard> storage = getFilteredStore().getCardStore().getStorage();
		if (storage instanceof IStorageInfo) {
			IStorageInfo si = ((IStorageInfo) storage);
			return si;
		}
		return null;
	}

	public static DeckView openCollection(final CardCollection col, IStructuredSelection sel) {
		if (col == null)
			return null;
		DeckView deckViewRes[] = new DeckView[1];
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				IWorkbenchWindow win = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
				if (win == null)
					return;
				IWorkbenchPage page = win.getActivePage();
				if (page == null)
					return;
				try {
					IViewPart navView = page.showView(CardsNavigatorView.ID, null, IWorkbenchPage.VIEW_CREATE);
					navView.getViewSite().getSelectionProvider().setSelection(new StructuredSelection(col));
					DeckView deckView = (DeckView) page.showView(DeckView.ID, col.getId(),
							IWorkbenchPage.VIEW_ACTIVATE);
					if (sel != null && !sel.isEmpty())
						deckView.setSelection(sel);
					deckViewRes[0] = deckView;
				} catch (PartInitException e) {
					MessageDialog.openError(MagicUIActivator.getShell(), "Error", e.getMessage());
				}
			}
		});
		return deckViewRes[0];
	}

	/**
	 * The sideboard/extra list of a deck looks like a plain deck tab; the
	 * sideboard/extra list of a collection looks like a plain collection tab -
	 * same icons the Cards Navigator tree uses for a deck vs. a collection,
	 * just applied here too instead of a dedicated sideboard/extra icon.
	 *
	 * <p>
	 * Reads the type straight off the file for {@code location} instead of going
	 * through {@code deck.isDeck()} (or needing {@code deck} at all): on a
	 * restart with several deck tabs restored at once, only the focused tab's
	 * {@code activate()} runs promptly - a background tab's `deck` model object
	 * may still be mid-load, or may never resolve until the tab is clicked. A
	 * location string + direct file read has no such dependency.
	 */
	private static String familyIcon(Location location) {
		File file = location.getFile();
		boolean isDeck = isDeckFile(file);
		String icon = isDeck ? "icons/obj16/ideck16.png" : "icons/obj16/lib16.png";
		return icon;
	}

	/** Whether {@code location}'s file already exists on disk - used to gate the
	 * Open Sideboard/Open Extra buttons so they only ever open, never create. */
	private static boolean familyMemberExists(Location location) {
		File file = location.getFile();
		return file != null && file.exists();
	}

	/** Whether {@code location} is this tab's own deck's sideboard or extra
	 * sibling (not the deck itself, which is handled separately). Used to
	 * decide whether an ADD_CONTAINER/REMOVE_CONTAINER event elsewhere should
	 * refresh this tab's Open Sideboard/Open Extra button enablement. */
	private boolean isFamilySibling(Location dataLocation) {
		if (dataLocation == null || deck == null)
			return false;
		Location main = deck.getLocation().toMainDeck();
		return dataLocation.equals(main.toSideboard()) || dataLocation.equals(main.toExtra());
	}

	private static boolean isDeckFile(File file) {
		if (file == null || !file.exists())
			return true; // matches CardCollection.isDeck()'s own default
		try (InputStream in = new FileInputStream(file)) {
			byte[] header = new byte[1000];
			int k = in.read(header);
			if (k == -1)
				return true;
			return new String(header, 0, k).contains("<type>deck</type");
		} catch (Exception e) {
			return true;
		}
	}

	private void setPartNameIfChanged(String newName) {
		if (!newName.equals(getPartName())) {
			setPartName(newName);
		}
	}

	private void setTitleImageIfChanged(String iconPath) {
		org.eclipse.swt.graphics.Image img = MagicUIActivator.getDefault().getImage(iconPath);
		boolean changed = getTitleImage() != img;
		if (changed)
			setTitleImage(img);
	}

	protected void updatePartName() {
		String deckId = getDeckId();
		Location location = Location.createLocation(deckId);
		String name = location.getName();
		setPartNameIfChanged(name);
		setTitleToolTip(deckId);

		// name prefix + icon only need the location string + the file on disk - set
		// them immediately, without waiting for `deck` (the CardCollection model
		// object) to finish its async load. On a restart, several deck/sideboard/
		// extra tabs restore at once and only the FOCUSED one's activate() runs
		// promptly; a background tab only gets here later via the async
		// loadInitialInBackground() job, so relying on `deck` left its icon wrong
		// until it was actually clicked. This part never needs `deck` at all.
		//
		// setPartNameIfChanged()/setTitleImageIfChanged() skip the call entirely
		// when the value is already right - updatePartName() runs more than once
		// per tab now (at creation, then again once the deck loads, then again on
		// activate()), and redundant tab-bar geometry changes right around a
		// selection click are a plausible contributor to the tab-strip redraw
		// glitch reported separately.
		if (location.isSideboard()) {
			setPartNameIfChanged("#" + name);
			setTitleImageIfChanged(familyIcon(location));
		} else if (location.isExtra()) {
			setPartNameIfChanged("~" + name);
			setTitleImageIfChanged(familyIcon(location));
		} else if (!isDeckFile(location.getFile())) {
			setTitleImageIfChanged("icons/lib32.png");
		}

		if (deck == null) {
			// IMagicControl c = getMagicControl();
			// c.setStatus("Loading " + deckId + "...");
			return;
		}
		// action enablement does need the resolved deck. The buttons only ever
		// open an existing sideboard/extra list, they never create one, so each
		// is enabled only when that sibling already exists on disk.
		Location main = deck.getLocation().toMainDeck();
		if (deck.getLocation().isSideboard()) {
			// can't open "the sideboard of a sideboard", but jumping straight to
			// this same deck's extra list from here makes sense - if it exists
			if (sideboard != null)
				sideboard.setEnabled(false);
			if (extra != null)
				extra.setEnabled(familyMemberExists(main.toExtra()));
			if (fillExtra != null)
				fillExtra.setEnabled(false);
		} else if (deck.getLocation().isExtra()) {
			if (sideboard != null)
				sideboard.setEnabled(familyMemberExists(main.toSideboard()));
			if (extra != null)
				extra.setEnabled(false);
			if (fillExtra != null)
				fillExtra.setEnabled(true);
		} else {
			if (sideboard != null)
				sideboard.setEnabled(familyMemberExists(main.toSideboard()));
			if (extra != null)
				extra.setEnabled(familyMemberExists(main.toExtra()));
			if (fillExtra != null)
				fillExtra.setEnabled(false);
		}
		// used in drop adapter
		getPartControl().setData("deck", deck);
	}

	@Override
	public void activate() {
		super.activate();
		updatePartName();
	}

	@Override
	protected void fillLocalToolBar(IToolBarManager manager) {
		// both stay in the toolbar everywhere - updatePartName() enables/disables
		// them so you can cross-navigate (sideboard -> extra and back) but never
		// "open the sideboard of a sideboard"
		manager.add(this.sideboard);
		manager.add(this.extra);
		manager.add(new Separator());
		super.fillLocalToolBar(manager);
	}

	@Override
	protected void fillLocalPullDown(IMenuManager manager) {
		super.fillLocalPullDown(manager);
		manager.add(this.sideboard);
		manager.add(this.extra);
		if (deck != null && deck.getLocation().isExtra())
			manager.add(this.fillExtra);
		manager.add(this.materialize);
	}

	@Override
	public void handleEvent(final CardEvent event) {
		if (getControl() == null || getControl().isDisposed())
			return;
		getControl().getDisplay().asyncExec(new Runnable() {
			@Override
			public void run() {
				if (deck == null)
					return;
				Location dataLocation = null;
				if (event.getData() instanceof CardElement) {
					dataLocation = ((CardElement) event.getData()).getLocation();
				}
				// System.err.println("DeckView " + getPartName() + " got " +
				// event);
				if (event.getType() == CardEvent.REMOVE_CONTAINER) {
					if (deck.getLocation().equals(dataLocation)) {
						close();
						deck.close();
						deck = null;
						// dispose();
						// System.err.println("---Removing itself");
						return;
					}
					// not this tab's own deck - if it's this deck's sideboard or extra
					// sibling being deleted from elsewhere, the Open Sideboard/Open Extra
					// buttons are only enabled while that file exists, so re-check now
					// instead of leaving them stale until the tab is reactivated
					if (isFamilySibling(dataLocation))
						updatePartName();
				} else if (event.getType() == CardEvent.ADD_CONTAINER) {
					// a sideboard/extra sibling appearing (e.g. re-created) should
					// re-enable its button the same way removal disables it
					if (isFamilySibling(dataLocation))
						updatePartName();
				} else if (event.getType() == CardEvent.RENAME_CONTAINER) {
					String secondaryId = getViewSite().getSecondaryId();
					Location srcLocation = (Location) event.getData();
					if (deck.getLocation().equals(srcLocation) || deck == event.getSource()) {
						updatePartName();
						if (!secondaryId.equals(deck.getLocation().getBaseFileName())) {
							// reopen newly named deck, to change secondary id
							openCollection(deck, getSelection());
							close();
							return;
						}
						reloadData();
					}
				} else {
					// System.err.println(event);
					// list control will do refresh
				}
			}
		});
	}

	@Override
	protected String getPreferencePageId() {
		return DeckViewPreferencePage.class.getName();
	}

	public CardCollection getCardCollection() {
		return deck;
	}

	@Override
	public void refreshView() {
		setStore();
		WaitUtils.asyncExec(() -> updatePartName());
		reloadData();
	}

	protected void setStore() {
		WaitUtils.waitForCondition(() -> (DeckFilteredCardFileStore.getStoreForKey(getDeckId()) != null), 5000, 300);
		IFilteredCardStore<IMagicCard> store = getFilteredStore();
	}

	public String getDeckId() {
		return getViewSite().getSecondaryId();
	}

	protected void updateViewer() {
		if (getControl().isDisposed())
			return;
		updatePartName();
		// updateActivePage();
	}

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public CardFilterDialog getCardFilterDialog() {
		return new DeckFilterDialog(getShell(), getFilterPreferenceStore());
	}
}