package com.eka.middleware.auth.db.entity;

public class Groups {
    private int group_id;
    private String groupName;

    private int tenant_id;


    public Groups(String groupName, int tenant_id) {
        this.groupName = groupName;
        this.tenant_id = tenant_id;
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
        return groupName; // Return the groupName for a meaningful representation
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

    public Groups() {
    }
}
