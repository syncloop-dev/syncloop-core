package com.eka.middleware.adapter;

import java.io.File;
import java.io.FileInputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.eka.middleware.service.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.eka.middleware.pooling.DBCPDataSource;

public class SQL {
    //public static Logger LOGGER = LogManager.getLogger(SQL.class);
    public static List<Map<String, Object>> DQL(String sqlCode, List<Map<String, Object>> sqlParameters, Connection myCon, DataPipeline dp, boolean logQuery) throws Exception {
        List<Map<String, Object>> outputDocList = new ArrayList<Map<String, Object>>();

        if (sqlParameters != null && sqlParameters.size() > 0) {
            for (Map<String, Object> map : sqlParameters) {
                String query = sqlCode;

                for (String k : map.keySet()) {
                    String v = map.get(k) + "";
                    //query=query.replaceAll(Pattern.quote("{"+k+"}"), v);
                    query = ServiceUtils.replaceAllIgnoreRegx(query, "{" + k + "}", v);
                }
                if (logQuery)
                    dp.log(query);
                PreparedStatement myStmt = myCon.prepareStatement(query);
                ResultSet myRs = myStmt.executeQuery();
                List<Map<String, Object>> docList = resultSetToList(myRs);
                if (docList != null && docList.size() > 0)
                    outputDocList.addAll(docList);
            }
        } else {
            if (logQuery)
                dp.log(sqlCode);
            PreparedStatement myStmt = myCon.prepareStatement(sqlCode);
            ResultSet myRs = myStmt.executeQuery();
            List<Map<String, Object>> docList = resultSetToList(myRs);
            if (docList != null && docList.size() > 0)
                outputDocList.addAll(docList);
        }
        return outputDocList;
    }


    public static int DML(String sqlCode, List<Map<String, Object>> sqlParameters, Connection myCon, DataPipeline dp, boolean logQuery) throws Exception {
        int rows = 0;
        if (sqlParameters != null && sqlParameters.size() > 0) {
            for (Map<String, Object> map : sqlParameters) {
                String query = sqlCode;
                for (String k : map.keySet()) {
                    String v = map.get(k) + "";
                    //query=query.replaceAll(Pattern.quote("{"+k+"}"), v);
                    query = ServiceUtils.replaceAllIgnoreRegx(query, "{" + k + "}", v);
                }
                if (logQuery)
                    dp.log(query);
                PreparedStatement myStmt = myCon.prepareStatement(query);
                rows += myStmt.executeUpdate();
            }
        } else {
            PreparedStatement myStmt = myCon.prepareStatement(sqlCode);
            rows = myStmt.executeUpdate();
        }
        return rows;
    }

    public static String[] DML_RGKs(String sqlCode, List<Map<String, Object>> sqlParameters, Connection myCon, DataPipeline dp, boolean logQuery) throws Exception {
        String ids = "";
        if (sqlParameters != null && sqlParameters.size() > 0) {
            for (Map<String, Object> map : sqlParameters) {
                String query = sqlCode;
                for (String k : map.keySet()) {
                    String v = map.get(k) + "";
                    //query=query.replaceAll(Pattern.quote("{"+k+"}"), v);
                    query = ServiceUtils.replaceAllIgnoreRegx(query, "{" + k + "}", v);
                }
                if (logQuery)
                    dp.log(query);
                PreparedStatement myStmt = myCon.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
                if (myStmt.executeUpdate() > 0) {
                    ResultSet keys = myStmt.getGeneratedKeys();
                    if (keys.next())
                        ids += keys.getObject(1) + ",";
                    else
                        ids += "null,";
                }

                //rows += myStmt.executeUpdate();
            }
        } else {
            if (logQuery)
                dp.log(sqlCode);
            PreparedStatement myStmt = myCon.prepareStatement(sqlCode);
            if (myStmt.executeUpdate() > 0) {
                ResultSet keys = myStmt.getGeneratedKeys();
                if (keys.next())
                    ids = keys.getObject(1) + ",";
                else
                    ids = "null";
            }
        }
        ids = (ids + "_").replace(",_", "");
        return ids.split(",");
    }

    public static Boolean DDL(String sqlCode, List<Map<String, Object>> sqlParameters, Connection myCon, DataPipeline dp, boolean logQuery) throws Exception {
        List<Map<String, Object>> outputDocList = new ArrayList<Map<String, Object>>();
        Boolean isSuccessful = false;
        if (sqlParameters != null && sqlParameters.size() > 0) {
            for (Map<String, Object> map : sqlParameters) {
                String query = sqlCode;

                for (String k : map.keySet()) {
                    String v = map.get(k) + "";
                    //query=query.replaceAll(Pattern.quote("{"+k+"}"), v);
                    query = ServiceUtils.replaceAllIgnoreRegx(query, "{" + k + "}", v);
                }
                if (logQuery)
                    dp.log(query);
                PreparedStatement myStmt = myCon.prepareStatement(query);
                isSuccessful = myStmt.execute();
            }
        } else {
            if (logQuery)
                dp.log(sqlCode);
            PreparedStatement myStmt = myCon.prepareStatement(sqlCode);
            isSuccessful = myStmt.execute();
        }
        return isSuccessful;
    }


    public static Connection startTransaction(String jdbcConnection, DataPipeline dp) throws Exception {
        Connection myCon = getConnection(jdbcConnection, dp);
        myCon.setAutoCommit(false);
        return myCon;
    }


    /**
     * @param connection
     * @param isolationLevel
     * @return
     * @throws Exception
     */
    public static Connection setTransactionIsolationLevel(Connection connection, int isolationLevel) throws Exception {
        connection.setTransactionIsolation(isolationLevel);
        return connection;
    }

    public static void commitTransaction(Connection myCon) throws Exception {
        myCon.commit();
    }

    public static void rollbackTransaction(Connection myCon) throws Exception {
        myCon.rollback();
    }

	public static void rollbackTransaction(Connection myCon, Savepoint savepoint) throws SQLException {
		myCon.rollback(savepoint);
	}

	public static Savepoint createSavepoint(Connection myCon, String savePointName) throws SQLException {
    	Savepoint savepoint = null;
    	if (StringUtils.isBlank(savePointName)) {
    		savepoint = myCon.setSavepoint();
		} else {
    		savepoint = myCon.setSavepoint(savePointName);
		}
		return savepoint;
	}

    public static Connection getConnection(String jdbcConnection, DataPipeline dp) throws Exception {
        Properties jdbcProperties = new Properties();
        String pPath = PropertyManager.getPackagePath(dp.rp.getTenant());
        String connectionPropFile = pPath + "packages" + jdbcConnection + ".jdbc";
        dp.log("connectionPropFile:\n" + connectionPropFile, Level.DEBUG);
        //LOGGER.debug("connectionPropFile:\n"+connectionPropFile);
        jdbcProperties.load(new FileInputStream(new File(connectionPropFile)));

        String connectionUrl = jdbcProperties.getProperty("url");
        connectionUrl = connectionUrl.replace("#{PackageConfig}", dp.getMyPackageConfigPath());
        //LOGGER.debug("connectionUrl:\n"+connectionUrl);
        dp.log("connectionPropFile:\n" + connectionPropFile, Level.DEBUG);
        String driver = jdbcProperties.getProperty("driver");
        String username = jdbcProperties.getProperty("username");
        String password = jdbcProperties.getProperty("password");
        String pool = jdbcProperties.getProperty("pool");
        String timeOut = jdbcProperties.getProperty("timeout");
        int pooling = 0;
        if (pool != null) {
            pooling = Integer.parseInt(pool.trim());
        }

        int timeOt = 0;
        if (timeOut != null) {
            timeOt = Integer.parseInt(timeOut.trim());
        }

        Driver driverObj=null;
        CustomClassLoader ccl=null;
        try {
            Class.forName(driver);
        } catch (Exception e) {
            ccl = RTCompile.classLoaderMap.get(dp.rp.getTenant().getName());
            driverObj = (Driver) Class.forName(driver, true, ccl).getConstructor().newInstance();
        }

        Connection myCon = null;

        if (username != null) {
            //myCon=DriverManager.getConnection(connectionUrl,username,password);
            myCon = DBCPDataSource.getConnection(connectionPropFile, connectionUrl, username, password, pooling, timeOt, ccl, driverObj, driver);
        } else {
            //myCon=DriverManager.getConnection(connectionUrl);
            myCon = DBCPDataSource.getConnection(connectionPropFile, connectionUrl, null, null, pooling, timeOt, ccl, driverObj, driver);
        }
        return myCon;
    }


    private static List<Map<String, Object>> resultSetToList(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int columns = md.getColumnCount();
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        while (rs.next()) {
            Map<String, Object> row = new HashMap<String, Object>(columns);
            for (int i = 1; i <= columns; ++i) {
                row.put(md.getColumnName(i), rs.getObject(i));
            }
            rows.add(row);
        }
        return rows;
    }

}
