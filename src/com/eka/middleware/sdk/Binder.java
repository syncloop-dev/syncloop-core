package com.eka.middleware.sdk;

import com.eka.middleware.heap.CacheManager;
import com.eka.middleware.service.DataPipeline;
import com.eka.middleware.service.RuntimePipeline;
import com.eka.middleware.template.Tenant;
import com.eka.middleware.flow.FlowResolver;
import com.eka.middleware.sdk.api.SyncloopFunctionScanner;
import com.eka.middleware.sdk.api.outline.ServiceOutline;
import com.eka.middleware.service.PropertyManager;
import com.eka.middleware.service.ServiceUtils;
import com.eka.middleware.template.SnippetException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class Binder {

    private final String propLoc;

    public Binder(String propLoc) throws Exception {
        this.propLoc = propLoc;
        PropertyManager.initConfig(new String[]{propLoc});
    }

    public Map<String, Object> runAsync(String sessionId, String apiServiceJson, Map<String, Object> payload) throws Exception {

        AtomicReference<Map<String, Object>> resp = new AtomicReference<>();

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    resp.set(Binder.this.run(sessionId, apiServiceJson, payload));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                ;
            }
        });

        thread.start();
        thread.join();

        return resp.get();
    }

    public Map<String, Object> run(String sessionId, String apiServiceJson, Map<String, Object> payload)
            throws Exception {

        UUID coId = UUID.randomUUID();
        RuntimePipeline rp = RuntimePipeline.create(Tenant.getTempTenant("default"), sessionId.toString(), coId.toString(), "standalone", null);
        DataPipeline dp = rp.dataPipeLine;
        dp.putAll(payload);
        executeService(dp, apiServiceJson);
        final Map<String, Object> response = new HashMap<>();
        dp.getMap().forEach((k, v) -> {
            response.put(k, v);
        });
        response.put("__snapData", dp.getSnapData());
        rp.destroy();
        return response;
    }

    private static final void executeService(DataPipeline dataPipeline, String apiServiceJson) throws SnippetException {
        try (InputStream is = new ByteArrayInputStream(apiServiceJson.getBytes(StandardCharsets.UTF_8));
             JsonReader jsonReader = Json.createReader(is);) {
            JsonObject jsonObject = jsonReader.readObject();
            dataPipeline.setRecordTrace(true);
            FlowResolver.execute(dataPipeline, jsonObject);
        } catch (Throwable e) {
            e.printStackTrace();
            dataPipeline.clear();
            dataPipeline.put("error", e.getMessage());
            dataPipeline.put("status", "Service error");
            throw new SnippetException(dataPipeline, "Failed to execute " + dataPipeline.getCurrentResourceName(),
                    new Exception(e));
        }
    }

    /**
     * @param serviceId
     * @param serviceJson
     */
    public void addEmbeddedService(String serviceId, String serviceJson) {
       addEmbeddedService(serviceId, serviceJson, "");
    }

    /**
     *
     * @param serviceId
     * @param serviceJson
     * @param identifier
     */
    public void addEmbeddedService(String serviceId, String serviceJson, String identifier) {
        CacheManager.addEmbeddedService(serviceId, serviceJson, identifier, Tenant.getTempTenant("default"));
    }

    /**
     *
     * @param serviceId
     * @param identifier
     */
    public void clearEmbeddedService(String serviceId, String identifier) {
        CacheManager.clearEmbeddedService(serviceId, identifier,  Tenant.getTempTenant("default"));
    }

    /**
     *
     * @param identifier
     */
    public void clearEmbeddedService(String identifier) {
        CacheManager.clearEmbeddedService(Tenant.getTempTenant("default"), identifier);
    }

    /**
     *
     */
    public Set<String> getEmbeddedService() {
        return CacheManager.getEmbeddedServices(Tenant.getTempTenant("default"));
    }

    public Set<String> getEmbeddedService(String identifier) {
        return CacheManager.getEmbeddedServices(Tenant.getTempTenant("default"), identifier);
    }

    public Map<String, String> getFunctions() {
        return getFunctions("");
    }

    /**
     * @param identifier
     * @return
     */
    public Map<String, String> getFunctions(String identifier) {
        return CacheManager.getMethods(Tenant.getTempTenant("default"), identifier);
    }

    /**
     * @return
     */
    public Set<String> getContextObjects() {
        return getContextObjects("");
    }

    /**
     * @param identifier
     * @return
     */
    public Set<String> getContextObjects(String identifier) {
        return CacheManager.getContextObjectsNames(Tenant.getTempTenant("default"), identifier);
    }

    /**
     * @param serviceId
     * @return
     */
    public String getServiceJson(String serviceId) {
        return getServiceJson(serviceId, "");
    }

    /**
     *
     * @param serviceId
     * @param identifier
     * @return
     */
    public String getServiceJson(String serviceId, String identifier) {
        return CacheManager.getEmbeddedService(serviceId, identifier, Tenant.getTempTenant("default"));
    }

    /**
     * @return
     */
    public Map<String, Object> getServices() {
        return getServices("");
    }

    /**
     * @param identifier
     * @return
     */
    public Map<String, Object> getServices(String identifier) {
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> children = new ArrayList<>();

        String packageDir = PropertyManager.getPackagePath(Tenant.getTempTenant("default")) + "packages/";

        File file = new File(packageDir);
        File fileList[] = file.listFiles();
        for (File fyle : fileList) {
            Map<String, Object> childMap = getTreeMap(fyle, "package");
            if (childMap != null) {
                children.add(childMap);
            } else {
                System.err.println("No files found in the directory: " + packageDir);
            }
        }

        Map<String, Object> root = new HashMap<>();
        root.put("text", "packages");
        root.put("type", "root");
        root.put("children", children);

        List<Object> list = new ArrayList<>();
        list.add(root);

        response.put("packages", list);
        response.put("embeddedServices", getEmbeddedService(identifier));
        response.put("functions", getFunctions(identifier));
        response.put("contexts", getContextObjects(identifier));
        return response;
    }

    private Map<String, Object> getTreeMap(File file, String type) {
        Map<String, Object> map = new HashMap<>();
        String allowedTypes = "service,map,doc,api,flow,package,root,folder,properties,jar,jdbc,sql,pem,graphql,txt,csv";
        int indx = file.getName().lastIndexOf(".") + 1;
        String fileType = file.getName().substring(indx);

        if (file.getName().equals("build") && !file.isDirectory()) {
            return null;
        }

        map.put("text", file.getName().replace("." + fileType, ""));
        if (file.isDirectory()) {
            map.put("type", type);
            List<Map<String, Object>> children = new ArrayList<>();
            File fileList[] = file.listFiles();
            for (File fyle : fileList) {
                int indx2 = fyle.getName().lastIndexOf(".") + 1;
                String fileType2 = fyle.getName().substring(indx2);
                if (allowedTypes.contains(fileType2) || fyle.isDirectory()) {
                    Map<String, Object> childMap = getTreeMap(fyle, "folder");
                    if (childMap != null) {
                        children.add(childMap);
                    }
                }
            }
            map.put("children", children);
        } else {
            if (indx != 0 && allowedTypes.contains(fileType)) {
                map.put("type", fileType);
            }
        }
        return map;
    }


    public Map<String, Object> getServiceInfo(String location) {
        return getServiceInfo(location, "");
    }

    /**
     * @param location
     * @param identifier
     * @return
     */
    public Map<String, Object> getServiceInfo(String location, String identifier) {
        try {
            String serviceInfo = null;
            if (location.startsWith("/packages")) {
                location = PropertyManager.getPackagePath(Tenant.getTempTenant("default")) + location;
                File file = new File(location);
                if (!file.exists()) {
                    return Collections.singletonMap("error", "File not found");
                }

                serviceInfo = IOUtils.toString(new FileInputStream(file));
            } else if (location.endsWith(".object")) {
                serviceInfo = CacheManager.getContextObjectServiceViewConfig();
            } else if (location.endsWith(".function")) {
                serviceInfo = CacheManager.getMethod(location.replaceAll(".function", ""), identifier, Tenant.getTempTenant("default"));
            } else {
                serviceInfo = CacheManager.getEmbeddedService(location.replaceAll("/embedded/", "").replaceAll(".api", ""), identifier, Tenant.getTempTenant("default"));
            }

            Map<String, Object> tokenData = new ObjectMapper().readValue(serviceInfo, Map.class);

            return tokenData;
        } catch (Throwable e) {
            return Collections.singletonMap("error", e.getMessage());
        }
    }

    /**
     * @param aClass
     * @param identifier
     * @throws Exception
     */
    public void addFunctionClass(Class<?> aClass, String identifier) throws Exception {
        addFunctionClass(aClass, false, identifier);
    }

    /**
     *
     * @param aClass
     * @throws Exception
     */
    public void addFunctionClass(Class<?> aClass) throws Exception {
        addFunctionClass(aClass, false, "");
    }

    /**
     *
     * @param aClass
     * @param allowNonSyncloopFunctions
     * @throws Exception
     */
    public void addFunctionClass(Class<?> aClass, boolean allowNonSyncloopFunctions) throws Exception {
        addFunctionClass(aClass, allowNonSyncloopFunctions, "");
    }

    /**
     * @param aClass
     * @param allowNonSyncloopFunctions
     * @param identifier
     * @throws Exception
     */
    public void addFunctionClass(Class<?> aClass, boolean allowNonSyncloopFunctions, String identifier) throws Exception {
        List<ServiceOutline> serviceOutlines = SyncloopFunctionScanner.addClass(aClass, allowNonSyncloopFunctions);

        for (ServiceOutline serviceOutline : serviceOutlines) {
            CacheManager.addMethod(
                    String.format("%s.%s_%s",
                            serviceOutline.getLatest().getData().getAcn(),
                            serviceOutline.getLatest().getData().getFunction(),
                            serviceOutline.getLatest().getData().getIdentifier()),
                    ServiceUtils.objectToJson(serviceOutline.getLatest()), identifier, Tenant.getTempTenant("default"));
        }
    }

    /**
     * @param identifier
     * @throws Exception
     */
    public void clearFunctionClass(String identifier) throws Exception {
        CacheManager.clearMethod(Tenant.getTempTenant("default"), identifier);
    }

    /**
     * @param key
     * @param identifier
     * @throws Exception
     */
    public void clearFunctionClass(String key, String identifier) throws Exception {
        CacheManager.clearMethod(key, identifier, Tenant.getTempTenant("default"));
    }

    /**
     *
     * @param objectName
     * @param object
     */
    public void addContextObject(String objectName, Object object) {
        CacheManager.addContextObject(objectName, object, Tenant.getTempTenant("default"));
    }

    /**
     *
     * @param objectName
     * @param object
     * @param identifier
     */
    public void addContextObject(String objectName, Object object, String identifier) {
        CacheManager.addContextObject(objectName, object, identifier, Tenant.getTempTenant("default"));
    }

    /**
     * @param objectName
     * @param identifier
     */
    public void clearContextObject(String objectName, String identifier) {
        CacheManager.clearContextObject(objectName, identifier, Tenant.getTempTenant("default"));
    }

    /**
     * @param identifier
     */
    public void clearContextObject(String identifier) {
        CacheManager.clearContextObject(Tenant.getTempTenant("default"), identifier);
    }
}