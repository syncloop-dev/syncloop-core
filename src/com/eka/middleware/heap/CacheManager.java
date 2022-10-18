package com.eka.middleware.heap;

import java.util.Map;
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
}
