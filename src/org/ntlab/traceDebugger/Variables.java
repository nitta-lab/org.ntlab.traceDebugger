package org.ntlab.traceDebugger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
	private List<Variable> roots = new ArrayList<>();

	public static Variables getInstance() {
		return theInstance;
	}

	public TreeNode[] getVariablesTreeNodes() {
		TreeNode[] rootNodes = new TreeNode[roots.size()];
		if (roots.isEmpty()) {
			return rootNodes;
		}
		for (int i = 0; i < roots.size(); i++) {
			Variable rootVariableData = roots.get(i);
			createVariablesTreeNode(null, rootNodes, i, rootVariableData);
		}
		return rootNodes;
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

	public void updateAllObjectDataByMethodExecution(MethodExecution methodExecution) {
		if (methodExecution == null) return;			
		List<Statement> statements = methodExecution.getStatements();
		int lastOrder = statements.size() - 1;
		TracePoint tp  = methodExecution.getTracePoint(lastOrder);
		updateAllObjectData(methodExecution, tp, false);
	}
	
	public void updateAllObjectDataByTracePoint(TracePoint tp, boolean isReturned) {
		MethodExecution methodExecution = tp.getMethodExecution();
		updateAllObjectData(methodExecution, tp, isReturned);
	}
	
	public void resetData() {
		roots.clear();
	}	
	
	private void updateAllObjectData(MethodExecution methodExecution, TracePoint tp, boolean isReturned) {
		resetData();
		updateRootThisState(methodExecution, tp, isReturned);		
		updateArgsState(methodExecution, tp, isReturned);
	}
	
	private void updateRootThisState(MethodExecution methodExecution, TracePoint tp, boolean isReturned) {
		String thisObjId = methodExecution.getThisObjId();
		String thisClassName = methodExecution.getThisClassName();
		Variable variable = new Variable("this", null, null, thisClassName, thisObjId, tp, isReturned);
		roots.add(variable);
		variable.createNextHierarchyState();
	}

	private void updateArgsState(MethodExecution methodExecution, TracePoint tp, boolean isReturned) {
		// methodExecution������arguments���擾(ArrayList)���A����arguments�̃T�C�Y���擾(int)
		List<ObjectReference> args = methodExecution.getArguments();
		if (args.size() > 0) {
			IType type = JavaEditorOperator.findIType(methodExecution);
			String methodSignature = methodExecution.getSignature();
			IMethod method = JavaEditorOperator.findIMethod(type, methodSignature);			
			String[] argNames = getParameterNames(method); // ������IMethod���牼���������擾����
			for (int i = 0; i < args.size(); i++) {
				String argName = (argNames.length == args.size()) ? argNames[i] : "arg" + i; // ���Ȃ��Ƃ������̌����s��v�̂Ƃ��͐����������������Ă��Ȃ�
				ObjectReference arg = args.get(i);
				String argId = arg.getId();
				String argType = arg.getActualType();
				Variable argData = new Variable(argName, null, null, argType, argId, tp, isReturned);
				argData.createNextHierarchyState();
				roots.add(argData);
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
	
	public void addAdditionalAttributes(final Set<String> idSet, final Map<String, Object> additionalAttributes) {
		for (Variable root : roots) {
			addAdditionalAttributes(root, idSet, additionalAttributes);
		}
	}
	
	private void addAdditionalAttributes(Variable variable, final Set<String> idSet, final Map<String, Object> additionalAttributes) {
		if (variable == null) return;
		if (idSet.contains(variable.getId())) {
			for (Map.Entry<String, Object> entry : additionalAttributes.entrySet()) {
				variable.addAdditionalAttribute(entry.getKey(), entry.getValue());	
			}			
		}
		for (Variable child : variable.getChildren()) {
			addAdditionalAttributes(child, idSet, additionalAttributes);
		}
	}
}
