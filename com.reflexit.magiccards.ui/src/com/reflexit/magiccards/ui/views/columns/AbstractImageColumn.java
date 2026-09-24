/*
 * Contributors:
 *     Rémi Dutil (2026) - handleMeasureEvent(): row height was never adjusted
 *                         for the image a column paints (paintCellWithImage
 *                         is a custom PaintListener draw, not a native
 *                         TableItem/TreeItem icon, so SWT's own row-height
 *                         auto-sizing never saw it) - a row shorter than the
 *                         image got the image's top/bottom clipped by the
 *                         cell's paint region, which reads as a squashed,
 *                         too-wide icon (e.g. the 19x19 Set symbol in a view
 *                         whose row height otherwise comes out under 19px)
 *     Rémi Dutil (2026) - paintCellWithImage(): schedule a one-shot, debounced
 *                         redraw of the whole control when getActualImage()
 *                         comes back null - images like the Set symbol load
 *                         asynchronously (see ImageCreator#getSetImage(IMagicCard)'s
 *                         own "first paint returns null; next paint will find
 *                         cached image" comment) and nothing was ever
 *                         triggering that "next paint" once the async load
 *                         actually finished (the refreshSetIconViewer()/
 *                         setSetIconViewer() machinery meant for this is
 *                         never wired up - setSetIconViewer() has no
 *                         callers), so a row only ever got its icon if some
 *                         unrelated repaint happened to land after the load
 *                         completed - otherwise it was stuck blank/glitched
 *                         indefinitely
 */
package com.reflexit.magiccards.ui.views.columns;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.TreeItem;

import com.reflexit.magiccards.core.model.abs.ICardField;

public abstract class AbstractImageColumn extends GenColumn implements Listener {
	protected boolean cannotPaintImage = false;

	/** One pending redraw timer per control at a time - getActualImage()
	 *  returning null happens on every paint of every not-yet-loaded cell,
	 *  so without this a single screenful of rows would each schedule their
	 *  own timer. */
	private static final Set<Control> pendingImageRedraws = Collections
			.newSetFromMap(new WeakHashMap<Control, Boolean>());

	/** See this class' own header for why - retries a control's paint once,
	 *  400ms later, after an image column found nothing to draw yet. */
	private static void scheduleRedrawWhenImageReady(Control control) {
		if (control == null || control.isDisposed())
			return;
		synchronized (pendingImageRedraws) {
			if (!pendingImageRedraws.add(control))
				return; // already scheduled
		}
		control.getDisplay().timerExec(400, () -> {
			synchronized (pendingImageRedraws) {
				pendingImageRedraws.remove(control);
			}
			if (!control.isDisposed())
				control.redraw();
		});
	}

	public AbstractImageColumn(ICardField field, String name) {
		super(field, name);
	}

	@Override
	public String getToolTipText(Object element) {
		String text = getText(element);
		if (text == null)
			return null;
		if (text.isEmpty())
			return null;
		return text;
	}

	protected Image getActualImage(Object row) {
		return null;
	};

	@Override
	public int getColumnWidth() {
		return 40;
	}

	public void paintCellWithImage(Event event, int imageWidth) {
		if (!isVisible())
			return;// no paint of invisible column
		Item item = (Item) event.item;
		Object row = item.getData();
		// int x = event.x;
		int y = event.y;
		Rectangle bounds = getBounds(event);
		int x = bounds.x;
		// int y = bounds.y;
		int w = bounds.width;
		int h = bounds.height;
		int leftMargin = 0;
		Image image = getActualImage(row);
		if (image != null) {
			imageWidth = Math.max(imageWidth, image.getBounds().width);
			leftMargin = imageWidth;
			Rectangle imageBounds = image.getBounds();
			event.gc.drawImage(image, x + (imageWidth - imageBounds.width) / 2, y + (h - imageBounds.height) / 2);
		} else {
			// nothing to draw *this* time - if it's actually still loading
			// asynchronously (e.g. the Set symbol), retry once it's had a
			// chance to finish; a permanently image-less row just repaints
			// the same way again, which is harmless
			scheduleRedrawWhenImageReady(event.widget instanceof Control ? (Control) event.widget : null);
		}
		paintCellText(event, row, y, x, w, h, leftMargin);
	}

	protected void paintCellText(Event event, Object row, int y, int x, int w, int h, int leftMargin) {
		String text = getText(row);
		if (text != null) {
			x += leftMargin;
			event.gc.setClipping(x, y, w - 3 - leftMargin, h);
			event.gc.drawText(text, x + 3, y + 1, true);
		}
	}

	@Override
	public void handleEvent(Event event) {
		// if (event.type == SWT.PaintItem) {
		// cannotPaintImage = false;
		// }
		if (event.index == this.columnIndex || this.columnIndex == -1) {
			if (event.type == SWT.EraseItem) {
				handleEraseEvent(event);
			} else if (event.type == SWT.MeasureItem) {
				handleMeasureEvent(event);
			} else if (event.type == SWT.PaintItem) {
				handlePaintEvent(event);
			}
		}
	}

	protected void handleMeasureEvent(Event event) {
		Item item = (Item) event.item;
		Object row = item.getData();
		Image image = getActualImage(row);
		if (image != null) {
			int imageHeight = image.getBounds().height;
			if (event.height < imageHeight) {
				event.height = imageHeight;
			}
		}
	}

	protected void handleEraseEvent(Event event) {
		if (cannotPaintImage)
			return;
		event.detail &= ~SWT.FOREGROUND;
	}

	public void handlePaintEvent(Event event) {
		paintCellWithImage(event, -1);
	}

	protected static Rectangle getBounds(Event event) {
		Item item = (Item) event.item;
		Rectangle bounds = null;
		if (item instanceof TableItem)
			bounds = ((TableItem) item).getBounds(event.index);
		else if (item instanceof TreeItem)
			bounds = ((TreeItem) item).getBounds(event.index);
		return bounds;
	}

	@Override
	public Image getImage(Object element) {
		if (cannotPaintImage)
			return getActualImage(element);
		return super.getImage(element);
	}
}
