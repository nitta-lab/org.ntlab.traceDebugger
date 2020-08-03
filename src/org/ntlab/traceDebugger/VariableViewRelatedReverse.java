package org.ntlab.traceDebugger;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodInvocation;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;
import org.ntlab.traceDebugger.Variable.VariableType;
import org.ntlab.traceDebugger.analyzerProvider.VariableUpdatePointFinder;

public class VariableViewRelatedReverse extends VariableView {
	protected IAction jumpAction;
	public static final String ID = "org.ntlab.traceDebugger.variableViewRelatedReverse";
	
	public VariableViewRelatedReverse() {
		// TODO Auto-generated constructor stub
		System.out.println("VariableViewRelatedReverseクラスが生成されたよ!");
	}

	@Override
	public void createPartControl(Composite parent) {
		// TODO Auto-generated method stub
		System.out.println("VariableViewRelatedReverse#createPartControl(Composite)が呼ばれたよ!");
		super.createPartControl(parent);
		TraceDebuggerPlugin.setActiveView(ID, this);
	}
	
	@Override
	public void setFocus() {
		// TODO Auto-generated method stub
		TraceDebuggerPlugin.setActiveView(ID, this);
		viewer.getControl().setFocus();
	}
	
	@Override
	protected void createActions() {
		jumpAction = new Action() {
			public void run() {
				TracePoint tp = null;
				TracePoint before = DebuggingController.getInstance().getCurrentTp();
				VariableType variableType = selectedVariable.getVariableType();
				if (variableType.equals(VariableType.USE_VALUE)) {
					String containerId = selectedVariable.getContainerId();
					String fieldName = selectedVariable.getFullyQualifiedVariableName();
					tp = VariableUpdatePointFinder.getInstance().getPoint(containerId, fieldName, before);
				} else if (variableType.equals(VariableType.USE_RETURN)) {
					String receiverId = selectedVariable.getContainerId();
					String valueId = selectedVariable.getValueId();
					String receiverClassName = selectedVariable.getContainerClassName();
					VariableUpdatePointFinder finder = VariableUpdatePointFinder.getInstance();
					if (receiverClassName.contains("Iterator") || receiverClassName.contains("Itr")
							|| receiverClassName.contains("Collections$UnmodifiableCollection$1")) {
						tp = finder.getIteratorPoint(receiverId);
						if (tp == null) return;
						MethodInvocation mi = ((MethodInvocation)tp.getStatement()); 
						receiverId = mi.getCalledMethodExecution().getThisObjId();
					}
					tp = finder.getDefinitionInvocationPoint(receiverId, valueId, before);
				}
				if (tp == null) return;
				DebuggingController controller = DebuggingController.getInstance();
				controller.jumpToTheTracePoint(tp, false);
			}
		};
		jumpAction.setText("Jump to Definition");
		jumpAction.setToolTipText("Jump to Definition");		
	}
	
	@Override
	protected void createPopupMenu() {
		MenuManager menuMgr = new MenuManager("#PopupMenu");
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			@Override
			public void menuAboutToShow(IMenuManager manager) {
				// 右クリックする度に呼び出される
				VariableType variableType = selectedVariable.getVariableType();
				if (variableType.equals(VariableType.USE_VALUE)) {
					manager.add(jumpAction);
					jumpAction.setText("Jump to Definition");
					jumpAction.setToolTipText("Jump to Definition");
				} else if (variableType.equals(VariableType.USE_RETURN)) {
					manager.add(jumpAction);
					jumpAction.setText("Jump to Addition");
					jumpAction.setToolTipText("Jump to Addition");
				} 
				manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
			}
		});
		Menu menu = menuMgr.createContextMenu(viewer.getControl());
		viewer.getControl().setMenu(menu);
		getSite().registerContextMenu(menuMgr, viewer);
	}
}
