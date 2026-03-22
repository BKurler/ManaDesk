/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */
package com.reflexit.magiccards.ui.utils;

import java.io.File;
import java.net.URL;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;

import com.reflexit.magiccards.core.model.Edition;
import com.reflexit.magiccards.core.model.Editions;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.sync.EditionFileCache;
import com.reflexit.magiccards.ui.MagicUIActivator;

/**
 * General-purpose image utilities used throughout the UI.
 * 
 * This class must remain generic and must NOT contain mana-specific logic.
 */
public final class ImageCreator {

	public static final int SET_IMG_HEIGHT = 16;
	public static final int SET_IMG_WIDTH = 32;
	public static final int CARD_HEIGHT = 408;
	public static final int CARD_WIDTH = 293;

	private static final ImageCreator INSTANCE = new ImageCreator();

	public static ImageCreator getInstance() {
		return INSTANCE;
	}

	private ImageCreator() {
	}

	public File getBaseImageDirectory() {
		File dir = MagicUIActivator.getDefault().getStateLocation().append("images").toFile();
		return dir;
	}

	// New universal implementation for all IMagicCard
	public String createCardPath(IMagicCard card, boolean useCollectorNumber, boolean hires) {
		if (card == null) {
			return null;
		}

		// 1. Resolve edition name → Edition object
		String editionName = card.getSet();
		if (editionName == null) {
			return null;
		}

		Edition ed = Editions.getInstance().getEditionByName(editionName);

		// 2. Determine the correct set code (abbreviation)
		String setCode;
		if (ed != null && ed.getMainAbbreviation() != null && !ed.getMainAbbreviation().isEmpty()) {
			setCode = ed.getMainAbbreviation(); // e.g. GPT, RVR, WAR
		} else {
			// Fallback: assume card already stores the abbreviation
			setCode = editionName;
		}

		// 3. Language (full name)
		String lang = card.getLanguage();
		if (lang == null || lang.isEmpty()) {
			lang = "English";
		}

		// 4. Card ID → filename
		String id = card.getCardId();
		if (id == null || id.isEmpty()) {
			return null;
		}

		// 5. Base directory where images are stored
		File base = getBaseImageDirectory();
		File setDir = new File(new File(new File(base, "Cards"), setCode), lang);

		// Ensure directories exist
		setDir.mkdirs();

		// 6. Final file path
		File file = new File(setDir, "CARD_" + id + ".jpg");
		return file.getAbsolutePath();
	}

	public ImageData createCardImageData(String path, boolean hires) {
		if (path == null) {
			return createCardNotFoundImageData(null);
		}

		Image img = loadImage(path);
		if (img == null) {
			return createCardNotFoundImageData(null);
		}

		if (!hires) {
			img = scale(img, CARD_WIDTH, CARD_HEIGHT);
		}

		return img.getImageData();
	}

	// =====================================================================================
	//  Load an image from the plugin bundle
	// =====================================================================================

	public Image loadImage(String path) {
		try {
			URL url = ImageCreator.class.getClassLoader().getResource(path);
			if (url == null) {
				System.err.println("ImageCreator: missing resource: " + path);
				return null;
			}
			return new Image(Display.getDefault(), url.openStream());
		} catch (Exception e) {
			System.err.println("ImageCreator: failed to load " + path + " : " + e.getMessage());
			return null;
		}
	}

	public ImageData getResizedCardImageData(ImageData data) {
		Image img = getResizedCardImage(data);
		return img != null ? img.getImageData() : null;
	}

	// =====================================================================================
	//  Scale an image to a specific width/height
	// =====================================================================================

	public Image scale(Image img, int width, int height) {
		if (img == null)
			return null;

		Image scaled = new Image(img.getDevice(), width, height);
		GC gc = new GC(scaled);

		gc.setAntialias(SWT.ON);
		gc.setInterpolation(SWT.HIGH);

		Rectangle b = img.getBounds();
		gc.drawImage(img, 0, 0, b.width, b.height, 0, 0, width, height);

		gc.dispose();
		return scaled;
	}

	public Image createCardNotFoundImage(IMagicCard card) {
		Image template = getCardNotFoundImageTemplate();
		if (template == null)
			return null;
		return scale(template, CARD_WIDTH, CARD_HEIGHT);
	}

	public ImageData createCardNotFoundImageData(IMagicCard card) {
		Image img = createCardNotFoundImage(card);
		return img != null ? img.getImageData() : null;
	}

	public Image getCardNotFoundImageTemplate() {
		return loadImage("icons/misc/card_not_found.png");
	}

	public Image getResizedCardImage(ImageData data) {
		if (data == null)
			return null;
		Image img = new Image(Display.getDefault(), data);
		return scale(img, CARD_WIDTH, CARD_HEIGHT);
	}

	public String getSetIconFileURL(String abbr) {
		// abbr = "KHM-Rare"

		int dash = abbr.lastIndexOf('-');
		if (dash < 0)
			return null;

		String setCode = abbr.substring(0, dash);
		String rarity = abbr.substring(dash + 1);

		Edition ed = Editions.getInstance().getEditionByAbbr(setCode);

		if (ed == null)
			return null;

		EditionFileCache efc = new EditionFileCache(ed);

		try {
			URL url = efc.createSetImageLocalURL(rarity);
			return url.toURI().toString(); // file:///C:/.../KHM-Rare.png
		} catch (Exception e) {
			return null;
		}
	}

	public Image getSetImage(MagicCard card) {
		Edition ed = card.getEdition();
		if (ed == null)
			return null;

		String rarity = card.getRarity();
		if (rarity == null)
			return null;

		EditionFileCache efc = new EditionFileCache(ed);

		// Cheap: compute local file path without IO
		URL localUrl = efc.createSetImageLocalURL(rarity);
		File file;
		try {
			file = new File(localUrl.toURI());
		} catch (Exception e) {
			return null;
		}

		String key = file.getAbsolutePath();
		ImageRegistry reg = MagicUIActivator.getDefault().getImageRegistry();

		Image cached = reg.get(key);
		if (cached != null && !cached.isDisposed()) {
			return cached;
		}

		// If PNG missing → schedule full generation in background
		if (!file.exists()) {
			scheduleSetIconLoad(efc, rarity, file, key, ed);
			return null;
		}

		// PNG exists but SWT image not loaded yet → schedule SWT load
		scheduleSetIconLoad(efc, rarity, file, key, ed);
		return null;
	}

	private final Queue<Runnable> iconTasks = new ConcurrentLinkedQueue<>();
	private final AtomicBoolean workerRunning = new AtomicBoolean(false);

	private void scheduleSetIconLoad(EditionFileCache efc, String rarity, File file, String key, Edition edition) {

		iconTasks.add(() -> {
			try {
				// Heavy work off UI thread
				if (!file.exists()) {
					File generated = efc.ensureSetSymbolExists(rarity);
					if (generated == null || !generated.exists()) {
						return;
					}
				}

				Display display = Display.getDefault();
				if (display == null || display.isDisposed())
					return;

				ImageRegistry reg = MagicUIActivator.getDefault().getImageRegistry();
				if (reg.get(key) != null)
					return;

				display.syncExec(() -> {
					if (display.isDisposed())
						return;
					if (reg.get(key) != null)
						return;

					Image img = new Image(display, file.getAbsolutePath());
					reg.put(key, img);
				});

				refreshSetIconViewer(edition);

			} catch (Exception e) {
				System.err.println("Error loading set icon: " + e.getMessage());
			}
		});

		startWorkerIfNeeded();
	}

	private void startWorkerIfNeeded() {
		if (workerRunning.compareAndSet(false, true)) {
			Job job = new Job("Set Icon Loader") {
				@Override
				protected IStatus run(IProgressMonitor monitor) {
					try {
						Runnable task;
						while ((task = iconTasks.poll()) != null) {
							task.run();
						}
					} finally {
						workerRunning.set(false);

						// If new tasks arrived while we were running, restart
						if (!iconTasks.isEmpty()) {
							startWorkerIfNeeded();
						}
					}
					return Status.OK_STATUS;
				}
			};

			job.setSystem(true);
			job.schedule();
		}
	}

	private StructuredViewer setIconViewer;

	public void setSetIconViewer(StructuredViewer viewer) {
		this.setIconViewer = viewer;
	}

	private void refreshSetIconViewer(Object element) {
		StructuredViewer v = this.setIconViewer;
		if (v == null)
			return;

		Control c = v.getControl();
		if (c == null || c.isDisposed())
			return;

		Display.getDefault().asyncExec(() -> {
			if (!c.isDisposed()) {
				v.update(element, null); // now valid
			}
		});
	}

	public Image getSetImage(IMagicCard card) {
		try {
			if (card == null) {
				return null;
			}

			Edition ed = card.getEdition();
			if (ed == null) {
				return null;
			}

			String rarity = card.getRarity();
			if (rarity == null) {
				return null;
			}

			EditionFileCache efc = new EditionFileCache(ed);
			File file = efc.ensureSetSymbolExists(rarity);
			if (file == null || !file.exists()) {
				return null;
			}

			String key = file.getAbsolutePath();
			ImageRegistry reg = MagicUIActivator.getDefault().getImageRegistry();

			// 1) Try cache
			Image cached = reg.get(key);
			if (cached != null && !cached.isDisposed()) {
				return cached;
			}

			// 2) Load asynchronously, but ONLY if Display and Registry are still alive
			Display display = Display.getDefault();
			if (display == null || display.isDisposed()) {
				return null;
			}

			display.asyncExec(() -> {
				try {
					// Double-check everything again inside async
					if (display.isDisposed())
						return;
					if (reg == null)
						return;
					if (reg.get(key) != null)
						return; // already loaded

					Image img = new Image(display, file.getAbsolutePath());
					reg.put(key, img);

				} catch (Exception ex) {
					System.err.println("Async set icon load failed: " + ex.getMessage());
				}
			});

			// First paint returns null; next paint will find cached image
			return null;

		} catch (Exception e) {
			System.err.println("Failed to load set icon: " + e.getMessage());
			return null;
		}
	}

	public ImageData scaleAndCenter(ImageData src, int targetW, int targetH, boolean keepAspect) {
		if (src == null)
			return null;

		int newW = targetW;
		int newH = targetH;

		if (keepAspect) {
			float ratio = Math.min((float) targetW / src.width, (float) targetH / src.height);
			newW = Math.round(src.width * ratio);
			newH = Math.round(src.height * ratio);
		}

		Image scaled = scale(new Image(Display.getDefault(), src), newW, newH);
		ImageData scaledData = scaled.getImageData();

		Image result = new Image(Display.getDefault(), targetW, targetH);
		GC gc = new GC(result);

		int x = (targetW - newW) / 2;
		int y = (targetH - newH) / 2;

		gc.drawImage(scaled, x, y);
		gc.dispose();

		ImageData out = result.getImageData();
		result.dispose();
		scaled.dispose();

		return out;
	}

	public ImageData setAlphaBlendingForCorners(ImageData data) {
		if (data == null)
			return null;

		int w = data.width;
		int h = data.height;

		int radius = Math.min(w, h) / 12; // old default corner radius

		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				boolean inCorner = (x < radius && y < radius) || (x >= w - radius && y < radius)
						|| (x < radius && y >= h - radius) || (x >= w - radius && y >= h - radius);

				if (inCorner) {
					int dx = x < radius ? radius - x : x - (w - radius - 1);
					int dy = y < radius ? radius - y : y - (h - radius - 1);
					if (dx * dx + dy * dy > radius * radius) {
						data.setAlpha(x, y, 0);
					}
				}
			}
		}

		return data;
	}
}