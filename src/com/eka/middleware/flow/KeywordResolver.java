package com.eka.middleware.flow;

import com.eka.middleware.service.DataPipeline;
import com.eka.middleware.service.PropertyManager;
import com.eka.middleware.service.ServiceUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.UUID;
import java.util.regex.Pattern;

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
            case "*TenantPackagePath":
                return PropertyManager.getPackagePath(dataPipeline.rp.getTenant());
            default:
                String strVal = ServiceUtils.getServerProperty(expressionKey.replace(Pattern.quote("*"), ""));
                if (StringUtils.isBlank(strVal)) {
                    return "null";
                }
                return strVal;
        }
    }
}
