package com.eka.middleware.heap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.ignite.Ignite;

import com.eka.middleware.distributed.offHeap.IgMap;
import com.eka.middleware.distributed.offHeap.IgNode;
import com.eka.middleware.template.Tenant;

public class CacheManager {
	
private static final Map<String, Map<String, Object>> tenantCache= new ConcurrentHashMap<String,  Map<String, Object>>();

private static final Ignite igNode= IgNode.getIgnite();

private static final Map<String, Map> cacheMap=new ConcurrentHashMap<>(); 

public static List<String> cacheList(Tenant tenant){
	List<String> list = new ArrayList<>();
	cacheMap.keySet().forEach(key->{
		list.add(key.replace(tenant.id+"-", ""));
	});
	return list;
}

public static Map<String, Object> getCacheAsMap(Tenant tenant) {
	Map<String, Object> tenantMap=tenantCache.get(tenant.id);
	if(tenantMap==null) {
		if(igNode!=null)
			tenantMap=new IgMap<String,  Object>(igNode,"myTenantCache-"+tenant.id);
		else
			tenantMap=new ConcurrentHashMap<String, Object>();
	}
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

	public static String getEmbeddedService(String key, Tenant tenant) {

		Map<String, Object> cache = getCacheAsMap(tenant);

		Map<String, String> services = (Map<String, String>)cache.get("embedded_service");
		if (null == services) {
			services = new HashMap<>();
		}

		return services.get(key);
	}
	
	public static Map getOrCreateNewCache(Tenant tenant,String name) {
		Map<String, Object> tenantMap=tenantCache.get(tenant.id);
		name=tenant.id+"-"+name;
		Map newCache=cacheMap.get(name);
		if(newCache==null) {
			if(IgNode.getNodeId()!=null)
				newCache=new IgMap<>(igNode, name);
			else
				newCache=new ConcurrentHashMap<String, Object>();
		}
		return newCache;
	}
	
	public static void deleteCache(Tenant tenant,String name) {
		name=tenant.id+"-"+name;
		Map newCache=cacheMap.get(name);
		if(newCache!=null) {
			if(IgNode.getNodeId()!=null)
				((IgMap)newCache).close();
			cacheMap.remove(name);
		}
	}
}
