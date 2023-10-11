package com.eka.middleware.auth.db.entity;

import java.sql.Timestamp;

/**
 * The type Groups.
 */
public class Groups {
    private int group_id;
    private String groupName;
    private int tenant_id;
    private Timestamp created_date;
    private Timestamp modified_date;
    private int deleted;

    public Groups(String groupName, int tenant_id) {
        this.groupName = groupName;
        this.tenant_id = tenant_id;
    }

    public Groups(String groupName, int tenant_id, Timestamp created_date, Timestamp modified_date, int deleted) {
        this.groupName = groupName;
        this.tenant_id = tenant_id;
        this.created_date = created_date;
        this.modified_date = modified_date;
        this.deleted = deleted;
    }

    public Groups(String groupName) {
        this.groupName = groupName;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String toString() {
        return groupName;
    }

    public int getGroupId() {
        return group_id;
    }

    public void setGroupId(int group_id) {
        this.group_id = group_id;
    }

    public int getTenantId() {
        return tenant_id;
    }

    public void setTenantId(int tenant_id) {
        this.tenant_id = tenant_id;
    }

    public Timestamp getCreated_date() {
        return created_date;
    }

    public void setCreated_date(Timestamp created_date) {
        this.created_date = created_date;
    }

    public Timestamp getModified_date() {
        return modified_date;
    }

    public void setModified_date(Timestamp modified_date) {
        this.modified_date = modified_date;
    }

    public int getDeleted() {
        return deleted;
    }

    public void setDeleted(int deleted) {
        this.deleted = deleted;
    }

    public Groups() {
    }
}
