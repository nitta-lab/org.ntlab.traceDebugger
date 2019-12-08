package org.ntlab.traceDebugger.analyzerProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MemberRef;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.FindReplaceDocumentAdapter;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.ui.editors.text.FileDocumentProvider;
import org.eclipse.ui.part.FileEditorInput;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ArrayAccess;
import org.ntlab.traceAnalysisPlatform.tracer.trace.BlockEnter;
import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldAccess;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodInvocation;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Statement;
import org.ntlab.traceDebugger.JavaEditorOperator;

public class DeltaMarkerManager {
	private static final DeltaMarkerManager theInstance = new DeltaMarkerManager();
	private Map<String, List<IMarker>> markers = new HashMap<>();
	
	private DeltaMarkerManager() {
		
	}
	
	public static DeltaMarkerManager getInstance() {
		return theInstance;
	}

	public IMarker addMarker(Alias alias, IFile file, String markerId) {
		try {
			IMarker marker = file.createMarker(markerId);
			Map<String, Object> attributes = new HashMap<>();
			setAttributesForAlias(attributes, alias, file, markerId);
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
	
	public Map<String, List<IMarker>> getMarkers() {
		return markers;
	}

	private void setAttributesForAlias(final Map<String, Object> attributes, Alias alias, IFile file, String markerId) {
		try {
			FileEditorInput input = new FileEditorInput(file);
			FileDocumentProvider provider = new FileDocumentProvider();
			provider.connect(input);
			IDocument document = provider.getDocument(input);
//			FindReplaceDocumentAdapter findAdapter = new FindReplaceDocumentAdapter(document);
			Statement statement = alias.getOccurrencePoint().getStatement();
			
			String aliasType = alias.getAliasType().toString();
			String statements = alias.getOccurrencePoint().getStatement().toString();
			System.out.println(aliasType + ": " + statements);
			StringBuilder message = new StringBuilder();
			if (markerId.equals(DeltaExtractionAnalyzer.DELTA_MARKER_ID)) {
				message.append("SrcSide: ");	
			} else if (markerId.equals(DeltaExtractionAnalyzer.DELTA_MARKER_ID_2)) {
				message.append("DstSide: ");
			}
			message.append(alias.getAliasType().toString());
			message.append(" (id = " + alias.getObjectId() + ")");
			
			ASTParser parser = ASTParser.newParser(AST.JLS10);
			MethodExecution methodExecution = alias.getMethodExecution();
			IType type = JavaEditorOperator.findIType(methodExecution);					
			IMethod method = JavaEditorOperator.findIMethod(methodExecution, type);
			ICompilationUnit unit = method.getCompilationUnit();
			parser.setSource(unit);
			ASTNode node = parser.createAST(new NullProgressMonitor());
			ASTVisitor visitor = createVisitor(alias, method, document, parser, attributes);
			
			if (visitor != null) node.accept(visitor);
			attributes.put(IMarker.MESSAGE, message.toString());
			attributes.put(IMarker.LINE_NUMBER, alias.getLineNo());
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}
	
	private ASTVisitor createVisitor(Alias alias, IMethod method, IDocument document, ASTParser parser, final Map<String, Object> attributes) {
		ASTVisitor visitor = new ASTVisitor() {
		};	
		try {
			Statement statement = alias.getOccurrencePoint().getStatement();
			final ICompilationUnit unit = method.getCompilationUnit();
			final String source = unit.getSource();
			switch (alias.getAliasType()) {
			// メソッドへの入口
			case FORMAL_PARAMETER: {
				ISourceRange range = method.getSourceRange();
				parser.setSourceRange(range.getOffset(), range.getLength());
				final int index = alias.getIndex();
				visitor = new ASTVisitor() {
					@Override
					public boolean visit(MethodDeclaration node) {
						Object obj = node.parameters().get(index);
						if (obj instanceof SingleVariableDeclaration) {
							SingleVariableDeclaration parameter = (SingleVariableDeclaration)obj;
							int start = parameter.getStartPosition();
							int end = start + parameter.getLength();
							attributes.put(IMarker.CHAR_START, start);
							attributes.put(IMarker.CHAR_END, end);
						}
						return false;
					}
				};
				return visitor;
			}
			case THIS: {
				if (statement instanceof FieldAccess) {
					final FieldAccess fa = (FieldAccess)statement;
					IRegion lineRegion = document.getLineInformation(fa.getLineNo() - 1);
					parser.setSourceRange(lineRegion.getOffset(), lineRegion.getLength());
					visitor = new ASTVisitor() {
						@Override
						public boolean visit(org.eclipse.jdt.core.dom.FieldAccess node) {
							String name1 = node.getName().toString();
							String name2 = fa.getFieldName();
							name2 = name2.substring(name2.lastIndexOf(".") + 1);
							if (!(name1.equals(name2))) return false;
							int start = node.getStartPosition();
							if (source.startsWith("this.", start)) {
								attributes.put(IMarker.CHAR_START, start);
								attributes.put(IMarker.CHAR_END, start + "this".length());								
							}
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
					IRegion lineRegion = document.getLineInformation(mi.getLineNo() - 1);
					parser.setSourceRange(lineRegion.getOffset(), lineRegion.getLength());
					visitor = new ASTVisitor() {
						@Override
						public boolean visit(org.eclipse.jdt.core.dom.MethodInvocation node) {
							String name1 = node.getName().toString();
							String name2 = calledMe.getCallerSideSignature();
							name2 = name2.substring(name2.lastIndexOf(".") + 1, name2.indexOf("("));
							if (!(name1.equals(name2))) return false;
							String receiverName = node.getExpression().toString();
							int start = node.getStartPosition() + (receiverName + ".").length();
							// int end = start + name1.length();
							int end = node.getStartPosition() + node.getLength();
							attributes.put(IMarker.CHAR_START, start);
							attributes.put(IMarker.CHAR_END, end);
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
					IRegion lineRegion = document.getLineInformation(mi.getLineNo() - 1);
					parser.setSourceRange(lineRegion.getOffset(), lineRegion.getLength());
					visitor = new ASTVisitor() {
						@Override
						public boolean visit(org.eclipse.jdt.core.dom.ConstructorInvocation node) {
//							String name1 = node.getClass().getName();
//							String name2 = calledMe.getCallerSideSignature();
//							name2 = name2.substring(0, name2.indexOf("("));
//							if (!(name1.equals(name2))) return false;
//							int start = node.getStartPosition();
//							int end = start + name1.length();
//							attributes.put(IMarker.CHAR_START, start);
//							attributes.put(IMarker.CHAR_END, end);
							return false;
						}
					};
				}
				return visitor;
			}
			// 追跡オブジェクトの切り替え
			case FIELD: {
				if (statement instanceof FieldAccess) {
					final FieldAccess fa = (FieldAccess)statement;
					IRegion lineRegion = document.getLineInformation(fa.getLineNo() - 1);
					parser.setSourceRange(lineRegion.getOffset(), lineRegion.getLength());
					visitor = new ASTVisitor() {
						@Override
						public boolean visit(org.eclipse.jdt.core.dom.FieldAccess node) {
							String name1 = node.getName().toString();
							String name2 = fa.getFieldName();
							name2 = name2.substring(name2.lastIndexOf(".") + 1);
							if (!(name1.equals(name2))) return false;
							int start = node.getStartPosition();
							int end = start + node.getLength();
							if (source.startsWith("this.", start)) {
								attributes.put(IMarker.CHAR_START, start + "this.".length());
							} else {
								attributes.put(IMarker.CHAR_START, start);
							}
							attributes.put(IMarker.CHAR_END, end);
							return false;
						}
					};			
				}
				return visitor;
			}
			case CONTAINER: {
				if (statement instanceof FieldAccess) {
					final FieldAccess fa = (FieldAccess)statement;
					IRegion lineRegion = document.getLineInformation(fa.getLineNo() - 1);
					parser.setSourceRange(lineRegion.getOffset(), lineRegion.getLength());
					visitor = new ASTVisitor() {						
						@Override
						public boolean visit(QualifiedName node) {
							String name1 = node.getName().toString();
							String name2 = fa.getFieldName();
							name2 = name2.substring(name2.lastIndexOf(".") + 1);
							if (!(name1.equals(name2))) return false;
							int start = node.getStartPosition();
							int end = start + node.getLength();
							// end = start + source.substring(start, end).lastIndexOf(".");
							attributes.put(IMarker.CHAR_START, start);
							attributes.put(IMarker.CHAR_END, end);
							return false;
						}
					};
				}
				return visitor;
			}
			case ARRAY_ELEMENT:
				break;			
			case ARRAY:
				break;
			// メソッドからの出口
			case ACTUAL_ARGUMENT: {
				if (statement instanceof MethodInvocation) {
					final MethodInvocation mi = (MethodInvocation)statement;
					final MethodExecution calledMe = mi.getCalledMethodExecution();
					final int index = alias.getIndex();
					calledMe.getArguments().get(alias.getIndex());
					IRegion lineRegion = document.getLineInformation(mi.getLineNo() - 1);
					parser.setSourceRange(lineRegion.getOffset(), lineRegion.getLength());
					visitor = new ASTVisitor() {
						@Override
						public boolean visit(org.eclipse.jdt.core.dom.MethodInvocation node) {
							String name1 = node.getName().toString();
							String name2 = calledMe.getCallerSideSignature();
							name2 = name2.substring(name2.lastIndexOf(".") + 1, name2.indexOf("("));
							if (!(name1.equals(name2))) return false;
							Object obj = node.arguments().get(index);
							if (obj instanceof Expression) {
								Expression argument = (Expression)obj;
								int start = argument.getStartPosition();
								int end = start + argument.getLength();
								attributes.put(IMarker.CHAR_START, start);
								attributes.put(IMarker.CHAR_END, end);
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
					IRegion lineRegion = document.getLineInformation(mi.getLineNo() - 1);
					parser.setSourceRange(lineRegion.getOffset(), lineRegion.getLength());
					visitor = new ASTVisitor() {
						@Override
						public boolean visit(org.eclipse.jdt.core.dom.MethodInvocation node) {
							String name1 = node.getName().toString();
							String name2 = calledMe.getCallerSideSignature();
							name2 = name2.substring(name2.lastIndexOf(".") + 1, name2.indexOf("("));
							if (!(name1.equals(name2))) return false;
							String receiverName = node.getExpression().toString();
							int start = node.getStartPosition();
							int end = start + (receiverName).length();
							attributes.put(IMarker.CHAR_START, start);
							attributes.put(IMarker.CHAR_END, end);
							return false;
						}
					};
				}
				return visitor;
			}
			case RETURN_VALUE:
				// note: どこでリターンしたかの情報(行番号等)がトレースには記録されていない
				ISourceRange range = method.getSourceRange();
				parser.setSourceRange(range.getOffset(), range.getLength());
				visitor = new ASTVisitor(){
					public boolean visit(org.eclipse.jdt.core.dom.ReturnStatement node) {
						int start = node.getStartPosition();
						int end = start + node.getLength();
						attributes.put(IMarker.CHAR_START, start);
						attributes.put(IMarker.CHAR_END, end);
						return false;
					}
				};
				return visitor;
			}			
		} catch (JavaModelException | BadLocationException e) {
			e.printStackTrace();
		}
		return visitor;
	}
	
//	private void setAttributesForAlias(Map<String, Object> attributes, Alias alias, IFile file, String markerId) {
//		try {
//			FileEditorInput input = new FileEditorInput(file);
//			FileDocumentProvider provider = new FileDocumentProvider();
//			provider.connect(input);
//			IDocument document = provider.getDocument(input);
//			FindReplaceDocumentAdapter findAdapter = new FindReplaceDocumentAdapter(document);
//			IRegion lineRegion = document.getLineInformation(alias.getLineNo() - 1);
//			Statement statement = alias.getOccurrencePoint().getStatement();
//			
//			String type = alias.getAliasType().toString();
//			String statements = alias.getOccurrencePoint().getStatement().toString();
//			System.out.println(type + ": " + statements);
//			StringBuilder message = new StringBuilder();
//			if (markerId.equals(DeltaExtractionAnalyzer.DELTA_MARKER_ID)) {
//				message.append("SrcSide: ");	
//			} else if (markerId.equals(DeltaExtractionAnalyzer.DELTA_MARKER_ID_2)) {
//				message.append("DstSide: ");
//			}
//			message.append(alias.getAliasType().toString());
//			message.append(" (id = " + alias.getObjectId() + ")");
//
//			switch (alias.getAliasType()) {
//			// メソッドへの入口
//			case FORMAL_PARAMETER:
//				if (statement instanceof BlockEnter) {
//					BlockEnter be = (BlockEnter)statement;
//					IRegion region = document.getLineInformation(alias.getLineNo() - 2);
//					if (region == null) break;
//					attributes.put(IMarker.CHAR_START, region.getOffset());
//					attributes.put(IMarker.CHAR_END, region.getOffset() + region.getLength());
//				}
//				break;
//			case THIS:
//				if (statement instanceof FieldAccess) {
//					FieldAccess fa = (FieldAccess)statement;
//					IRegion region = findAdapter.find(lineRegion.getOffset(), "this", true, true, true, false);
//					if (region == null) break;
//					attributes.put(IMarker.CHAR_START, region.getOffset());
//					attributes.put(IMarker.CHAR_END, region.getOffset() + region.getLength());
//				}
//				break;
//			case METHOD_INVOCATION:
//			case CONSTRACTOR_INVOCATION:
//				if (statement instanceof MethodInvocation) {
//					MethodInvocation mi = (MethodInvocation)statement;
//					MethodExecution me = mi.getCalledMethodExecution();
//					String signature = me.getSignature();
//					signature = signature.substring(signature.lastIndexOf(".") + 1, signature.lastIndexOf("("));
//					IRegion region = findAdapter.find(lineRegion.getOffset(), signature, true, true, true, false);
//					if (region == null) break;
//					attributes.put(IMarker.CHAR_START, region.getOffset());
//					attributes.put(IMarker.CHAR_END, region.getOffset() + region.getLength());
//				}
//				break;
//			// 追跡オブジェクトの切り替え
//			case FIELD:
//				if (statement instanceof FieldAccess) {
//					FieldAccess fa = (FieldAccess)statement;
//					String fieldName = fa.getFieldName();
//					fieldName = fieldName.substring(fieldName.lastIndexOf(".") + 1);
//					IRegion region = null;					
//					int start = lineRegion.getOffset();
//					while (true) {
//						region = findAdapter.find(start, fieldName, true, true, true, false);
//						if (region == null) break;
//						if (findAdapter.charAt(region.getOffset() - 1) == '.') break;
//						start = region.getOffset() + 1;
//					}
//					if (region == null) break;
//					attributes.put(IMarker.CHAR_START, region.getOffset());
//					attributes.put(IMarker.CHAR_END, region.getOffset() + region.getLength());
//				}
//				break;
//			case CONTAINER:
//				if (statement instanceof FieldAccess) {
//					FieldAccess fa = (FieldAccess)statement;
//					String fieldName = fa.getFieldName();
//					IRegion region = findAdapter.find(lineRegion.getOffset(), fieldName, true, true, true, false);
//					if (region == null) break;
//					attributes.put(IMarker.CHAR_START, region.getOffset());
//					attributes.put(IMarker.CHAR_END, region.getOffset() + region.getLength());				
//				}
//				break;
//			case ARRAY_ELEMENT:
//				if (statement instanceof ArrayAccess) {
//					ArrayAccess aa = (ArrayAccess)statement;
//					String arrayIndex = "[" + aa.getIndex() + "]";
//					IRegion region = findAdapter.find(lineRegion.getOffset(), arrayIndex, true, true, true, false);
//					if (region == null) break;
//					attributes.put(IMarker.CHAR_START, region.getOffset());
//					attributes.put(IMarker.CHAR_END, region.getOffset() + region.getLength());
//				}
//				break;			
//			case ARRAY:
//				if (statement instanceof ArrayAccess) {
//					ArrayAccess aa = (ArrayAccess)statement;
//					String arrayIndex = "[" + aa.getIndex() + "]";
//					IRegion region = findAdapter.find(lineRegion.getOffset(), arrayIndex, true, true, true, false);
//					if (region == null) break;
//					attributes.put(IMarker.CHAR_START, region.getOffset());
//					attributes.put(IMarker.CHAR_END, region.getOffset() + region.getLength());
//				}
//				break;
//			// メソッドからの出口
//			case ACTUAL_ARGUMENT:
//				if (statement instanceof MethodInvocation) {
//					MethodInvocation mi = (MethodInvocation)statement;
//					MethodExecution me = mi.getCalledMethodExecution();
//					String signature = me.getSignature();
//					signature = signature.substring(signature.lastIndexOf(".") + 1, signature.lastIndexOf("("));
//					IRegion region = findAdapter.find(lineRegion.getOffset(), signature, true, true, true, false);
//					if (region == null) break;
//					attributes.put(IMarker.CHAR_START, region.getOffset());
//					attributes.put(IMarker.CHAR_END, region.getOffset() + region.getLength());
//				}
//				break;
//			case RECEIVER:
//				if (statement instanceof MethodInvocation) {
//					MethodInvocation mi = (MethodInvocation)statement;
//					MethodExecution me = mi.getCalledMethodExecution();
//					String signature = me.getSignature();
//					signature = signature.substring(signature.lastIndexOf(".") + 1, signature.lastIndexOf("("));
//					IRegion region = findAdapter.find(lineRegion.getOffset(), signature, true, true, true, false);
//					if (region == null) break;
//					attributes.put(IMarker.CHAR_START, region.getOffset());
//					attributes.put(IMarker.CHAR_END, region.getOffset() + region.getLength());
//				}
//				break;
//			case RETURN_VALUE:
//				if (statement instanceof FieldAccess) {
//					FieldAccess fa = (FieldAccess)statement;
//					String fieldName = fa.getFieldName();
//					IRegion region = findAdapter.find(lineRegion.getOffset(), fieldName, true, true, true, false);
//					if (region == null) break;
//					attributes.put(IMarker.CHAR_START, region.getOffset());
//					attributes.put(IMarker.CHAR_END, region.getOffset() + region.getLength());				
//				}
//				break;
//			}
//			attributes.put(IMarker.MESSAGE, message.toString());
//			attributes.put(IMarker.LINE_NUMBER, alias.getLineNo());
//		} catch (CoreException | BadLocationException e) {
//			e.printStackTrace();
//		}
//	}

	private void setAttributesForMethodExecution(Map<String, Object> attributes, MethodExecution methodExecution, IFile file, int lineNo, String markerId) {
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
				IMethod method = JavaEditorOperator.findIMethod(methodExecution, type);
				int start = method.getSourceRange().getOffset();
				int end = start + method.getSource().indexOf(")") + 1;
				attributes.put(IMarker.CHAR_START, start);
				attributes.put(IMarker.CHAR_END, end);
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
