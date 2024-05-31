package com.eka.middleware.guaranteed;

import java.util.Date;
import java.util.Map;

import com.eka.middleware.heap.CacheManager;
import com.eka.middleware.heap.HashMap;
import com.eka.middleware.service.DataPipeline;
import com.eka.middleware.service.RuntimePipeline;
import com.eka.middleware.template.Tenant;
import com.eka.middleware.server.ServiceManager;
import com.eka.middleware.service.ServiceUtils;


public class ExecutionWorkflow implements WorkflowTask{
    
	public void execute(Tenant tenant, final String json, String fqn) {
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
			asyncOutputDoc.put("*metaData", metaData);

//			executor.submit(() -> {

				RuntimePipeline rpRef = null;
				long startTime = System.currentTimeMillis();
				try {
					final RuntimePipeline rpAsync = RuntimePipeline.create(tenant, uuidAsync, correlationID, 
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
					
					if (fqnOfFunction.startsWith("packages")) {
						metaData.put("*sessionID", dpAsync.getSessionId());
						try {
							ServiceManager.invokeJavaMethod(fqnOfFunction, dpAsync);
						} catch (Throwable e) {
							e.printStackTrace();
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
					// throw e;
				} finally {
					metaData.put("*end_time", new Date().toString());
					metaData.put("*total_duration_ms", (System.currentTimeMillis() - startTime) + "");
					try {
						if (enableResponse) {
							String jsonResult = ServiceUtils.toJson(asyncOutputDoc);
							Map<String, Object> cache = CacheManager.getCacheAsMap(tenant);
							cache.put(batchId, jsonResult);
							//System.out.println(" - Batch result saved: " + batchId + "\n");
						}
						rpRef.destroy();
					} catch (Exception e) {
						ServiceUtils.printException(
								"Exception caused on async operation after successful execution. correlationID: "
										+ correlationID + ". Batch Id: " + metaData.get("batchId"),
								e);
					}
				}
//			});
		} catch (Exception e) {
			ServiceUtils.printException("Exception in task execution. Task Data:"+json, e);
		}

	}
}


interface WorkflowTask{
	public void execute(Tenant tenant, final String json, String fqn);
}