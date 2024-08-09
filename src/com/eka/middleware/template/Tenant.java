package com.eka.middleware.template;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import com.eka.middleware.heap.HashMap;
import com.eka.middleware.service.ServiceUtils;
import org.apache.logging.log4j.ThreadContext;

public class Tenant {
    private final String name;
    public final String id;
    private static Map<String, Tenant> tenantMap = new HashMap();
    private static final Object syncObject = new Object();
    private final Map<String, Marker> logMarkerMap = new ConcurrentHashMap<String, Marker>();
    final Object lock = new Object();
    private static Logger LOGGER = LogManager.getLogger();
    private static final Marker TENANT_MARKER = MarkerManager.getMarker("TENANT");

    public static void reloadTenant(String name) {
        if (tenantMap.get(name) != null) {
            tenantMap.remove(name);
            getTenant(name);
        }
    }

    public static Tenant getTenant(String name) {

        synchronized (syncObject) {
            try {
                if (name == null)
                    name = "default";
                if (tenantMap.get(name) == null) {
                	Tenant tenant=new Tenant(name);
                    tenantMap.put(name, tenant);
                }
            } catch (Exception e) {
                ServiceUtils.printException("Failed while loading tenant.", e);
            }
        }
        return tenantMap.get(name);
    }

    public static boolean exists(String name) {
        if (name == null || name.trim().length() == 0)
            return false;
        return tenantMap.get(name) != null;
    }

    public static Tenant getTempTenant(String name) {
        return new Tenant(name);
    }

    private Tenant(String name) {
        this.name = name;
        this.id = null;
    }

    public void logInfo(String serviceName, String msg) {

        if (serviceName == null)
            serviceName = name;
        Logger logger = getLogger(serviceName);
        logger.info(TENANT_MARKER, msg);
        clearContext();
    }

    public void logTrace(String serviceName, String msg) {
        if (serviceName == null)
            serviceName = name;
        Logger logger = getLogger(serviceName);
        logger.trace(TENANT_MARKER, msg);
        clearContext();
    }

    public void logWarn(String serviceName, String msg) {
        if (serviceName == null)
            serviceName = name;
        Logger logger = getLogger(serviceName);
        logger.warn(TENANT_MARKER, msg);
        clearContext();
    }

    public void logError(String serviceName, String msg) {
        if (serviceName == null)
            serviceName = name;
        Logger logger = getLogger(serviceName);
        logger.error(TENANT_MARKER, msg);
        clearContext();
    }

    public void logDebug(String serviceName, String msg) {
        if (serviceName == null)
            serviceName = name;
        Logger logger = getLogger(serviceName);
        logger.debug(TENANT_MARKER, msg);
        clearContext();
    }

    private void clearContext() {
        //ThreadContext.remove("name");
        //ThreadContext.remove("service");
        //ThreadContext.clearAll();
    }

    private Marker getMarker(String serviceName) {

        serviceName = serviceName.replace("/", ".");
        String markerKey = name + "/logs/" + serviceName;
        Marker logMarker = logMarkerMap.get(markerKey);
        if (logMarker == null)
            synchronized (lock) {
                logMarker = logMarkerMap.get(markerKey);
                if (logMarker == null) {
                    logMarker = MarkerManager.getMarker(markerKey);
                    logMarkerMap.put(markerKey, logMarker);
                }
            }
        return logMarker;
    }

    private Logger getLogger(String serviceName) {
        serviceName = serviceName.replace("/", ".");
        Logger log = LogManager.getLogger();
        ThreadContext.put("name", name);
        ThreadContext.put("service", serviceName);
        return log;
    }

    public String getName() {
        return name;
    }



}
