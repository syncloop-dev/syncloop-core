package com.eka.middleware.service;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.pac4j.core.profile.UserProfile;
import org.pac4j.undertow.account.Pac4jAccount;

import com.eka.middleware.auth.AuthAccount;
import com.eka.middleware.auth.UserProfileManager;
import com.eka.middleware.server.ServiceManager;
import com.eka.middleware.template.SnippetException;
import com.eka.middleware.template.SystemException;

import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.session.Session;
import io.undertow.util.Sessions;

public class RuntimePipeline {
	private static final Map<String, RuntimePipeline> pipelines = new ConcurrentHashMap<String, RuntimePipeline>();
	private final String sessionId;
	private final String correlationId;
	private final HttpServerExchange exchange;
	private boolean isDestroyed = false;
	private Thread currentThread;
	private BufferedWriter bw = null;

	public boolean isExchangeInitialized() {
		if(exchange==null)
			return false;
		return true;
	}

	public HttpServerExchange getExchange() throws SnippetException {
		if (exchange == null)
			throw new SnippetException(dataPipeLine,
					"RuntimePipeline was not created through direct HTTP request. This can happen if it's restored from file or propagated through messaging service like JMS or KAFKA",
					new Exception("Exchange not initialized."));
		return exchange;
	}

	public void writeSnapshot(String resource, String json) {
		try {
			if (bw == null) {
				String name = sessionId + ".snap";
				File file = new File(ServiceManager.packagePath + "/snapshots/" + resource + "/" + name);
				file.getParentFile().mkdirs();
				file.createNewFile();
				BufferedWriter bufferedWriter = Files.newBufferedWriter(Path.of(file.toURI()));
				bw = bufferedWriter;
				bw.write("[");
			}
			bw.write(json+",");
			bw.newLine();
		} catch (Exception e) {
			ServiceUtils.printException("Exception while saving snapshot.", e);
		}
	}

	public boolean isDestroyed() {
		return isDestroyed;
	}

	public void setDestroyed(boolean isDestroyed) {
		this.isDestroyed = isDestroyed;
	}

	public final DataPipeline dataPipeLine;

	public String getSessionID() {
		return sessionId;
	}

	public RuntimePipeline(String requestId, String correlationId, final HttpServerExchange exchange, String resource,
						   String urlPath) {
		currentThread = Thread.currentThread();
		sessionId = requestId;
		this.exchange = exchange;
		if (correlationId == null)
			correlationId = requestId;
		this.correlationId = correlationId;
		dataPipeLine = new DataPipeline(this, resource, urlPath);
	}

	public UserProfile getCurrentLoggedInUserProfile() throws SnippetException {
		final SecurityContext context = getExchange().getSecurityContext();
		if (context != null)
			return ((Pac4jAccount)context.getAuthenticatedAccount()).getProfile();
		return null;
	}

	public void logOut() throws SnippetException {
		final SecurityContext context = getExchange().getSecurityContext();
		// ServletRequestContext servletRequestContext =
		// getExchange().getAttachment(ServletRequestContext.ATTACHMENT_KEY);
		clearSession();
		// SessionManager.ATTACHMENT_KEY.
		context.logout();
	}

	private void clearSession() {
		Map<String, String> sessionManager = exchange.getAttachment(HttpServerExchange.REQUEST_ATTRIBUTES);
		Map<String, Cookie> cookieMap = exchange.getRequestCookies();
		exchange.getConnection().terminateRequestChannel(exchange);
		Set<String> keys = cookieMap.keySet();
		for (String key : keys) {
			Cookie cookie = cookieMap.get(key);
			cookie.setDiscard(true);
		}
		cookieMap.clear();

		Session session = Sessions.getSession(exchange);
		if (session == null)
			return;
		HashSet<String> names = new HashSet<>(session.getAttributeNames());
		for (String attribute : names) {
			session.removeAttribute(attribute);
		}
	}

	public static RuntimePipeline create(String requestId, String correlationId, final HttpServerExchange exchange,
										 String resource, String urlPath) {
		// String md5=ServiceUtils.generateMD5(requestId+""+System.nanoTime());
		RuntimePipeline rp = pipelines.get(requestId);
		if (rp == null) {
			rp = new RuntimePipeline(requestId, correlationId, exchange, resource, urlPath);
			pipelines.put(requestId, rp);
		} else
			rp = create(requestId, correlationId, exchange, resource, urlPath);
		return rp;
	}

	public static RuntimePipeline getPipeline(String id) {
		RuntimePipeline rp = pipelines.get(id);
		return rp;
	}

	public List<RuntimePipeline> listActivePipelines() {
		List<RuntimePipeline> list = Arrays.asList((RuntimePipeline[]) pipelines.entrySet().toArray());
		return list;
	}

	public void destroy() {
		if(bw!=null) try{
			bw.write("]");
			bw.flush();
			bw.close();
			bw=null;
		}catch (Exception e) {
			ServiceUtils.printException("Exception while closing snapshot file.", e);
		}
		RuntimePipeline rtp = pipelines.get(sessionId);
		rtp.currentThread.interrupt();
		rtp.setDestroyed(true);
		pipelines.get(sessionId).payload.clear();
		pipelines.remove(sessionId);

	}

	public static void destroy(String sessionId) {
		pipelines.get(sessionId).destroy();
	}

	public String getCorrelationId() {
		return correlationId;
	}

	public final Map<String, Object> payload = new ConcurrentHashMap<String, Object>();
}
