package com.eka.middleware.auth.db.repository;

import com.eka.middleware.adapter.SQL;
import com.eka.middleware.auth.db.entity.Groups;
import com.eka.middleware.template.SystemException;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GroupsRepository {

    @Deprecated
    public static List<String> getAllGroups() throws SystemException {
        List<String> groups = new ArrayList<>();
        try (Connection conn = SQL.getProfileConnection(false)) {
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

    // 1- get groups for tenant
    public static List<String> getGroupsForTenant(int tenantId) throws SystemException {
        List<String> groups = new ArrayList<>();
        try (Connection conn = SQL.getProfileConnection(false)) {
           // String sql = "SELECT * FROM groups WHERE tenant_id = ?";
            String sql = "SELECT * FROM groups WHERE tenant_id = ? AND deleted = 0";
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
                statement.setInt(1, tenantId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        String group = getGroupFromResultSet(resultSet);
                        groups.add(group);
                    }
                }
            }
        } catch (SQLException e) {
            throw new SystemException("EKA_MWS_1001", e);
        }
        return groups;
    }

    public static String getGroupByName(String groupName) throws SystemException {
        try (Connection conn = SQL.getProfileConnection(false)) {
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

    public static void addGroup(Groups group) throws SystemException {
        try (Connection conn = SQL.getProfileConnection(false)) {
            String sql = "INSERT INTO groups (name, tenant_id, created_date, modified_date, deleted) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
                statement.setString(1, group.getGroupName());
                statement.setInt(2, group.getTenantId());
                statement.setTimestamp(3, group.getCreated_date());
                statement.setTimestamp(4, group.getModified_date());
                statement.setInt(5, group.getDeleted());
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new SystemException("EKA_MWS_1001", e);
        }
    }

    public static void updateGroup(Groups group) throws SystemException {
        try (Connection conn = SQL.getProfileConnection(false)) {
            String sql = "UPDATE groups SET name = ?, modified_date = ?, deleted = ? WHERE name = ? AND tenant_id = ?";
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
                statement.setString(1, group.getGroupName());
                statement.setTimestamp(2, group.getModified_date());
                statement.setInt(3, group.getDeleted());
                statement.setString(4, group.getGroupName());
                statement.setInt(5, group.getTenantId());
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new SystemException("EKA_MWS_1001", e);
        }
    }

    public static void deleteGroup(String groupName) throws SystemException {
        try (Connection conn = SQL.getProfileConnection(false)) {
            String sql = "UPDATE groups SET deleted = 1 WHERE name = ?";
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
                statement.setString(1, groupName);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new SystemException("EKA_MWS_1001", e);
        }
    }

    public static void deleteGroupForTenant(String groupName, int tenantId) throws SystemException {
        try (Connection conn = SQL.getProfileConnection(false)) {
            String sql = "UPDATE groups SET deleted = 1, modified_date = ? WHERE name = ? AND tenant_id = ?";
            Timestamp modifiedDate = new Timestamp(System.currentTimeMillis());
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
                statement.setTimestamp(1, modifiedDate);
                statement.setString(2, groupName);
                statement.setInt(3, tenantId);
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
    public static Groups getGroupByNameAndTenant(String groupName, int tenantId) throws SystemException {
        try (Connection conn = SQL.getProfileConnection(false)) {
            String sql = "SELECT * FROM groups WHERE name = ? AND tenant_id = ? AND deleted = 1";
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
                statement.setString(1, groupName);
                statement.setInt(2, tenantId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        String name = resultSet.getString("name");
                        int tenant_id = resultSet.getInt("tenant_id");
                        int deleted = resultSet.getInt("deleted");
                        Timestamp createdDate = resultSet.getTimestamp("created_date");
                        Timestamp modifiedDate = resultSet.getTimestamp("modified_date");
                        return new Groups(name, tenantId, createdDate, modifiedDate, deleted);

                    }
                }
            }
        } catch (SQLException e) {
            throw new SystemException("EKA_MWS_1001", e);
        }
        return null;
    }


    public static int getOrCreateGroup(String groupName, int tenantId, Connection connection) throws SQLException {
        String selectGroupSQL = "SELECT group_id FROM groups WHERE name = ? AND tenant_id = ?";
        String insertGroupSQL = "INSERT INTO groups (name, tenant_id, created_date, modified_date, deleted) VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement selectGroupStatement = connection.prepareStatement(selectGroupSQL)) {
            selectGroupStatement.setString(1, groupName);
            selectGroupStatement.setInt(2, tenantId);

            ResultSet resultSet = selectGroupStatement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getInt("group_id");
            } else {
                try (PreparedStatement insertGroupStatement = connection.prepareStatement(insertGroupSQL, Statement.RETURN_GENERATED_KEYS)) {
                    insertGroupStatement.setString(1, groupName);
                    insertGroupStatement.setInt(2, tenantId);
                    insertGroupStatement.setTimestamp(3, new Timestamp(System.currentTimeMillis())); // Set created_date
                    insertGroupStatement.setTimestamp(4, new Timestamp(System.currentTimeMillis())); // Set modified_date
                    insertGroupStatement.setBoolean(5, false); // Set deleted
                    insertGroupStatement.executeUpdate();

                    ResultSet generatedKeys = insertGroupStatement.getGeneratedKeys();
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    } else {
                        throw new SQLException("Failed to retrieve generated group_id.");
                    }
                }
            }
        }
    }

}
