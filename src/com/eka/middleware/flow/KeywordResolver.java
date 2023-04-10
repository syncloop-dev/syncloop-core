package com.eka.middleware.flow;

import com.eka.middleware.service.DataPipeline;

import java.util.UUID;

public class KeywordResolver {

    /**
     * @param expressionKey
     * @param dataPipeline
     * @return
     */
    public static String find(String expressionKey, DataPipeline dataPipeline) {

        switch (expressionKey) {
            case "*UUID":
                return UUID.randomUUID().toString();
            case "*tenant":
                return dataPipeline.rp.getTenant().getName();
            case "*CorrelationId":
                return dataPipeline.getCorrelationId();

            case "*ContextLocalPath":
                if (Boolean.parseBoolean(System.getProperty("CONTAINER_DEPLOYMENT"))) {
                    return "/eka";
                } else {
                    return "./";
                }
            case "PackageConfig":
                return dataPipeline.getMyPackageConfigPath();
            default:
                return "null";
        }
    }
}
