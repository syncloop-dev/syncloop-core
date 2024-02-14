package com.eka.middleware.pub.util;

import com.eka.middleware.service.DataPipeline;
import com.google.common.collect.Maps;

import java.util.Map;

public class AppUpdate {

    private static final Map<String, Object> UpdatingStatus = Maps.newHashMap();

    public static Object getStatus(String uniqueId, DataPipeline dataPipeline) {
        Object status = UpdatingStatus.get(String.format("%s_%s", dataPipeline.rp.getTenant().getName(), uniqueId));
        if (null == status) {
            return "COMPLETED_SUCCESS";
        }
        return status;
    }

    public static void updateStatus(String uniqueId, String status, DataPipeline dataPipeline) {
        UpdatingStatus.put(String.format("%s_%s", dataPipeline.rp.getTenant().getName(), uniqueId), status);
    }
}
