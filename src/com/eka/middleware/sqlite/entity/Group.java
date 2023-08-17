package com.eka.middleware.sqlite.entity;

public class Group {
    private int group_id;
    private String groupName;
    private int tenant_id;

    public Group(int group_id, String groupName, int tenant_id) {
        this.group_id = group_id;
        this.groupName = groupName;
        this.tenant_id = tenant_id;
    }

    public Group(String groupName) {
    }

    public int getGroupId() {
        return group_id;
    }

    public void setGroupId(int group_id) {
        this.group_id = group_id;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public int getTenantId() {
        return tenant_id;
    }

    public void setTenantId(int tenant_id) {
        this.tenant_id = tenant_id;
    }

    public Group() {
    }
}
