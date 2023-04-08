package com.eka.middleware.pub.util;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class FlowIOS {

    private final List<Object> input = new ArrayList();
    private final List<Object> output = new ArrayList();
    private final List<Object> flow = new ArrayList();
    final private String lockedByUser = null;
}
