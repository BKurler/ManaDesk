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
package com.reflexit.magiccards_rcp;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.util.OpenStrategy;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferenceConstants;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.WorkbenchPlugin;
import org.eclipse.ui.internal.WorkbenchWindow;

/**
 * Replacement for the platform "General" preferences node.
 * <p>
 * The RCP reuses {@code org.eclipse.ui.ExtensionFactory:workbenchPreferencePage}
 * as the parent category for Appearance / Keys / Capabilities, but in this
 * Eclipse release that page's {@code createContents()} no longer builds the
 * "save interval" editor while {@code performOk()} still dereferences it - so
 * pressing OK throws an NPE in any product without the full IDE resources layer.
 * <p>
 * This page keeps the same id (so the child pages still nest under it) and
 * re-exposes the three workbench options that are actually relevant here:
 * "always run in background", single/double-click open, and the heap-status
 * monitor. It writes to the same stores and keys the platform page used.
 */
public class GeneralPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

	/** keys of {@code org.eclipse.ui.internal.IPreferenceConstants} (not API) */
	private static final String RUN_IN_BACKGROUND = "RUN_IN_BACKGROUND"; //$NON-NLS-1$
	private static final String OPEN_ON_SINGLE_CLICK = "OPEN_ON_SINGLE_CLICK"; //$NON-NLS-1$

	private Button runInBackground;
	private Button singleClick;
	private Button heapStatus;

	private static IPreferenceStore wbStore() {
		return WorkbenchPlugin.getDefault().getPreferenceStore();
	}

	private static IPreferenceStore apiStore() {
		return PlatformUI.getPreferenceStore();
	}

	@Override
	public void init(IWorkbench workbench) {
		// the two workbench stores are used directly, not a single page store
	}

	@Override
	protected Control createContents(Composite parent) {
		Composite area = new Composite(parent, SWT.NONE);
		area.setLayout(new GridLayout());
		runInBackground = check(area, "Always run in &background",
				wbStore().getBoolean(RUN_IN_BACKGROUND));
		singleClick = check(area, "Open items with &single click",
				wbStore().getBoolean(OPEN_ON_SINGLE_CLICK));
		heapStatus = check(area, "Show &heap status",
				apiStore().getBoolean(IWorkbenchPreferenceConstants.SHOW_MEMORY_MONITOR));
		return area;
	}

	private static Button check(Composite parent, String text, boolean selected) {
		Button b = new Button(parent, SWT.CHECK);
		b.setText(text);
		b.setSelection(selected);
		return b;
	}

	@Override
	protected void performDefaults() {
		runInBackground.setSelection(wbStore().getDefaultBoolean(RUN_IN_BACKGROUND));
		singleClick.setSelection(wbStore().getDefaultBoolean(OPEN_ON_SINGLE_CLICK));
		heapStatus.setSelection(apiStore().getDefaultBoolean(IWorkbenchPreferenceConstants.SHOW_MEMORY_MONITOR));
		super.performDefaults();
	}

	@Override
	public boolean performOk() {
		wbStore().setValue(RUN_IN_BACKGROUND, runInBackground.getSelection());

		boolean single = singleClick.getSelection();
		wbStore().setValue(OPEN_ON_SINGLE_CLICK, single);
		OpenStrategy.setOpenMethod(single ? OpenStrategy.SINGLE_CLICK : OpenStrategy.DOUBLE_CLICK);

		boolean heap = heapStatus.getSelection();
		apiStore().setValue(IWorkbenchPreferenceConstants.SHOW_MEMORY_MONITOR, heap);
		for (IWorkbenchWindow w : PlatformUI.getWorkbench().getWorkbenchWindows()) {
			if (w instanceof WorkbenchWindow) {
				try {
					((WorkbenchWindow) w).showHeapStatus(heap);
				} catch (RuntimeException ignore) {
					// best-effort live toggle; the preference is saved regardless
				}
			}
		}
		return super.performOk();
	}
}
