package org.ntlab.traceDebugger.analyzerProvider;

import java.util.ArrayList;
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
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.ui.editors.text.FileDocumentProvider;
import org.eclipse.ui.part.FileEditorInput;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ArrayAccess;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ArrayCreate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldAccess;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodInvocation;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Statement;
import org.ntlab.traceDebugger.JavaEditorOperator;

public class DeltaMarkerManager {
	private static final DeltaMarkerManager theInstance = new DeltaMarkerManager();
	private Map<String, List<IMarker>> markers = new HashMap<>();
	public static final String DELTA_MARKER_ID = "org.ntlab.traceDebugger.deltaMarker";
	public static final String DELTA_MARKER_ID_2 = "org.ntlab.traceDebugger.deltaMarker2";
	
	private DeltaMarkerManager() {
		
	}
	
	public static DeltaMarkerManager getInstance() {
		return theInstance;
	}
	
	public Map<String, List<IMarker>> getMarkers() {
		return markers;
	}

	public IMarker addMarker(Alias alias, IFile file, String message, String markerId) {
		try {
			IMarker marker = file.createMarker(markerId);
			Map<String, Object> attributes = new HashMap<>();
			setAttributesForAlias(attributes, alias, file, message, markerId);
			attributes.put(IMarker.TRANSIENT, true);
			marker.setAttributes(attributes);
			addMarker(markerId, marker);
			return marker;
		} catch (CoreException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public IMarker addMarker(MethodExecution me, int lineNo, IFile file, String message, String markerId) {
		try {
			IMarker marker = file.createMarker(markerId);
			Map<String, Object> attributes = new HashMap<>();
			setAttributesForMethodExecution(attributes, me, file, lineNo, markerId);
			attributes.put(IMarker.MESSAGE, message);
			attributes.put(IMarker.TRANSIENT, true);
			marker.setAttributes(attributes);
			addMarker(markerId, marker);
			return marker;
		} catch (CoreException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	private void addMarker(String markerId, IMarker marker) {
		List<IMarker> markerList = markers.get(markerId);
		if (markerList == null) {
			markerList = new ArrayList<IMarker>();
			markers.put(markerId, markerList);
		}
		markerList.add(marker);
	}

	private void setAttributesForAlias(final Map<String, Object> attributes, Alias alias, IFile file, String message, String markerId) {
		try {
			FileEditorInput input = new FileEditorInput(file);
			FileDocumentProvider provider = new FileDocumentProvider();
			provider.connect(input);
			String aliasType = alias.getAliasType().toString();
			String statement = "";
			try {
				statement = alias.getOccurrencePoint().getStatement().toString(); // statementがない場合がある
			} catch (ArrayIndexOutOfBoundsException e) {
				// e.printStackTrace();
			}
			System.out.println(aliasType + ": " + statement);
			
			ASTParser parser = ASTParser.newParser(AST.JLS10);
			MethodExecution methodExecution = alias.getMethodExecution();
			IType type = JavaEditorOperator.findIType(methodExecution);					
			IMethod method = JavaEditorOperator.findIMethod(methodExecution, type);
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
			attributes.put(IMarker.MESSAGE, message);
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
						// note: 該当するメソッド呼び出しの場合のみ子ノードの探索を続行
						String src1 = node.toString().replaceAll(" ", "");
						src1 = src1.substring(0, src1.lastIndexOf("\n"));
						String src1Head = src1.substring(0, src1.indexOf(")") + 1);
						String src2 = method.getSource().replaceAll(" |\t|\r", "");						
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
			// note: メソッドへの入口
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
							attributes.put(IMarker.CHAR_START, start);
							if (source.startsWith("this.", start)) {
								attributes.put(IMarker.CHAR_END, start + "this".length());
							} else {
								attributes.put(IMarker.CHAR_END, start + node.getLength());
							}
							attributes.put(IMarker.LINE_NUMBER, lineNo);
							return false;
						}
						@Override
						public boolean visit(org.eclipse.jdt.core.dom.ArrayAccess node) {
							int lineNo = cUnit.getLineNumber(node.getStartPosition());
							if (lineNo != alias.getLineNo()) return true;
							int start = node.getStartPosition();
							attributes.put(IMarker.CHAR_START, start);
							if (source.startsWith("this.", start)) {
								attributes.put(IMarker.CHAR_END, start + "this".length());
							} else {
								attributes.put(IMarker.CHAR_END, start + node.getLength());
							}
							attributes.put(IMarker.LINE_NUMBER, lineNo);
							return true;
						}
						@Override
						public boolean visit(org.eclipse.jdt.core.dom.SimpleName node) {
							// note: メソッド呼び出しのレシーバがフィールドの場合はフィールドアクセスのノードだと来ないが代わりにこれで通る
							int lineNo = cUnit.getLineNumber(node.getStartPosition());
							if (lineNo != alias.getLineNo()) return true;
							if (!(node.getParent() instanceof org.eclipse.jdt.core.dom.MethodInvocation)) return true;
							String name1 = node.toString();
							String name2 = fa.getFieldName();
							name2 = name2.substring(name2.lastIndexOf(".") + 1);
							if (!(name1.equals(name2))) return true;
							int start = node.getStartPosition();
							attributes.put(IMarker.CHAR_START, start);
							if (source.startsWith("this.", start)) {
								attributes.put(IMarker.CHAR_END, start + "this".length());
							} else {
								attributes.put(IMarker.CHAR_END, start + node.getLength());
							}
							attributes.put(IMarker.LINE_NUMBER, lineNo);
							return false;
						}						
						@Override
						public boolean visit(org.eclipse.jdt.core.dom.ReturnStatement node) {
							int lineNo = cUnit.getLineNumber(node.getStartPosition());
							int start = node.getExpression().getStartPosition();
							attributes.put(IMarker.CHAR_START, start);
							if (source.startsWith("this.", start)) {
								attributes.put(IMarker.CHAR_END, start + "this".length());
							} else {
								attributes.put(IMarker.CHAR_END, start + node.getExpression().getLength());
							}
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
							String receiverName = node.getExpression().toString();
							int start = node.getStartPosition();
							if (source.startsWith("this.", start)) {
								start += ("this." + receiverName + ".").length();
							} else {
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
							name2 = name2.substring(0, name2.indexOf("(") + 1);
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
				// note: コンストラクタ呼び出しの際もエイリアスタイプはMETHOD_INVOCATIONになっている?
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
			// note: 追跡オブジェクトの切り替え
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
							// note: メソッド呼び出しのレシーバがフィールドの場合はフィールドアクセスのノードだと来ないが代わりにこれで通る
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
						public boolean visit(org.eclipse.jdt.core.dom.ReturnStatement node) {
							int lineNo = cUnit.getLineNumber(node.getStartPosition());
							int start = node.getExpression().getStartPosition();
							int end = start + node.getExpression().getLength();
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
							// end = start + source.substring(start, end).lastIndexOf(".");
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
				// note: 配列のセットとゲットのトレースには行番号や配列名が記録されていない
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
				// note: 配列のセットとゲットのトレースには行番号や配列名が記録されていない
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
			// note: メソッドからの出口
			case ACTUAL_ARGUMENT: {
				if (statement instanceof MethodInvocation) {
					final MethodInvocation mi = (MethodInvocation)statement;
					final MethodExecution calledMe = mi.getCalledMethodExecution();
					final int index = alias.getIndex();
					calledMe.getArguments().get(alias.getIndex());
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
							String receiverName = node.getExpression().toString();
							int start = node.getStartPosition();
							int end = start + (receiverName).length();
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
				// note: どこでリターンしたかの情報(行番号等)がトレースには記録されていない
				visitor = new MyASTVisitor(){
					public boolean visit(org.eclipse.jdt.core.dom.ReturnStatement node) {
						int lineNo = cUnit.getLineNumber(node.getStartPosition());
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

	private void setAttributesForMethodExecution(final Map<String, Object> attributes, MethodExecution methodExecution, IFile file, int lineNo, String markerId) {
		try {
			FileEditorInput input = new FileEditorInput(file);
			FileDocumentProvider provider = new FileDocumentProvider();
			provider.connect(input);
			IDocument document = provider.getDocument(input);
			if (lineNo > 1) {
				IRegion lineRegion = document.getLineInformation(lineNo - 1);
				attributes.put(IMarker.CHAR_START, lineRegion.getOffset());
				attributes.put(IMarker.CHAR_END, lineRegion.getOffset() + lineRegion.getLength());
				attributes.put(IMarker.LINE_NUMBER, lineNo);
			} else {
				// note: メソッドシグネチャをハイライト
				IType type = JavaEditorOperator.findIType(methodExecution);				
				final IMethod method = JavaEditorOperator.findIMethod(methodExecution, type);				
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
								String src2 = method.getSource().replaceAll(" |\t|\r", "");
								if (!(src2.contains(src1Head))) return false;
								int start = node.getStartPosition();
								int end = start + node.toString().indexOf(")") + 1;
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
		} catch (CoreException | BadLocationException e) {
			e.printStackTrace();
		}
	}
	
	public void deleteMarkers(String markerId) {
		List<IMarker> markerList = markers.get(markerId);
		if (markerList == null) return;
		for (IMarker marker : markerList) {
			try {
				marker.delete();
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}
		markerList.clear();
		markers.remove(markerId);
	}
}
