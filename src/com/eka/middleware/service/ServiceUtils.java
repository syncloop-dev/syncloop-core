package com.eka.middleware.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
//import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.crypto.spec.SecretKeySpec;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Value;

import com.eka.middleware.heap.CacheManager;
import com.eka.middleware.heap.HashMap;
import com.eka.middleware.template.Tenant;
import com.eka.middleware.flow.FlowResolver;
import com.eka.middleware.pooling.ScriptEngineContextManager;
import com.eka.middleware.server.ServiceManager;
import com.eka.middleware.template.SnippetException;
import com.eka.middleware.template.SystemException;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;


public class ServiceUtils {
	// private static final Properties serverProperties = new Properties();
	// private static final Properties urlMappings = new Properties();

	private static final Map<String, Properties> aliasMap = new ConcurrentHashMap<String, Properties>();

	private static final ObjectMapper om = new ObjectMapper();//CustomObjectSerializer.om;//
	public static final XmlMapper xmlMapper = new XmlMapper();
	public static Logger LOGGER = LogManager.getLogger(ServiceUtils.class);
	private static final String OS = System.getProperty("os.name").toLowerCase();

	public static boolean isWindows() {
		return (OS.indexOf("win") >= 0);
	}

	public static List<String> searchEndpoints(final String keyword, Tenant tenant) {
		Properties urlMappings = getUrlAliasMapping(tenant);
		Set endpoints = urlMappings.keySet();
		final List<String> endpointList = new ArrayList<>();
		endpoints.forEach((k) -> {
			String key = k + "";
			String value = urlMappings.getProperty(key, "");
			if (keyword == null || keyword.trim().length() == 0 || value.toLowerCase().contains(keyword.toLowerCase())
					|| key.toLowerCase().trim().contains(keyword.toLowerCase()))
				endpointList.add(key + " : " + value);
		});
		return endpointList;
	}

	public static boolean isMac() {
		return (OS.indexOf("mac") >= 0);
	}

	public static boolean isUnix() {
		return (OS.indexOf("nix") >= 0 || OS.indexOf("nux") >= 0 || OS.indexOf("aix") > 0);
	}

	public static boolean isSolaris() {
		return (OS.indexOf("sunos") >= 0);
	}

	public static String getSeparator() throws Exception {
		if (isWindows()) {
			return ";";
		} else if (isMac()) {
			return ":";
		} else if (isUnix()) {
			return ":";
		} else if (isSolaris()) {
			return ":";
		} else {
			throw new Exception("Un supported OS");
		}
	}

	public static final String generateUUID(String msg) {
		UUID uuid = UUID.nameUUIDFromBytes(msg.getBytes());
		return uuid.toString();
	}

	public static final String toJson(Map<String, Object> map) throws Exception {
		try {
			om.setSerializationInclusion(JsonInclude.Include.NON_NULL);
			om.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
			om.enable(SerializationFeature.WRITE_SELF_REFERENCES_AS_NULL);
			String json = om.writeValueAsString(map);
			return json;
		} catch (Exception e) {
			e.printStackTrace();
			throw new Exception(e);
		}
	}
	
	public static final String ObjectToJson(Object obj) throws Exception {
		try {
			om.setSerializationInclusion(JsonInclude.Include.NON_NULL);
			om.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
			String json = om.writeValueAsString(obj);
			return json;
		} catch (Exception e) {
			e.printStackTrace();
			throw new Exception(e);
		}
	}
	
    public static Object convertPolyglotValue(Value value) {
        if (value.isNumber()) {
            if (value.fitsInInt()) {
                return value.asInt();
            } else if (value.fitsInLong()) {
                return value.asLong();
            } else {
                return value.asDouble();
            }
        } else if (value.isBoolean()) {
            return value.asBoolean();
        } else if (value.isString()) {
            return value.asString();
        } else if (value.hasArrayElements()) {
            return convertArray(value);
        } else if (value.hasMembers()) {
            return convertObject(value);
        }
        return null;
    }

    private static Map<String, Object> convertObject(Value value) {
        Map<String, Object> map = new HashMap<>();
        for (String key : value.getMemberKeys()) {
            map.put(key, convertPolyglotValue(value.getMember(key)));
        }
        return map;
    }

    private static List<Object> convertArray(Value value) {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < value.getArraySize(); i++) {
            list.add(convertPolyglotValue(value.getArrayElement(i)));
        }
        return list;
    }

	public static final String toJson(Object obj) {
		om.setSerializationInclusion(JsonInclude.Include.NON_NULL);
		om.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
		String json = null;
		try {
			json = om.writeValueAsString(obj);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
		return json;
	}

	public static final String toPrettyJson(Map<String, Object> map) throws Exception {
		try {
			om.setSerializationInclusion(JsonInclude.Include.NON_NULL);
			om.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
			om.enable(SerializationFeature.WRITE_SELF_REFERENCES_AS_NULL);
			String json = om.writerWithDefaultPrettyPrinter().writeValueAsString(map);
			return json;
		} catch (Exception e) {
			e.printStackTrace();
			throw new Exception(e);
		}
	}

	public static final String objectToJson(Object object) throws Exception {
		om.setSerializationInclusion(JsonInclude.Include.NON_NULL);
		om.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
		om.enable(SerializationFeature.WRITE_SELF_REFERENCES_AS_NULL);
		String json = om.writeValueAsString(object);
		return json;
	}

	public static final byte[] readAllBytes(File file) throws Exception {
		if (!file.exists())
			return null;
		FileInputStream fis = null;
		ByteArrayOutputStream baos = null;
		try {
			fis = new FileInputStream(file);
			baos = new ByteArrayOutputStream();
			IOUtils.copy(fis, baos);
			baos.flush();
			byte[] data = baos.toByteArray();
			baos.close();
			return data;
		} catch (Exception e) {
			throw e;
		} finally {
			if (fis != null) {
				fis.close();
			}
			if (baos != null) {
				baos.close();
			}
		}
	}

	public static final Map<String, Object> jsonToMap(String json) {
		try {
			om.disable(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE);
			Map<String, Object> map = om.readValue(json, Map.class);
			return map;
		} catch (Exception e) {
			printException("Could not convert json to map", e);
		}
		return null;
	}

	public static final String xmlToString(Object o, String rootName) throws Exception {
		Map<String, Object> root = new HashMap<String, Object>();
		XmlMapper xmlMapper = new XmlMapper();
		if (StringUtils.isBlank(rootName)) {
			rootName = "";
		}

		root.put(rootName, o);
		String xml = xmlMapper.writeValueAsString(root);
		xml = xml.replaceAll("<HashMap>", "").replaceAll("</HashMap>", "");
		xml = xml.replaceAll("<>", "").replaceAll("</>", "");
		return xml;
	}

	public static final String toXml(Map<String, Object> map, String rootName) throws Exception {
		xmlMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
		xmlMapper.configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true);
		// xmlMapper.setDefaultUseWrapper(false);
		// xmlMapper.xmlBuilder().defaultUseWrapper(false)
		String xml = xmlMapper.writer().withRootName(rootName).writeValueAsString(map);
		return xml;
	}

	public static final Map<String, Object> xmlToMap(String json) {
		try {
			xmlMapper.disable(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE);
			Map<String, Object> map = xmlMapper.readValue(String.format("<root>%s</root>", json), Map.class);
			return map;
		} catch (JsonProcessingException e) {
			printException("Could not convert xml to map", e);

		}
		return null;
	}

	public static Object getExceptionMap(Exception e) {
		Map<String, Object> lastErrorDump = new HashMap<>();
		if (e == null)
			e = new SystemException("EKA_MWS_1007",
					new Exception("Exception thrown is null but it's not clear if it's null pointer exception"));
		if (e instanceof SnippetException) {
			lastErrorDump.put("lastErrorDump", (SnippetException) e);
		} else {
			lastErrorDump.put("lastErrorDump", e);
		}

		String json = null;
		try {
			json = ServiceUtils.toJson(lastErrorDump);
			lastErrorDump = ServiceUtils.jsonToMap(json);
		} catch (Exception e2) {
			// TODO: handle exception
			lastErrorDump.clear();
			lastErrorDump.put("lastErrorDump", "");
		}
		return lastErrorDump != null ? lastErrorDump.get("lastErrorDump") : null;
	}

	public static final void execute(String fqn, DataPipeline dataPipeLine) throws SnippetException {
		if (!fqn.endsWith(".main"))
			fqn += ".main";
		if (fqn.startsWith("packages")) {
			ServiceManager.invokeJavaMethod(fqn, dataPipeLine);
		} else {
			executeEmbeddedService(dataPipeLine, CacheManager.getEmbeddedService(
					fqn.replaceAll("embedded.", "").replaceAll(".main", ""), dataPipeLine.rp.getTenant()));
		}
	}

	public static final void executeEmbeddedService(DataPipeline dataPipeline, String apiServiceJson)
			throws SnippetException {
		try (InputStream is = new ByteArrayInputStream(apiServiceJson.getBytes(StandardCharsets.UTF_8));
			 JsonReader jsonReader = Json.createReader(is)) {
			JsonObject mainflowJsonObject = jsonReader.readObject();
			FlowResolver.execute(dataPipeline, mainflowJsonObject);
		} catch (Throwable e) {
			dataPipeline.clear();
			dataPipeline.put("error", e.getMessage());
			dataPipeline.put("status", "Service error");
			throw new SnippetException(dataPipeline, "Failed to execute " + dataPipeline.getCurrentResource(),
					new Exception(e));
		}
	}

	public static final boolean isPublicFolder(String path) {
		// String str="/files/gui/middleware/pub/";
		String array[] = path.split("/pub/");
		if (array.length >= 2) {
			String preFixPath = array[0];
			String pArray[] = preFixPath.split("/files/gui/");
			if (pArray.length >= 2) {
				String packageName = pArray[1];
				if (!packageName.contains("/"))
					return true;
			}
		}
		return false;
	}

	public static final void compileJavaCode(String fqn, DataPipeline dataPipeLine) throws SnippetException {
		try {
			ScriptEngineContextManager.clear();
			ServiceManager.compileJava(fqn, dataPipeLine);
		} catch (Throwable e) {
			throw new SnippetException(dataPipeLine, "Error during compilation", new Exception(e));
		}
	}

	public static final void printException(String msg, Exception e) {
		String logLine = getLogLine(e, msg);
		LOGGER.error(logLine);
	}

	public static final void printException(Tenant tenant, String msg, Exception e) {
		String logLine = getLogLine(e, msg);
		tenant.logError(null, logLine);
		LOGGER.error(logLine);
	}

	public static final void printException(DataPipeline dp, String msg, Exception e) {
		String logLine = getLogLine(e, msg);
		dp.log(logLine, Level.ERROR);
		LOGGER.error(logLine);

	}

	private static String getLogLine(Exception e, String msg) {
		StringBuilder sb = new StringBuilder();
		StackTraceElement[] stackTrace = null;// e.getStackTrace();
		sb.append(msg);
		sb.append("\n");
		if (e != null) {
			sb.append(e.getMessage());
			stackTrace = e.getStackTrace();
		} else
			sb.append("Custom error");
		sb.append("\n");
		if (stackTrace == null)
			stackTrace = Thread.currentThread().getStackTrace();
		for (StackTraceElement stackTraceElement : stackTrace) {
			sb.append(stackTraceElement.toString());
			sb.append("\n");
		}
		return sb.toString();
	}

	public static final String getServerProperty(String key) {
		String val = null;
		try {
			Properties props = PropertyManager.getServerProperties("server.properties");
			val = props.getProperty(key);
		} catch (SystemException e) {
			e.printStackTrace();
		}

		return val;
	}

	private static Properties getUrlAliasMapping(Tenant tenant) {
		Properties urlMappings = aliasMap.get(tenant.getName());
		if (urlMappings == null) {
			urlMappings = new Properties();
			aliasMap.put(tenant.getName(), urlMappings);
		}
		return urlMappings;
	}

	public static byte[] compress(byte data[]) {
		Deflater def = new Deflater();
		def.setInput(data);
		def.finish();
		byte compString[] = new byte[data.length];
		int compSize = def.deflate(compString);
		String str = new String(compString, StandardCharsets.UTF_8).trim();
		def.end();
		return str.getBytes();
	}

	public static byte[] deCompress(byte data[]) throws DataFormatException {
		Inflater inf = new Inflater();
		inf.setInput(data);
		byte orgString[] = new byte[data.length];
		int orgSize = inf.inflate(orgString);
		String str = new String(orgString, StandardCharsets.UTF_8).trim();
		inf.end();
		return str.getBytes();
	}


	public static final String getFormattedLogLine(String id, String resource, String info) {
		StringBuilder log = new StringBuilder();
		log.append(id);
		log.append("    ");
		log.append(resource);
		log.append("    ");
		log.append(info);
		return log.toString();
	}

	public static final Map<String, Object> getFormattedLogMap(String id, String resource, String info) {
		Map<String, Object> map = new HashMap<>();
		map.put("id", id);
		map.put("resource", resource);
		map.put("log", info);
		return map;
	}

	public static boolean areBracesBalanced(String input) {
		int braceCounter = 0;

		for (char c : input.toCharArray()) {
			if (c == '{') {
				braceCounter++;
			} else if (c == '}') {
				braceCounter--;
			}

			if (braceCounter < 0) {
				return false;
			}
		}

		return braceCounter == 0;
	}

	public static void copyDirectory(String sourceDirectoryLocation, String destinationDirectoryLocation)
			throws IOException {
		LOGGER.info("Copying files from : " + sourceDirectoryLocation.toString());
		LOGGER.info("Copying files to : " + destinationDirectoryLocation.toString());

		FileUtils.copyDirectory(new File(sourceDirectoryLocation), new File(destinationDirectoryLocation));
		LOGGER.info("Tenant instance created: " + destinationDirectoryLocation.toString());
	}

	public static final URL[] getClassesURLs(String path) throws Exception {
		List<URL> urls = new ArrayList<URL>();
		String paths[] = path.split(getSeparator());

		for (String location : paths) {
			File file = new File(location);
			LOGGER.info("Class file path: " + file.getAbsolutePath());
			if (file.getName().contains("ekamw"))
				LOGGER.info(file.getAbsolutePath());
			if (file.isFile())
				urls.add(file.toURL());
		}

		if (urls.size() > 0)
			return urls.toArray(new URL[urls.size()]);

		return null;
	}


	public static SecretKeySpec getKey(final String myKey) {
		SecretKeySpec secretKey = null;
		byte[] key;
		MessageDigest sha = null;
		try {
			key = myKey.getBytes("UTF-8");
			sha = MessageDigest.getInstance("SHA-1");
			key = sha.digest(key);
			key = Arrays.copyOf(key, 16);
			secretKey = new SecretKeySpec(key, "AES");
		} catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
			printException("Could not create secretKey.", e);
		}
		return secretKey;
	}

	public static Date addHoursToDate(Date date, int hours) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.add(Calendar.HOUR_OF_DAY, hours);
		return calendar.getTime();
	}

	public static final String[] getJarPaths(String path, String packagePath) throws Exception {
		URL urls[] = null;
		String paths[] = null;
		LOGGER.debug("JAR PATH: " + packagePath + path);
		File file = new File(packagePath + path);
		if (file.isDirectory()) {
			File files[] = file.listFiles();
			if (files.length > 0) {
				urls = new URL[files.length];
				paths = new String[files.length];
				int indx = 0;
				for (File fileItem : files) {
					if (fileItem.getName().endsWith(".jar")) {

						urls[indx] = fileItem.toURL();
						paths[indx] = fileItem.getAbsolutePath();
						indx++;
					}
				}
				if (indx > 0) {
					LOGGER.debug("JAR PATH(" + indx + "): " + packagePath + path);
					return paths;
				}
			}
		}
		return null;
	}

	public static void zipFile(File fileToZip, String fileName, ZipOutputStream zipOut) throws IOException {
		if (fileToZip.isHidden()) {
			return;
		}
		if (fileToZip.isDirectory()) {
			if (fileName.endsWith("/")) {
				zipOut.putNextEntry(new ZipEntry(fileName));
				zipOut.closeEntry();
			} else {
				zipOut.putNextEntry(new ZipEntry(fileName + "/"));
				zipOut.closeEntry();
			}
			File[] children = fileToZip.listFiles();
			for (File childFile : children) {
				zipFile(childFile, fileName + "/" + childFile.getName(), zipOut);
			}
			return;
		}
		try(FileInputStream fis = new FileInputStream(fileToZip)) {
			ZipEntry zipEntry = new ZipEntry(fileName);
			zipOut.putNextEntry(zipEntry);
			byte[] bytes = new byte[1024];
			int length;
			while ((length = fis.read(bytes)) >= 0) {
				zipOut.write(bytes, 0, length);
			}
			fis.close();
		}catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static String replaceAllIgnoreRegx(String source, String search, String replace) {

		return StringUtils.join(source.split(Pattern.quote(search)), replace);
	}
	
	public static void logInfo(String tenantName, String serviceName, String message) {
		Tenant.getTenant(tenantName).logError(serviceName, message);
	}

	/**
	 * @param map
	 * @param key
	 * @return
	 */
	public static Object getCaseInsensitiveKey(Map<String, Object> map, String key) {
		return (null == map.get(key)) ? map.get(key.toLowerCase()) : map.get(key);
	}

	public static String normalizeUri(String s) {
		String r = StringUtils.stripAccents(s);
		r = r.replace(" ", "_");
		r = r.replaceAll("[^\\.A-Za-z0-9_]", "");
		return r;
	}

	public static String normalizeApiPath(String s) {
		s = StringUtils.stripEnd(s, "/");
		String path = s;// s.replaceAll("\\{", "").replaceAll("\\}", "");

		String[] paths = StringUtils.split(s, "/");
		StringBuilder pathBuilder = new StringBuilder();
		for (int i = 0; i < paths.length; i++) {
			if (paths.length != 1 && (i == (paths.length - 1) && !(paths[i].indexOf("}") > 0))) {
				break;
			}
			pathBuilder.append("/").append(paths[i].replaceAll("\\{", "").replaceAll("\\}", "").replace(".", "_"));
		}

		return pathBuilder.toString().replaceAll(Pattern.quote("."), "_").replaceAll("-", "");
	}

	public static String normalizeApiPathName(String method, String s) {
		s = StringUtils.stripEnd(s, "/");
		String path = s.replaceAll("\\{", "").replaceAll("\\}", "");

		String[] paths = StringUtils.split(path, "/");

		path = paths[paths.length - 1];
		return method + (path.substring(0, 1).toUpperCase() + path.substring(1).toLowerCase());
	}

	public static String toServiceSlug(String str) {
		if (StringUtils.isBlank(str)) {
			return "";
		}
		return str.toLowerCase().replaceAll("[^a-z0-9\\-]+", "_").replaceAll("^-|-$", "");
	}

	public static void loadCustomJar(DataPipeline dp, String packageName, String jarFileName) throws Exception {
		String packagePath = PropertyManager.getPackagePath(dp.rp.getTenant());
		String path = packagePath + "/packages/" + packageName + "/dependency/jars/" + jarFileName;
		CustomClassLoader ccl = RTCompile.classLoaderMap.get(dp.rp.getTenant().getName());
		final JarFile jar = new JarFile(new File(path));
		List<String> classNames = ccl.loadClassFromJar(jar);
	}

	public static void loadCustomJar(DataPipeline dp, String path) throws Exception {
		CustomClassLoader ccl = RTCompile.classLoaderMap.get(dp.rp.getTenant().getName());
		final JarFile jar = new JarFile(new File(path));
		List<String> classNames = ccl.loadClassFromJar(jar);
	}

	public static void unzipBuildFile(String srcZip, String destinationBasePath) throws IOException {
		byte[] buffer = new byte[1024];
		try(ZipInputStream zis = new ZipInputStream(new FileInputStream(srcZip))){
		ZipEntry zipEntry = zis.getNextEntry();
		while (zipEntry != null) {
			File newFile = newFile(new File(destinationBasePath), zipEntry);
			if (zipEntry.isDirectory()) {
				if (!newFile.isDirectory() && !newFile.mkdirs()) {
					throw new IOException("Failed to create directory " + newFile);
				}
			} else {
				// fix for Windows-created archives
				File parent = newFile.getParentFile();
				if (!parent.isDirectory() && !parent.mkdirs()) {
					throw new IOException("Failed to create directory " + parent);
				}

				// write file content
				try(FileOutputStream fos = new FileOutputStream(newFile)) {
					int len;
					while ((len = zis.read(buffer)) > 0) {
						fos.write(buffer, 0, len);
					}
					fos.close();
				}
			}
			zipEntry = zis.getNextEntry();
		}
		zis.closeEntry();
		zis.close();
		}catch (IOException e) {
			e.printStackTrace();
			throw e;
		}
	}

	/**
	 * @param destinationDir
	 * @param zipEntry
	 * @return
	 * @throws IOException
	 */
	private static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
		File destFile = new File(destinationDir, zipEntry.getName());

		String destDirPath = destinationDir.getCanonicalPath();
		String destFilePath = destFile.getCanonicalPath();

		// if (!destFilePath.startsWith("/" + destDirPath + File.separator)) {
		// System.out.println(destFilePath);
		// throw new IOException("Entry is outside of the target dir: " +
		// zipEntry.getName());
		// }

		return destFile;
	}

	public static void beforeServiceExecution(DataPipeline dp, String fqn, Map<String, Object> passThroughData)
			throws Exception {
		String logRequest = null;
		String logResponse = null;
		String requestJson = "";
		String responseJson = "";
		Date dateTimeStmp = null;
		Set<String> keys = passThroughData.keySet();
		dp.put("*currentResourceFQN", fqn);
		dp.appLogMul("RESOURCE_NAME", fqn);
		String gqlEnabled = (String) dp.getMyProperties().get("GraphQL");
		String GraphQLDBC = (String) dp.getMyProperties().get("GraphQL.DBC");
		String GraphQLSchema = (String) dp.getMyProperties().get("GraphQL.Schema");
		JsonObject mainflowJsonObject = (JsonObject) passThroughData.get("mainflowJsonObject");

		Long timeout = (Long) passThroughData.get("timeout");
		Integer resetServiceInMS = (Integer) passThroughData.get("resetServiceInMS");
		String gql = null;
		Object rootObject = null;
		Map<String, Object> gqlData = new HashMap<>();

		if (resetServiceInMS != null) {

			String flowRef = (String) passThroughData.get("flowRef");
			mainflowJsonObject = configureServiceStartup(dp, timeout, mainflowJsonObject, resetServiceInMS, flowRef);
			passThroughData.put("mainflowJsonObject", mainflowJsonObject);

			if (Boolean.valueOf(gqlEnabled)) {
				gql = dp.getAsString("*payload/query");
				// Setting up the graphQL introspection request

				if (StringUtils.isNotBlank(gql)) {
					dp.drop("*payload");
					gql = gql.replaceAll("\\\\s+", " ").replaceAll("\\\\r|\\\\n", "").trim();
					gqlData.put("gQuery", gql);
					gqlData.put("fqn", fqn);
					if (StringUtils.isNotBlank(GraphQLSchema))
						gqlData.put("schema", GraphQLSchema);
					if (StringUtils.isNotBlank(GraphQLDBC))
						gqlData.put("connection", GraphQLDBC);
					String[] gqlTokens = gql.split(" ");
					String rootName = gqlTokens[1];

					gqlData.put("rootName", rootName);

					if (rootName.toLowerCase().equals("introspectionquery")) {
						// dp.put("*gqlData", gqlData);
						dp.map("*gqlData", gqlData);
						dp.apply("packages.middleware.pub.graphQL.rest.flow.applyGraphQL");
						dp.clearServicePayload();
						rootObject = dp.get("*multiPart");
						if (rootObject != null)
							dp.put("*multiPart", rootObject);
						else {
							dp.put(rootName, dp.get("executionResult/errors"));
						}
						return;
					}
					gqlTokens = gql.split(Pattern.quote("{"));
					if (gqlTokens.length > 1 && gqlTokens[1] != null)
						rootName = gqlTokens[1].trim();
					gqlData.put("rootName", rootName);
				}
			}
		}
		String stopRecursiveLogging = dp.getString("stopRecursiveLogging");
		if (stopRecursiveLogging == null && !fqn.equalsIgnoreCase("packages.middleware.pub.service.auditLogging")) {
			logRequest = dp.getMyConfig("logRequest");
			logResponse = dp.getMyConfig("logResponse");
			requestJson = "";
			responseJson = "";
			if ("true".equalsIgnoreCase(logRequest))
				requestJson = dp.toJson();
			dateTimeStmp = new Date();
		}
		passThroughData.put("stopRecursiveLogging", stopRecursiveLogging);
		passThroughData.put("logRequest", logRequest);
		passThroughData.put("logResponse", logResponse);
		passThroughData.put("requestJson", requestJson);
		passThroughData.put("responseJson", responseJson);
		passThroughData.put("dateTimeStmp", dateTimeStmp);

		// Calling the service
		if (resetServiceInMS != null)
			FlowResolver.execute(dp, mainflowJsonObject);

		if (StringUtils.isNotBlank(gql) && StringUtils.isBlank(GraphQLDBC)) {
			// dp.put("*gqlData", gqlData);
			dp.map("*gqlData", gqlData);
			Map<String, Object> data = new HashMap<>();
			String rootName = (String) gqlData.get("rootName");
			rootObject = dp.get(rootName);
			data.put(rootName, rootObject);
			gqlData.put("data", data);
			dp.apply("packages.middleware.pub.graphQL.rest.flow.applyGraphQL");
			// dp.drop("*gqlData");
			rootObject = dp.getValueByPointer("executionResult/rootObject");
			if (rootObject != null) {
				dp.put(rootName, rootObject);
			}
			Object errors = dp.getValueByPointer("executionResult/errors");
			if (errors != null) {
				dp.put(rootName, errors);
			}
			dp.drop("executionResult");
			dp.drop("*gqlData");
			dp.clearServicePayload();
		}
	}

	public static void afterServiceExecution(DataPipeline dp, String fqn, Map<String, Object> passThroughData)
			throws Exception {
		Long nanoSec = (Long) passThroughData.get("nanoSec");
		String logRequest = (String) passThroughData.get("logRequest");
		String logResponse = (String) passThroughData.get("logResponse");
		String requestJson = (String) passThroughData.get("requestJson");
		String responseJson = (String) passThroughData.get("responseJson");
		Date dateTimeStmp = (Date) passThroughData.get("dateTimeStmp");
		Long startTime = (Long) passThroughData.get("startTime");
		String stopRecursiveLogging = (String) passThroughData.get("stopRecursiveLogging");
		if (stopRecursiveLogging == null && !fqn.equalsIgnoreCase("packages.middleware.pub.service.auditLogging")) {
			if ("true".equalsIgnoreCase(logResponse))
				responseJson = dp.toJson();
			long endTime = System.currentTimeMillis();
			Map<String, String> auditLog = new HashMap<String, String>();
			auditLog.put("correlationId", dp.getCorrelationId());
			auditLog.put("sessionId", dp.getSessionId());
			auditLog.put("dateTimeStmp", dateTimeStmp + "");
			auditLog.put("duration", (endTime - startTime) + "");
			if (null == dp.getString("error")) {
				auditLog.put("error", "");
			} else {
				auditLog.put("error", dp.getString("error"));
			}

			auditLog.put("fqn", fqn);
			auditLog.put("request", requestJson);
			auditLog.put("response", responseJson);
			auditLog.put("nanoInstance", nanoSec + "");

			try {
				auditLog.put("hostName", InetAddress.getLocalHost().getHostName());
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}
			String nodeName = dp.getGlobalConfig("nodeName");
			auditLog.put("nodeName", nodeName);

			//auditLog.put("remoteAddr", dp.getRemoteIpAddr());
			//auditLog.put("userId", dp.getCurrentUserProfile().getId());
			auditLog.put("urlPath", dp.getUrlPath());

			Map<String, Object> asyncInputDoc = new HashMap();
			asyncInputDoc.put("auditLog", auditLog);
			asyncInputDoc.put("stopRecursiveLogging", "true");
			dp.put("asyncInputDoc", asyncInputDoc);
			dp.applyAsync("packages.middleware.pub.service.auditLogging");
			dp.drop("asyncInputDoc");
			dp.drop("asyncOutputDoc");

		}
	}

	private static JsonObject configureServiceStartup(DataPipeline dataPipeline, Long timeout,
			JsonObject mainflowJsonObject, Integer resetServiceInMS, String flowRef) throws Exception {
		Map<String, Object> chache = CacheManager.getCacheAsMap(dataPipeline.rp.getTenant());// -----reset fix
		Boolean resetEnabled = (Boolean) chache.get("ekamw.promote.runtime.service.reload");// -----reset fix
		if (timeout < System.currentTimeMillis() && (resetEnabled == null || resetEnabled == true))// -----reset fix
			timeout = 0l;// -----reset fix
		String location = PropertyManager.getPackagePath(dataPipeline.rp.getTenant());
		flowRef = location + flowRef;
		if (mainflowJsonObject == null || timeout == 0l) { // -----reset fix
			timeout = System.currentTimeMillis() + resetServiceInMS; // -----reset fix
			try (JsonReader reader = Json.createReader(new FileInputStream(new File(flowRef)))) {
				mainflowJsonObject = reader.readObject();
			}
		}
		return mainflowJsonObject;
	}

	public static Map<String, Object> decodeJWT(String jwtToken) {
		// Splitting the JWT Token into parts
		String[] parts = jwtToken.split("\\.");
		if (parts.length < 2) {
			return new HashMap<String, Object>(); // Not enough parts for a valid JWT
		}

		// Decoding the payload
		String payload = parts[1];
		byte[] decodedBytes = Base64.getUrlDecoder().decode(payload);
		String decodedString = new String(decodedBytes);

		// Converting JSON string to Map
		ObjectMapper objectMapper = new ObjectMapper();
		Map<String, Object> tokenData = new HashMap<String, Object>();
		try {
			tokenData = objectMapper.readValue(decodedString, Map.class);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return tokenData;
	}

	public static void saveServerProperties(DataPipeline dataPipeline) throws Exception {
		if (!dataPipeline.rp.getTenant().getName().equalsIgnoreCase("default")) {
			throw new Exception("Server properties are not allowed to save for other tenants");
		}
		Map<String, Object> properties = dataPipeline.getAsMap("properties");

		if (properties == null || properties.isEmpty()) {
			return;
		}

		Properties props = new Properties();
		String filePath = PropertyManager.getConfigFolderPath() + "server.properties";
		try(FileInputStream inputStream = new FileInputStream(filePath)) {
			props.load(inputStream);
			inputStream.close();
		}

		for (Map.Entry<String, Object> entry : properties.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue().toString();

			if (StringUtils.isNotBlank(key) && StringUtils.isNotBlank(value)) {
				props.setProperty(key, value);
			}
		}

		try(FileOutputStream outputStream = new FileOutputStream(filePath)) {
			props.store(outputStream, "");
			outputStream.flush();
			outputStream.close();
		}
	}

	/**
	 *
	 * @param connectionPropFile
	 * @param key
	 * @return
	 * @throws Exception
	 */
	public static String getKeyConnection(String connectionPropFile, String key) throws Exception {
		try(
				FileInputStream fileInputStream = new FileInputStream(connectionPropFile);
		) {
			Properties properties = new Properties();
			properties.load(fileInputStream);

			Object assistanceId = properties.get(key);

			if (null == assistanceId) {
				return null;
			}

			return assistanceId.toString();

		} finally {}
	}

	/**
	 * @param connectionPropFile
	 * @param key
	 * @param value
	 * @throws Exception
	 */
	public static void saveOrUpdateKeyInConnection(String connectionPropFile, String key, String value) throws Exception {

		try(
				FileInputStream fileInputStream = new FileInputStream(connectionPropFile);
		) {
			Properties properties = new Properties();
			properties.load(fileInputStream);

			Object assistanceId = properties.get(key);
			if (null == assistanceId) {
				properties.put(key, value);
				FileOutputStream fileOutputStream = new FileOutputStream(connectionPropFile);
				properties.store(fileOutputStream, "");
				fileOutputStream.close();
			}

		} finally {}
	}
}
