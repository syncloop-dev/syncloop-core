package com.eka.middleware.logging;

import com.google.common.collect.Maps;
import org.springframework.util.StopWatch;

import java.util.Map;

public class LogMetaHolder {

    private Map<String, Object> MAP;
    StopWatch stopWatch;

    public LogMetaHolder() {
        MAP = Maps.newHashMap();
        stopWatch = new StopWatch();
    }

    public Map<String, Object> getMAP() {
        return MAP;
    }

    public void startTracking() {
        stopWatch.start();
    }

    public StopWatch stopTracking() {
        if (stopWatch.isRunning()) {
            stopWatch.stop();
        }
        return stopWatch;
    }

}
