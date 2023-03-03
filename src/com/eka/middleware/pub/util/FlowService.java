package com.eka.middleware.pub.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.eka.middleware.heap.HashMap;

public class FlowService {

    final Map<String, Object> flow = new HashMap();
    final Map<String, Object> latest = new HashMap();
    final List<Object> input = new ArrayList();
    final List<Object> output = new ArrayList();
    final List<Object> flowSteps = new ArrayList();

    public FlowService(String description, String title) {
        flow.put("latest", latest);
        flow.put("description", description);
        flow.put("title", title);
        latest.put("input", input);
        latest.put("output", output);
        latest.put("flow", flowSteps);
        latest.put("lockedByUser", "admin");
    }

    public Map<String, Object> getFlow() {
        return flow;
    }

    public Map<String, Object> getVersion() {
        return latest;
    }

    public List<Object> getInput() {
        return input;
    }

    public List<Object> getOutput() {
        return output;
    }

    public List<Object> getFlowSteps() {
        return flowSteps;
    }

}
