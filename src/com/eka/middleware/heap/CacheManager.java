package com.eka.middleware.heap;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.eka.middleware.service.ServiceUtils;
import org.apache.commons.lang3.StringUtils;

import com.eka.middleware.sdk.api.SyncloopFunctionScanner;
import com.eka.middleware.template.Tenant;


public class CacheManager {

    private static final Map<String, Map<String, Object>> tenantCache = new ConcurrentHashMap<String, Map<String, Object>>();

    private static final Map<String, Map> cacheMap = new ConcurrentHashMap<>();


    /**
     *
     * @param tenant
     */
    public static void initTenantCache(Tenant tenant) {
        Map<String, Object> chache = CacheManager.getCacheAsMap(tenant);
        chache.put("ekamw.promote.runtime.service.reload", false);
    }

    public static List<String> cacheList(Tenant tenant) {
        List<String> list = new ArrayList<>();
        tenantCache.keySet().forEach(key -> {
            if (key.startsWith(tenant.getName() + "-"))
                list.add(key.replace(tenant.getName() + "-", ""));
        });
        return list;
    }

    public static Map<String, Object> getCacheAsMap(Tenant tenant) {
        Map<String, Object> tenantMap = tenantCache.get(tenant.getName());
        if (tenantMap == null) {
            /*
             * if (igNode != null) tenantMap = new IgMap<String, Object>(igNode,
             * "MyTenantCache-" + tenant.getName()); else
             */
            tenantMap = new ConcurrentHashMap<String, Object>();
            tenantCache.put(tenant.getName(), tenantMap);
        }
        return tenantMap;
    }

    public static void addEmbeddedService(String key, String json, Tenant tenant) {

        Map<String, Object> cache = getCacheAsMap(tenant);

        Map<String, String> services = (Map<String, String>) cache.get("embedded_service");
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

        Map<String, String> services = (Map<String, String>) cache.get("embedded_service");
        if (null == services) {
            services = new HashMap<>();
        }

        return services.keySet();
    }

    public static String getEmbeddedService(String key, Tenant tenant) {

        Map<String, Object> cache = getCacheAsMap(tenant);

        Map<String, String> services = (Map<String, String>) cache.get("embedded_service");
        if (null == services) {
            services = new HashMap<>();
        }

        return services.get(StringUtils.strip(key, "/"));
    }

    public static void addMethod(String key, String json, Tenant tenant) {

        Map<String, Object> cache = getCacheAsMap(tenant);

        Map<String, String> services = (Map<String, String>) cache.get("syncloop_method");
        if (null == services) {
            services = new HashMap<>();
            cache.put("syncloop_method", services);
        }

        services.put(key, json);
    }

    /**
     * @param tenant
     * @return
     */
    public static Map<String, String> getMethods(Tenant tenant) {

        Map<String, Object> cache = getCacheAsMap(tenant);

        Map<String, String> services = (Map<String, String>) cache.get("syncloop_method");
        if (null == services) {
            services = new HashMap<>();
        }

        return services;
    }

    public static String getMethod(String key, Tenant tenant) {

        Map<String, Object> cache = getCacheAsMap(tenant);

        Map<String, String> services = (Map<String, String>) cache.get("syncloop_method");
        if (null == services) {
            services = new HashMap<>();
        }

        return services.get(StringUtils.strip(key, "/"));
    }

    public static Set<String> getContextObjectsNames(Tenant tenant) {

        Map<String, Object> cache = getCacheAsMap(tenant);

        Map<String, Object> services = (Map<String, Object>) cache.get("context_objects");
        if (null == services) {
            services = new HashMap<>();
        }

        return services.keySet();
    }

    /**
     * @return
     */
    public static String getContextObjectServiceViewConfig() {
        try {
            return ServiceUtils.objectToJson(SyncloopFunctionScanner.getContextObjectServiceViewConfig().getLatest());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Object getContextObjects(String key, Tenant tenant) {

        Map<String, Object> cache = getCacheAsMap(tenant);

        Map<String, Object> services = (Map<String, Object>) cache.get("context_objects");
        if (null == services) {
            services = new HashMap<>();
        }

        return services.get(StringUtils.strip(key, "/"));
    }

    public static void addContextObject(String key, Object object, Tenant tenant) {

        Map<String, Object> cache = getCacheAsMap(tenant);

        Map<String, Object> services = (Map<String, Object>) cache.get("context_objects");
        if (null == services) {
            services = new HashMap<>();
            cache.put("context_objects", services);
        }

        services.put(key, object);
    }

    public static Map getBSQC() {
        // Map<String, Object> tenantMap = tenantCache.get(tenant.getName());
        String name = "BackupServiceQueueCache";//tenant.getName() + "-" + name;
        Map newCache = cacheMap.get(name);
        if (newCache == null || newCache.size() == 0) {
            newCache = new ConcurrentOffHeapHashMap<String, Object>();
            newCache.put("Created", Instant.now().toString());
            cacheMap.put(name, newCache);
        }
        return newCache;
    }

    public static Map getTenantDistributedCache(Tenant tenant) {
        // Map<String, Object> tenantMap = tenantCache.get(tenant.getName());
        String name = tenant.getName() + "-" + "DistributedOffHeapCache";//tenant.getName() + "-" + name;
        Map newCache = cacheMap.get(name);
        if (newCache == null || newCache.size() == 0) {
            newCache = new ConcurrentOffHeapHashMap<String, Object>();
            newCache.put("Created", Instant.now().toString());
            cacheMap.put(name, newCache);
        }
        return newCache;
    }

    public static void deleteCache(Tenant tenant, String name) {
        name = tenant.getName() + "-" + name;
        Map newCache = tenantCache.get(name);
        if (newCache != null) {
            /*
             * if (IgNode.getNodeId() != null) ((IgMap) newCache).close();
             */
            tenantCache.remove(name);
        }
    }
}
