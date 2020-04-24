package org.ntlab.traceDebugger;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

public class RadioButtonDialog extends Dialog {
	private int selectedIndex = 0;
	private String title;
	private String[] texts;
	
	protected RadioButtonDialog(Shell parentShell, String title, String[] texts) {
		super(parentShell);
		this.title = title;
		this.texts = texts;
	}
	
	@Override
	public void create() {
		super.create();
		getShell().setText(title);
	}
	
	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = (Composite)super.createDialogArea(parent);
		Button[] buttons = new Button[texts.length];
		for (int i = 0; i < texts.length; i++) {
			final int index = i;
			buttons[i] = new Button(parent, SWT.RADIO);
//			buttons[i].setLayoutData(new GridData(GridData.FILL_BOTH));
			buttons[i].setAlignment(SWT.CENTER);
			buttons[i].setText(texts[i]);
			buttons[i].addSelectionListener(new SelectionListener() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					selectedIndex = index;
				}
				@Override
				public void widgetDefaultSelected(SelectionEvent e) {
				}
			});
		}
		return composite;
	}
	
	public String getValue() {
		return texts[selectedIndex];
	}
}
