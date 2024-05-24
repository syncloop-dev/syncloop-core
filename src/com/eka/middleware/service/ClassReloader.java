package com.eka.middleware.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.eka.lite.service.DataPipeline;
import com.eka.middleware.template.SnippetException;

public class ClassReloader extends ClassLoader {
	
	public static Logger LOGGER = LogManager.getLogger(ClassReloader.class);
	
	final DataPipeline dp;
	final Map<String, byte[]> jeMap = new HashMap<String, byte[]>();
	
	final CustomClassLoader parent;

	public ClassReloader(String fqn, String path, CustomClassLoader p, DataPipeline dp) {
		this.dp = dp;
		parent=p;
		if (path != null && path.length() > 0)
			try {
				File file=new File(path);
				byte[] bytes=ServiceUtils.readAllBytes(file);
				jeMap.put(fqn, bytes);
				//loadCustomJars(fqn, path);
			} catch (Exception e) {
				ServiceUtils.printException(dp, "Loading custom jars failed: ", e);
			}
	}
	
	@Override
	public Class<?> loadClass(String name) throws ClassNotFoundException {

		try {
			byte[] classBytes = null;

			if (jeMap != null && jeMap.size() > 0)
				classBytes = jeMap.get(name);
			if (classBytes == null) {
				LOGGER.trace("Loading class '" + name + "'");
				try {
					return Class.forName(name);
				} catch (Exception e) {
					return super.loadClass(name);
				}
			} else {
				LOGGER.trace("Loading class from bytes'" + name + "'");
				try {
					
				} catch (Exception e) {
					return parent.findMyLoadedClass(name);
				}
				Class clz=defineClass(name, classBytes, 0, classBytes.length);
				return clz;
			}
		} catch (Exception e) {
			ServiceUtils.printException(dp, "Class Not found '" + name + "'", e);
			throw new ClassNotFoundException(name);
		}
	}
	
	private byte[] loadClassFromFile(String fileName, DataPipeline dp) throws SnippetException {
		// System.out.println(fileName);

		fileName = fileName.replace('.', '/') + ".class";
		// System.out.println(fileName);
		String packagePath = PropertyManager.getPackagePath(dp.rp.getTenant());
		URI path = null;
		try {
			path = new File((packagePath + fileName.replace("packages/packages", "packages")).replace("//", "/"))
					.toURI();// ServiceManager.class.getResource(fileName).toURI();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			ServiceUtils.printException(dp, "Failed to create URI '"
					+ (packagePath + fileName.replace("packages/packages", "packages")).replace("//", "/") + "'", e);
		}
		InputStream inputStream = null;
		File file = new File(path);
		if(!file.exists())
			return null;
		byte[] buffer = null;
		try {
			inputStream = new FileInputStream(file);

			ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
			int nextValue = 0;
			try {
				while ((nextValue = inputStream.read()) != -1) {
					byteStream.write(nextValue);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			buffer = byteStream.toByteArray();
			try {
				inputStream.close();
				inputStream = null;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				ServiceUtils.printException(dp, "Failed to close inputStream", e);
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			ServiceUtils.printException(dp, "Class file not found '"
					+ (packagePath + fileName.replace("packages/packages", "packages")).replace("//", "/") + "'", e);
		} finally {
			// if(file!=null && file.exists())
			// file.delete();
			if (inputStream != null)
				try {
					inputStream.close();
				} catch (IOException e) {
					ServiceUtils.printException(dp, "Failed to close inputStream in finally block", e);
				}
		}
		return buffer;
	}

	@Override
	public Class findClass(String name) throws ClassNotFoundException {
		// System.out.println(name);
		byte[] b;
		try {
			b = loadClassFromFile(name, dp);
			if(b==null)
				return parent.findMyLoadedClass(name);
			else
				return defineClass(name, b, 0, b.length);
		} catch (Throwable e) {
			throw new ClassNotFoundException(name,e);
		}
	}

}
