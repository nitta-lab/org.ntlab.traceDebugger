package org.ntlab.traceDebugger.analyzerProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.viewers.TreeNode;
import org.eclipse.ui.editors.text.FileDocumentProvider;
import org.eclipse.ui.part.FileEditorInput;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ArrayAccess;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ArrayCreate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldAccess;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodInvocation;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Reference;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Statement;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;
import org.ntlab.traceDebugger.JavaEditorOperator;
import org.ntlab.traceDebugger.JavaElementFinder;
import org.ntlab.traceDebugger.TraceDebuggerPlugin;

public class DeltaMarkerManager {
	private Map<String, List<IMarker>> markerIdToMarkers = new HashMap<>();
	private List<IMarker> markersByOrder = new ArrayList<>();
	private MethodExecution coordinator;
	private TracePoint relatedPoint;
	private Reference relatedPointReference;
	private DeltaRelatedAliasCollector aliasCollector;
	public static final String BOTTOM_DELTA_MARKER = "org.ntlab.traceDebugger.bottomDeltaMarker";
	public static final String COORDINATOR_DELTA_MARKER = "org.ntlab.traceDebugger.coordinatorDeltaMarker";
	public static final String SRC_SIDE_DELTA_MARKER = "org.ntlab.traceDebugger.srcSideDeltaMarker";
	public static final String DST_SIDE_DELTA_MARKER = "org.ntlab.traceDebugger.dstSideDeltaMarker";
	public static final String DELTA_MARKER_ATR_DATA = "data";
	public static final String DELTA_MARKER_ATR_OBJECT_ID = "objectId";
	public static final String DELTA_MARKER_ATR_OBJECT_TYPE = "objectType";
	public static final String DELTA_MARKER_ATR_ALIAS_TYPE = "aliasType";
	
	public DeltaMarkerManager(MethodExecution coordinator, TracePoint relatedPoint, Reference relatedPointReference, DeltaRelatedAliasCollector aliasCollector) {
		this.coordinator = coordinator;
		this.relatedPoint = relatedPoint;
		this.relatedPointReference = relatedPointReference;
		this.aliasCollector = aliasCollector;
	}
	
	public Map<String, List<IMarker>> getMarkers() {
		return markerIdToMarkers;
	}
	
	public List<IMarker> getMarkersByOrder() {
		return markersByOrder;
	}
	
	public TreeNode[] getMarkerTreeNodes() {
		TreeNode[] roots = new TreeNode[] {
				new TreeNode(""),
				new TreeNode(TraceDebuggerPlugin.isJapanese() ? "�ڋ߉ߒ�" : "Approach"),
				new TreeNode("")
		};
		List<IMarker> markers;
		markers = markerIdToMarkers.get(COORDINATOR_DELTA_MARKER);
		roots[0] = new TreeNode(markers.get(0));
		List<IMarker> dstSideMarkers = markerIdToMarkers.get(DST_SIDE_DELTA_MARKER);
		List<IMarker> srcSideMarkers = markerIdToMarkers.get(SRC_SIDE_DELTA_MARKER);
		int dstSideMarkersSize = (dstSideMarkers != null) ? dstSideMarkers.size() : 0;
		int srcSideMarkersSize = (srcSideMarkers != null) ? srcSideMarkers.size() : 0;
		TreeNode[] children = new TreeNode[dstSideMarkersSize + srcSideMarkersSize];
		if (dstSideMarkers != null) {
			for (int i = 0; i < dstSideMarkers.size(); i++) {
				children[i] = new TreeNode(dstSideMarkers.get(i));
			}
		}
		if (srcSideMarkers != null) {
			for (int i = 0; i < srcSideMarkers.size(); i++) {
				children[dstSideMarkersSize + i] = new TreeNode(srcSideMarkers.get(i));
			}
		}
		roots[1].setChildren(children);
		markers = markerIdToMarkers.get(BOTTOM_DELTA_MARKER);
		roots[2] = new TreeNode(markers.get(0));		
		return roots;
	}
	
	public IMarker getCoordinatorDeltaMarker() {
		List<IMarker> markers = markerIdToMarkers.get(COORDINATOR_DELTA_MARKER);
		if (markers == null || markers.isEmpty()) return null;
		return markers.get(0);		
	}
	
	public IMarker getBottomDeltaMarker() {
		List<IMarker> markers = markerIdToMarkers.get(BOTTOM_DELTA_MARKER);
		if (markers == null || markers.isEmpty()) return null;
		return markers.get(0);
	}

	public static MethodExecution getMethodExecution(IMarker deltaMarker) {
		try {
			Object data = deltaMarker.getAttribute(DELTA_MARKER_ATR_DATA);
			if (data instanceof MethodExecution) {
				return (MethodExecution) data;
			} else if (data instanceof TracePoint) {
				TracePoint tp = (TracePoint)data;
				return tp.getMethodExecution();
			} else if (data instanceof Alias) {
				Alias alias = (Alias)data;
				return alias.getMethodExecution();
			}
		} catch (CoreException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static TracePoint getTracePoint(IMarker deltaMarker) {
		try {
			Object data = deltaMarker.getAttribute(DELTA_MARKER_ATR_DATA);
			if (data instanceof MethodExecution) {
				return ((MethodExecution)data).getEntryPoint();
			} else if (data instanceof TracePoint) {
				return (TracePoint)data;
			} else if (data instanceof Alias) {
				return ((Alias)data).getOccurrencePoint();
			}
		} catch (CoreException e) {
			e.printStackTrace();
		}
		return null;
	}

	public void createMarkerAndOpenJavaFileForAll() {
		String msg = TraceDebuggerPlugin.isJapanese() ? "�J�n���_" : "InitialPoint";
		markAndOpenJavaFileForCoordinator(coordinator, msg, DeltaMarkerManager.COORDINATOR_DELTA_MARKER);
		List<Alias> dstSideAliases = new ArrayList<>(aliasCollector.getDstSideRelatedAliases());
		List<Alias> srcSideAliases = new ArrayList<>(aliasCollector.getSrcSideRelatedAliases());
		List<List<Alias>> relatedAliasesList = new ArrayList<>();
		relatedAliasesList.add(dstSideAliases);
		relatedAliasesList.add(srcSideAliases);
		String[] messagesTemplates = TraceDebuggerPlugin.isJapanese() ? new String[]{"�Q�Ɛ摤%03d", "�Q�ƌ���%03d"}
																		: new String[]{"ReferredSide%03d", "ReferringSide%03d"};
		String[] markerIDList = {DST_SIDE_DELTA_MARKER, SRC_SIDE_DELTA_MARKER};
		for (int i = 0; i < relatedAliasesList.size(); i++) {
			List<Alias> relatedAliases = relatedAliasesList.get(i);
			Collections.reverse(relatedAliases);
			int cnt = 1;
			for (Alias alias : relatedAliases) {
				String message = String.format(messagesTemplates[i], cnt++);
				markAndOpenJavaFileForAlias(alias, message, markerIDList[i]);
			}
		}
		msg = TraceDebuggerPlugin.isJapanese() ? "�Q�Ǝ��_" : "RelatedPoint";
		markAndOpenJavaFileForCreationPoint(relatedPoint, relatedPointReference, msg, DeltaMarkerManager.BOTTOM_DELTA_MARKER);
	}
	
	private void markAndOpenJavaFileForAlias(Alias alias, String message, String markerId) {
		IFile file = JavaElementFinder.findIFile(alias.getMethodExecution());
		if (file != null) {
			IMarker marker = addMarkerForAlias(alias, file, message, markerId);
			JavaEditorOperator.markAndOpenJavaFile(marker);			
		}
	}

	private void markAndOpenJavaFileForCreationPoint(TracePoint creationPoint, Reference reference, String message, String markerId) {
		MethodExecution me = creationPoint.getMethodExecution();
		String objectId = reference.getSrcObjectId() + " -> " + reference.getDstObjectId();
		String objectType = reference.getSrcClassName() + " -> " + reference.getDstClassName();
		IFile file = JavaElementFinder.findIFile(me);
		if (file != null) {
			IMarker marker = addMarkerForCreationPoint(creationPoint, file, message, objectId, objectType, markerId);
			JavaEditorOperator.markAndOpenJavaFile(marker);			
		}
	}
	
	private void markAndOpenJavaFileForCoordinator(MethodExecution methodExecution, String message, String markerId) {
		IFile file = JavaElementFinder.findIFile(methodExecution);
		if (file != null) {
			String objectId = methodExecution.getThisObjId();
			String objectType = methodExecution.getThisClassName();
			IMarker marker = addMarkerForCoordinator(methodExecution, file, message, objectId, objectType, markerId);
			JavaEditorOperator.markAndOpenJavaFile(marker);			
		}
	}

	private IMarker addMarkerForAlias(Alias alias, IFile file, String message, String markerId) {		
		try {
			IMarker marker = file.createMarker(markerId);
			Map<String, Object> attributes = new HashMap<>();
			setAttributesForAlias(attributes, alias, file, markerId);
			if (!(attributes.containsKey(IMarker.LINE_NUMBER))) {
				attributes.put(IMarker.LINE_NUMBER, alias.getLineNo() + " (no marker)");
			}
			attributes.put(IMarker.MESSAGE, message);
			attributes.put(IMarker.TRANSIENT, true);
			attributes.put(DELTA_MARKER_ATR_DATA, alias);
			attributes.put(DELTA_MARKER_ATR_OBJECT_ID, alias.getObjectId());
			attributes.put(DELTA_MARKER_ATR_OBJECT_TYPE, alias.getObjectType());
			attributes.put(DELTA_MARKER_ATR_ALIAS_TYPE, alias.getAliasType());			
			marker.setAttributes(attributes);
			addMarker(markerId, marker);
			return marker;
		} catch (CoreException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	private IMarker addMarkerForCreationPoint(TracePoint tp, IFile file, String message, String objectId, String objectType, String markerId) {
		try {
			MethodExecution me = tp.getMethodExecution();
			int lineNo = tp.getStatement().getLineNo();
			IMarker marker = file.createMarker(markerId);
			Map<String, Object> attributes = new HashMap<>();
			setAttributesForCreationPoint(attributes, me, file, lineNo, markerId);
			if (!(attributes.containsKey(IMarker.LINE_NUMBER))) attributes.put(IMarker.LINE_NUMBER, tp.getStatement().getLineNo());
			attributes.put(IMarker.MESSAGE, message);
			attributes.put(IMarker.TRANSIENT, true);
			attributes.put(DELTA_MARKER_ATR_DATA, tp);
			attributes.put(DELTA_MARKER_ATR_OBJECT_ID, objectId);
			attributes.put(DELTA_MARKER_ATR_OBJECT_TYPE, objectType);
			marker.setAttributes(attributes);
			addMarker(markerId, marker);
			return marker;
		} catch (CoreException e) {
			e.printStackTrace();
		}
		return null;
	}
		
	private IMarker addMarkerForCoordinator(MethodExecution me, IFile file, String message, String objectId, String objectType, String markerId) {
		try {
			IMarker marker = file.createMarker(markerId);
			Map<String, Object> attributes = new HashMap<>();
			setAttributesForCoordinator(attributes, me, file);
			attributes.put(IMarker.MESSAGE, message);
			attributes.put(IMarker.TRANSIENT, true);
			attributes.put("data", me);
			attributes.put("objectId", objectId);
			attributes.put("objectType", objectType);
			marker.setAttributes(attributes);
			addMarker(markerId, marker);
			return marker;
		} catch (CoreException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	private void addMarker(String markerId, IMarker marker) {
		List<IMarker> markerList = markerIdToMarkers.get(markerId);
		if (markerList == null) {
			markerList = new ArrayList<IMarker>();
			markerIdToMarkers.put(markerId, markerList);
		}
		markerList.add(marker);
		markersByOrder.add(marker);
	}

	private void setAttributesForAlias(final Map<String, Object> attributes, Alias alias, IFile file, String markerId) {
		try {
			FileEditorInput input = new FileEditorInput(file);
			FileDocumentProvider provider = new FileDocumentProvider();
			provider.connect(input);			
			ASTParser parser = ASTParser.newParser(AST.JLS10);
			MethodExecution methodExecution = alias.getMethodExecution();
			IType type = JavaElementFinder.findIType(methodExecution);					
			IMethod method = JavaElementFinder.findIMethod(methodExecution, type);
			if (method != null) {
				ICompilationUnit unit = method.getCompilationUnit();
				parser.setSource(unit);
				ASTNode node = parser.createAST(new NullProgressMonitor());
				if (node instanceof CompilationUnit) {
					CompilationUnit cUnit = (CompilationUnit)node;
					ASTVisitor visitor = createVisitor(alias, method, cUnit, attributes);
					if (visitor != null) {
						node.accept(visitor);
					}
				}
			}
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}
	
	private ASTVisitor createVisitor(final Alias alias, final IMethod method, final CompilationUnit cUnit, final Map<String, Object> attributes) {
		class MyASTVisitor extends ASTVisitor {
			public boolean preVisit2(ASTNode node) {
				if (attributes.containsKey(IMarker.LINE_NUMBER)) return false;
				if (node instanceof org.eclipse.jdt.core.dom.MethodDeclaration) {
					try {
						// note: �Y�����郁�\�b�h�Ăяo���̏ꍇ�̂ݎq�m�[�h�̒T���𑱍s
						String src1 = node.toString().replaceAll(" ", "");
						src1 = src1.substring(0, src1.lastIndexOf("\n"));
						String src1Head = src1.substring(0, src1.indexOf(")") + 1);
						src1Head = src1Head.replaceAll(" |\t|\r|\n", "");
						src1Head = src1Head.substring(src1Head.indexOf("*/") + 2); // note: �擪�̃R�����g�����O��
						String src2 = method.getSource().replaceAll(" |\t|\r|\n", "");
						return src2.contains(src1Head);
					} catch (JavaModelException e) {
						e.printStackTrace();
						return false;
					}
				}
				return true;
			}
		}
		ASTVisitor visitor = new MyASTVisitor();
		try {
			Statement statement = alias.getOccurrencePoint().getStatement();
			final String source = method.getCompilationUnit().getSource();
			switch (alias.getAliasType()) {
			// note: ���\�b�h�ւ̓���
			case FORMAL_PARAMETER: {
				final int index = alias.getIndex();
				visitor = new MyASTVisitor() {
					@Override
					public boolean visit(org.eclipse.jdt.core.dom.MethodDeclaration node) {
						Object obj = node.parameters().get(index);
						if (obj instanceof SingleVariableDeclaration) {
							int lineNo = cUnit.getLineNumber(node.getStartPosition());
							SingleVariableDeclaration parameter = (SingleVariableDeclaration)obj;
							int start = parameter.getStartPosition();
							int end = start + parameter.getLength();
							attributes.put(IMarker.CHAR_START, start);
							attributes.put(IMarker.CHAR_END, end);
							attributes.put(IMarker.LINE_NUMBER, lineNo);
						}
						return false;
					}
				};
				return visitor;
			}
			case THIS: {
				if (statement instanceof FieldAccess) {
					final FieldAccess fa = (FieldAccess)statement;
					visitor = new MyASTVisitor() {						
						@Override
						public boolean visit(org.eclipse.jdt.core.dom.FieldAccess node) {
							int lineNo = cUnit.getLineNumber(node.getStartPosition());
							if (lineNo != alias.getLineNo()) return true;
							String name1 = node.getName().toString();
							String name2 = fa.getFieldName();
							name2 = name2.substring(name2.lastIndexOf(".") + 1);
							if (!(name1.equals(name2))) return true;
							int start = node.getStartPosition();
							int end = start;
							if (source.startsWith("this.", start)) {
								end = start + "this".length();
							}
							attributes.put(IMarker.CHAR_START, start);
							attributes.put(IMarker.CHAR_END, end);
							attributes.put(IMarker.LINE_NUMBER, lineNo);
							return false;
						}
						@Override
						public boolean visit(org.eclipse.jdt.core.dom.ArrayAccess node) {
							int lineNo = cUnit.getLineNumber(node.getStartPosition());
							if (lineNo != alias.getLineNo()) return true;
							int start = node.getStartPosition();
							int end = start;
							if (source.startsWith("this.", start)) {
								end = start + "this".length();
							}
							attributes.put(IMarker.CHAR_START, start);
							attributes.put(IMarker.CHAR_END, end);
							attributes.put(IMarker.LINE_NUMBER, lineNo);
							return true;
						}
						@Override
						public boolean visit(org.eclipse.jdt.core.dom.SimpleName node) {
							// note: ���\�b�h�Ăяo���̃��V�[�o���t�B�[���h�̏ꍇ�̓t�B�[���h�A�N�Z�X�̃m�[�h���Ɨ��Ȃ�������ɂ���Œʂ�
							int lineNo = cUnit.getLineNumber(node.getStartPosition());
							if (lineNo != alias.getLineNo()) return true;
							if (!(node.getParent() instanceof org.eclipse.jdt.core.dom.MethodInvocation)) return true;
							String name1 = node.toString();
							String name2 = fa.getFieldName();
							name2 = name2.substring(name2.lastIndexOf(".") + 1);
							if (!(name1.equals(name2))) return true;
							int start = node.getStartPosition();
							int end = start;
							if (source.startsWith("this.", start)) {
								end = start + "this".length();
							}
							attributes.put(IMarker.CHAR_START, start);
							attributes.put(IMarker.CHAR_END, end);
							attributes.put(IMarker.LINE_NUMBER, lineNo);
							return false;
						}
						@Override
						public boolean visit(org.eclipse.jdt.core.dom.ClassInstanceCreation node) {
							// note: �R���X�g���N�^�Ăяo���̈�����this������ꍇ
							int lineNo = cUnit.getLineNumber(node.getStartPosition());
							if (lineNo != alias.getLineNo()) return true;
							String name1 = node.toString();
							name1 = name1.substring(name1.indexOf("("));
							String name2 = fa.getFieldName();
							name2 = name2.substring(name2.lastIndexOf(".") + 1);
							if (!(name1.contains(name2))) return true;
							int start = node.getStartPosition();
							int end = start;
							if (source.startsWith("this.", start)) {
								end = start + "this".length();
							}
							attributes.put(IMarker.CHAR_START, start);
							attributes.put(IMarker.CHAR_END, end);
							attributes.put(IMarker.LINE_NUMBER, lineNo);
							return false;
						}
						@Override
						public boolean visit(org.eclipse.jdt.core.dom.ReturnStatement node) {
							int lineNo = cUnit.getLineNumber(node.getStartPosition());
							if (lineNo != alias.getLineNo()) return true;
							Expression expression = node.getExpression();
							int start = node.getStartPosition();
							if (expression != null) {
								start = node.getExpression().getStartPosition();
							}
							int end = start;
							if (source.startsWith("this.", start)) {
								end = start + "this".length();
							}
							attributes.put(IMarker.CHAR_START, start);
							attributes.put(IMarker.CHAR_END, end);
							attributes.put(IMarker.LINE_NUMBER, lineNo);
							return false;
						}
						@Override
						public boolean visit(org.eclipse.jdt.core.dom.IfStatement node) {
							int lineNo = cUnit.getLineNumber(node.getStartPosition());
							if (lineNo != alias.getLineNo()) return true;
							String fieldName = fa.getFieldName();
							fieldName = fieldName.substring(fieldName.lastIndexOf(".") + 1);
							int start = node.getStartPosition();
							int end = start;
							Expression expression = node.getExpression();
							if (expression != null) {
								start = expression.getStartPosition();
								start += expression.toString().indexOf(fieldName);
								end = start;
								if (source.startsWith("this.", start - "this.".length())) {
									start -= "this.".length();
									end = start + "this".length();
								}
							}
							attributes.put(IMarker.CHAR_START, start);
							attributes.put(IMarker.CHAR_END, end);
							attributes.put(IMarker.LINE_NUMBER, lineNo);
							return false;
						}
					};
				} else if (statement instanceof MethodInvocation) {
					final MethodInvocation mi = (MethodInvocation)statement;
					final MethodExecution calledMe = mi.getCalledMethodExecution();
					visitor = new MyASTVisitor() {
						@Override
						public boolean visit(org.eclipse.jdt.core.dom.MethodInvocation node) {
							int lineNo = cUnit.getLineNumber(node.getStartPosition());
							if (lineNo != alias.getLineNo()) return true;
							String name1 = node.getName().toString() + "(";
							String name2 = calledMe.getCallerSideSignature();
							name2 = name2.substring(0, name2.indexOf("(") + 1);
							name2 = name2.substring(name2.lastIndexOf(".") + 1);
							if (!(name1.equals(name2))) return true;
							String receiverName = node.getExpression().toString();
							int start = node.getStartPosition();
							attributes.put(IMarker.CHAR_START, start);
							if (source.startsWith("this.", start)) {
								attributes.put(IMarker.CHAR_END, start + "this".length());
							} else {
								attributes.put(IMarker.CHAR_END, start + receiverName.length());
							}
							attributes.put(IMarker.LINE_NUMBER, lineNo);
							return false;
						}
						@Override
						public boolean visit(org.eclipse.jdt.core.dom.ClassInstanceCreation node) {
							int lineNo = cUnit.getLineNumber(node.getStartPosition());
							if (lineNo != alias.getLineNo()) return true;
							String name1 = node.toString();
							name1 = name1.substring("new ".length(), name1.indexOf("(") + 1);
							String name2 = calledMe.getCallerSideSignature();
							name2 = name2.substring(0, name2.indexOf("(") + 1);
							name2 = name2.substring(name2.lastIndexOf(".") + 1);
							if (!(name1.equals(name2))) return true;
							int start = node.getStartPosition();
							int end = start; // note: �R���X�g���N�^�Ăяo���ɑΉ�����this�̓R�[�h���ɂ͏o�Ă��Ȃ����ߒ���0�̃}�[�J�[�ɂ���
							attributes.put(IMarker.CHAR_START, start);
							attributes.put(IMarker.CHAR_END, end);
							attributes.put(IMarker.LINE_NUMBER, lineNo);
							return false;
						}
					};
				}
				return visitor;
			}
			case METHOD_INVOCATION: {
				if (statement instanceof MethodInvocation) {
					final MethodInvocation mi = (MethodInvocation)statement;
					final MethodExecution calledMe = mi.getCalledMethodExecution();
					visitor = new MyASTVisitor() {
						@Override
						public boolean visit(org.eclipse.jdt.core.dom.MethodInvocation node) {
							int lineNo = cUnit.getLineNumber(node.getStartPosition());
							if (lineNo != alias.getLineNo()) return true;
							String name1 = node.getName().toString() + "(";
							String name2 = calledMe.getCallerSideSignature();
							name2 = name2.substring(0, name2.indexOf("(") + 1);
							name2 = name2.substring(name2.lastIndexOf(".") + 1);
							if (!(name1.equals(name2))) return true;
							Expression expression = node.getExpression();
							String receiverName = "";
							if (expression != null) {
								receiverName = expression.toString();
							}
							int start = node.getStartPosition();
							if (source.startsWith("this.", start)) {
								start += ("this." + receiverName + ".").length();
							} else if (source.startsWith("super.", start)) {
								start += ("super." + receiverName + ".").length();
							} else if (!(receiverName.isEmpty())) {
								start += (receiverName + ".").length(); 
							}
							int end = node.getStartPosition() + node.getLength();
							attributes.put(IMarker.CHAR_START, start);
							attributes.put(IMarker.CHAR_END, end);
							attributes.put(IMarker.LINE_NUMBER, lineNo);
							return false;
						}
						@Override
						public boolean visit(org.eclipse.jdt.core.dom.ClassInstanceCreation node) {
							int lineNo = cUnit.getLineNumber(node.getStartPosition());
							if (lineNo != alias.getLineNo()) return true;
							String name1 = node.toString();
							name1 = name1.substring("new ".length(), name1.indexOf("(") + 1);
							String name2 = calledMe.getCallerSideSignature();
							name2 = name2.substring(name2.lastIndexOf(".") + 1, name2.indexOf("(") + 1);
							if (!(name1.equals(name2))) return true;
							int start = node.getStartPosition();
							int end = start + node.getLength();
							attributes.put(IMarker.CHAR_START, start);
							attributes.put(IMarker.CHAR_END, end);
							attributes.put(IMarker.LINE_NUMBER, lineNo);
							return false;
						}
					};
				}
				return visitor;
			}
			case CONSTRACTOR_INVOCATION: {
				if (statement instanceof MethodInvocation) {
					final MethodInvocation mi = (MethodInvocation)statement;
					final MethodExecution calledMe = mi.getCalledMethodExecution();
					visitor = new MyASTVisitor() {
						@Override
						public boolean visit(org.eclipse.jdt.core.dom.ClassInstanceCreation node) {
							int lineNo = cUnit.getLineNumber(node.getStartPosition());
							if (lineNo != alias.getLineNo()) return true;
							String name1 = node.toString();
							name1 = name1.substring("new ".length(), name1.indexOf("(") + 1);
							String name2 = calledMe.getCallerSideSignature();
							name2 = name2.substring(0, name2.indexOf("(") + 1);
							name2 = name2.substring(name2.lastIndexOf(".") + 1);
							if (!(name1.equals(name2))) return true;
							int start = node.getStartPosition();
							int end = start + node.getLength();
							attributes.put(IMarker.CHAR_START, start);
							attributes.put(IMarker.CHAR_END, end);
							attributes.put(IMarker.LINE_NUMBER, lineNo);
							return false;
						}
					};
				}
				return visitor;
			}
			// note: �ǐՃI�u�W�F�N�g�̐؂�ւ�
			case FIELD: {
				if (statement instanceof FieldAccess) {
					final FieldAccess fa = (FieldAccess)statement;
					visitor = new MyASTVisitor() {
						@Override
						public boolean visit(org.eclipse.jdt.core.dom.FieldAccess node) {
							int lineNo = cUnit.getLineNumber(node.getStartPosition());
							if (lineNo != alias.getLineNo()) return true;
							String name1 = node.getName().toString();
							String name2 = fa.getFieldName();
							name2 = name2.substring(name2.lastIndexOf(".") + 1);
							if (!(name1.equals(name2))) return true;
							int start = node.getStartPosition();
							int end = start + node.getLength();
							if (source.startsWith("this.", start)) {
								attributes.put(IMarker.CHAR_START, start + "this.".length());
							} else {
								attributes.put(IMarker.CHAR_START, start);
							}
							attributes.put(IMarker.CHAR_END, end);
							attributes.put(IMarker.LINE_NUMBER, lineNo);
							return false;
						}
						@Override
						public boolean visit(org.eclipse.jdt.core.dom.ArrayAccess node) {
							int lineNo = cUnit.getLineNumber(node.getStartPosition());
							if (lineNo != alias.getLineNo()) return true;
							int start = node.getStartPosition();
							int end = start + node.getArray().toString().length();
							if (source.startsWith("this.", start)) {
								attributes.put(IMarker.CHAR_START, start + "this.".length());
							} else {
								attributes.put(IMarker.CHAR_START, start);
							}
							attributes.put(IMarker.CHAR_END, end);
							attributes.put(IMarker.LINE_NUMBER, lineNo);
							return false;
						}
						@Override
						public boolean visit(org.eclipse.jdt.core.dom.SimpleName node) {
							// note: ���\�b�h�Ăяo���̃��V�[�o���t�B�[���h�̏ꍇ�̓t�B�[���h�A�N�Z�X�̃m�[�h���Ɨ��Ȃ�������ɂ���Œʂ�
							int lineNo = cUnit.getLineNumber(node.getStartPosition());
							if (lineNo != alias.getLineNo()) return true;
							if (!(node.getParent() instanceof org.eclipse.jdt.core.dom.MethodInvocation)) return true;							
							String name1 = node.toString();
							String name2 = fa.getFieldName();
							name2 = name2.substring(name2.lastIndexOf(".") + 1);
							if (!(name1.equals(name2))) return true;
							int start = node.getStartPosition();
							int end = start + node.getLength();
							if (source.startsWith("this.", start)) {
								attributes.put(IMarker.CHAR_START, start + "this.".length());
							} else {
								attributes.put(IMarker.CHAR_START, start);
							}
							attributes.put(IMarker.CHAR_END, end);
							attributes.put(IMarker.LINE_NUMBER, lineNo);
							return false;
						}
						@Override
						public boolean visit(org.eclipse.jdt.core.dom.ClassInstanceCreation node) {
							// note: �R���X�g���N�^�Ăяo���̈����Ƀt�B�[���h������ꍇ
							int lineNo = cUnit.getLineNumber(node.getStartPosition());
							if (lineNo != alias.getLineNo()) return true;
							String name1 = node.toString();
							name1 = name1.substring(name1.indexOf("("));
							String name2 = fa.getFieldName();
							name2 = name2.substring(name2.lastIndexOf(".") + 1);
							if (!(name1.contains(name2))) return true;
							int start = node.getStartPosition();
							start += (node.toString().indexOf("(") + name1.indexOf(name2));
							int end = start + name2.length();
							attributes.put(IMarker.CHAR_START, start);
							attributes.put(IMarker.CHAR_END, end);
							attributes.put(IMarker.LINE_NUMBER, lineNo);
							return false;
						}						
						@Override
						public boolean visit(org.eclipse.jdt.core.dom.ReturnStatement node) {
							int lineNo = cUnit.getLineNumber(node.getStartPosition());
							if (lineNo != alias.getLineNo()) return true;
							Expression expression = node.getExpression();
							String fieldName = fa.getFieldName();
							fieldName = fieldName.substring(fieldName.lastIndexOf(".") + 1);
							int start = node.getStartPosition();
							if (expression != null) {
								start = expression.getStartPosition();
							}
							int end = start + fieldName.length();
							attributes.put(IMarker.CHAR_START, start);
							attributes.put(IMarker.CHAR_END, end);
							attributes.put(IMarker.LINE_NUMBER, lineNo);
							return false;
						}
						@Override
						public boolean visit(org.eclipse.jdt.core.dom.IfStatement node) {
							int lineNo = cUnit.getLineNumber(node.getStartPosition());
							if (lineNo != alias.getLineNo()) return true;
							String fieldName = fa.getFieldName();
							fieldName = fieldName.substring(fieldName.lastIndexOf(".") + 1);
							int start = node.getStartPosition();
							int end = start + node.getLength();
							Expression expression = node.getExpression();
							if (expression != null) {
								start = expression.getStartPosition();
								start += expression.toString().indexOf(fieldName);
								end = start + fieldName.length();
							}
							attributes.put(IMarker.CHAR_START, start);
							attributes.put(IMarker.CHAR_END, end);
							attributes.put(IMarker.LINE_NUMBER, lineNo);
							return false;
						}
					};
				}
				return visitor;
			}
			case CONTAINER: {
				if (statement instanceof FieldAccess) {
					final FieldAccess fa = (FieldAccess)statement;
					visitor = new MyASTVisitor() {						
						@Override
						public boolean visit(org.eclipse.jdt.core.dom.QualifiedName node) {
							int lineNo = cUnit.getLineNumber(node.getStartPosition());
							if (lineNo != alias.getLineNo()) return true;
							String name1 = node.getName().toString();
							String name2 = fa.getFieldName();
							name2 = name2.substring(name2.lastIndexOf(".") + 1);
							if (!(name1.equals(name2))) return true;
							int start = node.getStartPosition();
							int end = start + node.getLength();
							attributes.put(IMarker.CHAR_START, start);
							attributes.put(IMarker.CHAR_END, end);
							attributes.put(IMarker.LINE_NUMBER, lineNo);
							return false;
						}
					};
				}
				return visitor;
			}
			case ARRAY_ELEMENT: {
				// note: �z��̃Z�b�g�ƃQ�b�g�̃g���[�X�ɂ͍s�ԍ���z�񖼂��L�^����Ă��Ȃ�
				if (statement instanceof ArrayAccess) {
					final ArrayAccess aa = (ArrayAccess)statement;
					visitor = new MyASTVisitor() {
						@Override
						public boolean visit(org.eclipse.jdt.core.dom.ArrayAccess node) {
							int index = Integer.parseInt(node.getIndex().toString());
							if (index != aa.getIndex()) return true;
							int lineNo = cUnit.getLineNumber(node.getStartPosition());
							int start = node.getStartPosition();
							int end = start + node.getLength();
							if (source.startsWith("this.", start)) {
								attributes.put(IMarker.CHAR_START, start + "this.".length());	
							} else {
								attributes.put(IMarker.CHAR_START, start);
							}
							attributes.put(IMarker.CHAR_END, end);
							attributes.put(IMarker.LINE_NUMBER, lineNo);
							return false;
						}
					};
				}
				return visitor;
			}
			case ARRAY: {
				// note: �z��̃Z�b�g�ƃQ�b�g�̃g���[�X�ɂ͍s�ԍ���z�񖼂��L�^����Ă��Ȃ�
				if (statement instanceof ArrayAccess) {
					final ArrayAccess aa = (ArrayAccess)statement;
					visitor = new MyASTVisitor() {
						@Override
						public boolean visit(org.eclipse.jdt.core.dom.ArrayAccess node) {
							int index = Integer.parseInt(node.getIndex().toString());
							if (index != aa.getIndex()) return true;
							int lineNo = cUnit.getLineNumber(node.getStartPosition());
							int start = node.getStartPosition();
							int end = start + node.getArray().toString().length();
							if (source.startsWith("this.", start)) {
								attributes.put(IMarker.CHAR_START, start + "this.".length());	
							} else {
								attributes.put(IMarker.CHAR_START, start);
							}
							attributes.put(IMarker.CHAR_END, end);
							attributes.put(IMarker.LINE_NUMBER, lineNo);
							return false;
						}
					};
				}
				return visitor;
			}
			case ARRAY_CREATE: {
				if (statement instanceof ArrayCreate) {
					final ArrayCreate ac = (ArrayCreate)statement;
					visitor = new MyASTVisitor() {
						@Override
						public boolean visit(org.eclipse.jdt.core.dom.ArrayCreation node) {
							int lineNo = cUnit.getLineNumber(node.getStartPosition());
							if (lineNo != ac.getLineNo()) return true;
							int start = node.getStartPosition();
							int end = start + node.getLength();
							attributes.put(IMarker.CHAR_START, start);
							attributes.put(IMarker.CHAR_END, end);
							attributes.put(IMarker.LINE_NUMBER, lineNo);
							return false;
						}
					};
				}
				return visitor;
			}
			// note: ���\�b�h����̏o��
			case ACTUAL_ARGUMENT: {
				if (statement instanceof MethodInvocation) {
					final MethodInvocation mi = (MethodInvocation)statement;
					final MethodExecution calledMe = mi.getCalledMethodExecution();
					final int index = alias.getIndex();
					visitor = new MyASTVisitor() {
						@Override
						public boolean visit(org.eclipse.jdt.core.dom.MethodInvocation node) {
							int lineNo = cUnit.getLineNumber(node.getStartPosition());
							if (lineNo != alias.getLineNo()) return true;
							String name1 = node.getName().toString();
							String name2 = calledMe.getCallerSideSignature();
							name2 = name2.substring(0, name2.indexOf("("));
							name2 = name2.substring(name2.lastIndexOf(".") + 1);
							if (!(name1.equals(name2))) return true;
							Object obj = node.arguments().get(index);
							if (obj instanceof Expression) {
								Expression argument = (Expression)obj;
								int start = argument.getStartPosition();
								int end = start + argument.getLength();
								attributes.put(IMarker.CHAR_START, start);
								attributes.put(IMarker.CHAR_END, end);
								attributes.put(IMarker.LINE_NUMBER, lineNo);
							}
							return false;
						}
						@Override
						public boolean visit(org.eclipse.jdt.core.dom.SuperMethodInvocation node) {
							int lineNo = cUnit.getLineNumber(node.getStartPosition());
							if (lineNo != alias.getLineNo()) return true;
							String name1 = node.getName().toString();
							String name2 = calledMe.getCallerSideSignature();
							name2 = name2.substring(0, name2.indexOf("("));
							name2 = name2.substring(name2.lastIndexOf(".") + 1);
							if (!(name1.equals(name2))) return true;
							Object obj = node.arguments().get(index);
							if (obj instanceof Expression) {
								Expression argument = (Expression)obj;
								int start = argument.getStartPosition();
								int end = start + argument.getLength();
								attributes.put(IMarker.CHAR_START, start);
								attributes.put(IMarker.CHAR_END, end);
								attributes.put(IMarker.LINE_NUMBER, lineNo);
							}
							return false;
						}
					};
				}
				return visitor;	
			}
			case RECEIVER: {
				if (statement instanceof MethodInvocation) {
					final MethodInvocation mi = (MethodInvocation)statement;
					final MethodExecution calledMe = mi.getCalledMethodExecution();
					visitor = new MyASTVisitor() {
						@Override
						public boolean visit(org.eclipse.jdt.core.dom.MethodInvocation node) {
							int lineNo = cUnit.getLineNumber(node.getStartPosition());
							if (lineNo != alias.getLineNo()) return true;
							String name1 = node.getName().toString();
							String name2 = calledMe.getCallerSideSignature();
							name2 = name2.substring(0, name2.indexOf("("));
							name2 = name2.substring(name2.lastIndexOf(".") + 1);
							if (!(name1.equals(name2))) return true;
							int start = node.getStartPosition();
							int end = start;
							Expression expression = node.getExpression(); // note: ���\�b�h�Ăяo���̃��V�[�o�����擾
							if (expression != null) {
								String receiverName = expression.toString(); // note: ���\�b�h�Ăяo���̃��V�[�o���܂�
								end = start + receiverName.length();
							}
							attributes.put(IMarker.CHAR_START, start);
							attributes.put(IMarker.CHAR_END, end);
							attributes.put(IMarker.LINE_NUMBER, lineNo);
							return false;
						}
						@Override
						public boolean visit(org.eclipse.jdt.core.dom.SuperMethodInvocation node) {
							int lineNo = cUnit.getLineNumber(node.getStartPosition());
							if (lineNo != alias.getLineNo()) return true;
							String name1 = node.getName().toString();
							String name2 = calledMe.getCallerSideSignature();
							name2 = name2.substring(0, name2.indexOf("("));
							name2 = name2.substring(name2.lastIndexOf(".") + 1);
							if (!(name1.equals(name2))) return true;
							int start = node.getStartPosition();
							int end = start + "super".length();
							attributes.put(IMarker.CHAR_START, start);
							attributes.put(IMarker.CHAR_END, end);
							attributes.put(IMarker.LINE_NUMBER, lineNo);
							return false;
						}
					};
				}
				return visitor;
			}
			case RETURN_VALUE:
				// note: �ǂ��Ń��^�[���������̏��(�s�ԍ���)���g���[�X�ɂ͋L�^����Ă��Ȃ�
				visitor = new MyASTVisitor(){
					public boolean visit(org.eclipse.jdt.core.dom.ReturnStatement node) {
						int lineNo = cUnit.getLineNumber(node.getStartPosition());
						int aliasLineNo = alias.getLineNo();
						if (lineNo < aliasLineNo) return true; // ���ۂɃ��^�[�������ꏊ�̒��O�ɂ���ŏI�X�e�[�g�����g�����O�̃��^�[���͔�΂�
						int start = node.getStartPosition();
						int end = start + node.getLength();
						attributes.put(IMarker.CHAR_START, start);
						attributes.put(IMarker.CHAR_END, end);
						attributes.put(IMarker.LINE_NUMBER, lineNo);
						return false;
					}
				};
				return visitor;
			}			
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
		return visitor;
	}
	
	private void setAttributesForCoordinator(final Map<String, Object> attributes, MethodExecution methodExecution, IFile file) {
		// note: ���\�b�h�V�O�l�`�����n�C���C�g
		IType type = JavaElementFinder.findIType(methodExecution);				
		final IMethod method = JavaElementFinder.findIMethod(methodExecution, type);
		if (method == null) return;
		ASTParser parser = ASTParser.newParser(AST.JLS10);
		ICompilationUnit unit = method.getCompilationUnit();
		parser.setSource(unit);
		ASTNode node = parser.createAST(new NullProgressMonitor());
		if (node instanceof CompilationUnit) {
			final CompilationUnit cUnit = (CompilationUnit)node;
			node.accept(new ASTVisitor() {			
				@Override
				public boolean visit(MethodDeclaration node) {
					try {
						if (attributes.containsKey(IMarker.LINE_NUMBER)) return false;
						String src1 = node.toString().replaceAll(" ", "");
						src1 = src1.substring(0, src1.lastIndexOf("\n"));
						String src1Head = src1.substring(0, src1.indexOf(")") + 1);
						String src2 = method.getSource().replaceAll(" |\t|\r|\n", "");
						if (!(src2.contains(src1Head))) return false;								
						int start = node.getStartPosition();
						int end = start + node.toString().indexOf(")") + 1;
						Javadoc javadoc = node.getJavadoc();
						if (javadoc != null) {
							start += javadoc.getLength();
							start += 5; // note: node.toString()�Ǝ��ۂ̃R�[�h�̃X�y�[�X���̍��������l�߂鉼����
							String tmp = node.toString().replace(javadoc.toString(), "");
							end = start + tmp.indexOf(")") + 1;
						}
						int lineNo = cUnit.getLineNumber(node.getStartPosition());
						attributes.put(IMarker.CHAR_START, start);
						attributes.put(IMarker.CHAR_END, end);
						attributes.put(IMarker.LINE_NUMBER, lineNo);
					} catch (JavaModelException e) {
						e.printStackTrace();
					}
					return false;
				}
			});
		}
	}
	
	private void setAttributesForCreationPoint(final Map<String, Object> attributes, MethodExecution methodExecution, IFile file, int lineNo, String markerId) {
		try {
			FileEditorInput input = new FileEditorInput(file);
			FileDocumentProvider provider = new FileDocumentProvider();
			provider.connect(input);
			IDocument document = provider.getDocument(input);
			IRegion lineRegion = document.getLineInformation(lineNo - 1);
			attributes.put(IMarker.CHAR_START, lineRegion.getOffset());
			attributes.put(IMarker.CHAR_END, lineRegion.getOffset() + lineRegion.getLength());
			attributes.put(IMarker.LINE_NUMBER, lineNo);
		} catch (CoreException | BadLocationException e) {
			e.printStackTrace();
		}		
	}	
	
	private void deleteMarkers(List<IMarker> markerList) {
		for (IMarker marker : markerList) {
			try {
				marker.delete();
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}		
	}
	
	public void clearAllMarkers() {
		for (List<IMarker> markerList: markerIdToMarkers.values()) {
			deleteMarkers(markerList);
		}
		markerIdToMarkers.clear();
		markersByOrder.clear();
	}
}
