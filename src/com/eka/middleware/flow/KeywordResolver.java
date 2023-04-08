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

        //TODO change to switch case.
        if (expressionKey.equals("UUID") || expressionKey.equals("*UUID")) {
            return UUID.randomUUID().toString();
        } else if (expressionKey.equals("tenant") || expressionKey.equals("*tenant")) {
            return dataPipeline.rp.getTenant().getName();
        } else if (expressionKey.equals("CorrelationId") || expressionKey.equals("*CorrelationId")) {
            return dataPipeline.getCorrelationId();
        } else if (expressionKey.equals("ContextLocalPath") || expressionKey.equals("*ContextLocalPath")) {
            if (Boolean.parseBoolean(System.getProperty("CONTAINER_DEPLOYMENT"))) {
                return "/eka";
            } else {
                return "./";
            }
        } else if (expressionKey.equals("PackageConfig")) {
            return dataPipeline.getMyPackageConfigPath();
        } else {
            return "null";
        }
    }
}
