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
        addEmbeddedService(key, json, "", tenant);
    }

    /**
     *
     * @param key
     * @param json
     * @param identifier
     * @param tenant
     */
    public static void addEmbeddedService(String key, String json, String identifier, Tenant tenant) {

        Map<String, Object> cache = getCacheAsMap(tenant);

        Map<String, String> services = (Map<String, String>) cache.get("embedded_service" + identifier);
        if (null == services) {
            services = new HashMap<>();
            cache.put("embedded_service" + identifier, services);
        }

        services.put(key, json);
    }

    /**
     * @param tenant
     */
    public static void clearEmbeddedService(Tenant tenant) {
        clearEmbeddedService(tenant, "");
    }

    /**
     *
     * @param tenant
     * @param identifier
     */
    public static void clearEmbeddedService(Tenant tenant, String identifier) {

        Map<String, Object> cache = getCacheAsMap(tenant);

        Map<String, String> services = (Map<String, String>) cache.get("embedded_service" + identifier);
        if (null != services) {
            services = new HashMap<>();
            cache.put("embedded_service" + identifier, services);
        }
    }

    /**
     *
     * @param key
     * @param tenant
     */
    public static void clearEmbeddedService(String key, Tenant tenant) {
        clearEmbeddedService(key, "", tenant);
    }

    /**
     *
     * @param key
     * @param tenant
     */
    public static void clearEmbeddedService(String key, String identifier, Tenant tenant) {

        Map<String, Object> cache = getCacheAsMap(tenant);

        Map<String, String> services = (Map<String, String>) cache.get("embedded_service" + identifier);
        if (null != services) {
            services.remove(key);
            cache.put("embedded_service" + identifier, services);
        }
    }

    /**
     * @param tenant
     * @return
     */
    public static Set<String> getEmbeddedServices(Tenant tenant) {
        return getEmbeddedServices(tenant, "");
    }

    /**
     *
     * @param tenant
     * @param identifier
     * @return
     */
    public static Set<String> getEmbeddedServices(Tenant tenant, String identifier) {

        Map<String, Object> cache = getCacheAsMap(tenant);

        Map<String, String> services = (Map<String, String>) cache.get("embedded_service" + identifier);
        if (null == services) {
            services = new HashMap<>();
        }

        return services.keySet();
    }

    /**
     *
     * @param key
     * @param tenant
     * @return
     */
    public static String getEmbeddedService(String key, Tenant tenant) {
        return getEmbeddedService(key, "", tenant);
    }

    /**
     *
     * @param key
     * @param identifier
     * @param tenant
     * @return
     */
    public static String getEmbeddedService(String key, String identifier, Tenant tenant) {

        Map<String, Object> cache = getCacheAsMap(tenant);

        Map<String, String> services = (Map<String, String>) cache.get("embedded_service" + identifier);
        if (null == services) {
            services = new HashMap<>();
        }

        return services.get(StringUtils.strip(key, "/"));
    }


    /**
     *
     * @param key
     * @param json
     * @param tenant
     */
    public static void addMethod(String key, String json, Tenant tenant) {
       addMethod(key, json, "", tenant);
    }

    /**
     *
     * @param key
     * @param json
     * @param tenant
     */
    public static void addMethod(String key, String json, String identifier, Tenant tenant) {

        Map<String, Object> cache = getCacheAsMap(tenant);

        Map<String, String> services = (Map<String, String>) cache.get("syncloop_method" + identifier);
        if (null == services) {
            services = new HashMap<>();
            cache.put("syncloop_method" + identifier, services);
        }

        services.put(key, json);
    }

    /**
     *
     * @param tenant
     */
    public static void clearMethod(Tenant tenant) {
        clearMethod(tenant, "");
    }

    /**
     *
     * @param tenant
     */
    public static void clearMethod(Tenant tenant, String identifier) {

        Map<String, Object> cache = getCacheAsMap(tenant);

        Map<String, String> services = (Map<String, String>) cache.get("syncloop_method" + identifier);
        if (null != services) {
            services = new HashMap<>();
            cache.put("syncloop_method" + identifier, services);
        }
    }

    /**
     *
     * @param key
     * @param tenant
     */
    public static void clearMethod(String key, Tenant tenant) {
        clearMethod(key, "", tenant);
    }

    /**
     *
     * @param key
     * @param identifier
     * @param tenant
     */
    public static void clearMethod(String key, String identifier, Tenant tenant) {

        Map<String, Object> cache = getCacheAsMap(tenant);

        Map<String, String> services = (Map<String, String>) cache.get("syncloop_method" + identifier);
        if (null != services) {
            services.remove(key);
            cache.put("syncloop_method" + identifier, services);
        }

    }

    /**
     * @param tenant
     * @return
     */
    public static Map<String, String> getMethods(Tenant tenant) {
        return getMethods(tenant, "");
    }

    /**
     *
     * @param tenant
     * @param identifier
     * @return
     */
    public static Map<String, String> getMethods(Tenant tenant, String identifier) {

        Map<String, Object> cache = getCacheAsMap(tenant);

        Map<String, String> services = (Map<String, String>) cache.get("syncloop_method" + identifier);
        if (null == services) {
            services = new HashMap<>();
        }

        return services;
    }

    public static String getMethod(String key, Tenant tenant) {
        return getMethod(key, "", tenant);
    }

    /**
     *
     * @param key
     * @param identifier
     * @param tenant
     * @return
     */
    public static String getMethod(String key, String identifier, Tenant tenant) {

        Map<String, Object> cache = getCacheAsMap(tenant);

        Map<String, String> services = (Map<String, String>) cache.get("syncloop_method" + identifier);
        if (null == services) {
            services = new HashMap<>();
        }

        return services.get(StringUtils.strip(key, "/"));
    }

    /**
     *
     * @param tenant
     * @return
     */
    public static Set<String> getContextObjectsNames(Tenant tenant) {
        return getContextObjectsNames(tenant, "");
    }

    /**
     *
     * @param tenant
     * @param identifier
     * @return
     */
    public static Set<String> getContextObjectsNames(Tenant tenant, String identifier) {

        Map<String, Object> cache = getCacheAsMap(tenant);

        Map<String, Object> services = (Map<String, Object>) cache.get("context_objects" + identifier);
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
       return getContextObjects(key, "", tenant);
    }

    /**
     *
     * @param key
     * @param identifier
     * @param tenant
     * @return
     */
    public static Object getContextObjects(String key, String identifier, Tenant tenant) {

        Map<String, Object> cache = getCacheAsMap(tenant);

        Map<String, Object> services = (Map<String, Object>) cache.get("context_objects" + identifier);
        if (null == services) {
            services = new HashMap<>();
        }

        return services.get(StringUtils.strip(key, "/"));
    }


    /**
     *
     * @param key
     * @param object
     * @param tenant
     */
    public static void addContextObject(String key, Object object, Tenant tenant) {
        addContextObject(key, object, "", tenant);
    }

    /**
     *
     * @param key
     * @param object
     * @param tenant
     */
    public static void addContextObject(String key, Object object, String identifier, Tenant tenant) {

        Map<String, Object> cache = getCacheAsMap(tenant);

        Map<String, Object> services = (Map<String, Object>) cache.get("context_objects" + identifier);
        if (null == services) {
            services = new HashMap<>();
            cache.put("context_objects" + identifier, services);
        }

        services.put(key, object);
    }

    /**
     *
     * @param tenant
     */
    public static void clearContextObject(Tenant tenant) {
        clearContextObject(tenant, "");
    }

    /**
     *
     * @param tenant
     * @param identifier
     */
    public static void clearContextObject(Tenant tenant, String identifier) {

        Map<String, Object> cache = getCacheAsMap(tenant);

        Map<String, Object> services = (Map<String, Object>) cache.get("context_objects" + identifier);
        if (null != services) {
            services = new HashMap<>();
            cache.put("context_objects" + identifier, services);
        }
    }

    /**
     *
     * @param key
     * @param tenant
     */
    public static void clearContextObject(String key, Tenant tenant) {
        clearContextObject(key, "", tenant);
    }

    /**
     *
     * @param key
     * @param identifier
     * @param tenant
     */
    public static void clearContextObject(String key, String identifier, Tenant tenant) {

        Map<String, Object> cache = getCacheAsMap(tenant);

        Map<String, Object> services = (Map<String, Object>) cache.get("context_objects" + identifier);
        if (null != services) {
            services.remove(key);
            cache.put("context_objects" + identifier, services);
        }

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
