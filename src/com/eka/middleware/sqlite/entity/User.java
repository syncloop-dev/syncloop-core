package com.eka.middleware.sqlite.entity;

import java.util.List;
import java.util.Map;

public class User {

    private static String password;
    private static String name;
    private static List<Group> groups;
    private static String email;
    private static String tenant;
    private static String status;
    private static Map<String, Object> profile;
    private static String user_id;



    public static String getPassword() {
        return password;
    }

    public static void setPassword(String password) {
        User.password = password;
    }

    public static String getName() {
        return name;
    }

    public static void setName(String name) {
        User.name = name;
    }

    public static List<Group> getGroups() {
        return groups;
    }

    public static void setGroups(List<Group> groups) {
        User.groups = groups;
    }

    public static String getEmail() {
        return email;
    }

    public static void setEmail(String email) {
        User.email = email;
    }
    public static String getUser_id() {
        return user_id;
    }

    public static void setUser_id(String user_id) {
        User.user_id = user_id;
    }

    public static String getTenant() {
        return tenant;
    }

    public static void setTenant(String tenant) {
        User.tenant = tenant;
    }

    public static String getStatus() {
        return status;
    }

    public static void setStatus(String status) {
        User.status = status;
    }

    public static Map<String, Object> getProfile() {
        return profile;
    }

    public static void setProfile(Map<String, Object> profile) {
        User.profile = profile;
    }

    public User() {
    }
    public User(String user_id, String password, String name, List<Group> groups, String email, String tenant, String status) {
        this.user_id = user_id;
        this.password = password;
        this.name = name;
        this.groups = groups;
        this.email = email;
        this.tenant = tenant;
        this.status = status;
    }

    public void addGroup(String groupName) {
    }
}
