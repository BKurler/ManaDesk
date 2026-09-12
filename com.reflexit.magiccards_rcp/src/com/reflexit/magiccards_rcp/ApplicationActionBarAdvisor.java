/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - dropped the generic File ▸ Import action; deck/collection
 *                         import is navigator-only now
 *     Rémi Dutil (2026) - Show View is now a custom, explicitly-filtered menu
 *                         (not the stock VIEWS_SHORTLIST) so DeckView (only ever
 *                         meaningful opened with a specific deck/collection, never
 *                         bare) and the unsupported MTG Tournament views can be
 *                         hidden - DeckView is not reachable through Activities
 *                         filtering alone: it also matches the broad, always-on
 *                         "cardorganizer" activity pattern
 *                         (com\.reflexit\.magiccards\..*\/.*), which covers this
 *                         whole plugin, so a narrower disabled activity just for
 *                         it would never win (Activities are additive/OR - an
 *                         item bound to several activities is visible if ANY one
 *                         of them is enabled)
 *     Rémi Dutil (2026) - also hides the stock "Internal Web Browser"
 *                         (org.eclipse.ui.browser.view) and "Welcome"
 *                         (org.eclipse.ui.internal.introview) platform views
 *     Rémi Dutil (2026) - dropped the Window ▸ "Cards Organizer" perspective-
 *                         switch action (and the now-unused OpenPerspectiveAction
 *                         class) - this app has exactly one perspective, so
 *                         switching to it was a no-op
 */
package com.reflexit.magiccards_rcp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.GroupMarker;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.activities.WorkbenchActivityHelper;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.actions.ActionFactory.IWorkbenchAction;
import org.eclipse.ui.application.ActionBarAdvisor;
import org.eclipse.ui.application.IActionBarConfigurer;
import org.eclipse.ui.views.IViewDescriptor;
import org.eclipse.ui.views.IViewRegistry;

import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.PerspectiveFactoryMagic;
import com.reflexit.magiccards.ui.views.lib.DeckView;

/**
 * An action bar advisor is responsible for creating, adding, and disposing of
 * the actions added to a workbench window. Each window will be populated with
 * new actions.
 */
public class ApplicationActionBarAdvisor extends ActionBarAdvisor {
	// Actions - important to allocate these only in makeActions, and then use
	// them
	// in the fill methods. This ensures that the actions aren't recreated
	// when fillActionBars is called with FILL_PROXY.
	private IWorkbenchAction exitAction;
	private IWorkbenchAction help_contents;
	private IWorkbenchAction windowAction;
	private IWorkbenchAction aboutAction;
	private IWorkbenchAction newAction;
	private IWorkbenchWindow window;
	private IAction resetAction;
	private IWorkbenchAction searchHelpAction;
	private IWorkbenchAction dynamicHelpAction;
	private IAction exportAction;
	private MenuManager showViewMenuMgr;

	/** Views that must never appear in "Show View": DeckView only makes sense
	 *  opened with a specific deck/collection secondary id (never meaningfully
	 *  "just open a Deck"); the MTG Tournament views/perspective are dead,
	 *  unsupported code still present in com.reflexit.mtgtournament.ui; and the
	 *  stock Eclipse "Internal Web Browser" / "Welcome" views are platform
	 *  boilerplate this app has no use for. */
	private static final Set<String> HIDDEN_SHOW_VIEW_IDS = new HashSet<>(Arrays.asList(DeckView.ID,
			"com.reflexit.mtgtournament.ui.tour.views.TNavigatorView",
			"com.reflexit.mtgtournament.ui.tour.views.TournamentView",
			"com.reflexit.mtgtournament.ui.tour.views.PlayersView", "com.reflexit.mtgtournament.ui.views.TimerView",
			"org.eclipse.ui.browser.view", "org.eclipse.ui.internal.introview"));

	public ApplicationActionBarAdvisor(IActionBarConfigurer configurer) {
		super(configurer);
		this.window = configurer.getWindowConfigurer().getWindow();
	}

	/**
	 * Returns the window to which this action builder is contributing.
	 */
	private IWorkbenchWindow getWindow() {
		return this.window;
	}

	@Override
	protected void makeActions(final IWorkbenchWindow window) {
		// Creates the actions and registers them.
		// Registering is needed to ensure that key bindings work.
		// The corresponding commands keybindings are defined in the plugin.xml
		// file.
		// Registering also provides automatic disposal of the actions when
		// the window is closed.
		this.exitAction = ActionFactory.QUIT.create(window);
		register(this.exitAction);
		this.help_contents = ActionFactory.HELP_CONTENTS.create(window);
		register(this.help_contents);
		searchHelpAction = ActionFactory.HELP_SEARCH.create(window);
		register(searchHelpAction);
		dynamicHelpAction = ActionFactory.DYNAMIC_HELP.create(window);
		register(dynamicHelpAction);
		this.windowAction = ActionFactory.PREFERENCES.create(window);
		register(this.windowAction);
		this.aboutAction = ActionFactory.ABOUT.create(window);
		register(this.aboutAction);
		this.newAction = ActionFactory.NEW.create(window);
		register(this.newAction);
		this.resetAction = ActionFactory.RESET_PERSPECTIVE.create(window);
		register(this.resetAction);
		this.exportAction = ActionFactory.EXPORT.create(window);
		register(this.exportAction);
		showViewMenuMgr = new MenuManager("Show View", "showView");
		showViewMenuMgr.setRemoveAllWhenShown(true);
		showViewMenuMgr.addMenuListener(this::fillShowViewMenu);
	}

	/** Rebuilt every time the menu opens (mirrors the stock VIEWS_SHORTLIST's
	 *  own dynamic behavior), listing every registered view except
	 *  {@link #HIDDEN_SHOW_VIEW_IDS} and anything Activities already filters
	 *  out. Flat and alphabetical rather than grouped by category - simpler,
	 *  and with the tournament views gone there is only the one real category
	 *  ("ManaDesk") left worth grouping. */
	private void fillShowViewMenu(IMenuManager manager) {
		IViewRegistry registry = PlatformUI.getWorkbench().getViewRegistry();
		List<IViewDescriptor> visible = new ArrayList<>();
		for (IViewDescriptor d : registry.getViews()) {
			if (HIDDEN_SHOW_VIEW_IDS.contains(d.getId()))
				continue;
			if (!WorkbenchActivityHelper.filterItem(d))
				visible.add(d);
		}
		visible.sort(Comparator.comparing(IViewDescriptor::getLabel, String.CASE_INSENSITIVE_ORDER));
		for (IViewDescriptor d : visible)
			manager.add(new ShowViewAction(d));
	}

	private class ShowViewAction extends Action {
		private final String viewId;

		ShowViewAction(IViewDescriptor d) {
			super(d.getLabel(), d.getImageDescriptor());
			this.viewId = d.getId();
		}

		@Override
		public void run() {
			try {
				window.getActivePage().showView(viewId);
			} catch (PartInitException e) {
				MagicUIActivator.log(e);
				ErrorDialog.openError(new Shell(), "Error", e.getMessage(),
						new Status(IStatus.ERROR, Activator.PLUGIN_ID, e.getMessage(), e));
			}
		}
	}

	@Override
	protected void fillMenuBar(IMenuManager menuBar) {
		MenuManager fileMenu = new MenuManager("&File", IWorkbenchActionConstants.M_FILE);
		MenuManager winMenu = new MenuManager("&Window", IWorkbenchActionConstants.M_WINDOW);
		MenuManager helpMenu = new MenuManager("&Help", IWorkbenchActionConstants.M_HELP);
		menuBar.add(fileMenu);
		menuBar.add(winMenu);
		// Add a group marker indicating where action set menus will appear.
		menuBar.add(new GroupMarker(IWorkbenchActionConstants.MB_ADDITIONS));
		menuBar.add(helpMenu);
		winMenu.add(showViewMenuMgr);
		winMenu.add(this.resetAction);
		winMenu.add(new Separator());
		winMenu.add(new GroupMarker(IWorkbenchActionConstants.MB_ADDITIONS));
		winMenu.add(this.windowAction);
		// file
		fileMenu.add(PerspectiveFactoryMagic.createNewMenu(getWindow()));
		fileMenu.add(new Separator());
		fileMenu.add(this.exportAction);
		fileMenu.add(new Separator());
		fileMenu.add(new GroupMarker(IWorkbenchActionConstants.MB_ADDITIONS));
		fileMenu.add(this.exitAction);
		// help
		helpMenu.add(this.help_contents);
		helpMenu.add(searchHelpAction);
		helpMenu.add(dynamicHelpAction);
		helpMenu.add(new GroupMarker(IWorkbenchActionConstants.MB_ADDITIONS));
		helpMenu.add(this.aboutAction);
	}
}
