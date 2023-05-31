package com.eka.middleware.template;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.spec.SecretKeySpec;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.pac4j.core.config.Config;
import org.pac4j.jwt.config.signature.SecretSignatureConfiguration;
import org.pac4j.jwt.profile.JwtGenerator;

import com.eka.middleware.auth.Security;
import com.eka.middleware.heap.HashMap;
import com.eka.middleware.service.PropertyManager;
import com.eka.middleware.service.ServiceUtils;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.spi.LoggerContext;

public class Tenant {
    private final String name;
    public final String id;
    public final SecretSignatureConfiguration secConf;
    public final SecretKeySpec KEY;// =ServiceUtils.setKey(JWT_MASALA);
    private static Map<String, Tenant> tenantMap = new HashMap();
    public final JwtGenerator jwtGenerator;
    private static final Object syncObject = new Object();
    private final Map<String, Marker> logMarkerMap = new ConcurrentHashMap<String, Marker>();
    final Object lock = new Object();
    private static Logger LOGGER = LogManager.getLogger();
    private static final Marker TENANT_MARKER = MarkerManager.getMarker("TENANT");
    public Config JWTAuthClientConfig = null;

    public static Tenant getTenant(String name) {

        synchronized (syncObject) {
            try {
                if (name == null)
                    name = "default";
                if (tenantMap.get(name) == null) {
                    tenantMap.put(name, new Tenant(name));
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
        return new Tenant(name, null);
    }

    private Tenant(String name, String id) {
        this.name = name;
        this.id = null;
        secConf = null;
        KEY = null;
        jwtGenerator = null;
    }

    private Tenant(String name) throws Exception {
        this.name = name;
        String key = PropertyManager.getGlobalProperties(name).getProperty(Security.PRIVATE_PROPERTY_KEY_NAME);
        if (key == null)
            throw new Exception("Tenant public key not found or tenant not found. Tenant name: " + name);
        key = Base64.getEncoder().encodeToString(key.substring(0, 32).getBytes());
        this.id = key;
        secConf = new SecretSignatureConfiguration(id);
        KEY = ServiceUtils.getKey(id);
        jwtGenerator = new JwtGenerator(secConf);
        Date expiryDate = new Date();
        expiryDate = ServiceUtils.addHoursToDate(expiryDate, 8);
        jwtGenerator.setExpirationTime(expiryDate);
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
        //LoggerContext ctx=LogManager.getContext();
        //ctx.putObject("name", name);
        //ctx.putObject("service", serviceName);
        return log;
    }

    public String getName() {
        return name;
    }

//	public static void main(String[] args) throws Exception{
//		LoggerContext context = (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
//		URL url = new File("D:\\git\\ekamw-distributions\\resources\\config\\log4j2.xml").toURL();
//		// this will force a reconfiguration
//		context.setConfigLocation(url.toURI());
//		Marker tm = MarkerManager.getMarker("TENANT");
//        Logger log = LogManager.getLogger();
//        ThreadContext.put("name", "default");
//        ThreadContext.put("service", "test");
//        log.info(tm, "Hey here's some info log from main!");
//    }

}
