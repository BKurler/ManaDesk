/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *******************************************************************************/
package com.reflexit.magiccards.ui.views.card;

import java.awt.Desktop;
import java.net.URI;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationAdapter;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;

import com.reflexit.magiccards.core.sync.ScryfallRulings;
import com.reflexit.magiccards.core.sync.WebUtils;
import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.utils.SymbolRenderer;

/**
 * A small standalone view that shows a card's Scryfall rulings as formatted HTML.
 * Opened / revealed by the "Rulings" link in the Card Info view; it does not
 * replace the card display.
 */
public class RulingsView extends ViewPart {

	public static final String ID = "com.reflexit.magiccards.ui.views.card.RulingsView";

	private Browser browser;
	/** URL of the request currently being shown - guards against a stale async result. */
	private volatile String currentUrl;

	@Override
	public void createPartControl(Composite parent) {
		parent.setLayout(new FillLayout());
		try {
			browser = new Browser(parent, SWT.NONE);
			browser.addLocationListener(new LocationAdapter() {
				@Override
				public void changing(LocationEvent event) {
					String loc = event.location;
					if (loc == null || loc.equals("about:blank"))
						return;
					if (loc.startsWith("http")) {
						event.doit = false;
						try {
							if (!WebUtils.isWorkOffline() && Desktop.isDesktopSupported()
									&& Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
								Desktop.getDesktop().browse(new URI(loc));
						} catch (Exception e) {
							MagicUIActivator.log(e);
						}
					}
				}
			});
			render("<p><i>Select a card and click its Rulings link.</i></p>");
		} catch (Throwable t) {
			MagicUIActivator.log(t);
		}
	}

	@Override
	public void setFocus() {
		if (browser != null && !browser.isDisposed())
			browser.setFocus();
	}

	/** Load and display the rulings at {@code rulingsUrl} (Scryfall rulings endpoint). */
	public void load(final String rulingsUrl, final String cardName) {
		if (browser == null || browser.isDisposed() || rulingsUrl == null)
			return;
		this.currentUrl = rulingsUrl;
		final String header = "<h3>Rulings" + (cardName == null || cardName.isEmpty() ? "" : " &ndash; " + cardName)
				+ "</h3>";
		if (WebUtils.isWorkOffline()) {
			render(header + "<p>Rulings are not available while working offline.</p>");
			return;
		}
		render(header + "<p><i>Loading&hellip;</i></p>");
		final Display display = browser.getDisplay();
		new Job("Loading rulings") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				String body;
				try {
					body = ScryfallRulings.fetchAsHtml(rulingsUrl);
				} catch (Exception e) {
					MagicUIActivator.log(e);
					body = "<p>Could not load rulings.</p>";
				}
				final String html = header + body;
				if (!display.isDisposed())
					display.asyncExec(() -> {
						if (rulingsUrl.equals(currentUrl))
							render(html);
					});
				return Status.OK_STATUS;
			}
		}.schedule();
	}

	private void render(String rawHtml) {
		if (browser != null && !browser.isDisposed())
			browser.setText(SymbolRenderer.wrapHtml(rawHtml, browser));
	}

	/**
	 * Open (or bring to front) the Rulings view and load the given Scryfall
	 * rulings URL. Does not steal focus from the current part.
	 */
	public static void show(String rulingsUrl, String cardName) {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		if (window == null)
			return;
		IWorkbenchPage page = window.getActivePage();
		if (page == null)
			return;
		try {
			RulingsView view = (RulingsView) page.showView(ID, null, IWorkbenchPage.VIEW_VISIBLE);
			view.load(rulingsUrl, cardName);
		} catch (PartInitException e) {
			MagicUIActivator.log(e);
		}
	}
}
