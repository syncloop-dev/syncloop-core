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

        if (expressionKey.equals("UUID")) {
            return UUID.randomUUID().toString();
        } else if (expressionKey.equals("tenant")) {
            return dataPipeline.rp.getTenant().getName();
        } else if (expressionKey.equals("ContextLocalPath")) {
            if (Boolean.parseBoolean(System.getProperty("CONTAINER_DEPLOYMENT"))) {
                return "/eka";
            } else {
                return "./";
            }
        } else {
            return "null";
        }
    }
}
