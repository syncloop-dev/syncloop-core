package com.eka.middleware.service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
//import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import javax.json.JsonArray;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pac4j.core.profile.UserProfile;

import com.eka.middleware.auth.AuthAccount;
import com.eka.middleware.auth.Security;
import com.eka.middleware.auth.UserProfileManager;
import com.eka.middleware.flow.FlowUtils;
import com.eka.middleware.flow.JsonOp;
import com.eka.middleware.heap.HashMap;
import com.eka.middleware.server.ServiceManager;
import com.eka.middleware.template.MultiPart;
import com.eka.middleware.template.SnippetException;

import io.undertow.server.HttpServerExchange;

public class DataPipeline {
	private static Logger LOGGER = LogManager.getLogger(DataPipeline.class);
	public final RuntimePipeline rp;
	private final String resource;
	private MultiPart mp = null;
	private final Map<String, Map<String, Object>> payloadStack = new HashMap<String, Map<String, Object>>();
	private final Map<String, Boolean> hasDroppedPrevious = new HashMap<String, Boolean>();
	private final List<String> resourceStack = new ArrayList<String>();
	private String currentResource = null;
	private final String urlPath;

	public DataPipeline(RuntimePipeline runtimePipeline, String resource, String urlPath) {
		rp = runtimePipeline;
		this.resource = resource;
		this.urlPath = urlPath;
		payloadStack.put(resource, rp.payload);
		currentResource = resource;
		resourceStack.add(resource);
	}

	public boolean isDestroyed() {
		return rp.isDestroyed();
	}

	public Object get(String key) {
		String currentResource = getCurrentResource();
		Map<String, Object> map = payloadStack.get(currentResource);
		if (map != null) {
			Object value = map.get(key);
			if (value != null)
				return value;
		}

		if (hasDroppedPrevious.get(currentResource) != null && hasDroppedPrevious.get(currentResource)
				|| (hasDroppedPrevious.get(currentResource + "-" + key) != null
						&& hasDroppedPrevious.get(currentResource + "-" + key))) {
			return null;
		}

		int length = resourceStack.size();
		if (length > 1) {
			boolean bit = false;
			for (int i = length - 1; i >= 0; i--) {
				String resource = resourceStack.get(i);
				if (resource.equals(currentResource) || bit) {
					bit = true;
					Map<String, Object> map2 = payloadStack.get(resource);
					if (map2 != null) {
						Object value = map2.get(key);
						if (value != null)
							return value;
					}
				}
				if (hasDroppedPrevious.get(resource + "-" + key) != null
						&& hasDroppedPrevious.get(resource + "-" + key))
					break;
			}
		}
		return null;
	}

	public String getString(String key) {

		Object value = get(key);
		if (value != null) {
			try {
				ArrayDeque<String> dVal = (ArrayDeque<String>) value;
				if (dVal.size() > 0)
					return dVal.getFirst();
			} catch (Exception e) {
				return value.toString();
			}
		}

		/*
		 * String currentResource = getCurrentResource(); Map<String, Object> map =
		 * payloadStack.get(currentResource); if (map != null) { Object value =
		 * map.get(key); if (value != null) { try { ArrayDeque<String>
		 * dVal=(ArrayDeque<String>)value; if(dVal.size()>0) return dVal.getFirst();
		 * }catch(Exception e) { return value.toString(); } } }
		 *
		 * if (hasDroppedPrevious.get(currentResource) != null &&
		 * hasDroppedPrevious.get(currentResource) ||
		 * (hasDroppedPrevious.get(currentResource + "-" + key) != null &&
		 * hasDroppedPrevious.get(currentResource + "-" + key))) { return null; }
		 *
		 * int length = resourceStack.size(); if (length > 1) { boolean bit = false; for
		 * (int i = length - 1; i >= 0; i--) { String resource = resourceStack.get(i);
		 * if (resource.equals(currentResource) || bit) { bit = true; Map<String,
		 * Object> map2 = payloadStack.get(resource); if (map2 != null) { Object value =
		 * map2.get(key); if (value != null) { try { ArrayDeque<String>
		 * dVal=(ArrayDeque<String>)value; if(dVal.size()>0) return dVal.getFirst();
		 * }catch(Exception e) { return value.toString(); } } } } if
		 * (hasDroppedPrevious.get(resource + "-" + key) != null &&
		 * hasDroppedPrevious.get(resource + "-" + key)) break; } }
		 */
		return null;
	}

	public Integer getInteger(String key) throws SnippetException {
		Object value = get(key);
		if (value != null) {
			try {
				ArrayDeque<Integer> dVal = (ArrayDeque<Integer>) value;
				if (dVal.size() > 0)
					return dVal.getFirst();
			} catch (Exception e) {
				try {
					return (Integer) value;
				} catch (Exception ee) {
					throw new SnippetException(this, ee.getMessage(), ee);
				}
			}
		}
		return null;
	}

	public void refresh() {
		int length = resourceStack.size();
		String currentResource = getCurrentResource();
		if (length > 1) {
			for (int i = length - 1; i >= 0; i--) {
				String resource = resourceStack.get(i);
				if (resource.equals(currentResource)) {
					String prevResource = resourceStack.get(i + 1);
					Map<String, Object> mapCur = payloadStack.get(currentResource);
					Map<String, Object> mapPrev = payloadStack.get(prevResource);
					if (mapPrev != null && mapPrev.size() > 0) {

						mapPrev.forEach((k, v) -> {
							if (v != null)
								mapCur.put(k, v);
							else
								mapCur.remove(k);
						});
						payloadStack.remove(prevResource);
					}
					resourceStack.remove(i + 1);
					payloadStack.remove(prevResource);
					Set<String> keys = hasDroppedPrevious.keySet();

					for (Iterator<String> iterator = keys.iterator(); iterator.hasNext();) {
						String key = iterator.next();
						if (key.startsWith(prevResource)) {
							iterator.remove();
						}
					}
					return;
				}
			}
		}
	}

	public void drop(String key) {
		String currentResource = getCurrentResource();
		Map<String, Object> map = payloadStack.get(currentResource);
		if (map != null)
			map.remove(key);
		hasDroppedPrevious.put(currentResource + "-" + key, true);
	}

	public void setResponseStatus(int statusCode) throws SnippetException {
		if (rp.isExchangeInitialized()) {
			rp.getExchange().setStatusCode(statusCode);
		} else {
			put("httpErrorCode", statusCode);
			// Ignore this exception. It's normally happens on async calls as exchange
			// object is not initialized.
		}
	}

	public void put(String key, Object value) {
		String currentResource = getCurrentResource();
		Map<String, Object> map = payloadStack.get(currentResource);
		if (map == null) {
			map = new HashMap<String, Object>();
			payloadStack.put(currentResource, map);
			// resourceStack.add(currentResource);
		}
		if (value == null)
			map.remove(key);
		else {
			map.put(key, value);
			hasDroppedPrevious.put(resource + "-" + key, false);
		}

	}

	public void putAll(Map<String, Object> value) {
		String currentResource = getCurrentResource();
		Map<String, Object> map = payloadStack.get(currentResource);
		if (map == null) {
			map = new HashMap<String, Object>();
			payloadStack.put(currentResource, map);
			// resourceStack.add(currentResource);
		}
		map.putAll(value);// (key, value);
		for (String key : map.keySet()) {
			hasDroppedPrevious.put(resource + "-" + key, false);
		}
	}

	public String getSessionId() {
		return rp.getSessionID();
	}

	public String getCorrelationId() {
		return rp.getCorrelationId();
	}

	public String getResource() {
		return resource;
	}

	public Map<String, Object> getPathParameters() {
		return (Map<String, Object>) rp.payload.get("*pathParameters");
	}

	public Map<String, Object> getQueryParameters() {
		return (Map<String, Object>) rp.payload.get("parameters");
	}

	public Map<String, Object> getHeaders() {
		return (Map<String, Object>) rp.payload.get("*requestHeaders");
	}

	public Map getAsMap(String pointer) {
		Map<String, Object> myMap = (Map<String, Object>) getValueByPointer(pointer);
		return myMap;
	}

	public List getAsList(String pointer) {
		Object list = getValueByPointer(pointer);
		if (list == null || list.getClass() != ArrayList.class) {
			
			if(list instanceof Object[]) {
				return Arrays.asList((Object[])list);
			}
			else
				return null;
		}
		List<Object> arrayList = (List<Object>) list;
		return arrayList;
	}

	public Integer getAsInteger(String pointer) {
		Integer val = (Integer) getValueByPointer(pointer);
		return val;
	}

	public Double getAsNumber(String pointer) {
		Double val = (Double) getValueByPointer(pointer);
		return val;
	}

	public Byte getAsByte(String pointer) {
		Byte val = (Byte) getValueByPointer(pointer);
		return val;
	}

	public Date getAsDate(String pointer) {
		Date val = (Date) getValueByPointer(pointer);
		return val;
	}

	public Boolean getAsBoolean(String pointer) {
		Boolean val = (Boolean) getValueByPointer(pointer);
		return val;
	}

	public String getAsString(String pointer) {
		String val = (String) getValueByPointer(pointer);
		return val;
	}

	public Object getValueByPointer(String pointer) {
		return MapUtils.getValueByPointer(pointer, this);
	}
	
	public void setValueByPointer(String pointer, Object value, String outTypePath) {
		MapUtils.setValueByPointer(pointer,value,outTypePath, this);
	}

	

	public String toJson() {
		try {

			return ServiceUtils.toJson(payloadStack.get(currentResource));
		} catch (Exception e) {
			ServiceUtils.printException("Could not convert datapipeline '" + rp.getSessionID() + "' to Json", e);
		}
		;
		return null;
	}

	public String toXml() {
		try {
			String wrapperRootName = (String) get("@wrapperRootName");
			if (wrapperRootName == null)
				wrapperRootName = "root";
			return ServiceUtils.toXml(payloadStack.get(currentResource), wrapperRootName);
		} catch (Exception e) {
			ServiceUtils.printException("Could not convert datapipeline '" + rp.getSessionID() + "' to Xml", e);
		}
		return null;
	}

	public Map<String, Object> getMap() {
		return payloadStack.get(currentResource);
	}

	public String toYaml() {
		try {

			return ServiceUtils.toYaml(payloadStack.get(currentResource));
		} catch (Exception e) {
			ServiceUtils.printException("Could not convert datapipeline '" + rp.getSessionID() + "' to Yaml", e);
		}
		;
		return null;
	}

	public void save(String name) {
		String packagePath=PropertyManager.getPackagePath(rp.getTenant());
		File file = new File(packagePath + "/packages/" + (resource.replace(".", "/")) + "_" + name
				+ "_dataPipeline.json");

		LOGGER.info("Saving dataPipeline: " + file.getAbsolutePath());
		String dirname = file.getAbsolutePath().replace(file.getName(), "");
		File dir = new File(dirname);
		if (!dir.exists())
			dir.mkdirs();
		try {
			if (!file.exists()) {
				file.createNewFile();
			}
			Map<String, Object> map = new HashMap<String, Object>();
			map.put("@sessionId", getSessionId());
			map.put("@correlationId", getCorrelationId());
			map.put("@resource", resource);
			Map<String, Object> payload = payloadStack.get(currentResource);
			payload.put("@DataPipeLineMetaData", map);
			String json = toJson();
			if (json != null) {
				FileOutputStream fos = new FileOutputStream(file);
				fos.write(json.getBytes());
				fos.flush();
				fos.close();
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			ServiceUtils.printException("Could not save pipeline", e);
		}
	}

	public void restore(String name) {
		String packagePath=PropertyManager.getPackagePath(rp.getTenant());
		try {
			File file = new File(packagePath + "/packages/" + (currentResource.replace(".", "/")) + "_"
					+ name + "_dataPipeline.json");
			byte[] data = ServiceUtils.readAllBytes(file);
			String json = new String(data);
			Map<String, Object> payload = ServiceUtils.jsonToMap(json);
			if (resource.equals(currentResource))
				rp.payload.putAll(payload);
			else
				payloadStack.put(currentResource, payload);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			ServiceUtils.printException("Could not save pipeline", e);
		}
	}

	public void snap(String comment) {
		try {
			HashMap<String, Object> map = new HashMap<>();
			if (comment == null)
				comment = "Commentless step";
			map.put("comment", comment);
			map.put(currentResource, payloadStack);
			String json = ServiceUtils.toPrettyJson(map);
			rp.writeSnapshot(resource, json);
		} catch (Exception e) {
			ServiceUtils.printException("Exception while taking snapshot.", e);
		}
	}

	public InputStream getBodyAsStream() throws SnippetException {
		return ServiceUtils.getBodyAsStream(this);
	}

	public InputStream getFile(String paramNameOrFileName) throws SnippetException {
		Object obj = getMultiPart().formData.get(paramNameOrFileName);
		if (obj == null)
			return null;
		InputStream is = (InputStream) obj;
		return is;
	}

	public String getFileName(String paramName) throws SnippetException {
		Object obj = getMultiPart().formData.get(paramName + "_fileName");
		return (String) obj;
	}

	public List<InputStream> getFiles() throws SnippetException {
		Object obj = getMultiPart().formData.get("uploadedFiles");
		if (obj == null)
			return null;
		List<InputStream> streams = (List<InputStream>) obj;
		return streams;
	}

	public String getFileName(InputStream fileStream) throws SnippetException {
		Object obj = getMultiPart().formData.get(fileStream);
		return (String) obj;
	}

	private byte body[] = null;

	public byte[] getBody() throws SnippetException {
		if (this.rp.getExchange() == null)
			throw new SnippetException(this, "Error while getting body from datapipeline.", new Exception(
					"Multipart entry can only be fetched from top level service stack called from outside the middleware server any async or pub/sub will cause exchange to close the session"));
		if (body == null)
			body = ServiceUtils.getBody(this);
		return body;
	}

	public void setBody(byte bytes[]) throws SnippetException {
		try {
			new MultiPart(this, bytes);
		} catch (Exception e) {
			throw new SnippetException(this, "Service('" + getCurrentResource() + "') failed while setting body bytes",
					e);
		}
	}

	public void setBody(InputStream is, boolean downloadAsStreamFile) throws SnippetException {
		try {
			new MultiPart(this, is, downloadAsStreamFile);
		} catch (Exception e) {
			throw new SnippetException(this,
					"Service('" + getCurrentResource() + "') failed while setting body streaming", e);
		}
	}

	public void setBody(File file) throws SnippetException {
		try {
			new MultiPart(this, file);
		} catch (Exception e) {
			throw new SnippetException(this, "Service('" + getCurrentResource() + "') failed while setting body file",
					e);
		}
	}

	public MultiPart getMultiPart() throws SnippetException {
		if (this.rp.getExchange() == null)
			throw new SnippetException(this, "Error while getting body from datapipeline.", new Exception(
					"Multipart entry can only be fetched from top level service stack called from outside the middleware server any async or pub/sub will cause exchange to close the session"));
		if (mp == null)
			mp = ServiceUtils.getMultiPart(this);
		return mp;
	}

	public String getUniqueThreadName() {
		return "cb_" + (getCurrentResource().hashCode() & 0xfffffff);
	}

	public String getCurrentResource() {
		// StackTraceElement[] ste = Thread.currentThread().getStackTrace();
		// Since its a private method it will be called
		// inside it's own class function so we get the
		// stack that called the last public function that
		// called this method
		// LOGGER.info("Current stack resource: " + currentResource);
		return currentResource;// ste[3].getClassName() + "." + ste[3].getMethodName();
	}

	public void clear() {
		String currentResource = getCurrentResource();
		if (currentResource.equals(resource))
			rp.payload.clear();
		hasDroppedPrevious.put(currentResource, true);
		Map<String, Object> map = payloadStack.get(currentResource);
		if (map != null)
			map.clear();
	}
	private String callingResource = null;
	public void apply(String fqnOfMethod) throws SnippetException {
		if(fqnOfMethod==null)
			return;
		try {

			fqnOfMethod = fqnOfMethod.replace("/", ".");
			if (!fqnOfMethod.endsWith(".main"))
				fqnOfMethod += ".main";
			if (payloadStack.get(fqnOfMethod) != null) {
				Exception e = new Exception("Can not apply method.");
				e.setStackTrace(Thread.currentThread().getStackTrace());
				throw new SnippetException(this, "Recursion is not allowed for method '" + fqnOfMethod + "'", e);
			}
			String callingResource = currentResource;
			this.callingResource=callingResource;
			currentResource = fqnOfMethod;
			resourceStack.add(currentResource);
			put("*currentResource", currentResource);
			try {
				ServiceUtils.execute(fqnOfMethod, this);
			} catch (SnippetException e) {
				// currentResource = curResourceBkp;
				// refresh();
				throw e;
				// ServiceUtils.printException("Error caused by "+fqnOfMethod, new
				// Exception(e));
			} finally {
				currentResource = callingResource;
				this.callingResource=null;
				refresh();
			}
		} catch (Exception e) {
//			this.logDataPipeline();
			if(!e.getMessage().contains("packages.middleware.pub.service.exitRepeat"))
				throw new SnippetException(this,"Something went wrong with fqnOfMethod: "+fqnOfMethod , e);
			else
				throw new SnippetException(this,e.getMessage() , e);
		}
	}

	
	public void applyAsync(String fqnOfMethod,final JsonArray transformers) throws SnippetException {
		if(fqnOfMethod==null)
			return;
		fqnOfMethod = fqnOfMethod.replace("/", ".");
		if (!fqnOfMethod.endsWith(".main"))
			fqnOfMethod += ".main";
		final String fqnOfFunction = fqnOfMethod;
		// String curResourceBkp = currentResource;
		// currentResource = fqnOfFunction;
		// resourceStack.add(currentResource);
		// put("*currentResource", currentResource);
		final RuntimePipeline rp=this.rp;
		final String correlationID = this.getCorrelationId();
		final Map<String, Object> asyncInputDoc = this.getAsMap("asyncInputDoc");
		final Map<String, Object> asyncOutputDoc = new HashMap<String, Object>();
		final Map<String, String> metaData = new HashMap<String, String>();
		asyncOutputDoc.put("*metaData", metaData);
		final String uuidAsync = ServiceUtils.generateUUID("Async operation: " + fqnOfFunction + System.nanoTime());
		metaData.put("batchId", uuidAsync);
		metaData.put("status", "Active");
		ExecutorService executor = Executors.newSingleThreadExecutor();
		
		try {
			if(transformers!=null) {
				Map<String, List<JsonOp>> map = FlowUtils.split(transformers, "out");
				List<JsonOp> leaders = map.get("leaders");
				for (JsonOp jsonValue : leaders) {
					String srcPath=jsonValue.getFrom();
					if(srcPath.contains("*metaData") || srcPath.equals("/asyncOutputDoc"))
						continue;
					String keyPath=srcPath.replace("/asyncOutputDoc", "");
					srcPath=keyPath;
					//Object val = dpAsync.getValueByPointer(srcPath);
					///
					//String[] keyTokens = key.split("/");
					//String actualKey = (keyTokens[keyTokens.length - 1]);
					
					String typePath=jsonValue.getInTypePath();
					Object value=null;
					String[] typeTokens = typePath.split("/");
					String valueType = (typeTokens[typeTokens.length - 1]).toLowerCase();
					switch (valueType) {
					case "documentlist":
						value=new ArrayList<Object>();
						break;
					case "document":
						value=new HashMap<>();
						break;
					}
					if(value!=null) {
						final Object newFinalObj=value;
						MapUtils.setValueByPointer(srcPath, newFinalObj, typePath,asyncOutputDoc);
					}
				}
			}
		} catch (Exception e) {
			throw new SnippetException(this, uuidAsync, e);
		}
		final String currResrc=currentResource;
		final Future<Map<String, Object>> futureMap = executor.submit(() -> {
			try {
				final RuntimePipeline rpAsync = RuntimePipeline.create(rp.getTenant(),uuidAsync, correlationID, null, fqnOfFunction,
						"");
				final DataPipeline dpAsync = rpAsync.dataPipeLine;
				String json = null;
				if (asyncInputDoc != null && asyncInputDoc.size() > 0) {
					Object mtdt = asyncInputDoc.get("*metaData");
					if (mtdt != null) {
						Map<String, String> mtdtMap = (Map<String, String>) mtdt;
						if (mtdtMap != null && mtdtMap.size() > 0) {
							mtdtMap.forEach((k, v) -> {
								if (v != null && !("batchId".equals(k) || "status".equals(k)))
									metaData.put(k, v);
							});
						}
					} /*
						 * //Don't delete this code it will required later at some point of time. json =
						 * ServiceUtils.toJson(asyncInputDoc); Map<String, Object> mapIn =
						 * ServiceUtils.jsonToMap(json); if (mapIn != null && mapIn.size() > 0)
						 * mapIn.forEach((k, v) -> { dpAsync.put(k, v); });
						 */
					asyncInputDoc.forEach((k, v) -> {
						if (v != null)
							dpAsync.put(k, v);
					});
				}
				// dpAsync.put("asyncInputDoc", asyncInputDoc);
				// ServiceUtils.execute(fqnOfFunction, dpAsync);
				dpAsync.callingResource=currResrc;
				ServiceManager.invokeJavaMethod(fqnOfFunction, dpAsync);
				
				
				
				Map<String, Object> asyncOut = dpAsync.getMap();
				asyncOut.forEach((k, v) -> {
					if(transformers!=null) {
					Object obj=dpAsync.get(k);
					Object outObj=asyncOutputDoc.get(k);
					if(outObj!=null ) {
						if(obj instanceof Map) {
							Map<String, Object> objMap=(Map)obj;
							Map<String, Object> outObjMap=(Map)outObj;
							objMap.forEach((mk, mv) -> {
								outObjMap.put(mk,mv);
							});
						}else if(obj instanceof List) {
							List objLst=(List)obj;
							List outObjLst=(List)outObj;
							objLst.forEach((o)->{
								outObjLst.add(o);
							});
						}
					}else
						asyncOutputDoc.put(k, v);
					}else
						asyncOutputDoc.put(k, v);
				});
				metaData.put("status", "Completed");
				return asyncOutputDoc;
			} catch (Exception e) {
				ServiceUtils.printException("Exception caused on async operation correlationID: " + correlationID
						+ ". Batch Id: " + metaData.get("batchId"), e);
				metaData.put("status", "Failed");
				asyncOutputDoc.put("error", e.getMessage());
				throw e;
			} finally {
				asyncOutputDoc.put("*metaData", metaData);
			}
		});
		// currentResource = curResourceBkp;
		// refresh();
		put("asyncOutputDoc", asyncOutputDoc);
	}
	
	public void applyAsync(String fqnOfMethod) throws SnippetException {
		applyAsync(fqnOfMethod, null);
	}

	public String getMyConfig(String key) throws SnippetException {
		Properties props = PropertyManager.getProperties(this, getCurrentResource() + ".properties");
		String value = props.getProperty(key);
		String expressionValue=null;
		if (value != null && value.trim().toUpperCase().startsWith("#GLOBAL/")) {
			String vls[] = value.split("/");
			if (vls.length == 2 && vls[1] != null && vls[1].trim().length() > 0)
				key = vls[1];
			else
				return null;
			expressionValue=getGlobalConfig(key);
		} else if (value != null && value.trim().toUpperCase().startsWith("#PACKAGE/")) {
			String vls[] = value.split("/");
			if (vls.length == 2 && vls[1] != null && vls[1].trim().length() > 0)
				key = vls[1];
			else
				return null;
			expressionValue = getGlobalConfig(key);
		}
		
		expressionValue=props.getProperty(key);
		
		if(key.toLowerCase().startsWith("secure.") && expressionValue.startsWith("[#]")) {
			expressionValue=expressionValue.replace("[#]","");
			String privKey=getGlobalConfig(Security.PRIVATE_PROPERTY_KEY_NAME);
			if(key!=null)
				try {
					expressionValue=Security.getNormalString(expressionValue,privKey);
				} catch (Exception e) {
					throw new SnippetException(this, "Could not decrypt property '"+key+"' for '"+rp.getTenant()+"'", e);
				}
		}
		return expressionValue;
	}

	public String getMyPackageConfig(String key) throws SnippetException {
		Properties props = PropertyManager.getProperties(this, "package.properties");
		String expressionValue=props.getProperty(key);
		if(key.toLowerCase().startsWith("secure.") && expressionValue.startsWith("[#]")) {
			expressionValue=expressionValue.replace("[#]","");
			String privKey=getGlobalConfig(Security.PRIVATE_PROPERTY_KEY_NAME);
			if(key!=null)
				try {
					expressionValue=Security.getNormalString(expressionValue,privKey);
				} catch (Exception e) {
					throw new SnippetException(this, "Could not decrypt property '"+key+"' for '"+rp.getTenant()+"'", e);
				}
		}
		
		return expressionValue;
	}
	
	public void saveProperties(String path,byte[] data) {
		Properties props=new Properties();
		File file=new File(path);
		ByteArrayInputStream bais=new ByteArrayInputStream(data);
		try {
			props.load(bais);
			bais.close();
			Set keys=props.keySet();
			for (Object key : keys) {
				if(key.toString().toLowerCase().startsWith("secure.")) {
					String value=props.getProperty((String)key);
					if(!value.startsWith("[#]")) {
						String publicKey=getGlobalConfig(Security.PUBLIC_PROPERTY_KEY_NAME);
						value="[#]"+Security.getSecureString(value, publicKey);
						props.setProperty((String) key, value);
					}
				}
			}
			PropertyManager.saveProperties(path, props,"Properties saved at "+new Date().toString());
		} catch (Exception e) {
			ServiceUtils.printException("Failed to save properties '"+path+"'", e);
		} 
	}
	
	public String getMyPackageConfigPath() {
		String packagePath=PropertyManager.getPackagePath(rp.getTenant())+"packages/";
		String packageName = getCurrentResource();
		String pkg[] = packageName.split(Pattern.quote("."));
        if (pkg.length == 1)
            packageName = pkg[0];
        else
            packageName = pkg[1];
        String path = packageName + "/dependency/config";
		return packagePath + path;
	}

	public Properties getMyProperties() throws SnippetException {
		Properties props = PropertyManager.getProperties(this, getCurrentResource() + ".properties");
		return props;
	}

	public void logDataPipeline(Level level) {
		if (level == null)
			level = Level.INFO;
		String dpl = ServiceUtils.getFormattedLogLine(getCorrelationId(), currentResource, toJson());
		LOGGER.log(level, dpl);
	}

	public void logException(Throwable exception) {
		new SnippetException(this, "Eexception reported by " + getCurrentResource(), new Exception(exception));
	}

	public void logDataPipeline() {
		logDataPipeline(null);
	}

	public void log(String msg) {
		log(msg, null);
	}

	public void log(String msg, Level level) {
		if (level == null)
			level = Level.INFO;
		String resource=callingResource;
		if(resource==null)
			resource=currentResource;
		String log = ServiceUtils.getFormattedLogLine(getCorrelationId(), resource, msg);
		LOGGER.log(level, log);
	}

	public String getGlobalConfig(String key) throws SnippetException {
		Properties props = PropertyManager.getProperties(this, "global.properties");
		String expressionValue=props.getProperty(key);
		if(key.toLowerCase().startsWith("secure.") && expressionValue.startsWith("[#]")) {
			expressionValue=expressionValue.replace("[#]","");
			String privKey=getGlobalConfig(Security.PRIVATE_PROPERTY_KEY_NAME);
			if(key!=null)
				try {
					expressionValue=Security.getNormalString(expressionValue,privKey);
				} catch (Exception e) {
					throw new SnippetException(this, "Could not decrypt property '"+key+"' for '"+rp.getTenant()+"'", e);
				}
		}
		return expressionValue;
	}

	public int getStackIndex() {
		return resourceStack.size() - 1;
	}

	public String getUrlPath() {
		return urlPath;
	}

	public AuthAccount getCurrentRuntimeAccount() throws SnippetException {
		if(getCurrentUserProfile()==null)
			return null;
		return UserProfileManager.getUserProfileManager().getAccount(getCurrentUserProfile());
	}

	public UserProfile getCurrentUserProfile() throws SnippetException {
		return rp.getCurrentLoggedInUserProfile();
	}

	public void clearUserSession() throws SnippetException {
		rp.logOut();
	}

	public String getRemoteIpAddr() {
		
		if(!rp.isExchangeInitialized())
			return "localhost";
		try {
			HttpServerExchange httpServerExchange = rp.getExchange();
			if (null != httpServerExchange) {
				return httpServerExchange.getHostAndPort();
			}
		} catch (SnippetException e) {
			ServiceUtils.printException("Exchange not initialized..", e);
		}
		return "localhost";
	}
}
