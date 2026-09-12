/*
 * Contributors:
 *     Rémi Dutil (2026) - createNewMenu(): dropped "Folder" from File ▸ New
 *                         (only Deck/Collection); explicit newWizardAction()
 *                         helper replacing BaseNewWizardMenu
 *     Rémi Dutil (2026) - default layout: Rulings now stacks with Printings/
 *                         Instances ("right" folder) instead of its own
 *                         "bottom" folder under Scryfall Database
 */
package com.reflexit.magiccards.ui;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;
import org.eclipse.ui.IPlaceholderFolderLayout;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWizard;
import org.eclipse.ui.actions.ActionFactory;

import com.reflexit.magiccards.ui.views.MagicDbView;
import com.reflexit.magiccards.ui.views.card.CardDescView;
import com.reflexit.magiccards.ui.views.card.RulingsView;
import com.reflexit.magiccards.ui.views.collector.CollectorView;
import com.reflexit.magiccards.ui.views.instances.InstancesView;
import com.reflexit.magiccards.ui.views.lib.DeckView;
import com.reflexit.magiccards.ui.views.lib.MyCardsView;
import com.reflexit.magiccards.ui.views.nav.CardsNavigatorView;
import com.reflexit.magiccards.ui.views.printings.PrintingsView;
import com.reflexit.magiccards.ui.wizards.BoosterGeneratorCollectionWizard;
import com.reflexit.magiccards.ui.wizards.BoosterGeneratorWizard;
import com.reflexit.magiccards.ui.wizards.NewCardCollectionWizard;
import com.reflexit.magiccards.ui.wizards.NewCollectionContainerWizard;
import com.reflexit.magiccards.ui.wizards.NewDeckWizard;

public class PerspectiveFactoryMagic implements IPerspectiveFactory {
	public static String PERSPECTIVE_ID = "com.reflexit.magiccards.ui.perspective.magic";
	public static String SEARCH_CONTEXT = "com.reflexit.magiccards.ui.context.search";
	public static String TABLES_CONTEXT = "com.reflexit.magiccards.ui.context";

	@Override
	public void createInitialLayout(IPageLayout layout) {
		String editorArea = layout.getEditorArea();
		layout.setEditorAreaVisible(false);
		IFolderLayout left = layout.createFolder("left", IPageLayout.LEFT, (float) 0.18, editorArea);
		IFolderLayout main = layout.createFolder("main", IPageLayout.RIGHT, (float) 0.82, editorArea);
		IFolderLayout right = layout.createFolder("right", IPageLayout.RIGHT, (float) 0.8, "main");
		IPlaceholderFolderLayout pf = layout.createPlaceholderFolder("up", IPageLayout.TOP, 0.5f, "main");
		IFolderLayout rightTop = layout.createFolder("rightTop", IPageLayout.TOP, 0.5f, "right");
		left.addView(CardDescView.ID);
		rightTop.addView(CardsNavigatorView.ID);
		right.addView(PrintingsView.ID);
		right.addView(InstancesView.ID);
		right.addView(RulingsView.ID);
		right.addPlaceholder(InstancesView.ID);
		main.addView(MagicDbView.ID);
		main.addView(MyCardsView.ID);
		main.addView(CollectorView.ID);
		main.addPlaceholder("org.eclipse.ui.browser.view");
		main.addPlaceholder("org.eclipse.ui.browser.view:*");
		pf.addPlaceholder(DeckView.ID + ":*");
		pf.addPlaceholder(DeckView.ID);
		// layout.addStandaloneView(MagicDbView.ID, false, IPageLayout.LEFT,
		// 1.0f, editorArea);
		layout.addShowViewShortcut(CardDescView.ID);
		layout.addShowViewShortcut(RulingsView.ID);
		layout.addShowViewShortcut(MagicDbView.ID);
		layout.addShowViewShortcut(CardsNavigatorView.ID);
		layout.addShowViewShortcut(MyCardsView.ID);
		layout.addNewWizardShortcut(NewDeckWizard.ID);
		layout.addNewWizardShortcut(NewCardCollectionWizard.ID);
		layout.addNewWizardShortcut(BoosterGeneratorWizard.ID);
		layout.addNewWizardShortcut(BoosterGeneratorCollectionWizard.ID);
		layout.addNewWizardShortcut(NewCollectionContainerWizard.ID);
		layout.getViewLayout(MagicDbView.ID).setCloseable(false);
		layout.getViewLayout(CardDescView.ID).setCloseable(false);
		layout.getViewLayout(CardsNavigatorView.ID).setCloseable(false);
	}

	/**
	 * Creates a "New..." menu
	 *
	 * @param window
	 *            - workbench window
	 * @param menu
	 * @return
	 *
	 */
	public static MenuManager createNewMenu(IWorkbenchWindow window) {
		// an explicit list - just the ManaDesk elements, no generic "Other..."
		String newId = ActionFactory.NEW.getId();
		MenuManager newMenu = new MenuManager("New", newId);
		newMenu.setActionDefinitionId("org.eclipse.ui.file.newQuickMenu"); //$NON-NLS-1$
		newMenu.add(new Separator(newId));
		newMenu.add(newWizardAction(window, "Deck", "icons/obj16/ideck16.png", NewDeckWizard::new));
		newMenu.add(newWizardAction(window, "Collection", "icons/obj16/lib16.png", NewCardCollectionWizard::new));
		// "Folder" is navigator-only (right-click ▸ New Folder…) - not useful from
		// the main File menu, which has no notion of "current container"
		newMenu.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
		return newMenu;
	}

	private static IAction newWizardAction(IWorkbenchWindow window, String label, String icon,
			java.util.function.Supplier<? extends IWorkbenchWizard> factory) {
		Action a = new Action(label) {
			@Override
			public void run() {
				ISelection s = window.getSelectionService().getSelection();
				IStructuredSelection sel = s instanceof IStructuredSelection ? (IStructuredSelection) s
						: StructuredSelection.EMPTY;
				IWorkbenchWizard wizard = factory.get();
				wizard.init(window.getWorkbench(), sel);
				new WizardDialog(window.getShell(), wizard).open();
			}
		};
		a.setImageDescriptor(MagicUIActivator.getImageDescriptor(icon));
		return a;
	}
}
