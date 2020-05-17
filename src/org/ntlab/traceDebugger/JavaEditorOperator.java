package org.ntlab.traceDebugger;

import org.eclipse.core.resources.IMarker;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
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
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;

public class JavaEditorOperator {

	/**
	 * 引数で渡したmeCaller内にあるmethodExecutionが定義されているクラスのソースコードを対象Eclipseのエディタで開かせる
	 * 
	 * @param methodExecution
	 */
	public static void openSrcFileOfMethodExecution(MethodExecution methodExecution, int highlightLineNo) {
		IType type = JavaElementFinder.findIType(methodExecution);
		if (type != null) {
			IMethod method = JavaElementFinder.findIMethod(methodExecution, type);
			openSrcFile(type, method);
			highlightCurrentJavaFile(highlightLineNo);
		}
	}
	
	/**
	 * 現在エディタで開かれているファイルの指定した行にハイライトをかける
	 * @param lineNo ハイライトをかける行数
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

	/**
	 * 引数で渡したITypeとIMethodに対応するソースコードを対象Eclipseのエディタで開かせる
	 * 
	 * @param type
	 * @param method
	 */
	private static void openSrcFile(IType type, IMethod method) {
		openInJavaEditor(type, method);
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
}
