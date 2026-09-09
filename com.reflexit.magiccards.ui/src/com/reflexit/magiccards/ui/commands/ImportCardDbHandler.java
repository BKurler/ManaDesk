/*
 * Contributors:
 *     Rémi Dutil (2026) - build the card database from a Scryfall bulk file on
 *                         disk, for users who cannot reach Scryfall directly
 */

package com.reflexit.magiccards.ui.commands;

import java.io.File;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

import com.reflexit.magiccards.core.sync.ScryfallBulkCache;
import com.reflexit.magiccards.ui.MagicUIActivator;

/**
 * "Import Card Database from File…": point {@link ScryfallBulkCache} at a Scryfall
 * <em>Default Cards</em> bulk file the user downloaded elsewhere, then run the
 * normal full update against it - no network needed.
 */
public class ImportCardDbHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		Shell shell = HandlerUtil.getActiveShell(event);
		if (shell == null)
			shell = MagicUIActivator.getShell();
		FileDialog dialog = new FileDialog(shell, SWT.OPEN);
		dialog.setText("Import Card Database");
		dialog.setFilterNames(new String[] { "Scryfall bulk data (*.jsonl.gz, *.jsonl, *.json)", "All files" });
		dialog.setFilterExtensions(new String[] { "*.jsonl.gz;*.jsonl;*.json", "*.*" });
		String path = dialog.open();
		if (path == null)
			return null;
		final File src = new File(path);
		// the copy can be several GB (All Cards) - do it off the UI thread, then
		// run the normal update, which will parse this file as-is.
		new Job("Importing card database") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					monitor.beginTask("Copying card file…", IProgressMonitor.UNKNOWN);
					ScryfallBulkCache.installLocalBulk(src);
				} catch (Exception e) {
					MagicUIActivator.log(e);
					Display.getDefault().asyncExec(() -> MessageDialog.openError(MagicUIActivator.getShell(),
							"Import Card Database", "Could not use that file:\n" + e.getMessage()));
					return Status.OK_STATUS;
				}
				UpdateDbHandler.performUpdate();
				return Status.OK_STATUS;
			}
		}.schedule();
		return null;
	}

	@Override
	public void setEnabled(Object evaluationContext) {
		setBaseEnabled(true);
	}
}
