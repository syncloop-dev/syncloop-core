package com.eka.middleware.service;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.eka.middleware.heap.CacheManager;
import com.eka.middleware.pooling.ScriptEngineContextManager;
import com.eka.middleware.template.SnippetException;
import com.eka.middleware.template.Tenant;


public class RuntimePipeline {
	private static Logger LOGGER = LogManager.getLogger(DataPipeline.class);

	private static final Map<String, RuntimePipeline> pipelines = new ConcurrentHashMap<String, RuntimePipeline>();
	private final String sessionId;
	private final String correlationId;
	private boolean isDestroyed = false;
	private Thread currentThread;
	private BufferedWriter bw = null;
	private Tenant tenant = null;
	private String user = null;
	private Date createDate = null;
	private Future<Map<String, Object>> futureMap = null;
	public final Map<String, Object> payload = new ConcurrentHashMap<String, Object>();

	private static final ExecutorService executor=Executors.newFixedThreadPool(200);

	public static ExecutorService getExecutor() {
		return executor;
	}

	public void writeSnapshot(String resource, String json) {
		String packagePath = PropertyManager.getPackagePath(getTenant());
		try {
			if (bw == null) {
				String name = sessionId + ".snap";
				File file = new File(packagePath + "/snapshots/" + resource + "/" + name);
				file.getParentFile().mkdirs();

				boolean isNewFileCreated = file.createNewFile();
				if (isNewFileCreated) {
					LOGGER.info("File created successfully: {}", file.getAbsolutePath());
				} else {
					LOGGER.warn("File already exists: {}", file.getAbsolutePath());
				}
				BufferedWriter bufferedWriter = Files.newBufferedWriter(Path.of(file.toURI()));
				bw = bufferedWriter;
				bw.write("[");
			}
			bw.write(json + ",");
			bw.newLine();
		} catch (Exception e) {
			ServiceUtils.printException(getTenant(), "Exception while saving snapshot.", e);
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



	public RuntimePipeline(Tenant tenant, String requestId, String correlationId,
						   String resource, String urlPath) {
		// Securitycont

		String threadAllowed = ServiceUtils.getServerProperty("middleware.server.datapipeline.async.threads");
		int numberOfThreadAllowed = 5;
		if (StringUtils.isNotBlank(threadAllowed)) {
			numberOfThreadAllowed = Integer.parseInt(threadAllowed);
		}
		//executor = Executors.newFixedThreadPool(numberOfThreadAllowed);
		this.tenant = tenant;
		currentThread = Thread.currentThread();
		sessionId = requestId;
		if (correlationId == null)
			correlationId = requestId;
		this.correlationId = correlationId;
		setCreateDate(new Date());

		setUser("System");

		dataPipeLine = new DataPipeline(this, resource, urlPath);
	}

	public static RuntimePipeline create(Tenant tenant,String requestId, String correlationId,
										 String resource, String urlPath) {
		// String md5=ServiceUtils.generateMD5(requestId+""+System.nanoTime());
		RuntimePipeline rp = pipelines.get(requestId);
		if (rp == null) {
			rp = new RuntimePipeline(tenant,requestId, correlationId, resource, urlPath);
			pipelines.put(requestId, rp);
		} else {
			ServiceUtils.printException(tenant, "Unable to create unique runtime pipeline", null);
			return null;
		}
		return rp;
	}

	public static RuntimePipeline getPipeline(String id) {
		RuntimePipeline rp = pipelines.get(id);
		return rp;
	}

	public static List<RuntimePipeline> listActivePipelines(DataPipeline dp) {
		Tenant tenant = dp.rp.tenant;
		List<RuntimePipeline> list = new ArrayList<>();
		Set<Entry<String, RuntimePipeline>> rtSet = pipelines.entrySet();
		for (Entry<String, RuntimePipeline> entry : rtSet) {
			RuntimePipeline arp = entry.getValue();
			if (tenant.getName().equalsIgnoreCase("default")
					|| tenant.getName().equalsIgnoreCase(arp.getTenant().getName()))
				list.add(entry.getValue());
		}
		return list;
	}

	public void destroy() {
		if (bw != null)
			try {
				bw.write("{}]");
				bw.flush();
				bw.close();
				bw = null;
			} catch (Exception e) {
				ServiceUtils.printException(getTenant(), "Exception while closing snapshot file.", e);
			}
		RuntimePipeline rtp = pipelines.get(sessionId);
		try {
			Map cache = CacheManager.getCacheAsMap(tenant);
			cache.remove(sessionId);
			ScriptEngineContextManager.removeContext(dataPipeLine.getUniqueThreadName());
			ScriptEngineContextManager.removeContext(rtp.dataPipeLine.getUniqueThreadName());
		} catch (Exception e) {

		}finally {
			try {
				rtp.currentThread.interrupt();
			} catch (Exception e2) {
				// TODO: handle exception
			}
			rtp.setDestroyed(true);
			pipelines.get(sessionId).payload.clear();
			pipelines.remove(sessionId);
		}
		try {
			/*executor.shutdown();
			try {
				// Wait for existing tasks to complete within the timeout
				if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
					// Forcefully shutdown if tasks did not finish in time
					executor.shutdownNow();

					// Wait for tasks to respond to cancellation
					if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
						ServiceUtils.printException("Executor did not terminate for tenant:" + getTenant().getName(),
								new Exception("Executor did not terminate"));// ();
					}
				}
			} catch (InterruptedException ie) {
				// Re-cancel if current thread also interrupted
				executor.shutdownNow();
			}*/
			if (futureMap != null && !(futureMap.isDone() || futureMap.isCancelled())) {
				futureMap.cancel(true);
			}
		} catch (Exception e) {
			ServiceUtils.printException(getTenant(), "Exception while finishing appLogger.", e);
		}

	}

	@Override
	protected void finalize() throws Throwable {
		destroy();
	}

	public static void destroy(String sessionId) {
		pipelines.get(sessionId).destroy();
	}

	public String getCorrelationId() {
		return correlationId;
	}

	public Tenant getTenant() {
		return tenant;
	}

	public Date getCreateDate() {
		return createDate;
	}

	public void setCreateDate(Date createDate) {
		this.createDate = createDate;
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public void setFutureMap(Future<Map<String, Object>> futureMap) {
		this.futureMap = futureMap;
	}

}
