package org.ntlab.traceDebugger;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.viewers.TreeNode;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ObjectReference;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Statement;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

public class Variables {
	private static final Variables theInstance = new Variables();
	private Variable rootThisObjData;
	private List<Variable> argsData = new ArrayList<>();
	private List<Variable> allObjData = new ArrayList<>();

	public static Variables getInstance() {
		return theInstance;
	}

	public TreeNode[] getVariablesTreeNodes() {
		TreeNode[] roots = new TreeNode[allObjData.size()];
		if (allObjData.isEmpty()) {
			return roots;
		}
		for (int i = 0; i < allObjData.size(); i++) {
			Variable rootVariableData = allObjData.get(i);
			createVariablesTreeNode(null, roots, i, rootVariableData);
		}
		return roots;
	}

	private void createVariablesTreeNode(TreeNode parentNode, TreeNode[] addingNodes, int index, Variable addingVariableData) {
		TreeNode newNode = new TreeNode(addingVariableData);
		newNode.setParent(parentNode);
		addingNodes[index] = newNode;
		TreeNode[] childNodes = new TreeNode[addingVariableData.getChildren().size()];
		addingNodes[index].setChildren(childNodes);
		for (int i = 0; i < addingVariableData.getChildren().size(); i++) {
			Variable child = addingVariableData.getChildren().get(i);
			createVariablesTreeNode(newNode, childNodes, i, child);
		}
	}

	public List<Variable> getAllObjectDataByMethodExecution(MethodExecution methodExecution) {
		if (methodExecution == null) return new ArrayList<>();			
		List<Statement> statements = methodExecution.getStatements();
		int lastOrder = statements.size() - 1;
		TracePoint tp  = methodExecution.getTracePoint(lastOrder);
		getAllObjectData(methodExecution, tp, false);
		return allObjData;
	}
	
	public List<Variable> getAllObjectDataByTracePoint(TracePoint tp, boolean isReturned) {
		MethodExecution methodExecution = tp.getMethodExecution();
		getAllObjectData(methodExecution, tp, isReturned);
		return allObjData;
	}
	
	public void resetData() {
		rootThisObjData = null;
		argsData.clear();
		allObjData.clear();
	}	
	
	private void getAllObjectData(MethodExecution methodExecution, TracePoint tp, boolean isReturned) {
		resetData();
		getRootThisState(methodExecution, tp, isReturned);		
		getArgsState(methodExecution, tp, isReturned);
		allObjData.add(rootThisObjData);
		for (Variable argData : argsData) {
			allObjData.add(argData);
		}
	}
	
	private void getRootThisState(MethodExecution methodExecution, TracePoint tp, boolean isReturned) {
		String thisObjId = methodExecution.getThisObjId();
		String thisClassName = methodExecution.getThisClassName();
		rootThisObjData = new Variable("this", null, null, thisClassName, thisObjId, tp, isReturned);
		rootThisObjData.createNextHierarchyState();
	}

	private void getArgsState(MethodExecution methodExecution, TracePoint tp, boolean isReturned) {
		// methodExecutionが持つargumentsを取得(ArrayList)し、そのargumentsのサイズも取得(int)
		List<ObjectReference> args = methodExecution.getArguments();
		if (args.size() > 0) {
			IType type = JavaEditorOperator.findIType(methodExecution);
			String methodSignature = methodExecution.getSignature();
			IMethod method = JavaEditorOperator.findIMethod(type, methodSignature);			
			String[] argNames = getParameterNames(method); // 引数のIMethodから仮引数名を取得する
			for (int i = 0; i < args.size(); i++) {
				String argName = (argNames.length == args.size()) ? argNames[i] : "arg" + i; // 少なくとも引数の個数が不一致のときは正しい引数名が取れていない
				ObjectReference arg = args.get(i);
				String argId = arg.getId();
				String argType = arg.getActualType();
				Variable argData = new Variable(argName, null, null, argType, argId, tp, isReturned);
				argData.createNextHierarchyState();
				argsData.add(argData);
			}
		}
	}
	
	private String[] getParameterNames(IMethod method) {
		String[] argNames = new String[0];
		if (method != null) {
			try {
				argNames = method.getParameterNames();
			} catch (JavaModelException e) {
				e.printStackTrace();
			}
		}
		return argNames;
	}	
}
