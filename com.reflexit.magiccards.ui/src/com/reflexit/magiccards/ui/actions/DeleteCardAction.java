/*
 * Contributors:
 *     Rémi Dutil (2026) - isShiftHeld(): so the wrapped removeSelected() can
 *                         skip its own confirmation dialog on Shift+Delete -
 *                         a deliberate "I know what I'm doing" bypass,
 *                         matching Delete's own long-standing unconfirmed
 *                         behavior before that confirmation existed. First
 *                         tried reading the triggering Event's stateMask via
 *                         IAction#runWithEvent(Event) (which ActionHandler#
 *                         execute() does call for a keybinding-triggered
 *                         command, confirmed by decompiling it), but the
 *                         Event Eclipse's key-binding service synthesizes
 *                         for that path doesn't reliably carry Shift's true
 *                         modifier state the way a native widget
 *                         SelectionEvent (toolbar/menu click) does - Shift
 *                         wasn't being detected for the actual Shift+Delete
 *                         keypress. Switched to tracking the physical Shift
 *                         key directly via a Display-level KeyDown/KeyUp
 *                         filter instead, independent of however the
 *                         triggering command's Event got built.
 */
package com.reflexit.magiccards.ui.actions;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;

public class DeleteCardAction extends ImageAction {
	private final Display display;
	private volatile boolean shiftHeld;
	private final Listener shiftTracker = new Listener() {
		@Override
		public void handleEvent(Event event) {
			if (event.keyCode == SWT.SHIFT)
				shiftHeld = event.type == SWT.KeyDown;
		}
	};

	public DeleteCardAction(Runnable runnable) {
		super("Remove", null, runnable);
		setId(ActionFactory.DELETE.getId());
		setActionDefinitionId(ActionFactory.DELETE.getCommandId());
		ISharedImages sharedImages = PlatformUI.getWorkbench().getSharedImages();
		this.setImageDescriptor(sharedImages.getImageDescriptor(ISharedImages.IMG_TOOL_DELETE));
		display = PlatformUI.getWorkbench().getDisplay();
		display.addFilter(SWT.KeyDown, shiftTracker);
		display.addFilter(SWT.KeyUp, shiftTracker);
	}

	/** Live physical Shift-key state, tracked independently of whatever
	 *  triggered this action (keybinding, toolbar click, context menu). */
	public boolean isShiftHeld() {
		return shiftHeld;
	}

	/** Must be called when the owning view is disposed, or this action's
	 *  Display-level filter outlives it. */
	public void dispose() {
		if (!display.isDisposed()) {
			display.removeFilter(SWT.KeyDown, shiftTracker);
			display.removeFilter(SWT.KeyUp, shiftTracker);
		}
	}
}
