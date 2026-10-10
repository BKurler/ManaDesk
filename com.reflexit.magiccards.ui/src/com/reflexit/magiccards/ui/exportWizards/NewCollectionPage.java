/*
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: the "New Collection" page.
 *                         Creates a collection under "Collections" - empty or from
 *                         a card list - with the "Unsorted" option. No Sideboard /
 *                         Extra. Importing into an existing collection is
 *                         ImportIntoCollectionPage.
 *     Rémi Dutil (2026) - the collection Type (Standard / For Trade / Wishlist/To Print) is
 *                         chosen first, above Name, and decides the virtual
 *                         flag (Wishlist/To Print = virtual). Unsorted only for
 *                         Standard / For Trade. No Website source.
 */
package com.reflexit.magiccards.ui.exportWizards;

import java.util.EnumMap;
import java.util.Map;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;

import com.reflexit.magiccards.core.model.CollectionType;
import com.reflexit.magiccards.core.model.nav.ModelRoot;
import com.reflexit.magiccards.ui.utils.StatusDots;

public class NewCollectionPage extends AbstractCreateElementPage {
	private Button unsorted;
	private final Map<CollectionType, Button> typeRadios = new EnumMap<>(CollectionType.class);

	public NewCollectionPage(String pageName, IStructuredSelection selection) {
		super(pageName, selection);
	}

	@Override
	protected String typeName() {
		return "collection";
	}

	@Override
	protected ModelRoot.Side side() {
		return ModelRoot.Side.COLLECTION;
	}

	@Override
	protected CollectionType wantCollectionType() {
		for (Map.Entry<CollectionType, Button> e : typeRadios.entrySet()) {
			if (!e.getValue().isDisposed() && e.getValue().getSelection())
				return e.getKey();
		}
		return CollectionType.STANDARD;
	}

	@Override
	protected boolean wantVirtual() {
		return wantCollectionType().isVirtual();
	}

	@Override
	protected boolean wantUnsorted() {
		return unsorted != null && unsorted.isEnabled() && unsorted.getSelection();
	}

	/** Type: one radio per collection type, each with its meaning. */
	@Override
	protected void createLeadingOptions(Group group) {
		Label tl = new Label(group, SWT.NONE);
		tl.setText("Type:");
		tl.setLayoutData(GridDataFactory.swtDefaults().align(SWT.BEGINNING, SWT.BEGINNING).create());
		Composite types = new Composite(group, SWT.NONE);
		GridLayout gl = new GridLayout(2, false);
		gl.marginWidth = 0;
		gl.marginHeight = 0;
		types.setLayout(gl);
		types.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).span(2, 1).create());
		for (CollectionType t : CollectionType.values()) {
			Button radio = new Button(types, SWT.RADIO);
			radio.setText(t.getLabel());
			radio.setSelection(t == CollectionType.STANDARD);
			Label desc = new Label(types, SWT.NONE);
			desc.setText(t.getDescription());
			radio.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					syncUnsorted();
				}
			});
			typeRadios.put(t, radio);
		}
		Label rules = new Label(group, SWT.WRAP);
		rules.setText("Standard and For Trade collections only hold cards you own. A Wishlist/To Print collection is virtual"
				+ " (cards to buy, proxies to print): set a card to Own once bought or printed, then move it to a"
				+ " Standard collection. Only Standard and For Trade collections can be Unsorted.");
		rules.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).span(3, 1).hint(300, SWT.DEFAULT)
				.create());
	}

	@Override
	protected void createTypeSpecificOptions(Group group) {
		unsorted = StatusDots.check(group, StatusDots.UNSORTED,
				"Unsorted - keep the manual card order and do not merge identical cards");
		syncUnsorted();
	}

	/** Only a Standard / For Trade collection can be Unsorted (not Wishlist/To Print). */
	private void syncUnsorted() {
		if (unsorted == null || unsorted.isDisposed())
			return;
		boolean allowed = !wantVirtual();
		if (!allowed)
			unsorted.setSelection(false);
		unsorted.setEnabled(allowed);
	}

	/** No Website source for a collection (see the base class). */
	@Override
	protected boolean offerWebsiteSource() {
		return false;
	}
}
