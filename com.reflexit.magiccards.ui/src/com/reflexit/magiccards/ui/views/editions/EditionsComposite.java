/*******************************************************************************
 * Copyright (c) 2008 Alena Laskavaia.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Alena Laskavaia - initial API and implementation
 *******************************************************************************/

/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 *     Rémi Dutil (2026) - createDefaultColumns(): no longer wires cell
 *                         editing for a checked (SWT.CHECK) tree - see the
 *                         method's own comment
 *     Rémi Dutil (2026) - grew the tree's heightHint (300 -> 450): the Main
 *                         Filter tab got noticeably more compact this round
 *                         (two-column Text/Legality/Artist/etc. layout), so
 *                         the Set Filter tab - which actually sets the whole
 *                         dialog's height (see this class' own class-level
 *                         javadoc) - had room to show more sets at once
 *                         without growing the dialog further.
 *     Rémi Dutil (2026) - two new buttons, "Show Selected Only"/"Show All
 *                         Sets" - a display-only ViewerFilter
 *                         (selectedOnlyFilter) toggled by a showOnlySelected
 *                         flag, filtering rows by whether checkedSet
 *                         contains them. Deliberately reads checkedSet (the
 *                         same model-backed store the check-state provider
 *                         already uses so a row keeps its check even while
 *                         scrolled out of view or hidden by the search box's
 *                         own PatternFilter) rather than the tree widget's
 *                         own checkbox bits, and never writes to it - hiding
 *                         or reshowing a row this way can't change what's
 *                         actually checked, only what's currently visible.
 *                         Combines with the existing search-text PatternFilter
 *                         via ordinary multi-filter AND semantics (both
 *                         JFace ViewerFilters on the same TreeViewer), so
 *                         typing a search AND toggling "Show Selected Only"
 *                         narrows to matches that are also checked. Gated on
 *                         checkedTree - meaningless for the non-checkbox
 *                         tree modes (BoosterGeneratorWizard/
 *                         EditionsPreferencePage), where checkedSet is never
 *                         populated at all.
 *     Rémi Dutil (2026) - performApply(): Editions.getInstance().save()
 *                         (rewrites the WHOLE editions data file to disk) is
 *                         now only called for a non-checked tree - a checked
 *                         tree (the Set Filter tab) never has cell editing
 *                         wired at all (see createDefaultColumns()'s own
 *                         comment), so nothing about edition data itself can
 *                         ever change there; only which sets are checked
 *                         does, and that's a preference-store value already
 *                         written a few lines above, not edition data. Every
 *                         Apply/OK in the Set Filter tab was rewriting that
 *                         file for nothing.
 */

package com.reflexit.magiccards.ui.views.editions;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceStore;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.ICheckStateProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.window.ToolTip;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.ui.dialogs.FilteredTree;
import org.eclipse.ui.dialogs.PatternFilter;

import com.reflexit.magiccards.core.model.Edition;
import com.reflexit.magiccards.core.model.Editions;
import com.reflexit.magiccards.core.model.FilterField;

/**
 * Composite that contains checked tree selection for editions. If supplied with
 * preferenceStore can be also used as field editor
 *
 * <code>
 * c = new EditionsComposite(parent,SWT.CHECK | SWT.BORDER);
 * c.setPreferenceStore(store);
 * c.initialize();
 * ...
 * // when user pressed ok in dialog call this to store values in preference store
 * c.performApply();
 * </code>
 */
public class EditionsComposite extends Composite {
	private static final String SORT_DIRECTION = "set_sort_direction";
	private static final String SORT_COLUMN = "set_sort_column";
	private boolean buttons;

	public EditionsComposite(Composite parent) {
		this(parent, SWT.CHECK | SWT.BORDER, true);
	}

	/**
	 * @param parent
	 * @param treeStyle
	 */
	public EditionsComposite(Composite parent, int treeStyle, boolean buttons) {
		super(parent, SWT.NONE);
		this.buttons = buttons;
		this.setLayout(new GridLayout());
		this.setFont(parent.getFont());
		Composite one = (Composite) createContents(this, treeStyle);
		one.setLayoutData(new GridData(GridData.FILL_BOTH));
		setPreferenceStore(new PreferenceStore());
	}

	private TreeViewer treeViewer;
	private Composite panel;
	private IPreferenceStore prefStore;
	private boolean checkedTree = false;
	private Button selAll;
	private Button deselAll;
	private Label countLabel;
	/** Checked sets, tracked independently of the tree so the filter box can't drop them. */
	private final Set<Edition> checkedSet = new HashSet<>();
	private ArrayList<AbstractEditionColumn> columns;
	private EditionsViewerComparator vcomp;
	/** "Show Selected Only" state - display-only, never touches checkedSet. */
	private boolean showOnlySelected = false;

	protected Control createContents(Composite parent, int treeStyle) {
		this.panel = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		this.panel.setLayout(layout);
		this.panel.setFont(parent.getFont());
		PatternFilter filter = new PatternFilter();
		FilteredTree filteredTree = new FilteredTree(panel, treeStyle, filter, true) {
			@Override
			protected TreeViewer doCreateTreeViewer(Composite parent, int style) {
				if ((style & SWT.CHECK) != 0) {
					checkedTree = true;
					EditionsComposite.this.treeViewer = new CheckboxTreeViewer(parent, style);
				} else {
					checkedTree = false;
					EditionsComposite.this.treeViewer = new TreeViewer(parent, style);
				}
				return EditionsComposite.this.treeViewer;
			}

			@Override
			protected Text doCreateFilterText(Composite parent) {
				Text text = super.doCreateFilterText(parent);
				text.setFont(parent.getFont());
				return text;
			}
		};
		filteredTree.setFont(panel.getFont());
		// this.treeViewer.setLabelProvider(null);
		this.treeViewer.setContentProvider(new EditionsContentProvider());
		vcomp = new EditionsViewerComparator();
		this.treeViewer.setComparator(vcomp);
		this.treeViewer.setUseHashlookup(true);
		treeViewer.getControl().setFont(parent.getFont());
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.heightHint = 450;
		filteredTree.setLayoutData(gd);
		createDefaultColumns();
		// display-only: hides rows without touching checkedSet - see this
		// class' own header
		this.treeViewer.addFilter(new ViewerFilter() {
			@Override
			public boolean select(Viewer viewer, Object parentElement, Object element) {
				if (!showOnlySelected)
					return true;
				return element instanceof Edition && checkedSet.contains(element);
			}
		});
		this.countLabel = new Label(panel, SWT.NONE);
		this.countLabel.setFont(panel.getFont());
		this.countLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		Composite buttons = new Composite(parent, SWT.NONE);
		buttons.setLayout(GridLayoutFactory.fillDefaults().numColumns(5).create());
		createButtonsControls(buttons);
		this.treeViewer.setInput(Editions.getInstance());
		if (checkedTree) {
			CheckboxTreeViewer ctv = (CheckboxTreeViewer) this.treeViewer;
			// the tree asks the model for each row's checked state, so a row that
			// scrolls out of view (or is hidden by the filter) keeps its check
			ctv.setCheckStateProvider(new ICheckStateProvider() {
				@Override
				public boolean isChecked(Object element) {
					return element instanceof Edition && checkedSet.contains(element);
				}

				@Override
				public boolean isGrayed(Object element) {
					return false;
				}
			});
			ctv.addCheckStateListener(e -> {
				if (e.getElement() instanceof Edition) {
					if (e.getChecked())
						checkedSet.add((Edition) e.getElement());
					else
						checkedSet.remove((Edition) e.getElement());
					updateCount();
				}
			});
		} else {
			this.treeViewer.addSelectionChangedListener(e -> updateCount());
		}
		updateCount();
		return this.panel;
	}

	@Override
	public void dispose() {
		treeViewer = null;
		columns = null;
		super.dispose();
	}

	protected void createDefaultColumns() {
		createColumnLabelProviders();
		for (int i = 0; i < columns.size(); i++) {
			AbstractEditionColumn man = this.columns.get(i);
			TreeViewerColumn colv = new TreeViewerColumn((TreeViewer) getViewer(), i);
			TreeColumn col = colv.getColumn();
			col.setText(man.getColumnName());
			col.setWidth(man.getColumnWidth());
			col.setToolTipText(man.getColumnTooltip());
			final int coln = i;
			col.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					sort(coln);
				}
			});
			col.setMoveable(false);
			colv.setLabelProvider(man);
			if (man instanceof Listener) {
				treeViewer.getTree().addListener(SWT.PaintItem, (Listener) man);
			}
			// A checked tree (SWT.CHECK) is a selection UI - the checkbox is the
			// only thing meant to be interactive, e.g. the Set Filter page. The
			// generic AbstractEditionColumn#getEditingSupport() default lets
			// every cell open a live TextCellEditor regardless, so a set's name
			// looked directly editable there even though nothing was meant to
			// let the user rename/retype it. Only wire real cell editing for a
			// plain (non-checked) tree, i.e. the actual edition-editing admin
			// page (EditionsPreferencePage).
			if (!checkedTree) {
				colv.setEditingSupport(man.getEditingSupport(treeViewer));
			}
		}
		ColumnViewerToolTipSupport.enableFor(treeViewer, ToolTip.NO_RECREATE);
		treeViewer.getTree().setHeaderVisible(true);
	}

	private void createColumnLabelProviders() {
		columns = new ArrayList<>();
		columns.add(new EditionNameColumn());
		columns.add(new AbbrColumn());
		columns.add(new DateColumn());
		columns.add(new TypeColumn());
		columns.add(new BlockColumn());
		columns.add(new AliasesColumn());
	}

	protected void sort(int index) {
		updateSortColumn(index);
		treeViewer.refresh();
	}

	/** Shows how many sets are selected in total - the filter box hides rows, not the count. */
	private void updateCount() {
		if (countLabel == null || countLabel.isDisposed())
			return;
		int n;
		if (checkedTree)
			n = checkedSet.size();
		else
			n = ((IStructuredSelection) treeViewer.getSelection()).size();
		int total = Editions.getInstance().getEditions().size();
		countLabel.setText(n + " of " + total + " sets selected");
	}

	public void updateSortColumn(int index) {
		boolean sort = index >= 0;
		TreeColumn column = sort ? treeViewer.getTree().getColumn(index) : null;
		treeViewer.getTree().setSortColumn(column);
		if (sort) {
			int sortDirection = treeViewer.getTree().getSortDirection();
			if (sortDirection != SWT.DOWN)
				sortDirection = SWT.DOWN;
			else
				sortDirection = SWT.UP;
			treeViewer.getTree().setSortDirection(sortDirection);
			AbstractEditionColumn man = (AbstractEditionColumn) treeViewer.getLabelProvider(index);
			vcomp.setOrder(man.getSortField(), sortDirection == SWT.UP);
			treeViewer.setComparator(vcomp);
			getPreferenceStore().setValue(SORT_COLUMN, man.getColumnName());
			getPreferenceStore().setValue(SORT_DIRECTION, sortDirection == SWT.UP ? 1 : -1);
		} else {
			getPreferenceStore().setValue(SORT_COLUMN, null);
			treeViewer.setComparator(null);
		}
	}

	protected void createButtonsControls(Composite panel) {
		// buttons
		if (buttons) {
			this.selAll = new Button(panel, SWT.PUSH);
			this.selAll.setText("Select All");
			this.selAll.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					selectAll();
				}
			});
			this.deselAll = new Button(panel, SWT.PUSH);
			this.deselAll.setText("Deselect All");
			this.deselAll.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					deselectAll();
				}
			});
			selAll.setFont(panel.getFont());
			deselAll.setFont(panel.getFont());
			// display-only, never touches which sets are checked - see this
			// class' own header. Meaningless outside a checked tree (nothing
			// is ever tracked in checkedSet there).
			if (checkedTree) {
				Button showSelectedOnly = new Button(panel, SWT.PUSH);
				showSelectedOnly.setText("Show Selected Only");
				showSelectedOnly.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						showOnlySelected = true;
						treeViewer.refresh();
					}
				});
				showSelectedOnly.setFont(panel.getFont());
				Button showAllSets = new Button(panel, SWT.PUSH);
				showAllSets.setText("Show All Sets");
				showAllSets.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						showOnlySelected = false;
						treeViewer.refresh();
					}
				});
				showAllSets.setFont(panel.getFont());
			}
		}
	}

	protected void deselectAll() {
		IPreferenceStore store = getPreferenceStore();
		String ids[] = getIds();
		if (store != null) {
			for (String id : ids) {
				store.setValue(id, false);
			}
			initialize();
		} else {
			if (this.treeViewer instanceof CheckboxTreeViewer) {
				((CheckboxTreeViewer) this.treeViewer).setAllChecked(false);
			} else {
				this.treeViewer.getTree().deselectAll();
			}
		}
	}

	protected void selectAll() {
		IPreferenceStore store = getPreferenceStore();
		String ids[] = getIds();
		if (store != null) {
			for (String id : ids) {
				store.setValue(id, true);
			}
			initialize();
		} else {
			if (this.treeViewer instanceof CheckboxTreeViewer) {
				((CheckboxTreeViewer) this.treeViewer).setAllChecked(true);
			} else {
				this.treeViewer.getTree().selectAll();
			}
		}
	}

	public void initialize() {
		this.treeViewer.setInput(Editions.getInstance());
		Collection<Edition> names = Editions.getInstance().getEditions();
		ArrayList<Edition> sel = new ArrayList<>();
		if (this.checkedTree)
			this.checkedSet.clear();
		for (Iterator<Edition> iterator = names.iterator(); iterator.hasNext();) {
			Edition ed = iterator.next();
			String abbr = ed.getMainAbbreviation();
			String id = FilterField.getPrefConstant(FilterField.EDITION, abbr);
			boolean checked = getPreferenceStore().getBoolean(id);
			if (this.checkedTree) {
				if (checked)
					this.checkedSet.add(ed);
			} else if (checked) {
				sel.add(ed);
			}
		}
		if (!this.checkedTree) {
			this.treeViewer.setSelection(new StructuredSelection(sel));
		}
		updateCount();
		String colName = getPreferenceStore().getString(SORT_COLUMN);
		if (colName != null)
			for (int i = 0; i < columns.size(); i++) {
				AbstractEditionColumn man = columns.get(i);
				if (colName.equals(man.getColumnName())) {
					int sortvalue = getPreferenceStore().getInt(SORT_DIRECTION);
					if (sortvalue != 0) {
						int sortDirection = sortvalue == 1 ? SWT.UP : SWT.DOWN;
						vcomp.setOrder(man.getSortField(), sortDirection == SWT.UP);
						treeViewer.setComparator(vcomp);
						treeViewer.getTree().setSortDirection(sortDirection);
					}
					break;
				}
			}
		treeViewer.refresh(true);
	}

	/**
	 * @return
	 */
	public IPreferenceStore getPreferenceStore() {
		return this.prefStore;
	}

	public void setPreferenceStore(IPreferenceStore s) {
		this.prefStore = s;
	}

	public void performApply() {
		if (this.treeViewer == null)
			return;
		Collection<Edition> editions = Editions.getInstance().getEditions();
		for (Iterator<Edition> iterator = editions.iterator(); iterator.hasNext();) {
			Edition ed = iterator.next();
			boolean checked = false;
			if (this.checkedTree) {
				// from the model, not the tree - filtered-out rows must still count
				checked = this.checkedSet.contains(ed);
			}
			String abbr = ed.getMainAbbreviation();
			String id = FilterField.getPrefConstant(FilterField.EDITION, abbr);
			getPreferenceStore().setValue(id, checked);
		}
		if (!this.checkedTree) {
			IStructuredSelection selection = (IStructuredSelection) this.treeViewer.getSelection();
			for (Iterator<Edition> iterator = selection.iterator(); iterator.hasNext();) {
				Edition ed = iterator.next();
				String abbr = ed.getMainAbbreviation();
				String id = FilterField.getPrefConstant(FilterField.EDITION, abbr);
				getPreferenceStore().setValue(id, true);
			}
		}
		if (!this.checkedTree) {
			// only the non-checked tree (EditionsPreferencePage's own set-
			// editing admin page) can actually change edition DATA (name,
			// abbreviations, block, etc - see createDefaultColumns()'s own
			// comment on why cell editing is only wired there); a checked
			// tree (the Set Filter tab) only ever changes which sets are
			// checked, a preference-store value already written above, so
			// rewriting the whole editions file here was pure waste on every
			// Apply/OK
			try {
				Editions.getInstance().save();
			} catch (FileNotFoundException e) {
				// ignore
			}
		}
	}

	public IStructuredSelection getSelection() {
		return (IStructuredSelection) this.treeViewer.getSelection();
	}

	private String[] getIds() {
		Collection<String> names = Editions.getInstance().getNames();
		ArrayList<String> res = new ArrayList<>();
		for (Iterator<String> iterator = names.iterator(); iterator.hasNext();) {
			String ed = iterator.next();
			String abbr = Editions.getInstance().getAbbrByName(ed);
			if (abbr == null)
				abbr = ed.replaceAll("\\W", "_");
			String id = FilterField.getPrefConstant(FilterField.EDITION, abbr);
			res.add(id);
		}
		return res.toArray(new String[res.size()]);
	}

	public Viewer getViewer() {
		return treeViewer;
	}

	public void setToDefaults() {
		IPreferenceStore store = getPreferenceStore();
		String ids[] = getIds();
		for (String id : ids) {
			store.setToDefault(id);
		}
		initialize();
	}
}
