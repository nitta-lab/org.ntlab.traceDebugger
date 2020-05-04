package org.ntlab.traceDebugger;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
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

	public static IFile findIFile(MethodExecution methodExecution) {
		TraceJSON trace = (TraceJSON)TraceDebuggerPlugin.getAnalyzer().getTrace();
		String declaringClassName = methodExecution.getDeclaringClassName();
		declaringClassName = declaringClassName.replace(".<clinit>", "");
		ClassInfo info = trace.getClassInfo(declaringClassName);
		if (info == null) return null;

		String tmp = info.getPath();
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IProject[] projects = workspace.getRoot().getProjects();
		String projectName = "";
		String srcForderName = "/src";
		boolean hasFoundSrcForderName = false;
		for (IProject project : projects) {
			projectName = project.getFullPath().toString();
			if (!(tmp.contains(projectName + "/"))) continue;
			IJavaProject javaProject = JavaCore.create(project);
			try {
				for (IClasspathEntry entry : javaProject.getResolvedClasspath(true)) {
					if (entry.getEntryKind() != IClasspathEntry.CPE_SOURCE) continue;
					IPath srcForderPath = entry.getPath();
					srcForderName = srcForderPath.toString();
					hasFoundSrcForderName = true;
					break;
				}
			} catch (JavaModelException e) {
				e.printStackTrace();
			}
			if (hasFoundSrcForderName) break;
		}
//		tmp = tmp.replace(tmp.substring(0, tmp.lastIndexOf(projectName)), "");
		tmp = tmp.replace(tmp.substring(0, tmp.indexOf(projectName)), "");
		tmp = tmp.replace("/bin", srcForderName.substring(srcForderName.lastIndexOf("/")));
		tmp = tmp.replace(".class", ".java");
		String filePath = tmp;
		IPath path = new Path(filePath);
		return ResourcesPlugin.getWorkspace().getRoot().getFile(path);
	}

	public static IType findIType(MethodExecution methodExecution) {		
		String declaringClassName = methodExecution.getDeclaringClassName();
		declaringClassName = declaringClassName.replace(".<clinit>", "");
		return findIType(methodExecution, declaringClassName);
	}

	public static IType findIType(MethodExecution methodExecution, String declaringClassName) {
		String projectPath = getLoaderPath(methodExecution, declaringClassName);
//		if (projectPath == null) projectPath = previousJavaProjectPath;
		if (projectPath != null) {
			IJavaProject javaProject = findJavaProject(projectPath);
			if (javaProject != null) {
//				previousJavaProjectPath = projectPath;
				try {
					return javaProject.findType(declaringClassName);
				} catch (JavaModelException e) {
					e.printStackTrace();
				}
			}			
		}
		return null;
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

	public static String resolveType(IType type, String typeName) {
		try {
			String[][] resolveTypes = type.resolveType(typeName);
			if (resolveTypes != null) {
				if (resolveTypes[0][0].isEmpty()) {
					return (resolveTypes[0][1]); // �f�t�H���g�p�b�P�[�W�̏ꍇ�̓p�b�P�[�W����.�͓���Ȃ�
				} else {
					return (resolveTypes[0][0] + "." + resolveTypes[0][1]); // ���S����N���X��
				}
			}
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
		return null;
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
			String tmp = resolveType(type, readableTypeSplit[0]);
			if (tmp != null) {
				readableTypeSplit[0] = tmp;
			}		
			parameters.add(readableTypeSplit[0] + readableTypeSplit[1]);
		}
		return parameters;
	}
	
//	/**
//	 * IMethod����擾�ł���S�����̃V�O�l�`�������S����N���X���ɒu�������Ċi�[�������X�g��Ԃ�
//	 * 
//	 * @param type
//	 * @param method
//	 * @return
//	 */
//	private static List<String> parseFQCNParameters(IType type, IMethod method) throws JavaModelException {
//		List<String> parameters = new ArrayList<>();
//		for (String parameterType : method.getParameterTypes()) {
//			String readableType = Signature.toString(parameterType); // �V�O�l�`���̕�������^���̕�����ɕϊ� (�z��͌���[]����)
//			String[] readableTypeSplit = { "", "" }; // �^����[]�̕����p
//			int firstBracketIndex = readableType.indexOf("[]");
//			final int NO_BRACKET_INDEX = -1;
//			if (firstBracketIndex == NO_BRACKET_INDEX) {
//				readableTypeSplit[0] = readableType; // �^��
//			} else {
//				readableTypeSplit[0] = readableType.substring(0, firstBracketIndex); // �^��
//				readableTypeSplit[1] = readableType.substring(firstBracketIndex); // �^���̌���[]�S��
//			}
//			String[][] resolveType = type.resolveType(readableTypeSplit[0]); // �p�b�P�[�W���ƃN���X���̑g�ݍ��킹���擾
//			if (resolveType != null) {
//				if (resolveType[0][0].isEmpty()) {
//					readableTypeSplit[0] = (resolveType[0][1]); // �f�t�H���g�p�b�P�[�W�̏ꍇ�̓p�b�P�[�W����.�͓���Ȃ�
//				} else {
//					readableTypeSplit[0] = (resolveType[0][0] + "." + resolveType[0][1]); // ���S����N���X��
//				}
//			}
//			parameters.add(readableTypeSplit[0] + readableTypeSplit[1]);
//		}
//		return parameters;
//	}
}
