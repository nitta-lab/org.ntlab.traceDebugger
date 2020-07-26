package org.ntlab.traceDebugger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ArrayUpdate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldAccess;
import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldUpdate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodInvocation;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ObjectReference;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Statement;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;
import org.ntlab.traceDebugger.Variable.VariableType;

public class Variables {
	private static final Variables theInstance = new Variables();
	private List<Variable> roots = new ArrayList<>();
	private List<MyTreeNode> rootTreeNodes = new ArrayList<>();
	private Map<String, List<TracePoint>> containerIdToDifferentialUpdateTracePoints = new HashMap<>(); // 変数差分更新箇所を記憶
	public static final String VARIABLE_TYPE_KEY = "variableType";
	
	public static Variables getInstance() {
		return theInstance;
	}

	public List<MyTreeNode> getVariablesTreeNodesList() {
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
		updateAllObjectData(null, tp, false, null);
	}

	public void updateAllObjectDataByTracePoint(TracePoint from, TracePoint to, boolean isReturned, TracePoint before) {
		updateAllObjectData(from, to, isReturned, before);
	}

	private void updateAllObjectData(TracePoint from, TracePoint to, boolean isReturned, TracePoint before) {
		resetData();
		MethodExecution me = to.getMethodExecution();
		updateRootThisState(me, to, isReturned, before);
		updateArgsState(me, to, isReturned, before);		
		for (int i = 0; i < roots.size(); i++) {
			Variable rootVariableData = roots.get(i);
			createVariablesTreeNodeList(null, rootTreeNodes, i, rootVariableData);
		}
		createSpecialVariables(from, to, isReturned);	
	}
	
	private void updateRootThisState(MethodExecution methodExecution, TracePoint tp, boolean isReturned, TracePoint before) {
		String thisObjId = methodExecution.getThisObjId();
		String thisClassName = methodExecution.getThisClassName();
		if (before == null) before = tp;
		Variable variable = new Variable("this", null, null, thisClassName, thisObjId, before, isReturned);
		roots.add(variable);
		variable.createNextHierarchyState();
	}

	private void updateArgsState(MethodExecution methodExecution, TracePoint tp, boolean isReturned, TracePoint before) {
		// methodExecutionが持つargumentsを取得(ArrayList)し、そのargumentsのサイズも取得(int)
		List<ObjectReference> args = methodExecution.getArguments();
		if (before == null) before = tp;
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
				Variable argData = new Variable(argName, null, null, argType, argId, before, isReturned, VariableType.PARAMETER);
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
		VariableType variableType = variable.getVariableType();
		String id = variableType.isContainerSide() ? variable.getContainerId() : variable.getValueId();
		if (id.equals("0")) return;
		if (idSet.contains(id)) {
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
		
	public void updateForDifferential(TracePoint from, TracePoint to, boolean isReturned) {
		updateForDifferential();
		resetSpecialValues();
		createSpecialVariables(from, to, isReturned);
	}
	
	private void updateForDifferential() {
		for (Variable variable : roots) {
			updateForDifferential(variable, new HashSet<String>());
		}
		containerIdToDifferentialUpdateTracePoints.clear();
	}

	private void updateForDifferential(Variable variable, Set<String> hasCheckedObjectIdSet) {
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
		HashSet<String> hasCheckedObjectIdSetOnNext = new HashSet<>(hasCheckedObjectIdSet);
		hasCheckedObjectIdSetOnNext.add(variable.getContainerId());
		for (Variable child : variable.getChildren()) {
			if (hasCheckedObjectIdSetOnNext.contains(child.getContainerId())) continue;
			updateForDifferential(child, hasCheckedObjectIdSetOnNext);
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
	
	private void createSpecialVariables(TracePoint from, TracePoint to, boolean isReturned) {
//		List<Variable> list = new ArrayList<>();
		List<Variable> specialVariablesOfUseSide = new ArrayList<>();
		List<Variable> specialVariablesDefSide = new ArrayList<>();
		String parentNodeNameOfUseSide = null;
		String parentNodeNameOfDefSide = null;
		if (from != null) {
			// 実行直後のuse要素
			Statement fromStatement = from.getStatement();
			if (fromStatement instanceof FieldAccess) {
				FieldAccess fa = (FieldAccess)fromStatement;
				String containerClassName = fa.getContainerClassName();
				String containerObjId = fa.getContainerObjId();
				String valueClassName = fa.getValueClassName();
				String valueObjId = fa.getValueObjId();
				Variable container = new Variable(Variable.CONTAINER_VARIABLE_NAME, containerClassName, containerObjId, valueClassName, valueObjId, from, isReturned, VariableType.USE_CONTAINER);
				Variable value = new Variable(Variable.VALUE_VARIABLE_NAME, containerClassName, containerObjId, valueClassName, valueObjId, from, isReturned, VariableType.USE_VALUE);
				specialVariablesOfUseSide.add(container);
				specialVariablesOfUseSide.add(value);
				parentNodeNameOfUseSide = "PreviousFieldAccess:" + fa.getFieldName();
			} else if (fromStatement instanceof MethodInvocation) {
				MethodInvocation mi = (MethodInvocation)fromStatement;
				MethodExecution calledME = mi.getCalledMethodExecution();
				ObjectReference returnValue = calledME.getReturnValue();
				if (returnValue != null) {
					String containerClassName = calledME.getThisClassName();
					String containerObjId = calledME.getThisObjId();
					String valueClassName = returnValue.getActualType();
					String valueObjId = returnValue.getId();
					Variable receiver = new Variable(Variable.RECEIVER_VARIABLE_NAME, containerClassName, containerObjId, valueClassName, valueObjId, from, isReturned, VariableType.USE_RECEIVER);
					Variable returned = new Variable(Variable.RETURN_VARIABLE_NAME, containerClassName, containerObjId, valueClassName, valueObjId, from, isReturned, VariableType.USE_RETURN);
					specialVariablesOfUseSide.add(receiver);
					specialVariablesOfUseSide.add(returned);
					if (calledME.isConstructor()) {
						parentNodeNameOfUseSide = "ReturnConstructor:" + calledME.getSignature();
					} else {
						parentNodeNameOfUseSide = "ReturnMethod:" + calledME.getSignature();	
					}
				}
			}			
		}

		if (to != null) {
			// 実行直前のdef要素
			Statement toStatement = to.getStatement();
			if (toStatement instanceof FieldUpdate) {
				FieldUpdate fu = (FieldUpdate)toStatement;
				String containerClassName = fu.getContainerClassName();
				String containerObjId = fu.getContainerObjId();
				String valueClassName = fu.getValueClassName();
				String valueObjId = fu.getValueObjId();
				Variable container = new Variable(Variable.CONTAINER_VARIABLE_NAME, containerClassName, containerObjId, valueClassName, valueObjId, to, isReturned, VariableType.DEF_CONTAINER);
				Variable value = new Variable(Variable.VALUE_VARIABLE_NAME, containerClassName, containerObjId, valueClassName, valueObjId, to, isReturned, VariableType.DEF_VALUE);
				specialVariablesDefSide.add(container);
				specialVariablesDefSide.add(value);
				parentNodeNameOfDefSide = "NextUpdate:" + fu.getFieldName();
			} else if (toStatement instanceof MethodInvocation) {
				MethodInvocation mi = (MethodInvocation)toStatement;
				MethodExecution calledME = mi.getCalledMethodExecution();
				List<ObjectReference> args = calledME.getArguments();
				if (args.size() == 1) {
					ObjectReference argObj = args.get(0);
					String containerClassName = calledME.getThisClassName();
					String containerObjId = calledME.getThisObjId();
					String valueClassName = argObj.getActualType();
					String valueObjId = argObj.getId();
					Variable receiver = new Variable(Variable.RECEIVER_VARIABLE_NAME, containerClassName, containerObjId, valueClassName, valueObjId, to, isReturned, VariableType.DEF_RECEIVER);
					Variable arg = new Variable(Variable.ARG_VARIABLE_NAME, containerClassName, containerObjId, valueClassName, valueObjId, to, isReturned, VariableType.DEF_ARG);
					specialVariablesDefSide.add(receiver);
					specialVariablesDefSide.add(arg);
					if (calledME.isConstructor()) {
						parentNodeNameOfDefSide = "NextConstructor:" + calledME.getSignature();
					} else {
						parentNodeNameOfDefSide = "NextMethod:" + calledME.getSignature();	
					}
				}
			} 
		}
		if (parentNodeNameOfUseSide != null) {
			setSpecialVariableNodes(parentNodeNameOfUseSide, specialVariablesOfUseSide);
		}	
		if (parentNodeNameOfDefSide != null) {
			setSpecialVariableNodes(parentNodeNameOfDefSide, specialVariablesDefSide);
		}
		
//		for (Variable variable : list) {
//			variable.createNextHierarchyState();
//			roots.add(0, variable);
//			MyTreeNode variableNode = new MyTreeNode(variable);
////			rootTreeNodes.add(0, variableNode);
//			List<MyTreeNode> childList = new ArrayList<>();
//			variableNode.setChildList(childList);
//			for (int i = 0; i < variable.getChildren().size(); i++) {
//				Variable childVariable = variable.getChildren().get(i);
//				createVariablesTreeNodeList(variableNode, childList, i, childVariable);	
//			}
//		}
	}
	
	private void setSpecialVariableNodes(String parentNodeName, List<Variable> specialVariables) {
		MyTreeNode parentNode = new MyTreeNode(parentNodeName);
		rootTreeNodes.add(0, parentNode);
		MyTreeNode[] children = new MyTreeNode[specialVariables.size()];
		for (int i = 0; i < specialVariables.size(); i++) {
			Variable variable = specialVariables.get(i);
			variable.createNextHierarchyState();
			roots.add(0, variable);
			MyTreeNode variableNode = new MyTreeNode(variable);
			children[i] = variableNode;
			variableNode.setParent(parentNode);
			createChildNodesOfSpecialVariableNode(variableNode);				
		}
		parentNode.setChildren(children);		
	}
	
	private void createChildNodesOfSpecialVariableNode(MyTreeNode variableNode) {
		List<MyTreeNode> childList = new ArrayList<>();
		variableNode.setChildList(childList);
		Variable variable = (Variable)variableNode.getValue();
		for (int i = 0; i < variable.getChildren().size(); i++) {
			Variable childVariable = variable.getChildren().get(i);
			createVariablesTreeNodeList(variableNode, childList, i, childVariable);	
		}
	}
	
	public void resetData() {
		roots.clear();
		rootTreeNodes.clear();
		containerIdToDifferentialUpdateTracePoints.clear();
	}
	
	private void resetSpecialValues() {
		for (int i = roots.size() - 1; i >= 0; i--) {
			Variable root = roots.get(i);
			String variableName = root.getVariableName();
			if (variableName.equals(Variable.CONTAINER_VARIABLE_NAME)
				|| variableName.equals(Variable.VALUE_VARIABLE_NAME)
				|| variableName.equals(Variable.RECEIVER_VARIABLE_NAME)
				|| variableName.equals(Variable.ARG_VARIABLE_NAME)
				|| variableName.equals(Variable.RETURN_VARIABLE_NAME)) {
				roots.remove(i);
			}
		}
		for (int i = rootTreeNodes.size() - 1; i >= 0; i--) {
			MyTreeNode node = rootTreeNodes.get(i);
			if (node.getValue() instanceof String) {
				rootTreeNodes.remove(i);
			}
		}
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
