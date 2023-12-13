package com.eka.middleware.distributed;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.ignite.lang.IgniteBiPredicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.eka.middleware.distributed.offHeap.IgNode;
import com.eka.middleware.distributed.offHeap.IgQueue;
import com.eka.middleware.heap.CacheManager;
import com.eka.middleware.heap.HashMap;
import com.eka.middleware.server.MiddlewareServer;
import com.eka.middleware.server.ServiceManager;
import com.eka.middleware.service.DataPipeline;
import com.eka.middleware.service.RuntimePipeline;
import com.eka.middleware.service.ServiceUtils;
import com.eka.middleware.template.Tenant;

public class QueueTaskExecuter {

	private static final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(50);
	private static Logger LOGGER = LogManager.getLogger(MiddlewareServer.class);
	
	public static List<String> listPendingBatches(final Tenant tenant){
		final Queue pendingQueue = QueueManager.getQueue(tenant, "PendingTaskQueue");
		final List<String> pendingTasks=new ArrayList<>();
		pendingQueue.forEach(batchID->{
			pendingTasks.add((String)batchID);
		});
		return pendingTasks;
	}
	
	public static List<Map> listPendingTasks(final Tenant tenant){
		final Map BSQC=CacheManager.getOrCreateNewCache(tenant, "BackupServiceQueueCache");
		final Queue pendingQueue = QueueManager.getQueue(tenant, "PendingTaskQueue");
		final List<Map> pendingTasks=new ArrayList<>();
		pendingQueue.forEach(batchID->{
			String json=(String)BSQC.get(batchID);
			if(json!=null)
				pendingTasks.add((Map) ServiceUtils.jsonToMap(json).get("*metaData"));
		});
		return pendingTasks;
	}
	
	public static List<Map> listActiveServices(final Tenant tenant){
		final Map BSQC=CacheManager.getOrCreateNewCache(tenant, "BackupServiceQueueCache");
		final Queue pendingQueue = QueueManager.getQueue(tenant, "ServiceQueue");
		final List<Map> pendingTasks=new ArrayList<>();
		pendingQueue.forEach(batchID->{
			String json=(String)BSQC.get(batchID);
			if(json!=null)
				pendingTasks.add((Map) ServiceUtils.jsonToMap(json).get("*metaData"));
		});
		return pendingTasks;
	}
	
	public static void start(final Tenant tenant) {
		final Map BSQC=CacheManager.getOrCreateNewCache(tenant, "BackupServiceQueueCache");
		String localNodeID = IgNode.getLocalNode(tenant);
		LOGGER.debug("*************************************************************************");
		LOGGER.debug("Localnode ID: " + localNodeID);
		LOGGER.debug("*************************************************************************");
		final Queue bq = QueueManager.getQueue(tenant, "BatchQueue-" + localNodeID);
		final Queue pendingQueue = QueueManager.getQueue(tenant, "PendingTaskQueue");
		final Queue serviceQueue = QueueManager.getQueue(tenant, "ServiceQueue");
		
//------Receives the task and queues it to serviceQueue----------
		IgNode.getIgnite().message().localListen(tenant.getName(), new IgniteBiPredicate<UUID, String>() {
			@Override
			public boolean apply(UUID nodeId, String data) {
				try {
					String batchID = data;
					Queue queue = QueueManager.getQueue(tenant, IgNode.getLocalNode(tenant));
					if (batchID != null) {
						serviceQueue.add(batchID);
					}
					return true; // Return true to keep listening
				} catch (Exception e) {
					ServiceUtils.printException(
							"Could not add the message to node queue:" + IgNode.getLocalNode(tenant), e);
					return false;
				}

			}
		});
		
//------polling from service queue------------------
		executor.submit(() -> {
			while (true) {
				try {
					String batchID = null;
					if (IgNode.getIgnite() != null)
						batchID = (String) ((IgQueue) serviceQueue).take();
					else
						batchID = (String) serviceQueue.poll();
					if (batchID != null) {
						bq.add(batchID);
						pendingQueue.add(batchID);
						if (batchID != null) {
							try {
								String json = (String) BSQC.get(batchID);
								if(json!=null) {
									LOGGER.debug("Backing up	" + batchID + " on BatchQueue: " + "BatchQueue-" + IgNode.getLocalNode(tenant));
									execute(tenant, json, null);
								}
								else {
									bq.remove(batchID);
									pendingQueue.remove(batchID);
								}
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}
				} catch (Throwable e) {
					ServiceUtils.printException(
							"Exception caused while reading tasks from ServiceQueue: " + localNodeID, new Exception(e));
				}
			}
		});
		
//------polling from random node removal queue------------------
		executor.submit(() -> {
			String nodeID = IgNode.getLocalNode(tenant);
			String queueName = "RemoveFromBatchQueueCache-" + nodeID;
			Queue queue = QueueManager.getQueue(tenant, queueName);
			while (true) {
				try {
					int delay = 1;
					Thread.sleep(delay);
					String batchId = null;
					if (IgNode.getIgnite() != null)
						batchId = (String) ((IgQueue) queue).take();
					else
						batchId = (String) queue.poll();
					if (batchId != null) {
						pendingQueue.remove(batchId);
						BSQC.remove(batchId);
						LOGGER.debug("Batch removed: " + batchId);
					}
				} catch (Throwable e) {
					ServiceUtils.printException(
							"Exception caused while reading tasks from internal random node removal queue: " + nodeID,
							new Exception(e));
				}
			}
		});
	}

	public static void start(final Tenant tenant, final String queueName, final String fqn) {
		executor.submit(() -> {
			while (true) {
				try {
					Queue queue = QueueManager.getQueue(tenant, queueName);
					String json = (String) queue.poll();
				} catch (Throwable e) {
					ServiceUtils.printException(
							"Exception caused while reading tasks from configured queue: " + queueName,
							new Exception(e));
				}
			}
		});
	}

	public static void execute(Tenant tenant, final String json, String fqn) {
		try {
			Map<String, Object> asyncInputDoc = ServiceUtils.jsonToMap(json);
			Map<String, Object> asyncOutputDoc = new HashMap<>();
			final Map<String, Object> metaData = (Map<String, Object>) asyncInputDoc.get("*metaData");
			if (fqn == null)
				fqn = (String) metaData.get("*fqnOfFunction");
			final String fqnOfFunction = fqn;
			final Boolean enableResponse = (Boolean) metaData.get("*enableResponse");
			final String correlationID = (String) metaData.get("*correlationID");
			final String uuidAsync = (String) metaData.get("*uuidAsync");
			final String batchId = (String) metaData.get("batchId");
			String nodeID = IgNode.getLocalNode(tenant);
			String queueName = "RemoveFromBatchQueueCache-" + nodeID;
			
			final Queue bqRemoval = QueueManager.getQueue(tenant, queueName);
			
			asyncOutputDoc.put("*metaData", metaData);
			executor.submit(() -> {

				RuntimePipeline rpRef = null;
				long startTime = System.currentTimeMillis();
				try {
					final RuntimePipeline rpAsync = RuntimePipeline.create(tenant, uuidAsync, correlationID, null,
							fqnOfFunction, "");
					rpRef = rpAsync;

					metaData.put("*start_time", new Date().toString());
					metaData.put("*start_time_ms", System.currentTimeMillis());
					final DataPipeline dpAsync = rpAsync.dataPipeLine;
					asyncInputDoc.forEach((k, v) -> {
						if (v != null && k != null)
							dpAsync.put(k, v);
					});

					dpAsync.appLog("TENANT", dpAsync.rp.getTenant().getName());
					dpAsync.appLog("URL_PATH", dpAsync.getUrlPath());
					dpAsync.appLog("RESOURCE_NAME", dpAsync.getCurrentResource());
					metaData.put("*sessionID", dpAsync.getSessionId());
					if (fqnOfFunction.startsWith("packages")) {
						try {
							ServiceManager.invokeJavaMethod(fqnOfFunction, dpAsync);
						} catch (Throwable e) {
							e.printStackTrace();
							throw new Exception(e);
						}

					} else {
						ServiceUtils.executeEmbeddedService(dpAsync,
								CacheManager.getEmbeddedService(
										fqnOfFunction.replaceAll("embedded.", "").replaceAll(".main", ""),
										dpAsync.rp.getTenant()));
					}
					Map<String, Object> asyncOut = dpAsync.getMap();
					asyncOutputDoc.putAll(asyncOut); // put("asyncOutputDoc", asyncOut);
					metaData.put("status", "Completed");
				} catch (Exception e) {
					ServiceUtils.printException("Exception caused on async operation correlationID: " + correlationID
							+ ". Batch Id: " + metaData.get("batchId"), e);
					metaData.put("status", "Failed");
					metaData.put("error", e.getMessage());
				} finally {
					metaData.put("*end_time", new Date().toString());
					metaData.put("*total_duration_ms", (System.currentTimeMillis() - startTime) + "");
					try {
						if (enableResponse) {
							String jsonResult = ServiceUtils.toJson(asyncOutputDoc);
							Map<String, Object> cache = CacheManager.getCacheAsMap(tenant);
							cache.put(batchId, jsonResult);
						}
					} catch (Exception e) {
						ServiceUtils.printException(
								"Exception caused on execute queued task operation after successful execution. correlationID: "
										+ correlationID + ". Batch Id: " + batchId,
								e);
					} finally {
						try {
							bqRemoval.add(batchId);
							rpRef.destroy();
						} catch (Exception e) {
							ServiceUtils.printException(
									"Exception caused on execute queued task operation in finally block. correlationID: "
											+ correlationID + ". Batch Id: " + batchId,
									e);
						}
					}
				}
			});
		} catch (Exception e) {
			ServiceUtils.printException("Exception in task execution. Task Data:" + json, e);
		}

	}

}
