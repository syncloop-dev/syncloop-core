package com.eka.middleware.sdk;

import com.eka.middleware.flow.FlowResolver;
import com.eka.middleware.heap.CacheManager;
import com.eka.middleware.service.DataPipeline;
import com.eka.middleware.service.PropertyManager;
import com.eka.middleware.service.RuntimePipeline;
import com.eka.middleware.template.MultiPart;
import com.eka.middleware.template.SnippetException;
import com.eka.middleware.template.Tenant;
import io.undertow.util.Headers;

import javax.json.Json;
import javax.json.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

public class Binder {

    private final String propLoc;

    public Binder(String propLoc) {
        this.propLoc = propLoc;
    }

    public Map<String, Object> run(String sessionId, String apiServiceJson, Map<String, Object> payload)
            throws Exception {

        PropertyManager.initConfig(new String[]{propLoc});

        JsonObject mainflowJsonObject = null;
        UUID coId = UUID.randomUUID();
        RuntimePipeline rp = RuntimePipeline.create(Tenant.getTempTenant("default"), sessionId.toString(), coId.toString(), null, "standalone",
                null);
        DataPipeline dp = rp.dataPipeLine;
        dp.putAll(payload);
        executeService(dp, apiServiceJson, mainflowJsonObject);
        final Map<String, Object> response = new HashMap<>();
        dp.getMap().forEach((k, v) -> {
            response.put(k, v);
        });
        rp.destroy();
        return response;
    }

    private static final void executeService(DataPipeline dataPipeline, String apiServiceJson,
                                             JsonObject mainflowJsonObject) throws SnippetException {
        try {
            InputStream is = new ByteArrayInputStream(apiServiceJson.getBytes(StandardCharsets.UTF_8));
            mainflowJsonObject = Json.createReader(is).readObject();
            FlowResolver.execute(dataPipeline, mainflowJsonObject);
        } catch (Throwable e) {
            dataPipeline.clear();
            dataPipeline.put("error", e.getMessage());
            dataPipeline.put("status", "Service error");
            throw new SnippetException(dataPipeline, "Failed to execute " + dataPipeline.getCurrentResourceName(),
                    new Exception(e));
        }
    }

    public Map<String, Object> getServices() {
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> children = new ArrayList<>();

        String packageDir = PropertyManager.getPackagePath(Tenant.getTempTenant("default"));

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
        response.put("packages", children);
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
        try {
            String serviceInfo = null;
            if (location.startsWith("/packages")) {
                String split[] = location.split(Pattern.quote("."));
                String ext = split[split.length - 1];
                location = PropertyManager.getPackagePath(Tenant.getTempTenant("default")) + location;
                File file = new File(location);
                if (!file.exists()) {
                    return Collections.singletonMap("error", "File not found");
                }

                String contentType = java.nio.file.Files.probeContentType(file.toPath());
                if (contentType == null) {
                    if (ext.toLowerCase().equals("js"))
                        contentType = "application/javascript";
                    if (ext.toLowerCase().equals("json"))
                        contentType = "application/json";
                    if (ext.toLowerCase().equals("css"))
                        contentType = "text/css";
                }
                if (contentType == null) {
                    URLConnection connection = file.toURL().openConnection();
                    contentType = connection.getContentType();
                    connection.getInputStream().close();
                    //contentType="application/";
                }
                MultiPart mp = new MultiPart(null, new FileInputStream(file), false);
                mp.putHeader(Headers.CONTENT_TYPE_STRING, contentType);
                serviceInfo = mp.toString();
            } else {
                String json = CacheManager.getEmbeddedService(location.replaceAll("/embedded/", "").replaceAll(".api", ""), Tenant.getTempTenant("default"));
                MultiPart mp = new MultiPart(null, json.getBytes());
                mp.putHeader(Headers.CONTENT_TYPE_STRING, "application/json");
            }

            return Collections.singletonMap("serviceInfo", serviceInfo);
        } catch (Throwable e) {
            return Collections.singletonMap("error", e.getMessage());
        }
    }
}
