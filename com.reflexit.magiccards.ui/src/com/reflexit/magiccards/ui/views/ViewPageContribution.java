/*
 * Contributors:
 *     Rémi Dutil (2026) - isContentCreated()/markContentCreated()/
 *                         resetContentCreated(): tracks whether this page's
 *                         SWT control was actually built, separate from
 *                         isInstantiated() (the Java object exists) - needed
 *                         for FolderPageGroup's lazy page materialization
 */
package com.reflexit.magiccards.ui.views;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.swt.graphics.Image;

public class ViewPageContribution {
	private String name;
	private String id;
	private Image image;
	private IViewPage instance;
	private IConfigurationElement conf;
	/** True once this page's SWT control has actually been built
	 *  ({@code createContents()} called) - distinct from {@link #isInstantiated()},
	 *  which only means the Java object exists. {@link FolderPageGroup} creates
	 *  every page's Java object up front (cheap) but defers the expensive
	 *  control-building to the page's first activation, so callers that need to
	 *  know whether there is actually a widget/resource to deal with (disposal,
	 *  in particular) must check this, not {@link #isInstantiated()}. */
	private boolean contentCreated;

	public ViewPageContribution(String id, String name, Image image, IViewPage instance) {
		super();
		this.name = name;
		this.id = id;
		this.image = image;
		this.instance = instance;
	}

	public ViewPageContribution(String id, String name, IConfigurationElement conf) {
		this(id, name, null, (IViewPage) null);
		this.conf = conf;
	}

	public boolean isInstantiated() {
		return instance != null;
	}

	public boolean isContentCreated() {
		return contentCreated;
	}

	public void markContentCreated() {
		contentCreated = true;
	}

	/** Called when a previously-built control is disposed out from under this
	 *  contribution (e.g. {@link ViewPageGroup#createContent} rebuilding
	 *  everything under a fresh parent) so a later {@code isContentCreated()}
	 *  check does not report a stale, now-disposed control as still valid. */
	public void resetContentCreated() {
		contentCreated = false;
	}

	public synchronized IViewPage getViewPage() {
		if (instance == null)
			instance = instantiate();
		return instance;
	}

	private IViewPage instantiate() {
		try {
			return (IViewPage) conf.createExecutableExtension("class");
		} catch (CoreException e) {
			throw new IllegalArgumentException(e);
		}
	}

	public static ViewPageContribution parseElement(IConfigurationElement elp) {
		String id = elp.getAttribute("id");
		String name = elp.getAttribute("name");
		ViewPageContribution page = new ViewPageContribution(id, name, elp);
		return page;
	}

	public String getName() {
		return name;
	}
}
