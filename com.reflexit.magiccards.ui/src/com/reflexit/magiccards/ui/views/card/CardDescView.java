/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - LoadCardJob caches the card image on its background
 *                         thread so the UI-thread render never blocks on the network
 */

package com.reflexit.magiccards.ui.views.card;


import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ContributionItemFactory;
import org.eclipse.ui.browser.IWebBrowser;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;
import org.eclipse.ui.part.IShowInSource;
import org.eclipse.ui.part.IShowInTarget;
import org.eclipse.ui.part.ShowInContext;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.progress.UIJob;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.MagicLogger;
import com.reflexit.magiccards.core.model.CardGroup;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICardGroup;
import com.reflexit.magiccards.core.sync.ParseGathererOracle;
import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.dialogs.EditMagicCardDialog;
import com.reflexit.magiccards.ui.dialogs.EditMagicCardPhysicalDialog;
import com.reflexit.magiccards.ui.preferences.PreferenceConstants;
import com.reflexit.magiccards.ui.preferences.PreferenceInitializer;
import com.reflexit.magiccards.ui.utils.WaitUtils;
import com.reflexit.magiccards.ui.views.AbstractCardsView;
import com.reflexit.magiccards.ui.views.MagicDbView;

public class CardDescView extends ViewPart implements ISelectionListener, IShowInTarget, IShowInSource {
	public static final String ID = CardDescView.class.getName();
	private CardDescComposite panel;
	private Label message;
	private LoadCardJob loadCardJob;
	// !!! RD private Action actionAsScanned;
	// !!! RD private boolean asScanned;
	private Action open;
	// !!! RD 	private Action edit;
	private Image cardImage;
	private IWebBrowser browser;
	// ensure this field exists in the class
	private IMagicCard card;

	public class LoadCardJob extends Job {
		private IMagicCard jCard;

		public LoadCardJob(IMagicCard card) {
			super("Loading card");
			this.jCard = card;
		}

		@Override
		protected IStatus run(IProgressMonitor monitor) {
			if (jCard == null)
				return Status.OK_STATUS;
			setName("Loading card info: " + jCard.getName());
			monitor.beginTask("Loading info for " + jCard.getName(), 100);
			panel.setCard(jCard);
			final boolean nocard = (jCard == IMagicCard.DEFAULT);
			// Pull the card image into the on-disk cache here, on this background
			// thread, so the UI-thread setText() below never blocks on the network.
			if (!nocard && !monitor.isCanceled())
				panel.ensureCardImageCached(jCard);
			new UIJob("Set temp image") {
				@Override
				public IStatus runInUIThread(IProgressMonitor uimonitor) {
					// view or panel may be gone
					if (panel == null || panel.isDisposed()) {
						return Status.CANCEL_STATUS;
					}

					CardDescView.this.panel.setVisible(!nocard);
					if (nocard) {
						setMessage("Click on a card to populate the view");
						return Status.OK_STATUS;
					}
					if (monitor.isCanceled() || !isStillNeeded(jCard))
						return Status.CANCEL_STATUS;

					if (!panel.isDisposed()) {
						panel.setText(jCard);
					}
					return Status.OK_STATUS;
				}
			}.schedule();
			monitor.done();
			return Status.OK_STATUS;
		}
	}

	boolean isStillNeeded(final IMagicCard card) {
		return panel.getCard() == card;
	}

	public void setMessage(String text) {
		if (Display.getCurrent() == null) {
			// dumpUiThread();
			Display.getDefault().syncExec(() -> setMessage(text));
			return;
		}
		if (message.isDisposed())
			return;
		if (text == null || text.length() == 0) {
			message.setText("");
			message.setVisible(false);
			((GridData) message.getLayoutData()).heightHint = 0;
		} else {
			message.setText(text);
			message.setVisible(true);
			((GridData) message.getLayoutData()).heightHint = SWT.DEFAULT;
		}
		message.getParent().layout(true);
	}

	/**
	 * debugging method
	 */
	public void dumpUiThread() {
		if (Display.getCurrent() == null) {
			Thread.getAllStackTraces().forEach((t, s) -> {
				if (t.getId() == 1) {
					for (StackTraceElement stackTraceElement : s) {
						System.err.println(stackTraceElement.toString());
					}
				}
			});
		} else {
			new Exception().printStackTrace();
		}
	}

	public void setImage(IMagicCard card, Image remoteImage) {
		if (card == panel.getCard()) {

			Image old = cardImage;
			cardImage = remoteImage;

			Display.getDefault().asyncExec(() -> {
				if (panel == null || panel.isDisposed()) {
					if (remoteImage != null && !remoteImage.isDisposed())
						remoteImage.dispose();
					return;
				}

				if (old != null && !old.isDisposed()) {
					old.dispose();
				}
			});
		}
	}

	@Override
	public void createPartControl(Composite parent) {
		GridLayout layout = new GridLayout();
		layout.verticalSpacing = 0;
		parent.setLayout(layout);
		this.message = new Label(parent, SWT.WRAP);
		this.message.setText("Click on a card to populate the view");
		this.message.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		this.panel = new CardDescComposite(this, parent, SWT.INHERIT_DEFAULT);
		this.panel.setVisible(false);
		this.panel.setLayoutData(new GridData(GridData.FILL_BOTH));
		this.loadCardJob = new LoadCardJob(IMagicCard.DEFAULT);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(parent, MagicUIActivator.helpId("viewcard"));
		// PlatformUI.getWorkbench().getHelpSystem().setHelp(panel,
		// MagicUIActivator.helpId("viewcard"));
		makeActions();
		hookContextMenu();
		contributeToActionBars();
		revealCurrentSelection();

		getSite().setSelectionProvider(new ISelectionProvider() {
			@Override
			public void addSelectionChangedListener(ISelectionChangedListener listener) {
			}

			@Override
			public void removeSelectionChangedListener(ISelectionChangedListener listener) {
			}

			@Override
			public ISelection getSelection() {
				return StructuredSelection.EMPTY;
			}

			@Override
			public void setSelection(ISelection selection) {
			}
		});

	}

	private void hookContextMenu() {
		// Remove / comment out everything Eclipse-related:
		// - MenuManager
		// - registerContextMenu
		// - setSelectionProvider

		// Just create a raw empty SWT menu and attach it.
		Menu empty = new Menu(panel); // or textBrowser if that’s what you right-click
		panel.setMenu(empty);
	}

	private Control getControl() {
		return panel;
	}

	private void contributeToActionBars() {
		IActionBars bars = getViewSite().getActionBars();
		// fillLocalPullDown(bars.getMenuManager());
		fillLocalToolBar(bars.getToolBarManager());
		// setGlobalHandlers();
	}

	private void fillLocalToolBar(IToolBarManager manager) {
		// !!! RD manager.add(open);
		// !!! RD manager.add(actionAsScanned);
		// !!! RD manager.add(sync);
		// !!! RD		manager.add(edit);
	}

	private void fillContextMenu(IMenuManager manager) {
		fillShowInMenu(manager);
		// !!! RD		manager.add(open);
		//		!!! RD		manager.add(sync);
		// !!! RD 		manager.add(edit);
		// Other plug-ins can contribute there actions here
		manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
	}

	protected void fillShowInMenu(IMenuManager manager) {
		IWorkbench workbench = PlatformUI.getWorkbench();
		IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
		IContributionItem showViewItem = ContributionItemFactory.VIEWS_SHOW_IN.create(window);
		ImageDescriptor eyeImage = MagicUIActivator.getImageDescriptor("icons/clcl16/eye.png");
		IMenuManager showInMenu = new MenuManager("Show In", eyeImage, "showin");
		showInMenu.add(showViewItem);
		manager.add(showInMenu);
	}

	void makeActions() {

		/*
		 * !!! RD must be Disabled this.sync = new Action("Update card info from web", SWT.NONE) { { setImageDescriptor(MagicUIActivator.getImageDescriptor( "icons/clcl16/software_update.png")); }
		 * 
		 * @Override public void run() { LoadCardJob job = new LoadCardJob(); job.setUser(true); job.schedule(); } };
		 */
		/*	this.actionAsScanned = new Action("When depressed - scanned image is not scaled", IAction.AS_CHECK_BOX) {
				{
					setImageDescriptor(MagicUIActivator.getImageDescriptor("icons/clcl16/zoom_original.png"));
				}
		
				@Override
				public void run() {
					// Keep the UI state only. Do not change image loading or panel behavior.
					asScanned = actionAsScanned.isChecked();
		
					// Optional: update the action state only, no reload, no panel flag changes.
					// This makes the checkbox inert with respect to image scaling.
				}
			};
		*/
		/*
		 * !!! RD this.open = new Action("Open card in browser", SWT.NONE) { { setImageDescriptor(MagicUIActivator.getImageDescriptor( "icons/clcl16/discovery.gif")); }
		 * 
		 * @Override public void run() { try { if (panel.getCard() == null) return; String url = getUrl(); if (WebUtils.isWorkOffline()) return; IWebBrowser browser = getBrowser(); browser.openURL(new URL(url)); } catch (Exception e) { MessageDialog.openError(getControl().getShell(), "Error", "Well that kind of failed... " + e.getMessage()); MagicUIActivator.log(e); } } };
		 * 
		 * edit = new Action("Edit...") { { setImageDescriptor(MagicUIActivator.getImageDescriptor( "icons/clcl16/edit.png")); }
		 * 
		 * @Override public void run() { editCard(); } };
		 */
	}

	protected void editCard() {
		IMagicCard card = panel.getCard();
		if (card instanceof MagicCard) {
			new EditMagicCardDialog(panel.getShell(), (MagicCard) card).open();
		} else if (card instanceof MagicCardPhysical) {
			new EditMagicCardPhysicalDialog(panel.getShell(), (MagicCardPhysical) card).open();
		}
		this.loadCardJob = new LoadCardJob(card);
		this.loadCardJob.schedule();
	}

	protected String getUrl() {
		int gathererId = panel.getCard().getGathererId();
		return ParseGathererOracle.DETAILS_QUERY_URL_BASE + gathererId;
	}

	private void revealCurrentSelection() {
		try {
			IWorkbenchPage page = getViewSite().getWorkbenchWindow().getActivePage();
			if (page == null)
				return;
			IViewPart dbview = page.findView(MagicDbView.ID);
			if (dbview != null) {
				ISelection sel = dbview.getSite().getSelectionProvider().getSelection();
				runLoadJob(sel);
			}
		} catch (NullPointerException e) {
			// workbench of active window is null, just ignore then
		}
	}

	@Override
	public void setFocus() {
		this.panel.getParent().setFocus();
	}

	@Override
	public void init(IViewSite site) throws PartInitException {
		super.init(site);
		getSite().getPage().addSelectionListener(this);
		setInitialSelection();
	}

	private void setInitialSelection() {
		final String id = PreferenceInitializer.getGlobalStore().getString(PreferenceConstants.LAST_SELECTION);
		if (id == null)
			return;
		new Job("Setting saved card id") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				WaitUtils.waitForDb();
				IMagicCard card = (IMagicCard) DataManager.getCardHandler().getMagicDBStore().getCard(id);
				if (card == null)
					return Status.OK_STATUS;
				final ISelection sel = new StructuredSelection(card);
				runLoadJob(sel);
				Display.getDefault().asyncExec(new Runnable() {
					@Override
					public void run() {
						if (getViewSite() == null)
							return;

						IWorkbenchWindow win = getViewSite().getWorkbenchWindow();
						if (win == null)
							return;

						IWorkbenchPage page = win.getActivePage();
						if (page == null)
							return;

						IViewPart dbview = page.findView(MagicDbView.ID);
						if (dbview != null) {
							dbview.getSite().getSelectionProvider().setSelection(sel);
						}
					}
				});

				return Status.OK_STATUS;
			}
		}.schedule(5000);
	}

	@Override
	public void dispose() {
		if (cardImage != null && !cardImage.isDisposed()) {
			cardImage.dispose();
		}
		getSite().getPage().removeSelectionListener(this);
		saveSelection();
		try {
			if (browser != null)
				browser.close();
		} catch (Exception e) {
			// ignore
		}
		super.dispose();
	}

	private void saveSelection() {
		try {
			if (panel != null && panel.getCard() != null) {
				IMagicCard firstElement = panel.getCard();
				String id = firstElement.getBase().getCardId();
				PreferenceInitializer.getGlobalStore().setValue(PreferenceConstants.LAST_SELECTION, id);
			}
		} catch (Exception e) {
			MagicUIActivator.log(e);
		}
		return;
	}

	@Override
	public void selectionChanged(IWorkbenchPart part, ISelection sel) {
		if (part instanceof AbstractCardsView)
			runLoadJob(sel);
	}

	private IMagicCard getCard(ISelection sel) {
		if (sel.isEmpty()) {
			return IMagicCard.DEFAULT;
		}
		if (sel instanceof IStructuredSelection) {
			IStructuredSelection ss = (IStructuredSelection) sel;
			Object firstElement = ss.getFirstElement();
			if (firstElement instanceof ICardGroup) {
				if (((ICardGroup) firstElement).size() == 0)
					return IMagicCard.DEFAULT;
				return ((CardGroup) firstElement).getFirstCard();
			}
			if (firstElement instanceof IMagicCard) {
				IMagicCard card = (IMagicCard) firstElement;
				return card;
			}
		}
		return IMagicCard.DEFAULT;
	}

	private void runLoadJob(ISelection sel) {
		final IMagicCard card = getCard(sel);
		if (panel == null || panel.getCard() == card || sel.isEmpty())
			return;
		if (loadCardJob != null) {
			MagicLogger.trace("cancelling " + loadCardJob.jCard);
			this.loadCardJob.cancel();
		}
		MagicLogger.traceStart("image " + card);
		this.loadCardJob = new LoadCardJob(card);
		this.loadCardJob.schedule();
		this.loadCardJob.addJobChangeListener(new JobChangeAdapter() {
			@Override
			public void done(IJobChangeEvent event) {
				MagicLogger.traceEnd("image " + card);
			}
		});
	}

	public Display getDisplay() {
		return getViewSite().getShell().getDisplay();
	}

	public void setSelection(ISelection sel) {
		runLoadJob(sel);
	}

	protected IWebBrowser getBrowser() throws PartInitException {
		IWorkbenchBrowserSupport browserSupport = PlatformUI.getWorkbench().getBrowserSupport();
		browser = browserSupport.createBrowser(IWorkbenchBrowserSupport.AS_VIEW | IWorkbenchBrowserSupport.STATUS,
				MagicUIActivator.PLUGIN_ID, "Browser", null);
		return browser;
	}

	private boolean isLoadingOnClickEnabled() {
		return MagicUIActivator.getDefault().getPreferenceStore().getBoolean(PreferenceConstants.LOAD_IMAGES);
	}

	@Override
	public boolean show(ShowInContext context) {
		setSelection(context.getSelection());
		return true;
	}

	@Override
	public ShowInContext getShowInContext() {
		ISelectionProvider sp = getSite().getSelectionProvider();
		ISelection sel = StructuredSelection.EMPTY;
		if (sp != null) {
			ISelection s = sp.getSelection();
			if (s != null) {
				sel = s;
			}
		}
		return new ShowInContext(null, sel);
	}

}
