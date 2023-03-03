package com.eka.middleware.template;

import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.eka.middleware.service.DataPipeline;
import com.eka.middleware.service.ServiceUtils;

import java.util.Map;

public class SnippetException extends Exception {

    public static Logger logger = LogManager.getLogger(SnippetException.class);
    public boolean propagate = true;
    private final String message;
    private String code = "SY_0001";
    private final Map<String, Object> meta;

    public SnippetException(DataPipeline dataPipeLine, String errMsg, Exception e) {
        super(e);
        meta = Maps.newHashMap();
        propagate = !errMsg.equals(e.getMessage());
        message = e.getMessage();
        if (propagate) {
            ServiceUtils.printException(dataPipeLine.getSessionId() + "    " + dataPipeLine.getCorrelationId() + "    " + errMsg, this);
            ServiceUtils.printException(dataPipeLine, errMsg, this);
        }
    }

    /**
     * @param dataPipeLine
     * @param errMsg
     * @param e
     * @param meta
     */
    public SnippetException(DataPipeline dataPipeLine, String errMsg, Exception e, Map<String, Object> meta, String code) {
        super(e);
        this.meta = meta;
        propagate = !errMsg.equals(e.getMessage());
        message = e.getMessage();

        if (StringUtils.isNotBlank(code)) {
            this.code = code;
        }

        if (propagate) {
            ServiceUtils.printException(dataPipeLine.getSessionId() + "    " + dataPipeLine.getCorrelationId() + "    " + errMsg, this);
            ServiceUtils.printException(dataPipeLine, errMsg, this);
        }
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    public String getCode() {
        return code;
    }

    @Override
    public String toString() {
        return message;
    }
}
