package com.eka.middleware.sdk;

import com.beust.jcommander.internal.Maps;
import com.eka.middleware.flow.FlowResolver;
import com.eka.middleware.service.DataPipeline;
import com.eka.middleware.service.PropertyManager;
import com.eka.middleware.service.RuntimePipeline;
import com.eka.middleware.template.SnippetException;
import com.eka.middleware.template.Tenant;

import javax.json.Json;
import javax.json.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

}
