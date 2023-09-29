package com.eka.middleware.sqlite.entity;

import java.util.List;
import java.util.Map;

public class User {

    private String password;
    private String name;
    private List<Group> groups;
    private String email;
    private int tenant;
    private String status;
    private Map<String, Object> profile;
    private String user_id;

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Group> getGroups() {
        return groups;
    }

    public void setGroups(List<Group> groups) {
        this.groups = groups;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUser_id() {
        return user_id;
    }

    public void setUser_id(String user_id) {
        this.user_id = user_id;
    }

    public int getTenant() {
        return tenant;
    }

    public void setTenant(int tenant) {
        this.tenant = tenant;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, Object> getProfile() {
        return profile;
    }

    public void setProfile(Map<String, Object> profile) {
        this.profile = profile;
    }

    public User() {
    }

    public User(String password, String email, int tenant, String name, String status, String user_id, List<Group> groups) {
        this.user_id = user_id;
        this.password = password;
        this.name = name;
        this.groups = groups;
        this.email = email;
        this.tenant = tenant;
        this.status = status;
    }

    public void addGroup(String groupName) {
        // Implement this method if needed.
    }
}
