/*
 * Contributors:
 *     Rémi Dutil (2026) - createBackup() pulled out of execute()'s Job body
 *                         as its own reusable, synchronous method - the one
 *                         "back up the user's data" mechanism in the app,
 *                         now also called by UpdateDbHandler before a major
 *                         card-database update, instead of that needing (or
 *                         this app having) a second, separate backup
 *                         mechanism of its own
 */
package com.reflexit.magiccards.ui.commands;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;

import com.reflexit.magicassistant.p2.Activator;
import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.ui.widgets.Toast;

public class BackupHandler extends AbstractHandler {
	/** Zips the whole workspace (decks, collections, the card database, prefs -
	 *  everything) to a timestamped file under {@link FileUtils#getBackupDir()},
	 *  same as the manual "Backup" command - the one reused mechanism for
	 *  "make sure the user's data is backed up" anywhere in the app needs it.
	 *  Synchronous: runs on the caller's own thread, so a caller already
	 *  inside its own background Job (e.g. UpdateDbHandler) can just call
	 *  this directly instead of nesting another one. Returns the created zip
	 *  file; the partial file is removed and the IOException rethrown on
	 *  failure rather than swallowed, so callers can decide how to react
	 *  (abort, warn, ...). */
	public static File createBackup() throws IOException {
		File ws = FileUtils.getWorkspace();
		SimpleDateFormat format = new SimpleDateFormat("YYYY_MMdd_HHmmss");
		File backupDir = FileUtils.getBackupDir();
		if (!backupDir.exists()) {
			backupDir.mkdirs();
		}
		File backup = new File(backupDir, format.format(new Date()) + ".zip");
		List<File> exclude = new ArrayList<File>();
		exclude.add(FileUtils.getBackupDir());
		exclude.add(FileUtils.getWorkspaceFile(".metadata"));
		try {
			FileUtils.zip(ws, backup, exclude);
			return backup;
		} catch (IOException e) {
			if (backup.exists()) {
				backup.delete();
			}
			throw e;
		}
	}

	@Override
	public Object execute(final ExecutionEvent aevent) {
		final File[] backup = new File[1];
		Job job = new Job("Backing up...") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					backup[0] = createBackup();
				} catch (Throwable e) {
					Activator
							.getDefault()
							.getLog()
							.log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, 1,
									"Failed to save backup " + backup[0], e));
				}
				return Status.OK_STATUS;
			}
		};
		job.addJobChangeListener(new JobChangeAdapter() {
			@Override
			public void done(final IJobChangeEvent event) {
				Display.getDefault().asyncExec(new Runnable() {
					@Override
					public void run() {
						try {
							final IWorkbenchWindow window = aevent != null ? HandlerUtil
									.getActiveWorkbenchWindowChecked(aevent)
									: PlatformUI.getWorkbench().getActiveWorkbenchWindow();
							Shell shell = window.getShell();
							if (event.getResult() == Status.OK_STATUS) {
								MessageDialog.openInformation(shell, "Info", "Backup saved in " + backup[0]);
							} else {
								new Toast(shell, "Backup failed:  " + event.getResult()).open();
							}
						} catch (ExecutionException e) {
						}
					}
				});
			}
		});
		job.setUser(true);
		job.schedule();
		return null;
	}

	public static File getWorkspaceFile() {
		String str = System.getProperty("osgi.instance.area");
		if (str != null) {
			return new File(str.replaceFirst("^file:", ""));
		} else {
			return new File(System.getProperty("user.home"), "MagicAssistant");
		}
	}
}
