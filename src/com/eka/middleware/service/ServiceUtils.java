package com.eka.middleware.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
//import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.undertow.account.Pac4jAccount;

import com.eka.middleware.auth.AuthAccount;
import com.eka.middleware.auth.Security;
import com.eka.middleware.auth.UserProfileManager;
import com.eka.middleware.heap.HashMap;
import com.eka.middleware.pooling.ScriptEngineContextManager;
import com.eka.middleware.server.ServiceManager;
import com.eka.middleware.template.MultiPart;
import com.eka.middleware.template.SnippetException;
import com.eka.middleware.template.SystemException;
import com.eka.middleware.template.Tenant;
import com.eka.middleware.template.UriTemplate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;

import io.undertow.io.Receiver.FullBytesCallback;
import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.server.handlers.form.FormParserFactory.Builder;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;

public class ServiceUtils {
	// private static final Properties serverProperties = new Properties();
	// private static final Properties urlMappings = new Properties();

	private static final Map<String, Properties> aliasMap = new ConcurrentHashMap<String, Properties>();

	private static final Map<String, UriTemplate> parameterResolverMap = new ConcurrentHashMap<String, UriTemplate>();
	private static final ObjectMapper om = new ObjectMapper();
	public static final XmlMapper xmlMapper = new XmlMapper();
	public static final YAMLMapper yamlMapper = new YAMLMapper();
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
		om.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
		String json = om.writeValueAsString(map);
		return json;
	}

	public static final String toPrettyJson(Map<String, Object> map) throws Exception {
		om.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
		String json = om.writerWithDefaultPrettyPrinter().writeValueAsString(map);
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
			fis.close();
			baos.close();
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

	public static final String toYaml(Map<String, Object> map) throws Exception {
		yamlMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
		String xml = yamlMapper.writeValueAsString(map);
		return xml;
	}

	public static final Map<String, Object> yamlToMap(String json) {
		try {
			yamlMapper.disable(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE);
			Map<String, Object> map = yamlMapper.readValue(json, Map.class);
			return map;
		} catch (JsonProcessingException e) {
			printException("Could not convert xml to map", e);
		}
		return null;
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
			Map<String, Object> map = xmlMapper.readValue(json, Map.class);
			return map;
		} catch (JsonProcessingException e) {
			printException("Could not convert xml to map", e);
		}
		return null;
	}

	public static void main(String[] args) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("Data", "mydata");
		Map<String, Object> map2 = new HashMap<String, Object>();
		map2.put("MoreInfo", "Additional data");
		try {
			map2.put("complexObject", System.err);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		map.put("map2", map2);
		map.put("Name", "unknown");
		om.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
		String json = null;
		try {
			json = om.writeValueAsString(map);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		// System.out.println(json);
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
		return lastErrorDump.get("lastErrorDump");
	}

	public static final void execute(String fqn, DataPipeline dataPipeLine) throws SnippetException {
		if (!fqn.endsWith(".main"))
			fqn += ".main";
		ServiceManager.invokeJavaMethod(fqn, dataPipeLine);
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
		StringBuilder sb = new StringBuilder();
		StackTraceElement[] stackTrace = e.getStackTrace();
		sb.append(msg);
		sb.append("\n");
		sb.append(e.getMessage());
		sb.append("\n");
		if (stackTrace == null)
			stackTrace = Thread.currentThread().getStackTrace();
		for (StackTraceElement stackTraceElement : stackTrace) {
			sb.append(stackTraceElement.toString());
			sb.append("\n");
		}
		LOGGER.error(sb.toString());

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

	public static final String getPathService(String requestPath, Map<String, Object> payload, Tenant tenant) {
		
		if(requestPath.startsWith("GET/packages"))
			return requestPath.split("/")[1];
		try {
			String URLAliasFilePath = PropertyManager.getPackagePath(tenant) + "URLAliasMapping.properties";
			if (requestPath.contains("//")) {
				LOGGER.log(Level.INFO, requestPath);
				return null;
			}
			if (requestPath.endsWith("/")) {
				requestPath += "$$^%@#";
				requestPath = requestPath.replace("/$$^%@#", "");
			}
			final Properties urlMappings = getUrlAliasMapping(tenant);
			Map<String, String> pathParams = new HashMap<String, String>();
			boolean reload = PropertyManager.hasTenantFileChanged(URLAliasFilePath);
			if (reload) {
				Properties props = PropertyManager.getProperties(URLAliasFilePath);
				if (props == null)
					props = new Properties();
				props.load(new FileInputStream(new File(URLAliasFilePath)));
				Set<Object> keys = props.keySet();
				// Add keys
				for (Object keyStr : keys) {
					String key = keyStr.toString();
					urlMappings.put(key, props.get(key));
					UriTemplate parameterResolver = new UriTemplate(key);
					parameterResolverMap.put(tenant.getName() + "-" + key, parameterResolver);
				}
				// Remove Keys
				keys = urlMappings.keySet();
				for (Object keyStr : keys) {
					String key = keyStr.toString();
					if (props.get(key) == null) {
						urlMappings.remove(key);
						parameterResolverMap.remove(tenant.getName() + "-" + key);
					}
				}
			}
			String serviceName = urlMappings.getProperty(requestPath + "/*");
			if (serviceName == null)
				serviceName = urlMappings.getProperty(requestPath);
			if (serviceName == null && payload != null) {
				Set<Object> keys = urlMappings.keySet();
				for (Object keyStr : keys) {
					UriTemplate parameterResolver = parameterResolverMap.get(tenant.getName() + "-" + keyStr);
					if (parameterResolver == null) {
						continue;
					}
					// if (parameterResolver.match(requestPath)) {
					pathParams = parameterResolver.matcher(requestPath);
					if (pathParams != null && pathParams.size() > 0) {
						serviceName = urlMappings.getProperty(keyStr.toString());
						payload.put("pathParameters", pathParams);
						break;
					}
					// }
				}
			}
			return serviceName;

		} catch (Exception e) {
			ServiceUtils.printException("Failed during evaluating resource path", e);
		}
		return null;
	}

	public static final Map<String, Object> extractHeaders(HttpServerExchange exchange) {
		Map<String, Object> map = new HashMap<String, Object>();
		HeaderMap hm = exchange.getRequestHeaders();
		Collection<HttpString> hts = hm.getHeaderNames();
		for (HttpString httpString : hts) {
			map.put(httpString.toString(), hm.get(httpString).getFirst());
		}
		return map;
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

	public static final MultiPart getMultiPart(DataPipeline dataPipeLine) throws SnippetException {
		String sessionId = dataPipeLine.getSessionId();
		RuntimePipeline rp = RuntimePipeline.getPipeline(sessionId);
		final HttpServerExchange exchange = rp.getExchange();

		Builder builder = FormParserFactory.builder();

		final FormDataParser formDataParser = builder.build().createParser(exchange);
		if (formDataParser != null) {

//			try {
//				ExecutorService threadpool = Executors.newCachedThreadPool();
//				@SuppressWarnings("unchecked")
//				Future<Long> futureTask = (Future<Long>) threadpool.submit(() -> {
			try {
				// BlockingHttpExchange bht = exchange.startBlocking();
				FormData formData = null;// new FormData(0);
				try {
					formData = formDataParser.parseBlocking();
				} catch (Exception e) {
					// e.printStackTrace();
					throw new SnippetException(dataPipeLine,
							"Could not fetch formData for multipart request.\n" + e.getMessage(), e);
				}
				Map<String, Object> formDataMap = new HashMap<String, Object>();
				List<InputStream> files = new ArrayList<InputStream>();
				for (String key : formData) {
					for (FormData.FormValue formValue : formData.get(key)) {
						if (formValue.isFileItem()) {
							if (key != null && key.trim().length() > 0) {
								formDataMap.put(key + "_fileName", formValue.getFileName());
								formDataMap.put(key, formValue.getFileItem().getInputStream());
							}
							InputStream is = formValue.getFileItem().getInputStream();
							files.add(is);
							formDataMap.put(formValue.getFileName(), is);
						} else
							formDataMap.put(key, formValue.getValue());
					}
				}
				formDataMap.put("uploadedFiles", files);
				MultiPart mp = new MultiPart(formDataMap);
				rp.payload.put("*multiPartRequest", mp);
			} catch (Exception e) {
				ServiceUtils.printException(rp.getSessionID() + " Could not stream file thread.", e);
			}
//				});
//
//				while (!futureTask.isDone()) {
//					Thread.sleep(100);
//				}
//
//				threadpool.shutdown();
//
//			} catch (Exception e) {
//				e.printStackTrace();
//				throw new SnippetException(dataPipeLine, e.getMessage(), e);
//			}

		}
		MultiPart mp = null;
		Map<String, Object> payload = rp.payload;
		if (payload.get("*multiPartRequest") != null) {
			mp = (MultiPart) payload.get("*multiPartRequest");
			payload.remove("*multiPartRequest");
		}
		return mp;
	}

	public static final String getURLAlias(String fqn, Tenant tenant) throws Exception {
		Properties urlMappings = getUrlAliasMapping(tenant);
		Set<Object> sset = urlMappings.keySet();
		for (Object setKey : sset) {
			if (fqn.equalsIgnoreCase(urlMappings.get(setKey).toString()))
				return setKey.toString();
		}
		return null;
	}

	public static final String registerURLAlias(String fqn, String alias, DataPipeline dp) throws Exception {

		// Map<String, Object> pathParams = new HashMap<String, Object>();
		// pathParams.put("pathParameters", "");
		String aliasTenantName = dp.rp.getTenant().getName();
		// alias=aliasTenantName+alias;
		String existingFQN = getPathService(alias, null, dp.rp.getTenant());

		// String existingFQN=urlMappings.getProperty(alias);
		Properties urlMappings = getUrlAliasMapping(dp.rp.getTenant());
		String msg = "Saved";
		if (existingFQN == null || existingFQN.equalsIgnoreCase(fqn)) {
			Set<Object> sset = urlMappings.keySet();
			for (Object setKey : sset) {
				if (fqn.equalsIgnoreCase(urlMappings.get(setKey).toString()))
					urlMappings.remove(setKey);
			}
			urlMappings.setProperty(alias, fqn);
		} else {
			msg = "Failed to save. The alias conflicted with the existing FQN(" + existingFQN + ").";
			return msg;
		}
		// URL url = new URL(MiddlewareServer.getConfigFolderPath() +
		// "URLAliasMapping.properties");
		FileOutputStream fos = new FileOutputStream(
				new File(PropertyManager.getPackagePath(dp.rp.getTenant()) + "URLAliasMapping.properties"));
		urlMappings.store(fos, "");// save(fos, "");
		fos.flush();
		fos.close();
		return msg;
	}

	private static final void streamResponseFile(final RuntimePipeline rp, final MultiPart mp) throws SnippetException {

		try {
			handleFileResponse(rp.getExchange(), mp);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			throw new SnippetException(rp.dataPipeLine, "Exception while streaming file.\n" + e.getMessage(), e);

		} /*
			 * // Thread newThread = new Thread(() -> {
			 *
			 * ExecutorService threadpool = Executors.newCachedThreadPool();
			 *
			 * @SuppressWarnings("unchecked") Future<Long> futureTask = (Future<Long>)
			 * threadpool.submit(() -> { try { handleFileResponse(rp.getExchange(), mp); }
			 * catch (Exception e) { // TODO Auto-generated catch block
			 * ServiceUtils.printException(rp.getSessionID() +
			 * " Could not stream file thread.", e); } });
			 *
			 * while (!futureTask.isDone()) { //
			 * System.out.println("FutureTask is not finished yet..."); Thread.sleep(100); }
			 *
			 * threadpool.shutdown();
			 *
			 */
	}

	private static void handleFileResponse(HttpServerExchange exchange, MultiPart mp) throws Exception {
		OutputStream os = null;
		InputStream inputStream = null;
		try {
			File tempFile = mp.file;

			if (tempFile == null)
				inputStream = mp.is;
			else
				inputStream = new FileInputStream(tempFile);
//			exchange.startBlocking();
			Set<String> headers = mp.headers.keySet();

			for (String key : headers) {
				exchange.getResponseHeaders().put(HttpString.tryFromString(key), mp.headers.get(key).toString());
			}
			os = exchange.getOutputStream();
			if (tempFile != null)
				IOUtils.copy(inputStream, os);
			else
				IOUtils.copyLarge(inputStream, os);
			os.flush();
			os.close();
			inputStream.close();
			exchange.endExchange();

		} finally {
			if (os != null)
				os.close();
			if (inputStream != null)
				inputStream.close();
		}
	}

	public static final void startStreaming(final RuntimePipeline rp, final MultiPart mp) throws SnippetException {
		if (mp != null) {
			if (mp.type.equals("file"))
				streamResponseFile(rp, mp);
			else if (mp.type.equals("body"))
				streamBodyResponse(rp, mp);
		}
	}

	private static final void streamBodyResponse(final RuntimePipeline rp, final MultiPart mp) throws SnippetException {
		try {
			handleBodyResponse(rp.getExchange(), mp);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			ServiceUtils.printException(rp.getSessionID() + " Could not stream body thread.", e);
		}
//		try {
//			ExecutorService threadpool = Executors.newCachedThreadPool();
//			@SuppressWarnings("unchecked")
//			Future<Long> futureTask = (Future<Long>) threadpool.submit(() -> {
//				
//			});
//
//			while (!futureTask.isDone()) {
//				// System.out.println("FutureTask is not finished yet...");
//				Thread.sleep(100);
//			}
//
//			threadpool.shutdown();
//
//		} catch (Exception e) {
//			// TODO Auto-generated catch block
//			throw new SnippetException(rp.dataPipeLine, "Exception while streaming body.\n" + e.getMessage(), e);
//
//		}
	}

	private static void handleBodyResponse(HttpServerExchange exchange, MultiPart mp) throws Exception {

		InputStream targetStream = null;
		OutputStream os = null;
		try {
			if (mp.is == null)
				targetStream = new ByteArrayInputStream(mp.body);
			else
				targetStream = mp.is;
//		exchange.startBlocking();
			Set<String> headers = mp.headers.keySet();

			for (String key : headers) {
				exchange.getResponseHeaders().put(HttpString.tryFromString(key), mp.headers.get(key).toString());
			}
			os = exchange.getOutputStream();
			// System.out.println("Available bytes:" + targetStream.available());
//		byte bytes[]=targetStream.readNBytes(200);
//		System.out.println(new String(bytes));
			IOUtils.copy(targetStream, os);
			os.flush();
			os.close();
			// os=null;
			targetStream.close();
			// targetStream=null;
			exchange.endExchange();

		} catch (Throwable e) {
			e.printStackTrace();
		} finally {
			if (os != null)
				os.close();
			if (targetStream != null)
				targetStream.close();
		}
	}

	public static byte[] getBody(DataPipeline dataPipeLine) throws SnippetException {
		try {
			final RuntimePipeline rp = RuntimePipeline.getPipeline(dataPipeLine.getSessionId());
			final Map<String, Object> payload = rp.payload;
			try {
				rp.getExchange().getRequestReceiver().setMaxBufferSize(1024);
				rp.getExchange().getRequestReceiver().receiveFullBytes(new FullBytesCallback() {
					@Override
					public void handle(HttpServerExchange exchange, byte[] body) {
						payload.put("@body", body);
					}
				});
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				ServiceUtils.printException(rp.getSessionID() + " Could not stream body thread.", e);
			}

			if (payload.get("@body") != null) {
				byte body[] = (byte[]) payload.get("@body");
				payload.remove("@body");
				return body;
			}

		} catch (Exception e) {
			e.printStackTrace();
			throw new SnippetException(dataPipeLine, e.getMessage(), e);
		}
		return null;
	}

	public static final InputStream getBodyAsStream(final DataPipeline dataPipeLine) throws SnippetException {
		try {
			String sessionId = dataPipeLine.getSessionId();
			RuntimePipeline rp = RuntimePipeline.getPipeline(sessionId);
			final HttpServerExchange exchange = rp.getExchange();
			rp.payload.put("@bodyStream", exchange.getInputStream());
			/*
			 * ExecutorService threadpool = Executors.newCachedThreadPool();
			 *
			 * @SuppressWarnings("unchecked") Future<Long> futureTask = (Future<Long>)
			 * threadpool.submit(() -> { try { exchange.startBlocking();
			 * rp.payload.put("@bodyStream", exchange.getInputStream()); } catch (Exception
			 * e) { e.printStackTrace(); ServiceUtils.printException(rp.getSessionID() +
			 * " Could not stream body thread.", e); } }); while (!futureTask.isDone()) { //
			 * System.out.println("FutureTask is not finished yet..."); Thread.sleep(10); }
			 * threadpool.shutdown();
			 */
			if (rp.payload.get("@bodyStream") != null) {
				InputStream is = (InputStream) rp.payload.get("@bodyStream");
				rp.payload.remove("@bodyStream");
				return is;
			}

		} catch (Exception e) {
			throw new SnippetException(dataPipeLine, e.getMessage(), e);
		}
		return null;
	}

	public static void copyDirectory(String sourceDirectoryLocation, String destinationDirectoryLocation)
			throws IOException {
		LOGGER.info("Copying files from : "+sourceDirectoryLocation.toString());
		LOGGER.info("Copying files to : "+destinationDirectoryLocation.toString());
		
		FileUtils.copyDirectory(new File(sourceDirectoryLocation), new File(destinationDirectoryLocation));
		/*Path src=new File(sourceDirectoryLocation).toPath();
		File dst=new File(destinationDirectoryLocation);
		dst.mkdirs();
		Path dest=dst.toPath();
		
		try (Stream<Path> stream = Files.walk(src)) {
			  stream.parallel().forEach(source -> copy(source, dest.resolve(src.relativize(source))));
		}*/
		LOGGER.info("Tenant instance created: "+destinationDirectoryLocation.toString());
	}
	
	private static void copy(Path source, Path dest) {
		try {
			//LOGGER.info("From: "+source.toAbsolutePath().toString());
			//LOGGER.info("To: "+dest.toAbsolutePath().toString());
			if(source.toFile().isDirectory()) {
				if(!source.toFile().exists())
					source.toFile().mkdir();
			}else
				Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
		  
		} catch (Exception e) {
			printException("Failed while copying "+source.toString()+"\n to \n"+dest.toString(), e);
		  //throw new RuntimeException(e.getMessage(), e);
		}
	}
	
	public static String initNewTenant(String name, AuthAccount account) {
		try {
			if(Tenant.exists(name))
				throw new Exception("Tenant already exists or null. Tenant Name : "+name);
			
			List<String> groups=null;
			if(account.getAuthProfile()!=null)
				groups=(List<String>) account.getAuthProfile().get("groups");
			if("default".equals(name)) {
				groups=new ArrayList<String>();
				groups.add(AuthAccount.STATIC_ADMIN_GROUP);
				groups.add(AuthAccount.STATIC_DEVELOPER_GROUP);
				account.getAuthProfile().put("groups",groups);
			}
			if(groups==null)
				throw new Exception("Claim 'groups' is required in jwt access_token : "+name);
			account.getAuthProfile().put("tenant", name);
			String src=PropertyManager.getPackagePath(null)+"released"+File.separator+"core";
			String dest=PropertyManager.getPackagePath(Tenant.getTenant(name));
			copyDirectory(src, dest);
			Security.setupTenantSecurity(Tenant.getTenant(name));
			LOGGER.info("Starting newly created tenant("+name+")......................");
			startTenantServices(name);
			if("default".equals(name)) {
				UserProfileManager.addUser(account);
				LOGGER.info("New user("+account.getUserId()+") added for the tenant "+name+" successfully.");
			}
			
			UserProfileManager.newTenant(name);
			LOGGER.info("New tenant with name "+name+" created successfully.");
			//Serc addPublicPrefixPath("/tenant/default/files/gui/middleware/pub/server/ui/welcome/", Tenant.getTenant("default"));
		} catch (Exception e) {
			printException("Failed to create tenant with name "+name, e);
			return e.getMessage();
		}
		return "Done";
	}
	
	public static void startTenantServices(String tenant) throws SnippetException {
		String uuid = UUID.randomUUID().toString();
		RuntimePipeline rp = RuntimePipeline.create(Tenant.getTenant(tenant),uuid, uuid, null, "GET/execute/packages.middleware.pub.server.core.service.main",
				"/execute/packages.middleware.pub.server.core.service.main");
		LOGGER.trace("RP created for "+tenant);
		LOGGER.trace("Executing startup servies for "+tenant);
		rp.dataPipeLine.applyAsync("packages.middleware.pub.server.core.service");
	}

	public static final URL[] getClassesURLs(String path) throws Exception {
		List<URL> urls = new ArrayList<URL>();
		String paths[] = path.split(getSeparator());

		for (String location : paths) {
			File file = new File(location);
			LOGGER.info("Class file path: "+ file.getAbsolutePath());
			if (file.getName().contains("ekamw"))
				LOGGER.info(file.getAbsolutePath());
			if (file.isFile())
				urls.add(file.toURL());
		}

		if (urls.size() > 0)
			return urls.toArray(new URL[urls.size()]);

		return null;
	}

	public static UserProfile getCurrentLoggedInUserProfile(HttpServerExchange exchange) throws SnippetException {
		final SecurityContext context = exchange.getSecurityContext();
		if (context != null)
			return ((Pac4jAccount) context.getAuthenticatedAccount()).getProfile();
		return null;
	}
	
	public static AuthAccount getCurrentLoggedInAuthAccount(HttpServerExchange exchange) throws SnippetException {
		if(getCurrentLoggedInUserProfile(exchange)==null)
			return null;
		return UserProfileManager.getUserProfileManager().getAccount(getCurrentLoggedInUserProfile(exchange));
	}

	public static final String[] getJarPaths(String path, String packagePath) throws Exception {
		URL urls[] = null;
		String paths[] = null;
		// String packagePath=PropertyManager.getPackagePath();
		LOGGER.info(" 780 JAR PATH: " + packagePath + path);
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
					// DynamicJarClassLoader dynamicJarClassLoader=new DynamicJarClassLoader(urls,
					// customClassLoader);
					// URLClassLoader urljl=new URLClassLoader(urls, customClassLoader);
					// urljl.d
					LOGGER.info("JAR PATH(" + indx + "): " + packagePath + path);
					return paths;
					// dataPipeline.getPayload().put("package", pname.toString() + " reloaded
					// successfully");
				}
				// else dataPipeline.getPayload().put("package", pname.toString() + " jars not
				// found at location '"+file.getAbsolutePath()+"'");

			}
			// dataPipeline.getPayload().put("package", pname.toString() + " no jars found
			// at location '"+file.getAbsolutePath()+"'");
		}
		// dataPipeline.getPayload().put("package", pname.toString() + " could not find
		// directory or it's empty '"+file.getAbsolutePath()+"'");
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
		FileInputStream fis = new FileInputStream(fileToZip);
		ZipEntry zipEntry = new ZipEntry(fileName);
		zipOut.putNextEntry(zipEntry);
		byte[] bytes = new byte[1024];
		int length;
		while ((length = fis.read(bytes)) >= 0) {
			zipOut.write(bytes, 0, length);
		}
		fis.close();
	}

	public static OIDCProviderMetadata fetchMetadata(String discoveryUrl, JWSAlgorithm jwsAlgo) throws Exception {
		URI issuerURI = new URI(discoveryUrl);
		URL providerConfigurationURL = issuerURI.toURL();
		InputStream stream = providerConfigurationURL.openStream();
		// Read all data from URL
		String providerInfo = null;
		try (java.util.Scanner s = new java.util.Scanner(stream)) {
			providerInfo = s.useDelimiter("\\A").hasNext() ? s.next() : "";
		}
		OIDCProviderMetadata oidcPM = OIDCProviderMetadata.parse(providerInfo);
		List<JWSAlgorithm> jwsArr = oidcPM.getIDTokenJWSAlgs();
		if (jwsArr == null)
			jwsArr = new ArrayList<>();
		jwsArr.add(jwsAlgo);
		oidcPM.setIDTokenJWSAlgs(jwsArr);
		return oidcPM;
	}

	public static String replaceAllIgnoreRegx(String source, String search, String replace) {

		return StringUtils.join(source.split(Pattern.quote(search)), replace);
	}
}
