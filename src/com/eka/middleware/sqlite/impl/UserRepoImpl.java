package com.eka.middleware.sqlite.impl;


import com.eka.middleware.sqlite.connection.ConnectionManager;
import com.eka.middleware.sqlite.entity.Group;
import com.eka.middleware.sqlite.entity.User;
import com.eka.middleware.template.SystemException;


import java.sql.*;
import java.util.*;

import static com.eka.middleware.auth.UserProfileManager.isUserExist;
import static com.eka.middleware.auth.UserProfileManager.usersMap;

public class UserRepoImpl {

    public static Map<String, Object> getUsers() throws SystemException {
        Map<String, Object> usersMap = new HashMap<>();

        try (Connection conn = ConnectionManager.getConnection()) {
            String userSql = "SELECT u.* FROM users u";
            try (PreparedStatement userStatement = conn.prepareStatement(userSql)) {
                ResultSet userResultSet = userStatement.executeQuery();

                while (userResultSet.next()) {
                    String userId = userResultSet.getString("user_id");
                    String tenantId = userResultSet.getString("tenant_id");

                    String passwordHash = userResultSet.getString("password");
                    String name = userResultSet.getString("name");
                    String email = userResultSet.getString("email");
                    String status = userResultSet.getString("status");

                    String tenantSql = "SELECT t.name FROM tenant t WHERE t.tenant_id = ?";
                    String groupSql = "SELECT g.name FROM \"groups\" g " +
                            "JOIN user_group_mapping ug ON g.group_id = ug.group_id " +
                            "WHERE ug.user_id = ?";


                    try (PreparedStatement tenantStatement = conn.prepareStatement(tenantSql);
                         PreparedStatement groupStatement = conn.prepareStatement(groupSql)) {

                        tenantStatement.setString(1, tenantId); // Set the tenant_id parameter
                        ResultSet tenantResultSet = tenantStatement.executeQuery();

                        String tenantName = null;
                        if (tenantResultSet.next()) {
                            tenantName = tenantResultSet.getString("name");
                        }

                        groupStatement.setString(1, userId);
                        ResultSet groupResultSet = groupStatement.executeQuery();

                        List<String> groupNames = new ArrayList<>();
                        while (groupResultSet.next()) {
                            String groupName = groupResultSet.getString("name");
                            groupNames.add(groupName);
                        }

                        Map<String, Object> profile = new HashMap<>();
                        profile.put("name", name);
                        profile.put("groups", groupNames);
                        profile.put("email", email);
                        profile.put("tenant", tenantName);

                        Map<String, Object> userMap = new HashMap<>();
                        userMap.put("password", passwordHash);
                        userMap.put("profile", profile);
                        userMap.put("status", status);

                        usersMap.put(email, userMap);
                    }
                }
            }
        } catch (SQLException e) {
            throw new SystemException("EKA_MWS_1001", e);
        }
        return usersMap;
    }


    public static final Map<String, Object> getUsers1() throws SystemException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:/Users/divyansh/Desktop/Syncloop/SyncloopDB/profile")) {
            Statement statement = conn.createStatement();
            ResultSet resultSet = statement.executeQuery("SELECT * FROM users");

            while (resultSet.next()) {
                String passwordHash = resultSet.getString("password");
                String name = resultSet.getString("name");
                String groupsString = resultSet.getString("groups");
                String email = resultSet.getString("email");
                String tenant = resultSet.getString("tenant");

                List<String> groupList = new ArrayList<>();
                if (groupsString != null) {
                    groupsString = groupsString.substring(1, groupsString.length() - 1);
                    String[] groupsArray = groupsString.split(",");
                    groupList.addAll(Arrays.asList(groupsArray));
                }

                Map<String, Object> profile = new HashMap<>();
                profile.put("name", name);
                profile.put("groups", groupList);
                profile.put("email", email);
                profile.put("tenant", tenant);

                Map<String, Object> userMap = new HashMap<>();
                userMap.put("password", passwordHash);
                userMap.put("profile", profile);
                userMap.put("status", "1");

                usersMap.put(email, userMap);
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new SystemException("EKA_MWS_1001", e);
        }
        return usersMap;
    }

    public static void main(String[] args) throws Exception {
        Map<String, Object> b =  getUsers();
        System.out.println(b);
    }

    public static void addUser(User user) throws SystemException {
        try (Connection conn = ConnectionManager.getConnection()) {
            if (isUserExist(user.getEmail())) {
                throw new SystemException("EKA_MWS_1002", new Exception("User already exists with email: " + user.getEmail()));
            }

            String sql = "INSERT INTO \"users\" (password, name, email, tenant_id, status) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
                statement.setString(1, user.getPassword());
                statement.setString(2, user.getName());
                statement.setString(3, user.getEmail());
                statement.setString(4, user.getTenant());
                statement.setString(5, user.getStatus());
                statement.executeUpdate();
            }

            int userId = getUserIdByEmail(conn, user.getEmail());
            addGroupsForUser(conn, userId, user.getGroups());
        } catch (SQLException e) {
            throw new SystemException("EKA_MWS_1001", e);
        }
    }

    public static void updateUser(String email, User user) throws SystemException {
        try (Connection conn = ConnectionManager.getConnection()) {
            String sql = "UPDATE \"users\" SET password = ?, name = ?, email = ?, tenant_id = ?, status = ? WHERE email = ?";
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
                statement.setString(1, user.getPassword());
                statement.setString(2, user.getName());
                statement.setString(3, user.getEmail());
                statement.setString(4, user.getTenant());
                statement.setString(5, user.getStatus());
                statement.setString(6, email);
                statement.executeUpdate();
            }

            int userId = getUserIdByEmail(conn, user.getEmail());
            updateGroupsForUser(conn, userId, user.getGroups());
        } catch (SQLException e) {
            throw new SystemException("EKA_MWS_1001", e);
        }
    }

    public static void deleteUser(String email) throws SystemException {
        try (Connection conn = ConnectionManager.getConnection()) {
            int userId = getUserIdByEmail(conn, email);
            deleteGroupsForUser(conn, userId);

            String sql = "DELETE FROM \"users\" WHERE email = ?";
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
                statement.setString(1, email);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new SystemException("EKA_MWS_1001", e);
        }
    }


    private static int getUserIdByEmail(Connection conn, String email) throws SQLException {
        String userIdSql = "SELECT user_id FROM \"users\" WHERE email = ?";
        try (PreparedStatement userIdStatement = conn.prepareStatement(userIdSql)) {
            userIdStatement.setString(1, email);
            try (ResultSet userIdResultSet = userIdStatement.executeQuery()) {
                return userIdResultSet.next() ? userIdResultSet.getInt("user_id") : -1;
            }
        }
    }

    private static void addGroupsForUser(Connection conn, int userId, List<Group> groups) throws SQLException {
        String insertGroupSql = "INSERT INTO user_group_mapping (user_id, name, group_id) VALUES (?, ?, ?)";
        try (PreparedStatement insertGroupStatement = conn.prepareStatement(insertGroupSql)) {
            for (Group group : groups) {
                int groupId = getGroupIdByName(conn, group.getGroupName());
                if (groupId != -1) {
                    insertGroupStatement.setInt(1, userId);
                    insertGroupStatement.setString(2, group.getGroupName());
                    insertGroupStatement.setInt(3, groupId);
                    insertGroupStatement.executeUpdate();
                }
            }
        }
    }

    private static void updateGroupsForUser(Connection conn, int userId, List<Group> groups) throws SQLException {
        String deleteGroupSql = "DELETE FROM user_group_mapping WHERE user_id = ?";
        try (PreparedStatement deleteGroupStatement = conn.prepareStatement(deleteGroupSql)) {
            deleteGroupStatement.setInt(1, userId);
            deleteGroupStatement.executeUpdate();
        }

        addGroupsForUser(conn, userId, groups);
    }

    private static void deleteGroupsForUser(Connection conn, int userId) throws SQLException {
        String deleteGroupSql = "DELETE FROM user_group_mapping WHERE user_id = ?";
        try (PreparedStatement deleteGroupStatement = conn.prepareStatement(deleteGroupSql)) {
            deleteGroupStatement.setInt(1, userId);
            deleteGroupStatement.executeUpdate();
        }
    }

    private static int getGroupIdByName(Connection conn, String groupName) throws SQLException {
        String groupIdSql = "SELECT group_id FROM \"group\" WHERE name = ?";
        try (PreparedStatement groupIdStatement = conn.prepareStatement(groupIdSql)) {
            groupIdStatement.setString(1, groupName);
            try (ResultSet groupIdResultSet = groupIdStatement.executeQuery()) {
                return groupIdResultSet.next() ? groupIdResultSet.getInt("group_id") : -1;
            }
        }
    }
}


