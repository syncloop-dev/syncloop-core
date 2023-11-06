package com.eka.middleware.adapter;

import com.eka.middleware.flow.FlowUtils;
import com.eka.middleware.pooling.DBCPDataSource;
import com.eka.middleware.service.*;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;

import java.io.File;
import java.io.FileInputStream;
import java.sql.*;
import java.sql.Date;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                String[] parameterNames = StringUtils.substringsBetween(sqlCode, "{", "}");

                if (parameterNames != null) {
                    for (String paramName : parameterNames) {
                        String valuePlaceholder = "'{" + paramName + "}'";
                        if (map.containsKey(paramName)) {
                            //Object value = map.get(paramName);
                            query = query.replace(valuePlaceholder, "?");
                        }
                    }
                }

                query = removeUninitialized(query);
                if (logQuery)
                    dp.log(query);

                try (PreparedStatement myStmt = myCon.prepareStatement(query)) {
                    int paramIndex = 1;
                    for (String paramName : parameterNames) {
                        if (map.containsKey(paramName)) {
                            Object value = map.get(paramName);
                            String columnName = paramName;
                            String columnType = getColumnTypeFromDatabase(sqlCode, columnName, myCon);

                            if (value == null) {
                                myStmt.setNull(paramIndex, Types.VARCHAR);
                            } else {
                                switch (columnType) {
                                    case "INT":
                                        myStmt.setInt(paramIndex, (Integer) value);
                                        break;
                                    case "DOUBLE":
                                        myStmt.setDouble(paramIndex, (Double) value);
                                        break;
                                    case "VARCHAR":
                                        myStmt.setString(paramIndex, value.toString());
                                        break;
                                    case "BIT":
                                    case "BOOLEAN":
                                        myStmt.setBoolean(paramIndex, (Boolean) value);
                                        break;
                                    case "BLOB":
                                        myStmt.setBytes(paramIndex, (byte[]) value);
                                        break;
                                    case "DATE":
                                        java.util.Date date = (java.util.Date) value;
                                        myStmt.setDate(paramIndex, new java.sql.Date(date.getTime()));
                                        break;
                                    case "STRING":
                                        myStmt.setString(paramIndex, (String) value);
                                        break;
                                    default:
                                        myStmt.setObject(paramIndex, value);
                                        break;
                                }
                            }
                            paramIndex++;
                        }
                    }

                    rows += myStmt.executeUpdate();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        } else {
            sqlCode = removeUninitialized(sqlCode);
            if (logQuery)
                dp.log(sqlCode);

            try (PreparedStatement myStmt = myCon.prepareStatement(sqlCode)) {
                rows = myStmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return rows;
    }

    private static String getColumnTypeFromDatabase(String sqlCode,String columnName, Connection myCon) throws SQLException {
        DatabaseMetaData databaseMetaData = myCon.getMetaData();
        ResultSet rs = databaseMetaData.getColumns(null, null, getTableNameFromQuery(sqlCode), null);

        while (rs.next()) {
            String colName = rs.getString("COLUMN_NAME");
            String colType = rs.getString("TYPE_NAME");

            if (colName.equals(columnName)) {
                rs.close();
                return colType;
            }
        }

        rs.close();
        return "STRING";
    }
    public static String getTableNameFromQuery(String sqlQuery) {
        try {
            net.sf.jsqlparser.statement.Statement statement = CCJSqlParserUtil.parse(sqlQuery);

            if (statement instanceof Update) {
                Update updateStatement = (Update) statement;
                Table table = updateStatement.getTable();
                return table.getName();
            } else if (statement instanceof Insert) {
                Insert insertStatement = (Insert) statement;
                Table table = insertStatement.getTable();
                return table.getName();
            } else if (statement instanceof Delete) {
                Delete deleteStatement = (Delete) statement;
                Table table = deleteStatement.getTable();
                return table.getName();
            } else {
                return null;
            }
        } catch (JSQLParserException e) {
            e.printStackTrace();
            return null;
        }
    }


    private static String removeUninitialized(String sqlCode) {
        String[] placeholders = StringUtils.substringsBetween(sqlCode, "{", "}");
        if (placeholders != null) {
            for (String placeholder : placeholders) {
                String replacement = "NULL";
                sqlCode = sqlCode.replace("'{" + placeholder + "}'", replacement);
            }
        }
        return sqlCode;
    }
    public static String[] DML_RGKs(String sqlCode, List<Map<String, Object>> sqlParameters, Connection myCon, DataPipeline dp, boolean logQuery) throws Exception {
        String ids = "";

        if (sqlParameters != null && sqlParameters.size() > 0) {
            for (Map<String, Object> map : sqlParameters) {
                String query = sqlCode;
                String[] parameterNames = StringUtils.substringsBetween(sqlCode, "{", "}");

                if (parameterNames != null) {
                    for (String paramName : parameterNames) {
                        String valuePlaceholder = "'{" + paramName + "}'";
                        query = query.replace(valuePlaceholder, "?");
                    }
                }

                query = removeUninitialized(query);
                if (logQuery)
                    dp.log(query);

                try (PreparedStatement myStmt = myCon.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
                    int paramIndex = 1;
                    for (String paramName : parameterNames) {
                        if (map.containsKey(paramName)) {
                            Object value = map.get(paramName);
                            String columnName = paramName;
                            String columnType = getColumnTypeFromDatabase(sqlCode, columnName, myCon);

                            if (value == null) {
                                myStmt.setNull(paramIndex, Types.VARCHAR);
                            } else {
                                switch (columnType) {
                                    case "INT":
                                        myStmt.setInt(paramIndex, (Integer) value);
                                        break;
                                    case "DOUBLE":
                                        myStmt.setDouble(paramIndex, (Double) value);
                                        break;
                                    case "VARCHAR":
                                        myStmt.setString(paramIndex, value.toString());
                                        break;
                                    case "BIT":
                                    case "BOOLEAN":
                                        myStmt.setBoolean(paramIndex, (Boolean) value);
                                        break;
                                    case "BLOB":
                                        myStmt.setBytes(paramIndex, (byte[]) value);
                                        break;
                                    case "DATE":
                                        java.util.Date date = (java.util.Date) value;
                                        myStmt.setDate(paramIndex, new java.sql.Date(date.getTime()));
                                        break;
                                    case "STRING":
                                        myStmt.setString(paramIndex, (String) value);
                                        break;
                                    default:
                                        myStmt.setObject(paramIndex, value);
                                        break;
                                }
                            }
                            paramIndex++;
                        }
                    }

                    int rows = myStmt.executeUpdate();
                    if (rows > 0) {
                        ResultSet keys = myStmt.getGeneratedKeys();
                        if (keys.next()) {
                            ids += keys.getObject(1) + ",";
                        }
                        else {
                            ids += "null,";
                        }
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        } else {
            sqlCode = removeUninitialized(sqlCode);
            if (logQuery)
                dp.log(sqlCode);

            try (PreparedStatement myStmt = myCon.prepareStatement(sqlCode, Statement.RETURN_GENERATED_KEYS)) {
                int rows = myStmt.executeUpdate();
                if (rows > 0) {
                    ResultSet keys = myStmt.getGeneratedKeys();
                    if (keys.next())
                        ids = keys.getObject(1) + ",";
                    else
                        ids = "null";

                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

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
        dp.log("connectionPropFile: " + connectionPropFile, Level.DEBUG);
        //LOGGER.debug("connectionPropFile:\n"+connectionPropFile);
        jdbcProperties.load(new FileInputStream(new File(connectionPropFile)));

        String connectionUrl = jdbcProperties.getProperty("url");
        connectionUrl = FlowUtils.placeXPathInternalVariables(connectionUrl, dp);
        //connectionUrl = connectionUrl.replace("#{PackageConfig}", dp.getMyPackageConfigPath());
        //LOGGER.debug("connectionUrl:\n"+connectionUrl);
        dp.log("connectionPropFile: " + connectionPropFile, Level.DEBUG);
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
                int colType = md.getColumnType(i);

                if (colType == Types.BINARY || colType == Types.BLOB || colType == Types.VARBINARY
                        || colType == Types.LONGVARBINARY) {
                    row.put(md.getColumnName(i), rs.getBytes(i));
                }else if (colType == Types.BIGINT || colType == Types.INTEGER) {
                    row.put(md.getColumnName(i), rs.getInt(i));
                }else if (colType == Types.VARCHAR || colType == Types.CHAR) {
                    row.put(md.getColumnName(i), rs.getString(i));
                }else if (colType == Types.BOOLEAN || colType == Types.TINYINT || colType == Types.BIT) {
                    row.put(md.getColumnName(i), rs.getBoolean(i));
                } else if(colType == Types.DATE || colType == Types.TIME || colType == Types.TIMESTAMP) {
                    row.put(md.getColumnName(i), rs.getDate(i));
                }else{
                    row.put(md.getColumnName(i), rs.getObject(i));
                }
            }
            rows.add(row);
        }
        return rows;
    }

}
