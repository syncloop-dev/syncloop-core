package com.eka.middleware.auth.db.entity;

import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

@Getter @Setter
public class Tenant {

    private Integer tenant_id;
    private String name;
    private Timestamp created_date;
    private Timestamp modified_date;
    private boolean deleted;
}
