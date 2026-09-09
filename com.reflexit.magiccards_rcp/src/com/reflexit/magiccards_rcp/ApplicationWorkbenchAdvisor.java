/*
 * Contributors:
 *     Rémi Dutil (2026) - postStartup(): first-run "download the card database" prompt
 */
package com.reflexit.magiccards_rcp;

import org.eclipse.ui.application.IWorkbenchConfigurer;
import org.eclipse.ui.application.IWorkbenchWindowConfigurer;
import org.eclipse.ui.application.WorkbenchAdvisor;
import org.eclipse.ui.application.WorkbenchWindowAdvisor;

import com.reflexit.magiccards.ui.PerspectiveFactoryMagic;
import com.reflexit.magiccards.ui.commands.CheckForUpdateDbHandler;

public class ApplicationWorkbenchAdvisor extends WorkbenchAdvisor {
	@Override
	public WorkbenchWindowAdvisor createWorkbenchWindowAdvisor(IWorkbenchWindowConfigurer configurer) {
		return new ApplicationWorkbenchWindowAdvisor(configurer);
	}

	@Override
	public void postStartup() {
		super.postStartup();
		// Runs once the workbench is fully up and the splash has closed - the
		// right moment for the first-run "download the card database" prompt.
		CheckForUpdateDbHandler.checkInitialDatabase();
	}

	@Override
	public String getInitialWindowPerspectiveId() {
		return PerspectiveFactoryMagic.PERSPECTIVE_ID;
	}

	@Override
	public void initialize(IWorkbenchConfigurer configurer) {
		super.initialize(configurer);
		configurer.setSaveAndRestore(true);
	}
}
