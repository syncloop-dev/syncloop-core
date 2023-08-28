package com.eka.middleware.heap;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.eka.middleware.template.Tenant;

public class CacheManager {
private static final Map<String, Map<String, Object>> tenantCache= new ConcurrentHashMap<String,  Map<String, Object>>();
	
	public static Map<String, Object> getCacheAsMap(Tenant tenant) {
		Map<String, Object> tenantMap=tenantCache.get(tenant.id);
		if(tenantMap==null)
			tenantMap=new ConcurrentHashMap<String,  Object>();
		tenantCache.put(tenant.id, tenantMap);
		return tenantMap;
	}

	public static void addEmbeddedService(String key, String json, Tenant tenant) {

		Map<String, Object> cache = getCacheAsMap(tenant);

		Map<String, String> services = (Map<String, String>)cache.get("embedded_service");
		if (null == services) {
			services = new HashMap<>();
			cache.put("embedded_service", services);
		}

		services.put(key, json);
	}

	/**
	 * @param tenant
	 * @return
	 */
	public static Set<String> getEmbeddedServices(Tenant tenant) {

		Map<String, Object> cache = getCacheAsMap(tenant);

		Map<String, String> services = (Map<String, String>)cache.get("embedded_service");
		if (null == services) {
			services = new HashMap<>();
		}

		return services.keySet();
	}
}
