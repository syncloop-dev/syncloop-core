package com.eka.middleware.heap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.eka.middleware.sdk.api.SyncloopFunctionScanner;
import com.eka.middleware.service.ServiceUtils;
import org.apache.commons.lang3.StringUtils;

import com.eka.middleware.template.Tenant;


public class CacheManager {

    private static final Map<String, Map<String, Object>> tenantCache = new ConcurrentHashMap<String, Map<String, Object>>();

    private static final Map<String, Map> cacheMap = new ConcurrentHashMap<>();

    public static List<String> cacheList(Tenant tenant) {
        List<String> list = new ArrayList<>();
        cacheMap.keySet().forEach(key -> {
            list.add(key.replace(tenant.getName() + "-", ""));
        });
        return list;
    }

    public static Map<String, Object> getCacheAsMap(Tenant tenant) {
        Map<String, Object> tenantMap = tenantCache.get(tenant.getName());
        if (null == tenantMap) {
            tenantMap = new ConcurrentHashMap<String, Object>();
        }
        tenantCache.put(tenant.getName(), tenantMap);
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

    public static Map getOrCreateNewCache(Tenant tenant, String name) {
        Map<String, Object> tenantMap = tenantCache.get(tenant.getName());
        name = tenant.getName() + "-" + name;
        Map newCache = cacheMap.get(name);
        if (newCache == null) {
             newCache = new ConcurrentHashMap<String, Object>();
        }
        return newCache;
    }

    public static void deleteCache(Tenant tenant, String name) {
        name = tenant.getName() + "-" + name;
        Map newCache = cacheMap.get(name);
        if (newCache != null) {
            cacheMap.remove(name);
        }
    }
}
