/*
 * Contributors:
 *     Rémi Dutil (2026) - status dots for virtual / read-only / unsorted collections
 *     Rémi Dutil (2026) - Boxed indicator: a 4th marker in the icon's top-left
 *                         corner (kept apart from the bottom-edge trio, which
 *                         stays virtual/read-only/unsorted only)
 *     Rémi Dutil (2026) - Boxed marker changed from brown to black - brown
 *                         wasn't visible enough against the disk icons
 *     Rémi Dutil (2026) - Digital = bold letter D in the centre; Read only stays red but moves to the bottom-right; Unsorted moved to the top-left corner (shared with Boxed: never both)
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
 * The small coloured markers that flag a deck / collection's state, used on
 * the deck tab icons, the Cards Navigator icons and next to the check boxes in
 * the New / Edit Properties dialogs so all three read the same:
 * <ul>
 * <li>blue = virtual (bottom-left)</li>
 * <li>red = read-only (bottom-right)</li>
 * <li>green = unsorted (top-left)</li>
 * <li>black = boxed (top-left: a deck is boxed, a collection is unsorted, never both)</li>
<li>letter D = digital (centre)</li>
 * </ul>
 */
public final class StatusDots {
	public static final RGB VIRTUAL = new RGB(40, 110, 235);
	public static final RGB READ_ONLY = new RGB(210, 45, 45);
	public static final RGB UNSORTED = new RGB(45, 165, 70);
	public static final RGB BOXED = new RGB(0, 0, 0);
	/** The colour of the letter D, in the centre: a digital (MTGO / Arena) deck or collection. */
	public static final RGB DIGITAL = new RGB(0, 0, 0);

	private StatusDots() {
	}

	/**
	 * The plugin icon at {@code basePath} with the status markers painted on -
	 * virtual/read-only/unsorted along the bottom edge, boxed in the top-left
	 * corner. The disk icon is never modified; the composed image is cached in
	 * the plugin image registry. Returns the plain icon when no flag is set.
	 */
	public static Image decorate(String basePath, boolean virtual, boolean readOnly, boolean unsorted,
			boolean boxed, boolean digital) {
		Image base = MagicUIActivator.getImage(basePath);
		if (base == null || !(virtual || readOnly || unsorted || boxed || digital))
			return base;
		String key = "sdots3:" + basePath + (virtual ? "V" : "") + (readOnly ? "R" : "") + (unsorted ? "U" : "")
				+ (boxed ? "B" : "") + (digital ? "D" : "");
		Image cached = MagicUIActivator.getDefault().getImage(key, (Image) null);
		if (cached != null)
			return cached;
		ImageData d = base.getImageData();
		Image img = new Image(base.getDevice(), d);
		GC gc = new GC(img);
		try {
			gc.setAntialias(SWT.ON);
			paint(gc, d.width, d.height, virtual, readOnly, unsorted, boxed, digital);
		} finally {
			gc.dispose();
		}
		Image reg = MagicUIActivator.getDefault().getImage(key, img);
		return reg != null ? reg : img;
	}

	/**
	 * Paint the status markers into an already-open {@code gc} sized {@code w} x
	 * {@code h}: virtual/read-only/unsorted along the bottom edge, boxed in the
	 * top-left corner (kept apart from the other three so it reads as a distinct
	 * signal, not a fourth member of that trio).
	 */
	public static void paint(GC gc, int w, int h, boolean virtual, boolean readOnly, boolean unsorted,
			boolean boxed, boolean digital) {
		// still readable on the small (16px) sideboard / extra tab icons
		int dot = Math.max(w >= 24 ? 6 : 4, w / 6);
		int y = h - dot;
		dot(gc, VIRTUAL, virtual, 0, y, dot);
		dot(gc, READ_ONLY, readOnly, w - dot, y, dot);
		// boxed (a deck) and unsorted (a collection) are never both: they share the top-left corner
		dot(gc, UNSORTED, unsorted, 0, 0, dot);
		dot(gc, BOXED, boxed, 0, 0, dot);
		letterD(gc, DIGITAL, digital, w, h);
	}

	/** A bold letter D in the centre of the icon (digital). */
	private static void letterD(GC gc, RGB rgb, boolean on, int w, int h) {
		if (!on)
			return;
		org.eclipse.swt.graphics.FontData fd = gc.getFont().getFontData()[0];
		fd.setStyle(SWT.BOLD);
		fd.setHeight(Math.max(8, h / 2));
		org.eclipse.swt.graphics.Font font = new org.eclipse.swt.graphics.Font(gc.getDevice(), fd);
		org.eclipse.swt.graphics.Font old = gc.getFont();
		Color c = new Color(gc.getDevice(), rgb);
		try {
			gc.setFont(font);
			gc.setForeground(c);
			org.eclipse.swt.graphics.Point size = gc.textExtent("D");
			gc.drawText("D", (w - size.x) / 2, (h - size.y) / 2, true);
		} finally {
			gc.setFont(old);
			font.dispose();
			c.dispose();
		}
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
		return swatch(parent, rgb, false);
	}

	/** {@link #swatch(Composite, RGB)}, or a bold letter D of {@code rgb} when {@code letter} (digital). */
	private static Canvas swatch(Composite parent, RGB rgb, boolean letter) {
		Canvas canvas = new Canvas(parent, SWT.NO_FOCUS);
		GridData gd = new GridData(SWT.CENTER, SWT.CENTER, false, false);
		gd.widthHint = 14;
		gd.heightHint = 14;
		canvas.setLayoutData(gd);
		canvas.addPaintListener(e -> {
			e.gc.setAntialias(SWT.ON);
			Color c = new Color(e.display, rgb);
			if (letter) {
				org.eclipse.swt.graphics.FontData fd = e.gc.getFont().getFontData()[0];
				fd.setStyle(SWT.BOLD);
				org.eclipse.swt.graphics.Font font = new org.eclipse.swt.graphics.Font(e.display, fd);
				e.gc.setFont(font);
				e.gc.setForeground(c);
				org.eclipse.swt.graphics.Point size = e.gc.textExtent("D");
				e.gc.drawText("D", (14 - size.x) / 2, (14 - size.y) / 2, true);
				e.gc.setFont(null);
				font.dispose();
			} else {
				e.gc.setBackground(c);
				int dia = 10;
				e.gc.fillOval((14 - dia) / 2, (14 - dia) / 2, dia, dia);
			}
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
		return check(parent, rgb, text, false);
	}

	/** The Digital check box: preceded by the letter D that marks a digital list. */
	public static Button checkDigital(Composite parent, String text) {
		return check(parent, DIGITAL, text, true);
	}

	private static Button check(Composite parent, RGB rgb, String text, boolean letter) {
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
		swatch(row, rgb, letter);
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
