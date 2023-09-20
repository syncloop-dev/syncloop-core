package com.eka.middleware.service;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Builder @ToString
public class FlowMeta {

    @Getter @Setter
    private String name;

    @Getter @Setter
    private String type;

    @Getter @Setter
    private String guid;

    @Getter @Setter
    private String resource;
}
