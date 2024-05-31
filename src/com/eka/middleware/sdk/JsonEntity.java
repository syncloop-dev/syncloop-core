package com.eka.middleware.sdk;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Getter @Setter
public class JsonEntity {

    private String $id;
    private String $schema;
    private String $ref;
    private String type;
    private String title;
    private String description;
    private Integer minimum;
    private Integer maximum;
    private String format;
    private Set<String> required = new HashSet<>();

    private Map<String, JsonEntity> properties = new HashMap<>();

    private JsonEntity items ;

    /**
     * @param name
     * @param jsonEntity
     */
    public void addEntity(String name, JsonEntity jsonEntity) {
        properties.put(name, jsonEntity);
    }
    public void addRequired(String name) {
        required.add(name);
    }
}
