package com.eka.middleware.adapter;

import com.eka.middleware.flow.FlowUtils;
import com.eka.middleware.service.DataPipeline;

import javax.json.*;
import java.io.StringReader;
import java.sql.Connection;
import java.util.*;

public class SqlResolver {
    public static void execute(DataPipeline dp, JsonObject mainSqlJsonObject) throws Exception {

        JsonValue sqlInputs = mainSqlJsonObject.asJsonObject().get("input");
        JsonValue sqlOutput = mainSqlJsonObject.asJsonObject().get("output");
        String sqlCode = mainSqlJsonObject.getString("sql");
        Boolean validationRequired = mainSqlJsonObject.getBoolean("enableServiceDocumentValidation", false);
        if (validationRequired)
            FlowUtils.validateDocuments(dp, sqlInputs, validationRequired);

        byte[] decodedBytes = Base64.getDecoder().decode(sqlCode);
        sqlCode = new String(decodedBytes);
        Properties sqlProperties = (Properties) dp.get("sqlLocalProperties");

        List<Map<String, Object>> sqlParameters = dp.getAsList("inputDocList");

        Boolean isTxn = dp.getAsBoolean("isTxn");
        if (isTxn == null)
            isTxn = false;

        Object txConn = dp.get("txConn");

        String type = "DQL";
        String logQuery = sqlProperties.getProperty("logQuery", "false");
        boolean lq = Boolean.parseBoolean(logQuery);
        if (sqlProperties.get("type") != null) {
            type = (String) sqlProperties.get("type");
            type = type.toUpperCase();
        } else
            throw new Exception("SQL type not specified in the configuration");

        Connection myCon = null;

        //DQL and DDL are non-transactional
        if (txConn != null && "DML".equals(type) && isTxn == true) {
            myCon = (Connection) txConn;
            //isTxn=true;
        } else {
            myCon = SQL.getConnection(sqlProperties.getProperty("JDBC"), dp);
            isTxn = false;
        }

        Map<String, String> columnsType = extractColumnTypes(sqlInputs.toString());


        List<Map<String, Object>> outputDocList = null;
		boolean rollbacked = false;
        try {
            myCon.setAutoCommit(false);
            switch (type) {
                case "DQL":
                    outputDocList = SQL.DQL(sqlCode, sqlParameters, columnsType, myCon, dp, lq);
                    dp.put("outputDocList", outputDocList);
                    dp.put("rows", outputDocList.size());
                    dp.put("success", true);
                    break;
                case "DDL":
                    Boolean isSuccessful = SQL.DDL(sqlCode, sqlParameters, myCon, dp, lq);
                    dp.put("success", isSuccessful);
                    break;
                case "DML":
                    int rows = SQL.DML(sqlCode, sqlParameters, columnsType, myCon, dp, lq);
                    dp.put("rows", rows);
                    dp.put("success", true);
                    myCon.commit();
                    break;
                case "DML_RGK":
                    String keys[] = SQL.DML_RGKs(sqlCode, sqlParameters, columnsType, myCon, dp, lq);
                    dp.put("rows", keys.length);
                    dp.put("keys", keys);
                    dp.put("success", true);
                    myCon.commit();
                    break;
                default:
                    break;
            }
        } catch(Exception e) {
			if(!isTxn) {
				myCon.rollback();
				rollbacked=true;
			}
			throw e;
		} finally {
			if(!isTxn) {
				if(!rollbacked)
					myCon.commit();
				myCon.close();
			}
		}

        JsonArray flowOutputArray = sqlOutput.asJsonArray();

        if (flowOutputArray.isEmpty())
            flowOutputArray = null;
        Map<String, Object> outPutData = null;

        if (flowOutputArray != null) {
            outPutData = new HashMap<>();
            for (JsonValue jsonValue : flowOutputArray) {
                String key = jsonValue.asJsonObject().getString("text");
                Object val = dp.get(key);
                if (val != null)
                    outPutData.put(key, dp.get(key));
            }
        }
        //if(dp.get("*multiPart")!=null)
        //outPutData.put("*multiPart", dp.get("*multiPart"));
        dp.clear();
        if (outPutData != null)
            dp.putAll(outPutData);
        if (validationRequired)
            FlowUtils.validateDocuments(dp, sqlOutput, validationRequired);
    }

    private static Map<String, String> extractColumnTypes(String jsonString) {
        Map<String, String> columnTypes = new HashMap<>();

        try (JsonReader jsonReader = Json.createReader(new StringReader(jsonString))) {
            JsonArray jsonArray = jsonReader.readArray();

            for (JsonObject jsonObject : jsonArray.getValuesAs(JsonObject.class)) {
                JsonArray children = jsonObject.getJsonArray("children");
                if (children == null) continue;

                for (JsonObject child : children.getValuesAs(JsonObject.class)) {
                    JsonObject data = child.getJsonObject("data");
                    if (data == null || !data.containsKey("columnType")) continue;

                    String columnName = child.getString("text");
                    String columnType = data.getString("columnType");
                    columnTypes.put(columnName, columnType);
                }
            }
        }

        return columnTypes;
    }

}
