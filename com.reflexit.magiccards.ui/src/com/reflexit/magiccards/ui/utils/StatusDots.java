/*
 * Contributors:
 *     Rémi Dutil (2026) - status dots for virtual / read-only / unsorted collections
 */
package com.reflexit.magiccards.ui.utils;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;

import com.reflexit.magiccards.ui.MagicUIActivator;

/**
 * The three small coloured dots that flag a deck / collection's state, used on
 * the deck tab icons, the Cards Navigator icons and next to the check boxes in
 * the New / Edit Properties dialogs so all three read the same:
 * <ul>
 * <li>blue = virtual</li>
 * <li>red = read-only</li>
 * <li>green = unsorted</li>
 * </ul>
 */
public final class StatusDots {
	public static final RGB VIRTUAL = new RGB(40, 110, 235);
	public static final RGB READ_ONLY = new RGB(210, 45, 45);
	public static final RGB UNSORTED = new RGB(45, 165, 70);

	private StatusDots() {
	}

	/**
	 * The plugin icon at {@code basePath} with the status dots painted along its
	 * bottom edge (left = virtual, centre = read-only, right = unsorted). The
	 * disk icon is never modified; the composed image is cached in the plugin
	 * image registry. Returns the plain icon when no flag is set.
	 */
	public static Image decorate(String basePath, boolean virtual, boolean readOnly, boolean unsorted) {
		Image base = MagicUIActivator.getImage(basePath);
		if (base == null || !(virtual || readOnly || unsorted))
			return base;
		String key = "sdots2:" + basePath + (virtual ? "V" : "") + (readOnly ? "R" : "") + (unsorted ? "U" : "");
		Image cached = MagicUIActivator.getDefault().getImage(key, (Image) null);
		if (cached != null)
			return cached;
		ImageData d = base.getImageData();
		Image img = new Image(base.getDevice(), d);
		GC gc = new GC(img);
		try {
			gc.setAntialias(SWT.ON);
			paint(gc, d.width, d.height, virtual, readOnly, unsorted);
		} finally {
			gc.dispose();
		}
		Image reg = MagicUIActivator.getDefault().getImage(key, img);
		return reg != null ? reg : img;
	}

	/** Paint the three bottom-edge dots into an already-open {@code gc} sized {@code w} x {@code h}. */
	public static void paint(GC gc, int w, int h, boolean virtual, boolean readOnly, boolean unsorted) {
		// still readable on the small (16px) sideboard / extra tab icons
		int dot = Math.max(w >= 24 ? 6 : 4, w / 6);
		int y = h - dot;
		dot(gc, VIRTUAL, virtual, 0, y, dot);
		dot(gc, READ_ONLY, readOnly, (w - dot) / 2, y, dot);
		dot(gc, UNSORTED, unsorted, w - dot, y, dot);
	}

	private static void dot(GC gc, RGB rgb, boolean on, int x, int y, int dia) {
		if (!on)
			return;
		Color c = new Color(gc.getDevice(), rgb);
		gc.setBackground(c);
		gc.fillOval(x, y, dia, dia);
		c.dispose();
	}

	/**
	 * A tiny square control that just paints one filled dot of {@code rgb} -
	 * drop it right before a check box so the colour is on the same row.
	 */
	public static Canvas swatch(Composite parent, RGB rgb) {
		Canvas canvas = new Canvas(parent, SWT.NO_FOCUS);
		GridData gd = new GridData(SWT.CENTER, SWT.CENTER, false, false);
		gd.widthHint = 14;
		gd.heightHint = 14;
		canvas.setLayoutData(gd);
		canvas.addPaintListener(e -> {
			e.gc.setAntialias(SWT.ON);
			Color c = new Color(e.display, rgb);
			e.gc.setBackground(c);
			int dia = 10;
			e.gc.fillOval((14 - dia) / 2, (14 - dia) / 2, dia, dia);
			c.dispose();
		});
		return canvas;
	}

	/**
	 * A {@code SWT.CHECK} button preceded by its {@code rgb} colour swatch, wrapped
	 * in a tight sub-composite so the two stay together whatever the parent layout
	 * is doing. The sub-composite spans the parent's grid columns.
	 */
	public static Button check(Composite parent, RGB rgb, String text) {
		Composite row = new Composite(parent, SWT.NONE);
		GridLayout gl = new GridLayout(2, false);
		gl.marginWidth = 0;
		gl.marginHeight = 0;
		gl.horizontalSpacing = 5;
		row.setLayout(gl);
		GridData rgd = new GridData(SWT.FILL, SWT.CENTER, true, false);
		if (parent.getLayout() instanceof GridLayout)
			rgd.horizontalSpan = ((GridLayout) parent.getLayout()).numColumns;
		row.setLayoutData(rgd);
		swatch(row, rgb);
		Button b = new Button(row, SWT.CHECK);
		b.setText(text);
		b.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		return b;
	}

	/** Wire two check boxes so at most one can be selected at a time. */
	public static void exclusive(Button a, Button b) {
		a.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (a.getSelection())
					b.setSelection(false);
			}
		});
		b.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (b.getSelection())
					a.setSelection(false);
			}
		});
	}
}
