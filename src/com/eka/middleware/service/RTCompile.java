package com.eka.middleware.service;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.eka.middleware.flow.FlowUtils;

//import sun.jvm.hotspot.HelloWorld;

public class RTCompile {
	public static Logger LOGGER = LogManager.getLogger(RTCompile.class);
	public static final Map<String, CustomClassLoader> classLoaderMap=new ConcurrentHashMap<String, CustomClassLoader>();

	public static Class getClassRef(String fqn, String path, boolean compile, DataPipeline dp) throws Throwable {
		List<String> options = new ArrayList<String>();
		options.add("-cp");
		String sep = ServiceUtils.getSeparator();

		String packageName = fqn.split(Pattern.quote("."))[1];
		String localJarsPath = packageName + "/dependency/jars/";
		String globalJarsPath = "global/dependency/jars/";

		String currentPath = System.getProperty("java.class.path");
		String packagePath= PropertyManager.getPackagePath(dp.rp.getTenant());
		
		LOGGER.trace("Path Jars: "+currentPath);
		LOGGER.trace("Package Jars: "+localJarsPath);
		LOGGER.trace("Global Jars: "+globalJarsPath);
		String paths[] = ServiceUtils.getJarPaths(localJarsPath,packagePath+"packages/");
		//URL localURLs[] = ServiceUtils.getJarURLs(localJarsPath);
		//URL currentURLs[]=ServiceUtils.getClassesURLs(currentPath);
		
		localJarsPath = "";
		if (paths != null && paths.length > 0)
			for (String jp : paths) {
				if (jp != null && jp.length() > 0)
					localJarsPath += jp + sep;
			}

		String globalPaths[] = ServiceUtils.getJarPaths(globalJarsPath,packagePath+"packages/");
		//URL globalURLs[] = ServiceUtils.getJarURLs(globalJarsPath);
		globalJarsPath = "";
		if (globalPaths != null && globalPaths.length > 0)
			for (String jp : globalPaths) {
				if (jp != null && jp.length() > 0)
					globalJarsPath += jp + sep;
			}
		options.add(localJarsPath + globalJarsPath + currentPath);
		//System.setProperty("java.class.path", localJarsPath + globalJarsPath + currentPath);
		LOGGER.trace("newClassPath: "+localJarsPath+globalJarsPath+currentPath);
		options.add(path);

		int result = 0;

		String compiledClassPath = path.replace(".java", ".class");
		File classFile = new File(compiledClassPath);
		if (!classFile.isFile())
			compile = true;
		String error = null;
		if (compile) {
			OutputStream output = new OutputStream() {
				private StringBuilder sb = new StringBuilder();

				@Override
				public void write(int b) throws IOException {
					this.sb.append((char) b);
				}

				@Override
				public String toString() {
					return this.sb.toString();
				}
			};
			// LOGGER.info(options.toString());
			JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
			result = compiler.run(null, null, output, options.toArray(new String[options.size()]));// ("-cp "+new
																									// File(paths[0]).toURI()+"
																									// "+path));
			if (output != null)
				error = output.toString();
			FlowUtils.resetJSCB(fqn + ".main");
			LOGGER.trace("Compiling '" + fqn + "'.\nCompiler response is " + result);
		}
		Class cls = null;
			if (result == 0) {
				ArrayList<String> pathArrays = new ArrayList<String>();
				Set<URL> URLs = new HashSet<URL>();
				//URLs.addAll(Arrays.asList(currentURLs));
				if (paths != null && paths.length > 0) {
					pathArrays.addAll(Arrays.asList(paths));
					//URLs.addAll(Arrays.asList(localURLs));
				}

				if (globalPaths != null && globalPaths.length > 0) {
					pathArrays.addAll(Arrays.asList(globalPaths));
					//URLs.addAll(Arrays.asList(globalURLs));
				}

				String jarPaths[] = new String[pathArrays.size()];
				jarPaths = pathArrays.toArray(jarPaths);
				
				//URLClassLoader classLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
				//URLClassLoader child = new URLClassLoader (URLs.toArray(new URL[URLs.size()]), ClassLoader.getSystemClassLoader());
				//cls=Class.forName (fqn, true, child);
				CustomClassLoader classLoader=classLoaderMap.get(dp.rp.getTenant().getName());
				if(classLoader==null) {
					classLoader = new CustomClassLoader(fqn, jarPaths, ClassLoader.getSystemClassLoader(),dp);// (CustomClassLoader)
					classLoaderMap.put(dp.rp.getTenant().getName(), classLoader);
				}else {
					classLoader.resetClassLoader(fqn, jarPaths, ClassLoader.getSystemClassLoader(),dp);
				}
				//CustomClassLoader classLoader = new CustomClassLoader(fqn, jarPaths, ClassLoader.getSystemClassLoader(),dp);// (CustomClassLoader)
				cls = classLoader.findClass(fqn);
			} else
				throw new Exception("Compilation failed '" + fqn + "'\n " + error);
		return cls;
	}
	
//	private static URL loadClassFromFile1(String fqn) throws Exception {
//		// System.out.println(fileName);
//		String packagePath=PropertyManager.getPackagePath();
//		fqn = fqn.replace('.', '/') + ".class";
//		// System.out.println(fileName);
//		URI path = null;
//		try {
//			path = new File((packagePath + fqn.replace("packages/packages", "packages")).replace("//", "/")).toURI();// ServiceManager.class.getResource(fileName).toURI();
//		} catch (Exception e) {
//			// TODO Auto-generated catch block
//			ServiceUtils.printException(
//					"Failed to create URI '" + ( packagePath + fqn.replace("packages/packages", "packages")).replace("//", "/") + "'", e);
//		}
//		
//		return path.toURL();
//	}
	
}