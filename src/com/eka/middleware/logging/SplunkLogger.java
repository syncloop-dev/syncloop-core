package com.eka.middleware.logging;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.springframework.util.StopWatch;

@Component
@Scope("singleton")
public class SplunkLogger {

	private static final Logger splunkLogger = LogManager.getLogger("splunkLogger");

	private static final ThreadLocal<LogMetaHolder> CONTEXT = new ThreadLocal<>();


	// Not accepts null as input
	/*public void init(HttpServletRequest request) {
		try {
			if(null == request) return;
			if(null!= CONTEXT.get()) {
				return;
			}
			
			Map<String,Object> requestLogMap = getRequestAttributes(request);
			
			requestLogMap.put("PROCESSOR", "REST_API_SERVER");
			requestLogMap.put("TXN_ID" , UUID.randomUUID().toString());

			LogMetaHolder logMetaHolder = new LogMetaHolder();
			logMetaHolder.getMAP().putAll(requestLogMap);
			
			CONTEXT.set(logMetaHolder);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}*/

	public void init(HttpServletRequest request) {
		try {
			if (null != CONTEXT.get()) {
				return;
			}

			Map<String, Object> requestLogMap = getRequestAttributes(request);

			requestLogMap.put("PROCESSOR", "REST_API_SERVER");
			requestLogMap.put("TXN_ID", UUID.randomUUID().toString());

			LogMetaHolder logMetaHolder = new LogMetaHolder();
			logMetaHolder.getMAP().putAll(requestLogMap);

			CONTEXT.set(logMetaHolder);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static String changeFormat(LocalDateTime dateTime, String pattern) {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
		return dateTime.format(formatter);
	}

	public static LocalDateTime getGMTDatetime() {
		return LocalDateTime.now();
	}

	/**
	 * @return
	 */
	public String txnId() {
		if (null == CONTEXT.get() || null == CONTEXT.get().getMAP().get("TXN_ID")) {
			String uuid = UUID.randomUUID().toString();
			CONTEXT.get().getMAP().put("TXN_ID" , uuid);
			return uuid;
		}
		return CONTEXT.get().getMAP().get("TXN_ID").toString();
	}
	
	/**
	 * @param httpRequest
	 * @return
	 */
	private Map<String,Object> getRequestAttributes(HttpServletRequest httpRequest) {
		if (null == httpRequest) {
			return Maps.newHashMap();
		}
		Map<String,Object> requestAttributeMap = new HashMap <>();
		try {
		    Enumeration<String> headerNames = httpRequest.getHeaderNames();
		    while(headerNames.hasMoreElements()) {
		        String headerName = (String)headerNames.nextElement();
		        if ("COOKIE".equals(headerName)) {
		        	requestAttributeMap.put(headerName.toUpperCase(), 
			        		 cookieRebuild(httpRequest.getHeader(headerName)));
		        } else {
		        	requestAttributeMap.put(headerName.toUpperCase(), 
			        		 maskeSecure(headerName.toUpperCase(), httpRequest.getHeader(headerName)));
		        }
		    }
		    
		    Enumeration<String> params = httpRequest.getParameterNames();
		    while(params.hasMoreElements()){
		        String paramName = (String)params.nextElement();
		        String value = httpRequest.getParameter(paramName);
		        if(null!=value) {
		        	value = value.replace("\"", "'");
		        }
		        requestAttributeMap.put("REQ_ATTIB_"+paramName, maskeSecure(paramName, value));
		    }
		    
		} catch (Exception e) {
		}
		return requestAttributeMap;
	}
	
	/**
	 * @param key
	 * @param value
	 */
	public void add(String key, Object value) {
		if(StringUtils.isBlank(key) || null == value) return;
		if(null!=CONTEXT.get()) { 
			if((key.contains("_TT") || key.contains("_QT")) && null != CONTEXT.get().getMAP().get(key)) {
				long tt = (long) CONTEXT.get().getMAP().get(key);
				if(tt > (long) value)
					return;
			}
			CONTEXT.get().getMAP().put(key, value);
			if("OPERATION".equals(key)) {
				CONTEXT.get().getMAP().put(value + "_TS", changeFormat(getGMTDatetime(), "yyyy-MMM-dd HH:mm:ss"));
			}
		}	
	}

	public void finish() {
		try {
			CONTEXT.get().stopWatch.start();

			StopWatch stopWatch = CONTEXT.get().stopTracking();
			add("TT", stopWatch.getLastTaskTimeMillis());

			splunkLogger.warn(getFormattedLog());

			clear();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void clear() {
		CONTEXT.remove();
	}

	public String getRequestLogs() {
		return CONTEXT.get() != null ? CONTEXT.get().toString() : "";
	}
	
	/**
	 * @param cookie
	 * @return
	 */
	private static String cookieRebuild(String cookie) {
		StringBuilder builder = new StringBuilder();
		
		String[] cookies = StringUtils.split(cookie , ";");
		for (int i = 0; i < cookies.length; i++) {
			String item[] = StringUtils.split(StringUtils.trim(cookies[i]) , "=");
			if (item.length > 1) {
				builder.append(item[0]).append("=").append(maskeSecure(item[1], "---"));
			} else {
				builder.append(item[0]);
			}
			
			builder.append("; ");
		}
		
		return builder.toString();
	}
	
	/**
	 * @param params
	 * @param val
	 * @return
	 */
	private static String maskeSecure(String params , String val) {
		if (StringUtils.isBlank(params)) {
			return "-";
		}
		if (SECURED_PARAMS.contains(params.toUpperCase())) {
			return "******";
		}
		return val;
	}
	
	private static Set<String> SECURED_PARAMS = Sets.newHashSet();
	
	static {
		SECURED_PARAMS.add("AUTHORIZATION");
		SECURED_PARAMS.add("ACCESS_TOKEN");
		SECURED_PARAMS.add("PASSWORD");
		SECURED_PARAMS.add("CC");
		SECURED_PARAMS.add("CVV");
		SECURED_PARAMS.add("CREDITCARDNUMBER");
		SECURED_PARAMS.add("EXPIRYMONTH");
		SECURED_PARAMS.add("EXPIRYYEAR");
		SECURED_PARAMS.add("SECRET");
		SECURED_PARAMS.add("IMAGE_AUTHORIZATION");
		SECURED_PARAMS.add("TOKEN");
		SECURED_PARAMS.add("x-api-key");
	}

	public String getFormattedLog() {
		if (CONTEXT.get() != null) {
			StringBuilder logBuilder = new StringBuilder();
			for (Map.Entry<String, Object> entry : CONTEXT.get().getMAP().entrySet()) {
				logBuilder.append(entry.getKey()).append("=").append(entry.getValue()).append(" ");
			}
			return logBuilder.toString().trim();
		}
		return "";
	}


	public static void main(String[] args) {
		Configurator.initialize(null, "/Users/divyansh/Desktop/Syncloop/Local_CodeBase/ekamw-distributions-main/resources/config/log4j2.xml");

		SplunkLogger splunkLoggerInstance = new SplunkLogger();
		splunkLoggerInstance.init(null);

		splunkLoggerInstance.add("key1", "value1");
		splunkLoggerInstance.add("key2", "value2");
		splunkLoggerInstance.add("key3", "value3");

		splunkLoggerInstance.finish();

	}

}
