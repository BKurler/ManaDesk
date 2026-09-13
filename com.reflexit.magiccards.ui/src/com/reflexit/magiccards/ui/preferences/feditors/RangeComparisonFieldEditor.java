/*
 * Contributors:
 *     Rémi Dutil (2026) - created for ManaDesk: Min/Max range field editor
 *                         (Power, Toughness, Converted CC, Online Price and
 *                         Collector's Number now support a real range instead
 *                         of a single =/<=/>= comparison)
 *     Rémi Dutil (2026) - reworked as an operator combo (=, <=, >=, between)
 *                         with a single number field, matching the old
 *                         NumericalComparisonFieldEditor for the basic
 *                         scenarios - "between" is the only case that reveals
 *                         a second number field, for the actual range case
 *     Rémi Dutil (2026) - the second number field is now always visible (just
 *                         disabled unless "between" is selected) instead of
 *                         being shown/hidden, so the dialog no longer resizes
 *                         as the operator changes
 */
package com.reflexit.magiccards.ui.preferences.feditors;

import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

/**
 * A field editor for a numeric filter that covers both the basic single-bound
 * scenarios (=, &lt;=, &gt;=  - one operator combo, one number field, exactly
 * like the old {@link NumericalComparisonFieldEditor}) and a genuine Min..Max
 * range (the "between" operator, which enables a second number field only
 * when chosen - it stays visible but disabled otherwise, so the dialog's
 * size never changes as the operator is switched). Stored as a single
 * preference string {@code "<min>:<max>"}
 * (either side may be empty for "unbounded"); a fully empty value means "no
 * filter" - see {@link #encode()} / {@link #applyValue(String)} for the
 * mapping between the two.
 */
public class RangeComparisonFieldEditor extends FieldEditor {
	private static final String OP_EQ = "=";
	private static final String OP_LE = "<=";
	private static final String OP_GE = ">=";
	private static final String OP_BETWEEN = "between";

	private Composite boundsControl;
	private Combo operationControl;
	private Text bound1Text;
	private Label andLabel;
	private Text bound2Text;
	private boolean isValid = true;
	private String oldValue = "";

	public RangeComparisonFieldEditor(String name, String labelText, Composite parent) {
		init(name, labelText);
		createControl(parent);
	}

	@Override
	protected void adjustForNumColumns(int numColumns) {
		GridData gd = (GridData) this.boundsControl.getLayoutData();
		gd.horizontalSpan = numColumns > 2 ? numColumns - 2 : 1;
	}

	@Override
	protected void doFillIntoGrid(Composite parent, int numColumns) {
		getLabelControl(parent);
		this.operationControl = getOperationControl(parent);
		this.operationControl.setLayoutData(new GridData());
		this.boundsControl = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(3, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		this.boundsControl.setLayout(layout);
		GridData gd = new GridData();
		gd.horizontalAlignment = GridData.FILL;
		gd.grabExcessHorizontalSpace = true;
		this.boundsControl.setLayoutData(gd);
		this.bound1Text = createBoundText(this.boundsControl);
		this.andLabel = new Label(this.boundsControl, SWT.NONE);
		this.andLabel.setText("and");
		this.andLabel.setFont(parent.getFont());
		this.andLabel.setLayoutData(new GridData());
		this.bound2Text = createBoundText(this.boundsControl);
		updateBoundsVisibility();
	}

	protected Combo getOperationControl(Composite parent) {
		if (this.operationControl == null) {
			this.operationControl = new Combo(parent, SWT.READ_ONLY);
			this.operationControl.setFont(parent.getFont());
			this.operationControl.setItems(new String[] { OP_EQ, OP_LE, OP_GE, OP_BETWEEN });
			this.operationControl.select(2); // ">=", same default as the old editor
			this.operationControl.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					updateBoundsVisibility();
					valueChanged();
				}
			});
			this.operationControl.addDisposeListener(new DisposeListener() {
				@Override
				public void widgetDisposed(DisposeEvent event) {
					RangeComparisonFieldEditor.this.operationControl = null;
				}
			});
		} else {
			checkParent(this.operationControl, parent);
		}
		return this.operationControl;
	}

	private String getOperation() {
		return this.operationControl.getText();
	}

	private Text createBoundText(Composite parent) {
		final Text text = new Text(parent, SWT.SINGLE | SWT.BORDER);
		text.setFont(parent.getFont());
		GridData gd = new GridData();
		gd.widthHint = 50;
		text.setLayoutData(gd);
		text.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				valueChanged();
			}
		});
		text.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent event) {
				if (text == RangeComparisonFieldEditor.this.bound1Text)
					RangeComparisonFieldEditor.this.bound1Text = null;
				else if (text == RangeComparisonFieldEditor.this.bound2Text)
					RangeComparisonFieldEditor.this.bound2Text = null;
			}
		});
		return text;
	}

	/**
	 * The "and <second box>" pair stays visible and reserves its layout space
	 * at all times (so the dialog never resizes as the operator changes) -
	 * it's simply disabled when the operator isn't "between".
	 */
	private void updateBoundsVisibility() {
		if (this.andLabel == null || this.andLabel.isDisposed())
			return;
		boolean between = OP_BETWEEN.equals(getOperation());
		this.andLabel.setEnabled(between);
		this.bound2Text.setEnabled(between);
	}

	private boolean checkState() {
		boolean result = isNumberOrEmpty(this.bound1Text)
				&& (!OP_BETWEEN.equals(getOperation()) || isNumberOrEmpty(this.bound2Text));
		if (result) {
			clearErrorMessage();
		} else {
			showErrorMessage("Value must be a whole number, or left empty");
		}
		return result;
	}

	private boolean isNumberOrEmpty(Text text) {
		if (text == null)
			return true;
		String txt = text.getText().trim();
		if (txt.length() == 0)
			return true;
		try {
			Integer.parseInt(txt);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	/**
	 * Encodes the current operator + number field(s) into the stored
	 * {@code "<min>:<max>"} preference value.
	 */
	private String encode() {
		String b1 = this.bound1Text == null ? "" : this.bound1Text.getText().trim();
		String op = getOperation();
		if (OP_BETWEEN.equals(op)) {
			String b2 = this.bound2Text == null ? "" : this.bound2Text.getText().trim();
			if (b1.length() == 0 && b2.length() == 0)
				return "";
			return b1 + ":" + b2;
		}
		if (b1.length() == 0)
			return "";
		if (OP_EQ.equals(op))
			return b1 + ":" + b1;
		if (OP_LE.equals(op))
			return ":" + b1;
		// OP_GE
		return b1 + ":";
	}

	/** Reverse of {@link #encode()}: picks the operator/bound(s) that reproduce {@code value}. */
	private void applyValue(String value) {
		String min = "";
		String max = "";
		if (value != null && value.length() > 0) {
			int idx = value.indexOf(':');
			if (idx >= 0) {
				min = sanitize(value.substring(0, idx).trim());
				max = sanitize(value.substring(idx + 1).trim());
			} else {
				// leftover from the old =/<=/>= comparator format - discard, not a bound
				min = sanitize(value.trim());
				max = min;
			}
		}
		String op;
		String b1 = "";
		String b2 = "";
		if (min.length() > 0 && max.length() > 0) {
			if (min.equals(max)) {
				op = OP_EQ;
				b1 = min;
			} else {
				op = OP_BETWEEN;
				b1 = min;
				b2 = max;
			}
		} else if (min.length() > 0) {
			op = OP_GE;
			b1 = min;
		} else if (max.length() > 0) {
			op = OP_LE;
			b1 = max;
		} else {
			op = OP_GE;
		}
		if (this.operationControl != null)
			this.operationControl.setText(op);
		if (this.bound1Text != null)
			this.bound1Text.setText(b1);
		if (this.bound2Text != null)
			this.bound2Text.setText(b2);
		updateBoundsVisibility();
	}

	/**
	 * A stray non-numeric value here can only be a leftover from the old
	 * =/&lt;=/&gt;= comparator format this field editor replaced - discard it
	 * rather than showing garbage the user would have to notice and clear by
	 * hand.
	 */
	private String sanitize(String value) {
		if (value.length() == 0)
			return value;
		try {
			Integer.parseInt(value);
			return value;
		} catch (NumberFormatException e) {
			return "";
		}
	}

	@Override
	protected void doLoad() {
		if (this.bound1Text != null) {
			String value = getPreferenceStore().getString(getPreferenceName());
			applyValue(value);
			this.oldValue = value;
		}
	}

	@Override
	protected void doLoadDefault() {
		if (this.bound1Text != null) {
			String value = getPreferenceStore().getDefaultString(getPreferenceName());
			applyValue(value);
		}
		valueChanged();
	}

	@Override
	protected void doStore() {
		getPreferenceStore().setValue(getPreferenceName(), encode());
	}

	@Override
	public int getNumberOfControls() {
		return 3;
	}

	@Override
	public boolean isValid() {
		return this.isValid;
	}

	@Override
	protected void refreshValidState() {
		this.isValid = checkState();
	}

	private void valueChanged() {
		setPresentsDefaultValue(false);
		boolean oldState = this.isValid;
		refreshValidState();
		if (this.isValid != oldState) {
			fireStateChanged(IS_VALID, oldState, this.isValid);
		}
		String newValue = encode();
		if (!newValue.equals(this.oldValue)) {
			fireValueChanged(VALUE, this.oldValue, newValue);
			this.oldValue = newValue;
		}
	}

	@Override
	public void setFocus() {
		if (this.bound1Text != null) {
			this.bound1Text.setFocus();
		}
	}

	@Override
	public void setEnabled(boolean enabled, Composite parent) {
		super.setEnabled(enabled, parent);
		getOperationControl(parent).setEnabled(enabled);
		if (this.bound1Text != null)
			this.bound1Text.setEnabled(enabled);
		// bound2/and stay disabled unless both this field and "between" are active
		if (this.andLabel != null)
			this.andLabel.setEnabled(enabled && OP_BETWEEN.equals(getOperation()));
		if (this.bound2Text != null)
			this.bound2Text.setEnabled(enabled && OP_BETWEEN.equals(getOperation()));
	}
}
