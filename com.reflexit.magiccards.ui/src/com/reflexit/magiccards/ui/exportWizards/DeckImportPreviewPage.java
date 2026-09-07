/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.ui.exportWizards;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Iterator;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnViewer;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Text;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.MagicException;
import com.reflexit.magiccards.core.exports.ImportData;
import com.reflexit.magiccards.core.exports.ImportSource;
import com.reflexit.magiccards.core.model.CollectorNumber;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.model.nav.CardElement;
import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.dnd.CopySupport;
import com.reflexit.magiccards.ui.utils.WaitUtils;
import com.reflexit.magiccards.ui.views.IMagicColumnViewer;
import com.reflexit.magiccards.ui.views.SimpleTableViewer;
import com.reflexit.magiccards.ui.views.columns.AbstractColumn;
import com.reflexit.magiccards.ui.views.columns.ColumnCollection;
import com.reflexit.magiccards.ui.views.columns.CommentColumn;
import com.reflexit.magiccards.ui.views.columns.CountColumn;
import com.reflexit.magiccards.ui.views.columns.GenColumn;
import com.reflexit.magiccards.ui.views.columns.GroupColumn;
import com.reflexit.magiccards.ui.views.columns.IdColumn;
import com.reflexit.magiccards.ui.views.columns.MagicColumnCollection;
import com.reflexit.magiccards.ui.views.columns.OwnershipColumn;
import com.reflexit.magiccards.ui.views.columns.SetColumn;
import com.reflexit.magiccards.ui.views.columns.StringEditorColumn;
import com.reflexit.magiccards.ui.widgets.ComboStringEditingSupport;

public class DeckImportPreviewPage extends WizardPage {
	private IMagicColumnViewer viewer;
	private Text text;
	private org.eclipse.swt.widgets.Button ignoreErrors;
	private Text errorZone;
	protected ImportData importData;
	private Job thread = new Job("Modify") {
		@Override
		protected IStatus run(IProgressMonitor monitor) {
			if (monitor.isCanceled())
				return Status.CANCEL_STATUS;
			WaitUtils.asyncExec(() -> reload());
			return Status.OK_STATUS;
		}
	};
	private ModifyListener modifyLister = new ModifyListener() {
		@Override
		public void modifyText(ModifyEvent e) {
			DeckImportPage startingPage = getMainPage();
			startingPage.setInputChoice(ImportSource.TEXT);
			CopySupport.runCopy(text.getText());
			importData.setText(text.getText());
			thread.cancel();
			thread.schedule(500);
		}
	};

	protected DeckImportPreviewPage(String pageName) {
		super(pageName);
	}

	@Override
	public void setVisible(boolean visible) {
		super.setVisible(visible);
		if (visible == true) {
			reload();
		}
	}

	public void reload() {
		DeckImportPage startingPage = getMainPage();
		setTitle("Importing format " + startingPage.getReportType().getLabel());
		setErrorMessage(null);
		setDescription(getFirstDescription());
		importData = startingPage.getImportData();
		TRACED_NAMES.clear();
		nameKnownCache.clear();
		autoFilled.clear();
		conflictFields.clear();
		userResolved.clear();
		startingPage.performImport(true);
		resolveByExternalIds();
		autoCompleteUnique();
		checkConsistency();
		computeErrors();
		safeSetText(importData.getText());
		updateColumns(importData.getFields());
		viewer.setInput(importData.getList());
		validate();
		showErrorForSelection();
	}

	/**
	 * Give every still-unresolved card the clearest possible one-line error,
	 * overriding the resolver's guess. Priority: name not found &gt; the name is
	 * in the DB but the Num is wrong &gt; the name isn't in the given set &gt;
	 * something is simply missing. Bound cards keep whatever error they have
	 * (a mismatch, or a language issue the resolver flagged).
	 */
	private void computeErrors() {
		if (importData == null || importData.getList() == null)
			return;
		int total = 0, errored = 0;
		for (Object o : importData.getList()) {
			if (!(o instanceof MagicCardPhysical))
				continue;
			total++;
			MagicCardPhysical card = (MagicCardPhysical) o;
			if (!isBlank(card.getCardId())) {
				// resolved: only checkConsistency / the resolver own its error
				if (card.getError() != null)
					errored++;
				continue;
			}
			String err = diagnose(card);
			card.setError(err);
			if (err != null) {
				errored++;
				trace("error name=\"" + importedName(card) + "\" set=\"" + card.getSet() + "\" num=\""
						+ card.getCollectorId() + "\" -> " + err);
			}
		}
		trace("computeErrors: " + errored + "/" + total + " card(s) with an error");
	}

	private String diagnose(MagicCardPhysical card) {
		String imp = importedName(card);
		if (imp == null)
			imp = card.getName();
		java.util.List<String[]> printings = new java.util.ArrayList<>();
		for (IMagicCard b : dbPrintings(imp))
			printings.add(new String[] { nz(b.getSet()), nz(b.getCollectorId()) });
		return diagnose(imp, card.getSet(), card.getCollectorId(), printings);
	}

	/**
	 * A specific, full-sentence error for an unresolved card, given the DB
	 * printings its name resolves to as {@code {set, collectorNumber}} pairs (an
	 * empty list = the name is not a real card). The Error column shows only the
	 * part before the first ':' ({@link #errorShort}); the whole sentence goes in
	 * the zone below the table. Priority: a name that isn't in the database beats
	 * every other problem.
	 */
	static String diagnose(String name, String set, String num, java.util.Collection<String[]> printings) {
		String imp = nz(name).trim();
		num = nz(num).trim();
		set = nz(set).trim();
		if (imp.isEmpty())
			return "Name not found: the row has no card name";
		if (printings == null || printings.isEmpty())
			return "Name not found: '" + imp + "' is not a card in the database";
		boolean nameInSet = false, numAnywhere = false, numInSet = false;
		for (String[] b : printings) {
			boolean sameSet = !set.isEmpty() && set.equalsIgnoreCase(nz(b[0]));
			if (sameSet)
				nameInSet = true;
			if (!num.isEmpty() && CollectorNumber.compare(num, nz(b[1])) == 0) {
				numAnywhere = true;
				if (sameSet)
					numInSet = true;
			}
		}
		if (!set.isEmpty() && !nameInSet)
			return "Set not found: '" + imp + "' has no printing in '" + set + "'";
		if (!num.isEmpty()) {
			if ((!set.isEmpty() && !numInSet) || (set.isEmpty() && !numAnywhere))
				return "Num not found: no printing of '" + imp + "' has collector number " + num
						+ (set.isEmpty() ? "" : " in '" + set + "'");
			return set.isEmpty() ? "Set needed: pick the set that has '" + imp + "' #" + num
					: "Pick the printing: '" + imp + "' #" + num + " matches more than one";
		}
		return set.isEmpty() ? "Num + Set needed: enter the collector number, then pick the set"
				: "Num needed: pick the collector number for '" + set + "'";
	}

	/**
	 * Re-run after the user edits Name / Set / Num / Multiverse id in the table:
	 * re-check the file's identifiers against the (new) binding, recompute the
	 * errors, repaint the row colours and refresh the Finish button.
	 * <p>
	 * The model work (checkConsistency / computeErrors) runs now. The viewer is
	 * then updated via {@code update(elements)} - NOT {@code refresh()} - and
	 * deferred with {@code asyncExec}:
	 * <ul>
	 * <li>{@code refresh()} re-sorts and disposes/recreates rows, which resets
	 * the scroll position and moves the row the user is working on;</li>
	 * <li>this method is called from a cell editor's {@code setValue}, i.e. while
	 * the editor is still tearing down - touching the table right then leaves it
	 * confused so the next click doesn't land.</li>
	 * </ul>
	 */
	private void revalidateAfterEdit() {
		checkConsistency();
		computeErrors();
		if (viewer == null || viewer.getControl() == null || viewer.getControl().isDisposed())
			return;
		viewer.getControl().getDisplay().asyncExec(() -> {
			if (viewer == null || viewer.getControl() == null || viewer.getControl().isDisposed())
				return;
			org.eclipse.jface.viewers.Viewer v = viewer.getViewer();
			if (v instanceof org.eclipse.jface.viewers.StructuredViewer && importData != null
					&& importData.getList() != null)
				((org.eclipse.jface.viewers.StructuredViewer) v).update(importData.getList().toArray(),
						(String[]) null);
			else
				viewer.refresh();
			validate();
			showErrorForSelection();
		});
	}

	/** short label for the Error column; full text goes in the error zone */
	static String errorShort(Object err) {
		if (err == null)
			return "";
		String s = String.valueOf(err).trim();
		if (s.startsWith("Mismatched info"))
			return "Mismatched info";
		int cut = s.indexOf(" - ");
		if (cut > 0)
			s = s.substring(0, cut);
		cut = s.indexOf(':');
		if (cut > 0)
			s = s.substring(0, cut);
		return s;
	}

	private MagicCardPhysical selectedCard() {
		if (viewer == null)
			return null;
		org.eclipse.jface.viewers.ISelection sel = viewer.getViewer().getSelection();
		if (sel instanceof org.eclipse.jface.viewers.IStructuredSelection) {
			Object o = ((org.eclipse.jface.viewers.IStructuredSelection) sel).getFirstElement();
			if (o instanceof MagicCardPhysical)
				return (MagicCardPhysical) o;
		}
		return null;
	}

	/** Fill the zone below the table with the full error for the selected row. */
	private void showErrorForSelection() {
		if (errorZone == null || errorZone.isDisposed())
			return;
		String msg = "";
		MagicCardPhysical card = selectedCard();
		if (card != null && card.getError() != null)
			msg = String.valueOf(card.getError());
		if (msg.isEmpty()) {
			int n = importData == null ? 0 : importData.getErrorCount();
			msg = n == 0 ? "" : n + " card(s) have errors - select a row for the details.";
		}
		errorZone.setText(msg);
	}

	private static void trace(String message) {
		com.reflexit.magiccards.core.MagicLogger.info("[import-preview] " + message);
	}

	private static Color red() {
		return Display.getDefault().getSystemColor(SWT.COLOR_RED);
	}

	private static boolean hasField(ICardField[] fields, ICardField f) {
		if (fields != null)
			for (ICardField x : fields)
				if (x == f)
					return true;
		return false;
	}

	private static String nz(String s) {
		return s == null ? "" : s;
	}

	private static String norm(String s) {
		if (s == null)
			return "";
		s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD);
		StringBuilder b = new StringBuilder();
		for (char c : s.toLowerCase().toCharArray())
			if (Character.isLetterOrDigit(c) && Character.getType(c) != Character.NON_SPACING_MARK)
				b.append(c);
		return b.toString();
	}

	private static java.util.Set<String> faces(String name) {
		java.util.Set<String> out = new java.util.HashSet<>();
		if (name == null)
			return out;
		out.add(norm(name));
		for (String p : name.split("//"))
			out.add(norm(p));
		out.remove("");
		return out;
	}

	/** Lenient: same name, a face of a split card, or one is a prefix of the other (trailing junk). */
	private static boolean namesMatch(String declared, String actual) {
		java.util.Set<String> a = faces(declared);
		java.util.Set<String> b = faces(actual);
		if (a.isEmpty() || b.isEmpty())
			return true;
		for (String x : a)
			for (String y : b)
				if (x.equals(y) || x.startsWith(y) || y.startsWith(x))
					return true;
		return false;
	}

	private static boolean setsMatch(String declared, String actual) {
		if (declared == null || declared.trim().isEmpty())
			return true;
		if (norm(declared).equals(norm(actual)))
			return true;
		com.reflexit.magiccards.core.model.Editions eds = com.reflexit.magiccards.core.model.Editions.getInstance();
		com.reflexit.magiccards.core.model.Edition dEd = eds.getEditionByName(declared);
		if (dEd == null)
			dEd = eds.getEditionByAbbr(declared);
		com.reflexit.magiccards.core.model.Edition aEd = eds.getEditionByName(actual);
		if (aEd == null)
			aEd = eds.getEditionByAbbr(actual);
		if (dEd == null || aEd == null)
			return true; // can't compare cleanly - the Set cell already flags unknown sets
		return norm(dEd.getName()).equals(norm(aEd.getName()));
	}

	/** the name the file gave for this card, verbatim; never changed by any bind. */
	private String importedName(MagicCardPhysical card) {
		String[] d = importData == null ? null : importData.getDeclared(card);
		if (d != null && !isBlank(d[0]))
			return d[0];
		return null;
	}

	/**
	 * true when {@code name} points at a real DB card - an exact hit, a near-miss
	 * on punctuation / case / accents, or an exact hit once trailing junk (a
	 * mashed-in type word like "Legendary") is dropped. Only a name that resolves
	 * to nothing at all is "not found".
	 */
	private final java.util.Map<String, Boolean> nameKnownCache = new java.util.HashMap<>();

	private boolean nameKnown(String name) {
		if (isBlank(name))
			return false;
		String n = name.trim();
		Boolean cached = nameKnownCache.get(n);
		if (cached != null)
			return cached;
		boolean known = !dbPrintings(n).isEmpty();
		nameKnownCache.put(n, known);
		return known;
	}

	/** true when the imported name is not a real card name (see {@link #nameKnown}). */
	private boolean nameNotFound(MagicCardPhysical card) {
		if (userResolved.contains(card))
			return false; // the user bound this row by hand
		String imp = importedName(card);
		if (imp == null)
			imp = card.getName();
		if (isBlank(imp))
			return false;
		return !nameKnown(imp);
	}

	/** fields the file's own identifiers disagree about, per card */
	private final java.util.Map<Object, java.util.Set<ICardField>> conflictFields = new java.util.IdentityHashMap<>();

	/**
	 * Cards the user resolved by hand (edited Name / Set / Num / Multiverse id).
	 * Their binding is authoritative - stop second-guessing it against the file's
	 * other values.
	 */
	private final java.util.Set<Object> userResolved = java.util.Collections
			.newSetFromMap(new java.util.IdentityHashMap<>());

	/** call after a manual edit leaves the row bound to a printing */
	private void markUserResolved(MagicCardPhysical card) {
		if (!isBlank(card.getCardId())) {
			userResolved.add(card);
			conflictFields.remove(card);
			if (card.getError() != null && String.valueOf(card.getError()).startsWith(MISMATCH))
				card.setError(null);
		}
	}

	private boolean isConflict(Object card, ICardField field) {
		java.util.Set<ICardField> s = conflictFields.get(card);
		if (s == null || !s.contains(field))
			return false;
		// a conflict cell is only red while its "Mismatched info" error is live -
		// never leave a stale red behind after the error was cleared
		Object e = card instanceof MagicCardPhysical ? ((MagicCardPhysical) card).getError() : null;
		return e != null && String.valueOf(e).startsWith(MISMATCH);
	}

	/**
	 * The file can carry several identifiers (Scryfall id, Multiverse id, name,
	 * set, Num). When they don't all point at the same printing we must not
	 * silently pick one. Priority of trust: Scryfall id &gt; Multiverse id &gt;
	 * name / set / Num. Whichever of the first two the row actually resolved to
	 * "wins": only the OTHER fields that disagree are flagged red. If neither id
	 * anchors the row, every value the file gave is suspect. Idempotent per card.
	 */
	private void checkConsistency() {
		if (importData == null || importData.getList() == null)
			return;
		for (Object o : importData.getList()) {
			if (!(o instanceof MagicCardPhysical))
				continue;
			MagicCardPhysical card = (MagicCardPhysical) o;
			if (userResolved.contains(card)) {
				conflictFields.remove(card);
				if (card.getError() != null && String.valueOf(card.getError()).startsWith(MISMATCH))
					card.setError(null);
				continue; // the user's manual binding is authoritative
			}
			if (card.getError() != null && !conflictFields.containsKey(card))
				continue; // a non-mismatch error already owns this card
			if (isBlank(card.getCardId())) {
				conflictFields.remove(card);
				continue; // only meaningful once bound to a printing
			}
			String[] d = importData.getDeclared(card);
			if (d == null) {
				conflictFields.remove(card);
				continue;
			}
			String dName = d[0], dSet = d[1], dNum = d[2], dId = d[3];
			String dG = blankId(d[4]) ? "" : d[4].trim();

			boolean nameBad = !isBlank(dName) && !namesMatch(dName, card.getName());
			boolean setBad = !isBlank(dSet) && !setsMatch(dSet, card.getSet());
			boolean numBad = !isBlank(dNum) && CollectorNumber.compare(dNum.trim(), nz(card.getCollectorId())) != 0;
			boolean idBad = !isBlank(dId) && !sameId(dId, card.getCardId());
			boolean gBad = !isBlank(dG) && !sameId(dG, card.getBase().getGathererCardId());
			if (!(nameBad || setBad || numBad || idBad || gBad)) {
				if (conflictFields.remove(card) != null && card.getError() != null
						&& String.valueOf(card.getError()).startsWith(MISMATCH))
					card.setError(null);
				continue;
			}

			boolean idAnchored = !isBlank(dId) && sameId(dId, card.getCardId());
			boolean gAnchored = !idAnchored && !isBlank(dG) && sameId(dG, card.getBase().getGathererCardId());

			java.util.Set<ICardField> fields = new java.util.HashSet<>();
			java.util.List<String> parts = new java.util.ArrayList<>();
			if (nameBad) {
				fields.add(MagicCardField.NAME);
				parts.add("name '" + dName.trim() + "'");
			}
			if (setBad) {
				fields.add(MagicCardField.SET);
				parts.add("set '" + dSet.trim() + "'");
			}
			if (numBad) {
				fields.add(MagicCardField.COLLNUM);
				parts.add("Num " + dNum.trim());
			}

			String card4 = "'" + card.getName() + "' (" + nz(card.getSet()) + " #" + nz(card.getCollectorId()) + ")";
			String msg;
			if (idAnchored) {
				// the id is truth; a wrong Multiverse id is one of the bad fields
				if (gBad) {
					fields.add(MagicCardField.GATHERERID);
					parts.add("Multiverse " + dG);
				}
				msg = MISMATCH + "the Scryfall id is trusted and resolves to " + card4 + ", but the file's "
						+ String.join(", ", parts) + " do not match. The id wins - fix the file.";
			} else if (gAnchored) {
				// the Multiverse id is truth; a wrong Scryfall id is a bad field
				if (idBad) {
					fields.add(MagicCardField.ID);
					parts.add("Scryfall id");
				}
				msg = MISMATCH + "the Multiverse id is trusted and resolves to " + card4 + ", but the file's "
						+ String.join(", ", parts) + " do not match. The Multiverse id wins - fix the file.";
			} else {
				// no id anchors the row: every identifier the file gave is suspect
				fields.clear();
				parts.clear();
				if (!isBlank(dId)) {
					fields.add(MagicCardField.ID);
					parts.add("Scryfall id");
				}
				if (!isBlank(dG)) {
					fields.add(MagicCardField.GATHERERID);
					parts.add("Multiverse " + dG);
				}
				if (!isBlank(dName)) {
					fields.add(MagicCardField.NAME);
					parts.add("name '" + dName.trim() + "'");
				}
				if (!isBlank(dSet)) {
					fields.add(MagicCardField.SET);
					parts.add("set '" + dSet.trim() + "'");
				}
				if (!isBlank(dNum)) {
					fields.add(MagicCardField.COLLNUM);
					parts.add("Num " + dNum.trim());
				}
				msg = MISMATCH + "the file (" + String.join(", ", parts) + ") does not agree with this row's card "
						+ card4 + ". Fix the file, or set the Set / Num by hand.";
			}
			conflictFields.put(card, fields);
			card.setError(msg);
			autoFilled.remove(card);
			trace("conflict [" + (idAnchored ? "id" : gAnchored ? "multiverse" : "none") + " anchored] "
					+ parts + " vs bound " + card4);
		}
	}

	private static final String MISMATCH = "Mismatched info: ";

	private static boolean sameId(String a, String b) {
		a = nz(a).trim();
		b = nz(b).trim();
		if (a.isEmpty() || b.isEmpty())
			return a.equals(b);
		if (a.equalsIgnoreCase(b))
			return true;
		try {
			return Long.parseLong(a) == Long.parseLong(b);
		} catch (NumberFormatException e) {
			return false;
		}
	}

	/** Multiverse / TCG ids default to 0 when the source has none - treat that as missing. */
	private static boolean blankId(String s) {
		s = nz(s).trim();
		if (s.isEmpty())
			return true;
		try {
			return Long.parseLong(s) == 0L;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	// --- "auto-filled" (green) field tracking ---------------------------------

	private final java.util.Map<Object, java.util.Set<ICardField>> autoFilled = new java.util.IdentityHashMap<>();
	private Color greenBg;

	private Color green() {
		if (greenBg == null || greenBg.isDisposed())
			greenBg = new Color(Display.getDefault(), 198, 239, 206); // soft "good" green
		return greenBg;
	}

	private void markAuto(MagicCardPhysical card, ICardField... fields) {
		java.util.Set<ICardField> s = autoFilled.get(card);
		if (s == null) {
			s = new java.util.HashSet<>();
			autoFilled.put(card, s);
		}
		for (ICardField f : fields)
			s.add(f);
	}

	private void clearAuto(MagicCardPhysical card, ICardField... fields) {
		java.util.Set<ICardField> s = autoFilled.get(card);
		if (s != null)
			for (ICardField f : fields)
				s.remove(f);
	}

	private boolean isAuto(Object card, ICardField field) {
		java.util.Set<ICardField> s = autoFilled.get(card);
		return s != null && s.contains(field);
	}

	/** snapshot of the identity fields, to see what a (re)bind changed */
	private static final class Snap {
		final String set, num, g;

		Snap(MagicCardPhysical c) {
			set = c.getSet();
			num = c.getCollectorId();
			g = c.getBase().getGathererCardId();
		}
	}

	private static boolean filled(String before, String after) {
		String a = before == null ? "" : before.trim();
		String b = after == null ? "" : after.trim();
		return !b.isEmpty() && !b.equals(a);
	}

	/**
	 * After a (re)bind, mark as auto-filled every identity field the tool just
	 * populated / changed - except the one the user edited directly.
	 */
	private void applyAutoMarks(MagicCardPhysical card, Snap before, ICardField userEdited) {
		if (userEdited == MagicCardField.SET)
			clearAuto(card, MagicCardField.SET);
		else if (filled(before.set, card.getSet()))
			markAuto(card, MagicCardField.SET);

		if (userEdited == MagicCardField.COLLNUM)
			clearAuto(card, MagicCardField.COLLNUM);
		else if (filled(before.num, card.getCollectorId()))
			markAuto(card, MagicCardField.COLLNUM);

		if (filled(before.g, card.getBase().getGathererCardId()))
			markAuto(card, MagicCardField.GATHERERID);
	}

	// --- "auto fix using first match" ---------------------------------------

	private static java.util.Date releaseDate(String set) {
		com.reflexit.magiccards.core.model.Edition e = com.reflexit.magiccards.core.model.Editions.getInstance()
				.getEditionByName(set);
		return e == null ? null : e.getReleaseDate();
	}

	/** The lowest collector number in the most recently released set that has this name. */
	private static MagicCard firstMatch(String name) {
		java.util.Collection<IMagicCard> printings = dbPrintings(name);
		if (printings.isEmpty())
			return null;
		String bestSet = null;
		java.util.Date bestDate = null;
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (IMagicCard b : printings) {
			String s = b.getSet();
			if (s == null || s.isEmpty() || !seen.add(s))
				continue;
			java.util.Date d = releaseDate(s);
			boolean better = bestSet == null || (d != null && (bestDate == null || d.after(bestDate)));
			if (better) {
				bestSet = s;
				bestDate = d;
			}
		}
		if (bestSet == null)
			return null;
		IMagicCard lowest = null;
		for (IMagicCard b : printings) {
			if (!bestSet.equals(b.getSet()))
				continue;
			if (lowest == null || CollectorNumber.compare(b.getCollectorId(), lowest.getCollectorId()) < 0)
				lowest = b;
		}
		return (MagicCard) lowest;
	}

	private void autoFixFirstMatch() {
		if (importData == null || importData.getList() == null)
			return;
		int fixed = 0;
		for (Object o : importData.getList()) {
			if (!(o instanceof MagicCardPhysical))
				continue;
			MagicCardPhysical card = (MagicCardPhysical) o;
			if (card.getCardId() != null && !card.getCardId().isEmpty())
				continue;
			if (conflictFields.containsKey(card))
				continue; // never auto-fix a card whose file info already contradicts itself
			String name = importedName(card);
			if (name == null)
				name = card.getName();
			MagicCard pick = firstMatch(name);
			if (pick == null)
				continue;
			if (!pickAgreesWithFile(card, pick)) {
				trace("auto-fix skip \"" + name + "\": first match " + pick.getSet() + " #" + pick.getCollectorId()
						+ " contradicts the file's own set / Num / id");
				continue;
			}
			Snap before = new Snap(card);
			card.setMagicCard(pick);
			card.setError(null);
			applyAutoMarks(card, before, null);
			fixed++;
			trace("auto-fix \"" + name + "\" -> " + pick.getSet() + " #" + pick.getCollectorId());
		}
		trace("auto-fix first match: bound " + fixed + " card(s)");
		revalidateAfterEdit();
	}

	/**
	 * true when the "first match" printing is consistent with every identifier
	 * the file explicitly gave for this card. If the file already named a set /
	 * Num / id that points somewhere else, auto-fix must not touch the card.
	 */
	private boolean pickAgreesWithFile(MagicCardPhysical card, MagicCard pick) {
		String[] d = importData.getDeclared(card);
		if (d == null)
			return true;
		if (!isBlank(d[1]) && !setsMatch(d[1], pick.getSet()))
			return false;
		if (!isBlank(d[2]) && CollectorNumber.compare(d[2].trim(), nz(pick.getCollectorId())) != 0)
			return false;
		if (!isBlank(d[3]) && !sameId(d[3], pick.getCardId()))
			return false;
		if (!blankId(d[4]) && !sameId(d[4], pick.getGathererCardId()))
			return false;
		return true;
	}

	// --- resolution by Multiverse (Gatherer) id -------------------------------

	private java.util.Map<String, MagicCard> dbByGatherer;

	/** One pass over the DB, lazily, only when a card actually needs it. */
	private void buildExternalIdIndex() {
		if (dbByGatherer != null)
			return;
		dbByGatherer = new java.util.HashMap<>();
		long t0 = System.currentTimeMillis();
		int n = 0;
		for (IMagicCard o : DataManager.getInstance().getMagicDBStore()) {
			if (!(o instanceof MagicCard))
				continue;
			MagicCard c = (MagicCard) o;
			n++;
			String g = c.getGathererCardId();
			if (g != null && !g.isEmpty())
				dbByGatherer.putIfAbsent(g.trim(), c);
		}
		trace("Multiverse-id index: " + n + " DB cards -> " + dbByGatherer.size() + " ids ("
				+ (System.currentTimeMillis() - t0) + "ms)");
	}

	private MagicCard byGatherer(String id) {
		if (blankId(id))
			return null;
		buildExternalIdIndex();
		return dbByGatherer.get(id.trim());
	}

	/**
	 * For cards not already pinned by a Scryfall id, use the Multiverse id from
	 * the file (when the file carries such a column) to bind the printing.
	 */
	private void resolveByExternalIds() {
		if (importData == null || importData.getList() == null)
			return;
		if (!hasField(importData.getFields(), MagicCardField.GATHERERID))
			return;
		for (Object o : importData.getList()) {
			if (!(o instanceof MagicCardPhysical))
				continue;
			MagicCardPhysical card = (MagicCardPhysical) o;
			if (card.getCardId() != null && !card.getCardId().isEmpty())
				continue;
			MagicCard hit = byGatherer(card.getBase().getGathererCardId());
			if (hit != null) {
				trace("resolved by Multiverse " + card.getBase().getGathererCardId() + " -> " + hit.getName() + " / "
						+ hit.getSet() + " #" + hit.getCollectorId());
				Snap before = new Snap(card);
				card.setMagicCard(hit);
				card.setError(null);
				applyAutoMarks(card, before, null);
			}
		}
	}

	/**
	 * If what the file gave (name, plus Num and/or Set) already pins down a
	 * single database printing, bind it now and green-fill the fields the file
	 * left out. Example: "+2 Mace" #1 exists in exactly one set, so the Set is
	 * filled in automatically.
	 */
	private void autoCompleteUnique() {
		if (importData == null || importData.getList() == null)
			return;
		for (Object o : importData.getList()) {
			if (o instanceof MagicCardPhysical)
				tryAutoComplete((MagicCardPhysical) o);
		}
	}

	private void tryAutoComplete(MagicCardPhysical card) {
		if (!isBlank(card.getCardId()))
			return;
		String imp = importedName(card);
		if (imp == null)
			imp = card.getName();
		if (isBlank(imp) || !nameKnown(imp))
			return;
		String num = nz(card.getCollectorId()).trim();
		String set = nz(card.getSet()).trim();
		java.util.List<IMagicCard> matches = new java.util.ArrayList<>();
		for (IMagicCard b : dbPrintings(imp)) {
			if (!num.isEmpty() && CollectorNumber.compare(num, nz(b.getCollectorId())) != 0)
				continue;
			if (!set.isEmpty() && !setsMatch(set, b.getSet()))
				continue;
			matches.add(b);
		}
		// name alone is only enough when the card has a single printing ever
		if (matches.size() != 1)
			return;
		MagicCard hit = (MagicCard) matches.get(0);
		Snap before = new Snap(card);
		card.setMagicCard(hit);
		card.setError(null);
		applyAutoMarks(card, before, null);
		trace("auto-complete \"" + imp + "\" num='" + num + "' set='" + set + "' -> " + hit.getSet() + " #"
				+ hit.getCollectorId());
	}

	/** The user typed a corrected card name; re-resolve the row from scratch. */
	private void renameCard(MagicCardPhysical card, String v) {
		String cur = importedName(card);
		if (cur == null)
			cur = nz(card.getName());
		if (v.isEmpty() || v.equals(cur))
			return;
		String[] d = importData.getDeclared(card);
		if (d != null)
			d[0] = v;
		// clone first - the base may be a shared DB card once the row resolved
		MagicCard base = (MagicCard) card.getBase().clone();
		base.setName(v);
		base.setCardId(0); // force a fresh resolve (Set / Num are kept)
		card.setMagicCard(base);
		card.setError(null);
		nameKnownCache.remove(v);
		conflictFields.remove(card);
		autoFilled.remove(card);
		userResolved.remove(card);
		tryAutoComplete(card);
		markUserResolved(card);
		revalidateAfterEdit();
		trace("rename -> '" + v + "'"
				+ (isBlank(card.getCardId()) ? " (still unresolved)" : " -> " + card.getSet() + " #"
						+ card.getCollectorId()));
	}

	/** true when the card's collector number really exists for that name/set. */
	private static boolean numIsKnown(MagicCardPhysical card) {
		if (card.getCardId() != null && !card.getCardId().isEmpty())
			return true;
		String num = card.getCollectorId();
		if (num == null || num.trim().isEmpty())
			return false;
		String n = num.trim();
		String set = card.getSet();
		for (IMagicCard b : dbPrintings(card.getName())) {
			if (!n.equals(b.getCollectorId()))
				continue;
			if (set == null || set.isEmpty() || set.equals(b.getSet()))
				return true;
		}
		return false;
	}

	/** true when the name has at least one printing in the card's set. */
	private static boolean setIsKnown(MagicCardPhysical card) {
		if (card.getCardId() != null && !card.getCardId().isEmpty())
			return true;
		String set = card.getSet();
		if (set == null || set.isEmpty())
			return false;
		for (IMagicCard b : dbPrintings(card.getName()))
			if (set.equals(b.getSet()))
				return true;
		return false;
	}

	/**
	 * Database printings for a card name. Tolerates trailing junk that loose /
	 * freeform importers leave on the name (e.g. a mashed-in type line -
	 * "Wall of Faith Creature" -&gt; "Wall of Faith") by dropping trailing words
	 * one at a time. Every probe is an O(1) {@code getCandidates()} hash lookup;
	 * at most a handful of probes, never a scan.
	 */
	// dbPrintings() runs on every cell repaint (background colours) - only trace
	// each distinct outcome once so the console stays readable.
	private static final java.util.Set<String> TRACED_NAMES = java.util.Collections
			.synchronizedSet(new java.util.HashSet<>());

	private static void traceOnce(String key, String message) {
		if (TRACED_NAMES.add(key))
			trace(message);
	}

	/**
	 * DB names indexed by their normalized form ({@link #norm}: lower-case,
	 * accents and every non-alphanumeric char stripped). Lets a name with
	 * imperfect punctuation ("Aang Swift Savior" vs "Aang, Swift Savior") still
	 * resolve. Built once, lazily.
	 */
	private static volatile java.util.Map<String, java.util.List<IMagicCard>> DB_BY_NORM_NAME;

	private static java.util.Map<String, java.util.List<IMagicCard>> dbByNormName() {
		java.util.Map<String, java.util.List<IMagicCard>> m = DB_BY_NORM_NAME;
		if (m != null)
			return m;
		m = new java.util.HashMap<>();
		long t0 = System.currentTimeMillis();
		int n = 0;
		for (IMagicCard c : DataManager.getInstance().getMagicDBStore()) {
			n++;
			for (String f : faces(c.getName()))
				m.computeIfAbsent(f, k -> new java.util.ArrayList<>()).add(c);
		}
		trace("db-name index: " + n + " cards -> " + m.size() + " normalized names (" + (System.currentTimeMillis() - t0)
				+ "ms)");
		DB_BY_NORM_NAME = m;
		return m;
	}

	/** exact hit, or a punctuation/case/accent-relaxed hit - NOT a trailing-word trim. */
	private static java.util.Collection<IMagicCard> dbExact(String name) {
		if (isBlank(name))
			return java.util.Collections.emptyList();
		String n = name.trim();
		java.util.Collection<IMagicCard> c = DataManager.getInstance().getMagicDBStore().getCandidates(n);
		if (c != null && !c.isEmpty())
			return c;
		java.util.List<IMagicCard> relaxed = dbByNormName().get(norm(n));
		return relaxed == null ? java.util.Collections.emptyList() : relaxed;
	}

	/**
	 * MTG super- and card-type words. A trailing run of these (optionally after a
	 * "{@code - subtype}" clause) is a mashed-in type line, not part of the name.
	 */
	private static final java.util.Set<String> TYPE_WORDS = new java.util.HashSet<>(java.util.Arrays.asList("basic",
			"legendary", "snow", "world", "ongoing", "host", "elite", "land", "creature", "artifact", "enchantment",
			"instant", "sorcery", "planeswalker", "battle", "kindred", "tribal", "dungeon", "plane", "phenomenon",
			"vanguard", "scheme", "conspiracy", "emblem", "token", "card", "hero", "sticker", "attraction",
			"contraption", "summon"));

	/**
	 * Drop a trailing type line ("... Legendary Creature - Elf Warrior") if one
	 * is mashed onto the card name. Only strips known type words - random junk
	 * ("Plains Basic M10") is left intact so the name still reads as not found.
	 */
	static String stripTypeLine(String name) {
		if (name == null)
			return "";
		String s = name.trim();
		int dash = s.indexOf(" - ");
		if (dash > 0)
			s = s.substring(0, dash).trim();
		String[] toks = s.split("\\s+");
		int end = toks.length;
		while (end > 1 && TYPE_WORDS.contains(toks[end - 1].toLowerCase()))
			end--;
		return end == toks.length ? s : String.join(" ", java.util.Arrays.copyOfRange(toks, 0, end));
	}

	private static java.util.Collection<IMagicCard> dbPrintings(String rawName) {
		if (rawName == null || rawName.trim().isEmpty())
			return java.util.Collections.emptyList();
		String base = rawName.trim();
		java.util.Collection<IMagicCard> c = dbExact(base);
		if (!c.isEmpty())
			return c;
		String stripped = stripTypeLine(base);
		if (!stripped.equalsIgnoreCase(base)) {
			c = dbExact(stripped);
			if (!c.isEmpty()) {
				traceOnce("strip:" + base, "name \"" + base + "\" resolved against DB name \"" + stripped + "\"");
				return c;
			}
		}
		traceOnce("miss:" + base, "no DB card for name \"" + base + "\"");
		return java.util.Collections.emptyList();
	}

	/**
	 * The database sets that actually hold a printing of {@code name}. When
	 * {@code num} is given and at least one printing carries it, only the sets
	 * that contain <em>that</em> collector number are returned.
	 */
	private static java.util.List<String> candidateSets(String name, String num) {
		java.util.LinkedHashSet<String> all = new java.util.LinkedHashSet<>();
		java.util.LinkedHashSet<String> matching = new java.util.LinkedHashSet<>();
		String n = num == null ? null : num.trim();
		int printings = 0;
		for (IMagicCard base : dbPrintings(name)) {
			printings++;
			String set = base.getSet();
			if (set == null || set.isEmpty())
				continue;
			all.add(set);
			if (n != null && !n.isEmpty() && n.equals(base.getCollectorId()))
				matching.add(set);
		}
		java.util.List<String> out = new java.util.ArrayList<>(matching.isEmpty() ? all : matching);
		out.sort(String.CASE_INSENSITIVE_ORDER);
		trace("candidateSets name=\"" + name + "\" num=\"" + num + "\" -> " + printings + " printing(s), " + out.size()
				+ " set(s) " + out + (matching.isEmpty() ? "" : " [num-filtered]"));
		return out;
	}

	/**
	 * The collector numbers available for {@code name} in {@code set} (all sets
	 * when {@code set} is blank), ascending.
	 */
	private static java.util.List<String> numsForSet(String name, String set) {
		java.util.List<String> nums = new java.util.ArrayList<>();
		boolean anySet = set == null || set.isEmpty();
		for (IMagicCard base : dbPrintings(name)) {
			if (!anySet && !set.equals(base.getSet()))
				continue;
			String n = base.getCollectorId();
			if (n != null && !n.isEmpty() && !nums.contains(n))
				nums.add(n);
		}
		nums.sort(CollectorNumber::compare);
		return nums;
	}

	/**
	 * Bind {@code card} to a database printing in {@code set}. Honours the card's
	 * current collector number when the set has it, otherwise lands on the lowest
	 * number (the "main" printing). All iteration is over the small candidate
	 * list for the single card name.
	 */
	private void bindToSet(MagicCardPhysical card, String set) {
		java.util.List<IMagicCard> printings = new java.util.ArrayList<>();
		for (IMagicCard base : dbPrintings(card.getName())) {
			if (set.equals(base.getSet()))
				printings.add(base);
		}
		trace("bindToSet name=\"" + card.getName() + "\" set=\"" + set + "\" num=\"" + card.getCollectorId() + "\" -> "
				+ printings.size() + " printing(s) in that set");
		if (printings.isEmpty()) {
			MagicCard base = (MagicCard) card.getBase().clone(); // don't mutate a shared DB card
			base.setSet(set);
			base.setCardId(0);
			card.setMagicCard(base);
			card.setError("No \"" + card.getName() + "\" in set \"" + set + "\"");
			return;
		}
		String num = card.getCollectorId();
		IMagicCard chosen = null;
		if (num != null && !num.trim().isEmpty()) {
			for (IMagicCard p : printings) {
				if (num.trim().equals(p.getCollectorId())) {
					chosen = p;
					break;
				}
			}
		}
		if (chosen == null) {
			chosen = printings.get(0);
			for (IMagicCard p : printings) {
				if (CollectorNumber.compare(p.getCollectorId(), chosen.getCollectorId()) < 0)
					chosen = p;
			}
		}
		trace("bindToSet -> bound \"" + card.getName() + "\" to " + chosen.getSet() + " #" + chosen.getCollectorId()
				+ " id=" + chosen.getCardId());
		card.setMagicCard((MagicCard) chosen);
		card.setError(null);
	}

	/**
	 * Apply a collector number the user typed / picked. Stores it, drops the old
	 * DB binding, then re-binds: to the printing with that number in the card's
	 * current set if there is one, else - only if the number is unique across all
	 * sets - to that printing.
	 */
	private void bindNum(MagicCardPhysical card, String num) {
		MagicCard base = (MagicCard) card.getBase().clone(); // don't mutate a shared DB card
		base.setCollNumber(num);
		base.setCardId(0);
		card.setMagicCard(base);
		card.setError(null);
		if (num == null || num.isEmpty())
			return;
		String set = card.getSet();
		if (set != null && !set.isEmpty()) {
			for (IMagicCard p : dbPrintings(card.getName())) {
				if (set.equals(p.getSet()) && num.equals(p.getCollectorId())) {
					trace("bindNum \"" + card.getName() + "\" #" + num + " -> matched in current set " + set);
					card.setMagicCard((MagicCard) p);
					card.setError(null);
					return;
				}
			}
		}
		autoBindByNum(card, num);
	}

	/**
	 * When the collector number the user just typed is unique across every set
	 * that has this card name, bind that printing straight away. Otherwise leave
	 * the card unresolved so the user disambiguates with the Set drop-down.
	 */
	private void autoBindByNum(MagicCardPhysical card, String num) {
		if (num == null || num.isEmpty())
			return;
		IMagicCard match = null;
		int hits = 0;
		for (IMagicCard base : dbPrintings(card.getName())) {
			if (num.equals(base.getCollectorId())) {
				hits++;
				match = base;
			}
		}
		if (hits == 1) {
			trace("autoBindByNum \"" + card.getName() + "\" #" + num + " -> unique in " + match.getSet());
			card.setMagicCard((MagicCard) match);
			card.setError(null);
		} else {
			trace("autoBindByNum \"" + card.getName() + "\" #" + num + " -> " + hits
					+ " match(es), leaving unresolved for a Set pick");
		}
	}

	private static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

	public boolean isIgnoreErrors() {
		return ignoreErrors != null && ignoreErrors.getSelection();
	}

	public void safeSetText(String text2) {
		text.removeModifyListener(modifyLister);
		int k = text2.indexOf('\n');
		if (k == -1)
			k = text2.indexOf('\r');
		text.setText(text2);
		int maxLine = 80 * 40;
		int len = text2.length();
		if (k == -1 && len > maxLine) {
			text.setText(text2.substring(0, maxLine));
			text.setEditable(false);
			return;
		}
		text.setEditable(true);
		text.addModifyListener(modifyLister);
	}

	public void validate() {
		setErrorMessage(null);
		int errorCount = importData.getErrorCount();
		Throwable e = importData.getError();
		if (e != null) {
			DeckImportPage main = getMainPage();
			String src = main != null ? main.getSourceDescription() : "input";
			String fmt = main != null && main.getReportType() != null ? main.getReportType().getLabel() : "?";
			// a MagicException is an expected "bad input / wrong format" condition -
			// log one concise line (no stack trace); anything else is a real bug
			if (e instanceof MagicException)
				MagicUIActivator.log(new Status(IStatus.WARNING, MagicUIActivator.PLUGIN_ID,
						"Import parse failed - " + src + " as format \"" + fmt + "\": " + e.getMessage()));
			else
				MagicUIActivator.log("Import parse failed - " + src + " as format \"" + fmt + "\"", e);
			setErrorMessage("Cannot parse " + src + " as \"" + fmt + "\": " + e.getMessage());
		} else if (!importData.isOk())
			setErrorMessage("Cannot parse data file: unknown reason");
		else if (errorCount == 0) {
			setDescription(getFirstDescription());
		} else if (isIgnoreErrors()) {
			setMessage(errorCount + " card(s) still have errors and will be skipped on Finish.",
					org.eclipse.jface.dialogs.IMessageProvider.WARNING);
			setPageComplete(true);
			return;
		} else {
			setErrorMessage(errorCount + " card(s) have errors - select a row to see why, fix the Num / Set in "
					+ "the table, or tick \"Ignore cards with errors\" to import only the valid ones.");
			setPageComplete(false);
			return;
		}
		setPageComplete(getErrorMessage() == null);
	}

	/**
	 * Show only the columns the selected format actually reads from the source
	 * file, plus Name / Set / Num / Count which are always shown so the user can
	 * review and fix them. Database-derived data (cost, price, power, ...) is
	 * never a column here even though the resolved card carries it.
	 */
	public void updateColumns(ICardField[] fields) {
		ColumnCollection colls = viewer.getColumnsCollection();
		java.util.LinkedHashSet<ICardField> cols = new java.util.LinkedHashSet<>();
		cols.add(MagicCardField.NAME);
		cols.add(MagicCardField.SET);
		cols.add(MagicCardField.COLLNUM);
		cols.add(MagicCardField.COUNT);
		if (fields != null)
			for (ICardField field : fields)
				if (field != null)
					cols.add(field);
		StringBuilder pref = new StringBuilder();
		// the Error column is always the leftmost one
		AbstractColumn err = colls.getColumn(MagicCardField.ERROR);
		if (err != null)
			pref.append(err.getColumnFullName());
		for (ICardField field : cols) {
			AbstractColumn column = colls.getColumn(field);
			if (column != null)
				pref.append(pref.length() == 0 ? "" : ",").append(column.getColumnFullName());
		}
		final String p = pref.toString();
		Display.getDefault().syncExec(() -> viewer.updateColumns(p));
	}

	public String getTextOfFileAsString(InputStream st, int lines) throws FileNotFoundException, IOException {
		String textFile = "";
		if (st != null) {
			String line;
			int i = 0;
			BufferedReader b = new BufferedReader(new InputStreamReader(st));
			while ((line = b.readLine()) != null && i < lines) {
				textFile += line + "\n";
				i++;
			}
		}
		return textFile;
	}

	public DeckImportPage getMainPage() {
		IWizardPage[] pages = getWizard().getPages();
		for (IWizardPage wizardPage : pages) {
			if (wizardPage instanceof DeckImportPage)
				return (DeckImportPage) wizardPage;
		}
		return null;
	}

	public String getFirstDescription() {
		DeckImportPage startingPage = getMainPage();
		int choice = startingPage.getIntoChoice();
		switch (choice) {
		case 1:
			return "Importing into a new deck/collection";
		case 2:
			CardElement element = startingPage.getElement();
			String deckName = element == null ? "newdeck" : element.getName();
			String desc = "Importing into " + deckName + ".";
			return desc;
		case 3:
			return "Extending Magic Card Database";
		default:
			break;
		}
		return "";
	}

	@Override
	public void createControl(Composite parent) {
		setDescription("Import preview (10 rows)");
		Composite comp = new Composite(parent, SWT.NONE);
		setControl(comp);
		comp.setLayout(new GridLayout());
		text = new Text(comp, SWT.WRAP | SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		GridData ld = new GridData(GridData.FILL_HORIZONTAL);
		ld.heightHint = text.getLineHeight() * 5;
		text.setLayoutData(ld);
		text.addModifyListener(modifyLister);
		viewer = new SimpleTableViewer(comp, columns);
		GridData tld = new GridData(GridData.FILL_BOTH);
		tld.widthHint = 1000;
		tld.heightHint = 340;
		viewer.getControl().setLayoutData(tld);
		viewer.getViewer().addSelectionChangedListener(e -> showErrorForSelection());

		errorZone = new Text(comp, SWT.WRAP | SWT.MULTI | SWT.READ_ONLY | SWT.V_SCROLL | SWT.BORDER);
		GridData ezd = new GridData(GridData.FILL_HORIZONTAL);
		ezd.heightHint = errorZone.getLineHeight() * 3;
		errorZone.setLayoutData(ezd);
		errorZone.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_RED));

		org.eclipse.swt.widgets.Button autoFix = new org.eclipse.swt.widgets.Button(comp, SWT.PUSH);
		autoFix.setText("Auto complete using first match");
		autoFix.setToolTipText("For every unresolved card whose file data doesn't already point "
				+ "elsewhere, pick the most recently released set that has it and its lowest collector "
				+ "number. Auto-filled fields turn green.");
		autoFix.addSelectionListener(new org.eclipse.swt.events.SelectionAdapter() {
			@Override
			public void widgetSelected(org.eclipse.swt.events.SelectionEvent e) {
				autoFixFirstMatch();
			}
		});

		ignoreErrors = new org.eclipse.swt.widgets.Button(comp, SWT.CHECK);
		ignoreErrors.setText("Ignore cards with errors (import only the valid ones)");
		ignoreErrors.setToolTipText("When checked, Finish stays enabled and cards that still have "
				+ "an error are skipped instead of blocking the import.");
		ignoreErrors.addSelectionListener(new org.eclipse.swt.events.SelectionAdapter() {
			@Override
			public void widgetSelected(org.eclipse.swt.events.SelectionEvent e) {
				validate();
			}
		});
		comp.addDisposeListener(e -> {
			if (greenBg != null && !greenBg.isDisposed())
				greenBg.dispose();
		});
		/*
		 * !!! RD Button button = new Button(comp, SWT.PUSH); button.setText("Attempt to Auto Fix Errors"); button.addSelectionListener(new SelectionAdapter() {
		 * 
		 * @Override public void widgetSelected(SelectionEvent event) { Collection<IMagicCard> result = (Collection<IMagicCard>) importData.getList(); DeckImportPage mainPage = getMainPage(); int choice = mainPage.getIntoChoice(); final boolean dbImport = choice == 3; try { IRunnableWithProgress work = new IRunnableWithProgress() {
		 * 
		 * @Override public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException { mainPage.fixErrors(result, dbImport, monitor); } }; getRunnableContext().run(true, true, work); } catch (InvocationTargetException ite) { Throwable e = ite.getCause(); if (e instanceof OperationCanceledException) { reload(); return; } importData.setError(e); if (e instanceof RuntimeException && !(e instanceof MagicException)) MagicUIActivator.log(e); } catch (InterruptedException e) { importData.setError(e); reload(); } } });
		 */
	}

	protected IRunnableContext getRunnableContext() {
		return getContainer();
	}

	public ImportData getPreviewResult() {
		return importData;
	}

	private MagicColumnCollection columns = new MagicColumnCollection(null) {
		@Override
		protected GroupColumn createGroupColumn() {
			// plain text column (the owner-draw GroupColumn leaves a sliver to the
			// left of the name, and the preview needs neither the set icon nor the
			// group indent). Shows the imported name; editable so the user can fix
			// a name the database doesn't recognise.
			return new GroupColumn(true, false, true) {

				@Override
				public void handleEvent(org.eclipse.swt.widgets.Event event) {
					// no custom cell painting - use JFace's native text rendering
				}

				@Override
				public String getText(Object element) {
					if (element instanceof MagicCardPhysical) {
						String imp = importedName((MagicCardPhysical) element);
						if (imp != null)
							return imp;
					}
					return super.getText(element);
				}

				@Override
				public String getToolTipText(Object element) {
					return getText(element);
				}

				@Override
				public Color getBackground(Object element) {
					if (element instanceof MagicCardPhysical) {
						MagicCardPhysical card = (MagicCardPhysical) element;
						if (isConflict(card, MagicCardField.NAME) || nameNotFound(card))
							return red();
					}
					return super.getBackground(element);
				}

				@Override
				public EditingSupport getEditingSupport(ColumnViewer v) {
					return new EditingSupport(v) {
						@Override
						protected boolean canEdit(Object e) {
							return e instanceof MagicCardPhysical;
						}

						@Override
						protected CellEditor getCellEditor(Object e) {
							return new TextCellEditor((Composite) getViewer().getControl(), SWT.NONE);
						}

						@Override
						protected Object getValue(Object e) {
							return getText(e);
						}

						@Override
						protected void setValue(Object e, Object value) {
							renameCard((MagicCardPhysical) e, value == null ? "" : value.toString().trim());
						}
					};
				}
			};
		}

		@Override
		protected CountColumn createCountColumn() {
			return new CountColumn() {

				@Override
				protected boolean canEditElement(Object element) {
					return false;
				}
			};
		}

		@Override
		protected StringEditorColumn createSpecialColumn() {
			return new StringEditorColumn(MagicCardField.SPECIAL, "Special") {

				@Override
				public EditingSupport getEditingSupport(final ColumnViewer viewer) {
					return null;
				}

				@Override
				protected boolean canEditElement(Object element) {
					return element instanceof MagicCardPhysical;
				}

			};
		}

		@Override
		protected CommentColumn createCommentColumn() {
			return new CommentColumn() {

				@Override
				protected boolean canEditElement(Object element) {
					return false;
				}
			};
		}

		@Override
		protected OwnershipColumn createOwnershipColumn() {
			return new OwnershipColumn() {

				@Override
				public EditingSupport getEditingSupport(final ColumnViewer viewer) {
					return null;
				}

				@Override
				protected boolean canEditElement(Object element) {
					return false;
				}
			};
		}

		@Override
		protected IdColumn createIdColumn() {
			return new IdColumn() {

				@Override
				public Color getBackground(Object element) {
					IMagicCard card = (IMagicCard) element;
					if (card != null && card.getCardId() != null) {

						IMagicCard iref = DataManager.getInstance().resolve(card,
								DataManager.getInstance().getMagicDBStore());

						if (iref == null) {
							return Display.getDefault().getSystemColor(SWT.COLOR_RED);
						}
					}
					return super.getBackground(element);
				}
			};
		}

		@Override
		protected SetColumn createSetColumn() {
			return new SetColumn() {
				@Override
				public Color getBackground(Object element) {
					if (element instanceof MagicCardPhysical) {
						if (isConflict(element, MagicCardField.SET))
							return red();
						if (isAuto(element, MagicCardField.SET))
							return green();
						if (!setIsKnown((MagicCardPhysical) element))
							return red();
					}
					return super.getBackground(element);
				}

				@Override
				public EditingSupport getEditingSupport(ColumnViewer viewer) {
					return new SetEditingSupport(viewer) {
						@Override
						public String[] getItems(Object element) {
							MagicCardPhysical card = (MagicCardPhysical) element;
							java.util.List<String> sets = candidateSets(card.getName(), card.getCollectorId());
							String cur = card.getSet();
							if (cur != null && !cur.isEmpty() && !sets.contains(cur)) {
								sets.add(cur);
								sets.sort(String.CASE_INSENSITIVE_ORDER);
							}
							return sets.toArray(new String[sets.size()]);
						}

						@Override
						protected void setValue(Object element, Object value) {
							if (!(element instanceof MagicCardPhysical))
								return;
							MagicCardPhysical card = (MagicCardPhysical) element;
							String set = (String) value;
							if (set == null || set.isEmpty())
								return;
							// re-bind even when the set is unchanged: this is how the
							// user clears a wrong Num (it drops to the lowest number
							// the set actually has)
							Snap before = new Snap(card);
							bindToSet(card, set);
							applyAutoMarks(card, before, MagicCardField.SET);
							markUserResolved(card);
							revalidateAfterEdit();
						}
					};
				}
			};
		}

		@Override
		protected GenColumn createCollectorsNumberColumn() {
			// editable combo: lists the collector numbers the selected set has for
			// this card (so the user can pick when a set has several printings),
			// but still accepts free text so a Num can be typed before the card
			// resolves.
			return new GenColumn(MagicCardField.COLLNUM, "Num") {
				@Override
				public String getColumnFullName() {
					return "Collector's Number";
				}

				@Override
				public int getColumnWidth() {
					return 60;
				}

				@Override
				public Color getBackground(Object element) {
					if (element instanceof MagicCardPhysical) {
						if (isConflict(element, MagicCardField.COLLNUM))
							return red();
						if (isAuto(element, MagicCardField.COLLNUM))
							return green();
						if (!numIsKnown((MagicCardPhysical) element))
							return red();
					}
					return super.getBackground(element);
				}

				@Override
				public EditingSupport getEditingSupport(ColumnViewer v) {
					return new ComboStringEditingSupport(v) {
						@Override
						protected boolean canEdit(Object element) {
							return element instanceof MagicCardPhysical;
						}

						@Override
						public int getStyle() {
							return SWT.NONE; // editable CCombo, not read-only
						}

						@Override
						public String[] getItems(Object element) {
							MagicCardPhysical card = (MagicCardPhysical) element;
							java.util.List<String> nums = numsForSet(card.getName(), card.getSet());
							String cur = card.getCollectorId();
							if (cur != null && !cur.isEmpty() && !nums.contains(cur)) {
								nums.add(cur);
								nums.sort(CollectorNumber::compare);
							}
							return nums.toArray(new String[nums.size()]);
						}

						@Override
						protected Object getValue(Object element) {
							String n = ((MagicCardPhysical) element).getCollectorId();
							return n == null ? "" : n;
						}

						@Override
						protected void setValue(Object element, Object value) {
							MagicCardPhysical card = (MagicCardPhysical) element;
							String num = value == null ? "" : value.toString().trim();
							String old = card.getCollectorId() == null ? "" : card.getCollectorId();
							if (num.equals(old))
								return;
							Snap before = new Snap(card);
							bindNum(card, num);
							applyAutoMarks(card, before, MagicCardField.COLLNUM);
							markUserResolved(card);
							revalidateAfterEdit();
						}
					};
				}
			};
		}

		@Override
		protected GenColumn createErrorColumn() {
			// only the error *type* in the column; the full text goes in the zone
			// below the table
			return new GenColumn(MagicCardField.ERROR, "Error") {
				@Override
				public String getText(Object element) {
					if (element instanceof MagicCardPhysical)
						return errorShort(((MagicCardPhysical) element).getError());
					return super.getText(element);
				}

				@Override
				public String getToolTipText(Object element) {
					if (element instanceof MagicCardPhysical && ((MagicCardPhysical) element).getError() != null)
						return String.valueOf(((MagicCardPhysical) element).getError());
					return null;
				}

				@Override
				public int getColumnWidth() {
					return 120;
				}
			};
		}

		@Override
		protected GenColumn createGathererIdColumn() {
			// editable Multiverse id: 0 / empty shows blank; typing the right id
			// re-binds the row (TCGID is not imported, so no override for it).
			// Header stays the app-standard "Multiverse ID".
			return new GenColumn(MagicCardField.GATHERERID, "Multiverse ID") {
				@Override
				public int getColumnWidth() {
					return 95;
				}

				@Override
				public Color getBackground(Object element) {
					return gathererBackground(element, super.getBackground(element));
				}

				@Override
				public String getText(Object element) {
					return element instanceof MagicCardPhysical ? gathererId((MagicCardPhysical) element)
							: super.getText(element);
				}

				@Override
				public EditingSupport getEditingSupport(ColumnViewer v) {
					return new EditingSupport(v) {
						@Override
						protected boolean canEdit(Object e) {
							return e instanceof MagicCardPhysical;
						}

						@Override
						protected CellEditor getCellEditor(Object e) {
							return new TextCellEditor((Composite) getViewer().getControl(), SWT.NONE);
						}

						@Override
						protected Object getValue(Object e) {
							return gathererId((MagicCardPhysical) e);
						}

						@Override
						protected void setValue(Object e, Object value) {
							String v = value == null ? "" : value.toString().trim();
							setGathererId((MagicCardPhysical) e, blankId(v) ? "" : v);
						}
					};
				}
			};
		}
	};

	private static String gathererId(MagicCardPhysical card) {
		String id = card.getBase().getGathererCardId();
		return blankId(id) ? "" : id.trim();
	}

	private void setGathererId(MagicCardPhysical card, String v) {
		if (v.equals(gathererId(card)))
			return;
		// treat the correction as the file's value so the mismatch check uses it
		String[] d = importData.getDeclared(card);
		if (d != null)
			d[4] = v;
		// clone first - the base may be a shared DB card once the row resolved
		MagicCard base = (MagicCard) card.getBase().clone();
		base.setGathererCardId(v.isEmpty() ? null : v);
		card.setMagicCard(base);
		MagicCard hit = byGatherer(v);
		if (hit != null && !sameId(hit.getCardId(), card.getCardId())) {
			Snap before = new Snap(card);
			card.setMagicCard(hit);
			card.setError(null);
			applyAutoMarks(card, before, null);
			trace("edit GATHERERID=" + v + " -> rebound '" + hit.getName() + "' / " + hit.getSet());
		} else {
			trace("edit GATHERERID=" + (v.isEmpty() ? "(blank)" : v));
		}
		markUserResolved(card);
		revalidateAfterEdit();
	}

	/**
	 * Green when we filled the Multiverse id from the database, red on conflict
	 * or when the file's id matches no database card. 0 / empty is left alone.
	 */
	private Color gathererBackground(Object element, Color deflt) {
		if (!(element instanceof MagicCardPhysical))
			return deflt;
		if (isConflict(element, MagicCardField.GATHERERID))
			return red();
		if (isAuto(element, MagicCardField.GATHERERID))
			return green();
		String id = ((MagicCardPhysical) element).getBase().getGathererCardId();
		if (blankId(id))
			return deflt;
		return byGatherer(id) == null ? red() : deflt;
	}
}
