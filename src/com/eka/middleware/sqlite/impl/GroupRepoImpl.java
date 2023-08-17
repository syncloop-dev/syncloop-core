package com.eka.middleware.sqlite.impl;

import com.eka.middleware.sqlite.connection.ConnectionManager;
import com.eka.middleware.sqlite.entity.Group;
import com.eka.middleware.template.SystemException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class GroupRepoImpl {

    public static List<String> getAllGroups() throws SystemException {
        List<String> groups = new ArrayList<>();
        try (Connection conn = ConnectionManager.getConnection()) {
            String sql = "SELECT * FROM groups";
            try (PreparedStatement statement = conn.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {

                while (resultSet.next()) {
                    String group = getGroupFromResultSet(resultSet);
                    groups.add(group);
                }
            }
        } catch (SQLException e) {
            throw new SystemException("EKA_MWS_1001", e);
        }
        return groups;
    }

    public static String getGroupByName(String groupName) throws SystemException {
        try (Connection conn = ConnectionManager.getConnection()) {
            String sql = "SELECT * FROM groups WHERE name = ?";
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
                statement.setString(1, groupName);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return getGroupFromResultSet(resultSet);
                    }
                }
            }
        } catch (SQLException e) {
            throw new SystemException("EKA_MWS_1001", e);
        }
        return null;
    }

    public static void addGroup(Group group) throws SystemException {
        try (Connection conn = ConnectionManager.getConnection()) {
            String sql = "INSERT INTO groups (name) VALUES (?)";
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
                statement.setString(1, group.getGroupName());
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new SystemException("EKA_MWS_1001", e);
        }
    }

    public static void updateGroup(String groupName, Group group) throws SystemException {
        try (Connection conn = ConnectionManager.getConnection()) {
            String sql = "UPDATE groups SET name = ? WHERE name = ?";
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
                statement.setString(1, group.getGroupName());
                statement.setString(2, groupName);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new SystemException("EKA_MWS_1001", e);
        }
    }

    public static void deleteGroup(String groupName) throws SystemException {
        try (Connection conn = ConnectionManager.getConnection()) {
            String sql = "DELETE FROM groups WHERE name = ?";
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
                statement.setString(1, groupName);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new SystemException("EKA_MWS_1001", e);
        }
    }
    private static String getGroupFromResultSet(ResultSet resultSet) throws SQLException {
        String groupName = resultSet.getString("name");
        return groupName;
    }
}
