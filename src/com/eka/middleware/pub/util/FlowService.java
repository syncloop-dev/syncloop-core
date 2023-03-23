package com.eka.middleware.pub.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.eka.middleware.heap.HashMap;
import org.apache.commons.lang3.StringUtils;

public class FlowService {

    final Map<String, Object> flow = new HashMap();
    final FlowIOS latest = new FlowIOS();


    public FlowService(String description, String title, Set<String> consumers, Set<String> developers) {
        flow.put("latest", latest);
        flow.put("description", description);
        flow.put("title", title);
        flow.put("consumers", StringUtils.join(consumers, ","));
        flow.put("developers", StringUtils.join(developers, ","));
    }

    public Map<String, Object> getFlow() {
        return flow;
    }

    public FlowIOS getVersion() {
        return latest;
    }

    public List<Object> getInput() {
        return latest.getInput();
    }

    public List<Object> getOutput() {
        return latest.getOutput();
    }

    public List<Object> getFlowSteps() {
        return latest.getFlow();
    }

}
