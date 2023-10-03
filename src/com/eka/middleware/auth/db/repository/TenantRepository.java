package com.eka.middleware.auth.db.repository;

import com.beust.jcommander.internal.Lists;
import com.eka.middleware.adapter.SQL;
import com.eka.middleware.template.SystemException;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TenantRepository {

    /**
     * @param name
     * @throws SystemException
     */
    public static int create(String name) throws SystemException {
        try (Connection connection = SQL.getProfileConnection()) {
            String sql = "INSERT INTO tenant (name, created_date) VALUES (?, ?)";
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, name);
            statement.setString(2, LocalDateTime.now().toString());

            statement.executeUpdate();

            ResultSet generatedKeys = statement.getGeneratedKeys();
            if (generatedKeys.next()) {
                int id = generatedKeys.getInt(1);
                return id;
            } else {
                throw new SystemException("EKA_MWS_1002", new Exception("Failed to retrieve user_id after insert"));
            }

        } catch (SQLException e) {
            throw new SystemException("EKA_MWS_1001", e);
        }
    }

    public static List<String> getAllTenants() {
        List<String> tenants = new ArrayList<>();
        try (Connection conn = SQL.getProfileConnection()) {
            String sql = "SELECT name FROM tenant";
            try (PreparedStatement statement = conn.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {

                while (resultSet.next()) {
                    String name = resultSet.getString("name");
                    tenants.add(name);
                }
            }
        } catch (SQLException e) {
            //throw new SystemException("EKA_MWS_1001", e);
            e.printStackTrace();
        }
        return tenants;
    }

}
