package org.ntlab.traceDebugger;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ClassInfo;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TraceJSON;

public class JavaEditorOperator {
//	private static List<IMarker> markers = new ArrayList<>();

	/**
	 * �����œn����meCaller���ɂ���methodExecution����`����Ă���N���X�̃\�[�X�R�[�h��Ώ�Eclipse�̃G�f�B�^�ŊJ������
	 * 
	 * @param methodExecution
	 */
	public static void openSrcFileOfMethodExecution(MethodExecution methodExecution, int highlightLineNo) {
		IType type = findIType(methodExecution);
		if (type != null) {
			IMethod method = findIMethod(methodExecution, type);
			openSrcFile(type, method);
			highlightCurrentJavaFile(highlightLineNo);
		}
	}

	/**
	 * �����œn����IType��IMethod�ɑΉ�����\�[�X�R�[�h��Ώ�Eclipse�̃G�f�B�^�ŊJ������
	 * 
	 * @param type
	 * @param method
	 */
	private static void openSrcFile(IType type, IMethod method) {
		openInJavaEditor(type, method);
	}
	
	/**
	 * ���݃G�f�B�^�ŊJ����Ă���t�@�C���̎w�肵���s�Ƀn�C���C�g��������
	 * @param lineNo �n�C���C�g��������s��
	 */
	public static void highlightCurrentJavaFile(int lineNo) {
		if (lineNo < 1) return;
		IWorkbench workbench = PlatformUI.getWorkbench();
		IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
		IEditorPart editorPart = window.getActivePage().getActiveEditor();
		if (editorPart instanceof ITextEditor) {
			ITextEditor editor = (ITextEditor)editorPart;
			IDocumentProvider provider = editor.getDocumentProvider();
			IDocument document = provider.getDocument(editor.getEditorInput());
			try {
				editor.selectAndReveal(document.getLineOffset(lineNo - 1), document.getLineLength(lineNo - 1));
//				tmp();
			} catch (BadLocationException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static void markAndOpenJavaFile(IMarker marker) {
		IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();			
		try {
			IDE.openEditor(page, marker);
		} catch (PartInitException e) {
			e.printStackTrace();
		}
	}	
	
//	public static void markAndOpenJavaFile(Alias alias, int lineNo, String markerId) {
//		try {
//			IFile file = findIFile(alias.getMethodExecution());
//			DeltaMarkerManager mgr = DeltaMarkerManager.getInstance();
//			IMarker marker = mgr.addMarker(alias, file, lineNo, markerId);		
//			IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();			
//			IDE.openEditor(page, marker);
//		} catch (PartInitException e) {
//			e.printStackTrace();
//		}
//	}

//	public static void markAndOpenJavaFile(MethodExecution methodExecution, int lineNo, String findString, String message, String markerId) {
//		IFile file = findIFile(methodExecution);	
//		try {
//			FileEditorInput input = new FileEditorInput(file);
//			FileDocumentProvider provider = new FileDocumentProvider();
//			provider.connect(input);
//			IDocument document = provider.getDocument(input);
//			FindReplaceDocumentAdapter findAdapter = new FindReplaceDocumentAdapter(document);			
//			IMarker marker = file.createMarker(markerId);
//			Map<String, Object> attributes = new HashMap<>();
//			attributes.put(IMarker.MESSAGE, message);
//			attributes.put(IMarker.TRANSIENT, true);
//			if (lineNo > 1) {
//				// ����s���̎w�肵�������񕔕����n�C���C�g (���݂��Ȃ��ꍇ�͓���s�S�̂��n�C���C�g)
//				IRegion lineRegion = document.getLineInformation(lineNo - 1);
//				IRegion findStringRegion = findAdapter.find(lineRegion.getOffset(), findString, true, true, true, false);
//				if (findStringRegion != null) {
//					attributes.put(IMarker.CHAR_START, findStringRegion.getOffset());
//					attributes.put(IMarker.CHAR_END, findStringRegion.getOffset() + findStringRegion.getLength());
//				} else {
//					attributes.put(IMarker.CHAR_START, lineRegion.getOffset());
//					attributes.put(IMarker.CHAR_END, lineRegion.getOffset() + lineRegion.getLength());					
//				}
//				attributes.put(IMarker.LINE_NUMBER, lineNo);
//			} else {
//				// ���\�b�h�V�O�l�`�����n�C���C�g
//				IType type = findIType(methodExecution);
//				IMethod method = findIMethod(methodExecution, type);
//				int start = method.getSourceRange().getOffset();
//				int end = start + method.getSource().indexOf(")") + 1;
//				attributes.put(IMarker.CHAR_START, start);
//				attributes.put(IMarker.CHAR_END, end);
//			}
//
//			marker.setAttributes(attributes);
//			markers.add(marker);
//			IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
//			IDE.openEditor(page, marker);
//		} catch (CoreException | BadLocationException e) {
//			e.printStackTrace();
//		}
//	}
	
//	private static void tmp() {
//		IBreakpointManager mgr = DebugPlugin.getDefault().getBreakpointManager();
//		IBreakpoint[] bps = mgr.getBreakpoints();
//		for (IBreakpoint bp : bps) {
//			IMarker marker = bp.getMarker();
//			int lineNo = marker.getAttribute(IMarker.LINE_NUMBER, -1);
//			String name = marker.getAttribute("org.eclipse.jdt.debug.core.typeName", "");
////			TraceJSON trace = (TraceJSON)TraceDebuggerPlugin.getAnalyzer().getTrace();
////			ClassInfo classInfo = trace.getClassInfo(name);
////			if (classInfo == null) continue;
//			
//			
////			name = mgr.getTypeName(bp);
//			try {
//				for (Map.Entry<String, Object> entry: marker.getAttributes().entrySet()) {
//					System.out.println(entry.getKey() + ": " + entry.getValue());
//				}
//			} catch (CoreException e) {
//				e.printStackTrace();
//			}
//			System.out.println("Name: " + name + ", lineNo: " + lineNo);
//		}
//	}
	
	public static IFile findIFile(MethodExecution methodExecution) {
		TraceJSON trace = (TraceJSON)TraceDebuggerPlugin.getAnalyzer().getTrace();
		String declaringClassName = methodExecution.getDeclaringClassName();
		declaringClassName = declaringClassName.replace(".<clinit>", "");
		String tmp = trace.getClassInfo(declaringClassName).getPath();
		IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		String projectName = "";
		for (IProject project : projects) {
			projectName = project.getFullPath().toString();
			if (tmp.contains(projectName + "/")) break;
		}
		tmp = tmp.replace(tmp.substring(0, tmp.lastIndexOf(projectName)), "");
		tmp = tmp.replace("/bin/", "/src/");
		tmp = tmp.replace(".class", ".java");
		String filePath = tmp;
		IPath path = new Path(filePath);
		return ResourcesPlugin.getWorkspace().getRoot().getFile(path);
	}

//	public static void deleteMarkers(String markerId) {
//		Iterator<IMarker> it = markers.iterator();
//		while (it.hasNext()) {
//			IMarker marker = it.next();
//			try {
//				if (marker.getType().equals(markerId)) {
//					marker.delete();
//					it.remove();
//				}
//			} catch (CoreException e) {
//				e.printStackTrace();
//			}
//		}
//	}

	public static IType findIType(MethodExecution methodExecution) {		
		String declaringClassName = methodExecution.getDeclaringClassName();
		declaringClassName = declaringClassName.replace(".<clinit>", "");
		return findIType(methodExecution, declaringClassName);
	}
	
	public static IType findIType(MethodExecution methodExecution, String declaringClassName) {
		String projectPath = getLoaderPath(methodExecution, declaringClassName);
		IType type = null;
		if (projectPath != null) {
			IJavaProject javaProject = findJavaProject(projectPath);
			if (javaProject != null) {
				try {
					type = javaProject.findType(declaringClassName);
				} catch (JavaModelException e) {
					e.printStackTrace();
				}
			}
		}
		return type;		
	}

	private static String getLoaderPath(MethodExecution methodExecution, String declaringClassName) {
		TraceJSON traceJSON = (TraceJSON)TraceDebuggerPlugin.getAnalyzer().getTrace();
		ClassInfo classInfo = traceJSON.getClassInfo(declaringClassName);
		if (classInfo == null && methodExecution != null) {
			declaringClassName = methodExecution.getThisClassName();			
			classInfo = traceJSON.getClassInfo(declaringClassName);
		}
		String loaderPath = null;
		if (classInfo != null) {
			// �Ȃ���loaderPath���擾�ł��Ă��Ȃ����߁A���ۂɏo�͂����JSON�g���[�X���Q�l�ɂ���path����^���Ă݂�
			String path = classInfo.getPath();
			String declaringClassNameString = declaringClassName.replace(".", "/");
			loaderPath = path.substring(0, path.lastIndexOf(declaringClassNameString)); // path����N���X�̊��S���薼�ȍ~��S�ĊO�������̂�projectPath�ɂ��Ă݂�
		}
		return loaderPath;
	}

	private static IJavaProject findJavaProject(String projectPath) {
		IJavaProject javaProject = null;
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IProject[] projects = root.getProjects();
		for (IProject project : projects) {
			if (checkProjectPath(project, projectPath)) {
				javaProject = JavaCore.create(project);
				break;
			}
		}
		return javaProject;
	}

	private static boolean checkProjectPath(IProject project, String projectPath) {
		String projectLocation = project.getLocation().toString();
		if (projectPath.startsWith(projectLocation)) {
			String[] projectPathSplit = projectPath.split(projectLocation);
			return (projectPathSplit[1].charAt(0) == '/'); // �v���W�F�N�g���̑O����v�ɂ�鑼�v���W�F�N�g�Ƃ̌딻��������
		}
		return false;
	}

	private static void openInJavaEditor(IType type, IMethod method) {
		try {
			if (type != null) {
				IEditorPart editor = JavaUI.openInEditor(type);
				if (!type.isLocal() && !type.isMember()) {
					if (method != null) {
						JavaUI.revealInEditor(editor, (IJavaElement)method);
					} else {
						JavaUI.revealInEditor(editor, (IJavaElement)type);
					}
				}
			}
		} catch (PartInitException | JavaModelException e) {
			e.printStackTrace();
		}		
	}
	
	public static IMethod findIMethod(MethodExecution methodExecution, IType type) {
		IMethod method = null;
		if (type != null) {
			String methodSignature = methodExecution.getSignature();
			method = findIMethod(type, methodSignature);
		}
		return method;
	}

	public static IMethod findIMethod(IType type, String methodSignature) {
		try {
			for (IMethod method : type.getMethods()) {
				if (checkMethodSignature(type, method, methodSignature)) {
					return method;
				}
			}
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
		return null;
	}

	private static boolean checkMethodSignature(IType type, IMethod method, String methodSignature) {
		String fqcn = type.getFullyQualifiedName();
		try {
			StringBuilder jdtMethodSignature = new StringBuilder();
			jdtMethodSignature.append((method.isConstructor()) ? (fqcn + "(") : (fqcn + "." + method.getElementName() + "("));
			if (methodSignature.contains(jdtMethodSignature)) {
				// ������v���Ă����ꍇ�Ɍ���A�������X�g����������ł���v���邩�ǂ����𔻒� (�I�[�o�[���[�h�ɂ��딻��������)
				jdtMethodSignature.append(String.join(",", parseFQCNParameters(type, method)) + ")"); // �S�����̃V�O�l�`�������S����N���X���ɕϊ�����,�ŋ�؂���������
				return methodSignature.contains(jdtMethodSignature);
			}
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
		return false;
	}

	/**
	 * IMethod����擾�ł���S�����̃V�O�l�`�������S����N���X���ɒu�������Ċi�[�������X�g��Ԃ�
	 * 
	 * @param type
	 * @param method
	 * @return
	 */
	private static List<String> parseFQCNParameters(IType type, IMethod method) throws JavaModelException {
		List<String> parameters = new ArrayList<>();
		for (String parameterType : method.getParameterTypes()) {
			String readableType = Signature.toString(parameterType); // �V�O�l�`���̕�������^���̕�����ɕϊ� (�z��͌���[]����)
			String[] readableTypeSplit = { "", "" }; // �^����[]�̕����p
			int firstBracketIndex = readableType.indexOf("[]");
			final int NO_BRACKET_INDEX = -1;
			if (firstBracketIndex == NO_BRACKET_INDEX) {
				readableTypeSplit[0] = readableType; // �^��
			} else {
				readableTypeSplit[0] = readableType.substring(0, firstBracketIndex); // �^��
				readableTypeSplit[1] = readableType.substring(firstBracketIndex); // �^���̌���[]�S��
			}
			String[][] resolveType = type.resolveType(readableTypeSplit[0]); // �p�b�P�[�W���ƃN���X���̑g�ݍ��킹���擾
			if (resolveType != null) {
				if (resolveType[0][0].isEmpty()) {
					readableTypeSplit[0] = (resolveType[0][1]); // �f�t�H���g�p�b�P�[�W�̏ꍇ�̓p�b�P�[�W����.�͓���Ȃ�
				} else {
					readableTypeSplit[0] = (resolveType[0][0] + "." + resolveType[0][1]); // ���S����N���X��
				}
			}
			parameters.add(readableTypeSplit[0] + readableTypeSplit[1]);
		}
		return parameters;
	}
}
