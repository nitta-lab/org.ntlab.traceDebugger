package org.ntlab.traceDebugger;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ClassInfo;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TraceJSON;

public class JavaElementFinder {
	
	public static IFile findIFile(MethodExecution methodExecution) {
		TraceJSON trace = (TraceJSON)TraceDebuggerPlugin.getAnalyzer().getTrace();
		String declaringClassName = methodExecution.getDeclaringClassName();
		declaringClassName = declaringClassName.replace(".<clinit>", "");
		ClassInfo info = trace.getClassInfo(declaringClassName);
		if (info == null) {
			// 無名クラスの場合はその外のクラス名でClassInfoを取得する (Javaのソースファイルを取得するため)
			StringBuilder tmp = new StringBuilder();
			tmp.append(declaringClassName.substring(0, declaringClassName.lastIndexOf(".")));
			info = trace.getClassInfo(tmp.toString());
		}
		if (info == null) return null;

		String tmp = info.getPath();
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IProject[] projects = workspace.getRoot().getProjects();
		String projectName = "";
		String srcForderName = "/src";
		String outputClassPath = null;
		IPath projectOutputLocation = null;
		boolean hasFoundSrcForderName = false;
		for (IProject project : projects) {
			projectName = project.getFullPath().toString();
			if (!(tmp.contains(projectName + "/"))) continue;
			IJavaProject javaProject = JavaCore.create(project);
			try {
				projectOutputLocation = javaProject.getOutputLocation();		// プロジェクト全体の出力フォルダ
				for (IClasspathEntry entry : javaProject.getResolvedClasspath(true)) {
					if (entry.getEntryKind() != IClasspathEntry.CPE_SOURCE) continue;
					IPath srcForderPath = entry.getPath();
					srcForderName = srcForderPath.toString();				
					IPath outputLocation = entry.getOutputLocation();		// ソース毎の出力フォルダ
					if (outputLocation != null) {
						URI path = PathUtility.workspaceRelativePathToAbsoluteURI(outputLocation, workspace);
						outputClassPath = PathUtility.URIPathToPath(path.getPath());
					}
					hasFoundSrcForderName = true;
					break;
				}
			} catch (JavaModelException e) {
				e.printStackTrace();
			}
			if (hasFoundSrcForderName) break;
		}
		if (outputClassPath == null && projectOutputLocation != null) {
			URI path = PathUtility.workspaceRelativePathToAbsoluteURI(projectOutputLocation, workspace);
			outputClassPath = PathUtility.URIPathToPath(path.getPath());
		}
//		tmp = tmp.replace(tmp.substring(0, tmp.lastIndexOf(projectName)), "");
		if (outputClassPath != null && tmp.startsWith(outputClassPath)) {
			tmp = srcForderName + tmp.substring(outputClassPath.length());			
		} else {
			tmp = tmp.replace(tmp.substring(0, tmp.indexOf(projectName)), "");
			tmp = tmp.replace("/bin", srcForderName.substring(srcForderName.lastIndexOf("/")));
		}
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
			// なぜかloaderPathが取得できていないため、実際に出力されるJSONトレースを参考にしてpathから真似てみる
			String path = classInfo.getPath();
			String declaringClassNameString = declaringClassName.replace(".", "/");
			loaderPath = path.substring(0, path.lastIndexOf(declaringClassNameString)); // pathからクラスの完全限定名以降を全て外したものをprojectPathにしてみる
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
//		if (projectPath.startsWith(projectLocation)) {
//			String[] projectPathSplit = projectPath.split(projectLocation);
//			return (projectPathSplit[1].charAt(0) == '/'); // プロジェクト名の前方一致による他プロジェクトとの誤判定を避ける
//		}
		if (projectPath.startsWith(projectLocation)) {
			String[] projectPathSplit = projectPath.split(projectLocation);
			return (projectPathSplit[1].charAt(0) == '/');  // プロジェクト名の前方一致による他プロジェクトとの誤判定を避ける
		} else if (projectPath.startsWith(projectLocation.substring(1))) {
			String[] projectPathSplit = projectPath.split(projectLocation.substring(1));
			return (projectPathSplit[1].charAt(0) == '/');  // プロジェクト名の前方一致による他プロジェクトとの誤判定を避ける
		}
		return false;
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
				// 部分一致していた場合に限り、引数リストを加えた上でも一致するかどうかを判定 (オーバーロードによる誤判定を避ける)
				jdtMethodSignature.append(String.join(",", parseFQCNParameters(type, method)) + ")"); // 全引数のシグネチャを完全限定クラス名に変換して,で区切った文字列
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
					return (resolveTypes[0][1]); // デフォルトパッケージの場合はパッケージ名と.は入れない
				} else {
					return (resolveTypes[0][0] + "." + resolveTypes[0][1]); // 完全限定クラス名
				}
			}
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * IMethodから取得できる全引数のシグネチャを完全限定クラス名に置き換えて格納したリストを返す
	 * 
	 * @param type
	 * @param method
	 * @return
	 */
	private static List<String> parseFQCNParameters(IType type, IMethod method) throws JavaModelException {
		List<String> parameters = new ArrayList<>();
		for (String parameterType : method.getParameterTypes()) {
			String readableType = Signature.toString(parameterType); // シグネチャの文字列を型名の文字列に変換 (配列は後ろに[]がつく)
			String[] readableTypeSplit = { "", "" }; // 型名と[]の分割用
			int firstBracketIndex = readableType.indexOf("[]");
			final int NO_BRACKET_INDEX = -1;
			if (firstBracketIndex == NO_BRACKET_INDEX) {
				readableTypeSplit[0] = readableType; // 型名
			} else {
				readableTypeSplit[0] = readableType.substring(0, firstBracketIndex); // 型名
				readableTypeSplit[1] = readableType.substring(firstBracketIndex); // 型名の後ろの[]全て
			}
			String tmp = resolveType(type, readableTypeSplit[0]);
			if (tmp != null) {
				readableTypeSplit[0] = tmp;
			}		
			parameters.add(readableTypeSplit[0] + readableTypeSplit[1]);
		}
		return parameters;
	}	
}
