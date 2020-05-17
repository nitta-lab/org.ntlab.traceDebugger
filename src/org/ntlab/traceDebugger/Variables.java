package org.ntlab.traceDebugger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ArrayUpdate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldUpdate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodInvocation;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ObjectReference;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Statement;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

import com.sun.org.apache.regexp.internal.RE;

public class Variables {
	private static final Variables theInstance = new Variables();
	private List<Variable> roots = new ArrayList<>();
	private List<MyTreeNode> rootTreeNodes = new ArrayList<>();
	private Map<String, List<TracePoint>> containerIdToDifferentialUpdateTracePoints = new HashMap<>(); // 変数差分更新箇所を記憶
	public static final String RETURN_VARIABLE_NAME = "return";

	public static Variables getInstance() {
		return theInstance;
	}

	public List<MyTreeNode> getVariablesTreeNodesList() {
		rootTreeNodes.clear();
		if (roots.isEmpty()) {
			return rootTreeNodes;
		}
		for (int i = 0; i < roots.size(); i++) {
			Variable rootVariableData = roots.get(i);
			createVariablesTreeNodeList(null, rootTreeNodes, i, rootVariableData);
		}
		return rootTreeNodes;
	}

	private void createVariablesTreeNodeList(MyTreeNode parentNode, List<MyTreeNode> addingNodes, int index, Variable addingVariableData) {
		MyTreeNode newNode = new MyTreeNode(addingVariableData);
		newNode.setParent(parentNode);
		addingNodes.add(index, newNode);
		List<MyTreeNode> childNodes = new ArrayList<>();
		addingNodes.get(index).setChildList(childNodes);
		for (int i = 0; i < addingVariableData.getChildren().size(); i++) {
			Variable child = addingVariableData.getChildren().get(i);
			createVariablesTreeNodeList(newNode, childNodes, i, child);
		}
	}
	
	public void updateAllObjectDataByMethodExecution(MethodExecution methodExecution) {
		if (methodExecution == null) return;			
		List<Statement> statements = methodExecution.getStatements();
		int lastOrder = statements.size() - 1;
		TracePoint tp  = methodExecution.getTracePoint(lastOrder);
		updateAllObjectData(null, tp, false);
	}

	public void updateAllObjectDataByTracePoint(TracePoint from, TracePoint to, boolean isReturned) {
		updateAllObjectData(from, to, isReturned);
	}

	private void updateAllObjectData(TracePoint from, TracePoint to, boolean isReturned) {
		resetData();
		if (from != null) updateReturnValue(from, to, isReturned);
		MethodExecution me = to.getMethodExecution();
		updateRootThisState(me, to, isReturned);
		updateArgsState(me, to, isReturned);
	}
	
	private void updateReturnValue(TracePoint from, TracePoint to, boolean isReturned) {
		Statement statement = from.getStatement();
		ObjectReference ref = null;
		MethodExecution me = null;
		if (statement instanceof MethodInvocation) {
			MethodInvocation mi = (MethodInvocation)statement;
			me = mi.getCalledMethodExecution();
			ref = mi.getCalledMethodExecution().getReturnValue();
		} else if (isReturned) {
			me = from.getMethodExecution();
			ref = me.getReturnValue();
		}
		if (ref != null) {
			String returnValueClassName = ref.getActualType();
			if (returnValueClassName.equals("void")) return;
			String returnValueId = ref.getId();
			String thisObjId = me.getThisObjId();
			String thisClassName = me.getThisClassName();
			Variable variable = new Variable(RETURN_VARIABLE_NAME, thisClassName, thisObjId, returnValueClassName, returnValueId, from, isReturned);
			variable.createNextHierarchyState();
			Variable old = roots.get(0);
			if (old.getVariableName().equals(RETURN_VARIABLE_NAME)) {
				roots.set(0, variable);
			} else {
				roots.add(0, variable);	
			}
		}
	}
	
	private void updateRootThisState(MethodExecution methodExecution, TracePoint tp, boolean isReturned) {
		String thisObjId = methodExecution.getThisObjId();
		String thisClassName = methodExecution.getThisClassName();
		Variable variable = new Variable("this", null, null, thisClassName, thisObjId, tp, isReturned);
		roots.add(variable);
		variable.createNextHierarchyState();
	}

	private void updateArgsState(MethodExecution methodExecution, TracePoint tp, boolean isReturned) {
		// methodExecutionが持つargumentsを取得(ArrayList)し、そのargumentsのサイズも取得(int)
		List<ObjectReference> args = methodExecution.getArguments();
		if (args.size() > 0) {
			IType type = JavaElementFinder.findIType(methodExecution);
			String methodSignature = methodExecution.getSignature();
			IMethod method = JavaElementFinder.findIMethod(type, methodSignature);			
			String[] argNames = getParameterNames(method); // 引数のIMethodから仮引数名を取得する
			for (int i = 0; i < args.size(); i++) {
				String argName = (argNames.length == args.size()) ? argNames[i] : "arg" + i; // 少なくとも引数の個数が不一致のときは正しい引数名が取れていない
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
		if (idSet.contains(variable.getValueId())) {
			for (Map.Entry<String, Object> entry : additionalAttributes.entrySet()) {
				variable.addAdditionalAttribute(entry.getKey(), entry.getValue());	
			}			
		}
		for (Variable child : variable.getChildren()) {
			addAdditionalAttributes(child, idSet, additionalAttributes);
		}
	}

	public void addDifferentialUpdatePoint(TracePoint tp) {
		Statement statement = tp.getStatement();
		String containerId = null;
		if (statement instanceof FieldUpdate) {
			FieldUpdate fu = (FieldUpdate)statement;
			containerId = fu.getContainerObjId();
		} else if (statement instanceof ArrayUpdate) {
			ArrayUpdate au = (ArrayUpdate)statement;
			containerId = au.getArrayObjectId();
		}
		if (containerId == null) return;
		List<TracePoint> tracePoints = containerIdToDifferentialUpdateTracePoints.get(containerId);
		if (tracePoints == null) {
			tracePoints = new ArrayList<TracePoint>();
			containerIdToDifferentialUpdateTracePoints.put(containerId, tracePoints);
		}
		tracePoints.add(tp.duplicate());
	}
	
	public void updateForDifferential() {
		for (Variable variable : roots) {
			updateForDifferential(variable);
		}
		containerIdToDifferentialUpdateTracePoints.clear();
	}
	
	public void updateForDifferentialAndReturnValue(TracePoint from, TracePoint to, boolean isReturned) {
		updateForDifferential();
		updateReturnValue(from, to, isReturned);
		Variable variable = roots.get(0);
		if (variable.getVariableName().equals(RETURN_VARIABLE_NAME)) {
			MyTreeNode node = new MyTreeNode(variable);
			Object top = rootTreeNodes.get(0).getValue();
			if (top instanceof Variable && ((Variable)top).getVariableName().equals(RETURN_VARIABLE_NAME)) {
				rootTreeNodes.set(0, node);
			} else {
				rootTreeNodes.add(0, node);
			}
			List<MyTreeNode> childList = new ArrayList<>();
			node.setChildList(childList);
			for (int i = 0; i < variable.getChildren().size(); i++) {
				Variable childVariable = variable.getChildren().get(i);
				createVariablesTreeNodeList(node, childList, i, childVariable);	
			}
		}
	}
	
	private void updateForDifferential(Variable variable) {
		Set<String> containerIdList = containerIdToDifferentialUpdateTracePoints.keySet();
		String containerId = variable.getContainerId();
		if (containerIdList.contains(containerId)) {
			for (TracePoint tp : containerIdToDifferentialUpdateTracePoints.get(containerId)) {
				Statement statement = tp.getStatement();
				if (statement instanceof FieldUpdate) {
					FieldUpdate fu = (FieldUpdate)statement;
					if (variable.getFullyQualifiedVariableName().equals(fu.getFieldName())) {
						updateForDifferentialField(variable, fu.getValueClassName(), fu.getValueObjId(), tp);						
					}
				} else if (statement instanceof ArrayUpdate) {
					ArrayUpdate au = (ArrayUpdate)statement;
					String fullyQualifiedVariableName = variable.getFullyQualifiedVariableName();
					if (fullyQualifiedVariableName.contains("[" + au.getIndex() + "]")) {
						updateForDifferentialField(variable, au.getValueClassName(), au.getValueObjectId(), tp);
					}
				}
			}
		}
		for (Variable child : variable.getChildren()) {
			updateForDifferential(child);
		}
	}
	
	private void updateForDifferentialField(Variable variable, String valueClassName, String valueId, TracePoint lastUpdatePoint) {		
		variable.update(valueClassName, valueId, lastUpdatePoint, false);
		variable.createNextHierarchyState();
		MyTreeNode node = getTreeNodeFor(variable, rootTreeNodes);
		List<MyTreeNode> childList = node.getChildList();
		childList.clear();
		for (int i = 0; i < variable.getChildren().size(); i++) {
			Variable childVariable = variable.getChildren().get(i);
			createVariablesTreeNodeList(node, childList, i, childVariable);	
		}
	}
	
	public void resetData() {
		roots.clear();
		rootTreeNodes.clear();
		containerIdToDifferentialUpdateTracePoints.clear();
	}
	
	private MyTreeNode getTreeNodeFor(Variable variable, List<MyTreeNode> nodes) {
		for (MyTreeNode node : nodes) {
			if (node.getValue().equals(variable)) return node;
			MyTreeNode deep = getTreeNodeFor(variable, node.getChildList());
			if (deep != null) return deep;
		}
		return null;
	}
}
